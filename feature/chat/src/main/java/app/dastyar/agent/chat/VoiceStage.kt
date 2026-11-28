package app.dastyar.agent.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.dastyar.agent.ui.PrimaryButton
import app.dastyar.agent.ui.Space
import app.dastyar.agent.voice.ListenError
import app.dastyar.agent.voice.ProviderVoiceNote
import app.dastyar.agent.voice.SpeechErrors
import app.dastyar.agent.voice.VoiceHint
import app.dastyar.agent.voice.VoicePhase

@Composable
fun VoiceStage(
    phase: VoicePhase,
    liveText: String,
    hint: VoiceHint,
    note: ProviderVoiceNote,
    listenError: ListenError?,
    persianVoice: Boolean,
    persianUi: Boolean,
    pathLabel: String = "",
    onStop: () -> Unit,
    onClose: () -> Unit,
    onHint: () -> Unit,
    onRetry: () -> Unit = onStop,
    onOpenChat: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val listening = phase == VoicePhase.Listening
    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .padding(Space.s24)
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            tonalElevation = 2.dp,
            shadowElevation = 6.dp,
        ) {
            Column(
                Modifier.padding(horizontal = Space.s16, vertical = Space.s16),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Space.s12),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.voice_close))
                    }
                }
                ListenPulse(active = listening)
                AnimatedContent(targetState = phase, label = "voice-phase") { current ->
                    Text(
                        phaseTitle(current),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                }
                if (pathLabel.isNotBlank() && (phase == VoicePhase.Listening || phase == VoicePhase.Processing)) {
                    Text(
                        pathLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                }
                if (liveText.isNotBlank()) {
                    Text(
                        liveText,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (note == ProviderVoiceNote.Spoken) {
                    Text(
                        stringResource(R.string.voice_provider_spoken),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                } else if (note == ProviderVoiceNote.Transcribed) {
                    Text(
                        stringResource(R.string.voice_provider_transcribed),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                }
                val hintText = hintText(hint, listenError, persianVoice, persianUi)
                if (hintText != null) {
                    Text(
                        hintText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    val action = hintAction(hint)
                    if (action != null) {
                        PrimaryButton(action, onHint, modifier = Modifier.fillMaxWidth())
                    }
                }
                if (onOpenChat != null) {
                    PrimaryButton(stringResource(R.string.voice_open_chat), onOpenChat, modifier = Modifier.fillMaxWidth())
                }
                if (phase == VoicePhase.Error) {
                    PrimaryButton(
                        text = stringResource(R.string.voice_retry),
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    )
                }
                PrimaryButton(
                    text = stringResource(if (phase == VoicePhase.Error) R.string.voice_dismiss else R.string.voice_stop),
                    onClick = if (phase == VoicePhase.Error) onClose else onStop,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                )
            }
        }
    }
}

@Composable
private fun ListenPulse(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "voice-pulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (active) 1.18f else 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
        Box(
            Modifier
                .size(88.dp)
                .graphicsLayer {
                    scaleX = if (active) scale else 1f
                    scaleY = if (active) scale else 1f
                }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
        )
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (active) Icons.Outlined.Mic else Icons.Outlined.Stop,
                contentDescription = null,
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun phaseTitle(phase: VoicePhase): String = when (phase) {
    VoicePhase.Idle -> stringResource(R.string.voice_idle)
    VoicePhase.Listening -> stringResource(R.string.voice_listening)
    VoicePhase.Processing -> stringResource(R.string.voice_processing)
    VoicePhase.Speaking -> stringResource(R.string.voice_speaking)
    VoicePhase.Error -> stringResource(R.string.voice_error)
}

@Composable
private fun hintText(hint: VoiceHint, error: ListenError?, persianVoice: Boolean, persianUi: Boolean): String? = when (hint) {
    VoiceHint.InstallTts -> stringResource(if (persianVoice) R.string.voice_missing_tts else R.string.voice_missing_tts_en)
    VoiceHint.MicSettings -> error?.let { SpeechErrors.message(it, persianUi) } ?: stringResource(R.string.voice_permission)
    VoiceHint.NoRecognizer -> error?.let { SpeechErrors.message(it, persianUi) } ?: stringResource(R.string.voice_no_recognizer)
    VoiceHint.None -> error?.let { SpeechErrors.message(it, persianUi) }
}

@Composable
private fun hintAction(hint: VoiceHint): String? = when (hint) {
    VoiceHint.InstallTts -> stringResource(R.string.voice_install_tts)
    VoiceHint.MicSettings -> stringResource(R.string.voice_open_settings)
    else -> null
}

@Composable
fun listenErrorText(error: ListenError, persianUi: Boolean): String = SpeechErrors.message(error, persianUi)
