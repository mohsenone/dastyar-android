package app.dastyar.agent.loop

import app.dastyar.agent.tools.RiskLevel
import org.json.JSONObject

/**
 * Confirmation modes:
 * - always: ask before every tool
 * - sensitive: ask for anything that is not a plain read (the default)
 * - auto: skip the sheet
 *
 * Safety floor: sending a message, email, or call, deleting or overwriting data,
 * paying, or any other action that leaves the device still asks in auto mode.
 * An explicit unattended routine is the only opt-out, and it is off by default.
 */
object ConfirmPolicy {
    enum class Mode { ALWAYS, SENSITIVE, AUTO }

    fun parse(raw: String?): Mode = when (raw?.trim()?.lowercase()) {
        "always", "always_ask" -> Mode.ALWAYS
        "auto" -> Mode.AUTO
        else -> Mode.SENSITIVE
    }

    fun shouldConfirm(
        risk: RiskLevel,
        mode: Mode,
        safetyFloor: Boolean,
        routineUnattended: Boolean,
    ): Boolean {
        if (safetyFloor) return !routineUnattended
        if (routineUnattended && mode != Mode.ALWAYS) return false
        return when (mode) {
            Mode.ALWAYS -> true
            Mode.SENSITIVE -> risk != RiskLevel.SAFE
            Mode.AUTO -> false
        }
    }

    fun isSafetyFloor(name: String, argsJson: String = "{}"): Boolean {
        if (name in FLOOR) return true
        val args = runCatching { JSONObject(if (argsJson.isBlank()) "{}" else argsJson) }.getOrNull()
        if (name == "compose_email" && args?.optBoolean("tap_send") == true) return true
        if (name == "web_fetch") {
            val method = args?.optString("method", "GET").orEmpty()
            if (!method.equals("GET", true) && !method.equals("HEAD", true)) return true
        }
        if (name == "app_whatsapp" && args?.optBoolean("tap_send") == true) return true
        if (name == "app_telegram" && args?.optBoolean("tap_send") == true) return true
        if (name == "app_gmail" && args?.optBoolean("tap_send") == true) return true
        if (name.startsWith("mcp_")) {
            val lower = name.lowercase()
            if (MUTATING.any { lower.contains(it) }) return true
        }
        return false
    }

    private val FLOOR = setOf(
        "send_sms",
        "place_call",
        "send_whatsapp",
        "reply_notification",
        "delete_file",
        "delete_event",
        "delete_memory",
        "write_file",
        "sandbox_shell",
        "google_gmail_send",
        "google_calendar_update",
        "google_calendar_delete",
        "google_drive_upload",
        "google_contacts_write",
        "github_create_issue",
        "github_comment",
        "telegram_bot_send",
        "telegram_bot_send_file",
        "notion_append",
        "notion_create_page",
        "webhook_request",
        "shizuku_settings_put",
        "shizuku_pm_grant",
        "shizuku_pm_revoke",
        "shizuku_component",
    )

    private val MUTATING = listOf("delete", "send", "remove", "drop", "pay", "write", "update", "create", "post")
}
