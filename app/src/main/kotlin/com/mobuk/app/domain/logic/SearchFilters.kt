package com.mobuk.app.domain.logic

import com.mobuk.app.domain.model.DietTag
import com.mobuk.app.domain.model.MealType
import com.mobuk.app.domain.model.Recipe
import com.mobuk.app.domain.model.RecipeSummary
import kotlinx.serialization.Serializable

@Serializable
enum class SortOrder(val label: String) {
    RELEVANCE("Relevance"),
    QUICKEST("Quickest"),
    LOWEST_CALORIES("Lowest calories"),
    HIGHEST_PROTEIN("Highest protein"),
    A_TO_Z("A to Z"),
}

@Serializable
data class SearchFilters(
    val query: String = "",
    val diets: Set<DietTag> = emptySet(),
    val cuisines: Set<String> = emptySet(),
    val categories: Set<String> = emptySet(),
    val mealTypes: Set<MealType> = emptySet(),
    val maxMinutes: Int? = null,
    /** "What's in my fridge" search: every recipe must use at least one of these. */
    val ingredients: List<String> = emptyList(),
    val excludeIngredients: Set<String> = emptySet(),
    val maxCalories: Int? = null,
    val sort: SortOrder = SortOrder.RELEVANCE,
) {
    val isEmpty: Boolean
        get() = query.isBlank() && diets.isEmpty() && cuisines.isEmpty() && categories.isEmpty() &&
            mealTypes.isEmpty() && maxMinutes == null && ingredients.isEmpty() && excludeIngredients.isEmpty() && maxCalories == null

    val activeCount: Int
        get() = diets.size + cuisines.size + categories.size + mealTypes.size + (if (maxMinutes != null) 1 else 0) +
            ingredients.size + excludeIngredients.size + (if (maxCalories != null) 1 else 0)

    fun matches(recipe: Recipe): Boolean {
        if (diets.isNotEmpty() && !recipe.dietTags.containsAll(diets)) return false
        if (cuisines.isNotEmpty() && recipe.cuisine?.let { c -> cuisines.any { it.equals(c, true) } } != true) return false
        if (categories.isNotEmpty() && recipe.category?.let { c -> categories.any { it.equals(c, true) } } != true) return false
        if (mealTypes.isNotEmpty() && recipe.mealTypes.none { it in mealTypes }) return false
        val minutes = recipe.effectiveMinutes
        if (maxMinutes != null && minutes != null && minutes > maxMinutes) return false
        if (maxCalories != null) {
            val cal = recipe.nutrition?.calories
            if (cal != null && cal > maxCalories) return false
        }
        if (excludeIngredients.isNotEmpty()) {
            val names = recipe.ingredients.map { it.name.lowercase() + " " + it.raw.lowercase() }
            if (excludeIngredients.any { ex -> names.any { it.contains(ex.lowercase()) } }) return false
        }
        if (ingredients.isNotEmpty() && recipe.ingredients.isNotEmpty()) {
            val names = recipe.ingredients.map { it.name.lowercase() + " " + it.raw.lowercase() }
            if (ingredients.none { ing -> names.any { it.contains(ing.lowercase()) } }) return false
        }
        if (query.isNotBlank()) {
            val q = query.lowercase()
            val hay = buildString {
                append(recipe.title.lowercase()); append(' ')
                append(recipe.cuisine.orEmpty().lowercase()); append(' ')
                append(recipe.category.orEmpty().lowercase()); append(' ')
                append(recipe.tags.joinToString(" ").lowercase()); append(' ')
                append(recipe.ingredients.joinToString(" ") { it.name }.lowercase())
            }
            if (q.split(' ').filter { it.isNotBlank() }.any { !hay.contains(it) }) return false
        }
        return true
    }

    fun sorted(recipes: List<Recipe>): List<Recipe> = when (sort) {
        SortOrder.RELEVANCE -> recipes
        SortOrder.QUICKEST -> recipes.sortedWith(compareBy(nullsLast()) { it.effectiveMinutes })
        SortOrder.LOWEST_CALORIES -> recipes.sortedWith(compareBy(nullsLast()) { it.nutrition?.calories })
        SortOrder.HIGHEST_PROTEIN -> recipes.sortedWith(compareByDescending(nullsLast()) { it.nutrition?.proteinG })
        SortOrder.A_TO_Z -> recipes.sortedBy { it.title.lowercase() }
    }

    fun sortedSummaries(recipes: List<RecipeSummary>): List<RecipeSummary> = when (sort) {
        SortOrder.RELEVANCE -> recipes
        SortOrder.QUICKEST -> recipes.sortedWith(compareBy(nullsLast()) { it.totalMinutes })
        SortOrder.LOWEST_CALORIES -> recipes.sortedWith(compareBy(nullsLast()) { it.calories })
        SortOrder.HIGHEST_PROTEIN -> recipes
        SortOrder.A_TO_Z -> recipes.sortedBy { it.title.lowercase() }
    }
}
