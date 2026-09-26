package com.mobuk.app.data.remote.myplate

import android.util.Log
import com.mobuk.app.data.local.AppJson
import com.mobuk.app.data.remote.BrowseFacets
import com.mobuk.app.data.remote.RecipeProvider
import com.mobuk.app.domain.logic.DietTagger
import com.mobuk.app.domain.logic.IngredientNormalizer
import com.mobuk.app.domain.logic.SearchFilters
import com.mobuk.app.domain.logic.TimerExtractor
import com.mobuk.app.domain.model.DietTag
import com.mobuk.app.domain.model.MealType
import com.mobuk.app.domain.model.Nutrition
import com.mobuk.app.domain.model.Recipe
import com.mobuk.app.domain.model.RecipeSource
import com.mobuk.app.domain.model.RecipeStep
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
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

/**
 * MyPlate.food: keyless JSON API over the 1,072 USDA MyPlate Kitchen recipes (US federal works, public domain)
 * with full nutrition tables. Base URL https://myplate.food/api/v1 — see https://myplate.food/api.
 *
 * The API is documented by an OpenAPI file rather than fixed field names, so this mapper reads defensively:
 * it looks for the common spellings of each field and never fails on an unknown shape.
 */
class MyPlateProvider(private val client: HttpClient) : RecipeProvider {

    override val source = RecipeSource.MYPLATE
    private val base = "https://myplate.food/api/v1"

    override suspend fun isAvailable(): Boolean = true

    override suspend fun search(filters: SearchFilters, limit: Int): List<Recipe> = safe {
        val params = mutableListOf<Pair<String, String>>()
        val query = listOf(filters.query.trim(), filters.ingredients.joinToString(" ")).filter { it.isNotBlank() }.joinToString(" ")
        if (query.isNotBlank()) params += "q" to query
        filters.maxCalories?.let { params += "max_calories" to it.toString() }
        if (filters.mealTypes.contains(MealType.BREAKFAST)) params += "category" to "breakfast"
        if (filters.diets.contains(DietTag.VEGETARIAN) || filters.diets.contains(DietTag.VEGAN)) params += "food_group" to "vegetables"
        params += "limit" to limit.coerceIn(1, 50).toString()
        fetchList("$base/recipes", params)
    }

    override suspend fun getRecipe(sourceId: String): Recipe? = safe {
        val text = getText("$base/recipes/$sourceId", emptyList()) ?: return@safe emptyList()
        val root = AppJson.parseToJsonElement(text)
        val obj = (root as? JsonObject)?.let { it["recipe"] as? JsonObject ?: it["data"] as? JsonObject ?: it } ?: return@safe emptyList()
        listOfNotNull(toRecipe(obj, complete = true))
    }.firstOrNull()

    override suspend fun discover(limit: Int): List<Recipe> = safe {
        // The API has no random endpoint; rotate through a few broad queries for variety.
        val seeds = listOf("chicken", "vegetable", "bean", "rice", "salad", "soup", "pasta", "fish", "egg", "fruit", "pork", "beef")
        val seed = seeds[((System.currentTimeMillis() / 3_600_000L) % seeds.size).toInt()]
        fetchList("$base/recipes", listOf("q" to seed, "limit" to limit.toString()))
    }

    override suspend fun facets(): BrowseFacets = BrowseFacets(
        categories = listOf("Breakfast", "Lunch", "Dinner", "Snacks", "Desserts", "Salads", "Soups", "Beverages"),
    )

    override suspend fun byIngredient(ingredient: String, limit: Int): List<Recipe> = safe {
        fetchList("$base/recipes", listOf("q" to ingredient, "limit" to limit.toString()))
    }

    override suspend fun byCategory(category: String, limit: Int): List<Recipe> = safe {
        fetchList("$base/recipes", listOf("category" to category.lowercase(), "limit" to limit.toString()))
    }

    private suspend fun fetchList(url: String, params: List<Pair<String, String>>): List<Recipe> {
        val text = getText(url, params) ?: return emptyList()
        val root = AppJson.parseToJsonElement(text)
        val items: JsonArray = when (root) {
            is JsonArray -> root
            is JsonObject -> (root["recipes"] ?: root["results"] ?: root["data"] ?: root["items"]) as? JsonArray ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }
        return items.mapNotNull { (it as? JsonObject)?.let { obj -> toRecipe(obj, complete = false) } }
    }

    private suspend fun getText(url: String, params: List<Pair<String, String>>): String? = withContext(Dispatchers.IO) {
        val response = client.get(url) { params.forEach { (k, v) -> parameter(k, v) } }
        if (response.status.value !in 200..299) null else response.bodyAsText()
    }

    internal fun toRecipe(o: JsonObject, complete: Boolean): Recipe? {
        val id = o.first("id", "slug", "recipe_id")?.asString() ?: return null
        val title = o.first("title", "name")?.asString() ?: return null
        val ingredientsEl = o.first("ingredients", "ingredient_lines")
        val ingredients = when (ingredientsEl) {
            is JsonArray -> ingredientsEl.mapNotNull { el ->
                when (el) {
                    is JsonPrimitive -> IngredientNormalizer.fromLine(el.content)
                    is JsonObject -> {
                        val line = el.first("text", "original", "line", "display")?.asString()
                        val name = el.first("name", "ingredient", "food")?.asString()
                        val qty = el.first("quantity", "amount")?.asString()
                        val unit = el.first("unit", "measure")?.asString()
                        when {
                            line != null -> IngredientNormalizer.fromLine(line)
                            name != null -> IngredientNormalizer.fromNameAndMeasure(name, listOfNotNull(qty, unit).joinToString(" "))
                            else -> null
                        }
                    }
                    else -> null
                }
            }
            else -> emptyList()
        }
        val directionsEl = o.first("directions", "instructions", "steps", "method")
        val stepTexts: List<String> = when (directionsEl) {
            is JsonArray -> directionsEl.mapNotNull { el ->
                when (el) {
                    is JsonPrimitive -> el.content
                    is JsonObject -> el.first("text", "step", "instruction", "description")?.asString()
                    else -> null
                }
            }
            is JsonPrimitive -> directionsEl.content.split(Regex("\n+")).map { it.trim() }.filter { it.isNotBlank() }
            else -> emptyList()
        }
        val steps = stepTexts.mapIndexed { i, t -> RecipeStep(i, t.replace(Regex("""^\d+[.)]\s*"""), ""), TimerExtractor.extractSeconds(t)) }
        val nutritionObj = o.first("nutrition", "nutrients", "nutrition_facts") as? JsonObject
        val nutrition = nutritionObj?.let { n ->
            Nutrition(
                calories = n.num("calories", "energy", "kcal", "total_calories"),
                proteinG = n.num("protein", "protein_g"),
                carbsG = n.num("carbohydrates", "carbohydrate", "carbs", "total_carbohydrate"),
                fatG = n.num("total_fat", "fat", "fat_g"),
                saturatedFatG = n.num("saturated_fat", "sat_fat"),
                fibreG = n.num("dietary_fiber", "fiber", "fibre"),
                sugarG = n.num("total_sugars", "sugars", "sugar"),
                sodiumMg = n.num("sodium", "sodium_mg"),
            )
        } ?: o.num("calories")?.let { Nutrition(calories = it) }
        val servings = o.first("servings", "yield", "makes")?.asInt()
        val prep = o.first("prep_time", "prep_minutes", "prepTime")?.asMinutes()
        val cook = o.first("cook_time", "cook_minutes", "cookTime")?.asMinutes()
        val total = o.first("total_time", "total_minutes", "totalTime")?.asMinutes()
        val image = o.first("image", "image_url", "photo", "thumbnail")?.let { el ->
            when (el) {
                is JsonObject -> el.first("url", "src", "large", "medium")?.asString()
                else -> el.asString()
            }
        }
        val category = o.first("category", "course", "meal_type")?.asString()
        val tags = (o.first("tags", "food_groups", "foodGroups") as? JsonArray)?.mapNotNull { el ->
            (el as? JsonPrimitive)?.content ?: (el as? JsonObject)?.first("name", "label")?.asString()
        }.orEmpty()
        val description = o.first("description", "summary", "notes")?.asString()
        val sourceUrl = o.first("canonical_url", "url", "source_url")?.asString()
        val cuisine = o.first("cuisine")?.asString()
        val effectiveTotal = total ?: listOfNotNull(prep, cook).takeIf { it.isNotEmpty() }?.sum()
        return Recipe(
            id = Recipe.makeId(source, id),
            source = source,
            sourceId = id,
            title = title,
            description = description,
            imageUrl = image,
            sourceUrl = sourceUrl ?: "https://myplate.food/recipes/$id",
            author = "USDA MyPlate Kitchen",
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
            rating = o.num("rating", "average_rating"),
            cachedAt = System.currentTimeMillis(),
            isComplete = complete || (ingredients.isNotEmpty() && steps.isNotEmpty()),
        )
    }

    private fun JsonObject.first(vararg keys: String): JsonElement? =
        keys.firstNotNullOfOrNull { k -> this[k]?.takeIf { it !is JsonNull } }

    private fun JsonObject.num(vararg keys: String): Double? = first(*keys)?.let { el ->
        when (el) {
            is JsonPrimitive -> el.doubleOrNull ?: el.content.replace(Regex("[^0-9.]"), "").toDoubleOrNull()
            is JsonObject -> el.first("value", "amount", "quantity")?.let { (it as? JsonPrimitive)?.doubleOrNull }
            else -> null
        }
    }

    private fun JsonElement.asString(): String? = when (this) {
        is JsonPrimitive -> content.takeIf { it.isNotBlank() }
        is JsonObject -> this["name"]?.let { (it as? JsonPrimitive)?.content } ?: this["text"]?.let { (it as? JsonPrimitive)?.content }
        is JsonArray -> firstOrNull()?.let { (it as? JsonPrimitive)?.content }
    }

    private fun JsonElement.asInt(): Int? = when (this) {
        is JsonPrimitive -> intOrNull ?: content.replace(Regex("[^0-9]"), "").toIntOrNull()
        is JsonObject -> this["value"]?.let { (it as? JsonPrimitive)?.intOrNull }
        else -> null
    }

    /** Accepts 25, "25", "25 minutes", "PT25M", "1 hour 10 minutes". */
    private fun JsonElement.asMinutes(): Int? {
        val prim = this as? JsonPrimitive ?: return (this as? JsonObject)?.get("value")?.let { (it as? JsonPrimitive)?.intOrNull }
        prim.intOrNull?.let { return it }
        val text = prim.content
        Regex("""PT(?:(\d+)H)?(?:(\d+)M)?""").matchEntire(text)?.let { m ->
            val h = m.groupValues[1].toIntOrNull() ?: 0
            val mm = m.groupValues[2].toIntOrNull() ?: 0
            return (h * 60 + mm).takeIf { it > 0 }
        }
        return TimerExtractor.extractSeconds(text)?.let { it / 60 }
    }

    private suspend fun <T> safe(block: suspend () -> List<T>): List<T> = try {
        block()
    } catch (e: Exception) {
        Log.w("MyPlate", "Request failed: ${e.message}")
        emptyList()
    }
}
