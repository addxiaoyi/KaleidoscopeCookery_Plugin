# SenLuo 五插件安装指南（开发版）

目标组合：Paper/Folia 1.21.11、Java 21、CraftEngine 26.9.1。五个插件可以分别安装；CraftEngine 是唯一硬依赖，`series/common` 已内嵌到各系列 JAR，不需要单独安装。

1. 将需要的 JAR 放入 `plugins/`：`KaleidoscopeCookeryPlugin`、`KaleidoscopeDoll`、`KaleidoscopeTavern`、`KaleidoscopeDeco`、`KaleidoscopeMusic`。每个插件都可以单独安装，但对应玩法的 CE 包也要一起安装。
2. 将对应的 `*-ce-pack-*.zip` 放入 `plugins/CraftEngine/resources/`。需要跨模块共享图集时再放入 `shared-ce-pack-*.zip`。
3. 启动服务器并确认日志中没有 `Unknown behavior/item/recipe`。不要把 CE 内容 ZIP 当成原版客户端最终资源包；客户端包由 CraftEngine 生成并发送。
4. 首次安装先备份世界和 `plugins/CraftEngine/`。开发验收可使用 `series/tools/prepare_independent_servers.py` 生成隔离目录和 `INSTALL-MANIFEST.json`，该工具不会启动服务器。

只安装某一个系列时，不需要安装其他系列的 JAR 或内容包。跨插件联动使用 soft-depend/API；目标插件缺失时，发布方应继续工作。

当前版本仍是开发版。服务端静态检查通过不代表 Paper/Folia 启动、资源包下载或原版客户端画面已经验收。
