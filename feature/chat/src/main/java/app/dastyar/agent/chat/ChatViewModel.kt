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
import app.dastyar.agent.network.TranscribeOutcome
import app.dastyar.agent.ui.t
import app.dastyar.agent.voice.ListenError
import app.dastyar.agent.voice.ListenPath
import app.dastyar.agent.voice.ProviderVoiceNote
import app.dastyar.agent.voice.SpeakOutcome
import app.dastyar.agent.voice.Speakable
import app.dastyar.agent.voice.SpeechErrors
import app.dastyar.agent.voice.TranscriptionCandidate
import app.dastyar.agent.voice.TranscriptionChoice
import app.dastyar.agent.voice.VoiceController
import app.dastyar.agent.voice.VoiceGate
import app.dastyar.agent.voice.VoiceGateDecision
import app.dastyar.agent.voice.VoiceHint
import app.dastyar.agent.voice.VoiceLang
import app.dastyar.agent.voice.VoicePhase
import app.dastyar.agent.voice.VoiceSnap
import app.dastyar.agent.voice.VoiceSnapRules
import kotlinx.coroutines.delay
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
    val voiceBanner: String? = null,
    val listenPath: ListenPath = ListenPath.Phone,
    val pathProvider: String = "",
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
    private var voiceGen: Int = 0
    private var autoRestarts: Int = 0
    private var dismissJob: Job? = null

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
        stopVoice()
        viewModelScope.launch {
            val id = chats.createConversation("…")
            open(id)
        }
    }

    fun setComposer(value: String) = _state.update {
        it.applyVoice(VoiceSnapRules.clearBanner(it.voiceSnap())).copy(composer = value)
    }

    fun attach(path: String) = _state.update { it.copy(attachments = it.attachments + path) }

    fun clearError() = _state.update { it.copy(error = null, suggestProxy = false) }

    fun acceptShare(text: String, imagePath: String?) {
        stopVoice()
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
                    voiceBanner = null,
                    voiceHint = VoiceHint.None,
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
                        is AgentEvent.Failed -> {
                            _state.update {
                                it.copy(
                                    error = event.message,
                                    suggestProxy = event.suggestProxy,
                                    running = false,
                                    draft = "",
                                    voicePhase = if (speakAfter) VoicePhase.Error else it.voicePhase,
                                    handsFree = if (speakAfter) false else it.handsFree,
                                    voiceBanner = if (speakAfter) event.message else it.voiceBanner,
                                    listenError = if (speakAfter) ListenError.Unknown else it.listenError,
                                )
                            }
                            if (speakAfter) scheduleDismiss()
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

    fun startOneShot() {
        autoRestarts = 0
        _state.update {
            it.applyVoice(VoiceSnapRules.onRetry(it.voiceSnap())).copy(wantVoice = true, handsFree = false)
        }
    }

    fun startHandsFree() {
        autoRestarts = 0
        _state.update {
            it.applyVoice(VoiceSnapRules.onRetry(it.voiceSnap())).copy(wantVoice = true, handsFree = true)
        }
    }

    fun retryVoice() {
        if (_state.value.handsFree) startHandsFree() else startOneShot()
    }

    fun consumeWantVoice() = _state.update { it.copy(wantVoice = false) }

    fun dismissVoiceBanner() = _state.update { it.applyVoice(VoiceSnapRules.clearBanner(it.voiceSnap())) }

    fun dismissVoiceError() {
        val gen = voiceGen
        val next = VoiceSnapRules.dismiss(_state.value.voiceSnap())
        _state.update { it.applyVoice(next) }
        if (gen != voiceGen) return
        if (VoiceSnapRules.shouldRestart(next, autoRestarts) && voiceUiActive) {
            autoRestarts++
            listenNow()
        }
    }

    fun setVoiceUiActive(active: Boolean) {
        voiceUiActive = active
        if (!active) stopVoice()
    }

    fun onMicDenied() {
        voice.stopListening()
        presentError(ListenError.Permission)
    }

    fun onMicGranted() {
        if (_state.value.listening) {
            stopVoice()
            return
        }
        autoRestarts = 0
        _state.update {
            it.applyVoice(VoiceSnapRules.onRetry(it.voiceSnap())).copy(providerNote = ProviderVoiceNote.None)
        }
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
        voiceGen++
        autoRestarts = 0
        dismissJob?.cancel()
        voice.stopListening()
        voice.stopRecording()
        voice.stopSpeaking()
        _state.update {
            it.applyVoice(VoiceSnapRules.onLeave(it.voiceSnap())).copy(wantVoice = false)
        }
    }

    fun speakMessage(text: String) {
        viewModelScope.launch {
            _state.update { it.copy(handsFree = false, voicePhase = VoicePhase.Speaking, voiceBanner = null, listenError = null, voiceHint = VoiceHint.None) }
            deliverSpeech(text, continueLoop = false)
        }
    }

    private fun listenNow() {
        if (!voice.recognitionAvailable()) {
            viewModelScope.launch { transcribeWithProviderOrHint() }
            return
        }
        val snap = _state.value
        val gen = voiceGen
        _state.update {
            it.copy(
                listening = true,
                voicePhase = VoicePhase.Listening,
                listenError = null,
                voiceBanner = null,
                voiceHint = VoiceHint.None,
                listenPath = if (voice.onlineComponent() != null) ListenPath.Phone else ListenPath.OnDevice,
                pathProvider = "",
            )
        }
        voice.listen(
            choice = snap.voiceLang,
            appLang = snap.lang,
            onPartial = { partial ->
                if (gen != voiceGen) return@listen
                _state.update { state ->
                    if (state.handsFree) state.copy(voiceLive = partial) else state.copy(composer = partial, voiceLive = partial)
                }
            },
            onResult = { text ->
                if (gen != voiceGen) return@listen
                autoRestarts = 0
                _state.update { it.applyVoice(VoiceSnapRules.onRecognized(it.voiceSnap())).copy(voiceLive = text) }
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
                if (gen != voiceGen) return@listen
                if (err == ListenError.NoRecognizer || err == ListenError.Client || err == ListenError.Server || err == ListenError.Language || err == ListenError.Unavailable) {
                    viewModelScope.launch { if (gen == voiceGen) transcribeWithProviderOrHint(err) }
                } else {
                    presentError(err)
                }
            },
        )
    }

    private suspend fun transcribeWithProviderOrHint(fallbackFrom: ListenError = ListenError.NoRecognizer) {
        val gen = voiceGen
        val loaded = loadProfiles()
        val chosen = transcriptionCandidates(loaded)
        if (chosen.isEmpty()) {
            val error = when {
                loaded.any { it.apiKey.isNotBlank() } -> ListenError.Unsupported
                else -> ListenError.MissingKey
            }
            presentError(if (fallbackFrom == ListenError.Permission) fallbackFrom else error)
            return
        }
        val first = chosen.first()
        _state.update {
            it.copy(
                voicePhase = VoicePhase.Listening,
                providerNote = ProviderVoiceNote.Transcribed,
                listening = true,
                listenPath = ListenPath.Provider,
                pathProvider = first.label,
                listenError = null,
                voiceBanner = null,
                voiceHint = VoiceHint.None,
            )
        }
        val wav = withContext(Dispatchers.IO) { voice.recordWav() }
        if (gen != voiceGen) return
        if (_state.value.voicePhase != VoicePhase.Listening && !_state.value.handsFree) return
        if (wav == null) {
            presentError(ListenError.Audio)
            return
        }
        val lang = VoiceLang.resolved(_state.value.voiceLang, _state.value.lang)
        var lastError = ListenError.Unsupported
        for (candidate in chosen) {
            if (gen != voiceGen) return
            _state.update { it.copy(pathProvider = candidate.label, listenPath = ListenPath.Provider) }
            val route = ProviderAudioPolicy.transcriptionRoute(candidate.kind, candidate.baseUrl)
            val outcome = withContext(Dispatchers.IO) {
                audio.transcribe(
                    candidate.baseUrl,
                    candidate.apiKey,
                    wav,
                    lang,
                    candidate.extraHeaders,
                    candidate.kind,
                )
            }
            if (gen != voiceGen) return
            when (outcome) {
                is TranscribeOutcome.Text -> {
                    autoRestarts = 0
                    _state.update {
                        it.applyVoice(VoiceSnapRules.onRecognized(it.voiceSnap()))
                            .copy(voiceLive = outcome.value, providerNote = ProviderVoiceNote.Transcribed, pathProvider = candidate.label)
                    }
                    if (_state.value.handsFree) {
                        _state.update { it.copy(composer = outcome.value, voicePhase = VoicePhase.Processing) }
                        sendInternal(fromVoice = true)
                    } else {
                        _state.update {
                            it.copy(composer = outcome.value, voicePhase = VoicePhase.Idle, voiceReview = true, voiceLive = "")
                        }
                    }
                    return
                }
                TranscribeOutcome.Unauthorized -> {
                    presentError(ListenError.Unauthorized)
                    return
                }
                TranscribeOutcome.MissingKey -> lastError = ListenError.MissingKey
                TranscribeOutcome.Unsupported -> {
                    if (route == AudioHope.Probe) audioProbeFailed = true
                    lastError = ListenError.Unsupported
                }
                TranscribeOutcome.Empty -> lastError = ListenError.NoMatch
                TranscribeOutcome.Failed -> {
                    if (route == AudioHope.Probe) audioProbeFailed = true
                    lastError = ListenError.Unavailable
                }
            }
        }
        presentError(lastError)
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
            _state.update { it.applyVoice(VoiceSnapRules.onSpoken(it.voiceSnap())) }
            if (continueLoop && _state.value.handsFree && voiceUiActive && !_state.value.running) {
                listenNow()
            } else if (_state.value.voicePhase == VoicePhase.Speaking) {
                _state.update { it.copy(voicePhase = VoicePhase.Idle) }
            }
            return outcome
        }
        if (outcome == SpeakOutcome.MissingVoice) presentMissingVoice() else presentError(ListenError.Unavailable)
        return outcome
    }

    private fun presentError(error: ListenError) {
        val persian = _state.value.lang.startsWith("fa")
        _state.update { it.applyVoice(VoiceSnapRules.showError(it.voiceSnap(), error, persian)) }
        scheduleDismiss()
    }

    private fun presentMissingVoice() {
        val persian = _state.value.lang.startsWith("fa")
        _state.update { it.applyVoice(VoiceSnapRules.showMissingVoice(it.voiceSnap(), persian)) }
        scheduleDismiss()
    }

    private fun scheduleDismiss() {
        dismissJob?.cancel()
        val gen = voiceGen
        dismissJob = viewModelScope.launch {
            delay(SpeechErrors.DISMISS_AFTER_MS)
            if (gen == voiceGen) dismissVoiceError()
        }
    }

    private suspend fun loadProfiles(): List<ReadyVoiceProfile> =
        _state.value.providers.mapNotNull { entity ->
            val profile = providers.profile(entity.id) ?: return@mapNotNull null
            ReadyVoiceProfile(
                id = profile.id,
                kind = profile.kind,
                baseUrl = profile.baseUrl,
                label = profile.label.ifBlank { entity.kind },
                apiKey = profile.apiKey,
                extraHeaders = profile.extraHeaders,
            )
        }

    private fun transcriptionCandidates(loaded: List<ReadyVoiceProfile>): List<ReadyVoiceProfile> {
        val snap = _state.value
        val conv = snap.conversations.firstOrNull { it.id == snap.activeId }
        val activeId = conv?.providerId?.takeIf { it.isNotBlank() }
        val ordered = TranscriptionChoice.ordered(
            activeId,
            loaded.map { TranscriptionCandidate(it.id, it.kind, it.baseUrl, it.apiKey.isNotBlank(), it.label) },
        ) { row ->
            val route = ProviderAudioPolicy.transcriptionRoute(row.kind, row.baseUrl)
            ProviderAudioPolicy.shouldTranscribe(true, route, audioProbeFailed)
        }
        val byId = loaded.associateBy { it.id }
        return ordered.mapNotNull { byId[it.id] }
    }

    private fun ChatUi.voiceSnap() = VoiceSnap(
        phase = voicePhase,
        handsFree = handsFree,
        hint = voiceHint,
        error = listenError,
        banner = voiceBanner,
        listening = listening,
        review = voiceReview,
        live = voiceLive,
        note = providerNote,
        path = listenPath,
        pathProvider = pathProvider,
    )

    private fun ChatUi.applyVoice(s: VoiceSnap) = copy(
        voicePhase = s.phase,
        handsFree = s.handsFree,
        voiceHint = s.hint,
        listenError = s.error,
        voiceBanner = s.banner,
        listening = s.listening,
        voiceReview = s.review,
        voiceLive = s.live,
        providerNote = s.note,
        listenPath = s.path,
        pathProvider = s.pathProvider,
    )

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

private data class ReadyVoiceProfile(
    val id: String,
    val kind: String,
    val baseUrl: String,
    val label: String,
    val apiKey: String,
    val extraHeaders: Map<String, String>,
)

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
