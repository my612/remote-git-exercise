package com.mobuk.app.ai.engines

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.mobuk.app.ai.EngineAvailability
import com.mobuk.app.ai.EngineId
import com.mobuk.app.ai.LlmEngine
import com.mobuk.app.ai.LlmRequest
import com.mobuk.app.ai.LlmUnavailableException
import com.mobuk.app.ai.download.ModelDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Gemma running in-process through Google's LiteRT-LM (`com.google.ai.edge.litertlm:litertlm-android`).
 * The `.litertlm` model file is downloaded once by [ModelDownloader] into the app's private storage.
 * Works on any arm64 phone with enough RAM (Gemma 3 1B int4 needs ~1.5GB; Gemma 3n E2B ~3GB).
 *
 * Everything that touches the LiteRT-LM SDK lives here so version drift is a one-file fix.
 */
class LiteRtLmEngine(
    private val context: Context,
    private val downloader: ModelDownloader,
    private val preferGpu: Boolean = true,
) : LlmEngine {

    override val id = EngineId.GEMMA_LITERT

    private var engine: Engine? = null
    private val mutex = Mutex()

    override suspend fun availability(): EngineAvailability {
        val file = downloader.modelFile()
        return when {
            file.exists() && file.length() > 10_000_000L -> EngineAvailability.Available
            downloader.isDownloading() -> EngineAvailability.Downloading(downloader.progress.value)
            else -> EngineAvailability.Downloadable
        }
    }

    override suspend fun prepare(onProgress: (Float) -> Unit) {
        if (availability() !is EngineAvailability.Available) {
            downloader.download(onProgress)
        }
        ensureEngine()
        onProgress(1f)
    }

    override suspend fun generate(request: LlmRequest): String = withContext(Dispatchers.Default) {
        val eng = ensureEngine()
        val conversation = newConversation(eng, request)
        try {
            conversation.sendMessage(userMessage(request)).toString()
        } finally {
            runCatching { conversation.close() }
        }
    }

    override fun stream(request: LlmRequest): Flow<String> = flow {
        val eng = ensureEngine()
        val conversation = newConversation(eng, request)
        try {
            conversation.sendMessageAsync(userMessage(request)).collect { chunk -> emit(chunk.toString()) }
        } finally {
            runCatching { conversation.close() }
        }
    }.flowOn(Dispatchers.Default)

    override fun close() {
        runCatching { engine?.close() }
        engine = null
    }

    private suspend fun ensureEngine(): Engine = mutex.withLock {
        engine?.let { return it }
        val file: File = downloader.modelFile()
        if (!file.exists()) throw LlmUnavailableException("Gemma model has not been downloaded yet")
        val cacheDir = File(context.cacheDir, "litertlm").apply { mkdirs() }
        val backend = if (preferGpu) Backend.GPU() else Backend.CPU()
        val created = try {
            Engine(EngineConfig(modelPath = file.absolutePath, backend = backend, cacheDir = cacheDir.absolutePath)).also { it.initialize() }
        } catch (e: Throwable) {
            Log.w("LiteRtLm", "GPU init failed (${e.message}); retrying on CPU")
            Engine(EngineConfig(modelPath = file.absolutePath, backend = Backend.CPU(), cacheDir = cacheDir.absolutePath)).also { it.initialize() }
        }
        engine = created
        created
    }

    private fun newConversation(eng: Engine, request: LlmRequest): Conversation {
        val config = ConversationConfig(
            systemInstruction = Contents.of(request.systemPrompt),
            maxOutputToken = request.maxOutputTokens,
        )
        return eng.createConversation(config)
    }

    private fun userMessage(request: LlmRequest): String =
        if (request.jsonMode) request.userPrompt + "\n\nRespond with a single JSON object and nothing else." else request.userPrompt
}
