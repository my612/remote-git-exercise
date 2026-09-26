package com.mobuk.app.ui.mealplans

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mobuk.app.di.AppGraph
import com.mobuk.app.domain.model.MealSlot
import com.mobuk.app.domain.model.PlannerEntry
import com.mobuk.app.ui.common.EmptyState
import com.mobuk.app.ui.common.LoadingState
import com.mobuk.app.ui.common.MobTopBar
import com.mobuk.app.ui.common.PillChip
import com.mobuk.app.ui.common.RecipeImage
import com.mobuk.app.ui.common.RecipeRow
import com.mobuk.app.ui.common.graphViewModel
import com.mobuk.app.ui.home.MealPlanCard
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.LocalDate

class MealPlansViewModel(val graph: AppGraph) : ViewModel() {
    val plans = graph.mealPlans.plans
    val building = MutableStateFlow(false)

    init { viewModelScope.launch { if (graph.mealPlans.count() < graph.mealPlans.themes().size) rebuild(force = false) } }

    fun rebuild(force: Boolean) = viewModelScope.launch {
        building.value = true
        graph.mealPlans.buildAll(force)
        building.value = false
    }
}

@Composable
fun MealPlansScreen(onBack: () -> Unit, onOpenPlan: (String) -> Unit) {
    val vm = graphViewModel { MealPlansViewModel(it) }
    val plans by vm.plans.collectAsStateWithLifecycle(initialValue = emptyList())
    val building by vm.building.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        MobTopBar("Meal plans", onBack = onBack) {
            if (building) CircularProgressIndicator(Modifier.padding(12.dp).width(20.dp).height(20.dp), strokeWidth = 2.dp)
            else IconButton(onClick = { vm.rebuild(force = true) }) { Icon(Icons.Filled.Refresh, contentDescription = "Rebuild plans") }
        }
        Text("Curated weeks built from real recipes on your phone. Add a whole plan to your planner in one tap.", modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (plans.isEmpty()) {
            if (building) LoadingState(message = "Assembling plans from live sources…") else EmptyState("No plans yet", "Connect to the internet and tap refresh to build them.", actionLabel = "Build plans", onAction = { vm.rebuild(true) })
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(plans, key = { it.id }) { plan -> MealPlanCard(plan, onClick = { onOpenPlan(plan.id) }, modifier = Modifier.fillMaxWidth()) }
            }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MealPlanDetailViewModel(val graph: AppGraph, val id: String) : ViewModel() {
    val plan = graph.mealPlans.observe(id)
    val recipes = plan.flatMapLatest { p -> if (p == null) flowOf(emptyList()) else graph.recipes.observeSummaries(p.recipeIds) }
    val message = MutableStateFlow<String?>(null)

    fun addAllToPlanner(startDay: LocalDate, slot: MealSlot) = viewModelScope.launch {
        val p = plan.first() ?: return@launch
        val prefs = graph.prefs.prefs.first()
        graph.planner.addMany(p.recipeIds.mapIndexed { i, rid -> PlannerEntry(epochDay = startDay.plusDays(i.toLong()).toEpochDay(), slot = slot, recipeId = rid, servings = prefs.householdSize) })
        message.value = "Added ${p.recipeIds.size} meals to your planner"
    }

    fun addAllToShopping() = viewModelScope.launch {
        val p = plan.first() ?: return@launch
        graph.recipes.cachedMany(p.recipeIds).forEach { graph.shopping.addRecipe(it, null) }
        message.value = "Ingredients added to your shopping list"
    }

    fun rebuild() = viewModelScope.launch { graph.mealPlans.themes().firstOrNull { it.id == id }?.let { graph.mealPlans.build(it, force = true) } }
}

@Composable
fun MealPlanDetailScreen(planId: String, onBack: () -> Unit, onOpenRecipe: (String) -> Unit, onOpenPlanner: () -> Unit) {
    val vm = graphViewModel(key = "plan-$planId") { MealPlanDetailViewModel(it, planId) }
    val plan by vm.plan.collectAsStateWithLifecycle(initialValue = null)
    val recipes by vm.recipes.collectAsStateWithLifecycle(initialValue = emptyList())
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showAdd by remember { mutableStateOf(false) }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        val p = plan
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Box(Modifier.fillMaxWidth().height(220.dp)) {
                    RecipeImage(p?.coverImageUrl, modifier = Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
                    MobTopBar("", onBack = onBack) { IconButton(onClick = vm::rebuild) { Icon(Icons.Filled.Refresh, contentDescription = "Rebuild", tint = Color.White) } }
                    Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                        Text(p?.title ?: "", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                        Text(p?.subtitle ?: "", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.9f))
                    }
                }
            }
            item {
                Column(Modifier.padding(16.dp)) {
                    Text(p?.description ?: "", style = MaterialTheme.typography.bodyLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) { p?.tags?.forEach { PillChip(it, selected = false, onClick = {}) } }
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showAdd = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.CalendarMonth, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Add to planner") }
                        OutlinedButton(onClick = vm::addAllToShopping) { Icon(Icons.Filled.ShoppingCart, contentDescription = null) }
                    }
                }
            }
            if (recipes.isEmpty()) item { LoadingState(message = "Loading recipes…") }
            itemsIndexed(recipes, key = { _, r -> r.id }) { i, r ->
                RecipeRow(r, onClick = { onOpenRecipe(r.id) }, modifier = Modifier.padding(horizontal = 8.dp), subtitle = "Day ${i + 1} · " + listOfNotNull(r.totalMinutes?.let { "$it min" }, r.cuisine).joinToString(" · "))
            }
        }
    }
    if (showAdd) {
        var slot by remember { mutableStateOf(MealSlot.DINNER) }
        var start by remember { mutableStateOf(LocalDate.now()) }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add plan to planner") },
            text = {
                Column {
                    Text("One recipe per day, starting:")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillChip("Today", start == LocalDate.now(), onClick = { start = LocalDate.now() })
                        PillChip("Tomorrow", start == LocalDate.now().plusDays(1), onClick = { start = LocalDate.now().plusDays(1) })
                        PillChip("Next Monday", start.dayOfWeek == java.time.DayOfWeek.MONDAY && start > LocalDate.now(), onClick = { var d = LocalDate.now().plusDays(1); while (d.dayOfWeek != java.time.DayOfWeek.MONDAY) d = d.plusDays(1); start = d })
                    }
                    Text("Slot:", modifier = Modifier.padding(top = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { MealSlot.entries.forEach { s -> PillChip(s.label, s == slot, onClick = { slot = s }) } }
                }
            },
            confirmButton = { Button(onClick = { showAdd = false; vm.addAllToPlanner(start, slot); onOpenPlanner() }) { Text("Add") } },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } },
        )
    }
}
