# Fabric 全版本兼容修复与整合包验收（2026-11-21）

对象是本地源码候选，不是已发布的 v1.5.2。全部 23 个 Fabric 工程都已接入相机兼容判断；这不是 23 个版本都通过了整合包进世界测试的声明。

本轮最终候选在 **4 个 Minecraft 版本、6 个「版本 × 固定整合包」组合**完成离线单人登录、停服保存、正常退出：1.21.4 两组，1.21.6 两组，1.21.11 和 26.2 各一组。原包对照、26.2 读回复测、较早轮次不重复计数。1.21.5、1.21.9、26.1、26.3 未取得与当前源码匹配的候选启动证据；26.3 临时 JAR 的退出 `0` 不计。上轮 Create+ 的实验性设置确认、AdventureCrafts 的迁移确认也不计通过。

## 相机与 Loader

- 根工程（1.20.1）、1.20.2 至 1.20.6、1.21 至 1.21.11、26.1、26.1.1、26.1.2、26.2、26.3：安装 `betterthirdperson` 时，让它控制相机旋转，不应用 Wurst 的 `OpenOpalCameraMixin`；未安装时仍应用。FancyMenu、Sodium 及其他游戏 Mixin 的判断保持原样。
- [配置注册检查](../scripts/tests/test_fabric_camera_registration.py)逐工程从 `fabric.mod.json` 读取实际客户端 Mixin 配置，检查相机恰好注册一次、绑定被测插件。[独立 Java 回归](../build/fabric-rollout-20261121/java.log)按源码去重，覆盖 4 种 Fabric 插件实现以及非 Fabric 的既有行为，58 项通过；[Python 工具回归](../build/fabric-rollout-20261121/python.log)41 项通过。独立测试不启动 Minecraft，不充当渲染或整合包证据。
- [23 份正式发布 JAR 的递归依赖清单](../build/fabric-rollout-20261121/loader-audit.json)：先比对发布 SHA-256，再读出每个内嵌模组对 `fabricloader` 的声明。声明相容只用于筛选待测版本，不能证明 Loader 实际兼容。
- 运行时最低 Loader 仅按已实测版本调整：1.20.1 `0.16.0`、1.20.4 `0.15.11`、1.21.1 `0.16.14`（[上一轮证据](FABRIC-FIX-2026-11-21.md)），本轮新增 1.21.6 `0.16.14`。其余 19 版未下调；未修改任何整合包的 Loader。

## 1.21.6 固定包对照

使用 Java 21、原包指定 Loader、独立客户端实例和同一份测试存档的副本。正式通过要求：记录单人玩家登录、停服后保存所有维度，存档 `LastPlayed` 增长，原始存档不变，没有强杀或崩溃报告且进程退出 `0`。测试进程使用限定于各自实例目录的 ZIP 兼容辅助，不修改 Launcher 或发行包。验收对象是离线单人启动与保存，不含多人或各模组的完整玩法。

| MC / 整合包 | 原包固定 Loader | 原包与加装结果 |
|---|---|---|
| 1.21.6 / Simply Optimized `KOt1v4xx`，15 个原包顶层 JAR | `0.16.14` | [原包](../build/fabric-rollout-20261121/1.21.6/KOt1v4xx/baseline/result.json)通过；[旧声明](../build/fabric-rollout-20261121/1.21.6/KOt1v4xx/unchanged/console.log)明确因 Wurst 要求 `>=0.17.0` 拒载，未登录且退出 `1`；[最终候选](../build/fabric-rollout-20261121/1.21.6/KOt1v4xx/final/result.json)通过，退出 `0`。 |
| 1.21.6 / All the MrCrayfishs Mods `iuD48htU`，18 个原包顶层 JAR | `0.17.2` | [原包](../build/fabric-rollout-20261121/1.21.6/iuD48htU/baseline/result.json)和[最终候选](../build/fabric-rollout-20261121/1.21.6/iuD48htU/final/result.json)均通过，退出 `0`。 |

两份 `final` 加载的 JAR SHA-256 同为 `cb3cdef5a226707c048006ac1e1e77ef11c1132c4762af03162491ae030d57eb`，包含相机与 Loader 修复，但不含试验过的 `ClientWorkerPools`、`SeedWorkerPoolsMixin`。较早的 `candidate` 实例不计入此表。

## 另外两组单人验收

以下两组也已使用最终候选实际进世界。两包都不含 Better Third Person，因此不能单独证明与该模组共存时的相机表现：

| MC / 固定整合包 | Loader | 当前候选实测 |
|---|---|---|
| 1.21.4 / All the MrCrayfishs Mods 1.0.0 | `0.17.2` | [本次启动](../.dsh-tmp/pack-offline-853532e5/1.21.4/offline/review.json)：登录、停服保存三个维度，退出 `0`。测试 JAR 与按当前输入构建的对照 JAR 的 SHA-256 相同。 |
| 1.21.11 / Simply Optimized 1.21.11-3.1.2 | `0.18.4` | [本次启动](../.dsh-tmp/pack-offline-853532e5/1.21.11/offline/review.json)：登录、停服保存三个维度，退出 `0`；有两条线程中断日志。 |

两组均已核对：候选实际加载、存档副本 `LastPlayed` 增长、原始存档未变、未强杀且无崩溃报告。

## Feather 相机共存实测

Feather 3.1.3，MC 1.21.4，Fabric Loader `0.19.3`。原始清单中 111 个客户端文件全部通过 SHA-512 校验；原包有 99 个顶层模组。以同一存档的两个独立副本分别启动，[原包结果](../build/feather-acceptance-20261121/1.21.4/feather-baseline/result.json)及[候选结果](../build/feather-acceptance-20261121/1.21.4/feather-final/result.json)均已登录、停服保存主世界、下界及末地，退出 `0`，无强杀、无崩溃报告，副本 `LastPlayed` 增长且原始存档未变。

[候选日志](../build/feather-acceptance-20261121/1.21.4/feather-final/console.log)确认 `betterthirdperson 1.9.0` 与 `wurstpenguin 1.5.2-Fabric-1.21.4` 同时加载，未见原相机注入崩溃。实测候选 SHA-256 为 `19481192374b243287753d8688074e9c4682ebb4c971e664690e04d50be769ff`，与同版 All the MrCrayfishs Mods 一致。本次验证了两者共存时能进世界、保存并退出，不包括手动切换视角或验证镜头旋转手感。

## 1.20.x 项目测试


六版分别执行完整的离线 Gradle 项目测试。按各版本最后一次完整运行的 [JUnit 结果](../build/fabric-rollout-20261121/batch)统计：1.20.1、1.20.2、1.20.3、1.20.4 各 146 项；1.20.5、1.20.6 各 150 项，合计 **884 项通过，失败、错误、跳过均为 0**。1.20.2、1.20.3、1.20.5 首轮在清理临时目录时失败，换较短的独立路径后各自完整重跑通过。每版另外执行的 35 项组件检查有重叠，不重复计数。本项没有启动整合包。

## 退出路径及 26.2 读回

逐版检查了 1.20.x 六版及 1.21.x 十二版的 `Minecraft.destroy()` 字节码。在无延迟崩溃的正常关闭分支上，这些版本先执行 `close()`，然后显式 `System.exit(0)`。这不排除 `close()` 内部阻塞，但不能据此推断主方法返回后非守护线程导致进程挂住。未在这 18 版新增线程池注入，也未改变其既有关闭逻辑。历史记录只作对照，不重计为本轮启动。

26.1、26.1.1、26.1.2 的正常退出路径同样显式结束 JVM；26.3 有关闭 watchdog，但没有复现 26.2 的失败。上述四版没有新增线程池注入，最终源码各通过 254 项 Gradle 项目测试，合计 1,016 项，无失败或跳过。另有[七版相机专项逐版结果](../build/fabric-rollout-20261121/camera-only/20261121-170232/results.csv)：对照实际 Minecraft 字节码检查相机调用点，再隔离测试，合计 42 项通过；不计入实际启动。

26.2 的故障和修复有独立对照：[正式版及只关闭 SeedCrackerX 的候选均退出 -8，修复后保存并读回](../build/fabric-fix-20261121/26.2/verification.md)。保留游戏原有的关闭 watchdog。

本轮 26.2 最终源码在 Fabulously Optimized `HiDsl7yh`、Loader `0.19.5` 上[首次启动](../build/fabric-rollout-20261121/26.2-world/first.log)和[读回刚写出的存档](../build/fabric-rollout-20261121/26.2-world/readback.log)均登录并在停服后保存三个维度；第二次实际从磁盘加载了 Baritone 缓存区域。两次均退出 `0`、未强杀、无崩溃报告（[首次退出](../build/fabric-rollout-20261121/26.2-world/first.json)、[复测退出](../build/fabric-rollout-20261121/26.2-world/readback.json)）。上游缓存线程按预期在取消时打印 `InterruptedException`。1.21.6、26.2 的 Gradle 项目测试各通过 254 项，无失败或跳过。
