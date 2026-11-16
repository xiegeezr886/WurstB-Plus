# Rise 导航器移植（开发中）

分支：`codex/v1.5.2`。基线：`fabric/`，Minecraft 1.20.1、Java 17。
当前状态：完成导航器主界面与自绘控件的源码适配，按用户截图继续校准外观；
未宣称通过逐像素一致性验收。

2026-11-14 按用户最新要求，尺寸以 Rise 截图为准，不采用 v1.5.1 的紧凑尺寸。
用户随后确认透明度分区：左侧分类栏半透、右侧功能栏不透明；背景模糊继续保留。
保留当前导航器结构、图标、字体、控件形状和交互。

用户确认保留 Wurst 的 logo、现有 MiSans 文字字体、最初的 GuiIcon 图标，
以及原有 11 个分类及其模块归属：
搜索、方块、移动、战斗、渲染、聊天、娱乐、物品、其他、HUD、界面。
因此不添加 Rise 的 Ghost、CaS、Themes、Language 分类。

## 对照来源

`AnShanArchive/Rise-6.9.5`，提交 `f58ef9ca99b43ddb192ca4d2319c2f2a09e8c16a`。
本地路径：`reference/clients/Rise-6.9.5/`。

视觉基准为用户提供的 `Screenshot_2026-11-13-18-23-40-781_tv.danmaku.bi.jpg`，
图中版本为 Rise 6.9.2。截图与本地 6.9.5 源码有差异时，主界面外观优先采用截图。
截图来自压缩视频，不能据此宣称颜色及抗锯齿逐像素一致。

主要对照 `ui/click/standard/` 的 `RiseClickGUI`、`ModuleComponent`、
`SidebarCategory`、`CategoryScreen`、`SearchScreen`、各类 `ValueComponent`，
以及 `util/gui/ScrollUtil`、`util/gui/textbox/TextBox`、`RenderUtil.dropShadow`、
`BloomShader`、`GaussianKernel` 和 `bloom.frag`。参考工程不参与基线编译。
移植资源的原始声明见 `fabric/src/main/resources/assets/wurstpenguin/navigator/NOTICE.txt`。

## 绘制参数

- 以 810×653 的 Rise 截图测量：主窗口约 640×480、侧栏 160 像素。
  对应固定 400×300、侧栏 100 的参考坐标，按视口高度统一换算；1920×1080 下窗口约 1059×794 像素。
  主窗口圆角 12，卡片参考宽 283、高 38、间距 7，名称字号 10、描述字号 7.5。
  绘制、裁剪、搜索、点击、拖动及滚动共用同一坐标变换，不修改 Minecraft 的 GUI 缩放设置。
  卡片圆角按 6.9.2 截图收紧为 3（6.9.5 源码值为 6）。
- 左侧分类栏使用 `#B013161F`（约 69% 不透明度），右侧功能区使用实色 `#FF101016`，
  模块卡片保留 `#321C1F2B` 轻微叠色。两侧独立绘制，功能区实色底板不覆盖分类栏。
- 颜色引用 ClickGUI 的 `OpaiStyle`：强调/选中 `#B5BEF0`、选中条文字 `#0C185C`、
  正文 `#F0F0F5`、辅助文字 `#C1C5D6`；保留导航器原有文字透明度。
- 版本号、启用模块标题、滑块、布尔圆点及设置文字使用同一套配色；文字字体未替换。
- 侧栏使用移植前的 GuiIcon 图片，搜索改用同一图标集的放大镜：SEARCH、WORLD、MOVEMENT、COMBAT、
  RENDER、PLAYER、FUN、BOOK、MISC、CLIENT、CLIENT。图标统一为原来的 8px，
  标签偏移和选中条宽度使用同一尺寸；保留 Rise 侧栏布局及选中动画。
- 侧栏首行参考坐标 y+45.5，行距 19.5；选中条高 15、圆角 5、线性 200ms，横移量为 alpha/80。
- 开启使用 300ms ease-out-expo；关闭透明度线性 100ms、缩放线性 300ms。
- 分类切换旧页淡出 200ms、新页淡入 200ms。设置高度与透明度分别动画；
  淡出直接作用于页面内容，不叠加不透明遮罩，避免半透背景在切换时变黑。
- 描边外阴影使用 Rise 原循环和透明度公式。按参考截图取消选中条的额外 Bloom，
  不创建或执行其 RenderTarget；已适配的 NavigatorBloom 源码暂不接入主界面。
- 窗口阴影只画在圆角窗口外，挖空内部，避免多层黑色阴影叠加后使分类栏失去半透效果。
  分类栏只绘制一层圆角背景，交界处使用裁剪，不重复填充。
- 修正 RenderUtil.c 的圆心和半径语义：第 i 层圆心为 (x+100−25i, y+150−25i)，
  半径为 50i，恢复从左上方延伸的低透明度底色。叠色改为共享强调色
  `#01B5BEF0`，透明度保持不变。
- 描述统一使用低透明度灰色，忽略原模块描述的 Minecraft 颜色码。
- NanoVG 承担矢量绘制，继续使用其 OpenGL 状态保护。
- 无世界时，每帧重绘菜单底图，避免主帧缓冲中上一帧的半透明阴影、缩放轮廓和
  主菜单文字残留叠加成黑框。进入世界后由世界画面提供底图。

## 已接入的行为

| 操作 | 当前实现 |
| --- | --- |
| 模块标题左键 / 右键 | 执行原功能动作 / 原地展开设置；支持多卡片同时展开 |
| 分类与列表 | 独立分类滚动状态，展开高度推动后续卡片 |
| 搜索 | 直接输入进入搜索，首次输入清空旧词，名称及独立别名做 OR 子串匹配 |
| 布尔 / 模式 | 点击切换；模式左键下一项、右键上一项 |
| 数值 / 文本 | 滑轨拖动、数值内联输入并提交；文本实时更新 |
| 颜色 | 卡片内 HSV 面板，RGB / HEX 复制 |
| 开关界面 / 分类 | 缩放及透明度动画、分类淡入淡出 |
| 窗口 | 顶部拖动，会话内记忆位置，越界后重开居中 |

功能及设置继续使用 Wurst 本身的模型和保存机制。主界面的搜索、数字和字符串输入已移除
原版 EditBox，统一使用自绘 TextBox。恢复参考控件比例：100 长的滑轨、5 大小的布尔圆点、105×105 色盘，
所有绘制和命中区域随截图比例统一缩放。
数值按 Enter 提交，文本实时保存。

方块、物品列表、交易条件和文件等 Wurst 特有设置显示为紧凑操作行，打开已有专用编辑器。
这些专用编辑器不属于 Rise 的原始控件集合，仍保留其功能界面。
背景复用 ClickGUI 的 `OpaiBackground`：界面绘制前执行模糊，关闭时释放资源。
世界模糊使用半分辨率目标和 4 px 内部半径，再线性还原，保留约 8 px 的原始范围。
主菜单渐变不执行模糊；底图、导航器、弹层和提示统一在一次 NanoVG pass 绘制，减少全屏合成。
未加入 Rise 的联网配置或脚本后端。

## 验证

2026-11-14 透明度分区验证：Fabric 1.20.1 build 成功，30 项测试通过（0 失败、0 错误、0 跳过）。
Java 17 客户端在 1920×1080 的独立 QA 世界中检查：左侧分类栏透出模糊场景，右侧功能区保持实色，
窗口仍约 1059×794 像素。右侧空白边距像素 (705,300)、(705,650) 均为 RGB (16,16,22)，
左侧 (675,300)、(675,650) 分别为 (51,35,39)、(26,35,45)，确认两侧透明度独立。
截图：`.dsh-tmp/navigator-sidebar-translucent-content-solid.png`。
日志未出现 NanoVG pass failed、OpenGL 错误或 Background blur unavailable；未使用鼠标或键盘自动输入。
测试包 SHA256：`5AD3FABB1A552A1B55778FC951171352B8FE9E16096E8B25FAB9AFA1AB39940A`。

2026-11-14 截图尺寸校准（透明度分区调整前）：Fabric 1.20.1 build 成功，30 项测试通过（0 失败、0 跳过）。
Java 17 客户端在 1920×1080 下检查：主窗口约 1059×794 像素，侧栏及卡片保持 Rise 截图比例，
功能区可透出模糊后的世界画面。未使用鼠标或键盘自动输入。
截图：`.dsh-tmp/navigator-reference-size-menu.png`、`.dsh-tmp/navigator-reference-size-world.png`。

2026-11-14 配色统一验证：Fabric 1.20.1 build 成功，27 项现有测试全部通过，0 失败、0 跳过。
Java 17 客户端已检查淡紫选中条、深蓝选中文字、侧栏与卡片的冷黑配色；
窗口仍为 400×300、侧栏 100、卡片高 38，未修改布局或事件处理。
截图：`.dsh-tmp/navigator-opai-palette.png`。未使用鼠标或键盘自动输入。

`NavigatorInteractionTest` 的 9 项测试覆盖别名查询、语言环境、滚动边界、
收起后的回弹、帧率无关滚动、动画中途反转、关闭线性曲线、数字编辑和原分类顺序。
基线另有 4 项 NanoVG native loader 测试。
2026-11-13 重跑结果：13 项全部通过，失败、错误及跳过均为 0；Fabric build 成功。

本机 Gradle 全局初始化脚本默认禁用测试。验证时使用独立本地 init script 重新启用 Test，
并将 `java.io.tmpdir` 指向工作区，避免系统临时目录权限导致旧测试失败。
未修改全局初始化脚本。

实际客户端采用 Java 17 启动，使用独立 `fabric/run/` 目录。
人工检查中发现菜单错误调用依赖玩家的 HUD 动态名称，已改用稳定的本地化模块名称。
启动日志确认 NanoVG GL3 正常；导航器打开后检查 NanoVG pass failed 和 OpenGL 错误。
11 月 13 日首轮图片 `.dsh-tmp/navigator-v152-selfdraw.png` 保留用于改动前对照。
新参考图检查使用 `.dsh-tmp/navigator-v152-reference.png`，左右等比例对照图为
`.dsh-tmp/navigator-reference-after.png`（左为 Rise 截图，右为本地客户端）。
右图初始为搜索页，比左图的渲染分类多一个搜索框；模块和列表长度来自各自功能集合。
黑框修复验证：无世界状态下两次截图相隔 62.7 秒，底边阴影区域像素完全相同，
不再累积变黑；截图保存在 `.dsh-tmp/navigator-shadow-fixed-{early,late}.png`。
恢复原始 GuiIcon 图标后再次完成 Fabric build 和 13 项测试；Java 17 客户端的
`.dsh-tmp/navigator-original-icons.png` 已检查 11 个分类图标与文字间距，
日志无 NanoVG pass failed 或 OpenGL 错误。客户端保留供用户手动验收。
手动验收时重点比较侧栏选中条、搜索滚动、右键多卡片展开、滑轨拖动、
数字输入 Enter、文本输入、颜色面板，以及关闭/重开动画。按用户要求不自动点击或输入。

测试包：`fabric/build/libs/WurstB+ Plus-1.5.2-Fabric-1.20.1.jar`。
