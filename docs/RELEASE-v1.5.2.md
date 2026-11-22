# WurstB+ Plus v1.5.2 正式版

版本日期：2026-11-18。以 Fabric 1.20.1 为功能基线，源码覆盖 Minecraft 1.20.1 至 26.3 的 23 个版本、67 个工程：Fabric 23、Forge 21、NeoForge 23。全部工程使用 1.5.2 正式构建编号。

## 发布资产核验（2026-11-22）

GitHub Release `v1.5.2` 于 **2026-11-18 22:04（UTC+8）**发布，标签指向 `f9d6be04f8f5e9aaf746ffe30a81164853d1d737`。截至核验日，67 个已上传 JAR 的文件名、字节数和 SHA-256 均与本地归档记录一致。详见[资产清单](v1.5.2-release-assets.json)：`name` 是完整文件名，`size` 以字节为单位，`digest` 是带 `sha256:` 前缀的摘要。在 PowerShell 中可用 `Get-FileHash -Algorithm SHA256 -LiteralPath <文件路径>` 对照摘要的十六进制部分，字母大小写不影响结果。

本地旧目录 `build/release-v1.5` 中的 67 个 1.5.1 JAR 不属于此清单。后续审查提交 `3900f680`、`5709fb20` 的兼容性修复与模块移除也不在这些线上 JAR 中。资产摘要一致不代表每个整合包都通过启动、保存和读回测试。

发布后合入 `main` 的具体修复见[变更记录的“未发布”一节](../CHANGELOG.md#post-v152-fixes)。下文“主要变更”只描述 11 月 18 日的正式版。

## 安装

1. 从 [GitHub Release](https://github.com/xiegeezr886/WurstB-Plus/releases/tag/v1.5.2) 检查实际可下载的 JAR，按自己的 Minecraft 版本和加载器选择。可用组合见 [README 版本支持矩阵](../README.md#版本支持矩阵)。Forge 没有 1.20.5 和 1.21.2 两个组合。
2. 按游戏版本选择 Java：1.20.1–1.20.4 使用 Java 17，1.20.5–1.21.11 使用 Java 21，26.x 使用 Java 25。Fabric 实例还需要对应游戏版本的 Fabric API。
3. 替换 `mods` 目录中的旧版 WurstB+ Plus JAR，避免同一个实例同时加载两个版本。升级前备份现有配置和存档；已保存的设置会覆盖新默认值。

## 主要变更

- 开始菜单使用圆形玻璃按钮，单人、多人和账户页面使用翻转卡片、圆滑滚动和选中渐变。导航器与 ClickGUI 统一配色，保留各自布局。
- 保留 Soar HUD，使用浅紫主题、Rise TargetHUD、Opai 灵动岛与药水卡片。新配置默认启用 RedFix HackList 和 LogoHUD 辉光，默认隐藏帧率、延迟与坐标；实体 Moon 辉光只描绘模型轮廓。
- 接入 NeoRaven 音乐界面、Opai 歌词 HUD 和中文加载提示。
- 同步玩法模块、Baritone 和种子联动；渲染与输入按 Minecraft 版本适配，包括 26.3 的 SDL 输入和 RenderPearl。

## 验收与限制

67 个工程的 1.5.2 正式构建均通过生产打包与递归审计，共通过 **15,074 项单元测试**，无失败、错误或跳过。已逐项复核当前源码指纹与生产 JAR 的 SHA-256，分加载器结果见 [全版本移植记录](PORTING-v1.5.2.md)。

Minecraft **1.20.1 Fabric** 正式 JAR 已在 Java 17 独立实例中通过主菜单启动，确认 WurstB+ Plus 和 Baritone 加载成功。**26.3 的 Fabric、Forge、NeoForge** 正式 JAR 均已通过主菜单启动、单人世界加载、NanoVG 初始化、存档保存和正常退出。其他组合的实机验证、完整战斗、搭路、在线音乐播放和自动种子求解仍有未测试的路径。

NanoVG HUD 依赖 OpenGL。26.x 的 Vulkan 支持受各加载器和具体版本限制，详见 [维护手册](RELEASE.md)。构建阶段、实机验收和远端资产上传分别记录；版本矩阵中的源码存在不表示对应 JAR 已上传。
