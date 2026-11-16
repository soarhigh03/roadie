package ai.roadie.core

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationTest {
    class Voice : VoicePort {
        val input = Channel<String>(Channel.UNLIMITED)
        val spoken = mutableListOf<String>()
        var stops = 0
        var listens = 0
        var speechGate: CompletableDeferred<Unit>? = null
        var failure: Exception? = null
        override suspend fun listen(onPartial: (String) -> Unit): String { listens++; failure?.let { throw it }; return input.receive() }
        override suspend fun speak(text: String) { spoken += text; speechGate?.await() }
        override fun stop() { stops++ }
    }
    class Backend : BackendPort {
        var calls = 0
        var histories = mutableListOf<List<Turn>>()
        var response: suspend () -> String = { "오늘도 함께 갈게요." }
        var news: suspend (Topic) -> Briefing = { Briefing(it, "2026-11-16T00:00:00Z", emptyList()) }
        override suspend fun reply(text: String, history: List<Turn>): String { calls++; histories += history; return response() }
        override suspend fun briefing(topic: Topic) = news(topic)
        override suspend fun places(coordinates: Coordinates) = emptyList<Place>()
    }
    class Focus : FocusPort {
        var allowed = true
        var releases = 0
        override fun acquire() = allowed
        override fun release() { releases++ }
    }
    class Memory : MemoryPort {
        var turns = emptyList<Turn>(); var writes = 0
        override fun load() = turns
        override fun save(turns: List<Turn>) { this.turns = turns; writes++ }
        override fun clear() { turns = emptyList() }
    }
    class Fixture(val scope: TestScope, prefs: Preferences = Preferences()) {
        val voice = Voice(); val backend = Backend(); val focus = Focus(); val memory = Memory()
        val c = Conversation(scope, voice, backend, focus, memory, prefs) { scope.testScheduler.currentTime }
        fun utter(text: String = "오늘 길이 예뻐") { c.start(); scope.runCurrent(); voice.input.trySend(text); scope.runCurrent() }
    }
    @Test fun `start listens then thinks and speaks`() = runTest {
        val f = Fixture(this); val gate = CompletableDeferred<String>(); f.backend.response = { gate.await() }
        f.c.start(); runCurrent(); assertEquals(Phase.LISTENING, f.c.state.value.phase)
        f.voice.input.send("안녕"); runCurrent(); assertEquals(Phase.THINKING, f.c.state.value.phase)
        f.voice.speechGate = CompletableDeferred(); gate.complete("안녕, 반가워요."); runCurrent()
        assertEquals(Phase.SPEAKING, f.c.state.value.phase)
        f.voice.speechGate!!.complete(Unit); runCurrent(); assertEquals(Phase.WAITING, f.c.state.value.phase)
        assertEquals(1, f.voice.listens); f.c.end()
    }
    @Test fun `late non cooperative network response cannot speak after mute`() = runTest {
        val f = Fixture(this); val gate = CompletableDeferred<String>()
        f.backend.response = { withContext(NonCancellable) { gate.await() } }
        f.utter(); f.c.mute(); gate.complete("늦은 응답"); runCurrent()
        assertEquals(Phase.MUTED, f.c.state.value.phase); assertTrue(f.voice.spoken.isEmpty())
    }
    @Test fun `interrupt speech cancels output and opens fresh recognition`() = runTest {
        val f = Fixture(this); f.voice.speechGate = CompletableDeferred(); f.utter()
        assertEquals(Phase.SPEAKING, f.c.state.value.phase)
        val stops = f.voice.stops; f.c.listen(); runCurrent()
        assertTrue(f.voice.stops > stops); assertEquals(Phase.LISTENING, f.c.state.value.phase); f.c.end()
    }
    @Test fun `permission denial is recoverable without restarting session`() = runTest {
        val f = Fixture(this); f.voice.failure = RoadieFailure("권한을 허용해주세요.", permission = true)
        f.c.start(); runCurrent(); assertTrue(f.c.state.value.permissionDenied)
        f.voice.failure = null; f.c.retry(); runCurrent()
        assertEquals(Phase.LISTENING, f.c.state.value.phase); assertFalse(f.c.state.value.permissionDenied); f.c.end()
    }
    @Test fun `transient focus gain does not automatically speak or record by default`() = runTest {
        val f = Fixture(this); f.c.start(); runCurrent(); f.c.pauseForFocus(); f.c.regainFocus(); runCurrent()
        assertEquals(Phase.QUIET, f.c.state.value.phase); assertEquals(1, f.voice.listens)
    }
    @Test fun `opt in resumes listening after transient focus`() = runTest {
        val f = Fixture(this, Preferences(resumeListening = true)); f.c.start(); runCurrent()
        f.c.pauseForFocus(); f.c.regainFocus(); runCurrent()
        assertEquals(Phase.LISTENING, f.c.state.value.phase); assertEquals(2, f.voice.listens); f.c.end()
    }
    @Test fun `user quiet overrides pending resume`() = runTest {
        val f = Fixture(this, Preferences(resumeListening = true)); f.c.start(); runCurrent()
        f.c.pauseForFocus(); f.c.quiet(); f.c.regainFocus(); runCurrent()
        assertEquals(Phase.QUIET, f.c.state.value.phase); assertEquals(1, f.voice.listens)
    }
    @Test fun `mute survives focus changes`() = runTest {
        val f = Fixture(this); f.c.start(); runCurrent(); f.c.mute(); f.c.pauseForFocus(); f.c.regainFocus()
        assertEquals(Phase.MUTED, f.c.state.value.phase)
    }
    @Test fun `failed focus request never records`() = runTest {
        val f = Fixture(this); f.focus.allowed = false; f.c.start(); runCurrent()
        assertEquals(Phase.PAUSED, f.c.state.value.phase); assertEquals(0, f.voice.listens)
        f.focus.allowed = true; f.c.resume(); runCurrent(); assertEquals(1, f.voice.listens); f.c.end()
    }
    @Test fun `retry resends failed utterance and does not duplicate history`() = runTest {
        val f = Fixture(this); f.backend.response = { throw RoadieFailure("연결 오류") }; f.utter()
        assertEquals(Phase.ERROR, f.c.state.value.phase)
        f.backend.response = { "다시 연결됐어요." }; f.c.retry(); runCurrent()
        assertEquals(2, f.backend.calls); assertEquals(1, f.voice.spoken.size)
        assertTrue(f.backend.histories.all { it.isEmpty() })
    }
    @Test fun `AI timeout shows error and retry`() = runTest {
        val f = Fixture(this); f.backend.response = { delay(100_000); "늦은 응답" }; f.utter()
        advanceTimeBy(25_001); runCurrent(); assertEquals(Phase.ERROR, f.c.state.value.phase)
        assertTrue(f.c.state.value.retryable); assertTrue(f.voice.spoken.isEmpty())
    }
    @Test fun `background clears active recording and rejects late results`() = runTest {
        val f = Fixture(this); f.c.start(); runCurrent(); val stops = f.voice.stops
        f.c.background(); f.voice.input.send("늦은 말"); runCurrent()
        assertFalse(f.c.state.value.active); assertTrue(f.voice.stops > stops); assertEquals(0, f.backend.calls)
    }
    @Test fun `no consent never writes conversation memory`() = runTest {
        val f = Fixture(this); f.utter(); f.c.end(); assertEquals(0, f.memory.writes); assertTrue(f.memory.turns.isEmpty())
    }
    @Test fun `consent persists at most six turns and revocation deletes them`() = runTest {
        val f = Fixture(this, Preferences(remember = true))
        repeat(5) { f.utter() }; assertEquals(6, f.memory.turns.size)
        f.c.configure(Preferences(remember = false)); assertTrue(f.memory.turns.isEmpty()); f.c.end()
    }
    @Test fun `fatigue never reaches model and proposes safe stopping`() = runTest {
        val f = Fixture(this); f.utter("졸려서 눈이 감겨")
        assertEquals(Phase.REST, f.c.state.value.phase); assertEquals(0, f.backend.calls)
        assertTrue(f.voice.spoken.single().contains("정차"))
    }
    @Test fun `rest timer requires consent and does not interrupt explicit quiet`() = runTest {
        val f = Fixture(this); f.utter(); advanceTimeBy(121 * 60_000); f.c.tick(); runCurrent()
        assertEquals(Phase.WAITING, f.c.state.value.phase)
        f.c.configure(Preferences(restReminder = true)); f.c.quiet(); f.c.tick(); runCurrent()
        assertEquals(Phase.QUIET, f.c.state.value.phase)
        f.utter(); f.c.tick(); runCurrent(); assertEquals(Phase.REST, f.c.state.value.phase)
    }
    @Test fun `briefing preserves source timestamp and stale status`() = runTest {
        val f = Fixture(this)
        val a = Article("a", "제목", "요약", "출처", "https://example.org/news", "2020-01-01T00:00:00Z", true, "headline")
        f.backend.news = { Briefing(it, "2026-11-16T00:00:00Z", listOf(a, a.copy(id = "b"))) }
        f.c.start(); runCurrent(); f.c.loadBriefing(); runCurrent(); assertEquals(a, f.c.state.value.briefing!!.articles[0])
        f.c.playBriefing(); runCurrent(); assertTrue(f.voice.spoken.last().startsWith("지난 소식"))
        f.c.nextArticle(); assertEquals(1, f.c.state.value.articleIndex); f.c.end()
    }
    @Test fun `empty briefing cannot start speech`() = runTest {
        val f = Fixture(this); f.c.start(); runCurrent(); f.c.loadBriefing(); runCurrent(); f.c.playBriefing(); runCurrent()
        assertTrue(f.voice.spoken.isEmpty()); assertTrue(f.c.state.value.message.contains("없어요")); f.c.end()
    }
    @Test fun `demo input is rejected in live mode`() = runTest {
        val f = Fixture(this); f.c.start(); runCurrent(); f.c.demoInput("가짜 입력"); runCurrent()
        assertEquals(0, f.backend.calls); f.c.end()
    }
    @Test fun `proactive opt in never interrupts quiet or mute`() = runTest {
        val f = Fixture(this, Preferences(proactive = true)); f.utter(); val n = f.voice.spoken.size
        f.c.quiet(); advanceTimeBy(21 * 60_000); f.c.tick(); runCurrent(); assertEquals(n, f.voice.spoken.size)
        f.c.mute(); advanceTimeBy(21 * 60_000); f.c.tick(); runCurrent(); assertEquals(n, f.voice.spoken.size)
    }
}
