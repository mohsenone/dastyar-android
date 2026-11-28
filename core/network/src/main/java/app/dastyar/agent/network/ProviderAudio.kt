package app.dastyar.agent.network

import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

enum class AudioHope { KnownYes, Probe, KnownNo }

sealed class TranscribeOutcome {
    data class Text(val value: String) : TranscribeOutcome()
    data object MissingKey : TranscribeOutcome()
    data object Unauthorized : TranscribeOutcome()
    data object Unsupported : TranscribeOutcome()
    data object Empty : TranscribeOutcome()
    data object Failed : TranscribeOutcome()
}

/**
 * Speech (TTS) and transcription are not the same route.
 * Official OpenAI is a known speech host. Groq is not assumed to speak.
 * Transcription treats Groq and official OpenAI as known Whisper-style hosts.
 * A custom or non-official OpenAI-compatible base is only a probe.
 * Gemini, OpenRouter, and the other built-in hosts are not assumed to transcribe.
 */
object ProviderAudioPolicy {
    private val never = setOf(
        ProviderKinds.GEMINI,
        ProviderKinds.ANTHROPIC,
        ProviderKinds.GROQ,
        ProviderKinds.OPENROUTER,
        ProviderKinds.CLOUDFLARE,
        ProviderKinds.NVIDIA,
        ProviderKinds.CEREBRAS,
        ProviderKinds.GITHUB_MODELS,
        ProviderKinds.MISTRAL,
        ProviderKinds.DEEPSEEK,
        ProviderKinds.XAI,
    )

    fun route(kind: String, baseUrl: String): AudioHope {
        val key = kind.trim().uppercase()
        if (key in never) return AudioHope.KnownNo
        val host = hostOf(baseUrl)
        if (key == ProviderKinds.OPENAI && host == "api.openai.com") return AudioHope.KnownYes
        if (key == ProviderKinds.OPENAI || key == ProviderKinds.CUSTOM) return AudioHope.Probe
        return AudioHope.KnownNo
    }

    fun shouldTry(onDeviceFailed: Boolean, route: AudioHope, probeAlreadyFailed: Boolean): Boolean {
        if (!onDeviceFailed) return false
        return when (route) {
            AudioHope.KnownYes -> true
            AudioHope.Probe -> !probeAlreadyFailed
            AudioHope.KnownNo -> false
        }
    }

    /** Whisper-style POST /v1/audio/transcriptions. Groq's compatible host is included. */
    fun transcriptionRoute(kind: String, baseUrl: String): AudioHope {
        val key = kind.trim().uppercase()
        val host = hostOf(baseUrl)
        if (key == ProviderKinds.GROQ || host == "api.groq.com") return AudioHope.KnownYes
        if (key == ProviderKinds.OPENAI && host == "api.openai.com") return AudioHope.KnownYes
        if (key == ProviderKinds.OPENAI || key == ProviderKinds.CUSTOM) return AudioHope.Probe
        return AudioHope.KnownNo
    }

    fun shouldTranscribe(hasKey: Boolean, route: AudioHope, probeAlreadyFailed: Boolean): Boolean {
        if (!hasKey) return false
        return when (route) {
            AudioHope.KnownYes -> true
            AudioHope.Probe -> !probeAlreadyFailed
            AudioHope.KnownNo -> false
        }
    }

    /** Groq rejects OpenAI's `whisper-1` id. Other compatible hosts keep that id. */
    fun transcriptionModel(kind: String, baseUrl: String): String {
        val key = kind.trim().uppercase()
        val host = hostOf(baseUrl)
        return if (key == ProviderKinds.GROQ || host == "api.groq.com") "whisper-large-v3-turbo" else "whisper-1"
    }

    fun hostOf(baseUrl: String): String {
        val trimmed = baseUrl.trim()
        val withoutScheme = trimmed.substringAfter("://", trimmed)
        return withoutScheme.substringBefore('/').substringBefore(':').lowercase()
    }

    fun speechUrl(baseUrl: String): String = audioUrl(baseUrl, "speech")

    fun transcriptionUrl(baseUrl: String): String = audioUrl(baseUrl, "transcriptions")

    private fun audioUrl(baseUrl: String, leaf: String): String {
        val base = baseUrl.trim().trimEnd('/')
        if (base.endsWith("/audio/$leaf")) return base
        val root = if (base.endsWith("/v1")) base else "$base/v1"
        return "$root/audio/$leaf"
    }
}

@Singleton
class ProviderAudioClient @Inject constructor() {
    fun speech(
        baseUrl: String,
        apiKey: String,
        text: String,
        extraHeaders: Map<String, String> = emptyMap(),
    ): ByteArray? {
        if (apiKey.isBlank() || text.isBlank()) return null
        val payload = JSONObject()
            .put("model", "tts-1")
            .put("input", text.take(1500))
            .put("voice", "alloy")
            .toString()
        val call = Net.request(
            ProviderAudioPolicy.speechUrl(baseUrl),
            payload,
            headers(apiKey, extraHeaders),
        )
        return runCatching {
            Net.client.newCall(call).execute().use { response ->
                if (!response.isSuccessful) return null
                val type = response.header("Content-Type").orEmpty()
                if (type.contains("json", ignoreCase = true)) return null
                response.body?.bytes()?.takeIf { it.size > 64 }
            }
        }.getOrNull()
    }

    fun transcribe(
        baseUrl: String,
        apiKey: String,
        wav: ByteArray,
        language: String,
        extraHeaders: Map<String, String> = emptyMap(),
        kind: String = "",
    ): TranscribeOutcome {
        if (apiKey.isBlank()) return TranscribeOutcome.MissingKey
        if (wav.isEmpty()) return TranscribeOutcome.Empty
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", ProviderAudioPolicy.transcriptionModel(kind, baseUrl))
            .addFormDataPart("language", language.take(8))
            .addFormDataPart("file", "speech.wav", wav.toRequestBody("audio/wav".toMediaType()))
            .build()
        val builder = Request.Builder()
            .url(ProviderAudioPolicy.transcriptionUrl(baseUrl))
            .post(body)
        headers(apiKey, extraHeaders).forEach { (key, value) -> builder.header(key, value) }
        return runCatching {
            Net.client.newCall(builder.build()).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                when (response.code) {
                    401, 403 -> return TranscribeOutcome.Unauthorized
                    404, 405, 501 -> return TranscribeOutcome.Unsupported
                }
                if (!response.isSuccessful) {
                    return if (response.code in 400..499) TranscribeOutcome.Unsupported else TranscribeOutcome.Failed
                }
                val text = runCatching { JSONObject(raw).optString("text") }.getOrDefault("").trim()
                if (text.isBlank()) TranscribeOutcome.Empty else TranscribeOutcome.Text(text)
            }
        }.getOrDefault(TranscribeOutcome.Failed)
    }

    private fun headers(apiKey: String, extraHeaders: Map<String, String>): Map<String, String> {
        val out = linkedMapOf<String, String>()
        extraHeaders.forEach { (key, value) ->
            if (key.isNotBlank() && value.isNotBlank() && !key.equals("Authorization", ignoreCase = true)) {
                out[key] = value
            }
        }
        out["Authorization"] = "Bearer $apiKey"
        return out
    }
}
