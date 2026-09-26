package com.mobuk.app.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mobuk.app.di.AppGraph
import com.mobuk.app.domain.model.ChatMessage
import com.mobuk.app.domain.model.ChatRole
import com.mobuk.app.domain.model.RecipeSummary
import com.mobuk.app.ui.common.MobTopBar
import com.mobuk.app.ui.common.RecipeCard
import com.mobuk.app.ui.common.graphViewModel
import com.mobuk.app.ui.theme.Orange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class AssistantViewModel(val graph: AppGraph) : ViewModel() {
    val messages = graph.chat.messages
    val busy = MutableStateFlow<String?>(null)
    val engine = graph.llmRouter.active
    val cards = MutableStateFlow<Map<Long, List<RecipeSummary>>>(emptyMap())

    init { viewModelScope.launch { graph.llmRouter.refreshStatuses(); graph.llmRouter.current() } }

    fun send(text: String) = viewModelScope.launch {
        busy.value = "Thinking…"
        try {
            graph.chat.send(text) { p -> busy.value = p }
        } finally {
            busy.value = null
        }
    }

    suspend fun cardsFor(message: ChatMessage): List<RecipeSummary> {
        cards.value[message.id]?.let { return it }
        val list = graph.recipes.cachedMany(message.recipeIds).map { RecipeSummary.from(it) }
        cards.value = cards.value + (message.id to list)
        return list
    }

    fun clear() = viewModelScope.launch { graph.chat.clear() }
}

private val suggestions = listOf(
    "What can I cook with eggs, spinach and feta?",
    "Plan my dinners for the week",
    "Find a quick veggie curry",
    "High-protein lunch ideas under 500 calories",
    "Add milk, bread and lemons to my list",
    "What's on my planner?",
)

@Composable
fun AssistantScreen(onBack: () -> Unit, onOpenRecipe: (String) -> Unit, onOpenAiSettings: () -> Unit) {
    val vm = graphViewModel { AssistantViewModel(it) }
    val messages by vm.messages.collectAsStateWithLifecycle(initialValue = emptyList())
    val busy by vm.busy.collectAsStateWithLifecycle()
    val engine by vm.engine.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, busy) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size) }

    Column(Modifier.fillMaxSize().imePadding()) {
        MobTopBar("Ask Mob", onBack = onBack) {
            IconButton(onClick = vm::clear) { Icon(Icons.Filled.Delete, contentDescription = "Clear chat") }
        }
        Surface(onClick = onOpenAiSettings, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Memory, contentDescription = null, tint = if (engine != null) Orange else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(
                    engine?.let { "Running on your phone with ${it.label}. Nothing leaves the device." } ?: "No on-device model yet: using rules. Tap to set up Gemini Nano or Gemma.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (messages.isEmpty()) {
                item {
                    Text("Hi! I'm Mob. Ask me for recipe ideas, a plan for the week, swaps for an ingredient, or to add things to your list.", style = MaterialTheme.typography.bodyLarge)
                }
            }
            items(messages, key = { it.id }) { m -> MessageBubble(m, vm, onOpenRecipe) }
            if (busy != null) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Orange); Spacer(Modifier.width(8.dp)); Text(busy ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(suggestions) { s -> AssistChip(onClick = { vm.send(s) }, label = { Text(s) }, enabled = busy == null) }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f), placeholder = { Text("Ask for ideas, plans or swaps…") }, shape = RoundedCornerShape(24.dp), maxLines = 4)
            IconButton(onClick = { if (input.isNotBlank() && busy == null) { vm.send(input.trim()); input = "" } }, enabled = input.isNotBlank() && busy == null) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Orange)
            }
        }
    }
}

@Composable
private fun MessageBubble(m: ChatMessage, vm: AssistantViewModel, onOpenRecipe: (String) -> Unit) {
    val isUser = m.role == ChatRole.USER
    var cards by remember(m.id) { mutableStateOf<List<RecipeSummary>>(emptyList()) }
    LaunchedEffect(m.id) { if (m.recipeIds.isNotEmpty()) cards = vm.cardsFor(m) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Text(m.content, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyLarge)
        }
        if (cards.isNotEmpty()) {
            LazyRow(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(cards, key = { it.id }) { r -> RecipeCard(r, onClick = { onOpenRecipe(r.id) }, modifier = Modifier.width(150.dp)) }
            }
        }
    }
}
