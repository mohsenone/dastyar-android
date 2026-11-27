package app.dastyar.agent.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.dastyar.agent.data.AppLanguage
import app.dastyar.agent.data.ChatRepository
import app.dastyar.agent.data.ConversationEntity
import app.dastyar.agent.data.LocaleGate
import app.dastyar.agent.data.MessageEntity
import app.dastyar.agent.data.ModelCacheEntity
import app.dastyar.agent.data.ModelCacheRepository
import app.dastyar.agent.data.ProviderEntity
import app.dastyar.agent.data.ProviderProfile
import app.dastyar.agent.data.ProviderRepository
import app.dastyar.agent.data.SettingsStore
import app.dastyar.agent.data.deviceLanguageTag
import app.dastyar.agent.loop.AgentEvent
import app.dastyar.agent.loop.AgentLoop
import app.dastyar.agent.loop.AgentRequest
import app.dastyar.agent.network.AudioHope
import app.dastyar.agent.network.BuiltinModels
import app.dastyar.agent.network.CatalogModel
import app.dastyar.agent.network.ModelCatalog
import app.dastyar.agent.network.ModelFetchRequest
import app.dastyar.agent.network.ModelFetchStatus
import app.dastyar.agent.network.ModelOrder
import app.dastyar.agent.network.ModelSource
import app.dastyar.agent.network.ProviderAudioClient
import app.dastyar.agent.network.ProviderAudioPolicy
import app.dastyar.agent.network.ProviderKinds
import app.dastyar.agent.ui.t
import app.dastyar.agent.voice.ListenError
import app.dastyar.agent.voice.ProviderVoiceNote
import app.dastyar.agent.voice.SpeakOutcome
import app.dastyar.agent.voice.Speakable
import app.dastyar.agent.voice.VoiceController
import app.dastyar.agent.voice.VoiceGate
import app.dastyar.agent.voice.VoiceGateDecision
import app.dastyar.agent.voice.VoiceHint
import app.dastyar.agent.voice.VoiceLang
import app.dastyar.agent.voice.VoicePhase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatUi(
    val lang: String = LocaleGate.uiLang,
    val conversations: List<ConversationEntity> = emptyList(),
    val messages: List<MessageEntity> = emptyList(),
    val activeId: String? = null,
    val draft: String = "",
    val running: Boolean = false,
    val error: String? = null,
    val suggestProxy: Boolean = false,
    val composer: String = "",
    val listening: Boolean = false,
    val voiceLang: String = VoiceLang.AUTO,
    val voicePhase: VoicePhase = VoicePhase.Idle,
    val handsFree: Boolean = false,
    val voiceLive: String = "",
    val voiceHint: VoiceHint = VoiceHint.None,
    val voiceReview: Boolean = false,
    val providerNote: ProviderVoiceNote = ProviderVoiceNote.None,
    val listenError: ListenError? = null,
    val attachments: List<String> = emptyList(),
    val query: String = "",
    val wantVoice: Boolean = false,
    val providers: List<ProviderEntity> = emptyList(),
    val pickerOpen: Boolean = false,
    val pickerProviderId: String? = null,
    val pickerFollowDefault: Boolean = true,
    val pickerQuery: String = "",
    val pickerModels: List<CatalogModel> = emptyList(),
    val pickerBusy: Boolean = false,
    val pickerStatus: String = "",
    val pickerError: String? = null,
    val toolWarning: Boolean = false,
    val visionWarning: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chats: ChatRepository,
    private val providers: ProviderRepository,
    private val settings: SettingsStore,
    private val loop: AgentLoop,
    private val voice: VoiceController,
    private val audio: ProviderAudioClient,
    private val catalog: ModelCatalog,
    private val modelCache: ModelCacheRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val _state = MutableStateFlow(ChatUi())
    val state = _state.asStateFlow()
    private var messageJob: Job? = null
    private var loadGen: Int = 0
    private var voiceUiActive: Boolean = false
    private var audioProbeFailed: Boolean = false

    init {
        viewModelScope.launch {
            settings.data.collect { snap ->
                _state.update {
                    it.copy(
                        lang = AppLanguage.uiLang(snap.language, deviceLanguageTag()),
                        voiceLang = VoiceLang.normalize(snap.voiceLanguage),
                    )
                }
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
        viewModelScope.launch {
            providers.observe().collect { rows ->
                _state.update { it.copy(providers = rows) }
                refreshToolWarning()
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
        refreshToolWarning()
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

    fun send() = sendInternal(fromVoice = _state.value.voiceReview || _state.value.handsFree)

    fun acceptVoiceTurn(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) {
            startHandsFree()
            return
        }
        _state.update { it.copy(handsFree = true, composer = clean, voicePhase = VoicePhase.Processing, wantVoice = false) }
        sendInternal(fromVoice = true)
    }

    private fun sendInternal(fromVoice: Boolean) {
        val snapshot = _state.value
        val text = snapshot.composer.trim()
        val images = snapshot.attachments
        if ((text.isBlank() && images.isEmpty()) || snapshot.running) return
        voice.stopListening()
        voice.stopRecording()
        val speakAfter = fromVoice || snapshot.handsFree
        viewModelScope.launch {
            val id = snapshot.activeId ?: chats.createConversation(text.ifBlank { "…" }).also { open(it) }
            _state.update {
                it.copy(
                    composer = "",
                    attachments = emptyList(),
                    running = true,
                    error = null,
                    draft = "",
                    listening = false,
                    voiceReview = false,
                    voiceLive = "",
                    listenError = null,
                    voicePhase = if (speakAfter) VoicePhase.Processing else VoicePhase.Idle,
                    providerNote = ProviderVoiceNote.None,
                )
            }
            try {
                loop.run(AgentRequest(id, text.ifBlank { "(image)" }, images)) { event ->
                    if (event.conversationId != id) return@run
                    when (event) {
                        is AgentEvent.Token -> _state.update { it.copy(draft = it.draft + event.text) }
                        is AgentEvent.DraftCleared -> _state.update { it.copy(draft = "") }
                        is AgentEvent.Failed -> _state.update {
                            it.copy(
                                error = event.message,
                                suggestProxy = event.suggestProxy,
                                running = false,
                                draft = "",
                                voicePhase = if (speakAfter) VoicePhase.Error else it.voicePhase,
                                handsFree = if (speakAfter) false else it.handsFree,
                            )
                        }
                        is AgentEvent.Completed -> {
                            _state.update { it.copy(running = false, draft = "") }
                            if (speakAfter) viewModelScope.launch { speakVisibleReply() }
                        }
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
        stopVoice()
        _state.update { it.copy(running = false) }
    }

    fun requestVoice() = startHandsFree()

    fun startOneShot() = _state.update { it.copy(wantVoice = true, handsFree = false, voiceHint = VoiceHint.None) }

    fun startHandsFree() = _state.update { it.copy(wantVoice = true, handsFree = true, voiceHint = VoiceHint.None) }

    fun consumeWantVoice() = _state.update { it.copy(wantVoice = false) }

    fun setVoiceUiActive(active: Boolean) {
        voiceUiActive = active
        if (!active) stopVoice()
    }

    fun onMicDenied() {
        voice.stopListening()
        _state.update {
            it.copy(
                listening = false,
                handsFree = false,
                voicePhase = VoicePhase.Error,
                voiceHint = VoiceHint.MicSettings,
                listenError = ListenError.Permission,
            )
        }
    }

    fun onMicGranted() {
        if (_state.value.listening) {
            stopVoice()
            return
        }
        _state.update { it.copy(voiceHint = VoiceHint.None, listenError = null, providerNote = ProviderVoiceNote.None) }
        val gate = VoiceGate.decision(
            userStarted = true,
            granted = true,
            recognizerInstalled = voice.recognitionAvailable(),
        )
        if (gate == VoiceGateDecision.NoRecognizer) {
            viewModelScope.launch { transcribeWithProviderOrHint() }
        } else {
            listenNow()
        }
    }

    fun stopVoice() {
        voice.stopListening()
        voice.stopRecording()
        voice.stopSpeaking()
        _state.update {
            it.copy(
                handsFree = false,
                listening = false,
                voicePhase = VoicePhase.Idle,
                voiceReview = false,
                voiceLive = "",
                wantVoice = false,
            )
        }
    }

    fun speakMessage(text: String) {
        viewModelScope.launch {
            _state.update { it.copy(handsFree = false, voicePhase = VoicePhase.Speaking) }
            val outcome = deliverSpeech(text, continueLoop = false)
            if (outcome != SpeakOutcome.Spoken && _state.value.providerNote != ProviderVoiceNote.Spoken) {
                _state.update { it.copy(voicePhase = VoicePhase.Error) }
            } else if (_state.value.voicePhase == VoicePhase.Speaking) {
                _state.update { it.copy(voicePhase = VoicePhase.Idle) }
            }
        }
    }

    private fun listenNow() {
        if (!voice.recognitionAvailable()) {
            viewModelScope.launch { transcribeWithProviderOrHint() }
            return
        }
        val snap = _state.value
        _state.update {
            it.copy(
                listening = true,
                voicePhase = VoicePhase.Listening,
                listenError = null,
                voiceHint = VoiceHint.None,
            )
        }
        voice.listen(
            choice = snap.voiceLang,
            appLang = snap.lang,
            onPartial = { partial ->
                _state.update { state ->
                    if (state.handsFree) state.copy(voiceLive = partial) else state.copy(composer = partial, voiceLive = partial)
                }
            },
            onResult = { text ->
                _state.update { it.copy(listening = false, voiceLive = text) }
                if (_state.value.handsFree) {
                    _state.update { it.copy(composer = text, voicePhase = VoicePhase.Processing) }
                    sendInternal(fromVoice = true)
                } else {
                    _state.update {
                        it.copy(
                            composer = text,
                            voicePhase = VoicePhase.Idle,
                            voiceReview = true,
                            voiceLive = "",
                        )
                    }
                }
            },
            onError = { err ->
                _state.update {
                    it.copy(
                        listening = false,
                        voicePhase = VoicePhase.Error,
                        listenError = err,
                        handsFree = false,
                        voiceHint = if (err == ListenError.Permission) VoiceHint.MicSettings else VoiceHint.None,
                    )
                }
            },
        )
    }

    private suspend fun transcribeWithProviderOrHint() {
        val profile = activeProfile()
        val route = profile?.let { ProviderAudioPolicy.route(it.kind, it.baseUrl) } ?: AudioHope.KnownNo
        if (profile == null || profile.apiKey.isBlank() || !ProviderAudioPolicy.shouldTry(true, route, audioProbeFailed)) {
            _state.update {
                it.copy(
                    voicePhase = VoicePhase.Error,
                    voiceHint = VoiceHint.NoRecognizer,
                    listenError = ListenError.NoRecognizer,
                    handsFree = false,
                    providerNote = ProviderVoiceNote.None,
                )
            }
            return
        }
        _state.update { it.copy(voicePhase = VoicePhase.Listening, providerNote = ProviderVoiceNote.Transcribed, listening = true) }
        val wav = withContext(Dispatchers.IO) { voice.recordWav() }
        if (!_state.value.handsFree && _state.value.voicePhase != VoicePhase.Listening) return
        if (wav == null) {
            if (route == AudioHope.Probe) audioProbeFailed = true
            _state.update {
                it.copy(
                    listening = false,
                    voicePhase = VoicePhase.Error,
                    voiceHint = VoiceHint.NoRecognizer,
                    listenError = ListenError.NoRecognizer,
                    handsFree = false,
                    providerNote = ProviderVoiceNote.None,
                )
            }
            return
        }
        val lang = VoiceLang.resolved(_state.value.voiceLang, _state.value.lang)
        val text = withContext(Dispatchers.IO) {
            audio.transcribe(profile.baseUrl, profile.apiKey, wav, lang, profile.extraHeaders)
        }
        if (text.isNullOrBlank()) {
            if (route == AudioHope.Probe) audioProbeFailed = true
            _state.update {
                it.copy(
                    listening = false,
                    voicePhase = VoicePhase.Error,
                    voiceHint = VoiceHint.NoRecognizer,
                    listenError = ListenError.NoRecognizer,
                    handsFree = false,
                    providerNote = ProviderVoiceNote.None,
                )
            }
            return
        }
        _state.update { it.copy(listening = false, voiceLive = text, providerNote = ProviderVoiceNote.Transcribed) }
        if (_state.value.handsFree) {
            _state.update { it.copy(composer = text, voicePhase = VoicePhase.Processing) }
            sendInternal(fromVoice = true)
        } else {
            _state.update { it.copy(composer = text, voicePhase = VoicePhase.Idle, voiceReview = true, voiceLive = "") }
        }
    }

    private suspend fun speakVisibleReply() {
        val id = _state.value.activeId ?: return
        val text = Speakable.reply(
            chats.messages(id).lastOrNull { it.role == "assistant" && !it.isError && it.text.isNotBlank() }?.text.orEmpty(),
        )
        if (text.isBlank()) {
            if (_state.value.handsFree && voiceUiActive) listenNow() else _state.update { it.copy(voicePhase = VoicePhase.Idle) }
            return
        }
        deliverSpeech(text, continueLoop = true)
    }

    private suspend fun deliverSpeech(text: String, continueLoop: Boolean): SpeakOutcome {
        val clean = Speakable.reply(text)
        if (clean.isBlank()) return SpeakOutcome.Empty
        _state.update { it.copy(voicePhase = VoicePhase.Speaking, voiceHint = VoiceHint.None) }
        val lang = VoiceLang.resolved(_state.value.voiceLang, _state.value.lang)
        val onDevice = voice.speakAwait(clean, lang)
        var outcome = onDevice
        if (outcome == SpeakOutcome.Stopped) return outcome
        if (outcome != SpeakOutcome.Spoken) {
            val profile = activeProfile()
            val route = profile?.let { ProviderAudioPolicy.route(it.kind, it.baseUrl) } ?: AudioHope.KnownNo
            val tryProvider = profile != null && profile.apiKey.isNotBlank() &&
                ProviderAudioPolicy.shouldTry(true, route, audioProbeFailed)
            val bytes = if (tryProvider) {
                withContext(Dispatchers.IO) {
                    audio.speech(profile.baseUrl, profile.apiKey, clean, profile.extraHeaders)
                }
            } else {
                null
            }
            if (bytes != null) {
                val played = voice.playAwait(bytes)
                if (played) {
                    _state.update { it.copy(providerNote = ProviderVoiceNote.Spoken, voiceHint = VoiceHint.None) }
                    outcome = SpeakOutcome.Spoken
                } else {
                    _state.update { it.copy(providerNote = ProviderVoiceNote.None) }
                    outcome = if (onDevice == SpeakOutcome.MissingVoice) SpeakOutcome.MissingVoice else SpeakOutcome.Failed
                }
            } else if (tryProvider && route == AudioHope.Probe) {
                audioProbeFailed = true
            }
        }
        if (outcome == SpeakOutcome.Stopped) return outcome
        if (outcome == SpeakOutcome.Spoken) {
            if (continueLoop && _state.value.handsFree && voiceUiActive && !_state.value.running) {
                listenNow()
            } else {
                _state.update { it.copy(voicePhase = VoicePhase.Idle) }
            }
            return outcome
        }
        _state.update {
            it.copy(
                voicePhase = VoicePhase.Error,
                handsFree = false,
                voiceHint = if (outcome == SpeakOutcome.MissingVoice) VoiceHint.InstallTts else it.voiceHint,
            )
        }
        return outcome
    }

    private suspend fun activeProfile(): ProviderProfile? {
        val snap = _state.value
        val conv = snap.conversations.firstOrNull { it.id == snap.activeId }
        val id = conv?.providerId
        return if (!id.isNullOrBlank()) providers.profile(id) else providers.defaultProfile()
    }

    fun stopSpeaking() = voice.stopSpeaking()

    fun setChatModel(providerId: String?, model: String?) {
        val id = _state.value.activeId ?: return
        viewModelScope.launch {
            chats.setModel(id, providerId, model)
            refreshToolWarning()
        }
    }

    fun openModelPicker() {
        val snap = _state.value
        val conv = snap.conversations.firstOrNull { it.id == snap.activeId }
        val follow = conv?.providerId.isNullOrBlank()
        val providerId = conv?.providerId
            ?: snap.providers.firstOrNull { it.isDefault }?.id
            ?: snap.providers.firstOrNull()?.id
        _state.update {
            it.copy(
                pickerOpen = true,
                pickerFollowDefault = follow,
                pickerProviderId = providerId,
                pickerQuery = "",
                pickerError = null,
            )
        }
        if (providerId != null) loadModels(providerId, refresh = true)
    }

    fun closeModelPicker() {
        loadGen += 1
        _state.update { it.copy(pickerOpen = false, pickerBusy = false) }
    }

    fun setModelQuery(value: String) = _state.update { it.copy(pickerQuery = value) }

    fun followDefaultProvider() {
        val snap = _state.value
        val providerId = snap.providers.firstOrNull { it.isDefault }?.id ?: snap.providers.firstOrNull()?.id
        _state.update { it.copy(pickerFollowDefault = true, pickerProviderId = providerId, pickerQuery = "") }
        setChatModel(null, null)
        if (providerId != null) loadModels(providerId, refresh = false)
    }

    fun selectPickerProvider(id: String) {
        _state.update { it.copy(pickerFollowDefault = false, pickerProviderId = id, pickerQuery = "") }
        loadModels(id, refresh = true)
    }

    fun refreshModels() {
        val id = _state.value.pickerProviderId ?: return
        loadModels(id, refresh = true)
    }

    fun applyListedModel(modelId: String) {
        val trimmed = modelId.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val snap = _state.value
            val convId = snap.activeId ?: chats.createConversation("…").also { open(it) }
            val known = snap.pickerModels.firstOrNull { it.id == trimmed }
            val providerArg = if (snap.pickerFollowDefault) null else snap.pickerProviderId
            _state.update { it.copy(toolWarning = known?.tools == false) }
            chats.setModel(convId, providerArg, trimmed)
            refreshToolWarning()
        }
    }

    fun saveAsDefault(modelId: String) {
        val providerId = _state.value.pickerProviderId ?: return
        val model = modelId.trim()
        if (model.isEmpty()) return
        viewModelScope.launch {
            providers.setDefaultModel(providerId, model)
            val convId = _state.value.activeId
            if (_state.value.pickerFollowDefault && convId != null) {
                chats.setModel(convId, null, null)
            }
            refreshToolWarning()
        }
    }

    private fun loadModels(providerId: String, refresh: Boolean) {
        val gen = ++loadGen
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) { modelCache.list(providerId).map { it.toCatalog() } }
            if (gen != loadGen) return@launch
            val entity = _state.value.providers.firstOrNull { it.id == providerId }
            if (entity?.kind == ProviderKinds.GITHUB_MODELS) {
                _state.update {
                    if (it.pickerProviderId != providerId) it
                    else it.copy(
                        pickerModels = emptyList(),
                        pickerBusy = false,
                        pickerStatus = "discontinued",
                        pickerError = null,
                    )
                }
                return@launch
            }
            if (cached.isNotEmpty()) {
                _state.update {
                    if (it.pickerProviderId != providerId) it
                    else it.copy(
                        pickerModels = ModelOrder.sort(cached),
                        pickerBusy = refresh,
                        pickerStatus = "cache",
                        pickerError = null,
                    )
                }
                refreshToolWarning()
            } else if (refresh) {
                _state.update {
                    if (it.pickerProviderId != providerId) it else it.copy(pickerBusy = true, pickerError = null)
                }
            }
            if (!refresh && cached.isNotEmpty()) return@launch
            if (entity == null) {
                _state.update { if (it.pickerProviderId != providerId) it else it.copy(pickerBusy = false) }
                return@launch
            }
            val profile = try {
                withContext(Dispatchers.IO) { providers.profile(providerId) }
            } catch (_: Exception) {
                null
            }
            val result = if (profile == null) {
                null
            } else {
                catalog.fetch(
                    ModelFetchRequest(
                        kind = profile.kind,
                        baseUrl = profile.baseUrl,
                        apiKey = profile.apiKey,
                        accountId = profile.accountId,
                        extraHeaders = profile.extraHeaders,
                    ),
                )
            }
            val live = result?.models?.takeIf { result.status == ModelFetchStatus.OK && it.isNotEmpty() }
            if (live != null) {
                val fetchedAt = System.currentTimeMillis()
                withContext(Dispatchers.IO) {
                    modelCache.replace(providerId, live.map { it.toEntity(providerId, fetchedAt) })
                }
            }
            if (gen != loadGen) return@launch
            val builtin = BuiltinModels.fallback(entity.kind, entity.model, entity.baseUrl)
            val (models, source) = ModelOrder.choose(live, cached, builtin)
            val failed = result == null || result.status != ModelFetchStatus.OK
            val status = when {
                entity.kind == ProviderKinds.GITHUB_MODELS -> "discontinued"
                result?.status == ModelFetchStatus.DISCONTINUED -> "discontinued"
                source == "live" -> "live"
                source == "cache" -> "cache"
                source == "builtin" -> "builtin"
                else -> "empty"
            }
            _state.update {
                if (it.pickerProviderId != providerId) it
                else it.copy(
                    pickerModels = models,
                    pickerBusy = false,
                    pickerStatus = status,
                    pickerError = if (failed && status != "discontinued") {
                        fetchMessage(it.lang, result?.status ?: ModelFetchStatus.NETWORK, result?.httpCode)
                    } else {
                        null
                    },
                )
            }
            refreshToolWarning()
        }
    }

    private fun refreshToolWarning() {
        viewModelScope.launch {
            val snap = _state.value
            val conv = snap.conversations.firstOrNull { it.id == snap.activeId }
            if (conv == null) {
                _state.update { it.copy(toolWarning = false) }
                return@launch
            }
            val provider = conv.providerId?.let { id -> snap.providers.firstOrNull { it.id == id } }
                ?: snap.providers.firstOrNull { it.isDefault }
                ?: snap.providers.firstOrNull()
            if (provider == null) {
                _state.update { it.copy(toolWarning = false) }
                return@launch
            }
            val modelId = conv.modelOverride?.takeIf { it.isNotBlank() } ?: provider.model
            val listed = if (snap.pickerProviderId == provider.id) {
                snap.pickerModels.firstOrNull { it.id == modelId }
            } else {
                null
            }
            val cached = withContext(Dispatchers.IO) {
                modelCache.list(provider.id).firstOrNull { it.modelId == modelId }
            }
            val tools = listed?.tools ?: cached?.tools.toTri()
            val vision = listed?.vision ?: cached?.vision.toTri()
            _state.update { cur ->
                if (cur.activeId != conv.id) cur else cur.copy(toolWarning = tools == false, visionWarning = vision == false)
            }
        }
    }

    private fun fetchMessage(lang: String, status: ModelFetchStatus, code: Int?): String = when (status) {
        ModelFetchStatus.HTTP -> t(
            lang,
            "دریافت مدل‌ها ناموفق بود${code?.let { " ($it)" } ?: ""}. فهرست ذخیره‌شده یا آفلاین نشان داده می‌شود.",
            "Could not refresh models${code?.let { " ($it)" } ?: ""}. Showing the saved or offline list.",
        )
        ModelFetchStatus.EMPTY -> t(
            lang,
            "فهرست زنده خالی بود. فهرست ذخیره‌شده یا آفلاین نشان داده می‌شود.",
            "The live list was empty. Showing the saved or offline list.",
        )
        else -> t(
            lang,
            "شبکه جواب نداد. فهرست ذخیره‌شده یا آفلاین نشان داده می‌شود.",
            "The network did not answer. Showing the saved or offline list.",
        )
    }

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
        voice.stopListening()
        voice.stopRecording()
        voice.stopSpeaking()
        super.onCleared()
    }
}

private fun ModelCacheEntity.toCatalog(): CatalogModel = CatalogModel(
    id = modelId,
    displayName = displayName.ifBlank { modelId },
    free = free,
    promptPrice = promptPrice,
    completionPrice = completionPrice,
    contextLength = contextLength,
    vision = vision.toTri(),
    tools = tools.toTri(),
    source = ModelSource.CACHE,
    note = note,
)

private fun CatalogModel.toEntity(providerId: String, fetchedAt: Long): ModelCacheEntity = ModelCacheEntity(
    providerId = providerId,
    modelId = id,
    displayName = displayName.ifBlank { id },
    free = free,
    promptPrice = promptPrice,
    completionPrice = completionPrice,
    contextLength = contextLength,
    vision = vision.toFlag(),
    tools = tools.toFlag(),
    note = note,
    fetchedAt = fetchedAt,
)

private fun Int?.toTri(): Boolean? = when (this) {
    null -> null
    0 -> false
    else -> true
}

private fun Boolean?.toFlag(): Int? = when (this) {
    null -> null
    true -> 1
    false -> 0
}
