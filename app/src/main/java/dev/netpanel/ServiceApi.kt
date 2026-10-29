package dev.netpanel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class Health(val label: String) { UNKNOWN("未配置"), OK("正常"), PARTIAL("部分不可用"), UNREACHABLE("无法连接"), AUTH("认证失败"), UNSUPPORTED("接口不兼容") }
data class ServiceState(
    val health: Health = Health.UNKNOWN, val version: String? = null,
    val uptimeMs: Long? = null, val cpu: Double? = null, val memoryMb: Double? = null,
    val mode: String? = null, val node: String? = null, val connections: Int? = null,
    val delayMs: Int? = null, val queries: Long? = null, val queryErrors: Long? = null,
    val queryWindowSeconds: Long? = null, val recentQueries: Long? = null, val recentErrors: Long? = null,
    val uploadTotal: Long? = null, val downloadTotal: Long? = null
)
data class TrafficPoint(val time: Long, val up: Long, val down: Long, val upTotal: Long? = null, val downTotal: Long? = null)
class ApiFailure(val status: Int) : IOException("HTTP $status")

class ServiceApi(val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(5, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).pingInterval(20, TimeUnit.SECONDS)
    .followRedirects(false).followSslRedirects(false).build()) {
    fun request(base: String, path: String, auth: String): Request = Request.Builder()
        .url(base.trimEnd('/').toHttpUrl().newBuilder().addPathSegments(path.trimStart('/')).build())
        .apply { if (auth.isNotEmpty()) header("Authorization", auth) }.build()

    suspend fun get(base: String, path: String, auth: String): String {
        val call = client.newCall(request(base, path, auth))
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (cont.isActive) cont.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val body = response.use {
                            if (!it.isSuccessful) throw ApiFailure(it.code)
                            // Guard unexpectedly large management responses.
                            val source = it.body?.source() ?: throw IOException("Empty response")
                            if (source.request(4L * 1024 * 1024 + 1)) throw IOException("Response too large")
                            source.readUtf8()
                        }
                        if (cont.isActive) cont.resume(body)
                    } catch (e: Exception) { if (cont.isActive) cont.resumeWithException(e) }
                }
            })
        }
    }

    suspend fun dns(s: Settings): ServiceState = withContext(Dispatchers.Default) {
        if (s.dnsApi.isBlank()) return@withContext ServiceState()
        val auth = dnsAuth(s)
        val health = JSONObject(get(s.dnsApi, "api/health", auth))
        val system = optionalJson(s.dnsApi, "api/system", auth)
        val metrics = try { get(s.dnsApi, "api/metrics", auth) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
        val ready = health.optString("status") == "ok" && health.optJSONObject("checks")?.optString("server_startup") != "not_ready"
        ServiceState(
            health = if (ready) Health.OK else Health.PARTIAL,
            version = health.stringOrNull("version") ?: system?.stringOrNull("version"),
            uptimeMs = health.longOrNull("uptime_ms") ?: system?.longOrNull("uptime_ms"),
            cpu = system?.doubleOrNull("process_cpu_percent"), memoryMb = system?.doubleOrNull("process_memory_mb"),
            queries = metrics?.let { metricTotal(it, "query_total") }, queryErrors = metrics?.let { metricTotal(it, "query_error_total") }
        )
    }

    suspend fun proxy(s: Settings): ServiceState = withContext(Dispatchers.Default) {
        if (s.controller.isBlank()) return@withContext ServiceState()
        val auth = proxyAuth(s)
        val version = JSONObject(get(s.controller, "version", auth)).stringOrNull("version")
        val config = optionalJson(s.controller, "configs", auth)
        val snapshot = optionalJson(s.controller, "connections", auth)
        val connections = snapshot?.optJSONArray("connections")?.length()
        val proxies = if (s.proxyGroup.isNotBlank()) optionalJson(s.controller, "proxies", auth)?.optJSONObject("proxies") else null
        val node = proxies?.optJSONObject(s.proxyGroup)?.stringOrNull("now")
        val history = node?.let { proxies?.optJSONObject(it)?.optJSONArray("history") }
        val delay = history?.takeIf { it.length() > 0 }?.optJSONObject(history.length() - 1)?.optInt("delay")?.takeIf { it > 0 }
        ServiceState(if (config != null && connections != null) Health.OK else Health.PARTIAL,
            version = version, mode = config?.stringOrNull("mode"), node = node, connections = connections, delayMs = delay,
            memoryMb = snapshot?.longOrNull("memory")?.takeIf { it > 0 }?.div(1048576.0),
            uploadTotal = snapshot?.longOrNull("uploadTotal"), downloadTotal = snapshot?.longOrNull("downloadTotal"))
    }

    private suspend fun optionalJson(base: String, path: String, auth: String): JSONObject? = try {
        JSONObject(get(base, path, auth))
    } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }

    fun stream(s: Settings, point: (TrafficPoint) -> Unit, failure: () -> Unit): WebSocket =
        client.newWebSocket(request(s.controller, "traffic", proxyAuth(s)), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val j = JSONObject(text)
                    val up = j.longOrNull("up") ?: throw IOException()
                    val down = j.longOrNull("down") ?: throw IOException()
                    if (up < 0 || down < 0) throw IOException()
                    point(TrafficPoint(System.currentTimeMillis(), up, down, j.longOrNull("upTotal"), j.longOrNull("downTotal")))
                } catch (_: Exception) { webSocket.cancel(); failure() }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { failure() }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { failure() }
        })
    companion object {
        fun dnsAuth(s: Settings) = if (s.dnsUser.isNotEmpty()) Credentials.basic(s.dnsUser, s.dnsPassword, Charsets.UTF_8) else ""
        fun proxyAuth(s: Settings) = if (s.secret.isNotEmpty()) "Bearer ${s.secret}" else ""
        fun failure(e: Exception): Health = when {
            e is ApiFailure && e.status in listOf(401, 403) -> Health.AUTH
            e is ApiFailure && e.status in listOf(404, 405, 501) -> Health.UNSUPPORTED
            e is ApiFailure -> Health.PARTIAL
            e is org.json.JSONException -> Health.UNSUPPORTED
            else -> Health.UNREACHABLE
        }
    }
}

internal fun metricTotal(raw: String, name: String): Long? {
    val pattern = Regex("^" + Regex.escape(name) + "(?:\\{[^}]*\\})?\\s+([0-9.eE+\\-]+)(?:\\s+.*)?$")
    val values = raw.lineSequence().mapNotNull { line -> pattern.matchEntire(line.trim())?.groupValues?.get(1)?.toDoubleOrNull() }
        .filter { it.isFinite() && it >= 0 }.toList()
    return if (values.isEmpty()) null else values.sum().toLong()
}

internal fun isLoopback(base: String): Boolean {
    val host = base.toHttpUrlOrNull()?.host ?: return false
    return host == "localhost" || host == "::1" ||
        (host.startsWith("127.") && host.split('.').size == 4 && host.split('.').all { part -> part.toIntOrNull()?.let { it in 0..255 } == true })
}

fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
fun JSONObject.longOrNull(key: String): Long? = if (!has(key) || isNull(key)) null else optLong(key, -1).takeIf { it >= 0 }
fun JSONObject.doubleOrNull(key: String): Double? = if (!has(key) || isNull(key)) null else optDouble(key).takeIf { it.isFinite() && it >= 0 }
