package com.mobuk.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobuk.app.data.remote.BrowseFacets
import com.mobuk.app.di.AppGraph
import com.mobuk.app.domain.logic.SearchFilters
import com.mobuk.app.domain.logic.SortOrder
import com.mobuk.app.domain.model.DietTag
import com.mobuk.app.domain.model.MealType
import com.mobuk.app.domain.model.RecipeSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val filters: SearchFilters = SearchFilters(),
    val results: List<RecipeSummary> = emptyList(),
    val loading: Boolean = false,
    val searched: Boolean = false,
    val recent: List<String> = emptyList(),
    val facets: BrowseFacets = BrowseFacets(),
    val fridgeInput: String = "",
    val error: String? = null,
    val sourcesLabel: String = "TheMealDB · USDA MyPlate Kitchen",
)

class SearchViewModel(private val graph: AppGraph, initialQuery: String) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState(filters = SearchFilters(query = initialQuery)))
    val state: StateFlow<SearchUiState> = _state
    private var job: Job? = null

    init {
        viewModelScope.launch { graph.recipes.recentSearches.collect { r -> _state.update { it.copy(recent = r) } } }
        viewModelScope.launch {
            runCatching { graph.recipes.facets() }.getOrNull()?.let { f -> _state.update { it.copy(facets = f) } }
            val active = graph.recipes.availableProviders()
            _state.update { it.copy(sourcesLabel = active.joinToString(" · ") { p -> p.source.label }) }
        }
        if (initialQuery.isNotBlank()) search()
    }

    fun setQuery(q: String) {
        _state.update { it.copy(filters = it.filters.copy(query = q)) }
    }

    fun setFridgeInput(text: String) = _state.update { it.copy(fridgeInput = text) }

    fun applyFridge() {
        val ings = _state.value.fridgeInput.split(',', '\n').map { it.trim() }.filter { it.isNotBlank() }
        _state.update { it.copy(filters = it.filters.copy(ingredients = ings)) }
        search()
    }

    fun toggleDiet(tag: DietTag) = updateFilters { f -> f.copy(diets = if (tag in f.diets) f.diets - tag else f.diets + tag) }
    fun toggleCuisine(c: String) = updateFilters { f -> f.copy(cuisines = if (c in f.cuisines) f.cuisines - c else setOf(c)) }
    fun toggleCategory(c: String) = updateFilters { f -> f.copy(categories = if (c in f.categories) f.categories - c else setOf(c)) }
    fun toggleMealType(m: MealType) = updateFilters { f -> f.copy(mealTypes = if (m in f.mealTypes) f.mealTypes - m else setOf(m)) }
    fun setMaxMinutes(m: Int?) = updateFilters { f -> f.copy(maxMinutes = m) }
    fun setMaxCalories(c: Int?) = updateFilters { f -> f.copy(maxCalories = c) }
    fun setSort(s: SortOrder) = updateFilters { f -> f.copy(sort = s) }
    fun clearFilters() = updateFilters { f -> SearchFilters(query = f.query) }.also { _state.update { it.copy(fridgeInput = "") } }

    private fun updateFilters(transform: (SearchFilters) -> SearchFilters) {
        _state.update { it.copy(filters = transform(it.filters)) }
        if (_state.value.searched || !_state.value.filters.isEmpty) search(debounce = true)
    }

    fun search(debounce: Boolean = false) {
        job?.cancel()
        job = viewModelScope.launch {
            if (debounce) delay(350)
            val filters = _state.value.filters
            if (filters.isEmpty) { _state.update { it.copy(results = emptyList(), searched = false) }; return@launch }
            _state.update { it.copy(loading = true, error = null, searched = true) }
            try {
                val results = graph.recipes.search(filters, 40)
                _state.update { it.copy(results = results.map { r -> RecipeSummary.from(r) }, loading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Search failed") }
            }
        }
    }

    fun clearRecent() = viewModelScope.launch { graph.recipes.clearSearchHistory() }
}
