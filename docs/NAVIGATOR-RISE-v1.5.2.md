# Rise 导航器移植（开发中）

分支：`codex/v1.5.2`。基线：`fabric/`，Minecraft 1.20.1、Java 17。
当前状态：完成导航器主界面与自绘控件的源码适配，按用户截图继续校准外观；
未宣称通过逐像素一致性验收。

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

- 固定 400×300，侧栏 100，主窗口圆角 12；模块卡片宽 283、高 38、间距 7。
  卡片圆角按 6.9.2 截图收紧为 3（6.9.5 源码值为 6）。
- 主背景 `#FE171A21`，侧栏 `#FF121419`，模块背景黑色 alpha 50。
- 默认 Blend 主题，蓝绿动态插值；文字字体未替换。
- 侧栏使用移植前的 GuiIcon 图片，搜索改用同一图标集的放大镜：SEARCH、WORLD、MOVEMENT、COMBAT、
  RENDER、PLAYER、FUN、BOOK、MISC、CLIENT、CLIENT。图标统一为原来的 8px，
  标签偏移和选中条宽度使用同一尺寸；保留 Rise 侧栏布局及选中动画。
- 侧栏首行 y+45.5，行距 19.5；选中条高 15、圆角 5、线性 200ms，横移量为 alpha/80。
- 开启使用 300ms ease-out-expo；关闭透明度线性 100ms、缩放线性 300ms。
- 分类切换旧页淡出 200ms、新页淡入 200ms。设置高度与透明度分别动画。
- 描边外阴影使用 Rise 原循环和透明度公式。按参考截图取消选中条的额外 Bloom，
  不创建或执行其 RenderTarget；已适配的 NavigatorBloom 源码暂不接入主界面。
- 修正 RenderUtil.c 的圆心和半径语义：第 i 层圆心为 (x+100−25i, y+150−25i)，
  半径为 50i，恢复从左上方延伸的低透明度底色。叠色采用截图的冷蓝色
  `#014764FD`；直接使用 6.9.5 的 Blend 主色会使实机截图产生明显青色偏移。
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
原版 EditBox，统一使用自绘 TextBox。固定 100px 滑轨、5px 布尔圆点及 105×105 色盘
按参考代码布置。数值按 Enter 提交，文本实时保存。

方块、物品列表、交易条件和文件等 Wurst 特有设置显示为紧凑操作行，打开已有专用编辑器。
这些专用编辑器不属于 Rise 的原始控件集合，仍保留其功能界面。
标准导航器未使用原版背景模糊控件，也未加入 Rise 的联网配置或脚本后端。

## 验证

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
