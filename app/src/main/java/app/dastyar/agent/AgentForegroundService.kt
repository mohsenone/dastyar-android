package app.dastyar.agent

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.dastyar.agent.loop.AgentRequest
import app.dastyar.agent.tools.AutomationGate
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AgentForegroundService : Service() {
    @Inject lateinit var gate: ForegroundGate

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val routine = intent?.action == ACTION_ROUTINE
        startInForeground(routine)
        when (intent?.action) {
            ACTION_ROUTINE -> {
                val id = intent.getStringExtra(EXTRA_ID)
                if (id.isNullOrBlank()) {
                    stopIfIdle()
                } else {
                    runRoutine(id)
                }
            }
            ACTION_RELEASE -> stopIfIdle()
            else -> if (!gate.isHeld() && intent?.action != ACTION_HOLD) stopIfIdle()
        }
        return START_STICKY
    }

    private fun startInForeground(routine: Boolean) {
        Notifications.ensure(this)
        val notification = buildNotification(this, routine)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(ID, notification)
        }
    }

    private fun runRoutine(id: String) {
        val entry = EntryPointAccessors.fromApplication(applicationContext, AppEntry::class.java)
        entry.scope().scope.launch {
            var repeat = false
            try {
                val routine = entry.routines().get(id)
                if (routine != null && routine.enabled && !AutomationGate.paused) {
                    repeat = routine.repeatDaily
                    val conversation = entry.chats().createConversation(routine.title)
                    entry.loop().run(
                        AgentRequest(
                            conversationId = conversation,
                            userText = routine.prompt,
                            routineUnattended = routine.allowUnattended,
                        ),
                    ) { }
                    if (!routine.repeatDaily) {
                        entry.routines().save(routine.copy(enabled = false))
                        entry.scheduler().cancel(id)
                        repeat = false
                    }
                }
            } finally {
                if (repeat) entry.scheduler().schedule(id)
                stopIfIdle()
            }
        }
    }

    private fun stopIfIdle() {
        if (gate.isHeld()) return
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ID = 7101
        const val ACTION_HOLD = "app.dastyar.agent.HOLD"
        const val ACTION_RELEASE = "app.dastyar.agent.RELEASE"
        const val ACTION_ROUTINE = "app.dastyar.agent.ROUTINE"
        const val EXTRA_ID = AlarmRoutineScheduler.EXTRA_ID

        fun buildNotification(context: Context, routine: Boolean) = NotificationCompat.Builder(context, Notifications.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(body(context, routine))
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .addAction(
                R.drawable.ic_stat,
                pauseLabel(context),
                PendingIntent.getBroadcast(
                    context,
                    1,
                    Intent(context, ToolPauseReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

        private fun body(context: Context, routine: Boolean): String {
            val fa = Bootstrap.language(context).startsWith("fa")
            return when {
                routine && fa -> "دستیار در حال اجرای روال است"
                routine -> "Dastyar is running a routine"
                fa -> "دستیار در حال کار است"
                else -> "Dastyar is working"
            }
        }

        private fun pauseLabel(context: Context): String =
            if (Bootstrap.language(context).startsWith("fa")) "توقف ابزارها" else "Pause tools"
    }
}
