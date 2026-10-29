package dev.netpanel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject

/** Candidates observed on the target phone, verified before use; manual ports remain supported. */
class LocalServices(private val api: ServiceApi,
    private val dnsCandidate: String = "http://127.0.0.1:9199",
    private val proxyCandidate: String = "http://127.0.0.1:9090") {
    suspend fun detect(s: Settings): Settings = coroutineScope {
        val dns = async {
            if (s.dnsApi.isNotBlank()) null else optional {
                val base = dnsCandidate
                val health = JSONObject(api.get(base, "api/health", ""))
                if (health.has("checks") && health.has("version") && health.has("uptime_ms")) base else null
            }
        }
        val proxy = async {
            if (s.controller.isNotBlank()) null else optional {
                val base = proxyCandidate
                val version = JSONObject(api.get(base, "version", ""))
                if (version.optBoolean("meta") && version.has("version")) base else null
            }
        }
        val dnsBase = dns.await()
        val proxyBase = proxy.await()
        var result = s.copy(
            dnsApi = dnsBase ?: s.dnsApi,
            dnsWeb = if (dnsBase != null && (s.dnsWeb.isBlank() || s.dnsWeb == Settings().dnsWeb)) dnsBase else s.dnsWeb,
            controller = proxyBase ?: s.controller,
            localSetupChecked = true
        )
        if (result.controller.isNotBlank()) {
            if (result.proxyWeb.isBlank()) {
                val page = optional { api.get(result.controller, "ui/", ServiceApi.proxyAuth(result)) }
                if (page?.contains("MetaCubeXD", ignoreCase = true) == true)
                    result = result.copy(proxyWeb = result.controller.trimEnd('/') + "/ui/")
            }
            if (result.proxyGroup.isBlank()) {
                val proxies = optional { JSONObject(api.get(result.controller, "proxies", ServiceApi.proxyAuth(result))).optJSONObject("proxies") }
                val groups = proxies?.keys()?.asSequence()?.filter { name ->
                    name.contains("节点选择") && proxies.optJSONObject(name)?.has("now") == true
                }?.toList().orEmpty()
                if (groups.size == 1) result = result.copy(proxyGroup = groups.single())
            }
        }
        result
    }
    private suspend fun <T> optional(block: suspend () -> T): T? = try { block() }
        catch (e: CancellationException) { throw e } catch (_: Exception) { null }
}
