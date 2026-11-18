package dev.notcustom.app.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.app.Notification
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Feeds notification dots: the set of packages with a badge-worthy notification. */
class NotificationDotService : NotificationListenerService() {

    override fun onListenerConnected() = refresh()

    override fun onListenerDisconnected() {
        _packages.value = emptySet()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        val ranking = Ranking()
        val rankings = runCatching { currentRanking }.getOrNull()
        _packages.value = active.asSequence()
            .filter { (it.notification.flags and Notification.FLAG_ONGOING_EVENT) == 0 }
            // Respect apps/users that turned badges off for a channel, like stock launchers do.
            .filter { sbn -> rankings?.getRanking(sbn.key, ranking) != true || ranking.canShowBadge() }
            .map { it.packageName }
            .toSet()
    }

    companion object {
        private val _packages = MutableStateFlow<Set<String>>(emptySet())
        val packages: StateFlow<Set<String>> = _packages.asStateFlow()

        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ).orEmpty()
            val me = ComponentName(context, NotificationDotService::class.java)
            return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        fun settingsIntent(context: Context): Intent =
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    ComponentName(context, NotificationDotService::class.java).flattenToString()
                )
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
