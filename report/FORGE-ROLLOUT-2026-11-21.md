# Forge 1.5.2 整合包验收

范围只含 Minecraft Forge。该线共 21 个正式发布组合，1.20.5 和 1.21.2 没有 Forge 发布物。[发布文件归档检查](MODPACK-COMPAT-v1.5.2-2026-11-21.md)不等于进世界验收。以下均为离线单人测试，未连接游戏服务器，修复候选不是新的正式版本。

**覆盖：21/21 个发布版本均实际启动过至少一个第三方整合包。10 个版本有加装后的保存和读回证据，11 个版本未取得加装通过。** 10 个中包括仅 GUI 探针的 1.20.1、限定普通功能的白名单以及双重修订实例 1.21.3；不能解读为 10 个正式包全部兼容。大型包 Create Live 5、Better MC 与 Pixelmon 的结果也不相同。

## 2026-11-22 补验记录

上面的 10/21 是 11 月 21 日首次汇总的历史统计，下表保留当时结果。后续核对到 **7 个此前未计入的版本**有修订副本的候选保存、不同 JVM 同档读回记录；不能与原包普通成品通过数混用，也不是本轮源码提交的重新实测。

- [1.21.6、1.21.7、1.21.8](FORGE-121678-FOLLOWUP.md)：三组修订副本读回；1.21.6 的最终模组列表与无候选对照相差一件。
- [1.21.11](FORGE-12111-FOLLOWUP.md)：仅升级 Forge 至 61.2.0，原锁定 61.0.2 的拒载结论不变。
- [26.1、26.1.1、26.1.2](FORGE-261-FOLLOWUP.md)：修订配置下各有独立对照、候选和同档读回。
- [1.20.2、1.20.3、1.20.6](FORGE-120-FOLLOWUP.md)：11 月 22 日补跑三个无候选基线，均从首次输出的副本重新登录并保存；Pixelmon 通过关窗保存，另两组是修订副本从菜单退出。不新增候选通过数。

## 版本矩阵（初次汇总）

“读回”指新 JVM 登录首次退出时保存的世界、再次保存并正常退出；“原包”指不加本项目。GUI 探针只证明指定组件；白名单客户端只覆盖保留的初始化与界面代码。修订实例明确列出更换的加载器或模组。每行只覆盖列出的组合，不证明该 Minecraft 版本下的其他整合包兼容。

| Minecraft | 实际整合包 / 锁定 Forge | 验收结果 |
|---|---|---|
| 1.20.1 | Create Live 5 / 47.2.17 | 原包与 GUI 探针均保存、读回；大型包 Better MC BMC4 v59 在 Intel OpenGL 着色器注册时崩溃，The Horrors in the Fog 的 Essential 写入失败。见 [1.20 系列](FORGE-120-QA.md)。 |
| 1.20.2 | Pixelmon 9.2.10、TBO Forge / 48.1.0 | Pixelmon 原包首次保存正常，重启读出标记后在再次登录前触发 `LootModifierManager` 崩溃；TBO 的 ServerCore 在服务器 tick 时崩溃。 |
| 1.20.3 | Essentials、SuperSMP4 / 49.0.2 | 两个实际 Forge 原包都拒载：前者的 JEI 要求 MC 1.20.4，后者缺少 Curios 和 Corpse。另测的纯 Forge 不计作整合包。 |
| 1.20.4 | Medusa 3.0.5 / 49.1.29 | 原包拒载；缺少 MCPitanLib，并含无效的 Fabric 文件。 |
| 1.20.6 | Fastly Optimised / 50.1.19、Mamuts Land / 50.2.0、Helios / 50.2.1 | 三个原包分别遇到 Packet Fixer 的 Mixin 兼容级别错误、本地登录配置为空、世界掉落管理器未初始化。均未通过。见 [1.20 系列实测](FORGE-120-QA.md)。 |
| 1.21 | All the MrCrayfish’s Mods 1.0.0 / 51.0.33 | **当前源码构建通过**：修复重复依赖后，在锁定原包上进平坦档、保存、读回。详见下文。 |
| 1.21.1 | All the MrCrayfish’s Mods 1.0.1 / 52.1.8 | **当前源码构建通过**：去重后的成品在原包的九个模组外单独加装，[首次建档保存](../build/forge-121-qa/1.21.1/fresh-flat/product/first/result.json)、[新 JVM 备份并读回](../build/forge-121-qa/1.21.1/fresh-flat/product/backup-readback/result.json)均正常退出。原包和普通功能白名单也独立通过，见 [1.21.1 详情](FORGE-121-QA.md)。 |
| 1.21.3 | Fastly Optimised / 53.0.25 | 原包失败；Dynamic FPS 3.11.8 与 Forge 53.1.0 的独立修订实例通过候选保存、读回。 |
| 1.21.4 | Potato Reforged / 54.1.12 | 白名单客户端在平坦档完成保存、读回；原有世界在实体掉落时崩溃，原包也复现。 |
| 1.21.5 | Marvelously Optimized / 55.0.12 | 白名单客户端在平坦档完成保存、读回；原有世界在方块更新时崩溃，原包也复现。 |
| 1.21.6 | Fastly Optimised / 56.0.9 | 原包和候选均被 FPSBooster 注入签名错误阻断。 |
| 1.21.7 | Fastly Optimised / 57.0.3 | 原包和候选均在 Exordium 渲染路径出现相同空指针。 |
| 1.21.8 | Fastly Optimised / 58.1.0 | 候选运行到本项目功能注册，未取得登录、保存证据。 |
| 1.21.9 | Fastly Optimised / 59.0.5 | 旧候选缺 MixinExtras；修复仅该版本的构建，让 0.5.4 随包加载，[首次](../build/forge-121-late-qa/1.21.9/embedded-first/result.json)和[读回](../build/forge-121-late-qa/1.21.9/embedded-readback/result.json)均通过，无外置补件。见 [1.21 后期版本](FORGE-121-LATE-QA.md)。 |
| 1.21.10 | Fastly Optimised / 60.1.0 | 普通入口离线候选无需补依赖，已保存、读回。 |
| 1.21.11 | Fastly Optimised / 61.0.2 | 原包能保存；候选要求 Forge ≥61.2.0，被锁定加载器拒载。 |
| 26.1 | Fastly Optimised / 62.0.9 | 原包的 SmartOptimizer 清单与实际构造的模组数不符，未进世界。 |
| 26.1.1 | Fastly Optimised / 63.0.2 | 原包的 Chunky Extended 要求 javafml ≥64，未进世界。 |
| 26.1.2 | Fastly Optimised / 64.0.8 | 原包保存、读回；候选要求 Forge ≥64.1.0，原包加载器拒载。 |
| 26.2 | Fastly Optimised OpenGL / 65.1.5 | 当前源码修复退出看门狗问题；候选保存、读回，两次正常退出。 |
| 26.3 | Fastly Optimised / 66.0.4 | 原包、修复前后候选均保存、读回；修复后停止缓存线程不再输出中断栈。 |

## 1.21 与 1.21.1：重复打包

逐一按发布 SHA-256 核验并[扫描 21 个原 Forge 归档](../build/forge-early-qa/forge-release-split-packages.json)，只有 1.21 和 1.21.1 检出这种外层模块与 JarJar 内嵌模块的 Java 包冲突。[两版重建前后对照](../build/forge-early-qa/jarjar-proof.json)保留三个内嵌依赖的版本、成品摘要和每项冲突。

Forge 1.21 在固定包 `blLcU6Dr` 中加载旧候选时，JVM 拒绝同时由 `wurstpenguin` 和 `io.netty.codec.socks` 导出的包（[原始启动日志](../build/forge-early-qa/1.21/add2/console.log)）。1.21 与 1.21.1 的原发布物均存在三个重复提供者：Java-WebSocket、netty-codec-socks 和 netty-handler-proxy。两个版本均保留原 `implementation`、`jarJar` 和依赖版本，只删除额外平铺到外层 JAR 的声明。未将这个改动套用到仅有一种打包方式的版本。

两个版本分别运行离线 Gradle `jarJar` 成功（[1.21](../build/forge-early-qa/1.21/build-final.log)、[1.21.1](../build/forge-early-qa/build-1.21.1-final.log)）。对原发布物和重建成品执行新的 [Forge 归档检查](../scripts/validate-release-jars.py)，旧包每个命中三个内嵌依赖，新包均为零。检查以 Java 包而非类名判定冲突；[回归用例](../scripts/tests/test_forge_jarjar.py)也覆盖同包不同类。完整 Python 脚本套件 [44 项通过](../build/forge-early-qa/script-tests.log)。

1.21 的[原包](../build/forge-early-qa/1.21/flat-screen-control/result.json)、[新成品首次](../build/forge-early-qa/1.21/flat-screen-first/result.json)和[读回](../build/forge-early-qa/1.21/flat-screen-readback/result.json)均正常退出 `0`。使用新建的同版平坦存档夹具，未复制旧区块或改写旧档数据版本。只在一次性测试目录匹配游戏的实验性世界标题后选择“Create Backup and Load”。新成品不含 ForceOP，SHA-256 为 `ba770f97a2e01cfc833b85f6d7cc3bc93c660a6648e23768248806e538210ef2`，两次相同。读回开始的 `LastPlayed` 与首次结束值 `1795261013614` 一致，随后增至 `1795261083980`。

## 1.21.3：原包和修订实例

1. 固定包 `j0tgNCoQ` 原带 Dynamic FPS 3.11.4，版本范围 `[1.21.2,1,21.4]` 不合法。[原包](../build/forge-early-qa/1.21.3/ctrl2/result.json)和[加装组](../build/forge-early-qa/1.21.3/add2/result.json)均未进入世界。
2. 在副本中换用经过 SHA-512 验证的 Dynamic FPS 3.11.8（`3ndPKP4H`，修正范围 `[1.21.2,1.21.4]`）。Forge 保持 53.0.25 时，[新平坦档原包对照](../build/forge-early-qa/1.21.3/flat-control/result.json)正常保存；[旧档对照](../build/forge-early-qa/1.21.3/verified-control/result.json)则在流体更新时触发 `LootModifierManager` 崩溃。[加装组](../build/forge-early-qa/1.21.3/verified-first/result.json)另被产品最低 Forge 53.1.0 门槛拒载。关闭错误页退出 `0` 不算通过。
3. 使用另一个独立实例，在上项基础上更换为官方 Forge 53.1.0，保留产品依赖门槛。在同版新平坦档，[对照](../build/forge-early-qa/1.21.3/flat-revised-control/result.json)、[候选首次](../build/forge-early-qa/1.21.3/flat-revised-first/result.json)及[读回](../build/forge-early-qa/1.21.3/flat-revised-readback/result.json)均登录、停服保存、正常退出 `0`。读回的 `LastPlayed` 从 `1795261045515` 增至 `1795261129531`；不表示修复了原始包或旧世界的掉落逻辑。

## 26.2：Fastly Optimised OpenGL

固定版本 `3ZTnzDms`，Minecraft 26.2，Forge `65.1.5`，原包 6 个模组。核验 MRPACK 的 SHA-512、Modrinth 客户端文件清单和 Forge 官方客户端补丁的输出摘要。原版来源存档不变；只在[一次性种子副本](../build/forge-262-qa/seed.json)中预先写入实验性设置的已确认标记，各组的首次启动均从该副本复制。启动禁用多人和聊天，QA 制品中的 ForceOP 实现与对话框均已移除。

| 测试 | 结果与证据 |
|---|---|
| [原包](../build/forge-262-qa/clean-base/result.json) | 登录、停服后三维度保存、正常退出 `0`。 |
| [发布逻辑对照](../build/forge-262-qa/clean-added/result.json) | 已登录、保存三个维度，但关窗后触发退出看门狗，退出 `-8`；报告显示 Baritone 缓存任务仍阻塞在队列。不是存档丢失，也不算整体验收通过。 |
| [隔离修复](../build/forge-262-qa/fix-first/result.json)及[读回](../build/forge-262-qa/fix-readback/result.json) | 仅引入两处注册及三个新类；两轮停服保存、退出 `0`，无强杀、崩溃报告或未捕获异常。 |
| [当前源码构建首次](../build/forge-262-qa/project-first/result.json)及[读回](../build/forge-262-qa/project-readback/result.json) | 分别通过同样的完整单人验收。两个进程使用相同 QA SHA-256：`3e87c881da84f2eea294ae9668a2e14677181aae7162f52f837d350b7486ddb2`。读回前 `LastPlayed` 与首次退出值 `1795259488094` 一致，再次退出后增至 `1795259529983`；源存档及模板均未变化。 |

修复在 `Minecraft.close()` 收尾时关闭 SeedCrackerX 执行器并取消 Baritone 的两个常驻任务，而非中断所有存档任务；保留退出看门狗。用内嵌 Forge 版 Baritone `1.19.0` 的字节码核对两个任务提交点，注入对数量有明确约束。原包和加装组均可见 Entity Cleaner 配置解析、素材元数据等日志，测试通过不代表全包无告警。首次 Gradle 测试因临时目录错误失败；修正 QA 临时目录后[全量重跑](../build/forge-262-qa/tests-final.log)，256 项全部通过，无失败、错误或跳过。

## 26.3：Fastly Optimised

固定版本 `YA8iKYaF`，Forge `66.0.4`。独立原包、离线客户端对照、修复候选分别保存并用新进程读回。26.3 在修复前也能正常退出；这里的改动针对 Baritone 正常结束时打印中断异常栈的问题，与 26.2 的退出崩溃不同。详见[对照、修复与证据](FORGE-26-QA.md)。
