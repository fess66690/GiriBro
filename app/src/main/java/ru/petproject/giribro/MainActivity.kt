package ru.petproject.giribro

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.petproject.giribro.data.ThemeMode
import ru.petproject.giribro.ui.AppRoot
import ru.petproject.giribro.ui.theme.GiriBroTheme

class MainActivity : ComponentActivity() {

    private var shortcutRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        shortcutRoute = intent.shortcutRoute()
        enableEdgeToEdge()
        setContent {
            val vm: AppViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()

            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }

            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as Activity).window
                    WindowCompat.getInsetsController(window, view)
                        .isAppearanceLightStatusBars = !darkTheme
                }
            }

            GiriBroTheme(darkTheme = darkTheme) {
                AppRoot(
                    vm = vm,
                    shortcutRoute = shortcutRoute,
                    onShortcutConsumed = {
                        shortcutRoute = null
                        intent?.removeExtra(EXTRA_SHORTCUT)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        shortcutRoute = intent.shortcutRoute()
    }

    private fun Intent.shortcutRoute(): String? = when (getStringExtra(EXTRA_SHORTCUT)) {
        "new_workout" -> "new"
        "timer" -> "timer"
        "body" -> "body"
        "settings" -> "settings"
        else -> null
    }

    companion object {
        const val EXTRA_SHORTCUT = "shortcut"
    }
}
