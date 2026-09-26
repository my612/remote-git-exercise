package com.mobuk.app.ui.cook

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobuk.app.domain.logic.TimerExtractor
import com.mobuk.app.domain.model.Recipe
import com.mobuk.app.ui.common.FullScreenLoading
import com.mobuk.app.ui.common.LocalAppGraph
import com.mobuk.app.ui.theme.Orange
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/** Hands-free step-by-step mode: big text, screen kept awake, one-tap timers detected from the method. */
@Composable
fun CookModeScreen(recipeId: String, onExit: () -> Unit) {
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    val recipe by graph.recipes.observe(recipeId).collectAsStateWithLifecycle(initialValue = null)
    val prefs by graph.prefs.prefs.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()

    LaunchedEffect(recipeId) { if (graph.recipes.cached(recipeId) == null) graph.recipes.get(recipeId) }

    val keepOn = prefs?.keepScreenOnInCookMode ?: true
    DisposableEffect(keepOn) {
        val window = context.findActivity()?.window
        if (keepOn) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    val r = recipe
    if (r == null) { FullScreenLoading(); return }
    CookPager(r, onExit = onExit, onFinish = { scope.launch { graph.recipes.markCooked(r.id) }; onExit() })
}

@Composable
private fun CookPager(recipe: Recipe, onExit: () -> Unit, onFinish: () -> Unit) {
    val steps = recipe.steps
    val pagerState = rememberPagerState(pageCount = { steps.size + 1 })
    val scope = rememberCoroutineScope()
    var showIngredients by remember { mutableStateOf(false) }
    var large by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(setOf<Int>()) }

    // One timer at a time, keyed by step index.
    var timerStep by remember { mutableIntStateOf(-1) }
    var remaining by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    LaunchedEffect(running, timerStep) {
        while (running && remaining > 0) {
            delay(1000)
            remaining -= 1
        }
        if (remaining == 0 && timerStep >= 0) running = false
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onExit) { Icon(Icons.Filled.Close, contentDescription = "Exit cook mode") }
                Column(Modifier.weight(1f)) {
                    Text(recipe.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(if (pagerState.currentPage < steps.size) "Step ${pagerState.currentPage + 1} of ${steps.size}" else "Done", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { large = !large }) { Icon(Icons.Filled.TextIncrease, contentDescription = "Text size") }
                IconButton(onClick = { showIngredients = true }) { Icon(Icons.Filled.FormatListBulleted, contentDescription = "Ingredients") }
            }
            LinearProgressIndicator(progress = { (pagerState.currentPage + 1f) / (steps.size + 1f) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = Orange)

            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                if (page < steps.size) {
                    val step = steps[page]
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.Center) {
                        Text("${page + 1}", style = MaterialTheme.typography.displayLarge, color = Orange)
                        Spacer(Modifier.height(12.dp))
                        Text(step.text, fontSize = if (large) 30.sp else 24.sp, lineHeight = if (large) 40.sp else 34.sp, fontWeight = FontWeight.Medium)
                        if (step.timerSeconds != null) {
                            Spacer(Modifier.height(24.dp))
                            val isThis = timerStep == page
                            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(if (isThis) TimerExtractor.format(remaining) else TimerExtractor.format(step.timerSeconds), style = MaterialTheme.typography.displayMedium)
                                        Text(if (isThis && remaining == 0) "Time's up!" else "Timer for this step", style = MaterialTheme.typography.labelMedium, color = if (isThis && remaining == 0) Orange else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (!isThis) {
                                        FilledTonalButton(onClick = { timerStep = page; remaining = step.timerSeconds; running = true }) { Icon(Icons.Filled.PlayArrow, contentDescription = null); Text("Start") }
                                    } else {
                                        IconButton(onClick = { running = !running }) { Icon(if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Pause or resume") }
                                        IconButton(onClick = { remaining = step.timerSeconds; running = false }) { Icon(Icons.Filled.Replay, contentDescription = "Reset") }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Orange, modifier = Modifier.size(72.dp))
                        Text("That's dinner sorted.", style = MaterialTheme.typography.headlineLarge)
                        Text("Mark it cooked to keep your planner and shopping list tidy.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("I cooked it!") }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0)) } }, modifier = Modifier.weight(1f), enabled = pagerState.currentPage > 0) { Text("Back") }
                Button(onClick = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(steps.size)) } }, modifier = Modifier.weight(1f), enabled = pagerState.currentPage < steps.size) { Text("Next step") }
            }
        }
    }

    if (showIngredients) {
        ModalBottomSheet(onDismissRequest = { showIngredients = false }) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState())) {
                Text("Ingredients", style = MaterialTheme.typography.headlineSmall)
                recipe.ingredients.forEachIndexed { i, ing ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = i in checked, onCheckedChange = { checked = if (i in checked) checked - i else checked + i })
                        Text(ing.raw, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}
