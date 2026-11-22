# Fabric 整合包修复候选验收（2026-11-21）

以下为本地源码构建，不是重新发布的 `v1.5.2`。测试使用整合包清单固定的 Loader、独立实例和存档副本；离线单人登录、停服后保存及退出码分别核对。1.20.1、1.20.4、1.21.1 沿用限定于测试目录的 ZIP 可写性诊断 agent；26.2 的最终两次验收均未加线程创建探针。正式包在相同环境的记录见[原版验收](MODPACK-COMPAT-v1.5.2-2026-11-21.md)。

| MC | 固定整合包 / Loader | 候选结果 |
|---|---|---|
| 1.20.1 | Simply Optimized `MSICaFbr` / `0.16.0` | 原包可运行，正式 v1.5.2 拒载。[源码候选](../build/fabric-fix-20261121/1.20.1/so.json)已登录、保存并退出 `0`；原始存档未更改。 |
| 1.20.1 | Create+ `rlTgMYB4` / `0.16.10` | [原版世界副本](../build/fabric-fix-20261121/1.20.1/create.json)已越过依赖检查，但未登录，关窗退出 `0` 不计通过。改用原包已写出的世界副本，实际界面停在 Minecraft 的实验性设置确认；未替用户确认。 |
| 1.20.1 | AdventureCrafts `DotEHi3W` / `0.19.3` | [候选复测日志](../build/fabric-fix-20261121/1.20.1/adventure-retry.log)：330 个顶层 JAR，原有 `OpenOpalCameraMixin` 重定向崩溃未再出现，已进入原包也有的 Guardian 存档迁移确认界面。未确认修改隔离世界，未取得登录证据。第一次候选启动因脚本误判另一模组的非致命警告而提前结束，不计入结果。 |
| 1.20.4 | Fabulously Optimized `JcFyKEJ3` / `0.15.11` | 正式版在检查最低版本时拒载。[源码候选](../build/fabric-fix-20261121/1.20.4/source-built.json)已登录、保存三个维度，退出 `0`，未强制终止；原包、候选均有 Win32 渲染错误，不据此认定渲染无异常。 |
| 1.21.1 | Better MC BMC3 v30 `OdTbhRit` / `0.16.14` | [源码候选](../build/fabric-fix-20261121/1.21.1/candidate-9012.json)已登录、停服保存全部维度并退出 `0`。[逐项比对](../build/fabric-fix-20261121/1.21.1/jar-comparison.json)只有 `fabric.mod.json` 内容不同。使用 9012 MiB 堆；不包括包作者另列的 26 个手动补充项。 |
| 26.2 | Fabulously Optimized 14.0.0 `HiDsl7yh` / `0.19.5` | [正式包复现](../build/fabric-fix-20261121/26.2/release.json)在保存后退出 `-8`。[源码候选首次](../build/fabric-fix-20261121/26.2/final.json)和[读取其存档的复测](../build/fabric-fix-20261121/26.2/readback.json)均登录、停服后保存三个维度并退出 `0`；均未强杀，无崩溃报告。Baritone 缓存工作线程收到取消时仍打印 `InterruptedException`，不计为无警告。 |

仅对实测的 MC 子项目调整运行时最低 Loader 声明，不修改整合包 Loader，不要求已有玩家迁移存档。1.20.1 另在加载 `betterthirdperson` 时保留该模组对相机读数的控制：此时 Wurst 的 OpenOpal 相机 yaw/pitch 替换不生效，不影响其他 Mixin 或未安装该模组的客户端。

回归测试：[独立 Java 行为测试](../build/fabric-fix-20261121/1.20.1/behavior-tests.log) 45 项通过（包括相机所有权）；Python 工具回归 40 项通过；[1.21.1 子项目测试与构建](../build/fabric-fix-20261121/1.21.1/build-04.log)通过。真实启动结果与单元测试分开计数。

26.2 的[创建栈](../build/fabric-fix-20261121/26.2/stacks.log)定位到 SeedCrackerX 的线程池；[只关闭该池的对照](../build/fabric-fix-20261121/26.2/seed-only.json)未能消除退出故障。最终修复在客户端关闭时同时处理 SeedCrackerX 和 Baritone 的长期缓存任务，让已提交的缓存写入有机会完成；未禁用游戏的关停 watchdog。

本机候选 JAR：[1.20.1](<../build/fabric-fix-20261121/1.20.1/WurstB+ Plus-1.5.2-Fabric-1.20.1-compat.1.jar>)、[1.20.4](../build/fabric-fix-20261121/1.20.4/source-built.jar)、[1.21.1](<../build/fabric-fix-20261121/1.21.1/gradle/libs/WurstB+ Plus-1.5.2-Fabric-1.21.1.jar>)、[26.2](../build/fabric-fix-20261121/26.2/candidate.jar)。仅在对应 MC 版本使用，不能与同版本正式 JAR 同时放入 `mods`。
