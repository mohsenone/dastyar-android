package app.dastyar.agent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import app.dastyar.agent.security.UserAuthenticator
import app.dastyar.agent.tools.UiBridge
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withTimeoutOrNull

sealed class UiRequest {
    class Permissions(
        val permissions: List<String>,
        val rationale: String,
        val reply: CompletableDeferred<Boolean>,
    ) : UiRequest()

    class Photo(val reply: CompletableDeferred<String?>) : UiRequest()
    class Video(val reply: CompletableDeferred<String?>) : UiRequest()
    class Screen(val reply: CompletableDeferred<String?>) : UiRequest()
    class Directory(val reply: CompletableDeferred<String?>) : UiRequest()
    class Document(val name: String, val mime: String, val reply: CompletableDeferred<String?>) : UiRequest()
    class ConfirmImage(
        val path: String,
        val title: String,
        val body: String,
        val reply: CompletableDeferred<Boolean>,
    ) : UiRequest()
}

@Singleton
class AppUiBridge @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authenticator: UserAuthenticator,
    private val overlay: OverlayController,
) : UiBridge {
    private val resumed = AtomicBoolean(false)
    private val outgoing = MutableSharedFlow<UiRequest>(extraBufferCapacity = 8)
    val requests = outgoing.asSharedFlow()
    val resumeEpoch = MutableStateFlow(0)

    fun setResumed(value: Boolean) {
        resumed.set(value)
        if (value) resumeEpoch.value = resumeEpoch.value + 1
    }

    override suspend fun ensurePermissions(permissions: List<String>, rationale: String): Boolean {
        val reply = CompletableDeferred<Boolean>()
        outgoing.emit(UiRequest.Permissions(permissions, rationale, reply))
        return withTimeoutOrNull(180_000) { reply.await() } ?: false
    }

    override suspend fun takePhoto(): String? = ask(90_000) { UiRequest.Photo(it) }

    override suspend fun captureVideo(): String? = ask(180_000) { UiRequest.Video(it) }

    override suspend fun captureScreen(): String? = ask(45_000) { UiRequest.Screen(it) }

    override suspend fun pickDirectory(): String? = ask(180_000) { UiRequest.Directory(it) }

    override suspend fun createDocument(name: String, mime: String): String? =
        ask(180_000) { UiRequest.Document(name, mime, it) }

    override suspend fun confirmImage(path: String, title: String, body: String): Boolean {
        val reply = CompletableDeferred<Boolean>()
        outgoing.emit(UiRequest.ConfirmImage(path, title, body, reply))
        return withTimeoutOrNull(180_000) { reply.await() } ?: false
    }

    override fun openAccessibilitySettings() = launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    override fun openNotificationListenerSettings() = launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))

    override fun openOverlaySettings() = launch(
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
    )

    override fun openAllFilesSettings() {
        val intent = if (Build.VERSION.SDK_INT >= 30) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        }
        launch(intent)
    }

    override fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= 31) {
            launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
        }
    }

    override fun openUsageAccessSettings() = launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

    override fun openAppSettings() = launch(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
    )

    override fun openBatterySettings() = launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

    override fun openLocationSettings() = launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))

    override suspend fun authenticate(title: String): Boolean = authenticator.authenticate(title)

    override fun showOverlay() = overlay.show()

    override fun hideOverlay() = overlay.hide()

    override fun isResumed(): Boolean = resumed.get()

    private suspend fun ask(timeoutMs: Long, build: (CompletableDeferred<String?>) -> UiRequest): String? {
        val reply = CompletableDeferred<String?>()
        outgoing.emit(build(reply))
        return withTimeoutOrNull(timeoutMs) { reply.await() }
    }

    private fun launch(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
