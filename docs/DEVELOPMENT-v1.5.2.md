# v1.5.2 开发基线

开发分支：`codex/v1.5.2`。目标版本：`v1.5.2`。

## 基线工程

- 工程根目录：[`fabric/`](../fabric/)，Minecraft **1.20.1**。
- 源码：`fabric/src/main/java/`；资源：`fabric/src/main/resources/`。
- Java **17**，Fabric Loader **0.17.0**，Fabric API **0.92.6+1.20.1**，Loom **1.9.2**。
- 起点：`main` 的 `6b2575b`，包含创建开发分支时保留的本地未提交改动。
- v1.5.2 的功能开发和首轮验证以此工程为准；跨版本移植在基线功能验证后进行。

在仓库根目录构建基线：

```powershell
.\fabric\gradlew.bat -p fabric build
```

导航器的 Rise 移植进度与验证说明见 [NAVIGATOR-RISE-v1.5.2.md](NAVIGATOR-RISE-v1.5.2.md)。

ClickGUI 的 Opai 外观移植与验证说明见 [CLICKGUI-OPAI-v1.5.2.md](CLICKGUI-OPAI-v1.5.2.md)。

LeavesHack 的 29 模块移植、排除项与验证说明见 [LEAVESHACK-v1.5.2.md](LEAVESHACK-v1.5.2.md)。

## 当前开发状态（2026-11-16）

本轮提交覆盖 `fabric/` 的 v1.5.2 开发成果，其他加载器与 Minecraft 版本未同步。
这是开发分支源码更新，尚未发布正式 Release。

| 内容 | 当前实现与记录 |
| --- | --- |
| 开始菜单 | Nextgen 圆形控件、中文入口、Navigator、浅紫模糊壁纸与 W 标志轮廓辉光；见 [开始菜单](TITLE-MENU-NEXTGEN-v1.5.2.md) |
| 世界 / 服务器 / 账号列表 | 三页共用 Opai 卡片布局，取消顶部大标题，中文操作按钮沿用黑色半透圆角样式；见 [菜单与动画](MENUS-OPAI-v1.5.2.md) |
| 页面切换 | 主菜单 240ms 渐隐后，目标卡片 700ms 翻转；返回先收起卡片再淡入主菜单，完整接管 TitleScreen 渲染以消除原版背景闪现 |
| 列表交互 | 圆滑滚动、键盘选中滚入可见区域、浅紫横向渐变高亮及平滑淡入淡出；只保留可见条目的动画状态 |
| 账户后端 | 保留账号保存、登录、收藏、编辑、导入导出和确认流程；Moon 阶段说明见 [历史与现状](ACCOUNT-MANAGER-MOON-v1.5.2.md) |
| HUD | Soar HUD 为基础，Rise TargetHUD、可选毛玻璃主题、浅紫强调色、灵动岛圆角；见 [TargetHUD](TARGETHUD-RISE-v1.5.2.md)、[Opai 药水效果](POTION-HUD-OPAI-v1.5.2.md) |
| 分类与 CGUI | RedFix HackList / 八种玩法分类、Moon CGUI 交互；见下方对应记录 |
| 模块与本地化 | LeavesHack 29 模块适配，中英文界面、设置、聊天与命令翻译；各轮范围与边界见 [变更记录](../CHANGELOG.md) |

### 本轮验证

- Fabric 1.20.1 完整 `build` 通过，显式启用测试任务后 **85 项测试、0 失败、0 错误**；最后移除顶部标题的改动另经 `assemble` 验证。
- 独立测试目录确认三个列表显示与主菜单入口、返回流程、服务器编辑返回，以及账号添加、收藏、编辑返回。
- 实机录帧检查先渐隐后翻转、切换时无原版全景闪现、圆滑滚动和选中渐变过渡。
- 测试世界仅用于列表显示，未实际进入；服务器探测使用本地不可连接地址，未进行外部服务器兼容性验证。在线账号认证未实测。
- 本地构建产物：`.dsh-tmp/hud-glass-build/libs/WurstB+ Plus-1.5.2-Fabric-1.20.1.jar`。
- 测试截图、日志、账号数据、工具链及上游参考源码均为本地验证资料，不进入版本库。

本机使用 `scripts/build-env.ps1` 和本地 Gradle init 文件配置工具链、输出目录和测试任务。
`test` 若显示 `SKIPPED`，不能作为通过依据；实际结果以 JUnit XML 为准。

## 本地参考源码

2026-11-13 拉取各仓库默认分支，均为浅克隆（`--depth 1`），包含该提交的完整跟踪文件。
完整提交号、来源及检出信息保存在本地 `reference/clients/v1.5.2-sources.json`。

| 项目 | 本地路径 | 提交 | 当前上游工程 |
| --- | --- | --- | --- |
| [Rise-6.9.5](https://github.com/AnShanArchive/Rise-6.9.5) | `reference/clients/Rise-6.9.5/` | `f58ef9c` | Minecraft 1.8.9，Gradle，JDK 21+ |
| [NeoRaven](https://github.com/Matr1ze/NeoRaven) | `reference/clients/NeoRaven/` | `1a4ae73` | Minecraft 1.8.9 Forge，Java 8 |
| [moon-rebase](https://github.com/xiaozhuhou233/moon-rebase) | `reference/clients/moon-rebase/` | `55cd694` | Minecraft 1.8.9 MCP / LWJGL 3，Maven |
| [OpaiBounce](https://github.com/snmwdd/OpaiBounce) | `reference/clients/OpaiBounce/` | `6a6bc99` | LiquidBounce 主题、配套脚本及音乐服务；Svelte / TypeScript / Java |
| [OpenOpal](https://github.com/ZSZ7/OpenOpal/tree/main) | `reference/clients/OpenOpal/` | `0411550` | Minecraft 1.21.10 Fabric，Java 21，Yarn 映射 |
| [Epsilon](https://github.com/NekoyaHouse/Epsilon) | `reference/clients/Epsilon/` | `2fb4aad` | Minecraft 26.3 Fabric / NeoForge，Java 25，默认分支 `26.3.x` |
| [LeavesHack](https://github.com/MrBZBZ/LeavesHack) | `reference/clients/LeavesHack/` | `13daf96` | Minecraft 1.21.11 Fabric / Meteor Client 插件，Java 21+，默认分支 `main` |

`reference/` 由主仓库忽略，各参考项目保留独立 `.git/`。这些源码用于开发对照，不参与基线构建。
来源、许可证文件情况和可否删除见 [REPO-HYGIENE.md](REPO-HYGIENE.md)。

## RedFix HackList / 分类（2026-11-16）

Fabric 1.20.1 已接入 RedFix 分类渐变条目、按文字宽度排序、模式标签与 200ms 动画。CGUI / Navigator / TabGUI 共用八种玩法分类，保留 HUD / 客户端入口。细节见 [HACKLIST-REDFIX-v1.5.2.md](HACKLIST-REDFIX-v1.5.2.md)。

## Moon CGUI 交互完善（2026-11-16）

CGUI 接入绑定键标签、平滑开关高亮及圆角方形滑块；按最终要求不添加模块/面板箭头；保留现有尺寸、主题和 RedFix 分类。详见 [CGUI-MOON-v1.5.2.md](CGUI-MOON-v1.5.2.md)。
