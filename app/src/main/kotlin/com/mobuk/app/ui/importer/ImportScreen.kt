package com.mobuk.app.ui.importer

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mobuk.app.data.remote.webimport.RecipeUrlImporter
import com.mobuk.app.di.AppGraph
import com.mobuk.app.domain.model.Recipe
import com.mobuk.app.ui.common.MobTopBar
import com.mobuk.app.ui.common.RecipeRow
import com.mobuk.app.domain.model.RecipeSummary
import com.mobuk.app.ui.common.graphViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class ImportViewModel(val graph: AppGraph) : ViewModel() {
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val result = MutableStateFlow<Recipe?>(null)

    fun import(url: String) = viewModelScope.launch {
        loading.value = true; error.value = null; result.value = null
        when (val r = graph.recipes.importFromUrl(url)) {
            is RecipeUrlImporter.Result.Success -> result.value = r.recipe
            is RecipeUrlImporter.Result.Failure -> error.value = r.message
        }
        loading.value = false
    }
}

@Composable
fun ImportScreen(initialUrl: String, onBack: () -> Unit, onOpenRecipe: (String) -> Unit) {
    val vm = graphViewModel { ImportViewModel(it) }
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var url by remember { mutableStateOf(initialUrl) }

    LaunchedEffect(initialUrl) { if (initialUrl.isNotBlank()) vm.import(initialUrl) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        MobTopBar("Import a recipe", onBack = onBack)
        Column(Modifier.padding(16.dp)) {
            Text("Paste a link from any recipe site. We read the page's schema.org recipe data on your phone: ingredients, method, times, nutrition and the photo.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = url, onValueChange = { url = it }, modifier = Modifier.weight(1f), placeholder = { Text("https://www.bbcgoodfood.com/recipes/…") }, singleLine = true, leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) })
                IconButton(onClick = {
                    val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.primaryClip
                    clip?.getItemAt(0)?.coerceToText(context)?.toString()?.let { text -> Regex("""https?://\S+""").find(text)?.value?.let { url = it } }
                }) { Icon(Icons.Filled.ContentPaste, contentDescription = "Paste") }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { vm.import(url) }, enabled = url.isNotBlank() && !loading, modifier = Modifier.fillMaxWidth()) {
                if (loading) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary); Spacer(Modifier.width(8.dp)); Text("Reading page…") } else Text("Import")
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
            result?.let { r ->
                Text("Imported and saved", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
                RecipeRow(RecipeSummary.from(r), onClick = { onOpenRecipe(r.id) }, subtitle = "${r.ingredients.size} ingredients · ${r.steps.size} steps" + (r.author?.let { " · $it" } ?: ""))
                Button(onClick = { onOpenRecipe(r.id) }, modifier = Modifier.fillMaxWidth()) { Text("Open recipe") }
            }
            Spacer(Modifier.height(24.dp))
            Text("Tip: share a page from your browser to Mob and it lands here automatically.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
