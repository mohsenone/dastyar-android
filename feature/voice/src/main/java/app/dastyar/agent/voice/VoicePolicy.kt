package app.dastyar.agent.voice

/** Stored voice language. Auto follows the app language. */
object VoiceLang {
    const val AUTO = "auto"
    const val FA = "fa"
    const val EN = "en"

    fun normalize(raw: String?): String = when {
        raw.isNullOrBlank() || raw.equals(AUTO, ignoreCase = true) -> AUTO
        raw.startsWith(FA, ignoreCase = true) -> FA
        raw.startsWith(EN, ignoreCase = true) -> EN
        else -> AUTO
    }

    /** Concrete language after Auto is resolved against the app language. */
    fun resolved(choice: String, appLang: String): String = when (normalize(choice)) {
        FA -> FA
        EN -> EN
        else -> if (appLang.startsWith(FA)) FA else EN
    }

    fun recognizerTag(choice: String, appLang: String): String =
        if (resolved(choice, appLang) == FA) "fa-IR" else "en-US"

    /** Auto may ask the recognizer to detect language. The tag above is still set. */
    fun requestDetection(choice: String): Boolean = normalize(choice) == AUTO
}

object TtsSupport {
    const val MISSING_DATA = -1
    const val NOT_SUPPORTED = -2

    fun missing(code: Int): Boolean = code == MISSING_DATA || code == NOT_SUPPORTED

    /**
     * Persian speech is missing when the engine reports it, or when it lists voices and none are `fa`.
     * An empty voice list with a successful language result is treated as available, because some
     * engines do not expose [installedLanguageTags].
     */
    fun persianMissing(setLanguageCode: Int, installedLanguageTags: Collection<String>?): Boolean {
        if (missing(setLanguageCode)) return true
        if (installedLanguageTags.isNullOrEmpty()) return false
        return installedLanguageTags.none { tag ->
            tag.lowercase().replace('_', '-').startsWith("fa")
        }
    }

    /** Persian voice when the language is `fa`, or when the reply itself is Persian. */
    fun prefersPersian(language: String, text: String): Boolean {
        if (language.lowercase().startsWith("fa")) return true
        return text.any { ch -> ch in '\u0600'..'\u06FF' || ch in '\u0750'..'\u077F' }
    }
}

enum class VoiceGateDecision { Idle, NeedPermission, Listen, NoRecognizer }

object VoiceGate {
    /** Chat opening must not ask for the microphone. */
    fun shouldRequestOnChatOpen(): Boolean = false

    fun decision(userStarted: Boolean, granted: Boolean, recognizerInstalled: Boolean): VoiceGateDecision {
        if (!userStarted) return VoiceGateDecision.Idle
        if (!granted) return VoiceGateDecision.NeedPermission
        if (!recognizerInstalled) return VoiceGateDecision.NoRecognizer
        return VoiceGateDecision.Listen
    }
}

enum class VoicePhase { Idle, Listening, Processing, Speaking, Error }

enum class VoiceHint { None, InstallTts, MicSettings, NoRecognizer }

enum class ProviderVoiceNote { None, Spoken, Transcribed }

enum class ListenError {
    NoRecognizer,
    Permission,
    Network,
    NoMatch,
    Unavailable,
    Unknown,
    Client,
    Busy,
    Server,
    Language,
    Audio,
    MissingKey,
    Unauthorized,
    Unsupported,
}

enum class SpeakOutcome { Spoken, MissingVoice, Failed, Empty, Stopped }

/** User-visible reply only. Keys and bearer tokens are not spoken. */
object Speakable {
    private val secret = Regex(
        "(?i)(sk-[a-z0-9_\\-]{8,}|api[_-]?key\\s*[:=]\\s*\\S+|bearer\\s+\\S+|ya29\\.[A-Za-z0-9._\\-]+)",
    )

    fun reply(text: String): String = text.lineSequence()
        .map { secret.replace(it, "").trim() }
        .filter { it.isNotBlank() }
        .joinToString("\n")
        .trim()
        .take(1500)
}

enum class AssistantTarget { RoleRequest, VoiceInputSettings, ManageDefaultApps }

object AssistantRole {
    const val MIN_SDK = 29

    fun select(sdk: Int, roleIntentResolves: Boolean): AssistantTarget = when {
        sdk >= MIN_SDK && roleIntentResolves -> AssistantTarget.RoleRequest
        else -> AssistantTarget.VoiceInputSettings
    }

    fun status(held: Boolean?, persian: Boolean): String = when (held) {
        true -> if (persian) "دستیار پیش‌فرض این گوشی است." else "Dastyar is the default digital assistant."
        false -> if (persian) "هنوز دستیار پیش‌فرض نیست." else "Not the default digital assistant yet."
        null -> if (persian) {
            "این نسخهٔ اندروید وضعیت نقش دستیار را گزارش نمی‌کند."
        } else {
            "This Android version does not report the assistant role."
        }
    }
}
