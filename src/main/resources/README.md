# WurstB+ Plus - Forge 1.20.1

基于 Wurst 代码结构扩展的 Minecraft Forge 客户端模组。完整文档见主分支。

## 环境要求
- Minecraft 1.20.1
- Forge 47.4.10
- Java 17

## 构建
```powershell
.\gradlew.bat jarJar --console=plain
```
产物：`build/libs/WurstB+ Plus-v1.5.0-Forge-1.20.1.jar`

## 开发
```powershell
.\gradlew.bat runClient --console=plain
```

## 技术栈
- ForgeGradle 6.0
- MixinGradle 0.7 + Mixin 0.8.7
- Mojang 官方映射
- MixinExtras 0.4.1
- Baritone 1.20.1 (jarJar 嵌入)

## 架构概要
入口 `WurstForgeInitializer`，在 `FMLClientSetupEvent` 初始化。GUI 由 `RenderGuiEvent.Post` 驱动，`IngameHudMixin` 处理遮罩取消。HUD 元素通过 `HudManager` 统一管理布局和锚点。

详见主分支 README。
