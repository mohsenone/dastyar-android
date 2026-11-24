package app.dastyar.agent.security

/** UI-side biometric or device-credential prompt. Implemented in the app module. */
interface UserAuthenticator {
    suspend fun authenticate(title: String): Boolean
}
