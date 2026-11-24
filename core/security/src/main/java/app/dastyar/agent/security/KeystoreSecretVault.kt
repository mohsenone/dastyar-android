package app.dastyar.agent.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Singleton
class KeystoreSecretVault @Inject constructor(
    @ApplicationContext context: Context,
) : SecretVault {
    private val mutex = Mutex()
    private val dir = context.noBackupFilesDir
    private val file = File(dir, "vault.json")
    private val modeFile = File(dir, "vault.mode")

    override suspend fun put(id: String, json: String) = guarded {
        val auth = modeAuth()
        val map = decode(auth)
        map[id] = json
        encodeAtomic(map, auth)
    }

    override suspend fun get(id: String): String? = guarded { decode(modeAuth())[id] }

    override suspend fun delete(id: String) = guarded {
        val auth = modeAuth()
        val map = decode(auth)
        map.remove(id)
        encodeAtomic(map, auth)
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            file.delete()
            modeFile.delete()
            val ks = keyStore()
            listOf(alias(true), alias(false)).forEach { name ->
                if (ks.containsAlias(name)) ks.deleteEntry(name)
            }
        }
    }

    override fun hardwareBacked(): Boolean = try {
        val key = existingKey(modeAuth()) ?: return false
        val factory = SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
        val info = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo
        info.isInsideSecureHardware
    } catch (_: Exception) {
        false
    }

    override fun userAuthRequired(): Boolean = modeAuth()

    override suspend fun setUserAuthRequired(required: Boolean) = guarded {
        if (modeAuth() == required) return@guarded
        val secrets = decode(modeAuth())
        try {
            getOrCreate(required)
        } catch (e: Exception) {
            if (required && isLockScreenMissing(e)) throw ScreenLockRequiredException()
            throw e
        }
        val tmp = File(dir, "vault.json.new")
        try {
            writeFile(tmp, secrets, required)
            if (file.exists() && !file.delete()) error("Could not replace the secret vault")
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
            modeFile.writeText(if (required) "auth" else "open")
            val old = alias(!required)
            val ks = keyStore()
            if (ks.containsAlias(old)) ks.deleteEntry(old)
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
    }

    private suspend fun <T> guarded(block: () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                block()
            } catch (e: VaultLockedException) {
                throw e
            } catch (e: Exception) {
                if (isLocked(e)) throw VaultLockedException() else throw e
            }
        }
    }

    private fun modeAuth(): Boolean = modeFile.exists() && modeFile.readText().trim() == "auth"

    private fun alias(auth: Boolean) = if (auth) "dastyar.master.auth.v1" else "dastyar.master.open.v1"

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun existingKey(auth: Boolean): SecretKey? =
        keyStore().getKey(alias(auth), null) as? SecretKey

    private fun getOrCreate(auth: Boolean): SecretKey {
        existingKey(auth)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val builder = KeyGenParameterSpec.Builder(
            alias(auth),
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(auth)
        if (auth) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setUserAuthenticationParameters(
                    AUTH_VALIDITY_SECONDS,
                    KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                )
            } else {
                @Suppress("DEPRECATION")
                builder.setUserAuthenticationValidityDurationSeconds(AUTH_VALIDITY_SECONDS)
            }
        }
        generator.init(builder.build())
        return generator.generateKey()
    }

    private fun decode(auth: Boolean): MutableMap<String, String> {
        if (!file.exists() || file.length() == 0L) return mutableMapOf()
        return try {
            parse(file.readText(), auth)
        } catch (e: Exception) {
            if (isLocked(e)) throw e
            // Recovery if the mode flag and ciphertext key fell out of step.
            parse(file.readText(), !auth)
        }
    }

    private fun parse(raw: String, auth: Boolean): MutableMap<String, String> {
        val root = JSONObject(raw)
        val entries = root.optJSONObject("entries") ?: return mutableMapOf()
        val out = mutableMapOf<String, String>()
        val keys = entries.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            val obj = entries.getJSONObject(id)
            val iv = Base64.decode(obj.getString("iv"), Base64.NO_WRAP)
            val ct = Base64.decode(obj.getString("ct"), Base64.NO_WRAP)
            out[id] = String(decrypt(iv, ct, auth), Charsets.UTF_8)
        }
        return out
    }

    private fun encodeAtomic(map: Map<String, String>, auth: Boolean) {
        val tmp = File(dir, "vault.json.tmp")
        writeFile(tmp, map, auth)
        if (file.exists() && !file.delete()) error("Could not update the secret vault")
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    private fun writeFile(target: File, map: Map<String, String>, auth: Boolean) {
        val entries = JSONObject()
        map.forEach { (id, plain) ->
            val (iv, ct) = encrypt(plain.toByteArray(Charsets.UTF_8), auth)
            entries.put(
                id,
                JSONObject()
                    .put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                    .put("ct", Base64.encodeToString(ct, Base64.NO_WRAP)),
            )
        }
        target.writeText(JSONObject().put("v", 1).put("entries", entries).toString())
    }

    private fun encrypt(plain: ByteArray, auth: Boolean): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreate(auth))
        return cipher.iv to cipher.doFinal(plain)
    }

    private fun decrypt(iv: ByteArray, ct: ByteArray, auth: Boolean): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreate(auth), GCMParameterSpec(128, iv))
        return cipher.doFinal(ct)
    }

    private fun isLocked(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is android.security.keystore.UserNotAuthenticatedException) return true
            if (current.javaClass.name.contains("UserNotAuthenticated")) return true
            current = current.cause
        }
        return false
    }

    private fun isLockScreenMissing(error: Throwable): Boolean {
        val text = generateSequence(error) { it.cause }.joinToString { it.message.orEmpty() }
        return text.contains("secure lock screen", ignoreCase = true) ||
            text.contains("NoSuchAlgorithm", ignoreCase = true) ||
            error is java.security.InvalidAlgorithmParameterException ||
            error is java.lang.IllegalStateException
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val AUTH_VALIDITY_SECONDS = 300
    }
}
