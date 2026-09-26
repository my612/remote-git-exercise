package com.mobuk.app.data.repository

import com.mobuk.app.data.local.PlannerDao
import com.mobuk.app.data.local.PlannerEntryEntity
import com.mobuk.app.data.local.RecipeDao
import com.mobuk.app.data.local.toDomain
import com.mobuk.app.data.local.toSummary
import com.mobuk.app.domain.model.MealSlot
import com.mobuk.app.domain.model.PlannedMeal
import com.mobuk.app.domain.model.PlannerEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class PlannerRepository(
    private val plannerDao: PlannerDao,
    private val recipeDao: RecipeDao,
    private val shopping: ShoppingRepository,
) {
    fun observeWeek(weekStart: LocalDate): Flow<List<PlannedMeal>> {
        val from = weekStart.toEpochDay()
        val to = from + 6
        return plannerDao.observeRange(from, to).flatMapLatest { entries ->
            val ids = entries.map { it.recipeId }.distinct()
            recipeDao.observeByIds(ids).map { recipes ->
                val byId = recipes.associateBy { it.id }
                entries.map { e -> PlannedMeal(e.toDomain(), byId[e.recipeId]?.toSummary()) }
                    .sortedWith(compareBy({ it.entry.epochDay }, { it.entry.slot.order }))
            }
        }
    }

    fun observeUpcoming(from: LocalDate = LocalDate.now()): Flow<List<PlannedMeal>> =
        plannerDao.observeUpcoming(from.toEpochDay()).flatMapLatest { entries ->
            val ids = entries.map { it.recipeId }.distinct()
            recipeDao.observeByIds(ids).map { recipes ->
                val byId = recipes.associateBy { it.id }
                entries.map { e -> PlannedMeal(e.toDomain(), byId[e.recipeId]?.toSummary()) }
            }
        }

    val upcomingCount: Flow<Int> = plannerDao.observeUpcomingCount(LocalDate.now().toEpochDay())

    suspend fun add(date: LocalDate, slot: MealSlot, recipeId: String, servings: Int, note: String? = null): Long {
        val id = plannerDao.insert(
            PlannerEntryEntity(epochDay = date.toEpochDay(), slot = slot.name, recipeId = recipeId, servings = servings, note = note, createdAt = System.currentTimeMillis()),
        )
        shopping.syncFromPlanner()
        return id
    }

    suspend fun addMany(entries: List<PlannerEntry>) {
        entries.forEach { e ->
            plannerDao.insert(PlannerEntryEntity(epochDay = e.epochDay, slot = e.slot.name, recipeId = e.recipeId, servings = e.servings, note = e.note, createdAt = System.currentTimeMillis()))
        }
        shopping.syncFromPlanner()
    }

    suspend fun move(entryId: Long, date: LocalDate, slot: MealSlot) {
        val entry = plannerDao.getById(entryId) ?: return
        plannerDao.update(entry.copy(epochDay = date.toEpochDay(), slot = slot.name))
        shopping.syncFromPlanner()
    }

    suspend fun setServings(entryId: Long, servings: Int) {
        val entry = plannerDao.getById(entryId) ?: return
        plannerDao.update(entry.copy(servings = servings.coerceIn(1, 24)))
        shopping.syncFromPlanner()
    }

    suspend fun setCooked(entryId: Long, cooked: Boolean) {
        plannerDao.setCooked(entryId, cooked)
        if (cooked) plannerDao.getById(entryId)?.let { recipeDao.markCooked(it.recipeId, System.currentTimeMillis()) }
    }

    suspend fun remove(entryId: Long) {
        plannerDao.delete(entryId)
        shopping.syncFromPlanner()
    }

    suspend fun clearWeek(weekStart: LocalDate) {
        plannerDao.clearRange(weekStart.toEpochDay(), weekStart.toEpochDay() + 6)
        shopping.syncFromPlanner()
    }

    suspend fun entriesForWeek(weekStart: LocalDate): List<PlannerEntry> =
        plannerDao.range(weekStart.toEpochDay(), weekStart.toEpochDay() + 6).map { it.toDomain() }

    suspend fun allEntries(): List<PlannerEntry> = plannerDao.all().map { it.toDomain() }

    companion object {
        fun weekStart(date: LocalDate, mondayStart: Boolean): LocalDate {
            val dow = date.dayOfWeek.value // Monday=1..Sunday=7
            val offset = if (mondayStart) dow - 1 else dow % 7
            return date.minusDays(offset.toLong())
        }
    }
}
