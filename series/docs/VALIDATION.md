# 本地验证记录

2026-11-07 交接复核见 [统一证据](../../docs/handoff/2026-11-07/README.md)：在线构建遇到 Maven TLS 错误，离线构建 exit 0（24 任务 UP-TO-DATE，59 项现存 Java 报告）；本轮系列工具测试 20 项通过，项目虚拟环境导入检查及资源引用检查通过。四插件仍无服务器/原版客户端验收。

## 独立插件与独立 CE 包（2026-11-07）

- Doll、Tavern、Deco、Music 各自保留独立 `plugin.yml`、Shadow JAR 和 CraftEngine 内容 ZIP。每个插件只硬依赖 CraftEngine；Doll/Tavern 对厨房或彼此联动使用 `softdepend`，`common` 不作为服务器插件安装。
- `import_sources.py --module-archives-dir` 生成 `doll-ce-pack-0.1.0-dev.zip`、`tavern-ce-pack-0.1.0-dev.zip`、`deco-ce-pack-0.1.0-dev.zip`、`music-ce-pack-0.1.0-dev.zip`，另生成可选 `shared-ce-pack-0.1.0-dev.zip`。单包根目录直接包含 `pack.yml`，无需安装其他模块目录；总包模式仍保留兼容。
- 独立包逐个引用检查通过：Doll 729 物品/725 家具/3 配方，Tavern 190/39/114，Deco 854/785/0，Music 1/0/0；共享图集包无玩法定义但可独立校验。合并检查为 1774 物品、1549 家具、117 配方、2710 个可达模型、0 errors。
- `./gradlew -p series :doll:build :tavern:build :deco:build :music:build` 通过；产物为 `KaleidoscopeDoll-0.1.0-dev.jar`、`KaleidoscopeTavern-0.1.0-dev.jar`、`KaleidoscopeDeco-0.1.0-dev.jar`、`KaleidoscopeMusic-0.1.0-dev.jar`，各 Shadow JAR 均无未重定位的 `net/kaleidoscope/series` 类。系列 Python 工具测试 22 项通过。模块规格现在由 `tools/module-specs.json` 校验，发布器可同时收入根工程的 `cookery-ce-pack-1.3.0.zip`，形成 5 个 JAR + 5 个 CE 包的独立清单；本次开发整包 `series/build/distribution/series-five-independent-20261107-final.zip` SHA-256 为 `96eb17c7add4f0393ecd3eaad8fd04641055f23e1b656ef8a2ab227496d5ec69`。
- 以上是构建、包引用和元数据证据，不代表四插件已在 Paper/Folia 实服启动或经原版客户端验证；真人流程仍按下方验收表执行。

## 代码边界优化（2026-11-07）

- `SeriesScheduler` 集中处理玩家所属 Folia region 和延迟任务；停服关闭菜单仍允许执行，普通延迟任务在插件停用后自动丢弃。
- `FurnitureTypes.register` 对同一行为 ID 幂等，注册失败会移除占位标记，避免重载后半注册状态。
- `SavedState` 和 `CookeryStateEnvelope` 统一使用 namespace/schema/source/payload 信封，限制序列化大小；旧裸 payload 继续读取，未知命名空间、未来版本和坏类型拒绝操作。Doll 姿态、系列 PDC、Cookery bite/plate PDC 已接入，其他旧家具字段仍按兼容迁移逐步包裹。
- `InventoryTrade` 在完整计划通过后触发可取消 `InventoryTradeEvent`，玩家重入使用 UUID guard；Doll 的 `DollCollectedEvent` 与 Tavern 的 `DrinkCreatedEvent` 位于各自未重定位 API 包，未安装监听插件时发布方仍可运行。
- `Items.create` 使用 512 项有界模板缓存并在 CraftEngine reload 事件中清空，每次调用返回 clone；现有 `ControllerIndex` 和 Cookery `ChunkEntityCache` 继续作为已加载索引，不新增逐 tick 世界扫描。

下文为 2026-11-05 的历史构建检查点，数字与原始包只代表当时源码。新设备从 [根 README](../../README.md#开发交接2026-11-07) 接续；厨房当前证据不能沿用下文历史 29 项测试数字。

## 已执行并通过

- `gradlew.bat -p series build --console=plain`：新四个插件与 common 编译、JUnit 和 Shadow JAR 通过。新增 JAR 只声明最低 Paper API 1.21.11。
- Java：common 12、doll 10、tavern 27、deco 3、music 7，共 **59 项**，失败/错误/跳过均为0；数量由各模块JUnit XML汇总。
- `series/.venv/Scripts/python.exe -m unittest discover -s series/tools -p 'test_*.py'`：**18 项**工具测试通过。
- `import_sources.py --check`：固定源码导出的 catalog 与工程内容一致，参考 checkout HEAD 匹配并且 tracked clean。
- 包内引用检查：**1774** 物品定义、**1549** 家具、**117** 工作台配方、**2710** 个可达自定义模型；自定义父模型、具体纹理路径、家具与物品引用、共享文件冲突检查零错误。数量包含葡萄、瓶子、Deco状态的内部显示物品。
- 根工程 `gradlew.bat build --console=plain`：成功，全部任务命中已有有效缓存。厨房代码本轮未改动，已有 **29 项** Java 测试仍通过；不把增量执行描述成重新运行全部测试。
- 8 个官方仓库 HEAD 与来源记录一致，全部干净。原厨房工作树保留既有未提交改动。

本轮分发名为 `KaleidoscopeSeries-0.1.0-dev-gameplay.zip`，旧包保留。`package_dev.py` 再检查 JAR：catalog 必须与源码相同；无未重定位的共用类、CE/Paper/JUnit 类；plugin.yml 版本已展开；分发 ZIP 的 CRC 与逐文件 SHA-256 再读回一致。最终数量与哈希以包内 `BUILD-MANIFEST.json`、`SHA256SUMS.txt` 及旁置的同名manifest/checksum为准。

## 关键测试覆盖

背包交易覆盖付款不足、满包、付款释放槽位、部分堆叠、不同元数据、非法奖励不提交。存档测试覆盖缺字段/错类型拒绝和不把错误物品当成空槽。玩法测试覆盖抽奖队列、熟成时序、重叠候选原料、调酒边界、特调效果合并、姿态范围、MIDI tempo/多轨/SMPTE。catalog 测试确认24酒桶、6果盆、12调酒配方和效果标签，其中燃烧瓶没有上游饮品效果表，明确单列。

工具测试覆盖固定源转换的标签、循环/缺失标签、重复 JSON key、SNBT、模板继承、模型分支、掉落歧义；资源验证正确处理子模型覆盖父模型纹理，不把父模型未使用的默认路径误报为最终缺图。

本轮增加物品PDC完整状态/坏数据、投掷蓄力/弹跳/击退公式、葡萄年龄/概率、Deco规则匹配与循环状态图、附加函数拒绝、染色模型分支测试。龙头回归确认水瓶取出原版WATER药水、蜂蜜瓶和龙息使用实际物品ID，拒绝把其他药水存档识别为水；三个放置模型通过内部显示物品引用，缺模型时导出失败。

实现层做了已加载家具坐标索引、葡萄错峰检查、红石事件触发、移动实体区域调度、雪克杯状态变化时同步掉落物、支付前预编码，以及退出/死亡/传送清理。没有据此承诺实服MSPT或每项交互都已验证。

## 仍未证明的部分

未验证 CE 26.9.1 或其他版本实际加载导出配置、服务端启动、行为注册时机、真实饮用和恢复事件、Folia 线程边界、所有家具移除路径、显示包/碰撞、动画、资源托管与真人操作。新增松手调酒、玩偶蓄力释放、Deco座位PASS及停用清理都需运行验收。引用检查不包含所有原版内建纹理、字体、shader、模型渲染合法性和动态组件分支的视觉结果。

抽奖/酒桶/调酒代码的物品守恒测试不能替代真人操作；强制移除恢复只是 NBT 快照，需要管理员核对，不能声称所有故障自动无损恢复。饮品效果服务端等价实现的差异逐项记在 [PORTING-MATRIX.md](PORTING-MATRIX.md)。

保留一个果盆 `isOnGround` 弃用提示与 Gradle 未来版本提示；当前编译通过。完整目标仍保留，未完成项没有被标为已复刻。
