# WurstB+ Plus - Forge 64.1.0 (Minecraft 26.1.2)

基于 Wurst 代码结构扩展的 Minecraft Forge 客户端模组。完整文档见主分支。

## 环境要求
- Minecraft 26.1.2
- Forge 64.1.0
- Java 25

## 构建
```powershell
$env:_JAVA_OPTIONS = "-Djavax.net.ssl.trustAll=true"
.\gradlew.bat allJar --console=plain
```
> JDK 25 SSL 需设置 `_JAVA_OPTIONS`。baritone 从 `libs/` flatDir 加载。

产物：`build/libs/WurstB+ Plus-v1.5.0-Forge-26.1.2.jar`

## 开发
```powershell
$env:_JAVA_OPTIONS = "-Djavax.net.ssl.trustAll=true"
.\gradlew.bat runClient --console=plain
```

## 技术栈
- ForgeGradle 7.x
- Mixin 0.8.7 + MixinExtras 0.4.1
- Mojang 官方映射
- Baritone 26.1.2 (compileOnly，运行时安全降级)

## 26.1.2 渲染管线说明

26.1.2 使用全新的 extract/render 分离管线（`extractRenderState`），主要变更：

- **Screen API**：`render(GuiGraphics, int, int, float)` → `extractRenderState(GuiGraphicsExtractor, int, int, float)`
- **填充**：`fill()` 在 extract 阶段记录指令，后续批量渲染。大量逐像素调用会产生缓冲区膨胀
- **文字**：`centeredText()`/`text()` 处理 Alpha 通道，颜色需 8 位 hex（`0xFFxxxxxx`），6 位色视为透明
- **纹理**：`blit()` 需 `RenderPipelines.GUI_TEXTURED` 第一参数
- **BufferUploader**：已移除，三角扇批量渲染不可用
- **圆角**：使用 4×4 超采样抗锯齿

## 已修复问题
- 文字颜色：20 个文件的 6 位 hex → 8 位 Alpha
- 暂停菜单 Wurst 图标：`blit()` API 适配
- 启动崩溃：移除 `AbstractBlockStateMixin`
- ClickGui 圆角性能：extract 管线 `fill()` 缓冲区膨胀修复

更新日志见 `CHANGELOG.md`，迁移详情见 `PORTING_TASK.md`。
