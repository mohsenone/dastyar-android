package app.dastyar.agent.loop

import app.dastyar.agent.data.AuditEntity
import app.dastyar.agent.data.AuditRepository
import app.dastyar.agent.data.ChatRepository
import app.dastyar.agent.data.MemoryRepository
import app.dastyar.agent.data.MessageEntity
import app.dastyar.agent.data.ProviderRepository
import app.dastyar.agent.data.SettingsStore
import app.dastyar.agent.network.ImageEncoder
import app.dastyar.agent.network.LlmClientFactory
import app.dastyar.agent.network.LlmMessage
import app.dastyar.agent.network.LlmOptions
import app.dastyar.agent.network.LlmRequest
import app.dastyar.agent.network.LlmStreamEvent
import app.dastyar.agent.network.LlmToolCall
import app.dastyar.agent.network.LlmToolSpec
import app.dastyar.agent.network.ProviderKinds
import app.dastyar.agent.security.UserAuthenticator
import app.dastyar.agent.security.VaultLockedException
import app.dastyar.agent.tools.AutomationGate
import app.dastyar.agent.tools.RiskLevel
import app.dastyar.agent.tools.ToolContext
import app.dastyar.agent.tools.ToolRegistry
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class AgentRequest(
    val conversationId: String,
    val userText: String,
    val imagePaths: List<String> = emptyList(),
    val routineUnattended: Boolean = false,
)

sealed class AgentEvent {
    abstract val conversationId: String

    data class Token(override val conversationId: String, val text: String) : AgentEvent()
    data class ToolStarted(override val conversationId: String, val name: String) : AgentEvent()
    data class ToolFinished(
        override val conversationId: String,
        val name: String,
        val success: Boolean,
        val summary: String,
    ) : AgentEvent()
    data class Failed(
        override val conversationId: String,
        val message: String,
        val suggestProxy: Boolean,
    ) : AgentEvent()
    data class Completed(override val conversationId: String) : AgentEvent()
    data class DraftCleared(override val conversationId: String) : AgentEvent()
}

data class ConfirmationRequest(
    val id: String,
    val title: String,
    val details: String,
    val risk: RiskLevel,
)

interface RunForeground {
    fun enter()
    fun exit()
}

fun shouldConfirm(risk: RiskLevel, routineUnattended: Boolean): Boolean =
    ConfirmPolicy.shouldConfirm(
        risk,
        ConfirmPolicy.Mode.SENSITIVE,
        safetyFloor = false,
        routineUnattended = routineUnattended,
    )

@Singleton
class ConfirmationBus @Inject constructor() {
    private val pending = MutableStateFlow<ConfirmationRequest?>(null)
    val requests = pending.asStateFlow()
    private val waiters = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.CompletableDeferred<Boolean>>()

    suspend fun confirm(request: ConfirmationRequest): Boolean {
        if (AutomationGate.paused) return false
        val deferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
        waiters[request.id] = deferred
        pending.value = request
        return try {
            kotlinx.coroutines.withTimeoutOrNull(180_000) { deferred.await() } ?: false
        } finally {
            waiters.remove(request.id)
            if (pending.value?.id == request.id) pending.value = null
        }
    }

    fun answer(id: String, approved: Boolean) {
        waiters.remove(id)?.complete(approved)
        if (pending.value?.id == id) pending.value = null
    }

    fun denyAll() {
        waiters.values.forEach { it.complete(false) }
        waiters.clear()
        pending.value = null
    }

    fun current(): ConfirmationRequest? = pending.value
}

@Singleton
class AgentLoop @Inject constructor(
    private val providers: ProviderRepository,
    private val chats: ChatRepository,
    private val memory: MemoryRepository,
    private val audit: AuditRepository,
    private val settings: SettingsStore,
    private val tools: ToolRegistry,
    private val factory: LlmClientFactory,
    private val confirm: ConfirmationBus,
    private val authenticator: UserAuthenticator,
    private val foreground: RunForeground,
) {
    private val gate = Mutex()
    private val current = AtomicReference<Job?>(null)

    fun cancel() {
        current.get()?.cancel()
        confirm.denyAll()
    }

    suspend fun run(request: AgentRequest, emit: suspend (AgentEvent) -> Unit) {
        if (!gate.tryLock()) {
            val lang = runCatching { settings.current().language }.getOrDefault("fa")
            emit(AgentEvent.Failed(request.conversationId, busy(lang), false))
            return
        }
        current.set(kotlinx.coroutines.currentCoroutineContext()[Job])
        foreground.enter()
        try {
            runLocked(request, emit)
        } catch (cancelled: CancellationException) {
            val lang = runCatching { settings.current().language }.getOrDefault("en")
            withContext(NonCancellable) {
                emit(AgentEvent.DraftCleared(request.conversationId))
                emit(AgentEvent.Failed(request.conversationId, stopped(lang), false))
            }
            throw cancelled
        } finally {
            current.set(null)
            foreground.exit()
            gate.unlock()
        }
    }

    private suspend fun runLocked(request: AgentRequest, emit: suspend (AgentEvent) -> Unit) {
        val snap = settings.current()
        val lang = snap.language
        tools.applyPolicy(snap.disabledCategories, snap.disabledTools)
        val confirmMode = ConfirmPolicy.parse(snap.confirmMode)
        if (AutomationGate.paused) {
            emit(AgentEvent.Failed(request.conversationId, paused(lang), false))
            return
        }
        val now = System.currentTimeMillis()
        chats.append(
            MessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = request.conversationId,
                role = "user",
                text = request.userText,
                toolCallsJson = null,
                toolCallId = null,
                toolName = null,
                imagePathsJson = encodePaths(request.imagePaths),
                createdAt = now,
                isError = false,
            ),
        )
        chats.touch(request.conversationId, request.userText.trim())
        val profile = loadProfile(lang, request.conversationId, emit) ?: return
        val client = factory.clientFor(profile.kind)
        val options = LlmOptions(
            kind = profile.kind,
            baseUrl = profile.baseUrl,
            apiKey = profile.apiKey,
            model = profile.model,
            accountId = profile.accountId.ifBlank { null },
            extraHeaders = profile.extraHeaders,
        )
        var guard = 0
        while (guard++ < MAX_STEPS) {
            if (AutomationGate.paused) {
                emit(AgentEvent.Failed(request.conversationId, paused(lang), false))
                return
            }
            val history = toLlm(chats.messages(request.conversationId), profile.kind)
            val facts = memory.latest(20).joinToString("\n") { "- ${it.fact}" }.ifBlank { "(none)" }
            val messages = listOf(LlmMessage("system", systemPrompt(lang, facts))) + history
            val specs = tools.specs().map { LlmToolSpec(it.name, it.description, it.parametersJson) }
            val text = StringBuilder()
            val calls = mutableListOf<LlmToolCall>()
            var failure: LlmStreamEvent.Failure? = null
            var attempt = 0
            while (true) {
                text.clear()
                calls.clear()
                failure = null
                try {
                    client.stream(LlmRequest(messages, specs, options)).collect { event ->
                        when (event) {
                            is LlmStreamEvent.Text -> {
                                text.append(event.delta)
                                emit(AgentEvent.Token(request.conversationId, event.delta))
                            }
                            is LlmStreamEvent.ToolCalls -> calls += event.calls
                            is LlmStreamEvent.Failure -> failure = event
                            LlmStreamEvent.Done -> Unit
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    failure = LlmStreamEvent.Failure(
                        app.dastyar.agent.network.LlmErrorCode.UNKNOWN,
                        TransientRetry.publicError(error.message.orEmpty(), if (lang.startsWith("fa")) "درخواست ناموفق بود." else "The request failed."),
                        false,
                    )
                }
                val pending = failure ?: break
                val emitted = text.isNotEmpty() || calls.isNotEmpty()
                if (!TransientRetry.shouldRetry(pending.code, pending.suggestProxy, emitted, attempt)) break
                delay(TransientRetry.delayMs(attempt))
                attempt++
            }
            val error = failure
            if (error != null) {
                val message = explain(error, lang)
                persist(request.conversationId, "error", message, isError = true)
                emit(AgentEvent.DraftCleared(request.conversationId))
                emit(AgentEvent.Failed(request.conversationId, message, error.suggestProxy))
                return
            }
            if (calls.isEmpty()) {
                val finalText = text.toString().ifBlank { emptyReply(lang) }
                persist(request.conversationId, "assistant", finalText, isError = false)
                emit(AgentEvent.DraftCleared(request.conversationId))
                emit(AgentEvent.Completed(request.conversationId))
                return
            }
            persist(
                request.conversationId,
                "assistant",
                text.toString(),
                toolCalls = calls,
            )
            emit(AgentEvent.DraftCleared(request.conversationId))
            for (call in calls) {
                if (AutomationGate.paused) {
                    emit(AgentEvent.Failed(request.conversationId, paused(lang), false))
                    return
                }
                val risk = riskFor(call)
                emit(AgentEvent.ToolStarted(request.conversationId, call.name))
                val floor = ConfirmPolicy.isSafetyFloor(call.name, call.arguments)
                val approved = if (!ConfirmPolicy.shouldConfirm(risk, confirmMode, floor, request.routineUnattended)) {
                    true
                } else {
                    confirm.confirm(
                        ConfirmationRequest(
                            id = UUID.randomUUID().toString(),
                            title = titleFor(risk, lang),
                            details = tools.summary(call.name, call.arguments, lang),
                            risk = risk,
                        ),
                    )
                }
                val result = if (!approved) {
                    app.dastyar.agent.tools.ToolResult(false, denied(lang))
                } else {
                    tools.execute(call.name, call.arguments.ifBlank { "{}" }, ToolContext(lang, request.conversationId))
                }
                audit.insert(
                    AuditEntity(
                        id = UUID.randomUUID().toString(),
                        conversationId = request.conversationId,
                        toolName = call.name,
                        argsSummary = app.dastyar.agent.tools.Redaction.audit(call.arguments),
                        resultSummary = app.dastyar.agent.tools.Redaction.audit(result.output),
                        risk = risk.name,
                        success = result.success,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                persist(
                    request.conversationId,
                    "tool",
                    result.output,
                    toolCallId = call.id,
                    toolName = call.name,
                    images = result.imagePaths,
                )
                emit(
                    AgentEvent.ToolFinished(
                        request.conversationId,
                        call.name,
                        result.success,
                        result.output.take(280),
                    ),
                )
            }
        }
        val message = maxSteps(lang)
        persist(request.conversationId, "error", message, isError = true)
        emit(AgentEvent.Failed(request.conversationId, message, false))
    }

    private suspend fun loadProfile(
        lang: String,
        conversationId: String,
        emit: suspend (AgentEvent) -> Unit,
    ): app.dastyar.agent.data.ProviderProfile? {
        val conv = chats.get(conversationId)
        val profile = try {
            val picked = if (!conv?.providerId.isNullOrBlank()) providers.profile(conv!!.providerId!!) else null
            (picked ?: providers.defaultProfile())?.let { base ->
                val model = conv?.modelOverride?.takeIf { it.isNotBlank() } ?: base.model
                base.copy(model = model)
            }
        } catch (_: VaultLockedException) {
            val ok = authenticator.authenticate(unlock(lang))
            if (!ok) {
                emit(AgentEvent.Failed(conversationId, unlock(lang), false))
                return null
            }
            try {
                val picked = if (!conv?.providerId.isNullOrBlank()) providers.profile(conv!!.providerId!!) else null
                (picked ?: providers.defaultProfile())?.let { base ->
                    val model = conv?.modelOverride?.takeIf { it.isNotBlank() } ?: base.model
                    base.copy(model = model)
                }
            } catch (error: Exception) {
                emit(AgentEvent.Failed(conversationId, error.message ?: "vault", false))
                return null
            }
        }
        if (profile == null || profile.apiKey.isBlank() || profile.model.isBlank() || profile.baseUrl.isBlank()) {
            emit(AgentEvent.Failed(conversationId, missingKey(lang), true))
            return null
        }
        return profile
    }

    private fun toLlm(messages: List<MessageEntity>, kind: String): List<LlmMessage> {
        val vision = ProviderKinds.supportsVision(kind)
        return messages.filter { it.role != "error" }.takeLast(24).flatMap { entity ->
            val images = decodePaths(entity.imagePathsJson)
            val uris = if (vision) images.mapNotNull { path -> ImageEncoder.dataUri(path) } else emptyList()
            val note = if (!vision && images.isNotEmpty()) {
                "\n[image saved on device; this provider will not receive the pixels]"
            } else {
                ""
            }
            val calls = decodeCalls(entity.toolCallsJson)
            val base = LlmMessage(
                role = entity.role,
                text = entity.text + note,
                toolCallId = entity.toolCallId,
                name = entity.toolName,
                toolCalls = calls,
                imageDataUris = emptyList(),
            )
            if (uris.isEmpty()) {
                listOf(base)
            } else {
                listOf(base, LlmMessage(role = "user", text = "Image attached from the previous step.", imageDataUris = uris))
            }
        }
    }

    private suspend fun persist(
        conversationId: String,
        role: String,
        text: String,
        toolCalls: List<LlmToolCall> = emptyList(),
        toolCallId: String? = null,
        toolName: String? = null,
        images: List<String> = emptyList(),
        isError: Boolean = false,
    ) {
        chats.append(
            MessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                role = role,
                text = text,
                toolCallsJson = if (toolCalls.isEmpty()) null else encodeCalls(toolCalls),
                toolCallId = toolCallId,
                toolName = toolName,
                imagePathsJson = encodePaths(images),
                createdAt = System.currentTimeMillis(),
                isError = isError,
            ),
        )
    }

    private fun riskFor(call: LlmToolCall): RiskLevel {
        val base = tools.riskOf(call.name)
        if (call.name == "web_fetch") {
            val method = runCatching { JSONObject(call.arguments.ifBlank { "{}" }).optString("method", "GET") }.getOrDefault("GET")
            if (!method.equals("GET", true)) return RiskLevel.DANGEROUS
        }
        if (call.name == "compose_email") {
            val tap = runCatching { JSONObject(call.arguments.ifBlank { "{}" }).optBoolean("tap_send") }.getOrDefault(false)
            if (tap) return RiskLevel.DANGEROUS
        }
        return base
    }

    private fun encodeCalls(calls: List<LlmToolCall>): String {
        val array = JSONArray()
        calls.forEach { call ->
            array.put(JSONObject().put("id", call.id).put("name", call.name).put("arguments", call.arguments))
        }
        return array.toString()
    }

    private fun decodeCalls(raw: String?): List<LlmToolCall> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(LlmToolCall(obj.optString("id"), obj.optString("name"), obj.optString("arguments")))
            }
        }
    }

    private fun encodePaths(paths: List<String>): String? =
        if (paths.isEmpty()) null else JSONArray().apply { paths.forEach { put(it) } }.toString()

    private fun decodePaths(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = JSONArray(raw)
        return buildList { for (i in 0 until array.length()) add(array.getString(i)) }
    }

    private companion object {
        const val MAX_STEPS = 12

        fun systemPrompt(lang: String, facts: String): String = """
            You are Dastyar (دستیار), an on-device assistant inside the user's own Android app.
            You are not ChatGPT, Claude, Grok, Muse, or any company's official client.
            UI language: $lang. Answer in Persian if the UI language is fa, and in English if it is en, unless the user writes in the other language.
            Tools can read and change the phone: messages, calls, calendar, files, camera, location, notifications, and other apps through accessibility after the user confirms.
            When SMS, WhatsApp, Gmail, accessibility, or notification access fails, call permissions_diagnostic and tell the user the next step in their language. On Android 13 and later, a sideloaded install needs Allow restricted settings (App info, three-dot menu) before SMS, call log, accessibility, and notification access will stick.
            WhatsApp: send_whatsapp confirms the number and the full text. reply_notification answers a notification that already has a reply box. Email: compose_email opens Gmail or another mail app. If the user connected Google in Connectors, google_gmail_search and google_gmail_send use the Gmail API; send still asks. Read mail and chats from their notifications, the API, or read_screen while that app is open.
            operate_app opens an app by package or by its Persian or English launcher name, then runs confirmed taps, long-presses, typing, scrolls, and global actions. After each step the screen tree is read again. accessibility_action is the same step list when the app is already open. wait_for_element waits for text, a view id, or a content description.
            Connectors (Google, GitHub, Telegram bot, Notion, webhook, web search, RSS, weather, MCP, Shizuku) only appear when the user enabled them. A missing token is an error, not success. Shizuku is optional. Do not claim a Wi-Fi, Bluetooth, or settings toggle worked if the tool only opened a system panel.
            Sending messages, email, calls, deletes, and anything that leaves the phone still needs confirmation unless that routine was explicitly marked unattended.
            Never claim a tool ran unless its result is in the conversation. If a tool fails, say so.
            Do not ask for or repeat API keys. Saved facts:
            $facts
        """.trimIndent()

        fun explain(error: LlmStreamEvent.Failure, lang: String): String {
            val fa = lang.startsWith("fa")
            val base = when (error.code) {
                app.dastyar.agent.network.LlmErrorCode.UNAUTHORIZED ->
                    if (fa) "کلید پذیرفته نشد (401). آن را در ارائه‌دهندگان بررسی کنید." else "The key was rejected (401). Check it in Providers."
                app.dastyar.agent.network.LlmErrorCode.FORBIDDEN ->
                    if (fa) "دسترسی رد شد (403). اگر شبکه محدود است یک آدرس پایهٔ سفارشی بگذارید." else "Access was denied (403). If the network is blocked, set a custom base URL."
                app.dastyar.agent.network.LlmErrorCode.RATE_LIMIT ->
                    if (fa) "محدودیت نرخ (429). کمی بعد دوباره تلاش کنید." else "Rate limited (429). Wait and try again."
                app.dastyar.agent.network.LlmErrorCode.NETWORK ->
                    if (fa) "شبکه قطع است یا مسدود شده. یک آدرس پایهٔ سفارشی (پروکسی) تنظیم کنید." else "The network failed or looks blocked. Set a custom base URL (proxy)."
                else -> TransientRetry.publicError(error.message, if (fa) "درخواست ناموفق بود." else "The request failed.")
            }
            return if (error.suggestProxy && error.code != app.dastyar.agent.network.LlmErrorCode.NETWORK && error.code != app.dastyar.agent.network.LlmErrorCode.FORBIDDEN) {
                base + if (fa) " اگر شبکه مسدود است، آدرس پایهٔ سفارشی بگذارید." else " If the network is blocked, set a custom base URL."
            } else {
                base
            }
        }

        fun busy(lang: String) = if (lang.startsWith("fa")) "دستیار مشغول است." else "Dastyar is already working."
        fun paused(lang: String) = if (lang.startsWith("fa")) "ابزارها متوقف شده‌اند." else "Tools are paused."
        fun denied(lang: String) = if (lang.startsWith("fa")) "کاربر رد کرد." else "The user denied this action."
        fun missingKey(lang: String) = if (lang.startsWith("fa")) "ارائه‌دهنده یا کلید یا مدل تنظیم نشده." else "No provider, key, or model is set."
        fun unlock(lang: String) = if (lang.startsWith("fa")) "برای استفاده از کلید، قفل دستگاه را باز کنید." else "Unlock the device to use the saved key."
        fun emptyReply(lang: String) = if (lang.startsWith("fa")) "(پاسخ خالی)" else "(empty reply)"
        fun stopped(lang: String) = if (lang.startsWith("fa")) "متوقف شد." else "Stopped."
        fun maxSteps(lang: String) = if (lang.startsWith("fa")) "به سقف ۱۲ گام ابزار رسید." else "Stopped after 12 tool steps."
        fun titleFor(risk: RiskLevel, lang: String): String = when (risk) {
            RiskLevel.DANGEROUS -> if (lang.startsWith("fa")) "تأیید عمل خطرناک" else "Confirm a dangerous action"
            RiskLevel.CONFIRM -> if (lang.startsWith("fa")) "تأیید عمل" else "Confirm this action"
            RiskLevel.SAFE -> if (lang.startsWith("fa")) "ادامه" else "Continue"
        }
    }
}
