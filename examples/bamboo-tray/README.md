# 竹筛独立验证包

这是与本仓库插件配套的最小 CraftEngine 示例：一件可放置竹筛、合成配方、四槽玩法和 26 条原版物品配方。`optional/bamboo_tray_tea.yml` 另含 2 条茶叶配方。代码编译基线是 CE 26.9.1；实际服务器加载、碰撞、漏斗及玩家操作尚需按下列场景验收。

模型为本次自制的简单几何形状，引用客户端原版竹材贴图，没有包含官方美术素材。示例借用 `lower_tripwire` 外观状态；它的客户端碰撞/选框不等于模组的 2/16 实体薄盘。每槽目前只展示一个物品，原模组按数量最多展示五份。当前用于玩法验证，不能据此宣称外观和碰撞完全还原。

## 安装到测试服

1. 安装本仓库构建出的插件与兼容版本 CraftEngine。把 `pack/` 整个目录复制到 CE 的 `resources/` 下，例如 `resources/cookery_bamboo_tray/`。先备份已有配置。
2. 若已有 `kaleidoscopecookery:bamboo_tray` 物品或方块，不要再注册同名定义。把新 `kaleidoscopecookery:bamboo_tray` 行为与 `stand` boolean 属性合并到原定义，仅导入缺失配方。模型可以继续使用已有包内模型。
3. 在测试服执行 `/ce reload all`，检查控制台不存在方块、物品和配方解析错误，构建/应用 CE 资源包。客户端只需原版 Minecraft 加资源包。
4. 用四根线围绕一个竹活板门合成竹筛（十字形）；或在 CE 物品浏览中取出 `kaleidoscopecookery:bamboo_tray`。用 `/kcrecipe` 查看竹筛类别，拥有编辑权限时用 `/kcrecipe edit` 修改配方。
5. 只有确认 `kaleidoscopecookery:fresh_tea_leaves` 和 `kaleidoscopecookery:dried_tea_leaves` 已定义时，才将 `optional/bamboo_tray_tea.yml` 放入包的 `configuration/`。这些 ID 来自当前插件成就表，仍应与目标完整资源包核对；缺失物品会拒绝配方加载并保留上次成功快照。

目录里的 `recipe-sources.json` 记录每条配方的官方源文件、固定提交和 Git blob SHA-256。配方表不会自动从网上更新。重新生成：

```powershell
python tools/export_bamboo_tray.py --source ../.references/KaleidoscopeCookery --commit 2f4e386ce23f49a385ddf003c67fc6415c55417a --output examples/bamboo-tray
```

## 配方语法

```yaml
bamboo_tray_recipes:
  kaleidoscopecookery:dry_kelp:
    require: minecraft:kelp
    result: minecraft:dried_kelp
    result_count: 1
    mode: drying
    time: 1800
```

`mode` 为 `drying` 或 `wetting`；`time` 必须是正整数服务器 tick，`result_count` 默认为 1。输入可使用精确物品 ID 或插件 `item_tags` 声明的 `'#namespace:tag'`，不会自动把原版或 Forge tag 当作插件 tag。允许 `cooking_time` / `cooking-time` 时间别名，但同一条配方的多个别名必须一致。

竹筛每 19 tick 重新查当前配方，热编辑会作用于正在加工的槽。与其他厨具冻结 `CookingPlan` 的规则不同；未完成时切换晾晒/浸湿共用进度，已完成后反向加工才重新计时。输出数量沿用官方上限：每份结果乘原料数量后，截到结果物品最大堆叠数。

## 待执行的服务器验收

- 顶面四象限分别存物；相同组件可叠加，异物不混槽；空手取一个，潜行空手整槽取出。副手、侧面、观察者模式、创造模式和保护区取消都分别核对。
- 晴天露天：海带在第 95 次加工检查后变干海带（约 1805 tick，受首次错峰检查时刻影响）；湿海绵、腐肉和泥块同样测试。
- 露天降雨或有效水源滴水石锥：干海带、海绵、皮革、泥土、黏土、铜块及 16 色混凝土粉末各按配方转化。雪区、沙漠、无水/有熔岩的滴水石锥不得误判为雨水。
- 无色玻璃/玻璃板和上层竹筛允许晾晒，实心屋顶阻挡；湿润优先于晾晒。晴天/雨天切换、无匹配配方、成品同向禁止循环和反向加工分别检查。
- 上方/侧方漏斗投料，下方漏斗只抽成品。取消 `InventoryMoveItemEvent` 后应恢复同一物品及完成状态；区块边缘、漏斗矿车和 Folia 分区另测。
- 加工中和部分取物后重启/卸载，检查物品数量、完整组件和进度；拆除、爆炸、叠放下层拆除、活塞及保护插件取消时均不复制、不吞物。
- 菜单新增/修改/删除后立即查询，重载后保持一致；重复 ID、非法时间、缺失/循环 tag、缺失结果要报告错误并保留上次成功配方。

自动化测试仅覆盖可脱离服务器的状态机、配方字段、标签快照、文件编辑和导出规则；不能替代上面的验收。

2026-11-04 本地验证：29 项 Java 测试、15 项 Python 测试通过，全 9 个 NMS 适配构建成功；示例 YAML/模型 JSON 已解析，28 条配方与官方固定提交清单一致。尚未执行本节服务器场景。
