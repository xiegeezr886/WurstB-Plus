# Forge 1.21.x 固定整合包离线实机记录（2026-11-21）

只覆盖以下三个 Forge 组合。下表区分组件探针（`FuzzySearch + LocalProbe`）与原入口七类白名单副本。另在 **1.21.1** 上直接运行了完整成品 `1.5.2`：离线启动、全新平坦档保存及新进程读回通过。结果不外推到其他功能、版本或旧档。

| MC / Forge | 固定第三方包（Modrinth 版本 ID） | 原有世界副本：原包 / 加组件 | 独立平坦存档：原包 + 组件 | 独立平坦存档：原包 + 客户端白名单 |
|---|---|---|---|---|
| 1.21.1 / 52.1.8 | All the MrCrayfish's Mods [FORGE] 1.0.1 · `Lef9QZ23` | 两组都未见加入；`level.dat` 未变 | 未验收 | 全新档：原包、白名单均通过首次保存及新进程备份、读回、二次保存 |
| 1.21.4 / 54.1.12 | Potato Reforged 1.1.0 · `PyxEzflJ` | 两组都加入后崩溃：实体掉落 | 两个 JVM 均正常保存退出 | 原入口、偏好、翻译、游戏内显示、保存及重启读回 |
| 1.21.5 / 55.0.12 | Marvelously Optimized (Forge) 1.1 · `73aIvcBm` | 两组都加入后崩溃：方块更新 | 两个 JVM 均正常保存退出 | 原入口、偏好、翻译、游戏内显示、保存及重启读回 |

## 1.21.1 完整成品 1.5.2：离线保存及读回通过

另起独立实例，保留 `Lef9QZ23` 原包九个模组及 Forge `52.1.8`，仅加指定的完整 JAR，未叠加白名单、改动 Gradle 或重打包。两轮加载的成品 SHA-256 均为 `ed466edd4716eaa20d09d66bc7ac359d1ea371edd85d61bff9cdaa0fcb32a574`。[启动前递归检查主包及嵌套 JAR](../build/forge-121-qa/1.21.1/fresh-flat/product/preflight.json)：Forge 门槛为 `[52,53)`；原 `HackList.class` 存在，检查类名、字节码及注册资源均未发现 ForceOP 实现、对话框或注册引用。

| 实测 | 观察结果 | 证据 |
|---|---|---|
| 空目录建档，PID `33340` | 出现 `Starting WurstB+ Plus...`；游戏新建 `PackFresh`，NBT 确认 `1.21.1` / `minecraft:flat`。玩家加入，三个维度保存，退出码 `0`，未强杀。 | [首轮结果](../build/forge-121-qa/1.21.1/fresh-flat/product/first/result.json) · [首轮日志](../build/forge-121-qa/1.21.1/fresh-flat/product/first/console.log) |
| 新进程读回，PID `8896` | 完整入口再次启动；测试 agent 仅在指定备份确认页按一次 `Create Backup and Load`。玩家重新加入，三个维度再次保存，退出码 `0`，未强杀。 | [读回结果](../build/forge-121-qa/1.21.1/fresh-flat/product/backup-readback/result.json) · [读回日志](../build/forge-121-qa/1.21.1/fresh-flat/product/backup-readback/console.log) · [保留的游戏备份](../build/forge-121-qa/1.21.1/fresh-flat/product/game/backups/2026-11-21_19-45-43_PackFresh.zip) |

首轮退出、读回前与备份内的 `level.dat` 三者 SHA-256 相同（`31775fe93927…`）；备份 ZIP 的 CRC 全部通过且含区块。再次退出后摘要变为 `a78a5d820dcd…`，`LastPlayed` 从 `1795261526446` 增至 `1795261557413`。与白名单组不同，本轮只验证完整成品的真实入口和普通存档流程，没有主动启用其他功能，也没有测试 HUD、偏好或网络功能。

**新增但未阻塞本流程的错误：** 成品两轮均报告 `mixins.baritone.json` 未声明 `minVersion` 或 `requiredFeatures`；Forge `EventSubclassTransformer` 在 QA agent 线程查找 MixinExtras 的 `InjectorWrapperImpl`、`HandlerTransformer` 失败，并报事件处理器构建错误。两轮另有 `[nether-pathfinder] Failed to delete temp file`。同一原包的首次和最终读回日志未见这些签名；未确定脱离 agent 后是否复现。离线环境中的服务和更新检查失败不计入此差异。

## 1.21.1 原包与白名单：由实际整合包新建平坦档

使用原包指定的 Forge `52.1.8`，未修订 Loader，未修改 Gradle。两组分别在空 `saves` 目录中开始，不复制旧世界，不编辑 NBT。原包实例保留相同的九个模组；客户端实例仅增加此前已审计的七类普通功能白名单 JAR（SHA-256 前缀 `0b5f37c88664`），不含攻击模块。采用同一离线身份及固定种子 `202611211`。

| 组别 | 首轮：从空目录建档 | 第二轮：无驱动 Quick Play 读回 | `level.dat` 交接摘要前缀 |
|---|---|---|---|
| 原包 | PID `31196`：加入、三维度保存、正常退出 | PID `49352`：未加入，未再保存，退出码 `0` | `62ed85db6f84` |
| 原包 + 白名单客户端 | PID `3880`：加入，执行本地 FPS 显示，三维度保存、正常退出 | PID `47108`：读到 GUI 偏好，未加入，未再保存，退出码 `0` | `6d11b591ede2` |

首轮启动有一个[建档驱动](../build/forge-121-qa/1.21.1/FlatDriver.java)：在真实游戏主线程调用公开的 `WorldOpenFlows.createFreshLevel` 及 `WorldPresets.FLAT`，不注册字节码转换器，也不向 `mods` 添加测试模组。游戏生成 `PackFresh` 的区块和 `level.dat`，退出后解析 NBT 确认 MC `1.21.1`、生成器 `minecraft:flat`。因此这是实际客户端建档证据，但不计作鼠标完成创建菜单的证据。

首次读回尝试不带 Java agent。两组启动前的 `level.dat` 摘要和 `LastPlayed` 均与各自首轮退出值一致。进程在配方加载后均观测到一个存活的 `BackupConfirmScreen`（[原包](../build/forge-121-qa/1.21.1/fresh-flat/baseline/readback/screen-classes.txt)、[白名单](../build/forge-121-qa/1.21.1/fresh-flat/client/readback/screen-classes.txt)）。这一轮未确认或修改存档标记，正常关闭后两个存档均未变；不计作成功读回。

**最终读回：两组通过。** 新 JVM 启动同一存档，仅附加[测试限定确认 agent](../build/forge-121-qa/1.21.1/WorldScreen.java)。它只允许两个指定的 QA 实例，核对精确页面类、标题 `Worlds using Experimental Settings are not supported` 和可见且可用的 `Create Backup and Load` 按钮后，在游戏线程调用一次 `onPress()`。两组均由游戏生成并保留一个备份 ZIP；校验全部条目 CRC、确认含区块文件，并核对包内 `PackFresh/level.dat` 的 SHA-256 等于首轮退出值。随后玩家实际加入，三个维度完成二次保存；进程正常退出（`0`），无强杀或崩溃报告。

| 组别 | 新 JVM PID | `LastPlayed` 首次退出 → 再次退出 | 证据与保留的备份 |
|---|---:|---|---|
| 原包 | `10860` | `1795260689189 → 1795261162441` | [结果](../build/forge-121-qa/1.21.1/fresh-flat/baseline/backup-readback/result.json) · [日志](../build/forge-121-qa/1.21.1/fresh-flat/baseline/backup-readback/console.log) · [备份 ZIP](../build/forge-121-qa/1.21.1/fresh-flat/baseline/game/backups/2026-11-21_19-39-07_PackFresh.zip) |
| 原包 + 白名单客户端 | `6420` | `1795260710371 → 1795261157464` | [结果](../build/forge-121-qa/1.21.1/fresh-flat/client/backup-readback/result.json) · [日志](../build/forge-121-qa/1.21.1/fresh-flat/client/backup-readback/console.log) · [备份 ZIP](../build/forge-121-qa/1.21.1/fresh-flat/client/game/backups/2026-11-21_19-39-02_PackFresh.zip) |

白名单组另外核对了原 GUI 偏好文件摘要，并在世界内再次执行本地 FPS 显示。此次结论仅针对上述固定组合及两份新建的平坦档，不改变旧档和无驱动尝试的记录。

运行证据：[原包首次](../build/forge-121-qa/1.21.1/fresh-flat/baseline/first/result.json) · [原包读回](../build/forge-121-qa/1.21.1/fresh-flat/baseline/readback/result.json) · [白名单首次](../build/forge-121-qa/1.21.1/fresh-flat/client/first/result.json) · [白名单读回](../build/forge-121-qa/1.21.1/fresh-flat/client/readback/result.json) · [启动与判定脚本](../build/forge-121-qa/1.21.1/fresh_run.py)。

## 可复核的通过条件

- 基线与加装组使用同一整合包和独立目录；旧档试验使用起始存档副本，1.21.1 全新档试验则分别从空目录建档；读回轮不重新复制存档。实际图形客户端使用 Java 21、离线身份、单人 Quick Play，禁用多人及聊天，JVM 使用仅含回环地址的 hosts 文件和不可用的回环代理。依赖下载仅属于准备阶段；日志中第三方更新检查和 Mojang 服务解析失败不属于联网功能实测。
- `quiet` 和 `quiet-client-pinned` 为**专用持久化存档**：以同 MC 版本的元数据建立新的平坦和平世界，不复制旧区块和玩家，关闭刷怪、结构及随机刻。整合包模组保持原样（包括作者提供的禁用文件）；仅在实例中预置 `onboardAccessibility:false` 通过首次启动引导。原有世界结果不能被平坦档的成功覆盖。
- 每轮要求玩家实际加入，停服后各维度保存，进程自行返回 `0`，无强杀及崩溃报告。第二轮 PID 必须不同；启动前 `level.dat` SHA-256 和 NBT `LastPlayed` 必须与第一轮结束值一致，且第二轮结束后 `LastPlayed` 再次增加。仅白名单组另要求首轮新建、次轮真实载入 `fontEnabled:false` 的偏好，两轮文件摘要一致，且两轮都执行集成世界中的 HUD 显示调用。未以主菜单、编译成功或崩溃收尾保存充当通过。

## 原入口白名单副本

独立复核通过的 JVM：1.21.4 `2676 → 20280`，交接的 `level.dat` 摘要前缀为 `8be55687ada4`；1.21.5 `52320 → 20096`，交接摘要前缀为 `f129e1a32774`。四个进程均正常退出。原包分别保留 9 和 2 个顶层启用的 JAR，客户端实例比各自原包仅多一个白名单 JAR。

以各版本自己的源文件为输入。保留 `@Mod(wurstpenguin)`、原构造器的客户端限制及 `FMLClientSetupEvent.enqueueWork`、一次性初始化标志、`WurstClient.INSTANCE.initialize()`。保留原 `createWurstFolder` 和 `GuiPreferences`，以及本地 JSON 读取/原子写入；`WurstTranslator` 使用游戏的真实资源管理器，固定不强制英语，不创建 OTF 注册表。QA 加入集成世界后用译文、GUI 文本样式与实际帧率调用 `setOverlayMessage`。这是诊断用显示，不声称载入了完整 HUD、ClickGUI 或生产 Mixin。

JAR 只有七个白名单类（含一个内部枚举），无嵌套 JAR、HackList、命令、种子分析、网络钩子、代理、更新器、账号管理或 ForceOP。组件探针不在客户端组的 `mods` 中。留存[生成器](../build/forge-121-qa/client.py)、[运行器](../build/forge-121-qa/run.py)、[独立复核](../build/forge-121-qa/audit.py)和[客户端读回摘要](../build/forge-121-qa/client-readback-audit.json)。生成源码、实际 JAR 清单、每次 JVM 的参数、控制台日志和结果 JSON 均在对应实例目录。

**元数据阻塞与修正范围：** 保留仓库原 Forge 范围时，[1.21.4](../build/forge-121-qa/1.21.4/quiet-client/first/console.log) 要求至少 `54.1.14`，[1.21.5](../build/forge-121-qa/1.21.5/quiet-client/first/console.log) 要求至少 `55.1.0`。这两轮在 FML setup 前被拒载。只在白名单 JAR 中将 Forge 依赖设为对应快照的精确版本，没有更换加载器，也没有据此降低正式客户端的最低版本。

| 证据 | 1.21.4 | 1.21.5 |
|---|---|---|
| 原包、组件首轮与读回 | [基线](../build/forge-121-qa/1.21.4/quiet/control/result.json) · [首次](../build/forge-121-qa/1.21.4/quiet/first/result.json) · [重启](../build/forge-121-qa/1.21.4/quiet/readback/result.json) | [基线](../build/forge-121-qa/1.21.5/quiet/control/result.json) · [首次](../build/forge-121-qa/1.21.5/quiet/first/result.json) · [重启](../build/forge-121-qa/1.21.5/quiet/readback/result.json) |
| 白名单客户端独立三轮 | [基线](../build/forge-121-qa/1.21.4/quiet-client-pinned/control/result.json) · [首次](../build/forge-121-qa/1.21.4/quiet-client-pinned/first/result.json) · [重启](../build/forge-121-qa/1.21.4/quiet-client-pinned/readback/result.json) | [基线](../build/forge-121-qa/1.21.5/quiet-client-pinned/control/result.json) · [首次](../build/forge-121-qa/1.21.5/quiet-client-pinned/first/result.json) · [重启](../build/forge-121-qa/1.21.5/quiet-client-pinned/readback/result.json) |

## 失败与边界

1.21.1 的[原包](../build/forge-121-qa/1.21.1/accepted/control/result.json)和[组件组](../build/forge-121-qa/1.21.1/accepted/first/result.json)加载到资源与配方阶段；未观察到玩家加入，输入存档未变。没有证据确定在哪个确认界面停止。

1.21.1 的平坦档[原包基线](../build/forge-121-qa/1.21.1/quiet-client-pinned/control/result.json)及[白名单客户端](../build/forge-121-qa/1.21.1/quiet-client-pinned/first/result.json)也未加入世界。后者完成 FML setup 并创建 GUI 偏好，但未执行世界内 HUD 回调；等待窗口到期后正常关闭，未启动无意义的读回轮。平坦档另记有 `DragonFight` 元数据缺项；未认定它是阻塞根因。

1.21.4 的[原包崩溃](../build/forge-121-qa/1.21.4/accepted/baseline/crash-reports/crash-2026-11-21_19.09.39-server.txt)与 1.21.5 的[原包崩溃](../build/forge-121-qa/1.21.5/accepted/baseline/crash-reports/crash-2026-11-21_19.09.32-server.txt)均报告 `Can not retrieve LootModifierManager until resources have loaded once`。前者发生于实体掉落，后者发生于方块更新。加组件也复现；没有证据将它们归因于客户端、某个第三方模组或加载顺序，未修改掉落逻辑。没有把失败世界当作成功世界的起点。

输入和校验：各包的[1.21.1 清单](../build/forge-121-qa/packs/Lef9QZ23/template/inventory.json)、[1.21.4 清单](../build/forge-121-qa/packs/PyxEzflJ/template/inventory.json)、[1.21.5 清单](../build/forge-121-qa/packs/73aIvcBm/template/inventory.json)记录归档 SHA-512；相邻 `version.json` 为发布元数据，`modrinth.index.json` 为原始文件摘要。各轮 `mods.json` 另含随包覆盖文件。1.21.4 个别文件名标为 Fabric，未替换或删除；这里证明包按原样启动，不表示 Forge 加载了每个文件。

范围交接：收窄实机范围以前，11 个现有 `versions/1.21*` 源集已经物理删除 `ForceOpHack.java`、`ForceOpDialog.java`、注册及翻译，并清理了范围内含旧实现的构建输出（[清单](../build/forge-121-qa/stale-generated-removed.json)）。另八个版本仅有上述清理，不计入固定组合实机覆盖。未修改范围外发布包，仅 1.21.1 实际运行了完整成品的普通离线流程，未交付客户端。
