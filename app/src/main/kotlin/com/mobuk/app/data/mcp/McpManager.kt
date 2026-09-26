package com.mobuk.app.data.mcp

import android.util.Log
import com.mobuk.app.data.local.AppJson
import com.mobuk.app.data.local.McpServerDao
import com.mobuk.app.data.local.toDomain
import com.mobuk.app.data.local.toEntity
import com.mobuk.app.domain.model.McpServerConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.header
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.client.mcpStreamableHttpTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A tool exposed by a remote MCP server, flattened for the on-device agent. */
data class McpTool(
    val serverId: Long,
    val serverName: String,
    val name: String,
    val description: String,
    val inputSchema: JsonObject?,
    val required: List<String>,
) {
    /** Unique name across servers, e.g. "recipes__search_meals". */
    val qualifiedName: String get() = "${serverName.lowercase().replace(Regex("[^a-z0-9]+"), "_")}__$name"
}

data class McpToolResult(
    val text: String,
    val structured: JsonObject?,
    val isError: Boolean,
)

/**
 * Connects to user-configured MCP servers over Streamable HTTP using the official Kotlin SDK.
 * Sessions are lazy and cached per server; failures degrade gracefully so the app keeps working offline.
 *
 * Ready-made recipe servers you can point this at (see README): pipeworx-io/mcp-recipes (TheMealDB),
 * suraj-yadav-aiml/recipe-mcp (FastMCP, remote-deployable), ddsky/spoonacular-mcp, recipe-mcp/recipe-mcp.
 */
class McpManager(private val dao: McpServerDao) {

    private val http: HttpClient = HttpClient(OkHttp) {
        install(SSE)
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 120_000
        }
    }

    private val sessions = mutableMapOf<Long, Client>()
    private val toolCache = mutableMapOf<Long, List<McpTool>>()
    private val mutex = Mutex()

    val servers: Flow<List<McpServerConfig>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun add(config: McpServerConfig): Long = dao.insert(config.toEntity())

    suspend fun update(config: McpServerConfig) {
        val existing = dao.getById(config.id)
        dao.update(config.toEntity(existing?.createdAt ?: System.currentTimeMillis()))
        disconnect(config.id)
    }

    suspend fun remove(id: Long) {
        disconnect(id)
        dao.delete(id)
    }

    suspend fun disconnect(id: Long) = mutex.withLock {
        sessions.remove(id)?.let { runCatching { it.close() } }
        toolCache.remove(id)
    }

    /** Connects (or reuses) a session and returns its tools. Updates the stored status for the Settings screen. */
    suspend fun tools(server: McpServerConfig, forceRefresh: Boolean = false): List<McpTool> {
        if (!forceRefresh) mutex.withLock { toolCache[server.id] }?.let { return it }
        return try {
            val client = session(server)
            val result = withTimeout(30_000) { client.listTools() }
            val tools = result.tools.map { it.toMcpTool(server) }
            mutex.withLock { toolCache[server.id] = tools }
            dao.setStatus(server.id, "Connected · ${tools.size} tools", tools.joinToString(",") { it.name })
            tools
        } catch (e: Exception) {
            Log.w("Mcp", "listTools failed for ${server.name}: ${e.message}")
            disconnect(server.id)
            dao.setStatus(server.id, "Error: ${e.message?.take(80) ?: "connection failed"}", "")
            emptyList()
        }
    }

    /** All tools from every enabled server. Never throws. */
    suspend fun allTools(): List<McpTool> = dao.enabled().flatMap { tools(it.toDomain()) }

    suspend fun callTool(tool: McpTool, arguments: Map<String, Any?>): McpToolResult {
        val server = dao.getById(tool.serverId)?.toDomain()
            ?: return McpToolResult("Server no longer configured", null, true)
        return try {
            val client = session(server)
            val result = withTimeout(60_000) { client.callTool(name = tool.name, arguments = arguments) }
            val text = result.content.filterIsInstance<TextContent>().joinToString("\n") { it.text }
            McpToolResult(text.ifBlank { result.structuredContent?.toString() ?: "" }, result.structuredContent, result.isError == true)
        } catch (e: Exception) {
            Log.w("Mcp", "callTool ${tool.name} failed: ${e.message}")
            disconnect(server.id)
            McpToolResult("Tool call failed: ${e.message}", null, true)
        }
    }

    /** Probe used by the Settings screen's "Test connection" button. */
    suspend fun test(server: McpServerConfig): Result<List<McpTool>> = runCatching {
        disconnect(server.id)
        val tools = tools(server, forceRefresh = true)
        if (tools.isEmpty() && dao.getById(server.id)?.lastStatus?.startsWith("Error") == true) {
            error(dao.getById(server.id)?.lastStatus ?: "Connection failed")
        }
        tools
    }

    private suspend fun session(server: McpServerConfig): Client = mutex.withLock {
        sessions[server.id]?.let { return it }
        val transport = http.mcpStreamableHttpTransport(server.url) {
            server.bearerToken?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
        }
        val client = Client(Implementation(name = "MobUK Android", version = "1.0.0"), ClientOptions())
        withTimeout(30_000) { client.connect(transport) }
        sessions[server.id] = client
        client
    }

    private fun Tool.toMcpTool(server: McpServerConfig) = McpTool(
        serverId = server.id,
        serverName = server.name,
        name = name,
        description = description ?: title ?: name,
        inputSchema = inputSchema.properties,
        required = inputSchema.required.orEmpty(),
    )

    companion object {
        /** Converts a JSON object of arguments (as produced by the LLM) into the plain map the SDK expects. */
        fun jsonToArguments(json: JsonObject): Map<String, Any?> = json.mapValues { (_, v) -> jsonToAny(v) }

        private fun jsonToAny(el: JsonElement): Any? = when (el) {
            is JsonPrimitive -> when {
                el.isString -> el.content
                el.content == "true" -> true
                el.content == "false" -> false
                el.content == "null" -> null
                el.content.contains('.') -> el.content.toDoubleOrNull() ?: el.content
                else -> el.content.toLongOrNull()?.let { if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else it } ?: el.content
            }
            is JsonObject -> el.mapValues { jsonToAny(it.value) }
            is kotlinx.serialization.json.JsonArray -> el.map { jsonToAny(it) }
        }

        /** Renders a tool's schema compactly for the model prompt. */
        fun describeSchema(schema: JsonObject?, required: List<String>): String {
            if (schema == null || schema.isEmpty()) return "{}"
            return schema.entries.joinToString(", ", prefix = "{", postfix = "}") { (k, v) ->
                val type = (v as? JsonObject)?.get("type")?.let { runCatching { it.jsonPrimitive.content }.getOrNull() } ?: "any"
                val desc = (v as? JsonObject)?.get("description")?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
                buildString {
                    append(k); append(": "); append(type)
                    if (k in required) append(" (required)")
                    if (desc != null) append(" – ").append(desc.take(80))
                }
            }
        }

        fun parseJsonObject(text: String): JsonObject? = runCatching { AppJson.parseToJsonElement(text).jsonObject }.getOrNull()

        fun emptyObject(): JsonObject = buildJsonObject { }
    }
}
