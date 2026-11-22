# Forge 1.20.x 固定整合包单人实机 QA（2026-11-21）

本报告测试内置服务器的世界保存，不是独立专用服务器测试。结论只覆盖实际启动的包、锁定加载器和注明的测试件；不代表完整客户端通过。Windows 11 x64，Azul Java 17.0.21，客户端堆上限 5 GiB。使用本地身份 `ForgeQA`、单人 Quick Play、独立游戏目录和世界副本，禁用多人及聊天，没有连接游戏服务器或进行网络功能测试。安装阶段下载与游戏运行分开。

## 实测结论

**Create Live 5：原包和只加 GUI 测试件两组均完成启动、登录、停服保存、重启读回和再次保存。** 四轮均无强杀、无崩溃报告；两组安装后的模组散列与包模板一致，加装组只多出同一份测试 JAR。此次不能外推为 BMC4 通过，亦不能外推为完整客户端兼容。

| 组别 | 首次运行 | 新进程读回 | 退出码（首次 / 读回） |
|---|---|---|---|
| 原包，91 JAR | 登录，写入 `…4EBC944B`，全维度保存 | 输入存档散列逐项一致，游戏内读出 `…4EBC944B`，再次保存 | 0 / 0 |
| GUI 测试件，92 JAR | 登录，写入 `…C5F740FA`，全维度保存 | 输入存档散列逐项一致，游戏内读出 `…C5F740FA`，再次保存 | 0 / 0 |

[四轮核验汇总：完整标记、对应日志行、退出码与读回比对](../build/forge-120-qa/QKVowWlC/evidence.json)。加装读回日志另有 `create_dd:striated_ores_savanna` 区块边界写入警告；本结论只覆盖上述世界往返，不证明所有区块生成或玩法正确。

## 固定输入和通过标准

**Create Live 5 1.3.2.1**，Modrinth 版本 ID `QKVowWlC`，Minecraft 1.20.1 / Forge 47.2.17。原包 91 个顶层模组 JAR，加装组 92 个；包含 Create、多个 Create 扩展和 KubeJS。参见[发布记录](../build/forge-120-qa/QKVowWlC/version.json)、[实际索引](../build/forge-120-qa/QKVowWlC/index.json)及[下载核验](../build/forge-120-qa/QKVowWlC/preparation.json)。没有升级或替换包内模组。

- 原包和加装组分别从同一原版测试世界复制，使用各自的随机标记。存档内小型数据包只在标记不存在时写入 `forge120qa:proof`；再次载入时必须先读到原标记才会输出 `QA120_READ_…`。
- 首次验收要求本地玩家登录、标记写入日志及实际压缩 NBT、内置服务器停止后确认维度保存、`level.dat` 变化、进程自然退出 `0`。读回另要求新游戏目录启动前的存档逐文件 SHA-256 与首次停服后相同（不计 `session.lock`），并且第二个进程在世界内输出原标记。
- 加装 JAR 使用白名单：只提取正式 Forge 1.20.1 归档里的 `AbstractSliderButtonAccessor` 和 `ToastComponentAccessor` 两个 GUI 类及对应映射。没有 ForceOP 类、注册器、占位实现、网络库或嵌套 JAR。两种测试件均未安装完整客户端。本组修复仅为 QA 归档补上 `pack.mcmeta`，没有改 Forge 产品代码。见[最终归档清单和 SHA-256](../build/forge-120-qa/probe-audit.json)。
- 初版探针启动时记录 `Missing metadata in pack mod:forge120qaui`，停在 Forge 加载提示页，未登录；见[初版日志](../build/forge-120-qa/QKVowWlC/ui-first/console.log)。该轮不计作通过。

## 已复现的阻塞

| 固定包 | 实测到达位置及阻塞 |
|---|---|
| Better MC [FORGE] BMC4 v59，`bdRIjRgs`，1.20.1 / 47.4.20 | 原包首次停在 Missing Mods Checker。按包内文件 ID 从 CurseForge 官方 CDN 取得全部 51 个额外文件（35 个 JAR），未屏蔽检查器。补齐后 365 个包内 JAR，加初版探针为 366；日志确认 `All mods found!`、已应用 GUI Mixin。随后在 Twilight Forest 注册着色器的 `glLinkProgram` 调用中发生原生崩溃，故障帧 `igxelpicd64.dll+0xc28374`，退出 `1`，未进世界。没有同配置无探针对照，不能判定由探针引起。建议在独显环境先做补齐后的原包对照。 |
| The Horrors in the Fog 1.0.5，`rINdY3l0`，1.20.1 / 47.2.17 | 初版探针组在 Essential 安装内嵌模组元数据时抛出 `ReadOnlyFileSystemException`，退出 `1`，未进世界。没有去掉 Essential 或放开联网。 |
| Medusa 3.0.5，`x1CtfXR7`，1.20.4 / 49.1.29 | **原包对照**（46 个顶层 JAR）被 Forge 拒载：YamatoGunMod 需要未装的 `mcpitanlib >= 3.0.0`，Forge 同时列出无效 Fabric API 和 FabricSkyBoxes 文件。关闭错误页后进程退出 `0`，不代表通过；未加装、未保存、未读回。 |

BMC4 的 340 个索引项全部获得；部分清单大小不准，逐项核对官方文件内容哈希，而不是接受仅文件名匹配的缓存。Forge 官方 Maven 在本机曾返回 403；安装器改用镜像、比对 SHA-1，依赖优先复用符合清单散列的本机缓存。[BMC4 下载及大小差异](../build/forge-120-qa/bdRIjRgs/preparation.json) · [51 项补件及实得摘要](../build/forge-120-qa/bdRIjRgs/supplements.json) · [原生崩溃栈](../build/forge-120-qa/bdRIjRgs/ui-first/game/hs_err_pid33536.log) · [Essential 阻塞](../build/forge-120-qa/rINdY3l0/ui-first/console.log) · [Medusa 拒载报告](../build/forge-120-qa/x1CtfXR7/baseline/game/crash-reports/crash-2026-11-21_19.14.24-fml.txt)。

## 追加实测：1.20.2、1.20.3、1.20.6

**三个版本均找到了实际锁定 Forge 的发布包。共实测 7 个原包，无一完成本报告要求的两次正常登录、保存和退出。** 以下为原包对照：未加装 GUI 探针、Wurst 或 ForceOP，未测试网络功能。每轮复制包模板，顶层 JAR 文件名与 SHA-256 均逐项核对；没有删模组、补依赖或抬高加载器版本。1.20.2、1.20.3 用 Java 17；1.20.6 用 Java 21。首次世界是同一 **1.20.1 原版测试世界的独立副本**，由目标游戏读取；不是各版本新建世界测试。只在首轮通过后执行第二轮。

| MC / Forge | 原包（发布文件 ID；顶层 JAR 数） | 首次启动和保存 | 读回或阻塞 |
|---|---|---|---|
| 1.20.2 / 48.1.0 | Pixelmon Modpack 9.2.10（`lmwiXuDD`；13） | 本地登录、写入标记、保存全部维度，正常退出 `0` | 新进程输入与首次保存的逐文件散列一致，读出原标记 `…4082F88A`。重新登录前世界 tick 抛出 `LootModifierManager` 未初始化；异常停服有保存日志，关闭崩溃页退出 `0`。**标记读到，读回验收未过。** |
| 1.20.2 / 48.1.0 | TBO Forge（`tFOTKhMU`；16） | 写标记并登录；ServerCore 的 `DynamicManager` 为空，随后服务端 tick 崩溃 | 异常停服虽保存，退出非零，未做第二轮。 |
| 1.20.3 / 49.0.2 | Essentials 1.0.0（`I0nHhTVE`；25） | 加载时即拒载：JEI 要求 Minecraft `[1.20.4,1.20.5)`，实际为 1.20.3 | 未进世界，无保存或读回。 |
| 1.20.3 / 49.0.2 | SuperSMP4 1.7.7（`mFsVWq2u`；18） | `corpsecurioscompat` 需要 `curios` 和 `corpse`，原包均未提供 | 未进世界，无保存或读回。 |
| 1.20.6 / 50.1.19 | Fastly Optimised b-20.6（`dSX4khjJ`；11） | Mixin 初始化不识别 Packet Fixer 配置声明的 `JAVA_21`，进程退出 `1`；本轮确实运行在 Java 21 | 未进世界，无保存或读回。 |
| 1.20.6 / 50.2.0 | Mamuts Land 0.02（`dcmISvsQ`；10） | 内置服务器写标记，本地登录配置阶段报 `The wrapped config must not be null` 并断开；服务器随后保存 | 无本地玩家登录，关闭错误页退出 `0` 不计通过，未做第二轮。 |
| 1.20.6 / 50.2.1 | Helios 0.02（`eARONeXV`；5） | 写标记、本地登录后世界 tick 触发 `LootModifierManager` 未初始化；异常停服保存，退出非零 | 未做第二轮。包内另有数据函数解析错误。 |

**单列纯 Forge 对照，不计入上表 7 个包：** 1.20.3 / Forge 49.0.2（`mods` 为空）首次写标记、登录、保存和退出 `0` 均通过。第二次输入散列一致，重新读出 `…8D09240E` 并本地登录，但世界 tick 同样触发 `LootModifierManager` 未初始化，产生崩溃报告并非零退出。这能证明现象不只出现在加装第三方模组时，不能单凭这几轮区分加载器、Quick Play 路径和旧版测试世界的影响。后续隔离应优先在同版本新建的世界里通过普通单人菜单复测。

**检索与复核：** [1.20.3 的检索请求、11 份实际包索引与 SHA-512](../build/forge-120-qa/1203-search.json)记录了 Modrinth 双筛选的 4 个项目：Essentials 的四份文件分别锁定 Quilt、Forge、Fabric 和 NeoForge；SuperSMP 的五份均锁定 Forge 49.0.2；另外两份实际锁在 1.20.1。上表只列真正跑过的文件。CurseForge 筛选页返回 HTTP 403，不作为没有其他整合包的证据。[三版逐轮证据](../build/forge-120-qa/three-results.json)包含准确发布版本、归档散列、模组清单、日志位置与行号、退出码、是否强杀、崩溃文件及存档比对结果。

1.20.5 不在本次补测范围；仓库无对应 Forge 正式产物。
