package com.mobuk.app.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mobuk.app.domain.model.DietTag
import com.mobuk.app.domain.model.RecipeSummary
import com.mobuk.app.ui.theme.Orange

fun openUrl(context: Context, url: String) {
    runCatching {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(url))
    }.onFailure {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

fun shareText(context: Context, subject: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun formatMinutes(minutes: Int?): String? = minutes?.let { m ->
    when {
        m <= 0 -> null
        m < 60 -> "$m min"
        m % 60 == 0 -> "${m / 60} hr"
        else -> "${m / 60} hr ${m % 60} min"
    }
}

@Composable
fun MobTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
    )
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun RecipeImage(url: String?, modifier: Modifier = Modifier, contentDescription: String? = null) {
    if (url.isNullOrBlank()) {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Restaurant, contentDescription = contentDescription, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
        }
    } else {
        AsyncImage(model = url, contentDescription = contentDescription, modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentScale = ContentScale.Crop)
    }
}

@Composable
fun MetaChip(icon: ImageVector, text: String, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(3.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

@Composable
fun DietPill(tag: DietTag, modifier: Modifier = Modifier) {
    val bg = when (tag) {
        DietTag.VEGAN, DietTag.VEGETARIAN -> Color(0xFFDDF4E4)
        DietTag.HIGH_PROTEIN -> Color(0xFFFFE1D3)
        DietTag.QUICK -> Color(0xFFFFF0B8)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(color = bg, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(tag.label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF111111), modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

/** Tall card used in grids and feeds. */
@Composable
fun RecipeCard(
    recipe: RecipeSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    isSaved: Boolean? = null,
    onToggleSave: (() -> Unit)? = null,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box {
            RecipeImage(recipe.imageUrl, modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp)), contentDescription = recipe.title)
            if (onToggleSave != null) {
                FilledIconButton(
                    onClick = onToggleSave,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(34.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.9f), contentColor = Color(0xFF111111)),
                ) {
                    Icon(if (isSaved == true) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, contentDescription = "Save", modifier = Modifier.size(18.dp))
                }
            }
            val minutes = formatMinutes(recipe.totalMinutes)
            if (minutes != null) {
                Surface(color = Color(0xEE111111), shape = RoundedCornerShape(50), modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                    Text(minutes, color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
        }
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(recipe.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, minLines = 2)
            val meta = listOfNotNull(recipe.cuisine, recipe.calories?.let { "${it.toInt()} kcal" }).joinToString(" · ")
            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (caption != null) {
                Text(caption, style = MaterialTheme.typography.labelMedium, color = Orange, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** Compact horizontal row used in planner, collections and chat cards. */
@Composable
fun RecipeRow(
    recipe: RecipeSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    subtitle: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecipeImage(recipe.imageUrl, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)), contentDescription = recipe.title)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(recipe.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = subtitle ?: listOfNotNull(formatMinutes(recipe.totalMinutes), recipe.cuisine, recipe.calories?.let { "${it.toInt()} kcal" }).joinToString(" · ")
            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
fun RecipeCarousel(recipes: List<RecipeSummary>, onOpen: (String) -> Unit, captions: Map<String, String> = emptyMap(), cardWidth: Int = 172) {
    LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(recipes, key = { it.id }) { r ->
            RecipeCard(r, onClick = { onOpen(r.id) }, modifier = Modifier.width(cardWidth.dp), caption = captions[r.id])
        }
    }
}

@Composable
fun PillChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(text) }, modifier = modifier, shape = RoundedCornerShape(50))
}

@Composable
fun ServingsStepper(value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, min: Int = 1, max: Int = 24) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        FilledIconButton(onClick = { if (value > min) onChange(value - 1) }, modifier = Modifier.size(32.dp), shape = CircleShape) {
            Icon(Icons.Filled.Remove, contentDescription = "Fewer servings", modifier = Modifier.size(16.dp))
        }
        Text("$value", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))
        FilledIconButton(onClick = { if (value < max) onChange(value + 1) }, modifier = Modifier.size(32.dp), shape = CircleShape) {
            Icon(Icons.Filled.Add, contentDescription = "More servings", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier, message: String? = null) {
    Column(modifier = modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = Orange)
        if (message != null) {
            Spacer(Modifier.height(12.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.Filled.Restaurant, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(modifier = modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun StatRow(minutes: Int?, servings: Int?, calories: Double?) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        formatMinutes(minutes)?.let { MetaChip(Icons.Filled.Schedule, it) }
        servings?.let { MetaChip(Icons.Filled.Restaurant, "Serves $it") }
        calories?.let { MetaChip(Icons.Filled.LocalFireDepartment, "${it.toInt()} kcal") }
    }
}

@Composable
fun FullScreenLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Orange) }
}
