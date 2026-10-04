# 标题主界面（v1.6 根工程 Forge 1.20.1）

> 范围：Minecraft 标题界面（主菜单）的整体版式。代码在
> `net.wurstclient.gui.title`：`WurstTitleMenu`（画）+ `WurstTitleButton`（控件）
> + `TitleMenuLayout`（几何）+ `BackgroundSelectScreen`（背景选择）。
> 背景子系统本身见 [title-background.md](title-background.md)。

## 1. 版式来源

用户给了一张参考主界面截图（1296×672 物理像素）。版式不是照着感觉调的，而是
**先在截图上量像素，再折成比例**，见 `TitleMenuLayout` 里的 `*_RATIO` 常量。
量到的数字：

| 元素 | 参考图上的物理像素 |
| --- | --- |
| 账号胶囊 | 左上角 `(16, 15)` 起，`218×70`，圆角约 10；头像 42×42 |
| 账号名 / 副标题 | 名 `Eatgrapes` 约 24px 高、白色；`Welcome back` 约 13px、灰 |
| 齿轮 | 右上角 `(1244, 16)` 起，`36×36`，右边距 16 |
| 动作条底板 | `x 20..920`（宽 904）、`y 579..646`（高 67），约 50% 黑 |
| 动作条按钮 | 4 个 `208×48`、间隔 16，约 82% 黑，圆角约 12；图标约 20px |
| 左下角标题 | `x 28..213`、`y 506..547`（字高 41） |

**为什么用比例而不是像素**：屏幕上的物理尺寸 = 比例 × 逻辑尺寸 × guiScale，
而 guiScale = 物理高度 / 逻辑高度，两者相乘把 guiScale 约掉。所以同一套比例在
任何分辨率、任何 GUI scale 下都落在同一个物理位置。`TitleMenuLayoutTest`
的第一条用例就是把「逻辑值 ×2」拿回 1296×672 的参考帧上对数（GUI scale 2 下
逻辑画布是 648×336），容差 4 物理像素。

文字是唯一的例外：Minecraft 的字体固定 9 逻辑像素高，不随屏幕缩放。所以各处
的 `*_MIN` / `*_MAX` 钳制是按「9 像素的字还放得下」定的，而不是按参考图定的
（`CHIP_HEIGHT_MAX` / `RAIL_HEIGHT_MAX` 的注释写了理由）。

## 2. 画面结构

从下往上：

1. **壁纸**：`BackgroundManager.render()`，没选或还没解码好时退回内置网格
   （`VisualRenderer.gridBackground`）。
2. **整体压暗** `0x33000000`，壁纸再亮白字也读得出来。
3. **底部渐变压暗**：从 45% 高度处 `0x00000000` → 屏幕底部 `0x66000000`
   （`GuiGraphics.fillGradient`，一个 quad）。
4. **账号胶囊**：50% 黑圆角块 + 皮肤头像 + 账号名（苹方 semibold）+「欢迎回来」
   （苹方 light、`0xB3FFFFFF`）。
5. **左下角白色字标**：`assets/wurst/textures/gui/wurstb_plus_logo.png`
   （716×80）。先按 50% 黑、偏移 `+1,+2` 画一遍当投影，再正常画一遍——不然壁纸
   一亮，白字就糊在背景里。
6. **动作条底板**：50% 黑圆角块，4 个按钮是控件，画在它上面。
7. **控件层**（原版 `Screen.render` 画）：4 个动作按钮、右上角齿轮、齿轮菜单的
   3 行（`visible` 开关）。

## 3. 交互

- **动作条**：`单人游戏` / `多人游戏` / `游戏设置` / `退出游戏`，分别走
  `ScreenRegistry.WORLD_SELECTION` / `MULTIPLAYER` / `OPTIONS`、`Minecraft::stop`。
  **Minecraft Realms 的入口去掉了**（用户选择）。
- **右上角齿轮**：点开一个下拉卡片，里面 3 行：`背景`（`BackgroundSelectScreen`）、
  `账号`（`AltManagerScreen`）、`模组`（Forge `ModListScreen`）。菜单用
  `AbstractWidget.visible` 开关，不动态增删控件——渲染途中改控件表会
  `ConcurrentModificationException`。
- 点击空白处**不会**关掉齿轮菜单（要再点一次齿轮）。原因是「点外面关掉」需要在
  `Screen.mouseClicked` 上注入，而那是所有界面共用的方法，代价比收益大。
- 悬停动画沿用 `clickgui2.animation.HoverAnimation`（20 帧）。

## 4. 文案与 i18n

界面文案全部走 `Component.translatable`，键在
`assets/wurst/lang/en_us.json` 与 `zh_cn.json`（**新建**，这个仓库此前没有任何
语言文件）：

| 键 | en_us | zh_cn |
| --- | --- | --- |
| `wurst.title.singleplayer` | Singleplayer | 单人游戏 |
| `wurst.title.multiplayer` | Multiplayer | 多人游戏 |
| `wurst.title.settings` | Settings | 游戏设置 |
| `wurst.title.quit` | Quit | 退出游戏 |
| `wurst.title.accounts` | Accounts | 账号 |
| `wurst.title.mods` | Mods | 模组 |
| `wurst.title.background` | Background | 背景 |
| `wurst.title.menu` | Menu | 菜单 |
| `wurst.title.welcome` | Welcome back | 欢迎回来 |

语言跟随 Minecraft 的语言设置（`Component` 在渲染时按当前 `LanguageManager`
解析）。字体用仓库自带的苹方 TTF（`PingFangFont` 的三个字重），所以中英文都是
矢量字形，不是位图字体放大。

## 5. 与参考图的差异（如实说明）

1. **头像是圆角方块，不是正圆。** Minecraft 的 GUI 只有矩形裁剪
   （`enableScissor`），没有纹理圆形遮罩；皮肤脸部贴图只有 8×8 像素，按列切片
   拼圆的粒度太粗（每片 1 个源像素）。参考图那种正圆需要 Skia 路径或预生成
   遮罩贴图，本版没做。
2. **图标用的是仓库原有的 Wurst 实心图标**（`textures/gui/fdp/`），参考图那套是
   细描边线性图标。额外只生成了一个 `fdp/power.png`（电源符号，参考图的「退出」
   用它，仓库里没有）。
3. **图标素材是 88×88，实际画到约 20–40 物理像素**，双线性缩小时会略软
   （Minecraft 的纹理没有 mipmap）。
4. **左下角没有版本号/Forge 版本那一行**（参考图上没有）。旧版主界面右下角有
   `WurstB+ Plus`、左下角有 `Minecraft x / Forge y`，这次都去掉了。
5. **只有 4 个主按钮**：参考图是 4 个，Realms 与旧的「账号 / 模组」两个小按钮
   合并进了齿轮菜单。
6. **文字尺寸不随屏幕缩放**：GUI scale 越低，文字相对版式越小（这是原版字体的
   限制，见第 1 节）。
7. **`BackgroundSelectScreen` 仍然只有中文**，没有跟着 i18n 一起改（它的字符串
   多且带格式化参数，属于另一件事）。
8. **其他 14 个平台工程还是旧主界面**：`fabric/`、`neoforge/` 与
   `versions/*/gui/title/` 下各有一份 `WurstTitleMenu` / `WurstTitleButton` 的
   拷贝，这次只改了根工程（1.20.1 Forge）。移植时要一起带过去，否则主界面会
   按平台分成两套。

## 6. 验证状态（诚实声明）

- 通过：`gradlew test`（含新增的 `TitleMenuLayoutTest` 10 条用例：参考帧对数、
  4 个按钮等宽、动作条底边距、胶囊与齿轮共用上边距、胶囊宽度随账号名增长并有上
  限、菜单挂在齿轮下方且不压动作条、9 种画布尺寸下各区域互不重叠且不出屏、
  比例缩放、退化画布不产生零/负尺寸、文字垂直居中）。
- **没有在游戏里看过。** 所有绘制调用（`fillRoundedRect`、`fillGradient`、
  `blit`、`PlayerFaceRenderer.draw`、`setColor` 投影）都只做过签名核对与编译验证；
  实际观感、皮肤是否已加载、齿轮菜单的命中范围都还没有实机截图。
