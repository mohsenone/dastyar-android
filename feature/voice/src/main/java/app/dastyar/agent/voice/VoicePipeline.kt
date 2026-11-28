package app.dastyar.agent.voice

/**
 * SpeechRecognizer error ints. Kept here so unit tests do not touch the Android SDK.
 * Values match android.speech.SpeechRecognizer.
 */
object SpeechErrors {
    const val NETWORK_TIMEOUT = 1
    const val NETWORK = 2
    const val AUDIO = 3
    const val SERVER = 4
    const val CLIENT = 5
    const val SPEECH_TIMEOUT = 6
    const val NO_MATCH = 7
    const val RECOGNIZER_BUSY = 8
    const val INSUFFICIENT_PERMISSIONS = 9
    const val TOO_MANY_REQUESTS = 10
    const val SERVER_DISCONNECTED = 11
    const val LANGUAGE_NOT_SUPPORTED = 12
    const val LANGUAGE_UNAVAILABLE = 13
    const val CANNOT_CHECK_SUPPORT = 14

    /** How long an error may own the voice stage before it returns to idle or listening. */
    const val DISMISS_AFTER_MS = 4_500L

    /** Automatic re-listen after a soft error. A second failure waits for a tap. */
    const val MAX_AUTO_RESTARTS = 1

    fun listenError(code: Int): ListenError = when (code) {
        NETWORK, NETWORK_TIMEOUT -> ListenError.Network
        INSUFFICIENT_PERMISSIONS -> ListenError.Permission
        NO_MATCH, SPEECH_TIMEOUT -> ListenError.NoMatch
        CLIENT -> ListenError.Client
        RECOGNIZER_BUSY, TOO_MANY_REQUESTS -> ListenError.Busy
        SERVER, SERVER_DISCONNECTED -> ListenError.Server
        LANGUAGE_NOT_SUPPORTED, LANGUAGE_UNAVAILABLE -> ListenError.Language
        AUDIO -> ListenError.Audio
        CANNOT_CHECK_SUPPORT -> ListenError.Unavailable
        else -> ListenError.Unknown
    }

    /**
     * The engine cannot take this utterance (bind failure, our own stub, or a missing language).
     * Try on-device recognition, then provider transcription. A no-match or a busy engine is not this.
     */
    fun engineUnusable(code: Int): Boolean = when (code) {
        CLIENT, SERVER, SERVER_DISCONNECTED, LANGUAGE_NOT_SUPPORTED, LANGUAGE_UNAVAILABLE, CANNOT_CHECK_SUPPORT -> true
        else -> false
    }

    fun blocksAutoRestart(error: ListenError): Boolean = when (error) {
        ListenError.Permission,
        ListenError.NoRecognizer,
        ListenError.MissingKey,
        ListenError.Unauthorized,
        ListenError.Unsupported,
        ListenError.Language,
        -> true
        else -> false
    }

    fun hintFor(error: ListenError): VoiceHint = when (error) {
        ListenError.Permission -> VoiceHint.MicSettings
        ListenError.NoRecognizer, ListenError.MissingKey, ListenError.Unsupported -> VoiceHint.NoRecognizer
        else -> VoiceHint.None
    }

    fun message(error: ListenError, persian: Boolean): String = if (persian) fa(error) else en(error)

    fun speakMissing(persian: Boolean): String = if (persian) {
        "گفتار فارسی نصب نیست. پاسخ روی صفحه می‌ماند."
    } else {
        "No matching voice is installed. The reply stays on screen."
    }

    private fun en(error: ListenError): String = when (error) {
        ListenError.NoRecognizer ->
            "This phone has no speech recognizer Dastyar can use. Install Google speech services, or add a Groq or OpenAI key so a short recording can be transcribed."
        ListenError.Permission -> "Microphone permission is missing. Voice does not turn on anything else."
        ListenError.Network -> "The speech service could not reach its network. You can try again."
        ListenError.NoMatch -> "No speech was recognized. Tap the mic to try again."
        ListenError.Client ->
            "The speech service on this phone rejected the request. Dastyar will try another way when one is available."
        ListenError.Busy -> "The speech service is busy. Wait a moment and try again."
        ListenError.Server -> "The speech service failed. You can try again."
        ListenError.Language -> "This phone has no voice model for that language."
        ListenError.Audio -> "The microphone did not return audio. Check that no other app is using it."
        ListenError.Unavailable -> "Speech recognition is not available on this phone right now."
        ListenError.Unknown -> "Speech recognition stopped. You can try again."
        ListenError.MissingKey ->
            "Voice needs a key for transcription. Add a Groq or OpenAI key. There is no separate free voice plan."
        ListenError.Unauthorized -> "The provider rejected the key (401). Check the key in Providers."
        ListenError.Unsupported -> "This provider does not accept audio transcription. A Groq or OpenAI key can."
    }

    private fun fa(error: ListenError): String = when (error) {
        ListenError.NoRecognizer ->
            "این گوشی تشخیص گفتاری ندارد که دستیار بتواند از آن استفاده کند. سرویس گفتار گوگل را نصب کنید، یا یک کلید گروک یا OpenAI بگذارید تا یک ضبط کوتاه نوشته شود."
        ListenError.Permission -> "اجازهٔ میکروفون نیست. صدا چیز دیگری را روشن نمی‌کند."
        ListenError.Network -> "سرویس گفتار به شبکه‌اش نرسید. می‌توانید دوباره تلاش کنید."
        ListenError.NoMatch -> "گفتاری شناخته نشد. برای تلاش دوباره میکروفون را بزنید."
        ListenError.Client ->
            "سرویس گفتار این گوشی درخواست را نپذیرفت. اگر راه دیگری باشد، دستیار همان را امتحان می‌کند."
        ListenError.Busy -> "سرویس گفتار مشغول است. کمی بعد دوباره تلاش کنید."
        ListenError.Server -> "سرویس گفتار خطا داد. می‌توانید دوباره تلاش کنید."
        ListenError.Language -> "مدل صدای این زبان روی گوشی نیست."
        ListenError.Audio -> "میکروفون صدایی نداد. ببینید برنامهٔ دیگری آن را نگرفته باشد."
        ListenError.Unavailable -> "تشخیص گفتار الان روی این گوشی در دسترس نیست."
        ListenError.Unknown -> "تشخیص گفتار متوقف شد. می‌توانید دوباره تلاش کنید."
        ListenError.MissingKey ->
            "برای نوشتن صدا یک کلید لازم است. کلید گروک یا OpenAI را بگذارید. طرح رایگان جدا برای صدا نیست."
        ListenError.Unauthorized -> "ارائه‌دهنده کلید را نپذیرفت (۴۰۱). کلید را در بخش کلیدها بررسی کنید."
        ListenError.Unsupported -> "این ارائه‌دهنده رونویسی صدا ندارد. کلید گروک یا OpenAI این کار را می‌کند."
    }
}

enum class ListenPath { Phone, OnDevice, Provider }

object ListenPathCopy {
    fun line(path: ListenPath, providerLabel: String, persian: Boolean): String = when (path) {
        ListenPath.Phone, ListenPath.OnDevice ->
            if (persian) "شنیدن با همین گوشی" else "Listening with this phone"
        ListenPath.Provider -> {
            val name = providerLabel.trim().ifBlank { if (persian) "ارائه‌دهنده" else "your provider" }
            if (persian) "رونویسی با $name (کلید شما)" else "Transcribing with $name (your key)"
        }
    }
}

/** Final hypothesis from a recognizer bundle. Some engines only fill the partial or the unstable extra. */
object RecognitionText {
    fun first(hypotheses: List<String>?, unstable: String?, lastPartial: String = ""): String {
        val listed = hypotheses?.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (listed.isNotEmpty()) return listed
        val loose = unstable?.trim().orEmpty()
        if (loose.isNotEmpty()) return loose
        return lastPartial.trim()
    }
}

/**
 * Which recognizer to bind. The app's own listing stub is never a usable engine:
 * it answers every session with ERROR_CLIENT.
 */
object SpeechEnginePicker {
    val preferredPackages = listOf(
        "com.google.android.googlequicksearchbox",
        "com.google.android.tts",
        "com.google.android.as",
        "com.samsung.android.bixby.agent",
        "com.xiaomi.mibrain.speech",
        "com.miui.voiceassist",
    )

    fun externalComponents(components: List<String>, ownPackage: String): List<String> =
        components.map { it.trim() }.filter { raw ->
            val pkg = raw.substringBefore('/').trim()
            pkg.isNotBlank() && pkg != ownPackage
        }.distinct()

    fun bestOnline(components: List<String>, ownPackage: String): String? {
        val external = externalComponents(components, ownPackage)
        if (external.isEmpty()) return null
        preferredPackages.forEach { pkg ->
            external.firstOrNull { it.substringBefore('/') == pkg }?.let { return it }
        }
        return external.first()
    }

    enum class Kind { Online, OnDevice, None }

    fun choose(components: List<String>, ownPackage: String, onDevice: Boolean): Kind = when {
        bestOnline(components, ownPackage) != null -> Kind.Online
        onDevice -> Kind.OnDevice
        else -> Kind.None
    }
}

/** Manifest `<queries>` the speech pipeline needs. Intents alone do not reveal every OEM package. */
object SpeechVisibility {
    val packages: List<String> = SpeechEnginePicker.preferredPackages
    val actions = listOf(
        "android.speech.RecognitionService",
        "android.intent.action.TTS_SERVICE",
        "android.speech.tts.engine.INSTALL_TTS_DATA",
        "android.speech.action.RECOGNIZE_SPEECH",
    )
}

enum class MicAccess { Granted, Denied, NeedsSettings }

object MicAccessRules {
    fun classify(granted: Boolean, shouldShowRationale: Boolean, askedBefore: Boolean): MicAccess = when {
        granted -> MicAccess.Granted
        askedBefore && !shouldShowRationale -> MicAccess.NeedsSettings
        else -> MicAccess.Denied
    }
}

enum class OnDeviceStatus { Available, Unavailable, NotThisAndroid }

object OnDeviceRules {
    const val MIN_SDK = 31

    fun status(sdk: Int, available: Boolean): OnDeviceStatus = when {
        sdk < MIN_SDK -> OnDeviceStatus.NotThisAndroid
        available -> OnDeviceStatus.Available
        else -> OnDeviceStatus.Unavailable
    }
}

data class VoiceDiagnostics(
    val systemRecognizer: Boolean,
    val onDevice: OnDeviceStatus,
    val ttsEngine: String?,
    val faVoice: Boolean?,
    val enVoice: Boolean?,
    val mic: MicAccess,
)

object VoiceDiagnosticsCopy {
    data class Row(val label: String, val value: String)

    fun title(persian: Boolean): String = if (persian) "عیب‌یابی صدا" else "Voice diagnostics"

    fun checking(persian: Boolean): String = if (persian) {
        "در حال بررسی میکروفون و سرویس گفتار…"
    } else {
        "Checking the microphone and speech services…"
    }

    fun rows(d: VoiceDiagnostics, persian: Boolean): List<Row> = listOf(
        Row(
            if (persian) "تشخیص‌گر سیستم" else "System recognizer",
            yesNo(d.systemRecognizer, persian),
        ),
        Row(
            if (persian) "تشخیص روی دستگاه" else "On-device recognizer",
            onDevice(d.onDevice, persian),
        ),
        Row(
            if (persian) "موتور گفتار" else "Speech engine",
            d.ttsEngine?.takeIf { it.isNotBlank() } ?: if (persian) "پیدا نشد" else "None found",
        ),
        Row(if (persian) "صدای فارسی" else "Persian voice", voice(d.faVoice, persian)),
        Row(if (persian) "صدای انگلیسی" else "English voice", voice(d.enVoice, persian)),
        Row(if (persian) "میکروفون" else "Microphone", mic(d.mic, persian)),
    )

    fun lines(d: VoiceDiagnostics, persian: Boolean): List<String> =
        rows(d, persian).map { "${it.label}: ${it.value}" }

    private fun yesNo(value: Boolean, persian: Boolean): String = when {
        value && persian -> "بله"
        value -> "Yes"
        persian -> "نه"
        else -> "No"
    }

    private fun onDevice(status: OnDeviceStatus, persian: Boolean): String = when (status) {
        OnDeviceStatus.Available -> if (persian) "بله" else "Yes"
        OnDeviceStatus.Unavailable -> if (persian) "نه" else "No"
        OnDeviceStatus.NotThisAndroid -> if (persian) "در این نسخهٔ اندروید نیست" else "Not on this Android version"
    }

    private fun voice(installed: Boolean?, persian: Boolean): String = when (installed) {
        true -> if (persian) "نصب است" else "Installed"
        false -> if (persian) "نصب نیست" else "Not installed"
        null -> if (persian) "گزارش نشد" else "Not reported"
    }

    private fun mic(access: MicAccess, persian: Boolean): String = when (access) {
        MicAccess.Granted -> if (persian) "داده شده" else "Granted"
        MicAccess.Denied -> if (persian) "داده نشده" else "Denied"
        MicAccess.NeedsSettings -> if (persian) "باید از تنظیمات روشن شود" else "Needs settings"
    }
}

/** Voice-stage fields the chat screen can reduce without a composable. */
data class VoiceSnap(
    val phase: VoicePhase = VoicePhase.Idle,
    val handsFree: Boolean = false,
    val hint: VoiceHint = VoiceHint.None,
    val error: ListenError? = null,
    val banner: String? = null,
    val listening: Boolean = false,
    val review: Boolean = false,
    val live: String = "",
    val note: ProviderVoiceNote = ProviderVoiceNote.None,
    val path: ListenPath = ListenPath.Phone,
    val pathProvider: String = "",
)

object VoiceSnapRules {
    fun showError(s: VoiceSnap, error: ListenError, persian: Boolean): VoiceSnap {
        val terminal = SpeechErrors.blocksAutoRestart(error)
        return s.copy(
            phase = VoicePhase.Error,
            error = error,
            banner = SpeechErrors.message(error, persian),
            listening = false,
            handsFree = if (terminal) false else s.handsFree,
            hint = SpeechErrors.hintFor(error),
            review = false,
        )
    }

    fun showMissingVoice(s: VoiceSnap, persian: Boolean): VoiceSnap = s.copy(
        phase = VoicePhase.Error,
        error = null,
        banner = SpeechErrors.speakMissing(persian),
        listening = false,
        handsFree = false,
        hint = VoiceHint.InstallTts,
        review = false,
    )

    fun dismiss(s: VoiceSnap): VoiceSnap {
        val terminal = s.hint == VoiceHint.InstallTts ||
            s.hint == VoiceHint.MicSettings ||
            s.hint == VoiceHint.NoRecognizer ||
            (s.error != null && SpeechErrors.blocksAutoRestart(s.error))
        val keepBanner = terminal && !s.banner.isNullOrBlank()
        return s.copy(
            phase = VoicePhase.Idle,
            error = null,
            listening = false,
            handsFree = if (terminal) false else s.handsFree,
            banner = if (keepBanner) s.banner else null,
            hint = if (keepBanner) s.hint else VoiceHint.None,
        )
    }

    fun shouldRestart(s: VoiceSnap, autoRestarts: Int): Boolean =
        s.handsFree &&
            s.phase == VoicePhase.Idle &&
            s.error == null &&
            s.hint == VoiceHint.None &&
            autoRestarts < SpeechErrors.MAX_AUTO_RESTARTS

    fun onRetry(s: VoiceSnap): VoiceSnap = s.copy(
        phase = VoicePhase.Idle,
        error = null,
        banner = null,
        hint = VoiceHint.None,
        listening = false,
        review = false,
        live = "",
    )

    fun onLeave(s: VoiceSnap): VoiceSnap = VoiceSnap()

    fun onSend(s: VoiceSnap): VoiceSnap = s.copy(
        error = null,
        banner = null,
        hint = VoiceHint.None,
        review = false,
        live = "",
        listening = false,
        note = ProviderVoiceNote.None,
        phase = if (s.handsFree) VoicePhase.Processing else VoicePhase.Idle,
    )

    fun onRecognized(s: VoiceSnap): VoiceSnap = s.copy(
        error = null,
        banner = null,
        hint = VoiceHint.None,
        listening = false,
    )

    fun onSpoken(s: VoiceSnap): VoiceSnap = s.copy(
        error = null,
        banner = null,
        hint = VoiceHint.None,
    )

    fun clearBanner(s: VoiceSnap): VoiceSnap = s.copy(
        banner = null,
        error = null,
        hint = VoiceHint.None,
    )
}

data class TranscriptionCandidate(
    val id: String,
    val kind: String,
    val baseUrl: String,
    val hasKey: Boolean,
    val label: String,
)

/** Active chat provider first, then Groq, then official OpenAI, then anything else the caller still allows. */
object TranscriptionChoice {
    fun ordered(
        activeId: String?,
        rows: List<TranscriptionCandidate>,
        usable: (TranscriptionCandidate) -> Boolean,
    ): List<TranscriptionCandidate> = rows
        .filter { it.hasKey && usable(it) }
        .sortedWith(
            compareByDescending<TranscriptionCandidate> { it.id.isNotBlank() && it.id == activeId }
                .thenByDescending { it.kind.equals("GROQ", ignoreCase = true) }
                .thenByDescending { it.kind.equals("OPENAI", ignoreCase = true) },
        )
}
