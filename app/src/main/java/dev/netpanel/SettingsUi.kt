package dev.netpanel

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun SettingsPage(initial: Settings, model: PanelViewModel, panels: WebPanels, onSaved: () -> Unit) {
    // Credentials intentionally never enter rememberSaveable / saved-instance bundles.
    var draft by remember { mutableStateOf(model.settingsDraft ?: initial) }
    SideEffect { model.settingsDraft = draft }
    var saving by remember { mutableStateOf(false) }
    var testDns by remember { mutableStateOf(false) }
    var testProxy by remember { mutableStateOf(false) }
    var dnsResult by remember { mutableStateOf<String?>(null) }
    var proxyResult by remember { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var confirmCache by remember { mutableStateOf(false) }
    var detecting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("服务配置", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = {
                detecting = true
                scope.launch {
                    try {
                        draft = model.detectLocal(draft)
                        feedback = "检测完成，仅填入已验证的空白配置；请保存设置。其他地址或认证可手动填写。"
                    } finally { detecting = false }
                }
            }, enabled = !detecting) { Text(if (detecting) "正在检测本机服务…" else "检测本机 OxiDNS / Mihomo") }
            PanelCard(Modifier.fillMaxWidth()) {
                Text("OxiDNS", color = DnsGreen, fontWeight = FontWeight.Bold)
                SettingField("Web 管理地址", draft.dnsWeb, hint = "http://192.168.1.1:9199") { draft = draft.copy(dnsWeb = it.trim()) }
                SettingField("API 根地址（可选）", draft.dnsApi, hint = "http://设备地址:端口，不附加 /api") { draft = draft.copy(dnsApi = it.trim()) }
                SettingField("Basic Auth 用户名", draft.dnsUser, keyboard = KeyboardType.Text) { draft = draft.copy(dnsUser = it) }
                SettingField("Basic Auth 密码", draft.dnsPassword, secret = true) { draft = draft.copy(dnsPassword = it) }
                Text("网页保留原有登录流程；API 地址留空时概览不请求 DNS 指标。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = {
                    testDns = true; dnsResult = null
                    scope.launch { try { dnsResult = model.test(draft, true) } finally { testDns = false } }
                }, enabled = !testDns) { Text(if (testDns) "测试中…" else "测试连接") }
                dnsResult?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            PanelCard(Modifier.fillMaxWidth()) {
                Text("Mihomo", color = ProxyBlue, fontWeight = FontWeight.Bold)
                SettingField("MetaCubeXD Web UI 地址", draft.proxyWeb, hint = "填写实际部署地址") { draft = draft.copy(proxyWeb = it.trim()) }
                SettingField("External Controller 根地址", draft.controller, hint = "http://设备地址:实际端口") { draft = draft.copy(controller = it.trim()) }
                SettingField("Secret", draft.secret, secret = true) { draft = draft.copy(secret = it) }
                SettingField("概览关注的代理组（可选）", draft.proxyGroup, keyboard = KeyboardType.Text, hint = "与后台中的组名完全一致") { draft = draft.copy(proxyGroup = it) }
                Text("Web UI 与 API 分别配置。请在 MetaCubeXD 原有连接设置中填写控制器；应用不把 Secret 注入网页或 URL。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = {
                    testProxy = true; proxyResult = null
                    scope.launch { try { proxyResult = model.test(draft, false) } finally { testProxy = false } }
                }, enabled = !testProxy) { Text(if (testProxy) "测试中…" else "测试连接") }
                proxyResult?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            Text("界面", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            PanelCard(Modifier.fillMaxWidth()) {
                ChoiceRow("主题", listOf("跟随系统", "浅色", "深色"), draft.theme) { draft = draft.copy(theme = it) }
                ChoiceRow("启动栏目", listOf("概览", "DNS", "代理"), draft.startTab) { draft = draft.copy(startTab = it) }
                var interval by remember { mutableStateOf(draft.refreshSeconds.toString()) }
                OutlinedTextField(value = interval, onValueChange = {
                    if (it.length <= 2 && it.all(Char::isDigit)) { interval = it; draft = draft.copy(refreshSeconds = it.toIntOrNull() ?: 0) }
                }, label = { Text("概览刷新间隔（2–60 秒）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("左右滑动切栏")
                        Text("三个页面均支持侧滑；网页横向控件优先，也可点击底部栏目切换", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = draft.swipe, onCheckedChange = { draft = draft.copy(swipe = it) })
                }
                TextButton(onClick = { draft = draft.resetInterface(); interval = "5" }) { Text("恢复默认界面设置") }
            }
            PanelCard(Modifier.fillMaxWidth()) {
                Text("其他", fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { confirmCache = true }) { Text("清理 WebView 缓存") }
                InfoRow("应用版本", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                Text("凭据使用 Android Keystore 加密保存。HTTP 连接仍以明文传输，请仅连接受信任服务，优先使用 HTTPS。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("不启动 DNS、VPN 或代理进程。切换后台不清除网页登录状态。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            feedback?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                val error = draft.validate()
                if (error != null) feedback = error else {
                    saving = true
                    scope.launch { try { if (model.save(draft)) onSaved() else feedback = "保存失败，请重试" } finally { saving = false } }
                }
            }, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text(if (saving) "保存中…" else "保存设置") }
            Text("返回将放弃未保存的修改。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }
    }
    if (confirmCache) AlertDialog(onDismissRequest = { confirmCache = false }, title = { Text("清理网页缓存？") },
        text = { Text("清理磁盘缓存，保留 Cookie 和网页存储中的登录与连接设置。") },
        confirmButton = { TextButton(onClick = { panels.clearCache(); confirmCache = false }) { Text("清理") } },
        dismissButton = { TextButton(onClick = { confirmCache = false }) { Text("取消") } })
}

@Composable
private fun SettingField(label: String, value: String, secret: Boolean = false, hint: String = "", keyboard: KeyboardType = KeyboardType.Uri, onChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(), label = { Text(label) },
        placeholder = { if (hint.isNotEmpty()) Text(hint) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboard),
        visualTransformation = if (secret && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (secret) ({ IconButton(onClick = { visible = !visible }) {
            Icon(if (visible) PanelIcons.VisibilityOff else PanelIcons.Visibility, if (visible) "隐藏密码" else "显示密码")
        } }) else null)
}

@Composable
private fun ChoiceRow(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, title -> FilterChip(selected = selected == i, onClick = { onSelect(i) }, label = { Text(title) }) }
    }
}
