# WurstB+ Plus 移植与修复任务清单

Updated: 2026-08-29

本文档记录 WurstB+ Plus 各平台工程的移植状态与修复任务。已完成任务含根因、改动与验证；待办任务含现状、方法与验证方式，供后续接手直接继续。

---

## 一、已完成修复任务

### 1. Baritone 26.2 compatibilityLevel 修复（解决启动闪退）

**影响版本**：NeoForge 26.2（`neoforge/versions/26.2`），同一问题存在于 Forge/Fabric 26.2（baritone-maven 已修，工程未重建）。

**根因**：
- WurstB+ 通过 jarJar 内嵌 `baritone-neoforge-1.18.0-26.2.jar`，其 `mixins.baritone.json` 声明 `"compatibilityLevel": "JAVA_17"`，但 Baritone mixin 类用 Java 25（class version 69）编译。
- Mixin 应用 `MixinEntity`（注入 `Entity.moveRelative`）时静默崩溃——日志停在 `Closing FML Loader` / `Clearing ModLoader`，无 Java 异常、无 crash-report、无 hs_err（debug.log 中有警告：`Class version 69 required is higher than ... (JAVA_17 supports class version 61)`）。

**改动**：
- `_tools/baritone-26.2-compat/src/tools/BaritoneCompatibilityPatcher.java`：新增 `patchMixinConfig()`，打包时把 `"compatibilityLevel": "JAVA_17"` 替换为 `"JAVA_25"`（新增常量 `MIXIN_CONFIG_ENTRY`，主循环里对 `mixins.baritone.json` 调用）。
- `_tools/baritone-26.2-config/{fabric,forge,neoforge}/mixins.baritone.json` 与 `_tools/baritone-26.2-repack/{fabric,forge,neoforge}/mixins.baritone.json`：6 个 json 全部改为 `JAVA_25`。
- 重跑 `scripts/patch-baritone-26.2.ps1`（依赖 JDK 25、ASM 9.10.1、Minecraft 26.2 compile jar），重新生成 `baritone-maven` 下 3 个 Baritone jar。
- 重建 `neoforge/versions/26.2`（`./gradlew build`，期间发现并修复了 `gradle-wrapper.jar` 缺 `Main-Class` 问题，见已完成任务 4）。
- 部署：新 jar 复制到测试实例 `D:\.penguin\.modpack\.minecraft\versions\芝士狐狸\mods`，删除旧 `.disabled` 文件；清理 `.cache/jij` 里旧的 `JAVA_17` baritone 缓存。

**验证**：`baritone-maven` 3 个 jar 与最终 `WurstB+ Plus-v1.5.0-NeoForge-26.2.jar` 内嵌 baritone 的 `mixins.baritone.json` 均为 `JAVA_25`。

> 注：Forge/Fabric 26.2 的重建已完成（见已完成任务 5），三个 26.2 平台产物现均内嵌 `JAVA_25` baritone。

### 2. fabric 26.1.2 摄像机渲染（3D ESP/透视）失效修复

**影响版本**：fabric 26.1.2（`fabric/versions/26.1.2`）。同一问题存在于 Forge/NeoForge 26.1.2（已完成任务 3）。

**根因**：
- 26.1.2 的 `LevelRenderer.renderLevel` 使用 FrameGraph 管线（`FrameGraphBuilder.execute()` 统一执行所有渲染 pass）。
- Wurst 的 3D 渲染原来用 `getVCP()`（`MC.renderBuffers().bufferSource()`）在 `renderLevel` 的 RETURN 钩子里直接提交几何并 `endBatch()`——但此时 FrameGraph 已执行完毕，这些几何**永远不会被渲染**。因此 HoleESP、PlayerESP、ChestESP 等所有"摄像机上的渲染"（世界空间 3D 渲染）全部失效。
- 26.2 没有此问题：其 `RenderUtils` 用 `submit()`（`SubmitNodeStorage.submitCustomGeometry`）提交几何，几何被收集进节点存储、在正确渲染 pass 中绘制。

**改动**（`fabric/versions/26.1.2`）：
- `net/wurstclient/util/RenderUtils.java`：
  - 新增 submit 机制：`submitNodeStorage` 字段、`setSubmitNodeStorage()`、`submit(PoseStack, RenderType, Consumer<VertexConsumer>)`、`submitText(...)`。
  - 15 个 3D 渲染方法从 `getVCP()` 模式改为 `submit()` 模式：`drawLine`、`drawTracer`、`drawTracers`（×2 重载）、`drawCurvedLine`、`drawSolidBox(es)`（×3）、`drawOutlinedBox(es)`（×3）、`drawCrossBox(es)`（×3）、`drawNode`。
  - 新增 imports：`java.util.function.Consumer`、`net.minecraft.client.renderer.SubmitNodeStorage`、`net.minecraft.util.FormattedCharSequence`；删除无用 `com.mojang.blaze3d.opengl.GlConst`。
- `net/wurstclient/mixin/LevelRendererMixin.java`：
  - 新增 `@Shadow @Final private SubmitNodeStorage submitNodeStorage;`。
  - `onRender`（`renderLevel` RETURN 钩子）开头调用 `RenderUtils.setSubmitNodeStorage(submitNodeStorage)`。

**验证**：`./gradlew compileJava` 与 `./gradlew build` 均通过（`BUILD SUCCESSFUL`），产物 `build/libs/WurstB+ Plus-1.5.0-Fabric-26.1.2.jar`（33,488,622 bytes）。运行期需进世界开启 HoleESP/PlayerESP 确认渲染。

**参考**：
- README「26.1.2 渲染管线说明」（extract/render 分离、文字需 8 位 Alpha 等）。
- 已确认 26.1.2 的矩阵链正确：`Camera.getViewRotationMatrix()` 用 `Matrix4f.rotation()` 只含旋转，`positionMatrix` + `getCameraPos().reverse()` 组合为标准做法，非双投影偏移。

### 3. Forge / NeoForge 26.1.2 摄像机渲染修复（与 fabric 26.1.2 相同问题）

**影响版本**：`versions/26.1.2`（Forge）与 `neoforge/versions/26.1.2`（NeoForge）。根因与 fabric 26.1.2 相同（FrameGraph 管线导致 getVCP 模式几何不被渲染），见已完成任务 2。

**改动**：
- 先修复脚本 `_tools/fix-renderutils-2612.js` 的匹配失败问题：**根因是文件为 CRLF 换行**（此前误记为 LF），脚本用 LF 签名（`\n\t\t`）匹配必然失败。脚本新增 `readNormalized()`/`writeNormalized()`（读时 `\r\n`→`\n`，写回时 `\n`→`\r\n`），并补充移除无引用 `com.mojang.blaze3d.opengl.GlConst` import 的逻辑。
- 运行 `node _tools/fix-renderutils-2612.js`：从已修复的 fabric 26.1.2 `RenderUtils.java` 提取 15 个 3D 方法 + submit 机制块，替换 Forge/NeoForge 的对应内容；补 imports（`java.util.function.Consumer`、`SubmitNodeStorage`、`FormattedCharSequence`），删 `GlConst`。
- `versions/26.1.2`（Forge）与 `neoforge/versions/26.1.2`（NeoForge）的 `LevelRendererMixin.java`：均新增 `@Shadow @Final private SubmitNodeStorage submitNodeStorage;` 与 `RenderUtils.setSubmitNodeStorage(submitNodeStorage)`。
  - NeoForge 26.1.2 与 fabric 一致（`RenderEvent` 3 参数含 projectionMatrix），修改后与 fabric 的 `LevelRendererMixin.java` 逐字节一致。
  - Forge 26.1.2 保留 2 参数 `new RenderEvent(matrixStack, tickProgress)`。

**验证**：
- 脚本输出：两个目标各「已插入 submit 机制」「替换了 15 个方法」。
- 逐字节校验：两个目标的 15 个 3D 方法体与 fabric 26.1.2 修复后完全一致；`submitNodeStorage` 机制块与 imports 一致；`getVCP()` 仅剩定义 1 处；`GlConst` 0 处；CRLF 换行保留。
- `versions/26.1.2` 与 `neoforge/versions/26.1.2` 各自 `./gradlew build` 均 `BUILD SUCCESSFUL`。
- 运行期需进世界开启 HoleESP/PlayerESP/ChestESP 确认渲染。

### 4. gradle-wrapper.jar Main-Class 修复

**现状**：仓库内所有活动工程的 `gradle/wrapper/gradle-wrapper.jar`（62,076 bytes）MANIFEST 均缺 `Main-Class`，`java -jar` 直接报「没有主清单属性」。`neoforge/versions/26.2` 此前已用完整 jar 修复。

**改动**：用完整 wrapper jar（`neoforge/versions/26.1.2/gradle/wrapper/gradle-wrapper.jar`，43,764 bytes，含 `Main-Class: org.gradle.wrapper.GradleWrapperMain` + `Enable-Native-Access: ALL-UNNAMED`）统一替换以下 12 处：
`gradle/`（根）、`versions/1.21.1`、`versions/1.21.11`、`versions/26.1.2`、`versions/26.2`、`fabric/`、`fabric/versions/1.21.1`、`fabric/versions/1.21.11`、`fabric/versions/26.1.2`、`fabric/versions/26.2`、`neoforge/`、`neoforge/versions/1.21.11`。

**验证**：
- 12 处替换后 `jar xf <jar> META-INF/MANIFEST.MF` 均含 `Main-Class: org.gradle.wrapper.GradleWrapperMain` 与 `Enable-Native-Access: ALL-UNNAMED`。
- 兼容性实测：43,764 bytes 完整 jar 分别配合 Gradle 8.11 / 9.4.1 / 9.6.0 的 `gradle-wrapper.properties`，`java -jar <jar> --version` 均正确启动对应发行版（wrapper 按 jar 所在目录的 properties 解析发行版，版本无关）。
- 注：`gradlew.bat` 用 `-classpath ... org.gradle.wrapper.GradleWrapperMain` 调用（非 `-jar`），故缺 Main-Class 不影响 `gradlew.bat` 构建；此修复保证 `java -jar` 方式与外部工具检查可用。

### 5. forge / fabric 26.2 重建（Baritone 修复生效）

**现状**：`baritone-maven` 下 3 个 Baritone 26.2 jar 已重打包（`compatibilityLevel: JAVA_25`），但 `versions/26.2`（Forge）与 `fabric/versions/26.2` 的 WurstB+ jar 仍内嵌旧 `JAVA_17` baritone（重建前实测 `META-INF/jarjar/baritone-forge-1.18.0-26.2.jar` 与 `META-INF/jars/baritone-api-fabric-1.18.0-26.2.jar` 均为 `JAVA_17`）。

**改动**：
- `versions/26.2`：`./gradlew allJar test` → `BUILD SUCCESSFUL`。
- `fabric/versions/26.2`：`./gradlew build` → `BUILD SUCCESSFUL`。

**验证**：新产物内嵌 baritone 的 `mixins.baritone.json` 均为 `JAVA_25`：
- `versions/26.2/build/libs/WurstB+ Plus-v1.5.0-Forge-26.2.jar`（31,432,218 bytes）→ `META-INF/jarjar/baritone-forge-1.18.0-26.2.jar` = `JAVA_25`。
- `fabric/versions/26.2/build/libs/WurstB+ Plus-1.5.0-Fabric-26.2.jar`（33,493,882 bytes）→ `META-INF/jars/baritone-api-fabric-1.18.0-26.2.jar` = `JAVA_25`。

### 6. 六版本移植基线（1.21.11 / 26.2）— 历史状态

> 来自原文档，作为背景保留。六个工程（Forge/Fabric/NeoForge × 1.21.11/26.2）已完成移植与冒烟验证。

**Verification Matrix**：

| Project | Compile/build | Tests | Client startup | Baritone |
| --- | --- | --- | --- | --- |
| Forge 1.21.11 | `clean allJar test` passed | 135/135 | World loaded | `#goto 0 88 0` passed |
| Forge 26.2 | `clean allJar test` passed | No test sources | World loaded | `#goto 0 88 0` passed |
| Fabric 1.21.11 | `clean build` passed | 135/135 | World loaded | `#goto 0 88 0` passed |
| Fabric 26.2 | `clean build` passed | No test sources | World loaded | `#goto 0 88 0` passed |
| NeoForge 1.21.11 | `clean build` passed | 135/135 | World loaded | `#goto 0 88 0` passed |
| NeoForge 26.2 | `clean build` passed | No test sources | World loaded | `#goto 0 88 0` passed |

**Packaging**：六个最终 client jar 均 jarJar 内嵌对应 Baritone（`META-INF/jarjar/`，Forge/NeoForge）或 `META-INF/jars/`（Fabric），并含 Java-WebSocket 与 Netty proxy 依赖。

**Final Runtime Validation**：六个发布 JAR 均通过真实启动、进入 `WurstSmokeFresh` 单人世界、`#goto 0 88 0` 响应验证（报告见 `.test/report-*`）。

---

### 7. fabric 26.1.2 雷达崩溃修复（GitHub issue #2）

**影响版本**：fabric 26.1.2（`fabric/versions/26.1.2`）。同一代码模式存在于 fabric 26.2 与 neoforge 26.2（`RenderUtils.fillQuads2D`/`submitQuadMesh2D` 通过 `GuiGraphicsExtractorAccessor` 读取 `GuiGraphicsExtractor.guiRenderState`），一并修复。

**根因**（2026-08-30 在 `.test` 环境完整复现，zh_cn 语言下 100% 崩溃）：
- `RenderUtils.fillQuads2D`/`submitQuadMesh2D` 用 `((GuiGraphicsExtractorAccessor)(Object)context).wurst_getGuiRenderState()` 拿 `GuiRenderState` 后 `addGuiElement(...)` 提交自定义 GUI 几何（雷达箭头、勾选标记、折线等）。
- 崩溃堆栈：`RenderUtils.fillQuads2D` → `ClassLoader.loadClass(GuiGraphicsExtractorAccessor)` → `IllegalClassLoadError: The mixin is missing from wurstpenguin.mixins.json ... and the mixin has not been applied`。
- 现象：**游戏语言为 zh_cn 时必崩，en_us 不崩**（实测对比）。zh_cn 下 accessor 接口在目标类 `GuiGraphicsExtractor` 被 Mixin 转换前就已被加载（`-verbose:class` 实测：accessor 于 18.585s 加载、目标类 18.586s 加载），Mixin 判定 accessor "not applied"，运行期从游戏代码加载该接口即抛 IllegalClassLoadError。en_us 下加载时序正常。其余 accessor（`ChatComponentAccessor` 等）不受影响。
- 触发路径：雷达窗口 `pinned=true` 且启用时（`enabled-hacks.json` 恢复或 `.t Radar`），`IngameHUD.onRenderGUI` → `getGui()`（首次触发 `ClickGui.init()`）→ `renderPinnedWindows` → `RadarComponent.render`（第 93 行无条件 `ClickGuiIcons.drawRadarArrow`）→ `fillQuads2D` → 崩溃。即「开启雷达 → 进世界首帧即崩」，与用户 issue #2 描述一致。

**改动**（三个工程，`net/wurstclient/util/RenderUtils.java`）：
- 移除 `GuiGraphicsExtractorAccessor` 的使用：新增 `GUI_RENDER_STATE_FIELD`（`GuiGraphicsExtractor.class.getDeclaredField("guiRenderState")` + `setAccessible(true)`，静态块初始化）与私有方法 `getGuiRenderState(GuiGraphicsExtractor)`（反射 `Field.get`），`fillQuads2D`/`submitQuadMesh2D` 改调该方法。
- 反射绕开 accessor 接口的类加载检查，彻底规避 IllegalClassLoadError；`Field.get` 经 JIT 内联后性能可接受（每帧数百次调用量级）。
- 保留 `GuiGraphicsExtractorAccessor.java` 与 mixins.json 注册不动（无其他引用）。
- 补充 imports：`java.lang.reflect.Field`、`net.minecraft.client.renderer.state.gui.GuiRenderState`。

**测试环境附加问题（已修）**：`.test/versions/26.1.2-Fabric 0.19.3/mods/` 下有 `_bak-fabric-26.1.2-prefix.jar`（8/13 手动备份，以 `.jar` 结尾，Fabric 会加载；`_bak-` 前缀字母序靠前，其旧版 `RenderUtils`（accessor 版）先于正式 jar 被加载，导致新 jar 的修复在测试中被旧类遮蔽）。已 rename 为 `.jar.disabled`。**教训：实例 mods/ 下非 `.disabled` 的 WurstB jar 只能有一个**。

**验证**：
- `fabric/versions/26.1.2`、`fabric/versions/26.2`、`neoforge/versions/26.2` 三个工程 `./gradlew build` 均 `BUILD SUCCESSFUL`。
- fabric 26.1.2 新 jar 部署到 `.test` 实例后，zh_cn + `enabled-hacks.json` 含 Radar（雷达自动启用）+ quick-play 进世界：**3 分钟无崩溃**（修复前 100% 崩溃，crash report 同栈）。
- javap 确认新 jar `fillQuads2D` 字节码为 `invokestatic getGuiRenderState`，无 accessor 引用。

**遗留**：#3（设置窗口 UI 偏移）仍未定位（见待办任务）。

### 8. NeoForge/Forge 26.1.2/26.2 启动崩溃修复（EntityMixin @WrapOperation 注入失败）

**用户报告**（2026-08-30）：D 盘 NeoForge 26.2 实例（芝士狐狸，客户端 `minecraft-client-patched-26.2.0.69`）启动即崩溃，Forge 崩溃界面：
`InjectionError: Critical injection failed: Callback method wrapUpdateFluidInteractionIsPushedByFluid ... failed injection check, (0/1) succeeded. Scanned 9 targets. No refMap loaded.`

**根因**：
- `EntityMixin.wrapUpdateFluidInteractionIsPushedByFluid` 用 `@WrapOperation(method = "updateFluidInteraction()Z", at = @At(INVOKE, target = "...Entity;isPushedByFluid()Z", ordinal = 0))` 包装 `isPushedByFluid()` 调用（AntiWaterPush 的 VelocityFromFluidEvent 触发点）。
- **NeoForge 的 patched 客户端 jar 里 `updateFluidInteraction()` 不再调用 `isPushedByFluid()`**（26.2.0.69 的 NeoForge coremod 把流体系统重构为 `EntityFluidInteraction`，`isPushedByFluid` 在 patched Entity 中 0 调用点；javap 实测）。
- Forge/NeoForge 的 mixin 对注入失败是**硬失败**（config `required=true`）→ 启动崩溃。Fabric 用官方（未 patched）客户端 jar，`updateFluidInteraction` 仍调用 `isPushedByFluid`，且 Fabric 注入失败为软失败，故 fabric 未暴露。

**改动**（6 个工程：fabric/forge/neoforge × 26.1.2/26.2 的 `net/wurstclient/mixin/EntityMixin.java`）：
- `@WrapOperation(...)` 增加 **`require = 0`**：目标存在时照常注入（vanilla/Fabric），目标不存在（NeoForge/Forge patched）时不再报错崩溃。
- 代价：AntiWaterPush 的流体推送拦截在 NeoForge patched 环境下暂不生效（注入被跳过）；后续可基于 `EntityFluidInteraction` 重构适配。

**验证**：
- 6 个工程 `./gradlew build` 均 `BUILD SUCCESSFUL`。
- 新 jar（`WurstB+ Plus-v1.5.0-NeoForge-26.2.jar`，33,499,556B）已部署到 `D:\.penguin\.modpack\.minecraft\versions\芝士狐狸\mods\`（旧 8/13 jar 已重命名 `.disabled`），待用户启动确认。
- javap 确认新 jar `EntityMixin` 的 `@WrapOperation` 注解含 `require` 属性。

> 注：此 jar 同时包含任务 7 的 RenderUtils 反射修复（zh_cn 雷达崩溃）与本次 require=0 修复。

**同类问题排查（2026-08-30 用户确认修复后）**：
- **26.1.2/26.2**（fabric/forge/neoforge 共 6 工程）：EntityMixin `require = 0` 已加；用户 NeoForge 26.2 实测启动成功，**其余全部 mixin 注入（LocalPlayerMixin/PlayerMixin/CameraMixin 等 30+ 处 @WrapOperation/@Redirect/@ModifyVariable）在 patched 环境均正常**。
- **1.21.11**（fabric/forge/neoforge 共 3 工程）：EntityMixin 用旧流体 API（`updateFluidHeightAndDoFluidPushing`），同样加 `require = 0` 防 patched 更新后失效；三个工程 `./gradlew build` 均 `BUILD SUCCESSFUL`。
- **1.21.1**（fabric/forge/neoforge）：EntityMixin 用 `@Redirect` 版本（`updateFluidHeightAndDoFluidPushing` + `setDeltaMovement`），**`require = 0` 已存在**，无需改动。
- **1.20.1**：不在当前仓库源码工程范围内（.test 实例为历史构建），无需处理。

---

## 二、待办任务

### 1. GitHub issue #3（设置窗口 UI 偏移）

**仓库**：`xiegeezr886/WurstB-Plus`，fabric 26.1.2。「右键功能的设置窗口会跑到左上角，但字不会跑」。目前无用户 crash-report / debug.log，已做静态排查（2026-08-29），未能定位确定性根因，需继续：

- 已排查（2026-08-29 新增）：
  - `SettingsWindow.java`、`Window.java`、`FlatRenderer.java`、`GuiGraphicsExtractorAccessor.java` 在 fabric 26.1.2 与 26.2 之间**逐字节一致**（`SettingsWindow`/`Window` diff=0），排除平台代码差异。
  - 设置窗口创建路径：`ClickGuiScreen`（右键 `clickRow`）→ `new SettingsWindow(feature, x2 + 6, y1 + HEADER_HEIGHT + row * ROW_HEIGHT)`，`x2`/`y1` 为 `TemplateWindow` 的**绝对屏幕坐标**（`moveTo` 已 clamp 到屏内），无矩阵变换包裹，坐标语义正确。
  - 渲染路径：`renderWindow` 中背景用 `FlatRenderer.drawWindowPanel(context, x1, y1, ...)`（绝对坐标），子组件（含文字）在 `matrixStack.translate(x1, y4)` 后以局部坐标绘制；`QuadMeshRenderState` 构造时 `new Matrix3x2f(pose)` 快照与原生 `ColoredRectangleRenderState` 均用 `addVertexWith2DPose(pose, x, y)`，坐标语义一致。**背景与文字在代码上不可能分家**。
  - 主题色：`FlatTheme` 全部返回 8 位 ARGB（`a << 24 | ...`），非 6 位透明色；文字「不跑」不是主题透明所致。
- 待查：需在游戏内复现。重点怀疑运行时因素——`getGuiScaledWidth()`/`getGuiScaledHeight()` 在特定 GUI 缩放/分辨率切换时返回异常值，使 `Window.getX()/getY()` 的 `Mth.clamp(x, 0, max(0, scaled - width))` 把窗口钳到 (0,0)；以及 `QuadMeshRenderState.bounds()` 未对 pose 做 `transformMaxBounds`，层叠排序（`GuiRenderState.findAppropriateNode`）可能错位（表现为层级问题而非位移）。

**流程**：向用户索取 crash-report / debug.log（或复现视频/截图）→ 复现 → 定位 → 修复 → 构建 → 关闭 issue。

---

## 三、构建与验证命令

| 工程 | 命令（Windows bash） |
| --- | --- |
| Forge 26.1.2 | `cd versions/26.1.2 && export JAVA_HOME="C:/Program Files/Java/jdk-25.0.4" && ./gradlew build` |
| NeoForge 26.1.2 | `cd neoforge/versions/26.1.2 && export JAVA_HOME="C:/Program Files/Java/jdk-25.0.4" && ./gradlew build` |
| Fabric 26.1.2 | `cd fabric/versions/26.1.2 && export JAVA_HOME="C:/Program Files/Java/jdk-25.0.4" && ./gradlew build` |
| Forge 26.2 | `cd versions/26.2 && export JAVA_HOME="C:/Program Files/Java/jdk-25.0.4" && ./gradlew build` |
| NeoForge 26.2 | `cd neoforge/versions/26.2 && export JAVA_HOME="C:/Program Files/Java/jdk-25.0.4" && ./gradlew build` |
| Fabric 26.2 | `cd fabric/versions/26.2 && export JAVA_HOME="C:/Program Files/Java/jdk-25.0.4" && ./gradlew build` |
| Baritone 26.2 重新打包 | `powershell -ExecutionPolicy Bypass -File scripts/patch-baritone-26.2.ps1`（需 JDK 25 + ASM + MC 26.2 compile jar） |

> 注：26.1.2 使用 Gradle 9.4.1 wrapper。所有活动工程的 `gradle-wrapper.jar` 已统一替换为含 `Main-Class` 的完整 wrapper（43,764 bytes，见已完成任务 4），`java -jar` 与 `gradlew.bat` 均可用。

---

# 2026-09-25 会话：运行时缺陷清查（真实启动验证）

上文记录的是更早几轮的静态检查成果。本次会话换了判据——**真启动客户端**——结果推翻了一部分
"已完成"的结论，并暴露出一个此前完全没被发现的缺陷大类。以下是可交接的状态。

## 一、方法论（比单条修复更重要）

1. **混入的注入点/访问器目标没有任何编译期校验。** 本仓库这个版本线上的注解处理器不生成
   refmap，等于完全不校验。已用对照实验确认：把一个目标方法早已不存在的过时混入注册进去，
   `clean compileJava` 照样 `BUILD SUCCESSFUL`。**"编译通过"不能作为"客户端能跑"的证据。**
2. **冒烟判据要看准。** 我最初用日志里的 `Sound engine started`（资源重载阶段）当"启动成功"，
   会产生**假通过**：混入注入是在目标类被加载时才应用的，可能晚于该判据。铁证是
   `PlayerMixin.java` 在 1.21.6 与 1.21.7 里**逐字节相同**、都注册了、配置的 `defaultRequire`
   也都是 1、目标方法在两个版本都不存在，却只有 1.21.7 崩——另一个只是还没走到就被结束了。
   **可信判据至少要"见到主菜单"，最好进一次世界。**
3. **判据本身也要被验证。** 本会话我两次因工具自身缺陷误判：一次是幂等守卫用子串判断（注释行
   含同样子串）导致 30 个文件被误判为"已完成"而跳过；一次是固定日志路径被并发运行的进程写乱，
   把 `1.21.1` 记成"10 秒启动成功"。**工具的结论要用别的方式交叉验证。**

## 二、已修复并推送（4 个提交）

| 提交 | 内容 | 验证 |
| --- | --- | --- |
| `3fdf47b` | 国内镜像（4 处）+ 启动脚本 + 资源预取 + 两个启动崩溃 | 见下 |
| `c12fc91` | `neoforge/26.1`、`26.1.1` 的非法 `mod_version` | 两工程 `runClient` 实测启动 |
| `c77c5f9` | 删除 1.21.3–1.21.5 失效的 `BlockMixin` 注入 | 编译通过 |
| `b78ac0d` | `tickDownDuration` 改 void 形式（1.21.5–26.3） | 编译通过，实测越过该失败 |
| `d444107` | `1.21.7` 的 `neo_version_range` 补 `-beta` | 实测错误消失 |
| `362b958` | `PlayerMixin` 的 `keepSprintMotion` 加 `require = 0`（1.21.6–1.21.10） | 编译通过 |

其中 `3fdf47b` 含两个**启动即崩**的真实缺陷（真启动才发现）：
- 内置 Baritone 的 `mixins.baritone.json` 声明 `compatibilityLevel: JAVA_25`，而 Forge 26.2 自带的
  Mixin 0.8.7 里该枚举**最高只到 `JAVA_21`** → 混入子系统初始化直接中止。全仓库其余 baritone jar
  都是 `JAVA_17`，只有 `1.18.0-26.2` 三个是异类。`baritone-maven/` 被 .gitignore，故做成可复现
  脚本 `scripts/patch-baritone-mixin-level.py`。
- `ToastManagerAccessor` 的 `@Accessor("queued")` 声明返回 `List<Toast>`，而 MC 自 **1.21.3** 起该
  字段是 `Deque<Toast>`。Mixin 按擦除后的描述符精确匹配，类型不符即找不到候选。26.x 线三棵树
  共 15 个文件，已全部改为 `Deque<Toast>`（调用方只用 `removeIf`，无需改动）。

修复后的 26.2 Forge 实机结果（唯一做过深度验证的一个）：

```text
Successfully loaded Mixin Connector [baritone.launch.BaritoneMixinConnector]
Setting user: Dev / Backend library: LWJGL 3.4.1+2
Starting WurstB+ Plus... / [nether-pathfinder] Loaded shared library
[HUD] Notification system started / Sound engine started
```

## 三、崩溃清单：neoforge 线（23 个工程，唯一扫完的一条线）

浅层判据下：**浅层通过 9、崩溃 14**（"浅层通过"含义见一.2，不等于健康）。崩溃聚成几类：

| 类别 | 工程 | 状态 |
| --- | --- | --- |
| `neo_version_range` 漏 `-beta` 限定符 | 1.21.2、1.21.7 | **已修** |
| 混入目标失效 `PlayerInventoryMixin`（目标 `swapPaint(D)V` 在 1.21.3+ 已从 `Inventory` 移除） | 1.21.2、1.21.4、1.21.5 | 未修，诊断完成 |
| 混入目标失效 `PlayerMixin`（`causeExtraKnockback` 只在 1.21.11 存在） | 1.21.7、1.21.8、1.21.10 | **已修**（`require = 0`） |
| baritone 的 `MixinMinecraft` 描述符不匹配 | 1.21.3 | 未修，需与该 MC 版本匹配的 baritone 构建 |
| 构建失败（仅有 Gradle 任务摘要，无 FATAL） | 1.20.4、1.20.6、1.21 | 未查 |
| 首次下载资源超时（NeoForm 自下、不走镜像） | 1.20.2、1.20.3、1.20.5 | 环境耗时，非缺陷 |
| 未编译通过（脚手架） | 26.3 | 见四 |

**fabric 与 forge（`versions/*`）两棵树从未做过同样扫描**，所以清单不完整。

## 四、26.3 的状态与精确修法

三个 26.3 工程（`versions/` `fabric/versions/` `neoforge/versions/`）`compileJava` 已通过，但
`runClient` **三棵树全部崩在同一处**：

```text
InvalidAccessorException: No candidates were found matching swingTime:I
  in net/minecraft/world/entity/LivingEntity
  for wurst.mixins.json:LivingEntityAccessor
```

根因：**26.3 把 `LivingEntity.swingTime` 换成了对象化机制**（26.1.2 与 26.2 都还是
`public int swingTime`，javap 已确认；26.3 整个 jar 里已无该字符串）：

```text
LivingEntity.swingState        (private final SwingState)
LivingEntity.getCurrentSwing() (public -> SwingDescription 记录: hand/animation/durationTicks)
LivingEntity$SwingState        (private int ticks / private float animation / isSwinging())
```

`SwingState.ticks` 才是旧 `swingTime` 的语义对应（`durationTicks()` 是挥动总时长，不是同一个量）。
唯一调用方是 `MultiAuraHack:866`（用对手挥动进度做节流）。修法三步：

1. 新增 `@Mixin(LivingEntity.SwingState.class)` 的访问器，暴露 private 的 `ticks`；
2. `LivingEntityAccessor` 里把 `@Accessor("swingTime")` 改为 `@Accessor("swingState")`（也是 private）；
3. 调用点改为 `((LivingEntityAccessor)living).wurst_getSwingState().wurst_getTicks() > ...`。

**注意 `@Accessor` 没有 `require` 参数**，字段找不到就是致命错误——不能像 `PlayerMixin` 那样用
`require = 0` 绕过，26.3 在改完之前一直起不来。三棵树同一改法。`attackStrengthTicker` 在 26.3
仍存在，那一半不用动。

## 五、环境事实（本会话搭建，交接时可直接用）

- **国内镜像落在四处**（细节见 `docs/CLIENT-LAUNCH.md`）：各工程 `settings.gradle` 的
  `pluginManagement`（插件）、`gradle/init-mirrors.gradle`（依赖）、23 个 fabric 工程的
  `gradle.properties`（Loom 的 `loom_*` 三项）、67 个 wrapper 的 `distributionUrl`（腾讯云）。
- **资源库已全量预取并合并**：`.minecraft/assets`（9222 对象 / 15 索引）已合并进
  `~/.gradle/caches/fabric-loom/assets`（→9222）与 `~/.gradle/caches/neoformruntime/assets`
  （→9365）。对象内容寻址、跨版本共用，因此 fabric/neoforge 工程不再需要那 435MB 首次下载。
  重新预取用 `python scripts/fetch-assets.py <版本>` 或 `--all`。
- **JDK 实际路径**（与 `scripts/common.ps1` 的默认值不一致，建议回填）：
  `1.17` → `C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot`；
  `21` → `C:\Program Files\Java\jdk-21.0.11`；
  `25` → `C:\Program Files\Microsoft\jdk-25.0.4.101-hotspot`。
  **老工程的 Gradle 8.x 跑在 JDK 25 上会死在 `Unsupported class file major version 69`**，与模组无关。
- **脚本**：`scripts/smoke-launch.py`（批量真启动＋按版本选 JDK）、`scripts/fetch-assets.py`、
  `scripts/audit-mixin-targets.py`（javap 校验访问器目标，启发式、有误报）、
  `scripts/patch-baritone-mixin-level.py`（可复现的 baritone 修复）、`scripts/run-client.ps1/.sh`。

## 六、两套测试体系（别混淆）

1. **浅层冒烟**（本会话的 `smoke-launch.py`）：编译＋`runClient`，判据 `Sound engine started`。
   覆盖 67 个工程，但判据浅（见一.2），且**全量扫描三次都在第一个工程后中断**，未跑完。
2. **全量测试**（仓库原有 `scripts/run-version-tests.ps1`）：跑**构建好的 jar** 在真实实例里启动，
   可 `-QuickPlayWorld` 进世界并断言 `worldLoaded`/`wurstSeen`/`baritoneSeen`——判据强得多。
   但它依赖 `.test/versions/<MC>-<Loader>_<版本>/` 下的实例（含 `<名>.json`、`<名>.jar`、
   `mods/`、`saves/`）。**目前该目录只有一个空壳 `1.20.1-Forge_47.4.22`（只有 `mods/`），
   缺 `.json` 与 `.jar`**，所以全量测试在任何版本上都跑不起来，需要单独搭建实例基础设施。

## 七、建议的下一步顺序

1. **先改冒烟判据**（`Sound engine started` → 主菜单出现），否则扫出来的"通过"没有意义。
2. 修 26.3 的 `LivingEntityAccessor`（方案已在四给出，三棵树，可编译＋启动验证）。
3. 用改好的判据扫完全部 67 个（资源瓶颈已解，预期半小时量级；分批落盘汇总）。
4. 按清单批量修：`PlayerInventoryMixin`（3 个）、baritone 的 `MixinMinecraft`（1 个）、
   `1.20.4/1.20.6/1.21` 的构建失败、以及清单暴露的其余项。
5. 顺手回填 `scripts/common.ps1` 的 JDK 路径。
