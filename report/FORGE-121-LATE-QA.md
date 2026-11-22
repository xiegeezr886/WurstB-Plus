# Forge 1.21.6 至 1.21.11 固定整合包 QA · 2026-11-21

**1.21.9 已确证并修复生产打包缺口：重建后的安全候选无需外置 MixinExtras，即可在原固定包上进世界、保存并由新 JVM 读回。** 下方六版矩阵保留修复前的发布包结果：当时只有 1.21.10 的候选在未补依赖时通过。未替换已发布的 v1.5.2 文件。

## 1.21.9 生产归档修复复验

- **归因：** 旧版 `allJar` 在开发类路径声明 MixinExtras，但归档没有它的 JarJar 条目，递归检查也找不到 `Operation.class`。Forge `59.0.5` 的实际类路径和固定包的四个模组都没有提供该类。旧候选在该处失败，单独补入 0.5.4 即通过。相邻 1.21.10 的已发布 JAR 同样没有这个类，但 [Forge 60.1.0 的既有运行日志](../build/forge-121-late-qa/1.21.10/candidate-repeat/game/logs/debug.log)显示选中的是 Forge 运行库提供的 `0.5.0`，而非 Exordium 内嵌的 `0.4.1`；本轮未重跑或更改相邻版本。
- **成品：** 只改 1.21.9 的 `allJar` 和版本自有 JarJar 清单，保留 Baritone，完整内嵌 `io.github.llamalad7:mixinextras-forge:0.5.4`。[隔离构建日志](../build/forge-121-late-qa/1.21.9/rebuild/build.log)确认 `compileJava`、`processResources`、`allJar` 成功。[成品检查](../build/forge-121-late-qa/1.21.9/packaging-fix.json)记录修复前后 SHA-256、嵌套原件摘要及唯一的 `Operation.class` 路径。成品已不含 ForceOP 类和注册引用；原 `@Mod` 入口保留，测试副本只额外加离线目录保护。
- **实测：** `PqNBUg09`、Forge `59.0.5` 不变。两轮 [mods 清单与候选摘要](../build/forge-121-late-qa/1.21.9/embedded-first/archive.json)相同，均为原包四个 JAR 加一个候选；没有单独摆放 MixinExtras，也没有修改启动类路径。日志确认初始化 `0.5.4`。[首次进档并保存](../build/forge-121-late-qa/1.21.9/embedded-first/result.json)和[新 JVM 读回并再保存](../build/forge-121-late-qa/1.21.9/embedded-readback/result.json)均退出 0、没有崩溃或强杀。第二轮启动前的整档指纹等于第一轮退出后的指纹；`LastPlayed` 为 `1794917569538 → 1795261305791 → 1795261338130`。本轮构建产物和测试候选均不交付。

## 修复前：固定包与运行结果

来源为 Modrinth 项目 Fastly Optimised（`uBPAMdwq`）。每行是一个具体发布，不代表该版本的所有整合包。点击包 ID 查看发布文件名、来源 URL 和完整 SHA-512；点击运行结果查看进程退出、登录、保存及存档指纹。JAR 数为原包 `mods` 顶层数量，包含 overrides，不等于 Forge 实际加载数。

| Minecraft | MRPACK 版本 ID | 固定 Forge | 原包 JAR | 原包 | 加离线候选，未补依赖 |
|---|---|---|---:|---|---|
| 1.21.6 | [n0JKzc62](../build/forge-121-late-qa/1.21.6/pin.json) | 56.0.9 | 16 | [初始化崩溃](../build/forge-121-late-qa/1.21.6/baseline-repeat/result.json) | [相同崩溃](../build/forge-121-late-qa/1.21.6/candidate-repeat/result.json)：FPSBooster 的 `EntityRenderDispatcherMixin` 注入签名与目标不符。 |
| 1.21.7 | [cqbH9dCU](../build/forge-121-late-qa/1.21.7/pin.json) | 57.0.3 | 11 | [渲染崩溃](../build/forge-121-late-qa/1.21.7/baseline-repeat/result.json) | [相同崩溃](../build/forge-121-late-qa/1.21.7/candidate-repeat/result.json)：Exordium 的 `instance` 为空，调用 `getMainBuffer()` 失败。 |
| 1.21.8 | [GBWDtSdh](../build/forge-121-late-qa/1.21.8/pin.json) | 58.1.0 | 9 | [未取得入服或保存证据](../build/forge-121-late-qa/1.21.8/baseline-repeat/result.json) | [已到普通 HackList](../build/forge-121-late-qa/1.21.8/candidate-repeat/result.json)，未入服，`LastPlayed` 不变。两组均退出 0，不判通过。 |
| 1.21.9 | [PqNBUg09](../build/forge-121-late-qa/1.21.9/pin.json) | 59.0.5 | 4 | [登录、保存通过](../build/forge-121-late-qa/1.21.9/baseline/result.json) | [启动失败，退出 1](../build/forge-121-late-qa/1.21.9/candidate-repeat/console.log)：缺少 `com.llamalad7.mixinextras.injector.wrapoperation.Operation`，未到普通注册表。 |
| 1.21.10 | [v5qLzCYm](../build/forge-121-late-qa/1.21.10/pin.json) | 60.1.0 | 9 | [登录、保存通过](../build/forge-121-late-qa/1.21.10/baseline-repeat/result.json) | [首次保存](../build/forge-121-late-qa/1.21.10/candidate-repeat/result.json)、[新 JVM 同档读回并再保存](../build/forge-121-late-qa/1.21.10/readback-repeat/result.json)均通过。 |
| 1.21.11 | [GX9v8ztX](../build/forge-121-late-qa/1.21.11/pin.json) | 61.0.2 | 6 | [备份测试副本后登录、保存通过](../build/forge-121-late-qa/1.21.11/baseline-screen/result.json) | [Forge 拒载](../build/forge-121-late-qa/1.21.11/candidate-repeat/console.log)：`wurstpenguin` 要求 `[61.2.0,62)`，实装 `61.0.2`。没有改包或降低门槛。 |

1.21.6、1.21.7 两侧崩溃报告均列于各自结果 JSON 的 `crashes`，位于同轮 `game/crash-reports/`。它们证明不加候选也会失败，不证明候选在其他包上必然兼容。1.21.8 两侧均有 `pingfix resources` 的 `pack_format` 元数据错误，没有证据认定它是未入服的根因。

## 两条读回链与优先版对照

- **1.21.10，未加额外依赖：** 候选两轮均到达原注册表、登录、保存所有维度、退出 0。`LastPlayed` 从 `1794914814044` 到 `1795260574015`，读回后为 `1795260626323`。
- **1.21.9，补依赖诊断：** 保留失败组相同的候选 SHA-256 `54e7c216c599e91d004a36a64d89f8600360fb4aaf33cee9f5e283d3b235928e`，不换 Forge，只在诊断实例加项目声明的 [MixinExtras Forge 0.5.4](../build/forge-121-late-qa/1.21.9/dependency-first/supplement.json)。[首次保存](../build/forge-121-late-qa/1.21.9/dependency-first/result.json)和[同档读回](../build/forge-121-late-qa/1.21.9/dependency-readback/result.json)均退出 0；`LastPlayed` 为 `1794917569538 → 1795260362694 → 1795260399659`。这定位了该固定组合缺少运行时依赖，不是对发布 JAR 做了修复。
- **1.21.11：** 原包首次虽退出 0，但没有登录且存档不变。[复测日志](../build/forge-121-late-qa/1.21.11/baseline-screen/console.log)确认实际界面为 `BackupConfirmScreen`。探针只对 QA 根内的测试副本、匹配实验性世界标题及 `Create Backup and Load` 按钮操作。原包通过不能抵消候选的版本拒载。

## 证据边界

- [汇总校验清单](../build/forge-121-late-qa/verified.json)包含六个发布、安装器 SHA-1、全部原包顶层模组 SHA-512、候选 SHA-256 和各轮结果。每版目录另有 `releases.json`、包内索引、`installer-pin.json`、`install.log`、`assets-proof.json` 和实际类路径。安装器来自 Forge Maven；使用的 Mojang 输入和 Forge 处理器产物按清单校验。1.21.6 曾因安装超时、内存不足重试，以最终成功日志和产物为准。
- 用两个现有 Fabric runtime 作**原版文件缓存**；类路径只取 Mojang Windows 清单及固定 Forge profile，入口 `net.minecraftforge.bootstrap.ForgeBootstrap`、目标 `forge_client`。未使用 Fabric 启动配置或 Fabric Loader。未移除整合包自带的跨加载器 JAR，也未把包名当作实装证据。
- 各版本候选由 SHA-256 与发布资产匹配的 Forge v1.5.2 JAR 制作，`@Mod` 入口字节不变；只删除 `ForceOpHack`、`ForceOpDialog`、注册字段和构造调用，并在普通 `HackList` 加离线测试目录检查。归档核对见各版 `candidate-repeat/archive.json`。候选制作首轮在启动前因 QA ZIP 条目复用校验失败，不计产品失败；实际结果采用修正后的轮次。没有交付候选 JAR，也没有改生产源码。
- 游戏使用独立的测试目录、离线身份、禁用多人及聊天、回环代理和本地 hosts；只用 `--quickPlaySingleplayer OfflineQA`。来源都是已有 QA 存档，1.21.7/.8/.10 使用 1.21.6 测试档的独立副本。登录以集成服玩家记录为准，保存需停服后的 `All dimensions are saved`、`LastPlayed` 前进、无崩溃/强杀且退出 0。读回在新 JVM 运行，启动前整棵存档树（排除 `session.lock`）须与首次退出后相同。源档和整合包原模组未变。
- **覆盖的是离线单人存档流程。** 保留普通功能不等于逐项测过功能；未测多人、远程服务器、长时运行或其他整合包。对未入服的版本没有候选保存或读回结论。
