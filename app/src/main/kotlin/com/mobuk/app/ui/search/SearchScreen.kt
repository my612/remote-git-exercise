package com.mobuk.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobuk.app.domain.logic.SortOrder
import com.mobuk.app.domain.model.DietTag
import com.mobuk.app.domain.model.MealType
import com.mobuk.app.ui.common.EmptyState
import com.mobuk.app.ui.common.LoadingState
import com.mobuk.app.ui.common.PillChip
import com.mobuk.app.ui.common.RecipeCard
import com.mobuk.app.ui.common.SectionHeader
import com.mobuk.app.ui.common.graphViewModel

@Composable
fun SearchScreen(initialQuery: String, onOpenRecipe: (String) -> Unit, onOpenImport: () -> Unit) {
    val vm = graphViewModel(key = "search-$initialQuery") { SearchViewModel(it, initialQuery) }
    val state by vm.state.collectAsStateWithLifecycle()
    var showFilters by remember { mutableStateOf(false) }
    var showFridge by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.filters.query,
                onValueChange = vm::setQuery,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search recipes, ingredients, cuisines") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.filters.query.isNotEmpty()) IconButton(onClick = { vm.setQuery(""); vm.search() }) { Icon(Icons.Filled.Close, contentDescription = "Clear") }
                },
                singleLine = true,
                shape = RoundedCornerShape(50),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search() }),
            )
            IconButton(onClick = { showFilters = true }) {
                BadgedBox(badge = { if (state.filters.activeCount > 0) Badge { Text(state.filters.activeCount.toString()) } }) {
                    Icon(Icons.Filled.Tune, contentDescription = "Filters")
                }
            }
        }
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { showFridge = true }, label = { Text(if (state.filters.ingredients.isEmpty()) "What's in my fridge?" else "Fridge: ${state.filters.ingredients.joinToString()}") }, leadingIcon = { Icon(Icons.Filled.Kitchen, contentDescription = null) })
            listOf(DietTag.VEGETARIAN, DietTag.VEGAN, DietTag.GLUTEN_FREE).forEach { tag ->
                PillChip(tag.label, selected = tag in state.filters.diets, onClick = { vm.toggleDiet(tag) })
            }
        }

        when {
            state.loading -> LoadingState(message = "Searching ${state.sourcesLabel}…")
            !state.searched -> {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    if (state.recent.isNotEmpty()) {
                        SectionHeader("Recent searches", actionLabel = "Clear", onAction = vm::clearRecent)
                        FlowRow(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.recent.forEach { q ->
                                AssistChip(onClick = { vm.setQuery(q); vm.search() }, label = { Text(q) }, leadingIcon = { Icon(Icons.Filled.History, contentDescription = null) })
                            }
                        }
                    }
                    if (state.facets.categories.isNotEmpty()) {
                        SectionHeader("Categories")
                        FlowRow(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.facets.categories.take(30).forEach { c -> PillChip(c, selected = c in state.filters.categories, onClick = { vm.toggleCategory(c) }) }
                        }
                    }
                    if (state.facets.cuisines.isNotEmpty()) {
                        SectionHeader("Cuisines")
                        FlowRow(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.facets.cuisines.take(40).forEach { c -> PillChip(c, selected = c in state.filters.cuisines, onClick = { vm.toggleCuisine(c) }) }
                        }
                    }
                    SectionHeader("Popular ingredients")
                    FlowRow(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Chicken", "Salmon", "Eggs", "Chickpeas", "Halloumi", "Prawns", "Tofu", "Beef", "Mushroom", "Lentils", "Aubergine", "Pasta").forEach { i ->
                            AssistChip(onClick = { vm.setQuery(i); vm.search() }, label = { Text(i) })
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = onOpenImport, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Have a recipe link? Import it") }
                    Spacer(Modifier.height(80.dp))
                }
            }
            state.results.isEmpty() -> EmptyState(
                title = "No recipes found",
                body = state.error ?: "Try fewer filters, another spelling, or paste a recipe link to import it.",
                actionLabel = "Import a link",
                onAction = onOpenImport,
            )
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(span = { GridItemSpan(2) }) {
                        Text("${state.results.size} recipes · ${state.filters.sort.label}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(state.results, key = { it.id }) { r -> RecipeCard(r, onClick = { onOpenRecipe(r.id) }) }
                }
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Filters", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = vm::clearFilters) { Text("Reset") }
                }
                Text("Diet", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DietTag.entries.forEach { tag -> PillChip(tag.label, selected = tag in state.filters.diets, onClick = { vm.toggleDiet(tag) }) }
                }
                Text("Meal", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MealType.entries.forEach { m -> PillChip(m.label, selected = m in state.filters.mealTypes, onClick = { vm.toggleMealType(m) }) }
                }
                val minutes = state.filters.maxMinutes
                Text(if (minutes == null) "Max time: any" else "Max time: $minutes min", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                Slider(
                    value = (minutes ?: 125).toFloat(),
                    onValueChange = { v -> vm.setMaxMinutes(if (v >= 125f) null else (v / 5).toInt() * 5) },
                    valueRange = 10f..125f,
                )
                val calories = state.filters.maxCalories
                Text(if (calories == null) "Max calories: any" else "Max calories: $calories kcal", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = (calories ?: 1050).toFloat(),
                    onValueChange = { v -> vm.setMaxCalories(if (v >= 1050f) null else (v / 50).toInt() * 50) },
                    valueRange = 200f..1050f,
                )
                if (state.facets.cuisines.isNotEmpty()) {
                    Text("Cuisine", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.facets.cuisines.forEach { c -> PillChip(c, selected = c in state.filters.cuisines, onClick = { vm.toggleCuisine(c) }) }
                    }
                }
                if (state.facets.categories.isNotEmpty()) {
                    Text("Category", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.facets.categories.forEach { c -> PillChip(c, selected = c in state.filters.categories, onClick = { vm.toggleCategory(c) }) }
                    }
                }
                Text("Sort", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SortOrder.entries.forEach { s -> PillChip(s.label, selected = s == state.filters.sort, onClick = { vm.setSort(s) }) }
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = { showFilters = false; vm.search() }, modifier = Modifier.fillMaxWidth()) { Text("Show recipes") }
            }
        }
    }

    if (showFridge) {
        ModalBottomSheet(onDismissRequest = { showFridge = false }) {
            Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
                Text("What's in your fridge?", style = MaterialTheme.typography.headlineSmall)
                Text("Comma-separate a few ingredients and we'll find real recipes that use them.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = state.fridgeInput,
                    onValueChange = vm::setFridgeInput,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    placeholder = { Text("e.g. chicken thighs, spinach, feta") },
                    minLines = 2,
                )
                Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showFridge = false; vm.applyFridge() }, modifier = Modifier.weight(1f)) { Text("Find recipes") }
                    if (state.filters.ingredients.isNotEmpty()) TextButton(onClick = { vm.setFridgeInput(""); vm.applyFridge(); showFridge = false }) { Text("Clear") }
                }
            }
        }
    }
}
