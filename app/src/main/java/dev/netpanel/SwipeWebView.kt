package dev.netpanel

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.WebView
import kotlin.math.abs

/** A read-only DOM hit test lets horizontal web controls retain their gestures. */
class SwipeWebView(context: Context) : WebView(context) {
    var swipeEnabled = false
    var canSwipe: (Int) -> Boolean = { false }
    var onSwipe: (Int) -> Unit = {}
    private var serial = 0
    private var startX = 0f
    private var startY = 0f
    private var eligible = false
    private var domAllowsSwipe = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    @SuppressLint("ClickableViewAccessibility")
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val gesture = ++serial
                startX = event.x; startY = event.y
                eligible = swipeEnabled
                domAllowsSwipe = false
                if (eligible && width > 0 && height > 0) {
                    val x = (event.x / width).coerceIn(0f, 1f)
                    val y = (event.y / height).coerceIn(0f, 1f)
                    // No event handlers or native bridge are installed in the page.
                    evaluateJavascript("""
                        (() => {
                          let e = document.elementFromPoint($x * innerWidth, $y * innerHeight);
                          if (!e) return false;
                          while (e) {
                            const s = getComputedStyle(e);
                            if (e.matches('input,textarea,select,iframe,video,canvas,[contenteditable="true"],[role="slider"],[role="tablist"]') ||
                                ['none','pan-x','pinch-zoom'].includes(s.touchAction) ||
                                (e.scrollWidth > e.clientWidth + 2 && /auto|scroll|overlay/.test(s.overflowX))) return false;
                            e = e.parentElement;
                          }
                          return true;
                        })()
                    """.trimIndent()) { result ->
                        if (serial == gesture) domAllowsSwipe = result == "true"
                    }
                }
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> { eligible = false; serial++ }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.y - startY) > slop && abs(event.y - startY) > abs(event.x - startX)) eligible = false
            }
            MotionEvent.ACTION_UP -> {
                serial++
                val dx = event.x - startX
                val direction = if (dx < 0) 1 else -1
                val switch = eligible && swipeEnabled && domAllowsSwipe && event.pointerCount == 1 &&
                    event.eventTime - event.downTime < ViewConfiguration.getLongPressTimeout() &&
                    abs(dx) >= 72 * resources.displayMetrics.density && abs(dx) > 2 * abs(event.y - startY) &&
                    !canScrollHorizontally(-1) && !canScrollHorizontally(1) && canSwipe(direction)
                eligible = false
                if (switch) {
                    val cancel = MotionEvent.obtain(event)
                    cancel.action = MotionEvent.ACTION_CANCEL
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                    onSwipe(direction)
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }
}
