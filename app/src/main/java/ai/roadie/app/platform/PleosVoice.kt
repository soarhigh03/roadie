package ai.roadie.app.platform

import android.content.Context
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.tts.TextToSpeech
import ai.roadie.core.RoadieFailure
import ai.roadie.core.VoicePort
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Pleos Speech Service only. No Android SpeechRecognizer or direct AudioRecord fallback. */
class PleosVoice(private val context: Context) : VoicePort {
    private val main = Handler(Looper.getMainLooper())
    private var cleanup: (() -> Unit)? = null

    private fun failure(code: String) = RoadieFailure(when (code) {
        "PERMISSION_DENIED" -> "차량 음성 서비스 권한을 허용해주세요. 정차 후 앱 권한을 확인할 수 있어요."
        "MIC_ERROR" -> "마이크를 사용할 수 없어요. 차량 마이크와 개인정보 보호 설정을 확인해주세요."
        "NOT_SUPPORTED_API", "ON_DEVICE_ENGINE_ERROR" -> "이 환경의 Pleos 기기 내 음성 엔진을 사용할 수 없어요. SDK 지원 상태를 확인해주세요."
        "UNAVAILABLE_LANGUAGE" -> "이 차량에서 한국어 음성을 지원하는지 확인해주세요."
        "UNAUTHENTICATED" -> "Pleos 음성 서비스 이용 승인 상태를 확인해주세요."
        else -> "음성 연결을 마치지 못했어요. 잠시 후 다시 시도해주세요."
    }, permission = code == "PERMISSION_DENIED" || code == "MIC_ERROR")

    override suspend fun listen(onPartial: (String) -> Unit): String {
        stop()
        val sdk = SpeechToText(context, ai.pleos.playground.stt.constant.Mode.ON_DEVICE)
        var closed = false
        var listener: ai.pleos.playground.stt.listener.ResultListener? = null
        val close = {
            if (!closed) {
                closed = true
                listener?.let { runCatching { sdk.removeListener(it) } }
                runCatching { sdk.stop() }
                runCatching { sdk.release() }
            }
        }
        cleanup = close
        try {
            return suspendCancellableCoroutine { continuation ->
                fun fail(code: String) { if (!closed && continuation.isActive) continuation.resumeWithException(failure(code)) }
                listener = object : ai.pleos.playground.stt.listener.ResultListener {
                    override fun onReady() = Unit
                    override fun onStartedRecognition() = Unit
                    override fun onUpdated(stt: String, completed: Boolean) { main.post {
                        if (!closed && continuation.isActive) {
                            if (completed) continuation.resume(stt) else onPartial(stt)
                        }
                    } }
                    override fun onUpdatedEpdData(on: Long, off: Long) = Unit
                    override fun onUpdatedRms(rms: Double) = Unit
                    override fun onEndedRecognition() { main.post {
                        if (!closed && continuation.isActive) continuation.resumeWithException(RoadieFailure("잘 듣지 못했어요. 다시 말해주세요."))
                    } }
                    override fun onError(errorType: ai.pleos.playground.stt.constant.ErrorType) { main.post { fail(errorType.name) } }
                }
                continuation.invokeOnCancellation { main.post { close() } }
                try {
                    sdk.initialize(object : ai.pleos.playground.stt.listener.OnInitializeListener {
                        override fun onSuccess() { main.post {
                            if (!closed && continuation.isActive) try {
                                sdk.addListener(listener!!)
                                sdk.request()
                            } catch (_: SecurityException) { fail("PERMISSION_DENIED") }
                            catch (_: Exception) { fail("UNKNOWN") }
                        } }
                        override fun onFailure(errorType: ai.pleos.playground.stt.constant.ErrorType) { main.post { fail(errorType.name) } }
                    })
                } catch (_: SecurityException) { fail("PERMISSION_DENIED") }
                catch (_: Exception) { fail("UNKNOWN") }
            }
        } finally { close(); if (cleanup === close) cleanup = null }
    }

    override suspend fun speak(text: String) {
        stop()
        val sdk = TextToSpeech(context, ai.pleos.playground.tts.constant.Mode.ON_DEVICE)
        var closed = false
        var listener: ai.pleos.playground.tts.listener.EventListener? = null
        val close = {
            if (!closed) {
                closed = true
                listener?.let { runCatching { sdk.removeEventListener(it) } }
                runCatching { sdk.stop() }; runCatching { sdk.release() }
            }
        }
        cleanup = close
        try {
            suspendCancellableCoroutine<Unit> { continuation ->
                fun fail(code: String) { if (!closed && continuation.isActive) continuation.resumeWithException(failure(code)) }
                listener = object : ai.pleos.playground.tts.listener.EventListener {
                    override fun onReady() = Unit
                    override fun onStart() = Unit
                    override fun onUpdatedRms(rms: Double) = Unit
                    override fun onDone() { main.post { if (!closed && continuation.isActive) continuation.resume(Unit) } }
                    override fun onStop() { main.post { if (!closed && continuation.isActive) continuation.resumeWithException(RoadieFailure("음성 재생이 중단됐어요.")) } }
                    override fun onError(errorType: ai.pleos.playground.tts.constant.ErrorType) { main.post { fail(errorType.name) } }
                }
                continuation.invokeOnCancellation { main.post { close() } }
                try {
                    sdk.initialize(object : ai.pleos.playground.tts.listener.OnInitializeListener {
                        override fun onSuccess() { main.post {
                            if (!closed && continuation.isActive) try {
                                sdk.addEventListener(listener!!); sdk.speak(text)
                            } catch (_: SecurityException) { fail("PERMISSION_DENIED") }
                            catch (_: Exception) { fail("UNKNOWN") }
                        } }
                        override fun onFailure(errorType: ai.pleos.playground.tts.constant.ErrorType) { main.post { fail(errorType.name) } }
                    })
                } catch (_: SecurityException) { fail("PERMISSION_DENIED") }
                catch (_: Exception) { fail("UNKNOWN") }
            }
        } finally { close(); if (cleanup === close) cleanup = null }
    }

    override fun stop() { cleanup?.invoke(); cleanup = null }
}
