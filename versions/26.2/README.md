# WurstB+ Plus - Forge 65.1.0 (Minecraft 26.2)

基于 Wurst 代码结构扩展的 Minecraft Forge 客户端模组。完整文档见主分支。

## 环境要求
- Minecraft 26.2
- Forge 65.1.0
- Java 25

## 构建
```powershell
$env:_JAVA_OPTIONS = "-Djavax.net.ssl.trustStoreType=Windows-ROOT"
.\gradlew.bat clean allJar test --console=plain
```
> 本机存在 TLS 代理时使用 Windows 根证书库。Baritone 通过根目录 `baritone-maven/` 解析并内嵌。

产物：`build/libs/WurstB+ Plus-v1.5.0-Forge-26.2.jar`

## 开发
```powershell
$env:_JAVA_OPTIONS = "-Djavax.net.ssl.trustStoreType=Windows-ROOT"
.\gradlew.bat runClient --console=plain
```

## 技术栈
- ForgeGradle 7.x
- Mixin 0.8.7 + MixinExtras 0.5.4
- Mojang 官方映射
- Baritone 1.18.0-26.2 (embedded with Forge JarJar)

## 26.2 渲染管线说明

26.2 使用 extract/render 分离管线（`extractRenderState`），主要变更：

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
- Forge 并行构建：Java 类与资源恢复独立输出目录，clean 构建不再生成空壳 JAR

最终发布包已通过真实客户端进世界和 Baritone `#goto 0 88 0` 测试。

更新日志见 `CHANGELOG.md`，迁移详情见 `PORTING_TASK.md`。
