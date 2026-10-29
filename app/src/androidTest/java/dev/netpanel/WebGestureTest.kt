package dev.netpanel

import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class WebGestureTest {
    @Test fun configuredProxyMoreButtonStaysInsideBottomBar() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val settings = runBlocking { SettingsStore(instrument.targetContext).settings.first() }
        assertTrue("Configure proxy Web address before live test", settings.proxyWeb.isNotBlank())
        val activity = instrument.startActivitySync(Intent(instrument.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        lateinit var web: WebView
        instrument.runOnMainSync {
            activity.panels.configure(settings); web = activity.panels.obtain(2)
            (web.parent as? ViewGroup)?.removeView(web)
            activity.addContentView(web, ViewGroup.LayoutParams(-1, -1))
        }
        try {
            var ready = false
            val deadline = SystemClock.uptimeMillis() + 15000
            while (!ready && SystemClock.uptimeMillis() < deadline) {
                val done = CountDownLatch(1)
                instrument.runOnMainSync {
                    web.evaluateJavascript("""
                        (() => {
                          const nav = document.querySelector('nav[aria-label="Mobile bottom navigation"]');
                          const button = nav?.querySelector('button[aria-label="More menu"]');
                          if (!button || !document.getElementById('netpanel-proxy-appearance')) return false;
                          const n = nav.getBoundingClientRect(), b = button.getBoundingClientRect();
                          return b.height >= 44 && b.top >= n.top && b.bottom <= n.bottom &&
                            getComputedStyle(button).top === '0px';
                        })()
                    """.trimIndent()) { ready = it == "true"; done.countDown() }
                }
                assertTrue(done.await(5, TimeUnit.SECONDS))
                if (!ready) SystemClock.sleep(200)
            }
            assertTrue("Real MetaCubeXD More button extends beyond the bottom bar", ready)
        } finally { instrument.runOnMainSync { (web.parent as? ViewGroup)?.removeView(web) } }
    }

    @Test fun configuredDnsUsesSingleSearchBorderAndSansFont() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val settings = runBlocking { SettingsStore(instrument.targetContext).settings.first() }
        assertTrue("Configure DNS Web address before live test", settings.dnsWeb.isNotBlank())
        val activity = instrument.startActivitySync(Intent(instrument.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        lateinit var web: WebView
        instrument.runOnMainSync {
            activity.panels.configure(settings); web = activity.panels.obtain(1)
            (web.parent as? ViewGroup)?.removeView(web)
            activity.addContentView(web, ViewGroup.LayoutParams(-1, -1))
        }
        var ready = false
        val deadline = SystemClock.uptimeMillis() + 15000
        while (!ready && SystemClock.uptimeMillis() < deadline) {
            val done = CountDownLatch(1)
            instrument.runOnMainSync {
                web.evaluateJavascript("""
                    (() => {
                      const input = document.querySelector('.cmd-search-bar #global-search-input');
                      return !!input && !!document.getElementById('netpanel-dns-appearance') &&
                        getComputedStyle(input).borderTopWidth === '0px' &&
                        getComputedStyle(input).backgroundColor === 'rgba(0, 0, 0, 0)' &&
                        getComputedStyle(document.body).fontFamily.includes('sans-serif');
                    })()
                """.trimIndent()) { ready = it == "true"; done.countDown() }
            }
            assertTrue(done.await(5, TimeUnit.SECONDS))
            if (!ready) SystemClock.sleep(200)
        }
        assertTrue("Real YoPilot search CSS and fixed sans-serif font did not apply", ready)
        val swipeWeb = web as SwipeWebView
        val directions = mutableListOf<Int>()
        instrument.runOnMainSync {
            swipeWeb.swipeEnabled = true; swipeWeb.canSwipe = { true }; swipeWeb.onSwipe = { directions.add(it) }
        }
        fun js(script: String): String {
            val done = CountDownLatch(1)
            var value = ""
            instrument.runOnMainSync { web.evaluateJavascript(script) { value = it; done.countDown() } }
            assertTrue(done.await(5, TimeUnit.SECONDS))
            return value
        }
        val point = org.json.JSONArray(js("(() => {const r=document.querySelector('.ypo-statistics').getBoundingClientRect();return [(r.top+40)/innerHeight,innerWidth]})()"))
        try {
            for (left in listOf(true, false)) {
                val start = SystemClock.uptimeMillis()
                val y = point.getDouble(0).toFloat() * web.height
                val x1 = web.width * if (left) .8f else .2f
                val x2 = web.width * if (left) .2f else .8f
                fun send(action: Int, x: Float) = instrument.runOnMainSync {
                    val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, x, y, 0)
                    web.dispatchTouchEvent(event); event.recycle()
                }
                send(MotionEvent.ACTION_DOWN, x1)
                js("true")
                send(MotionEvent.ACTION_MOVE, x2); send(MotionEvent.ACTION_UP, x2)
            }
            assertEquals("Real DNS horizontal gesture did not reach native paging; horizontal scroll=${web.canScrollHorizontally(1)}",
                listOf(1, -1), directions)
        } finally { instrument.runOnMainSync { (web.parent as? ViewGroup)?.removeView(web) } }
    }

    @Test fun horizontalPagingRespectsWebControlsAndDnsAppearance() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val activity = instrument.startActivitySync(Intent(instrument.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        lateinit var web: SwipeWebView
        val loaded = CountDownLatch(1)
        val directions = mutableListOf<Int>()
        instrument.runOnMainSync {
            web = SwipeWebView(activity).apply {
                settings.javaScriptEnabled = true
                swipeEnabled = true; canSwipe = { true }; onSwipe = { directions.add(it) }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) { loaded.countDown() }
                }
            }
            activity.addContentView(web, ViewGroup.LayoutParams(-1, -1))
            web.loadDataWithBaseURL("https://fixture.invalid/", """
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>body{margin:0} #plain{height:180px} #horizontal{height:120px;overflow-x:auto}
                #horizontal div{width:1800px;height:100px} .cmd-search-bar{border:1px solid black}
                input{border:2px solid red;background:white;width:100%;box-sizing:border-box} </style>
                <div id="plain">Plain page area</div>
                <div id="horizontal"><div>Horizontal web content</div></div>
                <div class="cmd-search-bar"><div class="cmd-search-left"><input id="global-search-input"></div></div>
            """.trimIndent(), "text/html", "UTF-8", null)
        }
        try {
            assertTrue(loaded.await(10, TimeUnit.SECONDS))
            instrument.waitForIdleSync()
            fun js(code: String): String {
                val result = CountDownLatch(1)
                var value = ""
                instrument.runOnMainSync { web.evaluateJavascript(code) { value = it; result.countDown() } }
                assertTrue(result.await(5, TimeUnit.SECONDS))
                return value
            }
            fun drag(left: Boolean, yDp: Int, vertical: Boolean = false) {
                val density = activity.resources.displayMetrics.density
                val start = SystemClock.uptimeMillis()
                val x1 = web.width * if (left) .8f else .2f
                val x2 = web.width * if (left) .2f else .8f
                val y = yDp * density
                fun send(action: Int, x: Float, targetY: Float) = instrument.runOnMainSync {
                    val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, x, targetY, 0)
                    web.dispatchTouchEvent(event); event.recycle()
                }
                send(MotionEvent.ACTION_DOWN, x1, y)
                // Wait for the asynchronous, read-only DOM hit test.
                js("true")
                send(MotionEvent.ACTION_MOVE, if (vertical) x1 else x2, if (vertical) y + 80 * density else y)
                send(MotionEvent.ACTION_UP, if (vertical) x1 else x2, if (vertical) y + 80 * density else y)
            }
            drag(true, 80); drag(false, 80)
            assertEquals(listOf(1, -1), directions)
            drag(true, 220) // Nested horizontal scrolling must keep the gesture.
            assertEquals(2, directions.size)
            drag(true, 310) // Text editing also keeps the gesture.
            assertEquals(2, directions.size)
            drag(true, 80, vertical = true)
            assertEquals(2, directions.size)
            instrument.runOnMainSync { web.swipeEnabled = false }
            drag(true, 80)
            assertEquals(2, directions.size)
            instrument.runOnMainSync { web.swipeEnabled = true; web.canSwipe = { false } }
            drag(true, 80)
            assertEquals(2, directions.size)
            val css = instrument.targetContext.assets.open("dns-appearance.css").bufferedReader().use { it.readText() }
            js("var style=document.createElement('style');style.textContent=${org.json.JSONObject.quote(css)};document.head.appendChild(style);true")
            assertEquals("true", js("getComputedStyle(document.querySelector('input')).borderTopWidth === '0px'"))
            assertEquals("true", js("getComputedStyle(document.body).fontFamily.includes('sans-serif')"))
            assertEquals("true", js("document.querySelector('input').value='search';document.querySelector('input').value==='search'"))
        } finally {
            instrument.runOnMainSync { (web.parent as? ViewGroup)?.removeView(web); web.destroy() }
        }
    }
}
