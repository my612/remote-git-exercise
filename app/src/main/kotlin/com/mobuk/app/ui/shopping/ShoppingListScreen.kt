package com.mobuk.app.ui.shopping

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mobuk.app.di.AppGraph
import com.mobuk.app.domain.logic.QuantityFormatter
import com.mobuk.app.domain.model.RecipeSummary
import com.mobuk.app.domain.model.ShoppingItem
import com.mobuk.app.ui.common.EmptyState
import com.mobuk.app.ui.common.MobTopBar
import com.mobuk.app.ui.common.RecipeRow
import com.mobuk.app.ui.common.graphViewModel
import com.mobuk.app.ui.common.shareText
import kotlinx.coroutines.launch

class ShoppingViewModel(val graph: AppGraph) : ViewModel() {
    val items = graph.shopping.items
    fun setChecked(id: Long, checked: Boolean) = viewModelScope.launch { graph.shopping.setChecked(id, checked) }
    fun add(text: String) = viewModelScope.launch { graph.shopping.addCustom(text) }
    fun remove(id: Long) = viewModelScope.launch { graph.shopping.remove(id) }
    fun clearChecked() = viewModelScope.launch { graph.shopping.clearChecked() }
    fun clearAll() = viewModelScope.launch { graph.shopping.clearAll() }
    fun uncheckAll() = viewModelScope.launch { graph.shopping.uncheckAll() }
    fun resync() = viewModelScope.launch { graph.shopping.syncFromPlanner() }
    suspend fun recipesFor(item: ShoppingItem): List<RecipeSummary> = graph.recipes.cachedMany(item.recipeIds).map { RecipeSummary.from(it) }
    fun shareText(items: List<ShoppingItem>) = graph.shopping.shareText(items)
}

@Composable
fun ShoppingListScreen(onOpenRecipe: (String) -> Unit, onOpenPlanner: () -> Unit) {
    val vm = graphViewModel { ShoppingViewModel(it) }
    val items by vm.items.collectAsStateWithLifecycle(initialValue = emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<ShoppingItem?>(null) }
    var detailRecipes by remember { mutableStateOf<List<RecipeSummary>>(emptyList()) }

    val grouped = remember(items) { vm.graph.shopping.grouped(items.filter { !it.checked }) }
    val done = remember(items) { items.filter { it.checked } }

    Column(Modifier.fillMaxSize()) {
        MobTopBar(title = "Shopping list") {
            IconButton(onClick = { shareText(context, "Shopping list", vm.shareText(items)) }) { Icon(Icons.Filled.Share, contentDescription = "Share") }
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Clear ticked items") }, onClick = { menu = false; vm.clearChecked() })
                DropdownMenuItem(text = { Text("Untick everything") }, onClick = { menu = false; vm.uncheckAll() })
                DropdownMenuItem(text = { Text("Re-sync from planner") }, onClick = { menu = false; vm.resync() })
                DropdownMenuItem(text = { Text("Clear whole list") }, onClick = { menu = false; vm.clearAll() })
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Add anything: 2 lemons, toothpaste…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (input.isNotBlank()) { vm.add(input); input = "" } }),
            )
            IconButton(onClick = { if (input.isNotBlank()) { vm.add(input); input = "" } }) { Icon(Icons.Filled.Add, contentDescription = "Add") }
        }
        if (items.isEmpty()) {
            EmptyState(
                title = "Your list is empty",
                body = "Plan a meal and its ingredients appear here, sorted by aisle. Change servings and the quantities update.",
                icon = Icons.Filled.ShoppingCart,
                actionLabel = "Open planner",
                onAction = onOpenPlanner,
            )
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Text("${items.count { !it.checked }} to buy · ${done.size} ticked", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                grouped.forEach { (aisle, list) ->
                    item(key = "aisle-${aisle.name}") {
                        Text(aisle.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
                    }
                    items(list, key = { it.id }) { item ->
                        ShoppingRow(item, onCheck = { vm.setChecked(item.id, it) }, onClick = { detail = item; scope.launch { detailRecipes = vm.recipesFor(item) } })
                    }
                }
                if (done.isNotEmpty()) {
                    item(key = "done-header") {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Ticked off", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = vm::clearChecked) { Text("Clear") }
                        }
                    }
                    items(done, key = { "done-${it.id}" }) { item -> ShoppingRow(item, onCheck = { vm.setChecked(item.id, it) }, onClick = { detail = item; scope.launch { detailRecipes = vm.recipesFor(item) } }) }
                }
            }
        }
    }

    detail?.let { item ->
        ModalBottomSheet(onDismissRequest = { detail = null }) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
                Text(listOf(QuantityFormatter.formatWithUnit(item.quantity, item.unit), item.name).filter { it.isNotBlank() }.joinToString(" "), style = MaterialTheme.typography.headlineSmall)
                Text(item.aisle.label + (item.note?.let { " · $it" } ?: ""), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.isCustom) Text("Added by you", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (detailRecipes.isNotEmpty()) {
                    Text("Needed for", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                    detailRecipes.forEach { r -> RecipeRow(r, onClick = { detail = null; onOpenRecipe(r.id) }) }
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { vm.remove(item.id); detail = null }) { Icon(Icons.Filled.Delete, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Remove from list") }
            }
        }
    }
}

@Composable
private fun ShoppingRow(item: ShoppingItem, onCheck: (Boolean) -> Unit, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = item.checked, onCheckedChange = onCheck)
        val qty = QuantityFormatter.formatWithUnit(item.quantity, item.unit)
        Column(Modifier.weight(1f)) {
            Row {
                if (qty.isNotBlank()) { Text(qty, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge); Spacer(Modifier.width(6.dp)) }
                Text(item.name, style = MaterialTheme.typography.bodyLarge, textDecoration = if (item.checked) TextDecoration.LineThrough else null, color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            }
            val sub = listOfNotNull(item.note, if (item.recipeIds.size > 1) "${item.recipeIds.size} recipes" else null).joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
