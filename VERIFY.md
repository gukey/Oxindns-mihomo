# NetPanel 0.1.5 实际验证记录

日期：2026-11-17。包名 `dev.netpanel`，versionCode 6。

- `assembleDebug`、`testDebugUnitTest`、`lintDebug`、`assembleDebugAndroidTest` 通过；13 项单元测试通过，Lint 0 错误、8 项既有警告。
- 5 项真机测试通过（8.596 秒）。新增真实 WebView 触摸验证：在液态玻璃风格下，打开侧栏并连续选择查询日志、强制转发和概览；等待开关动画后再操作，避免测试点击仍在移动的侧栏。主题切换、双向侧滑、网页控件手势优先、代理底栏和真实接口检查继续通过。
- 面板 `1cc512d`：7 项 Playwright 测试通过。修复前明确复现侧栏链接被 `side-mask` 遮罩截走点击；修复后五种风格均可选页，外部遮罩关闭正常。液态玻璃侧栏允许短屏滚动。
- 首页按 DNS 面板字体层级和材质重新排版：28sp 标题、28–32sp 关键数据、13–14sp 正文及 11–12sp 说明；累计数值和单位分行。真机竖屏截图确认原有服务资源、当前节点、流量图、累计统计、更新时间与底栏完整单屏显示。
- 原生柔光为静态绘制，无持续动画、实时背景模糊或 WebView 截图采样。浅色真机效果已检查；深色配色已实现，但本轮未单独进行深色截图验收，未覆盖横屏、大字体与跨 Android 版本矩阵。
- 单文件 HTML 为 192,459 字节，SHA-256：`49096f8d062cda00ea183cda100d79184f4e224989bc915dddbd5cb9d7c9d019`，手机 HTTP 内容与构建一致。原核心配置和强制转发规则校验值保持不变，健康接口返回 `ok`，线上无 YoPilot 文件。
- 最终 Debug APK v2 签名验证通过，SHA-256：`a8a2ae9756b7c1ce046262463e9f1fbd349a840bd2d058113f7ab5461b6d1f99`。已覆盖安装并保留配置。

交付位于 `dist/release-0.1.5/`，构建、测试和部署报告位于 `dist/reports/0.1.5/`；截图仅保存在本机忽略目录，不上传包含实际节点名称的画面。Root 模块保持 0.1.3 的既有发布，不改核心。

## 0.1.4 历史验证记录

日期：2026-11-17。包名 `dev.netpanel`，versionCode 5。

- `assembleDebug`、`testDebugUnitTest`、`lintDebug`、`assembleDebugAndroidTest` 全部通过；13 项单元测试通过，Lint 0 错误、8 项既有警告。
- 5 项真机测试通过（6.275 秒）：OxiDNS 和 Mihomo 真实接口、新 DNS 面板加载与主题切换、真实网页双向主体侧滑、嵌套横向控件/输入框手势优先、MetaCubeXD 更多按钮边界。已修正测试在 WebView 尚未完成布局时发手势的时序问题。
- 单文件面板来自 `gukey/oxidns-dashboard` 提交 `5d10bd0`。TypeScript / Vite 构建与 6 项 Playwright 测试通过，覆盖五种主题、存储受限、小屏弹层、跟随系统、规则批量去重/编辑/移除、版本冲突、不可达、任务失败与输出转义。
- 通过 ADB 转发连接手机真实 API，验证概览、查询页、五种外观与强制转发读取，现有 3 条规则可读，无 JavaScript 页面异常。生产环境未测试增删改；写入路径只在隔离接口中测试，避免改变现有路由。
- 新面板已部署到原 WebUI 根目录，HTTP 返回与本机构建逐字节一致；HTML 为 192,404 字节，SHA-256：`2bae56c54b51885c50c793b1f42cc4153bbf531632e3e429c83791426de04b6d`。
- 在线 YoPilot 页面与静态资源已移除，完整旧目录只在 WebUI 根目录之外保留回退备份。原规则更新脚本仍生成 `rule-update-status.json`，面板不依赖该文件；发布 UI 仍为单个 HTML。
- OxiDNS、Mihomo 配置及强制转发规则状态文件的迁移前后 SHA-256 相同，未重启核心。`/api/health` 返回 `ok`。
- NetPanel 已通过 ADB 覆盖安装，保留原配置；APK v2 签名验证通过，SHA-256：`991a30f60a1be60b550b47e7142cf507ac8c67b5e102442b7197311985a9e66d`。

交付文件位于本机 `dist/release-0.1.4/`，报告位于 `dist/reports/0.1.4/`。本轮完成目标手机竖屏验证，未覆盖横屏、超大字体和跨 Android 版本矩阵。0.1.3 通用 Root 安装包保持原版本；独立强制转发后端不是面板构建产物，新部署须另行安装配套服务。

## 0.1.3 历史验证记录

日期：2026-10-29。包名 `dev.netpanel`，versionCode 4。

- Debug 与 Android 测试 APK 构建成功；13 项单元测试通过，Lint 0 错误、8 项警告。
- 5 项真机测试通过（约 6.2 秒），包括真实 YoPilot 文档上的双向主体侧滑回调，及横向控件和输入框优先仲裁、DNS 字体/搜索样式、代理底栏按钮边界与两项真实服务接口测试。
- 已覆盖安装到目标手机，原配置保留，APK 同时位于手机 Download。
- 首页截图确认服务卡片、各自的资源/运行时长、流量区、累计统计、更新时间和服务设置全部显示在同一屏内，无需纵向滚动。独立资源区、重复状态区、快捷操作和事件列表已移除。
- Mihomo 内存读取 `/connections.memory`，已核实真实值并显示。当前核实接口未提供 CPU 和运行时长，显示“接口未提供”；不以 APP 或设备指标替代。
- APK 签名验证通过，SHA-256：`d8f125c484cccc7c4230eb59baafeccf7d867923367250dab4617f4497909b7a`。
- 本地 `key.txt` 已被忽略，不包含于源码、提交或归档；仓库提交规则见 AGENTS.md / agnet.md。

最新报告保存在本机 `dist/reports/0.1.3/`。截图验收基于当前目标手机竖屏；尚未执行横屏、超大字体与跨 Android 版本完整布局矩阵。底层指标与手势边界同历史记录。

## 0.1.2 历史验证记录

日期：2026-10-29。包名 `dev.netpanel`，versionCode 3。

- `assembleDebug`、`assembleDebugAndroidTest`、`testDebugUnitTest`、`lintDebug` 全部通过。
- 13 项单元测试通过；Lint 0 错误、8 项警告。
- 5 项 Android 真机测试通过（约 6.2 秒）：真实 DNS 健康与累计指标、Mihomo 当前节点与实时/累计流量、主体双向滑动及网页横向滚动/输入框优先、真实 DNS 搜索框单层边框和黑体、真实代理更多按钮完全处于底栏范围。
- 真机 DNS 截图确认首屏直接显示请求统计卡片，原运行概览及该页顶部品牌/搜索区隐藏，原网页导航保留；底部原生滑动提示区已移除。仅修改 APP 内置样式，未向服务端写入 HTML/CSS。
- APK 签名验证通过，已通过 ADB 覆盖安装并复制到手机 Download，保留原有配置。
- 最终 APK SHA-256：`2e44c11cdab2e7c83214edd94b88001907126d7f5343ab6a230dff135e7d71ee`。

构建日志、测试、Lint 和签名报告保存在本地 `dist/reports/0.1.2/`（构建产物不提交源码仓库）。测试设备与兼容性边界同下方记录。自定义网页手势不一定能从 DOM 属性识别；此类控件优先通过原有底部栏目按钮切换，不拦截所有横向操作。旧 WebView 不支持 CSS `:has()` 时可能仍显示 YoPilot 顶部品牌区，不影响统计和导航。

## 0.1.1 历史验证记录

日期：2026-10-29。包名 `dev.netpanel`，versionCode 2。

## 修复原因

首页最初的空白包含两项问题：配置为空，没有发起状态请求；DNS 指标正则中的字面量 `}` 没有转义，桌面 JDK 接受而 Android ICU 抛出 PatternSyntaxException，导致整组 DNS 数据误报无法连接。

先对照 GitHub 官方 OxiDNS / Mihomo API 与 MetaCubeXD 接入源码，再通过 ADB 连接真实 PKX110 手机调试。对旧解析器运行真机测试复现失败；安装修复版后，同一测试及 Mihomo 测试全部通过。未修改 DNS/代理核心、路由规则或防火墙。

## 实际结果

| 检查 | 结果 |
| --- | --- |
| assembleDebug / assembleDebugAndroidTest | 通过 |
| 桌面单元测试 | 13 项通过，0 失败、0 错误 |
| Android 真机接口测试 | 2 项通过，耗时约 1.2 秒 |
| lintDebug | 0 错误，8 项警告；报告保留原始警告 |
| APK 签名与版本 | v2 验证通过，0.1.1 |
| ADB 覆盖安装 | 成功，保留现有应用数据 |
| OxiDNS | 真实 1.6.1；健康、CPU、内存、运行时长、累计查询/异常均可读取 |
| Mihomo | 真实 v1.19.33；模式、活动连接、指定组节点、累计上下行均可读取 |
| 流量 WebSocket | 真机接收到实时样本及累计值 |
| 默认节点组 | `🚀 节点选择`，核对时选中 `已脱敏节点`，应用只读取并跟随变化 |
| MetaCubeXD 页面 | 已在 WebView 中显示真实后台、连接信息、累计流量，连接到本机控制器 |
| DNS 实际查询 | 对 example.com 发起一次真实 A 查询，返回 rcode=0；query_total 从 22750 增至 22751 |

手机系统报告 Android 17，WebView 155.0.8059.43。本次只验证这一台设备，不能据此声称所有 Android 版本均已通过。构建使用 JDK 17、Gradle 8.11.1、AGP 8.9.2、SDK 35。

报告：[构建日志](dist/reports/build.log)、[13 项单元测试](dist/reports/unit-tests.xml)、[旧解析器真机失败](dist/reports/android-before.txt)、[修复后真机通过](dist/reports/android-after.txt)、[真实 DNS 查询](dist/reports/live-dns-query.json)、[Lint](dist/reports/lint-results-debug.html)、[签名](dist/reports/apk-signature.txt)。

## 统计含义

- DNS 累计查询/异常来自 query_total / query_error_total，增量显示实际采样时间窗口。
- Mihomo 累计上传/下载来自核心 uploadTotal / downloadTotal 和 WebSocket upTotal / downTotal；不自行伪造历史累计。
- 当前接口不提供 Mihomo 累计 HTTP 请求数。活动连接按真实含义显示，不冒充请求数。
- DNS 刷新间隔可在设置中调整，默认 5 秒；流量按后端约每秒推送更新。后台暂停非必要原生请求。
- 本机服务候选须验证成功后才写入空白配置；其他端口、认证与已有设置仍由用户配置。

## 验证边界

- 主屏幕持续被切到其他应用时，停止坐标点击与截图，避免误操作其他应用；没有把其他应用的画面作为交付证据。
- 修复后的 API、统计解析、节点与实时流量已通过同一个生产 API 客户端的 Android 仪器测试；首页/DNS 页面最终视觉验收仍需保持 NetPanel 前台进行。
- 尚未执行跨 Android 版本矩阵、长时间功耗/性能测试、旋转与进程重建完整验收、需认证服务的完整 UI 流程、文件上传下载验收。
- 代理节点选择只做读取核对，没有为测试而改变实际代理选择。
- 网页滑动仍使用独立滑动区；blob/data、跨域或重定向下载交给外部浏览器。详见 README。
