package ai.roadie.app.platform

import android.content.Context
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.RouteInfo
import ai.roadie.core.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class RestNavigation(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var cleanup: (() -> Unit)? = null
    suspend fun locate(): Coordinates {
        close()
        val sdk = FusedLocation(context)
        if (!sdk.isSupported()) throw RoadieFailure("이 환경은 Pleos 위치 서비스를 지원하지 않아요.", false)
        var closed = false
        var listener: FusedLocationListener? = null
        val release = {
            if (!closed) {
                closed = true
                listener?.let { runCatching { sdk.unregisterFusedLocationCallback(it) } }
                runCatching { sdk.release() }
            }
        }
        cleanup = release
        try {
            return suspendCancellableCoroutine { cont ->
                listener = object : FusedLocationListener {
                    override fun onChanged(location: LocationData?) { main.post {
                        if (closed || !cont.isActive || location == null) return@post
                        val age = System.currentTimeMillis() - location.timestampMillis
                        if (age !in -60_000..120_000 || !location.latitude.isFinite() || !location.longitude.isFinite() || location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) return@post
                        cont.resume(Coordinates(location.latitude, location.longitude))
                    } }
                    override fun onError(exception: Exception) { main.post {
                        if (!closed && cont.isActive) cont.resumeWithException(RoadieFailure("위치를 받을 수 없어요. 위치 권한과 차량 개인정보 보호 설정을 확인해주세요."))
                    } }
                }
                cont.invokeOnCancellation { main.post { release() } }
                try {
                    sdk.initialize()
                    sdk.registerFusedLocationCallback(listener!!)
                    sdk.getLastKnownLocation(listener!!)
                } catch (_: SecurityException) {
                    if (cont.isActive) cont.resumeWithException(RoadieFailure("위치 권한을 허용해주세요.", permission = true))
                } catch (_: Exception) {
                    if (cont.isActive) cont.resumeWithException(RoadieFailure("Pleos 위치 서비스에 연결하지 못했어요."))
                }
            }
        } finally { release(); if (cleanup === release) cleanup = null }
    }

    suspend fun navigate(place: Place) {
        close()
        val sdk = NaviHelper(context)
        var closed = false
        var listener: NaviHelperEventListener? = null
        val release = {
            if (!closed) {
                closed = true
                listener?.let { runCatching { sdk.removeListener(it) } }
                runCatching { sdk.release() }
            }
        }
        cleanup = release
        try {
            suspendCancellableCoroutine<Unit> { cont ->
                fun fail() { if (!closed && cont.isActive) cont.resumeWithException(RoadieFailure("차량 내비게이션에 연결하지 못했어요. 내비 지원 여부와 경로를 확인해주세요.")) }
                listener = object : NaviHelperEventListener {
                    override fun onRoutingCompleted() { main.post { if (!closed && cont.isActive) cont.resume(Unit) } }
                    override fun onError(errorCode: NaviErrorCode) { main.post { fail() } }
                }
                cont.invokeOnCancellation { main.post { release() } }
                try {
                    sdk.initialize(onConnected = { main.post {
                        if (!closed && cont.isActive) try {
                            sdk.addListener(listener!!)
                            sdk.requestRoute(RouteInfo(latitude = place.latitude, longitude = place.longitude,
                                displayLatitude = null, displayLongitude = null, poiName = place.name,
                                poiId = place.id, poiSubId = "0", address = place.address, poiProvider = "Kakao"))
                        } catch (_: Exception) { fail() }
                    } }, onFailed = { main.post { fail() } })
                } catch (_: Exception) { fail() }
            }
        } finally { release(); if (cleanup === release) cleanup = null }
    }
    fun close() { cleanup?.invoke(); cleanup = null }
}
