package com.mobuk.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobuk.app.ai.features.Recommendation
import com.mobuk.app.ai.features.Recommender
import com.mobuk.app.di.AppGraph
import com.mobuk.app.domain.model.DietTag
import com.mobuk.app.domain.model.MealPlan
import com.mobuk.app.domain.model.PlannedMeal
import com.mobuk.app.domain.model.RecipeSummary
import com.mobuk.app.domain.model.UserPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

data class HomeUiState(
    val prefs: UserPrefs = UserPrefs(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val forYou: List<Recommendation> = emptyList(),
    val fresh: List<RecipeSummary> = emptyList(),
    val quick: List<RecipeSummary> = emptyList(),
    val veggie: List<RecipeSummary> = emptyList(),
    val cuisines: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val today: List<PlannedMeal> = emptyList(),
    val saved: List<RecipeSummary> = emptyList(),
    val mealPlans: List<MealPlan> = emptyList(),
    val engineLabel: String? = null,
    val error: String? = null,
) {
    val greeting: String
        get() {
            val hour = LocalTime.now().hour
            val base = when (hour) { in 5..11 -> "Morning"; in 12..16 -> "Afternoon"; else -> "Evening" }
            return if (prefs.displayName.isBlank()) "$base, Mob" else "$base, ${prefs.displayName}"
        }
}

class HomeViewModel(private val graph: AppGraph) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    init {
        viewModelScope.launch { graph.prefs.prefs.collect { p -> _state.update { it.copy(prefs = p) } } }
        viewModelScope.launch {
            graph.planner.observeWeek(LocalDate.now()).collect { meals ->
                val today = LocalDate.now().toEpochDay()
                _state.update { it.copy(today = meals.filter { m -> m.entry.epochDay == today }) }
            }
        }
        viewModelScope.launch { graph.recipes.savedRecipes.collect { s -> _state.update { it.copy(saved = s.take(10)) } } }
        viewModelScope.launch { graph.mealPlans.plans.collect { p -> _state.update { it.copy(mealPlans = p) } } }
        viewModelScope.launch { graph.llmRouter.active.collect { id -> _state.update { it.copy(engineLabel = id?.label) } } }
        load()
    }

    fun load(force: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.fresh.isEmpty(), refreshing = force, error = null) }
            try {
                val prefs = graph.prefs.prefs.first()
                val fresh = graph.recipes.discover(12, cacheKey = "home", maxAgeMs = if (force) 0 else 3 * 60 * 60 * 1000L)
                _state.update { it.copy(fresh = fresh.map { r -> RecipeSummary.from(r) }, loading = false) }
                val pool = (graph.recipes.candidatePool() + fresh).distinctBy { it.id }
                val quick = pool.filter { DietTag.QUICK in it.dietTags || (it.effectiveMinutes ?: 99) <= 30 }.shuffled().take(10)
                val veggie = pool.filter { DietTag.VEGETARIAN in it.dietTags }.shuffled().take(10)
                _state.update { it.copy(quick = quick.map { r -> RecipeSummary.from(r) }, veggie = veggie.map { r -> RecipeSummary.from(r) }) }
                val (cuisines, categories) = graph.recipes.interactionStats()
                val history = Recommender.History(
                    recentlyCookedTitles = graph.recipes.recentlyCookedTitles(),
                    recentlyViewedTitles = graph.recipes.recentlyViewed.first().map { it.title },
                    savedCuisines = cuisines,
                    savedCategories = categories,
                )
                graph.llmRouter.refreshStatuses()
                val recs = graph.recommender.recommend(pool, prefs, history, slotHint = "It is ${LocalTime.now().hour}:00; suggest what to cook next")
                _state.update { it.copy(forYou = recs, refreshing = false) }
                val facets = graph.recipes.facets()
                _state.update { it.copy(cuisines = facets.cuisines.take(24), categories = facets.categories.take(24)) }
                if (graph.mealPlans.count() == 0) graph.mealPlans.buildAll()
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, refreshing = false, error = e.message ?: "Couldn't load recipes") }
            }
        }
    }
}
