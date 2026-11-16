package ai.roadie.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.roadie.app.data.*
import ai.roadie.app.platform.*
import ai.roadie.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class RoadieViewModel(application: Application) : AndroidViewModel(application) {
    val vault = PrivateVault(application)
    private val store = SettingsStore(application)
    private val liveVoice = PleosVoice(application)
    private val demoVoice = DemoVoice()
    private val liveBackend = BackendClient(vault)
    private val demoBackend = DemoBackend()
    private val focus = AudioFocus(application)
    private val driving = DrivingRestrictions(application)
    val access = driving.access
    private val mutablePreferences = MutableStateFlow(Preferences())
    val preferences = mutablePreferences.asStateFlow()
    val loaded = MutableStateFlow(false)
    val notice = MutableStateFlow<String?>(null)
    val places = MutableStateFlow<List<Place>>(emptyList())
    val placesBusy = MutableStateFlow(false)
    private var placeWork: Job? = null
    private var placeGeneration = 0L
    private var navigation: RestNavigation? = null

    private val voice = object : VoicePort {
        private fun selected() = if (preferences.value.mode == Mode.DEMO) demoVoice else liveVoice
        override suspend fun listen(onPartial: (String) -> Unit) = selected().listen(onPartial)
        override suspend fun speak(text: String) = selected().speak(text)
        override fun stop() { liveVoice.stop(); demoVoice.stop() }
    }
    private val backend = object : BackendPort {
        private fun selected() = if (preferences.value.mode == Mode.DEMO) demoBackend else liveBackend
        override suspend fun reply(text: String, history: List<Turn>) = selected().reply(text, history)
        override suspend fun briefing(topic: Topic) = selected().briefing(topic)
        override suspend fun places(coordinates: Coordinates) = selected().places(coordinates)
    }
    val conversation = Conversation(viewModelScope, voice, backend, focus, vault)
    val session = conversation.state

    init {
        focus.onLoss = { conversation.pauseForFocus(it) }
        focus.onGain = { conversation.regainFocus() }
        viewModelScope.launch {
            store.preferences.collect {
                if (it.mode != preferences.value.mode) conversation.end()
                mutablePreferences.value = it
                conversation.configure(it)
                loaded.value = true
            }
        }
        viewModelScope.launch { while (isActive) { delay(30_000); conversation.tick() } }
    }

    fun updatePreferences(value: Preferences) {
        if (!access.value.settingsAllowed) return
        viewModelScope.launch {
            try { store.save(value) } catch (_: Exception) { notice.value = "설정을 저장하지 못했어요. 저장 공간을 확인해주세요." }
        }
    }
    fun saveConnection(endpoint: String, token: String) {
        if (!access.value.settingsAllowed) return
        if (!BackendClient.validEndpoint(endpoint) || token.isNotBlank() && token.length < 32 || token.isBlank() && vault.accessToken.isBlank()) {
            notice.value = "HTTPS 주소와 32자 이상의 서버 접근 토큰을 확인해주세요."; return
        }
        try {
            vault.endpoint = endpoint
            if (token.isNotBlank()) vault.accessToken = token
            notice.value = "서버 연결 정보를 저장했어요. 대화를 시작해 연결할 수 있어요."
        } catch (_: Exception) { notice.value = "연결 정보를 저장하지 못했어요." }
    }
    fun clearMemory() {
        if (!access.value.settingsAllowed) return
        runCatching { conversation.clearMemory() }.onSuccess { notice.value = "이 기기에 저장된 대화 기억을 지웠어요." }
            .onFailure { notice.value = "기억을 지우지 못했어요. 저장 공간을 확인해주세요." }
    }
    fun findRest() {
        conversation.quiet()
        if (preferences.value.mode == Mode.DEMO) { notice.value = "데모 모드에서는 실제 위치를 조회하거나 길 안내를 시작하지 않아요."; return }
        val stamp = ++placeGeneration
        placeWork?.cancel(); places.value = emptyList(); placesBusy.value = true
        placeWork = viewModelScope.launch {
            try {
                val nav = RestNavigation(getApplication()).also { navigation?.close(); navigation = it }
                val position = withTimeout(15_000) { nav.locate() }
                val result = withTimeout(25_000) { liveBackend.places(position) }
                if (stamp != placeGeneration) return@launch
                places.value = result
                if (result.isEmpty()) notice.value = "주변 20km에서 휴식 장소를 찾지 못했어요. 도로 표지와 차량 내비게이션을 확인해주세요."
            } catch (e: CancellationException) { if (e is TimeoutCancellationException && stamp == placeGeneration) notice.value = "위치나 장소 응답이 늦어졌어요. 다시 찾아볼 수 있어요." }
            catch (e: Exception) { if (stamp == placeGeneration) notice.value = (e as? RoadieFailure)?.userMessage ?: "휴식 장소를 찾지 못했어요. 위치 권한과 연결을 확인해주세요." }
            finally { if (stamp == placeGeneration) placesBusy.value = false }
        }
    }
    fun navigate(place: Place) {
        if (preferences.value.mode == Mode.DEMO) return
        val stamp = ++placeGeneration
        placeWork?.cancel()
        placeWork = viewModelScope.launch {
            try {
                placesBusy.value = true
                val nav = navigation ?: RestNavigation(getApplication()).also { navigation = it }
                withTimeout(20_000) { nav.navigate(place) }
                if (stamp != placeGeneration) return@launch
                places.value = emptyList(); notice.value = "차량 내비게이션에서 경로 탐색을 마쳤어요. 안내를 확인해주세요."
            } catch (e: CancellationException) { if (e is TimeoutCancellationException && stamp == placeGeneration) notice.value = "내비 응답을 확인하지 못했어요. 차량 내비 화면에서 경로를 확인해주세요." }
            catch (e: Exception) { if (stamp == placeGeneration) notice.value = (e as? RoadieFailure)?.userMessage ?: "이 환경에서 내비게이션을 연결하지 못했어요." }
            finally { if (stamp == placeGeneration) placesBusy.value = false }
        }
    }
    fun cancelPlaces() { placeGeneration++; placeWork?.cancel(); places.value = emptyList(); placesBusy.value = false; navigation?.close(); navigation = null }
    fun end() { cancelPlaces(); conversation.end() }
    fun background() { end(); notice.value = null }
    override fun onCleared() { background(); driving.close(); super.onCleared() }
}
