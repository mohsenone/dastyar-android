package app.dastyar.agent

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import app.dastyar.agent.loop.RunForeground
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForegroundGate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val overlay: OverlayController,
) : RunForeground {
    private val count = AtomicInteger(0)

    fun isHeld(): Boolean = count.get() > 0

    override fun enter() {
        if (count.incrementAndGet() == 1) {
            val intent = Intent(context, AgentForegroundService::class.java).setAction(AgentForegroundService.ACTION_HOLD)
            runCatching { ContextCompat.startForegroundService(context, intent) }.onFailure {
                count.set(0)
                return
            }
        }
        overlay.show()
    }

    override fun exit() {
        if (count.decrementAndGet() <= 0) {
            count.set(0)
            overlay.hide()
            runCatching {
                context.startService(
                    Intent(context, AgentForegroundService::class.java).setAction(AgentForegroundService.ACTION_RELEASE),
                )
            }
        }
    }
}
