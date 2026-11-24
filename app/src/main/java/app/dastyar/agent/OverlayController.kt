package app.dastyar.agent

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Small status bubble. Touches outside it pass through. It is not a click-jacking layer. */
@Singleton
class OverlayController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var view: TextView? = null

    fun show() {
        if (view != null) return
        if (Build.VERSION.SDK_INT < 23 || !Settings.canDrawOverlays(context)) return
        val window = context.getSystemService(WindowManager::class.java)
        val label = TextView(context).apply {
            text = if (Bootstrap.language(context).startsWith("fa")) "دستیار" else "Dastyar"
            setTextColor(Color.WHITE)
            setPadding(28, 12, 28, 12)
            setBackgroundColor(0xCC0E6B62.toInt())
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 72
        }
        runCatching {
            window.addView(label, params)
            view = label
        }
    }

    fun hide() {
        val current = view ?: return
        view = null
        val window = context.getSystemService(WindowManager::class.java)
        runCatching { window.removeView(current) }
    }
}
