package com.mobuk.app.data.remote.themealdb

import android.util.Log
import com.mobuk.app.data.remote.BrowseFacets
import com.mobuk.app.data.remote.RecipeProvider
import com.mobuk.app.domain.logic.DietTagger
import com.mobuk.app.domain.logic.IngredientNormalizer
import com.mobuk.app.domain.logic.SearchFilters
import com.mobuk.app.domain.logic.TimerExtractor
import com.mobuk.app.domain.model.Recipe
import com.mobuk.app.domain.model.RecipeSource
import com.mobuk.app.domain.model.RecipeStep
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * TheMealDB: free, keyless JSON API of real recipes with photos and YouTube videos.
 * Docs: https://www.themealdb.com/api.php. The public developer key is "1".
 */
class TheMealDbProvider(
    private val client: HttpClient,
    private val apiKey: String = "1",
) : RecipeProvider {

    override val source = RecipeSource.THEMEALDB
    private val base get() = "https://www.themealdb.com/api/json/v1/$apiKey"

    @Serializable
    private data class MealsResponse(val meals: List<JsonObject>? = null)

    @Serializable
    private data class CategoriesResponse(val categories: List<JsonObject>? = null)

    override suspend fun isAvailable(): Boolean = true

    override suspend fun search(filters: SearchFilters, limit: Int): List<Recipe> = safe {
        val query = filters.query.trim()
        val results = linkedMapOf<String, Recipe>()
        if (query.isNotBlank()) {
            // Name search returns full recipes.
            fetchMeals("$base/search.php", "s" to query).forEach { results[it.id] = it }
            // First-letter search helps single characters; ingredient search covers "what's in my fridge".
            if (results.isEmpty()) {
                byIngredient(query, limit).forEach { results[it.id] = it }
            }
        }
        if (filters.ingredients.isNotEmpty()) {
            val lists = coroutineScope { filters.ingredients.take(3).map { async { byIngredient(it, limit) } }.map { it.await() } }
            lists.flatten().forEach { results.putIfAbsent(it.id, it) }
        }
        if (query.isBlank() && filters.ingredients.isEmpty()) {
            val cuisine = filters.cuisines.firstOrNull()
            val category = filters.categories.firstOrNull()
            when {
                cuisine != null -> byCuisine(cuisine, limit).forEach { results[it.id] = it }
                category != null -> byCategory(category, limit).forEach { results[it.id] = it }
                filters.diets.any { it == com.mobuk.app.domain.model.DietTag.VEGAN } -> byCategory("Vegan", limit).forEach { results[it.id] = it }
                filters.diets.any { it == com.mobuk.app.domain.model.DietTag.VEGETARIAN } -> byCategory("Vegetarian", limit).forEach { results[it.id] = it }
                else -> discover(limit).forEach { results[it.id] = it }
            }
        }
        results.values.take(limit)
    }

    override suspend fun getRecipe(sourceId: String): Recipe? = safe {
        listOfNotNull(fetchMeals("$base/lookup.php", "i" to sourceId).firstOrNull())
    }.firstOrNull()

    override suspend fun discover(limit: Int): List<Recipe> = safe {
        // random.php returns one meal; fetch a handful concurrently and de-duplicate.
        coroutineScope {
            (1..limit.coerceIn(1, 12)).map { async { fetchMeals("$base/random.php") } }.map { it.await() }
        }.flatten().distinctBy { it.id }
    }

    override suspend fun facets(): BrowseFacets = safe {
        val areas = fetchList("$base/list.php", "a" to "list", "strArea")
        val categories = fetchList("$base/list.php", "c" to "list", "strCategory")
        val ingredients = fetchList("$base/list.php", "i" to "list", "strIngredient")
        listOf(BrowseFacets(cuisines = areas, categories = categories, ingredients = ingredients))
    }.firstOrNull() ?: BrowseFacets()

    override suspend fun byIngredient(ingredient: String, limit: Int): List<Recipe> = safe {
        fetchSummaries("$base/filter.php", "i" to ingredient.trim().replace(' ', '_')).take(limit)
    }

    override suspend fun byCuisine(cuisine: String, limit: Int): List<Recipe> = safe {
        fetchSummaries("$base/filter.php", "a" to cuisine).take(limit)
    }

    override suspend fun byCategory(category: String, limit: Int): List<Recipe> = safe {
        fetchSummaries("$base/filter.php", "c" to category).take(limit)
    }

    private suspend fun fetchMeals(url: String, vararg params: Pair<String, String>): List<Recipe> = withContext(Dispatchers.IO) {
        val response = client.get(url) { params.forEach { (k, v) -> parameter(k, v) } }
        if (response.status.value !in 200..299) return@withContext emptyList()
        val body: MealsResponse = response.body()
        body.meals.orEmpty().mapNotNull { toRecipe(it) }
    }

    /** filter.php returns only idMeal, strMeal and strMealThumb. */
    private suspend fun fetchSummaries(url: String, vararg params: Pair<String, String>): List<Recipe> = withContext(Dispatchers.IO) {
        val response = client.get(url) { params.forEach { (k, v) -> parameter(k, v) } }
        if (response.status.value !in 200..299) return@withContext emptyList()
        val body: MealsResponse = response.body()
        body.meals.orEmpty().mapNotNull { obj ->
            val id = obj.str("idMeal") ?: return@mapNotNull null
            Recipe(
                id = Recipe.makeId(source, id),
                source = source,
                sourceId = id,
                title = obj.str("strMeal") ?: return@mapNotNull null,
                imageUrl = obj.str("strMealThumb"),
                author = "TheMealDB",
                isComplete = false,
            )
        }
    }

    private suspend fun fetchList(url: String, param: Pair<String, String>, field: String): List<String> = withContext(Dispatchers.IO) {
        val response = client.get(url) { parameter(param.first, param.second) }
        if (response.status.value !in 200..299) return@withContext emptyList()
        val body: MealsResponse = response.body()
        body.meals.orEmpty().mapNotNull { it.str(field) }.filter { it.isNotBlank() }
    }

    private fun toRecipe(m: JsonObject): Recipe? {
        val id = m.str("idMeal") ?: return null
        val title = m.str("strMeal") ?: return null
        val ingredients = (1..20).mapNotNull { i ->
            val name = m.str("strIngredient$i")?.trim().orEmpty()
            val measure = m.str("strMeasure$i")?.trim().orEmpty()
            if (name.isBlank()) null else IngredientNormalizer.fromNameAndMeasure(name, measure)
        }
        val instructions = m.str("strInstructions").orEmpty()
        val steps = splitInstructions(instructions).mapIndexed { i, text ->
            RecipeStep(index = i, text = text, timerSeconds = TimerExtractor.extractSeconds(text))
        }
        val tags = m.str("strTags")?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
        val category = m.str("strCategory")
        val cuisine = m.str("strArea")?.takeIf { it != "Unknown" }
        val srcTags = tags + listOfNotNull(category)
        val dietTags = DietTagger.tag(ingredients, null, null, srcTags)
        return Recipe(
            id = Recipe.makeId(source, id),
            source = source,
            sourceId = id,
            title = title,
            imageUrl = m.str("strMealThumb"),
            videoUrl = m.str("strYoutube")?.takeIf { it.isNotBlank() },
            sourceUrl = m.str("strSource")?.takeIf { it.isNotBlank() },
            author = "TheMealDB",
            cuisine = cuisine,
            category = category,
            mealTypes = IngredientNormalizer.guessMealTypes(category, title, tags),
            tags = tags,
            dietTags = dietTags,
            servings = null,
            ingredients = ingredients,
            steps = steps,
            nutrition = null,
            cachedAt = System.currentTimeMillis(),
            isComplete = true,
        )
    }

    private fun splitInstructions(text: String): List<String> {
        val cleaned = text.replace("\r", "")
        val byLine = cleaned.split(Regex("\n+")).map { it.trim() }.filter { it.isNotBlank() }
            .map { it.replace(Regex("""^(step\s*\d+[:.)-]?\s*|\d+[.)]\s+)""", RegexOption.IGNORE_CASE), "") }
            .filter { it.isNotBlank() }
        if (byLine.size > 1) return byLine
        // One long paragraph: split into sentences and group into readable steps.
        val sentences = cleaned.split(Regex("""(?<=[.!?])\s+(?=[A-Z])""")).map { it.trim() }.filter { it.isNotBlank() }
        if (sentences.size <= 3) return sentences.ifEmpty { listOf(cleaned.trim()) }
        return sentences.chunked(2).map { it.joinToString(" ") }
    }

    private fun JsonObject.str(key: String): String? {
        val el: JsonElement = this[key] ?: return null
        if (el is JsonNull) return null
        return runCatching { el.jsonPrimitive.content }.getOrNull()?.takeIf { it.isNotBlank() && it != "null" }
    }

    private suspend fun <T> safe(block: suspend () -> List<T>): List<T> = try {
        block()
    } catch (e: Exception) {
        Log.w("TheMealDb", "Request failed: ${e.message}")
        emptyList()
    }
}
