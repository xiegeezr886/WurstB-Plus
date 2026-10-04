# Twilight Echo 移植规范 · 应用外壳与主题令牌

参考项目：`source/Twilight_Echo`（Vue 3 + Electron，Apache-2.0，只读引用）。
本文只提取**外壳几何 + 主题令牌**的精确值，用于在 Minecraft 客户端内用 Java/Skia 复刻；不覆盖播放器栏、歌词、设置页内部结构。

## 0. 引用路径对照

下文所有 `文件:行` 均相对仓库根目录。为压缩表格宽度，引用统一写成「文件名:行」，其完整路径 = `source/Twilight_Echo/src/` + 下表的相对路径：

| 别名 | 相对路径 | 别名 | 相对路径 |
| --- | --- | --- | --- |
| `App.vue` | `renderer/src/App.vue` | `base.css` | `renderer/src/assets/base.css` |
| `fonts.css` | `renderer/src/assets/fonts.css` | `paper-light.css` | `renderer/src/assets/theme-layouts/paper-light.css` |
| `obsidian-glass.css` | `renderer/src/assets/theme-layouts/obsidian-glass.css` | `SideMenu.vue` | `renderer/src/components/SideMenu.vue` |
| `TitleBar.vue` | `renderer/src/components/TitleBar.vue` | `AppNoticeHost.vue` | `renderer/src/components/AppNoticeHost.vue` |
| `SongList.css` | `renderer/src/components/song-list/SongList.css` | `LocalDashboard.css` | `renderer/src/components/LocalDashboard.css` |
| `StreamingPage.css` | `renderer/src/components/streaming-page/StreamingPage.css` | `StreamingContentHeader.css` | `renderer/src/components/streaming-page/StreamingContentHeader.css` |
| `ThemeStudioPage.css` | `renderer/src/components/theme-studio/ThemeStudioPage.css` | `themeTokens.ts` | `shared/themeTokens.ts` |
| `theme.ts` | `shared/theme.ts` | `themeData.ts` | `shared/themeData.ts` |
| `themePresets.ts` | `shared/themePresets.ts` | `themeLayout.ts` | `shared/themeLayout.ts` |
| `useThemeStore.ts` | `renderer/src/stores/useThemeStore.ts` | `usePlayerStore.ts` | `renderer/src/stores/usePlayerStore.ts` |
| `colorExtractor.ts` | `renderer/src/utils/colorExtractor.ts` | `useSideMenuClearance.ts` | `renderer/src/app/useSideMenuClearance.ts` |

## 1. 窗口外壳（Window frame）

### 1.1 DOM 结构

`<div class="app-shell">`（`App.vue:945`，内联写入 `--te-side-menu-bottom`）下只有 4 个子块，与 `themeLayout.ts:1` 的 4 个槽位一一对应：

- `.app-shell-title`（`App.vue:952`）→ `TitleBar`
- `.app-shell-navigation`（`App.vue:974`，`v-if="showLocalSidebar"`）→ `SideMenu`
- `.app-shell-content > .main-content`（`App.vue:986`、`App.vue:988`）→ 页面栈
- `.app-shell-player`（`App.vue:1119`，内联写入 `--te-side-menu-inline-end`）→ `PlayerBar`

### 1.2 默认外壳：没有外框

默认（`<html>` 不带 `data-te-shell-layout='custom'`）时**没有任何 `.app-shell` 规则**：无外边距、无内边距、无边框、无圆角、无投影。窗口是无边框 Electron 窗口，由 `body` 直接铺满：

| 属性 | 值 | 来源 |
| --- | --- | --- |
| `html` 底色 | `#fbfbff`，`font-size: 14px` | `base.css:613-619` |
| `body` 页面背景 | `background-color: var(--te-app-bg)` + `background-image: var(--te-app-bg-image)`，`center / cover / no-repeat / fixed` | `base.css:694-703` |
| `body` 排版 | `line-height: 1.65`，`letter-spacing: 0`，`overflow: hidden` | `base.css:694-718` |
| `#app` | `min-height: 100vh`、`position: relative`、`isolation: isolate` | `base.css:872-876` |
| 标题栏层级 | `.app-shell-title { position: relative; z-index: 2100 }` | `App.vue:1208-1211` |

`*` 全局重置为 `box-sizing: border-box; margin: 0; font-weight: inherit; user-select: none`（`base.css:591-599`）——移植时**边距必须显式给**，不要依赖默认值。

### 1.3 外壳网格（`data-te-shell-layout='custom'`）

主题预设可把外壳改成 CSS Grid；CSS 变量由 `themeLayout.ts:92-110` 生成，`<html>` 上写 `data-te-shell-layout='custom'` 与 `data-te-shell-navigation`（`toggle|persistent|hidden`，`themeLayout.ts:113-121`）。

| 规则 | 值 | 来源 |
| --- | --- | --- |
| `.app-shell` | `display: grid`；`grid-template-columns/rows/areas: var(--te-shell-template-*)`；`height/min-height: 100vh`；`overflow: hidden` | `App.vue:1256-1264` |
| 槽位映射 | `grid-area: titleBar / navigation / content / playerBar` | `App.vue:1266-1293` |
| 轨宽词表 | `auto`→`auto`、`content`→`max-content`、`narrow`→`minmax(56px, 0.45fr)`、`standard`→`minmax(164px, 0.9fr)`、`wide`→`minmax(240px, 1.5fr)`、`fill`→`minmax(0, 1fr)`、`double`→`minmax(0, 2fr)` | `themeLayout.ts:38-46` |
| 约束 | 最多 4 轨；`titleBar` 与 `content` 必填 | `themeLayout.ts:34-35` |
| 格内标题栏 | `.title-bar` 改为 `position: relative`、`width: 100%`、`min-height: 32px` | `App.vue:1295-1301` |
| 格内菜单 | `.side-menu` 改为 `position: relative`、`width: 100%`、`border-radius: 0` | `App.vue:1303-1310` |
| 格内内容 | `.main-content` 强制 `padding-left: 0`、`height: 100%` | `App.vue:1322-1327` |
| 紧凑断点 | `@media (max-width: 760px)` 换用 `--te-shell-compact-*`；临时菜单 `position: fixed; top: var(--te-titlebar-inset, 32px); width: var(--te-menu-width); bottom: var(--te-side-menu-bottom, 0px)` | `App.vue:1346-1374` |

### 1.4 内容容器与侧栏让位

| 规则 | 值 | 来源 |
| --- | --- | --- |
| `.main-content` | `display: grid`；`width: 100%`；`min-height: 100vh`；`padding-left: 0`；`transform: translateZ(0)`；`overflow: hidden`；`position: relative`；`z-index: 1` | `App.vue:1391-1403` |
| 子元素叠放 | `.main-content > * { grid-area: 1 / 1 }`（页面交叉淡入不做布局抖动） | `App.vue:1405-1407` |
| 菜单展开 | `.main-content.menu-open { padding-left: var(--te-menu-width) }`，过渡 `padding-left var(--te-motion-panel) var(--te-ease-soft)` | `App.vue:1424-1426`、`App.vue:1399` |
| ≤900px | 让位取消：`padding-left: 0 !important` | `App.vue:1711-1715` |
| 测量值 | `--te-side-menu-bottom` = 播放栏遮挡高度；`--te-side-menu-inline-end` = 菜单右缘；重叠间隙常量 `10` | `App.vue:945`、`App.vue:1120`、`useSideMenuClearance.ts:9` |
| 标题栏高度回写 | `--te-titlebar-inset` = `.title-bar` 的 `getBoundingClientRect().bottom` | `TitleBar.vue:49-58`；兜底 32px `App.vue:1364`、35px `AppNoticeHost.vue:226` |

### 1.5 背景分层（页面 vs 卡片）

| 层 | 值 | 来源 |
| --- | --- | --- |
| 页面底色 | `--te-app-bg`：`#f4f4f7`（light）/ `#17181a`（dark） | `themeTokens.ts:202` |
| 页面图片 | `--te-app-bg-image`，`cover` 固定 | `base.css:699-703` |
| 环境光层 | `body::before`：`position: fixed; inset: 0; z-index: -1`，conic-gradient(`from 18deg at 72% 18%`, `rgba(124,77,255,0.2) 52deg`, `rgba(255,126,182,0.12) 96deg`, transparent 148deg) + `linear-gradient(135deg, rgba(255,255,255,0.92), rgba(247,244,255,0.78))`，`opacity: 0.9`，`animation: ambient-light-shift 14s var(--te-ease-soft) infinite alternate` | `base.css:808-826`、`base.css:916-923` |
| 环境光层禁用 | dark → `display: none`；pureWhite → `display: none` | `base.css:1304-1306`、`base.css:866-870` |
| 卡片底 | `--te-card-bg`：`#ffffff` / `#181818` | `themeTokens.ts:694` |
| 卡片描边 | `--te-card-border`：`rgba(15,23,42,0.08)` / `rgba(255,255,255,0.1)`，宽 `--te-card-border-width: 1px` | `themeTokens.ts:704`、`themeTokens.ts:1016` |
| 卡片圆角 | `--te-card-radius: 16px`（两色调同值；paper-light 覆盖为 `6px`） | `themeTokens.ts:929`、`themePresets.ts:892` |
| 卡片阴影 | `--te-card-shadow` 默认 `none` | `base.css:3090` |
| 卡片模糊 | `backdrop-filter: blur(var(--te-card-blur, 20px)) saturate(var(--te-card-saturate, 150%))` | `base.css:2655-2656` |
| 设置覆盖层 | `.settings-overlay-root--active`：`position: fixed; inset: 0; z-index: 2000; isolation: isolate; background: #17181a`；`::before` 三层背景（图片 + 设置色 + `#17181a` 底板） | `App.vue:1221-1249` |
| 透明窗口 | `html[data-window-transparent='on']` → `background-color: rgba(var(--te-tp-surface-rgb), var(--te-tp-base-alpha, 0.78))` | `base.css:2751-2762` |

## 2. 侧边栏（Sidebar）

### 2.1 容器

| 属性 | 值 | 来源 |
| --- | --- | --- |
| 定位 | `position: fixed; top: 32px; left: 0; bottom: var(--te-side-menu-bottom, 0px)` | `SideMenu.vue:288-298` |
| 宽度 | `--te-menu-width: clamp(132px, 18vw, 216px)` | `base.css:49`、`themeTokens.ts:1079` |
| 宽度变体 | `expanded`：`clamp(180px, 18vw, 216px)`；`compact`：`164px`；`rail`：`72px`（均 `!important`，挂在 `html[data-te-navigation-style=…]`） | `SideMenu.vue:331-345` |
| 预设变体 | obsidian-glass：`--te-menu-width: 72px` | `obsidian-glass.css:680` |
| 圆角 | `border-radius: 0 var(--te-navigation-radius) var(--te-navigation-radius) 0`（`--te-navigation-radius: 0px`） | `SideMenu.vue:307`、`themeTokens.ts:507` |
| 描边 | `border-right: 1px solid var(--te-navigation-border)` | `SideMenu.vue:306` |
| 底色 | `background: transparent`（透出 body 的全局背景） | `SideMenu.vue:305` |
| 模糊 | `backdrop-filter: blur(24px) saturate(180%)` | `SideMenu.vue:311-312` |
| 投影 | `box-shadow: var(--te-navigation-shadow)`：`4px 0 24px rgba(15,23,42,0.03)` / `none` | `SideMenu.vue:310`、`themeTokens.ts:416` |
| 开合动画 | `transform: translate3d(-100%,0,0)` → `.open` 为 0；`transition: transform var(--te-motion-panel) var(--te-ease-soft)` | `SideMenu.vue:313-324` |
| 列表内边距 | `.menu-items { padding: 16px 12px 16px 4px }` | `SideMenu.vue:385` |
| 条目间距 | `.menu-nav { gap: 6px }` | `SideMenu.vue:396` |
| 分隔线 | `.menu-separator { height: 1px; margin: 12px 10px 12px 16px; background: linear-gradient(to right, var(--te-navigation-border), transparent) }` | `SideMenu.vue:705-709` |

light（`data-theme='pureWhite'`）另有覆盖：右侧描边 `rgba(17,24,39,0.06)`、底色 `transparent`、投影 `8px 0 24px rgba(15,23,42,0.04)`（`base.css:989-996`）；dark 覆盖为透明、无投影（`base.css:1348-1352`）。

### 2.2 菜单项

| 属性 | 值 | 来源 |
| --- | --- | --- |
| 高度 | `40px` | `SideMenu.vue:508` |
| 宽度 / 外边距 | `width: calc(100% - 8px)`、`margin-left: 8px` | `SideMenu.vue:509-511` |
| 内边距 | `padding: 0 12px 0 16px` | `SideMenu.vue:510` |
| 圆角 | `border-radius: var(--te-radius-global)` = `10px` | `SideMenu.vue:514`、`themeTokens.ts:945` |
| 图标–文字间距 | `gap: 14px` | `SideMenu.vue:515` |
| 常态 | 背景 `transparent`，文字 `var(--te-chrome-text, var(--te-navigation-text))` | `SideMenu.vue:517-518` |
| hover | `background: var(--te-navigation-hover)`、`color: var(--te-navigation-hover-text)`；light 硬覆盖 `#f3f4f6`（`base.css:1001`）、dark 用 `var(--te-hover-bg)`（`base.css:1368`） | `SideMenu.vue:532-536` |
| active | `background: var(--te-navigation-active)`、`color: var(--te-navigation-active-text)`、`font-weight: 600`；light 硬覆盖 `#e8e8e8`（`base.css:1005`）、dark 用 `var(--te-active-bg)`（`base.css:1372`） | `SideMenu.vue:538-542` |
| active 指示条 | `::before`：`left: -8px; top/bottom: 10px; width: 2px; border-radius: 2px; background: var(--te-navigation-indicator); opacity: 0.8` | `SideMenu.vue:544-554` |
| 指示条入场 | `html[data-te-motion='full']` 下 `animation: side-menu-indicator-in var(--te-motion-press) var(--te-ease-spring) both` | `SideMenu.vue:556-565` |
| 图标盒 | `.item-icon { width: 22px; height: 22px; font-size: calc(var(--te-font-size-body,14px) * 1.21429) }`（≈17px），色 `var(--te-navigation-icon)` | `SideMenu.vue:567-577` |
| 图标缩放 | `sm` → `×1`（14px）；`lg` → `×1.42857`（20px） | `SideMenu.vue:579-585` |
| 文字 | `.item-label { font-size: calc(var(--te-font-size-body,14px) * 1) }`（14px）、`font-weight: 500`、`letter-spacing: 0.3px`、收起时 `opacity: 0` | `SideMenu.vue:595-609` |
| 二级项 | `height: 36px`、`width: calc(100% - 44px)`、`margin-left: 44px`、`padding-inline: 10px`、`gap: 10px`；图标 `18px`；文字 `×0.92857`（13px） | `SideMenu.vue:445-462` |
| 品牌区 | `.navigation-brand { height: 56px; gap: 10px; padding: 12px 16px }`，字号 `×0.85714`（12px）、字重 `700`，`img 28×28 / radius 6px`；由 `html[data-te-navigation-logo='show']` 显示 | `SideMenu.vue:347-368` |
| 分组标题 | `.menu-caption { padding: 6px 12px 10px 24px; font-size: ×0.78571 }`（11px）、`letter-spacing: 1.5px` | `SideMenu.vue:399-405` |

### 2.3 paper-light / obsidian-glass 侧栏变体

| 变体值 | paper-light | obsidian-glass |
| --- | --- | --- |
| 容器 | `top/bottom: 22px`；`border: 1px solid var(--sf-shell-line)`、`border-left: 0`；`border-radius: 0 26px 26px 0`；底色 `color-mix(in srgb, var(--te-navigation-bg) 82%, var(--te-primary-500))`；投影 `12px 22px 58px color-mix(in srgb, var(--te-neutral-50) 32%, transparent)`；`backdrop-filter: blur(26px) saturate(140%)` — `paper-light.css:115-125` | `border-radius: 0`、`border-right: 1px solid var(--og-hairline)`、无投影、无模糊 — `obsidian-glass.css:42-48` |
| 列表 | `gap: 12px; padding: 22px 13px`；`.menu-nav { gap: 5px }` — `paper-light.css:127-134` | `padding: 8px 0`；`.menu-nav { gap: 0 }` — `obsidian-glass.css:50-58` |
| 条目 | `height: 45px; padding: 0 14px; border-radius: 13px`；hover `translateX(3px)`；active 加 `inset 0 0 0 1px var(--sf-shell-line)` — `paper-light.css:136-154` | `height: 38px; padding: 0 10px; justify-content: center; border-radius: 0; gap: 10px` — `obsidian-glass.css:60-68` |
| 指示条 | `left: 5px; top/bottom: 9px; width: 3px; border-radius: 999px; box-shadow: 0 0 18px var(--te-primary-500)` — `paper-light.css:156-164` | 满高 `2px`、无圆角、无发光 — `obsidian-glass.css:83-93` |
| 局部常量 | `--sf-shell-line: color-mix(in srgb, var(--te-neutral-900) 13%, transparent)` — `paper-light.css:2` | `--og-hairline` 同级 `13%`、`--og-radius: 2px`、`--og-bar-h: 64px` — `obsidian-glass.css:11-20` |

## 3. 顶栏与内容页头

### 3.1 标题栏（窗口 chrome）

| 属性 | 值 | 来源 |
| --- | --- | --- |
| 高度 | `height: var(--te-titlebar-height, 35px)` | `TitleBar.vue:297` |
| 高度变体 | obsidian-glass `44px`；主题工作室用 `var(--te-titlebar-height, 32px)` 兜底 | `obsidian-glass.css:10`、`ThemeStudioPage.css:12` |
| 拖拽区 | `.drag-region { height: 32px }` | `base.css:621-624` |
| 定位 | `position: fixed; top: 0; left/right: 0; z-index: 9999; overflow: hidden` | `TitleBar.vue:301-306` |
| 背景/描边/投影 | 全部 `transparent / 0 / none`，**默认无模糊** | `TitleBar.vue:299-310` |
| 按钮宽度 | back 36、menu 36、settings 36、plugins 36、login 36（均 `height: 100%`，无边框，色 `var(--te-shell-control-text)`） | `TitleBar.vue:394-505` |
| 窗口控制 | `.control-btn { width: 46px; min-width: 46px }`，图标 `12×12`、`stroke-width: 1` | `TitleBar.vue:543-583` |
| hover | `background: var(--te-shell-control-hover)`；关闭键 `#e81123 / #fff` | `TitleBar.vue:447-449`、`TitleBar.vue:597-602` |
| 账户入口 | `.user-avatar { width: 16px; height: 16px; border-radius: 50% }`（无头像时退回 person 图标） | `TitleBar.vue:511-516` |
| 液态玻璃变体 | `.title-bar-liquid .title-bar-background { backdrop-filter: blur(16px) saturate(124%); box-shadow: inset 0 -1px 0 …8% }` | `TitleBar.vue:609-621` |

### 3.2 内容页头（流媒体页，含搜索框与账户胶囊）

| 属性 | 值 | 来源 |
| --- | --- | --- |
| 页头盒 | `display: flex; align-items: center; gap: 18px; min-height: 72px; margin: calc(32px + 20px) clamp(36px, 6vw, 84px) 0; padding: 10px 0 14px; border-bottom: 1px solid var(--bar-line); border-radius: 0`，无背景/投影/模糊 | `StreamingContentHeader.css:351-366` |
| 主标题 | `font-size: clamp(20px, 2.2vw, 26px)`、`font-weight: 700`、`letter-spacing: -0.03em`、`line-height: 1.15`、色 `var(--bar-ink)`、字体 `var(--te-font-display)` + Georgia 兜底 | `StreamingContentHeader.css:407-418` |
| 副标题 | `font-size: calc(var(--te-font-size-body,14px) * 0.85714)`（12px）、`font-weight: 500`、`line-height: 1.4`、色 `var(--bar-ink-soft)` | `StreamingContentHeader.css:420-426` |
| 眉标题 | 11px、`700`、`letter-spacing: 0.1em`、全大写、色 `var(--bar-accent-ink)`；标记块 `14×2px` 圆角 `999px` | `StreamingContentHeader.css:383-401` |
| 详细/搜索态 | 标题降为 `clamp(18px, 2vw, 22px)` | `StreamingContentHeader.css:428-431` |
| 搜索框 | `height: 38px; width: clamp(220px, 28vw, 360px); min-width: 160px; padding: 0 14px; border: 1px solid var(--bar-line); border-radius: 999px; background: var(--bar-paper-raised)`，无模糊 | `StreamingContentHeader.css:442-460` |
| 搜索框聚焦 | 描边 `rgba(194,65,12,0.42)`、`box-shadow: 0 0 0 3px var(--bar-accent-soft)` | `StreamingContentHeader.css:462-466` |
| 搜索图标/输入 | 图标 13px `var(--bar-ink-faint)`；输入 13px / `500` / `var(--bar-ink)`；占位符 `var(--bar-ink-faint)`，光标 `var(--bar-accent)`；清除键 `20×20` 圆、底 `rgba(28,25,23,0.06)` | `StreamingContentHeader.css:468-495`、`StreamingContentHeader.css:504-531` |
| 账户胶囊 | `.streaming-avatar-btn`：`38×38`、`border-radius: 999px`、`border: 1px solid var(--bar-line)`、底 `var(--bar-paper-raised)`、`display: grid; place-items: center`，头像 `img` 铺满 `object-fit: cover` | `StreamingContentHeader.css:533-573` |
| 页头局部色 | light：`--bar-ink #1c1917`、`--bar-ink-soft #57534e`、`--bar-ink-faint #a8a29e`、`--bar-paper #faf8f5`、`--bar-paper-raised #ffffff`、`--bar-line rgba(28,25,23,0.1)`、`--bar-accent #c2410c`、`--bar-accent-soft rgba(194,65,12,0.1)`、`--bar-ease cubic-bezier(0.2,0.8,0.2,1)` | `StreamingPage.css:1567-1578` |
| 页头局部色 | dark：`--bar-ink #f5f5f4`、`--bar-ink-soft #a8a29e`、`--bar-ink-faint #78716c`、`--bar-paper #171513`、`--bar-paper-raised #1c1917`、`--bar-line rgba(245,245,244,0.1)`、`--bar-accent #fb923c`、`--bar-accent-soft rgba(251,146,60,0.14)` | `StreamingPage.css:1799-1810` |
| ≤900px | `padding-top: 58px`、改为纵向、`margin-inline: 22px`、搜索框 `width: min(100%, 440px)` | `StreamingContentHeader.css:626-644` |

### 3.3 本地曲库页头与搜索框

| 属性 | 值 | 来源 |
| --- | --- | --- |
| `.song-list-header` | `display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; min-height: 64px; margin-bottom: 26px; padding: 6px 32px 0`，透明无模糊 | `SongList.css:91-107` |
| ≤640px | `gap: 16px; padding: 52px 10px 0` | `SongList.css:1596-1599` |
| 页标题 | `.song-list-title { font-size: clamp(28px, 2.8vw, 36px); font-weight: 800; color: var(--te-neutral-900); letter-spacing: -0.015em; line-height: 1.1 }`，字体 `var(--te-font-display, var(--te-font-rounded))` | `SongList.css:286-299` |
| 统计文字 | `font-size: calc(var(--te-font-size-body,14px) * 0.89286)`（12.5px）、`500`、`letter-spacing: 0.03em`、色 `var(--te-neutral-500)` | `SongList.css:147-154` |
| 搜索框 | `height: 38px; width: clamp(260px, 30vw, 390px); padding: 0 17px; border-radius: 999px; background: var(--te-glass-bg); border: 1px solid rgba(255,255,255,0.72); box-shadow: 0 14px 36px rgba(86,70,160,0.08); backdrop-filter: blur(16px) saturate(145%)` | `SongList.css:1572-1589` |
| 聚焦 | 描边 `rgba(124,77,255,0.34)`、`box-shadow: 0 0 0 4px rgba(124,77,255,0.08), 0 16px 42px rgba(86,70,160,0.1)` | `SongList.css:1659-1665` |
| 图标/输入 | 图标 14px `#999` → 聚焦 `var(--te-primary-500)`；输入 13px `#333`；占位符 `#bbb` | `SongList.css:1667-1692` |
| dark 覆盖 | 描边 `rgba(148,163,184,0.16)`、底 `rgba(15,23,42,0.74)`；聚焦底 `rgba(17,24,39,0.92)` + 环 `rgba(var(--te-primary-rgb),0.16)`；输入 `rgba(248,250,252,0.92)`；占位符 `rgba(148,163,184,0.58)` | `base.css:1480-1505` |

### 3.4 内容区内边距与首页 masthead

| 属性 | 值 | 来源 |
| --- | --- | --- |
| 流媒体滚动区 | `.streaming-content { flex: 0 0 100%; padding-left: 0; transition: padding-left var(--te-motion-panel) var(--te-ease-soft) }`；`.menu-open` 时 `padding-left: var(--te-menu-width)` | `StreamingPage.css:27-39`、`App.vue:1687-1689` |
| 正文内边距 | `.streaming-content-body { padding: 14px clamp(36px, 6vw, 84px) 34px }`；有播放栏时底部 `126px` | `StreamingPage.css:79-87` |
| 首页容器 | `.home-inner { gap: 44px; width: min(100%, 1280px); margin: 0 auto; padding: clamp(26px,3.4vw,46px) clamp(22px,3.4vw,48px) 120px }` | `LocalDashboard.css:134-143` |
| 首页大标题 | `.masthead-title { font-size: clamp(34px, 4vw, 48px); font-weight: 900; line-height: 1.04; color: var(--home-ink) }` | `LocalDashboard.css:200-208` |
| 首页眉标题 / 副标题 | 眉标题 12px / `700` / `letter-spacing: 0.22em`；副标题 14px / `500`；均 `var(--home-muted)` | `LocalDashboard.css:193-198`、`LocalDashboard.css:210-215` |
| 首页局部令牌 | `--home-ink: var(--te-neutral-900)`、`--home-muted: var(--te-neutral-500)`、`--home-card: var(--te-card-bg)`、`--home-accent: var(--te-primary-500)`、`--home-radius-lg: 22px`、`--home-radius-md: 14px`、`--home-shadow: 0 18px 44px rgba(15,23,42,0.09)` | `LocalDashboard.css:7-23` |

## 4. 主题令牌

### 4.1 生效顺序（移植时按此优先级取值）

1. `themeTokens.ts` 令牌表 = **运行时权威值**：158 个令牌（`token(...)` 定义，`themeTokens.ts:39`–`themeTokens.ts:1697`），经 `theme.ts:1211-1233` 转成 CSS 变量，再由 `useThemeStore.ts:196-210` 以 `style.setProperty(name, value, 'important')` 写到 `<html>`，覆盖一切静态声明。
2. `base.css:456-495`（`pureWhite`）/ `base.css:497-589`（`dark`）= 与权威值一致的静态兜底。
3. `base.css:1-223` 的 `:root` = 首帧兜底；**个别值与权威值不同**（见 4.7 字重），不要以它为准。
4. 预设覆盖：`themePresets.ts` 的 `pureWhite`/`dark` 两套 token 覆盖（例：paper-light 见 4.9）。

### 4.2 强调色与"从封面取色"的派生链

| 项 | 值 | 来源 |
| --- | --- | --- |
| 强调色令牌 | `--te-accent` = `--te-primary-500`；`--te-accent-soft` = `--te-active-bg`；同时派生 `--brand-50…--brand-700` | `theme.ts:1217-1232` |
| `--te-primary-500` | `#2563eb`（light）/ `#f59e0b`（dark） | `themeTokens.ts:39-51`、`base.css:458`、`base.css:499` |
| `--te-primary-400/300` | `#3b82f6` / `#93c5fd`（light）、`#fbbf24` / `#fde68a`（dark） | `themeTokens.ts:52-77`、`base.css:459-460`、`base.css:500-501` |
| `--te-primary-rgb` | `37, 99, 235` / `245, 158, 11`（供 `rgba(var(--te-primary-rgb), a)` 使用） | `themeTokens.ts:78-90` |
| 辅助青 | `--te-accent-cyan`：`#0891b2` / `#2dd4bf`（渐变常与主色配对） | `themeTokens.ts:122-131` |
| 取色来源开关 | 模式 `appearance.accentSource`，属性 `data-te-accent-source`，取值 `fixed` \| `cover`，默认 `fixed` | `themePresets.ts:46-52` |
| 封面主色算法 | 画到 `50×50` canvas：跳过 `alpha < 128`、跳过近灰（`max-min < 15`）、跳过 `max < 40 || min > 220`；按每通道 12 桶直方图取最高票桶中心色；失败/空图回退 `#1a73e8` | `colorExtractor.ts:80-153`、`colorExtractor.ts:1` |
| 取色触发 | 曲目 `id/cover/coverSource` 变化 → `extractDominantColor(displayCover)` → `coverThemeColor`；无封面回退 `#1a73e8`，未开启封面主题时 `dominantColor = #7c4dff` | `usePlayerStore.ts:1695-1741` |
| 覆盖为令牌 | 仅当 `accentSource === 'cover'` 时执行 `createThemeAccentTokenOverrides(色, tone, 背景, adaptive=true)` | `useThemeStore.ts:636-643`、`theme.ts:995-1031` |
| 派生规则 | 主色先向灰 `#808080` 混 `10%`；对比度 < 3（背景 `#f4f4f7` / `#17181a`）则回退 `#2563eb`（light）/ `#f59e0b`（dark）；`primary400` = 向白混 `12%`（dark `18%`），`primary300` = `38%`（dark `44%`）；同时改写 `--te-glow-main`（`0.14`/`0.2` alpha）、`--te-active-bg`（`0.1`/`0.16`）、`--te-navigation-active-text`、`--te-navigation-indicator` | `theme.ts:995-1031` |

**结论**：外壳的强调色只有两个入口——`--te-primary-500`（用户固定色）或封面主色经上述归一化后的值；两者最终都落在 `--te-accent` / `--te-primary-*` 上，移植时只需实现"主色 → 3 级色阶 + rgb 三元组"这一条链。

### 4.3 基础色（colors）

| 令牌 | light（pureWhite） | dark | 来源 |
| --- | --- | --- | --- |
| `--te-neutral-50` | `#ffffff` | `#050505` | `themeTokens.ts:132-141` |
| `--te-neutral-100` | `#f8fafc` | `#111111` | `themeTokens.ts:142-151` |
| `--te-neutral-200` | `#e5e7eb` | `#1f1f1f` | `themeTokens.ts:152-161` |
| `--te-neutral-300` | `#d1d5db` | `#343434` | `themeTokens.ts:162-171` |
| `--te-neutral-500` | `#64748b` | `#9b9b9b` | `themeTokens.ts:172-181` |
| `--te-neutral-700` | `#334155` | `#d8d8d8` | `themeTokens.ts:182-191` |
| `--te-neutral-900` | `#0f172a` | `#f7f7f2` | `themeTokens.ts:192-201` |
| `--te-chrome-text` | `#475569` | `#d8d8d8` | `themeTokens.ts:919-928` |
| `--te-favorite-500` | `#db2777` | `#d94f7d` | `themeTokens.ts:91-100` |
| `--te-success-500` | `#16a34a` | `#14b881` | `themeTokens.ts:101-110` |
| `--te-warning-500` | `#d97706` | `#f59e0b` | `themeTokens.ts:111-120` |
| `--te-info-500` | `#2563eb` | `#38bdf8` | `themeTokens.ts:121` |

### 4.4 表面 / 背景（surfaces）

| 令牌 | light | dark | 来源 |
| --- | --- | --- | --- |
| `--te-app-bg` | `#f4f4f7` | `#17181a` | `themeTokens.ts:202` |
| `--te-local-bg` | `#f4f4f7` | `#17181a` | `themeTokens.ts:203-212` |
| `--te-settings-bg` | `#f5f6f8` | `#17181a` | `themeTokens.ts:213-222` |
| `--te-streaming-bg` | `#f4f4f7` | `#17181a` | `themeTokens.ts:223-232` |
| `--te-player-bg` | `#f4f4f7` | `#17181a` | `themeTokens.ts:233-242` |
| `--te-card-bg` | `#ffffff` | `#181818` | `themeTokens.ts:694-703` |
| `--te-card-border` | `rgba(15,23,42,0.08)` | `rgba(255,255,255,0.1)` | `themeTokens.ts:704-713` |
| `--te-subtle-bg` | `#f8fafc` | `#121212` | `themeTokens.ts:714-723` |
| `--te-hover-bg` | `#f3f4f6` | `rgba(255,255,255,0.065)` | `themeTokens.ts:724-733` |
| `--te-active-bg` | `#e8e8e8` | `rgba(245,158,11,0.16)` | `themeTokens.ts:734-743` |
| `--te-settings-backplate` | `#f5f6f8` | `#17181a` | `base.css:85`、`base.css:527` |

### 4.5 导航（navigation）

| 令牌 | light | dark | 来源 |
| --- | --- | --- | --- |
| `--te-navigation-bg` | `rgba(255,255,255,0.94)` | `#17181a` | `themeTokens.ts:396-405` |
| `--te-navigation-border` | `rgba(0,0,0,0.05)` | `transparent` | `themeTokens.ts:406-415` |
| `--te-navigation-shadow` | `4px 0 24px rgba(15,23,42,0.03)` | `none` | `themeTokens.ts:416-425` |
| `--te-navigation-text` | `#475569` | `#d8d8d8` | `themeTokens.ts:426-435` |
| `--te-navigation-icon` | `#64748b` | `#9b9b9b` | `themeTokens.ts:436-445` |
| `--te-navigation-hover` | `rgba(15,23,42,0.04)` | `rgba(255,255,255,0.065)` | `themeTokens.ts:446-455` |
| `--te-navigation-hover-text` | `#0f172a` | `#f7f7f2` | `themeTokens.ts:456-465` |
| `--te-navigation-active` | `rgba(37,99,235,0.08)` | `rgba(245,158,11,0.16)` | `themeTokens.ts:466-475` |
| `--te-navigation-active-text` | `#2563eb` | `#f59e0b` | `themeTokens.ts:476-485` |
| `--te-navigation-indicator` | `#2563eb` | `#f59e0b` | `themeTokens.ts:486-495` |
| `--te-navigation-opacity` | `94%` | `100%` | `themeTokens.ts:496-506` |
| `--te-navigation-radius` | `0px` | `0px` | `themeTokens.ts:507-517` |
| `--te-shell-control-text` | `#0f172a` | `#f7f7f2` | `themeTokens.ts:296-305` |
| `--te-shell-control-hover` | `rgba(37,99,235,0.08)` | `rgba(245,158,11,0.12)` | `themeTokens.ts:306-315` |

### 4.6 玻璃 / 卡片材质与阴影

| 令牌 | light | dark | 来源 |
| --- | --- | --- | --- |
| `--te-glass-bg` | `rgba(255,255,255,0.94)` | `rgba(24,24,24,0.82)` | `themeTokens.ts:744-753` |
| `--te-glass-bg-strong` | `rgba(255,255,255,0.98)` | `rgba(29,29,29,0.94)` | `themeTokens.ts:754-763` |
| `--te-glass-border` | `rgba(15,23,42,0.1)` | `rgba(255,255,255,0.09)` | `themeTokens.ts:764-773` |
| `--te-glass-shadow` | `0 16px 42px rgba(15,23,42,0.08)` | `0 18px 54px rgba(0,0,0,0.34)` | `themeTokens.ts:774-783` |
| `--te-card-blur` | `20px` | `20px` | `themeTokens.ts:1032-1047` |
| `--te-card-saturate` | `150%` | `150%` | `themeTokens.ts:1048-1063` |
| `--te-surface-opacity` | `100%` | `100%` | `themeTokens.ts:784-799` |
| `--te-glow-main` | `rgba(37,99,235,0.12)` | `rgba(245,158,11,0.18)` | `themeTokens.ts:800-812` |
| `--te-glow-soft` | `rgba(59,130,246,0.08)` | `rgba(217,79,125,0.12)` | `themeTokens.ts:813-822` |
| `--te-glow-cyan` | `rgba(8,145,178,0.08)` | `rgba(45,212,191,0.1)` | `themeTokens.ts:823-832` |
| `--te-library-table-shadow` | `0 26px 78px rgba(86,70,160,0.1)` | 同左 | `themeTokens.ts:548-557` |
| `--te-card-shadow` | `none`（默认值，`base.css` 独有、非注册表令牌） | — | `base.css:3090` |

### 4.7 圆角与尺寸（shape / layout）

| 令牌 | 值（两色调相同，除注明） | 来源 |
| --- | --- | --- |
| `--te-radius-global` | `10px`（paper-light 预设覆盖为 `6px`） | `themeTokens.ts:945-960`、`themePresets.ts:891` |
| `--te-card-radius` | `16px`（paper-light `6px`） | `themeTokens.ts:929-944`、`themePresets.ts:892` |
| `--te-dialog-radius` | `8px` | `themeTokens.ts:961-971` |
| `--te-search-radius` | `10px` | `themeTokens.ts:972-982` |
| `--te-toast-radius` | `8px` | `themeTokens.ts:983-993` |
| `--te-track-title-radius` | `6px` | `themeTokens.ts:994-1004` |
| `--te-navigation-radius` | `0px` | `themeTokens.ts:507-517` |
| `--te-library-selection-radius` | `10px` | `themeTokens.ts:629-639` |
| `--te-library-cover-radius` | `8px` | `themeTokens.ts:651-661` |
| `--te-library-action-radius` | `12px` | `themeTokens.ts:683-693` |
| `--te-card-border-width` | `1px` | `themeTokens.ts:1016-1031` |
| `--te-library-icon-size` | `18px` | `themeTokens.ts:618-628` |
| `--te-ui-scale` | `0.94` | `themeTokens.ts:1064-1078`、`base.css:48` |
| `--te-menu-width` | `clamp(132px, 18vw, 216px)` | `themeTokens.ts:1079-1088`、`base.css:49` |
| 播放器（对照） | `--te-player-control-size 32px`、`--te-player-play-size 44px`、`--te-player-control-gap 12px`、`--te-player-control-radius 999px`、`--te-player-progress-height 6px`、`--te-playback-cover-radius 26px` | `themeTokens.ts:1413-1510`、`themeTokens.ts:1170-1180` |

### 4.8 动效（motion / easing / duration）

| 令牌 | 值 | 来源 |
| --- | --- | --- |
| `--te-ease-enter` | `cubic-bezier(0.4, 0, 0.2, 1)` | `themeTokens.ts:1687-1696`、`base.css:27` |
| `--te-ease-soft` | `cubic-bezier(0.22, 1, 0.36, 1)`（= `--te-ease-out-quint`） | `themeTokens.ts:1697`、`base.css:33-35` |
| `--te-ease-out-expo` | `cubic-bezier(0.16, 1, 0.3, 1)` | `base.css:36` |
| `--te-ease-out-strong` | `cubic-bezier(0.23, 1, 0.32, 1)` | `base.css:38` |
| `--te-ease-spring` | `cubic-bezier(0.22, 1.14, 0.36, 1)` | `base.css:34` |
| `--te-motion-press` | `90ms` | `base.css:39` |
| `--te-motion-hover` | `160ms` | `base.css:40` |
| `--te-motion-panel` | `280ms` | `base.css:41` |
| `--te-motion-page` | `400ms` | `base.css:42` |
| `--te-motion-settle` | `500ms`（悬停「进入」用长尾 `--te-ease-out-quint`，「退出」用 `--te-motion-return`） | `base.css:43-44` |
| `--te-motion-return` | `220ms` | `base.css:45` |
| `--te-motion-press-scale` | `0.97` | `base.css:46` |
| `--te-motion-hover-translate` | `-1px` | `base.css:47` |
| 页面切换 | 进入 `opacity 0.34s ease / transform 0.48s cubic-bezier(0.16,1,0.3,1) / filter 0.42s`；离开 `opacity 0.22s / transform 0.3s cubic-bezier(0.4,0,0.2,1)`；位移 `±40px`、`scale(0.99)`、`blur(8px)` | `App.vue:1468-1516` |
| 减少动效 | `html[data-te-motion='reduced']` → `opacity 120ms var(--te-ease-out-strong)`、位移/模糊归零；`off` → `transition: none` | `App.vue:1520-1566`、`base.css:392-454` |

### 4.9 字体与字号（typography）

| 令牌 | 值 | 来源 |
| --- | --- | --- |
| `--te-font-sans` | `'Inter', 'Plus Jakarta Sans', 'MiSans', 'Microsoft YaHei UI', 'Microsoft YaHei', 'PingFang SC', 'Hiragino Sans GB', system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif` | `themeTokens.ts:833-842`、`themeData.ts:16-17`、`base.css:59-61` |
| `--te-font-display` / `--te-font-rounded` | 同一字体栈（注册表三者的 pureWhite/dark 默认值都等于 `lightFont`） | `themeTokens.ts:843-862`、`base.css:53-58` |
| CJK 字体 | `MiSans`（`assets/fonts.css:1` 注明按 unicode-range 分包；`/font/misans/misans.css`） | `fonts.css:1` |
| 打包拉丁字体 | Inter `100-900`、Plus Jakarta Sans `200-800`、Lora `400-700`、JetBrains Mono `400-700`、Space Grotesk `400-700`（均 woff2，带 unicode-range） | `fonts.css:3-80` |
| `--te-font-size-body` | `14px` | `themeTokens.ts:908-918`、`base.css:66` |
| `html` 根字号 | `14px` | `base.css:616` |
| `--te-text-title` | 注册表 `700`；`base.css:62` 兜底写的是 `500` —— **以 `700` 为准**（运行时 `!important` 覆盖） | `themeTokens.ts:863-877`、`base.css:62` |
| `--te-text-body` | 注册表 `500`；`base.css:63` 兜底 `400` | `themeTokens.ts:878-892`、`base.css:63` |
| `--te-text-meta` | `400` | `themeTokens.ts:893-907` |
| `--te-text-strong` | `500`（仅 `base.css` 声明，非注册表令牌） | `base.css:65` |
| `h1`–`h6` | `font-weight: var(--te-text-title)`、`letter-spacing: -0.01em`、`line-height: 1.18` | `base.css:895-904` |
| 外壳用到的字号 | 侧栏条目 14px/500；侧栏图标 ≈17px；窗口按钮图标 14–17px；页标题 `clamp(28px,2.8vw,36px)`/800；流媒体标题 `clamp(20px,2.2vw,26px)`/700；副标题 12px/500；眉标题 11px/700；统计 12.5px/500；首页大标题 `clamp(34px,4vw,48px)`/900 | `SideMenu.vue:595-600`、`SideMenu.vue:575`、`TitleBar.vue:407/464/484/504/556`、`SongList.css:288-289`、`StreamingContentHeader.css:411-412`、`StreamingContentHeader.css:423-424`、`StreamingContentHeader.css:389-390`、`SongList.css:149-150`、`LocalDashboard.css:203-205` |

字号写法约定：绝大多数尺寸是 `calc(var(--te-font-size-body, 14px) * N / 14)` 或 `* 系数`，因此**根字号即缩放旋钮**——移植时把 14px 作为基准，按比例推所有文字尺寸。

## 5. 效果可移植性（Skia 视角）

| 效果 | 位置与强度 | Skia 可行性 |
| --- | --- | --- |
| 侧栏毛玻璃 | `backdrop-filter: blur(24px) saturate(180%)` `SideMenu.vue:311`；paper-light `blur(26px) saturate(140%)` `paper-light.css:123` | **需降级**：无 backdrop 采样。改用「背景图区域 + `ImageFilter.makeBlur` 预模糊贴图 + 目标色 94% 覆盖」离线近似；saturate 用色彩矩阵 |
| 卡片毛玻璃 | `blur(20px) saturate(150%)` `base.css:2655`；参数来自 `--te-card-blur`/`--te-card-saturate` | 同上；paper-light 预设已把 `material.cardBlur` 设为 `0px`（`themePresets.ts:887`），可直接走纯色 |
| 搜索框毛玻璃 | `blur(16px) saturate(145%)` `SongList.css:1581`；流媒体搜索框刻意不模糊（`StreamingContentHeader.css:454-455`） | 半径小，可忽略或用一个 8–16px 预模糊底 |
| 液态玻璃 | 侧栏 `::before` `blur(20px) saturate(128%)` `SideMenu.vue:740`；标题栏 `blur(16px) saturate(124%)` `TitleBar.vue:619` | 高成本，属可选增强；先不做 |
| 小控件毛玻璃 | 返回键 `blur(10px)` `base.css:279-280`；回顶键 `blur(12px) saturate(150%)` `base.css:333-334` | 可直接退化为不透明色 |
| 播放页大模糊 | `blur(58px) saturate(1.22) brightness(0.52)`（dark `saturate(1.32) brightness(0.36)`） | **不可实时**：预渲染一次封面缩略图后高斯模糊并缓存 |
| 环境光渐变 | `body::before` conic + linear，`opacity: 0.9`，14s `translate3d` 往返位移 `±1.2~1.4%` `base.css:808-826`、`base.css:916-923` | 可移植：两个渐变层 + 缓慢平移；dark/pureWhite 下此层为 `display: none` |
| 其他渐变 | 首页强调下划线 `linear-gradient(90deg, var(--home-accent), var(--te-accent-cyan))` `LocalDashboard.css:658`；侧栏分隔线 `linear-gradient(to right, …)` `SideMenu.vue:708`；设置覆盖层三层背景 `App.vue:1243-1248` | 线性/径向渐变均可直接移植 |
| `mix-blend-mode` | **外壳完全未用**；全仓库仅迷你播放器两处（`overlay` / `soft-light`，`source/Twilight_Echo/src/renderer/src/mini-player/MiniPlayer.css:198`、`:273`） | 外壳移植可完全忽略 |
| 大半径 `filter: blur()` 过渡 | 页面切换 `blur(8px)` `App.vue:1495-1515`；引导页退场 `blur(10px)` `App.vue:1659` | 可移植但成本高；建议只保留透明度+位移 |
| 低特效开关 | `html[data-te-effects-mode='reduced']`：`--te-background-cover-blur: 0px`、`--te-card-blur: 0px`、`--te-card-saturate: 100%`、阴影全 `none`，并对 `.side-menu/.title-bar/.glass-card` 关闭 blur `base.css:3084-3120`；`body.te-no-blur *` 全局关模糊 `base.css:729-732` | **推荐的移植基线**：直接以 reduced 档实现，再逐项加回 |
| 背景图模糊参数 | `--te-background-cover-blur: 28px`（light）/ `36px`（dark）；`--te-background-overlay-opacity: 12%` / `38%` | `themeTokens.ts:274-295`、`base.css:74-75` |

## 6. 移植落地要点

1. **外壳没有外框**：唯一的"窗口框架"来自无边框窗口本身；不要给根容器加 margin/radius/border。分层是 body 页面背景 → 卡片 `--te-card-bg` + `--te-card-border` + `--te-card-radius`；设置页另有 `z-index: 2000` 的独立覆盖层。
2. **侧栏是固定浮层**：`top: 32px; bottom: 播放栏高 + 10px; width: clamp(132px,18vw,216px)`，靠 `padding-left` 挤压内容而不是 `transform`（`App.vue:1376-1390` 有明确设计说明）。
3. **令牌优先级**：注册表 158 个令牌 > `base.css` 静态兜底；`--te-accent` 只是 `--te-primary-500` 的别名，封面取色最终也写回 `--te-primary-*`。
4. **字号是相对的**：全部按 `14px` 基准缩放，改一个基准即可整体缩放。
5. **尺寸常量速查**：侧栏条目 `40px` 高 / `10px` 圆角 / `14px` 图标间距；标题栏 `35px` 高、按钮 `36px` / 窗口键 `46px`；内容页头 `min-height: 72px`、水平 `clamp(36px,6vw,84px)`；搜索框 `38px` 高、`999px` 圆角。
