package app.dastyar.agent.tools

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
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

    fun dumpText(): String {
        val root = rootInActiveWindow ?: return ""
        val out = StringBuilder()
        walk(root, out, 0)
        return out.toString().take(8000)
    }

    fun performSteps(steps: JSONArray): String {
        if (AutomationGate.paused) return "Paused"
        val notes = StringBuilder()
        for (i in 0 until steps.length()) {
            val step = steps.optJSONObject(i) ?: continue
            notes.append(i + 1).append(": ").append(runStep(step)).append('\n')
        }
        return notes.toString().ifBlank { "No steps" }
    }

    private fun runStep(step: JSONObject): String {
        return when (step.optString("type")) {
            "tap" -> tap(step)
            "set_text" -> setText(step)
            "scroll" -> scroll(step.optString("direction", "down"))
            "global" -> global(step.optString("action"))
            else -> "Unknown step"
        }
    }

    private fun tap(step: JSONObject): String {
        val label = step.optString("text")
        if (label.isNotBlank()) {
            val node = find(label)
            if (node != null) {
                val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK) ||
                    node.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
                if (clicked) return "clicked $label"
            }
        }
        if (step.has("x") && step.has("y")) {
            val path = Path().apply { moveTo(step.optDouble("x").toFloat(), step.optDouble("y").toFloat()) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
                .build()
            val ok = dispatchGesture(gesture, null, null)
            return if (ok) "tapped coordinates" else "gesture rejected"
        }
        return "target not found"
    }

    private fun setText(step: JSONObject): String {
        val node = find(step.optString("text")).takeIf { step.optString("text").isNotBlank() }
            ?: focusedEditable()
            ?: return "no text field"
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, step.optString("value"))
        }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        return if (ok) "text set" else "could not set text"
    }

    private fun scroll(direction: String): String {
        val root = rootInActiveWindow ?: return "no window"
        val action = if (direction == "up") {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }
        val ok = root.performAction(action)
        return if (ok) "scrolled $direction" else "nothing to scroll"
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

    private fun find(text: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val nodes = root.findAccessibilityNodeInfosByText(text)
        return nodes.firstOrNull { it.isClickable || it.isEditable } ?: nodes.firstOrNull()
    }

    private fun focusedEditable(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return findEditable(root)
    }

    private fun findEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditable(child)
            if (found != null) return found
        }
        return null
    }

    private fun walk(node: AccessibilityNodeInfo, out: StringBuilder, depth: Int) {
        if (out.length > 8000 || depth > 30) return
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        if (text.isNotBlank() || desc.isNotBlank()) {
            out.append("  ".repeat(depth.coerceAtMost(6)))
            out.append(node.className?.toString()?.substringAfterLast('.') ?: "view")
            if (text.isNotBlank()) out.append(" \"").append(text.take(180)).append('"')
            if (desc.isNotBlank() && desc != text) out.append(" (").append(desc.take(120)).append(')')
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
