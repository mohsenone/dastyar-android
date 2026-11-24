package app.dastyar.agent.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

internal object Net {
    val jsonMedia = "application/json; charset=utf-8".toMediaType()
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun request(url: String, body: String, headers: Map<String, String>): Request {
        val builder = Request.Builder().url(url).post(body.toRequestBody(jsonMedia))
        headers.forEach { (k, v) ->
            if (k.isNotBlank() && v.isNotBlank() && !k.equals("Authorization", ignoreCase = true)) {
                builder.header(k, v)
            }
        }
        headers.entries.firstOrNull { it.key.equals("Authorization", ignoreCase = true) }?.let {
            builder.header("Authorization", it.value)
        }
        return builder.build()
    }

    fun failureFor(code: Int, body: String): LlmStreamEvent.Failure {
        val message = extractMessage(body)
        return when (code) {
            401 -> LlmStreamEvent.Failure(LlmErrorCode.UNAUTHORIZED, message, suggestProxy = false)
            403 -> LlmStreamEvent.Failure(LlmErrorCode.FORBIDDEN, message, suggestProxy = true)
            429 -> LlmStreamEvent.Failure(LlmErrorCode.RATE_LIMIT, message, suggestProxy = false)
            else -> LlmStreamEvent.Failure(LlmErrorCode.HTTP, message, suggestProxy = code >= 500)
        }
    }

    fun networkFailure(error: Throwable): LlmStreamEvent.Failure {
        val message = redactSecrets(error.message ?: error.javaClass.simpleName)
        return LlmStreamEvent.Failure(LlmErrorCode.NETWORK, message, suggestProxy = true)
    }

    fun extractMessage(body: String): String {
        val parsed = try {
            val json = JSONObject(body)
            json.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                ?: json.optString("message").takeIf { it.isNotBlank() }
                ?: body
        } catch (_: Exception) {
            body
        }
        return redactSecrets(parsed)
    }

    fun normalizeBase(base: String): String = base.trim().trimEnd('/')

    fun jpegDataUri(path: String, maxEdge: Int = 1280): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxEdge * 2 || bounds.outHeight / sample > maxEdge * 2) {
            sample *= 2
        }
        val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scale = maxOf(bitmap.width, bitmap.height).toFloat() / maxEdge
        val scaled = if (scale > 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width / scale).toInt(), (bitmap.height / scale).toInt(), true)
        } else {
            bitmap
        }
        val bytes = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 72, bytes)
        if (scaled !== bitmap) bitmap.recycle()
        scaled.recycle()
        val b64 = Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$b64"
    }

    fun rawBase64(dataUri: String): String = dataUri.substringAfter("base64,", dataUri)
}
