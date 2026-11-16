# Moon 辉光（Fabric 1.20.1 / v1.5.2）

来源：本地 moon-rebase `55cd694e898025531de94e61e169ba6eb221f9f3` 的辉光实现。按最新要求，HUD 和实体 ESP 默认均使用固定浅紫色 `#B5BEF0`，取消时间和空间颜色流转。保留原有柔化、透明度、圆角和独立开关。

## 入口

- HUD 设置 → Moon 辉光：默认关闭，手动启用后围绕标准 HUD 背景、Rise TargetHUD、灵动岛和通知绘制。
- PlayerESP / MobESP → Moon 辉光：默认关闭，手动启用且对应 ESP 模块启用时绘制。现有方框、线条、2D 模式可继续使用。
- HUD 设置 → Logo 辉光：默认关闭，控制开始菜单与新增 Logo HUD 的同款图标轮廓辉光。
- 开关保存在现有 settings.json 中，旧配置缺少设置时使用默认值。

## 渲染适配

- 使用同一个 GLSL 150 核心配置着色器共享固定浅紫色，不加载 Moon 的旧版 OpenGL compatibility 渲染后端。
- HUD 按卡片尺寸生成圆角距离场软辉光，NanoVG 负责裁剪和进出动画。局部纹理每帧分槽复用，上限 64；纹理尺寸量化到 32 像素，单张上限 1024×512。没有为每个卡片执行全屏模糊。
- 实体使用 Minecraft 模型与透明纹理的白色轮廓遮罩，一组缓冲共用于 PlayerESP 和 MobESP，经过水平/垂直柔化、内部剔除后按屏幕坐标着色。摄像机视锥外不重绘，最多绘制最近 64 个目标。
- 关闭 Through walls 时按本地玩家到实体的视线判断筛除被遮挡目标；不是逐像素深度裁切。纹理分辨率最多半屏且宽度不超过 1280。
- 保存恢复帧缓冲、viewport、shader、纹理/采样器、混合和深度等 GL 状态；退出释放资源，窗口尺寸改变时重建实体缓冲。
- HUD 覆盖形状采用距离场，实体柔化采用两遍采样；这是 1.20.1 的效果适配，不宣称 Moon 全套遮罩与 Kawase 管线逐像素一致。

许可证及来源哈希随包位于 `META-INF/licenses/moon-glow/`。

## 验证

固定浅紫色更新：移除着色器时间输入和色相计算，HUD / ESP 使用同一常量颜色。此前彩虹截图仅为历史验证记录。

默认彩虹配色更新：在同一独立测试世界检查了 HUD 与僵尸/猪 ESP，实际显示多色辉光，无 MoonGlow / GL 错误。截图 `.dsh-tmp/moon-rainbow-preview.png`，运行日志 `.dsh-tmp/moon-rainbow-runtime.log`。本次为配色修改，没有新增单元测试。

最终完整构建成功，125 项测试全部通过，无失败、错误或跳过；`git diff --check` 通过。确认发布 jar 包含新渲染类、着色器、中文设置与 Moon 许可证。构建日志：`.dsh-tmp/moon-glow-final-build.log`。产物：`.dsh-tmp/hud-glass-build/libs/WurstB+ Plus-1.5.2-Fabric-1.20.1.jar`。

独立 Java 17 / Fabric 1.20.1 世界已验证 HUD、灵动岛、TargetHUD 编辑预览及僵尸/猪模型辉光；初版两个时间点的截图显示双色相位发生变化，关闭 MobESP 的 Moon glow 后模型光晕消失，HUD 继续流动。未出现 MoonGlow 错误或黑框。

双实体、1000×700、约 120 FPS 限帧环境下，ESP 辉光开关前后各一次稳定读数均为 118 FPS；这不是高实体数量压力测试，不能据此推断所有硬件零开销。PlayerESP 与 MobESP 共用模型管线，但没有再次登录多人服务器验证其他玩家皮肤/装备组合。

正常退出测试世界并完成存档，随后关闭独立测试进程。日志：`.dsh-tmp/moon-glow-runtime.log`；截图：`.dsh-tmp/moon-glow-preview.png` 与测试实例 `screenshots/screenshots/moon-glow-*`。离线认证服务错误与辉光渲染无关。
