# MC 26.1.2 迁移任务状态

## 项目概况
- 目标目录：`C:\Users\ui863\Documents\trae_projects\VAP\versions\26.1.2`
- 迁移方向：WurstB+ Plus 适配到 Minecraft 26.1.2 / Forge 64.1.0
- 当前阶段：编译通过 + 运行时世界加载成功 + GUI 渲染修复

## 当前进度
- 起始错误：`1964`
- 当前编译错误：`0`（100% 完成）
- `compileJava`：通过
- `allJar`：通过（~26 MB）
- `runClient` 世界加载：通过

## 构建
```bash
cd C:\Users\ui863\Documents\trae_projects\VAP\versions\26.1.2
$env:_JAVA_OPTIONS = "-Djavax.net.ssl.trustAll=true"
.\gradlew.bat allJar --console=plain
.\gradlew.bat runClient --console=plain
```
> JDK 25 SSL 需要 `_JAVA_OPTIONS`。baritone 通过 flatDir 加载（`libs/` 目录）。

## 产物
- `build/libs/WurstB+ Plus-v1.5.0-Forge-26.1.2.jar`
- SHA-256：见构建输出

## 已修复问题

### 渲染
- **文字颜色**：26.1.2 的 `centeredText()`/`text()` 处理 Alpha 通道，6 位颜色 `0xffffff` = `0x00ffffff`（完全透明）。全部改为 8 位 `0xFFxxxxxx`（20 个文件）。
- **暂停菜单 Wurst 图标**：`blit()` 签名变更，需加 `RenderPipelines.GUI_TEXTURED` 为第一参数。
- **圆角渲染**：26.1.2 extract 管线下 `fill()` 逐像素调用产生缓冲区膨胀，换用水平条带 + 单像素抗锯齿（O(r) vs O(r²) 调用）。

### Mixin
- `AbstractBlockStateMixin` 从 `wurst.mixins.json` 移除（`BlockBehaviour.BlockStateBase` 在 26.1.2 不存在，导致 FATAL 崩溃）。

### API 迁移
- `BufferUploader` 已移除，MeshData 通过 `drawSpecial()` 或 `GuiGraphicsExtractor` 渲染。
- `NativeImage.setPixelRGBA()` → `setPixel()`。
- `DynamicTexture` 通过 `new DynamicTexture(supplier, w, h, true)` 创建。

## 注意事项
- Baritone：源码已改为安全降级（类加载失败时自动禁用），`compileOnly` 依赖需 `libs/baritone-api-forge-1.11.2.jar`
- 渲染：`blit` 参数顺序已适配 26.1.2（`RenderPipelines.GUI_TEXTURED` 在前）
- Mixin：开发环境 refmap 缺失为正常警告，不影响正式 jar
- ClickGui 圆角使用原始 4×4 超采样抗锯齿，大量圆角会产生性能开销（已知限制）
