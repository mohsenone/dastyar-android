package app.dastyar.agent.tools

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Reads notifications the user has allowed this app to see. OTP-like text is redacted. */
class DastyarNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        instance = this
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun snapshot(): List<Row> = try {
        activeNotifications.orEmpty().map { it.toRow() }
    } catch (_: Exception) {
        emptyList()
    }

    fun dismiss(key: String): Boolean = try {
        cancelNotification(key)
        true
    } catch (_: Exception) {
        false
    }

    private fun StatusBarNotification.toRow(): Row {
        val extras = notification.extras
        val title = extras?.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras?.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()
        return Row(
            key = key,
            summary = Redaction.notification(packageName, title, text),
        )
    }

    data class Row(val key: String, val summary: String)

    companion object {
        @Volatile
        var instance: DastyarNotificationListener? = null
    }
}
