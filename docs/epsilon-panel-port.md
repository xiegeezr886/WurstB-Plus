# Epsilon 中央面板导航器 · 移植记录

参考项目：**[Nekoyahouse/Epsilon](https://github.com/Nekoyahouse/Epsilon)**，**GPLv3**
（本工程同为 GPLv3，许可兼容；本项目只借鉴其布局、配色与交互设计，代码是按
Minecraft 1.20.1 + 本工程的渲染层重写的，不是逐文件搬运）。

屏幕截图依据：用户提供的暗色截图（`2026.3.0`）。取源时发现 `2026.3.0` 与当前
`main`（`2026.12.0`）的 `PanelLayout` 公式**完全一致**（同样的
`0.56 / 584 / 324 / 528 / 300` 夹取），所以版本差异不影响几何，规格数字统一取自源码。

## 1. 交付物

| 文件 | 作用 |
| --- | --- |
| `clickgui2/epsilon/EpsilonPanelNavigatorScreen.java` | 界面本体：三栏布局、绘制、输入 |
| `clickgui2/epsilon/EpsilonPanelLayout.java` | 纯几何（无 MC 依赖，可单测） |
| `clickgui2/epsilon/EpsilonPanelTheme.java` | 暗色 MD3 调色板与派生色（无 MC 依赖） |
| `clickgui2/NavigatorScreens.java` | 模式选择，形状与 `ClickGuiScreens` 对称 |
| `test/.../epsilon/EpsilonPanelLayoutTest.java` | 18 个几何断言 |
| `test/.../epsilon/EpsilonPanelThemeTest.java` | 10 个颜色/派生公式断言 |

## 2. 几何（全部取自源码常量）

面板 = 一张居中大卡片，内部横排三栏 `rail｜modules｜detail`：

- 面板宽 `clamp(min(screenW*0.56, 584), 528, ∞)`，高 `clamp(min(screenH*0.56, 324), 300, ∞)`，居中。
- `OUTER_PADDING=5`、`SECTION_GAP=3`；中栏宽 `min(164, panelW*0.292)`；详情栏吃掉剩余宽度。
- 圆角：面板 `17`、栏 `13`、行/卡片 `9`、控件 `7`。
- rail：菜单按钮 28×28@`(+6,+4)`；标题 `x=38,y=7`；分类项自 `rail.y+40` 起、步进 38、尺寸
  `rail.width-10 × 34`；「客户端设置」永远贴底 `rail.bottom-39`；rail 宽 42↔120 动画 240ms。
- 中栏：标题 `(x+6, y+10)`、副标题 `(x+6, y+21)`、搜索框 `(right-6-76, y+8, 76×18)`、
  视口 `(x+3, y+34, w-6, h-40)`；模块行高 34、行距 3；开关尾部对齐 `26×16`。
- 详情栏：标题、header 卡片 `(x+3, y+34, w-6, 36)`、键位方块 `18×18`@`header.x+8`、
  分段控件 `72×18`、设置视口 `(x+3, header.bottom+6, …)`；设置行高 28、行距 3；
  滑条轨道 `(right-5-116, y+12, 72×6)`、数值框 `(right-5-40, y+4, 40×18)`。
- 滚动条：仅在内容装不下时出现；命中宽 10、滑块宽 3.5（hover 6）、右内缩 2.5、最小高 10；
  颜色 `OUTLINE@64 → PRIMARY@190`。

## 3. 调色板（TonalSpot + Dark）

关键：`MD3Theme` 的颜色是**运行时按 preset+mode 重算**的静态字段，类静态初值与其不同
（`SURFACE_DIM` / `LOW` / `HIGHEST` 三处）。本工程取的是重算后的真值，例如
`SURFACE=0xEE141218`、`SURFACE_DIM=0xE81B1820`、`CONTAINER=0xF4211F26`、
`HIGH=0xF82B2930`、`HIGHEST=0xFC35333B`、`PRIMARY=0xFFD0BCFF`、
`SECONDARY_CONTAINER=0xEC4A4458`、`TEXT_PRIMARY=0xFFECE6F0`。

**注意**：仓库里原有的 `supersoft/EpsilonMd3Theme` 是**浅色**（白底 + `#007CFF`）且服务
SuperSoft 体系，与这里的暗色面板是两套，互不影响。

## 4. 输入与交互

- 路由顺序与参考一致：详情栏 → 模块列表 → rail（客户端设置模式下是设置页 → rail）。
- 模块行左键 = 选中；点在开关上 = 开关模块（`doPrimaryAction()`，与其它 GUI 一致）。
- 详情栏键位方块左键 = 进入等待绑定：ESC 取消、Backspace/Delete 解绑、其它键绑定。
- 滑条按住拖动；枚举 chip 点击循环取值；有子项的设置行点击折叠/展开。
- 滚轮：`velocity -= delta*24`，每帧 `scroll += velocity*partialTick`、`velocity *= 0.86`、
  `<0.3` 归零——与参考项目的惯性模型同参数。
- ESC：先取消绑定监听 / 让搜索框失焦，再关闭界面。

## 5. 与参考项目的差异（诚实清单）

1. **枚举设置**：参考是点击弹出下拉列表；这里是点击循环取值。没有做弹窗。
2. **设置分组**：参考里分组是带描边与嵌套配色的折叠卡片；这里只在组头上折叠、子项缩进 8px，
   没有画分组卡片。
3. **header 的两个分段控件**只剩一个「开启/关闭」：WurstB 的按键只有切换语义，模块也没有
   「是否显示」标志，照着画两个会有一个是假控件。
4. **图标**：参考用自带的 TTF 图标字体；这里用几何占位字形，没有图标字形。
5. **无音效、无跑马灯、无 IME 预编辑**；参考的 `SoundManager` / 文本测量未移植。
6. **标题栏文案**用本工程的 `Navigator` / `WurstB+ 1.6`，不是 `Epsilon 2026.3.0`。
7. **字体**：参考用自研 TTF 渲染器，这里用原版字体 + pose 缩放，字号按 `scale*18` 换算；
   中文由原版的 unicode 字体兜底。

## 6. Rise 模式开关

与 ClickGUI 的 `vapeMode` 对称：`GuiPreferences.isRiseMode()` + 独立的
`NavigatorScreens.setRiseMode(boolean[, boolean reopen])`，不是从枚举派生。两个入口：

- **导航器（中央面板）的客户端设置页**：打开即切到原有的 Rise 界面。
- **ClickGUI（Epsilon 下拉）的设置列表**：`Rise mode` 行，只改偏好不换界面——
  这是从 Rise 模式**切回来的入口**。

`NavigatorScreen`（Rise 6.1.30 移植）里原本自带的「Rise 模式:开/关」开关**已移除**，
那一页现在只显示当前状态与开关去处。`PvPUtilsNavigatorScreen`（968 行）**保留文件但已无
任何入口**，与旧的 `NeteaseMusicScreen` 处理方式一致。

## 7. 验证状态（诚实声明）

- 只做了编译 + 单元测试：`gradlew compileJava compileTestJava test --offline`
  → `BUILD SUCCESSFUL`，**125 个测试类 / 691 个测试 / 0 失败**（本功能新增 28 个）。
- **从未在游戏里渲染过**：所有 `FlatRenderer` 圆角矩形、`pose` 缩放文本、`enableScissor`
  用法只做过签名核对与编译验证；视觉结果（字号、间距、对齐）需要实机确认。
- 参考项目的 `scale` 是相对字号，本工程按 `scale*18` 换算，这个换算系数是**我的选择**，
  不是源码里的公式，实机可能需要微调。
