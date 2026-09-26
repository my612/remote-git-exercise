package com.mobuk.app.data.remote.webimport

import android.util.Log
import com.mobuk.app.data.local.AppJson
import com.mobuk.app.domain.logic.DietTagger
import com.mobuk.app.domain.logic.IngredientNormalizer
import com.mobuk.app.domain.logic.TimerExtractor
import com.mobuk.app.domain.model.Nutrition
import com.mobuk.app.domain.model.Recipe
import com.mobuk.app.domain.model.RecipeSource
import com.mobuk.app.domain.model.RecipeStep
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import java.security.MessageDigest

/**
 * Imports any recipe page that publishes schema.org/Recipe JSON-LD (most recipe sites do, including
 * BBC Good Food, Jamie Oliver, Serious Eats, NYT Cooking, and Mob itself). Falls back to microdata.
 */
class RecipeUrlImporter(private val client: HttpClient) {

    sealed interface Result {
        data class Success(val recipe: Recipe) : Result
        data class Failure(val message: String) : Result
    }

    suspend fun import(url: String): Result = withContext(Dispatchers.IO) {
        val normalized = url.trim().let { if (it.startsWith("http")) it else "https://$it" }
        val html = try {
            val response = client.get(normalized) {
                header("Accept", "text/html,application/xhtml+xml")
                header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Mobile Safari/537.36")
            }
            if (response.status.value !in 200..299) return@withContext Result.Failure("The page returned HTTP ${response.status.value}")
            response.bodyAsText()
        } catch (e: Exception) {
            Log.w("Importer", "Fetch failed", e)
            return@withContext Result.Failure("Couldn't load the page: ${e.message}")
        }
        RecipeHtmlParser.parse(normalized, html)?.let { Result.Success(it) }
            ?: Result.Failure("No recipe data found on that page. The site may not publish schema.org Recipe markup.")
    }

    fun parseHtml(url: String, html: String): Recipe? = RecipeHtmlParser.parse(url, html)
}

/** Pure parser (no network) so it can be unit tested and reused by share-sheet imports. */
object RecipeHtmlParser {
    fun parse(url: String, html: String): Recipe? {
        val doc = Jsoup.parse(html, url)
        val scripts = doc.select("script[type=application/ld+json]")
        for (script in scripts) {
            val raw = script.data().trim()
            if (raw.isEmpty()) continue
            val element = runCatching { AppJson.parseToJsonElement(raw) }.getOrNull() ?: continue
            val recipeObj = findRecipeObject(element) ?: continue
            return toRecipe(url, recipeObj, doc.title())
        }
        return parseMicrodata(url, doc)
    }

    private fun findRecipeObject(element: JsonElement): JsonObject? {
        when (element) {
            is JsonObject -> {
                val types = when (val type = element["@type"]) {
                    is JsonPrimitive -> listOf(type.content)
                    is JsonArray -> type.mapNotNull { (it as? JsonPrimitive)?.content }
                    else -> emptyList()
                }
                if (types.any { it.equals("Recipe", true) }) return element
                (element["@graph"] as? JsonArray)?.firstNotNullOfOrNull { findRecipeObject(it) }?.let { return it }
                element["mainEntity"]?.let { findRecipeObject(it) }?.let { return it }
            }
            is JsonArray -> return element.firstNotNullOfOrNull { findRecipeObject(it) }
            else -> Unit
        }
        return null
    }

    private fun toRecipe(url: String, o: JsonObject, pageTitle: String): Recipe {
        val title = o.text("name") ?: pageTitle.substringBefore(" | ").substringBefore(" - ")
        val ingredients = (o["recipeIngredient"] as? JsonArray ?: o["ingredients"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.content }
            .map { IngredientNormalizer.fromLine(Jsoup.parse(it).text()) }
        val stepTexts = flattenInstructions(o["recipeInstructions"])
        val steps = stepTexts.mapIndexed { i, t -> RecipeStep(i, t, TimerExtractor.extractSeconds(t)) }
        val nutrition = (o["nutrition"] as? JsonObject)?.let { n ->
            Nutrition(
                calories = n.number("calories"),
                proteinG = n.number("proteinContent"),
                carbsG = n.number("carbohydrateContent"),
                fatG = n.number("fatContent"),
                saturatedFatG = n.number("saturatedFatContent"),
                fibreG = n.number("fiberContent"),
                sugarG = n.number("sugarContent"),
                sodiumMg = n.number("sodiumContent"),
            )
        }?.takeIf { !it.isEmpty }
        val prep = o.text("prepTime")?.let { isoMinutes(it) }
        val cook = o.text("cookTime")?.let { isoMinutes(it) }
        val total = o.text("totalTime")?.let { isoMinutes(it) }
        val servings = when (val y = o["recipeYield"]) {
            is JsonPrimitive -> y.intOrNull ?: y.content.replace(Regex("[^0-9]"), "").take(2).toIntOrNull()
            is JsonArray -> y.firstNotNullOfOrNull { (it as? JsonPrimitive)?.content?.replace(Regex("[^0-9]"), "")?.take(2)?.toIntOrNull() }
            else -> null
        }
        val image = when (val img = o["image"]) {
            is JsonPrimitive -> img.content
            is JsonObject -> img.text("url")
            is JsonArray -> img.firstNotNullOfOrNull { (it as? JsonPrimitive)?.content ?: (it as? JsonObject)?.text("url") }
            else -> null
        }
        val author = when (val a = o["author"]) {
            is JsonPrimitive -> a.content
            is JsonObject -> a.text("name")
            is JsonArray -> (a.firstOrNull() as? JsonObject)?.text("name") ?: (a.firstOrNull() as? JsonPrimitive)?.content
            else -> null
        } ?: hostOf(url)
        val cuisine = o.listOfText("recipeCuisine").firstOrNull()
        val category = o.listOfText("recipeCategory").firstOrNull()
        val keywords = o.text("keywords")?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
        val dietUrls = o.listOfText("suitableForDiet").map {
            it.substringAfterLast('/').replace("Diet", "").replace(Regex("([a-z])([A-Z])"), "$1 $2")
        }
        val tags = (keywords + dietUrls).distinct()
        val video = (o["video"] as? JsonObject)?.let { it.text("contentUrl") ?: it.text("embedUrl") }
        val sourceId = sha1(url).take(16)
        val effectiveTotal = total ?: listOfNotNull(prep, cook).takeIf { it.isNotEmpty() }?.sum()
        return Recipe(
            id = Recipe.makeId(RecipeSource.WEB_IMPORT, sourceId),
            source = RecipeSource.WEB_IMPORT,
            sourceId = sourceId,
            title = Jsoup.parse(title).text(),
            description = o.text("description")?.let { Jsoup.parse(it).text() },
            imageUrl = image,
            videoUrl = video,
            sourceUrl = url,
            author = author,
            cuisine = cuisine,
            category = category,
            mealTypes = IngredientNormalizer.guessMealTypes(category, title, tags),
            tags = tags,
            dietTags = DietTagger.tag(ingredients, nutrition, effectiveTotal, tags),
            servings = servings,
            prepMinutes = prep,
            cookMinutes = cook,
            totalMinutes = total,
            ingredients = ingredients,
            steps = steps,
            nutrition = nutrition,
            rating = (o["aggregateRating"] as? JsonObject)?.number("ratingValue"),
            cachedAt = System.currentTimeMillis(),
            isComplete = true,
        )
    }

    private fun flattenInstructions(el: JsonElement?): List<String> = when (el) {
        null, is JsonNull -> emptyList()
        is JsonPrimitive -> el.content.replace(Regex("<[^>]+>"), "\n").split(Regex("\n+")).map { it.trim() }.filter { it.isNotBlank() }
        is JsonArray -> el.flatMap { flattenInstructions(it) }
        is JsonObject -> {
            val type = (el["@type"] as? JsonPrimitive)?.content
            if (type.equals("HowToSection", true)) flattenInstructions(el["itemListElement"])
            else listOfNotNull(el.text("text") ?: el.text("name")).map { Jsoup.parse(it).text() }
        }
    }

    private fun parseMicrodata(url: String, doc: Document): Recipe? {
        val scope = doc.selectFirst("[itemtype*=schema.org/Recipe]") ?: return null
        val name = scope.selectFirst("[itemprop=name]")?.text() ?: doc.title()
        val ingredients = scope.select("[itemprop=recipeIngredient], [itemprop=ingredients]").map { IngredientNormalizer.fromLine(it.text()) }
        val steps = scope.select("[itemprop=recipeInstructions]").flatMap { el ->
            val items = el.select("li").map { it.text() }
            if (items.isNotEmpty()) items else listOf(el.text())
        }.filter { it.isNotBlank() }.mapIndexed { i, t -> RecipeStep(i, t, TimerExtractor.extractSeconds(t)) }
        if (ingredients.isEmpty() && steps.isEmpty()) return null
        val sourceId = sha1(url).take(16)
        return Recipe(
            id = Recipe.makeId(RecipeSource.WEB_IMPORT, sourceId),
            source = RecipeSource.WEB_IMPORT,
            sourceId = sourceId,
            title = name,
            imageUrl = microdataImage(scope),
            sourceUrl = url,
            author = hostOf(url),
            ingredients = ingredients,
            steps = steps,
            dietTags = DietTagger.tag(ingredients, null, null),
            mealTypes = IngredientNormalizer.guessMealTypes(null, name, emptyList()),
            cachedAt = System.currentTimeMillis(),
        )
    }

    private fun microdataImage(scope: org.jsoup.nodes.Element): String? {
        val el: org.jsoup.nodes.Element = scope.selectFirst("[itemprop=image]") ?: return null
        val src: String = el.attr("abs:src")
        val content: String = el.attr("abs:content")
        return src.ifBlank { content }.ifBlank { null }
    }

    private fun hostOf(url: String): String? = runCatching { URI(url).host?.removePrefix("www.") }.getOrNull()

    private fun JsonObject.text(key: String): String? = when (val v = this[key]) {
        is JsonPrimitive -> v.content.takeIf { it.isNotBlank() }
        is JsonArray -> (v.firstOrNull() as? JsonPrimitive)?.content
        is JsonObject -> (v["@value"] as? JsonPrimitive)?.content
        else -> null
    }

    private fun JsonObject.listOfText(key: String): List<String> = when (val v = this[key]) {
        is JsonPrimitive -> v.content.split(',').map { it.trim() }.filter { it.isNotBlank() }
        is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.content }
        else -> emptyList()
    }

    private fun JsonObject.number(key: String): Double? = (this[key] as? JsonPrimitive)?.let { p ->
        p.doubleOrNull ?: Regex("""[0-9]+(?:\.[0-9]+)?""").find(p.content)?.value?.toDoubleOrNull()
    }

    private fun isoMinutes(iso: String): Int? {
        Regex("""P(?:(\d+)D)?T?(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?""").matchEntire(iso.trim())?.let { m ->
            val d = m.groupValues[1].toIntOrNull() ?: 0
            val h = m.groupValues[2].toIntOrNull() ?: 0
            val min = m.groupValues[3].toIntOrNull() ?: 0
            return (d * 24 * 60 + h * 60 + min).takeIf { it > 0 }
        }
        return TimerExtractor.extractSeconds(iso)?.let { it / 60 }
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
