# v1.5.2 全版本归档核验与整合包运行对照（2026-11-21）

对象为 GitHub Release `v1.5.2`（标签提交 `f9d6be04`），不是本地 `main` 的重建版本。本次不修改发布 JAR 或用户启动器实例。

## 全版本矩阵

下表 `✓` 表示本地生产 JAR 与远端发布资产的字节数、SHA-256 一致，且重新通过归档验证：顶层及嵌套 JAR 的 ZIP CRC、所需运行类/资源、许可材料和注册 Mixin 的类文件。`—` 表示没有该发布组合。文件名差异按加载器和 MC 版本对应，以散列作最终确认。

| Minecraft | Fabric | Forge | NeoForge | 整合包进世界实测（与左侧归档检查分开计数） |
|---|---|---|---|---|
| 1.20.1 | ✓ | ✓ | ✓ | Fabric：Prominence II 通过；AdventureCrafts 相机 Mixin 冲突；Create+ 与 Simply Optimized 的固定 Loader 低于正式 JAR 要求 |
| 1.20.2 | ✓ | ✓ | ✓ | 未测 |
| 1.20.3 | ✓ | ✓ | ✓ | 未测 |
| 1.20.4 | ✓ | ✓ | ✓ | Fabric：Fabulously Optimized 原包登录并保存；加装组因固定 Loader 0.15.11 不满足要求而拒载 |
| 1.20.5 | ✓ | — | ✓ | 未测 |
| 1.20.6 | ✓ | ✓ | ✓ | 未测 |
| 1.21 | ✓ | ✓ | ✓ | 未测 |
| 1.21.1 | ✓ | ✓ | ✓ | Fabric：Aged 复测进世界，首次原生崩溃；Better MC 原始加载器不满足加装要求 |
| 1.21.2 | ✓ | — | ✓ | 未测 |
| 1.21.3 | ✓ | ✓ | ✓ | 未测 |
| 1.21.4 | ✓ | ✓ | ✓ | Fabric：All the MrCrayfishs Mods 原包、加装均登录、保存并退出 `0`（两组均使用目录限定 ZIP agent） |
| 1.21.5 | ✓ | ✓ | ✓ | Fabric：All the MrCrayfishs Mods 原包、加装均登录、保存并退出 `0`（两组均使用目录限定 ZIP agent） |
| 1.21.6 | ✓ | ✓ | ✓ | 未测 |
| 1.21.7 | ✓ | ✓ | ✓ | 未测 |
| 1.21.8 | ✓ | ✓ | ✓ | 未测 |
| 1.21.9 | ✓ | ✓ | ✓ | Fabric：Simply Optimized 原包、加装均登录、保存并退出 `0`（两组均使用目录限定 ZIP agent） |
| 1.21.10 | ✓ | ✓ | ✓ | 未测 |
| 1.21.11 | ✓ | ✓ | ✓ | Fabric：Simply Optimized 两组使用目录限定 ZIP agent 后均登录、保存并退出 `0`；加装组有关闭警告 |
| 26.1 | ✓ | ✓ | ✓ | Fabric：The Vanilla Experience 原包、加装均登录、保存并退出 `0`（两组均使用目录限定 ZIP agent） |
| 26.1.1 | ✓ | ✓ | ✓ | 未测 |
| 26.1.2 | ✓ | ✓ | ✓ | 未测 |
| 26.2 | ✓ | ✓ | ✓ | Fabric：进世界、保存和读回通过，两次退出 `-8`；NeoForge：Technical Electrical 加装组启动失败；Forge 未测 |
| 26.3 | ✓ | ✓ | ✓ | Fabric、NeoForge 已进世界；NeoForge 退出异常；Forge 待确认 |

## Prominence II 4.1.7

- 环境：Windows x64、Azul Java 17.0.21、Fabric Loader 0.19.3，最大堆 8 GiB，离线测试身份。MRPACK 的 SHA-512 与固定配置匹配。
- 原包 465 个顶层模组 JAR：408 个与清单或 FTB 补充依赖 SHA-512 匹配，57 个与包内 overrides 对应文件匹配。实验组只额外加入正式 Fabric 1.20.1 JAR（SHA-256 `a2e9cf025aa1aa057ddc4fb16fdafaa5ce0e46717a57c64baebaca70667ccbdb`）。
- 两组各自使用相同测试存档和配置的独立副本，通过 Quick Play 直接进入存档，不经过人工菜单点击。第一次两组均在 Fabric 重映射缓存写入处失败；复测两组均复制既有 Fabric 缓存并使用同一 ZIP 可写性诊断 agent，作用域分别限于各自测试目录。未修改生产 JAR。
- **加装 v1.5.2**：Loader 解析 719 个模组；10:21:02 服务端记录玩家加入；10:21:22 全部维度保存完毕；关闭窗口后退出码 0，无强制终止。日志中的离线身份认证失败不等于世界启动失败。

- **未装 Wurst 的对照组**：Loader 解析 700 个模组；10:25:06 服务端记录玩家加入；10:25:24 全部维度保存完毕；退出码 0，无强制终止。Necronomicon 的 `AnimatedItemNameMixin` 与 Packet Fixer 的常量冲突警告两组均有，不是本次加装产生的差异。

## 26.3 整合包对照增测

Windows x64、Java 25、独立测试目录、离线身份 `TestRunner`。每个组合核对 MRPACK 和包内下载项的哈希；原包与加装组的唯一区别是 `mods` 中是否额外放入相应正式发布 JAR。下列计数是顶层模组 JAR 数，不是加载器解析出的模组数。

| 整合包（Modrinth 版本 ID） | 原包锁定的加载器 | 原包 → 加装 | 结果 |
|---|---|---:|---|
| Fabulously Optimized 15.0.0-beta.2 (`Wo44jriL`) | Fabric 0.19.5 | 46 → 47 | 两组均登录世界，退出时记录三个维度保存，退出码 0。加装组 FastQuit 报告保存成功；Baritone 缓存工作线程在关闭时打印 `InterruptedException`。 |
| Simply Neoforged 26.3-1.0.3 (`sz2pHbyV`) | NeoForge 26.3.0.39-beta | 12 → 13 | 两组均进世界并记录三个维度保存，退出码 0。加装组重新启动并成功读回同一存档。两次退出控制台末尾均出现 `Exception in thread "WurstB-Shutdown"`；原包未见。没有完整异常栈，不认定根因或所有设置已保存。 |
| Fastly Optimised b-26.3 (`YA8iKYaF`) | Forge 66.0.4 | 8 → 9 | 两组均已启动。旧模组存档不计分；改用原版存档副本后，原包和加装组均停在相同的实验性世界确认窗口。未确认加载，不判定进世界通过或不兼容。以包内清单的 `forge` 为准，而非包名中的 FABRIC 字样。 |

26.3 的判据使用服务器记录 `logged in with entity id` 和停服后主世界、下界、末地三条 `Saving chunks for level`，并核对进程没有强制终止。原始运行器只匹配英文入服聊天和旧版 `All dimensions are saved`，因此不能把原始 JSON 的 `false` 当作进世界或保存失败。Fabric 首轮日志配置无效，仅采用重跑的日志。

证据：[Fabric 原包](../build/modpack-compat-v152-20261121/fo26/run3-baseline.log)、[Fabric 加装](../build/modpack-compat-v152-20261121/fo26/run3-with-v152.log)、[NeoForge 原包](../build/modpack-compat-v152-20261121/neo26/run2-baseline.log)、[NeoForge 加装](../build/modpack-compat-v152-20261121/neo26/run2-with-v152.log)、[NeoForge 同档重进](../build/modpack-compat-v152-20261121/neo26/reload.json)、[Forge 原包](../build/modpack-compat-v152-20261121/forge26/run2-baseline.log)。

## 1.20.1 / 1.21.1 大型内容包增测

| 包（Modrinth 版本 ID） | MC / Fabric Loader；原包 → 加装顶层 JAR | 验收结论 |
|---|---|---|
| AdventureCrafts [Fabric] 2.2.5 (`DotEHi3W`) | 1.20.1 / 0.19.3；329 → 330 | 原包已到 Guardian 存档兼容提示，未确认修复；之后在 `glfw.dll` 中发生原生访问异常。加装组在客户端初始化时即失败：Better Third Person 已重定向相机 yaw/pitch，导致 Wurst 的 `OpenOpalCameraMixin.opalYaw` 必需注入命中 `0/1`。两组均未登录，原包也不是健康基线；两种失败的阶段和栈不同。 |
| Better MC [FABRIC] BMC3 v30 (`OdTbhRit`) | 1.21.1 / 0.16.14；292 → 293 | 使用整合包提示的 9012 MiB 堆，原包在 11:21:54 登录、11:22:10 全维度保存，退出 `0`。加装组因正式 JAR 要求 Fabric Loader ≥0.17.0，被 0.16.14 拒绝，退出 `1`，未进世界。未升级加载器。 |
| Aged 4.0.2 (`9Js910iu`) | 1.21.1 / 0.20.1；189 → 190 | 原包登录、保存并退出 `0`。加装首次在登录前原生崩溃；按相同堆大小（5632 MiB）、相同初始存档独占 JVM 复测，11:33:27 登录、11:33:42 保存全部维度，退出 `0`。有进世界证据，不判定稳定通过；此处不是读回首次加装存档。 |

Better MC 使用发布的 Modrinth 包及 overrides。包作者另列 26 个需手工下载的补充项，本轮未验证这些内容全部装齐；原包登录是这份 MRPACK 客户端的实测，不等于完整内容验收。此限制不改变加装组明确的加载器最低版本冲突。两组使用同一测试存档的独立副本，均对本机 Fabric ZIP 可写性错误应用相同的实例目录限定诊断 agent。前期 5632 MiB 堆测试触发了包内内存提示，不作为最终配对结果。

证据：[Better MC 原包](../build/modpack-compat-v152-20261121/batch-content21/baseline-9012.json)、[加装拒载日志](../build/modpack-compat-v152-20261121/batch-content21/with-v152-9012.log)、[手动依赖记录](../build/modpack-compat-v152-20261121/batch-content21/manual-requirements.json)。

AdventureCrafts 的两组使用 Java 21、6 GiB 最大堆、同一旧测试存档的独立副本；测试后两份 `level.dat` 哈希均未改变。[原包与加装结果](../build/modpack-compat-v152-20261121/batch-content20/results.json)、[配对输入核验](../build/modpack-compat-v152-20261121/batch-content20/controlled-input-check.json)、[原包原生崩溃报告](../build/modpack-compat-v152-20261121/batch-content20/baseline-native.log)、[加装组相机冲突及致命异常](../build/modpack-compat-v152-20261121/batch-content20/with-v152.console.log)。

Aged 首次加装 JVM 的原生栈指向 `glfw.dll`，同一 PID 的 Windows 事件记录 `OpenAL.dll`；未确定崩溃原因，也未归因于 Wurst。[原包结果](../build/modpack-compat-v152-20261121/batch-content21/aged/baseline-scoped.json)、[首次加装](../build/modpack-compat-v152-20261121/batch-content21/aged/with-v152-scoped.json)、[原生栈](../build/modpack-compat-v152-20261121/batch-content21/aged/hs_err_pid13040.log)、[Windows 事件](../build/modpack-compat-v152-20261121/batch-content21/aged/windows-event-13040.json)、[加装复测](../build/modpack-compat-v152-20261121/batch-content21/aged/with-v152-retry.json)。

## 26.2 对照增测

原包与加装组分别从同一份 26.2 原版存档复制起步，Quick Play 离线登录；未修改真实存档、禁用包内模组或替换正式发布 JAR。两组 JVM 均为 Java 25，最大堆 4 GiB。模组数为顶层 JAR 数。每个实例核对包清单指定的加载器，不沿用相邻版本。

| 整合包（Modrinth 版本 ID） | 加载器；原包 → 加装 | 登录、保存、退出结果 |
|---|---|---|
| Fabulously Optimized 14.0.0 (`HiDsl7yh`)，优化包 | Fabric 0.19.5；49 → 50 | 原包成功登录、保存、退出 `0`；加装成功登录，FastQuit 明确确认保存，但退出 `4294967288`（有符号 `-8`）。复制加装组刚写出的存档，在新的加装实例成功重进、再次保存，仍退出 `-8`。三次均未被测试脚本强制终止；两次加装崩溃报告都显示 `ClientShutdownWatchdog: Client shutdown from post-main`，未证实具体阻塞线程。 |
| Technical Electrical Temporal Totality 0.26.3.9 (`ytAolSjB`)，内容包 | NeoForge 26.2.0.75；64 → 65 | 原包成功登录、保存所有维度、退出 `0`。加装组在登录前进入 NeoForge 致命错误窗口；关闭窗口后退出 `1`。同存档起点的独立诊断复测亦为 `1`；两次均未强制终止。此包与正式版在该环境下**不兼容**。 |

Technical Electrical 的包版本号虽含 `26.3`，其 MRPACK 锁定的是 MC **26.2**。诊断复测仅额外打开 JVM 异常日志：`wurst.mixins.json:EntityMixin` 的 `wrapUpdateFluidInteractionIsPushedByFluid` 必需注入命中 `0/1`，随后 `MixinTransformerError` 传播到 NeoForge 客户端入口。没有证据把冲突归因于包内某一个第三方模组。该注入不是前文所述的火焰遮罩或流体透明度常量注入。

证据：[Fabric 原包](../build/modpack-compat-v152-20261121/batch-fo262/baseline.json)、[加装](../build/modpack-compat-v152-20261121/batch-fo262/with-v152.json)、[读回](../build/modpack-compat-v152-20261121/batch-fo262/reload.json)、[首次退出报告](../build/modpack-compat-v152-20261121/batch-fo262/shutdown-first.txt)、[重进后退出报告](../build/modpack-compat-v152-20261121/batch-fo262/shutdown-reload.txt)；[NeoForge 原包](../build/modpack-compat-v152-20261121/batch-technical262/baseline.json)、[加装](../build/modpack-compat-v152-20261121/batch-technical262/with-v152.json)、[诊断复测](../build/modpack-compat-v152-20261121/batch-technical262/diagnostic.json)、[JVM 异常明细](../build/modpack-compat-v152-20261121/batch-technical262/diagnostic-exceptions.log)。

## Fabric 多版本专项

本批只启动 Fabric 客户端，实测 **7 个 MC 版本、8 套固定包配置**。原包 8 套均登录、保存并退出 `0`；加装正式 JAR 后 5 套完成相同步骤，3 套因原包固定 Loader 过旧而拒载。按包清单锁定 Minecraft 和 Loader，对照组与加装组各使用初始存档的独立副本。这里的通过不代表所有功能可用或无警告；同一 Minecraft 版本上的不同包不重复计算版本覆盖。

| Minecraft | 整合包（Modrinth 版本 ID） | Fabric Loader | 原包 / 加装正式 v1.5.2 |
|---|---|---|---|
| 1.20.1 | Create+ 3.1.1 (`rlTgMYB4`) | 0.16.10 | [原包](../build/modpack-compat-v152-20261121/fabric-focus-create/baseline.json)登录、保存并退出 `0`；[加装组](../build/modpack-compat-v152-20261121/fabric-focus-create/with-v152.log)在依赖检查阶段拒载，退出 `1`。正式 JAR 需要 Loader ≥0.17.0。 |
| 1.20.1 | Simply Optimized 1.20.1-12.3 (`MSICaFbr`) | 0.16.0 | [原包](../build/modpack-compat-v152-20261121/fabric-focus-so/baseline.json)登录、保存并退出 `0`；[加装组](../build/modpack-compat-v152-20261121/fabric-focus-so/with-v152.log)同样因 Loader 低于 0.17.0 退出 `1`。未替换加载器。 |
| 1.20.4 | Fabulously Optimized 5.10.2 (`JcFyKEJ3`) | 0.15.11 | [配对结果](../build/modpack-compat-v152-20261121/focus-1204/result.json)：47 → 48 个顶层 JAR。[原包](../build/modpack-compat-v152-20261121/focus-1204/baseline.latest.log)登录、保存全部维度并退出 `0`，期间有 Win32 渲染错误；[加装组](../build/modpack-compat-v152-20261121/focus-1204/with-v152.latest.log)启动时明确要求 Loader ≥0.17.0，未登录、未写存档，退出 `1`。 |
| 1.21.4 | All the MrCrayfishs Mods 1.0.0 (`TWMtXjp8`) | 0.17.2 | [配对结果](../build/modpack-compat-v152-20261121/focus-1214/result.json)：19 → 20 个顶层 JAR；两组均登录、停服保存三个维度，存档 `LastPlayed` 更新，退出 `0`。[原包日志](../build/modpack-compat-v152-20261121/focus-1214/baseline.log) · [加装日志](../build/modpack-compat-v152-20261121/focus-1214/with-v152.log)。 |
| 1.21.5 | All the MrCrayfishs Mods 1.0.0 (`fP01hueR`) | 0.17.2 | [配对结果](../build/modpack-compat-v152-20261121/focus-extra-1.21.5/result.json)：18 → 19 个顶层 JAR；两组使用目录限定 ZIP agent，均登录并在停服后保存三个维度，退出 `0`。[原包日志](../build/modpack-compat-v152-20261121/focus-extra-1.21.5/baseline.log) · [加装日志](../build/modpack-compat-v152-20261121/focus-extra-1.21.5/with-v152.log)；加装组另有重复字体码点和 SeedCracker 结构文件警告。 |
| 1.21.9 | Simply Optimized 1.21.9-0.2 (`lIGIcPaR`) | 0.17.2 | 11 → 12 个顶层 JAR；[原包](../build/modpack-compat-v152-20261121/fabric-focus-so1219/baseline-resolved.json)和[加装](../build/modpack-compat-v152-20261121/fabric-focus-so1219/with-v152-resolved.json)均使用目录限定 ZIP agent，分别在 12:21:37、12:22:28 登录，停服后所有维度保存完毕，未强制终止，退出均为 `0`。 |
| 1.21.11 | Simply Optimized 1.21.11-3.1.2 (`UP68ggjK`) | 0.18.4 | [无 agent 首轮](../build/modpack-compat-v152-20261121/focus-12111/unassisted-result.json)两组均因重映射临时 JAR 无法写入而失败。两组使用目录限定 ZIP agent 后，[配对复测](../build/modpack-compat-v152-20261121/focus-12111/result.json)均登录、保存三个维度并退出 `0`；[加装日志](../build/modpack-compat-v152-20261121/focus-12111/with-v152-scoped.log)在关闭时有两条 `InterruptedException`，均来自 `baritone.cache.CachedWorld` 工作线程。仅判有条件通过。 |
| 26.1 | The Vanilla Experience 7.33 (`xOch9NAm`) | 0.18.4 | [输入比对](../build/modpack-compat-v152-20261121/focus-extra-26.1/pair.json)：150 → 151 个顶层 JAR。[原包](../build/modpack-compat-v152-20261121/focus-extra-26.1/baseline.json)和[加装](../build/modpack-compat-v152-20261121/focus-extra-26.1/with-v152.json)均使用 Java 25 和目录限定 ZIP agent；两组均登录、停服保存三个维度、存档 `LastPlayed` 前移且未强制终止，退出 `0`。 |

两套 1.20.1 包使用同一份官方服务端新建的原版世界作为起点；每个进程使用独立副本。原包顶层模组分别为 76、18 个；加装组仅额外放入正式 Fabric 1.20.1 JAR。两组均使用限定于各自测试目录的 ZIP 可写性诊断 agent。

1.20.4、1.21.4 的两组也均使用目录限定 ZIP agent。1.21.4、1.21.9 首轮的启动类路径重复包含 ASM；表内只采信修正测试启动器类路径、从相同初始存档重新复制后运行的成对结果。启动器现按 Fabric 配置覆盖原版同名依赖；未替换整合包的 Loader 或模组。

## 限制与证据

- Fabric 加装正式 v1.5.2 后有整合包进世界证据的版本为 **9/23**：1.20.1、1.21.1、1.21.4、1.21.5、1.21.9、1.21.11、26.1、26.2、26.3。其中 5 个是本批首次取得证据，另外 14 个版本尚无加装进世界记录。另计此前的 26.3 NeoForge，全部加载器合计 **10/67** 个 MC × 加载器组合。固定 Loader 实测拒载不增加进世界数；这两个分数都是覆盖数，不是兼容率。

- 进世界覆盖以正式 JAR 在整合包中完成玩家登录为准，同一 MC 和加载器下的多个整合包不重复计数。进世界不等于干净退出；归档检查不代表第三方 Mixin 能实际织入。未测联网、全功能切换或长时运行。
- 标签发布后，本地 `1e51e8d5` 为 33 个工程的 39 处火焰遮罩/流体透明度常量注入增加容错；这里核验的正式 JAR 不含该修复。这是目标常量被其他模组改写时的风险，不是本轮 Prominence II 实例复现的崩溃。
- 附加验证：使用仓库缓存的 JDK 运行 Java 行为测试，39 项通过（含菜单所有权与可选渲染注入判断）；属于源码测试，不是 39 次游戏启动。
- 测试启动器修复后，`scripts/tests` 的 Python 回归测试 40 项通过，其中 7 项覆盖整合包准备和运行环境解析。将修复后的 ASM 选择与 1.21.4、1.21.9 两次真实启动的参数回比，两者都只使用 `asm-9.8.jar`。
- 本机原始证据：[67 个 JAR 的逐项结果](../build/modpack-compat-v152-20261121/package-audit.json)、[远端资产摘要](../build/modpack-compat-v152-20261121/release-assets.json)、[首次失败结果](../build/modpack-compat-v152-20261121/runtime-result.json)、[复测退出状态](../build/modpack-compat-v152-20261121/runtime-retry.json)、[加装日志](../build/modpack-compat-v152-20261121/with-v152-retry.log)、[对照日志](../build/modpack-compat-v152-20261121/baseline-retry.log)、[Java 测试](../build/modpack-compat-v152-20261121/java-compat-tests.log)。这些 build 输出为本机证据，不随发布分发。
