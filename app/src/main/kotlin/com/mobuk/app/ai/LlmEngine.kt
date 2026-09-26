package com.mobuk.app.ai

import kotlinx.coroutines.flow.Flow

enum class EngineId(val label: String) {
    GEMINI_NANO("Gemini Nano"),
    GEMMA_LITERT("Gemma (LiteRT-LM)"),
    RULES("Rules only"),
}

sealed interface EngineAvailability {
    data object Available : EngineAvailability
    data object Downloadable : EngineAvailability
    data class Downloading(val progress: Float) : EngineAvailability
    data class Unavailable(val reason: String) : EngineAvailability
}

data class LlmRequest(
    val systemPrompt: String,
    val userPrompt: String,
    val maxOutputTokens: Int = 512,
    val temperature: Float = 0.3f,
    /** Hint that the answer must be a single JSON object. Engines may use it to constrain decoding. */
    val jsonMode: Boolean = false,
)

/**
 * A reasoning model running entirely on the phone. Implementations wrap Gemini Nano (ML Kit GenAI / AICore)
 * and Gemma (LiteRT-LM). They must be cheap to construct; heavy work happens in [prepare] and [generate].
 */
interface LlmEngine {
    val id: EngineId

    suspend fun availability(): EngineAvailability

    /** Downloads or warms the model. Progress is 0..1. Returns when the engine is ready or throws. */
    suspend fun prepare(onProgress: (Float) -> Unit = {})

    suspend fun generate(request: LlmRequest): String

    fun stream(request: LlmRequest): Flow<String>

    fun close()
}

class LlmUnavailableException(message: String) : Exception(message)
