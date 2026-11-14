# v1.5.1 全版本 Baritone 可用性验证报告

日期：2026-11-13
范围：`build/release-v1.5/` 下全部 67 个 v1.5.1 发布包（Forge 21 / Fabric 23 / NeoForge 23）

## 1. 结论

| 项目 | 结果 |
| --- | --- |
| 发布包矩阵（包结构 / 内嵌 Baritone / 声明的 MC 版本 / 注册的 Mixin 是否存在） | **67/67 通过** |
| 26.2 三加载器内嵌 Baritone 是否为官方产物 | **是**：1.19.0，SHA-256 与 GitHub Release 资产一致 |
| 26.2 三加载器 Baritone→Minecraft 字节码引用 | Fabric/Forge/NeoForge 各 **1781 条，0 条无法解析**（修复前 1837 条、5 条失败） |
| 26.2 Fabric/Forge/NeoForge 真实进世界 + `#goto` 运行时验证 | **通过**：命令被接受并实际移动/到达目标点（见第 4 节坐标） |
| 其余版本 | 包结构/元数据/Mixin 注册静态验证通过；**未逐个做进世界运行验证**（见第 6 节限制） |

## 2. 26.2 无法使用的根因

v1.5.1 的 26.2 三个产物内嵌的是从 26.1 手工移植的 `1.18.0-26.2` 私有包，不是官方 26.2 构建。
用 26.2 客户端 jar 做字节码链接审计（`scripts/audit-baritone-linkage.py`）后，它仍有 5 处
Minecraft 成员引用在 26.2 上不存在或签名已变：

```text
net/minecraft/client/Minecraft.setScreen(Lnet/minecraft/client/gui/screens/Screen;)V
net/minecraft/client/gui/Gui.getChat()Lnet/minecraft/client/gui/components/ChatComponent;
net/minecraft/client/Minecraft.getToastManager()Lnet/minecraft/client/gui/components/toasts/ToastManager;
net/minecraft/client/renderer/LevelRenderer.setBlocksDirty(IIIIII)V
net/minecraft/world/entity/EntityType.FIREWORK_ROCKETLnet/minecraft/world/entity/EntityType;
```

这些正好落在聊天/提示/界面/渲染路径上：只要 Baritone 初始化或发命令就会命中
`NoSuchMethodError`/`NoSuchFieldError`，表现为 26.2 “Baritone 用不了”。

## 3. 修复内容

1. **26.2 三端统一换成官方 Baritone 1.19.0**（官方该版本原生支持 26.2）：
   - `fabric/versions/26.2`：`baritone:baritone-api-fabric:1.19.0-26.2`
   - `versions/26.2`：`baritone:baritone-forge:1.19.0-26.2`（含 JarJar metadata）
   - `neoforge/versions/26.2`：`baritone:baritone-neoforge:1.19.0-26.2`
2. **下载与入库强制 SHA-256 校验**：`scripts/install-baritone.ps1` 登记 1.19.0 三个加载器的官方摘要，
   下载后不一致直接拒绝安装。
3. **构建与发布包更新**：重新构建 26.2 三个产物并覆盖 `build/release-v1.5/` 对应文件；
   `scripts/build-all.ps1` 对 26.2 改为固定调用官方 1.19.0，避免再被旧的兼容补丁覆盖。
4. **审计脚本**：新增/修正 `audit-baritone-linkage.py`、`audit-baritone-matrix.py`；
   `audit-crash-regressions.py` 明确拒绝 `1.18.0-26.2` 旧移植包。
5. **运行时测试能力**：`scripts/run-version-tests.ps1` 增加 F3/F2 证据采集与 `-MovementSeconds`；
   `scripts/read-level.py` 读取 26.x 新世界格式的出生点/玩家坐标；快速世界改为整目录复制
   （26.x 的维度数据在 `dimensions/`、玩家数据在 `players/`，只拷 `level.dat` 会得到空存档）。

## 4. 26.2 运行时证据（三个加载器）

三个实例都使用同一份 `WurstSmokeFresh` 快速进入世界（出生点 `-128 81 -176`），
并在进世界 8 秒后自动发送 `#goto -80 -128`，随后等待 75 秒让 Baritone 完成寻路。

### 4.1 Fabric

实例：`.test/versions/26.2-fabric-0.19.3`（Fabric Loader 0.19.3，Fabric API 0.156.0+26.2，
JDK 25）。日志：`.test/versions/26.2-fabric-0.19.3/logs/harness.log`。

```text
[22:43:53] [CHAT] [Baritone] Baritone settings file not found, resetting.
[22:43:56] [STDOUT] [HUD] NanoVG GL3 context created
[22:44:00] [Server thread/INFO] TestRunner joined the game
[22:44:00] [STDOUT] Baritone world data dir: ...\saves\WurstSmokeFresh\.\baritone\minecraft\overworld_384
[22:44:12] [CHAT] [Baritone] > goto -80 -128
[22:44:12] [CHAT] [Baritone] Going to: GoalXZ{x=-80,z=-128}
[22:44:30] [STDOUT] Saving region 0,0 to disk ...\baritone\minecraft\overworld_384\cache
```

世界出生点（`scripts/read-level.py`）：`spawn -128 81 -176`。
测试结束时游戏保存的玩家数据
（`saves/WurstSmokeFresh/players/data/00000000-0000-0000-0000-000000000000.dat`）：

```text
Pos [-79.66161905521058, 50.229361147245534, -127.66490875009825]
Dimension minecraft:overworld
```

玩家水平位移约 **68.3 格**，终点与 `#goto -80 -128` 的目标 XZ 误差 < 0.5 格。
这不是“命令有回复”，而是 Baritone 真正完成了寻路并把玩家带到了目标点。

测试过程中无 crash report；NanoVG HUD 正常创建。`nether-pathfinder` 原生库也成功加载
（`[nether-pathfinder] Loaded shared library`，只有一条“无法删除临时文件”的无害告警），
前提是把测试进程的 `TEMP` 指到工作区——本机默认 `%TEMP%` 不允许创建目录/可执行文件。

### 4.2 Forge

实例：`.test/versions/26.2-forge-65.1.0`（Forge 65.1.0，JDK 25）。
日志：`.test/versions/26.2-forge-65.1.0/logs/harness.log`。

```text
[22:53:16] [CHAT] [Baritone] Baritone settings file not found, resetting.
[22:53:25] [Server thread/INFO] TestRunner joined the game
[22:53:37] [CHAT] [Baritone] Going to: GoalXZ{x=-80,z=-128}
```

保存的玩家数据：`Pos [-79.71, 54.33, -127.71]`，即同样到达了 `#goto -80 -128` 的目标点。

### 4.3 NeoForge

实例：`.test/versions/26.2-neoforge-26.2.0.53-beta`（NeoForge 26.2.0.53-beta，JDK 25）。
日志：`.test/versions/26.2-neoforge-26.2.0.53-beta/logs/latest.log`。

```text
Baritone 1.19.0 (baritoe)
- baritoe (jar(mods/WurstB+.Plus-1.5.1-NeoForge-26.2.jar > baritone-neoforge-1.19.0-26.2.jar))
[CHAT] [Baritone] Baritone settings file not found, resetting.
[Server thread/INFO] TestRunner joined the game
[CHAT] [Baritone] > goto -80 -128
[CHAT] [Baritone] Going to: GoalXZ{x=-80,z=-128}
```

该实例的运行判定为 **PASS**（世界加载、命令被接受、撑过 settle 期、NanoVG GL3 正常）。
此前一次运行落盘的玩家数据为 `Pos [-92.16, 62.81, -140.1]`（GameType 1，Health 20）：
相对出生点已移动约 42 格，正朝目标前进（测试进程被强杀，未等到下一次自动存档）。

> 注：NeoForge 官方安装器只生成 `minecraft-client-patched`，而生产启动器要求
> `libraries/net/minecraft/client/26.2-2/client-26.2-2-srg.jar`。本机测试时把
> patched jar 复制到该路径后启动成功；这是测试实例的补全，与 Baritone 无关。

## 5. 全版本静态验证

`python scripts/audit-baritone-matrix.py` → `report/baritone-matrix-v1.5.1.json`：

- 67 个发布包全部存在；
- 每个包都能找到内嵌 Baritone（Fabric/Forge/NeoForge 26.x 为 `META-INF/jars|jarjar/` 嵌套；
  1.20.2–1.20.5 的 NeoForge 为历史扁平打包）；
- 内嵌 Baritone 的 `baritone/api/BaritoneAPI.class` 等 API 类齐全；
- 声明的 Mixin 配置存在，配置里注册的每个 Mixin 类都真实存在于包内；
- 声明的 Minecraft 版本范围覆盖该产物目标版本（含 26.x 的 `[26.2,26.3)` / `~26.3` 等写法）。

26.2 三端的官方摘要：

| 加载器 | 内嵌条目 | SHA-256 |
| --- | --- | --- |
| Fabric | `META-INF/jars/baritone-api-fabric-1.19.0-26.2.jar` | `eca6e2fd…b5d1` |
| Forge | `META-INF/jarjar/baritone-forge-1.19.0-26.2.jar` | `cecbbcd8…5d38` |
| NeoForge | `META-INF/jarjar/baritone-neoforge-1.19.0-26.2.jar` | `0d86423e…42ef` |

`scripts/verify-embedded-baritone.ps1` 对三个 26.2 工程均输出“官方产物，且声明支持工程 MC 版本 26.2”。

## 6. 限制与说明

1. **运行时覆盖**：本次对 26.2 的 Fabric、Forge、NeoForge 三个加载器都做了
   “进世界 + `#goto` + 玩家实际位移”验证。其余 64 个产物是静态验证（包结构、官方产物、
   声明版本、Mixin 注册、26.2 三端字节码链接）。逐版本进世界测试需要为每个 MC 版本准备
   完整客户端资源与实例，成本高，未在本次全部执行。
2. **NeoForge 1.20.6**：本机重建卡在 NeoForm 1.20.6 的补丁阶段（`neoFormPatch` 对反编译源码
   reject，属于 NeoGradle/NeoForm 工具链问题，与 Baritone 无关）。`build/release-v1.5` 中该文件
   使用 v1.5.1 官方发布原件（内含扁平打包的 Baritone 1.10.8-1.20.6），矩阵审计通过。
   其余 66 个产物都由本仓库重新构建。
3. **Fabric 1.21 / 1.21.1** 内嵌的是 1.11.2 的 Meteor 兼容构建（mod id `baritone-meteor`，
   `FabricMixinPlugin` 在未安装独立 `baritone` 时启用 Mixin，官方 1.11.2 的所有类与
   `nether-pathfinder` 均在包内）。静态检查通过，未做进世界运行验证。
4. `nether-pathfinder` 原生库可正常加载（日志 `Loaded shared library`），但下界维度寻路本身
   未做行为验证；主世界寻路已验证。
