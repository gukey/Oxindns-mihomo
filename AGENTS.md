# NetPanel 开发与提交要求

- 仓库：https://github.com/gukey/Oxindns-mihomo.git
- 默认分支：`main`。保留远端历史，不使用强制推送覆盖他人提交。
- 每次完成修改后，执行与改动相关的构建、检查及必要测试；通过后自动提交并推送到上述仓库，无需重复请求确认。
- Git 提交标题和说明使用中文，描述最终改动及必要验证结果。
- 如验证失败，应先修复；无法解决时如实说明，不将失败检查描述为通过。如推送失败，保留本地提交并明确说明原因。
- GitHub 凭据仅从本机 `key.txt` 或已配置的安全凭据管理器读取。不得在日志、工具输出、源码、提交信息、远端 URL 中暴露凭据。
- `key.txt` 必须保持 Git 忽略状态；禁止提交凭据、Keystore、设备配置、构建缓存或包含敏感信息的调试日志。
- 不修改 OxiDNS 或 Mihomo 核心服务、实际代理节点、服务配置或防火墙。APP 复用现有后台；网页显示调整仅在 APP 内生效。
- 概览使用真实 API 数据；接口未提供的指标明确标注，不伪造。
- 用户已授权通过 ADB `192.168.1.1:5555` 覆盖安装新版并保留配置。调试坐标输入和截图前确认 NetPanel 位于前台，避免操作其他应用。

构建环境：JDK 17、SDK 35、Gradle Wrapper 8.11.1。常规验证：`assembleDebug testDebugUnitTest lintDebug`；涉及 Android/WebView 差异时执行相应真机测试。构建及测试说明见 README.md 和 VERIFY.md。
