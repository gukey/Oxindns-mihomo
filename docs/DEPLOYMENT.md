# OxiDNS + Mihomo + NetPanel 安卓部署教程

本文配套 [NetPanel Releases](https://github.com/gukey/Oxindns-mihomo/releases)。以 **NetPanel 0.1.3、Root 模块 0.1.3、OxiDNS 1.6.1、Mihomo 1.19.33、MetaCubeXD 1.273.1** 为基线。后续版本请同时阅读对应发布说明。

## 1. 先了解三个组件

| 组件 | 用途 | 是否需要 Root |
| --- | --- | --- |
| NetPanel APK | 原生概览 + 两个 WebView 管理入口；不启动代理或 DNS 核心 | APP 本身不需要 |
| OxiDNS | 解析 DNS，提供管理 API 与官方 WebUI | 普通高端口可不需要；模块以 Root 启动 |
| Mihomo | 提供显式代理、可选 TUN 和 External Controller | 显式代理可不需要；安卓 TUN 需要相应权限 |
| 配套 Root 模块 | 开机启动上面两个核心、保存独立配置、提供命令行启停 | 需要已有 Root 管理器 |

本教程不包含任何人的节点、订阅、账户和规则缓存。默认配置只有 `DIRECT`，可以启动、管理和验证 DNS，但**没有提供代理节点，也不会自动让全机走代理**。

请求路径：显式代理客户端 → Mihomo `7890` →“节点选择”组；Mihomo DNS → OxiDNS `5335` → 公共 DNS。NetPanel 读取两个 API，不参与数据转发。

## 2. 下载哪些文件

从同一版本的 Releases 下载：

- `NetPanel-0.1.3-debug.apk`：可安装 APP，当前为 Debug 签名构建，不能冒充正式商店签名包。
- `NetPanel-Root-0.1.3-arm64.zip`：**可刷入的 Root 模块**，ZIP 根目录有 `module.prop`。
- `NetPanel-Configs-0.1.3.zip`：独立通用配置和参考片段。
- `DEPLOYMENT.md`：本文离线副本。
- `SHA256SUMS.txt`：上述文件的校验值。
- `NetPanel-Upstream-Sources-0.1.3.zip`：模块中第三方核心/面板对应的上游源码与许可证，不是可刷入模块。

GitHub 自动生成的 `Source code.zip` 是项目源码，也不能当 Root 模块刷入。第三方组件来源及精确提交见 [THIRD_PARTY.md](THIRD_PARTY.md) 和 `root-module/upstream.lock.json`。

检查下载完整性：Linux/macOS 可在同一目录运行 `sha256sum -c SHA256SUMS.txt`（macOS 也可使用 `shasum -a 256` 单独计算）；Windows PowerShell 运行 `Get-FileHash .\NetPanel-Root-0.1.3-arm64.zip -Algorithm SHA256`，与清单相应一行比较。不要只看文件大小。

## 3. 设备条件与现有部署

1. Android 8.0 以上，CPU 为 `arm64-v8a`，有可用的 Android System WebView。`adb shell getprop ro.product.cpu.abi` 可查看 ABI。
2. 已安装并配置 Magisk、KernelSU 或 APatch。本文不指导解锁和获取 Root，不能把 ZIP 放进 Recovery 刷入。
3. 首个模块按这些管理器的标准脚本结构制作；本次验证包括目标安卓设备上的隔离安装脚本和核心运行，不等同于每种 Root 管理器、ROM 的刷入/重启兼容验收。
4. 如已有 Mihomo、Clash、OxiDNS、透明代理或 DNS 重定向模块，先备份并检查端口/路由冲突。**不要同时启用两套接管规则**。新模块不会替你关闭旧服务或清空防火墙。
5. 有现有部署且工作正常时，可以只装 NetPanel 并填写原服务地址，完全不刷这个模块。

迁移时先保留旧配置和旧模块安装包。通过旧方案自身的停止功能停止服务，在 Root 管理器禁用旧模块，再安装新模块并重启。禁用模块不一定会停止当前正在运行的进程，重启后再验证。不要照抄别人的 `pkill`、`iptables -F` 等命令。

## 4. 安装 Root 模块与 APP

1. 在 Root 管理器选择“从本地安装”，选择 `NetPanel-Root-0.1.3-arm64.zip`。
2. 阅读安装输出。非 arm64 或过旧安卓会终止安装；已有用户配置不会覆盖。
3. 重启手机，等待系统开机完成。
4. 安装 APK：文件管理器打开 APK，或使用 `adb install -r NetPanel-0.1.3-debug.apk`。
5. APP 不申请 Root。需要修改服务配置和启停时，使用已经授权的 Root 终端或 Root 管理器。

模块默认不启用 TUN，不改 Android 系统代理、私人 DNS、热点转发、SELinux 或自定义 iptables 规则。它只在开机时启动两项服务，没有额外的自动重启守护进程。核心异常退出后先看日志，再手动启动；没有承诺在崩溃后无限重试。

### 默认地址

| 功能 | 地址 | 认证 |
| --- | --- | --- |
| OxiDNS WebUI | `http://127.0.0.1:9199/` | Basic Auth，用户名 `netpanel`，随机密码 |
| OxiDNS 管理 API 根地址 | `http://127.0.0.1:9199` | 同上，**不要附加 `/api`** |
| OxiDNS DNS | `127.0.0.1:5335`，UDP/TCP | 无；仅监听本机 |
| Mihomo MetaCubeXD | `http://127.0.0.1:9090/ui/` | 面板连接配置使用 Controller 和 Secret |
| Mihomo External Controller | `http://127.0.0.1:9090` | 安装时随机生成 Secret |
| Mihomo HTTP/SOCKS 混合代理 | `127.0.0.1:7890` | 仅监听本机，不允许 LAN |
| Mihomo 内部 DNS | `127.0.0.1:1053`，UDP/TCP | 转发到 OxiDNS |

这里的 `127.0.0.1` 是**手机本身**。在电脑浏览器输入这个地址会访问电脑，而不是手机。远端/路由器部署见第 11 节。

### 目录

```text
/data/adb/modules/netpanel_services/    # 模块文件、二进制与只读 WebUI
  ctl.sh                              # 配置检查、启停和状态
/data/adb/netpanel/                    # 持久化用户数据，目录权限 0700
  oxidns/config.yaml                  # OxiDNS 私有配置，权限 0600
  oxidns/webui -> 模块的官方 WebUI
  mihomo/config.yaml                  # Mihomo 私有配置，权限 0600
  mihomo/ui -> 模块的 MetaCubeXD
  mihomo/providers/                   # 用户自行导入的订阅缓存，勿分享
  logs/                               # 本机私有运行日志
  run/                                # PID 和管理命令互斥锁
```

升级模块不会覆盖两个 `config.yaml`。WebUI 链接由启动脚本创建；如果使用者已有自定义 UI 目录，不会覆盖其内容。

## 5. 认证与 NetPanel 配置

安装器分别为两个服务生成随机凭据，不把密码显示到安装日志，也不使用公开的统一默认密码。使用本机 Root 文件管理器查看：

- DNS 密码：`/data/adb/netpanel/oxidns/config.yaml` → `api.http.auth.password`。
- Mihomo Secret：`/data/adb/netpanel/mihomo/config.yaml` → `secret`。

如使用 ADB，可先 `adb shell`，再 `su`，然后在自己的终端查看文件。不要把文件、输出或带凭据截图贴到公开 Issue，也不要把凭据拼接到 Web 地址的查询参数中。自己改过凭据后，以当前配置文件为准。

打开 NetPanel → 右上角设置：

1. **OxiDNS**：Web 地址和 API 根地址填写表中地址；认证用户名填 `netpanel`，密码填本机生成值；点击连接测试，保存。
2. **Mihomo**：Web UI 地址、External Controller 分别填写，Secret 填本机生成值；代理组名称填 `节点选择`；连接测试并保存。
3. 首次打开 DNS 页，使用原后台认证；配置正确时 APP 会按现有 Basic Auth 流程登录。
4. 打开代理页，在 **MetaCubeXD 自己的连接配置**中填写 `http://127.0.0.1:9090` 和 Secret。APP 的 Secret 仅供原生首页请求使用，不会通过 URL 或 JavaScript 偷传给面板，两处配置不能混为一谈。
5. 因为通用模块开启认证，APP 无认证自动探测可能不成功；手工填写凭据后再测试即可。

模块附带的是 **OxiDNS 官方发行包中的 WebUI**，不是从开发者手机复制的 YoPilot 定制版。已有 YoPilot 用户可继续填写自己的后台地址，APP 不要求替换 WebUI。针对 YoPilot 的特定样式修补不会把官方 UI 重写为 YoPilot。

## 6. 验证服务真的可用

进入 Root 终端：

```sh
sh /data/adb/modules/netpanel_services/ctl.sh check
sh /data/adb/modules/netpanel_services/ctl.sh status
```

`check` 会执行两个**真实核心的配置校验**。`status` 只显示本模块记录且可核实执行文件的进程；不会把别的同名进程认作本模块服务。

随后检查：

1. 浏览器或 NetPanel 可以打开两个 WebUI。HTTP 401 通常说明服务已响应，但需要认证；不是服务停止。
2. NetPanel 首页 OxiDNS 显示版本、累计查询、资源、运行时长。首次采样的查询增量需要等待下一个刷新周期。
3. 首页 Mihomo 显示版本、模式、活动连接、当前 `节点选择` 组节点和内存。CPU/运行时长当前接入接口未提供，显示“接口未提供”；不是手机 CPU 为 0。
4. 用有 DNS 测试工具的终端执行 `dig @127.0.0.1 -p 5335 example.com` 及 `dig @127.0.0.1 -p 5335 example.com +tcp`。Android 默认可能没有 `dig`，可使用自己信任的终端工具；不要因缺命令而误判 DNS 故障。
5. 发起查询后检查 DNS 累计数增加。没有接入真实查询流量时，QPS 为零是正常现象。
6. 如终端有 curl，使用 `curl --proxy http://127.0.0.1:7890 https://example.com/` 验证显式代理链。默认 DIRECT 时，这只证明代理入口与直连出口工作，不证明使用了远程节点。

Mihomo API 的累计上传/下载不是“累计 HTTP 请求数”。重新启动核心或重置统计后，累计值可能回退。首页图表只保存当前会话最近 60 秒，不保存历史数据库。

## 7. 导入你自己的节点或订阅

先备份 `/data/adb/netpanel/mihomo/config.yaml` 到**本机私有目录**。可以通过可信的本机编辑器或面板支持的配置功能操作。

### 方式 A：自己的节点配置

用自己已有的 `proxies` 列表替换默认的 `proxies: []`，再把对应节点名加入 `节点选择.proxies`。保留 `DIRECT` 作为可用的回退项。不要使用本文之外的陌生免费节点配置。

### 方式 B：自己的订阅

参考 [mihomo-provider.example.yaml](https://github.com/gukey/Oxindns-mihomo/blob/v0.1.3/root-module/config/mihomo-provider.example.yaml)：

1. 把 `proxy-providers.personal` 块合并到完整配置。
2. 将 `.invalid` 示例 URL 改为自己有权使用的订阅链接；示例地址故意不可访问。
3. **替换**原有 `节点选择` 组定义，使其保留 `DIRECT` 并添加 `use: [personal]`。不要在 YAML 中粘贴第二个同名 `proxy-groups` 顶层字段。
4. 订阅需返回 Mihomo 可用的 provider 格式。通常 provider 文件是含 `proxies:` 的节点集合，不能把任意网站、HTML 或不兼容的 Base64 订阅直接当 YAML。
5. 如果订阅只能在代理网络访问，先用自己可信的方式导入一个可用节点或在本机生成 provider 文件，解决首次拉取的依赖问题。

修改后：

```sh
sh /data/adb/modules/netpanel_services/ctl.sh check
# 仅在校验成功后重启。
sh /data/adb/modules/netpanel_services/ctl.sh restart
```

在 MetaCubeXD 选择节点后，NetPanel 首页会在下个刷新周期跟随显示。节点名、地址、订阅链接、密码、UUID、证书私钥和 provider 缓存都属于自己的私有配置，不要上传到本仓库。

## 8. 让应用使用代理或 DNS

### 默认：显式代理

支持代理设置的客户端可填写 HTTP/SOCKS `127.0.0.1:7890`。这只覆盖这些客户端，不能承诺所有 Android 应用流量都被代理。

也可在当前 Wi-Fi 的代理设置中手动填写主机 `127.0.0.1`、端口 `7890`；**先记录原设置**，关闭服务前恢复。Wi-Fi 系统代理不是全协议透明代理，也不等于蜂窝数据接管。不要把 `http://` 加到“代理主机名”输入框。

默认模板只是开放本机 `5335` DNS 服务；Android 系统 DNS 设置通常不能直接指定任意 DNS 端口，不能简单把 `127.0.0.1:5335` 当作系统 DNS 主机名填写。需要由支持自定义端口的客户端、Mihomo DNS 或明确配置的接管路径使用它。

### 可选：Root TUN 接管

这是使用者自行选择的网络变更，模块安装器不自动启用。先确认内核有 `/dev/net/tun`、Root 权限可用，没有其他冲突的 VPN/TUN/透明代理接管，并准备离线恢复方案。

1. 备份完整 Mihomo 配置。
2. 参考 [tun.example.yaml](https://github.com/gukey/Oxindns-mihomo/blob/v0.1.3/root-module/config/tun.example.yaml)，**替换**完整配置里的 `tun` 块，不能重复粘贴第二个顶层 `tun`。
3. 保持示例的 Root UID 排除，避免以 Root 运行的 OxiDNS 上游请求重新进入代理 DNS 导致递归；理解自己修改后的路由再调整。
4. Android“私人 DNS”、浏览器安全 DNS、应用自己的 DoH 可能绕过普通 53 端口 DNS 接管。如目标是统一交给 OxiDNS，在系统/对应客户端中手动调整并记下原设置；模块不偷偷改变这些设置。
5. `ctl.sh check` 成功后 `ctl.sh restart`。检查 TUN 网卡、路由、代理出口、DNS 查询是否增加，并分别验证 Wi-Fi、蜂窝、IPv4/IPv6。示例默认关闭 IPv6，不能据此声称所有 IPv6 都受保护。
6. 若无网，立即 `ctl.sh stop`，恢复备份后再启动；不要反复叠加未知规则。不能进入终端时，在 Root 管理器禁用模块并重启。

TUN 的实际行为与核心、内核和 ROM 有关。规则说明参考 [Mihomo 官方 TUN 文档](https://wiki.metacubex.one/config/inbound/tun/)。此处给出可校验片段，不声称已经在所有设备完成流量接管验证。热点共享、Chron、厂商网络守护等不在这个通用模块中。

## 9. 日常启停、升级、卸载

```sh
sh /data/adb/modules/netpanel_services/ctl.sh start
sh /data/adb/modules/netpanel_services/ctl.sh stop
sh /data/adb/modules/netpanel_services/ctl.sh restart
sh /data/adb/modules/netpanel_services/ctl.sh status
```

- 这些命令需要 Root；`stop` 只停止本模块能核实归属的进程，不会对系统执行泛化 `pkill`。
- 核心日志在 `/data/adb/netpanel/logs/`，默认采用较低日志级别；启动时只保留当前与上一份日志。长时间持续报错时仍应检查磁盘使用，不能声称运行期间有日志轮转守护。
- 两条管理命令不能并发执行。若命令被强制中断留下 `run/ctl.lock`，先确认没有管理命令运行，再用 `rmdir /data/adb/netpanel/run/ctl.lock` 移除空锁目录。
- 升级：备份自己的配置，在 Root 管理器安装新模块并重启。模块保留配置，不能假定新核心版本自动兼容旧配置；升级后再次 `check`。
- 卸载：先恢复自己手动设置的系统代理/网络设置，执行 `ctl.sh stop`，在管理器卸载并重启。模块卸载脚本保留 `/data/adb/netpanel`，防止误删订阅和配置；不再需要时由使用者确认目录后手动删除。
- APK 可用 `adb install -r` 升级，保留 APP 配置。不同签名的构建不能直接互相覆盖。

## 10. 常见问题

| 现象 | 检查方向 |
| --- | --- |
| 模块启动提示已有实例 | 旧模块或开机脚本仍启动同名核心；新模块不会接管它，先按旧方案自身方式停止并禁用 |
| 首页“未配置”或认证失败 | 检查 API 根地址、随机密码/Secret，不能把 UI 地址当 API 根地址 |
| API 正常，代理面板没数据 | MetaCubeXD 自己也需要配置 Controller 与 Secret；检查浏览器同源、WebSocket、错误提示 |
| DNS 页界面与截图不同 | 本模块附官方 WebUI；手机定制 YoPilot 不会随模块复制发布 |
| 流量为 0 | 确认应用真的连接了混合代理或有效 TUN 路由；仅打开管理后台不会接管系统流量 |
| DNS 查询为 0 | 确认 DNS 请求真正发往 OxiDNS，检查私人 DNS/DoH、DNS 端口和循环转发 |
| 端口无法监听 | 检查端口冲突、配置、Root 权限和 SELinux 拒绝；不要全局关闭 SELinux 或 HTTPS 验证 |
| `.invalid` 订阅无法下载 | 这是脱敏占位符，必须替换为自己的有效订阅 |
| 显示“接口未提供” | 当前接入 API 没返回该指标，不以模拟值补齐 |
| 页面侧滑未切栏 | 横向列表、输入框、图表等优先使用自己的手势；在普通主体区域短距离横滑，或点击底部栏目 |

反馈时仅提供脱敏错误、版本、安卓/Root 管理器信息。不要上传整个 `/data/adb/netpanel`、配置文件、节点截图、订阅缓存或日志全集。

## 11. 使用远端/路由器上的服务

NetPanel 也能管理已有远端服务，无需手机安装 Root 模块。分别填写服务器实际 Web 地址和 API 根地址；示例手机回环地址不能直接用于远端访问。

若要在 Linux/OpenWrt 部署核心，按 [OxiDNS 官方安装文档](https://oxidns.org/quickstart) 和 [Mihomo 官方发行包](https://github.com/MetaCubeX/mihomo/releases) 选择对应平台，不要把模块内 Android Mihomo 二进制当 Linux 通用包。通用 YAML 可以作为起点，但监听地址、路径、认证与服务注册应按部署环境调整。

默认只监听 `127.0.0.1` 是为了本机使用。如确实需要 LAN 管理，使用者需明确配置监听地址、认证、防火墙白名单，优先用可信 HTTPS 反向代理。不要把 `9090`、`9199`、DNS 或混合代理端口直接暴露到互联网，也不要使用“跳过证书校验”解决部署问题。

## 12. 构建与验证说明

维护者可运行 `python tools/build_root_module.py`。脚本只从固定上游 URL 下载并检查 `upstream.lock.json` 中的 SHA-256，**不会访问手机目录、读取个人节点或 key.txt**。输出目录默认 `dist/release-0.1.3/`。

APP 构建方法见项目 [README](https://github.com/gukey/Oxindns-mihomo/blob/v0.1.3/README.md)。核心配置、模块生命周期和隐私检查的具体结果见 [发布验证记录](https://github.com/gukey/Oxindns-mihomo/blob/v0.1.3/docs/RELEASE-0.1.3.md)。Root 模块是否通过实际刷入、重启、TUN 接管必须以该记录为准，不把配置校验等同于完整网络接管测试。
