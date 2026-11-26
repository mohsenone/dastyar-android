package app.dastyar.agent.tools

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads on-screen structure and performs gestures only after the agent has shown a confirmation.
 * The service does not upload anything by itself.
 */
class DastyarAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    fun activePackage(): String? = rootInActiveWindow?.packageName?.toString()

    fun dumpText(): String = dumpTree(24_000)

    fun dumpTree(cap: Int = 24_000): String {
        val root = rootInActiveWindow ?: return ""
        val out = StringBuilder()
        out.append("package=").append(root.packageName).append('\n')
        var nodes = 0
        walk(root, out, 0, cap, { nodes += 1; nodes })
        if (out.length >= cap) out.append("\n…truncated\n")
        return out.toString().take(cap)
    }

    fun findMatch(viewId: String, text: String, description: String, exact: Boolean): String {
        val node = findNode(viewId, text, description, exact) ?: return "not found"
        return describe(node, 0).trim()
    }

    fun performSteps(steps: JSONArray): String {
        if (AutomationGate.paused) return "Paused"
        val notes = StringBuilder()
        for (i in 0 until steps.length()) {
            if (AutomationGate.paused) {
                notes.append("Paused\n")
                break
            }
            val step = steps.optJSONObject(i) ?: continue
            notes.append(i + 1).append(": ").append(perform(step)).append('\n')
        }
        return notes.toString().ifBlank { "No steps" }
    }

    fun perform(step: JSONObject): String {
        if (AutomationGate.paused) return "Paused"
        return try {
            runStep(step)
        } catch (error: Exception) {
            error.message ?: "step failed"
        }
    }

    suspend fun captureScreenshot(): String? {
        if (Build.VERSION.SDK_INT < 30) return null
        return suspendCancellableCoroutine { cont ->
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val path = runCatching {
                            val hardware = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                            val soft = hardware?.copy(Bitmap.Config.ARGB_8888, false)
                            result.hardwareBuffer.close()
                            hardware?.recycle()
                            if (soft == null) return@runCatching null
                            val file = File(cacheDir, "a11y-${System.currentTimeMillis()}.jpg")
                            file.outputStream().use { soft.compress(Bitmap.CompressFormat.JPEG, 70, it) }
                            soft.recycle()
                            file.absolutePath
                        }.getOrNull()
                        if (cont.isActive) cont.resume(path)
                    }

                    override fun onFailure(errorCode: Int) {
                        if (cont.isActive) cont.resume(null)
                    }
                },
            )
        }
    }

    private fun runStep(step: JSONObject): String {
        return when (step.optString("type")) {
            "tap", "click" -> tap(step, longPress = false)
            "long_press", "longpress" -> tap(step, longPress = true)
            "set_text", "type" -> setText(step)
            "scroll" -> scroll(step.optString("direction", "down"))
            "swipe" -> swipe(step)
            "global" -> global(step.optString("action"))
            "wait" -> "wait"
            else -> "Unknown step"
        }
    }

    private fun exact(step: JSONObject): Boolean = step.optString("match").equals("exact", true)

    private fun tap(step: JSONObject, longPress: Boolean): String {
        val viewId = step.optString("view_id").ifBlank { step.optString("viewId") }
        val label = step.optString("text")
        val desc = step.optString("content_description").ifBlank { step.optString("description") }
        val node = findNode(viewId, label, desc, exact(step))
        if (node != null) {
            val acted = if (longPress) longClickNode(node) else clickNode(node)
            if (acted) {
                val name = viewId.ifBlank { label }.ifBlank { desc }.ifBlank { "node" }
                return if (longPress) "long-pressed $name" else "clicked $name"
            }
        }
        if (step.has("x") && step.has("y")) {
            val x = step.optDouble("x").toFloat()
            val y = step.optDouble("y").toFloat()
            val ok = gestureAt(x, y, if (longPress) 600 else 60)
            return if (ok) {
                if (longPress) "long-pressed coordinates" else "tapped coordinates"
            } else {
                "gesture rejected"
            }
        }
        return "target not found"
    }

    private fun setText(step: JSONObject): String {
        val viewId = step.optString("view_id").ifBlank { step.optString("viewId") }
        val label = step.optString("text")
        val desc = step.optString("content_description").ifBlank { step.optString("description") }
        val node = findNode(viewId, label, desc, exact(step))?.takeIf { it.isEditable }
            ?: findNode(viewId, label, desc, exact(step))
            ?: focusedEditable()
            ?: findEditable(rootInActiveWindow)
            ?: return "no text field"
        val target = if (node.isEditable) node else findEditable(node) ?: node
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, step.optString("value"))
        }
        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        return if (ok) "text set" else "could not set text"
    }

    private fun scroll(direction: String): String {
        val root = rootInActiveWindow
        val action = if (direction == "up" || direction == "left") {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }
        val node = findScrollable(root)
        if (node != null && node.performAction(action)) return "scrolled $direction"
        return swipeScroll(direction)
    }

    private fun swipe(step: JSONObject): String {
        if (step.has("x1") && step.has("y1") && step.has("x2") && step.has("y2")) {
            val path = Path().apply {
                moveTo(step.optDouble("x1").toFloat(), step.optDouble("y1").toFloat())
                lineTo(step.optDouble("x2").toFloat(), step.optDouble("y2").toFloat())
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, step.optLong("duration_ms", 320).coerceIn(50, 2_000)))
                .build()
            return if (dispatchGesture(gesture, null, null)) "swiped" else "swipe rejected"
        }
        return swipeScroll(step.optString("direction", "down"))
    }

    private fun swipeScroll(direction: String): String {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()
        val horizontal = direction == "left" || direction == "right"
        val startX = if (horizontal) width * (if (direction == "left") 0.8f else 0.2f) else width * 0.5f
        val endX = if (horizontal) width * (if (direction == "left") 0.2f else 0.8f) else width * 0.5f
        val startY = if (horizontal) height * 0.5f else height * (if (direction == "up") 0.3f else 0.78f)
        val endY = if (horizontal) height * 0.5f else height * (if (direction == "up") 0.78f else 0.3f)
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 320))
            .build()
        val ok = dispatchGesture(gesture, null, null)
        return if (ok) "swiped $direction" else "nothing to scroll"
    }

    private fun global(action: String): String {
        val id = when (action) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> GLOBAL_ACTION_QUICK_SETTINGS
            "screenshot" -> if (Build.VERSION.SDK_INT >= 31) GLOBAL_ACTION_TAKE_SCREENSHOT else return "screenshot global action needs API 31"
            "lock_screen", "lock" -> if (Build.VERSION.SDK_INT >= 28) GLOBAL_ACTION_LOCK_SCREEN else return "lock screen needs API 28"
            else -> return "unknown global action"
        }
        val ok = performGlobalAction(id)
        return if (ok) "global $action" else "global action failed"
    }

    private fun findNode(viewId: String, text: String, description: String, exact: Boolean): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        if (viewId.isNotBlank()) {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            if (!nodes.isNullOrEmpty()) {
                return nodes.firstOrNull { it.isClickable || it.isEditable || it.isLongClickable } ?: nodes.first()
            }
        }
        if (text.isNotBlank()) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            val matched = if (exact) {
                nodes.filter { it.text?.toString() == text || it.contentDescription?.toString() == text }
            } else {
                nodes
            }
            if (matched.isNotEmpty()) {
                return matched.firstOrNull { it.isClickable || it.isEditable || it.isLongClickable } ?: matched.first()
            }
        }
        if (description.isNotBlank()) {
            return findByDescription(root, description, exact)
        }
        return null
    }

    private fun findByDescription(node: AccessibilityNodeInfo?, query: String, exact: Boolean): AccessibilityNodeInfo? {
        if (node == null) return null
        val desc = node.contentDescription?.toString().orEmpty()
        val hit = if (exact) desc == query else desc.contains(query, true)
        if (hit && desc.isNotBlank()) return node
        for (i in 0 until node.childCount) {
            val found = findByDescription(node.getChild(i), query, exact)
            if (found != null) return found
        }
        return null
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean = actUp(node, AccessibilityNodeInfo.ACTION_CLICK)

    private fun longClickNode(node: AccessibilityNodeInfo): Boolean = actUp(node, AccessibilityNodeInfo.ACTION_LONG_CLICK)

    private fun actUp(node: AccessibilityNodeInfo, action: Int): Boolean {
        var current: AccessibilityNodeInfo? = node
        var hops = 0
        while (current != null && hops < 6) {
            if (current.performAction(action)) return true
            current = current.parent
            hops++
        }
        return false
    }

    private fun gestureAt(x: Float, y: Float, durationMs: Long): Boolean {
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x + 1f, y + 1f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun focusedEditable(): AccessibilityNodeInfo? = findEditable(rootInActiveWindow)

    private fun findEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val found = findEditable(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    private fun findScrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        for (i in 0 until node.childCount) {
            val nested = findScrollable(node.getChild(i))
            if (nested != null) return nested
        }
        val scrolls = node.actionList?.any {
            it.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD || it.id == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        } == true
        return if (node.isScrollable || scrolls) node else null
    }

    private fun walk(node: AccessibilityNodeInfo, out: StringBuilder, depth: Int, cap: Int, count: () -> Int) {
        if (out.length >= cap || depth > 30 || count() > 400) return
        out.append(describe(node, depth))
        for (i in 0 until node.childCount) {
            if (out.length >= cap) return
            val child = node.getChild(i) ?: continue
            walk(child, out, depth + 1, cap, count)
        }
    }

    private fun describe(node: AccessibilityNodeInfo, depth: Int): String {
        val text = node.text?.toString().orEmpty().take(180)
        val desc = node.contentDescription?.toString().orEmpty().take(120)
        val id = node.viewIdResourceName.orEmpty()
        val bounds = Rect()
        runCatching { node.getBoundsInScreen(bounds) }
        val kind = node.className?.toString()?.substringAfterLast('.') ?: "view"
        return buildString {
            append("  ".repeat(depth.coerceAtMost(8)))
            append('[').append(depth).append("] ").append(kind)
            if (text.isNotBlank()) append(" text=\"").append(text.replace('\n', ' ')).append('"')
            if (desc.isNotBlank()) append(" desc=\"").append(desc.replace('\n', ' ')).append('"')
            if (id.isNotBlank()) append(" id=").append(id)
            append(" clickable=").append(node.isClickable)
            append(" editable=").append(node.isEditable)
            append(" scrollable=").append(node.isScrollable)
            append(" checked=").append(node.isChecked)
            if (!bounds.isEmpty) {
                append(" bounds=").append(bounds.left).append(',').append(bounds.top)
                    .append(',').append(bounds.right).append(',').append(bounds.bottom)
            }
            append('\n')
        }
    }

    companion object {
        @Volatile
        var instance: DastyarAccessibilityService? = null
    }
}
