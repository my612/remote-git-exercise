package com.mobuk.app.ui.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mobuk.app.di.AppGraph
import com.mobuk.app.domain.model.RecipeCollection
import com.mobuk.app.ui.common.EmptyState
import com.mobuk.app.ui.common.MobTopBar
import com.mobuk.app.ui.common.RecipeCard
import com.mobuk.app.ui.common.RecipeImage
import com.mobuk.app.ui.common.graphViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

class SavedViewModel(val graph: AppGraph) : ViewModel() {
    val saved = graph.recipes.savedRecipes
    val collections = graph.collections.collections
    fun create(name: String) = viewModelScope.launch { graph.collections.create(name) }
    fun unsave(id: String) = viewModelScope.launch { graph.recipes.setSaved(id, false) }
}

@Composable
fun SavedScreen(onOpenRecipe: (String) -> Unit, onOpenCollection: (Long) -> Unit, onOpenSearch: () -> Unit, onOpenImport: () -> Unit) {
    val vm = graphViewModel { SavedViewModel(it) }
    val saved by vm.saved.collectAsStateWithLifecycle(initialValue = emptyList())
    val collections by vm.collections.collectAsStateWithLifecycle(initialValue = emptyList())
    var tab by remember { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        MobTopBar(title = "Saved") {
            IconButton(onClick = onOpenImport) { Icon(Icons.Filled.Link, contentDescription = "Import recipe") }
            IconButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, contentDescription = "New collection") }
        }
        TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("All (${saved.size})") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Collections (${collections.size})") })
        }
        if (tab == 0) {
            if (saved.isEmpty()) {
                EmptyState("Nothing saved yet", "Tap the bookmark on any recipe to keep it here, or import one from a link.", icon = Icons.Filled.Bookmark, actionLabel = "Find recipes", onAction = onOpenSearch)
            } else {
                LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(saved, key = { it.id }) { r -> RecipeCard(r, onClick = { onOpenRecipe(r.id) }, isSaved = true, onToggleSave = { vm.unsave(r.id) }) }
                }
            }
        } else {
            if (collections.isEmpty()) {
                EmptyState("No collections", "Group recipes into collections like Weeknight, Sunday lunch or Batch cook.", icon = Icons.Filled.Bookmark, actionLabel = "Create a collection", onAction = { creating = true })
            } else {
                LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(collections, key = { it.id }) { c -> CollectionCard(c, onClick = { onOpenCollection(c.id) }) }
                    item(span = { GridItemSpan(2) }) { TextButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, contentDescription = null); Text("New collection") } }
                }
            }
        }
    }
    if (creating) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("New collection") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, placeholder = { Text("e.g. Weeknight winners") }, singleLine = true) },
            confirmButton = { Button(onClick = { if (name.isNotBlank()) { vm.create(name.trim()); creating = false } }) { Text("Create") } },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CollectionCard(c: RecipeCollection, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp)) {
        Box(Modifier.fillMaxWidth().height(130.dp)) {
            RecipeImage(c.coverImageUrl, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text(c.name, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${c.recipeCount} recipes", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.9f))
            }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionDetailViewModel(val graph: AppGraph, val id: Long) : ViewModel() {
    val collection = graph.collections.observe(id)
    val recipes = graph.collections.observeRecipeIds(id).flatMapLatest { ids -> graph.recipes.observeSummaries(ids) }
    fun remove(recipeId: String) = viewModelScope.launch { graph.collections.removeRecipe(id, recipeId) }
    fun rename(name: String, description: String?) = viewModelScope.launch { graph.collections.rename(id, name, description) }
    fun delete() = viewModelScope.launch { graph.collections.delete(id) }
    fun addAllToShopping() = viewModelScope.launch {
        val ids = graph.collections.observeRecipeIds(id).first()
        graph.recipes.cachedMany(ids).forEach { graph.shopping.addRecipe(it, null) }
    }
}

@Composable
fun CollectionDetailScreen(collectionId: Long, onBack: () -> Unit, onOpenRecipe: (String) -> Unit) {
    val vm = graphViewModel(key = "collection-$collectionId") { CollectionDetailViewModel(it, collectionId) }
    val collection by vm.collection.collectAsStateWithLifecycle(initialValue = null)
    val recipes by vm.recipes.collectAsStateWithLifecycle(initialValue = emptyList())
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        MobTopBar(title = collection?.name ?: "Collection", onBack = onBack) {
            TextButton(onClick = { editing = true }) { Text("Edit") }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete collection") }
        }
        collection?.description?.let { Text(it, modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (recipes.isEmpty()) {
            EmptyState("Empty collection", "Open a recipe and tap Collect to add it here.", icon = Icons.Filled.Bookmark)
        } else {
            LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item(span = { GridItemSpan(2) }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = vm::addAllToShopping) { Text("Add all to shopping list") }
                    }
                }
                items(recipes, key = { it.id }) { r -> RecipeCard(r, onClick = { onOpenRecipe(r.id) }, isSaved = true, onToggleSave = { vm.remove(r.id) }) }
            }
        }
    }
    if (editing) {
        var name by remember { mutableStateOf(collection?.name.orEmpty()) }
        var desc by remember { mutableStateOf(collection?.description.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Edit collection") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") })
                }
            },
            confirmButton = { Button(onClick = { if (name.isNotBlank()) { vm.rename(name, desc); editing = false } }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete collection?") },
            text = { Text("The recipes stay saved; only the collection is removed.") },
            confirmButton = { Button(onClick = { confirmDelete = false; vm.delete(); onBack() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
