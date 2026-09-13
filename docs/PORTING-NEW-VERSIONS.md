# 新版本平行移植计划（v1.5 基线）

Updated: 2026-09-12（第二轮移植推进后）

不改已有工程：根目录 Forge 1.20.1（v1.6）、以及已完成的 1.20.1 / 1.21.1 / 1.21.11 / 26.1.2 / 26.2 三加载器。

新工程从 **v1.5** 最近邻拷贝，再按目标 MC API 修编译。**验收口径：`compileJava` 通过（不启动客户端）。**

## 总览：49 个新工程（17 版本 × 3 加载器，Forge 1.20.5 / 1.21.2 无官方包）

### ✅ 已通过 compileJava（21 个）

| 版本 | Forge | NeoForge | Fabric |
| --- | --- | --- | --- |
| 1.20.2 | ✅ 8.11 wrapper | ✅ NG userdev 7 | ✅ |
| 1.20.3 | ✅ | ✅ | ✅ |
| 1.20.4 | ✅ | ✅ | ✅ |
| 1.21 | ✅（mixinextras 去 deobf + libs baritone jar） | ✅（去 parchment + baritone jar） | ✅ |
| 26.1 | ✅ | ✅ | ✅ |
| 26.1.1 | ✅ | ✅ | ✅ |

### ⏳ 未通过（按剩余工作量排序）

| 版本 | 加载器 | 现状 / 阻塞点 |
| --- | --- | --- |
| 1.21.9 | Fabric | 1.21.11 源码，100 处：`GpuSampler`/`Identifier`/`RenderPipeline`（1.21.10+ 渲染管线命名）集中在 `GuiGraphicsExtractor`、`EasyVertexBuffer`、`WurstTitleMenu`、`Waypoint`、`AltRenderer` |
| 1.21.9 | NeoForge | 同上 152 处（另加 `WurstRenderLayers` 12 处） |
| 1.20.5 / 1.20.6 | Fabric | 1.20.4 源码 + 已修 5 文件，剩 97 处：`Font.drawInBatch` 需要 `Pose`（`RenderUtils` 54 处）、item components（`PotionUtils`/`MobType`/`SpawnPlacements.Type` 已用 1.21.1 文件解决） |
| 1.21.2 | Fabric / NeoForge | 1.21.1 基线 + 类名映射已落地（见下表）。NeoForge 剩 136 处、Fabric 剩 ~15 文件，全部集中在三类 **1.21.2 真实重构**：① `RenderSystem.setShader(GameRenderer::getXxxShader)` 移除 → 新 `RenderPipeline` 体系（PostEffectQueue/Window/WaypointsManager/LsdHack）；② `PostChain/TextureTarget/RenderTarget.clear/resize` 与 `setProjectionMatrix(...,VertexSorting)` → 新签名（`ProjectionType`）；③ `ServerboundMovePlayerPacket.Pos/Rot` 构造器变化。另 `GuiGraphics.blit` 需 `RenderType.GUI_TEXTURED` 首参（NF 已批量插入） |
| 1.21.3–1.21.8, 1.21.10 | 三加载器 | 源码已铺好（Fabric 1.21.3-1.21.5=1.21.1 基线，其余=1.21.11 基线），未逐版本修错 |
| 1.20.5 / 1.20.6 | NeoForge | 工具链：MDG 2.0.143 报 `neoforge-moddev-bundle` 变体缺失；NG userdev 7 走到 `neoFormRecompile` 失败（原版源码重编译）。需 MDG `legacy` 模块或升级 MDG |
| 1.20.5 / 1.20.6 | Forge | 1.20.5 无官方 Forge；1.20.6（Forge 50.2.0）源码仍为 1.20.1 基线，未同步 1.20.2-1.20.4 修复 |
| 1.21.3–1.21.10 | Forge | FG7 脚手架 + 1.21.11 源码已铺，未编译 |

## 总览：49 个新工程（17 版本 × 3 加载器，Forge 1.20.5 / 1.21.2 无官方包）

### ✅ 已通过 compileJava（26 个，2026-09-13）

| 版本 | Forge | NeoForge | Fabric |
| --- | --- | --- | --- |
| 1.20.2 / 1.20.3 / 1.20.4 | ✅ | ✅ | ✅ |
| 1.21 | ✅ | ✅ | ✅ |
| 1.21.2 | 无官方包 | ✅ | ✅ |
| 1.21.3 | ✅ | ✅ | ✅ |
| 1.21.4 | ✅ | ✅ | ✅ |
| 26.1 / 26.1.1 | ✅ | ✅ | ✅ |

 Forge 1.21.2-1.21.4 用的是 1.21.11 的 FG7 树拷贝（build.gradle 是 Forge 1.21.11 的），compile 通过即视为可行。

### ⏳ 未通过（按剩余工作量排序）

| 版本 | 加载器 | 阻塞点 |
| --- | --- | --- |
| 1.21.5 | NF/FB | 294+ 处：1.21.5 引入 GpuTexture/RenderPipeline 但**无** GuiRenderState/GpuSampler/Identifier——1.21.11 的 GuiGraphicsExtractor 设计（基于 GuiRenderState+GpuSampler）不适用，需按 1.21.5 的 pipeline API 重写 render 层（GuiGraphicsExtractor/EasyVertexBuffer/RenderUtils/WurstRenderLayers/ClickGuiScreen/WurstTitleMenu/两个 Minimap 等） |
| 1.21.6-1.21.10 | NF/FB | 1.21.11 基线（GuiRenderState 架构接近 1.21.11），差异集中在 GpuSampler→GpuTextureView 改名、Identifier 引入时点等；每版 100-150 处 |
| 1.21.5-1.21.10 | Forge | FG7 树未编译；预期与 NF/FB 同类 |
| 1.20.5 / 1.20.6 | NF | userdev 7 在 neoFormRecompile 失败（vanilla 源码自身编译错）；需 MDG legacy 模块或 NG 8 |
| 1.20.5 / 1.20.6 | FB | 1.20.4 基线 + 5 文件已修，剩 97 处：Font.drawInBatch 需要 Pose（RenderUtils 54 处）、item components |
| 1.20.5 / 1.20.6 | Forge | 1.20.5 无官方包；1.20.6（Forge 50.2.0）源码仍为 1.20.1 基线，未同步 1.20.2-1.20.4 修复 |

## 已固化的工具链方案（可直接复用）

1. **Forge 1.20.2-1.20.4**：Gradle 8.11 wrapper + FG6 + `component.disabled` exclude + `baritone-api-forge-1.20.1.jar` 放工程根，`flatDir dirs ".", "${rootProject.projectDir}/../.."`。
2. **NeoForge 1.20.2-1.20.4**：NG userdev 7.0.184（MDG 2.0.143 无法解析 20.2/20.3/20.4 的 bundle 变体）+ `EntityMaxUpStepAccessor`（1.20.2 起 `maxUpStep` 为 private）。
3. **Forge 1.21**：mixinextras-forge 不要 `fg.deobf`；baritone jar 放 `libs/`（flatDir）。
4. **NeoForge 1.21+**：删除 build.gradle 里 parchment 块（克隆来的 parchment 版本号多不存在）。
5. **26.x**：JDK 25 目录为 `C:/Program Files/Microsoft/jdk-25.0.4.101-hotspot`；JDK 21 为 `C:/Program Files/Java/jdk-21.0.11`。
6. **网络**：Mojang piston 下载偶发 `BUFFER_UNDERFLOW`（Forge 26.1.1 首次失败、重试成功），失败先重试再排查。

## 已落地的 API 适配（可向后续版本复制）

- 1.20.2：CustomPayload/`BrandPayload`、Authlib 移除密码登录、`User` UUID 构造、`ServerData.Type.OTHER`、`DefaultPlayerSkin.get(...).model()`、`renderBackground(g,mx,my,pt)` 四参、`mouseScrolled` 四参、`EditBox.tick()` 移除、骨粉三参、`getDebugOverlay().showDebugScreen()`。
- 1.20.3/1.20.4：`StemGrownBlock`→`Blocks.MELON/PUMPKIN`、`DownloadedPackSource` 移到 `client.resources.server`、`PlainTextContents.LiteralContents`、`ObjectSelectionList` 5 参构造、AABB 用 `Vec3.atLowerCornerOf`、`Checkbox.builder`（无 maxWidth）。
- 全部 Forge：`WurstForgeInitializer` 的调试屏判断；`WurstTitleMenu` 的加载器版本号行按加载器分叉。

## 下一轮建议顺序

1. **1.21.2 Fabric/NeoForge 收尾**：shim 已就位（见上），对 `RenderUtils`/`PostEffectQueue`/`EasyVertexBuffer`/`AltRenderer`/`GuiGraphicsExtractor`/`LsdHack` 等约 15 个文件做 1.21.2 渲染 API（`Tesselator.begin` 返回 BufferBuilder、`addVertex/setUv`、`Matrix4fStack`、`ShaderProgram`、`CompiledShaderProgram`）逐文件移植。注意：**1.21.11 的文件不能直接搬**——1.21.10+ 已把 `ResourceLocation` 改名 `Identifier`、新增 `RenderPipelines`，与 1.21.2 不兼容。
2. Fabric/NeoForge 1.21.6-1.21.10（1.21.11 基线，改 `Identifier`/`RenderPipeline` 等 4-5 个渲染文件的版本 shim）
3. Fabric/NeoForge 1.21.3-1.21.5（1.21.1 基线 + 1.21.2 映射表）
4. Fabric 1.20.5/1.20.6（`RenderUtils` Pose 化）
5. NeoForge 1.20.5/1.20.6 工具链（MDG legacy）
6. Forge 1.21.3-1.21.10 / 1.20.6

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
