package com.mobuk.app.ui.recipe

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobuk.app.ai.features.Adaptation
import com.mobuk.app.domain.logic.QuantityFormatter
import com.mobuk.app.domain.logic.ServingScaler
import com.mobuk.app.domain.model.MealSlot
import com.mobuk.app.domain.model.Nutrition
import com.mobuk.app.domain.model.Recipe
import com.mobuk.app.ui.common.DietPill
import com.mobuk.app.ui.common.EmptyState
import com.mobuk.app.ui.common.FullScreenLoading
import com.mobuk.app.ui.common.PillChip
import com.mobuk.app.ui.common.RecipeImage
import com.mobuk.app.ui.common.ServingsStepper
import com.mobuk.app.ui.common.StatRow
import com.mobuk.app.ui.common.graphViewModel
import com.mobuk.app.ui.common.openUrl
import com.mobuk.app.ui.common.shareText
import com.mobuk.app.ui.theme.Orange
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun RecipeDetailScreen(
    recipeId: String,
    onBack: () -> Unit,
    onStartCookMode: (String) -> Unit,
    onOpenRecipe: (String) -> Unit,
    onOpenPlanner: () -> Unit,
    onOpenShopping: () -> Unit,
    onBrowse: (String, String) -> Unit,
) {
    val vm = graphViewModel(key = "recipe-$recipeId") { RecipeDetailViewModel(it, recipeId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showPlanner by remember { mutableStateOf(false) }
    var showCollections by remember { mutableStateOf(false) }
    var showAdapt by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        val recipe = state.recipe
        when {
            state.loading && recipe == null -> FullScreenLoading(Modifier.padding(padding))
            recipe == null -> Column(Modifier.padding(padding)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                EmptyState("Recipe unavailable", state.error ?: "Try again later.", actionLabel = "Retry", onAction = { vm.load(force = true) })
            }
            else -> RecipeBody(
                recipe = recipe,
                state = state,
                padding = padding,
                onBack = onBack,
                onToggleSave = vm::toggleSave,
                onServings = vm::setServings,
                onPlanner = { showPlanner = true },
                onShopping = { vm.addToShoppingList() },
                onCollections = { showCollections = true },
                onAdapt = { showAdapt = true },
                onCook = { onStartCookMode(recipe.id) },
                onShare = {
                    shareText(context, recipe.title, buildString {
                        append(recipe.title).append('\n')
                        recipe.sourceUrl?.let { append(it).append('\n') }
                        append("\nIngredients:\n"); recipe.ingredients.forEach { append("• ").append(it.raw).append('\n') }
                    })
                },
                onOpenSource = { recipe.sourceUrl?.let { openUrl(context, it) } },
                onWatch = { recipe.videoUrl?.let { openUrl(context, it) } },
                onEstimate = vm::estimateNutrition,
                onRate = vm::rate,
                onBrowse = onBrowse,
            )
        }
    }

    if (showPlanner) {
        PlannerPickerSheet(
            onDismiss = { showPlanner = false },
            onPick = { date, slot -> vm.addToPlanner(date, slot); showPlanner = false },
        )
    }
    if (showCollections) {
        ModalBottomSheet(onDismissRequest = { showCollections = false }) {
            var newName by remember { mutableStateOf("") }
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
                Text("Add to collection", style = MaterialTheme.typography.headlineSmall)
                state.collections.forEach { c ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = c.id in state.inCollections, onCheckedChange = { vm.toggleCollection(c.id) })
                        Text(c.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text("${c.recipeCount}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = newName, onValueChange = { newName = it }, placeholder = { Text("New collection") }, modifier = Modifier.weight(1f), singleLine = true)
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { if (newName.isNotBlank()) { vm.createCollectionAndAdd(newName.trim()); newName = "" } }) { Text("Create") }
                }
            }
        }
    }
    if (showAdapt) {
        AdaptSheet(
            state = state,
            onDismiss = { showAdapt = false; vm.dismissAdapted() },
            onAdapt = { a, custom -> vm.adapt(a, custom) },
            onSave = { scope.launch { vm.saveAdapted()?.let { id -> showAdapt = false; onOpenRecipe(id) } } },
        )
    }
}

@Composable
private fun RecipeBody(
    recipe: Recipe,
    state: RecipeUiState,
    padding: PaddingValues,
    onBack: () -> Unit,
    onToggleSave: () -> Unit,
    onServings: (Int) -> Unit,
    onPlanner: () -> Unit,
    onShopping: () -> Unit,
    onCollections: () -> Unit,
    onAdapt: () -> Unit,
    onCook: () -> Unit,
    onShare: () -> Unit,
    onOpenSource: () -> Unit,
    onWatch: () -> Unit,
    onEstimate: () -> Unit,
    onRate: (Int) -> Unit,
    onBrowse: (String, String) -> Unit,
) {
    var checked by remember(recipe.id) { mutableStateOf(setOf<Int>()) }
    var rating by remember(recipe.id) { mutableStateOf(0) }

    Box(Modifier.fillMaxSize()) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 96.dp)) {
        item {
            Box {
                RecipeImage(recipe.imageUrl, modifier = Modifier.fillMaxWidth().height(320.dp), contentDescription = recipe.title)
                Row(Modifier.fillMaxWidth().padding(top = padding.calculateTopPadding() + 8.dp, start = 8.dp, end = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    FilledIconButton(onClick = onBack, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.9f), contentColor = Color.Black)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Row {
                        FilledIconButton(onClick = onShare, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.9f), contentColor = Color.Black)) {
                            Icon(Icons.Filled.Share, contentDescription = "Share")
                        }
                        Spacer(Modifier.width(8.dp))
                        FilledIconButton(onClick = onToggleSave, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.9f), contentColor = Color.Black)) {
                            Icon(if (state.isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, contentDescription = "Save")
                        }
                    }
                }
                if (recipe.videoUrl != null) {
                    FilledIconButton(onClick = onWatch, modifier = Modifier.align(Alignment.Center).size(64.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.9f), contentColor = Color.Black)) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Watch video", modifier = Modifier.size(36.dp))
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(16.dp)) {
                Text(recipe.title, style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("By ${recipe.author ?: recipe.source.label}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    if (recipe.sourceUrl != null) TextButton(onClick = onOpenSource) { Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Source") }
                }
                Spacer(Modifier.height(8.dp))
                StatRow(recipe.effectiveMinutes, recipe.servings, recipe.nutrition?.calories)
                if (recipe.dietTags.isNotEmpty() || recipe.cuisine != null || recipe.category != null) {
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        recipe.cuisine?.let { PillChip(it, selected = false, onClick = { onBrowse("cuisine", it) }) }
                        recipe.category?.let { PillChip(it, selected = false, onClick = { onBrowse("category", it) }) }
                        recipe.dietTags.sortedBy { it.ordinal }.forEach { DietPill(it) }
                    }
                }
                if (state.clashes.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(Modifier.width(8.dp))
                            Text("Contains ${state.clashes.joinToString()} from your allergies or dislikes.", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (!recipe.description.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(recipe.description, style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onPlanner, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Plan") }
                    FilledTonalButton(onClick = onShopping, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("List") }
                    FilledTonalButton(onClick = onCollections, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Bookmark, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Collect") }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onAdapt, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Orange, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(if (state.hasModel) "Adapt recipe with on-device AI" else "Adapt recipe (swaps, diets, spice)")
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Ingredients", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                Text("Servings", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                ServingsStepper(value = state.servings, onChange = onServings)
            }
            if (recipe.servings == null) Text("This source doesn't state servings, so quantities aren't scaled.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        }
        if (recipe.ingredients.isEmpty()) {
            item { Text("No ingredient list from this source. Open the original for details.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        itemsIndexed(recipe.ingredients) { i, ing ->
            val isChecked = i in checked
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = isChecked, onCheckedChange = { checked = if (isChecked) checked - i else checked + i })
                val qty = QuantityFormatter.formatWithUnit(ServingScaler.scaleAmount(ing.quantity, recipe.servings, state.servings), ing.unit)
                Column(Modifier.weight(1f)) {
                    Row {
                        if (qty.isNotBlank()) Text(qty, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        if (qty.isNotBlank()) Spacer(Modifier.width(6.dp))
                        Text(ing.name, style = MaterialTheme.typography.bodyLarge, color = if (isChecked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    }
                    if (ing.note != null) Text(ing.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(ing.aisle.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            Text("Method", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 16.dp))
        }
        if (recipe.steps.isEmpty()) {
            item { Text("No method from this source. Open the original for details.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(recipe.steps, key = { "step-${it.index}" }) { step ->
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Text("${step.index + 1}", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(step.text, style = MaterialTheme.typography.bodyLarge)
                    if (step.timerSeconds != null) Text("⏱ ${com.mobuk.app.domain.logic.TimerExtractor.format(step.timerSeconds)} timer in Cook Mode", style = MaterialTheme.typography.labelMedium, color = Orange, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            NutritionCard(recipe.nutrition, state.estimatingNutrition, state.hasModel, onEstimate)
        }
        item {
            Column(Modifier.padding(16.dp)) {
                Text("Rate this recipe", style = MaterialTheme.typography.titleMedium)
                Row {
                    (1..5).forEach { s ->
                        IconButton(onClick = { rating = s; onRate(s) }) {
                            Icon(if (s <= rating) Icons.Filled.Star else Icons.Filled.StarBorder, contentDescription = "$s stars", tint = if (s <= rating) Orange else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(recipe.source.attribution, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.BottomCenter) {
        Button(onClick = onCook, modifier = Modifier.fillMaxWidth().padding(16.dp).height(54.dp), shape = RoundedCornerShape(50), enabled = recipe.steps.isNotEmpty()) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Start Cook Mode", style = MaterialTheme.typography.titleMedium)
        }
    }
    }
}

@Composable
private fun NutritionCard(nutrition: Nutrition?, estimating: Boolean, hasModel: Boolean, onEstimate: () -> Unit) {
    Card(Modifier.padding(horizontal = 16.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Nutrition per serving", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (nutrition?.isEstimate == true) Text("AI estimate", style = MaterialTheme.typography.labelSmall, color = Orange)
            }
            if (nutrition == null || nutrition.isEmpty) {
                Text("This source doesn't publish nutrition.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(8.dp))
                if (estimating) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("Estimating on device…") }
                else OutlinedButton(onClick = onEstimate) { Text(if (hasModel) "Estimate with on-device AI" else "Estimate (needs on-device model)") }
            } else {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Macro("kcal", nutrition.calories?.let { "${it.toInt()}" })
                    Macro("Protein", nutrition.proteinG?.let { "${it.toInt()}g" })
                    Macro("Carbs", nutrition.carbsG?.let { "${it.toInt()}g" })
                    Macro("Fat", nutrition.fatG?.let { "${it.toInt()}g" })
                    Macro("Fibre", nutrition.fibreG?.let { "${it.toInt()}g" })
                }
            }
        }
    }
}

@Composable
private fun Macro(label: String, value: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value ?: "–", style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun PlannerPickerSheet(onDismiss: () -> Unit, onPick: (LocalDate, MealSlot) -> Unit, title: String = "Add to planner") {
    var day by remember { mutableStateOf(LocalDate.now()) }
    var slot by remember { mutableStateOf(MealSlot.DINNER) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text("Day", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0..13).map { LocalDate.now().plusDays(it.toLong()) }.forEach { d ->
                    val label = when (d) { LocalDate.now() -> "Today"; LocalDate.now().plusDays(1) -> "Tomorrow"; else -> "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.UK)} ${d.dayOfMonth}" }
                    PillChip(label, selected = d == day, onClick = { day = d })
                }
            }
            Text("Meal", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MealSlot.entries.forEach { s -> PillChip(s.label, selected = s == slot, onClick = { slot = s }) }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { onPick(day, slot) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Check, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Add to planner") }
        }
    }
}

@Composable
private fun AdaptSheet(state: RecipeUiState, onDismiss: () -> Unit, onAdapt: (Adaptation, String?) -> Unit, onSave: () -> Unit) {
    var selected by remember { mutableStateOf(Adaptation.VEGAN) }
    var custom by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            Text("Adapt recipe", style = MaterialTheme.typography.headlineSmall)
            Text(
                if (state.hasModel) "Your phone's model rewrites the ingredients and method. Nothing leaves the device." else "No on-device model yet, so swaps use Mob's substitution rules. Add a model in Settings → AI for smarter rewrites.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val adapted = state.adapted
            if (adapted == null) {
                FlowRow(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Adaptation.entries.forEach { a -> PillChip(a.label, selected = a == selected, onClick = { selected = a }) }
                }
                if (selected == Adaptation.SWAP || selected == Adaptation.CUSTOM) {
                    OutlinedTextField(value = custom, onValueChange = { custom = it }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), placeholder = { Text(if (selected == Adaptation.SWAP) "swap chicken with tofu" else "e.g. make it a one-pan tray bake") })
                }
                Spacer(Modifier.height(16.dp))
                if (state.adapting) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text("Rewriting the recipe…") }
                else Button(onClick = { onAdapt(selected, custom.ifBlank { null }) }, modifier = Modifier.fillMaxWidth()) { Text("Adapt") }
            } else {
                Text(adapted.recipe.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
                Text(adapted.notes, style = MaterialTheme.typography.bodyMedium, color = Orange, modifier = Modifier.padding(vertical = 6.dp))
                Text("Ingredients", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                adapted.recipe.ingredients.forEach { Text("• ${it.raw}", style = MaterialTheme.typography.bodyMedium) }
                Text("Method", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                adapted.recipe.steps.forEach { Text("${it.index + 1}. ${it.text}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp)) }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Discard") }
                    Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("Save as my recipe") }
                }
            }
        }
    }
}
