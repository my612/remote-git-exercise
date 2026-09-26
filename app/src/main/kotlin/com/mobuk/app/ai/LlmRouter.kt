package com.mobuk.app.ai

import android.util.Log
import com.mobuk.app.data.local.PrefsStore
import com.mobuk.app.domain.model.EnginePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class EngineStatus(
    val id: EngineId,
    val availability: EngineAvailability,
)

/**
 * Chooses which on-device model answers a request: Gemini Nano when the Pixel supports it, otherwise Gemma via
 * LiteRT-LM when its model file is present, otherwise nothing (callers fall back to rule-based logic).
 * The choice is re-evaluated on every call because AICore can evict Gemini Nano under storage pressure.
 */
class LlmRouter(
    private val engines: List<LlmEngine>,
    private val prefs: PrefsStore,
) {
    private val _statuses = MutableStateFlow<List<EngineStatus>>(emptyList())
    val statuses: StateFlow<List<EngineStatus>> = _statuses
    private val _active = MutableStateFlow<EngineId?>(null)
    val active: StateFlow<EngineId?> = _active
    private val mutex = Mutex()

    suspend fun refreshStatuses(): List<EngineStatus> {
        val list = engines.map { EngineStatus(it.id, it.availability()) }
        _statuses.value = list
        return list
    }

    fun engine(id: EngineId): LlmEngine? = engines.firstOrNull { it.id == id }

    /** The engine that will serve requests right now, or null when only rules are available. */
    suspend fun current(): LlmEngine? = mutex.withLock {
        val preference = prefs.prefs.first().enginePreference
        val ordered: List<LlmEngine> = when (preference) {
            EnginePreference.RULES_ONLY -> emptyList()
            EnginePreference.GEMINI_NANO -> engines.filter { it.id == EngineId.GEMINI_NANO }
            EnginePreference.GEMMA_LITERT -> engines.filter { it.id == EngineId.GEMMA_LITERT }
            EnginePreference.AUTO -> engines.sortedBy { if (it.id == EngineId.GEMINI_NANO) 0 else 1 }
        }
        val ready = ordered.firstOrNull { it.availability() == EngineAvailability.Available }
        _active.value = ready?.id
        ready
    }

    val hasModel: Boolean get() = _active.value != null

    /**
     * Runs [request] on the current engine. Returns null (instead of throwing) when no model is available so
     * every feature can fall back to deterministic rules.
     */
    suspend fun generateOrNull(request: LlmRequest): String? {
        val engine = current() ?: return null
        return try {
            engine.generate(request).takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w("LlmRouter", "${engine.id} failed: ${e.message}")
            null
        }
    }

    fun stream(engine: LlmEngine, request: LlmRequest): Flow<String> = engine.stream(request)

    fun closeAll() = engines.forEach { runCatching { it.close() } }
}
