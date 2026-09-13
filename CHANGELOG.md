# WurstB+ Plus 变更记录

WurstB+ Plus 是由 Penguin 开发的 Wurst 增强客户端。当前发布矩阵为 **5 个 MC 版本（1.20.1 / 1.21.1 / 1.21.11 / 26.1.2 / 26.2）× Forge / NeoForge / Fabric = 15 个独立工程**。

> **版本分布（重要）**：只有根目录 Forge 1.20.1 工程推进到 **v1.6.0**；
> 其余 14 个平台工程仍为 **v1.5.0**，v1.6 的 GUI/音乐/Skia 子系统尚未移植。

---

## v1.6.0 - 根目录 Forge 1.20.1（仅此工程）

### 新增子系统（152 个 Java 文件，14 个平台工程中不存在）

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
| `perimeter` | 35 | 周界挖掘全套：区域模型与游标、液体策略与边界封闭、方块限制与批次、导航 / 交互 / 装备 / 补给策略、28 状态自动化编排、双语文本 |
| `perimeter.config` | 11 | 按服务器 / 存档分存的配置模型、迁移与原子写盘（`PerimeterConfigStore`） |
| `perimeter.detect` | 2 | 不规则区域边界检测（`BoundaryDetector` / `PerimeterGrid`） |
| `seed` | 8 | 种子矿透核心：种子存储、纯 Java 矿物预测（含字节码对齐的抽样数学）、Baritone 目标挖掘作业 |
| `seed.structure` | 3 | 结构定位：调用原版公开放置方法、频率削减、群系校验 |
| `seed.search` | 4 | 种子反解：结构观测、零分配 LCG 候选搜索、后台任务与自检 |
| `seed.crack` | 2 | 无范围反解：位块分解 + 陪集求交的精确求解器 |
| `seed.scan` | 1 | 自动观测：方块特征识别 6 类结构 |

### 新增 Hack（14 个）

`AirJump`、`EntityCulling`、`MusicPlayer`、`NoMissCooldown`、`NoRotate`、`PerimeterDigger`、`ProjectilePuncher`、`ReverseStep`、`RightClicker`、`SeedOreESP`、`SeedStructureESP`、`SuperKnockback`、`VehicleBoost`、`WTap`。

`MusicPlayer`（OTHER 分类）打开 `NeteaseMusicScreen`，自身带 `@DontSaveState` / `@DontBlock`。

### 种子矿透（SeedOreESP，新增 10 个文件）

`net.wurstclient.seed`（8 个文件）+ `hacks/SeedOreEspHack` + `commands/SeedCmd`，**零新增依赖、不注入 Baritone 内部类**。

- **种子存储**（`SeedStore` / `SeedEntry`）：按 `server:<ip>` / `singleplayer:<path>` / `local:<dim>` 身份分存；单人模式直接读取集成服务器种子；`parseSeed` 支持纯数字，其他文本取 `hashCode`。
- **矿物预测**（`OrePredictor` / `OreRules` / `OreRule` / `OreCount` / `OreHeight`）：纯 Java 复刻原版矿物生成数学——`WorldgenRandom`（**Xoroshiro**，与原版 `applyBiomeDecoration` 一致）→ `setDecorationSeed` → 逐条 `setFeatureSeed(populationSeed, index, step)` → `count.sample` / rarity / `height_range` / 矿脉椭圆与 `discardOnAirChance`。规则表覆盖主世界 20 条、下界 10 条 placed_feature。
- **抽样数学逐条对齐字节码**：`UniformInt` / `ConstantInt` / `UniformHeight` / `TrapezoidHeight`（含「先 `nextInt(0, large)`、再 `nextInt(0, small)`」的顺序）/ `VerticalAnchor`（absolute / above_bottom / below_top）与 `Mth.nextInt` 均按 1.20.1 字节码核对后重写为纯值类型，`OreSamplingTest` 用记录型随机源把边界与顺序钉死。之所以不直接用原版 provider：`IntProvider` / `ChunkGenerator` 的静态 codec 会拉起原版 bootstrap，而 Forge 的 `Bootstrap.bootStrap()` 在单元测试环境会卡在 `NetworkHooks.init`。
- **ESP 与自动挖掘**（`SeedOreEspHack` / `SeedMineJob`）：按区块半径预测并缓存（每 tick 限量），`RenderListener` + `ESP_QUADS` 渲染；`Auto Mine` 通过官方 Baritone 公开 API（`ICustomGoalProcess#setGoalAndPath(new GoalComposite(GoalBlock...))`）按距离分批下发目标，把「目标方块变成空气」视为已挖到，支持暂停 / 恢复 / 取消。
- **命令**：`.seed` / `.seed get|set|clear|list|mine`。

**与参考实现的偏差（如实说明）**：① 不按生物群系过滤规则（`OreContext` 拿不到 biome 源），预测结果是原版超集，非该矿生物群系可能出现假阳性；② 高度图以「自上而下第一个非空气方块」近似原版 `OCEAN_FLOOR_WG`，只影响「矿脉是否位于地表之上」的判定，不消耗随机数；③ 方块判定用 `isAir` 而非 `canOcclude`，玻璃等非遮挡方块略有差异；④ 未实现种子反推（需 seedfinding/latticg，探测到的坐标 404）与结构 / Cubiomes 定位；⑤ **未在真实世界生成中逐格校验预测坐标**，单测只能验证确定性、区间与抽样契约。


- `org.jetbrains.skiko:skiko-awt:0.8.19`（jarJar）+ `kotlin-stdlib` + `kotlinx-coroutines-core-jvm`
- 音乐播放链：`java-stream-player`、`mp3spi`、`jlayer`、`jflac-codec`、`vorbis-support`、`tritonus-all`、`jorbis`、`jaudiotagger`
- Skiko 原生库（`skiko-windows-x64.dll` 16.5 MB、`icudtl.dat` 10.0 MB）**不 jarJar**——jarJar 会重定位资源路径导致 Skiko 无法在 jar 内定位原生库。改为随 mod 资源打包到 `assets/wurst/skiko/`，运行时由 `SkikoNatives` 解压到 gameDir，并通过 `skiko.library.path` / `skiko.data.path` 系统属性显式加载。
- 根工程 jarJar 共内嵌 19 个依赖 jar；产物体积由 v1.5 的约 29 MB 增至 **68.1 MB**（主要来自 Skiko 原生库）。

### 结构定位与结构 ESP（SeedStructureESP，新增 4 个文件）

`net.wurstclient.seed.structure`（3 个文件）+ `hacks/SeedStructureEspHack`，**零新增依赖**，与矿透共用同一份种子存储。

- **放置数学不重写**：1.20.1 的 `RandomSpreadStructurePlacement#getPotentialStructureChunk(long,int,int)` 是 public，因此直接调用原版方法（`StructureFinder`）——预测与原版**构造上一致**，数据包 / 模组新增的结构集自动生效。1.20.1 原版共 **19 个结构集：18 个 `random_spread` + 1 个 `concentric_rings`（要塞）**，后者是另一套算法，已明确跳过。
- **频率削减已实现**（`PlacementFrequency`）：`buried_treasures`（spacing=1, frequency=0.01）、`mineshafts`（0.004）、`pillager_outposts`（0.2）靠 `frequency`/`frequency_reducer` 控稀有度，不实现会让它们在每个区块命中。这两个字段是 protected 且无公开读取口，故通过**放置编解码器导出为 JSON**（即数据包 schema）读取，再按 1.20.1 字节码复刻四种削减器的判定——含 `legacy_type_1` 的 `regionX ^ regionZ << 4` 分组，以及 DEFAULT 变体把 salt 传在 chunkX 位置这一原版细节；随机数仍由原版 `WorldgenRandom` 产生。
- **群系校验（仅已加载区块）**：候选区块若已被客户端加载，则用 `Structure#biomes()` 与 `Level#getBiome` 核对；未加载区块无法校验，如实标注 `biome unknown`。
- **命令**：`.seed structures [半径]` 输出候选列表（结构集、区块、方块坐标、距离、群系状态）；`.seed structesp` 开关结构 ESP（已加载区块地表三点标记，青色）。
- **未实现 / 已知局限**：① `exclusion_zone` 被忽略（原版仅 `pillager_outposts` 用它排除村庄），结果为超集；② 要塞（同心环）跳过；③ 未实现种子反推——依赖 `maven.seedfinding.com` / `maven.latticg.com` 的 `mc_*` 与 `latticg`，实测这些地址 301 到 `nexus.seedfinding.com` 后制品 **404**，Maven Central 亦无，只能自研格基归约，本轮未做；④ **未在真实世界生成中逐格校验**，只验证了编译与单测（区域扫描、频率削减速率与分组性质）。

### 种子反解（结构观测 → 种子，新增 4 个文件）

`net.wurstclient.seed.search`（4 个文件），**零新增依赖**，把「正向结构定位」变成可用的**反解工具**。

- **能力边界（如实说明）**：这不是 Seedcracker 那种「从零反推」，而是**候选确认式搜索**——玩家提供一个结构集 + 坐标作为观测，工具在给定种子范围内找出所有能复现该观测的种子。每个观测把种子约束到约 `2 * log2(spacing - separation)` 比特；同集合的多个观测**并不独立**（同一水平种子加已知偏移派生），实测候选密度高于理论值，因此工具会返回多个候选，玩家用更多结构逐步收敛，而不是声称唯一解。
- **热循环零分配**：搜索每秒要评估上亿候选，若每候选构造一个 `WorldgenRandom` 太慢，故用 thread-local 的纯 LCG（`PlacementFrequency.Rng`：倍乘 25214903917、加数 11、48 位掩码、`nextInt` 的 2 的幂快速路径、`nextFloat`/`nextDouble` 的 2^-24 / 2^-53 定标）——`RngEquivalenceTest` 把这些与原版 `WorldgenRandom` **逐位对照**（含 `next(31)`/`nextInt(bound)`/`nextLong`/`nextFloat`/`nextDouble`、两种播种方法，以及 5000+ 组频率削减判定）全部一致。
- **运行时自检**：搜索开始前，`StructureFinder` 会用原版 `getPotentialStructureChunk` 对 32 个采样区块逐一比对快路径；一旦不一致就报告 `MISMATCH`，命令层**拒绝启动搜索**。
- **命令**：`.seed observe <结构集> <x> <z>`（记录观测，`observe list` / `observe clear`）、`.seed search <from> <to>`（后台线程搜索，每 20 tick 报进度）、`.seed search status` / `search cancel`；命中后直接给出 `.seed set <seed>` 命令与候选列表。
- **可自证的闭环**：`SeedSearchTest` 的观测值由**原版** `WorldgenRandom` + 原版偏移公式生成，再要求搜索找出来源种子——含 2/8 观测收敛性对比、多线程与单线程结果一致、进度计数、取消、不可用结构集（`buried_treasures` 间距 1）被排除、跨结构集组合等 12 例。
- **未实现 / 已知局限**：① 不做格基归约（LLL），因此**不能**在 2^48 全域内无范围反解，只能确认候选或搜索给定范围；② 观测需玩家手动提供（客户端拿不到服务端结构数据）；③ 未在真实存档中验证反解结果；④ `exclusion_zone` 与要塞（同心环）仍不参与。


- 根工程 `test` 通过：112 个测试类、545 项、0 失败（含种子矿透的抽样契约、预测确定性与种子存储、结构区域扫描与频率削减速率、LCG 与原版逐位一致性、种子反解闭环、格基替代解法的闭环还原、结构扫描器 28 例；含 ClickGUI 三布局切换、AMLL 歌词优化流水线/视觉公式/遮罩几何/缓动/强调动画/过渡/断行平衡/掩码、YRC/翻译/音译/背景人声、间奏三点、网易云 JSON 安全解析、周界挖掘的区域几何与进度/ETA）。
- 产物：`build/libs/WurstB+ Plus-v1.6.0-Forge-1.20.1.jar`（约 68 MB）。
- 仅验证构建与单元测试；v1.6 新子系统（GUI / 音乐 / Skia / 周界挖掘 / 种子矿透 / 结构定位）**未经游戏内运行验证**。
- 开发工具：`scripts/doctor.ps1`、`scripts/run-unit-tests.ps1`、`scripts/seed-gradle-wrapper.ps1`；`build-all.ps1` 根工程产物已对齐 v1.6.0。

### 无范围反解、热循环优化与自动观测（新增 3 个文件）

- **无范围反解（`seed.crack`，2 个文件）**：不做格基归约，而是**精确的位块分解 + 陪集求交**——把 LCG 状态拆成 `2^17·b + e` 与 `2^18·u + v`，使每条约束变成 13 位高位上的短等差数列（range 奇部）或区间（2 的幂 range 走原版 `nextInt` 快速路径），多约束求交即 `Z/2^13` 上的小 CRT；低 17 位用 `gcd(a·r1, r2, 2^31)` 的必要条件一次 bitmap 扫描压缩；每个候选**必须经原版 LCG 回放全部约束**才返回。`.seed crack [超时秒]` 在后台线程调用，客户端不卡。
  **实测边界（如实标注）**：支持 range 2..64；1~8 个观测（2~16 条约束）耗时 30 ms ~ 1.5 s；8 个偶部 range 观测（约 66 bit）**精确还原原种子**（约 0.2 s）；**只有奇数 range 的强约束集**（例如 7 个 ruined portal）没有可用 pin，会在超时后返回 `success=false` 并写明 "timed out … no seed found so far"，**不假装成功**；**弱约束集（≤4 观测）在数学上不唯一**（1 观测 2 约束约 4×10¹¹ 个解），此时契约是「返回的种子确实复现全部观测」，不保证等于原始种子。
- **热循环优化（`seed.search`）**：候选无关量全部预计算——`regionX/regionZ`、`wantX/wantZ`，以及 LCG 种子偏移 `base = regionX·341873128712 + regionZ·132897987541 + salt`（于是每个候选只需**一次加法**），`Target` 数组化去掉 `List.get`。**实测约 3800~4800 万候选/秒/线程**（2000 万候选约 0.4~0.5 s）：2³² 约 90 s 单线程 / 约 12 s（8 线程），2⁴⁰ 约 6.4 h / 约 48 min，**2⁴⁵ 以上不实用**。另修正报告口径：结果被 `maxResults` 截断时会明说「已达结果上限，范围未搜完」。
- **自动观测（`seed.scan`，1 个文件）**：`.seed observe scan [半径 1..8]` 扫描已加载区块的方块特征，认出 6 类结构并直接登记为观测（同结构集同区块自动去重）。判据均为「唯一来源/组合证据」：海底神殿（`sea_lantern≥2` 且棱镜石族 `≥16`）、远古城市（`reinforced_deepslate≥1` 或 `deepslate_bricks≥24` 且 `deepslate_tiles≥8`，强制 y<0）、古迹废墟（`mud_bricks≥8`）、沙漠神殿（`chiseled_sandstone≥2` 且 `chiseled+cut≥8` 且 `orange_terracotta≥4`）、冰屋（`snow_block≥16` 且内部方块≥2）、埋藏宝藏（沙滩/积雪沙滩/石岸群系中箱子**恰为 1**，群系未知则不报）。
  **已知风险（如实标注）**：阈值**未在真实存档标定**（漏报多时优先下调古城/古迹废墟阈值）；玩家自建（棱镜农场、深板岩砖房）会误报；跨区块切割与部分加载会漏报；沙滩区块里的地牢/搁浅沉船/海底废墟也含箱子，埋藏宝藏仍有残余误报。**冰屋判据修正**：1.20.1 冰屋模板实测为 94×`snow_block` + 白/浅灰地毯 + 红床 + 活板门等，**没有 `white_wool`**，故不以白羊毛为必要条件。
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

### 周界挖掘（v1.6 原生实现）

社区模组 [Perimeter Digger](https://github.com/HackerRouter/Perimeter-Digger) 是 **Fabric / MC 26.1.1 / Java 25** 工程，且强依赖 HackerRouter 的**修改版 Baritone**（自定义 XZ 区域挖掘 + 扩展 Elytra/寻路 API），因此无法以「内置第三方模组」的方式进入 Forge 1.20.1 的 v1.6 工程。本轮按同等用途**原生实现**了第一里程碑：

- **区域规划**：`.perimeter plan rectangle <x0> <z0> <x1> <z1> <minY> <maxY>`，XZ 两角与 Y 上下限**均为闭区间**，坐标支持 `~` 与 `~<偏移>`；两角顺序任意，内部归一化。
- **启停控制**：`.perimeter start | pause | resume | stop | clear | status`，可反复暂停/继续；`status` 输出状态、区域、已挖/总数、百分比、已用时间与 ETA。
- **矩形挖掘**：复用已验证过的 `ExcavatorHack`（`Area` 扫描 + `ExcavatorPathFinder` + `BlockBreaker`）按 Y 范围自顶向下开挖；计划区超过 800 万格时给出提示。
- **进度统计**：`PerimeterCursor` 按「顶层优先（Y 递减）→ Z 递增 → X 递增」零分配遍历，`PerimeterProgress` 负责总数/剩余/百分比/耗时/ETA；计数按每 tick 8192 格预算分摊，超大周界不会卡住客户端。
- **安全暂停**：背包无空位时自动暂停（自动卸货尚未实现）；挖掘器自行停止时最多自动重启 3 次，之后进入 `STALLED` 等待 `resume`。
- **液体**：沿用 `BlockUtils.canBeClicked`（按方块轮廓形状判定），流体没有可点击轮廓，所以**默认不会被挖掘**；封闭与替换液体尚未实现。
- **未实现（后续里程碑）**：不规则区域检测（`detect`）、液体封闭/替换、卸货点与背包管理、工具耐久与补给点、跨维度修复、鞘翅寻路与自动睡觉。
- 新增 `PerimeterDigger` Hack（Blocks 分类，`@DontSaveState`）与 `.perimeter` 命令；v1.6 源码包 98 → **134**，测试类 94 → **103**、用例 352 → **436**。

### 待办

- 将 v1.6 子系统移植到其余 14 个平台工程（1.21.1 / 1.21.11 / 26.1.2 / 26.2 × Forge/NeoForge/Fabric）。
- **完整移植（里程碑 2，等价全部移植）**：不规则区域检测、液体 avoid/replace/seal_boundary、自动拾取与卸货点、工具/鞘翅耐久替换、自动进食/补给/睡觉、跨维度 XP 炉修复、行走与鞘翅寻路、按服务器·存档分存配置、中英双语文本与 `/perimeterdig`（含 Tab 补全）。
  - **区域**：`/perimeterdig area detect <block> <y>` 用与原模组同源的扫描算法（8 邻接连通分量 + 奇偶遍历 + 外边界判定）识别任意形状边界，输出逐 Z 扫描线；`PerimeterRegion` / `PerimeterColumnArea` 支持游程合并与二分查找。
  - **液体**：`PerimeterMiningSchematic` 逐格复刻原模组 `AreaMiningSchematic` 的判定（区域内 / 边界外沿 / `touchesSideOrTopBoundary`），`AVOID` 额外跳过与流体相邻的方块；封堵方块取配置列表中第一个可放置者。
  - **背包与批次**：`PerimeterMiningJob` 统计区域内「实心变空气」的方块、按空槽位计算批次上限、达上限自动暂停并记录原因；`PerimeterInventoryPolicy` 决定何时卸货、哪些槽位可丢。
  - **卸货 / 补给 / 维修 / 睡觉**：命名卸货点，靠近后潜行、俯视竖井中心、逐槽 `THROW` 丢出可丢物品；补给点、床、周界传送门、维修传送门与熔炉行坐标均可配置，跨维度经传送门取熔炉产物。
  - **导航**：`PerimeterNavigation` 统一行走与鞘翅飞行，切换时保存/恢复 Baritone 的 `allowPlace`、`allowPlaceInFluidsSource/Flow`、`elytraTermsAccepted` 等开关。
  - **配置**：`PerimeterConfigStore` 按「服务器地址 / 单人存档路径 / 本地维度」生成稳定 UUID 文件名，写盘用临时文件 + `ATOMIC_MOVE`；`PerimeterConfigMigration` 负责 schema 版本迁移。
  - **文本与命令**：`PerimeterText` 内置英文与简体中文两套文案（跟随游戏语言），`PerimeterTextTest` 校验两套键集完全一致，`PerimeterSourceTextCoverageTest` 直接扫描自动化 / 命令 / Hack 源码，任何未配中文的新文案都会让测试失败；`/perimeterdig` 为 Brigadier 命令树，子命令/键名/取值全部有 Tab 补全。
- **与参考实现的偏差（如实说明）**：原模组依赖 HackerRouter 的修改版 Baritone（`IAreaMineProcess`、`MovementHelper.avoidBreakingDueToLiquid`、边界封堵优先放置通道、`sourceLiquids`/`areaInteriorNeighbors` 目标等）。本移植改为：(1) 把区域挖掘语义放进本项目自己的 `PerimeterMiningSchematic`（`AbstractSchematic` 子类）交给**官方 Baritone** 的 `BuilderProcess.build(...)`；(2) 用 `inSchematic` 内的「流体相邻即跳过」守卫代替 `avoidBreakingDueToLiquid`；(3) 批次上限、已挖计数与暂停原因在本项目内实现；(4) 补给/维修/睡觉为同状态、同导航、同容器点击方式的功能等价实现，而非逐行移植；(5) 文本使用本项目自带中英对照表而非 Minecraft 语言文件。

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
