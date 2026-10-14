# 玻璃控件与玻璃 HUD（v1.6 根工程 Forge 1.20.1）

> 范围一：客户端里**原版控件**的底色——按钮、滑块、复选框、列表滚动条。
> 范围二：**HUD 元素的卡片底**——17 个元素 + 通知卡（见第 3 节）。
> 代码在 `net.wurstclient.gui.glass`：`GlassButtons`（控件策略与取帧时机）、
> `GlassPanels`（卡片级入口，带回退）、`GlassRenderer`（抓帧 + 绘制）、
> `GlassStyle`（材质）；注入点是四个混入：
> `mixin/AbstractButtonMixin`、`mixin/AbstractSliderButtonMixin`、`mixin/CheckboxMixin`、
> `mixin/SelectionListMixin`；着色器在 `assets/wurst/shaders/core/glass.*` 与 `blur.*`。
>
> 界面版式本身见 [title-menu.md](title-menu.md)、ClickGUI 见
> [openeepsilon-refactor.md](openeepsilon-refactor.md)——这两处**不在**范围内。
> HUD 实机效果见 [hud-glass-ingame.png](hud-glass-ingame.png)。

## 1. 来源与许可

上游是 [LumaGlass](https://github.com/Sunny5349/LumaGlass)（Minecraft 1.20.1 / Forge
的液态玻璃 UI 模组），版本 **0.2.9**，移植时本地克隆的提交是
`f1d5717`（2026-09-25，*Merge GitHub screenshot updates for 0.2.9*）。
许可 **MIT，Copyright © 2026 Sunny5349**：正文在
`src/main/resources/META-INF/licenses/lumaglass/LumaGlass-MIT.txt`，逐文件来源写在同目录的
`ATTRIBUTION.txt`，条目见 [THIRD-PARTY-NOTICES.md](../THIRD-PARTY-NOTICES.md) 第一节。

| 本工程 | 上游 | 关系 |
| --- | --- | --- |
| `assets/wurst/shaders/core/glass.{json,vsh,fsh}` | `assets/lumaglass/shaders/core/glass.*` | `.vsh` / `.fsh` **逐字节相同**；`.json` 只改命名空间 |
| `assets/wurst/shaders/core/blur.{json,vsh,fsh}` | `assets/lumaglass/shaders/core/blur.*` | 同上 |
| `gui/glass/GlassRenderer.java` | `client/GlassRenderer.java` + `api/client/GlassCanvas.java` / `GlassFrame.java` 的取帧逻辑 | 改写 |
| `gui/glass/GlassStyle.java` | `api/client/GlassStyle.java` | 去掉泛光颜色分量 |
| `gui/glass/GlassButtons.java` | `client/theme/VanillaGlass.java`（自动主题与滚动条材质） | 合并改写 |
| `mixin/AbstractButtonMixin.java` | `mixin/client/ButtonMixin.java` | 只改控件底色那一段 |
| `mixin/AbstractSliderButtonMixin.java` | `mixin/client/SliderMixin.java` | 同上（旧的扁平画法保留为回退路径） |
| `mixin/CheckboxMixin.java` | `mixin/client/CheckboxMixin.java` | 整个混入 |
| `mixin/SelectionListMixin.java` | `mixin/client/SelectionListMixin.java` | **只取滚动条那一个分支** |

> 本地克隆在 `LumaGlass/`，与 `InfiniteGUI-Minecraft-DLL/`、`Lunar-Local-Cosmetics-Unlocker/`
> 一样是**只读参考、不参与构建**（`.gitignore` 最后一段）。客户端**不依赖** LumaGlass mod，
> 产物里也没有它的 jar。

**为什么不像上游文档那样"加一条 Maven 依赖"**：上游没有公共 Maven 仓库，只能本地
`publish`；而且真正想要的那条路（把原版控件自动换成玻璃）在**内部包**里
（`dev.lumaglass.client` / `dev.lumaglass.mixin`，上游明确写了"不属于稳定 API"），公开
API 是给"自己画面板"用的。要在自己的客户端里用它，只有移植这一条路。

## 2. 它替换了什么

四个注入点各自覆盖一类控件；共同点是**只换底色，不动布局、命中判定与输入**：

| 控件 | 注入点 | 画法 |
| --- | --- | --- |
| 原版按钮 | `AbstractButtonMixin`（`renderWidget` HEAD，取消原方法） | 一块玻璃；悬停按动画进度加底色与高光 |
| 滑块 | `AbstractSliderButtonMixin`（同上） | 一条圆角 10 的玻璃当滑轨 + 一块 8×高−2、圆角 4 的玻璃把手，把手位置按 `value` |
| 复选框 | `CheckboxMixin`（同上） | 20 宽、圆角 7 的玻璃方块；勾与标签按上游的画法补 |
| 列表滚动条 | `SelectionListMixin`（`@Redirect` `GuiGraphics.fill`） | 轨道（内缩 2px）与滑块各换成细玻璃条 |

**覆盖范围由原版类层次决定**，与上游一致：

- 覆写了 `renderWidget` 的控件（`ImageButton`、客户端自绘的 `WurstTitleButton`、ClickGUI
  各组件）**不在**按钮那条路径上；
- 列表的**底色、顶底边与选中行**没有移植（上游那部分还接管 `renderBackground` /
  `renderTopAndBottom` 与 `renderSelection`），只做了滚动条；
- 其它 66 个工程没有移植，那边的控件仍是扁平样式。

**一处行为变化（如实说明）**：旧的扁平样式会按文案把"删除 / 移除 / 登出"这类按钮涂成
红色（`isDangerous`）。LumaGlass 的材质用中性底色，只有泛光带色相，而泛光在上游默认
关闭、本移植里恒为 0，所以**玻璃这一侧没有等价物**；该判断只保留在回退路径上。

## 3. HUD 卡片（本轮）

一条口径：**HUD 元素的"底板"是玻璃，内容保持原样**。

| 位置 | 改法 |
| --- | --- |
| 文本类元素（`TextHudElement` 基类，覆盖 health / cps / fps / coords / tps / speed / clock / memory / reach_display / compass / day_counter / game_mode / weather / player_name / play_time / player_counter / potion_counter …） | 原来 `graphics.fill(..., SURFACE_50)`（直角、50% 黑）→ `GlassPanels.draw(...)`，圆角 4 |
| `PotionHudElement` | 同上（它自己写了一份底，没走基类） |
| `InventoryHudElement` | 卡片底 + 描边 → 玻璃；槽位、左侧强调条保持扁平 |
| `KeystrokesHudElement` | 每个键位块 → 玻璃；**按下反馈**改成玻璃上叠一层 35% 主题色薄涂（原来靠整块底色从 `SURFACE_68` 混到 `ACCENT`，玻璃底色固定后那条路失效） |
| `MinimapHudElement` | 圆盘 → 玻璃；**手画的多层投影删掉**，改用着色器自带的柔和投影；地图内框保持不透明——地图贴图本身不透明，玻璃放它后面看不见 |
| `MusicIslandHudElement` | 岛体 → 玻璃 |
| `MusicLyricsHudElement` | 卡片 → 玻璃，透明度跟 `visibility` 一起淡 |
| `ScoreboardHudElement` / `TargetHudElement` | 原来走 `RiseFrostedGlass`，改走同一套玻璃 |
| 通知卡（`compose/ComposeNotifications`） | 卡片底 → 玻璃；**两道流动渐变描边保留**，淡出时整块跟着淡 |

**保留扁平的内容**（这些不是"控件底板"）：血条与进度条、物品槽、地图贴图、播放按钮、点击涟漪，以及 `hacklist` / `logo`（纯文字与贴图元素，本来就没有底板）。

**取帧时机**：`HudManager.onRenderGUI` 开头 `GlassButtons.beginPass()`；抓帧本身是懒的——第一个真正要画玻璃的元素才去抓，那时世界与先画的 HUD 都已在帧缓冲里，玻璃透出的正是它们。原来是"只在记分板或 Target 启用时抓一次"，玻璃化后改成按趟次懒抓，任何元素都能玻璃化。HUD 编辑器（`HudEditorScreen`）同样在渲染开头重开一趟，所以编辑器里的预览与实际 HUD 一致。

**删掉的东西**：`hud2/render/RiseFrostedGlass.java`（235 行的自研磨砂）在全部调用点搬走之后**没有任何使用者**了，已删除；`TwilightSkia.softShadow` 注释里提到它的那句一并改掉。**没有删掉任何视觉功能**——它的柔和投影由玻璃着色器接管。
## 4. 管线

```
ScreenEvent.Render.Pre ──► GlassButtons.beginPass()        （每趟屏幕渲染开头）
                              │
第一个控件 renderWidget ──────┼─► GlassRenderer.capture()   （整趟只抓一次）
                              │     主 framebuffer ──blit──► scene 纹理
                              │     scene ──逐级降采样+高斯──► blurred
                              │
每个控件 renderWidget ────────┴─► GlassRenderer.panel()     （采样 scene/blurred 画一块）
                                    圆角 + 边缘折射 + 边缘高光 + 柔和投影
文字（renderString / 勾与标签）在面板之后画
```

- **一趟只抓一次背景**：同一趟里所有控件共用同一张快照，所以控件之间不会互相采样
  （上游 `GlassCanvas` 的同一约定）。
- 抓帧点在"屏幕背景已画完、第一个控件要画"的时刻，因此玻璃透出的是该界面自己的背景。
- 触发重置的还有 `TickEvent.RenderTickEvent` 的 START（兜底：万一某个界面没走
  `Screen.render`，也不会一直用上一帧的背景）。

## 5. 材质参数（与上游默认值一致）

| 参数 | 值 | 来源 |
| --- | ---: | --- |
| 模糊半径 | 4.8 | 上游 `GlassConfig.blur` 默认（界面里显示为 15%） |
| 折射 | 24 | `GlassConfig.refraction` 默认 |
| 磨砂 `frost` | 1 | `GlassConfig.frost` 默认 |
| 底色 `tint` | 0 | `GlassConfig.tint` 默认 |
| 高光 `highlight` | 1 | `VanillaGlass.style()` |
| 悬停追加 | 底色 +0.13、高光 +0.7 | `VanillaGlass.style(focused)` |
| 按钮圆角 | `min(10, 高度/2)` | `ButtonMixin` |
| 滑块 | 滑轨圆角 10；把手宽 8 / 圆角 4 / 上下各留 1 | `SliderMixin` |
| 复选框 | 20 宽、圆角 7 | `CheckboxMixin` |
| 滚动条 | 圆角 `min(宽,高)/2`、折射 0.6、磨砂 0.85；轨道底色 0.08 / 不透明度 0.22，滑块底色 0.38 / 0.95（禁用 0.4） | `VanillaGlass.scrollbar` |
| 未激活按钮透明度 | `alpha × 0.5` | `ButtonMixin` |
| 泛光 | 0（关） | `GlassConfig.glowEnabled` 默认 false |

## 6. 与上游的差异（逐条）

1. **泛光恒为 0**。上游由配置写入 `GlowStrength`，而 shader JSON 里它的默认值是 **1**，
   所以这里必须显式写 0——否则所有控件都会亮起来。
2. **不画贴图面板**（`Artwork`）：控件不需要把原版贴图玻璃化，`Artwork` 采样器仍按上游
   "无贴图"的分支绑到背景纹理上，`UseArtwork` 用 JSON 默认值 0。
3. **没有 `GlassCanvas` / `GlassFrame` 对象**：那两个类是为了让调用方 `try-with-resources`
   管理生命周期；这里只有少数几个持有者（四个注入点），改成按渲染趟次的布尔状态。
4. **悬停按进度插值**，不是上游的布尔跳变：本工程的控件本来有 20 帧悬停动画，直接跳变
   会退步。滚动条不参与悬停插值（它没有悬停态）。
5. **`pixelScale` 乘上 pose 缩放**（钳在 `[0.25, 4]`）。上游只按窗口 GUI scale 算，而本工程的
   `ScreenMixin` 在界面打开时给整个界面压一个等比缩放（`VisualScreenMotion`），不乘的话
   缩放动画期间折射量会偏。
6. **回退是"原来的画法"**，不是上游那个半透明矩形：按钮与滑块回退到旧的扁平样式，
   复选框回退到原版贴图，滚动条回退到原版 `fill`。回退是**静默且永久**的
   （`GlassButtons.failed`），只往 stderr 记一行。
7. **列表只移植了滚动条**：上游 `SelectionListMixin` 还接管列表底色（`renderBackground` /
   `renderTopAndBottom` 两个字段）、给列表铺一块玻璃、并把选中行也画成玻璃；本移植只做了
   滚动条那两个 `fill`，列表其余部分保持原版。
8. **保留了本工程的按下反馈**（按钮按下时面板内缩 1px）。

## 7. 失败与回退

玻璃这条路上任何一步出错都会退回原样，而不是把异常抛给界面：

| 情形 | 结果 |
| --- | --- |
| 着色器没装上（注册失败 / 资源重载中 / 驱动编译不过） | `GlassRenderer.available()` 为假 → 直接画原样 |
| uniform 名字对不上（`safeGetUniform` 返回 null） | 抛 NPE → 被 `GlassButtons` 捕获 → **永久**回退 |
| 任何 `RuntimeException` / `LinkageError` | 同上 |

这条回退路径不是"顺手加的"：这几个混入覆盖**所有**原版控件，异常一旦逃出去，每个带控件的
界面都会崩。名字对账交给单元测试（见下），运行期只做兜底。

## 8. 验证状态

**已做**：

- `gradlew.bat compileJava --offline`：通过
- `gradlew.bat test`：玻璃这一层 **24 项全通过**（下面三个新类）。**注意**：本轮全量跑（1396 项）
  有 **1 项失败**，是 `background` 里另一条正在改的粒子线（`WeParticleSheetTest`），与本次改动
  无关——本移植只碰了 `gui/glass/`、四个混入、`wurst.mixins.json` 与文档
- 新增 3 个测试类 / **24 项**：
  - `GlassButtonsTest`（13）：圆角上限、材质默认值与悬停插值、滑块把手的范围与越界钳制、
    滚动条材质、透明度减半、`pixelScale` 的钳制与非法输入回落
  - `GlassStyleTest`（5）：六个分量的取值范围、非有限值、值语义
  - `GlassShaderResourcesTest`（6）：**从 `GlassRenderer.java` 源码里解析出所有
    `safeGetUniform(...)` / `setSampler(...)` 名字，逐个核对 shader JSON 是否声明**，
    再反过来核对 JSON 里声明的 uniform 是否真的写在 GLSL 里；并检查命名空间是 `wurst:`
- 着色器 `.vsh` / `.fsh` 与上游**逐字节相同**（`Get-FileHash` 对比过），改动只在 JSON 的
  命名空间
- **实机（dev 客户端 `runClient`）**：客户端带这四个混入正常启动，日志无
  `Mixin apply failed` / `InvalidInjectionException`（`SelectionListMixin` 那个
  `require = 3` 的重定向确实命中了）；**按钮的玻璃已在实机截图里看到**（选项界面整屏按钮、
  视频设置界面的「完成」都是圆角玻璃，透出并模糊了背景，带边缘高光）

**未做（明确列出）**：

| # | 项 | 缺什么 |
| --- | --- | --- |
| 1 | **滑块 / 复选框 / 滚动条的实机外观** | 这三样还没有截图确认。混入能应用、代码路径与按钮同源，但"看起来对不对"没人看过 |
| 2 | 性能 | 每帧一次全屏 blit + 逐级降采样 + 两趟 33 抽头高斯没有任何帧时间测量；界面控件多时应该无感，**没量过** |
| 3 | 与光影共存 | Oculus / Embeddium 下 `minecraft:main` 的含义与采样时机未验证 |
| 4 | 其它平台 | 只按 Windows 的 GL 路径写的，未在 macOS / Linux 跑过 |
| 5 | 资源重载 | F3+T 之后"先关玻璃再装新 shader"的时序只在代码上成立，没有实机验证 |
| 6 | 列表底色 / 选中行 | 上游有、本移植没做（见 §5 第 7 条），所以带列表的界面只统一了滚动条 |
| 7 | 其它 66 个工程 | 本移植只在根工程（Forge 1.20.1）。`fabric/`、`neoforge/` 与 `versions/*/` 里的对应混入仍是扁平样式 |

## 9. 后续可做（未做）

- **把危险按钮的红色找回来**：上游 API 支持每块面板独立的泛光颜色
  （`GlassStyle.withGlowColor`），把强度开起来给"删除 / 登出"这类按钮加红色边缘泛光即可；
  本移植为了让外观严格等于上游默认值，把泛光整个去掉了。
- **加设置项**：模糊 / 折射 / 磨砂 / 泛光目前是编译期常量，没有开关；上游是一份
  `lumaglass-client.toml`。
- **列表底色与选中行**：见 §5 第 7 条。
- **`ImageButton` / 标签页 / 输入框**：上游另有对应混入（`EditBoxMixin` 等），本次未做。
- **移植到其余 66 个工程**：见验证状态第 7 项。
