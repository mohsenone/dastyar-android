package app.dastyar.agent.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dastyar.agent.data.MessageEntity
import app.dastyar.agent.ui.ErrorBanner
import app.dastyar.agent.ui.t
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    vm: ChatViewModel,
    ensureMic: suspend () -> Boolean,
    takePhoto: suspend () -> String?,
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
    LaunchedEffect(state.messages.size, state.draft) {
        val last = state.messages.lastIndex + if (state.draft.isNotBlank()) 1 else 0
        if (last >= 0) listState.animateScrollToItem(last)
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    t(lang, "گفتگوها", "Chats"),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::setQuery,
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    label = { Text(t(lang, "جستجو", "Search")) },
                    singleLine = true,
                )
                LazyColumn(Modifier.padding(vertical = 8.dp)) {
                    items(state.conversations, key = { it.id }) { conv ->
                        TextButton(
                            onClick = {
                                vm.open(conv.id)
                                scope.launch { drawer.close() }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    (if (conv.pinned) "★ " else "") + conv.title,
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        Row(Modifier.padding(horizontal = 8.dp)) {
                            TextButton(onClick = { vm.pin(conv.id, !conv.pinned) }) {
                                Text(if (conv.pinned) t(lang, "برداشتن سنجاق", "Unpin") else t(lang, "سنجاق", "Pin"))
                            }
                            TextButton(onClick = { vm.deleteConversation(conv.id) }) {
                                Text(t(lang, "حذف", "Delete"))
                            }
                        }
                    }
                }
            }
        },
    ) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                IconButton(onClick = { scope.launch { drawer.open() } }) {
                    Icon(Icons.Outlined.Menu, contentDescription = t(lang, "گفتگوها", "Chats"))
                }
                Text(t(lang, "گفتگو", "Chat"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = vm::newChat) {
                    Icon(Icons.Outlined.Add, contentDescription = t(lang, "گفتگوی تازه", "New chat"))
                }
                IconButton(onClick = vm::speakLast) {
                    Icon(Icons.Outlined.VolumeUp, contentDescription = t(lang, "خواندن پاسخ", "Speak reply"))
                }
                IconButton(onClick = vm::stopSpeaking) {
                    Icon(Icons.Outlined.Close, contentDescription = t(lang, "توقف صدا", "Stop speech"))
                }
            }
            if (state.error != null) {
                ErrorBanner(
                    message = state.error ?: "",
                    action = if (state.suggestProxy) t(lang, "ارائه‌دهندگان", "Providers") else null,
                    onAction = if (state.suggestProxy) onOpenProviders else null,
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.messages, key = { it.id }) { message ->
                    MessageBubble(message, lang)
                }
                if (state.draft.isNotBlank()) {
                    item {
                        Bubble(state.draft, assistant = true)
                    }
                }
                if (state.messages.isEmpty() && state.draft.isBlank()) {
                    item {
                        Text(
                            t(
                                lang,
                                "کلید خودتان را در ارائه‌دهندگان بگذارید، بعد بپرسید. عمل‌های خطرناک قبل از اجرا تأیید می‌خواهند.",
                                "Add your own key under Providers, then ask. Dangerous actions ask before they run.",
                            ),
                            modifier = Modifier.padding(24.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            if (state.attachments.isNotEmpty()) {
                Text(
                    t(lang, "پیوست: ${state.attachments.size}", "Attachments: ${state.attachments.size}"),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                        if (ensureMic()) vm.onMicGranted()
                    }
                }) {
                    Icon(Icons.Outlined.Mic, contentDescription = t(lang, "صدا", "Voice"))
                }
                OutlinedTextField(
                    value = state.composer,
                    onValueChange = vm::setComposer,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(if (state.listening) t(lang, "در حال شنیدن…", "Listening…") else t(lang, "پیام", "Message")) },
                    maxLines = 4,
                )
                Spacer(Modifier.width(4.dp))
                if (state.running) {
                    TextButton(onClick = vm::cancelRun) { Text(t(lang, "توقف", "Stop")) }
                } else {
                    IconButton(onClick = vm::send) {
                        Icon(Icons.Outlined.Send, contentDescription = t(lang, "ارسال", "Send"))
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: MessageEntity, lang: String) {
    val assistant = message.role == "assistant" || message.role == "tool" || message.isError
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!message.toolName.isNullOrBlank() || !message.toolCallsJson.isNullOrBlank()) {
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        message.toolName ?: t(lang, "ابزار", "Tool"),
                    )
                },
            )
        }
        if (message.text.isNotBlank()) {
            Bubble(message.text, assistant = assistant, error = message.isError)
        }
    }
}

@Composable
private fun Bubble(text: String, assistant: Boolean, error: Boolean = false) {
    val align = if (assistant) Alignment.Start else Alignment.End
    val color = when {
        error -> MaterialTheme.colorScheme.errorContainer
        assistant -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = align) {
        Surface(color = color, shape = MaterialTheme.shapes.medium) {
            Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
