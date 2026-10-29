package dev.netpanel

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.Response
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class ServiceApiTest {
    @Test fun `offline polling recognizes loopback without trusting lookalike hostnames`() {
        assertTrue(isLoopback("http://127.0.0.1:9090"))
        assertTrue(isLoopback("http://[::1]:9090"))
        assertTrue(isLoopback("http://localhost:9199"))
        assertFalse(isLoopback("http://localhost.example.com"))
        assertFalse(isLoopback("http://192.168.1.1:9199"))
    }
    @Test fun `traffic WebSocket authenticates in header and parses real samples`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send("""{"up":1024,"down":2048,"upTotal":10240,"downTotal":20480}""")
                }
            }))
            val received = CountDownLatch(1)
            var sample: TrafficPoint? = null
            val api = ServiceApi()
            val socket = api.stream(Settings(controller = server.url("/").toString(), secret = "private"), {
                sample = it; received.countDown()
            }, {})
            try {
                assertTrue(received.await(5, TimeUnit.SECONDS))
                assertEquals(1024L, sample!!.up); assertEquals(2048L, sample!!.down)
                assertEquals(10240L, sample!!.upTotal); assertEquals(20480L, sample!!.downTotal)
                val request = server.takeRequest(5, TimeUnit.SECONDS)!!
                assertEquals("/traffic", request.path)
                assertEquals("Bearer private", request.getHeader("Authorization"))
            } finally { socket.cancel(); api.client.dispatcher.executorService.shutdown() }
        }
    }
    @Test fun `HTTP failures distinguish incompatible API from offline service`() {
        assertEquals(Health.UNSUPPORTED, ServiceApi.failure(ApiFailure(404)))
        assertEquals(Health.AUTH, ServiceApi.failure(ApiFailure(401)))
        assertEquals(Health.PARTIAL, ServiceApi.failure(ApiFailure(503)))
        assertEquals(Health.UNREACHABLE, ServiceApi.failure(java.io.IOException()))
    }
    @Test fun `DNS reads documented process fields and authenticated metrics`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"status":"ok","version":"1.6.1","uptime_ms":90000}"""))
            server.enqueue(MockResponse().setBody("""{"process_cpu_percent":0.07,"process_memory_mb":26}"""))
            server.enqueue(MockResponse().setBody("query_total{plugin_tag=\"dns\"} 100\nquery_error_total 2"))
            val result = ServiceApi().dns(Settings(dnsApi = server.url("/").toString(), dnsUser = "user", dnsPassword = "password"))
            assertEquals(Health.OK, result.health); assertEquals(26.0, result.memoryMb!!, 0.001)
            assertEquals(0.07, result.cpu!!, 0.001); assertEquals(100L, result.queries)
            assertEquals("/api/health", server.takeRequest().path)
            val request = server.takeRequest()
            assertTrue(request.getHeader("Authorization")!!.startsWith("Basic "))
            assertFalse(request.path!!.contains("password"))
        }
    }
    @Test fun `DNS readiness is distinct from HTTP availability and optional fields stay absent`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"status":"not_ready","version":"1.6.1","uptime_ms":12345}"""))
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(MockResponse().setResponseCode(404))
            val value = ServiceApi().dns(Settings(dnsApi = server.url("/").toString()))
            assertEquals(Health.PARTIAL, value.health)
            assertEquals(12345L, value.uptimeMs)
            assertNull(value.cpu); assertNull(value.memoryMb); assertNull(value.queries)
        }
    }
    @Test fun `proxy status uses requested group and header authentication`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"version":"1.19.0"}"""))
            server.enqueue(MockResponse().setBody("""{"mode":"rule"}"""))
            server.enqueue(MockResponse().setBody("""{"connections":[{},{}],"uploadTotal":1024,"downloadTotal":2048,"memory":83886080}"""))
            server.enqueue(MockResponse().setBody("""{"proxies":{"Main":{"now":"Tokyo"},"Tokyo":{"history":[{"delay":73}]}}}"""))
            val value = ServiceApi().proxy(Settings(controller = server.url("/").toString(), secret = "test-secret", proxyGroup = "Main"))
            assertEquals(Health.OK, value.health); assertEquals("Tokyo", value.node)
            assertEquals(2, value.connections); assertEquals(73, value.delayMs)
            assertEquals(1024L, value.uploadTotal); assertEquals(2048L, value.downloadTotal)
            assertEquals(80.0, value.memoryMb!!, 0.001)
            assertNull(value.cpu); assertNull(value.uptimeMs)
            repeat(4) {
                val request = server.takeRequest()
                assertEquals("Bearer test-secret", request.getHeader("Authorization"))
                assertFalse(request.path!!.contains("test-secret"))
            }
        }
    }
    @Test fun `native API refuses redirect rather than forwarding credentials`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://example.com"))
            try {
                ServiceApi().get(server.url("/").toString(), "version", "Bearer private")
                fail("Redirect must not be followed")
            } catch (e: ApiFailure) { assertEquals(302, e.status) }
            assertEquals(1, server.requestCount)
        }
    }
    @Test fun `Prometheus totals match exact metric names and aggregate labels`() {
        val raw = "# query_total\nquery_total{plugin_tag=\"a\"} 12\nquery_total{plugin_tag=\"b\"} 3e1\nforward_query_total 999\nquery_error_total 2"
        assertEquals(42L, metricTotal(raw, "query_total"))
        assertEquals(2L, metricTotal(raw, "query_error_total"))
        assertNull(metricTotal(raw, "missing"))
    }
    @Test fun `settings reject credential URLs and invalid intervals`() {
        assertNotNull(Settings(controller = "http://user:pass@192.168.1.1").validate())
        assertNotNull(Settings(controller = "http://192.168.1.1?token=secret").validate())
        assertNotNull(Settings(controller = "file:///etc/passwd").validate())
        assertNotNull(Settings(secret = "value\nInjected: header").validate())
        assertNotNull(Settings(refreshSeconds = 0).validate())
        assertNull(Settings(controller = "http://[::1]:9090", refreshSeconds = 2).validate())
        assertFalse(Settings(secret = "private").toString().contains("private"))
    }
    @Test fun `origin comparison includes scheme host and effective port`() {
        assertTrue(sameOrigin("http://192.168.1.1", "http://192.168.1.1:80/ui/"))
        assertFalse(sameOrigin("http://192.168.1.1:9199", "http://192.168.1.1:9090"))
        assertFalse(sameOrigin("https://example.com", "https://example.com.evil.test"))
        assertFalse(sameOrigin("https://example.com", "http://example.com"))
    }
}
