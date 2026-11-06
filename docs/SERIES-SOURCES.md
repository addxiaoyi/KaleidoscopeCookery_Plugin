# 森罗物语官方系列与复刻范围

核查日期：2026-11-04，用户时区 Asia/Shanghai。以下来源为本次实际读取的官方组织、官方源码/文档及开发者发布的 Modrinth 项目 API；正文为研究摘要，不是官方原文。API 字段摘录保存于 [research/series-sources.json](research/series-sources.json)。本文仅记录研究来源；后续插件改动及验证见 [IMPLEMENTATION-ROADMAP.md](IMPLEMENTATION-ROADMAP.md) 与 [VALIDATION.md](VALIDATION.md)。

## 系列边界

官方组织为 [KaleidoscopeMods](https://github.com/KaleidoscopeMods)。[官方网站源码中的系列简介](https://github.com/KaleidoscopeMods/KaleidoscopeWeb/blob/main/docs/notes/wiki/简介.md) 列出三个现有主题模组：厨房 Cookery、酒馆 Tavern、玩偶 Doll。

- **已发布主系列**：Kaleidoscope Cookery、Kaleidoscope Tavern、Kaleidoscope Doll。
- **已发布官方衍生项目**：Kaleidoscope Deco，主要由数据包与资源包驱动，提供装饰和有限交互；不是把三个主模组全部功能移到原版客户端。
- **官方组织中的研发项目**：MusicPlus。源码语言文件称 `Kaleidoscope Music Plus`，有手持乐器和客户端 MIDI 音频实现；未查到 GitHub release，不能把源码 `mod_version=1.0.0` 当成正式发布。
- **其他官方仓库**：KaleidoscopeDemo 是正式开发前的美术演示素材；KaleidoscopeWeb 是官网；midi 是 `ming-sc/midi` 的 fork，并被 MusicPlus 构建引用为库。它们不应各算成已发布的玩法模组。
- **未找到的名称**：本轮官方组织 8 个公开仓库中没有 `Witchery`，Modrinth 的 `Kaleidoscope Witchery` 检索也没有结果。不能把本地 `luosengwuyu` 文件夹自动解释成一个叫 Witchery 的官方模组。

Modrinth 检索还返回 World Liquor、Chinese Food、End、Nether、Compat、Grilling、Doll Workshop、Automation、Refabricated 等项目，其作者、源码组织或项目说明表明它们是附属、兼容扩展或非官方移植。本说明不把这些第三方项目混入“官方主系列”；名称带 Kaleidoscope 本身不足以认定官方。

## 发布版本与源码基线

“稳定版本”取 Modrinth `version_type=release`，按发布时间排序，截至核查日。时间统一换算为北京时间。GitHub 默认分支 HEAD 通过 `git ls-remote --symref` 与 API 核对；HEAD 是开发源码，不自动等于对应发布 JAR。

### 厨房：Kaleidoscope Cookery

- [官方源码](https://github.com/KaleidoscopeMods/KaleidoscopeCookery)，默认分支 `main`，HEAD `2f4e386ce23f49a385ddf003c67fc6415c55417a`，提交于 2026-09-26 21:48:55。
- [Modrinth 项目](https://modrinth.com/mod/kaleidoscope-cookery)。稳定版 `1.6.0-forge+mc1.20.1` 于 2026-09-30 23:07:23 发布；`1.6.0-neoforge+mc1.21.1` 于同日 23:13:42 发布。
- 最近 GitHub release 是 `snapshot-2026-09-26-14-06-32`，`prerelease=true`，标签指向上述 HEAD。没有验证 Modrinth 二进制与这个提交的可重现构建关系。
- 核心范围：烹饪台、食材加工、配方、菜品、作物、食用效果及相关工具；1.6.0 更新涉及砧板发射器投料/切割、多产出、菜刀附魔、石磨生物规则、镰刀与便当袋。现有本仓库是它的 CE 插件移植，具体实现与差异另见 DEVELOPMENT.md。

### 玩偶：Kaleidoscope Doll

- [官方源码](https://github.com/KaleidoscopeMods/KaleidoscopeDoll)，默认分支 `1.20-forge`，HEAD `62303aa58bbd5ee52498f3ad0fbf4a0c7d04c183`，提交于 2026-09-30 23:10:01。
- [Modrinth 项目](https://modrinth.com/mod/kaleidoscope-doll)。稳定版 `1.4.2-forge+mc1.20.1` 于 2026-09-30 23:33:15 发布；`1.4.2-neoforge+mc1.21.1` 于同日 23:33:35 发布。
- 最近 GitHub release 是 `snapshot-2026-09-30-15-10-59`，`prerelease=true`，标签指向上述 HEAD。它与稍后发布的 1.4.2 接近，但尚未用产物哈希或可重现构建证明二者完全相同。
- 核心范围：抽奖机与分档礼盒、玩偶摆放及音效、电脑羊毛兑换、自定义玩偶、实体玩偶蓄力投掷/弹跳/浮力、骑乘生物、幻翼携带、微调尺寸/位移/旋转、收藏进度及模组兼容。
- [官方玩偶简介](https://github.com/KaleidoscopeMods/KaleidoscopeWeb/blob/main/docs/notes/wiki/玩偶/玩偶简介.md) 明确基于 1.4.1；它列出的数量与仓库 README、Modrinth 旧简介不同。不能把任何一处数量直接当成 1.4.2 的完整素材清单，完整复刻前应按选定源码提交枚举注册物品和模型。

### 酒馆：Kaleidoscope Tavern

- [官方源码](https://github.com/KaleidoscopeMods/KaleidoscopeTavern)，默认分支 `main`，HEAD `c4ec1880bd44cf3139d3ba744ab30bb379cf1416`，提交于 2026-07-21 10:35:04。
- [Modrinth 项目](https://modrinth.com/mod/kaleidoscopetavern)。稳定版 `1.2.0-forge+mc1.20.1` 于 2026-07-01 15:52:03 发布；`1.2.0-neoforge+mc1.21.1` 于同日 15:53:14 发布。
- HEAD **晚于稳定版发布**；最近 GitHub release `snapshot-2026-07-21-02-46-26` 为预发布，标签指向 HEAD。若以稳定版为复刻目标，不能把当前 main 全部变更默认当成 1.2.0 已发布内容。
- 核心范围：葡萄种植与榨汁、酒桶发酵和七档品质、龙头装瓶；1.2.0 还包括摇酒器、按原料/颜色/条件匹配的鸡尾酒与特调、酒杯与酒架、酒柜、吊灯、香炉、可写黑板/立牌、可坐家具、投掷燃烧瓶及饮品效果。仅实现酒桶并不覆盖已发布的 1.2.0。

### 装饰：Kaleidoscope Deco

- [官方源码](https://github.com/KaleidoscopeMods/KaleidoscopeDeco)，默认分支 `master`，HEAD `6ce180e1226ce93b0fb0d45ea80e0ed5b01b1dc0`，提交于 2026-07-11 23:55:59。
- [GitHub 正式 release](https://github.com/KaleidoscopeMods/KaleidoscopeDeco/releases/tag/1.4.0) 名称 `1.4`、标签 `1.4.0`，于 2026-07-12 00:25:41 发布，标签确实指向 HEAD。
- [Modrinth 项目](https://modrinth.com/datapack/kaleidoscope-deco)：`1.4` 数据包于 2026-07-12 00:33:10 发布，`1.4+mod` 包装版本于 00:33:51 发布。API 标注版本覆盖 1.21.6–1.21.11、26.1/26.1.1/26.1.2/26.2；这是发布声明，未进行服务器验收。
- 模块：Doll、Cookery、Bar、Chinese、Lab、Holiday、Road；扩展 Doll Plus 增加声音/粒子和交互，Cookery Plus 增加炉灶与部分烹饪。README 明确强调装饰并精简复杂玩法，不能据此宣称与主模组等价。

### 研发：MusicPlus

- [官方仓库](https://github.com/KaleidoscopeMods/MusicPlus)，默认分支 `main`，HEAD `cc84f29e2a1abe79805567fddd218b3a7b252655`，提交于 2026-10-23 23:14:29。
- 当前 GitHub releases 返回空列表。`gradle.properties` 声明 Forge 1.20.1、源码版本 1.0.0、作者 ysbb/tartaric_acid/frc；这是开发元数据。
- [源码语言文件](https://github.com/KaleidoscopeMods/MusicPlus/blob/cc84f29e2a1abe79805567fddd218b3a7b252655/src/main/resources/assets/musicplus/lang/en_us.json) 使用名称 `Kaleidoscope Music Plus`；[MidiSound](https://github.com/KaleidoscopeMods/MusicPlus/blob/cc84f29e2a1abe79805567fddd218b3a7b252655/src/main/java/com/github/kaleidoscopemods/musicplus/client/audio/MidiSound.java) 在客户端创建 MIDI 音频流并跟随演奏者，当前仍留有音符驱动动画的 TODO。不能把设想中的完整演奏系统记为已完成功能。

## 纯插件复刻的技术边界

本节是依据官方源码所作的工程判断，尚未验证实现。这里“纯插件”指玩家使用原版客户端，服务器可下发资源包并使用 CE 等服务端插件。

- **可在服务器重建的玩法**：配方匹配、发酵/加工进度、抽奖奖池、兑换、收藏与存档、家具交互、保护判断、常规粒子与音效。实现时仍需逐条核对事件取消、卸载恢复和物品守恒。
- **需要改成交互等价方案的界面**：Doll 的 ComputerMenuScreen/TweaksToolScreen、Tavern 的专用摇酒 HUD、JEI/EMI/Jade/Curios 集成依赖客户端模组。原版客户端可以使用背包菜单、支持版本的对话框、actionbar、原版装备槽等替代，但不能直接运行这些模组界面或新增客户端按键。
- **实体与视觉**：Doll 源码注册专用 DollEntity 与客户端渲染器。CE 家具、展示实体和服务器物理模拟可复刻许多操作；复杂碰撞、持物姿态、弹跳挤压动画与客户端预测的完全一致性必须另做测试，不能仅凭模型导入声称完整复刻。
- **特殊效果**：可用服务器事件、属性、状态数据重建效果逻辑；模组专用状态栏图标、相机/屏幕渲染与 HUD 需要替代呈现。目标 Minecraft 版本的原版属性与协议能力也会影响可行性。
- **MusicPlus**：原版客户端不会执行官方的 MidiAudioStream。若重建，只能另设计服务器定时音符、资源包预置音频等路径；不能宣称直接发送一个 MIDI 链接就获得与模组相同的实时音频流。
- **Deco**：官方已有原版数据包路线，可用作资源组织和简单交互参考；它刻意精简的行为仍不能作为三主模组完整玩法的替身。

## 素材声明与本地状态

只记录来源声明，不把“源码公开”理解为所有素材可以自由重新分发：Cookery/Tavern README 将源码与资源分别列为 BSD 3-Clause 与 CC BY-NC-SA 4.0；Doll README 将源码列为 BSD 3-Clause、素材列为 All Rights Reserved；Deco 分别列数据包 GPL v3 与资源包 CC BY-NC-SA 4.0；Demo 是 CC BY-NC-ND 4.0，并明确不用于正式存档。后续制作和发布资源包时应按选定文件的实际许可及已有授权确定素材来源。

本轮检查时 `D:\qwq\项目\luosen-plugins\luosengwanou` 与 `D:\qwq\项目\luosen-plugins\luosengwuyu` 均为空目录，没有代码可用于确定它们的实际项目范围。本说明没有克隆、初始化或改写这两个目录。

## 可复核的原始接口

- 组织仓库：`https://api.github.com/orgs/KaleidoscopeMods/repos?per_page=100`
- 发布项目：`https://api.modrinth.com/v2/project/<slug>`
- 发布列表：`https://api.modrinth.com/v2/project/<slug>/version`
- 源码 HEAD：`https://api.github.com/repos/KaleidoscopeMods/<repository>/commits/<branch>`
- GitHub 发布/标签：`https://api.github.com/repos/KaleidoscopeMods/<repository>/releases` 与 `/tags`

其中 slug 为 `kaleidoscope-cookery`、`kaleidoscope-doll`、`kaleidoscopetavern`、`kaleidoscope-deco`。研究 JSON 仅保留可公开的选定字段，不含令牌或本地账号信息。web.run 本轮未返回可用正文，所以没有生成不可核对的网页引用 ID；API 与官方源码链接是本说明的证据入口。
