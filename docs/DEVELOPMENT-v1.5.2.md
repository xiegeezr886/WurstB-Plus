# v1.5.2 开发基线

## SeedCrackerX / OpenSeed 挖矿联动（2026-11-16）

新增世界分类 SeedCracker / SeedMine：内嵌官方 SeedCrackerX 2.14.4，通过会话与种子哈希校验接收结果，按原版 1.20.1 放置规则重放 OpenSeed 矿脉预测，并提交 Baritone 寻路挖掘。共用 BaritoneMine 矿物列表，支持预览和破解后自动开挖，Baritone 模块互斥。来源、使用说明、准确性限制与尚待实机验证部分见 [移植记录](SEED-MINING-v1.5.2.md)。

## 加载提示与 Logo HUD 辉光（2026-11-16）

NeoRaven 加载界面标题和进度阶段跟随客户端语言翻译，补齐生成地形、接收地形、连接和常见世界进度中文。LogoHUD 添加独立「Logo 辉光」模块复选框，默认关闭，只影响 HUD Logo；开始菜单的辉光设置保持独立。中文运行截图已核对。

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

## 战斗模块后续重构（2026-11-16）

战斗采用 Epsilon 优先、NeoRaven 完善、LeavesHack 补充；自动搭路保持 NeoRaven。已替换模块、未覆盖的上游选项、原有专用模块清单与验证见 [战斗迁移记录](COMBAT-EPSILON-v1.5.2.md)。

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
| 战斗适配 | Epsilon 为主、NeoRaven 补充、LeavesHack 兜底；KillAura、自动搭路及相关模块适配；见 [战斗记录](COMBAT-EPSILON-v1.5.2.md) |
| 音乐与加载 | NeoRaven 音乐播放器、Opai 歌词 HUD，中文 NeoRaven 加载提示；见 [音乐播放器](MUSIC-NEORAVEN-v1.5.2.md)、[加载界面](LOADING-NEORAVEN-v1.5.2.md) |
| 种子挖矿 | SeedCrackerX 结构收集、OpenSeed 矿脉预测与 Baritone 联动；见 [SeedMine 验证](SEED-MINING-v1.5.2.md) |

### 本轮验证

- Fabric 1.20.1 完整 `build` 通过，显式启用测试任务后 **85 项测试、0 失败、0 错误**；最后移除顶部标题的改动另经 `assemble` 验证。
- 独立测试目录确认三个列表显示与主菜单入口、返回流程、服务器编辑返回，以及账号添加、收藏、编辑返回。
- 实机录帧检查先渐隐后翻转、切换时无原版全景闪现、圆滑滚动和选中渐变过渡。
- 测试世界仅用于列表显示，未实际进入；服务器探测使用本地不可连接地址，未进行外部服务器兼容性验证。在线账号认证未实测。
- 本地构建产物：`.dsh-tmp/hud-glass-build/libs/WurstB+ Plus-1.5.2-Fabric-1.20.1.jar`。
- 测试截图、日志、账号数据、工具链及上游参考源码均为本地验证资料，不进入版本库。

## v1.5.2 打包资源清单

以下资源由 `fabric/` 工程构建并进入 Fabric 1.20.1 的重映射 JAR。上游参考源码和 QA 资料留在本地 `reference/`、`.dsh-tmp/`，不随提交。

| 路径 | 用途 / 来源 | SHA-256 / 许可 |
| --- | --- | --- |
| `fabric/src/main/resources/assets/wurst/shaders/moon/flowing_glow.frag` | Moon 固定浅紫辉光的片元着色器 | `e7e8c38d2aa0d97046d51c690dd214f36cd91472adcd495e90b0dedeb4360e16`；Apache-2.0 归属见 `META-INF/licenses/moon-glow/` |
| `fabric/src/main/resources/assets/wurst/shaders/neoraven/m3_loading.frag` | NeoRaven 加载图形 GLSL 150 适配 | `f5b2f6cbf8171c8e3f09330092ff41ca5cb8e8f187dff8f16f61501b105b14be`；GPL-3.0 归属见 `META-INF/licenses/neoraven/` |
| `fabric/src/main/resources/assets/wurstpenguin/nanovg/fonts/MusicMaterialIcons.ttf` | 音乐播放器控件图标字体 | `ef149f08bdd2ff09a4e2c8573476b7b0f3fbb15b623954ade59899e7175bedda`；Apache-2.0 原字体声明见 `META-INF/licenses/neoraven/MaterialIcons-Apache-2.0.txt` |
| `fabric/libs/seedcrackerX-2.14.4.jar` | 内嵌官方 SeedCrackerX 2.14.4 发布包 | `cd61e1f68ca52218f61961af4ee9f97fcc62962a60acdd4525d075ac1bba42f6`；MIT，来源与许可见 `META-INF/licenses/seedcrackerx/` |
| `fabric/libs/seedcrackerx-deps/*.jar` | SeedCrackerX 所需逆向种子库与配置界面依赖，Gradle 运行时打包 | 上游每个依赖的元数据与许可证保留；另见 `META-INF/licenses/seedcrackerx/NOTICE.txt` |

Logo 与光晕 PNG 已在开始菜单资源目录；本轮复用既有文件，没有另复制一份 Logo。JAR 构建检查确认 SeedCrackerX 位于 `META-INF/jars/`，中文 / 英文词典、着色器和第三方许可均进入产物。

本次提交只同步源码及构建资源到 `main`，不创建 GitHub Release，也不上传正式下载 JAR；正式发布矩阵仍以 [发布手册](RELEASE.md) 标明的 v1.5.1 Release 为准。

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

HUD 与 PlayerESP / MobESP 已接入 Moon 彩色流转辉光，提供独立设置；实现范围及验证见 [辉光移植记录](MOON-GLOW-v1.5.2.md)。

CGUI 接入绑定键标签、平滑开关高亮及圆角方形滑块；按最终要求不添加模块/面板箭头；保留现有尺寸、主题和 RedFix 分类。详见 [CGUI-MOON-v1.5.2.md](CGUI-MOON-v1.5.2.md)。

## NeoRaven 音乐播放器（2026-11-16）

音乐播放器已接入其他分类与灵动岛，支持在线搜索/播放、推荐歌单、本地 MP3/LRC、歌词页及聊天界面播放控制。使用 NeoRaven 窄图标栏、五列封面网格、底部播放条，并恢复原版 840×520 画布、92% 屏幕适配及模块 GUI Scale 设置；补齐原版控件圆角、歌词换行与焦点动画。保持浅紫配色、分类栏半透、内容区不透明。实现范围、验证与账号登录限制见 [音乐播放器移植说明](MUSIC-NEORAVEN-v1.5.2.md)。

独立音乐歌词 HUD 已接入 Opai 的右对齐多行布局、翻译、淡出和 330ms 滚动动画，支持 HUD 编辑器拖动缩放。详见 [歌词 HUD](MUSIC-LYRICS-OPAI-v1.5.2.md)。

音乐播放器增加页面淡出/淡入、播放暂停图标渐变、波浪起停和进度插值；拖动进度立即跟手。播放栏复用主页圆形按钮玻璃材质并统一文字、封面和图标中心线，首页移除时段问候，多行歌词增加动态间距避免重叠。

继续细化音乐界面：播放按钮使用浅紫底和白色图标，分类选中块滑动、控件悬停渐变、播放键按压回弹与进度悬停预览；滚动条可拖动和点击。暂停保留歌曲高亮，点击当前歌曲可暂停/续播；新增加载反馈及推荐/搜索重试入口。

按后续反馈收敛视觉：分类滑动和播放键取消过冲回弹，按压幅度降至 3%；页面位移降至 10 单位，顶部搜索和账号区域保持稳定。歌单卡片采用单层低强度悬停，分区标题降为次级层级，设置控件统一右侧对齐；播放栏保持两行信息，音量和歌词开关加入渐变。1000×700 实机验证切页、设置控件、播放/暂停及音量展开，141 项测试通过。

盔甲 HUD 统一为药水同款渐变卡片并显示实际装备图标；颜色按耐久绿→黄→红连续变化。音乐侧栏移除“收起”，歌词固定左对齐和统一字号，纯黑仅作用于播放器内部，外围背景保持原样。详见音乐与药水 HUD 文档。

## NeoRaven 世界加载动画（2026-11-16）

后续界面调整：新增 [Moon 风格 Logo HUD](LOGO-HUD-MOON-v1.5.2.md)，复用开始菜单图标并支持拖动缩放；菜单和 HUD 的 Logo 辉光、HUD 与实体 ESP 的 Moon 辉光均默认关闭。加载动画按最终反馈保持原样。

世界准备、区块生成、接收地形及连接过程接入 NeoRaven 四形态渐变、旋转和呼吸动画，保留现有浅紫背景风格，显示真实生成进度。完整构建和 141 项测试通过，隔离存档已实机验证加载并正常进入世界；详见 [移植与验证记录](LOADING-NEORAVEN-v1.5.2.md)。
