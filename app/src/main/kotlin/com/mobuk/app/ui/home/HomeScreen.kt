package com.mobuk.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobuk.app.domain.model.MealPlan
import com.mobuk.app.ui.common.LoadingState
import com.mobuk.app.ui.common.RecipeCarousel
import com.mobuk.app.ui.common.RecipeImage
import com.mobuk.app.ui.common.RecipeRow
import com.mobuk.app.ui.common.SectionHeader
import com.mobuk.app.ui.common.graphViewModel
import com.mobuk.app.ui.theme.Ink
import com.mobuk.app.ui.theme.Orange

@Composable
fun HomeScreen(
    onOpenRecipe: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onBrowse: (String, String) -> Unit,
    onOpenPlanner: () -> Unit,
    onOpenAssistant: () -> Unit,
    onOpenInspiration: () -> Unit,
    onOpenMealPlans: () -> Unit,
    onOpenMealPlan: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenImport: () -> Unit,
) {
    val vm = graphViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.load(force = true) }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(state.greeting, style = MaterialTheme.typography.displayMedium)
                        Text("What are we cooking?", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
                }
            }
            item {
                Surface(
                    onClick = onOpenSearch,
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        Text("Search 5,000+ real recipes, or what's in your fridge", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                Row(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionTile(modifier = Modifier.weight(1f), icon = Icons.Filled.AutoAwesome, title = "Ask Mob", subtitle = state.engineLabel?.let { "On-device · $it" } ?: "Ideas, plans, swaps", color = Ink, onClick = onOpenAssistant)
                    ActionTile(modifier = Modifier.weight(1f), icon = Icons.Filled.PlayArrow, title = "Inspiration", subtitle = "Swipe the feed", color = Orange, onClick = onOpenInspiration)
                    ActionTile(modifier = Modifier.weight(1f), icon = Icons.Filled.Link, title = "Import", subtitle = "Any recipe URL", color = Color(0xFF2E6B4F), onClick = onOpenImport)
                }
            }
            if (state.today.isNotEmpty()) {
                item { SectionHeader("Today's plan", actionLabel = "Planner", onAction = onOpenPlanner) }
                items(state.today, key = { "today-${it.entry.id}" }) { meal ->
                    val r = meal.recipe
                    if (r != null) {
                        RecipeRow(r, onClick = { onOpenRecipe(r.id) }, subtitle = "${meal.entry.slot.label} · ${meal.entry.servings} servings", modifier = Modifier.padding(horizontal = 8.dp))
                    }
                }
            }
            item {
                SectionHeader("For you", subtitle = if (state.engineLabel != null) "Picked by ${state.engineLabel} on your phone" else "Picked from your tastes and goals")
                if (state.loading && state.forYou.isEmpty()) LoadingState(message = "Finding real recipes you'll love…")
                else if (state.forYou.isEmpty() && state.fresh.isNotEmpty()) RecipeCarousel(state.fresh, onOpenRecipe)
                else RecipeCarousel(state.forYou.map { it.recipe }, onOpenRecipe, captions = state.forYou.associate { it.recipe.id to it.reason })
            }
            if (state.error != null) {
                item { Text(state.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            }
            if (state.quick.isNotEmpty()) {
                item { SectionHeader("Quick tonight", subtitle = "On the table in 30 minutes", actionLabel = "See all", onAction = { onBrowse("quick", "30") }) }
                item { RecipeCarousel(state.quick, onOpenRecipe) }
            }
            if (state.categories.isNotEmpty()) {
                item { SectionHeader("Browse by category") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.categories) { c ->
                            Surface(onClick = { onBrowse("category", c) }, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary) {
                                Text(c, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                            }
                        }
                    }
                }
            }
            if (state.mealPlans.isNotEmpty()) {
                item { SectionHeader("Meal plans", subtitle = "A week sorted in one tap", actionLabel = "See all", onAction = onOpenMealPlans) }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.mealPlans, key = { it.id }) { plan -> MealPlanCard(plan, onClick = { onOpenMealPlan(plan.id) }) }
                    }
                }
            }
            if (state.veggie.isNotEmpty()) {
                item { SectionHeader("Veggie favourites", actionLabel = "See all", onAction = { onBrowse("diet", "VEGETARIAN") }) }
                item { RecipeCarousel(state.veggie, onOpenRecipe) }
            }
            if (state.cuisines.isNotEmpty()) {
                item { SectionHeader("Cuisines") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.cuisines) { c ->
                            Surface(onClick = { onBrowse("cuisine", c) }, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface) {
                                Text(c, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                            }
                        }
                    }
                }
            }
            if (state.saved.isNotEmpty()) {
                item { SectionHeader("From your saved") }
                item { RecipeCarousel(state.saved, onOpenRecipe) }
            }
            if (state.fresh.isNotEmpty() && state.forYou.isNotEmpty()) {
                item { SectionHeader("Fresh from the sources", subtitle = "TheMealDB · USDA MyPlate Kitchen") }
                item { RecipeCarousel(state.fresh, onOpenRecipe) }
            }
        }
    }
}

@Composable
private fun ActionTile(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    Column(
        modifier = modifier.height(104.dp).clip(RoundedCornerShape(16.dp)).background(color).clickable(onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White)
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun MealPlanCard(plan: MealPlan, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.width(240.dp), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp)) {
        Box(modifier = Modifier.fillMaxWidth().height(130.dp)) {
            RecipeImage(plan.coverImageUrl, modifier = Modifier.fillMaxSize(), contentDescription = plan.title)
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text(plan.title, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${plan.recipeIds.size} recipes · ${plan.subtitle}", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.9f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
