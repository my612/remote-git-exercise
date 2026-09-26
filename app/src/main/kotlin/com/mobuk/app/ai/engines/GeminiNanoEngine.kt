package com.mobuk.app.ai.engines

import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.mobuk.app.ai.EngineAvailability
import com.mobuk.app.ai.EngineId
import com.mobuk.app.ai.LlmEngine
import com.mobuk.app.ai.LlmRequest
import com.mobuk.app.ai.LlmUnavailableException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Gemini Nano through the ML Kit GenAI Prompt API (`com.google.mlkit:genai-prompt`). The model is managed by
 * AICore and shared system-wide, so nothing is bundled with the app. Supported on Pixel 9/10 series and other
 * AICore devices; elsewhere [availability] reports Unavailable and the router falls back to Gemma or rules.
 *
 * NOTE: the Prompt API is in beta and its surface has moved between releases. Everything that touches the SDK
 * is confined to this file so an API rename is a one-file fix.
 */
class GeminiNanoEngine : LlmEngine {

    override val id = EngineId.GEMINI_NANO

    private val model: GenerativeModel by lazy { Generation.getClient() }

    override suspend fun availability(): EngineAvailability = try {
        when (model.checkStatus()) {
            FeatureStatus.AVAILABLE -> EngineAvailability.Available
            FeatureStatus.DOWNLOADABLE -> EngineAvailability.Downloadable
            FeatureStatus.DOWNLOADING -> EngineAvailability.Downloading(0f)
            else -> EngineAvailability.Unavailable("Gemini Nano is not supported on this device")
        }
    } catch (e: Throwable) {
        Log.w("GeminiNano", "checkStatus failed: ${e.message}")
        EngineAvailability.Unavailable("AICore not available: ${e.message ?: e.javaClass.simpleName}")
    }

    override suspend fun prepare(onProgress: (Float) -> Unit) {
        when (availability()) {
            EngineAvailability.Available -> return
            is EngineAvailability.Unavailable -> throw LlmUnavailableException("Gemini Nano unavailable on this device")
            else -> Unit
        }
        var total = 0L
        model.download().collect { status ->
            when (status) {
                is DownloadStatus.DownloadStarted -> total = status.bytesToDownload
                is DownloadStatus.DownloadProgress -> if (total > 0) onProgress(status.totalBytesDownloaded.toFloat() / total)
                is DownloadStatus.DownloadCompleted -> onProgress(1f)
                is DownloadStatus.DownloadFailed -> throw LlmUnavailableException("Gemini Nano download failed: ${status.e.message}")
            }
        }
    }

    override suspend fun generate(request: LlmRequest): String {
        if (availability() != EngineAvailability.Available) throw LlmUnavailableException("Gemini Nano not ready")
        val response = model.generateContent(composePrompt(request))
        return response.candidates.firstOrNull()?.text.orEmpty()
    }

    override fun stream(request: LlmRequest): Flow<String> = flow {
        if (availability() != EngineAvailability.Available) throw LlmUnavailableException("Gemini Nano not ready")
        model.generateContentStream(composePrompt(request))
            .map { chunk -> chunk.candidates.firstOrNull()?.text.orEmpty() }
            .collect { emit(it) }
    }

    override fun close() {
        runCatching { model.close() }
    }

    /** Nano is a single-turn model without a separate system role, so the system prompt is prepended. */
    private fun composePrompt(request: LlmRequest): String = buildString {
        append(request.systemPrompt.trim())
        append("\n\n")
        append(request.userPrompt.trim())
        if (request.jsonMode) append("\n\nRespond with a single JSON object and nothing else.")
    }
}
