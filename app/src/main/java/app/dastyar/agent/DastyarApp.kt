package app.dastyar.agent

import android.app.Application
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.dastyar.agent.data.Appearance
import app.dastyar.agent.data.LocaleGate
import app.dastyar.agent.data.RoutineRepository
import app.dastyar.agent.data.SettingsStore
import app.dastyar.agent.tools.AutomationGate
import app.dastyar.agent.tools.RoutineSchedulerPort
import app.dastyar.agent.tools.RoutineTriggers
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@HiltAndroidApp
class DastyarApp : Application() {
    @Inject lateinit var settings: SettingsStore
    @Inject lateinit var scheduler: RoutineSchedulerPort
    @Inject lateinit var routines: RoutineRepository
    @Inject lateinit var scope: AppScope

    private val lastTrigger = ConcurrentHashMap<String, Long>()

    override fun onCreate() {
        super.onCreate()
        val snap = runBlocking {
            withTimeoutOrNull(2_000) {
                withContext(Dispatchers.IO) { runCatching { settings.current() }.getOrNull() }
            }
        }
        if (snap != null) {
            LocaleGate.publish(snap.language, snap.themeMode, snap.colorMode)
            Bootstrap.writeLanguage(this, snap.language)
            AppLocales.apply(snap.language)
            AutomationGate.paused = snap.toolsPaused
        } else {
            val pref = Bootstrap.language(this)
            LocaleGate.publish(pref, Appearance.SYSTEM, Appearance.BRAND)
            AppLocales.apply(pref)
        }
        Notifications.ensure(this)
        RoutineTriggers.onSms = { from, body -> dispatchTrigger("sms", "$from\n$body") }
        RoutineTriggers.onNotification = { pkg, title, text ->
            dispatchTrigger("notification", "$pkg\n$title\n$text")
        }
        publishShortcuts()
        scope.scope.launch {
            settings.data.collect { current ->
                val changed = current.language != LocaleGate.pref
                LocaleGate.publish(current.language, current.themeMode, current.colorMode)
                AutomationGate.paused = current.toolsPaused
                if (changed) {
                    Bootstrap.writeLanguage(this@DastyarApp, current.language)
                    AppLocales.apply(current.language)
                }
            }
        }
        scope.scope.launch {
            runCatching { scheduler.rescheduleAll() }
        }
        val request = PeriodicWorkRequestBuilder<RoutineKeepAliveWorker>(12, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "dastyar-reschedule",
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private fun dispatchTrigger(kind: String, haystack: String) {
        scope.scope.launch {
            val rows = runCatching { routines.list() }.getOrNull() ?: return@launch
            val now = System.currentTimeMillis()
            rows.filter { it.enabled && it.triggerKind == kind }.forEach { routine ->
                val filter = routine.triggerFilter.trim()
                if (filter.isNotEmpty() && !haystack.contains(filter, ignoreCase = true)) return@forEach
                val previous = lastTrigger[routine.id] ?: 0L
                if (now - previous < 60_000) return@forEach
                lastTrigger[routine.id] = now
                val start = Intent(this@DastyarApp, AgentForegroundService::class.java)
                    .setAction(AgentForegroundService.ACTION_ROUTINE)
                    .putExtra(AgentForegroundService.EXTRA_ID, routine.id)
                runCatching { ContextCompat.startForegroundService(this@DastyarApp, start) }
            }
        }
    }

    private fun publishShortcuts() {
        if (Build.VERSION.SDK_INT < 25) return
        val manager = getSystemService(ShortcutManager::class.java) ?: return
        val icon = Icon.createWithResource(this, R.drawable.ic_stat)
        val chat = ShortcutInfo.Builder(this, "new_chat")
            .setShortLabel(getString(R.string.shortcut_new_chat))
            .setIcon(icon)
            .setIntent(Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_NEW_CHAT))
            .build()
        val voice = ShortcutInfo.Builder(this, "voice")
            .setShortLabel(getString(R.string.shortcut_voice))
            .setIcon(icon)
            .setIntent(Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_VOICE))
            .build()
        runCatching { manager.dynamicShortcuts = listOf(chat, voice) }
    }
}
