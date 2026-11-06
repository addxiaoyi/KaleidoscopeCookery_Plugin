# 厨房 CE 隔离验收包

固定官方 Cookery 提交 `2f4e386ce23f49a385ddf003c67fc6415c55417a`；Paper/Folia 1.21.11、Java 21、CraftEngine 26.9.1。生成器沿用现有厨具控制器及存档，不改旧厨房规则。

包中有 271 个 CE 物品、12 方块厨具、180 方块状态及 1 石磨家具。包含 103 可消费品、空杯、原料、工具、8 种去底板茶杯、64 切割阶段模型、6 茶壶字形、9 汤底映射及 3 个配方汤面模型。3 个 Forge 视角模型转成原版 `display_context`；18 个音效别名保留官方音频。石磨的底座、两根杆和磨石共 12 个 cube 来自固定 Java 模型。官方资产与许可证来自固定 Git blob；字形与奶液纹理由生成器创建，是原版客户端替代。

`recipe-parity.json` 逐条记录 612 个官方配方的原始 JSON、来源路径、SHA-256、导入状态及差异。462 条导出为 11 茶壶（花茶展开 5 原料）、4 蒸笼、87 石磨、16 砧板、101 高汤锅、219 炒锅、33 合成配置；150 条 pending。4 条高汤锅及 4 条炒锅缺外部 `forge:mushrooms`，另 2 条炒锅依赖未映射的 `farmersdelight:cooked_rice`。石磨 85 条来源中 82 条展开为 87 条具体输入，3 条分别缺 `forge:gravel`、`forge:sand/red` 和 `forge:sand/colorless`。其他未知加工标签、字段、NBT 或循环会使导出失败。合成仍不丢弃 Forge NBT/缺失备选原料。

高汤锅新增 `stockpot_recipes`，`match: exact` 按全部原料数量匹配，`distinct` 先按真实物品 ID 去重；均匹配汤底，再回退旧 `stock_flex_foods`。输出数量/时长来自官方；品质继续用原插件档位。新批次保存 cooking/finished 模型、RGB 及液面份数；旧 plan v1 和无计划旧存档继续原规则。菜单只读查看九格和备选原料，旧编辑入口仍服务原配方。

炒锅新增 `pot_recipes`（195 精准、24 种类），使用相同备选匹配和品质算法，固定官方产量。配置保存 `stir_fry_count` 与 `source_time`，只有翻炒次数参与完成判定，最后一次动画仍为 24 tick；不添加模组的独立时间门槛。进行中的批次不受后续配方改动影响，旧无快照批次仍沿用默认翻炒次数与旧余弦。

石磨复用 `accurate_foods`，`cook: MILLSTONE`、`result_count` 和 `rotations: 1`；旧配置默认圈数和旧存档不变。事件取消、空产物或掉落失败保留原料并持久化阻塞；允许玩家取回重投。补箱只在完成、取料后及既有空转间隔发生。模型保留原插件动画锚点和滚动倍率，父级变换变化会拒绝导入；官方竖杆抬升、磨石高度、实体碰撞和客户端画面仍有差异。

这是独立验收包，不能与 `cookery_foods` 或已有同名厨房配置叠加。原版客户端、完整获取链和玩家物品守恒尚未验收，不应直接作为生产整合包。

## 生成与验证

在仓库根目录执行，输出路径必须不存在；导出失败不会覆盖前一代 ZIP。

```powershell
python -X utf8 tools/export_cookery_kitchen_pack.py --source ../.references/KaleidoscopeCookery --output build/acceptance/cookery-millstone-next.zip
python -m unittest discover -s tools -p 'test_*.py'
.\gradlew.bat test shadowJar acceptanceJar --console=plain
python -X utf8 tools/run_enchantment_acceptance.py --root build/acceptance/millstone-next --kitchen-pack build/acceptance/cookery-millstone-next.zip --accept-eula --timeout 360
```

前置测试服由 `tools/prepare_test_servers.py` 准备。用户已接受本地测试服 EULA。运行器只创建 `build/acceptance` 下的新副本，监听 `127.0.0.1:25583/25584`，保留原 `25581/25582` 实例。旧食品目录移到副本内 `fixture-backups`；安装前校验包路径和全部哈希。未知 loader/模型引用/状态、manifest 不符及启动/停服 ERROR 都会失败，不豁免缺失厨具定义。

配方探针在 `acceptance-world` 所属区域执行：219 个加工计划（含 87 石磨）、219 个炒锅计划和 33 合成注册/输出检查；API/存档注入蒸笼、茶壶、砧板、两锅高汤锅及两锅炒锅，暂停后重启续作，再取出部分成品重启。石磨在独立区块 `(2,0)` 放置，以默认 40 秒/圈的成年牛驱动真实 CE tick；验证未完成存档、重启续磨产出 10 骨粉、再次重启空槽无重复。包含高汤锅包时运行器自动跑三轮；任何失败立即退出，日志保留。它不是玩家点击、工作台余物或客户端画面测试。

## 石磨证据

`millstone-v2` Paper/Folia 各三轮 PASS 并正常停服；默认 40 秒/圈的牛驱动重启续磨产出 10 骨粉，空槽再次重启无重复。两端各 1,709 安装文件与 manifest 一致，生成各 1,251 模型无 Forge loader。132 Java、71 Python 通过；资源 ZIP SHA-256 `02ad7037bfcb603d3fa26910f80ec7271f26d405269275cdba82055f85099bf1`。详见 [石磨验收 JSON](../../docs/research/cookery-millstone-acceptance.json)。历史 Folia 启动 watchdog 仍 OPEN。

## 前序炒锅证据

`build/acceptance/cookery-pot-v1.zip`：2,264,500 字节，SHA-256 `f59fd9beeb7abd95ad34cd6c7d9411c1f7660977409b875c1d6c97835a690091`，重复导出逐字节一致。118 Java、64 Python 通过；Paper cycle 1–4、Folia 1/3/4/5 通过，最终 JAR 在 Paper 4/Folia 5 验证。219 炒锅计划及原加工回归、两锅中途重启续炒、部分盛出后再重启 2/0 通过。

Folia cycle 2 启动 watchdog 5.2812568 秒仍 OPEN，未进入探针；首轮 `pot-v1` 的解析依赖失败与所有历史失败保留。两端 1,705 安装文件与 manifest 一致，生成包各 1,247 模型无 Forge loader。见 [炒锅验收 JSON](../../docs/research/cookery-pot-acceptance.json) 和 [验证记录](../../docs/VALIDATION.md)。

## 高汤锅历史证据与限制

`build/acceptance/cookery-stockpot-v2.zip`：2,259,358 字节；SHA-256 `8a5d15b1dbbeea64bdcff015c574a7823ff41e21315e9ccb0ad2f567d8862e33`。重新导出逐字节一致。105 项 Java、61 项 Python 测试通过。两端 263 物品/12 方块/180 状态及非空容器存档通过；1,705 个安装文件哈希一致，生成包 1,247 模型无 Forge loader。

Paper cycle 1–5、Folia cycle 2–5 通过并正常停服。3 份米饭和 1 份骨汤完成后分别取 1 份，再次重启剩余 2/0 份、液面 -0.105、空锅不重复产出。原蒸食/茶饮/砧板回归继续通过。最终 JAR 在 cycle 5 两端安装哈希一致。证据 `build/acceptance/stockpot-v1`，[验证记录](../../docs/VALIDATION.md) 与 [高汤锅验收 JSON](../../docs/research/cookery-stockpot-acceptance.json)；历史 ZIP/日志保留。

Folia cycle 1 冷启动出现 5.7309607 秒 watchdog，采样在 CEWorld 构造/BukkitWorldManager.delayedInit；前轮字体缓存/join 失败也保留。整体 `PARTIAL_FOLIA_COLD_START_WATCHDOG_OPEN`；后续成功不能关闭它，不豁免日志或延长阈值。

- 非炉灶厨具用共享透明细绊线和显示实体，精确 voxel 碰撞、水浸未完成。
- 茶壶支架/吊链暂取官方高汤锅支撑；蒸笼保留旧 `slab_type`，TOP 用半笼上移。
- 茶壶/油壶由控制器掉落带内容物的物品，基础 loot 为空；沙威玛基础掉落仅 lower；双层蒸笼保留额外掉落。玩家保护取消和真实取存未测。
- 奶桶茶壶和高汤锅无法构造返回桶时保留汤底有单元证据；官方汤面/高度有服务端证据，彩色泡泡用 dust 替代，动物桶 NBT/玩家容器操作及客户端画面未验收。
- 当前仍有 150 条配方、外部 Forge 标签、原料获取、摆盘、导入配方菜单编辑、保护、多人和原版接包待完成；最新证据见 [验证记录](../../docs/VALIDATION.md)。
