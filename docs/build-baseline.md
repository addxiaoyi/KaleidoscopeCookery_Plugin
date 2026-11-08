# 可复现构建基线

记录日期：2026-11-08（UTC）

这份基线固定首个验收组合：Paper/Folia 1.21.11、Java 21 主源码、Java 25 的 26.x NMS 源集、CraftEngine 26.9.1。上游八个 checkout 的提交和工作树状态以 [`organization-checkouts.json`](research/organization-checkouts.json) 为准；导入器只接受这些提交。

工程治理基线提交为 `aea96140d3c78395c1f8754fb4cb286d6e08ba92`。本地复核使用固定 JDK 25 执行根工程（17 个任务，成功）和系列工程（24 个任务，成功）；工具测试为根 74 项、系列 23 项，独立性与台账检查均为 0 errors。CI 会在新的提交上重复同一组命令；提交、工具链和产物 SHA-256 以 CI artifact 和发布包 `BUILD-MANIFEST.json` 为最终证据。

## 本地复现

```bash
./gradlew javaToolchains --no-daemon --no-configuration-cache --console=plain
./gradlew test shadowJar acceptanceJar --no-daemon --no-configuration-cache --console=plain
./gradlew -p series build --no-daemon --no-configuration-cache --console=plain

python -m unittest discover -s tools -p 'test_*.py' -v
PYTHONPATH=series/tools python -m unittest discover -s series/tools -p 'test_*.py' -v
python tools/feature_ledger.py
PYTHONPATH=series/tools python series/tools/check_independence.py
```

也可以在 Linux/macOS 使用统一入口（Windows 会自动选择 `gradlew.bat`）：

```bash
python tools/build_all.py
```

如果要运行严格上游导入检查，先按锁文件恢复 `../.references/` 下的八个干净 checkout，再执行：

```bash
PYTHONPATH=series/tools python series/tools/import_sources.py --check
```

没有这些 checkout 时，CI 只运行导入器单测和 schema/独立性检查，不把“没有上游输入”误报成资源导入通过。

## 证据边界

- Java/Python 任务通过只证明源码和工具测试通过，不证明 CE 在服务器运行。
- `check_independence.py` 只检查 plugin.yml、可选 JAR 和 CE ZIP 的结构；它不替代 Paper/Folia 或原版客户端验收。
- Folia 冷启动 watchdog、资源包下载、玩家操作、保护插件、多人和跨区域行为必须在 [`series/docs/ACCEPTANCE.md`](../series/docs/ACCEPTANCE.md) 中单独记录首次失败与后续复验。
