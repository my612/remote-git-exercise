package com.mobuk.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.mobuk.app.domain.model.CookingGoal
import com.mobuk.app.domain.model.DietTag
import com.mobuk.app.ui.common.LocalAppGraph
import com.mobuk.app.ui.common.PillChip
import com.mobuk.app.ui.common.ServingsStepper
import com.mobuk.app.ui.settings.commonAllergens
import com.mobuk.app.ui.settings.commonCuisines
import com.mobuk.app.ui.theme.Orange
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val graph = LocalAppGraph.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var page by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var household by remember { mutableIntStateOf(2) }
    var diets by remember { mutableStateOf(setOf<DietTag>()) }
    var allergies by remember { mutableStateOf(setOf<String>()) }
    var cuisines by remember { mutableStateOf(setOf<String>()) }
    var goals by remember { mutableStateOf(setOf<CookingGoal>()) }

    fun finish() {
        scope.launch {
            graph.prefs.update { it.copy(onboardingDone = true, displayName = name.trim(), householdSize = household, diets = diets, allergies = allergies, favouriteCuisines = cuisines, goals = goals) }
            onDone()
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        LinearProgressIndicator(progress = { (page + 1) / 4f }, modifier = Modifier.fillMaxWidth(), color = Orange)
        Spacer(Modifier.height(24.dp))
        when (page) {
            0 -> {
                Text("Let's make weekly cooking fun again.", style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(12.dp))
                Text("Mob finds real recipes from public sources, plans your week, builds your shopping list by aisle and adapts recipes with AI that runs on your phone.", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("What should we call you?") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("How many are you cooking for?", modifier = Modifier.weight(1f))
                    ServingsStepper(value = household, onChange = { household = it })
                }
            }
            1 -> {
                Text("How do you eat?", style = MaterialTheme.typography.displayMedium)
                Text("We'll only suggest recipes that fit.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(DietTag.VEGETARIAN, DietTag.VEGAN, DietTag.PESCATARIAN, DietTag.GLUTEN_FREE, DietTag.DAIRY_FREE, DietTag.NUT_FREE).forEach { t ->
                        PillChip(t.label, selected = t in diets, onClick = { diets = if (t in diets) diets - t else diets + t })
                    }
                }
                Text("Any allergies?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    commonAllergens.forEach { a -> PillChip(a, selected = a in allergies, onClick = { allergies = if (a in allergies) allergies - a else allergies + a }) }
                }
            }
            2 -> {
                Text("What do you love?", style = MaterialTheme.typography.displayMedium)
                Text("Pick a few cuisines and the feed learns from there.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    commonCuisines.forEach { c -> PillChip(c, selected = c in cuisines, onClick = { cuisines = if (c in cuisines) cuisines - c else cuisines + c }) }
                }
            }
            else -> {
                Text("What are you here for?", style = MaterialTheme.typography.displayMedium)
                Text("Goals shape your recommendations and weekly plans.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CookingGoal.entries.forEach { g -> PillChip(g.label, selected = g in goals, onClick = { goals = if (g in goals) goals - g else goals + g }) }
                }
                Spacer(Modifier.height(16.dp))
                Text("Recipes come from TheMealDB and USDA MyPlate Kitchen out of the box. You can add Spoonacular, MCP servers and any recipe link later in Settings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (page > 0) TextButton(onClick = { page-- }) { Text("Back") }
            Spacer(Modifier.weight(1f))
            if (page < 3) Button(onClick = { page++ }) { Text("Next") } else Button(onClick = { finish() }) { Text("Let's cook") }
        }
        if (page == 0) TextButton(onClick = { finish() }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Skip for now") }
    }
}
