package com.mobuk.app.ui.inspiration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mobuk.app.di.AppGraph
import com.mobuk.app.domain.model.MealSlot
import com.mobuk.app.domain.model.Recipe
import com.mobuk.app.ui.common.FullScreenLoading
import com.mobuk.app.ui.common.RecipeImage
import com.mobuk.app.ui.common.formatMinutes
import com.mobuk.app.ui.common.graphViewModel
import com.mobuk.app.ui.common.openUrl
import com.mobuk.app.ui.recipe.PlannerPickerSheet
import com.mobuk.app.ui.theme.Orange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

class InspirationViewModel(val graph: AppGraph) : ViewModel() {
    val feed = MutableStateFlow<List<Recipe>>(emptyList())
    val saved = MutableStateFlow<Set<String>>(emptySet())

    init {
        viewModelScope.launch {
            val fresh = graph.recipes.discover(12, cacheKey = "inspiration", maxAgeMs = 30 * 60 * 1000L)
            val pool = graph.recipes.candidatePool()
            feed.value = (fresh + pool.shuffled()).distinctBy { it.id }.filter { it.imageUrl != null }.sortedByDescending { if (it.videoUrl != null) 1 else 0 }.take(40)
        }
        viewModelScope.launch { graph.recipes.savedRecipes.collect { s -> saved.value = s.map { it.id }.toSet() } }
    }

    fun loadMore() = viewModelScope.launch {
        val more = graph.recipes.discover(12, cacheKey = "inspiration-${System.currentTimeMillis() / 600_000}", maxAgeMs = 0)
        feed.value = (feed.value + more).distinctBy { it.id }
    }

    fun toggleSave(id: String) = viewModelScope.launch { graph.recipes.toggleSaved(id) }
    fun plan(id: String, date: LocalDate, slot: MealSlot) = viewModelScope.launch { graph.planner.add(date, slot, id, graph.prefs.prefs.first().householdSize) }
}

/** Full-screen vertical feed, in the spirit of Mob's Inspiration Feed. Videos open in the source player. */
@Composable
fun InspirationScreen(onBack: () -> Unit, onOpenRecipe: (String) -> Unit) {
    val vm = graphViewModel { InspirationViewModel(it) }
    val feed by vm.feed.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var planFor by remember { mutableStateOf<String?>(null) }

    if (feed.isEmpty()) { FullScreenLoading(); return }
    val pagerState = rememberPagerState(pageCount = { feed.size })
    LaunchedEffect(pagerState.currentPage) { if (pagerState.currentPage >= feed.size - 3) vm.loadMore() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val r = feed[page]
            Box(Modifier.fillMaxSize()) {
                RecipeImage(r.imageUrl, modifier = Modifier.fillMaxSize(), contentDescription = r.title)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)), startY = 600f)))
                if (r.videoUrl != null) {
                    FilledIconButton(onClick = { openUrl(context, r.videoUrl) }, modifier = Modifier.align(Alignment.Center).size(72.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.9f), contentColor = Color.Black)) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Watch", modifier = Modifier.size(40.dp))
                    }
                }
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(20.dp)) {
                    Text(listOfNotNull(r.cuisine, formatMinutes(r.effectiveMinutes), r.nutrition?.calories?.let { "${it.toInt()} kcal" }).joinToString(" · "), color = Orange, style = MaterialTheme.typography.labelLarge)
                    Text(r.title, color = Color.White, style = MaterialTheme.typography.headlineLarge)
                    Text("By ${r.author ?: r.source.label}", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { onOpenRecipe(r.id) }, modifier = Modifier.weight(1f)) { Text("Get the recipe") }
                        FilledIconButton(onClick = { vm.toggleSave(r.id) }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black)) {
                            Icon(if (r.id in saved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, contentDescription = "Save")
                        }
                        FilledIconButton(onClick = { planFor = r.id }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black)) {
                            Icon(Icons.Filled.CalendarMonth, contentDescription = "Plan")
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(top = 32.dp, start = 8.dp)) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White) }
        Text("${pagerState.currentPage + 1} / ${feed.size}", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.TopEnd).padding(top = 44.dp, end = 16.dp))
    }
    planFor?.let { id ->
        PlannerPickerSheet(onDismiss = { planFor = null }, onPick = { d, s -> vm.plan(id, d, s); planFor = null })
    }
}
