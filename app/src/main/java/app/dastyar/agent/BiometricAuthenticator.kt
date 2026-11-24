package app.dastyar.agent

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.dastyar.agent.security.UserAuthenticator
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Singleton
class BiometricAuthenticator @Inject constructor(
    @ApplicationContext private val context: Context,
) : UserAuthenticator {
    private val activityRef = AtomicReference<FragmentActivity?>(null)

    fun attach(activity: FragmentActivity) {
        activityRef.set(activity)
    }

    fun detach(activity: FragmentActivity) {
        activityRef.compareAndSet(activity, null)
    }

    override suspend fun authenticate(title: String): Boolean = withContext(Dispatchers.Main.immediate) {
        val activity = activityRef.get() ?: return@withContext false
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val can = BiometricManager.from(context).canAuthenticate(authenticators)
        if (can != BiometricManager.BIOMETRIC_SUCCESS) return@withContext false
        suspendCancellableCoroutine { cont ->
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (cont.isActive) cont.resume(false)
                    }
                },
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setAllowedAuthenticators(authenticators)
                .build()
            runCatching { prompt.authenticate(info) }.onFailure {
                if (cont.isActive) cont.resume(false)
            }
            cont.invokeOnCancellation { runCatching { prompt.cancelAuthentication() } }
        }
    }
}
