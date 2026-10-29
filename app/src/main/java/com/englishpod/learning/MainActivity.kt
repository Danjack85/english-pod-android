package com.englishpod.learning

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.glance.appwidget.updateAll
import com.englishpod.learning.ui.AppRoot
import com.englishpod.learning.ui.theme.EnglishPodTheme
import com.englishpod.learning.widget.StatsWidget
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
        consumeAutoResumeIntent(intent)

        setContent {
            val settings by EnglishPodApp.store.settings.collectAsStateWithLifecycle()
            val darkTheme = when (settings.themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }

            val view = LocalView.current
            SideEffect {
                val window = (view.context as? ComponentActivity)?.window ?: return@SideEffect
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }

            EnglishPodTheme(darkTheme = darkTheme, dynamicColor = settings.dynamicColor) {
                AppRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeAutoResumeIntent(intent)
    }

    private fun consumeAutoResumeIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_AUTO_RESUME, false) == true) {
            EnglishPodApp.instance.autoResumeRequested = true
        }
    }

    override fun onStop() {
        super.onStop()
        EnglishPodApp.store.flushNow()
        // Keep the home-screen widget in sync with today's numbers.
        lifecycleScope.launch {
            runCatching { StatsWidget().updateAll(this@MainActivity) }
        }
    }

    companion object {
        const val EXTRA_AUTO_RESUME = "auto_resume"
    }
}
