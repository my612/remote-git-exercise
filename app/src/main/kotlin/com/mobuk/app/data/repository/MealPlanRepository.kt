package com.mobuk.app.data.repository

import android.util.Log
import com.mobuk.app.ai.features.MealPlanTheme
import com.mobuk.app.ai.features.MealPlanThemes
import com.mobuk.app.data.local.MealPlanDao
import com.mobuk.app.data.local.toDomain
import com.mobuk.app.data.local.toEntity
import com.mobuk.app.domain.logic.SearchFilters
import com.mobuk.app.domain.model.MealPlan
import com.mobuk.app.domain.model.Recipe
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Curated meal plans are assembled from live recipes per theme and cached for a week. */
class MealPlanRepository(
    private val dao: MealPlanDao,
    private val recipes: RecipeRepository,
) {
    val plans: Flow<List<MealPlan>> = dao.observeAll().map { list -> list.mapNotNull { it.toDomain() } }

    fun observe(id: String): Flow<MealPlan?> = dao.observeById(id).map { it?.toDomain() }

    suspend fun get(id: String): MealPlan? = dao.getById(id)?.toDomain()

    fun themes(): List<MealPlanTheme> = MealPlanThemes.all

    /** Builds (or rebuilds) one theme from live sources. Returns null if nothing could be fetched. */
    suspend fun build(theme: MealPlanTheme, force: Boolean = false): MealPlan? {
        val existing = dao.getById(theme.id)?.toDomain()
        if (existing != null && !force && System.currentTimeMillis() - existing.createdAt < 7L * 24 * 60 * 60 * 1000 && existing.recipeIds.size >= 3) return existing
        val pool = try {
            coroutineScope {
                val searches = theme.queries.map { q -> async { recipes.search(theme.filters.copy(query = q), 12) } }
                val filtered = async { if (theme.filters.isEmpty) emptyList() else recipes.search(theme.filters, 20) }
                (searches.map { it.await() } + listOf(filtered.await())).flatten()
            }
        } catch (e: Exception) {
            Log.w("MealPlans", "build ${theme.id} failed: ${e.message}")
            emptyList<Recipe>()
        }
        val chosen = MealPlanThemes.assemble(theme, pool)
        if (chosen.isEmpty()) return existing
        val plan = MealPlan(
            id = theme.id,
            title = theme.title,
            subtitle = theme.subtitle,
            description = theme.description,
            coverImageUrl = chosen.firstOrNull { it.imageUrl != null }?.imageUrl,
            recipeIds = chosen.map { it.id },
            tags = theme.tags,
            generatedByAi = false,
            createdAt = System.currentTimeMillis(),
        )
        dao.upsert(plan.toEntity())
        return plan
    }

    suspend fun buildAll(force: Boolean = false) {
        for (theme in MealPlanThemes.all) {
            runCatching { build(theme, force) }
        }
    }

    suspend fun saveGenerated(plan: MealPlan) = dao.upsert(plan.toEntity())

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun count(): Int = dao.count()
}
