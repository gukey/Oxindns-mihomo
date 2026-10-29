# NetPanel

轻量 Android 原生管理容器：**概览 / DNS / 代理**。管理已有 OxiDNS（YoPilot）与 Mihomo（MetaCubeXD），不启动或修改服务核心，不提供 VPN。

## 0.1.3 首页单屏布局

- 首页取消纵向滚动，资源、运行时长分别合并到顶部 OxiDNS / Mihomo 卡片；去掉独立资源区、重复状态卡、快捷操作和事件列表。卡片可直接打开对应管理页，顶部保留刷新入口。
- 下方保留最近 60 秒流量图和 DNS 累计查询/异常、Mihomo 累计上传/下载；图表随剩余高度伸缩，避免主流程必须向下滚动。
- Mihomo 内存复用真实 `/connections` 的 `memory` 字节快照，按设置的概览刷新周期更新，无新增轮询端点。依据 [官方 Snapshot 结构](https://github.com/MetaCubeX/mihomo/blob/Meta/tunnel/statistic/manager.go)。当前已核实接口不提供 Mihomo CPU、运行时长，明确显示“接口未提供”；不以 APP 运行时间或手机总 CPU 冒充核心资源。
- `AGENTS.md` / `agnet.md` 记录仓库地址、验证后自动提交推送及中文描述约定；本地 `key.txt` 已忽略，凭据不随源码交付。

## 0.1.2 页面侧滑与 DNS 显示

- 概览、DNS、代理均可在主体区域左右侧滑，按相邻栏目切换；错误页和未配置页同样支持。
- WebView 仅对明确横向的单指短滑切栏。只读检查起点的 DOM，横向滚动区域、输入控件、滑块、画布及 iframe 优先处理自身手势；无法完成检查时交给网页。移除旧版底部滑动提示条，网页填满内容区，也可点击底部栏目切换。设置支持关闭侧滑。
- 切栏仍复用现有 WebView，不重建后台页面。
- DNS 页固定系统无衬线黑体；YoPilot 全局搜索仅保留外层边框，输入框保持透明、无内层边框，外层保留焦点提示。通过 APP 内置 CSS 作用于配置的 DNS 站点，不修改核心或网页业务事件。
- APP 内 YoPilot 概览直接从请求统计卡片开始，隐藏运行概览卡片及该页顶部品牌/搜索区；统计口径说明移到卡片后。原有网页导航保留，其他页面继续显示顶部搜索。CSS 仅存在于 APP 的 WebView，不改手机浏览器或桌面浏览器中的后台。
- APP 内修平 MetaCubeXD 底栏的“更多”按钮，将按钮收进底栏并保留弹出菜单。
- 手势处理参考 [Android 触摸事件说明](https://developer.android.com/develop/ui/views/touch-and-input/gestures/viewgroup)，DOM 查询使用 [WebView evaluateJavascript](https://developer.android.com/reference/android/webkit/WebView#evaluateJavascript(java.lang.String,%20android.webkit.ValueCallback))，不提供原生 JavaScript Bridge。

## 0.1.1 真机修复

- 修复 Android ICU 不接受指标解析正则中未转义 `}` 的问题。此前会把解析异常误报为 DNS 无法连接；现在已用真实 Android 设备复现旧版失败，并验证修复后通过。
- 首次配置完全为空时，验证本机 OxiDNS `127.0.0.1:9199`、Mihomo `127.0.0.1:9090` 候选接口；仅验证成功后填入地址。设置中也可手动“检测本机服务”。这些是已在目标手机上确认的候选，其他端口继续由用户配置，不将探测失败解释为服务停止。
- 仅在实际返回 MetaCubeXD 页面时填入 Controller 的 `/ui/` 地址，不覆盖已有 Web UI 地址和认证配置。
- 默认关注实际存在且名称包含“节点选择”的唯一代理组，首页服务卡片直接显示其当前节点；设置中仍可指定其他组。当前设备验证的是 `🚀 节点选择`。
- 首页增加 DNS 累计查询、累计异常，以及 Mihomo 累计上传/下载；流量累计取自真实 WebSocket 或连接快照。Mihomo 现有接口没有累计 HTTP 请求数，不将活动连接数冒充请求数。
- 加入独立真机接口测试，读取应用加密保存的配置，只执行 GET 和流量订阅，不修改代理组选择、核心配置或防火墙。

## 构建

- Android Studio 或命令行：JDK 17、Android SDK 35 / Build Tools 35.0.0。
- Gradle Wrapper 8.11.1、AGP 8.9.2、Kotlin 2.1.20；最低 Android 8.0（API 26），目标 API 35。
- 设置 `ANDROID_HOME`，或在本机 `local.properties` 中填写 `sdk.dir=...`。
- Windows：`./gradlew.bat assembleDebug testDebugUnitTest lintDebug`；Linux/macOS：`sh ./gradlew assembleDebug testDebugUnitTest lintDebug`。
- APK：`app/build/outputs/apk/debug/app-debug.apk`。
- 本项目中的 `.tools` 是本次构建使用的本地工具，不属于源码交付依赖。

## 首次使用

1. 在设置中填写 OxiDNS 的 Web 地址。示例 `http://192.168.1.1:9199` 不是手机本机地址。
2. 可选填写 OxiDNS **管理 API 根地址**（通常与 Web 地址相同，不附加 `/api`），以及 Basic Auth 凭据。留空则不请求 DNS 概览。
3. 分别填写 MetaCubeXD 页面地址、Mihomo External Controller 根地址和 Secret。不假定端口或自动拼出 Web UI 地址。本版 Secret 接受 HTTP 请求头可用的 ASCII 字符。
4. 在 MetaCubeXD 自身连接配置中设置控制器。原生设置中的 Secret 仅供原生状态请求使用，不注入第三方页面。
5. 如需首页节点信息，填写准确的代理组名称；不假定存在唯一全局节点。
6. 连接测试分别检测 Web HTTP 可达性和 API。前端加载成功不代表其中的 WebSocket、跨域请求成功，仍应打开后台验证。

手机的 `127.0.0.1` 指手机本身。服务运行于路由器时应使用路由器实际地址，并确保监听地址、防火墙、认证及跨域配置允许手机访问。

## 模块

| 文件 | 职责 |
| --- | --- |
| `PanelUi.kt` | 原生概览、60 秒流量图、三栏导航及 WebView 容器 |
| `SettingsUi.kt` | 服务、主题、启动栏目、刷新频率和缓存设置 |
| `WebPanel.kt` / `SwipeWebView.kt` | 两个 Activity 持有的 WebView、导航、认证、手势仲裁及状态保存 |
| `assets/dns-appearance.css` | DNS 固定黑体和 YoPilot 搜索框样式修正 |
| `MainActivity.kt` | 生命周期、系统文件选择和文档下载 |
| `PanelViewModel.kt` | StateFlow、前台按需轮询、退避、网络恢复、有限事件观察 |
| `ServiceApi.kt` | 只读真实 API、带 Bearer 请求头的流量 WebSocket |
| `LocalServices.kt` | 经过接口验证的本机服务发现、空白配置补全 |
| `Settings.kt` | DataStore 和 Android Keystore AES-GCM 加密 |

## 接口依据与显示含义

- OxiDNS：`GET /api/health`、可选 `/api/system`、`/api/metrics`。按官方字段读取版本、`uptime_ms`、`process_cpu_percent`、`process_memory_mb`。
- `/api/health` 可达与 DNS 就绪不同；响应 `status` 不为 `ok` 时显示部分不可用，不声称 DNS 运行正常。
- 查询与异常计数取 Prometheus 的精确指标 `query_total` / `query_error_total`，合计标签序列，相邻采样作差，显示实际窗口秒数。首次采样、计数回退、指标缺失时显示未提供。
- Mihomo：`GET /version`、`/configs`、`/connections`、配置关注组后读取 `/proxies`；`/traffic` 使用 WebSocket、Authorization 请求头，不拼接 Secret 查询参数。
- 累计流量读取 `/connections` 的 `uploadTotal` / `downloadTotal` 和 `/traffic` 的 `upTotal` / `downTotal`；服务重启或统计重置后以核心当前值为准，不自行累加历史。
- 节点延迟仅显示 API 中最后一条有效历史结果，不自动测试、不声称实时。Mihomo 运行时长没有确认的稳定接口，本版不展示虚构值。
- 事件仅保存本次会话观察到的 API 状态变化、指定组选择变化，最多 12 条；时间是应用观察时间，不能替代服务日志。
- 接口缺失不从截图推断数值。DNS 与 Mihomo 版本差异需通过实际部署联调。

官方依据：[OxiDNS 控制接口](https://oxidns.org/en/api/control/)、[指标目录](https://oxidns.org/en/api/metrics/)、[Mihomo 路由源码](https://github.com/MetaCubeX/mihomo/blob/Meta/hub/route/server.go)、[MetaCubeXD](https://github.com/MetaCubeX/metacubexd)。

## 生命周期与交互

- WebView 延迟创建，切栏仅改变可见性；保留页面、滚动和会话。进入设置也不销毁。
- Activity 暂停/恢复调用 WebView 对应生命周期，不调用全局暂停计时器破坏其他页面。
- 旋转等重建通过 `saveState` / `restoreState` 尽力恢复历史与滚动；**不能保证任意第三方单页应用的 JS 堆和临时表单在进程重建后完整恢复**。
- 原生概览仅在概览可见、应用前台时按需轮询/订阅。远程接口无网络时停止请求；环回地址在断网后继续可用。局域网无需通过互联网连通性验证。异常按刷新间隔倍增退避，最多 60 秒，网络恢复重启请求。
- 流量保留最近 60 秒，最多 120 点。缺失区间不连接为连续曲线，不补造零值。
- 网页内部返回优先；无历史时回概览。设置返回放弃未保存修改；密钥不写入实例状态。
- 三栏均支持主体侧滑；网页横向控件优先处理自身操作，复杂自定义手势组件仍可通过底部栏目切换。设置可关闭侧滑。

## 网络与安全取舍

- 因地址由用户动态配置（包括任意局域网 IP），Manifest 启用明文 HTTP。没有编造按 IP 网段动态生效的 Network Security Config。此为明确的兼容取舍，应优先部署 HTTPS。
- 网页主框架限制为所配置 Web 服务的同源地址；外部站点经确认交由系统浏览器打开。API 和 Web 分开配置。
- 不绕过证书验证，不开放文件 URL 访问，不注入脚本，不提供 Android JavaScript 桥接，不授予网页摄像头/麦克风权限。
- HTTPS 页面连接 HTTP 控制器可能受混合内容限制；跨域受服务 CORS 限制。应修复部署方式，不通过放松 WebView 安全策略规避。
- 完整配置经 Keystore AES-GCM 加密后写 DataStore，禁用应用备份；Android 12+ 显式排除云备份和设备迁移中的全部应用数据，避免迁移网页凭据与设备绑定的密文。无明文导出、无请求日志。WebView 自身 Cookie/DOM Storage 按 Android WebView 应用沙箱管理，保留第三方后台的认证方式。
- 第三方后台自身的认证、WebSocket 实现属于原页面行为，应用不重写；原生流量通道仅使用请求头认证。
- 文件上传由系统选择器授权，下载由系统文档选择器指定位置。支持同源 HTTP(S) 下载并携带当前 Cookie / DNS Basic Auth；拒绝下载重定向，避免凭据转发。
- `blob:` / `data:` 下载、跨域下载、第三方 SSO 新窗口流程需要在外部浏览器处理。本版不通过注入 JS 实现这些能力。

## 验证与限制

实际检查记录见 [VERIFY.md](VERIFY.md)。已在 PKX110 / Android 17 上验证真实 DNS 和 Mihomo API；其他 Android 版本、认证部署、旋转及性能验收仍需单独执行。真机测试命令：构建并安装主 APK 与 `app-debug-androidTest.apk`，执行 `adb shell am instrument -w -e class dev.netpanel.DeviceApiTest dev.netpanel.test/androidx.test.runner.AndroidJUnitRunner`。测试需要预先配置可达的真实服务。

官方接口和接入逻辑已对照 [OxiDNS health.rs](https://github.com/SvenShi/oxidns/blob/main/src/api/health.rs)、[control.rs](https://github.com/SvenShi/oxidns/blob/main/src/api/control.rs)、[Mihomo server.go](https://github.com/MetaCubeX/mihomo/blob/Meta/hub/route/server.go) 与 [MetaCubeXD useConnect.ts](https://github.com/MetaCubeX/metacubexd/blob/main/packages/ui/composables/useConnect.ts)。
