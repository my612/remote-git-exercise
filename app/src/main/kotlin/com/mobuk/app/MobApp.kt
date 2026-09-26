package com.mobuk.app

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.mobuk.app.di.AppGraph
import kotlinx.coroutines.launch

class MobApp : Application(), SingletonImageLoader.Factory {

    val graph: AppGraph by lazy { AppGraph(this) }

    override fun onCreate() {
        super.onCreate()
        graph.appScope.launch {
            runCatching { graph.recipes.evictStale() }
            runCatching { graph.shopping.syncFromPlanner() }
            runCatching { graph.llmRouter.refreshStatuses() }
        }
    }

    override fun newImageLoader(context: coil3.PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.2).build() }
            .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(200L * 1024 * 1024).build() }
            .crossfade(true)
            .build()
}
