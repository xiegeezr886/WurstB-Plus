# WurstB+ Plus 项目信息

## 基本信息
- 模组ID: `wurstpenguin`
- 显示名: WurstB+ Plus
- 开发者: Penguin
- 项目根目录: `C:\Users\ui863\Documents\trae_projects\VAP`

## 项目目录结构

```
C:\Users\ui863\Documents\trae_projects\VAP\
├── src/                    # 主版本（Minecraft 1.20.1, Forge 47.4.10）
├── newforge/               # NewForge 版本（Minecraft 1.20.1, Forge 47.1.3）
│   └── versions/
│       └── 1.21.1/         # NeoForge 版本（Minecraft 1.21.1, NeoForge 21.1.244）
└── versions/
    ├── 1.21.1/             # 1.21.1 版本（Forge 52.1.16）
    └── 26.1.2/             # 26.1.2 版本（Forge 64.1.0）
```

## 版本历史

### v1.0
- 初始版本（具体信息待补充）

### v1.1
- 待补充

### v1.2
- 待补充

### v1.3
- 待补充

### v1.4
- 待补充

## 当前版本架构

### 主版本（root src/）
- Minecraft 1.21.1, Forge 47.4.10, Java 17, FG6, Gradle 8.11
- 渲染管线：PoseStack 仅含视图矩阵，投影由 shader 处理
- GameRendererMixin 使用 `@Inject` + `@Redirect` 实现视角摇晃取消
- RenderEvent 从 GameRendererMixin 注入点触发
- BufferBuilder API: `getBuilder()` + `begin()`, `.vertex().endVertex()`, `.end()` → `RenderedBuffer`

### 1.21.1 版本（versions/1.21.1/）
- Minecraft 1.21.1, Forge 52.1.16, Java 17, FG6, Gradle 8.11
- GameRendererMixin 使用 `@WrapOperation` 实现视角摇晃取消（更简洁）
- RenderEvent 从 WurstForgeInitializer 的 `RenderLevelStageEvent` 触发（Forge 事件方式）
- BufferBuilder API: `Tesselator.getInstance().begin()` 返回 BufferBuilder, `.addVertex()`, `.build()` → `MeshData`
- LiquidsHack 使用 HitResultRayTraceEvent + includeFluids 参数（事件驱动方式）

### 1.20.1 版本（newforge/）
- Minecraft 1.20.1, Forge 47.1.3, Java 17, FG6, Gradle 8.14.4
- 构建系统：ForgeGradle 6.x + MixinGradle 0.7
- 从主版本源码复制，仅修复 `ResourceLocation.parse()` → `new ResourceLocation()` API 差异
- BufferBuilder API 与主版本相同
- 已验证编译成功

### NeoForge 1.21.1 版本（newforge/versions/1.21.1/）
- Minecraft 1.21.1, NeoForge 21.1.244, Java 21, ModDevGradle 2.0.143, Gradle 9.4.1
- 构建系统：`net.neoforged.moddev` 插件 + 原生 Mixin 支持（无 MixinGradle）
- 入口：`@Mod` 构造器注入 `IEventBus modBus`（无 `FMLJavaModLoadingContext`），渲染事件用 `RenderLevelStageEvent` + `NeoForge.EVENT_BUS`
- AT 使用 Mojang 名；`slotClicked` 需连同 5 个子类（InventoryScreen 等）一起 public 否则 NeoForm recompile 失败
- baritone 依赖：`jarJar(implementation("baritone:baritone-api-forge:1.21.2"))` 嵌入 `META-INF/jarjar/`，flatDir 指向项目目录；manifest 需声明 `MixinConfigs` + `MixinConnector`
- 不需要 refmap（官方映射环境，开发/生产映射一致）；`wurst.mixins.json` 不声明 refmap 字段
- `FMLLoader.getLoaderVersion()` 不存在，用 `FMLLoader.versionInfo().neoForgeVersion()`
- `RenderLevelStageEvent.getPartialTick()` 返回 `DeltaTracker`，需 `.getGameTimeDeltaPartialTick(false)`
- `processResources` 只对 `META-INF/neoforge.mods.toml` 和 `wurst.mixins.json` 做 expand（全局 expand 会因 UTF8 string too large 失败）
- 构建需 `JAVA_HOME` 指向 JDK 21（Java 25 会 SSL 失败）
- 产物：`build/libs/WurstB+ Plus-v1.5.0-NeoForge-1.21.1.jar`，已验证 `clean build` 成功

### 26.1.2 版本（versions/26.1.2/）
- Minecraft 26.1.2, Forge 26.1.2-64.1.0, Java 25, FG7, Gradle 9.4.1
- 渲染管线：`render()` → `extractRenderState()`，`GuiGraphics` → `GuiGraphicsExtractor`
- `blit()` 需 `RenderPipelines.GUI_TEXTURED` 第一参数
- 文字颜色需 8 位 Alpha（`0xFFxxxxxx`），6 位色视为透明
- `BufferUploader` 已移除，三角扇批量渲染不可用
- 圆角使用 4×4 超采样抗锯齿，大量圆角有性能开销
- baritone 通过 `libs/` flatDir `compileOnly`，运行时安全降级
- `AbstractBlockStateMixin` 已从 mixin 配置移除（`BlockStateBase` 不存在）
- 构建需 JDK 25 + `_JAVA_OPTIONS=-Djavax.net.ssl.trustAll=true`
- 产物：`build/libs/WurstB+ Plus-v1.5.0-Forge-26.1.2-all.jar`（~26 MB）
- CHANGELOG: `versions/26.1.2/CHANGELOG.md`，迁移记录: `versions/26.1.2/PORTING_TASK.md`

## 渲染管线关键文件

| 文件 | 作用 |
|------|------|
| `mixin/GameRendererMixin.java` | FOV、视角摇晃、眩晕、液体射线、Reach |
| `mixin/WorldRendererMixin.java` | 实体剔除、防失明 |
| `mixin/BackgroundRendererMixin.java` | 雾气渲染 |
| `mixin/BlockEntityRenderDispatcherMixin.java` | 方块实体渲染 |
| `mixin/IngameHudMixin.java` | GUI 渲染事件 |
| `util/render/RenderScope.java` | GL 状态保存/恢复 |
| `util/render/PostEffectQueue.java` | 后处理效果队列 |
| `util/render/AuraRangeRenderer.java` | 范围渲染 |
| `util/render/PlayerHaloRenderer.java` | 玩家光环渲染 |
| `util/render/EntityOcclusionCuller.java` | 遮挡查询 |
| `events/RenderListener.java` | 渲染事件（含 RenderScope 包裹） |
| `events/GUIRenderListener.java` | GUI 渲染事件 |
| `WurstForgeInitializer.java` | Forge 入口，触发 GUI 和渲染事件 |

## 构建命令
```bash
# 编译（所有版本通用）
.\gradlew.bat compileJava

# 打包 jar
.\gradlew.bat jar

# 完整构建（test task 有 Gradle 8.11 兼容问题）
# .\gradlew.bat build  ← 会失败，用 jar 替代

# 1.20.1 版本注意：
# - 使用 ForgeGradle 6.x（非 ModDevGradle）
# - 需要 JAVA_HOME 指向 JDK 17
# - Gradle 配置缓存已禁用（兼容性问题）
# - baritone 依赖通过 flatDir 从项目根目录加载

# NeoForge 1.21.1 版本注意（newforge/versions/1.21.1/）：
# - 需要 JAVA_HOME 指向 JDK 21，Java 25 会 SSL 失败
# - 构建用 `.\gradlew.bat clean build`（test 任务可用）
# - baritone jar（baritone-api-forge-1.21.2.jar）位于项目根目录，flatDir 加载
```

## 注意事项
- 文件编码必须保持 UTF-8，`Set-Content` 默认编码会损坏文件
- 1.21.1 的 BufferBuilder API 与主版本不同，无 `getBuilder()`/`building()`/`discard()`
- FMLJavaModLoadingContext.get() 在 Forge 52.x 中已标记为待删除
