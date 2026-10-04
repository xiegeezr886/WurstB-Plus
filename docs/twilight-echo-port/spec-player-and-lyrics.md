# Twilight Echo 播放条 + 沉浸播放页（歌词）规格提取

> 参考实现：`source/Twilight_Echo`（Vue 3 + Electron，Apache-2.0）。所有数值都带 `path:line`；查不到的一律写 **未找到**。
> 行号基准 = 当前检出（`PlayerBar.css` 3186 行、`PlayerBar.vue` 2232 行、`PlayingMusic.vue` 2178 行、`assets/base.css` 3701 行），与早先 `_te_ref` 快照的行号**不同**。
> **本文补掉了 `fidelity-map.md` 登记为"未找到"的三处缺口**：`player-bar/PlayerBar.css`（播放条盒模型）、`stores/usePlayerStore.*`（封面取色）、进度条填充方向（确认为**水平**渐变 `accent → #0d9488`）。
> CSS 原样书写；`calc(var(--te-font-size-body, 14px) * N / 14)` 换算成的 px 写在括号里，仅为可读性。
> 引用里省略目录时，基准是 `source/Twilight_Echo/src/`：`PlayerBar.css` / `PlayerControlIcon.vue` 等在 `renderer/src/components/player-bar/`，`lyricsAppearance.ts` / `playerBarLayout.ts` / `themePlayerBar.ts` 在 `shared/`，其余短名在 `renderer/src/` 下的同名子目录。

---

## 1. 播放条（`.player-bar`）

### 1.1 外壳与几何

| 元素 | 属性 → 值 | 来源 |
|---|---|---|
| `.player-bar-shell` | `position: fixed`；`bottom: 14px`；`left: 18px`；`right: 18px`；`z-index: 1002`；`pointer-events: none` | `components/player-bar/PlayerBar.css:17-25` |
| `.player-bar-shell` | `transition: left var(--te-motion-panel) var(--te-ease-soft)`（280ms） | `PlayerBar.css:24`；`assets/base.css:41,33` |
| `.player-bar-shell.menu-open` | `left: calc(var(--te-menu-width) + 18px)`；`z-index: 999`；`--te-menu-width: clamp(132px, 18vw, 216px)` | `PlayerBar.css:33-37`；`base.css:49` |
| `.player-bar` | `grid-template-columns: minmax(0, 280px) minmax(460px, 1fr) minmax(0, 280px)`；`column-gap: 22px` | `PlayerBar.css:819,821` |
| `.player-bar` | **`height: 72px`**；`max-width: 1180px`；`margin: 0 auto`；`border-radius: 22px` | `PlayerBar.css:822-825` |
| `.player-bar` | `padding: 0 22px`；`pointer-events: auto` | `PlayerBar.css:830,836` |
| 三区 | `.player-left` / `.player-center` / `.player-right` = `grid-area: 1/1`、`1/2`、`1/3` | `PlayerBar.css:843-853` |
| 默认编排（standard） | left `['cover','trackInfo']`；center `['transport']`；right `['favorite','playMode','volume','queue','miniPlayer','desktopLyrics','hifi']` | `shared/playerBarLayout.ts:84-89` |
| 默认编排（沉浸页 `glass=true`） | right 变为 `['playMode','volume','queue','hifi','exitPlayingPage']`（由 `App.vue:1125` 传 `:glass="showPlayingPage"`） | `shared/playerBarLayout.ts:92-93` |
| 响应式 | ≤900px：列 `minmax(0,.72fr) minmax(360px,1.8fr) minmax(0,.72fr)`、`column-gap: 12px`、`padding: 0 14px`、右区 gap 2px、进度 gap 6px；≤680px：列 `…0.55fr / minmax(320px,2fr) / 0.55fr`、`column-gap: 8px`、左区 gap 8px | `PlayerBar.css:1655-1668,1671-1679` |

### 1.2 背景 / 模糊 / 边框

| 元素 | 属性 → 值 | 来源 |
|---|---|---|
| `.player-bar` 背景 | `linear-gradient(145deg, rgba(255,255,255,0.66), rgba(248,245,255,0.42))` 叠在 `rgba(255,255,255,0.48)` 上 | `PlayerBar.css:826-828` |
| `.player-bar` 边框 / 投影 | `1px solid rgba(255,255,255,0.68)`；`0 24px 80px rgba(15,23,42,0.12), inset 0 1px 0 rgba(255,255,255,0.72)` | `PlayerBar.css:829,831-833` |
| `.player-bar` 毛玻璃 | `backdrop-filter: blur(24px) saturate(160%)`；`transition: background/border-color/box-shadow 0.3s` | `PlayerBar.css:834-835,837-840` |
| `.player-bar-glass` 背景 | `linear-gradient(145deg, rgba(255,255,255,0.08), rgba(255,255,255,0.035))` 叠在 `rgba(14,14,14,0.86)` 上 | `PlayerBar.css:856-858` |
| `.player-bar-glass` **上边框** | `border-top-color: rgba(255,255,255,0.14)`；四边 `rgba(255,255,255,0.12)` | `PlayerBar.css:859-860` |
| `.player-bar-glass` 投影 / 模糊 | `0 -18px 62px rgba(0,0,0,0.34), inset 0 1px 0 rgba(255,255,255,0.16)`（**向上**投）；`backdrop-filter: blur(28px) saturate(145%)` | `PlayerBar.css:861-865` |
| glass 下文字/图标色 | `.player-title #fff`；`.player-artist rgba(255,255,255,0.7)`；`.ctrl-btn rgba(255,255,255,0.8)`；`.time-label rgba(255,255,255,0.5)`；`.icon-btn rgba(255,255,255,0.6)`；hover 底 `rgba(255,255,255,0.1)`、active 底 `0.16` | `PlayerBar.css:868-876,909-911,921-926,933-937` |
| glass 下图标反色 | `.ctrl-btn img { filter: brightness(0) invert(1); opacity: 0.82 }`；`.mode-btn-right img { …; opacity: 0.55 }` | `PlayerBar.css:880-883,905-908` |
| `.player-bar-liquid` 表面 | `--te-lg-surface-alpha: var(--te-lg-tint, 0.12)`；`--te-lg-transmit: 0.5`；`--te-lg-transmit-sat: 0.62`；`--te-lg-luma: 1.4`；`--te-lg-rim-strength: 0.55`；背景/边框移交 warp（`transparent !important`） | `PlayerBar.css:1910,1928-1939` |
| `.player-bar-liquid` 边缘高光 | `inset 0 0 0 0.5px rgba(255,255,255, specular×0.9)`、`inset 0 1px 1px rgba(255,255,255, specular×0.5)`、`inset 0 -1px 1px rgba(15,23,42,0.08)`、`0 5px 18px rgba(15,23,42,0.07)`、`0 24px 70px rgba(15,23,42,0.16)` | `PlayerBar.css:1941-1948` |
| `.player-bar-warp` | `backdrop-filter: url(#te-lg-playbar) blur(var(--te-lg-blur,16px)) saturate(var(--te-lg-saturate,140%)) saturate(1) contrast(0.5) brightness(1.4)` | `PlayerBar.css:2022-2027` |
| `.player-bar-warp` 渐层 | 顶 `rgba(255,255,255,0.11)` → `transparent 20%`；`transparent 80%` → 底 `rgba(15,23,42,0.1)`；斜向高光 stop `0 / 22px / calc(100% - 22px) / 100%` | `PlayerBar.css:1982-2008` |
| `.player-bar-warp` 弹性 | `transform: translate(var(--te-lg-elastic-x,0px), var(--te-lg-elastic-y,0px))`；位移上限 `MAX_ELASTIC_SHIFT_PX = 10` | `PlayerBar.css:1960`；`utils/liquidGlassPointer.ts:81` |

### 1.3 专辑封面缩略图

| 元素 | 属性 → 值 | 来源 |
|---|---|---|
| `.player-left` | `flex; align-items: center; gap: 12px` | `PlayerBar.css:955-961` |
| `.player-cover-slot` / `img.player-cover` | **`48px × 48px`**；`border-radius: 12px`；`object-fit: cover`；槽底 `rgba(15,23,42,0.04)` | `PlayerBar.css:962-980` |
| `img.player-cover` **阴影** | `0 14px 32px rgba(15, 23, 42, 0.12)`；`transition: transform 0.22s var(--te-ease-soft), box-shadow 0.22s, filter 0.22s` | `PlayerBar.css:981-985` |
| 封面 hover | `translateY(-2px) scale(1.05)`；`0 20px 45px rgba(15,23,42,0.16)`；`filter: saturate(1.08)` | `PlayerBar.css:987-993` |
| `.player-cover-placeholder` | `48px × 48px`；`12px`；`radial-gradient(circle at 35% 30%, rgba(255,255,255,0.9), transparent 36%)` + `linear-gradient(135deg, rgba(37,99,235,0.16), rgba(13,148,136,0.12))`；图标 `18/14`（18px）`#bbb` | `PlayerBar.css:994-1013`；`PlayerBar.vue:1780-1783` |

### 1.4 标题 / 歌手

| 元素 | 属性 → 值 | 来源 |
|---|---|---|
| `.player-track-info` / `.player-title-row` | `overflow: hidden; min-width: 0; max-width: 100%`；行内 `flex; align-items: center; gap: 8px` | `PlayerBar.css:1044-1054` |
| `.player-title` **字号/字重** | `font-family: var(--te-font-rounded)`；`calc(var(--te-font-size-body,14px) * 16 / 14)`（**16px**）；**`font-weight: 900`** | `PlayerBar.css:1057-1059`；`base.css:56` |
| `.player-title` 行高/颜色/省略 | `line-height: 1.28`；`color: var(--te-chrome-text, var(--te-neutral-900))`（`#475569`/`#111827`）；`nowrap + ellipsis` | `PlayerBar.css:1060-1065`；`base.css:67,18` |
| `.player-title` 交互 | hover `color: var(--accent-color, var(--te-primary-500))`（glass 下 `#fff`）；focus 环 `2px solid color-mix(in srgb, accent 52%, transparent)` + `offset 2px` | `PlayerBar.css:1087-1095,1097-1100` |
| `.player-artist` | `margin: 2px 0 0`；`calc(var(--te-font-size-body,14px) * 12 / 14)`（**12px**）；**`700`**；`line-height: 1.35`；`color: #999` | `PlayerBar.css:1160-1172` |
| `.player-artist` 交互 | hover/focus `var(--accent-color, var(--te-primary-500))`（glass 下 `#fff`）；focus 环同上 | `PlayerBar.css:1181-1189,1195-1198` |

### 1.5 传输控件（上一首 / 播放 / 下一首）

| 元素 | 属性 → 值 | 来源 |
|---|---|---|
| `.player-center` | `flex; flex-direction: column; align-items: center; gap: 0`；`transform: translateY(2px)` | `PlayerBar.css:1258-1268` |
| `.player-controls` | `flex; align-items: center`；**`gap: 12px`** | `PlayerBar.css:1269-1275` |
| `.ctrl-btn` | `padding: 6px`；`border: none`；`background: transparent`；**`border-radius: 50%`**；`color: #555` | `PlayerBar.css:1276-1288` |
| `.ctrl-btn` 动效 / hover / active | `transition: background 0.16s ease, transform 0.24s var(--te-ease-soft)`；hover `background: #f0f0f0`（glass `rgba(255,255,255,0.1)`）；active `transition-duration: 0.1s`（glass 底 `0.16`） | `PlayerBar.css:1285-1288,1290-1295,877-879,933-937` |
| `.ctrl-btn img` **图标尺寸** | **`18px × 18px`**；`object-fit: contain`；`opacity: 0.8` | `PlayerBar.css:1296-1303` |
| `.btn-play` **尺寸** | **`44px × 44px`**（继承 `.ctrl-btn` 的 `border-radius: 50%`、`padding: 10px`） | `PlayerBar.css:1304-1313,1283-1284` |
| `.btn-play` **圆形填充** | `background: var(--te-player-bar-play-surface, var(--play-button-color, var(--accent-color, var(--te-primary-500))))`；`color: #fff`；`box-shadow: none`；hover 用同一变量（**不换色**） | `PlayerBar.css:1307-1313,1318-1327` |
| `--play-button-color` | `normalizeAccentColor(dominantColor)`（封面取色的**归一化**结果，见 §3.2）；`--accent-color` = 原始 `dominantColor` | `PlayerBar.vue:247,1703-1706` |
| `.btn-play` 图标 | `i` 字号 `18/14`（18px）；`img` **`21px × 21px`**、`filter: brightness(0) invert(1)` | `PlayerBar.css:1329-1339` |

### 1.6 进度条

| 元素 | 属性 → 值 | 来源 |
|---|---|---|
| `.progress-area` | `flex; align-items: center`；`gap: 10px`；`width: 100%` | `PlayerBar.css:1342-1347` |
| `.progress-slider-wrap` | `position: relative; flex: 1`；**`height: 24px`**（命中高度） | `PlayerBar.css:1355-1362` |
| `.progress-track` | **`height: 6px`**；`border-radius: 999px`；`top: 50%` + `translateY(-50%)`；`overflow: hidden`；`z-index: 0` | `PlayerBar.css:1365-1377` |
| `.progress-track` 底色 | `color-mix(in srgb, var(--accent-color, #1a73e8) 18%, transparent)` | `PlayerBar.css:1373` |
| `.progress-fill` **填充** | `background: linear-gradient(90deg, var(--accent-color, #2563eb), #0d9488)`（**水平**蓝→青绿） | `PlayerBar.css:1382` |
| `.progress-fill` 推进方式 | `width: 100%` + `transform: scaleX(0)`；`transform-origin: 0 50%`；`will-change: transform`；`transition: none` | `PlayerBar.css:1378-1387` |
| `.progress-fill.live` | `linear-gradient(90deg, #38bdf8, #0ea5e9 50%, #6366f1)` | `PlayerBar.css:1388-1390` |
| glass 下轨道 / 填充 | 轨道 `color-mix(in srgb, var(--accent-color,#1a73e8) 12%, transparent)`；填充 `linear-gradient(90deg, rgba(255,255,255,0.7), …)`，直播态 `0.55 → 0.85` | `PlayerBar.css:939-952` |
| `.ab-loop-range` | `height: 6px`；`999px`；`color-mix(in srgb, var(--accent-color,#f59e0b) 55%, transparent)`；`z-index: 1` | `PlayerBar.css:1391-1401` |
| `.progress-slider` / **无 thumb、无 hover 增高** | `height: 24px`、`appearance: none`、`background: transparent`、`z-index: 2`；`::-webkit-slider-thumb { width: 0; height: 0 }`、`::-moz-range-thumb { width/height: 0; border: 0 }`；**未找到**任何轨道 hover 增高规则 | `PlayerBar.css:1402-1412,1491-1496,1510-1514,1485-1489` |
| 填充平滑 | `SmoothedProgressFill`：`tau: 160`、`snapThreshold: 2.5`（>2.5% 跳变直接吸附）；逐帧 `value += gap × (1 − e^(−dt/tau))`，`epsilon 0.0005` 停 rAF | `components/SmoothedProgressFill.vue:12-15`；`utils/useSmoothedValue.ts:28,54` |

### 1.7 时间标签

| 元素 | 属性 → 值 | 来源 |
|---|---|---|
| `.time-label` | **`calc(var(--te-font-size-body,14px) * 11 / 14)`（11px）**；`color: #999`；**`min-width: 36px`**（≤900px 时 `32px`） | `PlayerBar.css:1348-1354,1685-1687` |
| `.time-label` | `font-variant-numeric: tabular-nums`；`text-align: center`；glass 下 `rgba(255,255,255,0.5)` | `PlayerBar.css:1352-1353,909-911` |
| `.player-time-readout`（紧凑形态） | `calc(var(--te-font-size-body,14px) * 12 / 14)`（12px）；`color-mix(in srgb, var(--te-shell-control-text) 62%, transparent)`；`line-height: 1` | `PlayerBar.css:3013-3019` |

### 1.8 音量控件

| 元素 | 属性 → 值 | 来源 |
|---|---|---|
| `.volume-anchor` | `position: relative; display: flex; flex-direction: column; align-items: center` | `PlayerBar.css:86-91` |
| 触发按钮 | `.volume-control-button { width: 32px; border-radius: 10px }`，但 `.icon-btn { 32px × 32px; border-radius: 50% }` 在文件更后处声明，**同特异性下覆盖** ⇒ 实际是 32px 圆形 | `PlayerBar.css:93-96,1567-1576` |
| `.volume-drawer` | `position: absolute; bottom: 100%; left: 50%; transform: translateX(-50%); margin-bottom: 10px`；`z-index: 2` | `PlayerBar.css:117-123` |
| `.volume-drawer` 表面 | `background: var(--te-card-bg)`（`#ffffff`）；`border: 1px solid #e2e8f0`；`border-radius: 14px`；`padding: 8px 8px 7px`；`gap: 5px`；`0 18px 55px rgba(15,23,42,0.12)`；`backdrop-filter: none` | `PlayerBar.css:124-135`；`base.css:163` |
| `.volume-drawer.drawer-glass` | `background: #151a24`；`border-color: #303848`；`0 22px 56px rgba(0,0,0,0.34), inset 0 1px 0 rgba(255,255,255,0.06)` | `PlayerBar.css:137-145` |
| 竖轨几何 | wrap `28px × 96px`；slider `96px × 28px` + `transform: translate(-50%,-50%) rotate(-90deg)`；轨道 `6px` / 圆角 `999px` | `PlayerBar.css:147-169` |
| 竖轨填充 | 已填充 `linear-gradient(90deg, var(--accent-color,#1a73e8), …)` 定位在 `var(--range-value, 70%)`（由 `PlayerBar.vue:1922` 写成 `${volume*100}%`）；未填充 `color-mix(in srgb, var(--accent-color,#1a73e8) 18%, transparent)`；glass 下未填充 `rgba(255,255,255,0.22)`、强调色回退 `#3b82f6`；thumb `0×0` | `PlayerBar.css:170-181,183-188,202-206` |
| `.volume-drawer-val` | `calc(var(--te-font-size-body,14px) * 11 / 14)`（11px）；`#888`；`tabular-nums`；glass 下 `#d8dee8` | `PlayerBar.css:208-212,243-245` |
| `.volume-unity-btn` | `border: 1px solid color-mix(var(--te-primary,#6366f1) 28%, transparent)`；`background: color-mix(… 10%)`；`color: var(--te-primary,#6366f1)`；`border-radius: 8px`；`padding: 2px 8px`；字号 `10/14`（10px）；`700`；`.accent` 态用 `#f59e0b`/`#d97706` | `PlayerBar.css:214-232` |
| 抽屉进出场 | 进 `opacity/transform 0.2s ease`；出 `0.15s`；起始 `opacity: 0; transform: translateX(-50%) translateY(6px)` | `PlayerBar.css:247-261` |

### 1.9 播放模式 / 队列 / 其它按钮

| 元素 | 属性 → 值 | 来源 |
|---|---|---|
| `.player-right` | `flex; align-items: center; gap: 6px; justify-content: flex-end` | `PlayerBar.css:1517-1523` |
| `.mode-btn-right`（播放模式） | `32px × 32px`；`padding: 3px`；`border-radius: 50%`；`color: #999`；`transition: background 0.15s, transform 0.24s var(--te-ease-soft)`；hover `#f0f0f0` | `PlayerBar.css:1525-1544` |
| `.mode-btn-right img` | **`19px × 19px`**；`object-fit: contain`；`opacity: 0.58`；心动模式激活时 `opacity: 0.95` + 底 `color-mix(var(--te-favorite-500,#ef4444) 16%)` | `PlayerBar.css:1545-1565` |
| `.icon-btn`（队列/收藏/HiFi/均衡器…） | `32px × 32px`；`border-radius: 50%`；字号 `14/14`（14px）；`color: #888`；`transition: background 0.15s, color 0.15s, transform 0.24s var(--te-ease-soft)` | `PlayerBar.css:1567-1583` |
| `.icon-btn` 交互 | hover `background: rgba(37,99,235,0.1)` + `color: var(--te-primary-500)`；active `color: var(--accent-color,#2563eb)` + 底 `color-mix(accent 12%)`；disabled `opacity: 0.62` | `PlayerBar.css:1584-1595` |
| `.favorite-btn.active` | `color: var(--te-favorite-500, #ef4444)`，且整组规则强制 `background: transparent !important; box-shadow: none !important` | `PlayerBar.css:1597-1618` |
| `PlayerControlIcon`（队列/HiFi/音量等图标本体） | `svg viewBox="0 0 24 24"`；`stroke="currentColor"`；**`stroke-width="1.8"`**；`stroke-linecap/linejoin: round`；`width/height: 1em`（跟随字号） | `components/player-bar/PlayerControlIcon.vue:9-18,42-47` |

---

## 2. 歌词视图（行布局与动画）

### 2.1 列与滚动容器

| 元素 | 属性 → 值 | 来源 |
|---|---|---|
| `.lyrics-column` | `flex; flex-direction: column`；`padding-left: 6px`；`transform: translateX(var(--te-lyric-offset-x, 0px))` | `PlayingMusic.vue:1293-1300` |
| `.lyrics-head` | `display: flex; align-items: end; justify-content: flex-end`；`gap: 16px`；`padding-bottom: 18px`；`min-height: 56px` | `PlayingMusic.vue:1317-1325` |
| `.lyrics-scroll` **padding** | `position: relative; flex: 1; min-height: 0; overflow: hidden`；**`padding-right: 8px`**（其余方向无 padding） | `PlayingMusic.vue:1351-1356` |
| `.lyrics-scroll` 滚动 | `scroll-behavior: auto`；`overscroll-behavior: contain`；滚动条 `display: none` / `scrollbar-width: none` | `PlayingMusic.vue:1357-1390` |
| `.lyrics-scroll` **上下淡出 mask** | `linear-gradient(to bottom, transparent 0%, rgba(0,0,0,0.26) 6%, rgba(0,0,0,1) 18%, rgba(0,0,0,1) 82%, rgba(0,0,0,0.26) 94%, transparent 100%)` | `PlayingMusic.vue:1361-1378` |
| `.lyrics-list` | `position: absolute; inset: 0`；`max-width: var(--te-lyric-max-width, 820px)`；`margin: 0 auto` | `PlayingMusic.vue:1392-1397` |
| `--te-lyric-max-width` | 默认 **`820px`**（`lyricsMaxWidth`，420–1200，步进 10；`lyricsOffsetX` 默认 0，−80–160） | `PlayingMusic.vue:152-153`；`shared/lyricsAppearance.ts:235-236,138-139` |
| 响应式 padding | ≤1120px：`.lyrics-list { padding-top: 4vh }`；≤760px：`{ padding: 2vh 0 18vh }` | `PlayingMusic.vue:1783-1785,1806-1808` |

### 2.2 行盒模型

| 元素 | 属性 → 值 | 来源 |
|---|---|---|
| `.lyric-row` 定位 | `position: absolute; left: 0; right: 0; top: 0; width: 100%` | `PlayingMusic.vue:1443-1448` |
| `.lyric-row` 布局 | `flex; flex-direction: column; align-items: center; justify-content: center`；**`gap: 5px`** | `PlayingMusic.vue:1449-1453` |
| `.lyric-row` 盒 | **`padding: 12px 20px`**；`border-radius: 18px`；`border: 1px solid transparent` | `PlayingMusic.vue:1454-1460` |
| `.lyric-row` 位移/模糊 | `transform: translate3d(0, var(--lyric-line-top, 0px), 0)`；`filter: blur(var(--lyric-line-blur, 0px))` | `PlayingMusic.vue:1475-1476` |
| `.lyric-row` **不透明度合成** | `opacity: calc(var(--lyric-line-ready,0) × var(--lyric-line-opacity,1) × var(--lyric-style-opacity,1))` | `PlayingMusic.vue:1467-1469` |
| `.lyric-row` transition / 性能 | 只过渡 `color`、`background` 各 `var(--te-motion-hover) ease`（**位移与模糊不加 transition**，交给弹簧）；`contain: layout style`；`contain-intrinsic-size: auto 4em`；`backface-visibility: hidden` | `PlayingMusic.vue:1480-1482,1477-1479`；`base.css:40` |
| 视口外剔除 | `--lyric-line-in-sight: 0` ⇒ `content-visibility: hidden`（写入见 `lyricViewportController.ts:281-285`） | `PlayingMusic.vue:1486-1488` |
| `.lyric-row-content` **缩放** | `transform: scale(var(--lyric-line-scale, 1))`；`transform-origin` 按对齐取 `left/center/right center` | `PlayingLyricLine.vue:327-344` |
| `.lyric-text` | 字号 `clamp(12px, var(--lyric-style-font-size, var(--te-lyric-font-size, 18px)), 48px)`；行高 `var(--lyric-style-line-height, var(--te-lyric-line-height, 1.85))` | `PlayingMusic.vue:1510-1518`；`PlayingLyricLine.vue:477-482` |

### 2.3 字号 / 颜色（活动行 vs 非活动行）

| 目标 | 属性 → 默认值 | 来源 |
|---|---|---|
| 基准 | `fontSize: 18`（px，12–48）；`fontWeight: 600`（300–900）；`lineHeight: 1.85`（1.1–2.8）；`letterSpacing: 0`；`align: center`；`opacity: 100` ⇒ `--lyric-style-opacity: 1` | `shared/lyricsAppearance.ts:164-172,129-133`；`utils/lyricsStyleVars.ts:108` |
| normal 颜色 | `var(--te-playback-lyric-text, rgba(255,255,255,0.42))`；hover `var(--te-playback-lyric-hover-text, rgba(255,255,255,0.74))` | `lyricsStyleVars.ts:16`；`PlayingMusic.vue:1466,1490-1492` |
| **active 颜色** | `var(--te-playback-lyric-active-text, #fff)` | `lyricsStyleVars.ts:17,24`；`PlayingMusic.vue:1494-1495` |
| active 排版 | `lineHeight: 1.65`（覆盖基准 1.85）；`letter-spacing: calc(var(--lyric-style-letter-spacing, 0em) + 0.012em)`；`text-shadow: var(--lyric-style-highlight, none)`；`-webkit-text-stroke: var(--lyric-style-stroke, 0 transparent)` | `lyricsAppearance.ts:185`；`PlayingMusic.vue:1520-1525` |
| active 高亮默认 | `highlightColor: #fff8df`，`highlightIntensity: 32`（normal 为 `#ffffff` / 30） | `lyricsAppearance.ts:177-178,186-187` |
| **karaoke 墨色** | `.lyric-word--karaoke { color: var(--te-playback-lyric-karaoke, currentColor) }`；默认 `karaokeColor: '#fff8df'` | `PlayingLyricWords.vue:419-421`；`lyricsAppearance.ts:232` |
| 自定义配色派生 | `translation = color-mix(textColor 72%)`；`translation-active = color-mix(activeColor 82%)`；`harmony 58%/68%`；`romanization 58%/72%` | `PlayingMusic.vue:127-143` |
| 渐隐相关开关 | `inactiveOpacity` 默认 `100`（⇒ `inactiveDim = 1`）；`focusLineCount` 默认 `'all'`；`hidePassedLines` 默认 `false` | `lyricsAppearance.ts:227-228,241`；`PlayingMusic.vue:381,350-354,385` |

### 2.4 不透明度 / 缩放 / 模糊斜坡（精确公式，常量见 `utils/lyricLineLayout.ts`）

| 常量 → 值 | 来源 |
|---|---|
| `LYRIC_OPACITY_SINGING 1`；`PRESENTED 0.86`；`PAST 0.68`；`FUTURE = NORMAL = NON_DYNAMIC 0.46`；`HIDDEN 0.00001`（故意非 0，避免被优化掉后"弹回"） | `lyricLineLayout.ts:30-37` |
| `LYRIC_SCALE_ACTIVE 104`；`PRESENTED 102`；`INACTIVE 100`；`BACKGROUND 75`（百分比）；`RANGE 4`；`PRESENTED_RANGE 2` | `lyricLineLayout.ts:25-28,49-50` |
| `LYRIC_BLUR_PER_INDEX 0.35`（px/行）；`LYRIC_BLUR_MAX 4`；`LYRIC_NARROW_VIEWPORT_PX 1024`；`LYRIC_NARROW_BLUR_SCALE 0.8` | `lyricLineLayout.ts:39-42` |
| **不透明度判定** | `focusHidden → 0.00001`；`hidePassedLines && line < anchor → 0.00001`；`singing → 1`；`presented → 0.86`；`line < anchor → 0.68 × dim`；否则 `0.46 × dim` | `lyricLineLayout.ts:247-260` |
| **缩放判定** | `singing → 100 + 4 × scaleIntensity`；`presented → 100 + 2 × scaleIntensity`；否则 `100`；背景声部 `75` | `lyricLineLayout.ts:216-221,272-277` |
| **模糊判定** | 仅 `!focused` 时：`distance = line < anchor ? abs(anchor − line) + 1 : abs(line − max(anchor, latestPresented))`；`blur = clamp((1 + distance) × 0.35 × blurScale, 0, 4) × blurIntensity`；`blurScale = viewportWidth <= 1024 ? 0.8 : 1` | `lyricLineLayout.ts:262-270,215` |
| 强度覆盖 | `scaleIntensity` / `blurIntensity` 默认 100（⇒1） | `PlayingMusic.vue:382-383`；`lyricsAppearance.ts:242-243` |
| 写入 DOM 的格式 | `--lyric-line-opacity`（`toFixed(5)`）；`--lyric-line-blur`（`toFixed(3)px`）；`--lyric-line-top`（`toFixed(2)px`）；`--lyric-line-scale` = `scale/100`（`toFixed(5)`）；另有 `--lyric-line-ready`、`--lyric-line-in-sight` | `utils/lyricViewportController.ts:257,262,290-291,275,284` |

### 2.5 活动行的垂直位置与滚动定位

| 量 → 值 / 公式 | 来源 |
|---|---|
| `LYRIC_ALIGN_POSITION` = **`0.35`**；运行时取 `lyricsAppearance.anchorPosition`（默认 `0.35`，0.15–0.85，步进 0.01）；`alignAnchor = 'center'` | `lyricLineLayout.ts:52`；`PlayingMusic.vue:371-372`；`lyricsAppearance.ts:239,142`；`lyricViewportController.ts:123` |
| `visibleHeight = viewportHeight − max(0, bottomReservedPx)`；`bottomReservedPx` = `.player-bar-shell` 的 `getBoundingClientRect().height`，`data-te-playbar-hidden="true"` 时取 0 | `lyricLineLayout.ts:166`；`PlayingMusic.vue:258-266` |
| **锚点行 top**：`curPos = −scrollOffset − stackedAbove + visibleHeight × clamp(0.35, 0, 1)`，再 `− anchorLine.height / 2`；`stackedAbove` = 锚点之上所有行高之和（跳过播放中折叠的背景声部与焦点窗口外的行） | `lyricLineLayout.ts:205-211,196-203` |
| **行间距** `LYRIC_ROW_GAP_PX = 10`（加进每行高度 `height + rowGap`） | `PlayingMusic.vue:227,374`；`lyricViewportController.ts:180` |
| 手动浏览边界 `scrollBoundary = [−stackedAbove, max(−stackedAbove, curPos + scrollOffset − visibleHeight/2)]`；释放时间 `LYRIC_MANUAL_BROWSE_RESET_MS = 5000` | `lyricLineLayout.ts:297-300`；`lyricViewportController.ts:35,467-478` |
| 视口内判定：`!(top > viewportHeight + height \|\| top + height < −height)` | `lyricLineLayout.ts:306-308` |

### 2.6 弹簧与级联（cascade）

| 参数 → 值 | 来源 |
|---|---|
| 位移 `LYRIC_POS_Y_SPRING`：`mass: 0.9`，`damping: 13`，`stiffness: 90`（ζ≈0.72，可见回弹） | `utils/lyricSpring.ts:25-31` |
| 缩放 `LYRIC_SCALE_SPRING`：`mass: 2`，`damping: 25`，`stiffness: 100`（刻意比位移慢：ω₀≈7.07 vs 10 rad/s） | `lyricSpring.ts:37-41` |
| 背景声部 `LYRIC_BG_SCALE_SPRING`：`mass: 1`，`damping: 20`，`stiffness: 50`（ζ≈1.414，无过冲）；未覆盖时默认 `1 / 10 / 100` | `lyricSpring.ts:44-48,20-22` |
| 收敛判据：位置/速度/加速度三者均 `< SETTLE_EPSILON (0.01)`；导数用中心差分 `DERIVATIVE_STEP = 1e-3` | `lyricSpring.ts:50-51,120-128` |
| 欠阻尼解析解：`x(t) = to − (cos(t·ωd/2m)·Δ + sin(t·ωd/2m)·leftover)·e^(−t·damping/2m)`，`ωd = sqrt(4mk − c²)` | `lyricSpring.ts:82-89` |
| **级联基础延迟** `LYRIC_CASCADE_BASE_DELAY = 0.08` 秒；**衰减** `LYRIC_CASCADE_DECAY = 1.05`（每步 `baseDelay /= 1.05`）；**上限** `LYRIC_CASCADE_MAX_DELAY = 0.4` 秒 | `lyricLineLayout.ts:44-46,288-290` |
| 级联速度系数 `resolveCascadeSpeedFactor(v) = 2^((50 − v)/50)`，`cascadeSpeed` 默认 `50` ⇒ 1 | `shared/lyricsAppearance.ts:156-159,244`；`PlayingMusic.vue:384` |
| 延迟累加条件：仅 `curPos >= 0 && !isSeeking && position >= anchorPosition` 且该行非背景声部、非焦点隐藏 | `lyricLineLayout.ts:286-291` |
| 帧步长 `delta = min(0.05, (now − lastFrameNow)/1000)`，首帧按 `1/60`；`FRAME_FALLBACK_MS = 120` | `lyricViewportController.ts:317,32` |
| 快照（不插值）条件：`force \|\| !hasCommittedLayout \|\| !springEnabled() \|\| document.hidden` | `lyricViewportController.ts:230-252` |
| 缩小动态效果：`data-te-motion='reduced'/'off'` ⇒ `.lyric-row { filter: none }`、`.lyric-row-content { transform: none }`、word/char 无 transform 与阴影、mask 移除 | `PlayingMusic.vue:1591-1614` |

### 2.7 逐字 karaoke 填充（mask 方案）

| 参数 → 值 | 来源 |
|---|---|
| 机制：动画 **`mask-position`**（不是改 mask 尺寸，也不是复制 `::after` 层），由 Web Animations API 驱动 | `utils/lyricEmphasis.ts:373-377,445-446` |
| 渐变构造 `buildFadeGradient`：`linear-gradient(to right, bright X%, dark Y%)`；`totalAspect = 2 + widthRatio`，`widthInTotal = widthRatio / totalAspect`，`leftPos = (1 − widthInTotal)/2` | `lyricEmphasis.ts:378-393` |
| **渐变色**：`bright = rgba(0,0,0,var(--lyric-bright-mask-alpha, 1))`；`dark = rgba(0,0,0,var(--lyric-dark-mask-alpha, 1))` | `lyricEmphasis.ts:381-382` |
| **量化后的实际渐变**（`widthRatio = 0.5`）：`totalAspect = 2.5`、`widthInTotal = 0.2`、`leftPos = 0.4` ⇒ `linear-gradient(to right, rgba(0,0,0,1) 40%, rgba(0,0,0,0.4) 60%)` | 代入 `lyricEmphasis.ts:384-390` |
| **扫描宽度** `DEFAULT_WORD_FADE_WIDTH = 0.5`（× 字号，注释：与 iPad 一致）；`fadeWidth = wordHeight × 0.5`；`fullWidth = width + padding × 2` | `lyricEmphasis.ts:51-52,421,431-432` |
| mask-size / origin / repeat：`${totalAspect × 100}% 100%`（= `250% 100%`）；`mask-origin: left`（rtl 为 `right`）；`no-repeat` | `lyricEmphasis.ts:512-513`；`PlayingLyricWords.vue:244-250` |
| 首/末词补偿：首词 `curPos += fadeWidth × 1.5`；末词 `curPos += fadeWidth × 0.5` | `lyricEmphasis.ts:495-496` |
| 关键帧：每帧 `maskPosition: "<px> 0"`（rtl 取反）；末帧强制 `'0px 0'`；`clampOffset` 下界 `−(fullWidth + fadeWidth)`；`timing = { duration: totalFadeDuration, fill: 'both' }`，`totalFadeDuration = (max(lastWordEnd, lineEnd) − lineStart) × 1000` ms | `lyricEmphasis.ts:428-429,437-438,445-446,503-508,515` |
| 同步容差 `DRIFT_TOLERANCE_MS = 80`（正向漂移才校正；暂停时双向校正） | `PlayingLyricWords.vue:81,300-310` |
| **明/暗 alpha（静态）**：`.lyric-row { --lyric-bright-mask-alpha: 1; --lyric-dark-mask-alpha: 0.4 }`；`.lyric-row.active { --lyric-dark-mask-alpha: 0.32 }`；`prefers-contrast: more` ⇒ `0.72` 且去掉 blur/阴影 | `PlayingMusic.vue:1581-1589,1717-1731` |
| `maskAlphaForScale(s)`：`focus = clamp((s/100 − 0.97)/0.03, 0, 1)`；`bright = focus×0.8 + 0.2`；`dark = focus×0.2 + 0.2` —— **已定义但生产代码未调用**（grep 只命中定义与单元测试） | `lyricEmphasis.ts:523-526`；`lyricEmphasis.test.ts:21,426-433` |
| 发光余量：`.lyric-word` / `.lyric-char` `padding: 0.35em; margin: -0.35em` | `PlayingMusic.vue:1559-1575` |
| 每词上浮：`FLOAT_RISE_EM = 0.012`（lead）/ `FLOAT_SECONDARY_RISE_EM = 0.008`；`FLOAT_MIN_DURATION_MS = 1000`；`composite: 'add'`；`easing: 'ease-out'` | `lyricEmphasis.ts:43-45,337-356` |
| 长音强调：阈值 `EMPHASIS_MIN_DURATION_MS = 1200`；拉丁词长上限 `7`；`EMPHASIS_FRAME_COUNT = 32`；`SCALE_GAIN 0.025`、`LIFT_EM 0.004`、`GLOW_ALPHA_CAP 0.18`、`AMOUNT_CAP 1`、`BLUR_CAP 0.18`；句尾 `1.6 / 1.5 / 1.2`，float `×1.4`、提前 `400ms`；强度 `amount = shape(d/2000)×0.6`、`blur = shape(d/3000)×0.5`，`shape(v) = v>1 ? √v : v³` | `lyricEmphasis.ts:22-54,211-220,243-261` |
| 强调发光色写作 `textShadow: 0 0 min(0.3, blur×0.3)em rgba(255,255,255, alpha)`；`MAX_EMPHASIS_GRAPHEMES = 24` | `lyricEmphasis.ts:304,54,142-145` |

### 2.8 间奏圆点（interlude dots）

| 参数 → 值 | 来源 |
|---|---|
| 触发位置：锚点行的**下一行**（`lineIndex === scrollToIndex + 1`），或 `interludeAfterIndex < 0` 时的锚点行本身 | `lyricLineLayout.ts:237-245` |
| `LYRIC_INTERLUDE_DOTS_OFFSET_PX = 10` ⇒ 圆点 `top = curPos + 10`；随后 `curPos += interludeDotsHeight + LYRIC_INTERLUDE_DOTS_GAP_PX`，其中 gap = **40**、`INTERLUDE_DOTS_HEIGHT_PX = 24` | `lyricLineLayout.ts:53-54,243-244`；`PlayingMusic.vue:226,387` |
| 容器：`position: absolute; left: 50%; top: var(--lyric-interlude-top, 0); display: flex; gap: 0.35em; transform: translateX(-50%)` | `PlayingMusic.vue:1399-1407` |
| 圆点：**`width/height: clamp(6px, 0.8vh, 12px)`**；`border-radius: 50%`；`background: var(--te-playback-lyric-active-text, #fff)`；`opacity: 0.5` | `PlayingMusic.vue:1409-1416` |
| 呼吸动画：`lyric-interlude-pulse 1.8s ease-in-out infinite`，第 2/3 点延迟 `0.22s` / `0.44s`；关键帧 `0/100%: opacity 0.32, scale(0.86)`、`50%: opacity 0.9, scale(1.08)`；reduced/off 时 `animation: none` | `PlayingMusic.vue:1415-1441` |

### 2.9 翻译 / 罗马音 / 和声声部

| 元素 | 属性 → 值 | 来源 |
|---|---|---|
| `.lyric-translation` | `margin-top: max(2px, var(--te-lyric-translation-spacing, 0px))`（`translationSpacing` 默认 0）；`font-size: var(--lyric-style-font-size, 14px)`；`font-weight: 500`；`line-height: 1.3` | `PlayingMusic.vue:1625-1633`；`lyricsAppearance.ts:199-201,240` |
| `.lyric-translation` 颜色 | 非活动 `var(--te-playback-lyric-translation, rgba(255,255,255,0.58))`；活动行 `…-active, rgba(255,255,255,0.82))` | `PlayingMusic.vue:1635,1651-1655`；`lyricsStyleVars.ts:19,27` |
| `.lyric-translation` 不透明度 | `opacity: var(--lyric-style-opacity, 1)`，默认 `opacity: 82` ⇒ `0.82`；过渡 `opacity/color/background/text-shadow` 各 `var(--te-motion-hover) ease` | `PlayingMusic.vue:1636,1644-1648`；`lyricsAppearance.ts:202` |
| `.lyric-romanization` | `margin-top: max(2px, var(--te-lyric-translation-spacing, 0px))`；`font-size: 13px`；`font-weight: 400`；`line-height: 1.25`；默认 `opacity: 70` ⇒ `0.70` | `PlayingMusic.vue:1659-1666`；`lyricsAppearance.ts:207-210` |
| `.lyric-romanization` 颜色 | 非活动 `rgba(255,255,255,0.46)`；活动行 `rgba(255,255,255,0.72)` | `PlayingMusic.vue:1669,1680-1684`；`lyricsStyleVars.ts:20,28` |
| 和声颜色默认 | 非活动 `rgba(255,255,255,0.48)`；活动 `rgba(255,255,255,0.62)`（`harmony` 字号 14 / 500 / 行高 1.3 / opacity 62） | `lyricsStyleVars.ts:18,26`；`lyricsAppearance.ts:189-196` |
| 声部宽度 / 对唱 | `.lyric-voice { width: min(100%, 32rem) }`；背景/和声 `min(82%, 26rem)`；`.lyric-duet-grid { gap: 32px }`，`--split` 为 `1fr 1fr`，≤620px 时 `gap: 12px` | `PlayingLyricLine.vue:410-426,387-398,579-587` |
| 副声部折叠动效 | 收起 `max-height: 0; opacity: 0; transform: translateY(-6px) scale(0.98)`；展开 `max-height: 14em; opacity: var(--lyric-style-opacity, 0.62)`；过渡 `max-height 320ms cubic-bezier(0.22,1,0.36,1), opacity 220ms ease, transform 320ms 同曲线, visibility 0s linear 320ms` | `PlayingLyricLine.vue:428-463` |
| 行自绘表面 | `backgroundStyle: 'glass'` 时**活动行** `backdrop-filter: blur(16px) saturate(130%)`；`'gradient'` 时 `linear-gradient(135deg, tint, transparent)`；`'none'` 时活动行底 `var(--te-playback-lyric-active-surface, transparent)` | `lyricsStyleVars.ts:46-68` |

### 2.10 代码中能找到的缓动 / 时长

| 令牌 → 值 | 来源 |
|---|---|
| `--te-ease-enter cubic-bezier(0.4, 0, 0.2, 1)`；`--te-ease-soft = var(--te-ease-out-quint) = cubic-bezier(0.22, 1, 0.36, 1)`；`--te-ease-spring cubic-bezier(0.22, 1.14, 0.36, 1)`；`--te-ease-out-expo cubic-bezier(0.16, 1, 0.3, 1)`；`--te-ease-out-strong cubic-bezier(0.23, 1, 0.32, 1)` | `assets/base.css:27,33-38` |
| `--te-motion-press / hover / panel / page / settle / return` = `90ms / 160ms / 280ms / 400ms / 500ms / 220ms`；`--te-motion-press-scale 0.97`；`--te-motion-hover-translate -1px` | `base.css:39-47` |
| 背景封面淡入淡出 `opacity 400ms var(--te-ease-out-strong)`；过渡期改用 `blur(18px) saturate(1.28) brightness(0.34)`（避免两层 58px 模糊重叠） | `PlayingMusic.vue:988-1001` |
| 沉浸页进出场 `transform var(--te-motion-page) var(--te-ease-out-expo)`，`from { transform: scale(0.12); border-radius: 28px; opacity: 0 }` | `App.vue:1589-1612` |
| 封面进场 `te-playing-artwork-arrive var(--te-motion-page) var(--te-ease-spring) both`，`from { opacity: 0; scale: 0.9 }` | `PlayingMusic.vue:1186-1195` |
| 元信息进场 `te-playing-meta-arrive var(--te-motion-panel) var(--te-ease-spring) 36ms both`，`from { opacity: 0; translate: 0 12px }` | `PlayingMusic.vue:1247-1256` |

---

## 3. 封面取色（cover-driven theming）

### 3.1 提取算法（手写，非第三方库）

grep `color-thief|colorthief|ColorThief` 在 `src/renderer/src` 内**无命中**；算法全部在 `utils/colorExtractor.ts`。

| 步骤 → 值 | 来源 |
|---|---|
| 入口 `extractDominantColor(imageSrc)`；缓存 `Map<string, Promise<string>>`，键 `imageSrc.trim()`，上限 `MAX_DOMINANT_COLOR_CACHE_SIZE = 64`（LRU：命中后重新 set）；空源直接返回兜底色 | `colorExtractor.ts:5,2-3,9-18,72-78` |
| 兜底色 `DEFAULT_DOMINANT_COLOR = '#1a73e8'`（空源 / 空直方图 / 无 ctx / 解码失败 / 画布污染 均用它） | `colorExtractor.ts:1,7,97,126,147,150` |
| CORS：仅对 `^(https?:\|twilight-media:\|cover:\|background:)` 设 `img.crossOrigin='anonymous'`（不带 credentials） | `colorExtractor.ts:86-88,155-157` |
| **采样尺寸 `50 × 50`**：`canvas.width/height = 50`，`ctx.drawImage(img, 0, 0, 50, 50)` | `colorExtractor.ts:91-100` |
| **量化 `buckets = 12`**：`ri = floor(r/255 × 11)`，`key = (ri << 16) \| (gi << 8) \| bi` | `colorExtractor.ts:103,118-121` |
| 像素过滤：`alpha < 128` 丢弃；**近灰** `max − min < 15` 丢弃；**明度** `max < 40` 或 `min > 220` 丢弃 | `colorExtractor.ts:110-116` |
| 选桶：取计数最大的桶；反量化 `r = round(ri / 11 × 255)`，输出小写 `#rrggbb` | `colorExtractor.ts:125-143` |
| 另一条路径 `extractAverageColor`：`32 × 32` canvas + **按 alpha 加权均值**，全透明返回 `null` | `colorExtractor.ts:26-70` |

### 3.2 归一化（真正驱动 UI 的那个色）

| 步骤 → 值 | 来源 |
|---|---|
| `normalizeAccentColor(color)`：非法输入回退 **`#3567b5`**；无缓存（每次重算） | `colorExtractor.ts:249-251,255-260` |
| 转 HSL（`rgbToHsl` / `hslToRgb`），`h` 不变 | `colorExtractor.ts:177-243,255` |
| **饱和度钳制 `s' = clamp(s × 0.56 + 0.18, 0.32, 0.64)`** | `colorExtractor.ts:256` |
| **明度钳制 `l' = clamp(l × 0.42 + 0.25, 0.34, 0.52)`** | `colorExtractor.ts:257` |

### 3.3 状态与驱动元素

| 环节 → 值 | 来源 |
|---|---|
| store 默认 `dominantColor = ref('#1a73e8')`；触发 `watch([track.id, track.cover, track.coverSource, settings.useCoverTheme])` → 先 `resolveCover` 再 `extractDominantColor` | `stores/usePlayerStore.ts:164,1695-1719` |
| 过期请求丢弃：`requestId = ++dominantColorRequestId`，回来时比对 id / cover / coverSource | `usePlayerStore.ts:1703,1709-1727,1791` |
| `useCoverTheme = false` ⇒ `'#7c4dff'`；无封面 / 解析失败 ⇒ `coverThemeColor = '#1a73e8'` | `usePlayerStore.ts:1729,1732-1733,1737-1738` |
| 注入：播放条 `--accent-color: dominantColor`、`--play-button-color: normalizeAccentColor(dominantColor)`；沉浸页根元素 inline `--accent-color: dominantColor`（组件内默认 `var(--te-playback-accent, #7c4dff)` 被 inline 覆盖） | `PlayerBar.vue:1703-1706,247`；`PlayingMusic.vue:658,914` |
| 主题可接管：`--te-playback-accent` 被登记为"主题决定"的属性 | `extensions/themeProfilePriority.ts:29` |
| 播放键圆形填充用**归一化色**；进度轨 `color-mix(accent 18%)`、进度填充 `linear-gradient(90deg, accent, #0d9488)`、A-B 区间 `color-mix(accent 55%)`（回退 `#f59e0b`） | `PlayerBar.css:1307-1310,884-891,1373,1382,1398` |
| 音量竖轨：已填充 `accent`、未填充 `color-mix(accent 18%)`（glass 下 `rgba(255,255,255,0.22)`）；标题/歌手 hover 与 `.icon-btn.active`（底 `color-mix(accent 12%)`） | `PlayerBar.css:171,173,180,1089,1183,1589-1590` |
| 玻璃下进度轨 `color-mix(accent 12%)`；迷你/紧凑轨道（暗色）`color-mix(in srgb, var(--accent-color,#60a5fa) 30%, rgba(255,255,255,0.14))` | `PlayerBar.css:939-941,2503-2509` |
| 续播胶囊 `color-mix(accent 12%, rgba(0,0,0,0.04))`；沉浸页 scrim 染色 `color-mix(accent 8%)`；光斑 `radial-gradient(circle at 18% 26%, color-mix(accent 22%), transparent 42%)`；封面占位 `color-mix(accent 18%)`（暗色 22% 叠 `rgba(15,23,42,0.65)`） | `PlayerBar.css:1430`；`PlayingMusic.vue:1036,1045-1049,1233,1240` |
| 液态玻璃表面色由背景采样写回：`--te-lg-surface-rgb: var(--te-lg-context-rgb, 241,245,249)`，写入点 `liquidGlassEnvironment.ts:89` | `PlayerBar.css:1896`；`utils/liquidGlassEnvironment.ts:89` |

---

## 4. 沉浸布局（大封面 + 歌词）

### 4.1 页面骨架与网格

| 元素 | 属性 → 值 | 来源 |
|---|---|---|
| `.playing-music` | `position: fixed; inset: 0; z-index: 1100; overflow: hidden`；`color: var(--te-playback-page-text, #f4f7fb)`；背景 `var(--te-player-bg)` / `var(--te-player-bg-image)`，`center / cover / no-repeat` | `PlayingMusic.vue:903-913`；`base.css:89-90` |
| **层叠真相** | 播放条**浮在沉浸页之上**：`.main-content` 有 `transform: translateZ(0)` + `z-index: 1`（自成层叠上下文，把沉浸页的 1100 关在里面），而 `.player-bar-shell` 在其外 `z-index: 1002` | `App.vue:1391-1403`；`PlayerBar.css:22`；`PlayingMusic.vue:906` |
| `.stage` | `width: min(100%, 1560px)`；`height: 100%`；`margin: 0 auto`；**`padding: 72px 36px 28px`** | `PlayingMusic.vue:1112-1119` |
| `.layout` 网格 | `grid-template-columns: minmax(300px, 360px) minmax(0, 1fr)`；`rows: minmax(0, 1fr)`；**`gap: var(--te-lyric-cover-gap, 40px)`**；`align-items: stretch`；`height: 100%` | `PlayingMusic.vue:1129-1137` |
| `--te-lyric-cover-gap` | 默认 `40px`（`coverGap`，0–160，步进 2）；standard 布局总宽 `min(100%, 360px + gap + 820px)`（默认 1220px） | `PlayingMusic.vue:151,1163-1169`；`lyricsAppearance.ts:234,137` |
| 无歌词时 | `.layout--single { grid-template-columns: minmax(300px, 440px); align-content/justify-content: center }`；`.cover-column { width: min(100%, 440px) }`；`.cover-meta { text-align: center }` | `PlayingMusic.vue:1139-1153` |
| `.cover-column` | `flex; flex-direction: column; gap: 18px; align-self: center` | `PlayingMusic.vue:1155-1161` |

### 4.2 大封面尺寸 / 圆角 / 阴影

| 元素 | 属性 → 值 | 来源 |
|---|---|---|
| `.cover-frame` 宽度 | `min(var(--te-playback-cover-size, 100%), max(120px, calc(100vh − 100px − var(--te-font-size-body,14px) × 180 / 14)))` ⇒ 上限 `max(120px, 100vh − 280px)`；`max-width: 100%` | `PlayingMusic.vue:1172-1176` |
| `--te-playback-cover-size` / `--te-playback-cover-radius` | 默认 `100%`（60–110，步进 1）／ 默认 **`26px`**（0–64，步进 1）；**仅当偏离默认才写 inline** | `lyricsAppearance.ts:237-238,140-141`；`PlayingMusic.vue:162-171` |
| `.cover-frame` 比例 / 圆角 | `aspect-ratio: 1`；`border-radius: var(--te-playback-cover-radius, 26px)` | `PlayingMusic.vue:1179-1180` |
| `.cover-frame` 底色 / 阴影 | 浅色：`rgba(255,255,255,0.06)` + `0 26px 70px rgba(0,0,0,0.38)`；light/pureWhite：`rgba(15,23,42,0.08)` + `0 26px 70px rgba(15,23,42,0.28)`；dark：`rgba(15,23,42,0.45)` + `0 26px 70px rgba(0,0,0,0.55), inset 0 0 0 1px rgba(255,255,255,0.06)` | `PlayingMusic.vue:1182-1183,1206-1210,1197-1204` |
| 封面图 / 占位 | `.cover-image { width/height: 100%; object-fit: cover }`；占位图标 `68px`、`rgba(255,255,255,0.34)` | `PlayingMusic.vue:1216-1221,1229-1230` |

### 4.3 标题 / 歌手 / 专辑相对封面的位置

`cover-meta` 是 `.cover-column` 的第二个子元素，与封面间隔 **`gap: 18px`**（`PlayingMusic.vue:1159`）。

| 元素 | 属性 → 值 | 来源 |
|---|---|---|
| `.track-title` | `margin: 0`；`font-family: var(--te-font-display)`；`calc(var(--te-font-size-body,14px) × 32 / 14)`（**32px**）；**`font-weight: 400`**；`line-height: 1.22`；`color: var(--te-playback-track-title, #fff)`；`-webkit-line-clamp: 2` | `PlayingMusic.vue:1258-1269`；`base.css:53` |
| `.track-artist` | `margin: 10px 0 0`；`calc(var(--te-font-size-body,14px) × 18 / 14)`（**18px**）；**`700`**；`color: var(--te-playback-track-artist, rgba(255,255,255,0.78))`；单行省略 | `PlayingMusic.vue:1271-1280` |
| `.track-album` | `margin: 4px 0 0`；`14/14`（14px）；`500`；`color: var(--te-playback-track-album, rgba(255,255,255,0.48))` | `PlayingMusic.vue:1282-1291` |
| ≤760px | 标题 `28/14`（28px）；歌手 `16/14`（16px） | `PlayingMusic.vue:1798-1804` |

### 4.4 沉浸页里的"控件"

| 元素 | 属性 → 值 | 来源 |
|---|---|---|
| **结论** | 沉浸页自身**没有**传输按钮 / 进度条 / 音量 / 队列；传输由浮在上方的播放条（glass 形态，见 §1.1 编排）提供 | `PlayingMusic.vue:654-673`；`shared/playerBarLayout.ts:92-93` |
| `.playback-time` 容器 | `position: absolute; top: 42px; right: 42px; z-index: 2`（≤760px 时 `right: 16px`） | `PlayingMusic.vue:1327-1332,1794-1796` |
| `.time-chip` | `padding: 8px 12px`；`border-radius: 999px`；`border: 1px solid var(--te-playback-control-border, rgba(255,255,255,0.1))`；`background: var(--te-playback-control-surface, rgba(255,255,255,0.08))`；`color: var(--te-playback-control-text, rgba(255,255,255,0.7))`；字号 `12/14`（12px）；`tabular-nums` | `PlayingMusic.vue:1334-1343`；`components/PlayingMusicTimeChip.vue:10` |
| 视觉化开关 | `position: fixed; top: 42px; left: 42px`；`40px × 40px`；`border-radius: 999px`；`backdrop-filter: blur(10px)`；字号 `16/14`（16px）；`z-index: 1200`；过渡含 `var(--te-ease-spring)` | `PlayingMusic.vue:1816-1841` |
| ≤1120px 单列 | `.stage { padding: 98px 22px 20px }`；`.layout { columns: 1fr; rows: auto minmax(0,1fr); gap: min(cover-gap, 28px) }`；`.cover-column { display: grid; grid-template-columns: minmax(132px,180px) minmax(0,1fr); gap: 22px; width: min(100%,720px) }`；`.cover-frame { width: min(100%, 180px) }`；`.lyrics-column { padding-left: 0 }` | `PlayingMusic.vue:1743-1781` |
| ≤760px | `.stage { padding: 98px 16px 16px }`；`.lyric-row { padding-inline: 12px }` | `PlayingMusic.vue:1788-1812` |

### 4.5 背景处理（三种模式）

| 模式 | 处理 → 值 | 来源 |
|---|---|---|
| `bg-blur`（默认） | 封面铺满 + `transform: scale(1.06)` + `filter: blur(58px) saturate(1.28) brightness(0.42)`；浅色主题 `blur(58px) saturate(1.22) brightness(0.52)`；暗色 `blur(58px) saturate(1.32) brightness(0.36)` | `PlayingMusic.vue:964-986` |
| 遮罩层 | `linear-gradient(180deg, rgba(5,7,11,0.72) 0%, rgba(5,7,11,0.74) 52%, rgba(5,7,11,0.78) 100%)` + `color-mix(in srgb, var(--accent-color) 8%, transparent)`；`backdrop-filter: blur(10px)` | `PlayingMusic.vue:1023-1038` |
| 强调光斑层 | `radial-gradient(circle at 18% 26%, color-mix(accent 22%), transparent 42%)` + `radial-gradient(circle at 88% 20%, rgba(255,255,255,0.12), transparent 26%)`；整层 `opacity: 0.8` | `PlayingMusic.vue:1040-1056` |
| `bg-fluid` | 伪元素 `inset: -50%`；`linear-gradient(135deg, #0f172a, #1e3a5f, #312e81, #1e3a5f, #0f172a)`；`background-size: 400% 400%`；`animation: fluid-drift-transform 18s ease-in-out infinite`；关键帧 `0/100% (0,0)`、`25% (-12%,0)`、`50% (-12%,-12%)`、`75% (0,-12%)` | `PlayingMusic.vue:1058-1076,1088-1102` |
| `bg-solid` | 直接用 `--te-player-bg` / `--te-player-bg-image`，`center / cover / no-repeat` | `PlayingMusic.vue:1078-1086` |

---

## 5. 移植要点（三条最关键的数值）

1. **播放条整体轮廓**：`height: 72px` + 外壳 `bottom: 14px / left: 18px / right: 18px` + `max-width: 1180px` + `border-radius: 22px` + `padding: 0 22px` +
   `backdrop-filter: blur(24px) saturate(160%)` + 背景 `rgba(255,255,255,0.48)` 叠 `linear-gradient(145deg, rgba(255,255,255,0.66), rgba(248,245,255,0.42))`
   （`PlayerBar.css:17-25,822-835`）。沉浸页打开时切 `player-bar-glass`：`rgba(14,14,14,0.86)`、`blur(28px) saturate(145%)`、`border-top-color: rgba(255,255,255,0.14)`、向上投影（`855-866`）。
   内部三件套：封面 **`48px` / 圆角 `12px` / 阴影 `0 14px 32px rgba(15,23,42,0.12)`**（`971-986`）；传输 `gap 12px` + `padding 6px` + 图标 `18px` + **播放键 `44px` 圆形**（`1269-1339`）；
   进度轨 **`6px` / 圆角 `999px`** + 未填充 `accent 18%` + 填充 `linear-gradient(90deg, accent, #0d9488)` + 命中高 `24px` + **无 thumb、无 hover 增高**（`1355-1412,1485-1514`）。

2. **歌词活动行的锚点与运动学**：`alignPosition = 0.35`、锚点居中（再减 `anchorLine.height/2`）、`visibleHeight = viewportHeight − 播放条高度`、行距 `10px`
   （`lyricLineLayout.ts:52,166,205-211`；`PlayingMusic.vue:227,373`）；位移弹簧 `mass 0.9 / damping 13 / stiffness 90`，缩放弹簧 `mass 2 / damping 25 / stiffness 100`（`lyricSpring.ts:25-31,37-41`）；
   缩放 `104% / 102% / 100% / 背景 75%`、不透明度 `1 / 0.86 / 0.68 / 0.46`、模糊 `clamp((1 + distance) × 0.35 × blurScale, 0, 4)px`（`lyricLineLayout.ts:25-42,247-277`）；
   级联延迟 `0.08s` 起步、每步 `/1.05`、上限 `0.4s`（`44-46,286-291`）。少了锚点 + 级联，静止的行距和位置对了，运动仍然是刚性整块平移。

3. **封面取色 + 归一化**：`50 × 50` canvas → 每通道 `12` 桶 → 丢弃 `alpha < 128`、`max − min < 15`、`max < 40 || min > 220` → 取最大桶反量化，兜底 `#1a73e8`（`colorExtractor.ts:91-143`）；
   再经 `normalizeAccentColor`：**`s' = clamp(s × 0.56 + 0.18, 0.32, 0.64)`、`l' = clamp(l × 0.42 + 0.25, 0.34, 0.52)`**，兜底 `#3567b5`（`249-261`）。
   播放键用**归一化后**的色（`--play-button-color`，`PlayerBar.vue:247,1705`），其余元素用**原始** `dominantColor`（`--accent-color`）。直接用原始主色会让播放键在深色封面上糊成一块。

### 仍属未知 / 需实机验证

| 项 | 状态 |
|---|---|
| 播放条的"缓冲（buffered）"进度层 | **未找到**——参考实现只有 `progress-track`（18% accent 底）与 `progress-fill` 两层，没有二级缓冲条 |
| 进度条 hover 增高 / 拖动 thumb | **未找到**——thumb 明确 `0×0`，无任何 hover 规则 |
| `--te-lg-blur` / `--te-lg-saturate` / `--te-lg-tint` / `--te-lg-elasticity` 的静态声明 | 只出现在 `var()` 回退里（`16px` / `140%` / `0.12` / `0`），实际值由主题编辑器写入，**快照内无静态声明** |
| `--te-playbar-height` | 只在仪表盘 CSS 的 `var(--te-playbar-height, 88px)` 回退里出现，**无静态声明**；歌词锚点用的是 `.player-bar-shell` 的实测高度（`PlayingMusic.vue:258-266`），不是这个令牌 |
| 与 `fidelity-map.md` 截图实测的差额 | 该文档实测白条 `y = 796..865`（≈70px）与 CSS `72px` 差 ~2px，属截图像素误差量级，以 CSS 为准 |
