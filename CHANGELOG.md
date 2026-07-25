# 更新日志

本文档记录 WurstB+ Plus 的正式版本变化。

## 1.5.0 - 2026-07-25

WurstB+ Plus `1.5.0` 是项目的第一个正式版本，提供完整的 Minecraft 1.20.1 Forge 客户端工程、资源、测试和构建配置。

### 核心内容

- 完成 Forge 47.4.10、Mojmap、Java 17 和 Gradle 8.11 工程迁移。
- 模组名称统一为 WurstB+ Plus，模组 ID 统一为 `wurstpenguin`，开发者署名为 Penguin。
- 提供右 Ctrl 浮窗 ClickGUI 与右 Shift 居中 Navigator 两套独立操作界面。
- 提供模块分类、模糊搜索、分层设置、滑块、枚举、颜色、文件和列表设置控件。
- 完成中文功能名称、GUI 文本、HUD 文本和通知文本的主要本地化。

### 功能系统

- 重构 Killaura、MultiAura、AimAssist、TriggerBot、Criticals、Reach、NoVelocity、AutoArmor、AutoTotem 和 AntiBot 等核心战斗功能。
- 重构 AutoSprint、SpeedHack、Flight、NoFall、NoSlowdown、Step、SafeWalk 和 ScaffoldWalk 等移动功能。
- 提供 PlayerESP、MobESP、ItemESP、NameTags、TargetShader、玩家光环、雷达和圆形小地图。
- 提供可编辑 HUD2，包括 HackList、通知、Target HUD、Combo、按键、背包、护甲、药水、TPS、速度、服务器和时间显示。
- 提供宏、路径点、代理、Addon、Discord RPC、配置档案和原生凭据存储支持。

### 架构与性能

- 使用统一事件总线、功能生命周期、冲突管理和分层设置模型。
- 引入实体快照、库存动作队列、目标追踪、旋转队列和移动规划器。
- 引入 RenderScope、VBO 几何缓存、异步纹理任务、线程本地像素缓冲和 GPU 实体遮挡查询。
- HUD 通过 Forge `RenderGuiEvent.Post` 单路派发，降低重复渲染和状态污染风险。
- ClickGUI 与 Navigator 菜单入口使用 `Info` 通知，不再误报普通模块的 `Disabled` 状态。

### 验证状态

- 50 个测试文件中的 133 项单元测试全部通过。
- Forge 重混淆、JarJar 打包和完整 Gradle `build` 通过。
- 发布目标：Minecraft 1.20.1、Forge 47.4.10、Java 17。
- 发布文件：`WurstB+ Plus-v1.5.0-MC1.20.1-all.jar`。
