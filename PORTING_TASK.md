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
