package com.mobuk.app.data.remote

import com.mobuk.app.domain.logic.SearchFilters
import com.mobuk.app.domain.model.Recipe
import com.mobuk.app.domain.model.RecipeSource

/** Browse facets a provider can offer (cuisines, categories, ingredients). */
data class BrowseFacets(
    val cuisines: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val ingredients: List<String> = emptyList(),
)

/**
 * A live recipe source. Every provider returns real, third-party recipes; nothing is generated.
 * Implementations must be safe to call concurrently and must never throw on network errors:
 * they return an empty list and log instead, so one broken source never blanks the whole app.
 */
interface RecipeProvider {
    val source: RecipeSource

    /** False when a required key is missing or the user disabled the source. */
    suspend fun isAvailable(): Boolean

    suspend fun search(filters: SearchFilters, limit: Int = 30): List<Recipe>

    /** Fetch the full recipe. Returns null when the id is unknown or the network is down. */
    suspend fun getRecipe(sourceId: String): Recipe?

    /** Random or featured recipes for the home feed. */
    suspend fun discover(limit: Int = 12): List<Recipe>

    suspend fun facets(): BrowseFacets = BrowseFacets()

    /** Recipes that use one ingredient (fridge search). Summaries are fine; details are fetched on open. */
    suspend fun byIngredient(ingredient: String, limit: Int = 30): List<Recipe> = emptyList()

    suspend fun byCuisine(cuisine: String, limit: Int = 30): List<Recipe> = emptyList()

    suspend fun byCategory(category: String, limit: Int = 30): List<Recipe> = emptyList()
}

class ProviderException(message: String, cause: Throwable? = null) : Exception(message, cause)
