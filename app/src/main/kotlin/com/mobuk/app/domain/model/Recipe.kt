package com.mobuk.app.domain.model

import kotlinx.serialization.Serializable

/** Where a recipe came from. The id of a recipe is always "<source>:<sourceId>". */
@Serializable
enum class RecipeSource(val label: String, val attribution: String) {
    THEMEALDB("TheMealDB", "Recipe data from TheMealDB (themealdb.com)"),
    MYPLATE("MyPlate Kitchen", "USDA MyPlate Kitchen recipe (public domain) via myplate.food"),
    SPOONACULAR("Spoonacular", "Recipe data from Spoonacular"),
    WEB_IMPORT("Imported", "Imported from the web using schema.org Recipe data"),
    MCP("MCP server", "Recipe supplied by a connected MCP server"),
    USER("You", "Created on this device"),
}

@Serializable
enum class DietTag(val label: String) {
    VEGETARIAN("Veggie"),
    VEGAN("Vegan"),
    PESCATARIAN("Pescatarian"),
    GLUTEN_FREE("Gluten free"),
    DAIRY_FREE("Dairy free"),
    NUT_FREE("Nut free"),
    HIGH_PROTEIN("High protein"),
    LOW_CALORIE("Lighter"),
    QUICK("Quick"),
}

@Serializable
enum class MealType(val label: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACK("Snack"),
    DESSERT("Dessert"),
    SIDE("Side"),
    DRINK("Drink"),
}

/** Supermarket aisles used to group the shopping list, in the order a UK shop is usually laid out. */
@Serializable
enum class Aisle(val label: String) {
    PRODUCE("Fruit & veg"),
    MEAT_FISH("Meat & fish"),
    DAIRY_EGGS("Dairy & eggs"),
    BAKERY("Bakery"),
    CHILLED("Chilled"),
    FROZEN("Frozen"),
    TINS_JARS("Tins & jars"),
    PANTRY("Dry goods & pasta"),
    HERBS_SPICES("Herbs & spices"),
    OILS_SAUCES("Oils, sauces & condiments"),
    WORLD("World foods"),
    DRINKS("Drinks"),
    HOUSEHOLD("Household"),
    OTHER("Other"),
}

@Serializable
data class Ingredient(
    /** Cleaned ingredient name, e.g. "chicken thighs". */
    val name: String,
    val quantity: Double? = null,
    val unit: String? = null,
    /** The original line as the source wrote it, e.g. "2 tbsp olive oil". */
    val raw: String,
    val note: String? = null,
    val aisle: Aisle = Aisle.OTHER,
)

@Serializable
data class RecipeStep(
    val index: Int,
    val text: String,
    /** A timer detected in the text (e.g. "simmer for 20 minutes"), in seconds. */
    val timerSeconds: Int? = null,
)

@Serializable
data class Nutrition(
    val calories: Double? = null,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
    val saturatedFatG: Double? = null,
    val fibreG: Double? = null,
    val sugarG: Double? = null,
    val sodiumMg: Double? = null,
    val perServing: Boolean = true,
    /** True when the numbers came from the on-device model rather than the source. */
    val isEstimate: Boolean = false,
) {
    val isEmpty: Boolean
        get() = listOf(calories, proteinG, carbsG, fatG).all { it == null }
}

@Serializable
data class Recipe(
    val id: String,
    val source: RecipeSource,
    val sourceId: String,
    val title: String,
    val description: String? = null,
    val imageUrl: String? = null,
    val videoUrl: String? = null,
    val sourceUrl: String? = null,
    val author: String? = null,
    val cuisine: String? = null,
    val category: String? = null,
    val mealTypes: List<MealType> = emptyList(),
    val tags: List<String> = emptyList(),
    val dietTags: Set<DietTag> = emptySet(),
    val servings: Int? = null,
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    val totalMinutes: Int? = null,
    val ingredients: List<Ingredient> = emptyList(),
    val steps: List<RecipeStep> = emptyList(),
    val nutrition: Nutrition? = null,
    val rating: Double? = null,
    /** Epoch millis when this recipe was first cached on the device. */
    val cachedAt: Long = 0L,
    /** Full details (steps, ingredients) have been fetched; list endpoints often return only summaries. */
    val isComplete: Boolean = true,
) {
    val effectiveMinutes: Int?
        get() = totalMinutes ?: listOfNotNull(prepMinutes, cookMinutes).takeIf { it.isNotEmpty() }?.sum()

    companion object {
        fun makeId(source: RecipeSource, sourceId: String) = "${source.name.lowercase()}:$sourceId"
    }
}

/** A lightweight card used in lists and feeds. */
@Serializable
data class RecipeSummary(
    val id: String,
    val title: String,
    val imageUrl: String?,
    val source: RecipeSource,
    val cuisine: String? = null,
    val category: String? = null,
    val totalMinutes: Int? = null,
    val dietTags: Set<DietTag> = emptySet(),
    val calories: Double? = null,
) {
    companion object {
        fun from(recipe: Recipe) = RecipeSummary(
            id = recipe.id,
            title = recipe.title,
            imageUrl = recipe.imageUrl,
            source = recipe.source,
            cuisine = recipe.cuisine,
            category = recipe.category,
            totalMinutes = recipe.effectiveMinutes,
            dietTags = recipe.dietTags,
            calories = recipe.nutrition?.calories,
        )
    }
}
