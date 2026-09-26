package com.mobuk.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mobuk.app.di.AppGraph
import com.mobuk.app.domain.model.McpServerConfig
import com.mobuk.app.ui.common.EmptyState
import com.mobuk.app.ui.common.MobTopBar
import com.mobuk.app.ui.common.graphViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class McpViewModel(val graph: AppGraph) : ViewModel() {
    val servers = graph.mcp.servers
    val testing = MutableStateFlow<Long?>(null)
    val lastTest = MutableStateFlow<String?>(null)

    fun add(name: String, url: String, token: String?) = viewModelScope.launch {
        val id = graph.mcp.add(McpServerConfig(name = name.trim(), url = url.trim(), bearerToken = token?.trim()?.ifBlank { null }))
        test(McpServerConfig(id = id, name = name.trim(), url = url.trim(), bearerToken = token?.trim()?.ifBlank { null }))
    }
    fun remove(id: Long) = viewModelScope.launch { graph.mcp.remove(id) }
    fun setEnabled(s: McpServerConfig, enabled: Boolean) = viewModelScope.launch { graph.mcp.update(s.copy(enabled = enabled)) }
    fun test(s: McpServerConfig) = viewModelScope.launch {
        testing.value = s.id
        val result = graph.mcp.test(s)
        lastTest.value = result.fold({ tools -> "${s.name}: ${tools.size} tools · " + tools.take(6).joinToString { it.name } }, { e -> "${s.name}: ${e.message}" })
        testing.value = null
    }
}

@Composable
fun McpServersScreen(onBack: () -> Unit) {
    val vm = graphViewModel { McpViewModel(it) }
    val servers by vm.servers.collectAsStateWithLifecycle(initialValue = emptyList())
    val testing by vm.testing.collectAsStateWithLifecycle()
    val lastTest by vm.lastTest.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        MobTopBar("MCP servers", onBack = onBack) { IconButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, contentDescription = "Add server") } }
        Text(
            "Connect Model Context Protocol servers over Streamable HTTP. Their tools become available to Ask Mob on this phone, e.g. a hosted recipe search server. Ready-made options: pipeworx-io/mcp-recipes (TheMealDB), suraj-yadav-aiml/recipe-mcp, ddsky/spoonacular-mcp, recipe-mcp/recipe-mcp — run one on your laptop or a cloud host and paste its /mcp URL.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp),
        )
        lastTest?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp)) }
        if (servers.isEmpty()) {
            EmptyState("No servers yet", "Add an MCP server URL to give the assistant more recipe tools.", actionLabel = "Add server", onAction = { adding = true })
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(servers, key = { it.id }) { s ->
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(s.name, style = MaterialTheme.typography.titleMedium)
                                    Text(s.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = s.enabled, onCheckedChange = { vm.setEnabled(s, it) })
                            }
                            Text(s.lastStatus ?: "Not tested yet", style = MaterialTheme.typography.bodySmall, color = if (s.lastStatus?.startsWith("Error") == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                            if (s.toolNames.isNotEmpty()) Text("Tools: ${s.toolNames.joinToString()}", style = MaterialTheme.typography.bodySmall)
                            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { vm.test(s) }, enabled = testing == null) { Text(if (testing == s.id) "Testing…" else "Test connection") }
                                TextButton(onClick = { vm.remove(s.id) }) { Icon(Icons.Filled.Delete, contentDescription = null); Spacer(Modifier.height(0.dp)); Text(" Remove") }
                            }
                        }
                    }
                }
            }
        }
    }
    if (adding) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        var token by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Add MCP server") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Streamable HTTP URL (…/mcp)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text("Bearer token (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), visualTransformation = PasswordVisualTransformation())
                }
            },
            confirmButton = { Button(onClick = { if (name.isNotBlank() && url.isNotBlank()) { vm.add(name, url, token); adding = false } }) { Text("Add") } },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
        )
    }
}
