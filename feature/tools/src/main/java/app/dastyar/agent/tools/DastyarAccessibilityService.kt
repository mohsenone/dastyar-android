package app.dastyar.agent.tools

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads on-screen text and performs gestures only after the agent has shown a confirmation.
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

    fun dumpText(): String {
        val root = rootInActiveWindow ?: return ""
        val out = StringBuilder()
        out.append("package=").append(root.packageName).append('\n')
        walk(root, out, 0)
        return out.toString().take(12_000)
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

    private fun runStep(step: JSONObject): String {
        return when (step.optString("type")) {
            "tap", "click" -> tap(step, longPress = false)
            "long_press", "longpress" -> tap(step, longPress = true)
            "set_text", "type" -> setText(step)
            "scroll", "swipe" -> scroll(step.optString("direction", "down"))
            "global" -> global(step.optString("action"))
            "wait" -> "wait"
            else -> "Unknown step"
        }
    }

    private fun tap(step: JSONObject, longPress: Boolean): String {
        val viewId = step.optString("view_id").ifBlank { step.optString("viewId") }
        val label = step.optString("text")
        val node = findNode(viewId, label)
        if (node != null) {
            val acted = if (longPress) longClickNode(node) else clickNode(node)
            if (acted) {
                val name = viewId.ifBlank { label }.ifBlank { "node" }
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
        val node = findNode(viewId, label)?.takeIf { it.isEditable }
            ?: findNode(viewId, label)
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
        val action = if (direction == "up") {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }
        val node = findScrollable(root)
        if (node != null && node.performAction(action)) return "scrolled $direction"
        return swipeScroll(direction)
    }

    private fun swipeScroll(direction: String): String {
        val metrics = resources.displayMetrics
        val x = metrics.widthPixels * 0.5f
        val upper = metrics.heightPixels * 0.3f
        val lower = metrics.heightPixels * 0.78f
        val startY = if (direction == "up") upper else lower
        val endY = if (direction == "up") lower else upper
        val path = Path().apply {
            moveTo(x, startY)
            lineTo(x, endY)
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
            else -> return "unknown global action"
        }
        return if (performGlobalAction(id)) "global $action" else "global action failed"
    }

    private fun findNode(viewId: String, text: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        if (viewId.isNotBlank()) {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            if (!nodes.isNullOrEmpty()) {
                return nodes.firstOrNull { it.isClickable || it.isEditable || it.isLongClickable } ?: nodes.first()
            }
        }
        if (text.isBlank()) return null
        val nodes = root.findAccessibilityNodeInfosByText(text)
        return nodes.firstOrNull { it.isClickable || it.isEditable || it.isLongClickable } ?: nodes.firstOrNull()
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

    private fun walk(node: AccessibilityNodeInfo, out: StringBuilder, depth: Int) {
        if (out.length > 12_000 || depth > 40) return
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        val id = node.viewIdResourceName.orEmpty()
        if (text.isNotBlank() || desc.isNotBlank() || id.isNotBlank() || node.isClickable || node.isEditable) {
            out.append("  ".repeat(depth.coerceAtMost(6)))
            out.append(node.className?.toString()?.substringAfterLast('.') ?: "view")
            if (text.isNotBlank()) out.append(" \"").append(text.take(180)).append('"')
            if (desc.isNotBlank() && desc != text) out.append(" (").append(desc.take(120)).append(')')
            if (id.isNotBlank()) out.append(" id=").append(id)
            if (node.isClickable) out.append(" clickable")
            if (node.isEditable) out.append(" editable")
            if (node.isScrollable) out.append(" scrollable")
            val bounds = Rect()
            runCatching { node.getBoundsInScreen(bounds) }
            if (!bounds.isEmpty) out.append(" @").append(bounds.centerX()).append(',').append(bounds.centerY())
            out.append('\n')
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            walk(child, out, depth + 1)
        }
    }

    companion object {
        @Volatile
        var instance: DastyarAccessibilityService? = null
    }
}
