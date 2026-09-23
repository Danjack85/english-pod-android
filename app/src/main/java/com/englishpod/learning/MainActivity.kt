package com.englishpod.learning

import android.Manifest
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
import com.englishpod.learning.ui.AppRoot
import com.englishpod.learning.ui.theme.EnglishPodTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }

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

    override fun onStop() {
        super.onStop()
        EnglishPodApp.store.flushNow()
    }
}
