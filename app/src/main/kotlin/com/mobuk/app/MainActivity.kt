package com.mobuk.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.mobuk.app.ui.common.LocalAppGraph
import com.mobuk.app.ui.navigation.MobNavHost
import com.mobuk.app.ui.navigation.Routes
import com.mobuk.app.ui.theme.MobTheme

class MainActivity : ComponentActivity() {

    private var sharedUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sharedUrl = extractUrl(intent)
        val graph = (application as MobApp).graph
        setContent {
            MobTheme {
                CompositionLocalProvider(LocalAppGraph provides graph) {
                    val prefs by graph.prefs.prefs.collectAsStateWithLifecycle(initialValue = null)
                    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
                        val p = prefs
                        if (p != null) {
                            val navController = rememberNavController()
                            val start = if (p.onboardingDone) Routes.HOME else Routes.ONBOARDING
                            MobNavHost(navController = navController, startDestination = start, pendingImportUrl = sharedUrl)
                            val url = sharedUrl
                            LaunchedEffect(url, p.onboardingDone) {
                                if (url != null && p.onboardingDone) {
                                    navController.navigate(Routes.import(url))
                                    sharedUrl = null
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractUrl(intent)?.let { sharedUrl = it }
    }

    private fun extractUrl(intent: Intent?): String? {
        if (intent == null) return null
        if (intent.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            return Regex("""https?://\S+""").find(text)?.value
        }
        if (intent.action == Intent.ACTION_VIEW) {
            val data = intent.data ?: return null
            if (data.scheme == "mobuk" && data.host == "import") return data.getQueryParameter("url")
        }
        return null
    }
}
