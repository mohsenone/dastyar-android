package app.dastyar.agent.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.dastyar.agent.data.ChatRepository
import app.dastyar.agent.data.ConversationEntity
import app.dastyar.agent.data.MessageEntity
import app.dastyar.agent.data.SettingsStore
import app.dastyar.agent.loop.AgentEvent
import app.dastyar.agent.loop.AgentLoop
import app.dastyar.agent.loop.AgentRequest
import app.dastyar.agent.voice.VoiceController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUi(
    val lang: String = "fa",
    val conversations: List<ConversationEntity> = emptyList(),
    val messages: List<MessageEntity> = emptyList(),
    val activeId: String? = null,
    val draft: String = "",
    val running: Boolean = false,
    val error: String? = null,
    val suggestProxy: Boolean = false,
    val composer: String = "",
    val listening: Boolean = false,
    val attachments: List<String> = emptyList(),
    val query: String = "",
    val wantVoice: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chats: ChatRepository,
    private val settings: SettingsStore,
    private val loop: AgentLoop,
    private val voice: VoiceController,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val _state = MutableStateFlow(ChatUi())
    val state = _state.asStateFlow()
    private var messageJob: Job? = null

    init {
        viewModelScope.launch {
            settings.data.collect { snap ->
                _state.update { it.copy(lang = snap.language) }
                val savedId = snap.activeConversationId
                if (_state.value.activeId == null && !savedId.isNullOrBlank()) {
                    open(savedId)
                }
            }
        }
        viewModelScope.launch {
            query.flatMapLatest { chats.search(it) }.collect { rows ->
                _state.update { it.copy(conversations = rows) }
            }
        }
    }

    fun setQuery(value: String) {
        query.value = value
        _state.update { it.copy(query = value) }
    }

    fun open(id: String) {
        messageJob?.cancel()
        _state.update { it.copy(activeId = id, error = null, draft = "") }
        viewModelScope.launch { settings.setActiveConversation(id) }
        messageJob = viewModelScope.launch {
            chats.observeMessages(id).collect { rows ->
                _state.update { it.copy(messages = rows) }
            }
        }
    }

    fun newChat() {
        viewModelScope.launch {
            val id = chats.createConversation("…")
            open(id)
        }
    }

    fun setComposer(value: String) = _state.update { it.copy(composer = value) }

    fun attach(path: String) = _state.update { it.copy(attachments = it.attachments + path) }

    fun clearError() = _state.update { it.copy(error = null, suggestProxy = false) }

    fun acceptShare(text: String, imagePath: String?) {
        viewModelScope.launch {
            val id = chats.createConversation(text.trim().take(48).ifBlank { "…" })
            open(id)
            _state.update {
                it.copy(
                    composer = text.trim(),
                    attachments = listOfNotNull(imagePath),
                )
            }
        }
    }

    fun send() {
        val snapshot = _state.value
        val text = snapshot.composer.trim()
        val images = snapshot.attachments
        if ((text.isBlank() && images.isEmpty()) || snapshot.running) return
        voice.stopListening()
        viewModelScope.launch {
            val id = snapshot.activeId ?: chats.createConversation(text.ifBlank { "…" }).also { open(it) }
            _state.update { it.copy(composer = "", attachments = emptyList(), running = true, error = null, draft = "", listening = false) }
            try {
                loop.run(AgentRequest(id, text.ifBlank { "(image)" }, images)) { event ->
                    if (event.conversationId != id) return@run
                    when (event) {
                        is AgentEvent.Token -> _state.update { it.copy(draft = it.draft + event.text) }
                        is AgentEvent.DraftCleared -> _state.update { it.copy(draft = "") }
                        is AgentEvent.Failed -> _state.update {
                            it.copy(error = event.message, suggestProxy = event.suggestProxy, running = false, draft = "")
                        }
                        is AgentEvent.Completed -> _state.update { it.copy(running = false, draft = "") }
                        is AgentEvent.ToolStarted, is AgentEvent.ToolFinished -> Unit
                    }
                }
            } finally {
                _state.update { it.copy(running = false) }
            }
        }
    }

    fun cancelRun() {
        loop.cancel()
        voice.stopSpeaking()
        voice.stopListening()
        _state.update { it.copy(running = false, listening = false) }
    }

    fun requestVoice() = _state.update { it.copy(wantVoice = true) }

    fun consumeWantVoice() = _state.update { it.copy(wantVoice = false) }

    fun onMicGranted() {
        val lang = _state.value.lang
        if (_state.value.listening) {
            voice.stopListening()
            _state.update { it.copy(listening = false) }
            return
        }
        _state.update { it.copy(listening = true) }
        voice.listen(
            language = lang,
            onPartial = { partial -> _state.update { it.copy(composer = partial) } },
            onResult = { text -> _state.update { it.copy(listening = false, composer = text) } },
            onError = { err -> _state.update { it.copy(listening = false, error = err, suggestProxy = false) } },
        )
    }

    fun speakLast() {
        val text = _state.value.messages.lastOrNull { it.role == "assistant" && it.text.isNotBlank() }?.text ?: return
        voice.speak(text, _state.value.lang)
    }

    fun stopSpeaking() = voice.stopSpeaking()

    fun rename(id: String, title: String) {
        viewModelScope.launch { chats.rename(id, title) }
    }

    fun pin(id: String, pinned: Boolean) {
        viewModelScope.launch { chats.pin(id, pinned) }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            chats.deleteConversation(id)
            if (_state.value.activeId == id) {
                _state.update { it.copy(activeId = null, messages = emptyList()) }
                settings.setActiveConversation(null)
            }
        }
    }

    override fun onCleared() {
        voice.shutdown()
        super.onCleared()
    }
}
