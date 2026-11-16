package ai.roadie.core

enum class Phase { READY, LISTENING, THINKING, SPEAKING, WAITING, QUIET, MUTED, PAUSED, REST, BRIEFING, ERROR }
enum class Theme { DAY, NIGHT }
enum class Topic(val label: String) { TECH("AI와 테크"), SCIENCE("과학과 우주"), CULTURE("문화와 일상") }
data class Preferences(
    val theme: Theme = Theme.DAY,
    val interests: Set<Topic> = setOf(Topic.TECH, Topic.SCIENCE),
    val proactive: Boolean = false,
    val remember: Boolean = false,
    val restReminder: Boolean = false,
    val resumeListening: Boolean = false,
    val onboarded: Boolean = false,
    val mode: Mode = Mode.LIVE,
)
data class Turn(val role: String, val text: String)
data class Article(
    val id: String, val title: String, val summary: String, val source: String,
    val url: String, val publishedAt: String, val stale: Boolean, val summaryKind: String,
)
data class Briefing(val topic: Topic, val fetchedAt: String, val articles: List<Article>)
data class Place(val id: String, val name: String, val address: String, val latitude: Double, val longitude: Double, val distanceMeters: Int)
data class Coordinates(val latitude: Double, val longitude: Double)
data class SessionState(
    val phase: Phase = Phase.READY,
    val active: Boolean = false,
    val message: String = "대화도, 음악 사이의 조용함도.\n오늘의 속도에 맞춰 함께할게요.",
    val partial: String = "",
    val briefing: Briefing? = null,
    val articleIndex: Int = 0,
    val retryable: Boolean = false,
    val permissionDenied: Boolean = false,
    val elapsedMinutes: Long = 0,
)
class RoadieFailure(val userMessage: String, val canRetry: Boolean = true, val permission: Boolean = false) : Exception(userMessage)

interface VoicePort {
    suspend fun listen(onPartial: (String) -> Unit): String
    suspend fun speak(text: String)
    fun stop()
}
interface BackendPort {
    suspend fun reply(text: String, history: List<Turn>): String
    suspend fun briefing(topic: Topic): Briefing
    suspend fun places(coordinates: Coordinates): List<Place>
}
interface FocusPort {
    fun acquire(): Boolean
    fun release()
}
interface MemoryPort {
    fun load(): List<Turn>
    fun save(turns: List<Turn>)
    fun clear()
}
