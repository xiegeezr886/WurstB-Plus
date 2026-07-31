# WurstB+ Plus v1.5.0 MC26.1.2 更新日志

## 版本概述
WurstB+ Plus 首次适配 Minecraft 1.21.2 (Forge 26.1.2 / 64.1.0)。从 1964 个编译错误开始，完成完整 API 迁移。

## 系统要求
- Minecraft 1.21.2
- Forge 64.1.0
- Java 25
- Windows 10/11 x64

## 修复与改动

### 渲染修复 (v1.5.0-26.1.2-r2)
- **文字颜色修复**：26.1.2 的 `GuiGraphicsExtractor.centeredText()`/`text()` 方法正确处理 Alpha 通道。所有 6 位十六进制颜色值（如 `0xffffff`）在 Java 中相当于 `0x00ffffff`（Alpha=0，完全透明），导致文字不显示。已将 20 个文件中的颜色值改为 8 位格式（如 `0xFFffffff`），涉及文件：
  - `AltManagerScreen`, `AltEditorScreen`, `AddBookOfferScreen`, `EditBookOfferScreen`, `EditBookOffersScreen`, `EditColorScreen`, `EditItemListScreen`, `EditBlockListScreen`, `SelectFileScreen`, `KeybindProfilesScreen`, `ForcedChatReportsScreen`, `NcrModRequiredScreen`, `KeybindEditorScreen`, `KeybindManagerScreen`, `ZoomManagerScreen`, `EditBlockScreen`, `WurstOptionsScreen`, `RenderUtils`

- **暂停菜单 Wurst 图标修复**：`GameMenuScreenMixin.blit()` 使用 26.1.2 新签名，添加 `RenderPipelines.GUI_TEXTURED` 作为第一参数。

- **ClickGui 圆角渲染优化**：26.1.2 的 extract 渲染管线下，`fill()` 每次调用都在缓冲区记录指令。原始逐像素抗锯齿每帧产生数千次 `fill()` 调用导致缓冲区膨胀和帧率下降。改用水平条带 + 单像素边缘抗锯齿方案，大幅减少调用次数。`BufferUploader` 在 26.1.2 已移除，原版三角扇渲染方式无法直接移植。

### Mixin 修复
- **启动崩溃修复**：从 `wurst.mixins.json` 移除 `AbstractBlockStateMixin`。该 Mixin 的目标类 `BlockBehaviour.BlockStateBase` 在 MC 1.21.2 中已被移除，导致游戏启动时 FATAL 崩溃。

### API 迁移
- `render()` → `extractRenderState()`（Screen 类）
- `GuiGraphics` → `GuiGraphicsExtractor`
- `drawCenteredString()` → `centeredText()`
- `renderComponentTooltip()` → `setComponentTooltipForNextFrame()`
- `renderBackground()` → `extractTransparentBackground()`
- `blit()` 需 `RenderPipelines` 参数
- `onPress()` → `onPress(InputWithModifiers)`
- `BufferUploader` 已移除
- `NativeImage.setPixelRGBA()` → `setPixel()`
- `DynamicTexture` 构造签名变更

### 其他
- Baritone 依赖仅保留 `compileOnly`，运行时通过 `BaritoneUtils.detectAvailability()` 安全降级
- `AbstractBlockStateMixin` 已从 Mixin 配置移除
- 构建需 `_JAVA_OPTIONS="-Djavax.net.ssl.trustAll=true"`（Java 25 SSL 限制）

## 已知问题
- ClickGui 在大量圆角时可能有帧率影响（extract 管线限制），后续版本会继续优化
- 开发环境 refmap 警告不影响正式 jar

## 构建信息
- 构建系统：ForgeGradle 7.x / Gradle 9.4.1
- 映射：Mojang 官方映射
- 产物：`versions/26.1.2/build/libs/WurstB+ Plus-v1.5.0-MC26.1.2-all.jar`
