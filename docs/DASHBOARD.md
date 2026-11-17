# OxiDNS Dashboard 单文件面板

源码：https://github.com/gukey/oxidns-dashboard （MIT，基于 Luke-Lab666/oxidns-dashboard）。NetPanel 0.1.4 通过原有 DNS Web 地址加载此面板，不在 APK 内嵌 DNS 或代理核心。

当前部署固定使用提交 `1cc512d`，单文件 HTML 内已保留原作者 MIT 许可声明。默认风格为液态玻璃；已修复手机侧栏被遮罩覆盖而无法选页的问题。

## 构建与部署

1. 克隆上述仓库，使用 Node.js 22 或更新版本执行 `npm ci`、`npm run build`；产物只有 `dist/index.html`，CSS 和 JS 均已内联，不依赖 CDN。
2. 保留已有 OxiDNS 配置中的 `api.http.webui.root` 和 API 认证。备份旧 WebUI 目录到服务目录之外，清理在线目录的旧 YoPilot 页面、脚本、样式和静态资源，再放入新 `index.html`。不要删除核心、配置、规则、证书或配套强制转发服务。
3. 如需从空部署开始，可使用以下配置片段，路径按实际安装位置填写。已有部署不必为了更换面板修改配置：

   ```yaml
   api:
     http:
       listen: "127.0.0.1:9199"
       webui:
         root: "/path/to/webui"
         index: "index.html"
   ```

4. 浏览器打开原 Web 地址验证；NetPanel 设置中的 DNS Web 地址也使用该站点。APP 若显示旧页面，先刷新或清理 WebView 缓存。原生首页使用原有 API 配置，与网页主题独立。
5. 检查累计请求、查询日志、缓存、系统与外观页面。数值来自现有 API；插件或构建特性未提供时，以面板提示为准。不要以可打开 HTML 代替接口验收。

面板从同源 `/api` 访问 OxiDNS。远端 API 或认证代理的配置方法见面板仓库 README，不要把凭据放进 URL。HTTP/HTTPS 及 CORS 使用实际部署的安全限制，不关闭证书校验。

单文件指面板产物与运行依赖均为一个 HTML。已有规则更新脚本可能继续在 WebUI 目录生成 `rule-update-status.json` 等状态数据；这是原服务的输出，不是新面板依赖，不能为了目录文件数而修改原服务脚本。

## 强制转发

新导航的“强制转发”复用已有手机配套服务，连接 OxiDNS API 相同主机的 `9198/api/force-forward`。这不是 OxiDNS 官方内置接口；只部署单文件 HTML 不会创建强制转发后端。普通安装未部署该服务时会提示不可连接并禁止提交，其他页面仍可使用。

保留原服务的规则列表、代理组、版本校验和应用/回滚状态，支持 DOMAIN、DOMAIN-SUFFIX、公网 IPv4 / CIDR、批量去重、编辑、搜索及确认删除。后台负责安全校验和事务；前端不直接修改服务文件。不会把 OxiDNS 的 Basic Auth 凭据发往 9198 服务。

## 外观与回退

中文正文默认无衬线黑体，代码使用等宽字体。顶栏调色板提供五种风格及浅色/深色/跟随系统；存储受限时仍可切换当前会话，小屏弹层可滚动。NetPanel 不再对 DNS 页面注入 YoPilot 样式。

如新面板与特定 OxiDNS 版本不兼容，将在线目录换回此前的完整备份即可，无需恢复 DNS 或 Mihomo 配置。备份不应放在可下载的 WebUI 根目录内。

## 测试边界

面板仓库使用 Playwright 验证主题、小屏和强制转发增删改异常路径，采用隔离测试接口，不拿生产规则做试验。手机验收只读取现有强制转发状态、切换主题及验证原生侧滑，核心配置与路由保持不变。详细本轮执行结果见仓库 `VERIFY.md`。
