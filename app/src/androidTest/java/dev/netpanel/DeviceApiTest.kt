package dev.netpanel

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Explicitly invoked live checks on the configured phone; no service configuration is mutated. */
@RunWith(AndroidJUnit4::class)
class DeviceApiTest {
    private fun settings() = runBlocking {
        SettingsStore(InstrumentationRegistry.getInstrumentation().targetContext).settings.first()
    }
    @Test fun dnsHealthResourcesAndCumulativeQueriesOnAndroid() = runBlocking {
        val s = settings()
        assertTrue("Configure DNS API before device test", s.dnsApi.isNotBlank())
        val result = ServiceApi().dns(s)
        assertEquals(Health.OK, result.health)
        assertNotNull(result.version); assertNotNull(result.uptimeMs)
        assertNotNull(result.cpu); assertNotNull(result.memoryMb)
        assertNotNull(result.queries); assertNotNull(result.queryErrors)
    }
    @Test fun mihomoSelectionGroupAndCumulativeTrafficOnAndroid() = runBlocking {
        val s = settings()
        assertTrue("Configure controller before device test", s.controller.isNotBlank())
        val api = ServiceApi()
        val result = api.proxy(s)
        assertEquals(Health.OK, result.health)
        assertNotNull(result.mode); assertNotNull(result.connections)
        assertNotNull(result.node); assertNotNull(result.uploadTotal); assertNotNull(result.downloadTotal)
        val latch = CountDownLatch(1)
        var point: TrafficPoint? = null
        val stream = api.stream(s, { point = it; latch.countDown() }, {})
        try {
            assertTrue("No live traffic sample", latch.await(8, TimeUnit.SECONDS))
            assertNotNull(point?.upTotal); assertNotNull(point?.downTotal)
        } finally { stream.cancel() }
    }
}
