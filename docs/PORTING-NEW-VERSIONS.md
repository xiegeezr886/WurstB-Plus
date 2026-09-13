# 新版本平行移植计划（v1.5 基线）

Updated: 2026-09-13（第四轮：全部工程 compileJava 通过）

不改已有工程：根目录 Forge 1.20.1（v1.6）、以及已完成的 1.20.1 / 1.21.1 / 1.21.11 / 26.1.2 / 26.2 三加载器。

新工程从 **v1.5** 最近邻拷贝，再按目标 MC API 修编译。**验收口径：`compileJava` 通过（不启动客户端）。** 有测试源码的工程同时要求 `compileTestJava` 通过。

## 状态总表（本轮结束，全部 `BUILD SUCCESSFUL`）

矩阵共 55 个**有效**工程（Forge 无官方 1.20.5 / 1.21.2 包，故为 19+19+17）；仓库里另有 6 个无上游版本的空占位目录，合计 61 个目录。

| MC | Fabric | NeoForge | Forge |
| --- | --- | --- | --- |
| 1.20.2 / 1.20.3 / 1.20.4 | ✅ | ✅ | ✅ |
| 1.20.5 | ✅ | ✅ | 无官方包 |
| 1.20.6 | ✅ | ✅ | ✅ |
| 1.21 / 1.21.1 | ✅ | ✅ | ✅ |
| 1.21.2 | ✅ | ✅ | 无官方包 |
| 1.21.3 / 1.21.4 | ✅ | ✅ | ✅ |
| 1.21.5 | ✅ main+test | ✅ main+test | ✅ main+test |
| 1.21.6 / 1.21.7 / 1.21.8 | ✅ | ✅ | ✅ |
| 1.21.9 / 1.21.10 | ✅ | ✅ | ✅ |
| 1.21.11 | ✅ | ✅ | ✅ |
| 26.1 / 26.1.1 / 26.1.2 / 26.2 | ✅ | ✅ | ✅ |

`compileTestJava` 也已通过的工程：`fabric/versions/1.21.5–1.21.8`、`neoforge/versions/1.21.5`（test 任务 135 用例全绿）、`versions/1.21.5`、`versions/1.21.7`。其余工程的 `src/test` 是从同代已通过的兄弟工程同步过去的，见下面"测试源码同步"一节。

### 本轮最重要的四个"降本"发现

1. **同 MC 版本的兄弟加载器可以直接互相移植。** 同一 MC 版本下，Forge/NeoForge/Fabric 的源码树差别几乎只在 loader glue 上（Forge 1.21.6 与 1.21.7 之间只差 `mods.toml` 一个文件；Forge 与 NeoForge 1.21.7 只差 `SubmitNodeCollectionMixin` + `mods.toml` + `jarjar/metadata.json` 三个）。做法：把已通过的同版本兄弟工程 `src/main` 整体覆盖过来，再把 loader 专属文件还原。本轮 **Forge 1.21.7、Forge 1.21.5、NeoForge 1.21.5、Forge 1.21.4/1.21.3 全部靠这条路走通**，比手改 API 快一个数量级。辅助脚本：`D:\WurstB\tmp-recon\cross-port.py`、`port-forge-1215.py`。
2. **移植的"供体"必须选对代。** 1.21.5 的 loader glue 若从 1.21.6/1.21.7 搬，会带进 1.21.6 才有的 `GuiGraphicsExtractor`、`SubmitNodeStorage`、Forge 56 event bus，反而从 0 错变成 42 错；换成同代的 `versions/1.21.4` 供体后立刻降到 7 错。**跨加载器移植时，"同 MC 版本的另一个加载器" > "同加载器的邻近版本"。**
3. **1.20.5 / 1.20.6 的"过度移植"问题**：这两个版本的 API 其实贴近 1.20.4（`Tesselator.getBuilder()` + `begin(mode,format)`、`BufferBuilder.RenderedBuffer`、`ResourceLocation.tryParse/tryBuild`、`Enchantments.*` 是普通 `Enchantment`、`RenderGuiEvent.Post`），而仓库里的树曾被写成 1.21 风格（`MeshData`、`DeltaTracker`、`DisconnectionDetails`）。正确做法是**反向还原**到 `fabric/versions/1.20.6` 的内容（Fabric 的 1.20.5 与 1.20.6 java 树逐字节相同），并补回缺失的 26 个文件（含 10 个 mixin accessor）。
4. **Forge 侧编译期访问私有成员：优先 accessor mixin，其次 AT。** FG7 的 `accesstransformer.cfg` 只有在 `minecraft { accessTransformer = true }` 且 AT 用 **official(mojmap) 名字**时才生效——1.21.6/1.21.8/1.21.5 三处都靠这个开关解决。1.21.9/1.21.10 则改用 `GuiGraphicsAccessor` mixin 解决 `GuiGraphics.guiRenderState`。注意 1.21.3/1.21.4 上 FG7 连 `accessTransformer` 属性都不接受，只能用 accessor mixin。


### 本轮固化的做法：**1.21.9 / 1.21.10 共用同一套 shim**

1.21.6–1.21.10 的源码树都是 **1.21.11 基线**，差异集中在下面那张表。脚本在 `D:\WurstB\tmp-recon\`：
`port-1219-shims.py`（包名/类名批量替换）、`post-shim-fixes.py`（`identifier()`→`location()` 等）、`fix-editcolor-resize.py`、`errs2.py`（编译日志归并）。
渲染层 4 个文件 + 2 个 shader 资源可直接从已通过的工程复制：
`WurstShaderPipelines.java`、`WurstRenderLayers.java`、`util/EasyVertexBuffer.java`、`util/render/GuiGraphicsExtractor.java`、`resources/assets/wurst/shaders/core/fogless_lines.{vsh,fsh}`。

完整操作手册：
- `D:\WurstB\tmp-recon\PLAYBOOK-1.21.5-1.21.10.md`（含 Forge/FG7 说明、1.21.6–1.21.8 的输入层重构章节）
- `D:\WurstB\tmp-recon\PLAYBOOK-1.21.5.md`（1.21.5 专属）

### 测试源码同步（本轮新增的收尾步骤）

`src/test` 里的 `HudLayoutTest` 等文件继承自 1.21.11 基线，引用了该代才有（或该代签名不同）的类，因此 `compileJava` 通过并不代表 `compileTestJava` 通过。做法：**从同代已通过的兄弟工程整体同步 `src/test`**——

| 目标 | 测试源码来自 |
| --- | --- |
| `versions/1.21.5` | `neoforge/versions/1.21.5` |
| `versions/1.21.3`、`versions/1.21.4` | `fabric/versions/1.21.4` |
| `{fabric,neoforge}/versions/1.21`–`1.21.4`（除上面两行） | `neoforge/versions/1.21.5` |
| `{fabric,neoforge}/versions/1.21.5–1.21.11`、`26.x` | 各自的同代已通过工程（多数本来就是绿的） |
| `neoforge/versions/1.20.2–1.20.6` | **不放测试源码**（这些工程没有 JUnit 依赖） |
| `fabric/versions/1.21`、`1.21.2` | 删除 `src/test`（1.21.5 的测试用了该代没有的 API） |

另一个坑：**`gradlew clean` 会删掉 Loom/NeoGradle 放在 `build/` 里的中间产物**，离线重跑就会失败（Fabric 报一堆 `net.minecraft.*` 找不到，NeoForge 报 `cacheVersionExecutableClient<版本>` 失败）。最终验收用 `compileJava compileTestJava`（**不加 `clean`**）即可。

**NeoForge 1.20.2–1.20.6 的 `compileJava` 必须联网跑**：它们的 MC 产物（client jar / neoForm 反编译结果）没有落在本地缓存里，加 `--offline` 会直接死在 `cacheVersionExecutableClient*` 或 `neoFormRecompile` 上。1.21 及以后各版加 `--offline` 都没问题。`neoforge/versions/1.20.2–1.20.6` 也**没有 JUnit 依赖**，因此这几个工程不放 `src/test`。

第三个坑：**`baritone-maven` 里手工改过的 jar 可能有非法 MANIFEST**。`fabric/versions/26.2` 依赖的 `baritone-api-fabric-1.18.0-26.2.jar` 的 `META-INF/MANIFEST.MF` 把 `MixinConfigs` 放在了空行之后（变成了第二个 section），javac 于是对**每一个** `net.minecraft.*` 导入报 `invalid manifest format (line 5)`（101 个错误，看着像源码全崩，其实是 jar 的锅）。修法：把 `MixinConfigs`/`MixinConnector` 移回主 section 重打包（旧 jar 备份为 `*.jar.badmanifest`）。

第四个坑：**`versions/1.21.3`、`versions/1.21.4` 的测试源码要取自 `fabric/versions/1.21.4`**，不能取自 `neoforge/versions/1.21.5`（后者用 1.21.5 的 API，`ComponentParentTest` / `HudLayoutTest` / `AttributeValuePlannerTest` 会报"找不到符号"）。同理 `neoforge/versions/1.21.3`、`1.21.4` 的测试源码取自 `neoforge/versions/1.21.5` 是可行的（同为 1.21.5 时代的 API）。



## 1.21.11 基线 → 1.21.6–1.21.10 的 API 差异表（已用 javap 逐项核对）

| 1.21.11 名称 | 1.21.6–1.21.10 名称 |
| --- | --- |
| `net.minecraft.resources.Identifier` | `net.minecraft.resources.ResourceLocation` |
| `IdentifierException` | `net.minecraft.resources.ResourceLocationException` |
| `client.renderer.rendertype.RenderType` 等 | `client.renderer.RenderType` |
| `GpuSampler` | `GpuTextureView` |
| `net.minecraft.util.Util` | `net.minecraft.Util` |
| `entity.npc.villager.Villager` | `entity.npc.Villager` |
| `entity.vehicle.boat.ChestBoat` | `entity.vehicle.ChestBoat` |
| `entity.vehicle.minecart.*` | `entity.vehicle.*` |
| `entity.animal.equine.*` | `entity.animal.horse.*` |
| `entity.animal.fish.*` / `animal.golem.*` / `monster.zombie.*` | 上一层包 |
| `GuiGraphics.renderOutline()` | 不存在 → 用 4 条 `fill()` |
| `GuiGraphics.textHighlight(...,boolean)` | 只接受 4 参 |
| `AbstractButton.renderContents` / `renderDefaultSprite` | `renderWidget` / 不存在 |
| `DebugScreenEntryList.isOverlayVisible()` | `isF3Visible()` |
| `VertexConsumer.setLineWidth()` | 不存在 → 删除 |
| `ResourceKey.identifier()` | `location()` |
| `Screen.resize(int,int)` / `init(int,int)` | `resize(Minecraft,int,int)` / `init(Minecraft,int,int)` |
| `Matrix3x2fc` | `Matrix3x2f` |
| `RenderPipelines.register(p)` | Fabric public；**NeoForge/Forge private** → `RenderPipelines.PIPELINES_BY_LOCATION.put(p.getLocation(), p)` |
| `RenderSetup` / `OutputTarget` / `TextureTransform` / `rendertype` 包 | 都不存在（1.21.11 才有） |
| `MouseButtonEvent` / `KeyEvent` / `InputWithModifiers` | **1.21.9+ 才有**；1.21.6–1.21.8 用 `mouseClicked(double,double,int)` 等旧签名与无参 `onPress()` |
| `SubmitNodeStorage` / `SubmitNodeCollector` | 1.21.9+ 才有；1.21.6–1.21.8 需移除 |
| `PlayerModelType` | 1.21.9+ 才有 |

## 1.21.4 → 1.21.5 的 API 差异表（已用 javap 核对）

| 1.21.4 | 1.21.5 |
| --- | --- |
| `blaze3d.platform.GlStateManager` / `GlConst` | `blaze3d.opengl.GlStateManager` / `GlConst` |
| `VertexBuffer`（含 `Usage`） | 删除 → `GpuBuffer` + `RenderPass` |
| `BufferUploader.drawWithShader(MeshData)` | 删除 → `RenderSystem.getDevice().createBuffer(...)` + `createCommandEncoder().createRenderPass(...)` |
| `RenderSystem.getShader()` / `setShader()` | 删除 |
| `ShaderInstance` / `ShaderProgram` / `CompiledShaderProgram` | 全删除（只剩 `ShaderManager`） |
| `ToastComponent` | `ToastManager` |
| `UseAnim` | `ItemUseAnimation` |
| `SwordItem` / `TieredItem` / `DiggerItem` | `stack.has(DataComponents.WEAPON)` / `stack.has(DataComponents.TOOL)` |
| `ArmorItem` | `DataComponents.EQUIPPABLE` + `ATTRIBUTE_MODIFIERS.compute(0, slot)` |
| `EnchantedBookItem` / `MilkBucketItem` | `stack.is(Items.ENCHANTED_BOOK)` / `stack.is(Items.MILK_BUCKET)` |
| `ElytraItem.isFlyEnabled(stack)` | `stack.is(Items.ELYTRA) && 耐久未耗尽` |
| `EffectRenderingInventoryScreen` | `InventoryScreen` / `AbstractContainerScreen` |
| `FoodProperties.effects()` / `PossibleEffect` | `DataComponents.CONSUMABLE` → `onConsumeEffects()` → `ApplyStatusEffectsConsumeEffect.effects()` |
| `client.resources.model.SimpleBakedModel` | 删除（`mixin/BasicBakedModelMixin.java` 整文件删除） |
| `FogRenderer.MobEffectFogFunction`（private） | `mixin/FogRendererAccessor.java` 的该方法删除 |
| `player.input.forwardImpulse` / `leftImpulse`（字段） | `player.input.getMoveVector().y` / `.x`（方法） |
| `RenderPipelines.POSITION_COLOR` | ⚠️ **1.21.5 里不存在**（javap 已确认）→ 用 `RenderPipelines.GUI`；另有 `LINES`、`DEBUG_FILLED_BOX`、`DEBUG_QUADS` |
| `RenderPass` 取 `GpuTextureView` | 取 `GpuTexture`；`drawIndexed(int,int)` 只有 2 个参数 |
| `RenderType.pipeline()` | `RenderType.getRenderPipeline()` |

1.21.5 **已有**：`GuiGraphics`（老式直接绘制，**无** GuiRenderState / GuiGraphicsExtractor / `SubmitNodeStorage`）、`RenderPipeline` / `RenderPipelines`（`GUI`、`LINES`、`DEBUG_FILLED_BOX`、`DEBUG_QUADS`、`LINES_SNIPPET`、`GUI_SNIPPET`、`MATRICES_*`，且 `PIPELINES_BY_LOCATION` 已是 **public**）、`GpuBuffer`、`RenderPass`、`RenderSystem.getDevice()`、`RenderSystem.getSequentialBuffer(Mode)`、`ClientInput#getMoveVector()`。**没有** `RenderSystem.getDynamicUniforms()` / `bindDefaultUniforms()` / `writeTransform`。

1.21.5 的输入层仍是**旧签名**（`mouseClicked(double,double,int)`、`keyPressed(int,int,int)`、无参 `onPress()`），与 1.21.6–1.21.8 相同。HUD 钩子在 `IngameHudMixin` 触发 `new GUIRenderEvent(...)`（1.21.5 还没有 `GuiMixin`）。


## 基线对照（重要）

| 目录 | 基线 |
| --- | --- |
| `fabric`/`neoforge`/`versions` 的 `1.20.2–1.20.4` | 1.20.1 派生 |
| `fabric/versions/1.20.5–1.20.6` | 1.20.4 派生 |
| `{fabric,neoforge}/versions/1.21–1.21.4` | **1.21.1 / 1.21.4 派生**（旧输入 API、旧渲染 API） |
| `{fabric,neoforge}/versions/1.21.6–1.21.11`、`versions/1.21.6–1.21.11` | **1.21.11 基线** |
| `versions/1.21.5`、`neoforge/versions/1.21.5` | 已由 `neoforge/versions/1.21.4` / `neoforge/versions/1.21.5` 重建（不再使用 1.21.11 基线） |

⚠️ **`versions/1.21.3` 与 `versions/1.21.4` 的 `src` 曾与 `versions/1.21.11` 逐字节相同**（只差 `mods.toml`）。它们名义上属"1.21.1 派生"那一代，实际是从 1.21.11 往下移植的，不能拿 1.21.1 当参考。`versions/1.21` 也含有无法编译的文件，请用 `versions/1.21.1` 做该代 Forge 的参考。

## 已固化的工具链方案（可直接复用）

1. **Forge 1.20.2-1.20.4**：Gradle 8.11 wrapper + FG6 + `component.disabled` exclude + `baritone-api-forge-1.20.1.jar` 放工程根，`flatDir dirs ".", "${rootProject.projectDir}/../.."`。
2. **NeoForge 1.20.2-1.20.4**：NG userdev 7.0.184（MDG 2.0.143 无法解析 20.2/20.3/20.4 的 bundle 变体）+ `EntityMaxUpStepAccessor`。
3. **Forge 1.21**：mixinextras-forge 不要 `fg.deobf`；baritone jar 放 `libs/`（flatDir）。
4. **NeoForge 1.21+**：删除 build.gradle / gradle.properties 里的 parchment 块与 `parchment_*` 属性（克隆来的版本号多不存在，例如 `parchment-1.21.9:2025.12.20` 在 maven 上不存在）。**注意：parchment 只是红鲱鱼**——仓库里的 `build.gradle` 根本没有 parchment 块，`neoFormRecompile` 的失败与它无关。
5. **1.20.5 / 1.20.6（NeoForge & Forge）必须用 JDK 21，不能用 JDK 17。** NeoGradle 用 *project toolchain* 重编译 MC 自己的源码，而 MC 1.20.5/1.20.6 源码用了 `List.getFirst()` / `List.reversed()` / `MatchException`。改 `build.gradle` 的 `JavaLanguageVersion.of(21)` 与 `options.release = 21`，并用 JDK 21 构建，`neoFormRecompile` 立刻通过。
6. **26.x**：JDK 25 = `C:/Program Files/Microsoft/jdk-25.0.4.101-hotspot`；JDK 21 = `C:/Program Files/Java/jdk-21.0.11`。所有 1.20.2–1.21.11 的工程都用 JDK 21。
7. **网络**：Mojang piston / Forge maven 偶发下载失败，先重试再排查；某版本首次构建**不要**加 `--offline`。Forge/FG7 与 NeoGradle 的首次构建要 10–45 分钟（下载 + 反编译），别在第一次就跑 `--offline`。
8. **工程内的 `compile-errors-latest.txt` / `compile-errors-pass2.txt` 是异机残留，已失效，忽略。**
9. **不要用 `... 2>&1 | Out-File` 收集 Gradle 输出**：这样会把 Gradle 的 stdout 吞掉（日志里只剩几行无关文本，"BUILD SUCCESSFUL" 消失）。正确写法是 `& .\gradlew.bat ... > $log 2>&1`，然后用 `$LASTEXITCODE` 判定。
10. **`gradlew` 的退出码是权威**：脚本循环验收 55 个工程时读 `$LASTEXITCODE`（0 = 通过）比 grep 日志更稳。

## 剩余工作（截至本轮结束）

**编译验收已全部完成**——**61 个工程目录**（`versions/` 19 + `fabric/versions/` 21 + `neoforge/versions/` 21）的 `compileJava` 均为 `BUILD SUCCESSFUL`；其中 34 个有测试源码的工程 `compileTestJava` 也全部通过。验收日志见 `D:\WurstB\tmp-recon\acceptance\`（`<loader>-<版本>.main.txt` / `.test.txt`），更早一轮的日志在 `D:\WurstB\tmp-recon\sweep\`。以下是**不影响编译、但影响运行时**的遗留项：

1. **Mixin 目标描述符不匹配（javac 只报 warning）**：`fabric/versions/1.20.5`、`1.20.6` 各 13 条（`GameRendererMixin.renderLevel`、`ClientPlayerInteractionManagerMixin.getPickRange/hasFarPickRange`、`EntityRendererMixin.renderNameTag`、`MouseMixin.turnPlayer`、`MinecraftClientMixin.getProfileProperties`、`ChatScreenMixin.handleChatInput`、`ChatHudMixin` 两处 `@Shadow`、`DisconnectedScreenMixin` 的 `@Shadow` 字段）。运行时这些注入会失败。
2. **`SubmitNodeCollectionMixin` 在 1.21.6–1.21.8 不存在**（目标类在该代没有），姓名牌缩放/透视调整在这些版本上失效。
3. **`AbstractSelectionList$Entry#render` 参数顺序陷阱**：真实签名是 `(GuiGraphics, int index, int Y, int X, int entryWidth, int entryHeight, ...)`——**Y 在 X 前**。历史上 NeoForge 1.21.6/1.21.7 树把它写成 `(index, x, y, width, height)`，`javac` 全是 `int` 无法发现，列表渲染整体错位。移植时务必对照同 MC 的 Fabric 树。
4. **多个工程缺少 `RenderTypeAccessor` / `freecam.*` / `sodium.OcclusionCullerMixin` 的 mixins.json 条目**（继承自上游），运行时可能需要补。
5. **1.21.5 的功能降级**（该项在 1.21.5 上无可用公共 API，属上游限制）：`PostEffectQueue` 被 stub、`LsdHack` 被强制禁用；`KeyBindingMixin` 的鼠标模拟退化为 `setDown`（`MouseHandler#onPress` 在该代是 private）。
6. **Forge 1.21.3/1.21.4 的功能降级**：删除了 1.21.11 才有的 `WurstShaderPipelines`、`GuiGraphicsExtractor`、`FogRendererMixin`、`AtmosphericFogEnvironmentMixin`、`SubmitNodeCollectionMixin`、`freecam/LocalPlayerMixin`，大气雾与自定义 shader pipeline 在该两版失效。
7. **编译通过 ≠ 运行时可用**：本轮所有验收均未启动客户端、未执行 mixin 应用、未跑 `gradlew build`。个别工程（如 `neoforge/versions/1.21.5`）已顺带跑通 JUnit（51 类 135 用例全绿），但不代表游戏内行为正确。

### 已通过但**仅编译验收**的告警（后续可做）

- 1.21.11 的树缺少 `assets/wurst/shaders/core/` 资源，但代码引用 `wurst:core/fogless_lines`。
- 1.21.9 的 `WurstOptionsScreen` / `CleanUpScreen` 按钮在 `renderDefaultSprite()` 被移除后失去 sprite 背景（纯外观问题）。

### 健康检查提示

- **`build/classes/java/main` 不是唯一的输出目录**：Forge 26.x 的 class 落在 `build/sourceSets/main/`，NeoForge 的 `build/` 里还含整套反编译 MC 的 class（1 万+）。统计"是否编译成功"要 `Get-ChildItem build -Recurse -Filter *.class`，否则会把已通过的工程误判为 0 个 class。


## 1.21.2 类名映射表（已从 named jar 确认）

| 1.21.1 | 1.21.2 |
| --- | --- |
| `net.minecraft.world.item.UseAnim` | `net.minecraft.world.item.ItemUseAnimation` |
| `net.minecraft.client.player.Input` | `net.minecraft.client.player.ClientInput` |
| `net.minecraft.client.gui.components.toasts.ToastComponent` | `...toasts.ToastManager` |
| `net.minecraft.client.renderer.ShaderInstance` | `net.minecraft.client.renderer.ShaderProgram` |
| `new VertexBuffer(VertexBuffer.Usage.STATIC)` | `new VertexBuffer()` |
| `ArmorItem.Type` | 已删除（unused import 直接移除） |
| `TieredItem` | `SwordItem`（或组件判断） |
| `ElytraItem.isFlyEnabled(stack)` | `stack.getDamageValue() < stack.getMaxDamage() - 1` |
| `EnchantedBookItem` instanceof | `stack.is(Items.ENCHANTED_BOOK)` |
| `MilkBucketItem` instanceof | `stack.is(Items.MILK_BUCKET)` |
| `ClientboundExplodePacket.getKnockbackX/Y/Z()` + 三字段 mixin | record：`playerKnockback(): Optional<Vec3>` + 单字段 `@Mutable @Accessor("playerKnockback")` |
| `FoodProperties.PossibleEffect`（effects()） | `DataComponents.CONSUMABLE` → `Consumable.onConsumeEffects()` → `ApplyStatusEffectsConsumeEffect.effects()` |
| `EffectRenderingInventoryScreen` | 已删除；用 `InventoryScreen` / `AbstractContainerScreen` 判断 |
