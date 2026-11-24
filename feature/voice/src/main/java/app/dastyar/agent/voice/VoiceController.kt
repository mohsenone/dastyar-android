package app.dastyar.agent.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var recognizer: SpeechRecognizer? = null

    fun recognitionAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun speak(text: String, language: String, onDone: () -> Unit = {}) {
        stopListening()
        val engine = tts ?: TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) speakNow(text, language, onDone)
        }.also { created ->
            tts = created
            created.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = onDone()
                @Deprecated("legacy")
                override fun onError(utteranceId: String?) = onDone()
                override fun onError(utteranceId: String?, errorCode: Int) = onDone()
            })
        }
        if (ready) speakNow(text, language, onDone) else Unit
        if (engine == null) onDone()
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    fun listen(language: String, onPartial: (String) -> Unit, onResult: (String) -> Unit, onError: (String) -> Unit) {
        stopSpeaking()
        if (!recognitionAvailable()) {
            onError("Speech recognition is not installed on this device.")
            return
        }
        val speech = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
        speech.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onError(error: Int) {
                onError(
                    when (error) {
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "Recognizer network failed. On-device recognition depends on the engine installed on the phone."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is missing."
                        else -> "Recognizer error $error"
                    },
                )
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isBlank()) onError("No speech recognized") else onResult(text)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isNotBlank()) onPartial(text)
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (language.startsWith("fa")) "fa-IR" else "en-US")
        speech.startListening(intent)
    }

    fun stopListening() {
        runCatching { recognizer?.stopListening() }
    }

    fun shutdown() {
        runCatching { recognizer?.destroy() }
        recognizer = null
        tts?.shutdown()
        tts = null
        ready = false
    }

    private fun speakNow(text: String, language: String, onDone: () -> Unit) {
        val engine = tts ?: return
        val locale = if (language.startsWith("fa")) Locale("fa", "IR") else Locale.US
        val available = engine.setLanguage(locale)
        if (available == TextToSpeech.LANG_MISSING_DATA || available == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.language = Locale.US
        }
        val id = UUID.randomUUID().toString()
        val spoken = engine.speak(text.take(4000), TextToSpeech.QUEUE_FLUSH, null, id)
        if (spoken == TextToSpeech.ERROR) onDone()
    }
}
