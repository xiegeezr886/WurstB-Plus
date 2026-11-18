# v1.5.2 全版本同步开发计划

更新日期：2026-11-18。开发分支：`codex/v1.5.2-all-versions`。
起点：`main` 的 `175e95163af0c7e7371803f9c9753710f6971ce8`。

## 范围与当前状态

以 **Fabric 1.20.1** 的 v1.5.2 为功能基线，向另外 **66 个工程**同步，
最终覆盖 23 个 MC 版本、67 个工程：Fabric 23、Forge 21、NeoForge 23。
根目录 Forge 1.20.1 也在目标内；`dist/` 只保留原始快照。
Forge 无官方 1.20.5 / 1.21.2，不创建占位工程。

**66 个移植目标**均已完成源码接入，另有 Fabric 1.20.1 功能基线；67 个工程均使用 `1.5.2` 正式编号。
正式编号下的 67 个生产包全部通过构建、单元测试和递归打包审计。安装与功能变更见 [v1.5.2 发布说明](RELEASE-v1.5.2.md)。

| 加载器 | 正式构建通过 | 单元测试通过 |
| --- | ---: | ---: |
| Fabric | 23 | 4,994 |
| Forge | 21 | 4,836 |
| NeoForge | 23 | 5,244 |
| 合计 | 67 | 15,074 |

正式构建汇总 `.dsh-tmp/v152-formal-final-audit.json` 逐项校验当前源码指纹和生产 JAR SHA-256，记录单元测试、包结构与原始日志。统计采用各目标最终通过的结果，无失败、错误或跳过。

2026-11-18 开发编号构建汇总：66 个目标合计 14,928 项测试，零失败、错误、跳过。
旧 JAR 的 SHA-256 均已复核，但 HUD 和实体轮廓的后续同步改变了源码指纹，旧记录仅作历史证据：
`.dsh-tmp/v152-all-targets-final-audit.json`，各行保留对应构建/收集记录的来源。
44 个原生 SeedCrackerX 后端的汇总为 `.dsh-tmp/v152-native-seed-all-final-audit.json`；
44 个现代工程的 LSD 加载生命周期修复以 `.dsh-tmp/v152-lsd-loading-final-audit.json` 为准。
这些汇总逐项核对实际源码和 JAR，不能用较早批次的成功记录覆盖后续修改。

| 工程 | 单元测试 | 生产包结构检查 | 开发客户端实机 |
| --- | ---: | --- | --- |
| 根 Forge 1.20.1、NeoForge 目录 1.20.1 | 各 148 | 通过 | 原生结构采集、已知测试种子联动、保存退出通过 |
| Fabric 1.20.4 | 146 | 通过 | 菜单、SeedCracker 开关、挖掘拾取/断线通过 |
| Fabric 1.20.2、1.20.3 | 各 146 | 通过 | 待验证 |
| Forge 1.20.2、1.20.3、1.20.4 | 各 148 | 通过 | 1.20.4 三个列表进退、原生采集、测试种子预览、保存退出通过；其余待验证 |
| NeoForge 1.20.2、1.20.3、1.20.4 | 各 148 | 通过 | 待验证 |
| Fabric 1.20.5、1.20.6、1.21、1.21.2 | 各 150 | 通过 | 待验证 |
| NeoForge 1.20.5 | 152 | 通过 | 待验证 |
| Forge / NeoForge 1.20.6 | 各 152 | 通过 | 待验证 |
| Fabric 1.21.1 | 254 | 通过 | 菜单、进世界、挖掘拾取、保存退出回主菜单及会话清理通过 |
| Fabric 1.21.3、1.21.4 | 各 254 | 通过 | 1.21.4 菜单/进世界/挖矿/断线、TargetShader 与 Moon 实体辉光通过 |
| Forge 1.21、1.21.1 | 各 254 | 通过 | 待验证 |
| NeoForge 1.21、1.21.1 | 各 254 | 通过 | 1.21 三页进退、真实采集、测试种子预览和保存退出通过；1.21.1 待验证 |
| Forge 1.21.3、1.21.4 | 各 256 | 通过 | 待验证 |
| NeoForge 1.21.2、1.21.3、1.21.4 | 各 256 | 通过 | 待验证 |
| Fabric / Forge / NeoForge 1.21.5 | Fabric 254；原生各 256 | 通过 | Fabric 已验证菜单与渲染；Forge 已验证三页进退、真实采集、Cloth 配置、结构线框、测试种子预览、LSD 和退出清理；NeoForge 待验证 |
| Fabric / Forge / NeoForge 1.21.6 | Fabric 254；原生各 256 | 通过 | Fabric 菜单、物品/盔甲图标、TargetShader、Moon 辉光、种子/挖矿及保存退出通过；其余加载器待验证 |
| Fabric / Forge / NeoForge 1.21.7、1.21.8 | Fabric 各 254；原生各 256 | 通过 | 待验证 |
| Fabric / Forge / NeoForge 1.21.9、1.21.10 | Fabric 各 254；原生各 256 | 通过 | Fabric 1.21.9 菜单、图标、TargetShader、Moon 辉光、种子/挖矿、保存退出通过；其余待验证 |
| Fabric / Forge / NeoForge 1.21.11 | Fabric 254；原生各 256 | 通过 | Fabric 菜单/导航器/CGUI/音乐推荐页、图标和 HUD、种子/挖矿及保存退出通过；其余待验证 |
| Forge / NeoForge 26.1 | 各 256 | 通过 | 待验证 |
| Fabric / Forge / NeoForge 26.1.1、26.1.2 | Fabric 各 254；原生各 256 | 通过 | 待验证 |
| Fabric 26.1 | 254 | 通过 | 菜单、导航器、CGUI、音乐推荐页、盔甲图标、TargetShader、Moon 辉光、种子/挖矿及保存退出通过 |
| Fabric / Forge / NeoForge 26.2 | Fabric 254；原生各 256 | 通过 | Fabric 菜单、导航器、CGUI、音乐推荐页、盔甲图标、TargetShader、Moon 辉光、种子/挖矿及保存退出通过；其余加载器待验证 |
| Fabric / Forge / NeoForge 26.3 | Fabric 254；原生各 256 | 通过 | Fabric 菜单/导航器/CGUI/音乐推荐页、盔甲图标、TargetShader、Moon 轮廓、种子/挖矿拾取、保存退出及会话清理通过；Forge 原生采集、测试种子预览、Cloth 配置、结构线框、LSD、菜单实际点击和退出通过；NeoForge 待验证 |

表中测试数量为已执行的最近成功批次，零失败/错误/跳过；后续修改须重跑对应检查。
生产 JAR 独立启动与开发客户端的验证结果分别记录；完整战斗、搭路和音乐播放仍有未覆盖路径。
Forge/NeoForge 1.20.1–26.3 的 44 个原生工程均已接入并注册真实 SeedCracker 后端，
各自通过测试和生产包审计；配置页、结构渲染和自动求解仍需逐代实机验收。
[生成清单](generated/v1.5.2-porting/README.md)
记录各工程版本、JDK、生产任务、文件差异和模块注册差异。
基线最近源码提交为 `69067e9060cf67a0b4e27de1717015586ad055e0`，
功能与既有验证见 [开发文档](DEVELOPMENT-v1.5.2.md)。

## 功能同步顺序

1. 公共设置、RedFix 分类/模块列表、翻译、主题、字体、图标、声音、壁纸和许可。
2. 开始菜单、世界/服务器/账号页面、导航器、CGUI、玻璃按钮、动画与平滑滚动。
3. Soar HUD、Rise TargetHUD、药水/盔甲卡片、Logo、灵动岛、歌词及可选辉光。
4. Epsilon 优先、NeoRaven 完善、LeavesHack 补充的战斗行为，以及 NeoRaven 自动搭路。
5. NeoRaven 音乐播放器、本地/在线播放、Opai 歌词、加载进度与中文提示。
6. SeedCracker 桥接、会话隔离、矿脉预测、预览及 Baritone 寻路/挖掘互斥。

每批同步设置、事件、资源、Mixin、访问声明、依赖与适用测试，
不以仅添加入口或同名类视作完成。保留用户配置标识和目标独有功能。

## 按 API 代际推进

| 批次 | 版本 | 目标数 | 主要适配点 |
| --- | --- | ---: | --- |
| B1 | 1.20.1–1.20.4 | 11 | Java 17、加载器事件、会话、旧输入与直接 GUI 绘制 |
| B2 | 1.20.5–1.21.4 | 19 | Java 21、物品组件、附魔/网络变化、各版实际渲染 API |
| B3 | 1.21.5 | 3 | 组件与渲染重构、Screen/RenderType 差异 |
| B4 | 1.21.6–1.21.8 | 9 | GUI 状态提交、NanoVgBlitter、旧输入签名 |
| B5 | 1.21.9–1.21.10 | 6 | 事件对象输入、提交节点、GUI 状态存取 |
| B6 | 1.21.11 | 3 | RenderSetup / Identifier / 实体包名与 shader |
| B7 | 26.1–26.3 | 15 | Java 25、原生名称与渲染状态、NanoVG / Baritone |

先做根 Forge 1.20.1 和 NeoForge 1.20.1，建立同版本跨加载器适配。
其后选 Fabric 1.20.4、1.21.4、1.21.5、1.21.6、1.21.10、1.21.11、26.3 为代表，
每段通过后，使用**同 MC 版本已验证的兄弟加载器**作为优先供体，扩展到对应补丁版本。
同一批次不代表各版本 API 相同，仍需逐目标核对。
生产任务见生成矩阵，特别是 `neoforge/` 1.20.1 仍使用 ForgeGradle / `jarJar`。

## 适配边界

- 加载器入口分别对接 Fabric API 与 Forge/NeoForge 事件总线，保留目标元数据和访问声明。
- 使用目标渲染适配层，核对坐标/剪裁、延迟提交纹理生命周期、GL 状态和输入签名。
- LWJGL / Netty 匹配游戏，Baritone 使用各版依赖；音乐、二维码、JNA、字体和 shader 核对实际打包。
- Mixin 逐个核对目标类、描述符、accessor 注册和可选模组；不通过 `require=0` 隐藏必要注入失败。
- 目标独有文件须逐项保留或合并，尤其重锤、属性交换等基线版本没有的能力；语言词典按键合并。
- SeedCrackerX 2.14.4 二进制仅适用于 Fabric 1.20/1.20.1，不直接嵌入其他版本/加载器。
  需选择兼容上游或适配源码/API；矿脉生成顺序、随机数消费和宿主方块规则按目标核对。
  完成适配和实测前，能力保持待完成，不能宣称已支持。

## 前置问题

以下问题在准备前已经存在，后续移植需建立可信基线：

- Fabric 1.20.1 历史渲染闸门的 6 项失败已确认为检查器误报：只检查无参包装方法，
  漏掉 `beginFrame(float)` 的状态处理。已修正并添加负例，同时纳入根 Forge 和初始化回调；
  当前 67 工程静态检查与 17 项检查器测试通过。这不代表游戏视觉验证通过。
- Forge 26.3 缺少 `META-INF/licenses/mixinextras-forge/`，许可闸门失败。
- parity 闸门按 Fabric 1.20.1 对比，报 195 项差异，且遗漏根 Forge；需随移植纳入并验证行为。
- 历史 `scripts/port/PortConfig.ps1` 只覆盖 65 个目标、固定旧机器 JDK 路径，旧批次绑定旧基线。
  不直接运行旧覆盖流程，本轮先用新清单和明确选择的单工程构建。

## 逐工程验收

1. 复核源码、资源、依赖和注册差异，保留目标专有能力。
2. `compileJava`、适用的 `compileTestJava`、实际单元测试和生产打包通过。
3. 检查 ZIP/CRC、版本、Mixin/refmap、入口、嵌套依赖、字体/shader 和许可。
4. 启动生产 JAR，验证菜单进退、进出世界、GUI/HUD、配置保存、音乐、战斗、搭路，
   以及对应版本已适配的种子/Baritone 功能。
5. 分别记录编译、打包、启动和交互结果；`SKIPPED` 不算测试通过。
   完成后才改为对应 `1.5.2-<Loader>-<MC>` 版本。

## 第一批：同版本跨加载器

适配器：`scripts/port/sync_v152_1201.py`，仅接受根目录和 `neoforge/`。
保留 Forge 入口、模组检测、元数据和 AT；断线时清理种子会话并停止挖矿。
盔甲模型染色使用游戏的 ItemColors。NanoVG 开发启动使用依赖中的类，生产包内嵌 API 与原生库。
音乐/二维码与网络运行库通过 Jar-in-Jar 分发，保留来源和许可证。

首轮 SeedMine 接入已知种子的哈希校验、矿脉预测与 Baritone；
2026-11-18 已补接原生 SeedCrackerX 源码后端，见本文末尾最新复验记录。
Baritone 预热改为后台调用公开的 `BlockOptionalMeta(Block)` 构造器，
避免反射 Fabric 私有实现；初始化失败保持不可用状态，不误报就绪。

2026-11-17 开发客户端验证（Windows / Java 17 / 隔离游戏目录）：

| 检查 | Forge 47.4.10 | NeoForge 目录（Forge 47.1.3） |
| --- | --- | --- |
| Mixin 启动、NanoVG GL3 初始化 | 通过 | 通过 |
| 主菜单、世界/服务器/账户页、音乐、Navigator、CGUI 打开与截图 | 通过 | 通过 |
| 错误种子拒绝、正确种子接受 | 通过 | 通过 |
| 种子 8675309，半径 1 预览快照 | 289 个候选，284 个方块匹配 | 307 个候选，299 个方块匹配 |
| BaritoneMine 启用时停止 SeedMine | 通过 | 通过 |
| 生存模式自动挖掉放置的钻石矿并拾取钻石，关闭后停止寻路 | 通过 | 通过 |
| 保存退出后清空种子会话、停止 SeedMine 与寻路 | 通过 | 通过 |

候选数受玩家位置和已加载区块影响；预测匹配不是百分之百，不能保证每个候选都是矿石。
上述矿石挖掘测试使用人工放置矿石验证 Baritone 链路，不代表自然矿脉完整采集验收。
界面截图已复核，页面关闭操作已调用；动画全过程、配置重启、实际音乐播放、
战斗/搭路与生产 JAR 独立启动仍需继续验收。
本地结果保存在 `.dsh-tmp/v152-<loader>1201-run/` 的 `smoke-results.json`、
`world-verification.json`、`disconnect-verification.json` 和 `screenshots/`，
运行日志为 `.dsh-tmp/v152-<loader>1201-runtime.log`。

```powershell
./gradlew.bat -I scripts/port/verify_v152_1201.gradle compileJava compileTestJava test jarJar --console=plain --max-workers=2
./neoforge/gradlew.bat -p neoforge -I "$PWD/scripts/port/verify_v152_1201.gradle" compileJava compileTestJava test jarJar --console=plain --max-workers=2
```

验证脚本显式启用测试，并将临时文件与构建输出隔离在 `.dsh-tmp/v152-<loader>1201-build/`。
普通 JUnit 没有 ModLauncher 的事件构造器转换，测试辅助类只预建网络启动所需的监听器列表，
用于验证模块/设置构造；不替代真实 Forge 事件与 Mixin 启动验证。
本地日志：`v152-forge1201-tests.log`、`v152-neoforge1201-tests.log` 和对应 `*-validation.log`。
最终生产重建后再次确认两端各 146 项测试、零失败/错误/跳过；JAR 的 CRC、
7 项嵌套依赖及其 CRC、Mixin/refmap、NanoVG API、字体/native/shader 资源检查通过，
生产包崩溃回归检查通过。自动破解后端没有混入 Fabric 二进制。

## 第二批：Fabric 1.20.4

版本号：`1.5.2-dev-Fabric-1.20.4`。使用 v1.5.1 标签中的 Fabric 1.20.1 源码作为
三方合并基点，保留 1.20.4 的皮肤、公共网络监听器和游戏 API 适配。
已接入菜单、导航器/CGUI、HUD、战斗/搭路、音乐和种子联动相关代码与资源。

适配器 `scripts/port/sync_v152_fabric1204.py` 仅用于此目标，
首次运行的原始文件和冲突报告保存在 `.dsh-tmp/v152-fabric1204-port/`。
保留目标专用文件，删除已被新搭路实现替代的旧 OpenOpal 模式；
翻译按键合并。不能直接用于其他版本。

本版重点适配：

- 登录/重生哈希从 `CommonPlayerSpawnInfo` 读取；保留公共网络包发送回调。
- 菜单和音乐滚轮适配水平轴；世界选择使用 `canJoin`，继承的键盘方法通过覆盖接入。
- 世界载入/创建、实体加入、EditBox、加载进度文本描述符按 1.20.4 更新。
- NanoVG API 与所有原生依赖禁止传递引入 LWJGL core，使用游戏的 core/GLFW 配对。
- QA 接口只在明确开启端口时注册客户端启动回调，待 Options 就绪后设置测试选项。
- 自动破解后端使用官方 SeedCrackerX **2.14.6** 原始包，
  固定 SHA-256，保留其 4 项嵌套库和许可证；不复用 1.20.1 的 2.14.4 二进制。

```powershell
./fabric/versions/1.20.4/gradlew.bat -p fabric/versions/1.20.4 -I "$PWD/scripts/port/verify_v152_fabric1204.gradle" compileJava compileTestJava test remapJar --console=plain --max-workers=2
# 隔离客户端，测试接口仅监听本机 18756
./fabric/versions/1.20.4/gradlew.bat -p fabric/versions/1.20.4 -I "$PWD/scripts/port/verify_v152_fabric1204.gradle" -PportQa runClient --console=plain --max-workers=2
```

构建输出为 `.dsh-tmp/v152-fabric1204-build/`，游戏目录为 `.dsh-tmp/v152-fabric1204-run/`。
编译、146 项测试（零失败、错误和跳过）及生产重映射已通过。
生产包的 ZIP/嵌套 JAR CRC、元数据、refmap、NanoVG 原生库以及基线 Logo/背景字节校验通过，
内嵌 SeedCrackerX 原包 SHA-256 与固定值一致。

2026-11-17 开发客户端验证：

| 检查 | 结果 |
| --- | --- |
| Mixin 启动、游戏配套 LWJGL/GLFW、NanoVG GL3 初始化 | 通过 |
| 主菜单、世界/服务器/账户页、音乐、Navigator、CGUI 打开与截图 | 通过 |
| 世界/服务器/账户页和音乐关闭后返回 TitleScreen | 通过 |
| 世界列表刷新/选择后保持中文“进入世界”，通过该按钮重开已有存档 | 通过 |
| 错误种子拒绝、正确种子接受 | 通过 |
| 种子 8675309，半径 1 预览快照 | 369 个候选，327 个方块匹配，预览未启动寻路 |
| BaritoneMine 接管时停止 SeedMine | 通过 |
| 生存模式挖掉人工放置的钻石矿、拾取钻石、关闭后停止寻路 | 通过 |
| SeedCracker 后端加载及启停 | 通过 |
| 保存退出后清空种子、预测和采集状态，并停止寻路 | 通过 |

候选数与匹配率仅代表该次已加载区块快照；矿脉预测仍存在洞穴/覆盖差异。
挖矿验证使用人工放置矿石，不代表自然矿脉完整采集验收。
Baritone 开发环境的临时战利品表注册快照出现过异常日志，但预热及实际挖掘/拾取成功。
自动破解仅验证后端加载与启停，尚未完成结构收集到完整种子破解链路。
上述为开发启动验证，生产 JAR 独立启动、动画全过程、实际音乐播放、配置重启、
战斗与搭路的完整回归仍待完成。

本地结果位于 `.dsh-tmp/v152-fabric1204-run/`：`smoke-results.json`、
`world-verification.json`、`disconnect-verification.json`、`final-existing-world-verification.json` 及 `screenshots/`；
界面汇总图为 `.dsh-tmp/v152-fabric1204-ui-contact.png`，
最终启动日志为 `.dsh-tmp/v152-fabric1204-final-validation-runtime.log`。

## 准备工具

```powershell
python scripts/port/prepare_v152.py
python scripts/port/prepare_v152.py --check
python scripts/port/prepare_v152.py --details .dsh-tmp/v152-porting-differences.json
```

工具仅生成/检查报告，不调用 Gradle 或覆盖源码。比较 `src/main` 和 `src/test` 全部物理文件，
包括 sourceSet 排除的内容，因此文件数不等于编译类数。文本统一 CRLF/LF；
JSON 记录分类计数和 HackList 字段差异，逐文件明细可输出到本地 `.dsh-tmp/`。

本阶段不创建全版本 Release 或上传资产。版本分支同步器只裁剪目录，不执行源码移植。

## 第三批：相邻版本及 Java 21 代表工程

`scripts/port/sync_v152_siblings.py` 保存首次目标快照，以供体 HEAD 作三方合并基点，
拒绝未审核冲突。1.20.2–1.20.4 的 Forge/NeoForge 保留各自加载器入口、访问声明、
目标依赖及注册；音乐/二维码/WebSocket/代理库和许可随生产包检查。
NeoGradle 离线构建先校验缓存下载物，避免其下载任务删除有效缓存。

Fabric 1.21.2–1.21.4 已适配物品组件、附魔注册表、碰撞移动包、加载界面、
实体渲染状态、GUI 渲染注入及 ShaderManager 管理的后处理链。
使用对应上游 SeedCrackerX 2.15.1 / 2.15.2 原始二进制，保留嵌套依赖和许可。
动态音乐封面改为读取 TextureManager 所有的纹理，避免资源流方式无法显示网络封面。
后处理资源改为对应版本 `post_effect` 格式，不能继续使用旧 `shaders/post` 目录。

1.21.4 隔离开发客户端已成功创建种子 8675309 世界：错误种子拒绝、正确种子接受，
首次预览 366 个候选、361 个方块匹配；SeedCracker 采集开关同步，
BaritoneMine 接管时 SeedMine 停止。人工放置钻石矿已挖除并拾取钻石，关闭后停止寻路。
这不代表完整自然矿脉采集或自动破解已验证。结果保存在
`.dsh-tmp/v152-matrix-runtime/fabric-versions-1.21.4/`；运行日志
`.dsh-tmp/v152-fabric1214-runtime.log`。菜单截图已复核，网络音乐封面正常显示。

编译日志 `.dsh-tmp/v152-<loader><MC去点>-build.log`，隔离构建产物
`.dsh-tmp/v152-matrix/<工程路径以连字符连接>/libs/`。
`verify_v152_package.py` 检查递归嵌套 JAR CRC、12 个必要运行类、Mixin 类及选定资源/许可，
结构检查不能替代生产环境启动。

Fabric 1.21/1.21.1 使用 `adapt_v152_fabric1211.py` 从 1.21.3 三方迁移，
保留旧 PostChain、物品 ArmorItem 槽位 API、旧碰撞移动包、像素 ABGR 和 ToastComponent 注册。
最终分别执行 150 / 254 项测试，成品包必要运行类及注册 Mixin 类检查通过。
1.21.1 初次账户保存测试遇到 Windows 临时文件原子替换拒绝访问；重跑通过，仍需实际配置回归。

1.21.4 TargetShader 实机发现默认 RenderType 会重绑主 framebuffer，已改为直接绘制遮罩，
四种效果均截图复核。盔甲图案/耐久、药水卡片显示正常；保存退出后无玩家、种子会话为空、
SeedMine 停止、预测清空且无寻路。Moon 实体辉光的空遮罩已定位为视锥矩阵缺少全局相机视图，
现在组合 RenderSystem 全局视图与事件 PoseStack，再筛选实体；1.21.4 猪模型实测遮罩有
11780 个非零 alpha 像素，浅紫色轮廓与辉光截图已复核。该修复已编码到 1.21.2–1.21.4
适配器，其余版本的同类实体辉光仍需独立核对。临时像素读取、诊断方框与 QA 状态接口均已移除。

NeoForge 1.21.1 构建工具需要 Java 21 的 zipfs 补丁；旧通用脚本固定加载 JDK 17 补丁会导致
`ZipFileSystem.lookupPath` 的 `NoSuchMethodError`。`build-env.ps1` 已按当前 `JAVA_HOME/release`
选择 17/21/25 对应补丁；跨主版本启动工具链时仍须为子进程匹配补丁。

### 最新构建与实机修复

Forge/NeoForge 1.21 和 1.21.1 均已完成 252 项测试及生产打包。普通 JUnit 不运行加载器发现流程，
`native_test_bootstrap.py` 仅在测试源码中提供空 LoadingModList，Forge 另补模块层上下文；
未跳过测试或修改产品初始化。NeoForge 1.21 首轮账户文件原子替换遇到 Windows 拒绝访问，
重跑通过，配置的真实保存/重启仍待验证。结构审计见本地 `v152-native121*-package-audit.json`。

Fabric 1.20.6 从 1.21 供体迁移，适配资源标识构造器、旧附魔对象、CombatRules、
BufferBuilder/VertexConsumer 和渲染事件 tickDelta。自动破解后端使用明确声明支持 1.20.6 的
SeedCrackerX 2.14.7，SHA-256 固定为
`f453bae397cfa66f1667e0ed3ce1983c96006ddde9b90b40ead260fd80a1cded`；
该二进制不能直接用于 1.20.5。150 项测试、重映射与包检查通过，尚未实机启动。

Fabric 1.21.1 的 `minecraft:brand` 配置包曾因 Netty 传递依赖升级而产生 14 字节残留，无法进入世界。
`host_netty.py` 已将六个 Fabric 目标的代理库匹配宿主：1.20.6–1.21.3 为 4.1.97.Final，
1.21.4 为 4.1.115.Final，并禁用代理库传递升级。新增两项测试检查 Netty 模块版本一致，
以及 brand 字符串完整编解码；强制混用版本的负例按预期失败。普通 JUnit 不运行游戏 Mixin，
因此测试验证依赖约束，不宣称复现实机的 14 字节错误。六项目最终测试和包审计均通过，
结果见 `.dsh-tmp/v152-netty-final-package-audit.json`。

1.21.1 已实机复核主菜单、世界/服务器/账户页、音乐、Navigator/CGUI，
错误种子拒绝、正确种子接受，预览快照 509 个候选/466 个匹配且未寻路；
BaritoneMine 接管停止 SeedMine，生存模式挖除人工放置钻石矿并拾取钻石，关闭后停止寻路。
最终构建完成后再次独立启动，进入已有存档并保存退出，主菜单正常显示，种子为空、预测为零、
pathing/collector 为 false。结果位于 `.dsh-tmp/v152-matrix-runtime/fabric-versions-1.21.1/`，
包括 `smoke-results.json`、`world-verification.json`、`disconnect-verification.json` 和
`final-lifecycle-verification.json`；最终启动日志 `v152-fabric1211-final-runtime.log`。
这些结果仍不替代生产包独立启动、完整战斗/搭路、实际音乐播放和完整自动破解验收。

## B3：1.21.5 GPU 接口

Fabric、Forge、NeoForge 最终源码均通过 254 项单测和生产打包，三份生产包 CRC、
12 个运行类、Mixin 注册、资源和许可审计通过，记录为 `.dsh-tmp/v152-b3-final-audit.json`。
Forge 生产任务为 `allJar`，NeoForge 使用 ModDevGradle 的 `jar`（包含 `jarJar`）。

Fabric 开发客户端使用 Java 21；Gradle 9 本身可以运行在 Java 25，游戏启动器按 MC 版本选择 JDK。
实机核对主菜单、世界/服务器/账户页、音乐、Navigator/CGUI；重复截图后四件盔甲、镐与钻石图标仍正常。
修复图标读取继承截图 PACK/PBO 状态造成的 Intel 驱动原生崩溃，以及 NanoVG 上传改变纹理绑定造成的黑图标。
读回校验 direct buffer 容量，保存恢复 PACK/UNPACK 参数和绑定。

TargetShader 四模式与 Moon 实体辉光截图通过；原版 blit fragment 的 `ColorModulate`
必须显式设为白色，否则合成透明。资源测试已加入非零调色断言。Mixin 的热栏选择和飞行姿态
分别对接 `getSelectedSlot()` 与 `getDesiredPose()`，Toast accessor 注册名称与实际类统一。

隔离世界中正确种子 8675309 接受、错误种子拒绝，快照 523 个候选、466 个匹配；
SeedMine 与 BaritoneMine 互斥，人工钻石矿挖掘及拾取通过，保存退出回主菜单通过。
证据位于 `.dsh-tmp/v152-matrix-runtime/fabric-versions-1.21.5/` 的
`clean-render-verification.json`、`world-verification.json` 和截图。
批量 HUD 设置曾遇到 Windows 临时配置文件 AccessDenied，真实配置保存/重启仍需专门回归。
Forge/NeoForge 实机及自动破解后端仍待完成，三工程继续保留开发版本标识。

## B4：1.21.6–1.21.8 延迟 GUI

九工程均已通过各 254 项单测（零失败、错误、跳过）、生产打包和递归包审计。
Fabric 使用 `remapJar`，Forge 使用 `allJar`，NeoForge 使用 `jar`。
审计记录为本地 `v152-fabric1216-package-audit.json`、
`v152-native1216-package-audit.json`、`v152-fabric1217-1218-package-audit.json`
及 `v152-native1217-1218-package-audit.json`。

Fabric 适配器为 `adapt_v152_fabric1216.py` 和 `adapt_v152_fabric1218.py`，
跨加载器适配器为 `adapt_v152_native1216.py`。复用游戏唯一 GuiRenderer，
在物品捕获前消费主 GUI 状态，捕获时缩放逻辑坐标，并保留延迟提交纹理的生命周期。
GL 状态恢复同步 GlStateManager 缓存并失效旧管线缓存，修复重复截图后物品图标为空的问题。
后处理使用 std140 uniform buffer，保留世界投影和真实实体模型遮罩。
原有 accessor 重名覆盖已消除；Forge / NeoForge 液体顶点的 alpha 参数索引分别核对。
Forge 1.21.7 独有的 SubmitNodeCollectionMixin 名牌缩放逻辑继续保留。

Fabric 1.21.6 最终源码开发客户端验证了菜单、重复物品/盔甲图标截图、
TargetShader 四模式与 Moon 实体辉光。正确种子 8675309 接受、错误种子拒绝，
预览 720 个候选、620 个匹配，BaritoneMine 接管停止 SeedMine；人工钻石矿挖掘拾取通过。
保存退出后所有维度写盘，玩家与种子会话清空、预测归零、寻路及采集停止。
QA 退出接口先关闭 ClientLevel 连接，再调用游戏保存退出流程，避免仅切换屏幕造成的假通过。
证据位于 `.dsh-tmp/v152-matrix-runtime/fabric-versions-1.21.6/` 的
`clean-render-verification.json`、`world-verification.json`、`final-lifecycle-verification.json`
及截图，最终正常启动日志为 `v152-fabric1216-runtime11.log`。

1.21.6 曾有两次间歇性原生启动崩溃：Windows 记录 `lwjgl.dll` 偏移 `0x10c5`、
异常 `0xc0000005`，没有 JVM 错误报告。后续诊断启动和移除诊断后的普通启动均成功，
根因尚未定位，不能视作修复。其余八工程尚未完成本批实机验收；
九工程的生产包独立启动、完整战斗/搭路和音乐回归仍待完成。

## B5：1.21.9–1.21.10 输入与实体提交节点

六工程均通过各 254 项单测（零失败、错误、跳过）、生产打包和递归包审计。
最新审计记录为 `.dsh-tmp/v152-b4-b5-final-package-audit.json`，同时覆盖 B4 图层修复后的九工程。
Fabric 1.21.9 从 1.21.8 三方迁移，适配器为 `adapt_v152_fabric1219.py`；
1.21.10 使用 `adapt_v152_fabric12110.py`，原生加载器使用 `adapt_v152_native1219.py`。
新版鼠标、键盘和字符事件使用事件对象，
按钮使用 InputWithModifiers；实体辉光改用独立 SubmitNodeStorage 与 FeatureRenderDispatcher，
不能继续调用已移除的直接实体渲染方法。加载进度接入 LevelLoadTracker。
自动破解依赖选择官方 SeedCrackerX 2.15.5（声明支持 1.21.9），固定 SHA-256 为
`0d3860596669f804dd81446b971ce3a8f77d5f69827f2e1a8b3c1fb1ee3fcc81`，
同时移除旧供体中重复的嵌套依赖。

新版原生菜单不再自己声明 render，改用显式覆盖并保留父类回退；LAN 数据使用真实 accessor。
NanoVG 全屏快照分配独立 GUI stratum，防止按纹理排序后背景遮住导航器/CGUI；
该修复回移到 B4 九工程，均已重新构建验收。后处理顶点着色器改用新版
`minecraft:core/screenquad`，修复原始遮罩直接呈白色方块的问题；
Moon 的普通模型和轮廓提交都限制在遮罩目标内。

Fabric 1.21.9 最新开发客户端实机验证菜单、带存档的单人列表、音乐推荐页、
Navigator/CGUI 开关及返回，重复截图后的四件盔甲与热栏图标、TargetShader 四模式与
Moon 实体辉光。已知种子正确接受、错误拒绝，SeedMine/BaritoneMine 互斥，
人工钻石矿挖除拾取通过。保存退出后无玩家、种子为空、预测为零、寻路/采集停止。
证据为 `.dsh-tmp/v152-matrix-runtime/fabric-versions-1.21.9/` 的
`world-verification.json`、`clean-render-verification.json`、`final-lifecycle-verification.json`
及截图；最终实机日志 `v152-fabric1219-runtime8.log`。
一次测试遭遇 Windows 临时文件原子替换 AccessDenied，未跳过或放宽测试，重跑后全部通过。
其余五工程实机、六工程生产包独立启动、实际音乐播放及完整自动破解仍待验证。

## B6：1.21.11

已完成本批构建验收。资源类改名为 Identifier，部分实体迁入新包，
宿主 Netty 升为 4.2.7.Final。SeedCrackerX 使用上游明确声明支持该版本的 2.15.6，
SHA-256 `8695edc230f832339dc53c6fe3543e6d507ac054ef775082083b819fa8b4f227`。
适配器 `adapt_v152_fabric12111.py` 保留目标独有模块注册与液体选取逻辑，实机范围及限制见下文。

## B6：1.21.11 验收补充

三加载器各 254 项单元测试零失败/错误/跳过，Fabric remapJar、Forge allJar、
NeoForge jar 通过，包审计见 `.dsh-tmp/v152-b6-final-package-audit.json`。
已适配 Identifier、独立 GpuSampler、实体包路径、环境属性、最终按钮绘制入口，
Forge/NeoForge 流体透明度使用各自真实 vertex 参数槽；NeoForge FML 11 的
JUnit 引导使用实际加载器环境，不改变产品启动路径。

实机最初发现 NanoVG 普通字体全消失。原因是继承的 Minecraft 独立 sampler
覆盖 NanoVG 字体图集纹理参数；在上传/提交前解绑单元 0 sampler，并由
GlStateGuard 恢复，复验导航器中文/英文、CGUI、音乐和 HUD 文字及图标正常。
修复同样同步到两 native 工程。最终运行记录 `v152-fabric12111-runtime2.log`，
截图和验证 JSON 在 `.dsh-tmp/v152-matrix-runtime/fabric-versions-1.21.11/`。
种子 8675309 预览 424 个候选/352 个匹配，挖矿互斥和钻矿拾取通过；
保存退出后无玩家，seed null、predictions 0、pathing/collector false。
本轮尚未进行 1.21.11 native 实机或生产 JAR 独立启动。

## B7：26 系列

Fabric 26.1 已通过完整 254 项单测（零失败、错误、跳过）、`jar` 和递归包审计，
记录为 `.dsh-tmp/v152-fabric261-package-audit.json`。
适配 `GuiGraphicsExtractor`/`extractRenderState`、新 GUI 状态包、GPU backend 包装、
整数颜色掩码和流体透明度、实体运动包 record、物品模板及 Java 25。
FOV 注入迁往 Camera.calculateFov，时间/天气由 ClientClockManagerMixin 处理，
XRay 使用 SectionCompiler/FluidRenderer 与 Indigo 8 的真实入口；未用 `require=0` 隐藏失败。
EntityCulling 采用已验证的 v1.5.2 距离/不可见筛选实现，未宣称修复旧 GPU 遮挡查询。

SeedCrackerX 使用上游支持 26.1.x 的 2.16.0，固定 SHA-256 为
`652d0f13339e54b0b1087431e343db18b2b2ea1b4f546215d31da0abd823217d`。
已知种子、错误结果拒绝、预览与挖矿互斥、钻石矿挖除拾取通过。
后处理实测发现世界渲染钩子漏掉刷新；接回投影与两条刷新路径后，
截图确认 TargetShader 效果和 Moon 实体模型辉光。四件盔甲与热栏图标重复截图正常。
交易交互已改为非空 EntityHitResult，与新原版实现一致；自动交易完整行为仍待实测。
退出世界后无玩家、种子为空、预测为零、寻路/采集停止。
证据目录 `.dsh-tmp/v152-matrix-runtime/fabric-versions-26.1/`，
最新实机日志 `v152-fabric261-runtime4.log`。

Forge/NeoForge 26.1、Fabric/Forge 26.1.1/26.1.2 及 NeoForge 26.1.1 均通过各 254 项单测和生产包审计。
记录为 `v152-261-final-package-audit.json`、`v152-fabric261-family-package-audit.json`
及 `v152-native261patch-package-audit.json`。NeoForge 26.1.2 也已完成测试和生产包审计。
跨加载器测试使用实际 FML 初始化环境与游戏编译类路径；不改变产品启动路径。

26.2 已适配新的 Gui 屏幕管理器/Hud 拆分与 staged feature 渲染阶段，
SeedCrackerX 选择支持该版本的 2.16.1；26.3 的适配与实机证据见下文。均保留开发版本标识。

## 26.2 最新复验

三个加载器均完成 254 项单元测试（零失败、错误和跳过）、生产打包及递归依赖/CRC 审计。
证据为本地 `.dsh-tmp/v152-262-final-package-audit.json` 和对应构建日志。
Fabric 开发客户端验证了四件盔甲图标、TargetShader 四种模式、Moon 实体轮廓、
已知种子接受/拒绝（8675309/1234）、582 个候选矿点中的 510 个匹配点、
SeedMine/BaritoneMine 互斥、钻石挖掘拾取和保存退出后的会话清理。

26.2 GPU 清屏改为显式 RGBA `(0, 0, 0, 0)`；JOML 无参 Vector4f 的 alpha 为 1，
会使目标着色覆盖全屏，并破坏 Moon 轮廓 mask。修复后已实机复验。
Baritone 1.19.0 已提供 tick/world/render 注入，移除重复的旧桥接注入；
保留 Gui 到 Hud 的聊天访问兼容方法，挖矿拾取测试确认事件仍正常派发。
这轮验证不涵盖生产 JAR 独立启动和 Forge/NeoForge 的自动破解后端。

## 26.3 RenderPearl / SDL 适配

三个加载器的最新源码均完成 254 项单元测试（零失败、错误和跳过）及生产打包。
26.3 使用 Java 25、LWJGL 3.4.3、Netty 4.2.16.Final，Fabric 集成 SeedCrackerX 2.16.2。
GPU 桥接按 RenderPearl 的设备/命令编码器和纹理格式迁移；键盘使用 SDL 对应的
InputConstants 命名常量，窗口参数和旧 GLFW 依赖不再带入新版输入代码。

保留 26.3 的文本提交节点、SwingState、月相/资源包账户缓存钩子。
原版移除了独立挥手包，攻击和使用物品由相应交互包驱动服务端动画；
GlobalSetting 的 Client/Both 控制本地动画，Packet/None 不额外播放本地动画。
矿脉回放改为 AbstractOreFeature/BlockReplacement 与 PlacementModifier.modify，
客户端世界仍只读；测试种子 8675309 得到 288 个候选、232 个匹配点，
错误种子 1234 未覆盖当前种子，Baritone 钻石矿挖除/拾取和互斥通过。

实机发现并修复两类编译期无法发现的问题：
- LevelRenderer 不再暴露旧描边目标访问器，Moon 直接使用 PreparedFrame.executeOutline
  渲染到自己的 mask RenderPass；水下遮罩使用 PlayerRenderState.WaterOverlay 参数。
- RenderPearl 通过 SPIR-V 编译着色器；TargetShader 的片元输入/输出补齐 location=0，
  避免编译失败触发资源重载和退出世界。修复后四种效果及 Moon 实体轮廓截图已复验。

构建/审计证据保存在 `.dsh-tmp/v152-263-final-package-audit.json`；
实机记录位于 `.dsh-tmp/v152-matrix-runtime/fabric-versions-26.3/`。
这些仍是开发构建，不能据此宣称全部平台和全部模块已完成发行验收。
保存退出复验也已通过：无玩家，seed null、predictions 0、pathing/collector false；
再次打开和关闭 Navigator/CGUI 均回到 TitleScreen。

## LSD 后处理恢复（2026-11-18）

1.21.2–1.21.4 的旧实现只清除原版后处理，未真正加载 LSD；1.21.5 及以后则直接禁用。
现为这 44 个工程补回独立 OpenGL 世界后处理：复用原始 `lsd_wobble.fsh`、
512/288 频率与 0.002 波动幅度，先复制场景再着色，避免读写同一纹理。
只保留一个可复用颜色目标，随分辨率重分配；关闭、退出世界和关闭渲染器时释放资源。
`GlStateGuard` 恢复原版绘制状态，世界渲染结束后执行，HUD 和菜单保持原色。

适配脚本 `scripts/port/restore_v152_lsd.py` 只修改 LSD 和必要的渲染/生命周期钩子；
全版本同步脚本在最终配置后也会恢复此实现，避免供体合并重新带回禁用代码。
不使用可选注入或空方法掩盖渲染入口缺失。

Fabric 26.3 已截图确认两个动画阶段、关闭恢复、三次反复开关以及开启状态下退出世界。
证据：`v152-fabric263-lsd-runtime2.log`、运行目录 `lsd-verification.json` 和 `lsd-*` 截图。
首次复验在开启 LSD 前发生一次 `0xc0000005` 崩溃，Windows 事件指向 `jvm.dll`；
没有 JVM 崩溃栈，重启后同样流程通过，原因仍未定位，不能据此宣称稳定性已解决。
44 个工程的修复后测试、生产构建和递归包审计现已全部通过，证据为
`.dsh-tmp/v152-lsd-package-audit.json`。26.1 家族使用该版本实际存在的主渲染目标接口，
26.2 起才切换到 GameRenderer.mainRenderTarget；未用禁用后处理绕过接口差异。

## 原生 SeedCrackerX 后端（2026-11-18）

Forge/NeoForge 1.20.1 已从固定的 SeedCrackerX 2.14.4 官方包恢复 Mojang 映射源码，
保留真实 Finder、DataStorage、TimeMachine、配置页、命令和结构持久化。
九个 seedfinding 库固定 SHA-256；仅合并求解包，不带入 Fabric 加载器元数据。
Cloth Config Forge 11.0.99 以原生嵌套模组打包。
两个工程各通过 148 项单元测试（无失败、错误、跳过）及生产包递归审计；
审计还检查原生求解器、Finder、Cloth Config 和许可文件确实在包中。

Forge 1.20.1 开发客户端已采集到 25 条结构数据，收到登录种子哈希；
错误测试结果 1234 被拒绝，正确测试结果 8675309 经会话验证后启用 SeedMine，
2355 个候选中 2280 个匹配，Baritone 寻路正常，关闭采集模块也会停止它启动的挖矿。
正确结果由专用测试接口注入，**这不等于自动破解已端到端通过**。
纯求解器测试另外确认结构种子加哈希可恢复正、负完整种子。

实机发现 Forge 退出事件晚于单人服务器对象清空，旧结构保存代码因此空指针崩溃。
已改为进入世界时记录存档身份，退出不再查询已销毁的服务器；再次进入、采集和保存退出通过。
结构写入 `seed-mining-qa.txt`，退出后 seed null、predictions 0、pathing/collector false、
structureData 0、hashedSeed false，无玩家。证据为
`.dsh-tmp/v152-forge1201-run/native-seed-verification.json` 和 `v152-forge1201-native-seed-runtime3.log`。

1.20.2–1.20.4 的六个原生工程已接入对应的 SeedCrackerX 2.14.6 共享源码，
位于 `native/seedcrackerx/2.14.6/`；求解库仅从固定官方包提取 `com/seedfinding/`，
清单同时固定官方包及提取库的 SHA-256。使用对应加载器的 Cloth Config 12/13，
保留各工程自己的初始化、区块和种子哈希接口。六个工程各通过 148 项测试与生产包递归审计，
证据为 `.dsh-tmp/v152-native-seed1204-final-audit.json`。
NeoForge 普通 JAR 与最终 JarJar 输出使用不同文件名，避免打包时截断自己的输入；
求解类只合并到客户端包，不作为独立运行库重复导出。
`restore_v152_native_seed1204.py` 负责恢复这些钩子，全版本同步脚本会在合并后重新应用。

Forge 1.20.4 实机另修复多人界面 onClose 覆盖冲突；Forge 49 开发启动的类与资源输出合并，
让元数据和 @Mod 入口在同一可扫描根目录，生产启动不依赖 QA 开关。
开发客户端完成真实结构采集（31 条）、错误结果拒绝、显式测试种子 8675309 接入，
206 个矿点候选中 141 个匹配。保存退出后种子/预测/寻路/采集/哈希均清空，无玩家。
单人、多人、账户三页打开及关闭返回主菜单通过，并保存截图。
证据位于 `.dsh-tmp/v152-matrix-runtime/versions-1.20.4/` 的
`native-seed-verification.json`、`menu-verification.json`，日志为
`v152-forge1204-native-seed-runtime3.log`。正确种子由测试接口注入，仍未验证完整自动破解。

原生 1.20.5/1.20.6 的三个工程接入上游 2.14.7，1.21/1.21.1 的四个工程接入 2.15.0。
对应共享源码、官方包与求解库哈希位于 `native/seedcrackerx/2.14.7/` 和 `2.15.0/`。
保留上游动态结构列表和对应矩阵/顶点 API，继续应用本地命令/配置/会话与退出保存修复。
七个工程均通过本次测试与生产包审计：前三个各 152 项，后四个各 254 项，无失败、错误或跳过。
证据为 `.dsh-tmp/v152-native-seed1206-package-audit.json`，内含每个目标的复验日志。
Cloth Config 14/15 使用原生加载器版本与各加载器自己的嵌套依赖机制。
NeoForge 1.21 开发客户端也已完成真实结构采集（28 条）、错误结果拒绝、测试结果接入和预览，
315 个候选中 141 个与当前世界匹配；保存退出后会话全部清空，无玩家。
单人/多人/账户三页打开及关闭返回主菜单通过，截图确认 NanoVG 页面正常。
实机记录位于 `.dsh-tmp/v152-matrix-runtime/neoforge-versions-1.21/`，
日志 `v152-neo121-native-seed-runtime6.log`。此前启动失败涉及缺失开发配置/DevLaunch缓存、
本机临时目录权限和 QA 移除 NanoVG 类；已补齐验证过的官方依赖并修复测试启动配置。
正确种子仍为显式测试注入，不能视为自动求解验收。

1.21.2/1.21.3 的三个原生工程接入上游 2.15.1，1.21.4 的两个工程接入 2.15.2。
五个工程均通过 256 项测试及生产包递归审计，证据为
`.dsh-tmp/v152-native-seed1214-final-audit.json`。对应源码与哈希固定在
`native/seedcrackerx/2.15.1/`、`2.15.2/`，构建使用 `gradle/seedcracker-native1214.gradle`。
Forge 53/54 使用现有 LevelRenderer 强制钩子保留结构渲染，Cloth Config 16/17
以实际 Mojang 名称依赖编译并纳入原有 allJar 嵌套清单；保留原有 Baritone 元数据。
这五个工程尚未完成实机验证。

1.21.5–1.21.8 的八个原生工程接入上游 2.15.3/2.15.4，
1.21.9–1.21.11 的六个接入 2.15.5/2.15.6，26.1–26.3 的十个接入 2.16.0–2.16.2。
这 24 个工程各通过 256 项测试（零失败、错误、跳过）与生产包递归审计，
最终核对源码指纹和 JAR SHA-256 的汇总证据为
`.dsh-tmp/v152-native-seed-modern-final-audit.json`。
Forge 1.21.9 的账号文件原子替换曾遇到 AccessDeniedException，未修改或跳过测试，单独复测通过。
26.1 家族保留 getMainCamera；26.2 起使用 mainCamera 和 gui.screen；26.3 区块包改用 x/z 访问器。
这些差异已写回恢复脚本，后续同步不会重新覆盖为旧接口。

Forge 1.21.5 开发客户端已完成三页菜单进退、真实结构采集和登录哈希接收；
错误结果被拒绝，显式测试结果 8675309 产生 283 个矿点候选，其中 278 个与当前世界匹配。
ClothConfigScreen 实际打开，结构标记开关前后及 LSD 开关均已截图核对；
保存退出后种子、预测、采集、结构数据和哈希全部清空，Baritone 未寻路。
证据位于 `.dsh-tmp/v152-matrix-runtime/versions-1.21.5/` 的
`native-seed-verification.json`、`menu-verification.json`、`native-render-config-verification.json`。
这仍是开发启动，且正确种子由测试接口注入，不能据此宣称生产包或自动破解验收。

实机另发现 Forge 资源加载画面早于 FMLClientSetupEvent，LSD 在模块列表初始化前调用引发空指针。
44 个现代工程的 LSD 准备/绘制入口现先检查模块列表是否就绪；初始化后仍完整执行渲染。
修复已写回 `restore_v152_lsd.py`，受影响工程须以修复后构建记录为准。
ForgeGradle 7 测试启动使用 DirectoryProperty、游戏对应 JDK 和工作区资源缓存，
不修改用户外部启动器的资源和游戏目录。

Forge 26.3 开发客户端已采集 6 条结构数据并收到登录种子哈希；错误结果被拒绝，
显式测试结果 8675309 产生 378 个矿点候选，其中 347 个匹配当前世界。
保存退出后会话种子、预测、采集器、结构数据、哈希和寻路全部清空，无玩家。
ClothConfigScreen、实际结构线框开关和 LSD 开关均已截图核对。
证据位于 `.dsh-tmp/v152-matrix-runtime/versions-26.3/` 的
`native-seed-verification.json`、`native-ui-verification.json` 和 `native-ui-visual-review.json`。
正确种子仍为专用接口显式注入，未验证从采集到自动破解的完整链路。

实测还发现 26.3 从 GLFW 切换至 SDL 后，部分新增控件仍硬编码鼠标 0/1/2 和旧键码。
三个加载器版本现统一使用原版 InputConstants，保留左键切换、右键展开、中键绑定的语义；
音乐页的 Esc、Enter、数字键盘 Enter、空格也使用该版本常量。
`restore_v152_sdl_input.py` 已接入同步脚本，避免后续同步重新引入旧编号。
Forge 26.3 实际点击进入单人、多人、账户、CGUI、导航器并按 Esc 返回通过；
CGUI 左键切换 AutoSprint 后恢复原状态，右键只展开设置；导航器选中 HUD、音乐切换设置页通过。
截图已逐项核对，证据为同目录 `native-input-verification.json`、
`native-input-controls-verification.json` 和 `native-input-visual-review.json`。
首次正常退出触发的 watchdog 报告来自 opt-in 测试 HTTP 服务未关闭；
现由客户端 close 钩子停止服务和保活线程，Forge 菜单退出后的启动任务退出码为 0，
记录见 `native-shutdown-verification.json`。测试服务默认仍不启用。

NeoForge 26.3 的开发启动曾因 `prepareClientRun` 缺少官方 ModDev 配置而中断。
正式生产包另在独立安装的 NeoForge 实例中启动，修正了流体交互 Mixin 的生产环境注入点。

### 正式生产包实机验证

Fabric 1.20.1 的 Java 17 独立实例通过主菜单启动，确认 WurstB+ Plus 和 Baritone 加载成功。
JAR 的 SHA-256 为 `a2e9cf025aa1aa057ddc4fb16fdafaa5ce0e46717a57c64baebaca70667ccbdb`，
本地报告位于 `.dsh-tmp/v152-fabric1201-production/report-retry/`。

2026-11-18，Minecraft 26.3 三种加载器的 1.5.2 JAR 均通过主菜单启动、单人世界加载、
NanoVG GL3 初始化、写回存档和正常退出（退出码 0）：

| 加载器 | 生产 JAR SHA-256 |
| --- | --- |
| Fabric | `e2ae57e47aea118ef3a7b1c39b7d46296352997766ab427575b92a7f1fafda61` |
| Forge | `e0454bda96ab7f954462608dbd97d10370685841ed0ef1f83e4f679314b16769` |
| NeoForge | `a65c32f177022d3f767d19807a1bc3eb3637c5989e1e987514f56d0bd69711d1` |

详细日志保存在本地 `.dsh-tmp/v152-production-20261118-201653/` 下的 `release-menu-report/`、
`release-world-report2/` 与 `release-neo-report/`。结果限于所列 JAR 和场景。

旧工程补验另修复审计器将根工程 `.` 误读为矩阵输出根目录的问题，实际 Gradle 输出为 `root/`。
Fabric 1.20.4 的 146 项测试通过后，TinyRemapper 被本机错误的 Files.isWritable 结果拦住；
使用限定在该工程隔离输出目录的非截断写入探测后成功打包，未修改工程源码和测试。
构建环境与前后源码哈希记录于 `.dsh-tmp/v152-legacy-fabric1204-probe-environment.json`，
产物审计为 `.dsh-tmp/v152-legacy-fabric1204-final-audit.json`。

Forge 26.3 已补齐实际 MixinExtras 0.5.4 依赖的 MIT 许可与来源哈希；
全仓 67 个工程的许可检查通过。

1.21.5 起 NeoForge 使用官方 Cloth Config 嵌套模组；Forge 合并对应版本完整配置页与数学 API，
仅转换 Dist/OnlyIn 注解命名空间，不复制 NeoForge 入口或 AutoConfig。
来源、哈希与重新提取说明见 `native/cloth-config/README.md`。
结构渲染保留上游边框、颜色和相机偏移；26.2/26.3 在当前帧通过 WurstBufferSource 提交几何，
避免在帧图结束后提交导致显示晚一帧。

生产包独立启动、实际采集到自动求解的完整链路与全模块回归仍待完成。
