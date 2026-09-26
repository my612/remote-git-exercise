package com.mobuk.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mobuk.app.di.AppGraph
import com.mobuk.app.domain.logic.SearchFilters
import com.mobuk.app.domain.model.DietTag
import com.mobuk.app.domain.model.RecipeSummary
import com.mobuk.app.ui.common.EmptyState
import com.mobuk.app.ui.common.LoadingState
import com.mobuk.app.ui.common.MobTopBar
import com.mobuk.app.ui.common.RecipeCard
import com.mobuk.app.ui.common.graphViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class BrowseViewModel(private val graph: AppGraph, private val kind: String, private val value: String) : ViewModel() {
    val results = MutableStateFlow<List<RecipeSummary>?>(null)

    init {
        viewModelScope.launch {
            val list = runCatching {
                when (kind) {
                    "cuisine" -> graph.recipes.byCuisine(value)
                    "category" -> graph.recipes.byCategory(value)
                    "ingredient" -> graph.recipes.byIngredient(value)
                    "quick" -> graph.recipes.search(SearchFilters(maxMinutes = value.toIntOrNull() ?: 30, query = "quick"), 40).ifEmpty { graph.recipes.candidatePool().filter { (it.effectiveMinutes ?: 99) <= 30 } }
                    "diet" -> {
                        val tag = runCatching { DietTag.valueOf(value) }.getOrNull()
                        if (tag == null) emptyList() else graph.recipes.search(SearchFilters(diets = setOf(tag)), 40).ifEmpty { graph.recipes.candidatePool().filter { tag in it.dietTags } }
                    }
                    else -> graph.recipes.search(SearchFilters(query = value), 40)
                }
            }.getOrDefault(emptyList())
            results.value = list.map { RecipeSummary.from(it) }
        }
    }
}

@Composable
fun BrowseScreen(kind: String, value: String, onBack: () -> Unit, onOpenRecipe: (String) -> Unit) {
    val vm = graphViewModel(key = "browse-$kind-$value") { BrowseViewModel(it, kind, value) }
    val results by vm.results.collectAsStateWithLifecycle()
    val title = when (kind) {
        "quick" -> "Quick dinners"
        "diet" -> runCatching { DietTag.valueOf(value).label }.getOrDefault(value)
        else -> value
    }
    Column(modifier = Modifier.fillMaxSize()) {
        MobTopBar(title = title, onBack = onBack)
        val list = results
        when {
            list == null -> LoadingState(message = "Loading $title…")
            list.isEmpty() -> EmptyState("Nothing here yet", "The sources didn't return anything for $title. Try again when you're online.")
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list, key = { it.id }) { r -> RecipeCard(r, onClick = { onOpenRecipe(r.id) }) }
            }
        }
    }
}
