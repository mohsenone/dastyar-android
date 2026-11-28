package app.dastyar.agent.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

@Singleton
class VoiceController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var recognizer: SpeechRecognizer? = null
    private var player: MediaPlayer? = null
    private var speakCallback: ((SpeakOutcome) -> Unit)? = null
    private var playCallback: ((Boolean) -> Unit)? = null
    private var speakTimeout: Runnable? = null
    private var triedOnDevice = false

    @Volatile private var stopRecord = false

    /** True only when an engine other than this app's listing stub can take audio. */
    fun recognitionAvailable(): Boolean = onlineComponent() != null || onDeviceAvailable()

    fun onlineComponent(): String? = SpeechEnginePicker.bestOnline(recognitionComponents(), context.packageName)

    fun onDeviceAvailable(): Boolean {
        if (Build.VERSION.SDK_INT < OnDeviceRules.MIN_SDK) return false
        return runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)
    }

    fun recognitionComponents(): List<String> {
        val intent = Intent("android.speech.RecognitionService")
        val infos = runCatching {
            context.packageManager.queryIntentServices(intent, PackageManager.MATCH_ALL)
        }.getOrDefault(emptyList())
        return infos.mapNotNull { info ->
            val service = info.serviceInfo ?: return@mapNotNull null
            val pkg = service.packageName ?: return@mapNotNull null
            val name = service.name ?: return@mapNotNull null
            "$pkg/$name"
        }
    }

    fun speak(text: String, language: String, onResult: (SpeakOutcome) -> Unit) {
        onMain {
            stopListeningOnMain()
            val spoken = Speakable.reply(text)
            if (spoken.isBlank()) {
                onResult(SpeakOutcome.Empty)
                return@onMain
            }
            val once = AtomicBoolean(false)
            fun finish(outcome: SpeakOutcome) {
                if (!once.compareAndSet(false, true)) return
                speakTimeout?.let { main.removeCallbacks(it) }
                speakTimeout = null
                speakCallback = null
                onResult(outcome)
            }
            speakCallback = { finish(it) }
            val timeout = Runnable { finish(SpeakOutcome.Failed) }
            speakTimeout = timeout
            val waitMs = (spoken.length * 90L + 2_000L).coerceIn(8_000L, 45_000L)
            main.postDelayed(timeout, waitMs)
            val engine = tts
            if (engine != null && ready) {
                attachProgress(engine)
                speakNow(engine, spoken, language, ::finish)
                return@onMain
            }
            val box = arrayOfNulls<TextToSpeech>(1)
            val created = TextToSpeech(context) { status ->
                onMain {
                    val current = box[0] ?: tts
                    ready = status == TextToSpeech.SUCCESS && current != null
                    if (current != null) attachProgress(current)
                    if (!ready || current == null) finish(SpeakOutcome.Failed) else speakNow(current, spoken, language, ::finish)
                }
            }
            box[0] = created
            tts = created
            attachProgress(created)
        }
    }

    suspend fun speakAwait(text: String, language: String): SpeakOutcome = suspendCancellableCoroutine { cont ->
        speak(text, language) { outcome -> if (cont.isActive) cont.resume(outcome) }
        cont.invokeOnCancellation { stopSpeaking() }
    }

    fun stopSpeaking() {
        onMain { stopSpeakingOnMain() }
    }

    fun listen(
        choice: String,
        appLang: String,
        onPartial: (String) -> Unit,
        onResult: (String) -> Unit,
        onError: (ListenError) -> Unit,
    ) {
        onMain {
            triedOnDevice = false
            stopSpeakingOnMain()
            val online = onlineComponent()
            val onDevice = onDeviceAvailable()
            when (SpeechEnginePicker.choose(recognitionComponents(), context.packageName, onDevice)) {
                SpeechEnginePicker.Kind.Online -> startEngine(SpeechEnginePicker.Kind.Online, online, choice, appLang, onPartial, onResult, onError)
                SpeechEnginePicker.Kind.OnDevice -> startEngine(SpeechEnginePicker.Kind.OnDevice, null, choice, appLang, onPartial, onResult, onError)
                SpeechEnginePicker.Kind.None -> onError(ListenError.NoRecognizer)
            }
        }
    }

    fun stopListening() {
        onMain { stopListeningOnMain() }
    }

    fun stopRecording() {
        stopRecord = true
    }

    /** Blocks. Call off the main thread. Returns a WAV clip, or null when the mic cannot start. */
    fun recordWav(maxMs: Int = 8_000): ByteArray? {
        stopRecord = false
        val rate = 16_000
        val min = AudioRecord.getMinBufferSize(
            rate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (min <= 0) return null
        val rec = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                min * 2,
            )
        }.getOrNull() ?: return null
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return null
        }
        val pcm = ByteArrayOutputStream()
        val buf = ByteArray(min)
        runCatching { rec.startRecording() }.onFailure {
            rec.release()
            return null
        }
        val start = SystemClock.elapsedRealtime()
        try {
            while (!stopRecord && SystemClock.elapsedRealtime() - start < maxMs) {
                val read = rec.read(buf, 0, buf.size)
                if (read > 0) pcm.write(buf, 0, read)
            }
        } finally {
            runCatching { rec.stop() }
            rec.release()
        }
        val data = pcm.toByteArray()
        if (data.size < rate / 5) return null
        return wavHeader(data.size, rate) + data
    }

    fun playMp3(bytes: ByteArray, onDone: (Boolean) -> Unit) {
        onMain {
            stopSpeakingOnMain()
            val once = AtomicBoolean(false)
            fun finish(ok: Boolean) {
                if (once.compareAndSet(false, true)) {
                    playCallback = null
                    onDone(ok)
                }
            }
            playCallback = { finish(it) }
            val file = File(context.cacheDir, "dastyar-voice-${UUID.randomUUID()}.mp3")
            val started = runCatching {
                file.writeBytes(bytes)
                val media = MediaPlayer()
                player = media
                media.setOnCompletionListener {
                    releasePlayer(it, file)
                    finish(true)
                }
                media.setOnErrorListener { mp, _, _ ->
                    releasePlayer(mp, file)
                    finish(false)
                    true
                }
                media.setDataSource(file.absolutePath)
                media.prepare()
                media.start()
            }
            if (started.isFailure) {
                file.delete()
                finish(false)
            }
        }
    }

    suspend fun playAwait(bytes: ByteArray): Boolean = suspendCancellableCoroutine { cont ->
        playMp3(bytes) { ok -> if (cont.isActive) cont.resume(ok) }
        cont.invokeOnCancellation { stopSpeaking() }
    }

    fun shutdown() {
        onMain {
            stopListeningOnMain()
            stopRecord = true
            stopSpeakingOnMain()
            destroyRecognizer()
            tts?.shutdown()
            tts = null
            ready = false
        }
    }

    private fun startEngine(
        kind: SpeechEnginePicker.Kind,
        component: String?,
        choice: String,
        appLang: String,
        onPartial: (String) -> Unit,
        onResult: (String) -> Unit,
        onError: (ListenError) -> Unit,
    ) {
        val speech = createRecognizer(kind, component)
        if (speech == null) {
            if (kind == SpeechEnginePicker.Kind.Online && !triedOnDevice && onDeviceAvailable()) {
                triedOnDevice = true
                startEngine(SpeechEnginePicker.Kind.OnDevice, null, choice, appLang, onPartial, onResult, onError)
            } else {
                onError(ListenError.NoRecognizer)
            }
            return
        }
        var lastPartial = ""
        speech.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onError(error: Int) {
                onMain {
                    if (SpeechErrors.engineUnusable(error) && kind == SpeechEnginePicker.Kind.Online && !triedOnDevice && onDeviceAvailable()) {
                        triedOnDevice = true
                        startEngine(SpeechEnginePicker.Kind.OnDevice, null, choice, appLang, onPartial, onResult, onError)
                    } else {
                        onError(SpeechErrors.listenError(error))
                    }
                }
            }
            override fun onResults(results: Bundle?) {
                val text = RecognitionText.first(
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION),
                    results?.getString("android.speech.extra.UNSTABLE_TEXT"),
                    lastPartial,
                )
                onMain {
                    if (text.isBlank()) onError(ListenError.NoMatch) else onResult(text)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val text = RecognitionText.first(
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION),
                    partialResults?.getString("android.speech.extra.UNSTABLE_TEXT"),
                )
                if (text.isNotBlank()) {
                    lastPartial = text
                    onMain { onPartial(text) }
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val tag = VoiceLang.recognizerTag(choice, appLang)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        if (kind == SpeechEnginePicker.Kind.OnDevice) {
            intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        if (VoiceLang.requestDetection(choice)) {
            intent.putExtra("android.speech.extra.ENABLE_LANGUAGE_DETECTION", true)
        }
        val started = runCatching { speech.startListening(intent) }
        if (started.isFailure) {
            if (kind == SpeechEnginePicker.Kind.Online && !triedOnDevice && onDeviceAvailable()) {
                triedOnDevice = true
                startEngine(SpeechEnginePicker.Kind.OnDevice, null, choice, appLang, onPartial, onResult, onError)
            } else {
                onError(ListenError.Client)
            }
        }
    }

    private fun createRecognizer(kind: SpeechEnginePicker.Kind, component: String?): SpeechRecognizer? {
        destroyRecognizer()
        return runCatching {
            when (kind) {
                SpeechEnginePicker.Kind.OnDevice -> {
                    if (Build.VERSION.SDK_INT < OnDeviceRules.MIN_SDK) return null
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                }
                SpeechEnginePicker.Kind.Online -> {
                    val flat = component ?: return null
                    val parts = flat.split('/', limit = 2)
                    if (parts.size != 2 || parts[0] == context.packageName) return null
                    SpeechRecognizer.createSpeechRecognizer(context, ComponentName(parts[0], parts[1]))
                }
                SpeechEnginePicker.Kind.None -> null
            }
        }.getOrNull()?.also { recognizer = it }
    }

    private fun destroyRecognizer() {
        val current = recognizer
        recognizer = null
        if (current != null) {
            runCatching { current.cancel() }
            runCatching { current.destroy() }
        }
    }

    private fun stopListeningOnMain() {
        runCatching { recognizer?.stopListening() }
        runCatching { recognizer?.cancel() }
    }

    private fun stopSpeakingOnMain() {
        speakTimeout?.let { main.removeCallbacks(it) }
        speakTimeout = null
        runCatching { tts?.stop() }
        val active = player
        player = null
        if (active != null) {
            runCatching { active.stop() }
            runCatching { active.release() }
        }
        val speak = speakCallback
        speakCallback = null
        speak?.invoke(SpeakOutcome.Stopped)
        val play = playCallback
        playCallback = null
        play?.invoke(false)
    }

    private fun attachProgress(engine: TextToSpeech) {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                onMain { speakCallback?.invoke(SpeakOutcome.Spoken) }
            }
            @Deprecated("legacy")
            override fun onError(utteranceId: String?) {
                onMain { speakCallback?.invoke(SpeakOutcome.Failed) }
            }
            override fun onError(utteranceId: String?, errorCode: Int) {
                onMain { speakCallback?.invoke(SpeakOutcome.Failed) }
            }
        })
    }

    private fun speakNow(engine: TextToSpeech, text: String, language: String, finish: (SpeakOutcome) -> Unit) {
        val wantFa = TtsSupport.prefersPersian(language, text)
        val locales = if (wantFa) {
            listOf(Locale("fa", "IR"), Locale("fa"))
        } else {
            listOf(Locale.US, Locale.UK, Locale.ENGLISH)
        }
        var code = TtsSupport.NOT_SUPPORTED
        for (locale in locales) {
            val next = runCatching { engine.setLanguage(locale) }.getOrDefault(TtsSupport.NOT_SUPPORTED)
            code = next
            if (!TtsSupport.missing(next)) break
        }
        val tags = runCatching { engine.voices?.map { it.locale.toLanguageTag() } }.getOrNull()
        val missing = if (wantFa) TtsSupport.persianMissing(code, tags) else TtsSupport.missing(code)
        if (missing) {
            finish(SpeakOutcome.MissingVoice)
            return
        }
        val match = runCatching {
            engine.voices?.firstOrNull { voice ->
                val tag = voice.locale.toLanguageTag().lowercase()
                if (wantFa) tag.startsWith("fa") else tag.startsWith("en")
            }
        }.getOrNull()
        if (match != null) runCatching { engine.voice = match }
        val id = UUID.randomUUID().toString()
        val spoken = runCatching { engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) }.getOrDefault(TextToSpeech.ERROR)
        if (spoken == TextToSpeech.ERROR) finish(SpeakOutcome.Failed)
    }

    private fun releasePlayer(media: MediaPlayer, file: File) {
        if (player === media) player = null
        runCatching { media.release() }
        file.delete()
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    private fun wavHeader(pcmBytes: Int, rate: Int): ByteArray {
        val channels = 1
        val bits = 16
        val byteRate = rate * channels * bits / 8
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        writeInt(header, 4, 36 + pcmBytes)
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        writeInt(header, 16, 16)
        header[20] = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        writeInt(header, 24, rate)
        writeInt(header, 28, byteRate)
        header[32] = (channels * bits / 8).toByte()
        header[33] = 0
        header[34] = bits.toByte()
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        writeInt(header, 40, pcmBytes)
        return header
    }

    private fun writeInt(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value and 0xff).toByte()
        target[offset + 1] = (value shr 8 and 0xff).toByte()
        target[offset + 2] = (value shr 16 and 0xff).toByte()
        target[offset + 3] = (value shr 24 and 0xff).toByte()
    }
}
