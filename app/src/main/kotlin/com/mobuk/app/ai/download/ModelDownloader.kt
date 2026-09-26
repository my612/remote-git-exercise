package com.mobuk.app.ai.download

import android.content.Context
import android.content.Intent
import android.util.Log
import com.mobuk.app.data.local.PrefsStore
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/**
 * Downloads a `.litertlm` Gemma model into app-private storage with resume support.
 * Large downloads should be started through [ModelDownloadService] so the OS keeps the process alive.
 */
class ModelDownloader(
    private val context: Context,
    private val client: HttpClient,
    private val prefs: PrefsStore,
) {
    val progress = MutableStateFlow(0f)
    val state = MutableStateFlow<State>(State.Idle)
    private val mutex = Mutex()

    sealed interface State {
        data object Idle : State
        data class Running(val downloadedBytes: Long, val totalBytes: Long) : State
        data object Done : State
        data class Failed(val message: String) : State
    }

    val stateFlow: StateFlow<State> get() = state

    fun modelsDir(): File = File(context.filesDir, "models").apply { mkdirs() }

    fun modelFile(): File = File(modelsDir(), "gemma.litertlm")

    private fun partFile(): File = File(modelsDir(), "gemma.litertlm.part")

    fun isDownloading(): Boolean = state.value is State.Running

    fun delete() {
        modelFile().delete()
        partFile().delete()
        state.value = State.Idle
        progress.value = 0f
    }

    /** Starts the foreground service; use this from UI so the download survives backgrounding. */
    fun startInService() {
        val intent = Intent(context, ModelDownloadService::class.java)
        context.startForegroundService(intent)
    }

    suspend fun download(onProgress: (Float) -> Unit = {}) = mutex.withLock {
        if (modelFile().exists()) { state.value = State.Done; progress.value = 1f; return@withLock }
        val userPrefs = prefs.prefs.first()
        val url = userPrefs.gemmaModelUrl.trim()
        if (url.isBlank()) { state.value = State.Failed("No model URL configured"); throw IllegalStateException("No model URL") }
        val part = partFile()
        val existing = if (part.exists()) part.length() else 0L
        try {
            withContext(Dispatchers.IO) {
                client.prepareGet(url) {
                    if (userPrefs.huggingFaceToken.isNotBlank()) header("Authorization", "Bearer ${userPrefs.huggingFaceToken.trim()}")
                    if (existing > 0) header("Range", "bytes=$existing-")
                }.execute { response ->
                    if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
                        error("Access denied (${response.status.value}). Gated models need a Hugging Face token and accepted licence.")
                    }
                    if (response.status.value !in 200..299) error("Server returned HTTP ${response.status.value}")
                    val resumed = response.status == HttpStatusCode.PartialContent
                    val startAt = if (resumed) existing else 0L
                    val total = (response.contentLength() ?: -1L).let { if (it > 0) it + startAt else -1L }
                    val raf = RandomAccessFile(part, "rw")
                    raf.setLength(startAt)
                    raf.seek(startAt)
                    var downloaded = startAt
                    val channel: ByteReadChannel = response.bodyAsChannel()
                    val buffer = ByteArray(256 * 1024)
                    var lastReport = 0L
                    while (true) {
                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read <= 0) break
                        raf.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded - lastReport > 2_000_000L || total in 1..downloaded) {
                            lastReport = downloaded
                            val p = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
                            progress.value = p
                            state.value = State.Running(downloaded, total)
                            onProgress(p)
                        }
                    }
                    raf.close()
                    if (total > 0 && downloaded < total) error("Download ended early ($downloaded of $total bytes)")
                }
            }
            if (!part.renameTo(modelFile())) error("Could not move the model into place")
            progress.value = 1f
            state.value = State.Done
            onProgress(1f)
        } catch (e: Exception) {
            Log.w("ModelDownloader", "Download failed", e)
            state.value = State.Failed(e.message ?: "Download failed")
            throw e
        }
    }
}
