package app.dastyar.agent

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors

/** Reschedules alarms only. It does not start the agent. */
class RoutineKeepAliveWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val entry = EntryPointAccessors.fromApplication(applicationContext, AppEntry::class.java)
        entry.scheduler().rescheduleAll()
        return Result.success()
    }
}
