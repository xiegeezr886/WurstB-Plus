# WurstB+ Plus - Forge 1.21.1

基于 Wurst 代码结构扩展的 Minecraft Forge 客户端模组。完整文档见主分支。

## 环境要求
- Minecraft 1.21.1
- Forge 52.1.16
- Java 21

## 构建
```powershell
.\gradlew.bat jarJar --console=plain
```
产物：`build/libs/WurstB+ Plus-v1.5.0-Forge-1.21.1.jar`

## 开发
```powershell
.\gradlew.bat runClient --console=plain
```

## 技术栈
- ForgeGradle 6.x
- Mixin 0.8.7 + MixinExtras 0.4.1
- Mojang 官方映射
- Baritone API 1.21.1 (flatDir)

## 1.21.1 vs 1.20.1 差异
- `renderBackground()` 签名变更：1 参数 → 4 参数 `(context, mouseX, mouseY, partialTicks)`
- BufferBuilder API：`Tesselator.getInstance().begin()` → BufferBuilder，`.addVertex()`，`.build()` → MeshData
- `ScreenMixin.renderBlurredBackground` 用于 Wurst 屏幕模糊取消
- LiquidsHack 使用 `HitResultRayTraceEvent` + `includeFluids` 参数

## 1.21.1 渲染管线说明
世界叠加层通过 `RenderLevelStageEvent.Stage.AFTER_LEVEL` 驱动，PoseStack 只包含视图矩阵（相机旋转），投影由 shader 的 ProjMat 处理。

详见主分支 README。
