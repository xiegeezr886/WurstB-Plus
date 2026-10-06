# WurstB+ Plus - Forge 1.21.7

基于 Wurst 代码结构扩展的 Minecraft Forge 客户端模组。完整文档见主分支。

## 环境要求
- Minecraft 1.21.7
- Forge 1.21.7-57.0.3
- Java 21

## 构建
```powershell
.\gradlew.bat clean allJar test --console=plain
```
产物：`build/libs/WurstB+ Plus-v1.5.0-Forge-1.21.7.jar`

## 开发
```powershell
.\gradlew.bat runClient --console=plain
```

## 技术栈
- ForgeGradle 7.x
- Mixin 0.8.7 + MixinExtras 0.5.4
- Mojang 官方映射
- Baritone 1.15.0（官方声明支持 1.21.6 / 1.21.7 / 1.21.8，经 Forge JarJar 内嵌）

> ⚠️ **内嵌 Baritone 的版本规则**
>
> Baritone 的 Mixin 目标使用 Fabric intermediary 方法名（`method_XXXXX`），该编号在每个
> Minecraft 版本都会重新分配。**必须使用官方声明支持本工程 `minecraft_version` 的那个
> Baritone 发布版**，否则 Mixin 会在 APPLY 阶段找不到目标方法；又因为 Baritone 的 mixin
> 配置是 `"required": true`，游戏会在窗口出现前直接崩溃，且加载器不会给出"版本不兼容"提示。
>
> 换版本后请执行校验：
> ```powershell
> pwsh ..\..\scripts\verify-embedded-baritone.ps1 -ProjectDir .
> ```
>
> 历史事故：本工程曾内嵌为 MC 1.21.11 构建的 Baritone 1.17.0
> （坐标 `1.17.0-1.21.11-mc1.21.7`），在 1.21.7 上必然启动崩溃。

## 1.21.7 与 1.20.1 的差异
- `renderBackground()` 签名变更：1 参数 → 4 参数 `(context, mouseX, mouseY, partialTicks)`
- BufferBuilder API：`Tesselator.getInstance().begin()` → BufferBuilder，`.addVertex()`，`.build()` → MeshData
- `ScreenMixin.renderBlurredBackground` 用于 Wurst 屏幕模糊取消
- LiquidsHack 使用 `HitResultRayTraceEvent` + `includeFluids` 参数

## 渲染管线说明
世界叠加层通过 `RenderLevelStageEvent.Stage.AFTER_LEVEL` 驱动，PoseStack 只包含视图矩阵（相机旋转），投影由 shader 的 ProjMat 处理。

详见主分支 README。
