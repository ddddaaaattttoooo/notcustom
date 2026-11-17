package dev.notcustom.app.launcher

import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
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
        viewModel.startWidgets()
        setContent {
            NotCustomTheme {
                LauncherRoot(viewModel)
            }
        }
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshAccessibility()
        viewModel.onActivityResumed()
    }

    override fun onDestroy() {
        viewModel.stopWidgets()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.closeDrawer()
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == LauncherApps.ACTION_CONFIRM_PIN_APPWIDGET) {
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
