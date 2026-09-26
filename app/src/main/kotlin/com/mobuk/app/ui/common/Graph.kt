package com.mobuk.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mobuk.app.di.AppGraph

val LocalAppGraph = compositionLocalOf<AppGraph> { error("AppGraph not provided") }

/** Creates a ViewModel from the app graph without a DI framework. */
@Composable
inline fun <reified VM : ViewModel> graphViewModel(key: String? = null, crossinline create: (AppGraph) -> VM): VM {
    val graph = LocalAppGraph.current
    return viewModel(key = key, factory = viewModelFactory { initializer { create(graph) } })
}
