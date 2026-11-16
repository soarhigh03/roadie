package ai.roadie.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** All commands and callbacks are serialized on the supplied main-thread scope. */
class Conversation(
    private val scope: CoroutineScope,
    private val voice: VoicePort,
    private val backend: BackendPort,
    private val focus: FocusPort,
    private val memory: MemoryPort,
    initialPreferences: Preferences = Preferences(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutable = MutableStateFlow(SessionState())
    val state = mutable.asStateFlow()
    var preferences = initialPreferences; private set
    private var history = if (initialPreferences.remember && initialPreferences.mode == Mode.LIVE) memory.load().takeLast(6) else emptyList()
    private var generation = 0L
    private var work: Job? = null
    private var startedAt = 0L
    private var restOffered = false
    private var lastPromptAt = 0L
    private var focusAvailable = true
    private var resumeAfterFocus = false
    private var retry: (() -> Unit)? = null

    fun configure(value: Preferences) {
        if (value.mode != preferences.mode) {
            end()
            history = if (value.remember && value.mode == Mode.LIVE) memory.load().takeLast(6) else emptyList()
        } else if (value.remember && !preferences.remember && value.mode == Mode.LIVE) history = memory.load().takeLast(6)
        if (!value.remember) { memory.clear(); if (preferences.remember) history = emptyList() }
        preferences = value
    }

    fun start() {
        if (!state.value.active) {
            startedAt = now(); lastPromptAt = startedAt; restOffered = false
            mutable.value = SessionState(active = true)
        }
        listen()
    }

    private fun cancelWork(releaseFocus: Boolean = true) {
        generation++
        work?.cancel(); work = null
        voice.stop()
        if (releaseFocus) focus.release()
    }

    private fun canAct() = state.value.active && focusAvailable

    fun listen() {
        if (!canAct()) return
        cancelWork()
        retry = { listen() }
        if (!focus.acquire()) { pauseForFocus(false); return }
        mutable.value = state.value.copy(phase = Phase.LISTENING, message = "편하게 말해요. 이야기가 끝나면 이어갈게요.", partial = "", briefing = null, retryable = false, permissionDenied = false)
        val stamp = generation
        work = scope.launch {
            try {
                val text = withTimeout(35_000) { voice.listen { if (stamp == generation) mutable.value = state.value.copy(partial = it.take(160)) } }
                if (stamp != generation) return@launch
                voice.stop()
                handleText(text, stamp)
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException && stamp == generation) fail(RoadieFailure(when (state.value.phase) {
                    Phase.THINKING -> "응답이 늦어지고 있어요. 다시 연결할 수 있어요."
                    Phase.SPEAKING -> "음성 재생이 지연됐어요. 다시 재생할 수 있어요."
                    else -> "잘 듣지 못했어요. 마이크와 권한을 확인하고 다시 말해주세요."
                }))
            } catch (e: Exception) { if (stamp == generation) fail(e) }
        }
    }

    /** Explicit demo input follows the same transitions but is never accepted in live mode. */
    fun demoInput(text: String) {
        if (preferences.mode != Mode.DEMO || !canAct()) return
        cancelWork()
        val stamp = generation
        work = scope.launch { try { handleText(text, stamp) } catch (e: Exception) { if (e !is CancellationException && stamp == generation) fail(e) } }
    }

    private suspend fun handleText(raw: String, stamp: Long) {
        val text = raw.trim().take(1200)
        if (text.isBlank()) throw RoadieFailure("잘 듣지 못했어요. 다시 말해주세요.")
        when {
            listOf("대화 끝", "그만할", "종료해").any(text::contains) -> { end(); return }
            listOf("마이크 꺼", "마이크 끄").any(text::contains) -> { mute(); return }
            listOf("조용히", "잠깐 조용").any(text::contains) -> { quiet(); return }
            fatigue(text) -> { offerRest(); return }
            listOf("뉴스", "브리핑", "소식 들려").any(text::contains) -> { loadBriefing(); return }
        }
        retry = { requestReply(text) }
        answer(text, stamp)
    }

    private fun requestReply(text: String) {
        if (!canAct()) return
        cancelWork()
        val stamp = generation
        work = scope.launch {
            try { answer(text, stamp) }
            catch (e: CancellationException) { if (e is TimeoutCancellationException && stamp == generation) fail(RoadieFailure("응답이 늦어지고 있어요. 다시 연결할 수 있어요.")) }
            catch (e: Exception) { if (stamp == generation) fail(e) }
        }
    }

    private suspend fun answer(text: String, stamp: Long) {
        mutable.value = state.value.copy(phase = Phase.THINKING, partial = "", message = "이야기를 생각하고 있어요.", retryable = false)
        focus.release()
        val reply = withTimeout(25_000) { backend.reply(text, history.takeLast(6)) }.trim().take(600)
        if (stamp != generation || !canAct()) return
        if (reply.isBlank()) throw RoadieFailure("응답을 받지 못했어요. 다시 연결해주세요.")
        history = (history + Turn("user", text) + Turn("assistant", reply)).takeLast(6)
        if (preferences.remember && preferences.mode == Mode.LIVE) memory.save(history)
        retry = { replayReply(reply) }
        say(reply, stamp)
    }

    private fun replayReply(reply: String) {
        if (!canAct()) return
        cancelWork()
        val stamp = generation
        work = scope.launch {
            try { say(reply, stamp) }
            catch (e: CancellationException) { if (e is TimeoutCancellationException && stamp == generation) fail(RoadieFailure("음성 재생이 지연됐어요.")) }
            catch (e: Exception) { if (stamp == generation) fail(e) }
        }
    }

    private suspend fun say(text: String, stamp: Long, after: Phase = Phase.WAITING) {
        if (stamp != generation || !canAct()) return
        if (!focus.acquire()) { pauseForFocus(false); return }
        mutable.value = state.value.copy(phase = Phase.SPEAKING, message = text, partial = "", retryable = false)
        withTimeout(90_000) { voice.speak(text) }
        if (stamp != generation) return
        voice.stop(); focus.release()
        mutable.value = state.value.copy(phase = after)
        // Microphone opens again only after explicit opt-in, and never after a briefing or rest suggestion.
        if (after == Phase.WAITING && preferences.resumeListening) listen()
    }

    fun quiet() {
        if (!state.value.active) return
        cancelWork(); retry = null; resumeAfterFocus = false
        mutable.value = state.value.copy(phase = Phase.QUIET, message = "말없이 함께 가도 괜찮아요.\n다시 이야기하기를 누르면 들을게요.", partial = "", retryable = false)
    }

    fun mute() {
        if (!state.value.active) return
        cancelWork(); retry = null; resumeAfterFocus = false
        mutable.value = state.value.copy(phase = Phase.MUTED, message = "내 목소리를 보내지 않아요.\n다시 이야기하고 싶을 때 켜주세요.", partial = "", retryable = false)
    }

    fun pauseForFocus(transient: Boolean = true) {
        focusAvailable = false
        val old = state.value.phase
        resumeAfterFocus = transient && old == Phase.LISTENING && preferences.resumeListening
        cancelWork(releaseFocus = !transient)
        if (state.value.active && old !in setOf(Phase.QUIET, Phase.MUTED, Phase.REST)) {
            mutable.value = state.value.copy(phase = Phase.PAUSED, message = "중요한 오디오가 먼저예요.\n안내가 끝나면 다시 이야기할 수 있어요.", partial = "", retryable = false)
        }
    }

    fun regainFocus() {
        focusAvailable = true
        val resume = resumeAfterFocus
        resumeAfterFocus = false
        if (state.value.phase != Phase.PAUSED) return
        if (resume) listen() else quiet()
    }

    /** Explicit user action may reacquire focus after a permanent loss. */
    fun resume() { focusAvailable = true; if (state.value.active) listen() else start() }

    fun retry() { if (canAct()) retry?.invoke() }

    fun permissionDenied() {
        if (!state.value.active) { startedAt = now(); mutable.value = SessionState(active = true) }
        cancelWork(); retry = { listen() }
        fail(RoadieFailure("음성 권한이 꺼져 있어요. 정차 후 권한을 허용하면 다시 연결할 수 있어요.", permission = true))
    }

    fun loadBriefing(topic: Topic = preferences.interests.firstOrNull() ?: Topic.TECH) {
        if (!canAct()) return
        cancelWork()
        retry = { loadBriefing(topic) }
        mutable.value = state.value.copy(phase = Phase.THINKING, message = "출처가 있는 소식을 가져오고 있어요.", partial = "", retryable = false)
        val stamp = generation
        work = scope.launch {
            try {
                val briefing = withTimeout(25_000) { backend.briefing(topic) }
                if (stamp != generation) return@launch
                mutable.value = state.value.copy(phase = Phase.BRIEFING, briefing = briefing, articleIndex = 0,
                    message = if (briefing.articles.isEmpty()) "지금은 전할 소식이 없어요. 다른 관심사를 골라주세요." else "${topic.label} 소식부터 짧게 들어볼까요?")
            } catch (e: CancellationException) { if (e is TimeoutCancellationException && stamp == generation) fail(RoadieFailure("소식을 가져오는 데 시간이 걸려요.")) }
            catch (e: Exception) { if (stamp == generation) fail(e) }
        }
    }

    fun playBriefing() {
        if (!canAct() || state.value.phase == Phase.MUTED) return
        val article = state.value.briefing?.articles?.getOrNull(state.value.articleIndex) ?: return
        cancelWork()
        retry = { playBriefing() }
        val stamp = generation
        work = scope.launch {
            try {
                val freshness = if (article.stale) "지난 소식이에요. " else ""
                say("$freshness${article.source}. ${article.summary}", stamp, Phase.BRIEFING)
            } catch (e: CancellationException) { if (e is TimeoutCancellationException && stamp == generation) fail(RoadieFailure("음성 재생이 지연됐어요.")) }
            catch (e: Exception) { if (stamp == generation) fail(e) }
        }
    }

    fun stopBriefing() { cancelWork(); if (state.value.active) mutable.value = state.value.copy(phase = Phase.BRIEFING, message = "브리핑을 멈췄어요.") }
    fun nextArticle() {
        val count = state.value.briefing?.articles?.size ?: return
        if (count == 0) return
        stopBriefing()
        mutable.value = state.value.copy(articleIndex = (state.value.articleIndex + 1) % count)
    }
    fun nextTopic() {
        val topics = preferences.interests.ifEmpty { setOf(Topic.TECH) }.toList()
        loadBriefing(topics[(topics.indexOf(state.value.briefing?.topic) + 1) % topics.size])
    }

    fun offerRest() {
        if (!state.value.active) return
        cancelWork(); restOffered = true; retry = null
        val text = "졸리거나 피곤하면 안전한 곳에 정차해서 쉬어요. 대화가 휴식을 대신할 수는 없어요."
        mutable.value = state.value.copy(phase = Phase.REST, message = text, partial = "")
        val stamp = generation
        if (!focusAvailable) return
        work = scope.launch {
            try { say(text, stamp, Phase.REST) }
            catch (_: CancellationException) { }
            catch (_: Exception) { if (stamp == generation) { voice.stop(); focus.release(); mutable.value = state.value.copy(phase = Phase.REST, message = text) } }
        }
    }

    fun tick() {
        if (!state.value.active) return
        val elapsed = (now() - startedAt).coerceAtLeast(0)
        mutable.value = state.value.copy(elapsedMinutes = elapsed / 60_000)
        if (preferences.restReminder && !restOffered && elapsed >= 120 * 60_000 && state.value.phase !in setOf(Phase.MUTED, Phase.PAUSED, Phase.QUIET)) offerRest()
        if (preferences.proactive && state.value.phase == Phase.WAITING && focusAvailable && now() - lastPromptAt >= 20 * 60_000) {
            lastPromptAt = now()
            cancelWork()
            val stamp = generation
            work = scope.launch {
                try { say("오늘 길은 어떤가요? 이야기하고 싶으면 다시 이야기하기를 눌러주세요.", stamp) }
                catch (_: CancellationException) { }
                catch (e: Exception) { if (stamp == generation) fail(e) }
            }
        }
    }

    private fun fail(error: Exception) {
        voice.stop(); focus.release()
        val failure = error as? RoadieFailure
        mutable.value = state.value.copy(phase = Phase.ERROR, message = failure?.userMessage ?: "연결을 확인해주세요. 잠시 후 다시 시도할 수 있어요.", partial = "", retryable = failure?.canRetry ?: true, permissionDenied = failure?.permission ?: false)
    }

    fun clearMemory() { history = emptyList(); memory.clear() }
    fun end() {
        cancelWork(); retry = null; resumeAfterFocus = false; focusAvailable = true
        if (!preferences.remember) clearMemory()
        else if (preferences.mode == Mode.DEMO) history = emptyList()
        mutable.value = SessionState()
    }
    fun background() = end()
    companion object {
        fun fatigue(text: String): Boolean = listOf("졸려", "졸리", "졸음", "피곤", "눈이 감", "잠이 와", "잠와", "잠 온", "힘들어").any(text::contains)
    }
}
