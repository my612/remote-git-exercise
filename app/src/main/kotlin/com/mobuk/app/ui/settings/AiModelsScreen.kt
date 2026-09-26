package com.mobuk.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mobuk.app.ai.EngineAvailability
import com.mobuk.app.ai.EngineId
import com.mobuk.app.ai.LlmRequest
import com.mobuk.app.ai.download.ModelDownloader
import com.mobuk.app.di.AppGraph
import com.mobuk.app.domain.model.EnginePreference
import com.mobuk.app.domain.model.UserPrefs
import com.mobuk.app.ui.common.MobTopBar
import com.mobuk.app.ui.common.PillChip
import com.mobuk.app.ui.common.graphViewModel
import com.mobuk.app.ui.theme.Orange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class AiModelsViewModel(val graph: AppGraph) : ViewModel() {
    val prefs = graph.prefs.prefs
    val statuses = graph.llmRouter.statuses
    val active = graph.llmRouter.active
    val downloadState = graph.modelDownloader.stateFlow
    val downloadProgress = graph.modelDownloader.progress
    val nanoProgress = MutableStateFlow<Float?>(null)
    val testResult = MutableStateFlow<String?>(null)

    fun refresh() = viewModelScope.launch { graph.llmRouter.refreshStatuses(); graph.llmRouter.current() }
    fun setPreference(p: EnginePreference) = viewModelScope.launch { graph.prefs.update { it.copy(enginePreference = p) }; refresh() }
    fun setModelUrl(url: String) = viewModelScope.launch { graph.prefs.update { it.copy(gemmaModelUrl = url) } }
    fun setHfToken(t: String) = viewModelScope.launch { graph.prefs.update { it.copy(huggingFaceToken = t) } }
    fun downloadGemma() = graph.modelDownloader.startInService()
    fun deleteGemma() = viewModelScope.launch { graph.llmRouter.engine(EngineId.GEMMA_LITERT)?.close(); graph.modelDownloader.delete(); refresh() }
    fun prepareNano() = viewModelScope.launch {
        nanoProgress.value = 0f
        runCatching { graph.llmRouter.engine(EngineId.GEMINI_NANO)?.prepare { p -> nanoProgress.value = p } }
            .onFailure { testResult.value = "Gemini Nano: ${it.message}" }
        nanoProgress.value = null
        refresh()
    }
    fun test() = viewModelScope.launch {
        testResult.value = "Testing…"
        val engine = graph.llmRouter.current()
        testResult.value = if (engine == null) "No model available; rules will be used." else runCatching {
            val out = engine.generate(LlmRequest("You are a UK cook.", "Suggest one quick dinner idea in one sentence.", maxOutputTokens = 60))
            "${engine.id.label}: $out"
        }.getOrElse { "${engine.id.label} failed: ${it.message}" }
    }
}

@Composable
fun AiModelsScreen(onBack: () -> Unit) {
    val vm = graphViewModel { AiModelsViewModel(it) }
    val prefs by vm.prefs.collectAsStateWithLifecycle(initialValue = UserPrefs())
    val statuses by vm.statuses.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val download by vm.downloadState.collectAsStateWithLifecycle()
    val progress by vm.downloadProgress.collectAsStateWithLifecycle()
    val nanoProgress by vm.nanoProgress.collectAsStateWithLifecycle()
    val testResult by vm.testResult.collectAsStateWithLifecycle()
    var url by remember(prefs.gemmaModelUrl) { mutableStateOf(prefs.gemmaModelUrl) }
    var token by remember(prefs.huggingFaceToken) { mutableStateOf(prefs.huggingFaceToken) }
    LaunchedEffect(Unit) { vm.refresh() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        MobTopBar("On-device AI", onBack = onBack) { TextButton(onClick = vm::refresh) { Text("Refresh") } }
        Section("Active engine") {
            Text(active?.label ?: "None (rule-based recommendations)", style = MaterialTheme.typography.titleMedium, color = if (active != null) Orange else MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Recommendations, Adapt Recipe, nutrition estimates, auto-plan and Ask Mob run on this engine. Nothing leaves your phone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                EnginePreference.entries.forEach { p -> PillChip(p.label, selected = p == prefs.enginePreference, onClick = { vm.setPreference(p) }) }
            }
            Row(Modifier.padding(top = 8.dp)) { OutlinedButton(onClick = vm::test) { Text("Test the model") } }
            testResult?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }
        }
        Section("Gemini Nano (AICore)") {
            val s = statuses.firstOrNull { it.id == EngineId.GEMINI_NANO }?.availability
            Text(describe(s), style = MaterialTheme.typography.bodyMedium)
            Text("Google's on-device model, managed by the system on Pixel 9/10 and other AICore phones. Needs no download by this app beyond what AICore fetches.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (s is EngineAvailability.Downloadable || s is EngineAvailability.Downloading) {
                Spacer(Modifier.height(8.dp))
                if (nanoProgress != null) LinearProgressIndicator(progress = { nanoProgress ?: 0f }, modifier = Modifier.fillMaxWidth()) else Button(onClick = vm::prepareNano) { Text("Download Gemini Nano") }
            }
        }
        Section("Gemma (LiteRT-LM)") {
            val s = statuses.firstOrNull { it.id == EngineId.GEMMA_LITERT }?.availability
            Text(describe(s), style = MaterialTheme.typography.bodyMedium)
            Text("Runs an open Gemma model inside the app on any capable arm64 phone. The default is Gemma 3 1B (about 550MB, int4). For better reasoning on a Pixel, paste the URL of a Gemma 3n E2B .litertlm file (~3GB). Gated Hugging Face models need a token and an accepted licence.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = url, onValueChange = { url = it; vm.setModelUrl(it) }, label = { Text("Model URL (.litertlm)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true)
            OutlinedTextField(value = token, onValueChange = { token = it; vm.setHfToken(it) }, label = { Text("Hugging Face token (optional)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true, visualTransformation = PasswordVisualTransformation())
            Spacer(Modifier.height(8.dp))
            when (val d = download) {
                is ModelDownloader.State.Running -> {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text("${d.downloadedBytes / 1_000_000} MB" + (if (d.totalBytes > 0) " of ${d.totalBytes / 1_000_000} MB" else ""), style = MaterialTheme.typography.labelMedium)
                }
                is ModelDownloader.State.Failed -> {
                    Text(d.message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = vm::downloadGemma) { Text("Retry download") }
                }
                else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (s is EngineAvailability.Available) OutlinedButton(onClick = vm::deleteGemma) { Text("Delete model") }
                    else Button(onClick = vm::downloadGemma) { Text("Download model") }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

private fun describe(a: EngineAvailability?): String = when (a) {
    null -> "Checking…"
    EngineAvailability.Available -> "Ready"
    EngineAvailability.Downloadable -> "Available to download"
    is EngineAvailability.Downloading -> "Downloading… ${(a.progress * 100).toInt()}%"
    is EngineAvailability.Unavailable -> "Unavailable: ${a.reason}"
}
