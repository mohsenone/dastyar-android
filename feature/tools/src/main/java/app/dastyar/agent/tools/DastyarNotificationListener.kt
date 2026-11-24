package app.dastyar.agent.tools

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Reads notifications the user has allowed this app to see. OTP-like text is redacted. Never auto-replies. */
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

    fun target(key: String): Target? = try {
        val posted = activeNotifications?.firstOrNull { it.key == key } ?: return null
        val title = posted.notification.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = posted.notification.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        Target(
            packageName = posted.packageName,
            title = title,
            sensitive = Redaction.isSensitive(posted.packageName, title, text),
            canReply = RemoteInputSelection.pick(replyActions(posted.notification)) != null,
        )
    } catch (_: Exception) {
        null
    }

    /**
     * Sends [text] through the first free-form RemoteInput. WearableExtender actions are included.
     * Returns [ReplyStatus.NoRemoteInput] when the shade has no reply field.
     */
    fun reply(key: String, text: String): ReplyStatus {
        val posted = try {
            activeNotifications?.firstOrNull { it.key == key }
        } catch (_: Exception) {
            null
        } ?: return ReplyStatus.NotFound
        val title = posted.notification.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = posted.notification.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (Redaction.isSensitive(posted.packageName, title, body)) return ReplyStatus.Sensitive
        val notification = posted.notification
        val standard = notification.actions?.toList().orEmpty()
        val wearable = Notification.WearableExtender(notification).actions.orEmpty()
        val chosen = RemoteInputSelection.pick(replyActions(notification)) ?: return ReplyStatus.NoRemoteInput
        val action = (if (chosen.source == "wearable") wearable.getOrNull(chosen.actionIndex) else standard.getOrNull(chosen.actionIndex))
            ?: return ReplyStatus.NoRemoteInput
        val inputs = action.remoteInputs ?: return ReplyStatus.NoRemoteInput
        val results = Bundle().apply { putCharSequence(chosen.resultKey, text) }
        val fill = Intent()
        RemoteInput.addResultsToIntent(inputs, fill, results)
        val pending = action.actionIntent ?: return ReplyStatus.Failed("missing intent")
        return try {
            pending.send(this, 0, fill)
            ReplyStatus.Sent
        } catch (error: PendingIntent.CanceledException) {
            ReplyStatus.Failed(error.message ?: "canceled")
        }
    }

    fun openContent(key: String): Boolean {
        val posted = try {
            activeNotifications?.firstOrNull { it.key == key }
        } catch (_: Exception) {
            null
        } ?: return false
        val pending = posted.notification.contentIntent ?: return false
        return try {
            pending.send()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun StatusBarNotification.toRow(): Row {
        val extras = notification.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val big = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val body = text.ifBlank { big }
        return Row(
            key = key,
            packageName = packageName,
            summary = Redaction.notification(packageName, title, body),
            canReply = RemoteInputSelection.pick(replyActions(notification)) != null,
        )
    }

    data class Row(
        val key: String,
        val packageName: String,
        val summary: String,
        val canReply: Boolean,
    )

    data class Target(
        val packageName: String,
        val title: String,
        val sensitive: Boolean,
        val canReply: Boolean,
    )

    companion object {
        @Volatile
        var instance: DastyarNotificationListener? = null
    }
}

sealed class ReplyStatus {
    data object Sent : ReplyStatus()
    data object NotFound : ReplyStatus()
    data object NoRemoteInput : ReplyStatus()
    data object Sensitive : ReplyStatus()
    data class Failed(val reason: String) : ReplyStatus()
}

internal fun replyActions(notification: Notification): List<ReplyAction> {
    val out = mutableListOf<ReplyAction>()
    fun add(source: String, actions: List<Notification.Action>) {
        actions.forEachIndexed { actionIndex, action ->
            action.remoteInputs?.forEachIndexed { inputIndex, input ->
                out += ReplyAction(
                    source = source,
                    actionIndex = actionIndex,
                    inputIndex = inputIndex,
                    resultKey = input.resultKey.orEmpty(),
                    freeForm = input.allowFreeFormInput,
                )
            }
        }
    }
    add("standard", notification.actions?.toList().orEmpty())
    add("wearable", Notification.WearableExtender(notification).actions.orEmpty())
    return out
}
