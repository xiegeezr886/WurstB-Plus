# WurstB+ Plus - Forge 66.0.3 (Minecraft 26.3)

基于 Wurst 代码结构扩展的 Minecraft Forge 客户端模组，功能基线 v1.5.0（与其余版本工程一致，不含 v1.6 子系统）。完整文档见主分支。

> **状态：本轮只完成脚手架。** 本工程由 `versions/26.2` 克隆而来，版本锚点已改写为 26.3，
> 但**尚未编译、尚未打包、尚未启动验证**。下文凡标注「继承自 26.2」的内容都来自供体树，
> 没有在 26.3 上重新验证。

## 环境要求
- Minecraft 26.3
- Forge 66.0.3（Forge 官方尚未发布 26.3 的 recommended 版本，66.0.3 是当前的 latest）
- Java 25

## 构建
```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-25.0.4.101-hotspot"
$env:_JAVA_OPTIONS = "-Djavax.net.ssl.trustStoreType=Windows-ROOT"
.\gradlew.bat clean allJar test --console=plain
```
> 本机存在 TLS 代理时使用 Windows 根证书库。Baritone 通过根目录 `baritone-maven/` 解析并内嵌。
> 首次构建必须联网（要下载并反编译 Minecraft / Forge），不要加 `--offline`。

产物：`build/libs/WurstB+ Plus-v1.5.0-Forge-26.3.jar`

## 开发
```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-25.0.4.101-hotspot"
$env:_JAVA_OPTIONS = "-Djavax.net.ssl.trustStoreType=Windows-ROOT"
.\gradlew.bat runClient --console=plain
```

## 技术栈
- ForgeGradle 7.x
- Mixin 0.8.7 + MixinExtras 0.5.4
- Mojang 官方映射
- Baritone 1.20.0-26.3（通过 Forge JarJar 内嵌）

## 继承自 26.2 的渲染管线说明（未在 26.3 上重新验证）

26.2 使用 extract/render 分离管线（`extractRenderState`），供体树已按此适配，要点：

- **Screen API**：`render(GuiGraphics, int, int, float)` → `extractRenderState(GuiGraphicsExtractor, int, int, float)`
- **填充**：`fill()` 在 extract 阶段记录指令，后续批量渲染。大量逐像素调用会产生缓冲区膨胀
- **文字**：`centeredText()`/`text()` 处理 Alpha 通道，颜色需 8 位 hex（`0xFFxxxxxx`），6 位色视为透明
- **纹理**：`blit()` 需 `RenderPipelines.GUI_TEXTURED` 第一参数
- **BufferUploader**：已移除，三角扇批量渲染不可用
- **圆角**：使用 4×4 超采样抗锯齿

26.3 有两处底层改动会直接影响上面这套适配，**必须实测确认，不能假定成立**：

- **SDL3 取代 GLFW**：窗口与输入层被重写。本工程输入相关的 Mixin（`MouseHandlerMixin`、`KeyboardHandlerMixin`、`KeyBindingMixin`）以及约 30 个使用 `GLFW.GLFW_KEY_*` 常量与 `InputConstants.isKeyDown(Window, …)` 的类都可能失效。
- **OIT（顺序无关透明）取代 Improved Transparency**：可能影响自建的 `WurstShaderPipelines`（`BlendFunction.TRANSLUCENT`）与 `WurstRenderLayers`（`RenderSetup` / `OutputTarget`）。

## 继承的债务（供体 26.2 同样存在，非本次引入）
- `libs/baritone-api-forge-26.1.2.jar` 是无人引用的旧版残留（`flatDir` 只提供解析路径，无依赖引用它）。
- `pack.mcmeta` 的 `pack_format` 仍是 34，未核对 26.3 是否已提升。

更新日志见 `CHANGELOG.md`，迁移状态见 `PORTING_TASK.md`。
