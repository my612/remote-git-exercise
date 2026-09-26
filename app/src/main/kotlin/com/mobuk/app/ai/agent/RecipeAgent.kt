package com.mobuk.app.ai.agent

import android.util.Log
import com.mobuk.app.ai.JsonExtract
import com.mobuk.app.ai.JsonExtract.obj
import com.mobuk.app.ai.JsonExtract.string
import com.mobuk.app.ai.JsonExtract.strings
import com.mobuk.app.ai.LlmRequest
import com.mobuk.app.ai.LlmRouter
import com.mobuk.app.ai.Prompts
import com.mobuk.app.domain.model.ChatMessage
import com.mobuk.app.domain.model.ChatRole
import com.mobuk.app.domain.model.UserPrefs
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class AgentReply(
    val text: String,
    val recipeIds: List<String>,
    val toolTrace: List<String>,
    val usedModel: Boolean,
)

/**
 * A small ReAct-style loop: the on-device model either calls a tool (JSON) or answers. Tool results are fed
 * back as text. Bounded to [maxSteps] iterations so a confused model can never spin.
 * Without a model, [RuleAssistant] handles the common intents so the chat is still useful.
 */
class RecipeAgent(
    private val router: LlmRouter,
    private val tools: ToolRegistry,
    private val maxSteps: Int = 5,
) {
    suspend fun reply(
        userMessage: String,
        history: List<ChatMessage>,
        prefs: UserPrefs,
        onProgress: (String) -> Unit = {},
    ): AgentReply {
        val engine = router.current()
        if (engine == null) {
            return RuleAssistant(tools).reply(userMessage)
        }
        val system = Prompts.agentSystem(tools.describe(), prefs)
        val transcript = StringBuilder()
        history.takeLast(6).forEach { m ->
            when (m.role) {
                ChatRole.USER -> transcript.append("User: ").append(m.content.take(300)).append('\n')
                ChatRole.ASSISTANT -> transcript.append("Assistant: ").append(m.content.take(300)).append('\n')
                else -> Unit
            }
        }
        transcript.append("User: ").append(userMessage.trim()).append('\n')
        val trace = mutableListOf<String>()
        val surfaced = linkedSetOf<String>()

        repeat(maxSteps) { step ->
            val request = LlmRequest(systemPrompt = system, userPrompt = transcript.toString(), maxOutputTokens = 350, temperature = 0.2f, jsonMode = true)
            val raw = try {
                engine.generate(request)
            } catch (e: Exception) {
                Log.w("RecipeAgent", "generation failed: ${e.message}")
                return RuleAssistant(tools).reply(userMessage).copy(toolTrace = trace + "model error: ${e.message}")
            }
            val json = JsonExtract.firstObject(raw)
            val toolName = json?.string("tool")
            val final = json?.string("final") ?: json?.string("answer")
            when {
                toolName != null && final == null -> {
                    val tool = tools.get(toolName)
                    val args = json.obj("args") ?: json.obj("arguments") ?: buildJsonObject { }
                    onProgress("Using ${tool?.name ?: toolName}…")
                    val output = if (tool == null) {
                        ToolOutput("Unknown tool '$toolName'. Available: ${tools.all().joinToString { it.name }}", isError = true)
                    } else {
                        runCatching { tool.invoke(args) }.getOrElse { ToolOutput("Tool failed: ${it.message}", isError = true) }
                    }
                    surfaced += output.recipeIds
                    trace += "$toolName(${args.entries.joinToString { "${it.key}=${it.value}" }})"
                    transcript.append("Assistant: ").append(raw.trim().take(400)).append('\n')
                    transcript.append("Tool result (").append(toolName).append("): ").append(output.text.take(1500)).append('\n')
                    if (step == maxSteps - 1) {
                        return AgentReply(
                            text = "Here's what I found:\n" + output.text.take(800),
                            recipeIds = surfaced.toList(),
                            toolTrace = trace,
                            usedModel = true,
                        )
                    }
                }
                final != null -> {
                    val ids = (json?.strings("recipe_ids").orEmpty() + surfaced).distinct()
                    return AgentReply(final, ids, trace, usedModel = true)
                }
                else -> {
                    // Not JSON: treat the model text as the final answer.
                    return AgentReply(raw.trim().ifBlank { "Sorry, I didn't catch that. Try asking for recipe ideas or a meal plan." }, surfaced.toList(), trace, usedModel = true)
                }
            }
        }
        return AgentReply("I couldn't finish that one. Try rephrasing?", surfaced.toList(), trace, usedModel = true)
    }

    companion object {
        fun args(vararg pairs: Pair<String, Any?>): JsonObject = buildJsonObject {
            for ((k, v) in pairs) when (v) {
                null -> Unit
                is String -> put(k, v)
                is Int -> put(k, v)
                is Long -> put(k, v)
                is Double -> put(k, v)
                is Boolean -> put(k, v)
                else -> put(k, v.toString())
            }
        }
    }
}

/**
 * Deterministic assistant used when no on-device model is available. Recognises the common intents
 * ("find me a quick veggie curry", "plan my week", "add milk to my list", "what's on my planner").
 */
class RuleAssistant(private val tools: ToolRegistry) {

    suspend fun reply(message: String): AgentReply {
        val text = message.trim()
        val lower = text.lowercase()
        val trace = mutableListOf<String>()

        suspend fun call(name: String, args: JsonObject): ToolOutput? {
            val tool = tools.get(name) ?: return null
            trace += name
            return runCatching { tool.invoke(args) }.getOrNull()
        }

        // Shopping list additions: "add eggs and milk to my shopping list"
        Regex("""^(?:please\s+)?add\s+(.+?)\s+to\s+(?:my\s+)?(?:shopping\s+)?list""").find(lower)?.let { m ->
            val items = m.groupValues[1].split(Regex(",|\\band\\b")).map { it.trim() }.filter { it.isNotBlank() }
            val out = call("add_to_shopping_list", RecipeAgent.args("items" to items.joinToString(",")))
            return AgentReply(out?.text ?: "Added ${items.joinToString()} to your list.", emptyList(), trace, usedModel = false)
        }
        if (lower.contains("planner") || lower.contains("what's for dinner") || lower.contains("whats for dinner") || lower.contains("this week")) {
            if (lower.startsWith("plan") || lower.contains("plan my")) {
                val out = call("plan_week", RecipeAgent.args("days" to 7))
                return AgentReply(out?.text ?: "Planned your week.", out?.recipeIds.orEmpty(), trace, usedModel = false)
            }
            val out = call("get_planner", buildJsonObject { })
            return AgentReply(out?.text ?: "Your planner is empty.", out?.recipeIds.orEmpty(), trace, usedModel = false)
        }
        if (lower.contains("shopping list") || lower.contains("what do i need to buy")) {
            val out = call("get_shopping_list", buildJsonObject { })
            return AgentReply(out?.text ?: "Your shopping list is empty.", emptyList(), trace, usedModel = false)
        }
        if (lower.startsWith("plan ") || lower.contains("meal plan")) {
            val out = call("plan_week", RecipeAgent.args("days" to 7))
            return AgentReply(out?.text ?: "Planned your week.", out?.recipeIds.orEmpty(), trace, usedModel = false)
        }
        // Fridge search: "what can I make with eggs, spinach and feta"
        Regex("""(?:make|cook|do)\s+with\s+(.+)$""").find(lower)?.let { m ->
            val ingredients = m.groupValues[1].replace("?", "").split(Regex(",|\\band\\b")).map { it.trim() }.filter { it.isNotBlank() }
            val out = call("fridge_search", RecipeAgent.args("ingredients" to ingredients.joinToString(",")))
            return AgentReply(out?.text ?: "No matches.", out?.recipeIds.orEmpty(), trace, usedModel = false)
        }
        // Default: recipe search with light intent extraction.
        val diet = listOf("vegan", "vegetarian", "veggie", "gluten free", "gluten-free", "dairy free", "dairy-free", "pescatarian", "high protein").firstOrNull { lower.contains(it) }
        val quick = listOf("quick", "fast", "15 minute", "20 minute", "30 minute", "easy").any { lower.contains(it) }
        val query = lower
            .replace(Regex("""\b(find|show|give|get|suggest|recommend|me|some|a|an|the|please|recipes?|ideas?|for|something|i|want|to|cook|make|tonight|dinner|lunch|breakfast|what|can|should|could|you)\b"""), " ")
            .replace(diet ?: "", "")
            .replace(Regex("""\b(quick|fast|easy)\b"""), "")
            .replace(Regex("\\s+"), " ").trim().trim('?', '.', '!')
        val out = call("search_recipes", RecipeAgent.args("query" to query.ifBlank { null }, "diet" to diet, "max_minutes" to if (quick) 30 else null))
        val intro = if (query.isBlank()) "Here are some ideas" else "Here's what I found for \"$query\""
        return AgentReply(
            text = out?.text?.let { "$intro:\n$it" } ?: "I couldn't search right now. Check your connection and try again.",
            recipeIds = out?.recipeIds.orEmpty(),
            toolTrace = trace,
            usedModel = false,
        )
    }
}
