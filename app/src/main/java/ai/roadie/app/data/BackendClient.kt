package ai.roadie.app.data

import ai.roadie.app.BuildConfig
import ai.roadie.core.*
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class BackendClient(private val vault: PrivateVault) : BackendPort {
    private val http = OkHttpClient.Builder().callTimeout(24, TimeUnit.SECONDS).connectTimeout(8, TimeUnit.SECONDS).followRedirects(false).build()
    companion object {
        fun validEndpoint(value: String): Boolean = runCatching {
            val uri = URI(value)
            uri.userInfo == null && uri.query == null && uri.fragment == null && !uri.host.isNullOrBlank() &&
                (uri.scheme == "https" || BuildConfig.DEBUG && uri.scheme == "http" && uri.host in setOf("10.0.2.2", "127.0.0.1", "localhost"))
        }.getOrDefault(false)
    }
    private suspend fun request(path: String, body: JSONObject? = null): JSONObject {
        val endpoint = vault.endpoint.trimEnd('/')
        if (!validEndpoint(endpoint) || vault.accessToken.isBlank()) throw RoadieFailure("정차 후 대화 설정에서 서버 주소와 접근 토큰을 연결해주세요.", false)
        val request = Request.Builder().url(endpoint + path).header("Authorization", "Bearer ${vault.accessToken}")
        if (body != null) request.post(body.toString().toRequestBody("application/json".toMediaType()))
        val response = suspendCancellableCoroutine<String> { cont ->
            val call = http.newCall(request.build())
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (cont.isActive) cont.resumeWithException(RoadieFailure("연결을 확인해주세요. 응답이 늦으면 다시 시도할 수 있어요.")) }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            if (!it.isSuccessful) {
                                val msg = when (it.code) {
                                    401, 403 -> "서버 접근 토큰을 확인해주세요."
                                    429 -> "요청이 많아요. 잠시 쉬었다가 다시 연결해주세요."
                                    503 -> "서버의 AI 또는 데이터 제공자 설정이 필요해요."
                                    else -> "서버에 연결하지 못했어요. 잠시 후 다시 시도해주세요."
                                }
                                throw RoadieFailure(msg, it.code !in setOf(401, 403, 503))
                            }
                            val stream = it.body?.byteStream() ?: throw IOException()
                            val buffer = java.io.ByteArrayOutputStream()
                            val chunk = ByteArray(4096)
                            while (buffer.size() <= 131072) { val n = stream.read(chunk); if (n < 0) break; buffer.write(chunk, 0, n) }
                            val bytes = buffer.toByteArray()
                            if (bytes.size > 131072) throw IOException()
                            if (cont.isActive) cont.resume(String(bytes, Charsets.UTF_8))
                        } catch (e: Exception) { if (cont.isActive) cont.resumeWithException(e) }
                    }
                }
            })
        }
        return JSONObject(response)
    }
    override suspend fun reply(text: String, history: List<Turn>): String {
        val body = JSONObject().put("message", text).put("history", JSONArray().apply {
            history.takeLast(6).forEach { put(JSONObject().put("role", it.role).put("text", it.text)) }
        })
        return request("/v1/chat", body).getString("reply")
    }
    override suspend fun briefing(topic: Topic): Briefing {
        val result = request("/v1/briefings?topic=${topic.name.lowercase()}")
        val array = result.getJSONArray("articles")
        return Briefing(topic, result.getString("fetchedAt"), (0 until array.length()).map {
            val a = array.getJSONObject(it)
            Article(a.getString("id"), a.getString("title"), a.getString("summary"), a.getString("source"),
                a.getString("url"), a.getString("publishedAt"), a.getBoolean("stale"), a.getString("summaryKind"))
        })
    }
    override suspend fun places(coordinates: Coordinates): List<Place> {
        val items = request("/v1/rest-places", JSONObject().put("latitude", coordinates.latitude).put("longitude", coordinates.longitude)).getJSONArray("places")
        return (0 until items.length()).map { val p = items.getJSONObject(it)
            Place(p.getString("id"), p.getString("name"), p.getString("address"), p.getDouble("latitude"), p.getDouble("longitude"), p.getInt("distanceMeters")) }
    }
}
