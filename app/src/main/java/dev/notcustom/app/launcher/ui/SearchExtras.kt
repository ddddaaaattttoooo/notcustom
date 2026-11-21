package dev.notcustom.app.launcher.ui

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.notcustom.app.launcher.calculate
import dev.notcustom.app.launcher.formatResult
import dev.notcustom.app.ui.theme.Muted
import dev.notcustom.app.ui.theme.Paper

private class SettingsShortcut(val label: String, val action: String, val keywords: String = "")

private val settingsShortcuts = listOf(
    SettingsShortcut("Wi-Fi", Settings.ACTION_WIFI_SETTINGS, "wifi internet network"),
    SettingsShortcut("Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS, "headphones pair"),
    SettingsShortcut("Mobile network", Settings.ACTION_WIRELESS_SETTINGS, "data sim cellular"),
    SettingsShortcut("Airplane mode", Settings.ACTION_AIRPLANE_MODE_SETTINGS, "flight"),
    SettingsShortcut("Display", Settings.ACTION_DISPLAY_SETTINGS, "brightness screen dark"),
    SettingsShortcut("Sound", Settings.ACTION_SOUND_SETTINGS, "volume ringtone vibrate"),
    SettingsShortcut("Battery", Intent.ACTION_POWER_USAGE_SUMMARY, "power"),
    SettingsShortcut("Apps", Settings.ACTION_APPLICATION_SETTINGS, "applications"),
    SettingsShortcut("Notifications", "android.settings.ALL_APPS_NOTIFICATION_SETTINGS"),
    SettingsShortcut("Storage", Settings.ACTION_INTERNAL_STORAGE_SETTINGS, "space memory"),
    SettingsShortcut("Location", Settings.ACTION_LOCATION_SOURCE_SETTINGS, "gps"),
    SettingsShortcut("Security", Settings.ACTION_SECURITY_SETTINGS, "lock password fingerprint"),
    SettingsShortcut("Accessibility", Settings.ACTION_ACCESSIBILITY_SETTINGS),
    SettingsShortcut("Language", Settings.ACTION_LOCALE_SETTINGS, "keyboard"),
    SettingsShortcut("Date & time", Settings.ACTION_DATE_SETTINGS, "clock timezone"),
    SettingsShortcut("NFC", Settings.ACTION_NFC_SETTINGS, "tap pay"),
    SettingsShortcut("Developer options", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, "usb debugging"),
    SettingsShortcut("About phone", Settings.ACTION_DEVICE_INFO_SETTINGS, "version model"),
    SettingsShortcut("Default home app", Settings.ACTION_HOME_SETTINGS, "launcher"),
    SettingsShortcut("All settings", Settings.ACTION_SETTINGS, "settings")
)

/** Extra search results above the apps: a calculator answer, Android settings pages and a web search. */
@Composable
fun SearchExtras(query: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val q = query.trim()
    val result = remember(q) { calculate(q)?.let(::formatResult) }
    val settings = remember(q) {
        if (q.length < 2) emptyList() else settingsShortcuts.filter {
            it.label.contains(q, ignoreCase = true) || it.keywords.split(' ').any { k -> k.startsWith(q, ignoreCase = true) }
        }.take(3)
    }
    Column(modifier.fillMaxWidth()) {
        result?.let { ExtraRow("= $it", "CALCULATOR") { } }
        settings.forEach { shortcut ->
            ExtraRow(shortcut.label, "SETTINGS") {
                startSafely(context, Intent(shortcut.action))
            }
        }
        ExtraRow("Search the web for \"$q\"", "WEB") { webSearch(context, q) }
        Spacer(Modifier.height(8.dp))
    }
}

fun webSearch(context: Context, query: String) {
    startSafely(context, Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query))
}

private fun startSafely(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
private fun ExtraRow(text: String, tag: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = Paper,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(tag, style = MaterialTheme.typography.labelSmall, color = Muted)
    }
}
