# 第三方组件来源与源码

NetPanel Root 包只组合未经修改的上游二进制及 WebUI，不重写 OxiDNS / Mihomo 核心。通用启动脚本和配置位于本仓库 `root-module/`。精确下载地址、提交与 SHA-256 以 `root-module/upstream.lock.json` 为准。

| 组件 | 版本 | 上游 | 对应源码提交 |
| --- | --- | --- | --- |
| OxiDNS 与官方 WebUI | v1.6.1 | https://github.com/svenshi/oxidns | `76259263122c60b2868cb78cd14ea7e6cb990c32` |
| Mihomo Android arm64 | v1.19.33 | https://github.com/MetaCubeX/mihomo | `3f27ef39f8c96bbfbc8b0b9bb10f5c9ef60149d8` |
| MetaCubeXD | v1.273.1 | https://github.com/MetaCubeX/metacubexd | `8bbc8f58fef71148a94fb5c0ff808f79b057337d` |

各项目的原始 LICENSE 保存在模块 `licenses/` 中。相同版本的 Releases 同时提供 `NetPanel-Upstream-Sources-0.1.3.zip`，包含上述精确提交的上游完整源码归档、许可证及校验锁；构建说明和依赖清单见各自归档中的 README、构建工作流、Cargo/Go/前端锁文件。发布源码是上游原样快照，不包含本机节点、订阅和用户设置。

本仓库新编写的 `root-module/*.sh`、Root 模块配置和打包脚本采用 GPL-3.0-or-later，许可证见 `root-module/LICENSE`。原上游代码以各自许可证和版权声明为准。此说明不替代第三方组件随附的完整许可文本。

本次 Android APP 沿用仓库现有源码和 Gradle 依赖，APK 没有内置这两个核心。模块包含的核心是单独发行的 Root 组件，APP 无需获取 Root 即可管理已启动的服务。
