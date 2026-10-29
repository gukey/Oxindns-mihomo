package dev.netpanel

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.URLUtil
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private val model: PanelViewModel by viewModels()
    lateinit var panels: WebPanels
        private set
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private data class Download(val url: String, val agent: String, val cookie: String?, val auth: String)
    private var pendingDownload: Download? = null
    private val downloadClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val chooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        fileCallback = null
    }
    private val destination = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val item = pendingDownload
        pendingDownload = null
        if (uri != null && item != null) lifecycleScope.launch {
            message("正在下载…")
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val request = Request.Builder().url(item.url).header("User-Agent", item.agent)
                        .apply { item.cookie?.let { header("Cookie", it) }; if (item.auth.isNotEmpty()) header("Authorization", item.auth) }.build()
                    downloadClient.newCall(request).execute().use { response ->
                        check(response.isSuccessful)
                        val body = response.body ?: error("Empty download")
                        contentResolver.openOutputStream(uri, "wt")!!.use { output -> body.byteStream().use { it.copyTo(output) } }
                    }
                }.isSuccess
            }
            message(if (ok) "下载已保存" else "下载失败：请检查网络；重定向下载请在浏览器中完成")
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        android.webkit.WebView.setWebContentsDebuggingEnabled(false)
        panels = WebPanels(this, savedInstanceState?.getBundle("panels"))
        setContent { NetPanelApp(model, panels) }
    }
    override fun onStart() { super.onStart(); model.foreground(true) }
    override fun onResume() { super.onResume(); panels.resumed(true) }
    override fun onPause() { panels.resumed(false); CookieManager.getInstance().flush(); super.onPause() }
    override fun onStop() { model.foreground(false); super.onStop() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBundle("panels", Bundle().also { panels.save(it) })
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        fileCallback?.onReceiveValue(null); fileCallback = null
        panels.destroy(); downloadClient.dispatcher.cancelAll()
        super.onDestroy()
    }
    fun chooseFiles(callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams) {
        fileCallback?.onReceiveValue(null); fileCallback = callback
        try { chooser.launch(params.createIntent().apply { addCategory(Intent.CATEGORY_OPENABLE) }) }
        catch (_: Exception) { fileCallback?.onReceiveValue(null); fileCallback = null; message("没有可用的文件选择器") }
    }
    fun download(url: String, agent: String, disposition: String?, mime: String?) {
        val s = model.state.value.settings ?: return
        val auth = if (sameOrigin(s.dnsWeb, url)) ServiceApi.dnsAuth(s) else ""
        pendingDownload = Download(url, agent, CookieManager.getInstance().getCookie(url), auth)
        try { destination.launch(URLUtil.guessFileName(url, disposition, mime).substringAfterLast('/').substringAfterLast('\\')) }
        catch (_: Exception) { pendingDownload = null; message("没有可用的文件保存位置") }
    }
    fun message(value: String) { Toast.makeText(this, value, Toast.LENGTH_LONG).show() }
}
