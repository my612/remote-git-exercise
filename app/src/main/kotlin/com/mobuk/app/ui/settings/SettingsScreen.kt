package com.mobuk.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mobuk.app.di.AppGraph
import com.mobuk.app.domain.model.CookingGoal
import com.mobuk.app.domain.model.DietTag
import com.mobuk.app.domain.model.UnitSystem
import com.mobuk.app.domain.model.UserPrefs
import com.mobuk.app.ui.common.MobTopBar
import com.mobuk.app.ui.common.PillChip
import com.mobuk.app.ui.common.ServingsStepper
import com.mobuk.app.ui.common.graphViewModel
import com.mobuk.app.ui.common.openUrl
import kotlinx.coroutines.launch

class SettingsViewModel(val graph: AppGraph) : ViewModel() {
    val prefs = graph.prefs.prefs
    fun update(transform: (UserPrefs) -> UserPrefs) = viewModelScope.launch { graph.prefs.update(transform) }
    fun clearCache() = viewModelScope.launch { graph.recipes.evictStale(); graph.recipes.clearSearchHistory() }
}

val commonCuisines = listOf("British", "Italian", "Indian", "Chinese", "Thai", "Japanese", "Mexican", "Greek", "Spanish", "French", "Middle Eastern", "Korean", "Vietnamese", "American", "Caribbean", "Moroccan", "Turkish")
val commonAllergens = listOf("peanuts", "tree nuts", "milk", "eggs", "wheat", "soy", "fish", "shellfish", "sesame", "celery", "mustard", "sulphites", "lupin", "molluscs")

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenMcp: () -> Unit, onOpenAi: () -> Unit, onRestartOnboarding: () -> Unit) {
    val vm = graphViewModel { SettingsViewModel(it) }
    val prefs by vm.prefs.collectAsStateWithLifecycle(initialValue = UserPrefs())
    val context = LocalContext.current
    var name by remember(prefs.displayName) { mutableStateOf(prefs.displayName) }
    var dislikes by remember(prefs.dislikedIngredients) { mutableStateOf(prefs.dislikedIngredients.joinToString(", ")) }
    var spoonKey by remember(prefs.spoonacularApiKey) { mutableStateOf(prefs.spoonacularApiKey) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        MobTopBar("Settings", onBack = onBack)
        Section("Profile") {
            OutlinedTextField(value = name, onValueChange = { name = it; vm.update { p -> p.copy(displayName = it) } }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Household size", modifier = Modifier.weight(1f))
                ServingsStepper(value = prefs.householdSize, onChange = { n -> vm.update { p -> p.copy(householdSize = n) } })
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Metric units", modifier = Modifier.weight(1f))
                Switch(checked = prefs.unitSystem == UnitSystem.METRIC, onCheckedChange = { m -> vm.update { p -> p.copy(unitSystem = if (m) UnitSystem.METRIC else UnitSystem.IMPERIAL) } })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Week starts on Monday", modifier = Modifier.weight(1f))
                Switch(checked = prefs.weekStartsOnMonday, onCheckedChange = { m -> vm.update { p -> p.copy(weekStartsOnMonday = m) } })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Keep screen on in Cook Mode", modifier = Modifier.weight(1f))
                Switch(checked = prefs.keepScreenOnInCookMode, onCheckedChange = { m -> vm.update { p -> p.copy(keepScreenOnInCookMode = m) } })
            }
        }
        Section("Diet") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(DietTag.VEGETARIAN, DietTag.VEGAN, DietTag.PESCATARIAN, DietTag.GLUTEN_FREE, DietTag.DAIRY_FREE, DietTag.NUT_FREE).forEach { t ->
                    PillChip(t.label, selected = t in prefs.diets, onClick = { vm.update { p -> p.copy(diets = if (t in p.diets) p.diets - t else p.diets + t) } })
                }
            }
            Text("Allergies (recipes containing these are flagged and excluded from picks)", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                commonAllergens.forEach { a -> PillChip(a, selected = a in prefs.allergies, onClick = { vm.update { p -> p.copy(allergies = if (a in p.allergies) p.allergies - a else p.allergies + a) } }) }
            }
            OutlinedTextField(
                value = dislikes,
                onValueChange = { dislikes = it; vm.update { p -> p.copy(dislikedIngredients = it.split(',').map { s -> s.trim().lowercase() }.filter { s -> s.isNotBlank() }.toSet()) } },
                label = { Text("Ingredients you dislike (comma separated)") }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
        Section("Taste & goals") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                commonCuisines.forEach { c -> PillChip(c, selected = c in prefs.favouriteCuisines, onClick = { vm.update { p -> p.copy(favouriteCuisines = if (c in p.favouriteCuisines) p.favouriteCuisines - c else p.favouriteCuisines + c) } }) }
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CookingGoal.entries.forEach { g -> PillChip(g.label, selected = g in prefs.goals, onClick = { vm.update { p -> p.copy(goals = if (g in p.goals) p.goals - g else p.goals + g) } }) }
            }
        }
        Section("On-device AI") {
            NavRow("Models & engine", "Gemini Nano (AICore) · Gemma via LiteRT-LM", onOpenAi)
        }
        Section("Recipe sources") {
            Text("Built in: TheMealDB and USDA MyPlate Kitchen (no keys). Add a Spoonacular key for thousands more recipes with full nutrition.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = spoonKey, onValueChange = { spoonKey = it; vm.update { p -> p.copy(spoonacularApiKey = it.trim()) } },
                label = { Text("Spoonacular API key (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                visualTransformation = PasswordVisualTransformation(),
            )
            TextButton(onClick = { openUrl(context, "https://spoonacular.com/food-api/console#Dashboard") }) { Text("Get a free key") }
            NavRow("MCP servers", "Connect recipe tools over Model Context Protocol", onOpenMcp)
        }
        Section("Data") {
            TextButton(onClick = vm::clearCache) { Text("Clear cached recipes and search history") }
            TextButton(onClick = onRestartOnboarding) { Text("Redo the welcome setup") }
        }
        Section("About") {
            Text("Recipes are real, third-party recipes fetched live from public sources; nothing here is generated. Attribution: TheMealDB (themealdb.com); USDA MyPlate Kitchen via myplate.food (public domain); Spoonacular; and any site you import from.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Recommendations, adaptations and the assistant run entirely on this phone. No recipe data or preferences are sent to a server by this app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp)) { content() }
        }
    }
}

@Composable
fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Filled.ChevronRight, contentDescription = null)
    }
}
