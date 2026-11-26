package app.dastyar.agent

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.dastyar.agent.tools.AutomationGate
import app.dastyar.agent.tools.RoutineTriggers
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

class RoutineAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val id = intent?.getStringExtra(AlarmRoutineScheduler.EXTRA_ID) ?: return
        val start = Intent(context, AgentForegroundService::class.java)
            .setAction(AgentForegroundService.ACTION_ROUTINE)
            .putExtra(AgentForegroundService.EXTRA_ID, id)
        try {
            ContextCompat.startForegroundService(context, start)
        } catch (_: Exception) {
            notifyOpen(context, id)
        }
    }

    private fun notifyOpen(context: Context, id: String) {
        Notifications.ensure(context)
        val open = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_ROUTINE)
            .putExtra(AgentForegroundService.EXTRA_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(
            context,
            id.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val fa = Bootstrap.language(context).startsWith("fa")
        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(if (fa) "برای اجرای روال برنامه را باز کنید." else "Open Dastyar to run the routine.")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id.hashCode(), notification) }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val entry = EntryPointAccessors.fromApplication(context.applicationContext, AppEntry::class.java)
        entry.scope().scope.launch {
            try {
                entry.scheduler().rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}

/** Incoming SMS can start a routine. It never sends a reply by itself. */
class SmsTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val from = messages.firstOrNull()?.displayOriginatingAddress.orEmpty()
        val body = messages.joinToString("") { it.displayMessageBody.orEmpty() }
        RoutineTriggers.onSms?.invoke(from, body)
    }
}

class ToolPauseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val entry = EntryPointAccessors.fromApplication(context.applicationContext, AppEntry::class.java)
        AutomationGate.paused = true
        entry.loop().cancel()
        entry.confirm().denyAll()
        entry.scope().scope.launch {
            try {
                entry.settings().setToolsPaused(true)
            } finally {
                pending.finish()
            }
        }
    }
}
