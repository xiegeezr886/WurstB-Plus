# WurstB+ Plus - Forge 64.1.0 (Minecraft 26.1.2)

## 环境要求
- Minecraft 26.1.2
- Forge 64.1.0
- Java 25

## 构建
```powershell
$env:_JAVA_OPTIONS = "-Djavax.net.ssl.trustAll=true"
.\gradlew.bat allJar --console=plain
```
> JDK 25 SSL 问题需设置 `_JAVA_OPTIONS`。baritone 从 `libs/` 目录 flatDir 加载。

产物：`build/libs/WurstB+ Plus-v1.5.0-MC26.1.2-all.jar`

## 26.1.2 渲染说明
- Screen API：`render()` → `extractRenderState()`
- 图形上下文：`GuiGraphics` → `GuiGraphicsExtractor`
- 文字颜色需 8 位 Alpha（`0xFFxxxxxx`）
- `blit()` 需 `RenderPipelines.GUI_TEXTURED` 参数
- `BufferUploader` 已移除
- 圆角使用 4×4 超采样抗锯齿

## 更新日志
见 `CHANGELOG.md`
