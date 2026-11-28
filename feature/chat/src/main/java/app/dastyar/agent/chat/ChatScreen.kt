package app.dastyar.agent.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dastyar.agent.data.MessageEntity
import app.dastyar.agent.ui.ChromeMetrics
import app.dastyar.agent.ui.EmptyState
import app.dastyar.agent.ui.ErrorBanner
import app.dastyar.agent.ui.MenuNavigation
import app.dastyar.agent.ui.PrimaryButton
import app.dastyar.agent.ui.Space
import app.dastyar.agent.ui.StatusChip
import app.dastyar.agent.ui.StatusTone
import app.dastyar.agent.voice.ListenPathCopy
import app.dastyar.agent.voice.VoiceHint
import app.dastyar.agent.voice.VoiceLang
import app.dastyar.agent.voice.VoicePhase
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    vm: ChatViewModel,
    ensureMic: suspend () -> Boolean,
    takePhoto: suspend () -> String?,
    pickImage: suspend () -> String?,
    onOpenProviders: () -> Unit,
    onOpenMenu: () -> Unit,
    onInstallTts: () -> Unit,
    onMicSettings: () -> Unit,
    onChatsDrawer: (Boolean) -> Unit = {},
    chatsCloseRequest: Int = 0,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val lang = state.lang
    val persianVoice = VoiceLang.resolved(state.voiceLang, lang) == VoiceLang.FA

    DisposableEffect(Unit) {
        vm.setVoiceUiActive(true)
        onDispose { vm.setVoiceUiActive(false) }
    }
    LaunchedEffect(state.wantVoice) {
        if (state.wantVoice) {
            vm.consumeWantVoice()
            if (ensureMic()) vm.onMicGranted() else vm.onMicDenied()
        }
    }
    LaunchedEffect(drawer.currentValue) {
        onChatsDrawer(drawer.currentValue == DrawerValue.Open)
    }
    LaunchedEffect(chatsCloseRequest) {
        if (chatsCloseRequest > 0 && drawer.currentValue != DrawerValue.Closed) drawer.close()
    }
    LaunchedEffect(state.messages.size, state.draft, state.running) {
        val last = state.messages.lastIndex + if (state.draft.isNotBlank() || state.running) 1 else 0
        if (last >= 0) runCatching { listState.animateScrollToItem(last.coerceAtLeast(0)) }
    }

    Box(Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawer,
            gesturesEnabled = false,
            drawerContent = {
                ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Text(
                        stringResource(R.string.chat_chats),
                        modifier = Modifier.padding(horizontal = Space.s16, vertical = Space.s16),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TextField(
                        value = state.query,
                        onValueChange = vm::setQuery,
                        modifier = Modifier.padding(horizontal = Space.s16).fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.chat_search)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                    )
                    LazyColumn(Modifier.padding(Space.s8), verticalArrangement = Arrangement.spacedBy(Space.s4)) {
                        items(state.conversations, key = { it.id }) { conv ->
                            NavigationDrawerItem(
                                selected = conv.id == state.activeId,
                                onClick = {
                                    vm.open(conv.id)
                                    scope.launch { drawer.close() }
                                },
                                shape = MaterialTheme.shapes.medium,
                                label = {
                                    Text(
                                        (if (conv.pinned) "★ " else "") + conv.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                            Row(Modifier.padding(horizontal = Space.s8)) {
                                TextButton(onClick = { vm.pin(conv.id, !conv.pinned) }) {
                                    Text(if (conv.pinned) stringResource(R.string.chat_unpin) else stringResource(R.string.chat_pin))
                                }
                                TextButton(onClick = { vm.deleteConversation(conv.id) }) {
                                    Text(stringResource(R.string.chat_delete))
                                }
                            }
                        }
                        if (state.conversations.isEmpty()) {
                            item {
                                EmptyState(
                                    title = stringResource(R.string.chat_empty_title),
                                    body = stringResource(R.string.chat_empty_body),
                                    icon = Icons.Outlined.Forum,
                                )
                            }
                        }
                    }
                }
            },
        ) {
            Column(Modifier.fillMaxSize().imePadding()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().height(ChromeMetrics.topBar).padding(horizontal = Space.s4),
                ) {
                    MenuNavigation(onOpenMenu)
                    IconButton(onClick = { scope.launch { drawer.open() } }) {
                        Icon(Icons.Outlined.Forum, contentDescription = stringResource(R.string.chat_chats))
                    }
                    ModelChip(state, Modifier.weight(1f), vm::openModelPicker)
                    IconButton(onClick = vm::startHandsFree) {
                        Icon(
                            Icons.Outlined.GraphicEq,
                            contentDescription = stringResource(R.string.chat_hands_free),
                            tint = if (state.handsFree) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = vm::newChat) {
                        Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.chat_new))
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                if (state.toolWarning) {
                    Text(
                        stringResource(R.string.chat_tools_warning),
                        modifier = Modifier.padding(horizontal = Space.s16, vertical = Space.s4),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (state.error != null) {
                    ErrorBanner(
                        message = state.error ?: "",
                        action = if (state.suggestProxy) stringResource(R.string.chat_providers) else null,
                        onAction = if (state.suggestProxy) onOpenProviders else null,
                    )
                }
                val empty = state.messages.isEmpty() && state.draft.isBlank() && !state.running
                if (empty) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Welcome(state.providers.isEmpty(), onOpenProviders) { vm.setComposer(it) }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = Space.s16),
                        verticalArrangement = Arrangement.spacedBy(Space.s8),
                    ) {
                        items(state.messages, key = { it.id }) { message ->
                            val newest = message.id == state.messages.lastOrNull()?.id
                            AnimatedVisibility(
                                visible = true,
                                enter = if (newest) fadeIn(tween(160)) + expandVertically(tween(160)) else fadeIn(tween(0)),
                            ) {
                                MessageBubble(message, lang, onSpeak = vm::speakMessage)
                            }
                        }
                        if (state.draft.isNotBlank()) {
                            item { AssistantBubble(state.draft, lang, markdown = false, onSpeak = null) }
                        }
                        if (state.running && state.draft.isBlank()) {
                            item { TypingIndicator() }
                        }
                    }
                }
                if (state.attachments.isNotEmpty()) {
                    Text(
                        stringResource(R.string.chat_attachments, state.attachments.size),
                        modifier = Modifier.padding(horizontal = Space.s16),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    if (state.visionWarning) {
                        Text(
                            stringResource(R.string.vision_warning),
                            modifier = Modifier.padding(horizontal = Space.s16),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                ComposerBar(state, vm, takePhoto, pickImage, scope)
            }
        }
        val showStage = state.handsFree || state.voicePhase != VoicePhase.Idle
        val persianUi = lang.startsWith("fa")
        if (showStage) {
            VoiceStage(
                phase = state.voicePhase,
                liveText = state.voiceLive.ifBlank { state.draft },
                hint = state.voiceHint,
                note = state.providerNote,
                listenError = state.listenError,
                persianVoice = persianVoice,
                persianUi = persianUi,
                pathLabel = ListenPathCopy.line(state.listenPath, state.pathProvider, persianUi),
                onStop = vm::stopVoice,
                onClose = {
                    if (state.voicePhase == VoicePhase.Error) vm.dismissVoiceError() else vm.stopVoice()
                },
                onRetry = vm::retryVoice,
                onHint = {
                    when (state.voiceHint) {
                        VoiceHint.InstallTts -> onInstallTts()
                        VoiceHint.MicSettings -> onMicSettings()
                        VoiceHint.NoRecognizer -> onOpenProviders()
                        else -> Unit
                    }
                },
            )
        }
        val voiceBanner = state.voiceBanner
        if (!showStage && !voiceBanner.isNullOrBlank()) {
            Box(Modifier.align(Alignment.BottomCenter).padding(Space.s16)) {
                ErrorBanner(
                    message = voiceBanner,
                    action = stringResource(R.string.voice_dismiss),
                    onAction = vm::dismissVoiceBanner,
                )
            }
        }
        if (state.pickerOpen) {
            ModelPickerSheet(
                state = state,
                onClose = vm::closeModelPicker,
                onOpenProviders = onOpenProviders,
                onFollowDefault = vm::followDefaultProvider,
                onSelectProvider = vm::selectPickerProvider,
                onQuery = vm::setModelQuery,
                onRefresh = vm::refreshModels,
                onApply = vm::applyListedModel,
                onSaveDefault = vm::saveAsDefault,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Welcome(needsKey: Boolean, onAddKey: () -> Unit, onSuggest: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Space.s24, vertical = Space.s16),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.s12),
    ) {
        WelcomeMark()
        Text(
            stringResource(R.string.chat_welcome_kicker),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            if (needsKey) stringResource(R.string.chat_key_title) else stringResource(R.string.chat_welcome_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            if (needsKey) stringResource(R.string.chat_key_body) else stringResource(R.string.chat_welcome_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (needsKey) {
            PrimaryButton(stringResource(R.string.chat_add_key), onAddKey)
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.s8, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(Space.s8),
            ) {
                val ideas = listOf(
                    stringResource(R.string.chat_suggest_day),
                    stringResource(R.string.chat_suggest_draft),
                    stringResource(R.string.chat_suggest_help),
                )
                ideas.forEach { idea ->
                    SuggestionChip(onClick = { onSuggest(idea) }, label = { Text(idea) })
                }
            }
        }
    }
}

@Composable
private fun WelcomeMark() {
    val teal = MaterialTheme.colorScheme.primary
    val clay = MaterialTheme.colorScheme.secondary
    val mist = MaterialTheme.colorScheme.primaryContainer
    androidx.compose.foundation.Canvas(Modifier.size(112.dp)) {
        drawCircle(mist, radius = size.minDimension * 0.42f, center = androidx.compose.ui.geometry.Offset(size.width * 0.46f, size.height * 0.5f))
        drawCircle(teal, radius = size.minDimension * 0.16f, center = androidx.compose.ui.geometry.Offset(size.width * 0.38f, size.height * 0.48f))
        drawCircle(clay, radius = size.minDimension * 0.09f, center = androidx.compose.ui.geometry.Offset(size.width * 0.66f, size.height * 0.4f))
    }
}

@Composable
private fun ModelChip(state: ChatUi, modifier: Modifier, onClick: () -> Unit) {
    val label = chatModelLabel(state)
    Surface(
        onClick = onClick,
        modifier = modifier.padding(horizontal = Space.s4).height(32.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            Modifier.padding(horizontal = Space.s8),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s4),
        ) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun ComposerBar(
    state: ChatUi,
    vm: ChatViewModel,
    takePhoto: suspend () -> String?,
    pickImage: suspend () -> String?,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.s12, vertical = Space.s8),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = Space.s4)) {
            IconButton(onClick = {
                scope.launch {
                    val path = takePhoto()
                    if (path != null) vm.attach(path)
                }
            }) {
                Icon(Icons.Outlined.PhotoCamera, contentDescription = stringResource(R.string.chat_photo))
            }
            IconButton(onClick = {
                scope.launch {
                    val path = pickImage()
                    if (path != null) vm.attach(path)
                }
            }) {
                Icon(Icons.Outlined.Image, contentDescription = stringResource(R.string.attach_image))
            }
            IconButton(onClick = {
                if (state.listening && !state.handsFree) vm.stopVoice() else vm.startOneShot()
            }) {
                Icon(
                    if (state.listening && !state.handsFree) Icons.Outlined.Close else Icons.Outlined.Mic,
                    contentDescription = stringResource(R.string.chat_voice),
                    tint = if (state.listening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            TextField(
                value = state.composer,
                onValueChange = vm::setComposer,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        when {
                            state.listening -> stringResource(R.string.chat_listening)
                            state.voiceReview -> stringResource(R.string.chat_review)
                            else -> stringResource(R.string.chat_message)
                        },
                    )
                },
                maxLines = 4,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
            )
            if (state.running) {
                FilledIconButton(onClick = vm::cancelRun) {
                    Icon(Icons.Outlined.Stop, contentDescription = stringResource(R.string.chat_stop))
                }
            } else {
                FilledIconButton(onClick = vm::send, enabled = state.composer.isNotBlank() || state.attachments.isNotEmpty()) {
                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = stringResource(R.string.chat_send))
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: MessageEntity, lang: String, onSpeak: (String) -> Unit) {
    val user = message.role == "user"
    val toolLabel = toolLabels(message)
    Column(verticalArrangement = Arrangement.spacedBy(Space.s4)) {
        if (!message.imagePathsJson.isNullOrBlank() && message.imagePathsJson != "[]" && message.imagePathsJson != "null") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
                StatusChip(stringResource(R.string.chat_photo_attached), StatusTone.Neutral)
            }
        }
        if (toolLabel.isNotBlank()) {
            ToolCallCard(toolLabel, toolStatus(message), if (message.role == "tool") message.text else "", message.isError)
        }
        if (message.text.isNotBlank() && message.role != "tool") {
            if (user) {
                UserBubble(message.text, lang)
            } else {
                AssistantBubble(message.text, lang, markdown = true, error = message.isError, onSpeak = { onSpeak(message.text) })
            }
        }
    }
}

@Composable
private fun ToolCallCard(name: String, status: String, detail: String, error: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(Space.s12), verticalArrangement = Arrangement.spacedBy(Space.s4)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s8)) {
                Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                StatusChip(status, if (error) StatusTone.Danger else StatusTone.Positive)
            }
            if (detail.isNotBlank()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun UserBubble(text: String, lang: String) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.4f
    val brush = if (dark) {
        Brush.linearGradient(listOf(Color(0xFF1A6B62), Color(0xFF0E4540)))
    } else {
        Brush.linearGradient(listOf(Color(0xFF0F6E64), Color(0xFF178578)))
    }
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 8.dp, bottomStart = 20.dp)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Box(
            Modifier
                .fillMaxWidth(0.86f)
                .clip(shape)
                .background(brush)
                .padding(horizontal = Space.s16, vertical = Space.s12),
        ) {
            MarkdownMessage(text, Color.White, lang)
        }
    }
}

@Composable
private fun AssistantBubble(text: String, lang: String, markdown: Boolean, error: Boolean = false, onSpeak: (() -> Unit)?) {
    val background = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLowest
    val foreground = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 8.dp)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f),
            color = background,
            contentColor = foreground,
            shape = shape,
            shadowElevation = 1.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column {
                if (markdown) {
                    MarkdownMessage(text, foreground, lang, Modifier.padding(horizontal = Space.s16, vertical = Space.s12))
                } else {
                    Text(text, modifier = Modifier.padding(horizontal = Space.s16, vertical = Space.s12), style = MaterialTheme.typography.bodyLarge, color = foreground)
                }
                if (onSpeak != null && !error) {
                    IconButton(onClick = onSpeak, modifier = Modifier.align(Alignment.End).size(40.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = stringResource(R.string.chat_speak), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        Modifier.padding(Space.s8),
        horizontalArrangement = Arrangement.spacedBy(Space.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val offset by transition.animateFloat(
                initialValue = 0f,
                targetValue = -4f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 420, delayMillis = index * 120),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(
                Modifier
                    .offset(y = offset.dp)
                    .size(7.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}

private fun toolLabels(message: MessageEntity): String {
    message.toolName?.takeIf { it.isNotBlank() }?.let { return it }
    val json = message.toolCallsJson ?: return ""
    return Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").findAll(json).map { it.groupValues[1] }.distinct().joinToString(", ")
}

@Composable
private fun toolStatus(message: MessageEntity): String = when {
    message.isError -> stringResource(R.string.chat_tool_error)
    message.role == "tool" -> stringResource(R.string.chat_tool_done)
    else -> stringResource(R.string.chat_tool_called)
}
