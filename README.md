<div align="center">

# 🍳 森罗物语 · SenLuo Plugin

**Paper/Folia + CraftEngine 的五插件森罗物语移植工程**

Cookery 厨房 · Doll 玩偶 · Tavern 酒馆 · Deco 装饰 · MusicPlus 音乐

![CraftEngine](https://img.shields.io/badge/CraftEngine-26.9.1-4C8BF5?style=flat-square)
![Paper/Folia](https://img.shields.io/badge/Paper%2FFolia-1.21.11-EF6C00?style=flat-square)
![Java](https://img.shields.io/badge/Java-21%2F25-007396?style=flat-square)

</div>

## 项目定位

本仓库把 [KaleidoscopeMods](https://github.com/KaleidoscopeMods) 的五类玩法移植为独立 Bukkit 插件和 CraftEngine 内容包，让原版客户端通过服务器资源包参与玩法。Demo、Web、midi 保留为上游参考，不作为玩法插件。

每个插件都能独立发布。CraftEngine 是唯一硬依赖，系列共享代码在构建时 shade 进对应 JAR。跨插件联动通过 soft-depend、公开 API 或事件完成。

| 插件 | JAR | CE 内容包 | 当前证据 | 主要缺口 |
| --- | --- | --- | --- | --- |
| Cookery | `KaleidoscopeCookeryPlugin-1.3.0.jar` | `cookery_kitchen` | Paper/Folia 五插件整合加载，11/11 方块自检通过，502/612 条来源配方已导入 | 玩家流程、客户端、110 条 pending 配方、保护/多人 |
| Doll | `KaleidoscopeDoll-0.1.0-dev.jar` | `doll-ce-pack-*.zip` | Paper/Folia 启动加载通过 | 精确物理、Bedrock 模型、玩家流程 |
| Tavern | `KaleidoscopeTavern-0.1.0-dev.jar` | `tavern-ce-pack-*.zip` | Paper/Folia 启动加载通过 | 葡萄世界生成、复杂家具、饮用和多人 |
| Deco | `KaleidoscopeDeco-0.1.0-dev.jar` | `deco-ce-pack-*.zip` | Paper/Folia 启动加载通过 | 418 条聚合缺口、脚本事件、座位/碰撞 |
| MusicPlus | `KaleidoscopeMusic-0.1.0-dev.jar` | `music-ce-pack-*.zip` | Paper/Folia 启动加载通过，吉他纹理已接入 | Bedrock 几何、持续声部、客户端合成器 |

当前代码、单测和服务器加载已经完成多层验证。原版客户端资源包、真人玩家闭环和许可证人工核验仍按台账记录，项目不把静态通过写成 100% 完成。

## 工程结构

```text
SenLuo_Plugin/
├── src/                         # Cookery 主插件与 NMS 适配
├── Kaleidoscope/                # 厨房资源入口和菜单图标
├── series/
│   ├── common/                  # 构建时内嵌的共享库
│   ├── doll/                    # 独立 Doll 插件
│   ├── tavern/                  # 独立 Tavern 插件
│   ├── deco/                    # 独立 Deco 插件
│   ├── music/                   # 独立 MusicPlus 插件
│   ├── tools/                   # 来源同步、资源转换、打包
│   └── docs/                    # 移植矩阵和验收表
├── tools/                      # 厨房导入、基线、许可证和验收工具
└── docs/                       # 证据、安装、升级和配置参考
```

## 当前运行基线

- Minecraft 1.21.11
- Paper build 132 和 Folia build 14
- Java 21 运行与主源码，Java 25 编译 26.x NMS 源集
- CraftEngine 26.9.1
- 官方来源固定在 [`organization-checkouts.json`](docs/research/organization-checkouts.json)
- 模块同步规格在 [`module-specs.json`](series/tools/module-specs.json)

五插件整合服已在 Paper/Folia 启动并正常关闭。Cookery 当前运行加载 451 个物品、64 个方块和 90 个配方，四个系列插件分别完成启用。完整哈希、版本和首次失败修复见 [`series-five-runtime-acceptance-2026-11-08.json`](docs/research/series-five-runtime-acceptance-2026-11-08.json)。

## 新设备构建

官方源码需要放在仓库同级 `.references/`，路径和提交以锁定文件为准。准备 Python 环境后执行：

```bash
python -m venv .venv
.venv/bin/python -m pip install -r tools/requirements.txt -r series/tools/requirements.txt

PYTHONPATH=series/tools .venv/bin/python tools/export_cookery_catalog.py \
  --source ../.references/KaleidoscopeCookery --check
PYTHONPATH=series/tools .venv/bin/python series/tools/import_sources.py --check

PYTHONPATH=series/tools .venv/bin/python -m unittest discover -s tools -p 'test_*.py' -v
PYTHONPATH=series/tools .venv/bin/python -m unittest discover -s series/tools -p 'test_*.py' -v

JAVA_HOME=/path/to/jdk-25 bash ./gradlew test shadowJar acceptanceJar --no-daemon --no-configuration-cache
JAVA_HOME=/path/to/jdk-25 bash ./gradlew -p series build --no-daemon --no-configuration-cache
```

统一入口是 `python tools/build_all.py`。Windows 使用 `.venv\Scripts\python.exe` 和 `gradlew.bat`。

## 隔离服准备

隔离服准备工具会复制五个 JAR、五个 CE 内容包、CraftEngine 运行 JAR 和基础资源包，Cookery 厨房包会自动解包成 `cookery_kitchen/` 目录。它不会启动服务器或自动接受 EULA。

```bash
PYTHONPATH=series/tools .venv/bin/python series/tools/prepare_independent_servers.py \
  --root build/acceptance/series-five-runtime \
  --artifacts series \
  --packs series/build/distribution/ce-packs-current \
  --cookery-jar build/libs/KaleidoscopeCookeryPlugin-1.3.0.jar \
  --cookery-pack build/acceptance/cookery-runtime.zip \
  --craft-engine build/acceptance/runtime/craft-engine-paper-plugin-26.9.1.jar \
  --base-resources build/acceptance/runtime/craftengine-resources \
  --paper-jar build/acceptance/runtime/paper-1.21.11-132.jar \
  --folia-jar build/acceptance/runtime/folia-1.21.11-14.jar
```

安装、升级和回滚说明见 [`docs/install-guide.md`](docs/install-guide.md) 与 [`docs/upgrade-guide.md`](docs/upgrade-guide.md)。

## 验证入口

- 功能台账：[`docs/feature-ledger.json`](docs/feature-ledger.json)
- 验收矩阵：[`series/docs/acceptance-matrix.json`](series/docs/acceptance-matrix.json)
- 许可证审计：`python tools/audit_licenses.py --output build/license-audit.json`
- 独立性检查：`PYTHONPATH=series/tools python series/tools/check_independence.py`
- 厨房状态信封：`CookeryStateEnvelope` 和 `CookeryBlockStateEnvelope`
- 系列统一存档：`series/common/SavedState`
- 构建基线：[`docs/build-baseline.md`](docs/build-baseline.md)
- 厨房对照：[`docs/COOKERY-PARITY.md`](docs/COOKERY-PARITY.md)
- 系列移植矩阵：[`series/docs/PORTING-MATRIX.md`](series/docs/PORTING-MATRIX.md)
- 详细厨房配置参考：[`docs/COOKERY-CONFIGURATION.md`](docs/COOKERY-CONFIGURATION.md)

状态枚举固定为 `not_started`、`implemented`、`unit_tested`、`runtime_tested`、`client_tested`、`accepted` 和 `blocked`。`accepted` 只能由明确证据更新。

## 存档与交易边界

状态保存使用 `namespace/schema_version/source_commit/payload` 信封。Cookery BlockEntity 保留旧 payload 作为兼容投影，系列插件读取旧裸状态并拒绝未知版本、错误来源和损坏数据。

玩家物品操作采用预检查、可取消事件、一次提交、状态保存和清理处理中标记的事务流程。Folia 世界、玩家和实体操作必须在所属调度器执行，纯文件和资源处理放到异步路径。

## 主要剩余任务

1. Cookery 完成 110 条 pending 配方、原料获取、竹筛和摆盘的玩家闭环。竹筛 28 条已进入独立配置并完成来源对账，仍需玩家验收。
2. Doll 补精确物理、移动玩偶姿态和原版客户端显示。
3. Tavern 补葡萄世界生成、酒桶/果盆复杂状态、液面和多人守恒。
4. Deco 按模型组件、overlay、脚本事件、座位和碰撞分类处理 418 条聚合缺口。
5. MusicPlus 补原版可行的乐器模型、持续声部和多人演奏生命周期。
6. 五插件完成原版客户端资源包、保护插件、多人、重启/卸载和性能验收。
7. 完成八个上游仓库的许可证人工复核。

## 许可证与发布

[`docs/license-policy.json`](docs/license-policy.json) 记录八个上游仓库的审核状态。没有许可证文件或未经人工确认的素材不会自动进入正式发布。开发包、独立包和整合包都应携带 `BUILD-MANIFEST.json` 与 `SHA256SUMS.txt`。

## 继续开发

```bash
git clone --branch main https://github.com/addxiaoyi/KaleidoscopeCookery_Plugin.git luosengchufang
cd luosengchufang
git status --short --branch
git log -1 --oneline
```

先恢复 `.references/`，运行上面的严格导入和构建命令，再按功能台账选择下一项。
