# 森罗系列插件移植工作区

更新：2026-11-07。目标为纯服务端插件 + CraftEngine 资源包，原版客户端可进服。当前是 **0.1.0-dev 开发版**，四插件首批功能已有代码，全系列尚未完成；本目录四插件未进行服务器或真人验收。厨房独立运行证据及换设备完整计划统一见 [根 README 开发交接](../README.md#开发交接2026-11-07)。

厨房主工程继续位于仓库根目录；这里的 `common / doll / tavern / deco / music` 是独立 Gradle 多模块工程，不替换厨房已有实现。

## 官方源码

组织的 8 个仓库全部位于 `../../.references/`：KaleidoscopeCookery、KaleidoscopeDoll、KaleidoscopeTavern、KaleidoscopeDeco、KaleidoscopeDemo、KaleidoscopeWeb、MusicPlus、midi。

固定提交和仓库地址见 [organization-checkouts.json](../docs/research/organization-checkouts.json)。Demo、Web、midi 是示例、官网和 MIDI 支撑代码，不单独包装成虚假的玩法插件。导入工具使用固定提交的 Git blobs，拒绝修改过的参考源码。

## 已落地的范围

| 模块 | 已实现的服务端逻辑 | 当前资源定义 |
| --- | --- | --- |
| Doll | 抽奖、礼盒、643 玩家玩偶兑换、收藏、九轴显示微调；新增蓄力投掷、落地弹跳、玩偶骑乘和幻翼携带事件 | 729 物品 / 725 家具 / 3 工作台配方 |
| Tavern | 果盆、24 酒桶配方、熟成、12 调酒配方、37 饮品效果表；新增三种葡萄、龙头装瓶、手持摇杯和向放置酒杯倒酒 | 190 物品 / 39 家具 / 114 工作台配方 |
| Deco | 主题图鉴、家具及部分座位；新增102条状态转换、沙发染色/清色/形态工具、拆装状态保存 | 854 物品 / 785 家具 / 68 沙发样式 |
| Music | 25 键菜单、本地 MIDI 多轨/速度/音色解析、原版音符播放、停止与离线清理 | 1 个乐器物品，模型暂为音符盒 |

这些数量是资源和数据条目数，**不是功能完成率**。逐项差异见 [PORTING-MATRIX.md](docs/PORTING-MATRIX.md)，运行验收见 [ACCEPTANCE.md](docs/ACCEPTANCE.md)。厨房进度继续看 [原实施表](../docs/IMPLEMENTATION-ROADMAP.md)。

## 构建与验证

新四模块的编译基线为 **Paper 1.21.11、Java 21、CraftEngine API 26.9.1**；`plugin.yml` 最低 API 明确为 1.21.11。其他 MC/CE 组合尚未验证，不能沿用厨房九个 NMS 适配的编译结论。

在仓库根目录运行：

```powershell
python -m venv series/.venv
.\series\.venv\Scripts\python.exe -m pip install -r series/tools/requirements.txt
.\series\.venv\Scripts\python.exe series/tools/import_sources.py --check
.\series\.venv\Scripts\python.exe -m unittest discover -s series/tools -p 'test_*.py' -v
.\gradlew.bat -p series build --console=plain
.\gradlew.bat build --console=plain
```

首次生成 CE 内容包：

```powershell
.\series\.venv\Scripts\python.exe series/tools/import_sources.py --archive series/build/distribution/KaleidoscopeSeries-CE-Packs-0.1.0-dev-gameplay.zip
.\series\.venv\Scripts\python.exe series/tools/validate_packs.py series/build/distribution/KaleidoscopeSeries-CE-Packs-0.1.0-dev-gameplay.zip
.\series\.venv\Scripts\python.exe series/tools/package_dev.py --packs series/build/distribution/KaleidoscopeSeries-CE-Packs-0.1.0-dev-gameplay.zip --output series/build/distribution/KaleidoscopeSeries-0.1.0-dev-gameplay.zip
```

生成工具拒绝覆盖已有输出；重新生成时选择新路径，或确认后整理自己生成的旧文件。`--assets <新目录>` 可输出展开后的 CE 包。`build/packs/`、`packs-v2/`、`packs-v3/` 是开发期间的旧输出，不能与最终归档混装。

四个 JAR 位于各模块 `build/libs/`；本轮本地整包位于 `build/distribution/KaleidoscopeSeries-0.1.0-dev-gameplay.zip`。整包含厨房已有开发 JAR、新四个 JAR、CE 内容归档、说明、来源记录、验证记录和 SHA-256 清单。旧 `0.1.0-dev.zip` 保留，不能代表本轮成果。

## 测试服安装

1. 在测试服准备 Java 21、Paper 1.21.11 和对应 CraftEngine，停服后把所需 JAR 放到 `plugins/`。`common` 不单独安装。新增或更换插件 JAR 使用正常停启，不使用热加载工具。
2. 将 CE 内容归档中的 `doll / tavern / deco / music / shared` 五个目录放到 `plugins/CraftEngine/resources/`，保证每个目录下直接有 `pack.yml`。`shared` 合并了原版方块图集配置，应一起安装。
3. 新系列归档不包含厨房 CE 包；根工程已能生成 [厨房独立验收包](../examples/kitchen-pack/README.md)，与四插件整合仍待验收。竹筛目前只有独立示例，尚未合入完整厨房生成器；同名厨房/食物定义不能重复安装。
4. 启动并检查未知物品、行为、配方、模型与 NBT 加载错误。按照目标 CE 版本执行资源生成和发送；当前官方命令为 `/ce reload all`，仅 `/ce reload` 默认只刷新配置，不代表客户端资源包已更新。
5. 用未安装模组的原版客户端接收服务器资源包，再执行验收表。MIDI 文件放在 `plugins/KaleidoscopeMusicPlugin/songs/`。

这里提供的是 **CraftEngine 内容包**，不是直接发给客户端的最终 vanilla resource-pack ZIP；最终客户端包须由 CE 生成并通过服务器的资源包托管流程发送。

## 操作

- `/kdoll` 查看收藏；OP 用 `/kdoll kit` 获取机器、电脑、微调工具。机器投入铁锭/金锭/钻石，电脑每只玩家玩偶消耗一块原版羊毛。手持微调工具右键玩偶打开宽/高/深、三轴偏移、三轴旋转菜单。
- 手持玩偶潜行右键蓄力，松开投掷；手持玩偶右键实体让玩偶骑乘目标。直接攻击移动玩偶回收，背包满时保留实体。原版盔甲架承载模型和物理，和模组自定义实体仍有差异。
- `/ktavern` 查看酒桶配方；OP 用 `/ktavern kit` 获取酒桶、果盆、雪克杯及空容器。酒桶加入 4 桶同种液体，再投最多四槽原料，空手潜行开关盖；发酵后用配方指定空瓶取酒。
- 果盆投料后站入跳踩，每次按配方增加果汁；达到 1000mB 后用桶取出。空手潜行可取回未使用原料。
- 雪克杯每次投入一份原料，酒类要求熟成至少 4 级。容器在投料时返还，倒入的液体不再以整瓶返还。满三份后空手右键开始，第二次右键停止；19–68 tick 迷之酒、69–88 tick 特调、89–98 tick 匹配配方、99 tick 起迷之酒，111 tick 自动结束。用空鸡尾酒杯领取结果。中途重启/卸载会停止摇动，保留原料。
- 用一个空雪克杯潜行右键已装料的雪克杯转装；手持右键持续摇动，松开结束；对放置的空鸡尾酒杯倒酒。手持同类空杯领取成品，放置杯留在原处。雪克杯原料及成品随物品保存。
- `/ktavern kit` 另含藤架、葡萄藤和龙头。葡萄种植、封蜡、剪刀采收、红石龙头及各来源的摆放规则见 [GAMEPLAY.md](docs/GAMEPLAY.md)。
- 饮品使用原版饮用动作。特调按原料熟成等级和药水收集效果，同类时长相加后乘 1.2，等级与概率取最大值。
- `/kdeco` 浏览分类及领取状态工具；领取需要 `kaleidoscopedeco.give`，默认 OP。沙发用染料换色、水桶清色、状态工具切换四种形态；其余交互按已导入规则执行，尚未移植的脚本会明确提示。
- 手持乐器用 `/kmusic` 演奏；`/kmusic play example.mid` 播放本地文件；`/kmusic stop` 停止。OP 用 `/kmusic kit` 获取乐器。

## 存档与恢复

有原料、酒液、待领取奖品的家具会拒绝普通玩家拆除。异常 NBT 会阻止操作，并原样保存；不会把读不出的库存覆盖为空。

如果其他插件或管理员强制移除家具，执行到 CE `preRemove` 时会将控制器数据写到该插件目录的 `recovery/*.nbt` 并刷盘。这是管理员核对用的内容快照，不自动发放物品；未执行该回调的外部直接删除、服务器崩溃和文件系统故障不在此保证内。

自定义饮品效果在玩家 PDC 保存，退出暂停剩余时间，死亡和牛奶清除。Folia 范围效果只读取当前线程拥有的已加载区块；跨区域边缘的目标可能跳过，尚未作实服压力验证。

原资源的 README/LICENSE 保留在各包的 `upstream-notices/` 中。本次 Git 交接保存源码、导入工具、目录和证据；生成资源、JAR、测试服及缓存保留为本机输出，不作为发布/部署成果。
