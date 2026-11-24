package app.dastyar.agent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

internal object Notifications {
    const val CHANNEL = "dastyar.work"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val name = if (Bootstrap.language(context).startsWith("fa")) "دستیار" else "Dastyar"
        val channel = NotificationChannel(CHANNEL, name, NotificationManager.IMPORTANCE_LOW)
        channel.setShowBadge(false)
        manager.createNotificationChannel(channel)
    }
}
