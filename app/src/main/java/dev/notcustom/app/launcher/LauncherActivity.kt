package dev.notcustom.app.launcher

import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dev.notcustom.app.launcher.ui.LauncherRoot
import dev.notcustom.app.ui.theme.NotCustomTheme

class LauncherActivity : ComponentActivity() {
    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        display?.supportedModes?.maxByOrNull { it.refreshRate }?.let { best ->
            window.attributes = window.attributes.apply {
                preferredDisplayModeId = best.modeId
            }
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        // Home must never finish on Back; Compose BackHandlers registered later still take priority.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })
        setContent {
            NotCustomTheme {
                LauncherRoot(viewModel)
            }
        }
        handleIntent(intent)
    }

    // Widgets only need updates while Home is visible; listening in the background costs battery.
    override fun onStart() {
        super.onStart()
        viewModel.startWidgets()
    }

    override fun onStop() {
        viewModel.stopWidgets()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshAccessibility()
        viewModel.onActivityResumed()
    }

    override fun onNewIntent(intent: Intent) {
        // Focus is still ours only when Home was already in front when the button was pressed.
        val alreadyOnHome = hasWindowFocus()
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            viewModel.onHomePressed(alreadyOnHome)
        }
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == LauncherApps.ACTION_CONFIRM_PIN_APPWIDGET ||
            intent?.action == LauncherApps.ACTION_CONFIRM_PIN_SHORTCUT
        ) {
            viewModel.handlePinRequest(intent)
            return
        }
        if (intent?.getBooleanExtra(EXTRA_OPEN_SETTINGS, false) == true) {
            viewModel.openSettings()
        }
    }

    companion object {
        const val EXTRA_OPEN_SETTINGS = "dev.notcustom.app.extra.OPEN_SETTINGS"
    }
}
