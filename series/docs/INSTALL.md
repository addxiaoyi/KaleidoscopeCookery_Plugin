# 森罗系列本地开发包 0.1.0-dev

包含5个 JAR：原厨房开发版，以及玩偶、酒馆、装饰、音乐四个新增插件。**不是全功能复刻完成版，也没有服务器/原版客户端运行验收。**

新增插件使用 Java 21，编译目标 Paper 1.21.11 / CraftEngine API 26.9.1；其他组合尚未验证。先在测试服安装。

1. 停服，把 `plugins/` 中需要的 JAR 复制到服务器的 `plugins/`。每个新插件只硬依赖 CraftEngine，common 已 shade 进对应 JAR，不单独安装；Doll/Tavern 的跨玩法联动仅使用 soft-depend。
2. 只安装所需模块对应的 CE ZIP：例如 Doll 复制 `doll-ce-pack-0.1.0-dev.zip` 的内容到 `plugins/CraftEngine/resources/doll/`，确保该目录直接有 `pack.yml`。需要共享原版图集时再复制 `shared-ce-pack-0.1.0-dev.zip` 到 `resources/shared/`；升级时只替换对应模块目录，不同时加载两套同名定义。
3. 厨房完整 CE 包需沿用现有包；这里没有打包完整厨房美术资源。新 CE 内容 ZIP 也不能直接发送给客户端。
4. 启动服务器并检查加载日志。按目标 CE 版本执行配置加载、最终资源包生成和发送；当前文档使用 `/ce reload all`，仅 `/ce reload` 默认不重建客户端包。
5. 原版客户端接收生成后的资源包，逐项执行 `docs/ACCEPTANCE.md`。

OP 获取测试物品：`/kdoll kit`、`/ktavern kit`、`/kdeco`、`/kmusic kit`。普通入口：`/kdoll` 收藏、`/ktavern` 配方、手持乐器 `/kmusic`。MIDI 放 `plugins/KaleidoscopeMusicPlugin/songs/`，用 `/kmusic play 文件.mid` 播放。

酒桶先加入4桶同种液体，再加四槽以内原料，空手潜行封盖；果盆投料后跳踩；雪克杯投三份原料后空手右键开始、再次右键停止，空鸡尾酒杯取出。酒类调酒要求熟成4级以上。

本轮新增玩偶蓄力投掷/弹跳/骑乘/幻翼，三类葡萄，龙头装瓶和红石触发，手持摇杯/倒酒，以及Deco状态切换和沙发染色。详细操作和原版客户端替代差异见 `docs/GAMEPLAY.md`。

`docs/PORTING-MATRIX.md` 保留全系列未完成项：精确玩偶物理/自定义Bedrock、野葡萄、燃烧瓶、剩余酒馆家具、Deco复杂脚本/连接、原音乐模型/合成器等。效果、存档、领地保护及Folia边界均需实服验收。

`BUILD-MANIFEST.json` 和 `SHA256SUMS.txt` 记录构建产物、测试和包内文件哈希；`organization-checkouts.json` 记录8个官方仓库来源提交；原资源说明和许可位于各 CE 内容包的 `upstream-notices/`。

有库存/加工中的家具拒绝普通拆除；外部强制移除若经过 CE 回调，会在对应插件目录 `recovery/` 保存 NBT 内容快照，需管理员核对后人工恢复，不会自动退款。不要用热加载插件工具测试这些存档流程。
