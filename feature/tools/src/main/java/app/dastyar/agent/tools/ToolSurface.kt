package app.dastyar.agent.tools

import org.json.JSONObject

enum class RiskLevel { SAFE, CONFIRM, DANGEROUS }

data class ToolSpec(
    val name: String,
    val description: String,
    val parametersJson: String,
)

data class ToolResult(
    val success: Boolean,
    val output: String,
    val imagePaths: List<String> = emptyList(),
)

data class ToolContext(
    val lang: String,
    val conversationId: String?,
)

interface UiBridge {
    suspend fun ensurePermissions(permissions: List<String>, rationale: String): Boolean
    suspend fun takePhoto(): String?
    suspend fun captureVideo(): String?
    suspend fun captureScreen(): String?
    suspend fun pickDirectory(): String?
    suspend fun createDocument(name: String, mime: String): String?
    suspend fun confirmImage(path: String, title: String, body: String): Boolean
    fun openAccessibilitySettings()
    fun openNotificationListenerSettings()
    fun openOverlaySettings()
    fun openAllFilesSettings()
    fun openExactAlarmSettings()
    fun openUsageAccessSettings()
    fun openAppSettings()
    fun openBatterySettings()
    fun openLocationSettings()
    suspend fun authenticate(title: String): Boolean
    fun showOverlay()
    fun hideOverlay()
    fun isResumed(): Boolean
}

interface RoutineSchedulerPort {
    fun schedule(routineId: String)
    fun cancel(routineId: String)
    suspend fun rescheduleAll()
}

interface ToolExecutor {
    fun specs(): List<ToolSpec>
    fun riskOf(name: String): RiskLevel
    fun summary(name: String, argsJson: String, lang: String): String
    suspend fun execute(name: String, argsJson: String, ctx: ToolContext): ToolResult
}

internal fun bi(lang: String, fa: String, en: String): String = if (lang.startsWith("fa")) fa else en

internal fun jsonArgs(raw: String): JSONObject = try {
    if (raw.isBlank()) JSONObject() else JSONObject(raw)
} catch (_: Exception) {
    JSONObject()
}

object AutomationGate {
    @Volatile
    var paused: Boolean = false
}

object Redaction {
    private val otpContext = Regex("(?i)(otp|one[- ]time|passcode|verification|رمز|کد|cvv|pin)")
    private val digits = Regex("\\b\\d{4,8}\\b")
    private val secret = Regex("(?i)(sk-[A-Za-z0-9_\\-]{6,}|bearer\\s+[A-Za-z0-9._\\-]{8,})")

    fun isSensitive(pkg: String, title: String, text: String): Boolean =
        pkg.contains("bank", true) || pkg.contains("otp", true) || otpContext.containsMatchIn("$title $text")

    fun notification(pkg: String, title: String, text: String): String {
        val body = if (isSensitive(pkg, title, text)) digits.replace(text, "••••") else text
        return secret.replace("$pkg | $title | $body", "••••").take(400)
    }

    fun audit(text: String): String = secret.replace(text, "••••").take(500)
}
