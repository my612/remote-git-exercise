package com.mobuk.app.ui.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobuk.app.ai.features.Recommender
import com.mobuk.app.data.repository.PlannerRepository
import com.mobuk.app.di.AppGraph
import com.mobuk.app.domain.model.MealSlot
import com.mobuk.app.domain.model.PlannedMeal
import com.mobuk.app.domain.model.PlannerEntry
import com.mobuk.app.domain.model.RecipeSummary
import com.mobuk.app.domain.model.UserPrefs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class PlannerUiState(
    val weekStart: LocalDate = LocalDate.now(),
    val meals: List<PlannedMeal> = emptyList(),
    val prefs: UserPrefs = UserPrefs(),
    val saved: List<RecipeSummary> = emptyList(),
    val recent: List<RecipeSummary> = emptyList(),
    val planning: Boolean = false,
    val message: String? = null,
) {
    val days: List<LocalDate> get() = (0..6).map { weekStart.plusDays(it.toLong()) }
    fun mealsFor(day: LocalDate, slot: MealSlot) = meals.filter { it.entry.epochDay == day.toEpochDay() && it.entry.slot == slot }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlannerViewModel(private val graph: AppGraph) : ViewModel() {

    private val _state = MutableStateFlow(PlannerUiState())
    val state: StateFlow<PlannerUiState> = _state
    private val weekStartFlow = MutableStateFlow(LocalDate.now())

    init {
        viewModelScope.launch {
            val prefs = graph.prefs.prefs.first()
            val start = PlannerRepository.weekStart(LocalDate.now(), prefs.weekStartsOnMonday)
            weekStartFlow.value = start
            _state.update { it.copy(weekStart = start, prefs = prefs) }
        }
        viewModelScope.launch { graph.prefs.prefs.collect { p -> _state.update { it.copy(prefs = p) } } }
        viewModelScope.launch {
            weekStartFlow.flatMapLatest { start -> graph.planner.observeWeek(start) }.collect { meals -> _state.update { it.copy(meals = meals) } }
        }
        viewModelScope.launch { graph.recipes.savedRecipes.collect { s -> _state.update { it.copy(saved = s) } } }
        viewModelScope.launch { graph.recipes.recentlyViewed.collect { s -> _state.update { it.copy(recent = s) } } }
    }

    fun shiftWeek(delta: Long) {
        val start = weekStartFlow.value.plusWeeks(delta)
        weekStartFlow.value = start
        _state.update { it.copy(weekStart = start) }
    }

    fun goToThisWeek() {
        val start = PlannerRepository.weekStart(LocalDate.now(), _state.value.prefs.weekStartsOnMonday)
        weekStartFlow.value = start
        _state.update { it.copy(weekStart = start) }
    }

    fun add(day: LocalDate, slot: MealSlot, recipeId: String) = viewModelScope.launch {
        graph.planner.add(day, slot, recipeId, _state.value.prefs.householdSize)
    }

    fun remove(entryId: Long) = viewModelScope.launch { graph.planner.remove(entryId) }
    fun setServings(entryId: Long, servings: Int) = viewModelScope.launch { graph.planner.setServings(entryId, servings) }
    fun setCooked(entryId: Long, cooked: Boolean) = viewModelScope.launch { graph.planner.setCooked(entryId, cooked) }
    fun move(entryId: Long, day: LocalDate, slot: MealSlot) = viewModelScope.launch { graph.planner.move(entryId, day, slot) }
    fun clearWeek() = viewModelScope.launch { graph.planner.clearWeek(_state.value.weekStart); _state.update { it.copy(message = "Week cleared") } }

    fun autoPlan(slots: List<MealSlot>, onlyEmpty: Boolean = true) = viewModelScope.launch {
        _state.update { it.copy(planning = true) }
        try {
            val prefs = _state.value.prefs
            val start = _state.value.weekStart
            val existing = graph.planner.entriesForWeek(start)
            val pool = graph.recipes.candidatePool(minimum = 7 * slots.size * 3)
            val (cuisines, categories) = graph.recipes.interactionStats()
            val history = Recommender.History(recentlyCookedTitles = graph.recipes.recentlyCookedTitles(), savedCuisines = cuisines, savedCategories = categories)
            val plan = graph.weekPlanner.plan(pool.filter { r -> existing.none { it.recipeId == r.id } }, prefs, 7, slots, history)
            val entries = plan.slots
                .filter { s -> !onlyEmpty || existing.none { it.epochDay == start.plusDays(s.dayOffset.toLong()).toEpochDay() && it.slot == s.slot } }
                .filter { s -> start.plusDays(s.dayOffset.toLong()) >= LocalDate.now() || start.plusDays(s.dayOffset.toLong()) == LocalDate.now() }
                .map { s -> PlannerEntry(epochDay = start.plusDays(s.dayOffset.toLong()).toEpochDay(), slot = s.slot, recipeId = s.recipe.id, servings = prefs.householdSize) }
            graph.planner.addMany(entries)
            _state.update { it.copy(planning = false, message = "${plan.title}: ${entries.size} meals added" + if (plan.usedModel) " (on-device AI)" else "") }
        } catch (e: Exception) {
            _state.update { it.copy(planning = false, message = "Couldn't plan: ${e.message}") }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
