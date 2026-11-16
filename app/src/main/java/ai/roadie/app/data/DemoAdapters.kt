package ai.roadie.app.data

import ai.roadie.core.*
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

/** Deliberately no microphone, AI request, location, or fabricated news in demo mode. */
class DemoVoice : VoicePort {
    override suspend fun listen(onPartial: (String) -> Unit): String = awaitCancellation()
    override suspend fun speak(text: String) { delay(1800) }
    override fun stop() = Unit
}
class DemoBackend : BackendPort {
    override suspend fun reply(text: String, history: List<Turn>): String { delay(800); return "데모 응답이에요. 오늘 길에서 눈에 들어온 풍경이 있나요?" }
    override suspend fun briefing(topic: Topic): Briefing {
        delay(500)
        return Briefing(topic, "2026-11-16T00:00:00Z", listOf(Article("demo", "브리핑 조작 예시", "이것은 조작을 살펴보는 데모 문장입니다. 실제 뉴스가 아니에요.", "로디 데모", "", "2026-11-16T00:00:00Z", true, "demo")))
    }
    override suspend fun places(coordinates: Coordinates): List<Place> = emptyList()
}
