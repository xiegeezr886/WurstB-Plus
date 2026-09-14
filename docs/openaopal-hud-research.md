# OpenOpal HUD/Overlay 架构研究（供 `hud2` 移植）

参考项目 [`ZSZ7/OpenOpal`](https://github.com/ZSZ7/OpenOpal)（1.21.10 / Fabric / Yarn /
Java 21），本地只读检出 `D:\WurstB\_op_ref`。**GPL-3.0**，与本工程同许可。

本文件只记录**结论与证据**。凡"未验证"均显式标注，不做推测。所有行号来自本次实际打开的文件，
版本为当前工作区快照。姊妹文档：`docs/openaopal-port.md`（ESP 第一批，已落地）。

阅读范围（全部实际打开）：

- 参考：`overlay/{IOverlayElement,OverlayModule}`、`overlay/impl/client/{ClientElements,ClientElementSettings}`、
  `overlay/impl/targetinfo/{TargetInfoElement,TargetInfoSettings}`、
  `overlay/impl/modulelist/{ModuleElement,ToggledModulesElement,ToggledSettings}`、
  `overlay/impl/notifications/{NotificationsElement,NotificationSettings}`、
  `overlay/impl/dynamicisland/{DynamicIslandElement,IslandTrigger,CustomIslandTrigger,preset/DefaultIsland}`、
  `helper/impl/render/{ScreenPositionManager,ScaleProperty}`、`property/impl/ScreenPositionProperty`、
  `property/Property`、`utility/render/{ScreenPosition,LayoutHelper,Scroller,SidebarEntry,ClientTheme}`、
  `utility/render/animation/{Animation,Easing}`、`utility/data/SaveUtility`、`utility/misc/HoverUtility`、
  `utility/misc/time/Stopwatch`、`client/notification/{Notification,NotificationManager}`、
  `event/impl/render/{RenderScreenEvent,RenderBloomEvent}`、`mixin/{InGameHudMixin,ChatScreenMixin}`、
  `renderer/{NVGRenderer,MinecraftRenderer}`、`renderer/shader/ShaderFramebuffer`、
  `client/OpalClient`、`client/feature/module/Module`、`client/music/MusicService`（仅触发线程相关部分）。
- 目标：`hud2/`（共 44 个文件，本次读了其中 14 个，其余元素文件只按名字/注册点引用）——
  `{HudElement,HudManager,HudLayout,HudEditorScreen,ClientMetricsManager,
  HudNotification,HudNotificationRenderer,render/RiseFrostedGlass,render/RiseHudFont}`、
  `elements/{TargetHudElement,KeystrokesHudElement,CoordsHudElement,TextHudElement,PotionHudElement,FpsHudElement,PingHudElement}`、
  `hud/IngameHUD`、`render/skia/{SkiaRegionRenderer,SkiaFontManager}`、`twilight/TwilightSkia`、
  `events/GUIRenderListener`、`WurstForgeInitializer`、`WurstClient`、`util/ScreenRegistry`、
  `util/esp/{EspNameTagLayout,EspNameTagElement,EspEquipmentLayout,EspEnchantNames}`、`hacks/PlayerEspHack`、
  `clickgui2/{RiseAnimation}`、`clickgui2/supersoft/{UiTween,UiMotion}`、`compose/AnimFloat`、
  `util/render/PostEffectQueue`、`mixin/GameRendererMixin`。

---

## 0. 先说结论

| 结论 | 说明 |
| --- | --- |
| 元素模型**可移植且值得移植** | `IOverlayElement` 只有 7 个方法，语义干净；`hud2` 的 `HudElement` 已经覆盖其中 6 个的等价物 |
| 定位模型**不照抄** | 参考是"相对坐标 0..1 + 左/右阈值"，hud2 是"锚点(LEFT/CENTER/RIGHT × TOP/CENTER/BOTTOM) + 整数偏移 + scale"，后者更强；参考能补的是**相对坐标↔像素的纯函数**与**边缘/中线吸附** |
| bloom 第二遍**不能照抄** | 参考的实现会让元素 `render()` 在同一帧跑两次，产生已证实的重复入队缺陷（§3-D5）；`hud2` 也没有 GUI 后处理通道（§6-B1） |
| 动画层**大部分已存在** | `UiTween`（钳制、零时长防护）与 `RiseAnimation` 已是同类物；缺的是 30 条缓动曲线 + 统一口径 |
| 最大工作量是 **TargetInfoElement** | 头像 + 装备栏 + 附魔短名 + 双血条；其中装备/附魔在 `PlayerEspHack` 已有先例，头像在 1.20.1 有 `graphics.blit` 方案 |

---

## 1. Overlay 元素模型

### 1.1 `IOverlayElement` 契约

`_op_ref/src/client/java/wtf/opal/client/feature/module/impl/visual/overlay/IOverlayElement.java`（全文 26 行）：

| 行 | 成员 | 含义 / 谁在何时调用 |
| --- | --- | --- |
| 7 | `void render(DrawContext, float delta, boolean isBloom)` | 唯一抽象方法。主渲染通道每帧调用一次；bloom 通道对 `isBloom()==true` 的元素再调用一次（`OverlayModule.java:118-134`）。`delta` = tickDelta |
| 9-10 | `default void renderBlur(DrawContext, float delta)` | **死钩子**：全仓库无任何调用点（`grep renderBlur` 只命中定义与 `ToggledModulesElement.java:62-65` 的空覆写，函数体被注释掉了） |
| 12-13 | `default void onResize()` | `OverlayModule.java:136-139` 在 `ResolutionChangeEvent` 时调用 |
| 15-16 | `default void tick()` | `OverlayModule.java:141-148` 在 `PostGameTickEvent` 时调用（**每游戏 tick**，不是每帧） |
| 18-19 | `default void onDisable()` | `OverlayModule.java:92-95` 在模块 `onDisable()` 时调用 |
| 21-23 | `default boolean isActive()` | 默认 true。`render`/`tick` 前都会被查一次（`OverlayModule.java:121,130,144`） |
| 25 | `boolean isBloom()` | 唯一第二个抽象方法：是否参与 bloom 通道 |

注意 `isActive()` 在 `tick()` 里也被检查（`OverlayModule.java:144`），所以"元素被关掉后不再 tick"是框架保证的；
但 `onResize()` / `onDisable()` **不查** `isActive()`（`OverlayModule.java:138,94`）。

### 1.2 `OverlayModule` 如何注册与驱动

`OverlayModule.java`：

- 元素容器：`private final List<IOverlayElement> elements = new ArrayList<>()`（47 行）。
- 注册：`register(T)`（87-90 行）只做 `elements.add`，返回同一实例；构造函数里依次注册
  `TargetInfoElement`(78)、`ToggledModulesElement`(79)、`ClientElements`(80)、`NotificationsElement`(82)、
  `DynamicIslandElement`(84)。**顺序即渲染顺序**（`elements` 是 ArrayList，渲染时顺序遍历）。
- 设置注册：每个元素把设置塞进模块自己的 property 树 —— `TargetInfoSettings.java:19`、
  `ClientElementSettings.java:24`、`ToggledSettings.java:38-43`、`NotificationSettings.java:15`，
  都走 `module.addProperties(new GroupProperty(...))`。也就是说**元素设置是宿主模块的设置**，
  元素自己不是 `Property` 的持有者。
- 主渲染：`onRenderScreen`（`@Subscribe(priority = -20)`，118-125 行）遍历 `elements`，
  `isActive()` 为真则 `element.render(event.drawContext(), event.tickDelta(), false)`。
- bloom 渲染：`onBloomRender`（同样 `priority = -20`，127-134 行）条件为
  `element.isActive() && element.isBloom()`，第三个参数传 `true`。
- 生命周期：`onEnable`（97-103）/ `onDisable`（92-95）/ `PostClientInitializationEvent`（105-109）
  只额外做两件事：`toggledModules.initialize()` 与 `targetInfo.initialize()`（后者的函数体是空的，
  `TargetInfoElement.java:78-79`）。`initialize()` 只在模块仓库建好之后才能跑（`OpalClient.java:86-169`
  建仓库 → 195-196 派发 `PostClientInitializationEvent`），这也是 `isPostInitialization()` 判断存在的原因（99 行）。
- `onPropertyUpdate`（111-116 行）：任何属性变化都把 `toggledModules` 标记为"需要重排"。

### 1.3 事件源与"第二遍渲染"的完整链路

`RenderScreenEvent` / `RenderBloomEvent` 都是 record，各 6 行：

```java
// RenderScreenEvent.java:5
public record RenderScreenEvent(DrawContext drawContext, float tickDelta, double mouseX, double mouseY) {}
// RenderBloomEvent.java:5
public record RenderBloomEvent(DrawContext drawContext, float tickDelta) {}
```

派发点全在 `mixin/InGameHudMixin.java`（`@Inject(method="render", at=TAIL)`，66-70 行）：

1. 79 行 `tickDelta = tickCounter.getTickProgress(false)`（1.21.10 的 `RenderTickCounter`）。
2. 81 行 `applyPostProcessing(context, tickDelta)`：
   - 130 行 `NVGTextRenderer.blockTextRendering = true`；
   - 131 行 `ShaderFramebuffer.applyBlurToFullScreen()`；
   - 135 行 若 `PostProcessingModule.isEnabled() && isBloom()`，开一个 `RenderPass`（管线 `RenderPipelines.GUI`）
     指向 **glow framebuffer**（136-138 行），在其内部 `NVGRenderer.beginFrame()`(141) →
     `EventDispatcher.dispatch(new RenderBloomEvent(context, tickDelta))`(143) →
     `NVGRenderer.endFrameAndReset(false)`(144)，然后 147 行 `applyGlowToNVGObjects()`（就地模糊 glow buffer）。
3. 88-89 行用 `mc.mouse` 与窗口尺寸算 GUI 空间的 `mouseX/mouseY`。
4. 91 行 `NVGRenderer.beginFrame()`；92 行先铺一层整屏 `GLOW_PAINT`；95 行派发 `RenderScreenEvent`；96 行 `endFrameAndReset(true)`。
5. 105 行 `MinecraftRenderer.render()` 冲刷延迟渲染队列。

`BLUR_PAINT` / `GLOW_PAINT` 不是颜色而是 **NVG image paint**，指向两个离屏 framebuffer：
`ShaderFramebuffer.java:112-113` 在 `onResized(width,height)`（100-114 行）里用
`NVGRenderer.createNVGPaintFromTex(...)` 把 `blurFramebuffer`/`glowFramebuffer` 的颜色附件包成 paint。
`applyBlurToFullScreen()` 先把**当前帧主 framebuffer** blit 到 blur buffer（44 行）再模糊（46 行）；
`applyGlowToNVGObjects()` 只对 glow buffer 做模糊（59 行）。

因此"bloom 第二遍"的语义是：

- 主通道里元素用 `BLUR_PAINT` 画面板 → 采样到的是**本帧世界画面的模糊版**（磨砂玻璃）；
- bloom 通道里同一个元素的 `render(..., true)` 被再调一次，画出的是**该元素自己**的亮部，
  进 glow buffer，模糊后作为下一帧/本帧主通道的 `GLOW_PAINT` 底图被合成回屏幕。

元素用 `isBloom` 参数做的实际区分只有一处：`TargetInfoElement.java:96`
(`isBloom ? -1 : this.getSkinTextureGlId(target.entity)`) 与 184 行的
`if (skinTextureGlId == -1) break renderHead;`（bloom 通道不画头像）。

---

## 2. 定位与布局模型

### 2.1 三个类各自的真实职责

| 类 | 真实职责 | 证据 |
| --- | --- | --- |
| `utility/render/ScreenPosition.java` | **与定位无关**：只是一个 `x,y,width,height` 数据袋，全仓库唯一用途是给 `NVGRenderer.scissor` 当矩形（`NVGRenderer.java:214`）。名字有误导性 | `ScreenPosition.java:3-59`；grep `new ScreenPosition` 仅 2 处 |
| `property/impl/ScreenPositionProperty.java` | **真正的定位模型 + 拖拽状态 + 尺寸回填**：`extends Property<Pair<Float,Float>>`，值是 0..1 的相对坐标 | 11 行、16-24 行 |
| `helper/impl/render/ScreenPositionManager.java` | 单例服务：把"哪个模块的哪个 ScreenPositionProperty"登记起来，在 `ChatScreen` 里做拖拽与吸附、画悬停高亮与网格线 | 25 行、31-33 行、35-86 行 |
| `helper/impl/render/ScaleProperty.java` | 元素缩放：把 GUI Scale 映射成相对于"某档基准大小"的倍率 | 28-59 行 |

### 2.2 位置如何存储与持久化

- 存储：`ScreenPositionProperty` 的值是 `Pair<Float,Float>`（相对 X / 相对 Y），构造时给默认值
  （`TargetInfoSettings.java:17`：`new ScreenPositionProperty("Screen Position", 0.43F, 0.65F)`）。
- 持久化：`Property<T>` 的字段带 GSON 注解（`Property.java:22-28`：`@SerializedName("name") id`、
  `@SerializedName("value") value`），保存是 `SaveUtility.saveConfig("default")` →
  `GSON.toJson(moduleRepository.getModules())` → `Files.writeString(configPath, json)`
  （`SaveUtility.java:147-164`）。
- 反序列化：`ScreenPositionProperty.applyValue(Object)`（26-42 行）只认 `LinkedTreeMap` 形态的
  `{"x":..,"y":..}`，空 map 或缺字段直接 `return`（不改值）；配置里属性名按
  `normalize(property.getId()/getName())` 匹配（`SaveUtility.java:330-339`）。
- **写入时机**：`Property.setValue` 无条件调用 `SaveUtility.autoSaveDefaultConfig()`（`Property.java:81-87`，
  第 86 行），而 `autoSaveDefaultConfig()` 只在 `postInitialization==true` 且非抑制状态下执行
  （`SaveUtility.java:224-230`）。

### 2.3 相对坐标 ↔ 像素（锚点/对齐的真相）

`ScreenPositionProperty.java`：

- `getScaledX()`（52-63 行）：`actualX = relativeX * window.getScaledWidth()`；
  **若 `relativeX > 0.5F` 则右对齐**：`return actualX - width`。也就是说"元素中心过屏幕中线"
  就自动变成"以右边缘为锚"。没有第三个锚点，也没有竖向对齐。
- `getScaledY()`（65-67 行）：`relativeY * window.getScaledHeight()`，**无对齐分支**。
- `setRelativeX(scaledX)`（77-86 行）：反变换，且若结果 `> 0.5F` 再加 `width/scaledWidth`。
- `setRelativeY(scaledY)`（88-91 行）：`scaledY / getScaledHeight()`。
- `snapToGrid()`（93-124 行）：X 在 `<0.01` 吸附到 0、`>0.99` 吸附到 1、`relativeX + halfWidth` 落在
  `(0.49,0.51)` 吸附到 `0.5 - halfWidth`；Y 同构但右边界判断用 `relativeY + relativeHeight > 0.99`。
  注意 X 的"贴右"用的是 `relativeX > 0.99` **而不是** `relativeX + relativeWidth > 0.99`，与 Y 不一致
  （两者都能"贴边"，但 X 只在元素左边缘过 99% 时才吸，靠右的元素在 `getScaledX()` 已经右对齐，行为上自洽）。
- 尺寸：`width/height` 由**元素自己在渲染时回填**（`TargetInfoElement.java:110-111`），
  供管理器做命中测试与吸附；未渲染过的元素 `width/height == 0`。
- 拖拽起点：`getStartX()`（150-154 行）在 `startX > scaledWidth/2` 时减去 `width`，与 `getScaledX()`
  的右对齐口径配套。

### 2.4 拖拽编辑器（参考没有独立编辑器）

链路：`ChatScreenMixin.java:15-18`（`@Inject` 到 `mouseClicked`）→
`ScreenPositionManager.onMouseClick`(88-108 行) 用 `HoverUtility.isHovering(...)` 命中测试，
命中则记录 `startX/startY`（抓取偏移）并 `setDragging(true)`；释放由
`ChatScreenMixin.java:20-26` 的 `mouseReleased` 覆写注入到
`ScreenPositionManager.releaseDraggedProperties()`（110-113 行）。拖动发生在**下一次渲染**里：

```java
// ScreenPositionManager.java:47-59
this.properties.forEach((property, module) -> {
    if (!module.isEnabled()) return;
    if (property.isDragging()) {
        property.setRelativeX((float) (mouseX - property.getStartX()));
        property.setRelativeY((float) (mouseY - property.getStartY()));
        if (!PlayerUtility.isKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT)) property.snapToGrid();
    }
    ...
});
```

鼠标坐标来自 `RenderScreenEvent`（InGameHudMixin 自己算的，88-89 行）。
拖动时整屏画 1px 外框 + 十字中线（76-85 行），悬停时给元素画 4px 外扩的半透明圆角框（61-73 行，
用 `NVGRenderer.roundedRect`）。按住 Shift 关闭吸附。
登记入口是 `Module.addProperties`（`Module.java:137-151`），它扫描 `ScreenPositionProperty`
（含 `GroupProperty` 一层）并 `ScreenPositionManager.getInstance().register(this, prop)`。
`ScreenPositionManager.setInstance()` 在 `OpalClient.runHelperInitializations()` 里调用
（`OpalClient.java:203-212`，第 210 行），而 `runHelperInitializations()` 在模块仓库构造**之前**
执行（`OpalClient.java:79` vs `86-87`），所以注册时单例已存在（否则 `getInstance()` 会 NPE）。

### 2.5 GUI Scale 如何介入

`ScaleProperty.java`：

- 两个工厂：`newMinecraftElement()`（16-18 行）与 `newNVGElement()`（20-22 行）；
  前者用 `MINECRAFT_VALUES`（9 行）——注意数组里第 4 个元素是 **`null`**，
  即"有 5 个档位但 MEDIUM 不可选"。
- `getScale()`（28-59 行）：读 `mc.options.getGuiScale().getValue()`，按 `ScaleMode`
  给出相对倍率。语义是"抵消 GUI Scale，让元素在物理像素上保持固定大小"：
  `SMALL`(1x) 在 guiScale=2 时给 0.5、`NORMAL`(2x) 在 guiScale=1 时给 2。
- **AUTO 没有实现**：`ScaleMode.AUTO` 落到 `default -> 1`（57 行），即 AUTO == 不缩放。
  默认值就是 AUTO（13 行），所以默认行为等价于"1:1 跟随 GUI Scale"。
- `MEDIUM`("2.67x") 的实际倍率是 2.25/1.125/0.75（45-50 行），与名字的 2.67 不一致（注释与实现对不上，非缺陷但要注意）。
- 元素用法：`TargetInfoElement.java:88` 取 `settings.getScale()`，然后在
  `NVGRenderer.scale(scale, x, y, 0, 0, () -> {...})`（127 行）里**以 (x,y) 为原点**缩放；
  `ClientElements`/`ModuleElement` 则用 `NVGRenderer.scale(scale, anchorX, anchorY, 0, 0, ...)`
  （`ClientElements.java:64`、`ModuleElement.java:56-62`）。`NVGRenderer.scale` 的实现是
  `translate(pivot) → scale → translate(-pivot)`（`NVGRenderer.java:115-127`）。

### 2.6 分辨率变化如何重算

- **位置不需要重算**：`getScaledX/Y` 每次都从 `mc.getWindow()` 现算（54、66 行），
  相对坐标天然分辨率无关。
- `ResolutionChangeEvent`（空类，`RenderScreenEvent` 旁边）由 `MinecraftClientMixin.java:299` 派发，
  `OverlayModule.java:136-139` 把它转成 `IOverlayElement.onResize()`。
- 唯一实际使用 `onResize()` 的元素是 `DynamicIslandElement`（76-79 行）：只把 `positioned` 置 false，
  下一次渲染走 `setValue()` 直接吸附（112-120 行），不播动画。

### 2.7 本节两个**可证明**的缺陷

**P1｜拖拽时每帧全量写盘。**
拖动期间每一帧都会调用 `setRelativeX` → `_setRelativeX` → `Property.setValue`
→ `SaveUtility.autoSaveDefaultConfig()`（`ScreenPositionProperty.java:69-91` → `Property.java:81-87`
第 86 行 → `SaveUtility.java:224-230` → `147-164`）。也就是**鼠标每移动一帧，
都把全部模块序列化成 JSON 并同步写文件一次**，而且 `setValue` 不比较新旧值是否相同。
60fps 拖动 1 秒 = 60 次全量序列化 + 60 次 `Files.writeString`。

**P2｜拖拽时每帧重排模块列表。**
同一路径还会派发 `PropertyUpdateEvent`（`Property.java:84`），`OverlayModule.java:111-116`
把 `toggledModules` 标记为 dirty，`ToggledModulesElement.java:67-71` 在渲染里
`tick() + sort()`（`Collections.sort(moduleList)`，52-55 行）。

**P3｜拖拽有一帧延迟，且该帧内高亮框与元素本体不同步。**
`ListenerMethod` 把 priority 取负后升序排序（`ListenerMethod.java:16`、`EventRegistry.java:57-59`），
即**数值大的先跑**。`OverlayModule` 是 `-20`，`ScreenPositionManager` 是 `-50`，
所以同一帧的顺序是：元素先用**旧**位置画完（`TargetInfoElement.java:107-108`），
然后管理器才更新位置并画出高亮框（`ScreenPositionManager.java:52-73`）。

---

## 3. 动画层

### 3.1 `Animation` 的精确更新契约

`utility/render/animation/Animation.java`（98 行）：

| 行 | 成员 | 语义 |
| --- | --- | --- |
| 15-19 | `Animation(Easing, long duration)` | 只记 easing/duration，`startTime = System.currentTimeMillis()`。`value`/`startValue`/`destinationValue` 默认 0，`finished` 默认 false |
| 21-46 | `void run(float destinationValue)` | **唯一的推进入口**。每次调用都重读 `System.currentTimeMillis()`（22 行） |
| 23-26 | 分支 A：`destinationValue != 新目标` | 记新目标 → `reset()`（`startTime=now`、`startValue=当前 value`、`finished=false`）→ 继续算出**本帧**的值 |
| 27-32 | 分支 B：目标未变 | `finished = (millis - duration > startTime) \|\| (value == destinationValue)`；若 finished 则 `value = destinationValue` 并 **return**（提前退出） |
| 34 | — | `result = easing.apply(getProgress())`；**在 35 行的 `duration == 0` 判断之前调用** |
| 35-41 | — | `duration == 0` 时直接 `value = destinationValue`；否则按 `startValue + (dest - startValue) * result`（升）或 `startValue - (startValue - dest) * result`（降）插值 |
| 43-45 | — | 结果非有限则退回 `destinationValue` |
| 48-50 | `float getProgress()` | `(now - startTime) / (float) duration`，**未钳制、无零时长保护** |
| 52-56 | `reset()` | `startTime = now`、`startValue = value`、`finished = false`（**不改 `destinationValue`**） |
| 62-64 | `setDuration(long)` | 直接赋值，无校验；**全仓库无人调用**（见 §3.3-D1） |
| 74-85 | `getValue/setValue/setStartValue/getStartValue` | `setValue` 只改 `value`（不影响 `destinationValue`/`startTime`）；`setStartValue` 同时把 `value` 设成同值 |

`Easing`（`Easing.java:7-35`）是 30 个枚举项、`Function<Float,Float>` 的表；
`EASE_IN_SINE`/`EASE_OUT_SINE`/`EASE_IN_OUT_SINE` 依赖 `net.minecraft.util.math.MathHelper`（23-25 行），
其余是纯 `Math`（30 行 `EASE_OUT_CIRC` 等）。其中 `DYNAMIC_ISLAND`（35 行）
与 `EASE_OUT_ELASTIC`（33 行）是**过冲/回弹**曲线，用在动态岛与通知上。

### 3.2 真实调用点（全部）

| 文件:行 | 用法 |
| --- | --- |
| `TargetInfoElement.java:72` | `targetAnimation = new Animation(EASE_OUT_EXPO, 200)`，随后 `setValue(1)`(73) |
| `TargetInfoElement.java:75` | `healthAnimation = new Animation(EASE_OUT_EXPO, 1000)` |
| `TargetInfoElement.java:123` | `healthAnimation.run(trueHealthPercent)` —— **每帧**（在 render 内） |
| `TargetInfoElement.java:347,351` | `targetAnimation.isFinished()` / `run(0)`（目标消失时的淡出驱动） |
| `TargetInfoElement.java:354-355` | `setValue(1); reset();` —— 目标存在时把进度钉在 1 |
| `NotificationsElement.java:57` | 每条通知一个 `new Animation(EASE_OUT_EXPO, 400)`，`Map<Notification,Animation>` 懒建 |
| `NotificationsElement.java:70-72` | `if(!hasExpired()) setStartValue(scaledWidth);` 然后 `run(hasExpired() ? scaledWidth : endX)` |
| `NotificationsElement.java:93` | `animation.getValue() == scaledWidth` 作为移除条件（浮点相等比较） |
| `ModuleElement.java:31-33` | `xAnimation.run(posX)`、`yAnimation.run(posY)`、`heightAnimation.run(enabled?1:0)` —— **每帧**，且因为 `isBloom()==true` 一帧跑两次（主通道 + bloom 通道） |
| `ModuleElement.java:137,141,145` | 三个动画的懒创建（`EASE_OUT_EXPO/400`、`EASE_OUT_EXPO/600`、`EASE_IN_OUT_CUBIC/200`） |
| `ToggledModulesElement.java:37` | `getTotalHeight()` 用 `heightAnimation.getValue()` 累加 |
| `DynamicIslandElement.java:113-127` | 4 条 `DYNAMIC_ISLAND/250` 的动画；`positioned==false` 时用 `setValue()` 吸附，否则 `run()` |
| `DynamicIslandElement.java:59,132` | `Math.min(1, heightAnimation.getProgress())` 当透明度/进度用 |
| `ModuleElement.java:110` | `xAnimation.isFinished()` 判定"退场动画播完 → 置空引用" |
| `Scroller.java:8,17,29` | `EASE_OUT_EXPO/250`，滚动偏移动画（点击 GUI 用） |
| 其余（click GUI） | `MenuPanel/CategoryPanel/PropertyComponent` 等：`DECELERATE/125`、`LINEAR/50` 等 |

**调用约定总结**：`run(dest)` 是"幂等的每帧推进一步"，调用方**不需要**在目标变化时手动 `reset()`；
只有需要"瞬间置位/重播"时才手动 `setValue`/`reset`（`TargetInfoElement.java:354-355`、
`DynamicIslandElement.java:114-118`）。`finished` 只在"目标未变且（已超时或已到值）"时才会为 true，
所以"目标存在时 `isFinished()` 一直为 false"（因为那时没人调 `run()`）。

### 3.3 可证明的缺陷（有具体输入）

**D1｜`getProgress()` 无零时长保护 —— 潜在，当前不可达。**
`run()` 在 34 行调用 `getProgress()`，**早于** 35 行的 `if (this.duration == 0L)` 保护；
`getProgress()` 是 `(float)(now - startTime) / (float) duration`（49 行）。若 `duration == 0`：
`System.currentTimeMillis()` 相减为 0 时得到 `0.0f/0.0f = NaN`，跨毫秒则 `Infinity`；
缓动函数会拿到 NaN/Inf（例如 `EASE_IN_CIRC(x) = 1 - sqrt(1 - x*x)`（29 行）在 `x = Inf` 时得 NaN，
`EASE_IN_EXPO`（26 行）得 Inf）。**但当前不可达**：`grep 'new Animation('` 的全部 20 个调用点
都传正常量（最短 50，`scroller` 250，`Scroller.java:8`），`setDuration(` 只有定义、
无任何调用点，`duration == 0` 的分支纯属防御。结论：**移植时保留这层保护，但不要把它当成"现网 bug"**。

**D2｜过冲曲线依赖"目标不变"的早退来兜底，进度一旦 > 1 也不会溢出（这个没问题，但边界要照抄）。**
`run()` 的 B 分支先算 `finished = (millis - duration > startTime)` 并提前 return（27-31 行），
所以 `getProgress() > 1` 只可能发生在 A 分支重启后的那一帧（progress ≈ 0），
`EASE_IN_OUT_CUBIC(1.2) = 1.032`（`Easing.java:16`）这类过冲**不会**发生。
但 `progress > 1` 直接出现在公众 API 上（`DynamicIslandElement.java:59,132` 必须自己 `Math.min(1,...)`）。

**D3｜`ACTIVE_TRIGGERS` / `SORTING_DIRTY` 跨线程，非线程安全（真实可达）。**
`DynamicIslandElement.java:24` 是 `static final List<IslandTrigger> ACTIVE_TRIGGERS = Lists.newArrayList(new DefaultIsland())`
（普通 `ArrayList`），`addTrigger`/`removeTrigger`（92-103 行）直接 `add/remove`，
`sort()`（107-110 行）在渲染线程做 `Collections.sort`，`getDecidingTrigger()`（171-173 行）
在渲染线程 `getFirst()`。
写入方不在渲染线程：`MusicService.java:44-45` 建了一个 `ScheduledExecutorService` 名为
"OpenOpal Music Monitor"，`MusicService.java:90` 以 100ms 周期跑 `monitorPlayback`
（420-438 行），其中 430 行 `next()`、434 行 `pausePlayback()`（502-514 行，512 行
`DynamicIslandElement.removeTrigger(islandTrigger)`）都会走到 `registerIsland()`
（468-474 行，470/472 行 add/remove）。
具体后果有两条：(1) 与 `Collections.sort` 并发 `add` → `ArrayList` 内部扩容/移位，
可能抛 `ArrayIndexOutOfBoundsException`/`ConcurrentModificationException` 或丢元素；
(2) `SORTING_DIRTY`（105 行）是**非 volatile 的 static boolean**，渲染线程可能长期看不见
monitor 线程写入的 `true`（36-38 行只在 true 时排序），于是新触发的岛永远排在错误优先级。
另外 `DynamicIslandElement.java:172` 的 `getFirst()` 在列表为空时抛 `NoSuchElementException`，
但 `DefaultIsland` 是构造时硬编码进去且**没有任何调用方能移除它**（`removeTrigger` 只会被传入
`Module` 实例或 `MusicIslandTrigger`，见 81-90、468-474 行），所以这一条是**潜在而非可达**。

**D4｜通知进度条未钳制（真实可达，有具体数值）。**
`NotificationsElement.java:77`：`progress = (float) notification.getTime() / notification.getDuration()`。
`getTime()` 是 Stopwatch 的"自创建以来的毫秒数"，只增不减（`Stopwatch.java:31-33`；
`Notification.hasExpired()` 调用 `hasTimeElapsed(duration, false)` **不重置**，`Notification.java:35-37`）。
默认时长 2000ms（`NotificationManager.java:38`），而通知过期后还要再播 400ms 的滑出动画
（`NotificationsElement.java:57` 的 400ms + 93-96 行的移除条件）。
于是退出动画期间 `progress` 最大到 `2400/2000 = 1.2`，而它被直接用作进度条宽度
`(width - 0.5F) * progress`（84 行）——**进度条会比面板宽 20%**；
同一个表达式里的圆角项 `progress > 0.95F ? 4 : 0` 也会永久latch成 4。
同一行还有 `NVGRenderer.roundedRectVaryingGradient` 的第 3 个参数用 `Color.BITMASK`
（`java.awt.Color`，84 行）——语义可疑但未验证，不作断言。

**D5｜bloom 通道导致装备图标一帧画两次（真实可达，默认配置下）。**
`TargetInfoElement.java:246` 的 `MinecraftRenderer.addToQueue(...)` 在 218-299 行的装备块里，
**不受 `isBloom` 保护**。因此当 `PostProcessingModule.isEnabled() && isBloom()` 时
（默认均开启：`PostProcessingModule.java:11-12` 两个 `BooleanProperty("Enabled", true)`、19 行 `setEnabled(true)`），
同一帧内该 lambda 先被 bloom 通道执行一次、再被主通道执行一次（`OverlayModule.java:118-134`），
而 `MinecraftRenderer.render()`（`MinecraftRenderer.java:17-21`）是 `while(!queue.isEmpty())` 的**排空式**执行，
且在 `InGameHudMixin.java:105` 只被调用一次 → **每个装备 ItemStack 每帧被绘制两次**。
`ClientElements` 的状态效果图标（`ClientElements.java:127-133`）因为 `isBloom()` 返回 false（170-172 行）
不受影响。

**D6｜遍历中删除别人的列表（真实可达，后果轻微）。**
`NotificationsElement.java:44` 拿的是 `NotificationManager.getNotifications()` 返回的**内部 ArrayList 本身**
（`NotificationManager.java:8-12`，无拷贝），94 行在 `for (int i ...)` 循环里
`notifications.remove(notification)`，紧接着 95 行还删 `animations` 的键。
索引循环删元素不会抛 CME，但会让被移位的下一条通知**跳过一帧渲染**；
同时该元素对外部容器的写没有同步，也没有走 `NotificationManager.remove`（24-26 行）这个公开口子。

**D7｜`renderBlur` 是死代码**（见 §1.1）：接口有钩子、`ToggledModulesElement.java:62-65` 有覆写，
但没有任何调用方；覆写体内还被注释掉了。移植时**不要**把它当接口契约带过去。

---

## 4. 具体元素

### 4.1 `ClientElements` + `ClientElementSettings`

- 画什么：左下角一列"前缀 + 值"（`XYZ`/`BPS`/`FPS`，每行 `FONT_HEIGHT` 递减，61-97 行）、
  右下角一列状态效果（文案 + 9×9 图标，99-136 行）。前缀用 `productsans-bold` 画**渐变**文字，
  值用 `productsans-regular` 画（34-38、71-94 行），字号固定 8（37 行）。
- 设置（`ClientElementSettings.java:16-23`）：`ScaleProperty.newNVGElement()`、
  `MultipleBooleanProperty("Options", 状态效果=true, FPS=true, BPS=true, XYZ=false)`、`Lowercase=true`。
- 数据源：`mc.player.getEntityPos()`(73)、`MoveUtility.getBlocksPerSecond()`(84)、
  `mc.getCurrentFps()`(94)、`mc.player.getActiveStatusEffects()`(107)、
  `I18n.translate(instance.getTranslationKey())`(144)、`InGameHud.getEffectTexture(registryEntry)`(128)。
- 激活条件：`!mc.getDebugHud().shouldShowDebugHud()`（165-167 行）。
- 它是**固定角标**元素：完全不用 `ScreenPositionProperty`（grep 该文件无此类型），
  位置写死为 `x = 2` / `x = scaledWidth - 2`（62、101 行）。

### 4.2 `TargetInfoElement` + `TargetInfoSettings`

设置（`TargetInfoSettings.java:16-18`）：`Enabled=true`、
`ScreenPositionProperty("Screen Position", 0.43F, 0.65F)`、`ScaleProperty.newNVGElement()`。

绘制内容（`TargetInfoElement.java:82-305`）：圆角磨砂面板（131-132 行）+ 目标名（135 行）
+ 心形图标与血量数字（139-143 行）+ **双血条**（146-179 行：底槽、`healthAnimation` 的"滞后条"、
  `trueHealthPercent` 的"实时条"、再叠一层 90° 渐变压暗）+ 头像（182-215 行）+ 装备栏（217-299 行：
  每格 10.5px 背景 + `context.drawItem` + 附魔短名）。

具体显示哪些实体属性（全部实测行号）：

| 属性 | 来源 | 行 |
| --- | --- | --- |
| 显示名（含队伍/颜色） | `entity.getDisplayName().asOrderedText()` 经 `OrderedTextVisitor` 提取为带 `§` 的字符串 | 407-421 |
| 血量 | `entity.getHealth()` | 119、140 |
| 吸收量 | `entity.getAbsorptionAmount()`（**并入血量数字与血量百分比分母**） | 119、138-140 |
| 最大血量 | `entity.getMaxHealth()` | 119 |
| 受伤闪红 | `entity.hurtTime` / `entity.maxHurtTime` | 200-208 |
| 皮肤/实体贴图 | 玩家 `getSkin().body().texturePath()`；骷髅/僵尸/苦力怕/猪灵用硬编码贴图 | 374-387 |
| 装备 | `AttributeModifierSlot.ARMOR` 过滤 `EquipmentSlot.Type.HUMANOID_ARMOR` + `getMainHandStack()`，再 `Collections.reverse` | 219-233 |
| 附魔短名 + 等级 | `EnchantmentHelper.getEnchantments(stack)`（经 `ESPUtility.ENCHANTMENT_NAMES`） | 282-293 |

数据源（`getTarget()`，312-372 行）优先级：
`LocalDataWatch.get().lastEntityAttack.getRight()`（且必须仍在 `LocalDataWatch.getTargetList()` 白名单里，
313-316 行）→ 否则若 `KillAuraModule.isEnabled()` 取 `killAuraModule.getTargeting().getTarget()`（318-326 行）
→ 都不行时**在 `ChatScreen` 打开的情况下把假目标设成玩家自己**（336-342 行，这是 HUD 预览机制）。
目标切换/消失时用 `targetAnimation` 淡出 + 释放 NVG 图像句柄（362-369 行）。

### 4.3 `ToggledModulesElement` + `ModuleElement` + `ToggledSettings`

- 画什么：右侧（或左侧）纵向的"已开启模块"列表，每项高 `OFFSET = 12`（`ModuleElement.java:80`）：
  磨砂底 + 半透明黑底（51-63 行）、左侧或右侧 1px 竖条（65-71 行，颜色按索引做彩虹插值 44-48 行）、
  模块名（+ 灰色后缀）文字（74 行）。展开/收起与位置分别由三条动画驱动（31-33 行）。
- 数据源：`OpalClient.getInstance().getModuleRepository().getModules()`（26-32 行，`initialize()` 一次性建列表）、
  `module.isEnabled()/isVisible()/getCategory().getName()/getSuffix()/getName()`
  （`ModuleElement.java:94-99,150-152`）、`settings.getVisibleCategories()` 按分类过滤。
- 设置（`ToggledSettings.java:23-36`）：`ScaleProperty.newNVGElement()`、`BarMode`(LEFT/RIGHT/NONE)、
  `Enabled`、`Lowercase`、`Show suffix`、`Offset scoreboard`、`Visible categories`（按 `ModuleCategory.VALUES` 生成）。
- 特有能力：`getTotalHeight()`（`ToggledModulesElement.java:34-40`）被记分板重排调用
  （`InGameHudMixin.java:225-245`）；`markSortingDirty()` 由任何属性变化触发（`OverlayModule.java:111-116`）；
  排序口径是**宽度降序**（`ModuleElement.java:162-165`）。

### 4.4 `NotificationsElement` + `NotificationSettings`

- 画什么：右下角自下而上堆叠的通知卡（高 21、内边距 3、图标 14），含磨砂底 + 黑底（80-81 行）、
  底部按 `progress` 走宽的进度条（84 行）、类型图标（88 行）、标题 7 号 + 描述 6.5 号（90-91 行）；
  从右侧滑入/滑出由 `Animation(EASE_OUT_EXPO, 400)` 驱动（57、70-72 行）。
- 数据源：`OpalClient.getInstance().getNotificationManager().getNotifications()`（44 行）；
  通知模型见 `Notification.java`（type/title/description/duration）与
  `NotificationManager.NotificationBuilder`（时长默认 2000，38 行）。
- 设置（`NotificationSettings.java:12-15`）：`On module toggle = false`（注意 **`enabled` 没有加进 GroupProperty**，
  13 行创建、15 行只加了 `moduleToggleNotifications`——`isEnabled()`（18-20 行）因此没有任何 UI 能改到，
  恒为 true 的默认值）。

### 4.5 `DynamicIslandElement` / `IslandTrigger` / `CustomIslandTrigger` / `DefaultIsland`

- 模型：`IslandTrigger`（`IslandTrigger.java:6-20`）是 `Comparable`，契约只有
  `renderIsland(ctx,x,y,w,h,progress)`、`getIslandWidth()/getIslandHeight()`、
  默认 `getIslandPriority()==0`，`compareTo` 按优先级**降序**（19 行）。
  `CustomIslandTrigger`（`CustomIslandTrigger.java:3-7`）额外提供 `getIslandX/getIslandY`，
  即"自带坐标、不受对齐设置影响"。
- 优先级栈：`ACTIVE_TRIGGERS`（24 行）默认只有 `DefaultIsland`；
  任何 `Module implements IslandTrigger` 在开关时自动进出栈（81-90 行），
  音乐模块与 Breaker/Clipper/Scaffold 也手动 push/pop。
  `getDecidingTrigger()` 永远取**表头**（171-173 行），即"优先级最高者独占海岛"。
- 默认岛（`DefaultIsland.java:28-116`）：品牌字（`borel-regular`）"OpenOpal" 渐变字、
  版本/发行通道、服务器地址与延迟（40-66 行：`mc.getNetworkHandler().getServerInfo()`、
  `getPlayerListEntry(uuid).getLatency()`，`latency < 2` 时回退 `serverInfo.ping`）、
  启动图标（`ImageRepository` 的 32/128 两档 png，125-129 行）、两条分隔线。
  高度固定 28（137-139 行），宽度按文字量算（77 行）。
- 渲染：非自定义触发时先画背景（`LiquidGlassRenderer` 或磨砂+黑底，130-138 行），
  `NVGRenderer.scissor(...)` 裁剪内容（71 行），`globalAlpha(progress)` 淡入（68-72 行）；
  淡入进度取 `Math.min(1, heightAnimation.getProgress())`（59、132 行）。
- 自定义触发直接以自身坐标调 `render.run()`，不画背景、不裁剪（63-66 行）。
- `isActive()` 在表头是 `CustomIslandTrigger` 时返回 **false**（166-169 行），
  把绘制完全让给那个触发者自己的模块。

---

## 5. 映射到 WurstB 的 `hud2`

分类约定：**(a)** hud2 已有等价物（给出类名）；**(b)** 可移植且值得加（给出落点）；
**(c)** 不可移植（给出理由）。

| 参考物 | 判定 | 落点 / 理由 |
| --- | --- | --- |
| `IOverlayElement` 的 `render/isActive/tick/onResize/onDisable/isBloom` | **(a)** | `HudElement`（`HudElement.java:6-53`）已有 `render(GuiGraphics,int x,int y,float partialTicks)`(45-46)、`onEnable/onDisable(HudManager)`(37-39)、`isSingleton`(27-30)、`renderEditorPreview`(32-35)、`getWidth/getHeight`(41-43)、`getDefaultLayout`(48-52)。缺 `isActive`（由 `HudLayout.HudElementConfig.isEnabled` 代替，`HudManager.java:123-126` 每帧查）、缺 `tick`（用 `EventManager` 监听器代替，`KeystrokesHudElement.java:48-58`、`TargetHudElement.java:69-80`）、缺 `onResize`（不需要，见下行） |
| `onResize()` + `ResolutionChangeEvent` | **(a)**（更强） | `HudManager.onRenderGUI` 每帧用 `graphics.guiWidth()/guiHeight()` 现算（`HudManager.java:127-131`），分辨率变化自动生效，不需要 resize 事件 |
| `renderBlur` 钩子 | **(c)** | 参考里本身就无人调用（§3-D7），不要带过去 |
| bloom 第二遍（`RenderBloomEvent` + `isBloom`） | **(c)** 照抄不行 / **(b)** 需重新设计 | 见 §6-B1。照抄会引入 §3-D5 的重复入队；要做的话走 `PostEffectQueue` 那套 `TextureTarget + PostChain`（`PostEffectQueue.java:82-103`） |
| `ScreenPositionProperty` 的相对坐标 + 左右阈值对齐 | **(a)**（更强）+ **(b)** 补几何 | `HudLayout.HudElementConfig`（`HudLayout.java:10-98`）已有 `horizontalAlignment/verticalAlignment/offset/scale`，比参考多一个竖向锚点。可补的是**相对坐标纯函数**（0..1 ↔ 像素、边缘/中线吸附），落点建议 `util/hud/HudPlacement.java`（纯逻辑、无 `net.minecraft.`，参照 `util/esp/EspNameTagLayout` 的 `GlyphMeasurer` 注入风格，`EspNameTagLayout.java:55-60`） |
| 拖拽编辑器（`ScreenPositionManager` + `ChatScreenMixin`） | **(a)** | `HudEditorScreen`（`HudEditorScreen.java:251-403`）：左键吸附拖动 + 右/左键点击开关 + 右键循环对齐 + 滚轮缩放 + 中键重置缩放 + 对其他元素的边缘吸附（`snapPosition`，405-476 行）+ 参考虚线（111-118 行）+ 工具栏提示（214-226 行）。比参考强，**保持不动** |
| 拖拽时写盘（§3-D2） | **(c)** 反面教材 | `HudManager.saveLayout()`（473-507 行）只在松手/改设置时调用（`HudEditorScreen.java:328-330`、`HudManager.updateElementLayout:206-215`），不要照抄每帧写盘 |
| `ScaleProperty` 的 GUI-Scale 抵消语义 | **(a)** 部分 | `HudElementConfig.scale`（`HudLayout.java:24,87-97`，钳到 0.2..5）已经能表达；参考"按 GUI Scale 反算档位"的 `ScaleMode` 语义可以补成一个纯函数（`SMALL/NORMAL/MEDIUM/LARGE` → 倍率），落点同上 `util/hud/` |
| `Animation` + `Easing` | **(a)** 已有同类 + **(b)** 补曲线 | 已有 `clickgui2/supersoft/UiTween`（`UiTween.java:3-65`，公开、时长制、`Math.max(1,duration)` 防零、`Math.min(1, elapsed)` 钳制）、`clickgui2.RiseAnimation`（`RiseAnimation.java:6-79`，**包私有**，只有 LINEAR/EASE_IN_EXPO/EASE_OUT_EXPO）、`compose.AnimFloat`（`AnimFloat.java:8-51`，指数平滑）、`clickgui2.supersoft.UiMotion`（`UiMotion.java:3-59`，弹簧）。**缺的是 30 条缓动曲线**，建议新增纯逻辑 `util/hud/Easings.java` + 一个 `util/hud/HudTween.java`（语义照 `UiTween`，但曲线可注入），并补单测 |
| `ModuleElement`/`ToggledModulesElement`（模块列表） | **(a)** 等价物存在 | `hud/HackListHUD`（`HackListHUD.java:26-197`：条目表、排序、每项进度动画、`ComposeHackList` 绘制）+ `hud2` 里注册的 `"hacklist"` 匿名元素（`HudManager.java:318-354`）。参考独有的是 `BarMode` 竖条、`Offset scoreboard`、`Visible categories`、`getTotalHeight()` 供记分板重排 —— **(b)** 可增量补进 `HackListHUD`/`HackListOtf` |
| `NotificationsElement` + `HudNotificationRenderer` | **(a)** 已有 | `HudNotificationRenderer`（`HudNotificationRenderer.java:13-218`）+ `ComposeNotifications`；`HudNotification.lifetimeProgress`（`HudNotification.java:93-100`）**已经钳到 0..1**，正好修掉 §3-D4。**(b)** 只缺"模块开关通知"开关项与被通知的钩子（参考 `NotificationSettings.moduleToggleNotifications`） |
| `ClientElements` 的 XYZ/BPS/FPS/状态效果 | **(a)** 大部分已有 | `TextHudElement`（`TextHudElement.java:9-45`）+ `CoordsHudElement`(XYZ)/`FpsHudElement`/`SpeedHudElement`/`PotionHudElement`（`PotionHudElement.java:49-68` 已做"名字+等级+时长"）。**(b)** 缺的是"一个元素里多行角标 + 前缀渐变字 + 状态效果图标"的合并形态（落点：`hud2/elements/` 新元素，数据源全在 1.20.1 可得） |
| `TargetInfoElement` | **(b)** 可移植 | 见 §4.2 与 §7 第 6 步；落点 `hud2/elements/TargetHudElement`（已存在，`TargetHudElement.java:32`）增量扩展，而不是新增第二个目标元素 |
| `DynamicIslandElement` / `IslandTrigger` 优先级栈 | **(b)** 可移植 | 纯逻辑部分（优先级表、按优先级选表头、自定义坐标分支）可无损移植到 `util/hud/IslandPriority.java`；绘制部分依赖图标字体与磨砂，需要替换（`MusicIslandHudElement.java` 已是同类 UI 的先例） |
| `LiquidGlassRenderer`（液体玻璃） | **(c)** | NanoVG 专属（`NVGRenderer.BLUR_PAINT` + `nvgImagePattern` 的变形 paint，`LiquidGlassRenderer.java:158-162`）；WurstB 的等价物是 `RiseFrostedGlass`（多抽样模糊，`RiseFrostedGlass.java:103-154`） |
| `LayoutHelper.percentWidth/centerX` | **(b)** 可选 | 4 个纯函数（`LayoutHelper.java:5-21`），可直接进 `util/hud/` 一并单测 |
| `Scroller` | **(a)** | 参考的滚动动画只服务 click GUI；`hud2` 无滚动列表需求 |
| `SidebarEntry` | **(a)** | `ScoreboardHudElement` 已存在（在 `HudManager.java:301` 注册，并 `RiseFrostedGlass` 背景，`ScoreboardHudElement.java:18,71`） |
| `ClientTheme`（22 套主题 + RAINBOW/CUSTOM） | **(b)** | 纯色表面（`ClientTheme.java:8-56`，只依赖 `ColorUtility`/`OverlayModule`），但 `getColors()` 直接读 `OverlayModule.primaryColorProperty` 这个静态属性（50 行），移植时要把颜色来源改成参数。WurstB 侧视觉常量在 `gui/visual/VisualTheme`（`HudEditorScreen.java:113` 等在用） |
| `materialicons-regular` / `borel-regular` / `productsans-*` 图标与品牌字体 | **(c)** | 工程内无这些字体资源；`SkiaFontManager` 只提供 PingFang 三字重（`SkiaFontManager.java:33-52`），MC 侧有 `wurst:rise`（`RiseHudFont.java:12-13`）。先例：`EspIndicatorGlyphs` 用中文字形替代图标字体（见 `docs/openaopal-port.md` §3.2、`EspNameTagElement.java:20-23`） |
| NanoVG 全套（`NVGRenderer`、`nvgShapeAntiAlias`、`NVG_*`、`nvgglCreateImageFromHandle`） | **(c)** | 明确不移植（任务前提）。WurstB 侧对应能力：`SkiaRegionRenderer`/`TwilightSkia`（矢量）或 `FlatRenderer` + `GuiGraphics`（原版） |

---

## 6. 硬阻断项（带 file:line）

**B1｜没有 GUI 的 bloom / 第二遍渲染通道。**
全仓库 `grep -i bloom` **零命中**（`src/main/java` 全量）。GUI 只有一个事件
`GUIRenderListener.GUIRenderEvent`（`GUIRenderListener.java:20-46`，字段只有 `context` 与 `partialTicks`），
由 Forge `RenderGuiEvent.Post` 触发一次（`WurstForgeInitializer.java:51-57`）。
唯一的离屏后处理设施 `PostEffectQueue` 只在**世界渲染**里 flush（`GameRendererMixin.java:110`，
`PostEffectQueue.java:48-103`），且它自带的 4 个效果是 outline/pulse/gradient/smoke
（`PostEffectQueue.java:192-212`，资源见 `assets/wurst/shaders/post/target_*.json`），没有 glow/bloom。
→ 想复刻"亮部进 glow buffer 再模糊叠加"，需要在 `HudManager.onRenderGUI` 里新增一条
`TextureTarget + PostChain` 通道（可复用 `PostEffectQueue` 的写法），这是一个**新增子系统**，不是移植。

**B2｜`GUIRenderEvent` 不携带鼠标坐标。**
`RenderScreenEvent` 有 `mouseX/mouseY`（`RenderScreenEvent.java:5`），参考的拖拽与悬停全靠它
（`ScreenPositionManager.java:44-45`）。WurstB 的事件只有 `(GuiGraphics, float partialTicks)`
（`GUIRenderListener.java:20-29`）。`hud2` 已有独立编辑器屏幕（`Screen` 自带 `mouseX/mouseY`），
所以对"编辑器"不构成阻断；对"在聊天栏里拖 HUD"这种交互是阻断。
`RenderGuiEvent.Post` 在 Forge 1.20.1 是否带鼠标坐标：**未验证**。

**B3｜Skia region 管线一帧只能有一个区域，且每区域一次全缓冲上传。**
`SkiaRegionRenderer.beginRegion` 在 `regionDrawing==true` 时**直接返回同一块画布且不重设变换**
（`SkiaRegionRenderer.java:66-69`，类的 Javadoc 192-203 行自己写明了这个陷阱，并提供了
`isRegionDrawing()`(201-204) 让后到者回退）；`uploadRegion()` 上传的是
`regionCapacityPixelW × regionCapacityPixelH` 全缓冲（132-155 行，第 146-148 行），
`beginRegion` 还会整块 `canvas.clear`（103 行），`endRegion` 每次 blit（113-130 行）。
→ 顺序地"每个元素一次 begin/end"在语义上可行，但**每帧 N 个元素 = N 次全缓冲上传 + N 次清屏 + N 次 blit**；
整屏一个区域则是每帧约 8MB 上传（`docs/openaopal-port.md` §6 已有同样的结论）。
`hud2` 目前**完全没有用 Skia**（`grep SkiaRegionRenderer|TwilightSkia` 在 `hud2/` 下零命中；
只用 `FlatRenderer` + `RiseFrostedGlass`），所以"把 HUD 换成 Skia 绘制"是一个需要预算的决策。

**B4｜1.21.10 专属 API 在 1.20.1 无等价物（这些文件里用到的）。**

| 参考用法 | 位置 | 1.20.1 状况 |
| --- | --- | --- |
| `com.mojang.blaze3d.systems.RenderPass` + `RenderSystem.getDevice().createCommandEncoder().createRenderPass(...)` | `InGameHudMixin.java:136-145`、`NVGRenderer.java:68-73` | **不存在**（1.21.2+ 的 blaze3d 抽象）。1.20.1 用 `RenderTarget`/`PostChain`（`PostEffectQueue.java:112-127` 是本地先例） |
| `RenderPipelines` + `DrawContext.drawGuiTexture(RenderPipelines.GUI_TEXTURED, ...)` | `ClientElements.java:6,131` | **不存在**。1.20.1 用 `GuiGraphics.blit(...)`（本地先例：`TargetHudElement.java:349-352`、`SkiaRegionRenderer.java:122`） |
| `DrawContext.createNewRootLayer()` | `TargetInfoElement.java:248` | **不存在**（1.21.6+）。它在这里的作用是"重置当前 layer/scissor 状态再画物品"，1.20.1 无对应物 |
| `DrawContext.getMatrices()` 的 `pushMatrix()/translate(Vector3f)/scale(f,f)` | `TargetInfoElement.java:260-280` | 1.20.1 是 `GuiGraphics.pose()` → `PoseStack`，方法名 `pushPose/popPose/translate(float,float,float)/scale(float,float,float)`（本地先例：`HudManager.java:232-242`、`TargetHudElement.java:237-247`） |
| `AttributeModifierSlot.ARMOR` + `EquipmentSlot.Type.HUMANOID_ARMOR` + `getEquippedStack` | `TargetInfoElement.java:221-225` | 1.20.1 是 `net.minecraft.world.entity.EquipmentSlot`（本地先例：`CopyItemCmd.java:11,69-78`）；1.20.1 无 `AttributeModifierSlot`（1.21 才有）。护甲槽位顺序需自己列 `HEAD/CHEST/LEGS/FEET` |
| `RegistryEntry<StatusEffect>` + `mc.player.getActiveStatusEffects()` + `InGameHud.getEffectTexture(registryEntry)` | `ClientElements.java:11-13,107,115,128` | 1.20.1：`player.getActiveEffects()` 返回 `Collection<MobEffectInstance>`（本地先例：`PotionHudElement.java:53-54`），`MobEffectInstance.getEffect()` 给 `MobEffect`。**effect sprite 的取法未验证**（猜测是 `EffectRenderingInventoryScreen.getEffectSprite(MobEffect)`；该类确实存在，见 `CreativeInventoryScreenMixin.java:16`、`ScreenRegistry.java:39`，但方法名/签名未在本机核对） |
| `mc.getTextureManager().getTexture(id).getGlTexture().getLabel()`（拿 GL 纹理 id） | `TargetInfoElement.java:386` | 1.21.10 的 `getGlTexture()`/`getLabel()` 在 1.20.1 不存在。1.20.1 只能拿 `AbstractTexture.getId()`（**未验证**），而且要把纹理交给 NVG/Skia 本就需要不同的桥（本地先例：`SkiaRegionRenderer` 用 `DynamicTexture` + `blit` 回屏） |
| `nvgCreateImageFromHandle` / `NVG_IMAGE_NODELETE` | `TargetInfoElement.java:53-54,427` | NanoVG 专属，**(c)** |
| `RenderTickCounter` / `tickCounter.getTickProgress(false)` | `InGameHudMixin.java:8,79` | 1.20.1 无此类；tickDelta 走 `RenderGuiEvent.getPartialTick()`（本地已验证：`WurstForgeInitializer.java:56`）或 `Minecraft.getFrameTime()`（`HudEditorScreen.java:142`） |
| `net.minecraft.client.gui.Click`（鼠标事件 record） | `ChatScreenMixin.java:3,16,21` | 1.21.x 才有；1.20.1 是 `mouseClicked(double,double,int)`（本地先例：`HudEditorScreen.java:252`） |
| `Window.getScaledWidth()/getScaledHeight()/getScaleFactor()` | `ScreenPositionProperty.java:54,66`、`InGameHudMixin.java:83-89` | 1.20.1：`com.mojang.blaze3d.platform.Window` 的 `getGuiScaledWidth()/getGuiScaledHeight()/getGuiScale()`（本地已验证：`RiseFrostedGlass.java:123-124,161-163`、`SkiaRegionRenderer.java:74`） |

**B5｜图标/品牌字体资源缺失**（同 `docs/openaopal-port.md` §3.2 的处理）：
`materialicons-regular`（`TargetInfoElement.java:62` 的 `\uE87D` 心形、`NotificationsElement.java:27` 的类型图标）、
`borel-regular`（`DefaultIsland.java:29`）、`productsans-bold/medium/regular`
（`ClientElements.java:34-35`、`TargetInfoElement.java:60-61`、`ModuleElement.java:79`、
`NotificationsElement.java:28-29`、`DefaultIsland.java:30-31`）。
WurstB 现有字体：`assets/wurst/font/pingfang_{light,regular,semibold}.ttf`（`SkiaFontManager.java:36-52`）
与 MC 侧 `wurst:rise`（`RiseHudFont.java:12`）。→ 所有图标字形必须替换（先例 `EspIndicatorGlyphs`）。

**B6｜依赖 OpenOpal 的 Module/Property 体系。**
`ToggledModulesElement` 需要 `ModuleRepository.getModules()`（26-32 行）、
`Module.getSuffix()/isVisible()/getCategory()/getName()`（`ModuleElement.java:94-99,150-152`）、
`ModuleCategory.VALUES`（`ToggledSettings.java:33`）、`PropertyUpdateEvent`（`OverlayModule.java:111-116`）。
WurstB 的对应物是 `Hack`/`HackList`/`Category`/`HackListOtf`/`ComposeHackList`
（`HackListHUD.java:26-197`）与 `EventManager`。**语义可对齐，代码必须重写**；
参考里"后缀灰色"依赖 `Formatting.GRAY` 拼进字符串（`ModuleElement.java:99`），
1.20.1 对应 `ChatFormatting`（本地已验证：`ScoreboardHudElement.java:7`）。

**B7｜目标元素的数据源不同。**
参考：`LocalDataWatch.lastEntityAttack` + `TargetList` 白名单 + `KillAuraModule.getTargeting().getTarget()`，
且在 `ChatScreen` 打开时伪造"目标=自己"（`TargetInfoElement.java:312-342`）。
WurstB：`PlayerAttacksEntityListener`（`TargetHudElement.java:33,83-90`）+ `killauraHack/multiAuraHack.getCurrentTarget()`
+ 准星 `EntityHitResult`（`TargetHudElement.java:178-202`），预览靠 `ScreenRegistry.HUD_EDITOR.isOpen()`
（`TargetHudElement.java:122-128`）。→ 逻辑要重写，但 WurstB 侧已经跑通。

**B8｜未验证清单（不得当作事实引用）**
1. 1.20.1 取状态效果贴图的确切 API（见 B4）。
2. `GuiGraphics.guiWidth()/guiHeight()` 是否就是 GUI 缩放尺寸：本报告按"是"使用，
   依据是 `HudManager`/`HudEditorScreen` 把它与 `width/height`（`Screen` 的 GUI 缩放尺寸）混用
   （`HudManager.java:129-130`、`HudEditorScreen.java:114-117`），**未查 1.20.1 源码**。
3. Forge 1.20.1 `RenderGuiEvent.Post` 是否暴露鼠标坐标（B2）。
4. `AbstractTexture.getId()` 是否能拿到 GL 纹理 id（B4）。
5. `GuiGraphics.renderItem` 之外是否有 1.20.1 的 GUI 物品光照控制（参考的 `DiffuseLighting` 相关行被注释掉了，
   `TargetInfoElement.java:252`）。

---

## 7. 建议移植顺序（小 → 大）

约定：纯逻辑放 `util/hud/`（或既有 `util/esp/` 旁边），**不含 `net.minecraft.`**，
按 `EspNameTagLayout` 的注入风格（`EspNameTagLayout.java:55-60`）写，配套单测放
`src/test/java/net/wurstclient/util/hud/`；**不改任何既有设置的名称/默认值/取值范围**。

1. **`Easings`（30 条曲线）+ `HudTween`（时长制补间）** —— 最小、可纯单测、后续每一步都依赖它；
   语义直接对齐 `UiTween`（`UiTween.java:40-51`：钳制进度 + `Math.max(1,duration)`），
   把参考 `Easing.java:8-35` 的曲线表搬过来即可（正弦三条换掉 `MathHelper`）。
   理由：先有统一动画口径，后面所有元素才不会各写一套。
2. **`HudPlacement`（相对坐标/锚点/吸附纯几何）** —— 把参考 `ScreenPositionProperty.getScaledX/setRelativeX/snapToGrid`
   （52-124 行）的几何语义做成纯函数，供 `HudEditorScreen` 与未来的"聊天栏拖拽"共用；
   `HudLayout` 的锚点模型保持不动（它更强）。理由：这是定位层唯一真正缺的东西，且零渲染依赖。
3. **通知元素补齐（模块开关通知开关 + 事件钩子）** —— `HudNotificationRenderer` 与
   `HudNotification.lifetimeProgress`（已钳制）都已就绪，只缺一个"模块开关时发通知"的开关项与回调；
   顺带把 §3-D4 的教训写成测试（进度必须钳制）。理由：最小的"新增可开关元素"闭环。
4. **`HackList` 增强（Bar mode / Offset scoreboard / Visible categories / 列表高度）** ——
   在既有 `HackListHUD`/`HackListOtf` 上加设置，不改默认值；`getTotalHeight()` 供记分板重排
   （参考 `InGameHudMixin.java:225-245` 的口径）。理由：模块列表是最有辨识度的 HUD，且数据源现成。
5. **`ClientElements` 式角标合并（XYZ/BPS/FPS 多行 + 状态效果列）** ——
   复用 `TextHudElement`（`TextHudElement.java:9-45`）与 `PotionHudElement` 的数据读取
   （`PotionHudElement.java:49-68`），只补"合并 + 前缀渐变 + 效果图标（字形替换）"。
   理由：单文件、无新数据源，但需要先定图标替代方案（B5）。
6. **`TargetInfoElement` 增强（并进 `TargetHudElement`）** —— 头像用
   `graphics.blit(skin, ...)`（`TargetHudElement.java:341-357` 已验证可用）替换 NVG image handle；
   装备栏几何用 `EspEquipmentLayout`（已存在）、附魔短名用 `EspEnchantNames`（已存在）
   与 `PlayerEspHack.java:430-457` 的 1.20.1 取法（`EnchantmentHelper.getEnchantments` + `BuiltInRegistries.ENCHANTMENT.getKey`）；
   双血条动画改用第 1 步的 `HudTween`。理由：最大件，且它的两个难点（装备/附魔、头像）都已有本地先例。
7. **动态岛（`IslandPriority` 纯逻辑 + 一个触发者）** —— 先只做"优先级表 + 表头选择 + 自定义坐标"
   这层纯逻辑与单测，再挑一个最少依赖的触发者（如音乐岛，`MusicIslandHudElement` 已是同类 UI）。
   理由：模型漂亮但收益依赖触发者数量，优先级低于目标元素。
8. **bloom / 第二遍通道** —— 最后做，且**必须重新设计**：新增一条 GUI 后处理通道
   （仿 `PostEffectQueue`），并给 `HudElement` 加一个**独立的** `renderGlow(...)` 入口，
   而不是照抄"同一帧把 `render()` 跑两遍 + `isBloom` 标志"（§3-D5 已证明会重复入队）。
   理由：这是唯一需要新增渲染子系统的项，且现有 `RiseFrostedGlass` 已能满足大部分"磨砂"观感。

---

## 8. 诚实声明

- 本文件所有行号来自本次实际打开的文件（列表见文首）；凡未打开的文件一律未引用。
- §3 的缺陷分三类：**可达且已给出具体输入**（D3/D4/D5/D6）、**潜在但已验证不可达并说明原因**（D1/D3 的 `getFirst()`）、
  **死代码**（D7）。没有把"看起来可疑"写成"缺陷"。
- 未在游戏内验证任何视觉效果；`hud2` 与参考的观感差异需要实机比对。
- §6-B8 列的 5 条为**未验证**，实现前需先在 1.20.1 侧确认。
