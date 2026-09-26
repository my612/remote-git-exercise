package com.mobuk.app.ai.agent

import kotlinx.serialization.json.JsonObject

/**
 * A capability the on-device agent can invoke. Built-in tools wrap the app's repositories; MCP tools are
 * adapted from remote servers. Results are plain text kept short for small context windows.
 */
data class AgentTool(
    val name: String,
    val description: String,
    /** Human readable parameter list, e.g. "query: string (required), diet: string". */
    val parameters: String,
    val invoke: suspend (JsonObject) -> ToolOutput,
)

data class ToolOutput(
    val text: String,
    /** Recipe ids surfaced by this tool so the chat UI can render cards. */
    val recipeIds: List<String> = emptyList(),
    val isError: Boolean = false,
)

class ToolRegistry {
    private val tools = linkedMapOf<String, AgentTool>()

    fun register(tool: AgentTool) { tools[tool.name] = tool }

    fun registerAll(list: List<AgentTool>) = list.forEach { register(it) }

    fun remove(name: String) { tools.remove(name) }

    fun get(name: String): AgentTool? = tools[name] ?: tools.values.firstOrNull { it.name.equals(name, true) }

    fun all(): List<AgentTool> = tools.values.toList()

    fun describe(): String = tools.values.joinToString("\n") { "- ${it.name}(${it.parameters}): ${it.description}" }
}
