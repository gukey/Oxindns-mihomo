package dev.netpanel

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.EditText
import android.widget.LinearLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.ByteArrayInputStream

fun sameOrigin(a: String, b: String): Boolean {
    val first = a.toHttpUrlOrNull() ?: return false
    val second = b.toHttpUrlOrNull() ?: return false
    return first.scheme == second.scheme && first.host == second.host && first.port == second.port
}

/** Activity-owned WebViews. Switching tabs never pauses or recreates an existing page. */
class WebPanels(private val activity: MainActivity, private val restored: Bundle?) {
    private val panels = mutableMapOf<Int, WebView>()
    private val popups = mutableSetOf<WebView>()
    private val autoAuthAttempted = mutableSetOf<Int>()
    private var settings = Settings()
    private val addresses = mutableMapOf<Int, String>()
    var errors by mutableStateOf<Map<Int, String>>(emptyMap())
        private set
    var loading by mutableStateOf<Map<Int, Boolean>>(emptyMap())
        private set
    private var released = false
    private val dnsAppearance by lazy { activity.assets.open("dns-appearance.css").bufferedReader().use { it.readText() } }
    private val proxyAppearance by lazy { activity.assets.open("proxy-appearance.css").bufferedReader().use { it.readText() } }
    private fun applyAppearance(view: WebView, index: Int) {
        val css = org.json.JSONObject.quote(if (index == 1) dnsAppearance else proxyAppearance)
        val id = if (index == 1) "netpanel-dns-appearance" else "netpanel-proxy-appearance"
        view.evaluateJavascript("""
            (() => {
              let style = document.getElementById('$id');
              if (!style) {
                style = document.createElement('style');
                style.id = '$id';
                (document.head || document.documentElement).appendChild(style);
              }
              style.textContent = $css;
            })()
        """.trimIndent(), null)
    }
    private fun address(index: Int) = if (index == 1) settings.dnsWeb else settings.proxyWeb
    private fun allowed(index: Int, url: String): Boolean = sameOrigin(address(index), url)
    fun configure(value: Settings) {
        settings = value
        panels.forEach { (index, web) ->
            val target = address(index)
            if (addresses[index] != target) {
                addresses[index] = target; web.stopLoading(); web.clearHistory()
                errors = errors - index
                web.loadUrl(target.ifBlank { "about:blank" })
            }
        }
    }
    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    fun obtain(index: Int): WebView = panels.getOrPut(index) {
        SwipeWebView(activity).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(true)
            settings.safeBrowsingEnabled = true
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    if (allowed(index, url)) return false
                    if (request.isForMainFrame && request.hasGesture() && request.url.scheme in listOf("http", "https", "mailto"))
                        external(request.url)
                    return true
                }
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    // Redirected top-level loads are checked here as well as in navigation callbacks.
                    if (request.isForMainFrame && request.url.scheme in listOf("http", "https") && !allowed(index, request.url.toString()))
                        return WebResourceResponse("text/plain", "UTF-8", 403, "Untrusted origin", emptyMap(), ByteArrayInputStream("请在浏览器中打开外部站点".toByteArray()))
                    return null
                }
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    if (released) return
                    autoAuthAttempted.remove(index)
                    loading = loading + (index to true); errors = errors - index
                }
                override fun onPageFinished(view: WebView, url: String?) {
                    if (released) return
                    if (url != null && allowed(index, url)) applyAppearance(view, index)
                    loading = loading + (index to false)
                    CookieManager.getInstance().flush()
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame && !released) {
                        errors = errors + (index to "管理页面暂时无法连接，请检查地址、网络及服务状态")
                        loading = loading + (index to false)
                    }
                }
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if (request.isForMainFrame && !released) {
                        errors = errors + (index to if (response.statusCode in listOf(401, 403)) "认证未通过，请检查凭据或重试登录" else "管理页面返回 HTTP ${response.statusCode}")
                        loading = loading + (index to false)
                    }
                }
                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
                    handler.cancel()
                    if (!released) errors = errors + (index to "HTTPS 证书验证失败，请修复服务端证书")
                }
                override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String) {
                    val current = view.url ?: ""
                    if (!allowed(index, current) || current.toHttpUrlOrNull()?.host != host) { handler.cancel(); return }
                    if (index == 1 && this@WebPanels.settings.dnsUser.isNotBlank() && autoAuthAttempted.add(index)) {
                        handler.proceed(this@WebPanels.settings.dnsUser, this@WebPanels.settings.dnsPassword)
                    } else {
                        val user = EditText(activity).apply { hint = "用户名" }
                        val password = EditText(activity).apply { hint = "密码"; inputType = 129 }
                        val fields = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 8, 32, 8); addView(user); addView(password) }
                        AlertDialog.Builder(activity).setTitle("管理页面认证").setView(fields)
                            .setPositiveButton("登录") { _, _ -> handler.proceed(user.text.toString(), password.text.toString()) }
                            .setNegativeButton("取消") { _, _ -> handler.cancel() }.setOnCancelListener { handler.cancel() }.show()
                    }
                }
                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    (view.parent as? android.view.ViewGroup)?.removeView(view)
                    panels.remove(index); view.destroy()
                    errors = errors + (index to "页面进程已退出，点击重试重新打开")
                    activity.recreate()
                    return true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                    activity.chooseFiles(callback, params); return true
                }
                override fun onCreateWindow(view: WebView, dialog: Boolean, gesture: Boolean, message: android.os.Message): Boolean {
                    if (!gesture) return false
                    val temporary = WebView(activity)
                    popups.add(temporary)
                    temporary.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            if (request.url.scheme in listOf("http", "https")) external(request.url)
                            popups.remove(temporary); temporary.destroy(); return true
                        }
                    }
                    (message.obj as WebView.WebViewTransport).webView = temporary
                    message.sendToTarget(); return true
                }
                override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
            }
            setDownloadListener { url, agent, disposition, mime, _ ->
                if (allowed(index, url)) activity.download(url, agent, disposition, mime)
                else activity.message("此下载地址不受信任，或使用了暂不支持的 blob/data 下载，请在浏览器中操作")
            }
            val saved = restored?.getBundle("web$index")
            val target = address(index)
            addresses[index] = target
            if (saved == null || restored?.getString("address$index") != target || restoreState(saved) == null)
                loadUrl(target.ifBlank { "about:blank" })
        }
    }
    fun retry(index: Int) { errors = errors - index; panels[index]?.reload() }
    fun back(index: Int): Boolean = panels[index]?.let { if (it.canGoBack()) { it.goBack(); true } else false } ?: false
    fun resumed(value: Boolean) { panels.values.forEach { if (value) it.onResume() else it.onPause() } }
    fun clearCache() { panels.values.forEach { it.clearCache(true) }; if (panels.isEmpty()) WebView(activity).apply { clearCache(true); destroy() } }
    fun save(bundle: Bundle) { panels.forEach { (i, web) -> bundle.putBundle("web$i", Bundle().also { web.saveState(it) }); bundle.putString("address$i", addresses[i]) } }
    fun destroy() { released = true; popups.forEach { it.destroy() }; popups.clear(); panels.values.forEach { (it.parent as? android.view.ViewGroup)?.removeView(it); it.stopLoading(); it.webChromeClient = null; it.destroy() }; panels.clear() }
    private fun external(uri: Uri) {
        AlertDialog.Builder(activity).setTitle("打开外部链接？").setMessage("该链接将交给系统浏览器或其他应用处理。")
            .setPositiveButton("打开") { _, _ -> runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, uri)) } }
            .setNegativeButton("取消", null).show()
    }
}
