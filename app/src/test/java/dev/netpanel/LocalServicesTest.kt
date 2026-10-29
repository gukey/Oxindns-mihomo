package dev.netpanel

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test

class LocalServicesTest {
    @Test fun `verified local services fill empty profile and identify unique selection group`() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = MockResponse().setBody(when (request.path) {
                    "/api/health" -> """{"checks":{},"version":"1.6.1","uptime_ms":42}"""
                    "/version" -> """{"meta":true,"version":"v1.19.33"}"""
                    "/ui/" -> "<html><title>MetaCubeXD</title></html>"
                    "/proxies" -> """{"proxies":{"🚀 节点选择":{"now":"Tokyo"}}}"""
                    else -> "{}"
                })
            }
            val base = server.url("/").toString().trimEnd('/')
            val result = LocalServices(ServiceApi(), base, base).detect(Settings())
            assertEquals(base, result.dnsApi); assertEquals(base, result.dnsWeb)
            assertEquals(base, result.controller); assertEquals("$base/ui/", result.proxyWeb)
            assertEquals("🚀 节点选择", result.proxyGroup); assertTrue(result.localSetupChecked)
        }
    }
    @Test fun `unrelated HTTP services are not adopted as DNS or proxy`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{}")); server.enqueue(MockResponse().setBody("{}"))
            val base = server.url("/").toString()
            val result = LocalServices(ServiceApi(), base, base).detect(Settings())
            assertEquals("", result.dnsApi); assertEquals("", result.controller); assertEquals("", result.proxyWeb)
        }
    }
    @Test fun `manual service addresses and selected group are preserved`() = runBlocking {
        val configured = Settings(dnsWeb = "https://dns.example", dnsApi = "https://dns.example", controller = "https://proxy.example", proxyWeb = "https://ui.example", proxyGroup = "My group")
        val result = LocalServices(ServiceApi()).detect(configured)
        assertEquals(configured.copy(localSetupChecked = true), result)
    }
}
