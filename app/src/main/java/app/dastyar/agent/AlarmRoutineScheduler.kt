package app.dastyar.agent

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.dastyar.agent.data.RoutineEntity
import app.dastyar.agent.data.RoutineRepository
import app.dastyar.agent.tools.RoutineSchedulerPort
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.launch

@Singleton
class AlarmRoutineScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val routines: RoutineRepository,
    private val scope: AppScope,
) : RoutineSchedulerPort {
    override fun schedule(routineId: String) {
        scope.scope.launch {
            val routine = routines.get(routineId) ?: return@launch
            if (!routine.enabled) {
                cancel(routineId)
            } else {
                arm(routine)
            }
        }
    }

    override fun cancel(routineId: String) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        alarm.cancel(pending(routineId))
    }

    override suspend fun rescheduleAll() {
        routines.list().forEach { routine ->
            if (routine.enabled) arm(routine) else cancel(routine.id)
        }
    }

    private fun arm(routine: RoutineEntity) {
        if (routine.triggerKind != "schedule") {
            cancel(routine.id)
            return
        }
        val alarm = context.getSystemService(AlarmManager::class.java)
        val whenMs = next(routine.hour, routine.minute)
        val pi = pending(routine.id)
        val exact = if (Build.VERSION.SDK_INT >= 31) alarm.canScheduleExactAlarms() else true
        try {
            if (exact) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
            } else {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
            }
        } catch (_: SecurityException) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
        }
    }

    private fun pending(id: String): PendingIntent {
        val intent = Intent(context, RoutineAlarmReceiver::class.java).putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun next(hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
        }
        if (calendar.timeInMillis <= System.currentTimeMillis() + 5_000) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }

    companion object {
        const val EXTRA_ID = "routineId"
    }
}
