# 2026-11-07 源码交接证据

完整开发目标、当前进度、未来计划与换设备步骤见 [根 README](../../../README.md#开发交接2026-11-07)。本次交接没有新增玩法或启动 Minecraft 服务器；上一运行检查点仍为 2026-11-06 的石磨 `millstone-v2`。

本目录的 [manifest.json](manifest.json) 保存真实命令、退出码、JUnit 汇总、8 个官方提交核对、来源路径和 SHA-256。`evidence/` 是可随 Git 获取的原始运行报告、JUnit XML 和日志副本；UTF-16/GB18030 日志转为 UTF-8 时同时记录原文件与副本哈希。`.gitattributes` 禁止这些证据及生成目录快照被 Git 自动换行转换。

| 交接校验 | 结果 | 证据范围 |
| --- | --- | --- |
| 厨房在线 `test shadowJar acceptanceJar` | exit 1 | CodeMC Spigot 1.21.4 Maven 元数据 TLS 握手失败；不是新增测试断言失败 |
| series 在线 `build` | exit 1 | Paper API Maven 元数据 TLS 握手失败 |
| 厨房离线 `test shadowJar acceptanceJar` | exit 0 | 17 任务 UP-TO-DATE，检查现存 132 项 JUnit 报告、0 失败/错误/跳过；本轮没有重新执行这 132 项 |
| series 离线 `build` | exit 0 | 24 任务 UP-TO-DATE，现存 common 12/doll 10/tavern 27/deco 3/music 7，共 59 项全通过 |
| 厨房 Python | exit 0 | 本轮实际运行 71 项 |
| series Python | exit 0 | 本轮实际运行 18 项 |
| 厨房 strict catalog | exit 0 | 固定源码与工程快照一致 |
| series 导入检查（系统 Python） | exit 1 | 缺少 nbtlib；保留失败日志 |
| series 导入检查（项目虚拟环境） | exit 0 | 四 catalog 与固定源码一致，418 Deco 缺口继续保留 |
| series 资源引用 | exit 0 | 1,774 物品/1,549 家具/117 配方/2,710 模型，errors 为空；不代表 CE 或客户端验收 |
| 参考源码 | 8/8 | HEAD 等于锁定提交，tracked clean |
| 本地测试端口 | 0 监听 | 25581、25582、25583、25584 均已释放；未操作生产服 |

历史石磨产物与日志共 13 个摘要条目重新核对哈希一致；[双端三轮原始汇总](evidence/millstone-v2/acceptance.json) 和六份 boot 日志保留。每端三轮 PASS、正常停服，真实牛驱动重启续磨产生 10 骨粉，清理后再次重启无重复。包内文件与模型检查见 [原始资源检查](evidence/millstone-artifact-check.log.txt)，完整限定见 [厨房验证记录](../../VALIDATION.md)。

失败证据也随 Git 保留：[石磨 Folia 首轮续磨失败](evidence/millstone-v1/folia/boot-2.log.txt)、[旧家具 ticker 诊断](evidence/millstone-diagnostic-v3/folia/plugins/CookeryAcceptanceProbe/millstone-drive.json)、[历史 CE 启动 watchdog](evidence/pot-v2/folia/boot-2.log.txt)。后者仍 OPEN，不因石磨 ticker 修复或后续重启成功关闭。

服务器世界、依赖缓存、JAR、生成资源 ZIP、完整参考仓库及未选入的历史日志继续留在旧设备；这里不是整机备份。新设备必须按 README 恢复依赖和源码，不能使用旧报告冒充新设备运行验证。当前全系列目标、竹筛整包接入、玩家/客户端、保护/多人、卸载及跨区域验收均未完成。

可在仓库根目录校验证据副本：

```powershell
python -c "import hashlib,json,pathlib; p=pathlib.Path('docs/handoff/2026-11-07'); m=json.loads((p/'manifest.json').read_text(encoding='utf-8')); bad=[f['file'] for f in m['files'] if hashlib.sha256((p/f['file']).read_bytes()).hexdigest()!=f['sha256']]; print('mismatches:',bad); raise SystemExit(bool(bad))"
```
