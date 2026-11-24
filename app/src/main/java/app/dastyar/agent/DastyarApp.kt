package app.dastyar.agent

import android.app.Application
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.dastyar.agent.data.SettingsStore
import app.dastyar.agent.tools.AutomationGate
import app.dastyar.agent.tools.RoutineSchedulerPort
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltAndroidApp
class DastyarApp : Application() {
    @Inject lateinit var settings: SettingsStore
    @Inject lateinit var scheduler: RoutineSchedulerPort
    @Inject lateinit var scope: AppScope

    override fun onCreate() {
        super.onCreate()
        Notifications.ensure(this)
        applyLocale(Bootstrap.language(this))
        publishShortcuts()
        scope.scope.launch {
            val snap = runCatching { settings.current() }.getOrNull() ?: return@launch
            AutomationGate.paused = snap.toolsPaused
            if (snap.language != Bootstrap.language(this@DastyarApp)) {
                Bootstrap.writeLanguage(this@DastyarApp, snap.language)
                applyLocale(snap.language)
            }
            runCatching { scheduler.rescheduleAll() }
        }
        val request = PeriodicWorkRequestBuilder<RoutineKeepAliveWorker>(12, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "dastyar-reschedule",
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private fun applyLocale(language: String) {
        val tag = if (language.startsWith("fa")) "fa" else "en"
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
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
