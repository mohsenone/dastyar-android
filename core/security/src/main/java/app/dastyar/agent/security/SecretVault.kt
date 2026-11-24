package app.dastyar.agent.security

/** Thrown when the Keystore key requires a recent device unlock. */
class VaultLockedException : Exception("Keystore key is locked")

/** Thrown when biometric gating cannot be enabled because the device has no lock screen. */
class ScreenLockRequiredException : Exception("Screen lock required")

/**
 * Encrypts provider secrets with an Android Keystore AES-256-GCM key.
 * Payloads never go through plain SharedPreferences.
 */
interface SecretVault {
    suspend fun put(id: String, json: String)

    suspend fun get(id: String): String?

    suspend fun delete(id: String)

    /** Deletes every secret and the Keystore keys. Does not require biometric unlock. */
    suspend fun clear()

    fun hardwareBacked(): Boolean

    fun userAuthRequired(): Boolean

    suspend fun setUserAuthRequired(required: Boolean)
}
