package app.dastyar.agent.voice

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
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
    private var tts: TextToSpeech? = null
    private var ready = false
    private var recognizer: SpeechRecognizer? = null
    private var player: MediaPlayer? = null
    private var speakCallback: ((SpeakOutcome) -> Unit)? = null
    private var playCallback: ((Boolean) -> Unit)? = null

    @Volatile private var stopRecord = false

    fun recognitionAvailable(): Boolean = runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)

    fun speak(text: String, language: String, onResult: (SpeakOutcome) -> Unit) {
        stopListening()
        val spoken = Speakable.reply(text)
        if (spoken.isBlank()) {
            onResult(SpeakOutcome.Empty)
            return
        }
        val once = AtomicBoolean(false)
        fun finish(outcome: SpeakOutcome) {
            if (once.compareAndSet(false, true)) {
                speakCallback = null
                onResult(outcome)
            }
        }
        speakCallback = { finish(it) }
        val engine = tts
        if (engine != null && ready) {
            speakNow(engine, spoken, language, ::finish)
            return
        }
        val created = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            val current = tts
            if (!ready || current == null) finish(SpeakOutcome.Failed) else speakNow(current, spoken, language, ::finish)
        }
        tts = created
        created.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                speakCallback?.invoke(SpeakOutcome.Spoken)
            }
            @Deprecated("legacy")
            override fun onError(utteranceId: String?) {
                speakCallback?.invoke(SpeakOutcome.Failed)
            }
            override fun onError(utteranceId: String?, errorCode: Int) {
                speakCallback?.invoke(SpeakOutcome.Failed)
            }
        })
    }

    suspend fun speakAwait(text: String, language: String): SpeakOutcome = suspendCancellableCoroutine { cont ->
        speak(text, language) { outcome -> if (cont.isActive) cont.resume(outcome) }
        cont.invokeOnCancellation { stopSpeaking() }
    }

    fun stopSpeaking() {
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

    fun listen(
        choice: String,
        appLang: String,
        onPartial: (String) -> Unit,
        onResult: (String) -> Unit,
        onError: (ListenError) -> Unit,
    ) {
        stopSpeaking()
        if (!recognitionAvailable()) {
            onError(ListenError.NoRecognizer)
            return
        }
        val speech = runCatching {
            recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
        }.getOrNull()
        if (speech == null) {
            onError(ListenError.NoRecognizer)
            return
        }
        speech.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onError(error: Int) {
                onError(
                    when (error) {
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> ListenError.Network
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> ListenError.Permission
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> ListenError.NoMatch
                        SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_SERVER -> ListenError.Unavailable
                        else -> ListenError.Unknown
                    },
                )
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isBlank()) onError(ListenError.NoMatch) else onResult(text)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isNotBlank()) onPartial(text)
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val tag = VoiceLang.recognizerTag(choice, appLang)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
        if (VoiceLang.requestDetection(choice)) {
            intent.putExtra("android.speech.extra.ENABLE_LANGUAGE_DETECTION", true)
        }
        runCatching { speech.startListening(intent) }.onFailure { onError(ListenError.Unavailable) }
    }

    fun stopListening() {
        runCatching { recognizer?.stopListening() }
        runCatching { recognizer?.cancel() }
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
        if (data.isEmpty()) return null
        return wavHeader(data.size, rate) + data
    }

    fun playMp3(bytes: ByteArray, onDone: (Boolean) -> Unit) {
        stopSpeaking()
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

    suspend fun playAwait(bytes: ByteArray): Boolean = suspendCancellableCoroutine { cont ->
        playMp3(bytes) { ok -> if (cont.isActive) cont.resume(ok) }
        cont.invokeOnCancellation { stopSpeaking() }
    }

    fun shutdown() {
        stopListening()
        stopRecording()
        stopSpeaking()
        runCatching { recognizer?.destroy() }
        recognizer = null
        tts?.shutdown()
        tts = null
        ready = false
    }

    private fun speakNow(engine: TextToSpeech, text: String, language: String, finish: (SpeakOutcome) -> Unit) {
        val wantFa = TtsSupport.prefersPersian(language, text)
        val locale = if (wantFa) Locale("fa", "IR") else Locale.US
        val code = runCatching { engine.setLanguage(locale) }.getOrDefault(TtsSupport.NOT_SUPPORTED)
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
