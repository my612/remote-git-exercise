package com.mobuk.app.data.repository

import com.mobuk.app.ai.agent.AgentReply
import com.mobuk.app.ai.agent.BuiltInTools
import com.mobuk.app.ai.agent.RecipeAgent
import com.mobuk.app.ai.agent.ToolRegistry
import com.mobuk.app.data.local.ChatDao
import com.mobuk.app.data.local.PrefsStore
import com.mobuk.app.data.local.toDomain
import com.mobuk.app.data.local.toEntity
import com.mobuk.app.domain.model.ChatMessage
import com.mobuk.app.domain.model.ChatRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Persists the "Ask Mob" conversation and runs the agent. */
class ChatRepository(
    private val dao: ChatDao,
    private val prefs: PrefsStore,
    private val agent: RecipeAgent,
    private val tools: BuiltInTools,
    private val registry: ToolRegistry,
) {
    val messages: Flow<List<ChatMessage>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun send(text: String, onProgress: (String) -> Unit = {}): AgentReply {
        dao.insert(ChatMessage(role = ChatRole.USER, content = text).toEntity())
        val history = dao.latest(12).reversed().map { it.toDomain() }
        // MCP servers may have been added since start-up; pick up their tools lazily.
        runCatching { tools.registerMcpTools(registry) }
        val reply = agent.reply(text, history.dropLast(1), prefs.prefs.first(), onProgress)
        dao.insert(ChatMessage(role = ChatRole.ASSISTANT, content = reply.text, recipeIds = reply.recipeIds).toEntity())
        return reply
    }

    suspend fun clear() = dao.clear()
}
