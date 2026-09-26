package com.mobuk.app.data.remote.spoonacular

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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Spoonacular (https://spoonacular.com/food-api): rich recipe search with diet/intolerance filters,
 * nutrition and analysed instructions. Needs a user supplied API key (free tier ~150 points/day).
 */
class SpoonacularProvider(
    private val client: HttpClient,
    private val apiKeyProvider: suspend () -> String,
) : RecipeProvider {

    override val source = RecipeSource.SPOONACULAR
    private val base = "https://api.spoonacular.com"

    override suspend fun isAvailable(): Boolean = apiKeyProvider().isNotBlank()

    override suspend fun search(filters: SearchFilters, limit: Int): List<Recipe> = safe {
        val key = apiKeyProvider()
        if (key.isBlank()) return@safe emptyList()
        val params = mutableListOf(
            "apiKey" to key, "number" to limit.coerceIn(1, 50).toString(), "addRecipeInformation" to "true",
            "fillIngredients" to "true", "addRecipeNutrition" to "true",
        )
        if (filters.query.isNotBlank()) params += "query" to filters.query.trim()
        if (filters.ingredients.isNotEmpty()) params += "includeIngredients" to filters.ingredients.joinToString(",")
        if (filters.excludeIngredients.isNotEmpty()) params += "excludeIngredients" to filters.excludeIngredients.joinToString(",")
        filters.cuisines.firstOrNull()?.let { params += "cuisine" to it }
        filters.maxMinutes?.let { params += "maxReadyTime" to it.toString() }
        filters.maxCalories?.let { params += "maxCalories" to it.toString() }
        val diet = when {
            DietTag.VEGAN in filters.diets -> "vegan"
            DietTag.VEGETARIAN in filters.diets -> "vegetarian"
            DietTag.PESCATARIAN in filters.diets -> "pescetarian"
            else -> null
        }
        diet?.let { params += "diet" to it }
        val intolerances = buildList {
            if (DietTag.GLUTEN_FREE in filters.diets) add("gluten")
            if (DietTag.DAIRY_FREE in filters.diets) add("dairy")
            if (DietTag.NUT_FREE in filters.diets) { add("tree nut"); add("peanut") }
        }
        if (intolerances.isNotEmpty()) params += "intolerances" to intolerances.joinToString(",")
        filters.mealTypes.firstOrNull()?.let {
            params += "type" to when (it) {
                MealType.BREAKFAST -> "breakfast"
                MealType.LUNCH, MealType.DINNER -> "main course"
                MealType.SNACK -> "snack"
                MealType.DESSERT -> "dessert"
                MealType.SIDE -> "side dish"
                MealType.DRINK -> "drink"
            }
        }
        val text = getText("$base/recipes/complexSearch", params) ?: return@safe emptyList()
        val root = AppJson.parseToJsonElement(text).jsonObject
        (root["results"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let { o -> toRecipe(o) } }
    }

    override suspend fun getRecipe(sourceId: String): Recipe? = safe {
        val key = apiKeyProvider()
        if (key.isBlank()) return@safe emptyList()
        val text = getText("$base/recipes/$sourceId/information", listOf("apiKey" to key, "includeNutrition" to "true")) ?: return@safe emptyList()
        listOfNotNull(toRecipe(AppJson.parseToJsonElement(text).jsonObject))
    }.firstOrNull()

    override suspend fun discover(limit: Int): List<Recipe> = safe {
        val key = apiKeyProvider()
        if (key.isBlank()) return@safe emptyList()
        val text = getText("$base/recipes/random", listOf("apiKey" to key, "number" to limit.coerceIn(1, 20).toString(), "includeNutrition" to "true")) ?: return@safe emptyList()
        val root = AppJson.parseToJsonElement(text).jsonObject
        (root["recipes"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let { o -> toRecipe(o) } }
    }

    override suspend fun facets(): BrowseFacets = BrowseFacets(
        cuisines = listOf("African", "American", "British", "Cajun", "Caribbean", "Chinese", "Eastern European", "European", "French", "German", "Greek", "Indian", "Irish", "Italian", "Japanese", "Jewish", "Korean", "Latin American", "Mediterranean", "Mexican", "Middle Eastern", "Nordic", "Southern", "Spanish", "Thai", "Vietnamese"),
        categories = listOf("Main course", "Side dish", "Dessert", "Appetizer", "Salad", "Bread", "Breakfast", "Soup", "Beverage", "Sauce", "Snack", "Drink"),
    )

    override suspend fun byIngredient(ingredient: String, limit: Int): List<Recipe> =
        search(SearchFilters(ingredients = listOf(ingredient)), limit)

    override suspend fun byCuisine(cuisine: String, limit: Int): List<Recipe> =
        search(SearchFilters(cuisines = setOf(cuisine)), limit)

    override suspend fun byCategory(category: String, limit: Int): List<Recipe> =
        search(SearchFilters(query = category), limit)

    private suspend fun getText(url: String, params: List<Pair<String, String>>): String? = withContext(Dispatchers.IO) {
        val response = client.get(url) { params.forEach { (k, v) -> parameter(k, v) } }
        when (response.status.value) {
            in 200..299 -> response.bodyAsText()
            401, 402 -> { Log.w("Spoonacular", "API key rejected or quota exhausted (${response.status.value})"); null }
            else -> null
        }
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.dbl(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull
    private fun JsonObject.bool(key: String): Boolean = (this[key] as? JsonPrimitive)?.content == "true"
    private fun JsonObject.strList(key: String): List<String> = (this[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }

    private fun toRecipe(o: JsonObject): Recipe? {
        val id = o.str("id") ?: return null
        val title = o.str("title") ?: return null
        val ingredients = (o["extendedIngredients"] as? JsonArray).orEmpty().mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val original = obj.str("original") ?: obj.str("originalString")
            val name = obj.str("nameClean") ?: obj.str("name")
            val amount = obj.dbl("amount")
            val unit = obj.str("unit")
            when {
                name != null -> {
                    val base = IngredientNormalizer.fromNameAndMeasure(name, listOfNotNull(amount?.toString(), unit).joinToString(" "))
                    base.copy(raw = original ?: base.raw, quantity = amount ?: base.quantity, unit = unit ?: base.unit)
                }
                original != null -> IngredientNormalizer.fromLine(original)
                else -> null
            }
        }
        val stepTexts = (o["analyzedInstructions"] as? JsonArray).orEmpty().flatMap { section ->
            ((section as? JsonObject)?.get("steps") as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.str("step") }
        }.ifEmpty {
            o.str("instructions")?.let { html ->
                html.replace(Regex("<[^>]+>"), "\n").split(Regex("\n+")).map { it.trim() }.filter { it.isNotBlank() }
            }.orEmpty()
        }
        val steps = stepTexts.mapIndexed { i, t -> RecipeStep(i, t, TimerExtractor.extractSeconds(t)) }
        val nutrients = ((o["nutrition"] as? JsonObject)?.get("nutrients") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        fun nutrient(name: String) = nutrients.firstOrNull { it.str("name").equals(name, true) }?.dbl("amount")
        val nutrition = if (nutrients.isEmpty()) null else Nutrition(
            calories = nutrient("Calories"), proteinG = nutrient("Protein"), carbsG = nutrient("Carbohydrates"), fatG = nutrient("Fat"),
            saturatedFatG = nutrient("Saturated Fat"), fibreG = nutrient("Fiber"), sugarG = nutrient("Sugar"), sodiumMg = nutrient("Sodium"),
        )
        val cuisines = o.strList("cuisines")
        val dishTypes = o.strList("dishTypes")
        val diets = o.strList("diets")
        val ready = o.int("readyInMinutes")
        val tags = diets + dishTypes + (if (o.bool("veryHealthy")) listOf("healthy") else emptyList())
        val inferred = DietTagger.tag(ingredients, nutrition, ready, tags).toMutableSet()
        if (o.bool("vegetarian")) inferred += setOf(DietTag.VEGETARIAN, DietTag.PESCATARIAN)
        if (o.bool("vegan")) inferred += DietTag.VEGAN
        if (o.bool("glutenFree")) inferred += DietTag.GLUTEN_FREE
        if (o.bool("dairyFree")) inferred += DietTag.DAIRY_FREE
        val summary = o.str("summary")?.replace(Regex("<[^>]+>"), "")?.take(400)
        val category = dishTypes.firstOrNull()?.replaceFirstChar { it.uppercase() }
        return Recipe(
            id = Recipe.makeId(source, id),
            source = source,
            sourceId = id,
            title = title,
            description = summary,
            imageUrl = o.str("image"),
            sourceUrl = o.str("sourceUrl") ?: o.str("spoonacularSourceUrl"),
            author = o.str("sourceName") ?: o.str("creditsText") ?: "Spoonacular",
            cuisine = cuisines.firstOrNull(),
            category = category,
            mealTypes = IngredientNormalizer.guessMealTypes(category, title, dishTypes),
            tags = tags,
            dietTags = inferred,
            servings = o.int("servings"),
            prepMinutes = o.int("preparationMinutes")?.takeIf { it > 0 },
            cookMinutes = o.int("cookingMinutes")?.takeIf { it > 0 },
            totalMinutes = ready,
            ingredients = ingredients,
            steps = steps,
            nutrition = nutrition,
            rating = o.dbl("spoonacularScore")?.let { it / 20.0 },
            cachedAt = System.currentTimeMillis(),
            isComplete = ingredients.isNotEmpty() && steps.isNotEmpty(),
        )
    }

    private suspend fun <T> safe(block: suspend () -> List<T>): List<T> = try {
        block()
    } catch (e: Exception) {
        Log.w("Spoonacular", "Request failed: ${e.message}")
        emptyList()
    }
}
