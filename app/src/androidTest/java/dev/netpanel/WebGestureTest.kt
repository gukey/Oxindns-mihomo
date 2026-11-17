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
    private fun awaitViewport(web: WebView) {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val deadline = SystemClock.uptimeMillis() + 10000
        var ready = false
        while (!ready && SystemClock.uptimeMillis() < deadline) {
            val done = CountDownLatch(1)
            instrument.runOnMainSync {
                web.evaluateJavascript("innerWidth > 0 && innerHeight > 0") {
                    ready = it == "true" && web.width > 0 && web.height > 0
                    done.countDown()
                }
            }
            assertTrue(done.await(5, TimeUnit.SECONDS))
            if (!ready) SystemClock.sleep(100)
        }
        assertTrue("WebView must have a measured viewport before gesture tests", ready)
    }
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

    @Test fun configuredDnsUsesDashboardThemeAndSansFont() {
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
                      return !!document.querySelector('#side a[data-path="force-forward"]') &&
                        !!document.querySelector('#btn-style') &&
                        !document.getElementById('netpanel-dns-appearance') &&
                        getComputedStyle(document.body).fontFamily.includes('sans-serif');
                    })()
                """.trimIndent()) { ready = it == "true"; done.countDown() }
            }
            assertTrue(done.await(5, TimeUnit.SECONDS))
            if (!ready) SystemClock.sleep(200)
        }
        assertTrue("New DNS dashboard did not load or legacy CSS is still injected", ready)
        awaitViewport(web)
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
        // Exercise the dashboard's own header picker in the actual Android WebView.
        js("document.getElementById('btn-style').click();true")
        for (style in listOf("bento", "brutal", "shadcn", "liquid")) {
            js("document.querySelector('[data-style-pick=\"$style\"]').click();true")
            assertEquals("\"$style\"", js("document.documentElement.dataset.style"))
        }
        js("document.getElementById('btn-style').click();true")
        fun waitJs(condition: String) {
            val until = SystemClock.uptimeMillis() + 5000
            while (js(condition) != "true" && SystemClock.uptimeMillis() < until) SystemClock.sleep(100)
            assertEquals("Menu state: " + js("JSON.stringify({open:document.getElementById('side').className,transform:getComputedStyle(document.getElementById('side')).transform,route:location.hash})"), "true", js(condition))
        }
        fun tap(selector: String) {
            val point = org.json.JSONArray(js("(() => {const r=document.querySelector('$selector').getBoundingClientRect();return [(r.left+r.width/2)/innerWidth,(r.top+r.height/2)/innerHeight]})()"))
            val start = SystemClock.uptimeMillis()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                instrument.runOnMainSync {
                    assertTrue("NetPanel must have focus before touch input", activity.hasWindowFocus())
                    web.visibility = android.view.View.VISIBLE
                    val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action,
                        point.getDouble(0).toFloat() * web.width, point.getDouble(1).toFloat() * web.height, 0)
                    web.dispatchTouchEvent(event); event.recycle()
                }
                SystemClock.sleep(50)
            }
        }
        // Use real WebView touch dispatch; DOM click() bypasses overlay hit testing.
        for (route in listOf("queries", "force-forward", "overview")) {
            tap("#menu")
            waitJs("document.getElementById('side').classList.contains('open')")
            SystemClock.sleep(300) // Drawer transition before computing the link's touch position.
            tap("#side a[data-path=\"$route\"]")
            waitJs("location.hash === '#/$route' && !document.getElementById('side').classList.contains('open')")
            SystemClock.sleep(300) // Wait for the closing drawer to leave the header hit area.
        }
        val point = org.json.JSONArray(js("(() => {const r=document.querySelector('#ov-stats').getBoundingClientRect();return [(r.top+40)/innerHeight,innerWidth]})()"))
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

    @Test fun horizontalPagingRespectsWebControls() {
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
            awaitViewport(web)
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
            assertEquals("true", js("document.querySelector('input').value='search';document.querySelector('input').value==='search'"))
        } finally {
            instrument.runOnMainSync { (web.parent as? ViewGroup)?.removeView(web); web.destroy() }
        }
    }
}
