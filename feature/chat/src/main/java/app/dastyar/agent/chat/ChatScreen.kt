package app.dastyar.agent.chat

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dastyar.agent.data.MessageEntity
import app.dastyar.agent.ui.EmptyState
import app.dastyar.agent.ui.ErrorBanner
import app.dastyar.agent.ui.Space
import app.dastyar.agent.ui.StatusChip
import app.dastyar.agent.ui.StatusTone
import app.dastyar.agent.ui.t
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    vm: ChatViewModel,
    ensureMic: suspend () -> Boolean,
    takePhoto: suspend () -> String?,
    pickImage: suspend () -> String?,
    onOpenProviders: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val lang = state.lang

    LaunchedEffect(state.wantVoice) {
        if (state.wantVoice) {
            vm.consumeWantVoice()
            if (ensureMic()) vm.onMicGranted()
        }
    }
    LaunchedEffect(state.messages.size, state.draft, state.running) {
        val last = state.messages.lastIndex + if (state.draft.isNotBlank() || state.running) 1 else 0
        if (last >= 0) listState.animateScrollToItem(last.coerceAtLeast(0))
    }

    Box(Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawer,
            drawerContent = {
                ModalDrawerSheet {
                    Text(
                        t(lang, "گفتگوها", "Chats"),
                        modifier = Modifier.padding(Space.s16),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = vm::setQuery,
                        modifier = Modifier.padding(horizontal = Space.s16).fillMaxWidth(),
                        label = { Text(t(lang, "جستجو", "Search")) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraLarge,
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
                                    Text(if (conv.pinned) t(lang, "برداشتن سنجاق", "Unpin") else t(lang, "سنجاق", "Pin"))
                                }
                                TextButton(onClick = { vm.deleteConversation(conv.id) }) {
                                    Text(t(lang, "حذف", "Delete"))
                                }
                            }
                        }
                        if (state.conversations.isEmpty()) {
                            item {
                                EmptyState(
                                    title = t(lang, "هنوز گفتگویی نیست", "No chats yet"),
                                    body = t(lang, "اولین پیام یک گفتگوی تازه می‌سازد.", "Your first message starts a chat."),
                                    icon = Icons.Outlined.Forum,
                                )
                            }
                        }
                    }
                }
            },
        ) {
            Column(Modifier.fillMaxSize().imePadding()) {
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.s4, vertical = Space.s4),
                    ) {
                        IconButton(onClick = { scope.launch { drawer.open() } }) {
                            Icon(Icons.Outlined.Menu, contentDescription = t(lang, "گفتگوها", "Chats"))
                        }
                        ModelChip(state, Modifier.weight(1f), vm::openModelPicker)
                        IconButton(onClick = vm::newChat) {
                            Icon(Icons.Outlined.Add, contentDescription = t(lang, "گفتگوی تازه", "New chat"))
                        }
                        IconButton(onClick = vm::speakLast) {
                            Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = t(lang, "خواندن پاسخ", "Speak reply"))
                        }
                        IconButton(onClick = vm::stopSpeaking) {
                            Icon(Icons.AutoMirrored.Outlined.VolumeOff, contentDescription = t(lang, "توقف صدا", "Stop speech"))
                        }
                    }
                }
                if (state.toolWarning) {
                    Text(
                        t(lang, "ابزار ممکن است نباشد یا قابل‌اتکا نباشد.", "Tools may be unavailable or unreliable."),
                        modifier = Modifier.padding(horizontal = Space.s16, vertical = Space.s4),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (state.error != null) {
                    ErrorBanner(
                        message = state.error ?: "",
                        action = if (state.suggestProxy) t(lang, "ارائه‌دهندگان", "Providers") else null,
                        onAction = if (state.suggestProxy) onOpenProviders else null,
                    )
                }
                val empty = state.messages.isEmpty() && state.draft.isBlank() && !state.running
                if (empty) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (state.providers.isEmpty()) {
                            EmptyState(
                                icon = Icons.Outlined.Key,
                                title = t(lang, "اول یک کلید بگذارید", "Add a key to begin"),
                                body = t(
                                    lang,
                                    "دستیار با کلید خودتان حرف می‌زند. هزینه را این برنامه حساب نمی‌کند.",
                                    "Dastyar talks through a key you bring. This app does not bill usage.",
                                ),
                                action = t(lang, "افزودن کلید", "Add a key"),
                                onAction = onOpenProviders,
                            )
                        } else {
                            EmptyState(
                                icon = Icons.Outlined.Forum,
                                title = t(lang, "یک گفتگو شروع کنید", "Start a conversation"),
                                body = t(
                                    lang,
                                    "به فارسی یا انگلیسی بپرسید. کار حساس قبل از اجرا تأیید می‌خواهد.",
                                    "Ask in English or Persian. Sensitive actions wait until you confirm.",
                                ),
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = Space.s12),
                        verticalArrangement = Arrangement.spacedBy(Space.s8),
                    ) {
                        items(state.messages, key = { it.id }) { message ->
                            MessageBubble(message, lang)
                        }
                        if (state.draft.isNotBlank()) {
                            item { AssistantBubble(state.draft, lang, markdown = false) }
                        }
                        if (state.running && state.draft.isBlank()) {
                            item { TypingIndicator() }
                        }
                    }
                }
                if (state.attachments.isNotEmpty()) {
                    Text(
                        t(lang, "پیوست: ${state.attachments.size}", "Attachments: ${state.attachments.size}"),
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
                ComposerBar(state, lang, vm, ensureMic, takePhoto, pickImage, scope)
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

@Composable
private fun ModelChip(state: ChatUi, modifier: Modifier, onClick: () -> Unit) {
    val label = chatModelLabel(state)
    val parts = label.split(" · ", limit = 2)
    Surface(
        onClick = onClick,
        modifier = modifier.padding(vertical = Space.s4),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            Modifier.padding(horizontal = Space.s12, vertical = Space.s8),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s4),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    parts.first(),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                if (parts.size > 1) {
                    Text(
                        parts[1],
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun ComposerBar(
    state: ChatUi,
    lang: String,
    vm: ChatViewModel,
    ensureMic: suspend () -> Boolean,
    takePhoto: suspend () -> String?,
    pickImage: suspend () -> String?,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.s12, vertical = Space.s8),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Space.s4)) {
            IconButton(onClick = {
                scope.launch {
                    val path = takePhoto()
                    if (path != null) vm.attach(path)
                }
            }) {
                Icon(Icons.Outlined.PhotoCamera, contentDescription = t(lang, "عکس", "Photo"))
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
                scope.launch {
                    if (ensureMic()) vm.onMicGranted()
                }
            }) {
                Icon(Icons.Outlined.Mic, contentDescription = t(lang, "صدا", "Voice"))
            }
            OutlinedTextField(
                value = state.composer,
                onValueChange = vm::setComposer,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(if (state.listening) t(lang, "در حال شنیدن…", "Listening…") else t(lang, "پیام", "Message"))
                },
                maxLines = 4,
                shape = MaterialTheme.shapes.extraLarge,
            )
            if (state.running) {
                FilledIconButton(onClick = vm::cancelRun) {
                    Icon(Icons.Outlined.Stop, contentDescription = t(lang, "توقف", "Stop"))
                }
            } else {
                FilledIconButton(onClick = vm::send, enabled = state.composer.isNotBlank() || state.attachments.isNotEmpty()) {
                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = t(lang, "ارسال", "Send"))
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: MessageEntity, lang: String) {
    val user = message.role == "user"
    val toolLabel = toolLabels(message)
    Column(verticalArrangement = Arrangement.spacedBy(Space.s4)) {
        if (!message.imagePathsJson.isNullOrBlank() && message.imagePathsJson != "[]" && message.imagePathsJson != "null") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
                StatusChip(t(lang, "عکس پیوست", "Photo attached"), StatusTone.Neutral)
            }
        }
        if (toolLabel.isNotBlank()) {
            ToolCallCard(toolLabel, toolStatus(message, lang), if (message.role == "tool") message.text else "", message.isError)
        }
        if (message.text.isNotBlank() && message.role != "tool") {
            if (user) UserBubble(message.text, lang) else AssistantBubble(message.text, lang, markdown = true, error = message.isError)
        }
    }
}

@Composable
private fun ToolCallCard(name: String, status: String, detail: String, error: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
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
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.88f),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = MaterialTheme.shapes.large,
            tonalElevation = 1.dp,
        ) {
            MarkdownMessage(
                text,
                MaterialTheme.colorScheme.onPrimaryContainer,
                lang,
                Modifier.padding(Space.s12),
            )
        }
    }
}

@Composable
private fun AssistantBubble(text: String, lang: String, markdown: Boolean, error: Boolean = false) {
    val background = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val foreground = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f),
            color = background,
            contentColor = foreground,
            shape = MaterialTheme.shapes.large,
        ) {
            if (markdown) {
                MarkdownMessage(text, foreground, lang, Modifier.padding(Space.s12))
            } else {
                Text(text, modifier = Modifier.padding(Space.s12), style = MaterialTheme.typography.bodyLarge, color = foreground)
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
                targetValue = -5f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 420, delayMillis = index * 120),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(
                Modifier
                    .offset(y = offset.dp)
                    .size(8.dp)
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

private fun toolStatus(message: MessageEntity, lang: String): String = when {
    message.isError -> t(lang, "خطا", "Error")
    message.role == "tool" -> t(lang, "انجام شد", "Done")
    else -> t(lang, "فراخوانی", "Called")
}
