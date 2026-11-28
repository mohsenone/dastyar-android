package app.dastyar.agent

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.view.View
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.dastyar.agent.chat.VoiceStage
import app.dastyar.agent.data.AppLanguage
import app.dastyar.agent.data.Appearance
import app.dastyar.agent.data.LocaleGate
import app.dastyar.agent.ui.DastyarTheme
import app.dastyar.agent.voice.ListenError
import app.dastyar.agent.voice.ListenPath
import app.dastyar.agent.voice.ListenPathCopy
import app.dastyar.agent.voice.ProviderVoiceNote
import app.dastyar.agent.voice.SpeechErrors
import app.dastyar.agent.voice.VoiceController
import app.dastyar.agent.voice.VoiceHint
import app.dastyar.agent.voice.VoiceLang
import app.dastyar.agent.voice.VoicePhase
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject

/** Listed as a digital assistant. The system binds this when the role is held. */
class DastyarVoiceInteractionService : VoiceInteractionService()

@AndroidEntryPoint
class DastyarSessionService : VoiceInteractionSessionService() {
    @Inject lateinit var voice: VoiceController

    override fun onNewSession(args: Bundle?): VoiceInteractionSession = DastyarSession(this, voice)
}

/**
 * Required so the system can list Dastyar as an assistant.
 * Dictation in the app uses the phone's SpeechRecognizer, not this stub.
 */
class DastyarRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        listener?.error(SpeechRecognizer.ERROR_CLIENT)
    }

    override fun onCancel(listener: Callback?) = Unit

    override fun onStopListening(listener: Callback?) = Unit
}

class DastyarSession(
    private val hostContext: Context,
    private val voice: VoiceController,
) : VoiceInteractionSession(hostContext) {
    private val phase = mutableStateOf(VoicePhase.Idle)
    private val live = mutableStateOf("")
    private val hint = mutableStateOf(VoiceHint.None)
    private val error = mutableStateOf<ListenError?>(null)
    private var content: View? = null
    private var owner: SessionOwner? = null
    private val main = Handler(Looper.getMainLooper())
    private var dismiss: Runnable? = null
    private var sessionRestarts = 0

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        setContentView(ensureContent())
        begin()
    }

    override fun onHide() {
        clearDismiss()
        voice.stopListening()
        voice.stopRecording()
        super.onHide()
    }

    override fun onDestroy() {
        clearDismiss()
        voice.stopListening()
        voice.stopRecording()
        owner?.destroy()
        owner = null
        content = null
        super.onDestroy()
    }

    override fun onBackPressed() {
        close()
    }

    private fun ensureContent(): View {
        content?.let { return it }
        val life = SessionOwner().also { owner = it }
        val view = ComposeView(hostContext).apply {
            setViewTreeLifecycleOwner(life)
            setViewTreeSavedStateRegistryOwner(life)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { SessionBody() }
        }
        content = view
        return view
    }

    private fun clearDismiss() {
        dismiss?.let { main.removeCallbacks(it) }
        dismiss = null
    }

    private fun showSessionError(err: ListenError) {
        phase.value = VoicePhase.Error
        error.value = err
        hint.value = SpeechErrors.hintFor(err)
        clearDismiss()
        val retry = !SpeechErrors.blocksAutoRestart(err) && sessionRestarts < SpeechErrors.MAX_AUTO_RESTARTS
        val task = Runnable {
            if (phase.value != VoicePhase.Error) return@Runnable
            if (retry) {
                sessionRestarts++
                begin()
            } else {
                close()
            }
        }
        dismiss = task
        main.postDelayed(task, SpeechErrors.DISMISS_AFTER_MS)
    }

    private fun begin() {
        clearDismiss()
        live.value = ""
        error.value = null
        hint.value = VoiceHint.None
        if (!micGranted()) {
            showSessionError(ListenError.Permission)
            return
        }
        phase.value = VoicePhase.Listening
        val appLang = AppLanguage.uiLang(Bootstrap.language(hostContext), Locale.getDefault().language)
        voice.listen(
            choice = VoiceLang.AUTO,
            appLang = appLang,
            onPartial = { text -> live.value = text },
            onResult = { text ->
                sessionRestarts = 0
                handoff(text)
            },
            onError = { err -> showSessionError(err) },
        )
    }

    private fun handoff(text: String) {
        voice.stopListening()
        val intent = Intent(hostContext, MainActivity::class.java)
            .setAction(MainActivity.ACTION_VOICE_TURN)
            .putExtra(MainActivity.EXTRA_UTTERANCE, text)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        runCatching { startAssistantActivity(intent) }.onFailure {
            runCatching { hostContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        hide()
    }

    private fun openApp(action: String) {
        voice.stopListening()
        val intent = Intent(hostContext, MainActivity::class.java)
            .setAction(action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        runCatching { startAssistantActivity(intent) }.onFailure {
            runCatching { hostContext.startActivity(intent) }
        }
        hide()
    }

    private fun close() {
        voice.stopListening()
        voice.stopRecording()
        hide()
    }

    private fun micGranted(): Boolean = ContextCompat.checkSelfPermission(
        hostContext,
        android.Manifest.permission.RECORD_AUDIO,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    @Composable
    private fun SessionBody() {
        val current by phase
        val said by live
        val shownHint by hint
        val shownError by error
        val lang = AppLanguage.uiLang(LocaleGate.pref, Locale.getDefault().language)
        val dark = when (LocaleGate.themeMode) {
            Appearance.DARK -> true
            Appearance.LIGHT -> false
            else -> isSystemInDarkTheme()
        }
        CompositionLocalProvider(
            LocalLayoutDirection provides if (lang == AppLanguage.PERSIAN) LayoutDirection.Rtl else LayoutDirection.Ltr,
        ) {
            DastyarTheme(dark = dark, dynamicColor = LocaleGate.colorMode == Appearance.DYNAMIC) {
                val persianUi = lang == AppLanguage.PERSIAN
                VoiceStage(
                    phase = current,
                    liveText = said,
                    hint = shownHint,
                    note = ProviderVoiceNote.None,
                    listenError = shownError,
                    persianVoice = VoiceLang.resolved(VoiceLang.AUTO, lang) == VoiceLang.FA,
                    persianUi = persianUi,
                    pathLabel = if (current == VoicePhase.Listening) {
                        ListenPathCopy.line(ListenPath.Phone, "", persianUi)
                    } else {
                        ""
                    },
                    onStop = ::close,
                    onClose = ::close,
                    onRetry = {
                        sessionRestarts = 0
                        begin()
                    },
                    onHint = { openApp(MainActivity.ACTION_VOICE) },
                    onOpenChat = { openApp(MainActivity.ACTION_OPEN_CHAT) },
                )
            }
        }
    }
}

private class SessionOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

    init {
        saved.performAttach()
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        if (registry.currentState != Lifecycle.State.DESTROYED) {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
