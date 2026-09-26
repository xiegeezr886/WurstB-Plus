# WurstB+ Plus 移植与修复任务清单

Updated: 2026-09-26

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
| 混入目标失效 `PlayerMixin`（`causeExtraKnockback` 在 1.21.6–1.21.10 **不存在**） | 1.21.7、1.21.8、1.21.10 | **只修了一半**——该类上有两个 `@Redirect`，上个会话只给其中一个加了 `require = 0`，另一个仍是默认 `require = 1`，**仍会崩**。详见下节「2026-09-26 / 一」 |
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

---

# 2026-09-26 会话：1.21.2–1.21.5 全线清零，1.21.6+ 的失效注入点定位到字节码

本节接着上一节。上一节把方法论的坑挖出来了，本节把「怎么离线、可复现地按版本验证注入点」
这件事做实，并用它把 1.21.2–1.21.5 清到 0，把 1.21.6–1.21.9 的每一条失效注入点归到了根因。

## 一、先更正上一节的一处结论（重要，直接关系到「还会崩」）

上一节「三、崩溃清单」里那一行「混入目标失效 `PlayerMixin`（`causeExtraKnockback` 只在
1.21.11 存在）→ **已修**（`require = 0`）」——**只修了一半，1.21.6–1.21.10 仍然会崩。**

`PlayerMixin` 在 `causeExtraKnockback` 上有**两个** `@Redirect`：

| 行 | 包的是什么 | 上个会话的状态 |
| --- | --- | --- |
| `keepSprintMotion` | `Vec3.multiply(DDD)` | 加了 `require = 0` |
| `keepSprintState` | `Player.setSprinting(Z)V` | **没加**，仍是默认 `require = 1` |

目标方法不存在时，`require = 1` 的那一个会抛 `InvalidInjectionException`，配置又是
`required=true` + `defaultRequire: 1`，所以照样是启动即崩。审计输出里这两行的区别一眼可见：
一行行尾有 `[require=0]`，另一行没有。

顺带把「只在 1.21.11 存在」这句也修准（javap 实测）：

| 版本 | `Player.causeExtraKnockback` |
| --- | --- |
| 1.21.6 / 1.21.7 / 1.21.8 / 1.21.9 / 1.21.10 | **不存在** |
| 1.21.11 / 26.1 | `(Entity, float, Vec3)V` |
| 26.2 / 26.3 | `(Entity, float, Vec3, DamageSource, float, boolean)V` |

即：1.21.11 起**重新出现**，26.2 起**又多三个参数**。所以 `versions/26.3` 的 `PlayerMixin`
用的是 6 参描述符，而 `1.21.11`/`26.1`/`26.2` 三处写的是 2 参描述符——1.21.11 与 26.1 对得上，
**26.2 对不上**（这一条尚未进审计清单，见第七节）。

## 二、离线审计脚本：一套入口覆盖三棵树

```bash
python scripts/audit-mixin-injections.py <版本> [--tree all|forge|fabric|neoforge]
python _smoke/disasm.py <版本> <全限定类名> [方法名]     # javap -p -c 探针
```

审计脚本做的事：把每条 `@Inject` / `@WrapOperation` / `@Redirect` / `@ModifyConstant` /
`@ModifyExpressionValue` / `@ModifyReturnValue` 的**方法名+描述符**、以及
`@At(target=...)` 指向的**调用点**，逐条拿去和该版本**真实 jar** 比对。jar 的取法复用
`find_jar()`，即 `~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/
<版本>-loom.mappings...jar`。三棵树的目录映射写死在 `TREES` 里，1.20.1 走特殊分支。

几个必须知道的边界条件：

1. **它会读源码里的 `require = 0`，并在结果行尾标 `[require=0]`。** 带这个标记的不是崩溃，
   只是「这条注入静默失效了」。不带标记的才是启动即崩。
2. **它只比对目标类自身，不回溯父类。** 从父类继承来的方法会被判成「不存在」，而 Mixin 自身
   的查找行为未必如此。遇到明显是继承来的方法（例如某 Screen 没覆写 `render`）要人工判断。
3. **它只打开 `src/main/resources/*.mixins.json` 的第一个匹配项。** 已确认没有任何一棵树存在
   第二个 `*.mixins.json`（同目录下的 `mixins.baritone.json`、`fabric.mod.json`、
   `intentionally_untranslated.json` 都不匹配该通配），所以结论有效。
4. **`WurstMixinConfigPlugin` 是配置插件不是混入**，永远出现在「未注册的混入文件」里，无害。
5. 输出末尾那句「另有 N 个未注册的混入文件已跳过」**不等于缺失功能**：这些绝大多数是**改名前
   的历史同名副本**。例如 1.21.6 注册的是 `LocalPlayerMixin`，同目录下的
   `ClientPlayerEntityMixin.java` 没注册；反过来 1.21.5 注册的是 `ClientPlayerEntityMixin`。
   判断某功能在不在，要看 **mixins.json 里注册了什么**，不是看目录里有什么文件。

⚠️ **`_smoke/` 下的旧结果别信。** `audit-rerun-1.21.3/4/5.txt`（62 / 65 / 62 条）是修复**前**
的快照；`audit-band.txt`、`audit-injections-all.txt`、`audit-decoded.txt`、`audit-targets-all.txt`
同理（其中 `audit-decoded.txt` 还是编码坏掉的）。**只有每版本一份的 `audit-<版本>.txt`
（本次新扫）代表当前状态。** `_smoke/` 与 `scripts/__pycache__/` 已加进 `.gitignore`。

## 三、1.21.2–1.21.5 已清零（本次已推送）

四条线的**三棵树**复查全部为 `发现 0 处可疑注入点`。这四条线上实际改掉的东西：

| 版本 | 改动 |
| --- | --- |
| 1.21.2 ×3 树 | `BackgroundRendererMixin` 对齐 1.21.2 的 `setupFog`——它变成了**静态方法并返回 `FogParameters`**（不再是写 `RenderSystem` 返回 void），故改为 `cancellable` + 返回 `FogParameters.NO_FOG`；`FluidRenderer` / `EntityRenderer` / `GameRenderer` / `LivingEntityRenderer` / `MobEntityRenderer` / `MouseMixin` / `ScreenMixin` / `ClientPlayerEntityMixin` 逐一按真实签名改写；删除已失效的 `PlayerInventoryMixin` 并从 `wurstpenguin.mixins.json` / `wurst.mixins.json` 注销 |
| 1.21.3 forge | `LocalPlayerMixin` 整体重写。1.21.3 的口径是：`handleConfusionTransitionEffect(Z)V`（不是 1.21.5 的 `handlePortalTransitionEffect`）、字段 `spinningEffectIntensity:F`（不是 `portalEffectIntensity`）、冲刺门槛 `hasEnoughFoodToStartSprinting()Z`（不是 `hasEnoughFoodToSprint`）。NoSlowdown 的三处拦截改为按 `isUsingItem()Z` / `autoJumpTime:I` 的**真实序数**注入；`effect == MobEffects.X` 全部改为 `Holder.is()`；删掉不存在的 `liquidsRaycast`；`handleConfusionTransitionEffect` 里的 PortalGUI 改成**直接读写 `minecraft.screen` 字段**而不是 `setScreen()`。1.21.4 的目标与 1.21.3 完全一致，文件已同步成同一份（md5 相同） |
| 1.21.3 forge | `LevelRendererMixin`：1.21.3 的 `renderLevel` 在 `GameRenderer` 与两个 `Matrix4f` 之间**多一个 `LightTexture` 形参**（1.21.4/1.21.5 没有），已补上；`ScreenEffectRendererMixin` 回到 `renderFire/renderWater(Minecraft, PoseStack)` |
| 1.21.3 / 1.21.4 / 1.21.5 | `KeyboardHandlerMixin` 的 `keyPress` 回调签名 `(JIII)V` → **`(JIIII)V`**（这三个版本都声明 `keyPress(long,int,int,int,int)`） |
| 1.21.4 | `ScreenEffectRendererMixin.renderFire` 去掉已不存在的 `TextureAtlasSprite` 形参，改为 `(PoseStack, MultiBufferSource)V`；`InGameOverlayRendererMixin` 同步 `MultiBufferSource` |
| 1.21.5 ×3 树 | `handleConfusionTransitionEffect` → `handlePortalTransitionEffect`、`spinningEffectIntensity` → `portalEffectIntensity`、`hasEnoughFoodToStartSprinting` → `hasEnoughFoodToSprint`；`LightmapTextureManagerMixin` 的 `getDarknessGamma(F)F` 在 1.21.5 已并入 **`calculateDarknessScale(LivingEntity,float,float)F`**，按新签名重写；`ZoomOtf.shouldUseZoom` 从恒 `false` 恢复为真正判断变焦键 |

同批还改了 `scripts/` 下若干构建与冒烟脚本。提交与推送：

| 提交 | 内容 | 规模 |
| --- | --- | --- |
| `0939cde` | `fix(mixin): 修掉 1.21.2–1.21.5 三棵树全部运行时失效的注入点` | 130 文件 / +1648 / −3116 |
| `12ca571` | `feat(26.3): 新增 26.3 三棵树工程、注入点审计脚本与移植交接文档` | 2721 文件 |

`pictures/` 下 4 张 PNG（约 2.2MB）未被任何文档引用，**没有入库**，需要时再决定去留。

## 四、1.21.6–1.21.9 的审计结果（本次扫完）

| 版本 | 可疑注入点 | forge | neoforge | fabric | 未注册的混入文件 |
| --- | --- | --- | --- | --- | --- |
| 1.21.6 | 44 | 16 | 14 | 14 | 15 |
| 1.21.7 | 44 | 16 | 14 | 14 | 16 |
| 1.21.8 | 42 | 14 | 14 | 14 | 15 |
| 1.21.9 | 35 | 12 | 12 | 11 | — |

（**除 `PlayerMixin:20` 与 forge 的 `EntityMixin:37` 外，全部没有 `require = 0`，即全部是启动即崩。**）

逐文件清单（同一文件三棵树基本同形，`forge` 独有项已注明）：

| 位置 | 1.21.6 | 1.21.7 | 1.21.8 | 1.21.9 |
| --- | :-: | :-: | :-: | :-: |
| `AtmosphericFogEnvironmentMixin:29` | ✔ | ✔ | ✔ | ✔ |
| `FogRendererMixin:37` | ✔ | ✔ | ✔ | ✔ |
| `LocalPlayerMixin:128`（`isSlowDueToUsingItem` 不在 `aiStep`） | ✔ | ✔ | ✔ | ✔ |
| `LocalPlayerMixin:161`（`isSlowDueToUsingItem` 不在 `canStartSprinting`） | ✔ | ✔ | ✔ | ✔ |
| `LocalPlayerMixin:243`（`isSprintingPossible(Z)Z` 不存在） | ✔ | ✔ | ✔ | — |
| `LocalPlayerMixin:385`（`pick(Entity;DDF)` 不存在） | ✔ | ✔ | ✔ | ✔ |
| `PlayerMixin:20`（`[require=0]`，失效不崩） | ✔ | ✔ | ✔ | ✔ |
| `PlayerMixin:33`（**致命**） | ✔ | ✔ | ✔ | ✔ |
| `ScreenEffectRendererMixin:27`（`renderFire` 描述符） | ✔ | ✔ | ✔ | — |
| `StatsScreenMixin:42`（`addToFooter` 不在 `init`） | ✔ | ✔ | ✔ | — |
| `StatsScreenMixin:80`（`render(GuiGraphics;IIF)V` 不存在） | ✔ | ✔ | ✔ | — |
| forge `EntityMixin:37`（`[require=0]`） | ✔ | ✔ | — | ✔（1.21.9 的形参是 `Predicate`） |
| forge `noshieldoverlay.ItemInHandRendererMixin` | ✔ :60 | ✔ :60 | — | ✔ :56 |

## 五、根因归类（每一条都用 javap 对着真实 jar 看过了）

### 5.1 `AtmosphericFogEnvironment.setupFog` 在 1.21.6 换了形参，26.3 又换回去

```text
1.21.6–1.21.9 : setupFog(FogData, Entity, BlockPos, ClientLevel, float, DeltaTracker)V
26.3          : setupFog(FogData, Camera, ClientLevel, float, DeltaTracker)V   ← 和 1.21.5 一样
```

混入里写的是 `(FogData, Camera, ClientLevel, float, DeltaTracker)`，在 1.21.6–1.21.9 上
**参数个数对不上**（Mixin 只按名字找到方法后再校验 handler 参数个数）。修法是把中间那个
`Camera camera` 换成 `Entity entity, BlockPos pos`。

> **这一条本身就是本节最重要的一课**：`26.3` 的同一份代码是**对的**，
> 所以「照着上一版改」和「照着最新版抄」在 1.21.6 这一跳上**两个都会错**。

### 5.2 `FogRenderer` 换包，并且多了一个 boolean

```text
1.21.5 / 26.3 : net.minecraft.client.renderer.fog.FogRenderer
                setupFog(Camera, int, DeltaTracker, float, ClientLevel)  → 26.3 返回 FogData
1.21.6        : setupFog(Camera, int, boolean, DeltaTracker, float, ClientLevel) → Vector4f
                ↑ 在 int 之后插了一个 boolean，返回类型也还是 Vector4f
```

那第 3 个参数被同时喂给 `getGameTimeDeltaPartialTick(Z)`、`getFogType(Camera,Z)` 与
`computeFogColor(...,Z)`；从 `getFogType` 的字节码看，它的语义是
「`camera.getFluidInCamera() == NONE` 时，取 `DIMENSION_OR_BOSS` 还是 `ATMOSPHERIC`」。
（Mojang 官方映射里没有参数名，loom 缓存里的 `mappings.tiny` 一行 `p` 记录都没有，
所以**参数名只能自己起**，Mixin 也只看类型。）`1.21.5` 的 `setupFog` 是完全另一个方法
（`(Camera, FogMode, Vector4f, float, boolean, float) → FogParameters`）。

### 5.3 `LocalPlayer`：三个目标一起消失，其中 `pick` 是**搬到了别的类**

1.21.6 的 `LocalPlayer` 里：

| 旧目标 | 1.21.6 的实际情况 |
| --- | --- |
| `isSlowDueToUsingItem()Z` | **方法没了**。1.21.6 的 `aiStep` 与 `canStartSprinting` 直接调 `isUsingItem()Z`（各只有 1 处调用点，序数 0）。`isMovingSlowly()` 在 1.21.6 是 `isCrouching() \|\| isVisuallyCrawling()`，**和物品无关**，不要拿它顶替 |
| `isSprintingPossible(Z)Z` | **方法没了**。AutoSprint 的「饿着也能跑」应改挂 `hasEnoughFoodToSprint()Z`（1.21.5 用的就是它） |
| `pick(Entity;DDF)HitResult` | **搬到 `GameRenderer`** 了：1.21.6 的 `GameRenderer` 有 `public void pick(float)` 与 `private HitResult pick(Entity,double,double,float)`，后者在偏移 29 处 `iconst_0` 后调 `Entity.pick(DFZ)`。所以 Liquids 的 `@WrapOperation` 要从 `LocalPlayerMixin` **挪进 `GameRendererMixin`**（`ordinal = 0` 即可），宿主方法名不变 |

> `26.3` 的 `LocalPlayer` 里 `isSlowDueToUsingItem()`、`isSprintingPossible(boolean)`、`pick(...)`
> **全都在**——再次说明不能跨版本抄。

### 5.4 `Player.causeExtraKnockback` 消失，KeepSprint 的真身在 `Player.attack`

1.21.6 的 `Player` 里没有 `causeExtraKnockback`，那段「冲刺时额外击退」的逻辑被**内联进了
`public void attack(Entity)`**，而且字节码位置非常干净：

```text
attack(Lnet/minecraft/world/entity/Entity;)V
   563: invokevirtual  LivingEntity.knockback:(DDD)V
   612: invokevirtual  Entity.push:(DDD)V
   627: invokevirtual  Vec3.multiply:(DDD)Lnet/minecraft/world/phys/Vec3;   ← keepSprintMotion 想要的
   635: invokevirtual  setSprinting:(Z)V                                      ← keepSprintState 想要的
```

全方法里 `Vec3.multiply(DDD)` 与 `setSprinting(Z)` **各只出现 1 次**，所以两个 `@Redirect`
把 `method` 改成 `attack(Lnet/minecraft/world/entity/Entity;)V`、不用写 ordinal 就能同时
「不再崩」和「KeepSprint 真的生效」。这是**比补 `require = 0` 更好的修法**——
补 `require = 0` 只是不崩，功能是死的。

### 5.5 `ScreenEffectRenderer.renderFire` 少了第三个形参

```text
1.21.4 / 1.21.5 : renderFire(PoseStack, MultiBufferSource, TextureAtlasSprite)V
1.21.6          : renderFire(PoseStack, MultiBufferSource)V      ← TextureAtlasSprite 没了
```

`renderWater(Minecraft, PoseStack, MultiBufferSource)V` 没变（所以那一处没报）。
`@ModifyConstant` 的 `constant = @Constant(floatValue = -0.3F)` 本身没问题。

### 5.6 `StatsScreen`：`addToFooter` 挪了方法，而且**这个类不再覆写 `render`**

- `HeaderAndFooterLayout.addToFooter(LayoutElement;)LayoutElement;` 在 1.21.6 的调用点在
  **`initButtons()`**（偏移 28），不在 `init()`。而 `1.21.5` 的 `StatsScreenMixin` 用的恰好是
  `@Inject(at = @At("TAIL"), method = "initButtons()V")`——说明这个坑 1.21.5 时是踩对了的。
- `render(GuiGraphics;IIF)V` 在 `StatsScreen` 上**不存在**。`Screen` 有
  `public void render(GuiGraphics,int,int,float)`，但 `StatsScreen`（1.21.5 也是）**没有覆写**。
  对比之下 26.1+ 的 `StatsScreen` **确实声明了** `extractRenderState(GuiGraphicsExtractor;IIF)V`，
  所以 26.x 那一份是对的。
  → 改这一处时要先决定：是改成 `initButtons()` + 放弃那个画 logo 的 `render` 钩子，
  还是把 logo 钩子挂到别的、`StatsScreen` 真的覆写了的方法上。**不要指望 Mixin 的父类回溯**，
  它即使成功也等于把回调挂到 `Screen.render` 上、对所有界面生效。
- 附：1.21.6 的 `StatsScreenMixin` 里 `new GuiGraphicsExtractor(graphics)` 用的是本仓库自己的
  `net.wurstclient.util.render.GuiGraphicsExtractor`（一个包了原版 `GuiGraphics` 的 26.x 兼容壳），
  **这一处不涉及原版类，编译没问题**——别把它误当成 `net.minecraft.client.gui.GuiGraphicsExtractor`。

### 5.7 forge 树独有的两条

| 位置 | 情况 |
| --- | --- |
| `EntityMixin:37` `updateFluidHeightAndDoFluidPushing` | `[require=0]`，**不崩**。1.21.9 上的描述符已变成 `(Ljava/util/function/Predicate;)V`，语义要重做；1.21.8 上反而存在（所以 1.21.8 没报） |
| `noshieldoverlay.ItemInHandRendererMixin` | **致命**。1.21.6/1.21.7 是 `ItemInHandRenderer.swingArm(F,PoseStack,I,HumanoidArm)V` 不在 `renderArmWithItem` 的字节码里；1.21.9 是 `ItemStack.getSwingAnimation()` 不在。注意 26.3 那一份已经改成 `FirstPersonHandsAndItemsRenderer` + `AvatarRenderState.currentSwing` 字段读取了（见 26.3 移植文档），**这正是 1.21.6+ 需要的方向** |

## 六、可复用的判据：别把「版本号相邻」当成「API 相邻」

本次几条反直觉的实测，建议以后遇到注入失败先查这几处：

1. **同一符号的存在性在 1.21.5→1.21.6 与 1.21.9→26.3 之间会来回摆**（`AtmosphericFogEnvironment.setupFog`
   的形参、`causeExtraKnockback` 的有无与参数个数）。**必须按版本查**，`26.3` 的正确写法不能反推 `1.21.6`。
2. **方法「消失」常常只是「搬家」**：`LocalPlayer.pick` → `GameRenderer.pick`。看到「目标类无此描述符」
   先去相邻类里找同名方法，再决定是改 target 还是换宿主 mixin。
3. **逻辑「内联」会让锚点从 `INVOKE` 变成直接调用**：`causeExtraKnockback` → 内联进 `attack`。
   这类改动的特征是**调用点数量很少且位置干净**（本次两处各 1 个），改 `method` 就能救活功能。
4. `require = 0` 是**止血**不是修复：它让客户端能起来，但对应功能静默失效。
   审计输出专门把 `[require=0]` 标出来，就是为了区分「还能用但没接上」和「会崩」。

## 七、尚未完成 / 下一步

**审计未覆盖：**

- `1.21.10`：本次扫描任务在写这个文件时**中断了**（`_smoke/audit-1.21.10.txt` 是空的），需重扫。
- `1.21.11`、`26.1`、`26.1.1`、`26.1.2`、`26.2`、`26.3`：**未扫**。
- ⚠️ 已知 `26.2` 的 `PlayerMixin` 用的是 2 参 `causeExtraKnockback` 描述符，而 26.2 的实际签名
  是 6 参（见第一节的表）——**这是审计扫到 26.2 时大概率会报的一条**，可以提前确认。

**修复未开始：** 1.21.6 / 1.21.7 / 1.21.8 / 1.21.9 三棵树共 4 × ~14 条致命注入点，
根因已在第五节逐条给出，但**一行代码都还没改**。

**建议顺序：**

1. 先把 `PlayerMixin:33` 这条补上（最小改动：`method` 改成 `attack(...)`，同时把 `:20` 的
   `require = 0` 去掉——两处合并成一个正确修法），三棵树，编译验证。
2. 按第五节 5.1–5.6 逐类改 1.21.6 的三棵树，改完用
   `python scripts/audit-mixin-injections.py 1.21.6` 复查到 0，再 `./gradlew compileJava`。
3. 1.21.6 验证通过后，把同一批改动按 `_smoke/audit-<版本>.txt` 的差异**逐版本**应用（不要盲目复制：
   5.1/5.6 在各版本间就有差异）。
4. 重扫 1.21.10 与 1.21.11/26.x，补完审计覆盖。
5. 之后再回到上一节的第 1 步（冒烟判据改「主菜单出现」）与第 3 步（全量真启动）——
   **判据升级要在注入点清完之后做，否则扫出来的崩溃清单会被这些已知项淹没。**

**本次会话留下的可复现资产：**

| 资产 | 说明 |
| --- | --- |
| `scripts/audit-mixin-injections.py` | 已入库。三棵树统一入口，按版本核对注入点 |
| `_smoke/disasm.py` | 未入库（`_smoke/` 已 ignore）。依赖上面的脚本，用法 `<版本> <类> [方法]` |
| `_smoke/audit-<版本>.txt` | 未入库。本次 1.20.1–1.21.9 的结果，1.21.10 为空需重扫 |
| `_smoke/results.tsv` | 未入库。冒烟启动结果，**只有 4 行旧判据的陈旧数据**，重跑前不要引用 |

## 八、2026-09-26 续跑进度

- 1.21.6 的三棵树 `PlayerMixin` 两处 `@Redirect` 均已改为 `attack(Lnet/minecraft/world/entity/Entity;)V`，第一处移除 `require = 0`。1.21.6–1.21.9 按上表逐版本修复后，三棵树的注入审计均为 0，相关 `compileJava` 通过。
- 1.21.10 已重新扫描，不再将先前空文件当作 0；1.21.10、1.21.11、26.1、26.1.1、26.1.2、26.2 的三树审计均为 0。26.2 的 `PlayerMixin` 现为 6 参 `causeExtraKnockback` 描述符。审计只覆盖 Wurst 注册的混入，不覆盖内嵌 Baritone 的混入。
- 冒烟脚本现在以 `Sound engine started` 为门槛，之后须存活 `--settle` 秒且没有 fatal 行才判 `LAUNCHED`。本轮枚举并运行全部 67 个工程，原始结果记录于 `_smoke/results.tsv`：27 通过、38 崩溃/构建失败、2 超时。扫描期间修复后，另有 8 个原失败工程定向复测通过；按最新结果计 35/67 通过。定向记录见 `_smoke/retest.tsv`、`_smoke/*probe*.tsv`，不能只看原始 TSV。
- 1.21.6–1.21.9 中 Fabric 四个版本均通过真实启动；1.21.9 NeoForge 和 Forge 也通过。Forge 1.21.10、1.21.11 通过。Forge/NeoForge 1.21.6–1.21.8 仍被内嵌 Baritone 的 `MixinMinecraft.preLoadWorld` 旧回调描述符阻断；NeoForge 1.21.5 另有 Baritone `MixinScreen` 失效。这不是 `compileJava` 或 Wurst 注入审计所能证明已修复的内容。
- Forge 26.1/26.1.1/26.1.2 开发运行缺少与 `META-INF/jarjar/metadata.json` 匹配的 Baritone jar；Forge 26.2 真实启动通过。26.3 Fabric 缺本地 Baritone 构件，其余失败的具体日志位于 `_smoke/`。不要把环境/依赖失败计作 Wurst 混入已通过。

## 九、2026-09-26 后续真启动修复

- 最新有效结果为 **49/67 `LAUNCHED`、16 `CRASHED`、2 `TIMEOUT`**。原始 `_smoke/results.tsv` 保留扫描时的 27/67 快照；本节新增复测结果在 `_smoke/baritone-setlevel-probe.tsv`、`_smoke/baritone-screen-probe.tsv`、`_smoke/forge-261x-probe.tsv`、`_smoke/late-retest.tsv`，统计时每个工程取最后一次结果。
- Forge/NeoForge 1.21.6–1.21.8 六工程全部真实启动通过。内嵌 Baritone 的 `MixinMinecraft` 两个 `setLevel` 回调需补 `ReceivingLevelScreen.Reason`，`MixinWorldRenderer.onStartHand` 需去掉不使用的第三个矩阵参数。补丁工具为 `scripts/Baritone1216Patcher.java`；六份本地按 MC 版本隔离的 jar 已修补、发布包重建，并逐一验证内嵌 jar 与本地构件 SHA-256 一致。
- Forge/NeoForge 1.21.5 两工程也真实启动通过。Baritone 的 `MixinScreen`、`MixinNetworkManager`、`MixinMinecraft`、`MixinWorldRenderer` 按该版本 API 适配；Forge 另修 `mods.toml` 的错误 Forge 56 范围和 `LevelRendererMixin` 多余的歧义 `@Local Matrix4f`。两份发布包已重建并核对内嵌 Baritone。可重复入口是 `scripts/patch-baritone-1214-1218.ps1`，它要求先存在本地 `baritone-maven/` 里的各版本 jar（该目录被 Git 忽略），脚本重复执行应报告 0 处改动或 `already patched`。
- 1.21.4 的 Forge/NeoForge 仍**未通过**。其 Baritone 点击与冲刺回调已跨过旧注入失败，Forge 的无用 `@Local` 也已移除，但 1.21.11 版 Baritone 的普通运行时代码引用 1.21.4 不存在的 `HoverEvent.ShowText`；需要针对 1.21.4 构建/适配 Baritone，不能以当前局部字节码修补宣称完成。
- Forge 26.1、26.1.1、26.1.2 的 `processResources` 现在把 `metadata.json` 对应的 Baritone jar 复制到开发运行资源目录，三工程均通过真实启动，`allJar` 也重新构建成功。
- Fabric 与 NeoForge 1.20.5、NeoForge 1.20.6 的旧失败已复测通过。1.20.5 两树的 `GameRendererMixin` 回调改为目标真实的 `(Entity, double, double, float)` 参数；Fabric 编译须使用官方 Mojang 库地址，NeoForge 编译须使用本仓库 `gradle/init-mirrors.gradle`。Forge 1.20.6 仍因内嵌 `baritone-api-forge-1.20.1.jar` 自报只支持 Minecraft 1.20–1.20.1 而失败，不要直接就地改这个被旧版本共享的 jar。

## 十、2026-09-26 收尾：1.21.5 的 GUI 缓冲区崩溃，与「25 秒判据」的假通过

**背景**：全量真启动（`--settle 25`）结束时只剩两个工程未过：`neoforge/versions/1.21.5`
与 `fabric/versions/1.21.6`。复测后确认前者是**真缺陷**，后者是外部关闭窗口造成的
干净退出（日志无任何异常，`Stopping!` 之后正常结束）——即 `EXITED` 属环境噪音，重跑即可。

**根因（1.21.5 三棵树共有）**：`RenderUtils.drawGuiMesh` 用
`drawParams.format().uploadImmediateVertexBuffer(...)` 取到顶点缓冲后，在 `finally`
块里把它 `close()` 了。这个缓冲**属于 `VertexFormat` 并被跨帧复用**，vanilla 的同类路径
（`RenderType$CompositeRenderType`）从不关闭它。关掉之后，同一 `VertexFormat` 的下一个
使用者（包括 vanilla 自己）就会崩在 `GlCommandEncoder.writeToBuffer`
（javap 实测：该分支只检查 `GlBuffer.closed`，而 `GlBuffer.size` 关闭后不变，所以
`uploadImmediateVertexBuffer` 会走 `writeToBuffer` 重放分支而不是重建分支）：

```text
java.lang.IllegalStateException: Buffer already closed
  at com.mojang.blaze3d.opengl.GlCommandEncoder.writeToBuffer(GlCommandEncoder.java:187)
  at com.mojang.blaze3d.vertex.VertexFormat.uploadImmediateVertexBuffer(VertexFormat.java:124)
  at net.wurstclient.util.RenderUtils.drawGuiMesh(RenderUtils.java:64)
  at net.wurstclient.clickgui2.RoundedRectRenderer.draw(RoundedRectRenderer.java:86)
  at net.wurstclient.gui.title.WurstTitleButton.renderWidget(WurstTitleButton.java:56)
```

**改动**：三个 1.21.5 工程（`versions/`、`fabric/versions/`、`neoforge/versions/`）的
`RenderUtils.drawGuiMesh` 去掉 `finally { vertexBuffer.close(); }`，并补注释说明该缓冲归
`VertexFormat` 所有、不可关闭。`drawGuiMesh` 只存在于 1.21.5 这三棵树——1.21.4 用
`BufferUploader.drawWithShader`，1.21.6+ 用 `submitQuadMesh2D`，所以这个坑不会外溢。

**重要教训：`--settle 25` 不足以排除假通过。** 同一个崩溃在三棵树上暴露的时间不同：

| 工程 | 门槛后多久崩 | 25 秒窗口的判定 |
| --- | --- | --- |
| `neoforge/versions/1.21.5` | 约 2 秒 | `CRASHED`（被 `[.*/FATAL]` 规则抓到） |
| `fabric/versions/1.21.5` | 约 31 秒 | **`LAUNCHED`（假通过）** |
| `versions/1.21.5` | 同族，未在 25 秒内暴露 | **`LAUNCHED`（假通过）** |

用 `--settle 55` 复测，Fabric 立刻复现同一崩溃（`_smoke/diag-1215.tsv`）。**因此在重要
版本上判定「已通过」时应把 `--settle` 显著调大（本次用 55 秒），不能只用默认的 25 秒。**

**旁证（假通过范围）**：对 67 个工程最近一次冒烟日志做未捕获异常扫描
（`Reported exception thrown` / `Caused by: java.lang.*`），**只有 1.21.5 的日志命中**，
说明这一类假通过目前只出现在 1.21.5 一族，其余 `LAUNCHED` 判定没有同类污染。

**顺带清理**：`neoforge/versions/1.21/build/libs/` 下遗留一份改名前的
`WurstB+ Plus-v1.5.0-NeoForge-1.21.1.jar`（与 `...-NeoForge-1.21.jar` 逐字节相同），已删除，
避免发布时取错文件名。全仓库产物名现已与各工程 `minecraft_version` 一致。

## 十一、2026-09-26 收尾（二）：1.21.2–1.21.4 标题界面图标不显示的修复

**现象**（用户报告）：1.21.x 标题界面的按钮「底框像没了」。实测确认：**1.21.3 / 1.21.4 的按钮左侧图标完全不绘制**，
按钮填充其实存在且与 1.21.6 取样值一致（`#11191A`），所以视觉上像是「空了一块」。

**根因**：`WurstTitleButton.renderWidget` 的图标 `graphics.blit(...)` 参数顺序错位。
1.21.2 起 `GuiGraphics.blit` 带 `RenderType` 的重载签名是
`(Function, ResourceLocation, int x, int y, float u, float v, int width, int height, ...)`——**u/v 在 width/height 之前**
（javap 对 1.21.2 / 1.21.3 / 1.21.4 三个 loom merged jar 逐一确认）。而这几棵树写的是 1.20.x 时代的顺序
`(x, y, width, height, u, v, ...)`，于是被解释成 u=22, v=22, **width=0, height=0** → 零面积四边形，什么都不画。

**改动**（8 个工程）：`.../gui/title/WurstTitleButton.java`
`iconY, iconSize, iconSize, 0, 0,` → `iconY, 0, 0, iconSize, iconSize,`

- `versions/1.21.3`、`versions/1.21.4`（`RenderType::guiTextured` 形式）
- `fabric/versions/1.21.{2,3,4}`、`neoforge/versions/1.21.{2,3,4}`（`RenderType.GUI_TEXTURED` 形式）

**没有改动的树**（避免误伤）：
- `1.20.2`–`1.21.1`（23 树）用的是**不带 RenderType 的旧重载**，其签名本来就是
  `(x, y, width, height, u, v, ...)`，现有写法是对的。
- `1.21.5` 与 `1.21.6`–`26.3`（36 树）本来就是 `(0, 0, iconSize, iconSize, ...)`，是对的。

**验证**：`fabric/versions/1.21.4` 真启动截图，图标全部回来；与 1.21.6 参考截图做像素比对，
图标区域取样值**逐点相等**（如 x=216,y=152 修复前 `#111919` → 修复后 `#323838`，1.21.6 参考同为 `#323838`）。
其余 7 个工程用 `compileJava` 收口（改动与 API 签名在三个版本上完全一致）。

**注意事项**：验证 GUI 视觉问题需要截图，而 Windows 的前台切换（`SetForegroundWindow`）会被系统拒绝。
可用 `SetWindowPos(hwnd, HWND_TOPMOST, ...)` 临时置顶再 `CopyFromScreen`，截完恢复 `HWND_NOTOPMOST`。
截图脚本见会话记录；`_smoke/mc-*.png` 为本次证据（`_smoke/` 已 ignore）。

## 十二、2026-09-26 收尾（三）：1.21.5 标题界面按钮「没有填充」——**已解决**

> **结论先说**：真因是 `RoundedRectRenderer` 生成的圆角几何**绕序反了**，
> 而 `RenderPipelines.GUI` 开启背面剔除，于是填充和描边整块被丢弃。修法见本节末尾。
> 下面保留完整的排查过程（含我中途两次错误结论），因为「怎么定位到绕序」比结论本身更可复用。

**现象**（用户报告，已实测确认）：1.21.5 三棵树的标题界面按钮**完全没有填充**。定量判据（同一 y 上
按钮内像素 vs 按钮外像素的 RGB 差）：

| 版本 | y=140 | y=160 | y=180 |
| --- | --- | --- | --- |
| 1.21.6（参考） | 47 | 44 | 40 |
| **1.21.5** | **0** | **0** | **0** |
| 1.21.4 | 47 | 44 | 40 |

色差 0 = 按钮内外颜色完全相同 = 填充根本没落屏。（我最初凭肉眼看截图判断「1.21.5 有填充」，
是错的；`0xC…` 这类深色填充与深色背景肉眼难分，**这个问题必须以像素差判定，不能靠看**。）

**已排除的路径**：1.21.5 的按钮填充走的是该版本独有的 `RenderUtils.drawGuiMesh`
（1.21.4 用 `BufferUploader.drawWithShader`，1.21.6+ 用 `submitQuadMesh2D`，这两条都正常）。

**已尝试但无效的修法**：`RoundedRectRenderer` 的网格建在 `VertexFormat.Mode.TRIANGLES`，
而 `drawGuiMesh` 绑定的 `RenderPipelines.GUI` 经 javap 确认是 **QUADS** 管线（`GUI_SNIPPET` 用
`DefaultVertexFormat.POSITION_COLOR` + `Mode.QUADS` + `BlendFunction.TRANSLUCENT`），模式确实不匹配。
已把三棵树的几何生成为 QUADS（`addSolidFan` 补一个重复顶点成退化四边形；`addColorStrip` 的
两个三角形本来就是同一四边形，改为 4 个顶点），`Window.java` 的 QUADS 网格也印证了该组合在本树上可用。

**两次尝试均无效（都已回退）**：

1. **模式对齐**：把几何改成 QUADS（`addSolidFan` 补重复顶点成退化四边形；`addColorStrip` 的两个三角形
   本来就是同一四边形，改为 4 顶点）。`javap` 确认运行的是新字节码（class 里已是 `QUADS`），
   填充色差**仍为 0**。
2. **改走缓冲路径**：把 `RoundedRectRenderer` 从一次性 `RenderPass` 改为
   `RenderUtils.getVCP().getBuffer(RenderType.gui())`（同树 `fill2D`/`fillQuads2D` 用的就是它，
   1.21.5 的 `GuiGraphics` 没有 26.x 的 `guiRenderState` 字段，javap 已确认）。编译通过、客户端
   正常启动，填充色差**仍为 0**。

两者都在「确认跑的是新代码」的前提下失败，因此**「管线模式不匹配」和「绘制顺序被背景盖掉」这两个假设
已被排除**。相关改动已 `git checkout` 回退，保持树干净。

**第三次实验（二分定位，已做）**：把 `WurstTitleButton` 的填充色临时改成不透明亮红
`0xFFFF0000`，跑一次真启动截图。`javap` 确认运行的 class 里确实是 `int -65536`，
但按钮内取样 **R=0**——**亮红也没出现**。

由此确定：**不是颜色/alpha 问题，几何根本没落屏**。结合前两次实验，可以排除：
颜色换算、管线模式、以及「被背景盖掉」这类绘制顺序问题（缓冲路径下已按提交顺序绘制，仍然没有）。
`FlatRenderer.fillRoundedRect` 只是直接转发给 `RoundedRectRenderer.fill`，链路无分支。

**第四次实验（对照实验，已做）**：在同一个 `renderWidget` 里、用**写死的 GUI 坐标**同时画两样东西：

```java
graphics.fill(60, 10, 100, 50, 0xFFFF0000);                              // 原版路径（对照组）
FlatRenderer.fillRoundedRect(graphics, 120, 10, 160, 50, 7, 0xFF00FF00); // 我们的圆角路径
```

真启动截图按像素计数（红/绿各计满足 `R>200,G<70,B<70` / `G>200,R<70,B<70` 的点）：

| | 像素数 |
| --- | --- |
| 原版 `graphics.fill`（红） | **1600** |
| 我们的圆角路径（绿） | **0** |

**结论**：坐标、pose、颜色、以及「`renderWidget` 里画的东西会不会被后续内容盖掉」全部**正常**——
同一个方法里原版绘制就能落屏。故障被**完全锁定在 `RoundedRectRenderer` → `drawGuiMesh` 这条链**上：
即使坐标写死、颜色不透明，几何也不落屏。

**由此可以排除**：坐标/姿势矩阵、背面剔除以外的一切外层因素。

**剩下就只剩这条链内部的两种可能**：
1. **几何根本没被画**（`drawGuiMesh` 的 RenderPass / `uploadImmediateVertexBuffer` / `drawIndexed`
   在 1.21.5 上实际是空操作，或 `Tesselator.begin()` 返回了不可用的 builder）；
2. **画了但被剔除**（绕序反了；本树 `fillQuads2D` 的注释明确写了「顶点非逆时针会被剔除」）。
3. 另有 `RenderState.restore()` 在绘制前就恢复 GL 状态，可能覆盖掉绘制所需状态。

**下一个实验（一次就能分开 1 和 2）**：保留 `drawGuiMesh` 的一次性 RenderPass 路径，
但把几何换成**一个已知正确的矩形四边形**（顶点用原版 `graphics.fill` 同样的 CCW 顺序），
坐标也写死。落屏 → 路径没问题，是绕序；仍不落屏 → `drawGuiMesh` 这条路径本身在 1.21.5 上不工作，
应改走缓冲路径（`getVCP().getBuffer(RenderType.gui())`），且几何必须按 CCW 输出。

**调试方法已就绪**：`_smoke/` 下有一套「临时改色 → 真启动 → 截图 → 像素取样」的流程可用
（Windows 前台切换会被拒，改用 `SetWindowPos(HWND_TOPMOST)` 临时置顶再 `CopyFromScreen`）。
判据一律用像素取样，不要用肉眼看截图。

**相关**：图标参数顺序问题见第十一节（那个已修复并验证）。

### 十二·解决：真因与修法

**真因**：`RoundedRectRenderer` 生成的圆角几何**顶点绕序是反的**，
而 `RenderPipelines.GUI`（= `RenderType.gui()`）开启背面剔除，于是整个填充/描边被丢弃。

**定位过程**（每一步都用真启动 + 像素取样，不靠肉眼看截图）：

| 实验 | 做法 | 结果 | 排除项 |
| --- | --- | --- | --- |
| 1 | 几何 TRIANGLES → QUADS（与管线声明一致） | 色差仍 0 | 模式不匹配 |
| 2 | 改走 `getVCP().getBuffer(RenderType.gui())` 缓冲路径 | 色差仍 0 | 绘制顺序被背景盖掉 |
| 3 | 填充色改不透明亮红 `0xFFFF0000`；`javap` 确认跑的是新字节码 | 仍无红像素 | 颜色/alpha |
| 4 | 同方法内写死坐标画「原版 `graphics.fill`(红)」与「我们的圆角路径(绿)」 | 红 1600 px、绿 **0** px | 坐标/pose/外层遮挡 |
| 5 | 只走 `drawGuiMesh`，写死坐标画两个四边形：A 用原版顶点顺序、B 用反序 | A **1600** px、B **0** px | **路径本身没坏；背面剔除是开着的，且反序会被剔除** |

实验 5 直接把「路径」和「绕序」分开，剩下的就是绕序。

**修法**（三个 1.21.5 工程 `clickgui2/RoundedRectRenderer.java`）：

1. 网格模式 `TRIANGLES` → **`QUADS`**（与 `RenderPipelines.GUI` 声明一致）。
2. `addSolidFan`：每个面片按反序提交，并补一个重复顶点凑成退化四边形
   （`center, p[next], p[i], p[i]`）。`prepareContour` 沿圆角矩形是**顺时针**遍历，
   而剔除剔除的是另一侧，所以必须反序。
3. `addColorStrip`：两个三角形本来就是一个四边形，改为 4 顶点 `A_i, A_next, B_next, B_i`；
   并要求**第一个参数在几何上位于第二个之内**。
4. `outline` 的三条轮廓是 0=最外、1、2=最内，与 `fill` 的内外关系相反，
   所以传参改为 `(1,0)` 与 `(2,1)`（颜色随轮廓走，视觉效果不变）——
   **这一条是修完填充后才暴露出来的**：只改 1–3 时填充回来了但描边仍然没有。

**验证**（`fabric/versions/1.21.5` 真启动 + 截图 + 像素取样）：

| 判据 | 1.21.5 修复前 | 1.21.5 修复后 | 1.21.6 参考 |
| --- | --- | --- | --- |
| 填充（按钮内 vs 外 色差） | 0 | **44** | 44 |
| 描边（x=450 上边缘亮度） | `#0D1314` | **`#202626`** | `#262C2D` |

另外两个工程（`neoforge/versions/1.21.5`、`versions/1.21.5`）文件与本树逐字节一致，
以 `compileJava` 收口。

**教训（比结论更重要）**：
1. **GUI 视觉问题一律用像素取样判定，不要看截图下结论**。这个 bug 我先后两次误判
   （先说「1.21.5 有填充」，后说「模式不匹配是根因」），都是因为凭肉眼/凭推理。
2. **同帧对照实验最省事**：在同一个方法里并排画「已知可用的原版调用」和「待测调用」，
   一次启动就能把锅定位到调用本身，而不是周边环境。
3. **写死的调试色 + `javap` 确认字节码**，是排除「改了没生效」这类假阴性的标准动作。

### 十二·验证覆盖的边界（重要，别当成全部都验过）

三个 1.21.5 工程的文件已确认**逐字节一致**（md5 相同），但运行期验证的覆盖并不一样：

| 工程 | 真启动判定 | 填充/描边像素量测 |
| --- | --- | --- |
| `fabric/versions/1.21.5` | `LAUNCHED` 129s | **有**：色差 44、上边缘 `#202626`（参考 44 / `#262C2D`） |
| `neoforge/versions/1.21.5` | `LAUNCHED` 165s | **有**：色差 44、上边缘 `#202626`（与 fabric 相同） |
| `versions/1.21.5`（Forge） | `LAUNCHED` 171s | **没有**：该 dev 实例启动后停在原版
  「Welcome to Minecraft!」无障碍引导页，没进 Wurst 标题界面，取样无效 |

**顺带发现的一个判据盲区（值得记）**：`versions/1.21.5` 的 `run/options.txt` 里
`onboardAccessibility` 已经是 `false`，但 Forge dev 客户端仍然显示了引导页——原因未查。
要紧的是：**「Sound engine started 之后存活 N 秒」这个判据在引导页上同样成立**，
也就是说 Forge 1.21.5 此前那些 `LAUNCHED` 可能一直停在这个原版引导页上，
**从未渲染过 Wurst 的标题界面**。混入注入在那一刻已经全部应用，所以启动判定本身依然有效，
但**凡是「要看到 Wurst 自己的 UI 才算过」的检查，都不能依赖这个判据**。

要补 Forge 的视觉验证，需要先让该 dev 实例越过引导页（手动点一次 Continue，
或查清为什么 `onboardAccessibility:false` 不生效）。

### 十一·补充：同类 `blit` 顺序错误的**全树扫描**结果

写了个扫描脚本（`_smoke/sweep_blits.py`，临时工具、未入库），把仓库里所有带 `RenderType` 的
`blit` 调用按「参数形态」归类，逐个版本树对比。结论：

| 文件 | 错误顺序 | 正确 | 备注 |
| --- | --- | --- | --- |
| `WurstTitleButton.java` | 8 树 | 36 树 | 已修 |
| `GuiIcon.java`（ClickGUI 图标） | **8 树** | 36 树 | 本次修 |
| `TargetHudElement.java`（目标 HUD 头像，2 处调用） | **8 树** | 36 树 | 本次修 |

**错的都只有那 8 棵树**（`{versions, fabric/versions, neoforge/versions} × 1.21.{2,3,4}`，
其中 `versions/1.21.2` 无此文件）；`1.20.2`–`1.21.1` 用的是不带 `RenderType` 的旧重载
（签名本来就是 `x, y, w, h, u, v`），`1.21.5` 与 `1.21.6`–`26.3` 的写法本来就是对的。
**其余版本（含 26.x）在这类问题上干净**——这是扫出来的，不是推的。

另外顺手确认了圆角矩形的**绕序问题只可能出在 1.21.5**：其余版本的 `RoundedRectRenderer`
分别用 `buffer.begin(...)`（1.20.x）、`BufferUploader.drawWithShader`（1.21.4）、
`submitQuadMesh2D`（1.21.6+），只有 1.21.5 走 `drawGuiMesh` + `RenderPipelines.GUI` 这条带剔除的路径。

**验证**：8 棵树 `compileJava` 收口。`GuiIcon` 的效果需要打开 ClickGUI（右 Shift）才能看，
`TargetHudElement` 需要进世界并对准实体——**这两处没有做运行期验证**，与标题界面那种
「启动即可见」的情况不同，不能同等看待。

### 十一·补充二：改动后的冒烟回归（`_smoke/ui-fix-smoke.tsv`）

改过代码的工程必须重跑冒烟，否则「全量通过」这个结论就过期了。本次改动的回归结果：

| 版本线 | 工程 | 结果 |
| --- | --- | --- |
| 1.21.2 | fabric、neoforge | `LAUNCHED` 86s / 86s |
| 1.21.3 | fabric、neoforge、forge | `LAUNCHED` 80s / 84s / 92s |
| 1.21.4 | fabric、neoforge、forge | `LAUNCHED` 80s / 86s / 92s |
| 1.21.5 | fabric、neoforge、forge | `LAUNCHED` 129s / 165s / 171s |

合计 11 个受影响工程全部通过（`--settle 55`）。其余 56 个工程自上次全量扫描后未再改动，原结论仍成立。

**仍未做运行期视觉验证的**：`GuiIcon`（需打开 ClickGUI）与 `TargetHudElement`（需进世界对准实体）——
这两处只有 `compileJava` + 启动通过，**没有截图像素证据**，与标题界面那几处不同等看待。

# 2026-09-26 会话：v1.5 发布矩阵重建与发布

## 一、构建工具已丢失，本次重建

`docs/RELEASE.md` 记录的权威脚本 `D:\WurstB\tmp-recon\build-v1.5-release.py` 在仓库外
（`tmp-recon/` 不随仓库分发），该机器已不存在，`build/release-v1.5/` 也是空的。
本次按 RELEASE.md 的规则重写了一套**入库**的工具：

| 脚本 | 作用 |
| --- | --- |
| `scripts/build-v1.5-release.py` | 重建矩阵：66 个工程 → `build/release-v1.5/`，支持 `--jobs N` 并发与 `--plan` |
| `scripts/validate-release-jars.py` | 打包校验：zip 完好 / 加载器元数据 / Mixin 配置 / 含 `.class` |
| `scripts/upload-release-assets.py` | 上传到 Release；`--dry-run`、`--body-only`、`--add-263-to-body` |

**任务映射**（来自 RELEASE.md 的矩阵表）：Forge ≤1.21.1 → `jarJar`，≥1.21.3 → `allJar`；
**Fabric → `remapJar`**；NeoForge → `build`。JDK 沿用仓库口径（1.20.1–1.20.4→17、1.20.5/1.21.x→21、26.x→25）。

## 二、Fabric 为什么不能用 `build`

`build` 会顺带跑 `validateAccessWidener` 与单元测试，这两件事在部分工程上本来就失败，与发布产物无关：

- `fabric/versions/1.20.5`、`1.20.6`：`wurstpenguin.accesswidener` 里
  `accessible field net/minecraft/world/entity/Entity maxUpStep F` 是**陈旧声明**——
  该字段在这两个版本已不存在。`validateAccessWidener` 因此失败。
- `fabric/versions/1.21.3`、`1.21.4`、`1.21.5`：`HudRenderHookTest.guiRenderEventUsesTheVanillaHudLayer()`
  去读 `src/main/java/net/wurstclient/mixin/GuiMixin.java`，而该文件在这几棵树里已改名，
  抛 `NoSuchFileException`。
- `fabric/versions/1.21.2`：`remapJar` 下载 `com.mojang:jtracy:1.0.29` 原生库超时（网络），重试即过。

**这三类都是既有问题，本次没有修**（改 AW 声明或测试源码属于源码改动，需要单独验证）。
`remapJar` 不跑这些检查，且产物与 `build` 里的 `remapJar` 是同一个 jar，所以发布用它是正确的。

## 三、⚠️ GitHub 资产名里的 `+`（踩过的坑，务必记住）

**用 Release 资产上传 API 时，`?name=` 参数必须百分号编码。**
`WurstB+.Plus-...` 里的 `+` 若是原样放进查询串，会被解析成**空格**，
GitHub 落盘时把空格再变成 `.`，于是资产名变成 `WurstB.Plus-...`——
看起来只差一个字符，但会把正确命名的资产**替换成错名的**。

正确写法：`urllib.parse.quote(name, safe='')` → `WurstB%2B.Plus-...`。
（`PATCH /releases/assets/{id}` 改名走 JSON，但若目标名与已存在资产冲突会报
`already_exists`，不能用来兜底。）

排查时的有效判据：**下载量/资产数突然只减不增**、以及**同一版本同时存在 `WurstB+.Plus-` 与
`WurstB.Plus-` 两个命名**。恢复靠本地 `build/release-v1.5/` 重传，产物不会丢。

## 四、本次发布结果（2026-09-26）

- 重建 **66 个工程**（原 64 个矩阵 + 26.3 三棵树），`validate-release-jars.py` **66/66 合格**。
  根目录 Forge 1.20.1 是 v1.6.0 形态，按文档保留旧产物不动。
- Release `v1.5.0`（id 359748087，标题「WurstB+ Plus 1.5.0-Navy」）资产由 64 → **67**
  （66 个替换/新增 + 1 个留存），命名全部为 `WurstB+.Plus-<版本>-<加载器>-<mc>.jar`，
  逐个核对了与本地产物的体积。
- Release 简介的「覆盖范围」三行版本表末尾各补 `26.3`，**标题与其余简介内容未动**。
- 开发文档同步：`docs/RELEASE.md`（计数 64→67、三行版本表加 26.3、资产口径 66+1）、
  `README.md`（徽章、23 个 MC 版本、67 个工程、v1.5.0 资产数）、`PROJECT_INDEX.md`（补 26.3 三行）。
  **注意 `PROJECT_INDEX.md` 整体仍停留在 15 工程时代**（标题写「15 个独立构建工程」、
  表里只列 15 个），本次只按 26.3 的要求补行，未做整体翻新。
