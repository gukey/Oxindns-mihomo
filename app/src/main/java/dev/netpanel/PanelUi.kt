@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.netpanel

import android.view.View
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val DnsGreen = Color(0xFF00AF79)
internal val ProxyBlue = Color(0xFF287DFF)
internal fun healthColor(health: Health) = when (health) {
    Health.OK -> DnsGreen
    Health.PARTIAL, Health.AUTH, Health.UNSUPPORTED -> Color(0xFFBD8100)
    else -> Color(0xFF8492A6)
}

@Composable
fun NetPanelApp(model: PanelViewModel, panels: WebPanels) {
    val state by model.state.collectAsStateWithLifecycle()
    val settings = state.settings
    val dark = when (settings?.theme) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val view = LocalView.current
    SideEffect {
        (view.context as? MainActivity)?.let { activity ->
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = ProxyBlue, secondary = DnsGreen)
        else lightColorScheme(primary = ProxyBlue, secondary = DnsGreen, background = Color(0xFFF1F5FA), surface = Color.White)) {
        if (settings == null) {
            Surface(Modifier.fillMaxSize()) { Box(contentAlignment = Alignment.Center) {
                if (state.storageError) Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text("无法读取安全设置。设备密钥可能已失效。")
                    var confirm by remember { mutableStateOf(false) }
                    TextButton(onClick = { confirm = true }) { Text("清除配置并重新设置") }
                    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("清除全部配置？") },
                        text = { Text("已保存的服务地址和认证信息将被删除。") }, confirmButton = { TextButton(onClick = { model.clearBrokenSettings(); confirm = false }) { Text("清除") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } })
                } else CircularProgressIndicator()
            } }
            return@MaterialTheme
        }
        var tab by rememberSaveable { mutableIntStateOf(settings.startTab) }
        var showSettings by rememberSaveable { mutableStateOf(false) }
        val pageAlpha = remember { Animatable(1f) }
        LaunchedEffect(tab) { pageAlpha.snapTo(.72f); pageAlpha.animateTo(1f, tween(140)) }
        val snackbar = remember { SnackbarHostState() }
        SideEffect { panels.configure(settings) }
        LaunchedEffect(tab, showSettings) { model.overview(tab == 0 && !showSettings) }
        LaunchedEffect(state.notice) { state.notice?.let { snackbar.showSnackbar(it); model.dismissNotice() } }
        BackHandler(enabled = showSettings || tab != 0) {
            if (showSettings) { model.settingsDraft = null; showSettings = false }
            else if (!panels.back(tab)) tab = 0
        }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(if (showSettings) PanelIcons.Tune else PanelIcons.Shield, null, tint = ProxyBlue)
                        Text(if (showSettings) "设置" else listOf("NetPanel", "DNS 管理", "代理管理")[tab], fontWeight = FontWeight.Bold)
                    }
                }, navigationIcon = {
                    if (showSettings) IconButton(onClick = { model.settingsDraft = null; showSettings = false }) { Icon(PanelIcons.ArrowBack, "返回") }
                }, actions = {
                    if (!showSettings) {
                        if (tab != 0) IconButton(onClick = { panels.retry(tab) }) { Icon(PanelIcons.Refresh, "刷新页面") }
                        IconButton(onClick = { showSettings = true }) { Icon(PanelIcons.Settings, "设置") }
                    }
                }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
            }, bottomBar = {
                if (!showSettings) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    val icons = listOf(PanelIcons.Dashboard, PanelIcons.Language, PanelIcons.Hub)
                    listOf("概览", "DNS", "代理").forEachIndexed { i, title ->
                        NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icons[i], title) }, label = { Text(title) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = if (i == 1) DnsGreen else ProxyBlue,
                                selectedTextColor = if (i == 1) DnsGreen else ProxyBlue))
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).graphicsLayer { alpha = pageAlpha.value }) {
                // Keep the AndroidView mounted under the native pages, including the settings screen.
                AndroidView(factory = { context -> FrameLayout(context) }, modifier = Modifier.fillMaxSize(), update = { host ->
                    panels.configure(settings)
                    host.visibility = if (showSettings || tab == 0) View.INVISIBLE else View.VISIBLE
                    for (i in 1..2) {
                        if (tab == i && !showSettings) {
                            val web = panels.obtain(i)
                            (web as SwipeWebView).apply {
                                swipeEnabled = settings.swipe
                                canSwipe = { direction -> i + direction in 0..2 }
                                onSwipe = { direction -> tab = (i + direction).coerceIn(0, 2) }
                            }
                            if (web.parent !== host) { (web.parent as? android.view.ViewGroup)?.removeView(web); host.addView(web, FrameLayout.LayoutParams(-1, -1)) }
                        }
                    }
                    for (i in 0 until host.childCount) host.getChildAt(i).visibility =
                        if (tab in 1..2 && host.getChildAt(i) === panels.obtain(tab)) View.VISIBLE else View.INVISIBLE
                })
                if (tab == 0 && !showSettings) {
                    Overview(state, onTab = { tab = it }, refresh = model::refresh, onSettings = { showSettings = true }, swipe = settings.swipe)
                }
                if (tab != 0 && !showSettings) {
                    val address = if (tab == 1) settings.dnsWeb else settings.proxyWeb
                    val error = panels.errors[tab]
                    if (address.isBlank() || error != null) EmptyWeb(
                        message = if (address.isBlank()) "请先配置 ${if (tab == 1) "OxiDNS" else "MetaCubeXD"} Web 管理地址" else error!!,
                        retry = if (address.isBlank()) null else ({ panels.retry(tab) }), settings = { showSettings = true },
                        swipe = settings.swipe, onSwipe = { direction -> tab = (tab + direction).coerceIn(0, 2) })
                    if (panels.loading[tab] == true && error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (showSettings) SettingsPage(settings, model, panels, onSaved = { model.settingsDraft = null; showSettings = false })
            }
        }
    }
}

@Composable
private fun EmptyWeb(message: String, retry: (() -> Unit)?, settings: () -> Unit, swipe: Boolean, onSwipe: (Int) -> Unit) {
    val callback by rememberUpdatedState(onSwipe)
    val gesture = if (swipe) Modifier.pointerInput(Unit) {
        var distance = 0f
        detectHorizontalDragGestures(onDragStart = { distance = 0f }, onDragEnd = {
            if (kotlin.math.abs(distance) > 72.dp.toPx()) callback(if (distance > 0) -1 else 1)
        }, onHorizontalDrag = { _, delta -> distance += delta })
    } else Modifier
    Surface(Modifier.fillMaxSize().then(gesture)) {
        Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(PanelIcons.CloudOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp)); Text(message, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp)); Row {
                retry?.let { Button(onClick = it) { Text("重试") } }
                TextButton(onClick = settings) { Text("检查设置") }
            }
        }
    }
}

@Composable
internal fun PanelCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun Overview(state: PanelState, onTab: (Int) -> Unit, refresh: () -> Unit, onSettings: () -> Unit, swipe: Boolean) {
    var drag by remember { mutableFloatStateOf(0f) }
    val gesture = if (swipe) Modifier.pointerInput(Unit) {
        detectHorizontalDragGestures(onDragStart = { drag = 0f }, onDragEnd = { if (drag < -72.dp.toPx()) onTab(1) },
            onHorizontalDrag = { _, delta -> drag += delta })
    } else Modifier
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).then(gesture).padding(horizontal = 12.dp, vertical = 6.dp)) {
        val compact = maxHeight < 490.dp
        val last = state.traffic.lastOrNull()
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val status = when {
                    state.detecting -> "正在检测本机服务…"
                    state.dns.health == Health.OK && state.proxy.health == Health.OK -> "DNS 与代理运行正常"
                    !state.online -> "外部网络不可用 · 检查本机服务"
                    else -> "服务状态 · 点击卡片打开管理"
                }
                Text(status, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = refresh, modifier = Modifier.size(32.dp)) {
                    if (state.refreshing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(PanelIcons.Refresh, "刷新状态", Modifier.size(18.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ServiceCard("OxiDNS", state.dns, DnsGreen, PanelIcons.Language, Modifier.weight(1f), compact) { onTab(1) }
                ServiceCard("Mihomo", state.proxy, ProxyBlue, PanelIcons.Hub, Modifier.weight(1f), compact) { onTab(2) }
            }
            TrafficCard(state, Modifier.fillMaxWidth().weight(1f), compact)
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(if (compact) 10.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("累计统计 · 本次运行", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TotalValue("DNS 查询", state.dns.queries?.toString() ?: "—", Modifier.weight(1f))
                        TotalValue("DNS 异常", state.dns.queryErrors?.toString() ?: "—", Modifier.weight(1f))
                        TotalValue("累计上传", (last?.upTotal ?: state.proxy.uploadTotal)?.let(::bytes) ?: "—", Modifier.weight(1f))
                        TotalValue("累计下载", (last?.downTotal ?: state.proxy.downloadTotal)?.let(::bytes) ?: "—", Modifier.weight(1f))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(state.updatedAt?.let { "更新于 ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it))}" } ?: "等待真实数据",
                    Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onSettings, contentPadding = PaddingValues(horizontal = 6.dp), modifier = Modifier.height(28.dp)) {
                    Text(if (state.settings?.dnsApi.isNullOrBlank() || state.settings?.controller.isNullOrBlank()) "配置服务" else "服务设置", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun TotalValue(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ServiceCard(name: String, state: ServiceState, accent: Color, icon: ImageVector, modifier: Modifier, compact: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(if (compact) 10.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(icon, null, Modifier.size(18.dp), tint = accent)
                Text(name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            val color by animateColorAsState(healthColor(state.health), label = "health")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
                Text(state.health.label, color = color, fontSize = 10.sp, maxLines = 1)
                Text(state.version ?: "版本未提供", fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(if (name == "OxiDNS") "近 ${state.queryWindowSeconds ?: "—"} 秒查询 ${state.recentQueries ?: "—"}" else "节点 ${state.node ?: "未提供"}",
                fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (name == "OxiDNS") "同期异常 ${state.recentErrors ?: "—"}" else "${state.mode ?: "模式未提供"} · 连接 ${state.connections ?: "—"}",
                fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            ResourceLine("运行时长", uptime(state.uptimeMs) ?: "接口未提供")
            ResourceLine("CPU", state.cpu?.let { "%.2f%%".format(it) } ?: "接口未提供")
            ResourceLine("内存", state.memoryMb?.let { "%.1f MB".format(it) } ?: "接口未提供")
        }
    }
}

@Composable
private fun ResourceLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 10.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun TrafficCard(state: PanelState, modifier: Modifier, compact: Boolean) = Surface(modifier, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
    Column(Modifier.fillMaxSize().padding(if (compact) 10.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Mihomo 实时流量", fontWeight = FontWeight.SemiBold)
        Text("最近 60 秒", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val latest = state.traffic.lastOrNull()
    val live = state.trafficLive && latest != null && now - latest.time < 5000
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
        Text("↓ ${if (live) speed(latest!!.down) else "—"}", color = DnsGreen, fontWeight = FontWeight.Bold)
        Text("↑ ${if (live) speed(latest!!.up) else "—"}", color = ProxyBlue, fontWeight = FontWeight.Bold)
    }
    val points = state.traffic
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f)
    Canvas(Modifier.fillMaxWidth().weight(1f)) {
        for (i in 1..3) drawLine(grid, Offset(0f, size.height * i / 4), Offset(size.width, size.height * i / 4), 1f)
        if (points.size < 2) return@Canvas
        val end = now
        val visible = points.filter { end - it.time <= 60_000 }
        val max = visible.maxOfOrNull { maxOf(it.up, it.down) }?.coerceAtLeast(1024)?.toFloat() ?: return@Canvas
        fun line(upload: Boolean): Path = Path().apply {
            var previous: TrafficPoint? = null
            visible.forEach { p ->
                val x = size.width * (1 - (end - p.time) / 60000f)
                val y = size.height - (if (upload) p.up else p.down) / max * (size.height - 8)
                if (previous == null || p.time - previous!!.time > 2500) moveTo(x, y) else lineTo(x, y)
                previous = p
            }
        }
        drawPath(line(false), DnsGreen, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        drawPath(line(true), ProxyBlue, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
    if (!live) Text(if (state.settings?.controller.isNullOrBlank()) "配置 External Controller 后显示真实流量" else "流量数据未连接或已中断", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

}

internal fun uptime(ms: Long?): String? = ms?.let {
    val minutes = it / 60_000
    when { minutes >= 1440 -> "${minutes / 1440}天 ${minutes % 1440 / 60}小时"; minutes >= 60 -> "${minutes / 60}小时 ${minutes % 60}分"; else -> "${minutes}分" }
}
internal fun speed(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB/s".format(bytes / 1048576.0)
    bytes >= 1024 -> "%.1f KB/s".format(bytes / 1024.0)
    else -> "$bytes B/s"
}
internal fun bytes(value: Long): String = when {
    value >= 1024L * 1024 * 1024 -> "%.2f GB".format(value / 1073741824.0)
    value >= 1024L * 1024 -> "%.2f MB".format(value / 1048576.0)
    value >= 1024L -> "%.1f KB".format(value / 1024.0)
    else -> "$value B"
}
