package app.dastyar.agent

import android.os.Build
import android.view.Window
import android.view.WindowManager

/**
 * FLAG_SECURE on an emulator software renderer (SwiftShader, API 35) composites the
 * window as a black framebuffer while the accessibility tree stays intact. The provider
 * catalog has no secret, so it is never marked secure. The key sheet is, except on those devices.
 */
object SecureSurfaces {
    fun blocksDrawing(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val hardware = Build.HARDWARE.lowercase()
        val product = Build.PRODUCT.lowercase()
        return fingerprint.contains("generic") ||
            fingerprint.contains("emulator") ||
            hardware == "ranchu" ||
            hardware == "goldfish" ||
            hardware.contains("swiftshader") ||
            product.contains("sdk") ||
            Build.MODEL.contains("sdk", true) ||
            Build.MODEL.contains("emulator", true)
    }

    fun apply(window: Window, secure: Boolean) {
        if (secure) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
