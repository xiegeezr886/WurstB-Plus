# OpenOpal 视觉层移植 · ESP 第一批

参考项目 [`ZSZ7/OpenOpal`](https://github.com/ZSZ7/OpenOpal)（`main`），本地只读检出
`D:\WurstB\_op_ref`。**GPL-3.0**，与本工程同许可，源码可复用。

本文件只记录**决定与证据**；布局数学在 `util/esp/`，绘制在 `render/skia/EspSkia`。

---

## 0. 先说差异：这是一次跨版本 + 跨加载器 + 跨映射的移植

| | OpenOpal | 本工程（根目录） |
| --- | --- | --- |
| Minecraft | 1.21.10 | 1.20.1 |
| 加载器 | Fabric（`splitEnvironmentSourceSets`，代码在 `src/client`） | Forge 47.4.10 |
| 映射 | Yarn | 官方 Mojmap |
| Java | 21 | 17 |
| 渲染后端 | **NanoVG**（`NVGRenderer`，`nvgShapeAntiAlias`、`BLUR_PAINT`） | 原版 `GuiGraphics` + **Skia/Skiko region**（v1.6 新增） |
| 模块基类 | `Module` + `Property` 体系 | `Hack` + `Setting` 体系 |

所以可搬的是**几何、排版、元素模型与描边组合**，不可搬的是模块框架与 NanoVG 调用。
这正是 `PROJECT_INDEX.md` 里 v1.6 的 `render.skia` 存在的原因：本工程已经有等价能力。

---

## 1. 后端决定

OpenOpal 的 2D ESP 全部靠 NanoVG 在**屏幕空间**画：把碰撞箱 8 个角投影成屏幕矩形，
再用 `rectOutline` / `rectOutlineStroke` / 圆角矩形 / 矢量文字画上去。

本工程的 2D ESP（`PlayerEspHack` 的 `RenderMode.TWO_D`）原本已经在屏幕空间工作，
但用的是 `RenderUtils.fill2D` / `drawBorder2D`——只有**轴对齐实心四边形**，
没有圆角、没有描边、没有文字，于是参考那种「细线外包一圈黑边」和铭牌条都做不出来。

**选择**：走既有的 `SkiaRegionRenderer` 区域管线，而不是把 NanoVG 原生库引进来。

- 复用而不是新增原生依赖：Skiko 已随 v1.6 打包（`assets/wurst/skiko/`），NanoVG 要另带
  `nanovg.dll` + LWJGL 绑定，且与 Skia 功能重叠。
- 与上次 Twilight Echo 端口的做法一致（`TwilightSkia` + 原版 `blit` 兜底）。
- 原生库缺失时**逐像素**退回原来的原版路径，行为与打这个补丁之前完全相同。

---

## 2. 搬了什么

| 参考位置 | 本工程 | 说明 |
| --- | --- | --- |
| `visual/esp/NameTagElement.java`、`NameTagIcon.java`、`NameTagIconPosition.java` | `util/esp/EspNameTagElement.java` | 三个类型合并成一个文件，用嵌套 record/enum |
| `visual/esp/ESPModule.java#calculateStartingPosition` + `renderNameTagElements` | `util/esp/EspNameTagLayout.java` | 只保留几何，字形宽度由 `GlyphMeasurer` 注入 |
| `visual/esp/ESPModule.java#renderNameTag` | `util/esp/EspNameTagPolicy.java` | 拆成 `Options`（设置）+ `State`（实体事实）→ 有序元素列表 |
| `utility/render/ESPUtility.java#ENCHANTMENT_NAMES` | `util/esp/EspEnchantNames.java` | 改按**注册名路径字符串**作键，去掉 `RegistryKey` 依赖 |
| `ESPModule.renderEquipment()` 的坐标式 | `util/esp/EspEquipmentLayout.java` | 槽位收集改成 `getArmorSlots()`（本就按头→胸→腿→靴有序），几何原式照抄 |
| `renderer/NVGRenderer.java#rectOutline` / `rectOutlineStroke` / `rectStroke` | `render/skia/EspSkia.java` | 逐条移植的描边组合 |
| `ESPModule.renderFullBox()` 的 `boxStroke` 分支 | `PlayerEspHack.renderScreenBox()` | `(0.5, 1.5, color, 0xff000000)` 参数原样 |

几何口径与参考一致的关键常量（`EspNameTagLayout`）：
字号 `5`、元素间隔 `5`、背景内边距 `2`、圆角 `2`、基线偏移 `4.5`。

> **那处 4.5 的来历**：参考里背景是 `position.y - 2 - 4.5`，正文画在 `position.y`。
> 看起来像多减了一次，但 `nvgText` 的 y 是**基线**而非顶边，多出来的 4.5 正好
> 抵消字形上伸部分、让文字在背景里竖直居中。Skia 的 `drawString` 同样取基线，
> 所以 `EspSkia.textBaseline()` 不做「顶边→基线」换算，偏移照抄。

## 3. 故意不搬的

1. **NanoVG 全套**：本工程走 Skia（见 §1）。
2. **`materialicons-regular` 图标字体**：参考的力量/潜行/隐身/举盾/红心都是图标字体
   字形（`\uefe4` 等）。本工程没有该字体资源，为 5 个图标引入一套字体 + 授权说明
   不划算；`TwilightSkia` 移植参考界面时对同类问题给出的结论也是「图标字体无法移植」。
   这里改用**中文字形**（`EspIndicatorGlyphs`：力/潜/隐/盾/血/吸）——铭牌正文本来就由
   苹方绘制，常用汉字必然存在，既不加资源也无缺字风险。
3. **`BLUR_PAINT` 背景模糊**：参考铭牌背景是「背景模糊 + 50% 黑」。CPU 光栅画布拿不到
   游戏帧缓冲，做不了真正的 backdrop blur，只保留那层半透明黑底。
4. **`Strength` 指示**：读的是 OpenPal 自己的 `LocalDataWatch.getStrengthedPlayerList()`
   （按玩家名记录力量药水状态），本工程没有任何等价数据源。为一个恒为 `false` 的分支
   留字段属于凭空造抽象，整项略去。
5. **`TargetList` / `TargetProperty` 目标系统**：参考全模块共用一个目标收集器。本工程
   的 `PlayerEsp` 已经有自己的一套（距离/FOV/实体过滤器 + `FakePlayerEntity`），
   换过去要重做过滤链，收益不成比例。
6. **`ESPUtility.getEntityPositionsOn2D` 的投影**：参考自己重建了一整个 `MatrixStack`
   （FOV + 受伤倾斜 + 视角摇晃 + 相机旋转）。本工程 `WorldToScreen.project()` 用的就是
   当前帧的 view/projection 矩阵，结果等价且不需要 `GameRendererAccessor` 那组
   accessor mixin，故沿用。

## 4. 与参考不同的三处（有意为之）

1. **血条颜色**：参考 `renderHealthBar` 把颜色写死成 `0xff00ff00`。本工程保留自己的
   `Color mode`（Distance / Health / Custom + 好友色）语义，血条仍用实体色——
   否则 `Color mode` 这个既有设置对血条就失效了。几何（碰撞箱左侧、自下而上填充）不变。
2. **距离元素不带图标**：参考是「数字 + 距离图标」。既然图标字体不搬，这里直接写成
   `12m`，比一个没有图标位的裸数字清楚。
3. **附魔短名排成一行**：参考给每个附魔各画一行、叠在图标右下角。图标缩放后只有
   10.4px 宽，那样会和图标本身以及右边的相邻图标糊在一起，所以这里拼成一行画在
   图标正上方。`EspNameTagPolicy.formatHealth` 同样的理由不用跟随区域设置的
   `DecimalFormat`（见该类的注释）。

> 顺带一处**不**照样搬的地方：参考的血量图标字形写成
> `new NameTagIcon(Formatting.RED + "\uE87D", RIGHT)`，把 `§c` 混进了字形串里。
> 那是给 NanoVG 画的，`§c` 会被当成两个普通字符画出来（颜色其实由元素本身的
> `-1` 决定）。本工程只取字形、颜色走元素颜色，不去复现这个瑕疵。

## 5. 新增设置（只加，不改）

`PlayerEspHack` 新增 8 项，全部只在 2D 模式下可见；**没有任何既有设置被改名、
改默认值或改取值范围**：

| 设置 | 默认 | 说明 |
| --- | --- | --- |
| `Box stroke` | 开 | 参考式「彩色细线 + 深色描边」，关掉回到原来的单像素边框 |
| `Rounded box` | 关 | 参考的框是直角，故默认关 |
| `Corner radius` | 1.5 | 仅在 `Rounded box` 打开时可见 |
| `Name tags` | 开 | 铭牌条总开关 |
| `Tag distance` | 开 | 距离元素 |
| `Tag health` | 开 | 血量元素（吸收值自动追加，与参考一致） |
| `Status indicators` | 开 | 潜行 / 隐身 / 举盾三个指示 |
| `Tag equipment` | 开 | 护甲 + 主手图标与附魔短名（与铭牌条一起出现，和参考的 `Equipment` 元素同层） |

## 6. 性能：区域不是整屏

`SkiaRegionRenderer` 每帧要 `peekPixels` + `glTexSubImage2D` 把画布传回 GPU，
所以区域开成整屏的话，1080p 每帧要上传约 8 MB。`PlayerEspHack` 因此先**预排版**
这一帧所有方框与铭牌条，取并集（再留出左侧血条、下方护甲条、上方铭牌与描边的余量）
作为区域；铭牌条按真实排版结果参与并集，长名字不会被裁掉。

## 7. 验证状态（诚实声明）

- `compileJava` / `compileTestJava` / `test` 通过：148 个测试类、905 个测试、0 失败，
  其中 ESP 纯逻辑层新增 4 个测试类共 35 个测试。
- **绘制图元本身有像素级验证**：`SkiaPrimitiveSemanticsTest` 直接开一块 Skia 光栅
  画布、画完读回像素，钉下了本工程对 Skia 的几条假设：
  - `Rect` 的 right/bottom 是开区间（`EspSkia.fillRect` 的宽度换算依赖它）；
  - `PaintMode.STROKE` 只画一圈空心边（`outlineRectCased` 的深色描边依赖它）；
  - `RRect` 圆角会切角、半径过大会被收进一半（`Rounded box` 依赖它）；
  - **`drawString` 的 y 是基线而不是顶边**——这是整套铭牌排版的立足点：参考把
    背景放在 `y - 2 - 4.5`、正文放在 `y`，本工程照抄了这个偏移。如果 Skia 的 y
    其实是顶边，整套铭牌会整体下移约一个字高，而编译期完全看不出。
    这一条现在有实测像素证据。
  Skiko 原生库加载不了的环境下这个类会整个跳过，不会变红。
- **从未在游戏里看过**：字号 5 在实机上是否偏小、描边 0.5px 在低 GUI 缩放下是否
  可见、物品图标缩放 0.65 后与铭牌条的相对高度是否好看，都没有实机确认。
- `EspSkia` 的兜底路径（`begin` 返回 false）只做了逻辑核对。可以确认的是它不会
  「卡死」：`SkiaRegionRenderer.beginRegion()` 只在原生库不可用时返回 null，而那
  条分支在 `regionDrawing = true` **之前**，所以守卫不会把后续帧一直挡掉。
- 装备元素的**顺序**假设（主手最左）是从参考原式推出来的，没有实机比对过。
- `EspSkia` 与 `PlayerEspHack` 里「画什么、画在哪」这层没有自动测试（需要
  GL 上下文与游戏内实体），只能靠人工分栏；有测试的是它下面那层图元语义。

### 7.1 兜底路径原先到不了（已修）

上面那条「兜底路径只做了逻辑核对」的写法过于宽松，复核后发现**它不是到不了画面，
而是根本执行不到**，两个独立原因：

1. **排版阶段就先把 Skia 摸了。** `onRenderGUI` 原先无条件用
   `EspSkia.textWidth` 量铭牌宽度，而这发生在 `EspSkia.begin` **之前**。
   `EspSkia.textWidth` → `font()` → `SkiaFontManager.semibold()` →
   `FontMgr.Companion.getDefault()`，`SkiaFontManager.load()` 只接 `IOException`，
   原生库不可用时抛的是 `UnsatisfiedLinkError`。异常穿出 `onRenderGUI`
   （渲染事件），`renderScreenBoxesVanilla` 一次都没被调用过。
2. **原生库探测本身不校验平台。** `SkikoNatives.ensure()` 只做「解压 DLL +
   设置 `skiko.library.path`」，打包的是 `skiko-windows-x64.dll`，
   所以在 Linux/macOS 上它照样返回 `true`；真正的加载失败要等到第一次触碰
   Skia 类才发生。`SkiaRegionRenderer.beginRegion` 的注释写「native 初始化失败时
   抛 `IllegalStateException`」，但 `EspSkia.begin` 当时没有任何 try/catch。

修法：

- `EspSkia.isUsable()`：真的去摸一次原生入口（`FontMgr`）并把结果缓存，
  异常（含 `UnsatisfiedLinkError` / `ExceptionInInitializerError`）收在方法内；
  同时把「这一帧已有别的界面开着区域」也算作不可用。
- `EspSkia.begin` 整个包进 try/catch，失败即返回 false，兜底得以执行。
  `regionDrawing` 是在 `beginRegion` 快结束时才置位的，抛在它之前，所以标志不会卡住。
- `onRenderGUI` 改成**先定路径再排版**：标尺与实际绘制路径同源
  （Skia 用 `EspSkia.textWidth`，兜底用原版字体宽度 ×
  `EspNameTagLayout.vanillaScale`）。
- 兜底路径补上铭牌条：`renderNameTagsVanilla`，与 Skia 路径共用**同一份**
  `EspNameTagLayout.Layout`，只是换成 `fill2D` + `drawString`。原版没有按字号绘制
  的入口，整串用 pose 缩放；基线/顶边口径换算见
  `EspNameTagLayout.vanillaTextTop`（只依赖公开的 `lineHeight`，不假设 ascent=7）。

由此新增的纯函数 `vanillaScale` / `vanillaTextTop` 有 4 个单测。
**仍未实机验证**：兜底路径的实际观感（原版字体缩放后的字形质量、与 Skia 路径的
并排差异）没有看过；本次只证明了「这条路现在走得到」，没有证明「走得好看」。

