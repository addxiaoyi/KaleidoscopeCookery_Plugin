# 本地开发验证记录

最新源码交接：2026-11-07（Asia/Shanghai）。完整计划和换设备步骤见 [根 README](../README.md#开发交接2026-11-07)，本轮构建/依赖失败、离线校验、实际 Python 测试及可随 Git 获取的历史运行日志见 [交接证据](handoff/2026-11-07/README.md)。本次未新增服务器运行或玩家验收。

下方运行检查点日期为 2026-11-06。范围：厨房目录、食物/茶饮、效果基础系统、快刀/清扫与隔离测试服；目标 Paper/Folia 1.21.11、Java 21、CraftEngine 26.9.1。历史构建/运行记录不因 Git 交接升级成全系列完成；未发布、部署或操作生产服。

## 石磨配方、守恒与真实动物驱动（当前）

- `gradlew test shadowJar acceptanceJar --console=plain` exit 0，132 项 Java 测试无失败/错误/跳过；Python 全量 71 项通过。证据 `build/acceptance/millstone-build-v2.log`、`millstone-java-final.json`、`millstone-python-all-v4.log`。strict catalog、资源引用、分发 JAR 隔离和 `git diff --check` 通过。
- 85 条官方石磨来源中 82 条展开为 87 条具体输入配置。3 条分别缺 `forge:gravel`、`forge:sand/red`、`forge:sand/colorless`，没有擅自删掉标签备选项。全厨房 612 来源现为 462 imported / 150 pending；438 加工配置＋33 合成，271 物品、12 方块、180 状态、1 家具、279 个可达模型、18 音效别名。
- 新石磨配方复用 `accurate_foods`、`cook: MILLSTONE`、`rotations: 1` 与官方产量。旧配方默认四圈、已冻结计划和旧档圈数保留；品质继续走原厨房规则。精准配方探针按 `cook` 分类，原 4 蒸笼断言仍保留。
- 14 项石磨控制器回归覆盖取料/完成回调重入、回调移除家具、无效输入与角度、空或非法事件产物、第二个掉落取消、速度设置异常、完成异常/取消持久化阻塞、配方变化和坏计划。`millstone-controller-red-v5.log` 真实复现 4 项行为失败，`green-v6` 与最终全量通过；纯测试环境的 Material/Registry 初始化问题没有当作业务失败。
- 取料先清槽再发物品，转移中拒绝再次取/放；完成事件前先保存阻塞标记，失败时保留原料且不逐 tick 重试。实体先加入回滚集合，再设置速度；后续失败撤回已创建实体。撤回失败显式抛出并保持阻塞，不承诺任意第三方已主动搬走的物品仍能事务恢复。补箱恢复为完成、取料后和原空转周期，不每 tick 扫描驴箱。
- `cookery_millstone.py` 从固定 Java 模型提取 12 个 cube 并保持面 UV；父级 pivot/旋转的未知变化拒绝导入。模型回归包含实际失败的四种父级变化；新测试初稿的上表面 UV 期望按源 `-Y` 转目标 `+Y` 修正后通过。长杆用 fixed scale 恢复尺寸；插件六倍滚动、竖杆固定抬升、磨石 1.4 高度和 interaction 碰撞继续列为差异，客户端仍 NOT_TESTED。

首次实服 `millstone-v1`：Paper 三轮通过；Folia 首轮通过，第二轮未完成，保留 `folia/boot-2.log`。三个独立诊断副本复现：角度冻结在 78.29998，传送已完成、牛有效且拥有区域，CE 当前家具与 ticker 引用的家具却不同（`sameTickingFurniture: false`）。旧家具仍有效，CE 没有替换 ticker。源码和运行 JAR 的 `javap` 均核对；`millstone-diagnostic-v3` 记录引用身份证据。

修复限定为石磨 Folia ticker：经 CE 公开 `getLoadedFurnitureByMetaEntity` 查到当前控制器再 tick，不读取/改写 CE 私有状态、不增加第二套定时器。只读私有字段诊断仅存在独立探针 JAR。`millstone-folia-ticker-red.log` 先复现新控制器未被调用，最终全量通过。修复版独立双端三轮证据入口为 `build/acceptance/millstone-v2/acceptance.json`，验收摘要为 [cookery-millstone-acceptance.json](research/cookery-millstone-acceptance.json)。

`millstone-v2` 双端均完成三轮并正常停服。中途存档保留两根骨头、牛、家具及冻结计划；第二轮真实 tick 续磨只触发一次完成事件并掉落恰好 10 骨粉；第三轮空槽无新事件或产物。原厨房、219 炒锅、219 其他加工计划、33 合成、103 食物及 5 附魔回归同时通过。

| 平台 | cycle 1 / 2 / 3 | 安装文件与 manifest | 生成资源模型 | 最终 JAR |
| --- | --- | --- | --- | --- |
| Paper | PASS / PASS / PASS | 1,709 全部一致 | 1,251，无 Forge loader | 三轮哈希一致 |
| Folia | PASS / PASS / PASS | 1,709 全部一致 | 1,251，无 Forge loader | 三轮哈希一致 |

| 本轮产物 | SHA-256 |
| --- | --- |
| `build/acceptance/cookery-millstone-v2.zip`（2,270,578 字节） | `02ad7037bfcb603d3fa26910f80ec7271f26d405269275cdba82055f85099bf1` |
| `build/libs/KaleidoscopeCookeryPlugin-1.3.0.jar`（1,362,289 字节） | `e061bc28cf878cb915b21faf16657127cf62462091842788b399bab23f77a469` |
| `build/acceptance/probe/CookeryAcceptanceProbe.jar`（61,227 字节） | `ce6348e0f1460b358202fcec1711aa4e0f5b5e957a93ef3ba12abeee941ea83c` |

v1/v2 石磨资源 ZIP 逐字节一致；v2 增加的严格导入检查没有改变已知模型。验收不会扩大到真实玩家/原版客户端、保护插件、多人同家具、跨 Folia 区域或独立区块卸载。前序 CE 启动 watchdog 继续 OPEN，本次石磨 ticker 修复不作为该启动问题的修复证据。全系列和剩余 150 条厨房来源配方仍未完成。

## 炒锅精准/种类配方与存档（前序证据）

- `gradlew test shadowJar acceptanceJar --console=plain` exit 0；118 Java 测试，0 失败/错误/跳过，日志 `build/acceptance/pot-build-final.log`。Python 全量 64 项通过，日志 `pot-python.log`。strict catalog check、`git diff --check`、分发 JAR 排除 CE/Paper/JUnit/Mockito/探针类检查通过。
- 官方 225 条炒锅配方导入 195 精准＋24 种类。6 条 pending：4 条缺 `forge:mushrooms`、2 条引用未映射 `farmersdelight:cooked_rice`，没有删除备选项。整个厨房 612 来源中 380 导入、232 pending；最终 351 加工＋33 合成配置，264 物品、12 方块、180 状态、273 可达模型检查。
- 运行时按原料数量建不可变索引，精准 → 具体物品去重 → 旧余弦。与高汤锅共用增广匹配和品质偏差计算；产出数量固定。新增 `pot_recipes` 原子发布、白名单、容器映射和数量统计。导入菜单九格及原料备选说明只读，原 CE 名称/lore 保留；记录本和家具查询识别 `pot` 类型。
- 官方 `stir_fry_count` 固定到新批次，最后一次 24 tick 动画结束后出锅；`source_time` 保留来源时间但不参与完成门槛。无快照且已翻炒的旧批次仍使用旧余弦与厨具默认次数。断点动画/配方表变更、旧批次、坏计划/保留原料、部分取出保存有单元回归。
- 盛出先获取控制器防重入标记，再发可取消事件；取消、空成品、份数/容器变化均不扣容器。每份先扣锅中数量并标脏，再给物品；同步物品回调改变余量后终止后续盛出。不会将这组 mock 回归宣称为保护插件或多人实测。
- 首轮 `pot-v1/paper/boot-1.log` 捕获 CE 物品未注册时炒锅解析已运行，129 条失败导致整代配方回退。`PotRecipeParser` 改为等待 `POT_FOOD_RAW`（传递依赖 ITEM），新目录 `pot-v2` 干净启动通过。`pot-load-order-red.log` 为修复前失败，最终测试通过。导入回归 `pot-import-red.log`（219 != 0）→ `pot-import-green.log`；无效盛出回归 `pot-serving-red-verified-v2.log`；同步回调重复盛出回归 `pot-reentrant-red.log`（实际扣取次数过多）→ 最终全量通过。此前 Mockito/NMS 初始化失败日志单列，不充当业务失败证明。

| 验证 | 真实结果 | 边界 |
| --- | --- | --- |
| Paper | `pot-v2` cycle 1/2/3/4 通过，全部正常停服 | 最终 JAR 为 cycle 4 |
| Folia | cycle 1/3/4/5 通过，正常停服 | cycle 2 启动 watchdog 5.2812568 秒，仍 FAIL/OPEN；最终 JAR 为 cycle 5 |
| 炒锅 | 每轮 195 精准＋24 种类的匹配、官方参数、固定产物构造、计划序列化；2 个记录本序列化通过 | 原料/翻炒由区域内 API 驱动，无玩家操作 |
| 重启与盛出 | 两锅炒一次后停服；读回 1/3 次进度后续炒完成 3 烤土豆、1 拔丝土豆。分别取一份，再次重启余量 2/0、原料显示随份数回收、空锅无重复、油被消耗 | 原版客户端动画、玩家碗扣除、保护/多人/跨区域仍未测 |
| 原功能回归 | 132 原加工计划、33 合成注册/输出、103 消费品、5 附魔、12 方块/180 状态/264 物品；蒸笼/茶壶/砧板/高汤锅中途重启继续通过 | 合成网格/余物、客户端接包/画面未测 |
| 资源与产物 | 两端 1,705 文件与 ZIP manifest 一致；生成包各 1,247 模型无 Forge loader；重复导出 ZIP 逐字节一致 | 服务端生成资源不代表客户端已渲染 |

Folia cycle 2 在 `CraftEngine.runDelayTasks` 的 `CompletableFuture.join()` 采样，未到炒锅探针。首次启动通过并不能关闭这个问题；前序冷启动的 CEWorld/字体缓存采样也保留，未改 watchdog 阈值或豁免 ERROR。整体继续 `PARTIAL_FOLIA_STARTUP_WATCHDOG_OPEN`。

摘要 [cookery-pot-acceptance.json](research/cookery-pot-acceptance.json)，完整证据 `build/acceptance/pot-v2/acceptance.json`，日志 `pot-runtime-v2.log`、`pot-folia-resume.log`、`pot-final-runtime.log`。旧 JAR 保存在各实例 `artifact-checkpoints`。新副本均停服；没有重启既有实例或生产服。

| 当前产物 | SHA-256 |
| --- | --- |
| `build/acceptance/cookery-pot-v1.zip`（2,264,500 字节） | `f59fd9beeb7abd95ad34cd6c7d9411c1f7660977409b875c1d6c97835a690091` |
| `build/libs/KaleidoscopeCookeryPlugin-1.3.0.jar`（1,360,593 字节） | `5c686adf0392e5459d7ee625a2888c2340ac2c0e98db4bed2d55b63b7aaf1733` |
| `build/acceptance/probe/CookeryAcceptanceProbe.jar`（48,214 字节） | `86321b81fcf521be7bf76ecbbd7e51353a36dff1c4438c90f7b5f222f04df514` |

全系列仍未完成：232 条厨房配方、原料获取链、农夫套装、茶旗、厨师/交易/村庄结构、摆盘，以及 Doll/Tavern/Deco/MusicPlus 剩余服务端和资源功能，参见系列矩阵。

## 高汤锅精准/种类配方与存档（前序证据）

- `gradlew test shadowJar acceptanceJar --console=plain` exit 0，105 项 Java 零失败/错误/跳过；全部 9 个 NMS 适配编译任务通过。`python -X utf8 -m unittest discover -s tools -p 'test_*.py'` exit 0，61 项通过。日志 `build/acceptance/stockpot-build-final-v2.log`、`stockpot-python-final.log`。strict catalog check 与 `git diff --check` 通过。
- 配方导入回归先失败（101 != 0），通过后记录 `stockpot-import-{red,green}.log`；汤底返回物品缺失时会清空旧锅的回归先失败，修复后通过，记录 `stockpot-controller-red-verified.log`、`stockpot-unit-green.log`。最初 Mockito 缺 NMS proxy 的环境失败另存 `stockpot-controller-red.log`，不当作业务失败证据。
- 105 条官方高汤锅来源中导入 75 精准、26 去重种类配方；另 4 条缺 `forge:mushrooms` 明确 pending。全包 612 来源中 161 已导出、451 pending；最终 132 加工配置＋33 合成配置。未知字段、标签、循环、NBT、汤底、输出数量失败，只有明确列出的外部依赖允许 pending。
- 选择器按汤底/原料数量建不可变索引，精准优先、种类其次、旧余弦最后。备选原料用增广匹配，不能用贪心误吃重叠标签。品质沿用既有档位/缩放；官方产出数量不随重复原料扩大。无计划旧炖煮档依旧走余弦补齐；plan v2 可读 v1，模型/颜色及结果随批次持久化。
- 新高汤锅汤面 `default_cooking/default_finished/rice_finished` 引用官方贴图，显示实体高度按 `0.065 + 0.315 × remaining / maximum`，旧配方液面规则保持。原版 dust 替代官方有色泡泡。九格菜单只读、备选项/时间放独立说明按钮；原物品 lore 保留。配方记录本序列化和查询通过，玩家自动投料/家具画面未测。

| 验证 | 本轮真实结果 | 边界 |
| --- | --- | --- |
| Paper | `stockpot-v1` cycle 1–5 通过，正常停服 | 最终 JAR 是 cycle 5；前轮 JAR 在 artifact-checkpoints 保留 |
| Folia | cycle 2–5 通过，正常停服 | cycle 1 冷启动 watchdog 5.7309607 秒，继续 FAIL/OPEN |
| 高汤锅 | 75 精准/26 种类的匹配、时长、成品/模型构造、计划序列化；2 锅保存后续煮，米饭 3 份/骨汤 1 份 | 由 API 放置、盖盖和投料，无玩家点击 |
| 盛出/重启 | 取 1 米饭与整份骨汤后重启，剩余 2/0 份，液面 -0.105；空锅再取为空；碗载体与米饭成品模型保留 | 不证明玩家手中碗扣除、事件取消/重入、多人与保护 |
| 原加工与资源 | 132 加工计划、33 合成输出、103 消费品、5 附魔、12 方块/180 状态/263 物品通过；1,705 安装文件哈希匹配，1,247 生成模型无 Forge loader | 原版客户端接包/画面、合成网格/余物仍 NOT_TESTED |

Folia 本次采样在 `CEWorld.<init>` → `BukkitWorldManager.delayedInit`；此前字体偏移缓存/`runDelayTasks.join` 的失败证据仍保留，不能证明根因相同或已修复。整体继续 `PARTIAL_FOLIA_COLD_START_WATCHDOG_OPEN`，没有延长 watchdog 或豁免 ERROR。

当前摘要：[cookery-stockpot-acceptance.json](research/cookery-stockpot-acceptance.json)，完整实例 `build/acceptance/stockpot-v1/acceptance.json`；主测试服 `25581/25582` 未重启，副本 `25583/25584` 均已停服。最终包 `cookery-stockpot-v2.zip` 仅 manifest 差异说明相对 v1 改动，所有资源逐字节相同；另一次导出与 v2 完全一致。

| 当前产物 | SHA-256 |
| --- | --- |
| `build/acceptance/cookery-stockpot-v2.zip`（2,259,358 字节） | `8a5d15b1dbbeea64bdcff015c574a7823ff41e21315e9ccb0ad2f567d8862e33` |
| `build/libs/KaleidoscopeCookeryPlugin-1.3.0.jar`（1,345,410 字节） | `98208e978790ed694c6289390ed8df53f7b0a3c975c9a297fc01ce63c77e74f2` |
| `build/acceptance/probe/CookeryAcceptanceProbe.jar`（36,986 字节） | `4bb28c106ebadda9ef78bac0f94ad49a7268caa0a70f799a73ebdde4e8194fa5` |

生产 JAR 不含 CE/Paper/JUnit/Mockito/acceptance 类。两端 cycle 5 安装 JAR 与上述产物哈希一致。剩余 451 配方、实际原版客户端、获取链、套装/茶旗/村民/结构/摆盘和其他系列目标均未完成，未提交/推送/发布/部署生产服。

## 厨房配方与加工重启（前序证据）

- `gradlew test shadowJar acceptanceJar --console=plain` exit 0；扩展回归后 `gradlew test` exit 0，94 项零失败/跳过。Python 全部 58 项通过。日志：`build/acceptance/recipes-build.log`、`recipes-java-tests.log`、`recipes-python-final.log`。
- 奶桶业务回归先失败 3 项，再实现倒入/取回和守恒；详见 `teapot-milk-red-verified.log`、`teapot-milk-green.log`。新增错误手持物、返回物品缺失、创意模式、加工/成品拒绝装桶回归，水/岩浆原行为继续通过。
- `cookery_recipes.py` 从固定 Git blob 读取两个资源命名空间，共 612 配方、16 类型。47 条已导出为 11 茶壶（花茶展开 5 原料）、4 蒸笼、16 砧板、20 合成配方；其余 565 条保留具体 pending 原因。未知结构失败；Forge NBT 不会静默丢弃，回归 `recipes-nbt-red.log` 先失败，全量测试再通过。
- 首轮 Paper 实测发现源 `minecraft:chain` 不存在于目标注册表；`javap org.bukkit.Material` 确认目标是 `IRON_CHAIN`。显式别名修复后回归通过，首轮失败日志保留于 `recipes-v1/paper/boot-1.log`，先红后绿见 `recipes-chain-{red,green}.log`。

| 验证 | 当前证据 | 边界 |
| --- | --- | --- |
| Paper | `recipes-v2` cycle 1/2/3 均通过并正常停服 | API/存档注入原料，未模拟玩家 |
| Folia | cycle 2/3 通过并正常停服；恢复 3 组中途批次 | cycle 1 冷启动 watchdog 5.4575362 秒仍判 FAIL |
| 注册与产物 | 两端 31 加工计划匹配、时长、序列化、构造成品/副产；20 原版合成配方注册/输出 | 合成网格、容器余物、玩家拿取未验证 |
| 加工存档 | 蒸笼 4 原料运行 100 tick 后暂停；茶壶移走热源；砧板切 1 刀。重启续作产出 4 蒸食、8 份酥油茶、3 小肉块＋1 骨头；额外切割无产出；再次重启成品仍在 | 跨区域/多人/保护/区块卸载独立验证仍缺 |
| 资源 | 252 物品、12 方块、180 状态；两端安装的 1,702 文件哈希一致；生成 ZIP 1,244 模型无 Forge loader，茶壶字体存在 | 8 茶杯、6 HUD 字形、64 切割帧及基础汤面未由原版客户端目视验收 |

本次 Folia watchdog 采样在 CE `OffsetFont.createOffset` → `registerShiftTags` → `delayedLoad` 中的 Caffeine 类加载；前轮采样在 `CompletableFutures.allOf(...).join()`。尚未证明共同根因或修复，保留全部日志，不延长阈值或忽略 ERROR。整体状态仍 `PARTIAL_FOLIA_COLD_START_WATCHDOG_OPEN`。

可追踪摘要：[cookery-recipes-acceptance.json](research/cookery-recipes-acceptance.json)，详细证据 `build/acceptance/recipes-v2/acceptance.json`。新包 `build/acceptance/cookery-recipes-v2.zip` SHA-256 `6e5af0077d5dcfd92c0de4e5350e9a875bf16e908ff34454769cf1baaa1c5494`，重新导出逐字节一致；生产 JAR `475e4eaca71d4e27cc615cf2678bf596547d72b4c90d2cef283608509e659b37`，未包含 CE/Paper/测试/探针类。旧运行证据和系列发布包保留。

仍未闭环：565 条厨房配方（含外部 Forge 标签、原料生长/获取、精准/模糊匹配冲突）、每配方汤面/动态高度、原版客户端、玩家交互、多人/保护、套装/茶旗/职业/结构/摆盘。

## 厨具资源检查点（上一检查点，保留历史证据）

- `tools/export_cookery_kitchen_pack.py` 从固定提交生成食品＋厨具验收包：165 物品、12 方块、180 状态、174 个可达模型引用检查、12 个音效别名。原先缺失的 11 个厨具定义均已加载；油壶作为第 12 个方块。
- `python -m unittest discover -s tools -p 'test_*.py'`：45 项通过，exit 0，日志 `build/acceptance/kitchen-python.log`。搪瓷盆中档油面错误映射到低档的回归先失败后通过，见 `kitchen-import-red.log` / `kitchen-import-green.log`，未削弱断言。
- 包安装路径回归先暴露 `cookery_kitchen/../default_assets/...` 可越出目标包目录的问题（`kitchen-path-red.log`）；现同时拒绝 `..`、反斜杠和目标包外路径，失败前不移动原食品目录。修复后全套 45 项通过。
- `gradlew test shadowJar` exit 0；生产类未改，84 项 Java 测试为 Gradle 缓存命中，日志 `kitchen-java.log`。`gradlew acceptanceJar` 实际编译扩展后的探针并成功，日志 `kitchen-probe-build.log`。
- 新副本在 `build/acceptance/kitchen-v1/{paper,folia}`，端口仍为隔离副本端口 `25583/25584`；原先 `25581/25582` 实例未重启。所有本轮副本已正常停服。

| 验收 | 实际结果 | 限制 |
| --- | --- | --- |
| Paper | 冷启动与重启通过；最新 cycle 3：165 物品构造/序列化、12 方块、180 状态/控制器、12 方块重启读回，均零失败 | 放置走服务端 API，未模拟玩家 |
| Folia | cycle 2/3 重启通过；最新 cycle 4 同样 165/12/180、重启读回 12，均零失败 | 首次冷启动 watchdog 5.1684424 秒，严格验收判 FAIL，仍 OPEN |
| 内容持久化 | 搪瓷盆打开状态与 7 份油、油壶 87 份油跨重启保留；同时检查沙威玛上半方块保留 | 不等于烹饪流程或多人守恒验收 |
| 食物与附魔 | 继续通过 103 食物、5 附魔等级及 5 个附魔物品重读 | 获取/铁砧、实际消费仍未测 |
| CE 资源 | 生成/引用验证/压缩成功；两服各 1,618 个安装文件 SHA-256 与包 manifest 一致；生成 ZIP 的 1,168 个模型 JSON 无 Forge loader | 原版客户端接包/显示为 NOT_TESTED |

Folia 失败堆栈落在 CE 26.9.1 `CraftEngine.runDelayTasks:282` 的 `CompletableFutures.allOf(delayedLoadTasks).join()`；这里只确认等待位置，尚未确定哪一个异步延迟加载任务耗时。保留 `folia/boot-1.log`，没有延长 watchdog 或过滤该错误。后续通过不覆盖冷启动失败。

证据摘要：[cookery-kitchen-acceptance.json](research/cookery-kitchen-acceptance.json)。逐轮日志、探针、资源哈希和整体 `PARTIAL_FOLIA_COLD_START_WATCHDOG_OPEN` 在 `build/acceptance/kitchen-v1/acceptance.json`；旧探针与扩展探针哈希按 cycle 分别记录。启动脚本启用 `--kitchen-pack` 后不再允许缺失厨具错误，停服出现 ERROR 也判失败。

本轮产物：

- `build/acceptance/cookery-kitchen-v2.zip`：2,099,878 字节，SHA-256 `08d9391b75e755caf81fe6592dcd12e9c933554ebd2d882e5193d83cf8ac9e94`。
- `build/acceptance/probe/CookeryAcceptanceProbe.jar`：19,632 字节，SHA-256 `1c1ec949ff285b0bf94a24481ae867512b210a251f787b1fc719a33fa09da543`，仅用于隔离验收。
- 主插件 JAR 和目录快照哈希保持下文基线；已有系列开发 ZIP 未覆盖。

仍有明确差异：非炉灶厨具用共享透明细绊线状态加显示实体，不能宣称具有官方实体碰撞；茶壶支架和吊链暂取高汤锅支撑模型，须继续转换官方 Java 模型；`steamer` 的 TOP 兼容态为半笼平移；未启用水浸。茶杯/汤面、完整配方、食物摆盘、保护、多人与原版客户端均未完成。复现方法见 [厨房验收包](../examples/kitchen-pack/README.md)。

## 上一轮附魔基线（保留历史证据）

- `.\gradlew.bat test shadowJar acceptanceJar --console=plain`：exit 0，包含主源码、全部 9 个 NMS 适配、Shadow JAR 和独立验收探针。日志 `build/acceptance/enchantments-build.log`。
- Java：84 项通过，0 失败、0 错误、0 跳过；XML 位于 `build/test-results/test/`。新增附魔读取 10 项、砧板 12 项、镰刀 9 项；效果重入等 9 项保留。
- `python -m unittest discover -s tools -p 'test_*.py'`：30 项通过，exit 0，日志 `build/acceptance/enchantments-python.log`。
- `python tools/export_cookery_catalog.py --source ../.references/KaleidoscopeCookery --check`：exit 0，固定提交及 SHA-256 一致；148 foodProperties、94 food、8 tea、12 effect、2 enchantment。
- 新附魔数据包 JSON、物品标签引用对照实际 Mojang 1.21.11 server JAR 通过。CE 运行资源生成见下文；没有客户端渲染证据。
- JAR 检查：含 `CookeryEnchantments`，未打入 CE、Paper、JUnit、Mockito、未重定位 bStats 或 acceptance 类。验收探针独立打包。
- `git diff --check` 通过，官方参考 checkout 无改动。

镰刀先运行行为回归，9 项中 7 项失败，确认旧实现没有附魔范围和区块归属检查；实现后全部通过。保留 `build/acceptance/sickle-regression-red.log` 和 `sickle-regression-green.log`。测试依赖最初缺类及无服务器 Registry 的问题在测试环境修正，没有删减业务断言。

砧板回归覆盖 0/I/II/越界等级、奇偶切割次数、旧无参入口、存档重读后换刀、末刀一次性产出、产物不可用时保留原料和整数边界。镰刀检查逐格保护、未加载区块和其他 Folia 区域；这是模拟世界单元测试，尚非玩家区域边界验收。

## 上一轮隔离服务器证据

`build/acceptance/paper` 和 `folia` 的既有服保持在 `127.0.0.1:25581/25582`。本轮副本使用 `build/acceptance/enchantments-v4/{paper,folia}` 的 `25583/25584`，自行正常停服，不影响已有实例。

| 范围 | 已确认结果 | 边界 |
| --- | --- | --- |
| Paper 1.21.11-132-c5eb079 + CE 26.9.1 | 冷启动/重启各 103 种食物和 5 个原生附魔等级验证零失败，重启读回 5/5 | 仍有 11 个缺失厨具定义报错 |
| Folia 1.21.11-14-529aabc + CE 26.9.1 | 后续两次重启均食物 103/103、附魔 5/5、旧物品重读 5/5，探针零失败且正常停服 | 首次 CE 初始化触发 global region 5.4128759 秒 watchdog，冷启动问题保持 OPEN |
| CE 资源包 | `ce reload pack` 执行生成、引用验证、压缩和本机托管；每轮资源 SHA-256 写入 smoke 记录 | 仅默认 CE + 食物包 + 图标，不包含缺失厨具；客户端 `NOT_TESTED` |
| 原版玩家 | `playerFlow: NOT_TESTED`、`clientResourcePack: NOT_TESTED` | 未宣称完成实际切菜、收割、接包或多人操作 |

可追踪摘要：[cookery-enchantment-acceptance.json](research/cookery-enchantment-acceptance.json)，记录构建、服务器、数据包、CE 源资源与运行日志哈希。原始证据入口 `build/acceptance/enchantments-v4/acceptance.json`，逐轮 `paper/boot-*.log`、`folia/boot-*.log` 与 `smoke-*.json`。首次 Paper 标签错误、脚本命令错误和 ANSI 日志识别超时保留在 `enchantments`、`enchantments-v2`、`enchantments-v3`，不作为通过记录。Folia 首次 watchdog 保留在 `enchantments-v4/folia/boot-1.log`，不静默排除。

复现命令（需要已有 `tools/prepare_test_servers.py` 的运行依赖及食品测试资源；EULA 已由用户在本会话明确接受）：

```powershell
python -X utf8 tools/run_enchantment_acceptance.py --accept-eula --root build/acceptance/enchantments-next
```

脚本只创建全新隔离目录，拒绝覆盖旧证据；两次启动间检查物品二进制重读，处理带颜色的控制台日志，任何新增 ERROR 会失败。完整例外清单仅为上述 11 个缺失厨具定义，仍写入结果。Folia 冷启动耗时异常需要继续定位，后续成功不能抹去该失败。

## 本地产物

`build/libs/KaleidoscopeCookeryPlugin-1.3.0.jar`

- 大小：1,325,139 字节。
- SHA-256：`d9b036bbacbea71459feff5ed6ac1d1b2088e3458d48b69279fedf0d43d5e405`。
- 仍沿用原项目 `1.3.0` 文件名，是本次本地开发构建，不能与原版发布产物混同。
- 上一轮附魔探针保留在 `build/acceptance/enchantments-v4/paper/plugins/CookeryAcceptanceProbe.jar`：10,106 字节，SHA-256 `5c41ad1df491fecafee9ee9c4d100fca4c69a60db9ae8a9f5b522317bb7ea421`。当前构建探针已扩展为本轮厨房版本。
- 原生附魔注册样例位于 `examples/cookery-enchantments`；不是完整附魔书获取/铁砧系统。

## 构建修复

原失效 `sparrow-reflection-proxy-scanner:1.1` 无源码引用，已移除。CE 基线维持 26.9.1。

本机 Windows 原生编码 GBK，JDK 21 启动器按原生编码读取 classpath 参数文件；Gradle 默认 UTF-8 导致中文 checkout 下测试类无法加载。Gradle daemon 与 Javadoc JVM 明确使用 `file.encoding=COMPAT` 后，参数文件读写编码一致；Java 编译、资源处理、文档输出和测试内容继续显式使用 UTF-8。保留既有 Gradle 弃用和 Java deprecated/unchecked 提示。

行为测试需要 CE API 未发布的 relocated 运行类，因此增加同版本官方 Modrinth artifact、Netty、Authlib 和已有目标 Spigot NMS 作为 testRuntimeOnly；不进入分发 JAR。

## 尚未验证

实际玩家、保护插件、多人同家具、世界切换、物品守恒、竹筛漏斗取消与碰撞/堆叠展示、完整厨房 CE 包仍未验收。附魔获取/铁砧、农夫套装、茶旗染色图案、厨师交易/五类村庄结构、各系列剩余功能保留在实施表中。没有生产部署。

当前示例模型和碰撞存在差异，每槽展示也尚未对齐官方数量堆叠。完整森罗系列中的自定义效果、玩偶、酒馆等工作保留在 [实施表](IMPLEMENTATION-ROADMAP.md)，不得标记为已复刻。

2026-11-04 历史基线：竹筛 `build` 含 Javadoc 通过，29 Java / 15 Python；当时 JAR SHA-256 `3971624172afb5020885f3d6891a8ec443c8e2f8960a4487de06ed843db52ab3`，日志 `build-bamboo-tray.log`。该旧产物不代表本轮附魔代码。
