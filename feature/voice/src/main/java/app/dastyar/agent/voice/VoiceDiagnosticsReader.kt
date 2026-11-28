package app.dastyar.agent.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceDiagnosticsReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val voice: VoiceController,
) {
    fun base(mic: MicAccess, sdk: Int = Build.VERSION.SDK_INT): VoiceDiagnostics {
        val onDevice = OnDeviceRules.status(sdk, voice.onDeviceAvailable())
        return VoiceDiagnostics(
            systemRecognizer = voice.onlineComponent() != null,
            onDevice = onDevice,
            ttsEngine = defaultTtsLabel(),
            faVoice = null,
            enVoice = null,
            mic = mic,
        )
    }

    /** Reads installed voices, then shuts the probe engine down. Returns a cancel hook. */
    fun enrichVoices(current: VoiceDiagnostics, onReady: (VoiceDiagnostics) -> Unit): () -> Unit {
        val box = arrayOfNulls<TextToSpeech>(1)
        var done = false
        val created = TextToSpeech(context) { status ->
            val engine = box[0]
            if (done) {
                runCatching { engine?.shutdown() }
                return@TextToSpeech
            }
            done = true
            val voices = if (status == TextToSpeech.SUCCESS) engine?.voices else null
            val fa = voices?.any { it.locale?.language.equals("fa", ignoreCase = true) }
            val en = voices?.any { it.locale?.language.equals("en", ignoreCase = true) }
            val name = engine?.defaultEngine?.let(::labelOf) ?: current.ttsEngine
            onReady(
                current.copy(
                    ttsEngine = name,
                    faVoice = if (voices == null) null else fa == true,
                    enVoice = if (voices == null) null else en == true,
                ),
            )
            runCatching { engine?.shutdown() }
        }
        box[0] = created
        return {
            done = true
            runCatching { box[0]?.shutdown() }
        }
    }

    private fun defaultTtsLabel(): String? {
        val pkg = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.TTS_DEFAULT_SYNTH)
        }.getOrNull()
        if (!pkg.isNullOrBlank()) return labelOf(pkg)
        val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
        val info = runCatching { context.packageManager.queryIntentServices(intent, 0).firstOrNull() }.getOrNull()
        val service = info?.serviceInfo ?: return null
        return labelOf(service.packageName)
    }

    private fun labelOf(packageName: String): String {
        val label = runCatching {
            val app = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(app).toString()
        }.getOrNull()
        return label?.takeIf { it.isNotBlank() } ?: packageName
    }
}
