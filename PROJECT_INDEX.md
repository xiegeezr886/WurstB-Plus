# 项目索引

本文档记录当前工作区的实际结构。统计基于各工程自身的 `src/main`，不包含根目录参考压缩包中的源码文件。

> **当前状态（重要）**：根目录工程已推进到 **v1.6.0**，其余 14 个平台工程仍停留在 **v1.5.0**。
> v1.6 新增的 18 个源码包（152 个 Java 文件）**只存在于根目录 Forge 1.20.1 工程**，尚未移植。
> 详见「v1.6 新增子系统」与 [CHANGELOG.md](CHANGELOG.md)。
>
> 本文件的下表只覆盖上述 **15 个已发布工程**。工作区另有 **46 个新版本工程**
> （`versions/` 无 1.20.5 / 1.21.2；`fabric/versions/`、`neoforge/versions/` 各 21 个），
> 它们的 `compileJava` 已全部通过，但**没有 `build/libs/` 产物、没有启动验证**，
> 逐版本状态见 [README.md](README.md#多版本平行移植) 与
> [docs/PORTING-NEW-VERSIONS.md](docs/PORTING-NEW-VERSIONS.md)。

## 版本与源码路径索引

仓库内共有 **15 个独立构建工程**（5 个 MC 版本 × Forge/NeoForge/Fabric）。各工程拥有独立的 `build.gradle`、`settings.gradle`、gradle wrapper 与**完整复制的源码树**——不存在共享 sourceSet。源码位于 `<工程根>/src/main/java/`，资源与元数据位于 `<工程根>/src/main/resources/`。

| 工程（构建目录） | 平台 / 游戏版本 | 工具链 | 源码文件数 | 版本 | 入口类 | 发布产物（build/libs/） |
| --- | --- | --- | ---: | --- | --- | --- |
| `.`（根） | Forge 47.4.10 / 1.20.1 | JDK 17 · Gradle 8.11 · ForgeGradle 6.0 | **952** | **v1.6.0** | `WurstForgeInitializer` | `WurstB+ Plus-v1.6.0-Forge-1.20.1.jar`（68.1 MB） |
| `versions/1.21.1/` | Forge 52.1.16 / 1.21.1 | JDK 21 · Gradle 8.11 · ForgeGradle 6.0 | 741 | v1.5.0 | `WurstForgeInitializer` | `WurstB+ Plus-v1.5.0-Forge-1.21.1.jar` |
| `versions/1.21.11/` | Forge 61.2.0 / 1.21.11 | JDK 21 · Gradle 9.4.1 · ForgeGradle 7.x | 748 | v1.5.0 | `WurstForgeInitializer` | `WurstB+ Plus-v1.5.0-Forge-1.21.11.jar` |
| `versions/26.1.2/` | Forge 64.1.0 / 26.1.2 | JDK 25 · Gradle 9.4.1 · ForgeGradle 7.x | 790 | v1.5.0 | `WurstForgeInitializer` | `WurstB+ Plus-v1.5.0-Forge-26.1.2.jar` |
| `versions/26.2/` | Forge 65.1.0 / 26.2 | JDK 25 · Gradle 9.4.1 · ForgeGradle 7.x | 791 | v1.5.0 | `WurstForgeInitializer` | `WurstB+ Plus-v1.5.0-Forge-26.2.jar` |
| `neoforge/` | NeoForge 47.1.3 / 1.20.1 | JDK 17 · Gradle 8.14.4 | 738 | v1.5.0 | `WurstForgeInitializer` | `WurstB+ Plus-v1.5.0-NeoForge-1.20.1.jar` |
| `neoforge/versions/1.21.1/` | NeoForge 21.1.244 / 1.21.1 | JDK 21 · Gradle 9.4.1 · ModDevGradle | 741 | v1.5.0 | `WurstForgeInitializer` | `WurstB+ Plus-v1.5.0-NeoForge-1.21.1.jar` |
| `neoforge/versions/1.21.11/` | NeoForge 21.11.45 / 1.21.11 | JDK 21 · Gradle 9.4.1 · ModDevGradle | 749 | v1.5.0 | `WurstForgeInitializer` | `WurstB+ Plus-v1.5.0-NeoForge-1.21.11.jar` |
| `neoforge/versions/26.1.2/` | NeoForge 26.1.2.87 / 26.1.2 | JDK 25 · Gradle 9.4.1 · ModDevGradle | 790 | v1.5.0 | `WurstForgeInitializer` | `WurstB+ Plus-v1.5.0-NeoForge-26.1.2.jar` |
| `neoforge/versions/26.2/` | NeoForge 26.2.0.53-beta / 26.2 | JDK 25 · Gradle 9.4.1 · ModDevGradle | 792 | v1.5.0 | `WurstForgeInitializer` | `WurstB+ Plus-v1.5.0-NeoForge-26.2.jar` |
| `fabric/` | Fabric Loader 0.16.14 / 1.20.1 | JDK 17 · Gradle 8.11 · Loom | 766 | v1.5.0 | `WurstInitializer` | `WurstB+ Plus-1.5.0-Fabric-1.20.1.jar` |
| `fabric/versions/1.21.1/` | Fabric Loader 0.16.14 / 1.21.1 | JDK 21 · Gradle 8.11 · Loom | 777 | v1.5.0 | `WurstInitializer` | `WurstB+ Plus-1.5.0-Fabric-1.21.1.jar` |
| `fabric/versions/1.21.11/` | Fabric Loader 0.19.3 / 1.21.11 | JDK 21 · Gradle 9.6.0 · Loom | 748 | v1.5.0 | `WurstInitializer` | `WurstB+ Plus-1.5.0-Fabric-1.21.11.jar` |
| `fabric/versions/26.1.2/` | Fabric Loader 0.19.3 / 26.1.2 | JDK 25 · Gradle 9.6.0 · Loom | 791 | v1.5.0 | `WurstInitializer` | `WurstB+ Plus-1.5.0-Fabric-26.1.2.jar` |
| `fabric/versions/26.2/` | Fabric Loader 0.19.3 / 26.2 | JDK 25 · Gradle 9.6.0 · Loom | 792 | v1.5.0 | `WurstInitializer` | `WurstB+ Plus-1.5.0-Fabric-26.2.jar` |

合计 **11,685 个 Java 源文件**。全部 15 个工程的 `build/libs/` 均存在已构建产物（除根目录为本轮实际重建外，其余为工作区导入时的既有产物，未在本轮重新验证）。

配套目录：

| 路径 | 说明 |
| --- | --- |
| `baritone-maven/` | 本地 Maven 仓库：`baritone/baritone-forge/1.18.0/`、`baritone/baritone-neoforge/1.18.0/` 等，供 Forge/NeoForge 工程 jarJar 引用 |
| `fabric/libs/`、`fabric/versions/*/libs/` | baritone-api-fabric flatDir 目录 |
| `scripts/` | 构建与运维脚本：`common.ps1`（JDK/工程表）、`doctor.ps1`（工具链诊断）、`seed-gradle-wrapper.ps1`（离线 wrapper 播种）、`run-unit-tests.ps1`（根工程单测）、`build-all.ps1`（批量构建 15 个版本）、`run-version-tests.ps1`（批量真实启动测试）、`replace-jar-entry.ps1`（JAR 条目替换）、`upgrade-lwjgl.ps1`（LWJGL 原生库升级）、`patch-baritone-26.2.ps1`（Baritone 兼容补丁）、`ccswitch-guardian.ps1` + `install-ccswitch-guardian.ps1`（代理守护） |
| `docs/` | `ANTICHEAT.md`、`COMBAT_ARCHITECTURE.md`、`CONFIG-FORMAT.md`、`PORTING-1.21.11-26.2.md` |
| `.test/` | PCL 格式本地测试环境（版本实例、libraries、assets、natives、报告）；不入库 |
| `.opencode/skills/` | 供 AI 代理使用的技能说明集合，不参与编译 |

## v1.6 新增子系统（仅根目录 Forge 1.20.1）

以下 18 个源码包在 14 个平台工程中**完全不存在**，合计 152 个 Java 文件：

| 包 | 文件数 | 职责 |
| --- | ---: | --- |
| `clickgui2/component` | 27 | VAPE 风格 GUI 组件层（`VapeClickGuiScreen`、`SuperSoft*` 窗口、`ModuleCardComponent`、`InlineSettingComponents` 等） |
| `music` + `music/apple` | 28 | 网易云 API（`NeteaseCloudApi`）、播放器（`NeteaseMusicPlayer`）、账号（`MusicAccountManager`）、逐字歌词动画（`AppleLyricPlayer` / `AppleTimeline` / `AppleLayout` / `Spring` / `LyricWordSplitter`）、AMLL 一比一视觉层（`AmlEasing` 缓动 / `AmlVisual` 透明度·模糊·缩放公式 / `AmlEmphasize` 逐字强调 / `AmlOptimize` 歌词优化流水线 / `AmlLayoutReason` 排版策略表 / `AmlTween` CSS 过渡 / `AmlLineBalancer` 断行平衡 / `AmlMask` 不雅词掩码） |
| `clickgui2/music` | 13 | 音乐 GUI 页面：`HomePage` / `SearchPage` / `LikedPage` / `PlaylistDetailPage` / `LoginPage` / `PlayerDetailOverlay` / `BottomPlayerBar` / `StarRiverBackground` + 封面粒子与涟漪 |
| `compose` | 11 | 声明式 UI 布局树（`UiNode` / `UiRow` / `UiColumn` / `UiBox` / `UiText` / `UiSpacer`）、`AnimFloat`、`FlowingGradient`、`ModuleColors` |
| `clickgui2/epsilon` | 5 | Epsilon 风格下拉式 GUI（`EpsilonDropdownScreen` / `Panel` / `ModuleButton` / `Theme` / `CategoryPanel`） |
| `clickgui2/supersoft` | 5 | SuperSoft 体系：`EpsilonMd3Theme`（MD3 TonalSpot 调色）、`SuperSoftTheme` / `SuperSoftRenderer` / `UiMotion` / `UiTween` |
| `render/skia` | 4 | Skiko 矢量渲染：`SkikoNatives`（解压并加载原生库）、`SkiaGlBackend`、`SkiaFontManager`、`SkiaRegionRenderer` |
| `gui/visual` | 3 | `VisualTheme`（语义色 token）、`VisualRenderer`、`VisualScreenMotion` |
| `hud2/render` | 2 | `RiseFrostedGlass`（磨砂玻璃）、`RiseHudFont` |
| `perimeter` | 35 | 周界挖掘全套：区域模型与游标、液体策略与边界封闭、方块限制与批次、导航 / 交互 / 装备 / 补给策略、28 状态自动化编排、双语文本 |
| `perimeter.config` | 11 | 按服务器 / 存档分存的配置模型、迁移与原子写盘（`PerimeterConfigStore`） |
| `perimeter.detect` | 2 | 不规则区域边界检测（`BoundaryDetector` / `PerimeterGrid`） |
| `seed` | 8 | 种子矿透核心：按服务器/存档分存的种子（`SeedStore`）、纯 Java 矿物预测（`OrePredictor` / `OreRules` / `OreCount` / `OreHeight`）、Baritone 目标挖掘作业（`SeedMineJob`） |
| `seed.structure` | 3 | 结构定位：调用原版公开放置方法（`StructureFinder`）、频率削减（`PlacementFrequency`）与命中模型（`StructureHit`） |
| `seed.search` | 4 | 种子反解：观测模型、零分配 LCG 候选搜索、后台任务 |
| `seed.crack` | 2 | 无范围反解：位块分解 + 陪集求交（`LatticeCracker`） |
| `seed.scan` | 1 | 自动观测：方块特征结构识别（`StructureScanner`） |

对应资源：`assets/wurst/skiko/`（`skiko-windows-x64.dll` 16.5 MB + `icudtl.dat` 10.0 MB，随 mod 资源打包而非 jarJar）、`assets/wurst/textures/gui/netease/`（音乐 UI 图标）。

**v1.6 新增 Hack（14 个，不存在于平台工程）**：`AirJumpHack`、`EntityCullingHack`、`MusicPlayerHack`、`NoMissCooldownHack`、`NoRotateHack`、`PerimeterDiggerHack`、`ProjectilePuncherHack`、`ReverseStepHack`、`RightClickerHack`、`SeedOreEspHack`、`SeedStructureEspHack`、`SuperKnockbackHack`、`VehicleBoostHack`、`WTapHack`。

根目录 `build.gradle` 相应新增：`org.jetbrains.skiko:skiko-awt:0.8.19`、`kotlin-stdlib`、`kotlinx-coroutines-core-jvm`，以及音乐播放依赖链（`java-stream-player`、`mp3spi`、`jlayer`、`jflac-codec`、`vorbis-support`、`tritonus-all`、`jorbis`、`jaudiotagger`）。根工程 jarJar 共内嵌 19 个依赖 jar。

## 根目录

| 路径 | 说明 |
| --- | --- |
| `build.gradle` | ForgeGradle 6.0、MixinGradle、JarJar、Skiko 与音乐依赖、Forge 运行配置 |
| `gradle.properties` | Minecraft、Forge、MixinExtras 与项目版本（`mod_version=v1.6.0-Forge-1.20.1`） |
| `settings.gradle` | ForgeGradle 插件仓库配置 |
| `gradle/` | Gradle 8.11 wrapper |
| `src/main/java/` | 纯 Forge/Mojmap Java 源码，共 952 个文件 |
| `src/main/resources/` | Mixin、Access Transformer、Forge 元数据、字体、shader、Skiko 原生库与翻译资源 |
| `src/test/java/` | 根工程单元测试（含 v1.6 音乐解析、歌词时间轴、Compose 动画、MD3 主题、周界挖掘与种子矿透） |
| `LICENSE.txt` | GPL-3.0 许可证 |
| `README.md` | 项目架构与状态说明（含 15 工程布局与新版本工程） |
| `CHANGELOG.md` | 版本变更记录 |
| `PORTING_TASK.md` | 移植与修复任务清单（含根因与验证） |

## 源码包索引（根目录 Forge 1.20.1 工程）

| 包 | 文件数 | 职责 |
| --- | ---: | --- |
| `net.wurstclient` | 11 | 客户端生命周期、功能基类、翻译和共享状态 |
| `net.wurstclient.addon` | 2 | Addon 扩展系统（WurstAddon、AddonManager） |
| `net.wurstclient.ai` | 8 | 路径搜索、Spider 垂直节点规划和路径执行 |
| `net.wurstclient.altmanager` | 25 | 账号、登录、系统原生凭据主密钥和账号管理界面 |
| `net.wurstclient.clickgui2` | 107 | 双 GUI、VAPE/SuperSoft/Epsilon 组件层、音乐界面、实心主题、字体偏好、窗口与控件 |
| `net.wurstclient.command` | 7 | 命令基础设施、BrigadierCommand 和处理器 |
| `net.wurstclient.commands` | 57 | 具体命令实现（含 `.macros` `.waypoints` `.proxy` `.perimeter` `.seed`、Brigadier `/perimeterdig`） |
| `net.wurstclient.compose` | 11 | 声明式 UI 布局树（v1.6 新增） |
| `net.wurstclient.discord` | 2 | Discord RPC IPC 客户端和管理器 |
| `net.wurstclient.event` | 6 | EventManager、WurstSubscriber（LambdaMetafactory）、注解 |
| `net.wurstclient.events` | 38 | 输入、移动、网络、渲染等事件接口 |
| `net.wurstclient.gui` | 5 | 标题界面（`title/`）与视觉 token/渲染（`visual/`，v1.6 新增） |
| `net.wurstclient.hack` | 7 | Hack 基类、注册表、冲突和生命周期 |
| `net.wurstclient.hacks` | 210 | Hack 实现及其内部辅助类 |
| `net.wurstclient.hud` | 4 | HUD 和 TabGUI 渲染 |
| `net.wurstclient.hud2` | 43 | HUD2 系统（统一渲染、磨砂玻璃、指标采样、卡片编辑器及独立元素） |
| `net.wurstclient.keybinds` | 6 | 按键绑定、智能绑定（TOGGLE/HOLD/SMART）、配置和执行 |
| `net.wurstclient.macros` | 2 | Macro 定义和 MacroManager |
| `net.wurstclient.mixin` | 75 | Minecraft/Forge 注入点及 Mixin 配置插件；普通运行类禁止放入该专用包 |
| `net.wurstclient.mixinterface` | 7 | Mixin 暴露接口 |
| `net.wurstclient.music` + `apple` | 28 | 网易云 API、播放器、账号与歌词动画（v1.6 新增） |
| `net.wurstclient.nochatreports` | 2 | 聊天报告相关数据处理 |
| `net.wurstclient.options` | 8 | 客户端选项与配置管理界面 |
| `net.wurstclient.other_feature` | 2 | Other Feature 基础设施 |
| `net.wurstclient.other_features` | 17 | 非 Hack 功能入口 |
| `net.wurstclient.perimeter` | 22 | 周界挖掘核心：区域/游标/进度、液体策略、方块限制、导航、交互、装备、补给、自动化编排与双语文本（v1.6 新增） |
| `net.wurstclient.perimeter.config` | 11 | 分存配置模型、迁移与存储（v1.6 新增） |
| `net.wurstclient.perimeter.detect` | 2 | 不规则区域边界检测（v1.6 新增） |
| `net.wurstclient.proxy` | 2 | ProxyConfig 和 ProxyManager |
| `net.wurstclient.render.skia` | 4 | Skiko 矢量渲染管线（v1.6 新增） |
| `net.wurstclient.seed` | 8 | 种子矿透核心：种子存储、纯 Java 矿物预测、Baritone 目标挖掘作业（v1.6 新增） |
| `net.wurstclient.seed.structure` | 3 | 结构定位与频率削减（v1.6 新增） |
| `net.wurstclient.seed.search` | 4 | 种子反解搜索与零分配 LCG（v1.6 新增） |
| `net.wurstclient.seed.crack` | 2 | 无范围反解求解器（v1.6 新增） |
| `net.wurstclient.seed.scan` | 1 | 自动结构观测扫描器（v1.6 新增） |
| `net.wurstclient.serverfinder` | 3 | 服务器扫描和清理界面 |
| `net.wurstclient.settings` | 55 | 设置类型、过滤器和配置文件 |
| `net.wurstclient.update` | 3 | 更新与资源包问题检测 |
| `net.wurstclient.util` | 95 | 渲染、实体/名称标签快照、屏幕投影、放置规划、战斗/击退规划、库存队列、异步纹理、GPU 遮挡、区块搜索和 JSON 工具 |
| `net.wurstclient.waypoints` | 2 | Waypoint 定义和 WaypointsManager |

## 功能注册表

### Hack

注册入口：`src/main/java/net/wurstclient/hack/HackList.java`，共 210 个 Hack 源文件。

| 分类 | 说明 |
| --- | --- |
| Blocks / Chat / Combat / Fun / Items / Movement / Other / Render | 常规分类 |
| `ClickGuiHack`、`NavigatorHack` | 两个 GUI 入口，不设常规分类，也不发送普通模块的 Enabled/Disabled 通知 |

v1.5 新增 14 个 Hack：AntiBot (Combat)，EntityCulling/BossStack/PopChams/Breadcrumbs/LightOverlay/LogoutSpots/PlayerHalo (Render)，DankBobbing/Notebot/Twerk/Vomit (Fun)，PacketCanceller/PacketLogger (Other)。
v1.6 新增 14 个 Hack：见「v1.6 新增子系统」。其中 `SeedOreESP`（Render）用已知种子预测矿物并在客户端渲染 ESP，可选交给 Baritone 自动挖掘；`SeedStructureESP`（Render）渲染预测结构位置，`.seed structures` 输出候选列表；其中 `PerimeterDigger`（Blocks）配合 `.perimeter` 与 Brigadier `/perimeterdig` 提供原生周界挖掘：闭区间矩形规划、不规则区域边界检测、液体 avoid/replace/seal_boundary、批次与背包管理、多卸货点卸货、工具/鞘翅耐久替换、自动进食/补给/睡觉、跨维度熔炉修复、行走与鞘翅寻路、按服务器/存档分存配置、中英双语文本。

### 命令

注册入口：`src/main/java/net/wurstclient/command/CmdList.java`，共 56 个命令，覆盖按键、设置、好友、路径、物品、传送、NBT、XRay、宏、路径点、代理、周界挖掘、种子矿透/结构/反解和功能管理。v1.5 新增 `.macros` `.waypoints` `.proxy`，v1.6 新增 `.perimeter` `.seed`。

### Other Feature

注册入口：`src/main/java/net/wurstclient/other_feature/OtfList.java`，共 17 个，包括 Changelog、Disable、HackList、KeybindManager、NoChatReports、NoTelemetry、ServerFinder、TabGUI、Translations、VanillaSpoof、Zoom 等。

## 核心文件导航

| 领域 | 文件 |
| --- | --- |
| 客户端生命周期 | `src/main/java/net/wurstclient/WurstClient.java` |
| 当前 Forge 入口 | `src/main/java/net/wurstclient/WurstForgeInitializer.java` |
| Hack 基类 / 注册表 | `src/main/java/net/wurstclient/hack/Hack.java`、`HackList.java` |
| 事件管理器 / 订阅器 | `src/main/java/net/wurstclient/event/EventManager.java`、`WurstSubscriber.java` |
| 设置基类 / 持久化 | `src/main/java/net/wurstclient/settings/Setting.java`、`SettingsFile.java` |
| ClickGUI 选择入口 | `src/main/java/net/wurstclient/clickgui2/ClickGuiScreens.java` |
| VAPE 风格屏幕 | `src/main/java/net/wurstclient/clickgui2/component/VapeClickGuiScreen.java` |
| Epsilon 下拉屏幕 | `src/main/java/net/wurstclient/clickgui2/epsilon/EpsilonDropdownScreen.java` |
| SuperSoft 屏幕 | `src/main/java/net/wurstclient/clickgui2/component/SuperSoftClickGuiScreen.java` |
| SuperSoft 主题 | `src/main/java/net/wurstclient/clickgui2/supersoft/EpsilonMd3Theme.java`、`SuperSoftTheme.java` |
| 视觉 token | `src/main/java/net/wurstclient/gui/visual/VisualTheme.java` |
| 声明式 UI | `src/main/java/net/wurstclient/compose/UiNode.java` |
| 网易云客户端 | `src/main/java/net/wurstclient/music/NeteaseCloudApi.java`、`NeteaseMusicPlayer.java` |
| Skiko 原生库加载 | `src/main/java/net/wurstclient/render/skia/SkikoNatives.java` |
| Navigator 主屏幕 | `src/main/java/net/wurstclient/clickgui2/NavigatorScreen.java` |
| ClickGUI 窗口管理 | `src/main/java/net/wurstclient/clickgui2/ClickGui.java` |
| HUD / HUD2 管理器 | `src/main/java/net/wurstclient/hud/IngameHUD.java`、`hud2/HudManager.java` |
| 每 Tick 实体快照 | `src/main/java/net/wurstclient/util/EntitySnapshotManager.java` |
| 渲染状态作用域 | `src/main/java/net/wurstclient/util/render/RenderScope.java` |
| 旋转协调 | `src/main/java/net/wurstclient/RotationFaker.java` |
| 通用渲染 | `src/main/java/net/wurstclient/util/RenderUtils.java` |
| 实体/旋转工具 | `src/main/java/net/wurstclient/util/EntityUtils.java`、`RotationUtils.java` |
| 代理管理器 | `src/main/java/net/wurstclient/proxy/ProxyManager.java` |
| 账号加密 | `src/main/java/net/wurstclient/altmanager/Encryption.java` |
| Forge 平台工具 | `src/main/java/net/wurstclient/util/PlatformUtils.java` |

## 新增架构文件

| 文件 | 职责 | 来源 |
|------|------|------|
| `event/WurstSubscriber.java` | LambdaMetafactory 直接方法调用订阅器 | BleachHack |
| `util/FuzzySearch.java` | Unicode/CamelCase 规范化、缩写/子序列/Damerau-Levenshtein 匹配与相关度评分 | Meteor 思路，按当前 GUI 重写 |
| `util/DeferredActionQueue.java` | 跨 tick 延迟操作队列 | BleachHack |
| `util/ClickPattern.java` | 7 种 LiquidBounce 原始 20 Tick 数组填充算法 | LiquidBounce |
| `util/RollingClickArray.java` + `CombatClickScheduler.java` | 双 20 Tick 循环点击数组、单 Tick 多点击、空挥冷却 | LiquidBounce |
| `util/RotationSmoothing.java` | 5 种旋转平滑算法 | LiquidBounce |
| `keybinds/KeyAction.java` | 智能绑定枚举（TOGGLE/HOLD/SMART） | LiquidBounce |
| `macros/`、`waypoints/`、`proxy/` | 宏、路径点、SOCKS 代理 | Meteor |
| `addon/` | 第三方模块扩展（ServiceLoader） | Meteor |
| `command/BrigadierCommand.java` | Minecraft 原生命令框架 | Meteor |
| `discord/` | Discord Rich Presence | Meteor |
| `hud2/elements/*` | FPS/Coords/Ping/TPS/Speed/Server/Clock/Armor/Inventory/Potion/Combo/Keystrokes/Target/Minimap 元素 | Meteor/LabyMod/FDP 融合 |
| `hud2/render/RiseFrostedGlass.java` | 磨砂玻璃 HUD 背景（v1.6） | Rise |
| `gui/visual/VisualTheme.java` | 语义色 token（v1.6） | LiquidBounce Nextgen 思路 |
| `clickgui2/supersoft/EpsilonMd3Theme.java` | MD3 TonalSpot 调色板（v1.6） | Epsilon |
| `clickgui2/component/*` | VAPE 风格组件层（v1.6） | VAPE |
| `compose/*` | 声明式布局树（v1.6） | 项目自研 |
| `music/*` | 网易云 API/播放器/歌词动画（v1.6） | 项目自研 |
| `render/skia/*` | Skiko 矢量渲染（v1.6） | Skiko |
| `util/EntitySnapshotManager.java` | 客户端线程每 Tick 发布不可变实体分类快照 | FrogClient |
| `util/RotationQueue.java` + `RotationFaker.java` | 多模块静默旋转优先级仲裁与本地相机分离 | LiquidBounce RotationManager 等价架构 |
| `util/inventory/InventoryActionQueue.java` | 菜单校验、状态复验和优先级原子库存动作链 | LiquidBounce InventoryManager 思路 |
| `util/PlacementPlan.java` + `ScaffoldPlacementPlanner.java` | 预测落点、支撑面、视线、距离和连续性评分 | LiquidBounce Scaffold 规划思路 |
| `util/MovementPlanner.java` | 标准化八向输入、世界方向速度、平滑转向和水平限速 | LiquidBounce/FrogClient |
| `util/WorldToScreen.java` | AABB 八角屏幕投影与裁剪 | FrogClient |
| `util/render/RenderScope.java` | 异常安全恢复 GL/FBO/BufferBuilder 状态 | EMC RenderStack 思路 |
| `util/render/PostEffectQueue.java` + `hacks/TargetShaderHack.java` | 独立 FBO 任务分组、PostChain 效果及 Alpha 合成 | FrogClient ShaderManager 思路 |
| `util/ScreenRegistry.java` | 原版与客户端屏幕识别、创建和打开 | EMC ScreenRegistry 思路 |
| `altmanager/credentials/` | Windows/macOS/Linux 原生凭据主密钥 | LabyMod |
| `util/render/AsyncTextureLoader.java` | 后台解码、渲染线程上传的纹理管线 | LabyMod |
| `util/render/EntityOcclusionCuller.java` | 异步 GPU 查询与缓存单位立方体 VBO | LabyMod |
| `util/NbsSong.java` | 有边界校验的旧版及 NBS v1-v5 解析器 | 项目重构 |

## 重构文件导航

### 事件与搜索

| 文件 | 当前职责 |
| --- | --- |
| `event/EventManager.java` | 接口监听器 + LambdaMetafactory 双路分发，支持事件继承 |
| `clickgui2/FeatureMenuSupport.java` | 集成模糊搜索退路 |
| `clickgui2/ClickGuiScreens.java` | 按 `ClickGuiStyle` 选择 Epsilon / SuperSoft / Vape 屏幕并重建 |

### 按键绑定

| 文件 | 当前职责 |
| --- | --- |
| `keybinds/KeybindProcessor.java` | 支持 TOGGLE/HOLD/SMART 三种模式 + 按键释放追踪 |

### 战斗

| 文件 | 当前职责 |
| --- | --- |
| `util/CombatTargetUtils.java` | AABB 最近点距离、全局过滤、四种优先级、评分和数量限制 |
| `util/CombatClickScheduler.java` | CPS 区间、点击技术、原版武器冷却、点击前瞻和成功点击计时 |
| `util/CombatAimPointPlanner.java` | 预测 AABB 多点采样、可见/穿墙独立距离与最小转角命中点选择 |
| `util/CombatTargetSession.java` / `CombatRotationController.java` / `CombatIntentQueue.java` | 目标连续性、旋转所有权与决策跨阶段传递（详见 `docs/COMBAT_ARCHITECTURE.md`） |
| `util/TargetTracker.java` | 黏性目标、切换冷却和评分优势阈值 |
| `util/CombatActionPolicy.java` | Criticals 的冷却、移动状态与异常状态纯策略检查 |
| `hacks/KillauraHack.java` / `MultiAuraHack.java` | LiquidBounce Nextgen / FDP 行为等价执行链 |
| `hacks/NoVelocityHack.java` + `util/VelocityPlanner.java` | Modify/JumpReset 模式、触发过滤、动量保留 |
| `hacks/AntiBotHack.java` | 每 Tick 重算的组合谓词 |
| `hacks/CrystalAuraHack.java` / `AnchorAuraHack.java` | 单步状态机与伤害规划 |
| `hacks/HitboxesHack.java` + `util/HitboxExpansionPolicy.java` | 仅扩展客户端世界中的非本地目标碰撞箱 |

### 移动模块

| 文件 | 当前职责 |
| --- | --- |
| `hacks/AutoSprintHack.java` + `mixin/ClientPlayerEntityMixin.java` | 全方向疾跑与状态所有权 |
| `hacks/KeepSprintHack.java` + `mixin/PlayerMixin.java` + `util/KeepSprintPolicy.java` | 只保留攻击前已存在的疾跑，`@Redirect` 替换速度倍率 |
| `hacks/SpeedHackHack.java`、`FlightHack.java`、`NoFallHack.java`、`NoSlowdownHack.java`、`StepHack.java`、`SafeWalkHack.java`、`ScaffoldWalkHack.java`、`InvWalkHack.java` | 统一使用 `MovementPlanner` 与库存/放置规划 |

### ESP 与 GUI

| 文件 | 当前职责 |
| --- | --- |
| `util/EntityEspRenderer.java`、`util/WorldToScreen.java` | 实体 ESP 批处理与屏幕投影 |
| `hacks/PlayerEspHack.java`、`MobEspHack.java`、`NameTagsHack.java`、`PlayerHaloHack.java` | 各类 ESP 与快照渲染 |
| `clickgui2/FlatRenderer.java`、`FlatUiRenderer.java`、`theme/FlatTheme.java` | 实心面板、控件、圆角与阴影绘制 |
| `clickgui2/Window.java`、`SettingsWindow.java`、`SettingTreeLayout.java` | 浮窗设置稳定绑定与缩进布局 |
| `hacks/RadarHack.java` + `clickgui2/components/RadarComponent.java` | 雷达快照、范围/旋转设置与固定窗口 |
| `gui/title/WurstTitleMenu.java` | 原版全景上的响应式实心深色主菜单 |
| `mixin/ClientConnectionMixin.java` + `util/ClientConnectionPolicy.java` | 仅在客户端 `CLIENTBOUND` 连接派发收发包事件 |

### 设置

| 文件 | 当前职责 |
| --- | --- |
| `settings/Setting.java` | 任意深度父子树、单一归属和循环检查 |
| `clickgui2/Component.java`、`components/CheckboxComponent.java` | indent 属性与展开/折叠箭头 |

## 资源索引

| 路径 | 说明 |
| --- | --- |
| `src/main/resources/META-INF/mods.toml` | Forge 模组元数据，模组 ID 为 `wurstpenguin` |
| `src/main/resources/wurst.mixins.json` | 74 个客户端 Mixin 和 `wurstpenguin-refmap.json` 配置 |
| `src/main/resources/mixins.baritone.json` | Baritone Mixin 配置 |
| `src/main/resources/META-INF/accesstransformer.cfg` | Forge SRG 访问转换 |
| `src/main/resources/assets/wurst/` | 兼容原资源标识的图像和图标命名空间 |
| `src/main/resources/assets/wurst/skiko/` | Skiko 原生库（`skiko-windows-x64.dll`、`icudtl.dat`），运行时解压到 gameDir |
| `src/main/resources/assets/wurst/shaders/core/` | 液态玻璃顶点、片元和 shader 描述资源 |
| `src/main/resources/assets/wurst/shaders/program/` | LSD 视觉功能使用的 PostPass shader |
| `src/main/resources/assets/wurst/textures/gui/netease/` | 网易云音乐 UI 图标（v1.6） |
| `src/main/resources/assets/wurst/translations/` | 16 个语言 JSON、WurstCN 原始名称表和中文功能名资源 |
| `src/main/resources/assets/minecraft/font/` | CozyUI+ 位图字体定义 |
| `src/main/resources/assets/minecraft/textures/font/` | 字体图集 |
| `src/main/resources/META-INF/licenses/cozyui/` | CozyUI 归属说明及许可证 |

## 移植优先级

核心模块替换顺序固定为：LiquidBounce（水影）优先；Aristois 与 FrogClient 同级补充；前三者缺失或实现不适配时才使用 Meteor。GPL/MIT 源码保留来源与许可证声明；无许可证反编译源码只核对行为，不直接复制。战斗模块共享目标追踪、评分和库存动作队列；移动模块共享 `MovementPlanner`，NoFall 使用移动包 accessor，NoSlowdown 通过 Entity/Block Mixin 处理卡滞与速度因子。全部接入层均按 Forge 1.20.1 Mojmap 重写。

1. **已完成**：`RenderScope` 为每个 GUI/3D 监听器隔离 GL、FBO 和共享 BufferBuilder 状态。
2. **已完成**：PlayerESP 增加可选 2D AABB 投影、生命条和护甲耐久条。
3. **已完成**：TPS、速度、服务器、时间与坐标换算均为可独立拖动的 HUD2 元素。
4. **已完成**：每 Tick 不可变实体快照供 PlayerESP、MobESP、ItemESP 使用。
5. **已完成**：`TargetShader` 使用原生 Forge/Mojmap FBO 与 PostChain，不依赖 Satin。
6. **已完成**：v1.6 引入 87 个文件的 GUI/音乐/Skia 子系统（仅根工程）。
7. **待办**：将 v1.6 子系统移植到其余 14 个平台工程。
8. **排除**：Satin/Sodium/Fabric 依赖、玻璃 GUI 模糊、无限自旋后台线程和反编译代码直接复制。

## Forge 平台边界

- `WurstForgeInitializer` 使用 `FMLClientSetupEvent` 初始化，通过 `RegisterClientCommandsEvent` 注册客户端 Brigadier 命令，由 `RenderGuiEvent.Post` 单路派发 HUD；攻击监听由 `ClientPlayerInteractionManagerMixin` 在攻击包发送前派发，`IngameHudMixin` 仅保留遮罩控制。
- 全部活动 Java 文件使用 Mojang 官方映射。
- `PlatformUtils` 直接调用 Forge `ModList` 和 `FMLLoader`。
- Fabric 入口、Fabric E2E 测试、Indigo Mixin、Fabric 元数据和 Access Widener 已从根工程删除。
- `EventManager` 使用变更时监听器快照与 `LongAdder` 计数，LambdaMetafactory 注解路径消除反射。
- `JsonUtils.toJson` 与 `Encryption.saveEncryptedFile` 使用 `ATOMIC_MOVE` 原子文件替换。
- 74 个 Mixin 目标由 MixinGradle 生成 `wurstpenguin-refmap.json`。
- 原 Wurst 遥测、官网入口和远程更新检查已从活动路径移除。
- 原 `net.wurstclient.clickgui` 与 `net.wurstclient.navigator` 源码包已经删除，新 GUI 均位于 `net.wurstclient.clickgui2`。

## 构建与验证状态（根目录 Forge 1.20.1）

项目版本为 `1.6.0`，Minecraft 1.20.1，Forge 47.4.10，Java 17，Gradle 8.11。

- `gradlew.bat compileJava` 通过。
- `gradlew.bat test` 通过：**112 个测试类、545 项测试、0 失败**（含周界挖掘的区域几何、游标遍历顺序、进度/ETA、列式区域、边界检测、液体策略、批次/背包策略、状态机、中英文本一致性，直接扫描源码的「文案必须双语」检查，以及种子矿透的抽样契约、预测确定性与种子存储、结构区域扫描与频率削减速率、LCG 与原版逐位一致性、种子反解闭环、无范围反解还原、结构扫描器）。
- 发布产物 `build/libs/WurstB+ Plus-v1.6.0-Forge-1.20.1.jar`（68.1 MB）含全部 v1.6 子系统、`assets/wurst/skiko/` 原生库及 19 个内嵌 jarJar 依赖。

其余 14 个平台工程在 v1.5.0 状态下各自包含 `build/libs/` 产物；其构建与启动验证结果见 [PORTING_TASK.md](PORTING_TASK.md) 与 `docs/PORTING-1.21.11-26.2.md`（记录 1.21.11 / 26.2 六工程的构建、进世界与 Baritone `#goto` 冒烟结果）。

> **Gradle wrapper 说明**：15 个工程现均含完整 `gradle-wrapper.jar`（43,764 bytes，含 `Main-Class`）。
> 四种发行版（8.11 / 8.14.4 / 9.4.1 / 9.6.0）已预置到 `~/.gradle/wrapper/dists/`，
> `gradlew.bat` 在离线环境下可直接使用；本工作区 `tools/` 亦备有对应 zip 与解压目录。
