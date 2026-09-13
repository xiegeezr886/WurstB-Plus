# Twilight Echo → Minecraft 1.20.1 (Forge / Mojmap) 移植：可行性 + 保真度映射

> 面向 `WurstB+ Plus`（`D:\WurstB\WurstB-Plus-main`）的 UI 落地文档。
> 所有数值都标了来源；来源查不到的一律写 **未找到**，不用"看起来差不多"填坑。
>
> 配套交付：`src/main/java/net/wurstclient/twilight/TwilightTheme.java`（设计令牌 Java 副本，已 javac 通过）。

---

## 0. 一句话结论

外壳（侧栏 / 标题栏 / 播放条）、主页卡片流、登录页二维码、沉浸页的**背景与封面层**都能做到"看起来 1:1"，
因为它们最终都归约成"圆角矩形 + 纯色/线性渐变 + 投影 + 位图 + 位图模糊"这几件本工程已经有的能力；
**液态玻璃的色散折射**、**CSS `backdrop-filter` 的实时背景模糊**（大面积）、以及**任意字重的矢量中文排版**只能做到近似或干脆放弃。
歌词**不复刻参考实现**，改为把本工程已有的 AMLL 渲染器塞进复刻出来的沉浸页外壳里。

---

## 1. 证据基础与不确定度

### 1.1 权威素材（本次实际读取）

| 素材 | 用途 | 状态 |
|---|---|---|
| `_te_ref/src/renderer/src/assets/base.css`（3736 行 / 119.7 KB） | 令牌唯一权威 | 已读 `:root` 1–222、pureWhite 484–523、dark 525–617、动效 224–482、环境光 817–854、body 722–756、透明窗 2690–2809 |
| `_te_ref/src/renderer/src/assets/theme-layouts/paper-light.css`（169 行） | 截图所用布局预设 | 全文已读 |
| `_te_ref/src/packages/plugin-api/theme-contract.json`（1477 行） | 令牌契约（158 token / 26 mode） | 全文已读 |
| `StreamingHome.vue`（2193 行） | 主页规格 | 全文已读 |
| `PlayingMusic.vue`（2041 行） | 沉浸页外壳规格 | 全文已读 |
| `LoginPage.vue`（2341 行） | 登录页规格 | 全文已读 |
| `LiquidGlassDefs.vue`（1212 行） | 液态玻璃实现 | 全文已读 |
| `SideMenu.vue` / `TitleBar.vue` / `PlayerBar.vue` | 外壳规格 | 见 §5.6 说明 |
| 6 张截图 | 交叉校验 | `streaming-home` / `local-dashboard` / `immersive-lyrics` / `playlist-discovery` / `streaming-playlist` / `settings-light` |

### 1.2 参考快照的缺口（**不能**作为依据的部分）

这些文件不在 `_te_ref` 检出里，导致一部分规格**物理上无法从参考工程抄**：

| 缺失文件 | 影响 |
|---|---|
| `assets/theme-layouts/index.css`（`main.css` 第 4 行 `@import` 它，但文件不存在） | 预设聚合入口缺失 ⇒ **最终级联无法完整重建**，可能存在我们看不到的覆盖规则 |
| `components/player-bar/PlayerBar.css` | PlayerBar 的内部布局（栏高、列宽、进度条位置）**无权威值**；只剩 `--te-player-*` 令牌 |
| `components/SongList.vue` / `.track-row` / `.song-list` 的组件样式表 | 列表行高与列宽**无权威值**（base.css 里只有主题覆盖，没有基础规则）；见 §6.2 的实测替代 |
| `components/AnimatedInput.vue` | 登录页输入框内部样式**部分未知**（LoginPage 只通过 `.field-input` 与 `--ai-placeholder` / `--ai-justify` 影响它） |
| `stores/usePlayerStore.*` | **封面取色算法本体未找到**（`dominantColor` 只被引用，不被定义） |
| `shared/liquidGlass.ts` + `utils/liquidGlassDisplacement.ts` / `liquidGlassSpecular.ts` / `liquidGlassPointer.ts` / `liquidGlassPress.ts` | 位移贴图与高光贴图的**生成算法、分辨率、`resolveChannelScales` / `resolveAberrationBlur` / `resolveSpecularMapStrength` 公式、4 个 filter 的 id 字面量、`LIQUID_GLASS_*` 常量值全部未找到** |
| `src/renderer` 的 `.track-row` / `.player-bar` / `.side-menu` **基础** CSS | 这三者只有 `base.css` 里的**主题/玻璃/透明窗覆盖**，基础盒模型不在快照内 |

**结论**：`--te-*` 令牌是完整的、可信的；**组件级盒模型只有 `PlayingMusic.vue`、`StreamingHome.vue`、`LoginPage.vue` 三个文件是全的**，其余靠布局预设 + 像素实测补。

### 1.3 像素实测（用于补 CSS 缺失，全部可复现）

对 `streaming-home.png`（1494×879）逐像素扫描：

| 实测项 | 结果 | 交叉验证 |
|---|---|---|
| 侧栏 5 个菜单项文字行心 | y = 101.5 / 151.5 / 201.5 / 251.5 / 301.5 | **行距恒为 50px** |
| 主页菜单项选中底色 | y = 81..122（42 行内部），色 `rgb(233,239,253)` | = `--te-navigation-active` 8% 蓝叠在侧栏面板上 |
| 侧栏面板右边缘 | x ≈ 216–218（其后是 12/22/58 投影的衰减带） | 与 `clamp(132px,18vw,216px)` 的上限 216 吻合 |

对 `streaming-playlist.png`（1491×882）扫描曲目区：相邻行距 **64px**（308 → 372 → 436 三行稳定）。

**行距 50px 是本次最重要的实测**，它唯一地指向 paper-light **第二层**（菜单项 45px + menu-nav gap 5px = 50），
而不是任务书里引用的第一层（40px + 4px = 44）或 SideMenu.vue 的 40px + 6px = 46。详见 §5.5。

### 1.4 一个仍未解释的差异（诚实登记）

菜单首项顶边实测 y ≈ 80。按 paper-light 第二层推算应是：
面板 `top: 22px` + 品牌行 64px + `menu-items` 上内边距 22px = **108**。
截图里**看不到品牌行内容**（主题契约 `navigation.logo` 默认值是 `hide`，`theme-contract.json`），
所以剩下的 36px 无法归因。**列为待确认项**，不要照抄 108 这个推算值。

另外 `base.css:47` 有 `--te-ui-scale: 0.94`，恰好 45 × 0.94 = 42.3 ≈ 实测的 42px 内部带高；
但同一张图里侧栏宽度实测 ≈216px，与"全局缩放 0.94"（会得到 203px）矛盾。
**判定：`--te-ui-scale` 没有以全局 zoom/scale 形式生效**；42px 只是 45px 减掉 2px 的 `inset 0 0 0 1px` 内描边再加抗锯齿。
（`--te-ui-scale` 在 base.css 里只有声明、没有消费点，属于"只点名无值/无用法"的令牌。）

---

## 2. 本工程可用的渲染能力盘点

本工程目前**三条绘制路径并存**，能力完全不同。移植前必须先选定一条，混用会踩状态污染。

### 2.1 路径 A：`GuiGraphics` + Minecraft 位图字体（最稳，能力最弱）

| 类 | 能做什么 | 关键限制 |
|---|---|---|
| `net.wurstclient.clickgui2.FlatRenderer` | `fillRoundedRect` / `drawRoundedOutline` / `drawGradientOutline` / `panel`（圆角+投影+描边三件套） | 全部是**整数坐标 int 半径**；描边只有 1.35px 一种厚度 |
| `net.wurstclient.clickgui2.FlatUiRenderer` | `fill` / `outline` / `panel`，**接受 float 坐标与半径** | 同上 |
| `net.wurstclient.clickgui2.RoundedRectRenderer` | 真正的圆角填充/描边实现：每角 `segments = clamp(ceil(r*2), 8, 16)` 段的三角扇；描边用 3 条等高线做内外插值 | **包级私有**（`final class` 无 `public`），`twilight` 包用不到，只能经 `FlatRenderer`/`FlatUiRenderer` 间接调用 |
| `net.wurstclient.gui.visual.VisualTheme` | `mix()` / `withAlpha()` / 一组 LiquidBounce 风格语义色 | 那是**深色 HUD 主题**，与 Twilight 浅色无关，只能借它的工具方法 |
| `net.wurstclient.gui.visual.VisualRenderer` | `panel` / `button` / `input` / `progress` | 同上，深色风格 |
| `MusicRegion.drawText/drawCenteredText/drawTextRight/drawWrappedCenteredText` | **任意字号文字**：`pose().scale(size / font.lineHeight)` + `PingFangFont` 组件 | 这是个可复制的做法，但 `MusicRegion` 是 `abstract class`，只能继承不能静态调用 |

**圆角能力定量**（从 `RoundedRectRenderer` 源码读出）：半径 r 的圆角由 `segments = max(8, min(16, ceil(r*2)))` 段逼近，
所以 **r=26px 时只有 16 段/角 ≈ 5.6° 一段**，弧线会有可见的折线感。这是"看起来 1:1"的第一个硬伤（见 §10）。

### 2.2 路径 B：Skia（`SkikoNatives` + `SkiaRegionRenderer` + `SkiaFontManager`）

| 类 | 职责 | 关键限制 |
|---|---|---|
| `SkikoNatives.ensure()` | 把 `assets/wurst/skiko/skiko-windows-x64.dll`（17.3 MB）与 `icudtl.dat`（10.5 MB）解压到 `gameDir/skiko/`，设置 `skiko.library.path` / `skiko.data.path` | 首次调用有 27.8 MB 落盘 IO；失败抛 `IllegalStateException` |
| `SkiaRegionRenderer` | **当前唯一实用的 Skia 入口**：CPU raster Surface（尺寸 = GUI 区域 × guiScale）→ `peekPixels` → `glTexSubImage2D` 上传 `DynamicTexture` → `GuiGraphics.blit` 贴回 | ① 每帧一次全区域像素上传，成本 ∝ 区域面积 × guiScale²；② **一帧只能有一个区域**（`regionDrawing` 为真时 `beginRegion` 直接返回同一个 canvas）；③ 5 秒空闲才释放 surface；④ 容量只增不减 |
| `SkiaGlBackend` | DirectContext 直绘 MC 主 framebuffer | 源码注释说明"与 PVPUtils 一样**默认不启用**，避免污染 MC 的 GL 状态" ⇒ 事实上不可用 |
| `SkiaFontManager` | 苹方 regular / light / semibold 三字重 Typeface（`assets/wurst/font/pingfang_*.ttf`，每个 ~11 MB） | 只有这 3 个字重，且只有"苹方"一种字族；**没有 Inter / Plus Jakarta Sans / MiSans** |

Skia 的价值：**任意半径的真圆弧圆角、真渐变、真阴影、逐字 SVG 级排版**——这是唯一能逼近 Twilight 观感的路径。
代价是每帧 CPU 光栅化 + 全区域纹理上传。

### 2.3 路径 C：FBO 抓帧模糊（`RiseFrostedGlass`）——"毛玻璃"的唯一真解

`net.wurstclient.hud2.render.RiseFrostedGlass`（260 行）：

- `captureFrame()`：把主 RenderTarget `glBlitFrameBuffer` 到一张 `TextureTarget`（每帧一次，`HudManager` 第 118 行已在调）。
- `draw(...)`：`BLUR_RADIUS = 12` → `sigma = 6`；`SAMPLE_INDICES = {-12,-8,-4,0,4,8,12}` 共 7 点/轴，
  权重是高斯的；**49 次**圆角矩形采样绘制（每轴 7 点做可分离卷积），每次采样用 UV 偏移 `±index × BLUR_COMPRESSION(3) / guiScale`。
- 另有 6 层外扩圆角矩形做柔影（`spread 7→2`，alpha `20*strength²*opacity`）。

**这就是 `backdrop-filter: blur()` 的等价物**，而且已经在本工程里跑通了（`TargetHudElement`、`ScoreboardHudElement`）。
成本：每块玻璃 49 个矩形 ≈ 98 三角形 + 一次纹理采样批次，**与模糊半径无关、与面积成正比**，非常便宜。

### 2.4 位图与取色

- `net.wurstclient.clickgui2.music.NeteaseImageCache`：异步 HTTP → `NativeImage.read` → `DynamicTexture`，
  8 MB 上限、8 秒连接超时、注册名 `wurst:netease/<hash>`；**并自带封面取色** `sampleAccent(NativeImage)`（见 §9）。
- `MusicRegion.drawCover(...)`：**cover-fit 裁剪 + `graphics.blit`**，已处理源图宽高比。
- `graphics.blit` 只能画**轴对齐矩形**，不能旋转、不能圆角裁剪 ⇒ 主页 hero 里那 3 张**旋转 -3°/5°/-7° 的浮动封面**只能近似（见 §10）。
- 二维码：`com.google.zxing:core:3.5.3` 已是 `build.gradle:144` 的依赖，
  `clickgui2/music/LoginPage.java:631` 已有 `new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 37, 37, hints)` + 逐模块 `graphics.fill` 的现成实现。

### 2.5 现有可复用的"页面级"骨架

- `clickgui2/music/MusicContext` + `MusicRegion` + `NeteaseMusicScreen`（18 KB）+ 8 个 `clickgui2/music/*.java`
  —— **一套已经跑起来的音乐 UI**（侧栏 170px、播放条 78px、行高 26px 的 PVPUtils 布局常量，见 `MusicRegion:26-30`）。
- `clickgui2/music/PlayerDetailOverlay`（389 行）—— **沉浸页 + AMLL 歌词接线的现成范本**，见 §7.3。

---

## 3. 能力对照表：Twilight 的每个视觉特性 → 本工程用什么做

| # | Twilight 特性 | 参考实现（出处） | 本工程实现方案 | 结论 |
|---|---|---|---|---|
| 1 | 圆角矩形 | CSS `border-radius`，最大用到 26px（侧栏右缘）、22px（卡片/登录壳）、999px（胶囊） | `FlatUiRenderer.fill(g, x1,y1,x2,y2, r, color)`；r 由 `RoundedRectRenderer` 用 ≤16 段/角逼近 | ⚠️ **近似**：≤8px 看不出差别；≥20px 弧线可见折线。要 1:1 需走 Skia |
| 2 | 线性/径向/圆锥渐变 | `linear-gradient` / `radial-gradient` / `conic-gradient`（`body::before` 环境光、进度条填充、hero 遮罩） | 线性 ✅ 用 `FlatRenderer.GradientColorFn`（**只能水平**，`colorAt(float x)`）；胶囊形渐变进度条可用；径向/圆锥 ❌ 没有 | ⚠️ **近似**：线性可用但仅水平；径向/圆锥要么用同心圆角矩形叠（丑），要么走 Skia |
| 3 | 投影（`box-shadow`，多层） | hero `0 18px 44px`、侧栏 `12px 22px 58px`、封面 `0 26px 70px` | `RiseFrostedGlass` 的 `drawSoftShadow`（6 层外扩圆角矩形）或 `FlatUiRenderer.panel` 的单层偏移填充 | ⚠️ **近似**：能做出"有影"，但扩散半径/衰减曲线与 CSS 不同；`FlatRenderer.panel` 只有硬偏移 3–5px |
| 4 | 毛玻璃 `backdrop-filter: blur()` | 侧栏 `blur(26px) saturate(140%)`；`.backdrop-scrim` `blur(10px)`；`.shelf-count` `blur(8px)`；全屏背景图 `blur(58px)` | **`RiseFrostedGlass.captureFrame()` + `draw(...)`**：真·背景采样 + 7×7 可分离高斯 | ✅ **可做**（本工程独有优势）；但 `saturate()` 不支持，且模糊核固定 σ=6，`26px` 与 `10px` 只能选其一近似 |
| 5 | 液态玻璃高光 / 色散折射 | SVG `feImage(位移贴图)` → 3× `feDisplacementMap` → `feColorMatrix` 抽 RGB → 2× `feBlend(screen)` → `feGaussianBlur`；再 `feImage(高光贴图)` → `feFuncA(slope)` → `feComposite(in)` → `feBlend(screen)` | **无位移贴图生成器、无 SVG filter 管线、无 screen 混合模式** | ❌ **做不了**。替代：`RiseFrostedGlass`（模糊）+ 1px 亮描边（`inset 0 0 0 0.5px rgba(255,255,255,.54)` 用 `outline` 近似）+ 左上角一层白色线性渐变高光。视觉上"像玻璃"，但没有折射与色散边缘 |
| 6 | 图片封面 | `<img object-fit:cover>` | `NeteaseImageCache` + `MusicRegion.drawCover` 的 cover-fit 裁剪 + `blit` | ✅ **可做**（尺寸/裁剪已解决） |
| 7 | 旋转 / 3D 变换 | hero 浮动封面 `rotate(-3deg)`、duo 卡封面 `rotate(5deg) scale(0.94)`、`translateZ(0)` | `graphics.pose()` 支持 `rotate` / `scale`，但 `blit` 是轴对齐四边形 | ⚠️ **近似**：`pose().mul(PoseStack.rotateAround(...))` 可以先旋转再 blit，能转；但没有圆角裁剪（旋转后的图会露出直角），需要 Skia `clipPath` 才能 1:1 |
| 8 | 自定义字体（Inter / Plus Jakarta Sans / MiSans） | `--te-font-sans` 字体栈 | 只有 `assets/wurst/font/` 里的 **苹方 3 字重**（MC 字体 provider：`pingfang.json` / `_light` / `_semibold`）+ `sf_pro_rounded_regular.otf`（**有文件但无对应 `sf_pro_rounded.json` provider**，且 Skia 侧没加载它）+ MC 内置位图字体 | ❌ **做不了 1:1**。Inter / Plus Jakarta Sans / MiSans / Space Grotesk 都没有。替代：正文用苹方 regular，标题用**苹方 semibold 假装 800 字重**；字重只有 3 档 |
| 9 | SVG 图标 | PrimeIcons / Phosphor 图标字体（`pi-play` / `ph-palette` / `pi-wave-pulse` …） | `assets/wurst/textures/gui/icons/*.png`（19 个 PNG + 3 个 SVG **未用**）+ `textures/gui/netease/*.png`（7 个）+ `MusicRegion` 里手写的 `drawPlay` / `drawQueue` / `drawVolume` / `drawPlaying` 图元 | ⚠️ **近似**：需要的图标得**手工补 PNG**（禁止新增依赖，不能引图标字体）；简单的播放/暂停/队列条可以用 `graphics.fill` 直接画 |
| 10 | CSS 过渡 / 弹簧 | `--te-motion-*` 五档时长 + 5 条 `cubic-bezier` | `compose.AnimFloat`（指数平滑，只有速度参数）、`clickgui2.supersoft.UiTween` / `UiMotion`（有阻尼的弹簧）、`compose.FlowingGradient`、`music.apple.Spring`（**解析解弹簧**：阻尼比 ≥1 过阻尼、<1 欠阻尼余弦） | ✅ **可做**（`Spring` 就是 AMLL 那套，质量最高）；`cubic-bezier` 需要自己按控制点求值，本工程没有现成的 |
| 11 | 封面取色（强调色随封面变化） | `usePlayerStore.dominantColor` → 写入 `--accent-color` → 3 处消费（§9） | **已存在**：`NeteaseImageCache.sampleAccent(NativeImage)`（8×8 网格采样 + 饱和度/亮度打分），`MusicContext.accentColor`，`MusicRegion.lerpAccent` 提亮 | ✅ **可做**，且算法已有，只需换调参（见 §9） |
| 12 | 虚拟滚动 / 长列表 | `v-memo` + `:ref` 注册行元素 + `ResizeObserver` + `IntersectionObserver(rootMargin: 128px)` + 展开面预算 | `MusicRegion.renderSongRows` 已有**可视区裁剪**（`rowTop + SONG_HEIGHT <= bounds().top` 就 `continue`）+ 手写滚动条 | ✅ **可做**（功能等价）；但没有 `IntersectionObserver` 的预热余量，快速滚动会白一帧 |
| 13 | 骨架屏 shimmer | `.sk-shimmer::after` + `translateX(-100%)→100%`，1.5s | 用 `FlowingGradient.flow()` 的思路（`System.currentTimeMillis() % period`）驱动一个白色半透明圆角矩形横向平移 | ✅ **可做**，成本极低 |
| 14 | `mask-image` 渐隐（歌词上下淡出） | `.lyrics-scroll` 的 6 停靠点 `linear-gradient` mask | Skia 有 `Mask`/`Shader`，但 `AppleLyricPlayer` 目前自己处理进出视口的透明度（`AmlVisual.opacity` 里 `!inViewport → 0`） | ✅ **可做**（AMLL 自己已解决） |
| 15 | `filter: blur()` 逐行歌词模糊 | `resolveBlurLevel` → `1 + 距焦点行数`，上限 5px | **已实现**：`AmlVisual.blurLevel/blurPx` + Skia `ImageFilter` 路径 | ✅ **可做**（不需要复刻参考实现） |
| 16 | 窗口拖拽区 `-webkit-app-region: drag` | `.drag-region` | Minecraft 没有窗口内拖拽概念（标题栏属于 OS） | ❌ **不适用**：直接省略标题栏拖拽语义，只保留视觉条 |
| 17 | `:hover` / `:active` 全局反馈 | `translate` 160ms、`scale(0.97)` 90ms | `AnimFloat` + `mouseX/mouseY` 命中测试（`MusicRegion.contains`）即可 | ✅ **可做** |
| 18 | 滚动条自动隐藏（`.te-auto-scrollbar`） | 靠近/滚动时才显示 | `MusicRegion.renderScrollbar` 已有手写滚动条 | ✅ **可做**，加一个"最近滚动时间"即可 |
| 19 | 透明窗口 / 桌面穿透（Acrylic） | `data-window-transparent='on'` 一整套 | Minecraft 是全屏/窗口化 GL 应用，没有 OS 合成器混色 | ❌ **做不了**，也不该做 |
| 20 | 桌面歌词 / TTML | `html.desktop-lyrics-document` | **明确排除**（本次范围外） | – |

---

## 4. 缺口清单 + 每个缺口的最小可行补法

按"挡不挡 1:1"排序。

| # | 缺什么 | 影响 | 最小可行补法 | 工作量 |
|---|---|---|---|---|
| G1 | **圆角弧线精度**：`RoundedRectRenderer` 上限 16 段/角 | 22–26px 圆角肉眼可见折线，卡片/侧栏/登录壳首当其冲 | ① 把 `MAX_SEGMENTS` 提到 `max(8, ceil(r*4))`（改的是别人正在动的 `clickgui2`，**本次禁止**）；② **推荐**：在 `twilight` 包内自己写一个 `TwilightRoundedRect`（照抄 `RoundedRectRenderer` 的三角扇算法，只改段数），不动既有文件 | 小（~120 行） |
| G2 | **无饱和/亮度调节的模糊** | `blur(26px) saturate(140%)` 只能做成 `blur` | `RiseFrostedGlass` 的采样顶点里，把 UV 采样结果的 RGB 做一次 `mix(tex, gray(tex), -0.4)` 就是在着色器里加饱和——但着色器是 MC 内置的 `position_color_tex`，改不了。**替代**：模糊层之上再叠一层 `saturate` 效果的半透明彩色矩形（`screens` 混合不可用，只能 alpha 叠加） | 小，但效果打折 |
| G3 | **径向 / 圆锥渐变** | 环境光 `body::before` 的 conic-gradient、`.backdrop-accent` 的 radial、hero 卡片里的 radial 装饰全部做不了 | ① 用 Skia（`RadialGradient` / `SweepGradient` 都有）；② 或用 8–16 段扇形三角近似（`conic`）；③ 或者**放弃环境光层**，直接用 `--te-background-gradient-start → end` 的 135° 线性渐变打底 | 中（走 Skia）/ 小（放弃） |
| G4 | **字体**：没有 Inter / Plus Jakarta Sans / MiSans / Space Grotesk；苹方只有 3 字重；MC 位图字体 9px 起 | `--te-font-size-body: 14px` 是**网页 px**，MC 位图字体只有 9px 行高，14px 需要 `pose().scale(14/9)` → **位图放大 1.56 倍会糊** | ① 文字全部走 `MusicRegion` 那套 `pose().scale()`（位图糊）；② **推荐**：文字也走 Skia（`SkiaFontManager` 的苹方 Typeface + `setFontSize(14)` 矢量渲染），这是本工程已经在歌词里做的事；需要扩 `SkiaFontManager` 支持加载 `sf_pro_rounded_regular.otf` | 中 |
| G5 | **`filter: url()` 型 SVG 滤镜管线** | 液态玻璃的位移/色散/镜面高光全做不了 | 无最小补法（要么实现一整套 SVG filter，要么放弃）。退化为 G2 的模糊 + 亮描边高光 | 大 / 放弃 |
| G6 | **screen / overlay 等混合模式** | 玻璃高光、`.hero` 的 `screen` 叠色 | MC 的 `RenderSystem.blendFunc` 可以设 `GL_ONE, GL_ONE`（加法）近似 screen；`FlatRenderer` 目前只用 `defaultBlendFunc()`，需要自己开一段状态 | 小 |
| G7 | **旋转位图的圆角裁剪** | hero / duo 卡的旋转封面会露出直角 | 走 Skia：`canvas.save(); canvas.clipRRect(rrect, antiAlias); canvas.rotate(deg); canvas.drawImageRect(...)` | 中（Skia） |
| G8 | **`SkiaRegionRenderer` 一帧只能有一个区域 + 全区域每帧上传** | 不能同时用 Skia 画"封面 + 文字 + 装饰"三块；4K 窗口下一个 1200×600 区域在 guiScale=3 下每帧上传 ~6.5M 像素 | ① 合并成一个大区域一次画完（推荐：整个内容区一个 region）；② 或给 `SkiaRegionRenderer` 加多区域支持（改既有文件，**本次禁止**）；③ 或在 `twilight` 包内自建一个同样的 region 渲染器 | 中 |
| G9 | **图标资产** | 缺 PrimeIcons / Phosphor 对应图标 | 只补真正需要的（播放/暂停/上一首/下一首/音量/队列/收藏/搜索/返回/关闭/循环/随机），手绘 PNG 或 `graphics.fill` 图元 | 小-中 |
| G10 | **列表行高/列宽无权威值** | SongList 规格只能实测 | 用实测 64px 行距 + `--te-library-*` 令牌；列宽参考 `local-dashboard.png` 再量 | 小 |
| G11 | **`cubic-bezier` 求值器** | `--te-ease-*` 五条曲线无处可用 | 在 `twilight` 包内写一个 20 行的牛顿迭代 `cubicBezier(t, p1x,p1y,p2x,p2y)` | 极小 |
| G12 | **CSS `color-mix()` 的预乘语义** | `TwilightTheme.shellSidebarBg()` 用的是非预乘线性混合，与浏览器差 1–3/255 | 已登记（`TwilightTheme` 里注释说明）；要完全一致就按预乘重算 | 极小 |

**结论**：真正卡住 1:1 的只有 **G1（圆角精度）+ G4（字体）**，这两个都能在**只新建 `twilight` 包内文件**的前提下解决（自写圆角渲染器 + 走 Skia 文字）。
**G5（液态玻璃）是唯一"做不了"且无替代的项**。

---

## 5. 应用外壳规格

外壳 = `SideMenu` + `TitleBar` + `PlayerBar` 三条，加 `body::before` 环境光背景。

### 5.0 外壳令牌（base.css，两套取值）

见 `TwilightTheme.java`，每个字段注释里有行号。摘要：

| 令牌 | 浅色（生效层 484–523） | 深色（525–617） | 行号 |
|---|---|---|---|
| `--te-app-bg` | `#f4f4f7` | `#17181a` | 67 / 552 |
| `--te-navigation-bg` | `rgba(255,255,255,.94)` | `#17181a` | 104 / 590 |
| `--te-navigation-border` | `rgba(0,0,0,.05)` | `transparent` | 105 / 591 |
| `--te-navigation-shadow` | `4px 0 24px rgba(15,23,42,.03)` | `none` | 106 / 592 |
| `--te-navigation-text` | `#475569` | `#d8d8d8` | 107 / 593 |
| `--te-navigation-icon` | `#64748b` | `#9b9b9b` | 108 / 594 |
| `--te-navigation-hover` | `rgba(15,23,42,.04)` | `rgba(255,255,255,.065)` | 109 / 595 |
| `--te-navigation-hover-text` | `#0f172a` | `#f7f7f2` | 110 / 596 |
| `--te-navigation-active` | `rgba(37,99,235,.08)` | `rgba(245,158,11,.16)` | 111 / 597 |
| `--te-navigation-active-text` | `#2563eb` | `#f59e0b` | 112 / 598 |
| `--te-navigation-indicator` | `#2563eb` | `#f59e0b` | 113 / 599 |
| `--te-navigation-opacity` | `94%`（深色未覆盖） | | 114 |
| `--te-navigation-radius` | `0px`（深色未覆盖） | | 115 |
| `--te-menu-width` | `clamp(132px, 18vw, 216px)`（深色未覆盖） | | 48 |
| `--te-primary-500` | `#2563eb`（`:root` 兜底 `#7c4dff`，行 2） | `#f59e0b` | 486 / 527 |
| `--te-chrome-text` | `#475569` | `#d8d8d8` | 66 / 543 |
| `--te-shell-control-text` | `#0f172a` | `#f7f7f2` | 167 / 564 |
| `--te-shell-control-hover` | `rgba(37,99,235,.08)` | `rgba(245,158,11,.12)` | 168 / 577 |

**分层线色 `--sf-shell-line`**：paper-light.css:2 `color-mix(in srgb, var(--te-neutral-900) 13%, transparent)`
⇒ 浅色 `rgba(15,23,42,.13)` = `0x210F172A`；深色 `rgba(247,247,242,.13)`。
**注意**：它只在 paper-light 里定义，`base.css` 没有这个变量。

**环境光背景**（这是截图里那层淡紫蓝的来源）：
`body::before`（base.css 836–854）：`conic-gradient(from 18deg at 72% 18%, transparent 0deg, rgba(124,77,255,.2) 52deg, rgba(255,126,182,.12) 96deg, transparent 148deg)` 叠
`linear-gradient(135deg, rgba(255,255,255,.92), rgba(247,244,255,.78))`，`opacity: .9`，`animation: ambient-light-shift 14s infinite alternate`。
深色版（817–829）改用 `rgba(var(--te-primary-rgb), .1)` / `rgba(217,79,125,.06)`，`opacity: .72`。
`--te-background-gradient-start: #eff6ff` / `-end: #f5f3ff` / `-angle: 135deg`（70–72）是另一组、不在这条 `::before` 里。

### 5.1 SideMenu（侧栏）

`paper-light.css` **对 `.side-menu` 声明了两次，后者覆盖前者**。两层都给出来，**渲染层是第二层**（§1.3 实测支持）。

| 属性 | 第一层（行 24–92） | **第二层（行 115–169，生效）** |
|---|---|---|
| 定位 | 静态（占布局） | `top: 22px; bottom: 22px`（浮动） |
| 边框 | `border-right: 1px solid --sf-shell-line`，圆角 `0` | `border: 1px solid --sf-shell-line; border-left: 0`，圆角 `0 26px 26px 0` |
| 背景 | `var(--te-navigation-bg)` | `color-mix(in srgb, var(--te-navigation-bg) 82%, var(--te-primary-500))` |
| 投影 | `none` | `12px 22px 58px color-mix(in srgb, var(--te-neutral-50) 32%, transparent)` |
| 模糊 | `backdrop-filter: none` | `backdrop-filter: blur(26px) saturate(140%)` |
| `.menu-items` 内边距 | `18px 12px` | `22px 13px` |
| `.menu-items` gap | 未声明 | `12px` |
| `.menu-nav` gap | `4px` | `5px` |
| `.menu-item` 高 | 未声明（SideMenu.vue 40px） | **`45px`** |
| `.menu-item` 圆角 | `4px` | **`13px`** |
| `.menu-item` 横向内边距 | 未声明 | `0 14px` |
| hover | `background: --te-navigation-hover; transform: none` | `transform: translateX(3px); background: --te-navigation-hover` |
| active 底色 | `--te-navigation-active` + `color: --te-navigation-active-text` | 同 + `box-shadow: inset 0 0 0 1px --sf-shell-line` |
| **选中指示条** | `top/bottom: 12px; left: 0; width: 2px; radius: 0; box-shadow: none` | **`top/bottom: 9px; left: 5px; width: 3px; radius: 999px; box-shadow: 0 0 18px --te-primary-500`** |
| 过渡 | 无 | `background --te-motion-hover(160ms), color 160ms, transform --te-motion-return(220ms) --te-ease-out-quint` |
| `.menu-separator` 外边距 | `14px 8px` | `10px 18px` |

**品牌行 `.navigation-brand`**（paper-light 33–43，两层都生效）：
`height: 64px`；`border-bottom: 1px solid --sf-shell-line`；
`font-family: 'Space Grotesk', var(--te-font-sans)`；`font-size: calc(14px * 12/14) = 12px`；`font-weight: 500`；
内部 `img { border-radius: 4px }`。
**但截图里看不到品牌行内容**（主题契约 `navigation.logo` 默认 `hide`），而实测首项顶边与"64px 品牌行"推算差 36px（§1.4）。

**菜单项文字**：`font-size: calc(14px * 12/14) = 12px`；`letter-spacing: 0`（paper-light 84–87）。
**图标**：未找到尺寸令牌（`--te-library-icon-size: 18px` 是曲库的，不是侧栏的）。

**导航模式**（主题契约 `data-te-navigation-style`）：`expanded | compact | rail`，默认 `expanded`。
宽度三档由任务书/父级核实值给出：常规 `clamp(180px, 18vw, 216px)`、紧凑 `164px`、图标轨 `72px`。
**注意** `base.css` 的 `--te-menu-width` 下限是 **132px**，与 180px 不同源；`TwilightTheme` 里两个都留了。

**Java 落地建议**：`TwilightTheme` 里
`MENU_ITEM_HEIGHT_PAPER_LIGHT = 45`、`MENU_ITEM_RADIUS_PAPER_LIGHT = 13`、`MENU_ITEM_PADDING_X_PAPER_LIGHT = 14`、
`SIDEBAR_NAV_PADDING_PAPER_LIGHT = {22,13}`、`SIDEBAR_ITEM_GAP_PAPER_LIGHT = 5`、
`MENU_INDICATOR_WIDTH_PAPER_LIGHT = 3`、`MENU_INDICATOR_INSET_PAPER_LIGHT = 9`、`MENU_INDICATOR_LEFT_PAPER_LIGHT = 5`、
`MENU_INDICATOR_GLOW_PAPER_LIGHT = 18`、`SHELL_SIDEBAR_RADIUS_RIGHT = 26`、`SHELL_SIDEBAR_BLUR_RADIUS = 26`、
`SHELL_SIDEBAR_INSET_Y = 22`。

### 5.2 TitleBar（标题栏）

| 属性 | 值 | 出处 |
|---|---|---|
| 高度 | `min-height: 54px` | paper-light.css 5–12 |
| 底边框 | `border-bottom: 0` | 同上 |
| 背景 | `var(--te-app-bg)`（**不是**玻璃） | 同上 |
| 投影 | `box-shadow: none !important` | 同上 |
| 模糊 | `backdrop-filter: none` | 同上 |
| 按钮圆角 | `4px`（`:is(.menu-btn,.settings-btn,.plugins-btn,.login-btn,.control-btn)`） | paper-light.css 18–22 |
| 按钮宽度 | 36px | 任务书/父级核实（SideMenu.vue 侧） |
| 拖拽区 | `.drag-region { height: 32px }` | base.css 649–652 |
| 文字色 | `--te-chrome-text` = `#475569` / `#d8d8d8` | 66 / 543 |

截图核对（`streaming-home.png`）：左上角是 ☰ / ⚙ / ☁ 三个图标按钮，右上角是 − / □ / × 窗口控件，
中间无可见底边框、无玻璃——**与 paper-light 的"无边框、无投影、无模糊"一致**。
标题栏是 Minecraft 的 OS 窗口标题栏，**拖拽语义不适用**（见 §3 #16）：只需要在 GUI 顶部画一条 54px 的视觉条 + 左右两组按钮。

### 5.3 PlayerBar（播放条）

**权威值只有令牌，没有组件 CSS**（`player-bar/PlayerBar.css` 不在快照内）。

令牌（base.css 135–147，深色块**未覆盖**这些，两套主题同值）：

| 令牌 | 值 | 行 |
|---|---|---|
| `--te-player-control-size` | `32px` | 135 |
| `--te-player-play-size` | `44px` | 136 |
| `--te-player-control-gap` | `12px` | 137 |
| `--te-player-control-radius` | `999px` | 138 |
| `--te-player-control-border-width` | `0px` | 139 |
| `--te-player-progress-track` | `rgba(37,99,235,.16)` | 140 |
| `--te-player-progress-fill` | `linear-gradient(90deg, #2563eb, #0d9488)` | 141 |
| `--te-player-progress-height` | `6px` | 142 |
| `--te-player-progress-radius` | `999px` | 143 |
| `--te-player-progress-thumb-size` | `12px` | 144 |
| `--te-player-time-surface` | `rgba(37,99,235,.08)` | 145 |
| `--te-player-time-radius` | `8px` | 146 |
| `--te-player-time-opacity` | `0%` | 147 |

另有两处相关：`base.css:3042` 把 `.player-title` / `.track-row .track-title` 与 `.playing-music .track-title` 一起处理；
`PlayingMusic.vue:233` 会查 `.player-bar-shell` 的高度来算歌词锚点——移植时**建议保留同一个类名语义**（一个可查询的栏高常量）。

**Java 落地**：`TwilightTheme.PLAYER_CONTROL_SIZE = 32`、`PLAYER_PLAY_SIZE = 44`、`PLAYER_CONTROL_GAP = 12`、
`PLAYER_PROGRESS_HEIGHT = 6`、`PLAYER_PROGRESS_THUMB_SIZE = 12`、`RADIUS_PILL = 999`。
进度条填充是**水平线性渐变**，恰好落在 `FlatRenderer.GradientColorFn` 的能力范围内（`colorAt(float x)`）⇒ 这一项**能 1:1**。

截图核对（`streaming-home.png` 底部）：悬浮白色圆角条（**圆角与浮动方式属于 `.player-bar-glass` 变体，其 CSS 不在快照内 ⇒ 未找到**），
从左到右为：封面缩略图 → 标题（强调色）/ 艺人（次要色）→ 播放控制（上一首 / 圆形播放暂停 / 下一首）→
进度条（青→蓝渐变）+ 左右时间标签 → 一排图标按钮（收藏 / 随机 / 循环 / 歌词 / 队列 …）→ 右侧总时长。
**注意截图里进度条填充是"青→蓝"**，而令牌写的是 `#2563eb → #0d9488`（蓝→青绿）；两者方向相反，
说明**截图里的进度条颜色可能来自封面强调色**（§9）或另一个未在快照内的变量。**列为待确认。**

### 5.4 外壳实现骨架（Java）

```
TwilightShellScreen (Screen)
├─ 背景层    : 线性渐变 135°（#eff6ff→#f5f3ff）→ 环境光近似（可选，见 G3）
├─ TitleBar  : 54px，高 36px 的 4px 圆角按钮 ×3 左 + ×3 右
├─ SideMenu  : x=0, y=22, w=sidebarWidthPx(...), h=H-44
│              → RiseFrostedGlass.draw(..., radius=[0,26,26,0], blur≈26px)
│              → 上面叠 shellSidebarBg() 的 82/18 混色
│              → 菜单项 45px / 13px 圆角 / 左侧 3px 指示条（glow 18px 用 3 层渐隐矩形近似）
│              → 1px 右边框 shellLine()
├─ MainContent: x=sidebarRight, y=54, 到 PlayerBar 顶部
└─ PlayerBar : 底部浮动条，控件 32px / 播放 44px / 间距 12px / 进度 6px 圆角 999
```

---

## 6. 内容页规格

### 6.1 StreamingHome（主页）

出处：`StreamingHome.vue`。局部变量（489–500）：
`--home-radius-lg: 22px`、`--home-radius-md: 14px`、
`--home-ink: var(--te-neutral-900)`、`--home-ink-soft: var(--te-neutral-500)`、`--home-line: var(--te-card-border)`、
`--home-primary-tint: color-mix(--te-primary-500 12%, transparent)`、`--home-cyan-tint: color-mix(--te-accent-cyan 12%, transparent)`、
`--home-shadow: 0 18px 44px color-mix(neutral-900 8%, transparent)`、`--home-shadow-lift: 0 24px 56px color-mix(neutral-900 13%, transparent)`。

**分节间距恒为 44px**（`.home-flow { gap: 44px; padding-bottom: 8px }`，502–507）。
入场错峰：`home-rise 0.62s --te-ease-out-quint both`，2/3/4 节 delay `0.06s/0.12s/0.18s`；`from { opacity:0; translateY(26px) scale(.985) }`。
**页面滚动容器的 padding / max-width 未找到**（父级 `.streaming-page` 的样式不在快照内）——这一项必须自己定。

#### Hero 卡（每日推荐）

| 项 | 值 | 行 |
|---|---|---|
| `.hero` | `border-radius: 22px; border: 1px solid --home-line; background: --te-card-bg; overflow: hidden; box-shadow: --home-shadow` | 539–546 |
| 高度 | **无固定 height、无 aspect-ratio**，由内容撑开 | – |
| `.hero-inner` | `display:flex; align-items:stretch; gap:24px; min-height: clamp(280px, 30vw, 340px)` | 582–589 |
| `.hero-copy` | `flex: 1 1 52%; padding: clamp(28px, 3.6vw, 48px); justify-content:center` | 591–599 |
| `.hero-stage`（右侧拼贴） | `flex: 1 1 48%; position:relative; overflow:hidden` | 745–754 |
| ambient 图 | `inset:-18%; width/height:136%; object-fit:cover; filter: blur(64px) saturate(1.35); opacity:.5` | 555–565 |
| ambient 遮罩 | `linear-gradient(100deg, --te-card-bg 0%, …82% 46%, …30% 100%)` + `linear-gradient(180deg, …30%, transparent 40%)` | 568–580 |
| `.hero-kicker-day` 日期徽章 | `min-width:46px; height:46px; padding:0 6px; border-radius:13px; background: color-mix(--home-ink 92%, transparent); color: --te-card-bg; font-size:20px; font-weight:800` | 607–620 |
| `.hero-kicker-meta` | `font-size:13px; font-weight:600`；sub `11px / 500 / letter-spacing .08em` | 622–636 |
| `.hero-title` | `margin-top:22px; font-family: --te-font-display; font-size: clamp(40px, 5vw, 62px); line-height:1.02; font-weight:900; letter-spacing:-.01em`（≤640px `clamp(30px,8vw,40px)`） | 638–646 |
| `.hero-title-en` | `margin-top:6px; font-size: clamp(13px,1.3vw,15px); font-weight:800; letter-spacing:.42em; color: color-mix(--te-primary-500 78%, --home-ink)` | 648–655 |
| `.hero-desc` | `max-width:400px; margin-top:18px; font-size:14px; line-height:1.7; font-weight:500; color: --home-ink-soft` | 657–664 |
| `.hero-actions` | `gap:12px; margin-top:28px` | 666–671 |
| **主 CTA** `.hero-play` | `height:46px; padding:0 24px; border-radius:999px; background: --home-ink; color: --te-card-bg; font-size:14px; font-weight:700; gap:9px; box-shadow: 0 14px 30px color-mix(--home-ink 26%, transparent)`；hover `translateY(-2px)` + `duration 500ms`；active `scale(.97)` + `90ms` | 673–707 |
| **次 CTA** `.hero-open` | `height:46px; padding:0 20px; border:1px solid color-mix(--home-ink 18%, transparent); border-radius:999px; background:transparent; font-size:14px; font-weight:600`；hover border 34% + `background: --te-hover-bg`，箭头 `translateX(3px)` | 709–741 |
| 拼贴 card-0 | `clamp(168px,15.5vw,218px)`，`right:24%; top:50%; translateY(-54%) rotate(-3deg); z-index:3`，浮动动画 7s | 780–788 |
| card-1 | `clamp(120px,10.5vw,150px)`，`right:7%; top:14%; rotate(5deg); opacity:.96`，8.5s | 790–799 |
| card-2 | `clamp(96px,8.5vw,122px)`，`right:12%; bottom:8%; rotate(-7deg); opacity:.92`，9.5s | 801–810 |
| 拼贴卡通用 | `border-radius:18px; overflow:hidden; box-shadow: 0 22px 48px color-mix(neutral-900 24%), 0 0 0 1px color-mix(--te-card-bg 40%)` | 756–770 |
| ≤880px | `.hero-inner` 转 `column`，`.hero-stage { width:100%; height:240px }`，card-0 `right:36%` | 1753–1771 |

**没有独立圆形播放按钮**（明确"未找到"）：hero 里只有 46px 高的胶囊 + 12px 的 `pi-play` 字形。

#### Duo 卡（私人漫游 / 私人雷达）

`.duo { grid 2 列; gap:20px }`（≤880px 单列）。
`.duo-card { gap:18px; padding:20px 22px; border:1px solid --home-line; border-radius:22px; background: --te-card-bg; box-shadow: --home-shadow }`，
hover `translateY(-3px)` + `--home-shadow-lift` + border `16%`。
`::before` 装饰：`radial-gradient(circle at 0% 0%, --home-primary-tint, transparent 52%)` + 右下角 6% 同色（radar 卡换成 cyan）。
`.duo-stack { width:96px; height:72px }`；`.duo-stack-cover { 60×60; border-radius:12px; box-shadow: 0 10px 22px …20%, 0 0 0 2px --te-card-bg }`，
三张位置 `left:0/top:6px z3`、`left:22px/top:0 rotate(5deg) scale(.94) z2`、`left:42px/top:10px rotate(-6deg) scale(.88) z1`。
`.duo-name { font-size:19px; font-weight:800; --te-font-display }`；`.duo-sub { 12px; letter-spacing:.04em; --home-ink-soft }`。
`.duo-arrow { 38×38; border-radius:999px; border:1px solid color-mix(--home-ink 14%) }`，hover 填充 `--home-ink` + `translateX(4px)`。
≤640px：`gap:14px; padding:16px`；stack `80×60`；cover `50×50`；name 17px；sub 11px。

#### Section 头部行

`.section-head { display:flex; align-items:flex-end; justify-content:space-between; gap:16px; margin-bottom:18px }`。
`h3 { font-family: --te-font-display; font-size:21px; font-weight:800; letter-spacing:-.005em }`（≤640px 18px）；
`p { margin-top:4px; font-size:12px; font-weight:500; --home-ink-soft }`（≤640px 11px）。
`.section-more`（"more"胶囊）：`height:34px; padding:0 14px; border:1px solid --home-line; border-radius:999px;
background: --te-card-bg; font-size:12px; font-weight:600`，hover `translateX(2px)` + border 26%，图标 10px。
**只有 chart 段有 more 链接；shelf 段只有标题 + 副标题。**

#### Chart 列表（2 列歌曲行，`CHART_LIMIT = 8`）

`.chart-grid { grid-template-columns: repeat(2, minmax(0,1fr)); gap: 6px 28px }`（≤1100px 单列）。
`.chart-row { grid-template-columns: 34px 48px minmax(0,1fr) auto; gap:14px; padding:9px 12px;
border-radius:14px; background:transparent }`，hover `--te-hover-bg`，`.is-playing` `--home-primary-tint`。
`.chart-index { font-size:20px; font-weight:800; tabular-nums; letter-spacing:-.02em; color: color-mix(--home-ink 22%) }`
→ hover 46% → playing `--te-primary-500`。
`.chart-cover { 48×48; border-radius:10px; overflow:hidden; background: --te-subtle-bg; box-shadow: 0 8px 18px …12% }`。
`.chart-cover-action` 遮罩 `color-mix(--home-ink 44%)`，hover/playing `opacity:1`（dark 用 `neutral-50 44%`）。
`.chart-eq`：3 条 3px 宽、圆角 2px、高度 60%/100%/42%，`chart-eq-bounce 0.9s ease-in-out infinite`，delay `0/0.22s/0.44s`，`scaleY(.5)→scaleY(1)`。
`.chart-title { 14px; font-weight:650 }`（playing 时 `color-mix(--te-primary-500 82%, --home-ink)`）；
`.chart-artist { 12px; --home-ink-soft }`；`.chart-duration { 12px; tabular-nums }`。
≤640px：`grid-template-columns: 28px 42px minmax(0,1fr) auto; gap:10px; padding:8px 8px`；cover `42×42`；title 13px。

#### Shelf 卡片网格（歌单）

`.shelf-grid { grid-template-columns: repeat(auto-fill, minmax(172px, 1fr)); gap: 24px 20px }`
（≤880px `minmax(148px,1fr)`；≤640px `minmax(120px,1fr)` + `gap:16px 12px`）。
**可见列数没有固定断点**，由 `auto-fill` 决定 ⇒ Java 侧用 `cols = max(1, (int)((w + gapX) / (172 + gapX)))` 复刻。
`.shelf-cover { width:100%; aspect-ratio:1; border-radius:16px; overflow:hidden; background: --te-subtle-bg;
box-shadow: 0 14px 30px …12% }`，hover `translateY(-4px)` + `0 20px 42px …20%`，内部 img `scale(1.06)`（transition `0.35s→0.9s`）。
`.shelf-scrim { linear-gradient(180deg, transparent 46%, color-mix(neutral-900 62%, transparent)); opacity:0 }`（dark 用 `neutral-50 62%`）。
`.shelf-count`：`left/bottom:10px; padding:3px 9px; border-radius:999px; font-size:11px; color:#fff;
background: color-mix(neutral-900 46%, transparent); backdrop-filter: blur(8px)`，hover 出现。
`.shelf-open`：`right/bottom:10px; 36×36; border-radius:999px; color: --te-neutral-900;
background: color-mix(#ffffff 92%, transparent); box-shadow: 0 10px 22px …30%`，hover `translateY(0) scale(1)`（`--te-ease-spring`）。
`.shelf-name { margin-top:10px; font-size:13px; line-height:1.4; font-weight:600 }`，`-webkit-line-clamp:2`。

#### 骨架屏

`.home-skeleton { gap:40px }`；`.sk-shimmer { background: color-mix(--home-ink 6%, transparent) }` +
`::after { transform: translateX(-100%); background: linear-gradient(90deg, transparent, color-mix(--te-card-bg 60%), transparent);
animation: sk-sweep 1.5s ease-in-out infinite }`。
`.sk-hero { height: clamp(280px,30vw,340px); border-radius:22px }`；`.sk-tile-cover { aspect-ratio:1; border-radius:16px }`；
`.sk-tile-line { height:13px; width:78%; border-radius:6px }`；渲染 **6** 个 tile。

#### 其它状态

`.home-error { min-height:340px; padding:48px 24px; border-radius:22px; background: --te-card-bg }`；
图标容器 `58×58; border-radius:18px; color: --te-warning-500; background: color-mix(--te-warning-500 12%)`；title 19px/800；
retry `height:40px; padding:0 20px; border-radius:999px`。
`.hero-invite { min-height:420px; padding:60px 24px; border-radius:22px }`；orb A `340×340 filter: blur(52px)`、
orb B `300×300`；notes 方块 `74×74; border-radius:22px`；kicker `12px/700/letter-spacing .32em`；
title `clamp(30px,3.6vw,42px)/900`；CTA `height:48px; margin-top:32px; padding:0 28px`。

**注意**：`StreamingHome.vue` **没有任何封面取色**（`providerColor` prop 声明后从未使用）。
主页里那点紫色/青色"环境色"全部来自 `--home-primary-tint` / `--home-cyan-tint`（静态令牌 12% 混色）+ hero 的模糊封面图。
所以截图里主页的紫色并非动态取色——**这与任务书"强调色来自封面取色"的印象在主页上不成立**；
取色真正生效的是沉浸页（§9）。

### 6.2 歌单 / 曲库列表

**权威 CSS 不在快照内**（`SongList.vue` / `.track-row` / `.song-list` 基础规则缺失；`base.css` 只有主题覆盖）。
可用的依据：

| 项 | 值 | 来源 |
|---|---|---|
| 行距 | **64px** | `streaming-playlist.png` 像素实测（三行稳定） |
| 选中指示器色 | `--te-library-selection-indicator` = `rgba(15,23,42,.55)` / 深 `rgba(255,255,255,.72)` | 124 / 608 |
| 选中底色 / hover | `rgba(15,23,42,.045)` / `rgba(15,23,42,.07)`；深 `rgba(255,255,255,.06)` / `.09` | 122–123 / 606–607 |
| 行文字 | `--te-library-row-text` = `#334155` / 深 `#d8d8d8` | 120 / 604 |
| 行 hover 表面 | `rgba(255,255,255,.22)` / 深 `rgba(255,255,255,.065)` | 121 / 605 |
| 选中圆角 | `--te-library-selection-radius: 10px` | 127 |
| 选中左右内缩 | `--te-library-selection-inline-inset: 0px` | 128 |
| 封面圆角 | `--te-library-cover-radius: 8px` | 129 |
| 列表容器表面 | `--te-library-table-bg: rgba(255,255,255,.16)`，边框 `rgba(255,255,255,.52)`，阴影 `0 26px 78px rgba(86,70,160,.1)` | 117–119 |
| 图标 | `--te-library-icon: #64748b`，`--te-library-icon-size: 18px` | 125–126 |
| 底部操作区 | `--te-library-action-bg: rgba(37,99,235,.08)`，`--te-library-action-radius: 12px` | 131–132 |
| 标题区叠层 | `--te-library-title-overlay-opacity: 72%` | 130 |
| 密度模式 | `data-te-library-density: comfortable \| compact`（默认 comfortable） | theme-contract |
| 选中样式模式 | `data-te-library-selection: fill \| stroke`（默认 fill） | theme-contract |
| 曲目标题背景 | `--te-track-title-radius: 6px`，`--te-track-title-opacity: 0%` | 78–79 |

**深色坑**：`base.css` 深色块 600–603 原样重复了浅色值 ⇒ **深色下曲库列表表面仍是白色/浅色**（上游疑似 bug）。
`TwilightTheme` 按原样保留并加了注释；如果要"看起来对"，深色下应该覆盖成 `--te-card-bg` 系。

**列宽**：未找到权威值；建议按 `local-dashboard.png` 实测（本次未量，登记为待确认）。
`MusicRegion.SONG_HEIGHT = 26`（PVPUtils 现有布局）与 64px 差距很大，**不能直接复用**。

### 6.3 设置页（对照 `settings-light.png`，仅作外壳一致性核对）

- 左侧导航：顶部一个圆角搜索框（`--te-settings-search-bg: #eef0f3` / 深 `rgba(255,255,255,.08)`），
  下面是一列 22px 左右高度的项；选中项是白色胶囊 + 左侧蓝色指示条（`--te-settings-nav-active: #ffffff`）。
- 右侧主面板：一张大白色圆角卡片（`--te-settings-row-bg: #ffffff`），行内左"标题 + 描述"、右控件；
  开关是蓝色胶囊（`--te-primary-500`）；按钮是灰底胶囊（"选择文件夹"）；有一个 kbd 芯片样式。
- 令牌：`--te-settings-text #1a1a1a`、`--te-settings-text-muted #8a8f98`、
  `--te-settings-control-bg #ffffff`、`--te-settings-control-border rgba(15,23,42,.06)`、
  `--te-settings-panel-border transparent`、`--te-settings-shadow 0 2px 16px rgba(15,23,42,.04)`、
  `--te-settings-shadow-soft 0 1px 4px rgba(15,23,42,.04)`（92–103）；深色 578–589。
- **设置页组件 CSS 不在快照内** ⇒ 行高、面板内边距、卡片圆角**未找到**。

---

## 7. 沉浸播放页：外壳复刻 + AMLL 歌词接线

> **范围修正**（用户明确要求）：参考项目的歌词视觉**不复刻**；本工程保留自己的
> **AMLL（Apple Music-like Lyrics）**逐字歌词渲染器。因此本节把 `PlayingMusic.vue` 拆成
> 「页面外壳（照 Twilight 复刻）」与「歌词槽位（接本工程 AMLL）」两部分。

### 7.1 外壳规格（出处：`PlayingMusic.vue`）

根容器 `.playing-music`（811–823）：
`position: fixed; inset: 0; z-index: 1100; overflow: hidden; color: var(--te-playback-page-text, #f4f7fb);
background-color: var(--te-player-bg); background-image: var(--te-player-bg-image); background-size: cover;
--accent-color: var(--te-playback-accent, #7c4dff)`。
根上加 `class="bg-{blur|fluid|solid}"`，并有 inline `style="--accent-color: <dominantColor>"`（inline 覆盖 CSS）。

**背景层栈**（自下而上，全部 `position:absolute; inset:0`，`.backdrop` 是 `z-index: 0`）：

| 层 | 关键值 | 行 |
|---|---|---|
| `.backdrop-cover` 图 | `object-fit:cover; transform: scale(1.06); filter: blur(58px) saturate(1.22) brightness(0.52)`（light）/ `saturate(1.32) brightness(0.36)`（dark） | 872–894 |
| 切歌过渡 | `opacity 400ms --te-ease-out-strong`；过渡期间 `filter: blur(18px) …!important`（注释：58px 重滤镜不能与两层重叠共用） | 896–929 |
| `.backdrop-scrim` | `linear-gradient(180deg, rgba(5,7,11,.72) 0%, .74 52%, .78 100%)` + `color-mix(--accent-color 8%, transparent)`，`backdrop-filter: blur(10px)` | 931–946 |
| `.backdrop-accent` | `radial-gradient(circle at 18% 26%, color-mix(--accent-color 22%), transparent 42%), radial-gradient(circle at 88% 20%, rgba(255,255,255,.12), transparent 26%)`，`opacity: .8` | 948–964 |
| `.backdrop-fluid` | `::before { inset:-50%; background-size:400% 400%; animation: fluid-drift-transform 18s ease-in-out infinite }`，keyframes 走 `(0,0)→(-12%,0)→(-12%,-12%)→(0,-12%)` | 966–1018 |
| `.backdrop-solid` | 纯 `--te-player-bg` | 986–994 |

**舞台**：`.stage { position:relative; z-index:1; width: min(100%, 1560px); height:100%; margin:0 auto;
padding: 72px 36px 28px }`。
`≤1120px` → `padding: 38px 22px 20px`；`≤760px` → `padding: 34px 16px 16px`，title 28px、artist 16px。

**布局网格**：`.layout { display:grid; grid-template-columns: minmax(300px, 360px) minmax(0,1fr);
grid-template-rows: minmax(0,1fr); gap: var(--te-lyric-cover-gap, 40px); align-items:stretch; height:100% }`。
无歌词时 `.layout--single { grid-template-columns: minmax(300px,440px); align-content:center; justify-content:center }`。

**封面列**：`.cover-column { display:flex; flex-direction:column; gap:18px; align-self:center }`；
`≥1121px` 时 `transform: translateX(clamp(42px, 5vw, 80px))`。
`.cover-frame { width: var(--te-playback-cover-size, 100%); aspect-ratio: 1;
border-radius: var(--te-playback-cover-radius, 26px); overflow:hidden;
background: rgba(15,23,42,.08); box-shadow: 0 26px 70px rgba(15,23,42,.28) }`（dark：`rgba(15,23,42,.45)` / `0 26px 70px rgba(0,0,0,.55)`）。
**封面尺寸没有 clamp/vh**：直径 = 网格轨道宽（`minmax(300px,360px)`，≤1120px 时 `minmax(132px,180px)`）× `--te-playback-cover-size`。
入场：`te-playing-artwork-arrive var(--te-motion-page=400ms) var(--te-ease-spring) both`，`from { opacity:0; scale:.9 }`。
占位：`.cover-placeholder { font-size:68px; color: rgba(255,255,255,.34);
background: linear-gradient(135deg, rgba(255,255,255,.08), rgba(255,255,255,.02)), color-mix(--accent-color 18%, transparent) }`。

**曲目信息**：`.cover-meta`（`te-playing-meta-arrive 280ms --te-ease-spring 36ms both`，`from { opacity:0; translate: 0 12px }`）
- `.track-title { font-family: --te-font-display; font-size: 32px; font-weight:400; line-height:1.22; color: var(--te-playback-track-title, #fff); -webkit-line-clamp:2 }`
- `.track-artist { margin-top:10px; font-family: --te-font-rounded; font-size:18px; font-weight:700; color: rgba(255,255,255,.78) }`
- `.track-album { margin-top:4px; font-size:14px; font-weight:500; color: rgba(255,255,255,.48) }`

**外壳上唯一的浮动控件**（本文件内**没有**返回按钮 / 播放控制条 / 进度条 / 音量 / 队列 / 收藏——那些在 `PlayerBar.vue`）：

| 控件 | 值 | 行 |
|---|---|---|
| `.visualizer-toggle-button` | `position:fixed; top:42px; left:42px; width:40px; height:40px; border-radius:999px;
background: rgba(255,255,255,.08); backdrop-filter: blur(10px); border:1px solid rgba(255,255,255,.1);
color: rgba(255,255,255,.7); font-size:16px; z-index:1200`；hover `background .14 / border .16 / color .92 / scale(1.06)` | 1706–1739 |
| 同上 `--close` | `z-index: 10000` | 1741–1750 |
| `.time-chip`（歌词列头部） | `padding: 8px 12px; border-radius:999px; border:1px solid rgba(255,255,255,.1);
background: rgba(255,255,255,.08); color: rgba(255,255,255,.7); font-size:12px; tabular-nums` | 1228–1237 |
| `.player-appearance-menu`（右键菜单） | `position:fixed; z-index:10000; width:218px; padding:5px; border:1px solid --te-card-border;
border-radius:6px; box-shadow: --te-glass-shadow; background: --te-card-bg`；按钮 `min-height:36px; gap:9px; padding:0 9px; border-radius:4px`；
定位 `x = max(8, min(clientX, innerWidth-210))`、`y = max(8, min(clientY, innerHeight-52))` | 70–76, 825–854 |

**z-index / 模糊汇总**：`.playing-music` 1100 → `.backdrop` 0 → 背景图 `blur(58px)`（过渡期 18px）→
`.backdrop-scrim` `blur(10px)` → `.stage` 1 → `.visualizer-toggle-button` 1200（close 态 10000）→
`.player-appearance-menu` 10000。`full-cover` 布局下 `.cover-meta` 是 `z-index:1` + `blur(18px) saturate(130%)`。

**布局模式**（`html[data-te-player-layout]`，默认 `standard`）：

| 模式 | 网格 / 关键差异 | 行 |
|---|---|---|
| `standard` | `minmax(300px,360px) minmax(0,1fr)`，封面列右移 `clamp(42px,5vw,80px)` | 1071–1075 |
| `full-cover` | 封面铺满整个 `.layout`（`border-radius:0`），`.cover-meta` 绝对定位到 `left: clamp(28px,6vw,92px); bottom:118px; width: min(520px, 100vw-56px); padding:18px 20px; border-radius:8px` + `blur(18px) saturate(130%)` | 1813–1856 |
| `lyrics-focus` | `minmax(150px,220px) minmax(0,1fr)`，`gap: clamp(24px,4vw,64px)`，封面列 `margin-top:10vh`，title 24px | 1872–1897 |
| `split` | `minmax(280px,.82fr) minmax(420px,1.38fr)`，`gap: clamp(32px,5vw,84px)`，封面 `width: min(100%,520px)` 右对齐 | 1895–1904 |
| `minimal` | `minmax(280px,460px)` 居中，隐藏歌词列与 artist/album | 1906–1951 |

可见性开关：`data-te-visible-player-artwork / -track-info / -duration / -misc-icons`（`false` 即隐藏，1926–1931）。

### 7.2 歌词槽位（**不复刻参考歌词，只取盒子**）

外来渲染器要落进去的槽位（模板 693–714）：

```
.playing-music  (fixed inset:0; z-index:1100; inline --accent-color)
└─ .stage        (z-index:1; width min(100%,1560px); padding 72px 36px 28px)
   └─ .layout    (grid 2 列; gap var(--te-lyric-cover-gap,40px); height 100%)
      └─ section.lyrics-column        ← 槽位根
         │   min-height:0; display:flex; flex-direction:column;
         │   padding-left:6px; align-self:stretch;
         │   transform: translateX(var(--te-lyric-offset-x, 0px))
         ├─ .lyrics-head               (flex; align-items:end; justify-content:flex-end;
         │                              gap:16px; padding-bottom:18px)  ← 放 time-chip
         └─ .lyrics-scroll             ← 外来渲染器的自然挂载点
             position:relative; flex:1; min-height:0; overflow:hidden;
             padding-right:8px; scrollbar-width:none;
             mask-image: linear-gradient(to bottom,
               transparent 0%, rgba(0,0,0,.26) 6%, rgba(0,0,0,1) 18%,
               rgba(0,0,0,1) 82%, rgba(0,0,0,.26) 94%, transparent 100%)
```

`.lyrics-list` 的 `max-width: var(--te-lyric-max-width, 820px)` 只作用于内容层，槽位本身不管。
**`standard` 布局下 `.lyrics-column` 自身没有 background / backdrop-filter**；
`full-cover` 布局显式写 `background: transparent; backdrop-filter: none`（1858–1870）。
⇒ 歌词**永远在背景层之上、且不带自己的底色**，可读性靠 `.backdrop-scrim`（`rgba(5,7,11,.72~.78)` + `blur(10px)`）保证。

**Java 映射**：

| Twilight 槽位 | 本工程对应 |
|---|---|
| `.lyrics-column`（flex column, min-height:0） | 一个矩形区域 `lyricLeft/Top/Right/Bottom` |
| `.lyrics-head` + `.time-chip` | 右上角一个 `padding 8/12`、圆角 pill 的时间胶囊（用 `FlatUiRenderer.fill` 画 `rgba(255,255,255,.08)` + 1px `rgba(255,255,255,.1)`） |
| `.lyrics-scroll`（`overflow:hidden` + mask） | 传给 `AppleLyricPlayer.setContentWidth()/setContainerHeight()` 的矩形；上下淡出**不需要 mask**——AMLL 的 `AmlVisual.opacity` 已按 `inViewport` 归零 |
| `--te-lyric-cover-gap: 40px` | 封面列右缘与歌词区左缘的间距常量（可做成设置项） |
| 需剥离的 JS 耦合 | `lyricsEl` ref + `ResizeObserver`、`createLyricViewportController`、`measurePlaybarReservedPx()`、`LYRIC_SCROLL_FRAME_FALLBACK_MS=120`、`LYRIC_ACTIVE_ANCHOR_RATIO`、`INTERLUDE_DOTS_HEIGHT_PX=24`、`LYRIC_ROW_GAP_PX=10`（201–204）——这些**本工程 AMLL 已有等价实现**，不复刻 |

### 7.3 接线方式（照 `PlayerDetailOverlay` 的现成范本）

`clickgui2/music/PlayerDetailOverlay.java:146-194` 已经写好了"外壳里塞 AMLL"的完整模式：

```java
// 1) 外壳先画背景（GuiGraphics）
graphics.fill(b.left, top, b.right, panelBottom, 0xE7080A0E);
drawCover(graphics, song.coverUrl(), ...);            // 封面
graphics.fill(...);                                    // 遮罩
// 2) 配置 AMLL
appleLyrics.setContentWidth(right - left);
appleLyrics.setContainerHeight(bottom - top);
if(song.id() != appleSongId){ appleSongId = song.id();
    appleLyrics.setLyricLines(lyrics, player().getAdjustedLyricPositionMs()); }
appleLyrics.setPlaying(player().getState() == PLAYING);
appleLyrics.setCurrentTime(player().getAdjustedLyricPositionMs(), false);
appleLyrics.update();
// 3) 一次 Skia region 画完歌词，失败则回退 MC 字体路径
try{ canvas = SkiaRegionRenderer.get().beginRegion(left, top, w, h); }catch(Throwable t){}
if(canvas != null){
    try{ appleLyrics.renderSkia(canvas, left, top, right, bottom); }
    finally{ SkiaRegionRenderer.get().endRegion(graphics); }
    return;
}
appleLyrics.render(graphics, left, top, right, bottom);   // 回退
```

**移植时要注意的三条硬约束**：

1. **一层级**：Skia region 是 `clear(0x00000000)` 后 `blit`，所以它是**覆盖式**的——必须在外壳（背景 / 封面 / 遮罩 / 强调色光斑）**全部画完之后**再 `beginRegion`。
2. **一帧一区域**：`SkiaRegionRenderer.beginRegion` 在 `regionDrawing == true` 时直接返回同一个 canvas ⇒ **一帧内不能开第二个 Skia 区域**。如果沉浸页想用 Skia 同时画封面圆角/旋转和歌词，必须**合并成一个区域**，或者在 `twilight` 包内自建第二个 region 渲染器。
3. **像素上传成本**：region 像素 = 区域尺寸 × guiScale²，每帧全量 `glTexSubImage2D`。沉浸页歌词区大约是 `(1560-360-40) × (H-100)`；4K + guiScale 3 时很容易上千万像素/帧，需要在实测里定一个上限（或降低 guiScale 下的区域尺寸）。

### 7.4 本工程 AMLL 代码清单（类名 + 职责，便于接线）

包：`net.wurstclient.music.apple`

| 类 | 职责 | 关键常量 / API |
|---|---|---|
| **`AppleLyricPlayer`**（53 KB） | 歌词舞台总控：行布局、弹簧滚动、遮罩、强调动画、两条渲染路径 | `setLyricLines/setContainerHeight/setContentWidth/setFontSize/setPlaying/setEnableBlur/setEnableScale/setHidePassedLines/setMaskMode/setCurrentTime(t,seek)/scroll(delta)/resetScroll/hitLine/lineTime/isEmpty/update/render(GuiGraphics)/renderSkia(Canvas)`；内部 `LineRender`（y / isInViewport / isActive / opacity / blurLevel / text / translation / romanization / background / hasRuby）；`shouldEmphasize(LyricWord)` |
| `AppleLayout` | `layout.ts` 1:1：前缀和行高、焦点度量、视口起点、滚动边界 | `ALIGN_POSITION = 0.35F`、`OVERSCAN_PX = 300F` |
| `AppleTimeline` | `timeline.ts` 1:1：播放行 / 高亮行 / scrollToIndex / 间奏判定 / seek 二分 | `INTERLUDE_THRESHOLD_MS = 4_000` |
| `AmlVisual` | 视觉规则：`resolveIsActive` / `resolveOpacity` / `resolveBlurLevel` / 缩放 / 逐字遮罩几何 | `HIGHLIGHTED_OPACITY=.85`、`NON_DYNAMIC_OPACITY=.2`、`INACTIVE_SCALE=97`、`INACTIVE_BG_SCALE=75`、`MAX_BLUR_PX=5`、`WORD_FADE_WIDTH=.5`、`SOLID_MASK_ALPHA=.2`、`ACTIVE_DARK_MASK_ALPHA=.4`、`BG_LINE_SCALE=.7`、`SUB_LINE_SCALE=.5`、`maskEdges()` / `maskAlphaAt()` |
| `AmlTween` | CSS `transition` 等价（目标中途改变从当前值重算） | opacity/blur 0.4s、遮罩 alpha 0.3s/0.45s |
| `AmlEasing` | `bezIn` / `bezOut` / 32 帧强调缓动 `empEasing` | `AmlEmphasize.FRAMES = 32` |
| `AmlEmphasize` | 逐字 ease-out 上浮 + 强调词的缩放/位移/白色辉光 | `FLOAT_EM` 相关 |
| `AmlMask` | 渲染**前**对文本套用遮蔽（改变字宽，故必须前置） | `DEFAULT_MASK_CHAR='*'`、`Mode` |
| `AmlOptimize` | `optimize-lyric.ts`：空格规范化 → 行时间戳回填字级 → 连续背景人声降级 → 时间同步 → 稳定排序 → 清洗重叠 → 提前开始 | – |
| `AmlLineBalancer` | `calcBalancedBreaks` 动态规划选换行点（标点 > 空格 > CJK 词界 > 普通文本） | – |
| `AmlLayoutReason` | `LayoutReason` 与策略表；连续拖动才 `snapPosY` | – |
| `InterludeDots` | 间奏三点：入场 `easeOutExpo` 放大、正弦呼吸、逐点点亮、`easeInOutBack` 收缩 | – |
| `LyricWordSplitter` | `is-cjk.ts` + `lyric-split-words.ts`：CJK 逐字成词、连续非 CJK 合并 | – |
| `Spring` | `utils/spring.ts` 1:1 解析解弹簧（阻尼比 ≥1 过阻尼、<1 欠阻尼余弦、delay 队列、三重 arrived 判定） | – |
| `LyricLine` / `LyricWord` / `LyricRuby` / `LyricParser` | 数据模型与解析（含 YRC 逐字） | – |

**周边**：`net.wurstclient.music.NeteaseMusicPlayer`（`getAdjustedLyricPositionMs()` / `getLyrics()` / `getState()` /
`seekTo` / `playPrevious` / `playNext` / `toggle` / `setVolume` / `getVolume` / `getDurationMs` / `getPositionMs` /
`adjustLyricOffset` / `resetLyricOffset`）、`NeteaseSong`、`NeteaseImageCache`、`MusicContext`、`MusicRegion`。

**结论**：歌词这条线**不需要任何新代码**，只要把 §7.2 的盒子尺寸喂给 `AppleLyricPlayer` 并调 `renderSkia`。
当前唯一消费者是 `PlayerDetailOverlay`（一个居中的"窗口式"覆盖层，`MusicRegion.PANEL_WIDTH=740 × PANEL_HEIGHT=500`）；
新的沉浸页外壳是**全屏版**，参数完全不同，需要一个新的 `MusicRegion` 子类。

---

## 8. 登录页规格（`LoginPage.vue`）

### 8.1 壳与配色

| 选择器 | 值 | 行 |
|---|---|---|
| `.login-page` | `position:fixed; inset:0; z-index:2000; display:grid; place-items:center; background: --te-streaming-bg; overflow:hidden; font-family: --te-font-sans` | – |
| 局部调色板 | `--lp-surface: --te-card-bg`；`--lp-text: --te-neutral-900`；`--lp-muted: --te-neutral-500`；`--lp-line: --te-card-border`；`--lp-inset: --te-subtle-bg`；`--lp-tint: color-mix(--te-primary-500 10%, transparent)`；`--lp-tint-strong: …18%…`；`--lp-shadow: 0 18px 44px color-mix(--te-neutral-900 8%, transparent)`；`--lp-shadow-lift: 0 24px 56px …13%…`；`--lp-shadow-soft: 0 1px 4px …8%…` | – |
| `.lp-shell` | `width: min(496px, 100vw - 48px); max-height: calc(100vh - 110px); border-radius: 22px; overflow:hidden; background: --lp-surface; border: 1px solid --lp-line; box-shadow: --lp-shadow; animation: shellIn 0.55s --te-ease-spring both` | – |
| `shellIn` | `from { opacity:0; translateY(22px) scale(.975) }` → `to { opacity:1; translateY(0) scale(1) }` | – |
| `.lp-shell::before` | `top:-120px; left:50%; width:560px; height:260px; translateX(-50%); background: radial-gradient(ellipse at center, color-mix(--te-primary-500 12%, transparent), transparent 68%)` | – |
| `≤600px` 高 | `max-height: calc(100vh - 72px)` | – |
| **backdrop-filter** | **未找到**（整个文件只有 QR 过期遮罩有一处 `blur(6px)`） | – |
| `.sky-blob` 天幕 | `filter: blur(110px); border-radius:50%; animation: skyDrift 22s ease-in-out infinite alternate`（b 卡 delay `-9s`）；a = `520×520 top:-180px right:-120px color-mix(primary-500 18%)`；b = `420×420 bottom:-160px left:-120px color-mix(primary-300 20%)` | – |
| `.sky-halo` | `inset:-30%; radial-gradient(ellipse at 50% 38%, primary 7%, transparent 55%)` | – |

### 8.2 二维码区（`.qr-stage { gap:18px; padding-top:6px }`）

| 选择器 | 值 |
|---|---|
| `.qr-frame` | `width:214px; height:214px; display:grid; place-items:center; border-radius:22px; background:white; box-shadow: var(--lp-shadow), 0 0 0 1px var(--lp-line); overflow:hidden` |
| `.qr-image` | `width:178px; height:178px; object-fit:contain` ⇒ 四周各 **18px 白边**（静默区） |
| 位图生成 | `QRCode.toDataURL(qr.qrContent \|\| qr.key, { margin: 1, width: 220 })`，或直接用 provider 的 `qr.imageDataUrl` |
| `.qr-corner` | `width:22px; height:22px; border:2.5px solid var(--te-primary-500); z-index:2`；四角内缩 10px，各去掉两条边，`border-*-radius: 10px` |
| `.qr-scanline` | `left:14px; right:14px; height:44px; background: linear-gradient(to bottom, transparent, color-mix(primary 22%)); border-bottom: 2px solid color-mix(primary 70%)`；`scanSweep 2.4s ease-in-out infinite`：`translateY(-8px) opacity 0` → 12% `opacity 1` → 50% `translateY(176px)` → 88% `opacity 1` |
| `.qr-expired-overlay` | `inset:0; z-index:3; gap:10px; background: color-mix(--lp-surface 86%, transparent); backdrop-filter: blur(6px); color: --lp-muted; font-size:13px; font-weight:600` + 24×24 刷新 SVG（stroke-width 2） |
| `.qr-status` | `gap:8px; font-size:13px; font-weight:500`；`.scanned` → `color: --te-success-soft-fg` |
| `.qr-dot` | `8×8; border-radius:50%`；`.waiting` 主色 + `dotPulse 1.6s`；`.scanned` 成功色 + `1s`；keyframes `opacity 1→.45`、`scale(1)→(.72)` |
| 轮询 | `POLL_INTERVAL = 5000`；`QR_KEY_COOLDOWN = 5000`；`SUCCESS_LINGER_MS = 1200`；`document.hidden` 时跳过 |
| 状态码 | `{ waiting:801, scanned:802, expired:800, denied:undefined, success:803 }` |
| **中央 logo 叠加** | **未找到** |
| **倒计时环** | **未找到**（过期靠状态机 + 遮罩点击刷新；倒计时只存在于短信登录的封禁冷却，纯文本） |

### 8.3 表单

| 选择器 | 值 |
|---|---|
| `.account-form` | `flex column; gap:9px; width: min(340px, 100%); margin: 0 auto` |
| `.field-label` | `margin-top:7px; font-size:12px; font-weight:600; color: --lp-muted` |
| `.field-row.phone` | `grid-template-columns: 64px minmax(0,1fr)`；`.captcha` = `minmax(0,1fr) 112px`；`gap:10px` |
| `.field-input` | `height:46px; padding:0 15px; border:1px solid --lp-line; border-radius:13px; background: --lp-inset; font-size:14px` |
| focus | `border-color: --te-primary-500; background: --lp-surface; box-shadow: 0 0 0 3px --lp-tint` ⇒ **3px 扩散阴影、offset 0、无 outline** |
| `.btn-captcha` | `height:46px; border-radius:13px; background: --lp-tint; color: --te-primary-500; font-size:12px; font-weight:600`；disabled `opacity .55` |
| `.btn-primary` | `height:46px; padding:0 30px; border-radius:999px; background: linear-gradient(135deg, --te-primary-500, --te-primary-400); color:white; font-size:14px; font-weight:600; letter-spacing:.02em; box-shadow: 0 8px 24px color-mix(primary 34%)`；hover `translateY(-2px)` + `500ms`；active `scale(.97)` + `90ms` |
| `.btn-secondary` | `height:40px; padding:0 22px; border-radius:999px; background: --lp-inset; font-size:13px` |
| `.btn-ghost-danger` | `height:46px; padding:0 24px; border-radius:999px; background: --te-danger-soft-bg; color: --te-danger-soft-fg; font-size:13px` |
| `.method-tabs` | `grid 3×minmax(0,1fr); gap:4px; padding:4px; border-radius:999px; background: --lp-inset; width: min(360px,100%)` |
| `.method-tab` | `height:36px; border-radius:999px; font-size:13px; font-weight:600`；`.active` → `background: --lp-surface; color: --te-primary-500; box-shadow: --lp-shadow-soft` |
| `.kind-chip` | `height:30px; padding:0 16px; border:1px solid --lp-line; border-radius:999px; font-size:12px` |
| `.form-message` | `min-height:18px; font-size:12px; line-height:1.6; text-align:center; color: --lp-muted`（**错误也用 muted 灰，不是危险色**） |
| 错误页 | `.error-badge { 58×58; border-radius:50%; background: --te-danger-soft-bg; color: --te-danger-soft-fg }`；`.error-text { max-width:340px; font-size:13px; line-height:1.7; color: --te-danger-soft-fg }` |
| 加载 | `.lp-spinner { 34×34; border:3px solid --lp-tint-strong; border-top-color: --te-primary-500; spin .8s linear infinite }`；`.btn-spinner { 14×14; border:2px solid color-mix(white 40%); border-top-color:white; spin .7s }` |
| **"或"分隔线** | **未找到** |
| **协议 checkbox** | **未找到** |

### 8.4 品牌与节奏

**没有位图 logo / wordmark**。品牌锚点是 `.title-glyph`（`34×34; border-radius:11px; background: --lp-tint; color: --te-primary-500; font-size:17px`）
与 `.provider-glyph`（`48×48; border-radius:15px; font-size:21px`）。
`.stage-title { font-family: --te-font-display; font-size: 23px; font-weight:800; letter-spacing:-.01em }`；
`.stage-sub/.stage-muted` = 13px。
`.chip-back`（返回药丸）`height:28px; padding: 0 12px 0 8px; border-radius:999px; background: --lp-inset; font-size:12px; font-weight:600`，
hover `translateX(-1px)`，内含 13×13 SVG。
垂直节奏：`.stage-view { gap:20px; padding: 38px 40px }`；`.stage-center { gap:14px; padding:38px 40px; min-height:340px }`；
`.stage-head gap:8px`；`.provider-list gap:12px`；`.qr-stage gap:18px`；`.account-form gap:9px`；`.field-row gap:10px`。
阶段切换 `<Transition mode="out-in">`：`opacity + transform var(--te-motion-panel=280ms) --te-ease-soft`，enter `translateY(14px)`，leave `translateY(-10px)`。
`.provider-row { grid-template-columns: 48px minmax(0,1fr) auto auto; gap:15px; padding:15px 18px;
border:1px solid --lp-line; border-radius:18px; animation: rowIn .5s --te-ease-spring both; animation-delay: calc(var(--d) * 70ms) }`；
`.provider-avatar { 30×30; border-radius:50%; border:2px solid --lp-surface }`；
`.provider-pill { height:19px; padding:0 8px; border-radius:999px; font-size:11px }`；`.provider-name 15px/600`；`.provider-desc 12px`。
`.id-card { border-radius:22px; box-shadow: --lp-shadow }`；`.id-banner { height:112px }`；
`.id-avatar-slot { margin-top:-46px; padding:5px; border-radius:50% }`；`.id-avatar { 92×92 }`；`.id-name { 25px/800 }`；
`.id-stats { gap:30px; margin-top:20px; padding:14px 30px; border-radius:16px; background: --lp-inset }`（strong 17px / span 11px）；
`.id-watermark { font-size:96px; right:26px; bottom:-14px }`。
成功页：`.success-burst { 92×92 }` + `burstRing .9s`；`.success-check { 62×62 }`；
`.success-circle { stroke-width:2.4; dasharray:152; drawStroke .7s .1s }`；`.success-tick { stroke-width:3.4; dasharray:36; drawStroke .4s .55s }`。

### 8.5 本工程侧对应能力

- **二维码位图**：`com.google.zxing:core:3.5.3` 已在依赖里（`build.gradle:144`），
  `clickgui2/music/LoginPage.java:631` 已有 `QRCodeWriter().encode(url, QR_CODE, 37, 37, hints)` + 逐模块 `graphics.fill`。
  ⇒ **214×214 白框 + 178×178 位图 + 18px 静默区 + 3px 四角装饰 + 扫描线**全部可做（扫描线就是一个 `translateY` 动画的渐变矩形）。
- **数据层**：`NeteaseMusicPlayer.beginQrLogin()` / `checkQrLogin(key)` 与 `NeteaseCloudApi.QrStatus`
  （`WAITING / SCANNED / EXPIRED / SUCCESS / ERROR`）已经就绪，`clickgui2/music/LoginPage.java` 里已有完整轮询状态机（`nextQrPoll` / `qrGeneration` / `POLL_INTERVAL` 语义）。
- **缺口**：QR 过期遮罩用到 `backdrop-filter: blur(6px)` ⇒ 本工程可用 `RiseFrostedGlass`（但 6px 太小，`σ=6` 固定，会明显偏糊，建议改用 86% 不透明底 + 无模糊）。
- **不做**：天幕 `.sky-blob` 的 `blur(110px)` 大色球（可以做，但 `RiseFrostedGlass` 是采样屏幕而非采样自己的 alpha，做不出"自身颜色被模糊"的效果 ⇒ 用同心渐隐圆近似或放弃）。

---

## 9. 封面取色 & 与 AMLL 歌词层的叠加关系（专章）

### 9.1 参考实现的取色链路

1. `usePlayerStore` 暴露 `dominantColor`（**算法本体不在快照内 ⇒ 未找到**）。
2. `PlayingMusic.vue:620` 把它写成根元素的 inline style：`--accent-color: <dominantColor>`。
   CSS 里同一元素还声明了 `--accent-color: var(--te-playback-accent, #7c4dff)`（822），
   **inline 优先级更高**，所以 `--te-playback-accent` 只在 inline 为空时生效；而该变量在 base.css 里**未定义**（base.css 的 playback 变量只有 `--te-playback-cover-size: 100%`，行 133）。
3. **消费点只有 3 处，全在背景/占位图上**：
   - `.backdrop-scrim`：`color-mix(in srgb, var(--accent-color) 8%, transparent)` 叠在 72–78% 的黑色渐变之上（行 936–941）
   - `.backdrop-accent`：`radial-gradient(circle at 18% 26%, color-mix(--accent-color 22%, transparent), transparent 42%)`，整层 `opacity: .8`（948–964）
   - `.cover-placeholder`：`color-mix(--accent-color 18%, transparent)`（dark 22%，1125–1143）
4. **主页的紫色不是动态取色**：`StreamingHome.vue` 里 `dominantColor` 一次都没出现，
   `providerColor` prop 声明后从未使用。主页的紫/青全部来自静态令牌 `--te-primary-500` / `--te-accent-cyan` 的 12% 混色。
   ⇒ **任务书里"streaming-home 紫 / local-dashboard 绿 = 封面取色"这个推断，在主页这一页不成立**（主页是恒定的蓝紫）。
   真正随封面变色的是沉浸页与播放条。
5. **播放条**：`PlayerBar.vue` 引用 `dominantColor`（107 / 206 / 1693 三处）⇒ 播放条的"正在播放"胶囊与强调色确实随封面变。

### 9.2 本工程已有的取色实现（可直接用，不需新代码）

`clickgui2/music/NeteaseImageCache.sampleAccent(NativeImage)`（源码 80–115）：

```
步长 stepX = max(1, W/8), stepY = max(1, H/8)     → 最多 8×8 = 64 个采样点
跳过 alpha < 160 的像素                            → 排除透明边
跳过 luminance < 0.14 或 > 0.9 的像素              → 排除纯黑/纯白
luminance = 0.299R + 0.587G + 0.114B（/255）
saturation = (max - min) / max
score = saturation * (1 - |luminance - 0.45| * 1.4)
取 score 最大者 → 0xFF<<24 | RGB                    → 强制不透明
兜底色 DEFAULT_ACCENT = 0xFF007CFF
```

配套 `MusicRegion.lerpAccent(from, to, t)`（368–385）会在结果亮度 < 0.35 时**提亮**（`boost = min(1.6, 0.35/luminance)`），
用于保证强调色在深色底上可读。当前强调色存在 `MusicContext.accentColor`，`MusicRegion.accent()` 取用。

**与参考的差异与调参建议**：
- 参考的 `dominantColor` 是"主色"（面积最大），本工程是"最饱和且中亮的颜色"。
  结果是本工程更容易选到封面上一个**很小的鲜艳色块**，观感更跳。若要对齐 Twilight，
  应改成"量化到 4×4×4 直方图取最大值 bin，再按亮度加权"——**这属于数据层改动，不在本次 UI 范围**。
- 参考只用 8% / 22% 两个很低的强度，本工程的 `PlayerDetailOverlay` 用法偏重（描边 45%、辉光 70–110/255）。
  移植时应把强度降到参考值。

### 9.3 与 AMLL 歌词层的叠加关系（层级 / 透明度 / 模糊半径）

**参考实现的层级（从下到上）**：

| z | 层 | 不透明度 | 模糊 |
|---|---|---|---|
| 1100 | `.playing-music` 根（`--te-player-bg`） | 1.0（`--te-tp-surface` 透明窗时例外） | – |
| 0 | `.backdrop-cover` 封面大图 | 1.0，封面本身被 `scale(1.06)` | **`blur(58px)`**（light 另加 `saturate(1.22) brightness(0.52)`；dark `saturate(1.32) brightness(0.36)`）；切歌过渡期降到 `blur(18px)` |
| 0 | `.backdrop-scrim` | 黑 `rgba(5,7,11,.72→.78)` + 强调色 8% | **`backdrop-filter: blur(10px)`** |
| 0 | `.backdrop-accent` | `opacity: .8`，强调色 22% 径向 | – |
| 1 | `.stage` → `.cover-column` / `.lyrics-column` | 1.0（**歌词列自身无底色、无 backdrop-filter**） | – |

⇒ **结论：强调色只影响背景层，从不给歌词本身染色。**
歌词的可读性完全由"58px 模糊背景 + 72–78% 黑 + 10px 背景模糊"这套暗底保证；
歌词自己的透明度/模糊由歌词渲染器内部规则决定（参考实现是 `--te-playback-lyric-*` 系列 + `.lyric-row.active` 的缩放，
**本工程不复刻**，改用 `AmlVisual` 的 0.85 / 0.2 / 97% / 5px 那一套）。

**本工程落地时的层级方案（GuiGraphics 绘制顺序 = 从下到上）**：

```
1. 全屏底：accen 无关的 --te-player-bg（TwilightTheme.appBg / cardBg 系）
2. 封面大图：cover-fit blit + 自绘的 box-blur（G2 缺口）
   —— 若走 RiseFrostedGlass：captureFrame() 已在 HudManager 每帧调用，可直接 draw() 出模糊块，
      但它模糊的是"屏幕当前内容"，所以必须先把封面大图铺满屏幕、再 draw 玻璃块，才能得到"封面被模糊"的效果
3. 暗底：graphics.fill(0,0,W,H, 0xC0050B0B 系) ← 对应 rgba(5,7,11,.72~.78)
4. 强调色光斑：用 8~16 段同心圆角矩形近似 radial 22%（G3 缺口），整层 alpha 0.8
5. 封面列：cover-frame 圆角 + cover-fit 图 + 投影
6. 曲目信息：32px 标题（苹方 semibold 顶替 400 字重）/ 18px 艺人 / 14px 专辑
7. 时间胶囊 / 可视化切换按钮：pill 底 + 1px 亮描边 + blur(10px)
8. 【最后】SkiaRegionRenderer.beginRegion(歌词矩形) → AppleLyricPlayer.renderSkia(...) → endRegion(graphics)
```

**关键约束**：第 8 步必须是**本帧唯一的 Skia region**（见 §7.3 约束 2），
而且 AMLL 的逐字遮罩/模糊/辉光全都发生在它自己的 canvas 里，所以第 1–7 步怎么画都不影响歌词观感——
**这正是"外壳复刻 + 歌词保留"能够成立的架构原因**。

---

## 10. 诚实结论

### 10.1 可以做到"看起来 1:1"

| 项 | 为什么能 |
|---|---|
| **侧栏**（浮动面板 22px 内缩、右缘 26px 圆角、82/18 混色底、135° 线性渐变系背景、菜单项 45px/13px 圆角/5px 间距/左侧 3px 指示条、1px `--sf-shell-line` 边框） | 全是圆角矩形 + 纯色 + 1px 描边 + 一层模糊，本工程齐活 |
| **标题栏**（54px、无边框无投影无模糊、36px / 4px 圆角按钮、`--te-chrome-text`） | 几乎是"画两个矩形 + 6 个按钮" |
| **播放条**（控件 32 / 播放 44 / 间距 12 / 进度 6px 圆角 999 / **水平渐变填充 `#2563eb→#0d9488`** / 时间胶囊 8px 圆角 `rgba(37,99,235,.08)`） | 进度条填充是**水平**线性渐变，正好落在 `FlatRenderer.GradientColorFn` 能力内 |
| **主页 Chart 列表**（`34px 48px 1fr auto`、gap 14、padding 9/12、圆角 14、序号 20px/800、封面 48px/圆角 10、三柱跳动 EQ） | 纯矩形 + 文字 + 三个会动的矩形 |
| **主页 Shelf 网格**（`auto-fill minmax(172px,1fr)`、gap 24/20、封面 1:1/圆角 16、hover 上浮 4px + 图片放大 1.06 + 底部黑色 scrim + 数量 pill + 白色圆形播放钮） | 同上，`auto-fill` 用 `(w+gap)/(172+gap)` 整除复刻 |
| **骨架屏 shimmer**（1.5s 横扫的白色渐变带） | `System.currentTimeMillis()` 驱动一个半透明白矩形横移 |
| **登录页二维码区**（214 白框 / 178 位图 / 18px 静默区 / 圆角 22 / 2.5px 四角装饰 22×22 内缩 10 / 扫描线 2.4s / 状态 pill + 呼吸点 / 过期遮罩可点刷新） | ZXing 已在依赖里、状态机已在 `clickgui2/music/LoginPage.java` 里；四角装饰用 4 段 2px 描边即可 |
| **沉浸页背景层**（封面 58px 模糊 + `scale(1.06)`、72–78% 黑 + 8% 强调色、22% 强调色径向光斑 opacity .8、`--te-player-bg` 兜底） | 模糊用 `RiseFrostedGlass`；黑/强调色用 `fill`；径向用同心近似 |
| **封面取色**（8% / 22% 两处低强度应用） | `NeteaseImageCache.sampleAccent` 已实现，只需调强度 |
| **所有动效时长与曲线**（90 / 160 / 220 / 280 / 400 / 500ms + 5 条 cubic-bezier） | `Spring`（解析解）+ `AnimFloat` + 自写 20 行 `cubicBezier`（G11） |

### 10.2 只能做到"功能等价、视觉近似"

| 项 | 差在哪 | 能到什么程度 |
|---|---|---|
| **圆角 ≥20px 的弧线** | 本工程圆角最多 16 段/角（§2.1）；22–26px 圆角会出现可见折线 | 85–92% 相似；走 Skia（G1 自写渲染器）可到 99% |
| **字体与字重** | 没有 Inter / Plus Jakarta Sans / MiSans / Space Grotesk；苹方只有 3 字重；MC 位图字体 14px 需放大 1.56× 会糊 | 走 Skia 矢量渲染可达 **95%**（字宽/字距会变，因为字族不同）；走位图字体只有 **70%** 且明显发糊 |
| **投影衰减** | CSS `box-shadow` 是高斯衰减，本工程是 6 层外扩或单层硬偏移 | 90%；把 `drawSoftShadow` 的层数从 6 提到 10–12 可到 95% |
| **径向 / 圆锥渐变** | 无原生支持（G3） | 同心近似 80%；走 Skia 可达 99% |
| **全景毛玻璃 `blur(26px) saturate(140%)`** | `RiseFrostedGlass` 的 σ 固定为 6，且不支持 saturate | 模糊方向对了，但侧栏后面的内容会比参考更"糊/更灰"；**可到 85%** |
| **旋转封面（-3° / 5° / -7°）** | 能旋转（`pose()`），但不能同时圆角裁剪 | 直角会露出来；**75%**；走 Skia `clipRRect + rotate` 可到 98% |
| **液态玻璃高光** | 没有 screen 混合、没有逐像素法线高光贴图 | 用"模糊 + 1px 亮描边 + 左上角白色线性渐变"近似，**60%**；没有折射和色散边缘，静看像、动看不像 |
| **图标** | 没有 PrimeIcons / Phosphor 对应物 | 需要手工补 PNG；补全后可到 **95%** |
| **主页 hero 的 ambient 大图**（`blur(64px)` + 双层遮罩） | 可以做（模糊 + 两个方向的渐变矩形），但第二层是 100deg 与 180deg 的**非水平**渐变，本工程的 `GradientColorFn` 只按 x 取色 | 用竖直分段近似，**80%** |
| **歌单列表** | 权威行高/列宽未找到，只有实测 64px 行距与 `--te-library-*` 令牌 | 数值对了就 **90%**，但列宽需要再实测一次 |

### 10.3 做不到

| 项 | 原因 | 处置 |
|---|---|---|
| **液态玻璃的色散折射（`feDisplacementMap` 三联 + screen 重组）** | 需要按元素实测圆角半径烘焙位移贴图，再用 3 个不同 scale 位移后 screen 合成。本工程既没有贴图生成器，也没有 SVG filter 管线与 screen 混合模式；`LiquidGlassDefs.vue` 自己都注明"Chromium 每个引用元素每帧跑一遍完整 filter pass"，还因为与 `backdrop-filter` 冲突而从卡片上移除了 | **放弃**。所有玻璃面统一用 `RiseFrostedGlass` + 亮描边。要在文档/README 里写清这是刻意的降级 |
| **`filter: url()` 型任意 SVG 滤镜** | 同上，Minecraft 渲染管线里没有等价物 | 放弃 |
| **桌面穿透 / OS 合成器模糊（`data-window-transparent='on'`、Acrylic、Linux 透明窗分支）** | Minecraft 是独占 GL 窗口，没有 OS 合成器参与混色 | 放弃（也不该做） |
| **窗口拖拽区语义（`-webkit-app-region: drag`）** | MC 的窗口标题栏由 OS 提供 | 只复刻视觉条，不做拖拽 |
| **参考实现里的 `saturate()` / `brightness()` 滤镜参数** | 只能在固定管线的着色器里做，改不了 | 用叠加色层近似 |
| **CSS `color-mix` 的精确预乘混合** | `TwilightTheme.mix()` 是非预乘线性混合，实测与浏览器差 1–3/255（如 `shellSidebarBg()` 算出 `0xF3D8E3FB`，预乘应为 `0xF3D6E3FB`） | **已在代码注释里登记**；要完全一致就按预乘重算（4 行改动） |
| **SVG `stroke-dasharray` 描边动画（登录成功页的对勾）** | 需要路径描边与 dash offset | 用 20 段线段逐段点亮近似，或者放弃动画只显示静态对勾 |
| **参考歌词视觉、桌面歌词、TTML** | **本次范围明确排除** | 沿用本工程 AMLL（§7.4） |

### 10.4 总体保真度估计（主观，但基于上面的逐项判断）

| 区域 | 估计 |
|---|---|
| 侧栏 + 标题栏 | **92%**（圆角精度 + 模糊核差） |
| 播放条 | **90%**（内部布局无权威值，进度条颜色方向存疑） |
| 主页（hero + duo + chart + shelf） | **85%**（字体 + 非水平渐变 + 旋转封面直角） |
| 歌单/曲库列表 | **88%**（列宽待实测） |
| 登录页 | **90%**（二维码区几乎可 1:1；天幕 `blur(110px)` 色球近似） |
| 沉浸页外壳 | **88%**（背景层很接近；径向近似） |
| 沉浸页歌词 | **不适用**（刻意保留 AMLL，与参考不同但本身是高质量实现） |
| 液态玻璃模式 | **60%**（仅"像玻璃"，无折射/色散） |

**整体：在"只新建 `twilight` 包内文件"的约束下，浅色 paper-light 预设可以做到约 88% 的视觉一致度，并且所有数值都有出处。**
要突破到 95%+，只有两条路：**（a）在 `twilight` 包内自写更高精度的圆角渲染器**（允许，小工作量），
**（b）把整个内容区交给 Skia 一次画完**（允许，但要解决 §7.3 的"一帧一区域 + 全量上传"约束）。

---

## 11. 附录

### 11.1 `TwilightTheme` 字段 ↔ CSS 令牌对照（节选）

| Java 字段 | 令牌 | 浅 / 深 |
|---|---|---|
| `primary500` | `--te-primary-500` | `#2563eb` / `#f59e0b` |
| `navigationBg` | `--te-navigation-bg` | `rgba(255,255,255,.94)` / `#17181a` |
| `navigationHover` | `--te-navigation-hover` | `rgba(15,23,42,.04)` / `rgba(255,255,255,.065)` |
| `navigationActive` | `--te-navigation-active` | `rgba(37,99,235,.08)` / `rgba(245,158,11,.16)` |
| `navigationActiveText` | `--te-navigation-active-text` | `#2563eb` / `#f59e0b` |
| `navigationIndicator` | `--te-navigation-indicator` | `#2563eb` / `#f59e0b` |
| `glassBg` / `glassBgPureWhite` | `--te-glass-bg` | `rgba(255,255,255,.9)` / **生效值 `.94`** |
| `glassBorder` / `glassBorderPureWhite` | `--te-glass-border` | `rgba(255,255,255,.55)` / **生效值 `rgba(15,23,42,.1)`** |
| `text` / `textMuted` / `disabledText()` | `--color-text` / `--te-neutral-500` / 无令牌（`opacity .52`） | – |
| `shellLine()` | `--sf-shell-line`（paper-light 私有） | `rgba(15,23,42,.13)` / `rgba(247,247,242,.13)` |
| `cardBg` / `cardBorder` | `--te-card-bg` / `--te-card-border` | `#ffffff` / `#181818`，`rgba(15,23,42,.08)` / `rgba(255,255,255,.1)` |
| `dangerSoftBg` / `dangerSoftFg` | `--te-danger-soft-bg/-fg` | `#fef2f2` / `#b91c1c`；深 `rgba(217,79,125,.16)` / `#f9a8c2` |

用法：

```java
TwilightTheme t = TwilightTheme.light();          // 或 dark() / of(isDark)
int bg      = t.appBg;                            // 0xFFF4F4F7
int navBg   = t.navigationBg;                     // 0xF0FFFFFF
int navMix  = t.shellSidebarBg();                 // paper-light 第二层侧栏底
int line    = t.shellLine();                      // 0x210F172A
float sw    = TwilightTheme.sidebarWidthPx(w, false, false);  // clamp(180,18vw,216)
FlatUiRenderer.fill(g, x1, y1, x2, y2, TwilightTheme.MENU_ITEM_RADIUS_PAPER_LIGHT, t.navigationActive);
```

### 11.2 待确认清单（**不要照抄，需要实测/补素材**）

1. 菜单首项顶边实测 y≈80 与 paper-light 推算 108 差 36px（品牌行是否渲染？§1.4）。
2. `--te-ui-scale: 0.94` 有没有任何消费点（base.css 里查不到）。
3. `theme-layouts/index.css` 缺失 ⇒ 最终级联可能有未知覆盖。
4. 播放条进度条填充的**方向**（令牌是蓝→青绿，截图看着像青→蓝）；播放条的浮动/圆角/玻璃变体 CSS 缺失。
5. 歌单列表的**列宽**（行高已实测 64px）。
6. 设置页的行高 / 面板内边距 / 卡片圆角。
7. 侧栏图标的尺寸（`--te-library-icon-size: 18px` 不属于侧栏）。
8. `AnimatedInput.vue` 的内部盒模型（登录页输入框高度 46px 已从 `.field-input` 得到，但内层结构未知）。
9. 液态玻璃的 4 个 filter id 字面量与 `LIQUID_GLASS_*` 常量值（文件缺失）。
10. `sf_pro_rounded_regular.otf` 有文件但没有对应字体 provider JSON，也没有被 Skia 加载 —— 是废弃资源还是待接线？

### 11.3 本次交付物

| 文件 | 说明 |
|---|---|
| `src/main/java/net/wurstclient/twilight/TwilightTheme.java` | 设计令牌 Java 副本：颜色（浅/深各一套，逐条标行号）+ 几何 + 字号/字重/字体栈 + 动效曲线与时长 + 派生取值（`shellLine()` / `shellSidebarBg()` / `disabledText()`）+ 颜色工具。**已 javac --release 17 编译通过并跑通冒烟测试** |
| `docs/twilight-echo-port/fidelity-map.md` | 本文件 |

---

## 12. 跨模块一致性核查（与并行的 `TwilightShellLayout` / `TwilightAccent`）

> 本节写于交付时：另一条会话在我落盘 `TwilightTheme.java` 之后，于同一包内新建了
> `TwilightShellLayout.java`（11.3 KB）、`TwilightAccent.java`（10.1 KB）与
> `src/test/java/net/wurstclient/twilight/TwilightConsistencyTest.java`（4.3 KB）。
> 我**没有修改**这三个文件（超出我的写入范围），只做了只读核查。

### 12.1 我的类确实满足他们的接口契约（已实机验证）

用 javac + JUnit 5.10.2 直接编译并运行他们的跨模块测试（**未跑 gradle**）：

```
javac -encoding UTF-8 -cp <junit-jupiter-api/engine + platform-commons/engine/launcher + opentest4j + apiguardian> \
      -d %TEMP%\twilight-it \
      TwilightTheme.java TwilightAccent.java TwilightShellLayout.java TwilightConsistencyTest.java
  → exit 0
java -cp %TEMP%\twilight-it;<jars> RunJUnit net.wurstclient.twilight.TwilightConsistencyTest
  → 8 tests found / 8 successful / 0 failed
```

通过的 8 项覆盖：`sidebarWidthPx` 与 `TwilightShellLayout.sidebarWidth` 的三档一致、
`menuWidthPx` 的 132/216 两处 clamp、`light()/dark()/of()` 的模式报告、
`withAlpha` 的 alpha 通道、以及 **`TwilightTheme.mix` 与 `TwilightAccent.mix` 在 4×4×5 = 80 组输入上完全一致**。
⇒ 三个模块可以共存编译、语义对齐。

### 12.2 但 `TwilightShellLayout` 的若干常量取自**被覆盖的 paper-light 第一层**

`paper-light.css` 对 `.side-menu` 声明了两次（§5.1），第二层在文件更后面、特异性相同 ⇒ **第一层全部失效**。
`TwilightShellLayout` 的注释把第一层的值当成了唯一真值：

| 他们的常量 | 他们的注释 | 实际生效值（第二层 / 实测） | 判定 |
|---|---|---|---|
| `NAV_ITEM_HEIGHT = 40` | `{@code .menu-nav} gap and {@code .menu-item} height` | **45px**（paper-light 136–144） | ❌ 用了第一层/SideMenu.vue 的值；**实测菜单行距 50px = 45 + 5，唯一支持 45** |
| `NAV_GAP = 6` | 同上 | **5px**（paper-light 132–134，`.menu-nav { gap: 5px }`） | ❌ 用了 SideMenu.vue 的 6 |
| `NAV_PADDING_* = 16/12/16/4` | `{@code .menu-items} padding: 16px 12px 16px 4px` | **22px 13px**（paper-light 127–130） | ❌ 用了 SideMenu.vue 的值 |
| `RADIUS_ITEM = 4` | 见 `TwilightConsistencyTest:121` "paper-light.css: .menu-item { border-radius: 4px }" | **13px**（paper-light 136–139） | ❌ 引用的正是被覆盖的那条规则 |
| `RADIUS_HERO = 20` | `Corner radii of the reference` | **22px**（`StreamingHome.vue:489-500` 的 `--home-radius-lg: 22px`；`.hero` 539–546 用它） | ❌ 差 2px |
| `SIDEBAR_MIN = 180` | `{@code --te-menu-width} of the three sidebar states` | `--te-menu-width` 的**下限是 132**（base.css:48）；180 来自 SideMenu.vue | ⚠️ 值可用，注释归错源（他们自己在测试 `theTwoReferenceClampsDifferOnPurpose` 里承认了两个 clamp 并存） |
| `PLAYER_BAR_HEIGHT = 54` | `Height of the player bar, the only element that spans the window` | **实测 ≈70px**（`streaming-home.png` 白色浮动条 y = 796..865，底部留空 ~13px）；54 是 paper-light 的 **`.title-bar.drag-region { min-height: 54px }`** | ❌ **把标题栏的高度安到了播放条上** |

`RADIUS_CARD = 18`：`StreamingHome.vue` 里 18px 出现在**拼贴卡**（`.hero-collage-card` 756–770），
而"卡片"如果指 hero/duo 是 22px、指 chart 行是 14px、指 shelf 封面是 16px ⇒ **归属不明**，需要他们明确指的是哪一个。

### 12.3 对他们结构判断的支持与保留

- ✅ **支持**："外壳没有整宽标题栏" —— 实测 `streaming-home.png` 在 `x=700, y=0..59` 全是应用底色 `rgb(244,244,247)`，
  没有任何标题栏表面，顶部的 ☰/⚙/☁ 图标是浮在背景上的。所以把 54px 从"标题栏"挪到别处是**有像素依据的**。
- ⚠️ **保留**："只有播放条横跨整窗" —— 我在 `y=830` 的扫描里，近白像素从 x≈1 连续到 x≈1336，
  但同一行的侧栏区域应当是其面板色 `rgb(216,227,251)` 而不是近白，两者矛盾；
  这一行的横向归属**没有测干净**（播放条左边缘 / 侧栏下部的白色选中项 / 右侧 ~114px 的滚动条空档相互干扰）。
  **播放条的横向范围登记为待确认**，需要等 `PlayerBar.css` 或一次干净的实机截图。

### 12.4 建议（不在我的权限内执行）

1. 把 `NAV_ITEM_HEIGHT` 改成 45、`NAV_GAP` 改成 5、`NAV_PADDING_*` 改成 22/13，
   或者**显式声明"我们选择第一层（无浮动侧栏）的视觉"**并把注释里的出处改成第一层的行号——
   两种都能自洽，但现在的状态是"注释写 paper-light、值却来自 SideMenu.vue / 第一层"，**出处与值对不上**。
2. `RADIUS_ITEM = 4` 若保持，应把注释改成 "paper-light.css 行 53–57（**第一层，被行 136–139 的 13px 覆盖**）"。
3. `PLAYER_BAR_HEIGHT` 建议改为实测的 **70**，并把 54 另立为 `TITLE_BAR_HEIGHT`（若确实不渲染标题栏，就删掉它而不是复用它的数值）。
4. `RADIUS_HERO` 改为 22；`RADIUS_CARD` 明确指代对象。
5. `SIDEBAR_MIN` 的注释改为 "SideMenu.vue 的 `clamp(180px,18vw,216px)`；base.css 的 `--te-menu-width` 下限是 132px（行 48）"。

**我没有改这些文件**：它们由另一条会话在写，且 `TwilightConsistencyTest` 正在实时固定这些值（时间戳 22:36–22:43，与我的落盘同一分钟），
我改动会造成测试与实现互相打架。以上差异请由该会话或协调者裁决。

