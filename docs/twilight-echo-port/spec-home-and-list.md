# 流媒体首页与歌曲列表 · 精确规格

参考项目 `source/Twilight_Echo`（Twilight Echo，Apache-2.0）。本文只提取
**流媒体首页（ProviderMusicHome）** 与 **歌曲列表（SongList）** 的视觉值，
每条数值都带 `path:line`。布局意图见 `fidelity-map.md`，接线见 `wiring-status.md`。

> **读法**：字号在参考里统一写成 `calc(var(--te-font-size-body, 14px) * N / 14)`，
> 即 `N` 就是设计像素值（body = 14px，见 `assets/base.css:66`）；下表已换算成
> 像素，并保留原始写法便于核对。行号路径相对于 `source/Twilight_Echo/`。

---

## 0. 共享 token（首页与列表都依赖）

| token | 值 | 来源 |
| --- | --- | --- |
| `--te-font-size-body` | `14px`（所有字号的分母） | `src/renderer/src/assets/base.css:66` |
| 缓动 | `--te-ease-out-quint: cubic-bezier(0.22, 1, 0.36, 1)`；`out-expo: (0.16, 1, 0.3, 1)`；`out-strong: (0.23, 1, 0.32, 1)` | `base.css:35-38` |
| `--te-motion-hover` / `panel` / `page` | `160ms` / `280ms` / `400ms` | `base.css:40-42` |
| `--te-hover-bg` 浅 / 暗 | `#f3f4f6` / `rgba(255, 255, 255, 0.065)` | `base.css:166` / `base.css:534` |
| `--te-card-bg` / `--te-card-border` 浅 / 暗 | `#ffffff` + `rgba(15,23,42,0.08)` / `#181818` + `rgba(255,255,255,0.1)` | `base.css:163-164` / `base.css:531-532` |
| `--te-neutral-50/500/700/900`（浅色） | `#fafafa` / `#6b7280` / `#374151` / `#111827` | `base.css:12-18` |
| `--te-primary-500` / `--te-success-500` / `--te-accent-cyan` | `#7c4dff` / `#20c65e` / `#22d3ee` | `base.css:3,8,11` |
| `--te-scrollbar-thumb` 浅 / 暗 | `rgba(71, 85, 105, 0.34)` / `rgba(212, 212, 216, 0.34)` | `base.css:211` / `base.css:537` |
| `--te-library-row-text` 浅 / 暗 | `#334155` / `#d8d8d8` | `base.css:121` / `base.css:576` |
| `--te-library-table-bg/border/shadow` | `rgba(255,255,255,0.16)` / `rgba(255,255,255,0.52)` / `0 26px 78px rgba(86,70,160,0.1)` | `base.css:118-120` |
| `--te-library-cover-radius` / `--te-artwork-list-radius` / `--te-radius-global` | `8px` / `12px` / `10px` | `base.css:130` / `base.css:135` / `base.css:50` |

首页的强调色由父级注入：`:style="{ '--provider-accent': providerColor || 'var(--te-primary-500)' }"`
（`streaming-page/ProviderMusicHome.vue:76`），即未取到封面色时退化为 `#7c4dff`。

---

## 1. 首页 Hero / Banner

### 1.1 容器与布局

| 元素 | 属性 | 值 | 来源 |
| --- | --- | --- | --- |
| `.music-home` | 底部留白 / 容器查询 | `padding-bottom: 28px`；`container-type: inline-size`（`cqw` 与 `@container` 的前提） | `streaming-page/ProviderMusicHome.css:2-4` |
| `.music-hero` | 网格 / 最小高度 | `grid-template-columns: 1.1fr 1fr`；**`min-height: 328px`** | `ProviderMusicHome.css:74,77` |
| `.music-hero` | **圆角 / 裁剪** | `border-radius: 22px`；`overflow: hidden; position: relative; isolation: isolate` | `ProviderMusicHome.css:75-79` |
| `.music-hero` | **背景** | `radial-gradient(ellipse at 84% 30%, color-mix(in srgb, var(--provider-accent) 42%, var(--music-hero-surface-deep)), transparent 75%), var(--music-hero-surface)` | `ProviderMusicHome.css:81-87` |
| `--music-hero-surface` / `-deep` | 底色 / 深底色 | `color-mix(in srgb, var(--te-neutral-900) 88%, var(--te-neutral-50))` → 等效 `#1f2533`；72% 版本 → 等效 `#464c5a` | `ProviderMusicHome.css:8-9` |
| `--music-hero-text` / `-muted` / `-soft` / `-caption` | 四级前景 | `var(--te-neutral-50)` = `#fafafa`；78% / 68% / 50% 透明度（暗色主题把 text 换成 `--te-neutral-900`） | `ProviderMusicHome.css:10-13`，`:28-31` |
| `--music-hero-line` / `-strong` | 描边 | text 的 12% / 20% 透明度 | `ProviderMusicHome.css:14-15` |
| `.music-hero-copy` | 内边距 / 排布 | `padding: 38px 0 26px 38px`（顶 38 / 右 0 / 底 26 / 左 38）；`flex; column; align-items: flex-start; z-index: 2` | `ProviderMusicHome.css:89-95` |

### 1.2 Hero 文字

| 元素 | 属性 | 值 | 来源 |
| --- | --- | --- | --- |
| `.music-eyebrow` | 字号 / 字重 / 字距 | `10px` / `650` / `letter-spacing: 1.8px`；`display: flex; gap: 8px` | `ProviderMusicHome.css:64-71` |
| `.music-hero .music-eyebrow` | 字号 / 颜色 | `9px`；`color-mix(in srgb, var(--provider-accent) 46%, var(--music-hero-text))`；其 `span` 计数靠右（`margin-left: auto`、text 40% 透明、`tabular-nums`、字距 `2px`） | `ProviderMusicHome.css:97-107` |
| `.music-hero h2` | **字号 / 字重 / 行高** | `clamp(34px, 5.7cqw, 61px)`；`font-weight: 700`；`line-height: 1.12`；`letter-spacing: -2px`；`margin-top: 32px` | `ProviderMusicHome.css:108-114` |
| `.music-hero-description` | **字号 / 行高 / 宽度** | `13px` / `1.9`；`max-width: 325px`；`margin-top: 17px`；色 `--music-hero-text-muted` | `ProviderMusicHome.css:115-122` |
| `.music-hero-foot` | 字号 / 行高 / 宽度 | `10px` / `1.6`；`margin-top: auto`；`max-width: 360px`；色 `--music-hero-text-soft` | `ProviderMusicHome.css:156-162` |

### 1.3 Hero 按钮组

| 按钮 | 属性 | 值 | 来源 |
| --- | --- | --- | --- |
| `.music-hero-actions` | 间距 / 外边距 | `gap: 22px`；`margin: 27px 0 24px`；`align-items: center` | `ProviderMusicHome.css:123-127` |
| **主按钮** `.music-primary` | **高度 / 内边距 / 圆角** | `min-height: 44px`；`padding: 0 24px`；`border-radius: 24px`；`display: inline-flex; gap: 10px; border: 0` | `ProviderMusicHome.css:129-139` |
| 主按钮 | 配色 / 字号 | `background: var(--music-hero-text)`（`#fafafa`）；`color: var(--music-hero-button-text)`（`--te-neutral-900`）；`13px` / `650` | `ProviderMusicHome.css:136-141` |
| 主按钮 hover / 按下 / 禁用 | 状态 | hover 底色 `--music-hero-button-hover` = `color-mix(in srgb, var(--te-success-500) 18%, var(--music-hero-text))`；按下 `transform: scale(0.98)`；禁用 `opacity: 0.5` | `ProviderMusicHome.css:17`，`:667-669`，`:56-58`，`:48-51` |
| 主按钮图标 `i` | 字号 | `12px` | `ProviderMusicHome.css:143-145` |
| **次按钮** `.music-hero-open` | 内边距 / 字号 | `padding: 10px 0`；`background: transparent; border: 0`；`display: flex; gap: 10px`；`12px`；文字色 `--music-hero-text-soft` | `ProviderMusicHome.css:146-155` |
| 焦点圈 / 过渡 | 全部按钮 | `outline: 2px solid var(--provider-accent); outline-offset: 5px`；`background 160ms ease, transform 160ms var(--te-ease-out-quint)` | `ProviderMusicHome.css:52-55`，`:44-47` |

### 1.4 Hero 右侧贴图（三张旋转封面）

| 元素 | 属性 | 值 | 来源 |
| --- | --- | --- | --- |
| `.music-orbit` | 尺寸 / 边框 / 定位 | `width: 390px; aspect-ratio: 1`；`border: 1px solid var(--music-hero-line)`；`border-radius: 50%`；`top: 50%; left: 51%; translate(-50%, -50%)`；内圈 `.music-orbit-inner` 宽 `305px` | `ProviderMusicHome.css:169-180` |
| `.music-artwork-cover` | **尺寸 / 圆角 / 阴影** | `width: clamp(125px, 20cqw, 216px); aspect-ratio: 1`；`border-radius: 9px`；`box-shadow: 0 18px 32px var(--music-hero-shadow)`；`border: 1px solid var(--music-hero-line-strong)`；`top: 48%; left: 48%` | `ProviderMusicHome.css:182-190` |
| 第 1 / 2 / 3 张 | 旋转叠放 | `rotate(-8deg)` / `rotate(12deg) scale(0.88)` / `rotate(-22deg) scale(0.8)`，`z-index: 3 / 2 / 1` | `ProviderMusicHome.css:198-209` |
| `.music-record`（无封面回退） | 尺寸 / 背景 | `190px` 圆；`repeating-radial-gradient(circle, var(--music-record-surface) 0 3px, var(--music-record-ridge) 4px 5px)`；内部圆钮 `62px × 62px`，底 `var(--provider-accent)`，图标 `26px` | `ProviderMusicHome.css:220-245` |
| `.music-artwork-caption` | 文字 | `font-size: 8px; letter-spacing: 3px`；`bottom: 30px`；居中 | `ProviderMusicHome.css:210-218` |

---

## 2. 首页各 Section

### 2.1 Section 间距与标题（"more" 位置）

| 元素 | 属性 | 值 | 来源 |
| --- | --- | --- | --- |
| `.music-track-section` / `.music-features` / `.music-playlists` | **区块间距** | `margin-top: 34px` | `ProviderMusicHome.css:267-271` |
| `.music-login-strip`（未登录提示条） | 上边距 / 字号 | `margin-top: 18px`；`11px`；色 `--music-muted` = `--te-neutral-500`；`display: flex; justify-content: space-between; gap: 14px` | `ProviderMusicHome.css:246-254` |
| `.music-section-head` | 排布 / 下外边距 | `display: flex; align-items: center; justify-content: space-between; gap: 16px`；`margin-bottom: 18px` | `ProviderMusicHome.css:272-278` |
| `.music-section-head h2` | **字号 / 字重 / 字距** | `18px` / `650` / `-0.4px`；英文副标 `h2 span` 为 `8px` / `500` / `letter-spacing: 1.4px` / 色 `--music-muted` / `margin-left: 8px` | `ProviderMusicHome.css:279-291` |
| `.music-section-head button`（"more"） | **尺寸 / 字号** | 无高度约束，`padding: 6px 0`；`display: inline-flex; gap: 10px`；`border: 0; background: transparent`；`11px`；色 `var(--music-muted)` | `ProviderMusicHome.css:255-266`，`:292-294` |

### 2.2 "先听这几首" 双列曲目（`.music-track-grid`）

| 元素 | 属性 | 值 | 来源 |
| --- | --- | --- | --- |
| `.music-track-grid` | **列 / 列间距** | `grid-template-columns: repeat(2, minmax(0, 1fr))`；`column-gap: 25px`（无 row-gap） | `ProviderMusicHome.css:295-299` |
| `.music-track` | 内边距 / 圆角 / 内部 | `padding: 10px 8px`；`border-radius: 10px`；`display: flex; align-items: center; gap: 13px` | `ProviderMusicHome.css:300-311` |
| `.music-track-number` | 列宽 / 字号 | `flex: 0 0 18px`；`11px`；`tabular-nums`；色 `--music-muted` | `ProviderMusicHome.css:312-317` |
| `.music-track-cover` | **尺寸 / 圆角** | `45px × 45px`；`border-radius: 6px`；`background: color-mix(in srgb, var(--provider-accent) 13%, var(--music-surface))` | `ProviderMusicHome.css:318-326` |
| `.music-track-cover i`（hover 播放遮罩） | 覆盖 / 底色 | `position: absolute; inset: 0`；`background: var(--music-hero-overlay)`（= button-text 22% 透明）；默认 `opacity: 0` | `ProviderMusicHome.css:333-342` |
| `.music-track-meta` | 内部 / 字号 | `flex-direction: column; gap: 7px; flex: 1`；曲名 `strong` `12px` / `550`，歌手 `span` `10px` / `--music-muted`；均单行省略 | `ProviderMusicHome.css:343-363` |
| `.music-track-duration` | 字号 / 数字 | `10px`；`tabular-nums`；色 `--music-muted` | `ProviderMusicHome.css:364-368` |
| `.music-track:hover` / `.is-current` | 背景 | hover `var(--te-hover-bg)`；正在播放 `color-mix(in srgb, var(--provider-accent) 10%, transparent)`，并把封面播放遮罩设为 `opacity: 1` | `ProviderMusicHome.css:662-666`，`:654-659` |

### 2.3 双卡推荐（`.music-features`）

| 元素 | 属性 | 值 | 来源 |
| --- | --- | --- | --- |
| `.music-features` | 网格 | `repeat(2, minmax(0, 1fr))`；`gap: 20px` | `ProviderMusicHome.css:369-373` |
| `.music-feature` | 内边距 / 圆角 / 背景 | `padding: 25px`；`border-radius: 18px`；`border: 1px solid var(--music-line)`；`linear-gradient(140deg, color-mix(in srgb, var(--provider-accent) 10%, transparent), transparent 65%), var(--music-surface)` | `ProviderMusicHome.css:374-386` |
| `.music-feature-alt`（第二张卡） | 背景 | 同上但把 accent 换成 `var(--te-accent-cyan)` 8% | `ProviderMusicHome.css:387-395` |
| `.music-feature h2` / `.music-eyebrow` | 字号 / 字重 | h2 `21px` / `650` / `letter-spacing: -0.5px` / `margin-top: 9px`；eyebrow `8px` / 字距 `1.4px` / 色 `--music-muted` | `ProviderMusicHome.css:396-412` |
| `.music-feature-play`（圆形播放键） | **尺寸** | `42px × 42px`；`border-radius: 50%`；`border: 1px solid var(--music-line)`；`background: var(--music-surface)` | `ProviderMusicHome.css:413-423` |
| `.music-feature-description` | 字号 / 行高 | `11px` / `1.7`；`margin: 14px 0 18px`；色 `--music-muted` | `ProviderMusicHome.css:424-429` |
| `.music-feature-track` | 内边距 / 圆角 / 子项 | `padding: 7px 0`；`border-radius: 6px`；`gap: 11px`；序号 `width: 17px` / `10px`；封面 `34px × 34px` / 圆角 `5px` / accent 12% 底；曲名 `11px` / `550`；副标 `9px` / `margin-top: 5px` | `ProviderMusicHome.css:430-485` |
| `.music-feature-more`（卡内"查看全部"） | 排布 / 分割线 | `padding: 15px 0 0`；`margin-top: 17px`；`border-top: 1px solid var(--music-line)`；`font-size: 10px`；色 `--music-muted`；`justify-content: space-between` | `ProviderMusicHome.css:486-498` |

### 2.4 歌单卡片网格（`.music-playlist-grid`）

| 元素 | 属性 | 值 | 来源 |
| --- | --- | --- | --- |
| `.music-playlist-grid` | **列数 / 间距** | `repeat(6, minmax(0, 1fr))`；`gap: 24px 18px`（行 24 / 列 18）；卡片数上限 `recommendPlaylists.slice(0, 12)` → 6 列正好两行 | `ProviderMusicHome.css:499-503`；`ProviderMusicHome.vue:36` |
| `.music-playlist` | 排布 | `display: flex; flex-direction: column`；`padding: 0; border: 0; background: transparent` | `ProviderMusicHome.css:504-513` |
| `.music-playlist-art` | **宽高比 / 圆角 / 底色** | `width: 100%; aspect-ratio: 1`；`border-radius: 11px`；`color-mix(in srgb, var(--provider-accent) 13%, var(--music-surface))` | `ProviderMusicHome.css:514-522` |
| `.music-playlist-count`（封面底部播放量） | 内边距 / 渐变 | `padding: 22px 10px 9px`；`linear-gradient(transparent, color-mix(in srgb, var(--music-hero-button-text) 47%, transparent))`；`font-size: 8px` | `ProviderMusicHome.css:529-541` |
| `.music-playlist-symbol`（hover 播放圆钮） | **尺寸 / 位置 / 默认态** | `29px × 29px`；`border-radius: 50%`；`right: 9px; bottom: 30px`；底色 `color-mix(in srgb, var(--music-hero-text) 93%, transparent)`；`opacity: 0`；`transition: opacity 160ms ease` | `ProviderMusicHome.css:542-555` |
| `.music-playlist strong` / `small` | 字号 | 歌单名 `11px` / `550` / `line-height: 1.65` / `margin-top: 10px` / `-webkit-line-clamp: 2`；作者 `9px` / `margin-top: 5px` / 色 `--music-muted` | `ProviderMusicHome.css:557-575` |
| **卡片 hover** | 变化 | 仅把 `.music-playlist-symbol` 的 `opacity` 提到 `1`——**卡片本身没有位移、缩放或阴影变化** | `ProviderMusicHome.css:670-673` |

### 2.5 响应式（容器查询，按首页自身宽度）

| 断点 | 变化 | 来源 |
| --- | --- | --- |
| `@container (max-width: 800px)` | 歌单网格 → `repeat(4, ...)`；隐藏标题英文副标与 eyebrow 计数；`.music-hero-copy` 左内边距 `28px`；`.music-feature` 内边距 `20px` | `ProviderMusicHome.css:680-696` |
| `@container (max-width: 560px)` | hero `min-height: 310px`、单列（`grid-template-columns: 1fr`）；`.music-hero-copy` `padding: 28px`；h2 固定 `42px`；隐藏贴图区；描述取消 `max-width`；曲目/双卡改单列；歌单网格 `repeat(3, ...)` + `gap: 20px 12px`；区块标题 `16px` | `ProviderMusicHome.css:697-730` |

---

## 3. 歌曲列表（SongList）

### 3.1 页面与滚动容器

| 元素 | 属性 | 值 | 来源 |
| --- | --- | --- | --- |
| `.song-list` | **内边距** | `padding: calc(32px + 30px) min(4.4vw, 48px) 132px`（上 62 / 左右 `min(4.4vw,48px)` / 下 132） | `song-list/SongList.css:34` |
| `.song-list` | 滚动 | `overflow-y: auto; overflow-x: hidden; display: grid` | `SongList.css:32-36` |
| `.song-list > *` | 叠放 | `grid-area: 1 / 1`（表格与网格视图共用同一格） | `SongList.css:66-68` |
| 紧凑密度 `data-te-library-density='compact'` | 覆盖 | `padding: 54px min(3vw, 34px) 116px` | `SongList.css:2279-2281` |
| `.grid-view` / `.table-view` | 变换原点 | `transform-origin: top center` | `SongList.css:70-75` |
| `.grid-view::before` / `.table-view::before` | 环境光 | `inset: -30px -28px auto; height: 230px; z-index: -1`；三层 `radial-gradient`（紫 3.5% / 粉 3.5% / 蓝 3.2%） | `SongList.css:77-89` |

### 3.2 列表头部

| 元素 | 属性 | 值 | 来源 |
| --- | --- | --- | --- |
| `.song-list-header` | 排布 | `flex; align-items: flex-end; justify-content: space-between; gap: 24px`；`min-height: 64px`；`margin-bottom: 26px`；`padding: 6px 32px 0` | `SongList.css:91-107` |
| `.song-list-title` | 字号 / 字重 | `clamp(28px, 2.8vw, 36px)` / `800`；`letter-spacing: -0.015em`；`line-height: 1.1` | `SongList.css:286-293` |
| `.view-stats` | 字号 / 颜色 | `12.5px`（`0.89286 × 14`）；`var(--te-neutral-500)`；`letter-spacing: 0.03em`；`transform: translateY(-2px)` | `SongList.css:147-153` |
| `.library-play-actions` | 排布 | `display: inline-flex; gap: 8px`；`margin: 0 0 20px`；`padding: 0 32px` | `SongList.css:156-163` |
| `.btn-play-all` / `.btn-shuffle-all` | **尺寸 / 圆角** | `height: 36px`；`padding: 0 17px`；`border-radius: 999px`；`gap: 7px` | `SongList.css:165-182` / `:198-215` |
| 两个按钮 | 边框 / 文字 | `border: 1px solid var(--te-neutral-300)`；`background: transparent`；`color: var(--te-neutral-700)`；字号 `12.5px`；字重 `600` | `SongList.css:171-176` |
| 两个按钮 | 图标字号 | `14px` | `SongList.css:184-186` / `:217-219` |
| 两个按钮 hover | 变化 | `background: var(--te-neutral-100)`；`border-color: var(--te-neutral-500)`；`color: var(--te-neutral-900)`；过渡 `0.18s` | `SongList.css:188-192` |
| 两个按钮禁用 | 透明度 | `opacity: 0.4` | `SongList.css:240-244` |

### 3.3 表格外壳与行几何（**核心**）

行高由 JS 常量决定，不是 CSS 写死的：

| 元素 | 属性 | 值 | 来源 |
| --- | --- | --- | --- |
| **行距（虚拟滚动步进）** | 常量 / 行元素高度 | `ROW_HEIGHT = 68`；行内联样式 `height: rowHeight - 4` → **64px**，余下 4px 由 `margin: 2px 0` 提供（2 + 64 + 2 = 68） | `song-list/useSongListVirtualScroll.ts:23`；`SongList.vue:2313`；`SongList.css:1117` |
| `.track-row` | 圆角 | `border-radius: var(--te-radius-global)` = `10px` | `SongList.css:1116` |
| 虚拟窗口 | 行数 | `Math.ceil(viewportHeight / rowHeight) + overscanRows`，`overscanRows` 默认 `6` | `song-list/songListVirtualWindow.ts:46-51` |
| `.track-table-wrapper` | 内边距 / 圆角 | `padding: 20px 18px 18px`；`border-radius: calc(var(--te-radius-global) + 6px)` = **16px** | `SongList.css:1056-1059` |
| `.track-table-wrapper` | 边框 / 底色 / 阴影 | `1px solid var(--te-library-table-border)` = `rgba(255,255,255,0.52)`；`background: color-mix(in srgb, var(--te-library-table-bg) var(--te-surface-opacity), transparent)`；`box-shadow: var(--te-library-table-shadow)`；`backdrop-filter: blur(30px) saturate(168%)` | `SongList.css:1060-1064` |
| `.track-table` | 表格模式 | `border-collapse: separate; border-spacing: 0; display: block`（`th/td` 都是 flex 单元格） | `SongList.css:1066-1071` |
| `.track-table thead` | 吸顶 | `position: sticky; top: 0; z-index: 2`；`background: var(--te-glass-bg)`；`backdrop-filter: blur(18px) saturate(140%)`；`border-radius: 10px`；`box-shadow: 0 1px 0 rgba(86,70,160,0.08)` | `SongList.css:1072-1083` |
| 表头单元格 `.track-table th` | 内边距 / 字号 | `padding: 2px 14px 12px`；`10.5px`（`0.75 × 14`）；`700`；`text-transform: uppercase`；`letter-spacing: 0.14em`；色 `color-mix(in srgb, var(--te-library-row-text) 72%, transparent)` | `SongList.css:1087-1096` |
| 行单元格 `.track-row td` | **垂直内边距 / 字号** | `padding: 0 14px`（**垂直为 0，高度全由行高给**）；`13px`（`0.92857 × 14`）；`color: var(--te-library-row-text)`；`display: flex; align-items: center; border-bottom: 0` | `SongList.css:1097-1105` |
| 表头列名（模板） | 顺序 | `#` / （封面，空） / `标题` / `专辑` / `时长` | `SongList.vue:2285-2289` |

### 3.4 各列宽度、字号与颜色

| 列 | 属性 | 值 | 来源 |
| --- | --- | --- | --- |
| **序号 `.col-index`** | **宽度 / 字号** | `width: 46px; flex-shrink: 0`；`13px !important`；色 `rgba(80, 88, 116, 0.62) !important` | `SongList.css:1366-1371` |
| 封面 `.col-cover` / `.col-cover-header` | 宽度 | `64px; flex-shrink: 0`；进入多选后 `88px` | `SongList.css:1390-1398` |
| 封面格 `.track-cover-cell` | 尺寸 | `40px × 40px`（`has-selection` 时 `64px`，`gap: 6px`） | `SongList.css:1274-1288` |
| **封面图 `.cover-img` / `.cover-placeholder`** | **尺寸 / 圆角 / 阴影** | `40px × 40px`；`border-radius: var(--te-artwork-list-radius, var(--te-library-cover-radius))` → 兜底 `12px`，未改主题时 `8px`；`box-shadow: 0 10px 22px rgba(86,70,160,0.14)` | `SongList.css:1338-1348` |
| 封面占位 | 背景 | `radial-gradient(circle at 35% 30%, rgba(255,255,255,0.9), transparent 36%), linear-gradient(135deg, rgba(124,77,255,0.18), rgba(34,211,238,0.12))` | `SongList.css:1349-1351` |
| 信息列 `.col-info` + `.track-title-row` | 宽度 / 网格 | `flex: 1.25`；`line-height: 1.4`；内部 `grid-template-columns: minmax(0, 1fr) auto`、`gap: 8px` | `SongList.css:1410-1426` |
| **曲名 `.track-title`** | **字号 / 字重 / 颜色** | `14px`（`1 × 14`）；`font-weight: 700`；`font-family: var(--te-font-rounded)`；`letter-spacing: 0.005em`；`color: var(--te-neutral-900)`；播放中变 `#6f4ee8` | `SongList.css:1428-1435`，`:1485-1487` |
| **歌手 `.track-artist`** | **字号 / 字重 / 颜色** | `12px`（`0.85714 × 14`）；`font-weight: 500`；`margin-top: 2px`；`rgba(71, 80, 112, 0.68)` | `SongList.css:1488-1494` |
| 来源徽标 `.track-source-chip` | 尺寸 / 圆角 | `padding: 2px 7px`；`border-radius: 999px`；`max-width: 92px`；字号 `11px`；字重 `800` | `SongList.css:1450-1472` |
| **时长列 `.col-duration`** | 对齐 | `flex-direction: column; align-items: flex-end !important; justify-content: center; gap: 2px`——**没有固定宽度**，靠右由信息列 `flex: 1.25` 撑开 | `SongList.css:1373-1378` |
| 时长文本 / 音质副标 | 数字与字号 | `.duration-time { font-variant-numeric: tabular-nums }`；`.duration-quality` `10px`（`0.71429 × 14`）、`rgba(80,88,116,0.55)`、`letter-spacing: 0.05em` | `SongList.css:1380-1389` |
| 专辑列 | 无 `.col-album` 规则 | 模板里有 `<th class="col-album">专辑</th>` 与对应 `<td>`，但 SongList.css **未定义**该类——走 `.track-row td` 的继承（`13px` / `--te-library-row-text`） | `SongList.vue:2288,2396`；`SongList.css:1097-1105` |

### 3.5 行的 hover / 播放 / 选中态与分割线

| 状态 | 属性 | 值 | 来源 |
| --- | --- | --- | --- |
| `.track-row` | **过渡** | `background 0.22s, transform 0.24s var(--te-ease-soft), box-shadow 0.24s, filter 0.24s` | `SongList.css:1107-1114` |
| **`.track-row:hover`** | **背景 / 阴影** | `background: transparent`（**没有底色**）；`box-shadow: inset 0 0 0 1px var(--te-library-row-hover-border, rgba(124, 77, 255, 0.22))`——只有一圈 1px 静态细描边；`::before` / `::after` 在 hover 下 `opacity: 0; animation: none !important` | `SongList.css:1186-1199` |
| `.track-playing`（正在播放） | 底色 / 描边 | `background: var(--te-playing-row-bg, rgba(124, 77, 255, 0.08)) !important`；`box-shadow: inset 0 0 0 1px rgba(124, 77, 255, 0.28)`；`::before` 是 `backdrop-filter: blur(18px) saturate(160%)` + `linear-gradient(rgba(255,255,255,0.38), rgba(255,255,255,0.2))` 玻璃层 | `SongList.css:1225-1238`，`:1266-1272` |
| `.track-playing::after` | 渐变描边 | `inset: 0; padding: 1px` + `mask` / `mask-composite: exclude` 把填充盒转成 1px 描边；渐变 `90deg rgba(124,77,255,0.88) → rgba(34,211,238,0.72) → rgba(255,126,182,0.82) → rgba(124,77,255,0.88)` | `SongList.css:1160-1172`，`:1240-1249` |
| **分割线** | 行间 | **没有分隔线**：`.track-row td { border-bottom: 0 }`，:hover 再显式 `border-bottom-color: transparent`，`.track-playing td` 也置透明；行与行的区分靠 4px 间隙 + hover 描边 | `SongList.css:1103`，`:1200-1202`，`:1230-1232` |
| `.track-row.track-selected` | 底色 / 圆角 / 左标识 | `background: var(--te-library-selection-bg)` = `rgba(15,23,42,0.045)`；`border-radius: var(--te-library-selection-radius)` = `10px`；`::before { inset: 3px 0; border-left: 3px solid var(--te-library-selection-indicator) }` = `rgba(15,23,42,0.55)`；`--te-library-selection-inline-inset` 默认 `0px` | `SongList.css:1913-1941`；`base.css:123,125,128-129` |
| 切页时抑制动效 | `.song-list.is-switching` | 行、`::before`、`::after` 全部 `transition: none !important; animation: none !important` | `SongList.css:1203-1219` |
| 播放中动画暂停 | 非可见表面 | `.main-content.playing-open` / `[data-te-motion='reduced']` / `[data-te-motion='off']` 下 `animation-play-state: paused` | `SongList.css:1254-1258` |

### 3.6 卡片网格视图（同一列表的另一个视图）

| 元素 | 属性 | 值 | 来源 |
| --- | --- | --- | --- |
| `.card-grid` | **列 / 间距** | `repeat(auto-fill, minmax(192px, 1fr))`；`gap: 22px 20px`；`padding: 2px 2px 8px` | `SongList.css:711-717` |
| `.card-grid` ≤900px / ≤640px | 覆盖 | `minmax(160px, 1fr)` + `gap: 16px 14px` / `minmax(128px, 1fr)` + `gap: 12px 10px` | `SongList.css:726-738` |
| `.card-grid` 紧凑密度 | 覆盖 | `minmax(154px, 1fr)`；`gap: 10px` | `SongList.css:2289-2292` |
| `.artist-card` / `.album-card` / `.playlist-card` | 内边距 / 圆角 | `padding: 13px 13px 15px`；`border-radius: 18px` | `SongList.css:741-748` |
| 卡片 | 背景 / 边框 / 阴影 | `linear-gradient(145deg, rgba(255,255,255,0.56), rgba(248,245,255,0.34)), rgba(255,255,255,0.34)`；`1px solid rgba(255,255,255,0.62)`；`0 18px 50px rgba(86,70,160,0.1)` | `SongList.css:749-760` |
| **卡片 hover** | **变换** | `transform: translateY(-6px) scale(1.01)`；过渡 `transform 0.26s var(--te-ease-soft)`；阴影升到 `0 24px 70px rgba(86,70,160,0.18), 0 0 0 1px rgba(124,77,255,0.08)`；`backdrop-filter: blur(20px) saturate(150%)` | `SongList.css:753-757` / `:797-813` |
| 卡片 hover 光斑 `::before` | 尺寸 / 位移 | `120px × 120px` 圆；`inset: -40% -25% auto auto`；hover 时 `opacity: 1; transform: translate3d(-18px, 18px, 0)` | `SongList.css:780-795` / `:815-820` |
| 封面 | **宽高比 / 圆角** | `width: 100%; aspect-ratio: 1`；`border-radius: calc(var(--te-library-cover-radius) + 2px)` = `10px`；`margin-bottom: 13px` | `SongList.css:823-837` |
| `.playlist-name`（含 album / artist） | 字号 / 字重 | `14px` / `700`；`line-height: 1.35`；`padding: 0 3px`；单行省略 | `SongList.css:859-873` |
| `.playlist-count` | 字号 / 颜色 | `11.5px`（`0.82143 × 14`）；`var(--te-neutral-500)`；`letter-spacing: 0.04em` | `SongList.css:875-883` |

### 3.7 聚合歌单页卡片（`aggregate-playlist`，另一套卡片）

| 元素 | 属性 | 值 | 来源 |
| --- | --- | --- | --- |
| `.aggregate-grid` | **列 / 间距** | `repeat(auto-fill, minmax(168px, 1fr))`；`gap: 16px`；`padding: 2px 2px 12px`；≤920px 降为 `minmax(140px, 1fr)` + `gap: 12px` | `aggregate-playlist/AggregatePlaylistPage.css:134-142`，`:786-788` |
| `.aggregate-card` | 内边距 / 圆角 / 边框 | `padding: 13px 13px 15px`；`border-radius: var(--te-card-radius, 18px)`；`var(--te-card-border-width, 1px) solid var(--te-card-border)`；`background: var(--te-card-bg)`；`box-shadow: var(--te-card-shadow)`（未设时 `none`） | `AggregatePlaylistPage.css:144-153`；`base.css:3090` |
| `.aggregate-card` hover | 变换 | `transform: var(--te-card-hover-transform, translateY(-2px))`；`background: var(--te-hover-bg)`；过渡 `transform 0.26s var(--te-ease-soft, ease)` | `AggregatePlaylistPage.css:159-170` |
| `.aggregate-cover` | 宽高比 / 圆角 | `aspect-ratio: 1`；`border-radius: calc(var(--te-library-cover-radius, 12px) + 2px)`；`margin-bottom: 13px` | `AggregatePlaylistPage.css:176-183` |
| `.aggregate-card-name` / `-meta` | 字号 | 名称 `14px`/`700`/行高 `1.35`；副标 `11.5px` / `var(--te-neutral-500)` | `AggregatePlaylistPage.css:221-243` |
| `.aggregate-card-actions` | 悬停出现 | `top: 8px; right: 8px; gap: 6px`；默认 `opacity: 0`，hover / focus-within 时 `opacity: 1`；`transition: opacity 0.18s ease` | `AggregatePlaylistPage.css:256-269` |

---

## 4. 空 / 加载 / 错误态

### 4.1 首页

| 状态 | 元素 | 值 | 来源 |
| --- | --- | --- | --- |
| 未登录 | `.music-state` | `text-align: center; padding: 70px 20px; border-radius: 22px`；`background: color-mix(in srgb, var(--provider-accent) 8%, var(--music-surface))` | `ProviderMusicHome.css:613-618` |
| 未登录 | `.music-state > i` | 图标字号 `42px`；色 `var(--provider-accent)` | `ProviderMusicHome.css:619-622` |
| 未登录 | `.music-state h2` / `p` | h2 `margin-top: 24px`（字号继承 `18px`）；p `color: var(--music-muted); margin: 14px 0 28px` | `ProviderMusicHome.css:623-629` |
| **骨架屏** | `.music-skeleton` / `> div` / hero 块 / 卡片块 | `display: grid; gap: 24px; grid-template-columns: 1fr 1fr`；块 `background: var(--te-hover-bg)` + `border: 1px solid var(--music-line)` + `border-radius: 20px`；hero 块跨两列（`grid-column: 1 / -1`）`height: 330px`，卡片块 `height: 250px` | `ProviderMusicHome.css:630-646` |
| 骨架屏 | 无障碍文本 | `role="status"` + `aria-label="正在加载首页推荐"` + `.music-sr-only` 视觉隐藏（`clip-path: inset(50%)`） | `ProviderMusicHome.vue:88-97`；`ProviderMusicHome.css:647-653` |
| 错误条 / 区块空态 | `.music-notice` / `.music-feature-empty` | 错误条 `border: 1px solid var(--music-line); border-radius: 10px; padding: 14px; margin-bottom: 18px`、`gap: 10px`、`12px`、色 `--music-muted`，其按钮 `text-decoration: underline; text-underline-offset: 4px`；区块空态 `min-height: 90px; gap: 18px; justify-content: space-between`、`12px`、`line-height: 1.8` | `ProviderMusicHome.css:576-599`，`:600-612` |
| 入场 | `.te-content-arrival` | 只在 `html[data-te-motion='full'|'reduced']` 下生效：`opacity: 1`，`transition: opacity 160ms var(--te-ease-out-strong) !important`，`@starting-style { opacity: 0 }`；reduced 层时长 `120ms` | `components/contentArrival.css:2-11` |

### 4.2 流媒体详情/搜索态

| 状态 | 元素 | 值 | 来源 |
| --- | --- | --- | --- |
| 占位 | `.streaming-placeholder` | `min-height: 260px; gap: 12px; border-radius: 8px`；`background: var(--te-card-bg); border: 1px solid #eef1f6; box-shadow: 0 14px 32px rgba(34,42,68,0.07)` | `streaming-page/StreamingPlaceholder.css:1-15`，`:106-120` |
| 占位 | `.detail-placeholder` | `min-height: 420px` | `StreamingPlaceholder.css:17-19` |
| 占位 | `.placeholder-title` | `18px` / `700`；色 `var(--te-neutral-900)` | `StreamingPlaceholder.css:21-26` |
| 占位 | `.placeholder-hint` | `13px`；色 `#bbb` | `StreamingPlaceholder.css:28-32` |
| 加载舞台 | `.tls-stage` | `min-height: clamp(420px, 58vh, 560px); padding: 56px 24px 40px; border-radius: 22px`；`background: var(--te-card-bg)`；`border: 1px solid var(--te-card-border)`；`animation: tls-stage-in 0.62s var(--te-ease-out-quint) both` | `streaming-page/StreamingLoadingStage.vue:55-74` |
| 加载入场 / 文案 | `tls-stage-in`；`.tls-kicker` / `.tls-title` / `.tls-hint` | 关键帧 `translateY(26px) scale(0.985)` + `opacity: 0` → 常态；文案 `12px`/`700`/字距 `0.32em`、`clamp(24px,2.8vw,32px)`/`900`、`13px`/`500`，依次延迟 `0.12/0.18/0.24s`，`tls-rise 0.62s` | `StreamingLoadingStage.vue:76-85`，`:302-336` |
| 加载进度条 / 骨架卡 | `.tls-progress`；`.tls-ghost` / `.tls-ghost-card` | 进度条 `min(300px, 62%) × 3px`、圆角 `999px`、光束宽 `38%`、`tls-beam 1.6s var(--te-ease-out-quint) infinite`；骨架三列 `gap: 16px`、宽 `min(520px, 86%)`、`margin-top: 40px`，卡 `height: 64px; border-radius: 14px`，扫光 `tls-shimmer 1.5s`（延迟 `0/0.2s/0.4s`） | `StreamingLoadingStage.vue:385-418`，`:423-468` |
| 详情行骨架 `.sk` | 底色 / 圆角 / 动画 | `border-radius: 6px`；`linear-gradient(90deg, rgba(28,25,23,0.05), rgba(28,25,23,0.09) 50%, rgba(28,25,23,0.05))`；`background-size: 200% 100%`；`stage-shimmer 1.4s ease-in-out infinite`；尺寸 `.sk-num 18×12`、`.sk-cover 42×42`（圆角 `9px`）、`.sk-line` 高 `11px` 宽 `62%/34%/56%` 或 `36px` | `StreamingDetailStage.css:648-698`，`:700-707` |
| 详情空态 `.stage-empty` | 盒 / 文字 | `min-height: 180px; padding: 28px 16px; gap: 10px`；`border: 1px dashed var(--stage-line-strong)`；`border-radius: var(--stage-radius)` = `18px`；色 `var(--stage-ink-faint)` = `#a8a29e`；图标 `28px` + `opacity: 0.7`，文字 `13px` / `600` | `StreamingDetailStage.css:257-280` |

### 4.3 歌曲列表空态

| 状态 | 元素 | 值 | 来源 |
| --- | --- | --- | --- |
| **空容器** | `.empty-state` | `height: 60vh`；`border-radius: 18px`；`background: var(--te-glass-bg)` = `rgba(255,255,255,0.9)`；`border: 1px solid rgba(255,255,255,0.58)`；`box-shadow: var(--te-glass-shadow)` = `0 20px 70px rgba(86,70,160,0.16)`；`backdrop-filter: blur(18px) saturate(145%)` | `SongList.css:933-946`；`base.css:19,22` |
| 空态图标 | `.empty-icon` / `.empty-library-icon` | `margin-bottom: 16px`；字号 `calc(var(--te-library-icon-size) + 28px)` → 默认 `46px`，色 `var(--te-library-icon)` = `#64748b` | `SongList.css:947-959`；`base.css:126-127` |
| **主文案** | `.empty-text` | `font-size: 18px`（`1.28571 × 14`）；`font-weight: 700`；`font-family: var(--te-font-rounded)`；`letter-spacing: 0.01em`；`color: var(--te-neutral-700)` = `#374151`；`margin: 0 0 8px` | `SongList.css:960-967` |
| **提示文案** | `.empty-hint` | `font-size: 13px`（`0.92857 × 14`）；`letter-spacing: 0.01em`；`color: var(--te-neutral-500)` = `#6b7280`；`margin: 0` | `SongList.css:968-973` |
| 文案内容 | 曲目空 | `暂无内容` + `通过左侧菜单「歌单 → 添加文件夹」导入音乐` | `SongList.vue:2178-2184` |
| 搜索态状态条 | `.unified-search-status` | `min-height: 34px; margin: -4px 32px 14px; padding: 8px 12px; border-radius: 10px`；玻璃渐变底 + `0 12px 32px rgba(86,70,160,0.07)` | `SongList.css:975-992` |
| 错误态 | `.unified-search-status.error` | `border-color: rgba(225,29,72,0.2)`；`background: rgba(225,29,72,0.06)`；图标色 `#e11d48` | `SongList.css:994-997`，`:1014-1016` |

---

## 5. 动效汇总

| 场景 | 值 | 来源 |
| --- | --- | --- |
| 首页按钮 | `transition: background 160ms ease, transform 160ms var(--te-ease-out-quint)`；按下 `transform: scale(0.98)` | `ProviderMusicHome.css:44-47`，`:56-58` |
| 首页歌单 hover 播放钮 | `transition: opacity 160ms ease`，`0 → 1` | `ProviderMusicHome.css:554`，`:670-673` |
| 首页内容入场 | `opacity 160ms var(--te-ease-out-strong)`，从 `@starting-style { opacity: 0 }` 起步 | `contentArrival.css:2-11` |
| **页面切换（进入 / 离开）** | 进入 `opacity 0.34s ease, transform 0.48s cubic-bezier(0.16,1,0.3,1), filter 0.42s cubic-bezier(0.16,1,0.3,1)`；离开 `opacity 0.22s ease, transform 0.3s cubic-bezier(0.4,0,0.2,1), filter 0.28s cubic-bezier(0.4,0,0.2,1)` | `streaming-page/StreamingPage.css:328-349` |
| 下→上 / 上→下切换 | 进入 `translate3d(0, 40px, 0) scale(0.99)` / `translate3d(0, -40px, 0) scale(0.99)`；离开 `translate3d(0, -28px, 0) scale(0.992)` / `translate3d(0, 28px, 0) scale(0.992)`；全部带 `blur(8px)`、`opacity: 0` | `StreamingPage.css:352-375` |
| 详情下钻 / 返回 | 下钻 `translate3d(36px, 0, 0) scale(0.988)` + `blur(10px)` → 离开 `translate3d(-16px, 0, 0) scale(0.994)` + `blur(8px)`；返回为其反向（`-24px` / `32px`） | `StreamingPage.css:378-401` |
| 顶部栏 kicker 入场 | `animation: stream-chrome-in 0.42s cubic-bezier(0.16,1,0.3,1) both`（`translate3d(-10px,0,0)` → `0`） | `StreamingPage.css:1623`；关键帧 `StreamingPage.css:313-322` |
| 歌曲列表行 / 伪元素 | 行 `background 0.22s, transform 0.24s var(--te-ease-soft), box-shadow 0.24s, filter 0.24s`；伪元素 `opacity 0.24s ease` | `SongList.css:1110-1114`，`:1138` |
| 本地页上下切换（列表） | 进入/离开 `transform 0.24s var(--te-ease-soft), opacity 0.18s ease`；位移 ±26px / ∓18px | `SongList.css:1-29` |
| 卡片 hover | `transform 0.26s var(--te-ease-soft), background/box-shadow/border-color 0.26s`；`translateY(-6px) scale(1.01)`；聚合卡片为 `0.26s` + `translateY(-2px)`（可被 token 覆盖） | `SongList.css:753-757`，`:806`；`AggregatePlaylistPage.css:159-170` |
| 详情行 / 播放图标 / 均衡器 | 行 `background 0.16s var(--stage-ease), box-shadow 0.16s`（`--stage-ease: cubic-bezier(0.2,0.8,0.2,1)`）；图标 `opacity 0.14s, transform 0.14s`，`scale(0.85) → 1`；均衡器 `stage-eq 0.9s ease-in-out infinite`，`scaleY(0.45) → 1`，三柱延迟 `0 / 0.15s / 0.28s`、高 `5/11/7px`、柱宽 `2px`、`gap: 2px` | `StreamingDetailStage.css:29`，`:411-414`，`:456-474`，`:481-517` |
| 降低 / 关闭动效 | 页面切换压到 `opacity 0.12s ease, transform 0.12s ease` 且 `transform: none; filter: none`；全局 `--te-motion-hover: 100ms`、`panel/page: 120ms`、`animation: none !important`、`transition-duration: 0.01ms !important`，交互元素只留 `opacity, color, background-color, border-color`；off 档全部 `0ms`、按下缩放 `1`、hover 位移 `0px` | `StreamingPage.css:414-443`；`base.css:392-435` |

---

## 6. 滚动与滚动条

### 6.1 滚动容器

| 元素 | 属性 | 值 | 来源 |
| --- | --- | --- | --- |
| `.streaming-content` | 滚动 / 侧栏 | `overflow-y: auto; overflow-x: hidden; scrollbar-width: thin; scrollbar-color: var(--te-scrollbar-thumb) transparent`；`transition: padding-left var(--te-motion-panel) var(--te-ease-soft)`（**用 padding 而非 transform 推开侧栏**，避免滚动条被拖进来） | `StreamingPage.css:31-38` |
| `.streaming-content-body` | 底部内边距 | `padding: 14px clamp(36px, 6vw, 84px) 34px`；有播放条时 `padding-bottom: 126px` | `StreamingPage.css:82`，`:85-87` |
| `.song-list` | 滚动 | `overflow-y: auto; overflow-x: hidden; scrollbar-width: thin; scrollbar-color: var(--te-scrollbar-thumb) transparent` | `SongList.css:35-36`，`:47-48` |
| 列表吸顶表头 | `thead` | `position: sticky; top: 0; z-index: 2`（滚动时表头固定，无淡出遮罩） | `SongList.css:1072-1075` |

### 6.2 滚动条样式

| 作用域 | 属性 | 值 | 来源 |
| --- | --- | --- | --- |
| 全局默认（所有元素） | 颜色 / 宽度 / 轨道 | `scrollbar-color: transparent transparent !important; scrollbar-width: thin`——**指针未靠近时滚动条完全不可见**；`::-webkit-scrollbar { width: 10px; height: 10px }`，`track, corner` 背景透明 | `base.css:226-239` |
| 全局默认 | 滑块 | `border: 3px solid transparent !important; border-radius: 999px; background: transparent !important; background-clip: content-box !important`；过渡 `background-color / border-width var(--te-motion-hover) ease` | `base.css:241-249` |
| 靠近/滚动中 `.te-auto-scrollbar.is-scrollbar-near` / `.is-scrollbar-active` | 滑块 | `border-width: 2px !important`；`background: var(--te-scrollbar-thumb) !important` | `base.css:251-262` |
| 同上 hover | 滑块 | `border-width: 1px !important`；`background: var(--te-scrollbar-thumb-hover) !important` | `base.css:264-271` |
| `.song-list` 覆盖 | 滑块 | `border: 3px solid transparent; border-radius: 999px; background: var(--te-scrollbar-thumb); background-clip: content-box`，轨道透明，宽度 `10px` | `SongList.css:51-64` |
| 滑块颜色 | 浅 / 暗 | `rgba(71,85,105,0.34)` / `rgba(212,212,216,0.34)`；hover `rgba(51,65,85,0.56)` / `rgba(244,244,245,0.58)` | `base.css:211-212`，`:537-538` |
| 顶部回顶按钮 | 相关 token | `--te-scroll-top-bg: rgba(255,255,255,0.92)`；`--te-scroll-top-color: #2563eb`；`--te-scroll-top-shadow: 0 8px 24px rgba(15,23,42,0.14)` | `base.css:218-222` |

### 6.3 滚动相关视觉（**没有** mask 淡出）

- **无 `mask-image` 淡出**：在 `src/renderer/src/components/` 全目录内，`mask-image`
  只出现在 `PlayingMusic.vue`（歌词）与 `PlayingLyricWords.vue`，首页与歌曲列表
  **都没有**用遮罩做上下淡出。
- 列表顶部与底部的"氛围"来自静态渐变伪元素：
  `.grid-view::before` / `.table-view::before`（`SongList.css:77-89`）与
  `.song-list` 自身的三层 `radial-gradient`（`SongList.css:40-44`），都是
  `position: absolute` / 背景层，**不随滚动位移**。
- 首页**没有**视差：`.music-artwork-cover` 的旋转是静态 `transform`
  （`ProviderMusicHome.css:200-208`），未绑定滚动。
- `.track-row::after` 的 `mask` / `mask-composite: exclude`
  （`SongList.css:1160-1172`）是把填充盒转成 1px 描边的几何技巧，不是滚动淡出。
- 虚拟滚动只做窗口化：`getSongListVirtualRange`（`songListVirtualWindow.ts:46-51`）
  与上下两个 `.virtual-spacer` 占位行（`SongList.vue:2296-2302`，`:2413-2421`），
  滚动时行数是常量级。

---

## 7. 给移植实现的对照（现状 → 参考）

`src/main/java/net/wurstclient/twilight/` 的常量是在**缺少 `SongList.vue` 的旧快照**
下估出来的（见 `TwilightListLayout.java:18-22` 的注释），下面几项需要按本文修正：

| 常量 | 现值 | 参考值 | 来源 |
| --- | --- | --- | --- |
| `TwilightListLayout.ROW_HEIGHT` | `66` | **`68`** | `useSongListVirtualScroll.ts:23` |
| `TwilightListLayout.INDEX_WIDTH` | `34` | **`46`** | `SongList.css:1367` |
| `TwilightListLayout.COVER_RADIUS` | `10` | **`12`**（`--te-artwork-list-radius`，兜底 `8`） | `SongList.css:1341`；`base.css:130,135` |
| `TwilightListLayout.ROW_RADIUS` | `14` | **`10`**（`--te-radius-global`） | `SongList.css:1116`；`base.css:50` |
| `TwilightListLayout.ROW_PADDING_X` | `12` | **`14`**（`.track-row td`） | `SongList.css:1100` |
| `TwilightListLayout.ROW_PADDING_Y` | `9` | **`0`**（垂直内边距为 0，由 64px 行高 + 4px 外边距构成） | `SongList.css:1100`；`SongList.vue:2313` |
| `TwilightListLayout.CHART_COVER` | `48` | **`45`**（首页曲目缩略图）；列表行是 `40` | `ProviderMusicHome.css:319-320`；`SongList.css:1339-1340` |
| `TwilightListLayout.LIST_COVER` | `40` | `40` ✅ | `SongList.css:1339-1340` |
| `TwilightHomeLayout.HERO_MIN_HEIGHT` | `280` | **`328`**（`min-height` 无上限，只在 560px 容器下降为 `310`） | `ProviderMusicHome.css:77`，`:699` |
| `TwilightHomeLayout.COPY_PADDING_*` | `28–48` | **左 38 / 顶 38 / 底 26 / 右 0** | `ProviderMusicHome.css:92` |
| `TwilightHomeLayout.TITLE_*_SIZE` | `40–62` | **`clamp(34px, 5.7cqw, 61px)`**，`line-height: 1.12`，`letter-spacing: -2px` | `ProviderMusicHome.css:109-112` |
| `TwilightHomeLayout.SECTION_GAP` | `44` | **`34`**（区块 `margin-top`） | `ProviderMusicHome.css:267-271` |
| `TwilightHomeLayout.CTA_HEIGHT` | `46` | **`44`**（`min-height`） | `ProviderMusicHome.css:134` |
| `TwilightHomeLayout.CTA_PRIMARY_PADDING` | `24` | `24` ✅（水平内边距，圆角 `24px`） | `ProviderMusicHome.css:135,139` |
| 首页曲目网格列间距 | `CHART_COLUMN_GAP = 28` | **`25`**（`column-gap`） | `ProviderMusicHome.css:298` |
| 首页曲目行内边距 | `ROW_PADDING_X/Y = 12/9` | **`8 / 10`**（`padding: 10px 8px`） | `ProviderMusicHome.css:306` |
| 首页曲目缩略图圆角 | `COVER_RADIUS = 10` | **`6`** | `ProviderMusicHome.css:324` |
| 歌单卡片网格 | `TwilightShellLayout.CARD_GAP = 16` | **行 24 / 列 18，固定 6 列** | `ProviderMusicHome.css:501-502` |
| 歌单封面圆角 | `RADIUS_MEDIUM = 14` | **`11`** | `ProviderMusicHome.css:519` |
| Section 标题字号 | — | **`18px` / `650` / `-0.4px`**；英文副标 `8px` | `ProviderMusicHome.css:280-288` |

**移植时最不能走形的三件事**（按重要性）：

1. **Hero 卡片 = `min-height: 328px` + `border-radius: 22px` + `1.1fr / 1fr` 两栏**，
   左栏内边距 `38px 0 26px 38px`，标题 `clamp(34px,5.7cqw,61px)/700/1.12/-2px`；
   背景是"深灰底 + 右上 84%/30% 处 42% 强调色椭圆光"（`ProviderMusicHome.css:74-114`）。
2. **歌曲列表的行距是 `68px`（行高 64 + 上下各 2px 外边距），序号列 `46px`，
   封面 `40px` 圆角 `12px`，垂直内边距 `0`**——参考没有行分隔线，
   hover 只是 `inset 0 0 0 1px rgba(124,77,255,0.22)` 的一圈静态描边
   （`useSongListVirtualScroll.ts:23`；`SongList.css:1097-1116,1186-1192,1366-1371`）。
3. **首页歌单网格固定 6 列、`gap: 24px 18px`、封面 `aspect-ratio: 1` 圆角 `11px`**，
   卡片 hover **不位移不缩放**，只让右下角 29px 的播放圆钮从 `opacity: 0 → 1`
   （`ProviderMusicHome.css:499-556,670-673`）。
