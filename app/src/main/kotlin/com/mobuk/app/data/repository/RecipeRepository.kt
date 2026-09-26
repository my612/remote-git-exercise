package com.mobuk.app.data.repository

import android.util.Log
import com.mobuk.app.data.local.QueryCacheEntity
import com.mobuk.app.data.local.RecipeDao
import com.mobuk.app.data.local.QueryCacheDao
import com.mobuk.app.data.local.SearchHistoryDao
import com.mobuk.app.data.local.SearchHistoryEntity
import com.mobuk.app.data.local.toDomain
import com.mobuk.app.data.local.toEntity
import com.mobuk.app.data.local.toSummary
import com.mobuk.app.data.remote.BrowseFacets
import com.mobuk.app.data.remote.RecipeProvider
import com.mobuk.app.data.remote.webimport.RecipeUrlImporter
import com.mobuk.app.domain.logic.SearchFilters
import com.mobuk.app.domain.model.Recipe
import com.mobuk.app.domain.model.RecipeSource
import com.mobuk.app.domain.model.RecipeSummary
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Single entry point for recipes. Fans out to every available [RecipeProvider], merges and de-duplicates the
 * results, and caches everything in Room so lists survive going offline and details open instantly.
 */
class RecipeRepository(
    private val recipeDao: RecipeDao,
    private val queryCacheDao: QueryCacheDao,
    private val searchHistoryDao: SearchHistoryDao,
    private val providers: List<RecipeProvider>,
    private val importer: RecipeUrlImporter,
) {
    val savedRecipes: Flow<List<RecipeSummary>> = recipeDao.observeSaved().map { list -> list.map { it.toSummary() } }
    val savedCount: Flow<Int> = recipeDao.observeSavedCount()
    val recentlyViewed: Flow<List<RecipeSummary>> = recipeDao.observeRecentlyViewed(20).map { list -> list.map { it.toSummary() } }
    val recentSearches: Flow<List<String>> = searchHistoryDao.observeRecent(10).map { list -> list.map { it.query } }

    fun observe(id: String): Flow<Recipe?> = recipeDao.observeById(id).map { it?.toDomain() }

    fun observeMany(ids: List<String>): Flow<List<Recipe>> = recipeDao.observeByIds(ids).map { list ->
        val byId = list.associateBy { it.id }
        ids.mapNotNull { byId[it]?.toDomain() }
    }

    fun observeSummaries(ids: List<String>): Flow<List<RecipeSummary>> = recipeDao.observeByIds(ids).map { list ->
        val byId = list.associateBy { it.id }
        ids.mapNotNull { byId[it]?.toSummary() }
    }

    suspend fun cached(id: String): Recipe? = recipeDao.getById(id)?.toDomain()

    suspend fun cachedMany(ids: List<String>): List<Recipe> = if (ids.isEmpty()) emptyList() else recipeDao.getByIds(ids).map { it.toDomain() }

    suspend fun isSaved(id: String): Boolean = recipeDao.getById(id)?.isSaved == true

    fun observeIsSaved(id: String): Flow<Boolean> = recipeDao.observeById(id).map { it?.isSaved == true }

    /** Full recipe: from cache when complete, otherwise fetched from its source and cached. */
    suspend fun get(id: String, forceRefresh: Boolean = false): Recipe? {
        val cached = recipeDao.getById(id)
        if (cached != null && cached.isComplete && !forceRefresh) return cached.toDomain()
        val (sourceName, sourceId) = id.split(':', limit = 2).let { if (it.size == 2) it[0] to it[1] else return cached?.toDomain() }
        val source = RecipeSource.entries.firstOrNull { it.name.equals(sourceName, true) } ?: return cached?.toDomain()
        val provider = providers.firstOrNull { it.source == source } ?: return cached?.toDomain()
        val fresh = withTimeoutOrNull(20_000) { provider.getRecipe(sourceId) } ?: return cached?.toDomain()
        upsert(listOf(fresh))
        return recipeDao.getById(fresh.id)?.toDomain() ?: fresh
    }

    suspend fun upsert(recipes: List<Recipe>) {
        if (recipes.isEmpty()) return
        val existing = recipeDao.getByIds(recipes.map { it.id }).associateBy { it.id }
        recipeDao.upsertAll(recipes.map { r ->
            val old = existing[r.id]
            // Never downgrade a complete cached recipe with a summary from a list endpoint.
            if (old != null && old.isComplete && !r.isComplete) old else r.toEntity(old)
        })
    }

    suspend fun saveUserRecipe(recipe: Recipe): Recipe {
        upsert(listOf(recipe))
        recipeDao.setSaved(recipe.id, true, System.currentTimeMillis())
        return recipe
    }

    suspend fun setSaved(id: String, saved: Boolean) {
        if (recipeDao.getById(id) == null) get(id)
        recipeDao.setSaved(id, saved, if (saved) System.currentTimeMillis() else 0)
    }

    suspend fun toggleSaved(id: String): Boolean {
        val now = !isSaved(id)
        setSaved(id, now)
        return now
    }

    suspend fun markOpened(id: String) = recipeDao.markOpened(id, System.currentTimeMillis())
    suspend fun markCooked(id: String) = recipeDao.markCooked(id, System.currentTimeMillis())
    suspend fun rate(id: String, rating: Int) = recipeDao.rate(id, rating.coerceIn(0, 5))
    suspend fun delete(id: String) = recipeDao.delete(id)

    suspend fun availableProviders(): List<RecipeProvider> = providers.filter { runCatching { it.isAvailable() }.getOrDefault(false) }

    /** Live search across all providers, merged and filtered locally. Falls back to the cache offline. */
    suspend fun search(filters: SearchFilters, limit: Int = 40): List<Recipe> {
        if (filters.query.isNotBlank()) searchHistoryDao.upsert(SearchHistoryEntity(filters.query.trim(), System.currentTimeMillis()))
        val active = availableProviders()
        val remote = coroutineScope {
            active.map { p -> async { withTimeoutOrNull(25_000) { p.search(filters, limit) } ?: emptyList() } }.map { it.await() }
        }.flatten()
        val local = if (filters.query.isNotBlank()) recipeDao.searchCached(filters.query.trim(), 30).map { it.toDomain() } else emptyList()
        val merged = mergeAndDedupe(remote + local)
        upsert(merged.filter { it.source != RecipeSource.USER })
        val filtered = merged.filter { filters.matches(it) || (!it.isComplete && lenientMatch(it, filters)) }
        return filters.sorted(filtered).take(limit)
    }

    /** Summaries from list endpoints have no ingredients yet, so diet/ingredient filters would wrongly drop them. */
    private fun lenientMatch(recipe: Recipe, filters: SearchFilters): Boolean {
        val relaxed = filters.copy(diets = emptySet(), ingredients = emptyList(), excludeIngredients = emptySet(), maxMinutes = null, maxCalories = null)
        return relaxed.matches(recipe)
    }

    suspend fun discover(limit: Int = 12, cacheKey: String = "discover", maxAgeMs: Long = 3 * 60 * 60 * 1000L): List<Recipe> {
        val cached = queryCacheDao.get(cacheKey)
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt < maxAgeMs) {
            val ids = cached.recipeIds.split(',').filter { it.isNotBlank() }
            val recipes = cachedMany(ids)
            if (recipes.size >= limit / 2) return ids.mapNotNull { id -> recipes.firstOrNull { it.id == id } }
        }
        val active = availableProviders()
        val results = coroutineScope {
            active.map { p -> async { withTimeoutOrNull(25_000) { p.discover(limit) } ?: emptyList() } }.map { it.await() }
        }.flatten().let { mergeAndDedupe(it) }.shuffled()
        if (results.isEmpty()) {
            return recipeDao.mostRecentlyCached(limit).map { it.toDomain() }
        }
        upsert(results)
        queryCacheDao.upsert(QueryCacheEntity(cacheKey, results.joinToString(",") { it.id }, System.currentTimeMillis()))
        return results.take(limit)
    }

    suspend fun byCuisine(cuisine: String, limit: Int = 30): List<Recipe> = fanOut("cuisine:$cuisine") { it.byCuisine(cuisine, limit) }
    suspend fun byCategory(category: String, limit: Int = 30): List<Recipe> = fanOut("category:$category") { it.byCategory(category, limit) }
    suspend fun byIngredient(ingredient: String, limit: Int = 30): List<Recipe> = fanOut("ingredient:$ingredient") { it.byIngredient(ingredient, limit) }

    private suspend fun fanOut(cacheKey: String, call: suspend (RecipeProvider) -> List<Recipe>): List<Recipe> {
        val cached = queryCacheDao.get(cacheKey)
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt < 6 * 60 * 60 * 1000L) {
            val ids = cached.recipeIds.split(',').filter { it.isNotBlank() }
            val recipes = cachedMany(ids)
            if (recipes.isNotEmpty()) return ids.mapNotNull { id -> recipes.firstOrNull { it.id == id } }
        }
        val active = availableProviders()
        val results = coroutineScope {
            active.map { p -> async { withTimeoutOrNull(25_000) { call(p) } ?: emptyList() } }.map { it.await() }
        }.flatten().let { mergeAndDedupe(it) }
        upsert(results)
        if (results.isNotEmpty()) queryCacheDao.upsert(QueryCacheEntity(cacheKey, results.joinToString(",") { it.id }, System.currentTimeMillis()))
        return results
    }

    suspend fun facets(): BrowseFacets {
        val active = availableProviders()
        val all = coroutineScope { active.map { p -> async { withTimeoutOrNull(15_000) { p.facets() } ?: BrowseFacets() } }.map { it.await() } }
        return BrowseFacets(
            cuisines = all.flatMap { it.cuisines }.distinctBy { it.lowercase() }.sorted(),
            categories = all.flatMap { it.categories }.distinctBy { it.lowercase() }.sorted(),
            ingredients = all.flatMap { it.ingredients }.distinctBy { it.lowercase() }.sorted(),
        )
    }

    suspend fun importFromUrl(url: String): RecipeUrlImporter.Result {
        val result = importer.import(url)
        if (result is RecipeUrlImporter.Result.Success) {
            upsert(listOf(result.recipe))
            recipeDao.setSaved(result.recipe.id, true, System.currentTimeMillis())
        }
        return result
    }

    /** Candidate pool for recommendations/plans: cache plus a fresh discover round. */
    suspend fun candidatePool(minimum: Int = 30): List<Recipe> {
        val cached = recipeDao.mostRecentlyCached(200).map { it.toDomain() }
        if (cached.size >= minimum) return cached
        return try {
            (discover(12, cacheKey = "discover") + cached).distinctBy { it.id }
        } catch (e: Exception) {
            Log.w("RecipeRepo", "candidatePool discover failed: ${e.message}")
            cached
        }
    }

    suspend fun interactionStats(): Pair<Map<String, Int>, Map<String, Int>> {
        val interacted = recipeDao.interactedWith()
        val cuisines = interacted.mapNotNull { it.cuisine }.groupingBy { it }.eachCount()
        val categories = interacted.mapNotNull { it.category }.groupingBy { it }.eachCount()
        return cuisines to categories
    }

    suspend fun recentlyCookedTitles(): List<String> = recipeDao.recentlyCooked(10).map { it.title }

    suspend fun clearSearchHistory() = searchHistoryDao.clear()

    suspend fun evictStale() {
        recipeDao.evictStale(System.currentTimeMillis() - 14L * 24 * 60 * 60 * 1000)
        queryCacheDao.evict(System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000)
    }

    private fun mergeAndDedupe(recipes: List<Recipe>): List<Recipe> {
        val byId = linkedMapOf<String, Recipe>()
        for (r in recipes) {
            val existing = byId[r.id]
            if (existing == null || (!existing.isComplete && r.isComplete)) byId[r.id] = r
        }
        // Drop near-duplicate titles across sources, keeping the richest.
        val byTitle = linkedMapOf<String, Recipe>()
        for (r in byId.values) {
            val key = r.title.lowercase().replace(Regex("[^a-z0-9]"), "")
            val existing = byTitle[key]
            if (existing == null || richness(r) > richness(existing)) byTitle[key] = r
        }
        return byTitle.values.toList()
    }

    private fun richness(r: Recipe): Int = (if (r.isComplete) 4 else 0) + (if (r.nutrition != null) 2 else 0) + (if (r.imageUrl != null) 1 else 0) + (if (r.videoUrl != null) 1 else 0)
}
