# 厨房附魔注册

将本目录复制到测试世界的 `datapacks/cookery_enchantments`，首次启动前安装。
目标为 Paper/Folia 1.21.11、Java 21、CraftEngine 26.9.1，无需客户端模组。
服务端数据包提供原生附魔注册；插件从 CE `Item.enchantments()` 读取等级。
只有现有菜刀判定或 `sickle_range_harvest` 行为认可的工具会触发厨房能力。

| ID | 等级 | 行为 |
| --- | --- | --- |
| `kaleidoscopecookery:quick_knife` | I / II | 每刀推进 2 / 4，最后一刀直接出菜 |
| `kaleidoscopecookery:sweep` | I / II / III | 默认半径 3 / 4 / 5，每次成功收割耗耐久 2 / 4 / 8 |

无附魔保持原有 1 点切割进度、半径 2、耐久 1。自定义镰刀配置的半径作为基数加等级，
最终半径仍封顶为 8；自定义耐久基数乘 2 的等级次方，关闭损耗/创造模式保持不扣耐久。
只有成功收割才扣一次耐久、进入冷却；逐格保护取消、未加载区块和其他 Folia 区域均跳过。

完整现用 CE 工具包尚缺，`supported_items` 暂按官方工具的剑类基材登记。
本包没有将两个附魔加入原版附魔台/交易随机池；CE 工具标签、附魔书获取和铁砧限制仍需专项验收。
安装后 OP 手持现有菜刀/镰刀，可在隔离测试服使用：

```text
/enchant @s kaleidoscopecookery:quick_knife 2
/enchant @s kaleidoscopecookery:sweep 3
```

数据来源为 Cookery 固定提交 `2f4e386ce23f49a385ddf003c67fc6415c55417a` 的
`QuickKnifeEnchantment`、`SweepEnchantment`、`ChoppingBoardBlockEntity`、`SickleItem`。
数据包只注册名称、等级和适用基材；实际厨房行为由 Java 插件执行，不能单独作为完整移植。
