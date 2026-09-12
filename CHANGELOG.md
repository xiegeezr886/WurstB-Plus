# WurstB+ Plus 变更记录

WurstB+ Plus 是由 Penguin 开发的 Wurst 增强客户端。当前发布矩阵为 **5 个 MC 版本（1.20.1 / 1.21.1 / 1.21.11 / 26.1.2 / 26.2）× Forge / NeoForge / Fabric = 15 个独立工程**。

> **版本分布（重要）**：只有根目录 Forge 1.20.1 工程推进到 **v1.6.0**；
> 其余 14 个平台工程仍为 **v1.5.0**，v1.6 的 GUI/音乐/Skia 子系统尚未移植。

---

## v1.6.0 - 根目录 Forge 1.20.1（仅此工程）

### 新增子系统（87 个 Java 文件，14 个平台工程中不存在）

| 包 | 文件数 | 说明 |
| --- | ---: | --- |
| `clickgui2/component` | 27 | VAPE 风格组件层：`VapeClickGuiScreen`、`SuperSoftClickGuiScreen` / `SuperSoftRowsWindow` / `SuperSoftSettingsWindow`、`ModuleCardComponent`、`InlineSettingComponents`、`CategoryPanelComponent`、`VapeTextInputComponent` 等 |
| `music` + `music/apple` | 17 | 网易云音乐：`NeteaseCloudApi`、`NeteaseMusicPlayer`、`MusicAccountManager`、`LyricParser`；`apple/` 提供逐字歌词动画（`AppleLyricPlayer`、`AppleTimeline`、`AppleLayout`、`LyricWordSplitter`、`Spring`） |
| `clickgui2/music` | 13 | 音乐 GUI 页面：`HomePage`、`SearchPage`、`LikedPage`、`PlaylistDetailPage`、`LoginPage`、`PlayerDetailOverlay`、`BottomPlayerBar`、`StarRiverBackground`、`MusicRegion`、`NeteaseImageCache`、`CoverParticleSystem`、`CoverRippleSystem`、`MusicContext` |
| `compose` | 11 | 声明式 UI 布局树：`UiNode` / `UiRow` / `UiColumn` / `UiBox` / `UiText` / `UiSpacer`，配合 `AnimFloat`、`FlowingGradient`、`ModuleColors` |
| `clickgui2/epsilon` | 5 | Epsilon 风格下拉式 GUI：`EpsilonDropdownScreen`、`EpsilonDropdownPanel`、`EpsilonDropdownTheme`、`EpsilonModuleButton`、`EpsilonCategoryPanel` |
| `clickgui2/supersoft` | 5 | `EpsilonMd3Theme`（Material Design 3 TonalSpot 暗色调色板）、`SuperSoftTheme`、`SuperSoftRenderer`、`UiMotion`、`UiTween` |
| `render/skia` | 4 | Skiko 矢量渲染：`SkikoNatives`（解压并加载原生库）、`SkiaGlBackend`、`SkiaFontManager`、`SkiaRegionRenderer` |
| `gui/visual` | 3 | `VisualTheme`（语义色 token）、`VisualRenderer`、`VisualScreenMotion` |
| `hud2/render` | 2 | `RiseFrostedGlass`（磨砂玻璃）、`RiseHudFont` |

### 新增 Hack（11 个）

`AirJump`、`EntityCulling`、`MusicPlayer`、`NoMissCooldown`、`NoRotate`、`ProjectilePuncher`、`ReverseStep`、`RightClicker`、`SuperKnockback`、`VehicleBoost`、`WTap`。

`MusicPlayer`（OTHER 分类）打开 `NeteaseMusicScreen`，自身带 `@DontSaveState` / `@DontBlock`。

### 新增依赖与打包

- `org.jetbrains.skiko:skiko-awt:0.8.19`（jarJar）+ `kotlin-stdlib` + `kotlinx-coroutines-core-jvm`
- 音乐播放链：`java-stream-player`、`mp3spi`、`jlayer`、`jflac-codec`、`vorbis-support`、`tritonus-all`、`jorbis`、`jaudiotagger`
- Skiko 原生库（`skiko-windows-x64.dll` 16.5 MB、`icudtl.dat` 10.0 MB）**不 jarJar**——jarJar 会重定位资源路径导致 Skiko 无法在 jar 内定位原生库。改为随 mod 资源打包到 `assets/wurst/skiko/`，运行时由 `SkikoNatives` 解压到 gameDir，并通过 `skiko.library.path` / `skiko.data.path` 系统属性显式加载。
- 根工程 jarJar 共内嵌 19 个依赖 jar；产物体积由 v1.5 的约 29 MB 增至 **68.1 MB**（主要来自 Skiko 原生库）。

### 验证状态

- 根工程 `test` 通过：94 个测试类、352 项、0 失败（含 ClickGUI 三布局切换、AMLL 歌词优化流水线/视觉公式/遮罩几何/缓动/强调动画/过渡/断行平衡/掩码、YRC/翻译/音译/背景人声、间奏三点、网易云 JSON 安全解析）。
- 产物：`build/libs/WurstB+ Plus-v1.6.0-Forge-1.20.1.jar`（约 68 MB）。
- 仅验证构建与单元测试；v1.6 新子系统（GUI / 音乐 / Skia）**未经游戏内运行验证**。
- 开发工具：`scripts/doctor.ps1`、`scripts/run-unit-tests.ps1`、`scripts/seed-gradle-wrapper.ps1`；`build-all.ps1` 根工程产物已对齐 v1.6.0。

### 已知问题（本轮已修复）

- `VisualThemeTest` 的两个断言仍指向 v1.5 时期的强调色 `#4677FF`，与统一后的 `#007CFF` 不符，导致 `test` 任务失败。已对齐断言与 `VisualTheme.ACCENT` / `EpsilonMd3Theme.PRIMARY`。

### 本轮补齐

- ClickGUI 入口按 `ClickGuiStyle` 在 **Epsilon / SuperSoft / Vape** 三套布局间切换；`SuperSoftClickGuiScreen` 现在可从主面板与 ClickGUI 设置打开。旧 `vapeMode` JSON 仍映射为 Vape。
- `NotificationSeverity.INFO` 已对齐统一强调色 `#007CFF`。
- 网易云歌词按 GitHub [applemusic-like-lyrics](https://github.com/Steve-xmh/applemusic-like-lyrics) `packages/core` / `packages/lyric` 移植：YRC 解析（含背景人声括号）、翻译行、AMLL 间奏三点（入场/呼吸/点亮/回弹）、长词强调辉光与上浮。原仓库是 TypeScript DOM，Minecraft 侧用 Java + Skia/GuiGraphics 实现同等视觉。

### 歌词视觉一比一复原（对齐 AMLL 源码）

按 `packages/core` 源码逐条复刻视觉公式，替换此前凭观感调出的近似值：

- **透明度**（`resolveOpacity`）：高亮行 **0.85**（原先误用 1），逐行歌词未高亮 **0.2**，逐字歌词未高亮 **1**（不靠透明度压暗，改由遮罩的 0.2 均匀 alpha 与模糊表达层次）；`hidePassedLines` 用 1e-4。
- **模糊**（`resolveBlurLevel`）：焦点之后 `1 + |i − latest|`、焦点之前额外 +1，窄视口（≤1024px）×0.8，上限 5px；Skia 路径用真实 `ImageFilter.makeBlur`，MC 字体路径用按 σ 采样的高斯重绘近似。
- **缩放**（`group.ts`）：主歌词统一 1em 字号，非激活只经弹簧缩放到 **97%**；背景人声 **75%**、CSS 透明度 0.4、`backgroundSlideY` 弹簧从 ±80 滑入。
- **字号体系**：取消原先「激活 20 / 非激活 16」的双字号，改为 AMLL 的单一基准字号（默认 24，即 1em），翻译行 `max(0.5em, 10px)` / 透明度 0.3，背景人声 `max(0.7em, 10px)`。
- **卡拉 OK 遮罩**：未唱部分按 `--dark-mask-alpha: 0.4` 压暗，交界处渐变带宽 = 字高 × 0.5。Skia 路径改为**单次渐变绘制**（alpha 由 1 过渡到 0.4），此前分两次绘制会让暗部叠两次而偏亮。
- **遮罩模型修正与 CSS 过渡**（`lyric-player.module.css`）：`AmlVisual` 补齐 `--bright-mask-alpha` / `--dark-mask-alpha` 的真实取值——**非激活的逐字行只有 0.2 的均匀遮罩**（SOLID 模式亮暗同值），因此 `opacity(1) × 0.2 = 0.2`，此前直接按 1.0 绘制会**亮 5 倍**；激活行取 1.0 / 0.4。新增 `AmlTween` 模拟 CSS `transition`（遮罩 alpha 激活 0.3s、退回 0.45s 且 `ease-out`，`opacity` / `filter` 0.4s `ease`），并在 rebuild-view / 配置变更时按 AMLL 的 `tmpDisableTransition` 语义瞬移。
- **MC 字体路径的真实高斯模糊**（取代此前的字重 + 透明度近似）：按 σ 生成归一化二维高斯采样表（权重阈值 0.02、上限 16 个采样点、按 σ 量化缓存），逐采样重绘字形；σ < 0.5px 时退化为单次绘制。逐字行的模糊与遮罩渐变不会叠加——AMLL 中激活行 `resolveBlurLevel` 恒为 0。可用 `setMcBlurEnabled(false)` 关闭。
- **MC 字体路径的精确渐变遮罩**：改为**三段互不重叠**的裁切带（渐变带前亮部、渐变带内按 1px 一档插值、渐变带后暗部），取消原先「先整词压暗再叠画亮部」的双重合成；顺带修掉 Minecraft `Font.drawInBatch` 在 `alpha < 4` 时强制不透明的坑——近乎隐形的行（如 `hidePassedLines`）过去会整行变实。几何也一并纠正：AMLL 的遮罩是「两端带渐变的遮罩图 + 平移」，`generateFadeGradient` 缩放后亮部停靠点落在元素右缘 `C`、暗部停靠点在 `C + fadeWidth`，遮罩位置从 `−(C + fadeWidth)` 线性走到 `0`，因此**渐变整段位于亮部边界右侧、并不居中于交界处**（只有 `p = 0.5` 时两者重合，两端各差 `fadeWidth / 2`）。该几何抽成 `AmlVisual.maskEdges` / `maskAlphaAt`，MC 与 Skia 两条路径共用并有单元测试锁定。强调词的遮罩与辉光同样改为「先按变换前尺寸定遮罩、再整体缩放」。
- **断行平衡**（`utils/lyric-line-break.ts`）：新增 `AmlLineBalancer`，用动态规划挑选换行点——超宽惩罚 ×1000、标点后换行奖励 0.6、空格后 0.4、CJK 边界 0.15、普通 0.5，避免末行只剩一两个字。逐字与非逐字行都走该路径，单个词就超宽时退化回贪心换行。
- **音译行**：`LyricLine` 新增 `romanLyric`，`LyricParser.parseBest` 增加第四个参数并新增 `attachRomanizations`（±800ms 配对），`NeteaseCloudApi` 转发网易云 `romalrc`；渲染顺序与 AMLL 一致——翻译行在前、音译行在后，透明度均为 0.3。
- **注音与不雅词掩码**：新增 `LyricRuby` 与 `AmlMask`（`DISABLED` / `FULL` / `PARTIAL`，`PARTIAL` 保留首尾字符，词长 ≤ 2 退回整词掩码，空白不受影响，行文本与词文本同步）。注音绘制于词上方居中、字号 0.5em 并计入行高。**注意**：网易云接口不提供注音与不雅词标记，这两条路径当前无数据源，属于为完整性预先移植、已被单元测试覆盖的能力。
- **强调动画**（`lyric-line.ts` `initEmphasizeAnimation`）：逐字绕字心缩放 `1 + transX×0.1×amount`、横向让位 `−transX×0.03×amount×(n/2−i)`、纵向抬升 `−transX×0.025×amount`、白色辉光 `rgba(255,255,255,transX×blur)`；`amount`/`blur` 由词时长经 `sqrt`/立方分段后取 0.6 / 0.5 系数并封顶 1.2 / 0.8，行末词放大 1.6 / 1.5 倍且时长 ×1.2。缓动为 `empEasing` 脉冲（`cubic-bezier(0.2,0.4,0.58,1)` 与 `(0.3,0,0.58,1)` 镜像，中点峰值 1）。
- **上浮动画**：由原先的 `sin(π·p)` 改为 AMLL 的 CSS `ease-out` 单调上升至 `−0.05em`（背景人声 −0.1em），时长 `max(1000ms, 词时长)`、延迟取词相对行的偏移；强调词再叠加一次正弦附加上浮。
- **排版策略表**（`LayoutReasonStrategyMap`）：`AmlLayoutReason` 完整移植九个场景的 `disableStagger` / `resetInterlude` / `snapPosY`。要点是**只有连续拖动才瞬移**——载入歌词（rebuild-view）刻意保留弹簧，新歌词行 `posY` 初值设在容器高两倍处，实现 AMLL 的「从下方飞入」。
- **歌词优化流水线**（`optimize-lyric.ts`）：`AmlOptimize` 移植 7 步——空格规范化、行时间戳回填字级、连续背景人声降级、主/背景时间同步（取并集）、按主歌词起始时间稳定排序、无逐字时间戳的行按 `parseLrc` 补齐结束时间、清洗非刻意重叠（≥500ms 有意；≤100ms 或 ≤下一行 10% 截断）、尝试提前开始时间（间隔 600ms / 重叠 400ms / 不足则 70%）。因提前开始会改动起始时间，`lineTime()` 单独保留解析器给出的原始时间用于拖动进度条。
- **间奏点**：尺寸改用 CSS `clamp(0.5em, 1vh, 3em)`，左右内边距 0.75em、上下外边距 0.4em（原先为固定 4.5px 半径）。
- 新增 `AmlEasing`、`AmlVisual`、`AmlEmphasize`、`AmlOptimize`、`AmlLayoutReason`、`AmlTween`、`AmlLineBalancer`、`AmlMask` 八个可独立测试的类，视觉常量不再散落在渲染代码里。
- 源码包总数 94 → **98** 个 Java 文件，其中 `music` + `music/apple` 由 24 → **28**。

### 待办

- 将 v1.6 子系统移植到其余 14 个平台工程（1.21.1 / 1.21.11 / 26.1.2 / 26.2 × Forge/NeoForge/Fabric）。

---

## v1.5.0 - 全平台适配与稳定性修复 (2026-08-07)

**NeoForge 1.21.1 启动即崩溃**

- 问题：启动时 `IllegalAccessError: class baritone.api.Settings cannot access class net.minecraft.world.item.Item (in module minecraft)`
- 根因：Baritone 以 jar-in-jar 库形式打包，NeoForge 21.1.x 的 JPMS 模块层将其隔离为独立模块 `baritone.api.forge`，该模块没有对 `minecraft` 模块的 read 边
- 修复：将 Baritone 类直接合并进 `wurstpenguin` 主模块，类归属主模块后即可访问 `minecraft`，不再作为独立库模块加载

**Forge 26.1.2 启动崩溃（JPMS 模块包冲突）**

- 问题：模块解析失败 `Module WurstB.Plus... contains package io.netty.util.concurrent, module io.netty.common exports`
- 根因：旧发布包把整个 netty 传递链（common/buffer/transport 等）扁平合并进主 jar，与游戏自带 netty 4.2.7 模块导出同一包
- 修复：仅合并不与其他模块冲突的 netty-codec-socks / netty-handler-proxy 自身包，依赖版本对齐游戏自带 netty 4.2.7；产物体积由 120MB 降至 32MB

**Forge 26.1.2 依赖声明错误**

- 问题：启动报 `Mod wurstpenguin requires neoforge` 无法加载
- 根因：旧发布包 mods.toml 错误声明依赖 `neoforge [26.1.2,27)`，Forge 64.1.0 环境下不存在该 mod
- 修复：依赖改为 `forge [64.1.0,65)`

**Forge 26.1.2 Baritone 注册失效**

- 问题：baritone-forge-1.18.0.jar 已嵌入但未被加载
- 根因：构建产物丢失 `META-INF/jarjar/metadata.json`（嵌套 jar 的注册清单）
- 修复：恢复 metadata.json，确认 Baritone 嵌套注册完整

**Forge/NeoForge 26.1.2 发布包资源丢失**

- 问题：发布包缺少全部 mod 资源（字体、翻译、shader、图标），Baritone 下界寻路类缺失
- 根因：打包任务沿用了为合并整个 runtimeClasspath 设计的 `exclude 'assets/**'`，把 mod 自身资源一并排除
- 修复：清理打包任务，仅合并 Baritone 类，恢复全部资源与 NetherPathfinder 类

**Fabric 26.1.2 启动崩溃（Mixin 注入失败）**

- 问题：StatusEffectInstanceMixin、ClientPlayerEntityMixin 注入失败导致启动崩溃
- 根因：MC 26.1.2 重命名了 `tickDownDuration()`（→ `mapDuration(Int2IntFunction)`）与 `hasEnoughFoodToStartSprinting()`（→ `canStartSprinting()`），旧注入目标不存在；refmap 声明指向不存在的文件（非混淆环境无需 refmap）
- 修复：更新注入目标，移除 refmap 声明，并将 mixin 清单对齐已验证的 65 项

**Fabric 1.21.1 HUD 渲染顺序错误（仅 Fabric）**

- 问题：打开半透明 ClickGUI 时，HUD 文字（TabGui、HackList 等）渲染在 GUI 下层，透过透明背景可见
- 根因：1.21.1+ 的 HUD 事件改由 `IngameHudMixin` 在 `Gui.renderTabList` HEAD 注入触发，屏幕打开时仍会派发；1.20.1 的 `HudRenderCallback` 在屏幕打开时不触发，故无此问题
- 修复：`renderTabList` 注入点增加 `WurstClient.MC.screen != null` 守卫，屏幕打开时跳过 HUD 渲染，与 1.20.1 行为一致；已同步应用至全部 6 个 1.21.1/26.1.2 工程

**模组图标更新**

- 替换全部 9 个工程的 `assets/wurst/icon.png` 为新版 Logo（等比缩放居中，400×400 透明背景）

**启动早期崩溃防护（全平台）**

- 问题：第三方整合包 / 移动端环境下，WurstClient 初始化前触发 mixin 导致空指针崩溃
- 根因：TextVisitFactoryMixin、LanguageManagerMixin、MinecraftClientMixin、TelemetryManagerMixin 直接访问 `getHax()`/`getTranslator()`/`getOtfs()` 未判空
- 修复：全部增加 null 防护；WurstMixinConfigPlugin 改用类资源存在性检查，避免提前触发类加载（修复 Supplementaries × Embeddium 的 MixinTargetAlreadyLoadedException）

### 构建链

- mixinextras 0.4.1 → 0.5.4，jarJar 版本范围放宽，解决整合包 mixinextras 版本冲突
- Forge 依赖范围 `[47.4.10,48)` → `[47,48)`，兼容 Forge 47.4.0 及更高版本
- 26.1.2 系列内嵌 baritone-forge/neoforge-1.18.0（jar-jar），Baritone 由 `baritone-maven/` 本地仓库提供
- 新增 scripts/build-all.ps1 一键构建 9 个产物；scripts/run-version-tests.ps1 在 PCL 测试环境批量真实启动验证（9/9 通过）

### 文件校验

```
WurstB+ Plus-v1.5.0-Forge-1.20.1.jar: CFC5EF862A0D822E20895AA69D952EB809D253D80CC89B2CB27172A5D2CDB9C0
WurstB+ Plus-v1.5.0-Forge-1.21.1.jar: A78BFEFA7BD7B4220EB7017827742CA477B971450CA08362D4C66C7BD32F00C8
WurstB+ Plus-v1.5.0-Forge-26.1.2.jar: 489DC385B0389AFF604B276829821E354F4FBD1EDBB384EA29708C3B4A27D5A2
WurstB+ Plus-v1.5.0-NeoForge-1.20.1.jar: 0C05A07ED15B5E4B84259D592777A00231A6C8639027E2C0E50C0E91E88ACB4D
WurstB+ Plus-v1.5.0-NeoForge-1.21.1.jar: 87622A2F7BC0692377A58B87D3B1787AC13CC12E7D87D872EB8DBD70E7452343
WurstB+ Plus-v1.5.0-NeoForge-26.1.2.jar: 5B772ED2791B2A1FE3315BF2F032BFFCC5F48B36F6D1EF306F10A85744C70BB3
WurstB+ Plus-1.5.0-Fabric-1.20.1.jar: 5382A8066844B38CE868590714F2B5F1A547DEAFFFD290934CAA3C32340F3727
WurstB+ Plus-1.5.0-Fabric-1.21.1.jar: 0E3838806FC21AC158CB3EB1550AE143F10D6066F84ADE7F73A69134ECCC110B
WurstB+ Plus-1.5.0-Fabric-26.1.2.jar: 8A2B04A5994B585BC3B8B7204DBA45BA3B5B1A6A5078CD8F10CA6F33E26865E0
```
