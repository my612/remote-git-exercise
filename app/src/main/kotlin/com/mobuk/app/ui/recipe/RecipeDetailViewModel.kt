package com.mobuk.app.ui.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobuk.app.ai.features.Adaptation
import com.mobuk.app.ai.features.AdaptedRecipe
import com.mobuk.app.ai.features.Recommender
import com.mobuk.app.di.AppGraph
import com.mobuk.app.domain.model.MealSlot
import com.mobuk.app.domain.model.Recipe
import com.mobuk.app.domain.model.RecipeCollection
import com.mobuk.app.domain.model.UserPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class RecipeUiState(
    val recipe: Recipe? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val isSaved: Boolean = false,
    val servings: Int = 4,
    val prefs: UserPrefs = UserPrefs(),
    val clashes: List<String> = emptyList(),
    val collections: List<RecipeCollection> = emptyList(),
    val inCollections: Set<Long> = emptySet(),
    val estimatingNutrition: Boolean = false,
    val adapting: Boolean = false,
    val adapted: AdaptedRecipe? = null,
    val message: String? = null,
    val hasModel: Boolean = false,
)

class RecipeDetailViewModel(private val graph: AppGraph, private val recipeId: String) : ViewModel() {

    private val _state = MutableStateFlow(RecipeUiState())
    val state: StateFlow<RecipeUiState> = _state

    init {
        viewModelScope.launch {
            val prefs = graph.prefs.prefs.first()
            _state.update { it.copy(prefs = prefs, servings = prefs.householdSize) }
            load()
        }
        viewModelScope.launch { graph.recipes.observeIsSaved(recipeId).collect { s -> _state.update { it.copy(isSaved = s) } } }
        viewModelScope.launch { graph.collections.collections.collect { c -> _state.update { it.copy(collections = c) } } }
        viewModelScope.launch { graph.collections.observeCollectionIdsForRecipe(recipeId).collect { ids -> _state.update { it.copy(inCollections = ids.toSet()) } } }
        viewModelScope.launch { graph.llmRouter.active.collect { id -> _state.update { it.copy(hasModel = id != null) } } }
    }

    fun load(force: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.recipe == null, error = null) }
            try {
                val recipe = graph.recipes.get(recipeId, forceRefresh = force)
                if (recipe == null) {
                    _state.update { it.copy(loading = false, error = "Couldn't load this recipe. Check your connection and try again.") }
                    return@launch
                }
                graph.recipes.markOpened(recipe.id)
                _state.update { s ->
                    s.copy(
                        recipe = recipe,
                        loading = false,
                        servings = if (s.recipe == null) (recipe.servings ?: s.prefs.householdSize) else s.servings,
                        clashes = Recommender.clashes(recipe, s.prefs),
                    )
                }
                graph.llmRouter.refreshStatuses()
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Couldn't load this recipe") }
            }
        }
    }

    fun setServings(n: Int) = _state.update { it.copy(servings = n.coerceIn(1, 24)) }

    fun toggleSave() = viewModelScope.launch {
        val now = graph.recipes.toggleSaved(recipeId)
        _state.update { it.copy(message = if (now) "Saved to your recipes" else "Removed from saved") }
    }

    fun addToPlanner(date: LocalDate, slot: MealSlot) = viewModelScope.launch {
        graph.planner.add(date, slot, recipeId, _state.value.servings)
        _state.update { it.copy(message = "Added to ${slot.label.lowercase()} on ${date.dayOfWeek.name.lowercase().replaceFirstChar { c -> c.uppercase() }} · shopping list updated") }
    }

    fun addToShoppingList() = viewModelScope.launch {
        val recipe = _state.value.recipe ?: return@launch
        graph.shopping.addRecipe(recipe, _state.value.servings)
        _state.update { it.copy(message = "Ingredients for ${_state.value.servings} added to your shopping list") }
    }

    fun toggleCollection(collectionId: Long) = viewModelScope.launch { graph.collections.toggle(collectionId, recipeId) }

    fun createCollectionAndAdd(name: String) = viewModelScope.launch {
        val id = graph.collections.create(name)
        graph.collections.addRecipe(id, recipeId)
        graph.recipes.setSaved(recipeId, true)
        _state.update { it.copy(message = "Added to $name") }
    }

    fun rate(stars: Int) = viewModelScope.launch { graph.recipes.rate(recipeId, stars) }

    fun estimateNutrition() = viewModelScope.launch {
        val recipe = _state.value.recipe ?: return@launch
        _state.update { it.copy(estimatingNutrition = true) }
        val n = graph.nutritionEstimator.estimate(recipe)
        if (n == null) {
            _state.update { it.copy(estimatingNutrition = false, message = "No on-device model available to estimate nutrition. Set one up in Settings → AI.") }
        } else {
            val updated = recipe.copy(nutrition = n)
            graph.recipes.upsert(listOf(updated))
            _state.update { it.copy(recipe = updated, estimatingNutrition = false) }
        }
    }

    fun adapt(adaptation: Adaptation, custom: String?) = viewModelScope.launch {
        val recipe = _state.value.recipe ?: return@launch
        _state.update { it.copy(adapting = true, adapted = null) }
        val result = runCatching { graph.recipeAdapter.adapt(recipe, adaptation, custom, _state.value.servings) }.getOrNull()
        _state.update { it.copy(adapting = false, adapted = result, message = if (result == null) "Couldn't adapt this recipe" else null) }
    }

    fun dismissAdapted() = _state.update { it.copy(adapted = null) }

    /** Saves the adapted variant as the user's own recipe and returns its id. */
    suspend fun saveAdapted(): String? {
        val adapted = _state.value.adapted ?: return null
        graph.recipes.saveUserRecipe(adapted.recipe)
        _state.update { it.copy(adapted = null, message = "Saved as ${adapted.recipe.title}") }
        return adapted.recipe.id
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
