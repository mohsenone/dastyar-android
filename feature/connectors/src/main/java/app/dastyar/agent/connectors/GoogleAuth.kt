package app.dastyar.agent.connectors

import android.content.Context
import android.content.Intent
import android.net.Uri
import app.dastyar.agent.security.SecretVault
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ClientSecretPost
import net.openid.appauth.ResponseTypeValues
import org.json.JSONObject

@Singleton
class GoogleAuth @Inject constructor(
    @ApplicationContext context: Context,
    private val vault: SecretVault,
) {
    private val service = AuthorizationService(context)

    fun intent(clientId: String, redirect: String, extraScopes: Boolean): Intent {
        val config = AuthorizationServiceConfiguration(
            Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"),
            Uri.parse("https://oauth2.googleapis.com/token"),
        )
        val verifier = net.openid.appauth.CodeVerifierUtil.generateRandomCodeVerifier()
        val challenge = net.openid.appauth.CodeVerifierUtil.deriveCodeVerifierChallenge(verifier)
        val request = AuthorizationRequest.Builder(config, clientId, ResponseTypeValues.CODE, Uri.parse(redirect))
            .setScopes(GoogleScopes.request(extraScopes))
            .setCodeVerifier(verifier, challenge, "S256")
            .build()
        return service.getAuthorizationRequestIntent(request)
    }

    suspend fun consume(data: Intent?): String {
        if (data == null) return "error: Google sign-in returned nothing."
        val response = AuthorizationResponse.fromIntent(data)
        val failure = AuthorizationException.fromIntent(data)
        if (response == null) {
            return "error: ${failure?.errorDescription ?: failure?.error ?: "Google sign-in was cancelled."}"
        }
        val secret = vault.get(VaultIds.GOOGLE_SECRET)?.trim().orEmpty()
        val token = suspendCancellableCoroutine { cont ->
            val callback = AuthorizationService.TokenResponseCallback { tokenResponse, _ ->
                if (cont.isActive) cont.resume(tokenResponse)
            }
            val exchange = response.createTokenExchangeRequest()
            if (secret.isBlank()) service.performTokenRequest(exchange, callback)
            else service.performTokenRequest(exchange, ClientSecretPost(secret), callback)
        }
        val access = token?.accessToken
        if (access.isNullOrBlank()) return "error: Google did not return an access token. Check the client id, the redirect URI, and the SHA-1 if this is an Android client."
        val json = JSONObject()
            .put("access_token", access)
            .put("refresh_token", token.refreshToken.orEmpty())
            .put("expires_at", token.accessTokenExpirationTime ?: (System.currentTimeMillis() + 3_500_000))
        vault.put(VaultIds.GOOGLE_TOKENS, json.toString())
        return "connected"
    }
}
