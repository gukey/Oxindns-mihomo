package dev.netpanel

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.WebSocket

data class ObservedEvent(val time: Long, val text: String)
data class PanelState(
    val settings: Settings? = null, val storageError: Boolean = false,
    val dns: ServiceState = ServiceState(), val proxy: ServiceState = ServiceState(),
    val traffic: List<TrafficPoint> = emptyList(), val trafficLive: Boolean = false,
    val online: Boolean = true, val refreshing: Boolean = false, val updatedAt: Long? = null,
    val notice: String? = null, val events: List<ObservedEvent> = emptyList(), val detecting: Boolean = false
)

class PanelViewModel(app: Application) : AndroidViewModel(app) {
    // In-memory only: preserve an unfinished settings form across Activity recreation.
    var settingsDraft: Settings? = null
    private val store = SettingsStore(app)
    private val api = ServiceApi()
    private val mutable = MutableStateFlow(PanelState())
    val state = mutable.asStateFlow()
    private var active = false
    private var overview = false
    private var polling: Job? = null
    private var socket: WebSocket? = null
    @Volatile private var streamGeneration = 0
    @Volatile private var streamFailed = false
    private val connectivity = app.getSystemService(ConnectivityManager::class.java)
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = updateNetwork()
        override fun onLost(network: Network) = updateNetwork()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = updateNetwork()
    }
    init {
        connectivity.registerDefaultNetworkCallback(networkCallback)
        updateNetwork()
        viewModelScope.launch {
            try {
                store.settings.collect { settings ->
                mutable.update { it.copy(settings = settings, storageError = false, dns = ServiceState(), proxy = ServiceState(), traffic = emptyList(), updatedAt = null, events = emptyList()) }
                    if (!settings.localSetupChecked) {
                        val emptyProfile = settings.dnsApi.isBlank() && settings.controller.isBlank() && settings.proxyWeb.isBlank() && settings.dnsWeb == Settings().dnsWeb
                        mutable.update { it.copy(detecting = emptyProfile) }
                        val configured = if (emptyProfile) LocalServices(api).detect(settings) else settings.copy(localSetupChecked = true)
                        withContext(Dispatchers.IO) { store.save(configured) }
                        mutable.update { it.copy(detecting = false) }
                        return@collect
                    }
                    restart()
                }
            } catch (e: CancellationException) { throw e } catch (_: Exception) {
                mutable.update { it.copy(storageError = true) }
            }
        }
    }
    private fun updateNetwork() {
        viewModelScope.launch {
            // A LAN without validated Internet is enough for local management.
            val connected = connectivity.activeNetwork != null
            if (connected != mutable.value.online) {
                mutable.update {
                    val s = it.settings
                    it.copy(online = connected,
                        dns = if (!connected && !s?.dnsApi.isNullOrBlank() && !isLoopback(s!!.dnsApi)) ServiceState(Health.UNREACHABLE) else it.dns,
                        proxy = if (!connected && !s?.controller.isNullOrBlank() && !isLoopback(s!!.controller)) ServiceState(Health.UNREACHABLE) else it.proxy)
                }
                restart()
            }
        }
    }
    fun foreground(value: Boolean) { if (active != value) { active = value; restart() } }
    fun overview(value: Boolean) { if (overview != value) { overview = value; restart() } }
    fun refresh() { restart() }
    private fun stopStream() {
        streamGeneration++
        socket?.cancel(); socket = null
        mutable.update { it.copy(trafficLive = false) }
    }
    private fun restart() {
        polling?.cancel(); stopStream()
        mutable.update { it.copy(refreshing = false) }
        val s = mutable.value.settings ?: return
        if (s.dnsApi.isBlank() && s.controller.isBlank()) return
        val dnsReachable = mutable.value.online || isLoopback(s.dnsApi)
        val proxyReachable = mutable.value.online || isLoopback(s.controller)
        if (!active || !overview || (!dnsReachable && !proxyReachable)) return
        polling = viewModelScope.launch {
            var backoff = s.refreshSeconds.toLong()
            var previousDns: ServiceState? = null
            var previousTime: Long? = null
            while (isActive) {
                if (socket == null || streamFailed) {
                    stopStream(); streamFailed = false
                    if (s.controller.isNotBlank() && proxyReachable) {
                        val generation = streamGeneration
                        socket = api.stream(s, { point ->
                            viewModelScope.launch {
                                if (generation == streamGeneration) mutable.update { it.copy(
                                    traffic = (it.traffic + point).filter { p -> point.time - p.time <= 60_000 }.takeLast(120), trafficLive = true) }
                            }
                        }, {
                            viewModelScope.launch {
                                if (generation == streamGeneration) { streamFailed = true; mutable.update { it.copy(trafficLive = false) } }
                            }
                        })
                    }
                }
                mutable.update { it.copy(refreshing = true) }
                val result = coroutineScope {
                    val dns = async { if (!dnsReachable && s.dnsApi.isNotBlank()) ServiceState(Health.UNREACHABLE) else safe("DNS") { api.dns(s) } }
                    val proxy = async { if (!proxyReachable && s.controller.isNotBlank()) ServiceState(Health.UNREACHABLE) else safe("Mihomo") { api.proxy(s) } }
                    dns.await() to proxy.await()
                }
                val now = System.currentTimeMillis()
                val previous = previousDns
                val window = previousTime?.let { ((now - it) / 1000).coerceAtLeast(1) }
                fun difference(current: Long?, old: Long?): Long? = if (current != null && old != null && current >= old) current - old else null
                val dns = result.first.copy(queryWindowSeconds = window,
                    recentQueries = difference(result.first.queries, previous?.queries),
                    recentErrors = difference(result.first.queryErrors, previous?.queryErrors))
                previousDns = result.first; previousTime = now
                mutable.update { old ->
                    val observed = mutableListOf<ObservedEvent>()
                    if (old.updatedAt != null) {
                        if (old.dns.health != dns.health && dns.health != Health.UNKNOWN) observed += ObservedEvent(now, "OxiDNS：${dns.health.label}")
                        if (old.proxy.health != result.second.health && result.second.health != Health.UNKNOWN) observed += ObservedEvent(now, "Mihomo：${result.second.health.label}")
                        if (old.proxy.node != null && result.second.node != null && old.proxy.node != result.second.node)
                            observed += ObservedEvent(now, "代理组选择变更：${result.second.node}")
                    }
                    old.copy(dns = dns, proxy = result.second, refreshing = false, updatedAt = now, events = (observed + old.events).take(12))
                }
                val failed = listOfNotNull(result.first.health.takeIf { dnsReachable }, result.second.health.takeIf { proxyReachable })
                    .any { it !in listOf(Health.OK, Health.UNKNOWN) }
                backoff = if (failed) (backoff * 2).coerceAtMost(60) else s.refreshSeconds.toLong()
                delay(backoff * 1000)
            }
        }
    }
    private suspend fun safe(service: String, block: suspend () -> ServiceState): ServiceState = try { block() }
        catch (e: CancellationException) { throw e } catch (e: Exception) {
            // Debug diagnostics include types/status only, never URLs, messages, headers or credentials.
            if (BuildConfig.DEBUG) android.util.Log.w("NetPanelApi", "$service ${e.javaClass.simpleName} status=${(e as? ApiFailure)?.status ?: 0} cause=${e.cause?.javaClass?.simpleName ?: "none"}")
            ServiceState(health = ServiceApi.failure(e))
        }
    suspend fun save(s: Settings): Boolean {
        s.validate()?.let { message -> mutable.update { it.copy(notice = message) }; return false }
        return try { withContext(Dispatchers.IO) { store.save(s) }; true }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { mutable.update { it.copy(notice = "设置保存失败，请检查设备安全存储") }; false }
    }
    suspend fun test(s: Settings, dns: Boolean): String {
        s.validate()?.let { return it }
        val web = if (dns) s.dnsWeb else s.proxyWeb
        val apiBase = if (dns) s.dnsApi else s.controller
        val webResult = if (web.isBlank()) "Web：未配置" else try {
            api.get(web, "", if (dns) ServiceApi.dnsAuth(s) else "")
            "Web：可访问"
        } catch (e: CancellationException) { throw e } catch (e: ApiFailure) {
            if (e.status in 300..399) "Web：需要跳转，请打开页面检查" else "Web：HTTP ${e.status}"
        } catch (_: Exception) { "Web：无法连接" }
        if (apiBase.isBlank()) return "$webResult\nAPI：未配置"
        val apiResult = try {
            val result = if (dns) api.dns(s) else api.proxy(s)
            "API：${result.health.label} · 版本 ${result.version ?: "未提供"}"
        } catch (e: CancellationException) { throw e } catch (e: Exception) { "API：${ServiceApi.failure(e).label}" }
        return "$webResult\n$apiResult\n网页内与控制器的连接需在后台中确认。"
    }
    fun dismissNotice() { mutable.update { it.copy(notice = null) } }
    suspend fun detectLocal(s: Settings): Settings = LocalServices(api).detect(s)
    fun clearBrokenSettings() { viewModelScope.launch {
        try {
            store.clear()
            store.settings.collect { settings -> mutable.update { it.copy(settings = settings, storageError = false) }; restart() }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { mutable.update { it.copy(storageError = true, settings = null) } }
    } }
    override fun onCleared() {
        polling?.cancel(); stopStream(); connectivity.unregisterNetworkCallback(networkCallback)
        api.client.dispatcher.executorService.shutdown()
    }
}
