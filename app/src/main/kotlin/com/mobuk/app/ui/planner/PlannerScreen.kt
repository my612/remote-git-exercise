package com.mobuk.app.ui.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobuk.app.domain.model.MealSlot
import com.mobuk.app.domain.model.PlannedMeal
import com.mobuk.app.domain.model.RecipeSummary
import com.mobuk.app.ui.common.MobTopBar
import com.mobuk.app.ui.common.PillChip
import com.mobuk.app.ui.common.RecipeImage
import com.mobuk.app.ui.common.RecipeRow
import com.mobuk.app.ui.common.ServingsStepper
import com.mobuk.app.ui.common.graphViewModel
import com.mobuk.app.ui.theme.Orange
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun PlannerScreen(onOpenRecipe: (String) -> Unit, onOpenShopping: () -> Unit, onOpenSearch: () -> Unit) {
    val vm = graphViewModel { PlannerViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pickerTarget by remember { mutableStateOf<Pair<LocalDate, MealSlot>?>(null) }
    var editing by remember { mutableStateOf<PlannedMeal?>(null) }
    var showAutoPlan by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                MobTopBar(title = "Planner") {
                    IconButton(onClick = onOpenShopping) { Icon(Icons.Filled.ShoppingCart, contentDescription = "Shopping list") }
                    IconButton(onClick = { confirmClear = true }) { Icon(Icons.Filled.Delete, contentDescription = "Clear week") }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.shiftWeek(-1) }) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous week") }
                    val end = state.weekStart.plusDays(6)
                    val fmt = DateTimeFormatter.ofPattern("d MMM", Locale.UK)
                    Text("${state.weekStart.format(fmt)} – ${end.format(fmt)}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).clickable { vm.goToThisWeek() }, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    IconButton(onClick = { vm.shiftWeek(1) }) { Icon(Icons.Filled.ChevronRight, contentDescription = "Next week") }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showAutoPlan = true }, modifier = Modifier.weight(1f), enabled = !state.planning) {
                        if (state.planning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text(if (state.planning) "Planning…" else "Auto-plan my week")
                    }
                    OutlinedButton(onClick = onOpenShopping) { Text("Shopping list") }
                }
            }
            items(state.days, key = { it.toEpochDay() }) { day ->
                DayCard(
                    day = day,
                    meals = MealSlot.entries.associateWith { slot -> state.mealsFor(day, slot) },
                    onOpenRecipe = onOpenRecipe,
                    onAdd = { slot -> pickerTarget = day to slot },
                    onEdit = { editing = it },
                    onToggleCooked = { m -> vm.setCooked(m.entry.id, !m.entry.cooked) },
                )
            }
        }
    }

    pickerTarget?.let { (day, slot) ->
        RecipePickerSheet(
            title = "${slot.label} · ${day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.UK)}",
            saved = state.saved,
            recent = state.recent,
            onDismiss = { pickerTarget = null },
            onPick = { id -> vm.add(day, slot, id); pickerTarget = null },
            onSearch = { pickerTarget = null; onOpenSearch() },
        )
    }
    editing?.let { meal ->
        ModalBottomSheet(onDismissRequest = { editing = null }) {
            var servings by remember { mutableStateOf(meal.entry.servings) }
            var day by remember { mutableStateOf(LocalDate.ofEpochDay(meal.entry.epochDay)) }
            var slot by remember { mutableStateOf(meal.entry.slot) }
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState())) {
                Text(meal.recipe?.title ?: "Planned meal", style = MaterialTheme.typography.headlineSmall)
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Servings", modifier = Modifier.weight(1f))
                    ServingsStepper(value = servings, onChange = { servings = it })
                }
                Text("Move to", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.days.forEach { d -> PillChip(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.UK), selected = d == day, onClick = { day = d }) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    MealSlot.entries.forEach { s -> PillChip(s.label, selected = s == slot, onClick = { slot = s }) }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.remove(meal.entry.id); editing = null }, modifier = Modifier.weight(1f)) { Text("Remove") }
                    Button(onClick = {
                        if (servings != meal.entry.servings) vm.setServings(meal.entry.id, servings)
                        if (day.toEpochDay() != meal.entry.epochDay || slot != meal.entry.slot) vm.move(meal.entry.id, day, slot)
                        editing = null
                    }, modifier = Modifier.weight(1f)) { Text("Save") }
                }
                meal.recipe?.let { r -> TextButton(onClick = { editing = null; onOpenRecipe(r.id) }) { Text("Open recipe") } }
            }
        }
    }
    if (showAutoPlan) {
        var slots by remember { mutableStateOf(setOf(MealSlot.DINNER)) }
        AlertDialog(
            onDismissRequest = { showAutoPlan = false },
            title = { Text("Auto-plan this week") },
            text = {
                Column {
                    Text("Fills empty slots with real recipes that match your diet, allergies and goals. Your on-device model picks for variety when available.")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                        MealSlot.entries.forEach { s -> PillChip(s.label, selected = s in slots, onClick = { slots = if (s in slots) slots - s else slots + s }) }
                    }
                }
            },
            confirmButton = { Button(onClick = { showAutoPlan = false; vm.autoPlan(slots.toList().sortedBy { it.order }.ifEmpty { listOf(MealSlot.DINNER) }) }) { Text("Plan it") } },
            dismissButton = { TextButton(onClick = { showAutoPlan = false }) { Text("Cancel") } },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear this week?") },
            text = { Text("Removes every planned meal for the week shown. Your shopping list will update.") },
            confirmButton = { Button(onClick = { confirmClear = false; vm.clearWeek() }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DayCard(
    day: LocalDate,
    meals: Map<MealSlot, List<PlannedMeal>>,
    onOpenRecipe: (String) -> Unit,
    onAdd: (MealSlot) -> Unit,
    onEdit: (PlannedMeal) -> Unit,
    onToggleCooked: (PlannedMeal) -> Unit,
) {
    val isToday = day == LocalDate.now()
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.UK), style = MaterialTheme.typography.titleLarge, color = if (isToday) Orange else MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(8.dp))
                Text(day.format(DateTimeFormatter.ofPattern("d MMM", Locale.UK)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (isToday) { Spacer(Modifier.width(8.dp)); Text("TODAY", style = MaterialTheme.typography.labelSmall, color = Orange) }
            }
            MealSlot.entries.forEach { slot ->
                val list = meals[slot].orEmpty()
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(slot.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(76.dp))
                    Column(Modifier.weight(1f)) {
                        list.forEach { meal ->
                            val r = meal.recipe
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onEdit(meal) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                RecipeImage(r?.imageUrl, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(r?.title ?: "Recipe", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${meal.entry.servings} servings" + (r?.totalMinutes?.let { " · $it min" } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { onToggleCooked(meal) }) {
                                    Icon(if (meal.entry.cooked) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, contentDescription = "Cooked", tint = if (meal.entry.cooked) Orange else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (list.isEmpty()) {
                            TextButton(onClick = { onAdd(slot) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Add") }
                        } else {
                            TextButton(onClick = { onAdd(slot) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("+ Add another", style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecipePickerSheet(title: String, saved: List<RecipeSummary>, recent: List<RecipeSummary>, onDismiss: () -> Unit, onPick: (String) -> Unit, onSearch: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                    Button(onClick = onSearch, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Search for a recipe") }
                }
            }
            if (saved.isNotEmpty()) {
                item { Text("Saved", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp)) }
                items(saved, key = { "s-${it.id}" }) { r -> RecipeRow(r, onClick = { onPick(r.id) }, modifier = Modifier.padding(horizontal = 8.dp)) }
            }
            if (recent.isNotEmpty()) {
                item { Text("Recently viewed", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp)) }
                items(recent, key = { "r-${it.id}" }) { r -> RecipeRow(r, onClick = { onPick(r.id) }, modifier = Modifier.padding(horizontal = 8.dp)) }
            }
            if (saved.isEmpty() && recent.isEmpty()) {
                item { Text("Save or open a few recipes and they'll show up here.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}
