# WurstB+ Plus 变更记录

WurstB+ Plus 是由 Penguin 开发的 Wurst 增强客户端。仓库当前覆盖 **23 个 MC 版本（1.20.1–26.3）× Forge / NeoForge / Fabric = 67 个独立 Gradle 工程**；发布资产与下载入口见 [README.md](README.md)。

---

<a id="post-v152-fixes"></a>

## 未发布：v1.5.2 之后的修复（2026-11-22）

以下修复已合入 `main`（`b067ee28`），不在 11 月 18 日发布的 67 个 v1.5.2 JAR 中。已发布文件以[资产清单](docs/v1.5.2-release-assets.json)为准。

- **渲染兼容性**（`1e51e8d5`）：33 个工程中的火焰遮罩和流体透明度注入不再要求原始常量必定存在，避免其他模组先改写常量时因找不到注入位置而启动失败。
- **Fabric**（`3900f680`）：23 个工程在检测到 Better Third Person 时让它控制相机旋转，未安装时保留原行为。仅在已实测的 1.20.1、1.20.4、1.21.1、1.21.6 调整最低 Loader 要求。
- **Forge**（`3900f680`）：1.21、1.21.1 消除重复网络依赖；1.21.9 将 MixinExtras 0.5.4 随包加载，修复需要外置补件才能启动的问题。26.3 修正 Baritone 正常关停时误报中断异常的问题。
- **NeoForge**（`3900f680`）：17 个工程不再把已内嵌的 Java-WebSocket 和两个 Netty 库重复解包到顶层。26.3 将清理提前到客户端关闭阶段，避免加载器卸载后再清理时抛出异常。
- **26.2 退出卡死**（Fabric、Forge、NeoForge，`3900f680`）：关闭客户端时结束 SeedCrackerX 执行器并取消 Baritone 缓存后台任务，保留游戏的退出看门狗。
- **源码与发布检查**（`3900f680`、`5709fb20`）：67 个工程的源码均已移除 ForceOP、CrashChest 和 Throw；发布预检核对包内版本、加载器和 Minecraft 目标，拒绝把旧 JAR 改名后作为新产物。
- **版本分支维护**（`b1f4f28c`）：生成各 Minecraft 版本的快照时保留原始 Git 路径。

**验证范围：** 合并后 50 项维护脚本测试通过，不等于 67 个新 JAR 均通过游戏实测。原整合包、修订副本与候选客户端的单人保存及读回结果分别记录在[维护手册](docs/RELEASE.md)。

## v1.5.2 正式版（2026-11-18）

- 以 Fabric 1.20.1 为基线，移植到 23 个 Minecraft 版本的 67 个加载器工程；构建版本统一为 1.5.2。
- 统一开始菜单、单人／多人／账号页面、导航器和 ClickGUI；更新 Soar HUD、Rise TargetHUD、Opai 灵动岛和音乐歌词。
- 新配置默认启用 RedFix HackList 和 LogoHUD 辉光，默认隐藏帧率、延迟与坐标；Moon 实体辉光只显示模型轮廓。
- 安装、兼容性、构建与实机结果见 [正式版说明](docs/RELEASE-v1.5.2.md) 和 [全版本移植记录](docs/PORTING-v1.5.2.md)。以下各批是当时的开发记录。

## v1.5.2 Fabric 1.20.4 移植（2026-11-17，开发中）

- 基于 v1.5.1 三方合并新界面、HUD、模块、音乐和种子联动，保留 1.20.4 皮肤与网络适配。
- 更新滚轮、世界加载、实体加入和加载文本注入；禁止 NanoVG 原生依赖传递带入 LWJGL core，解决 GLFW 版本冲突。
- 接入官方 SeedCrackerX 2.14.6 并固定校验值，保留原始嵌套库、来源和许可。
- 编译、146 项测试、生产重映射及产物崩溃检查通过；界面、已知种子预览、Baritone 挖矿/拾取与退出清理已做开发客户端验证。
- 当前三个目标已接入 dev 移植，其余 63 个目标尚未同步；完整功能验收仍按 [同步计划](docs/PORTING-v1.5.2.md) 推进。

## v1.5.2 首批跨加载器移植（2026-11-17，开发中）

- 根 Forge 与 NeoForge 1.20.1 同步基线菜单、CGUI、HUD、战斗、音乐、加载动画和资源；保留加载器入口与元数据。
- 已知种子的会话校验、矿脉预测及 Baritone 联动接入；自动 SeedCracker 后端待适配，暂不注册破解模块。
- 两端编译、各 146 项单元测试、生产打包及既有产物崩溃检查通过；开发客户端界面、挖矿与退出清理验证通过，完整验收待完成。
- 修正渲染检查器漏读重载方法造成的误报，并纳入根 Forge；67 工程静态渲染检查和 17 项负例测试通过。
- 本批实施两个目标，后续进度和验收边界见 [同步计划](docs/PORTING-v1.5.2.md)。

## 根工程恢复 Forge 1.20.1 v1.5.1（2026-11-17）

- 使用项目内 `dist/VAP-v1.5.1-Forge-1.20.1` 替换根源码及构建输入，1,268 个文件逐项校验一致。
- Forge 1.20.1 编译打包通过；验证结果与产物摘要见 [根工程说明](docs/ROOT-FORGE-1.5.1.md)。
- 更新当前版本文档、构建任务和状态生成器，清理退役版本的功能说明及历史入口。

## v1.5.2 全版本同步准备（2026-11-17）

- 新建 `codex/v1.5.2-all-versions`，以 Fabric 1.20.1 为基线，盘点其余 66 个同步目标。
- 新增只读准备工具、67 工程差异矩阵，以及按 API 代际分批的 [开发计划](docs/PORTING-v1.5.2.md)。
- 当前未批量覆盖目标源码或调整版本号，SeedCrackerX 和渲染/输入接口需分别适配。

---

## v1.5.2 资源、SeedCrackerX 与 Baritone 联动（2026-11-16，main 开发源码，未发布）

- 添加 Moon 流转辉光和 NeoRaven 加载动画 GLSL 着色器、音乐界面 Material Icons 字体，以及 SeedCrackerX 2.14.4 官方发布 JAR 与所需嵌套库；逐项来源、许可证、SHA-256 和打包路径见 [开发文档资源清单](docs/DEVELOPMENT-v1.5.2.md#v152-打包资源清单)。
- SeedCrackerX 结构收集通过种子哈希与连接会话校验；OpenSeed 矿脉预测配合 BaritoneMine 的矿物列表寻路挖掘，提供预览、破解后自动开挖和 Baritone 自动化互斥。
- NeoRaven 加载界面提示跟随客户端语言；LogoHUD 增加独立、默认关闭的辉光设置。菜单、音乐、HUD 与战斗适配细节见本版本专门文档。
- 按后续要求，开始菜单 Logo 的全局辉光默认开启；游戏内 LogoHUD 仍使用独立、默认关闭的开关。
- 实机：目标钻石实际挖出并取得 2 颗；下界样本预测的 39 个远古残骸位置均匹配已加载测试区块；SeedCrackerX 从沙漠神殿实际收集到 9/32 位基础数据，但未完成全种子破解。
- 验证：Fabric 1.20.1 完整构建通过，146 项测试零失败；加载中文截图、LogoHUD 开关、预览、停止、冲突接管与自动联动均实测。v1.5.2 正式发布资产尚未生成或上传。
- 完整功能范围、资源清单、构建及验证边界见 [v1.5.2 开发文档](docs/DEVELOPMENT-v1.5.2.md) 与 [SeedMine 移植记录](docs/SEED-MINING-v1.5.2.md)。

---

## v1.5.2 菜单统一与交互完善（2026-11-16，开发分支，未发布）

- 单人、多人和账号管理统一为 Opai 卡片列表；按钮改为中文并沿用客户端原来的黑色半透圆角材质，移除三页顶部大标题。
- 开始菜单先 240ms 渐隐，再展开 700ms 卡片翻转；从渲染入口跳过原版全景绘制，修复动画交接时闪现原版背景。
- 三个列表加入圆滑滚动、键盘选择滚入可见区域，以及浅紫横向渐变高亮和选中 / 悬停淡入淡出。
- 保留世界和服务器的现有操作后端；账号保留登录、添加、收藏、编辑、导入导出、恢复会话和确认流程，扩展操作放入「更多」。
- 同步本轮累计开发文档：Nextgen 开始菜单、Rise TargetHUD、Soar HUD 主题、Opai 药水效果、RedFix HackList / 分类、Moon CGUI 交互、LeavesHack 模块适配和中文本地化。
- 验证：Fabric 1.20.1 完整 build 与 85 项测试通过；最终移除标题后 assemble 通过。独立实例检查三个入口 / 返回、服务器编辑、账号添加 / 收藏 / 编辑、滚动和高亮动画。未实际加载测试世界或验证外部服务器、在线认证。
- 范围及实现细节见 [开发状态](docs/DEVELOPMENT-v1.5.2.md) 与 [菜单说明](docs/MENUS-OPAI-v1.5.2.md)。

---

## v1.5.2 界面文案与 HUD 标签汉化（2026-11-15，源码已改，未发布）

范围仍为 `fabric/`（Minecraft 1.20.1 Fabric，v1.5.2 基线）。

- HUD 模块标签 26 处改为 `WURST.translate(...)`（如 `No armor`、`No boss`、`Nothing is playing`、`Health: %.1f / %.1f`、`Tab list hidden`），中英词典同步补齐。
- GUI 界面文案：HUD 编辑器标题、Wurst 选项页签（设置/管理/链接）、按键管理器与按键编辑器、配置档、缩放管理、服务器搜索与清理界面、离线/正版账号界面（标题、账号统计、加号按钮、删除提示）、NoChatReports 提示与“可举报”标签，统一走 `WurstClient.tr(...)`（新增静态快捷方法）。
- 命令 `Syntax:` 前缀改为翻译键；Other Feature / 命令的主操作按钮文案（View Changelog、Export Data、Disable/Enable、Show Statistics、Get Position 等）与 HUD 模块的 `Toggle` 改为翻译键。
- `PossibleKeybind` 支持可变参数翻译描述（`Toggle %s`、`Check %s`、`Increase %s`、`Set %s to %s`、八种颜色等 17 条），设置面板生成按键提示时自动使用当前语言。
- 数字：`zh_cn.json` 5335 → 5432（+97），`en_us.json` 733 → 830（+97，含格式串的英文基础条目）。
- 验证：`compileJava` 与 `clean build` 通过；jar 内 `zh_cn` 5432 键、`en_us` 830 键；抽查界面标签、按键描述、`Syntax: %s`、HUD 标签在两种语言下均正确。
- 未覆盖：账号编辑器输入框占位文案（`AltEditorScreen`）、个别 Hack 的自定义提示（AdaAutoHotbar、BaritoneMine、ImgTips、ImgWorldStats 等）、部分第三方专名与枚举展示仍保留英文。

---

## v1.5.2 聊天提示与命令提示汉化（2026-11-15，源码已改，未发布）

在上一轮界面/设置汉化的基础上，把游戏内聊天反馈也接入翻译系统；范围仍为 `fabric/`（Minecraft 1.20.1 Fabric，v1.5.2 基线）。

- `ChatUtils.message/warning/error/syntaxError` 现在把英文原文当作翻译键查表（与设置说明同一机制）：字面量消息只需补 `zh_cn.json`，动态消息改成 `%s` 格式串。
- 82 条动态聊天消息自动改为格式串（如 `"Keybinds loaded: " + name` → `"Keybinds loaded: %s", name`），另有约 10 条复数/`String.format`/三元拼接的提示手工改写（GiveCmd、PathCmd、MacrosCmd、WaypointsCmd、BookBot、ImgTips 图腾计数、GAntiExplosion、GSuperVClip、KnockbackDirection 等）。
- `WARNING` / `ERROR` / `Syntax error:` 前缀与 HUD 通知标题（Info / Enabled / Disabled）改为翻译键。
- `CmdError` / `CmdSyntaxError` 新增可变参数构造器；77 条动态命令错误改为格式串，52 条字面量错误通过聊天层自动翻译；命令用法（`getSyntax()`）也进入翻译键。
- `EnumSetting` 新增 `getTranslatedValueName()`，ClickGUI 下拉框（`ComboBoxComponent` / `ComboBoxPopup`）显示中文枚举值；保存值、`.set` 命令与配置格式保持不变。
- 新增 `zh_cn.json` 键 768 条（4567 → 5335）：聊天 129、命令错误/用法 136、枚举值 476、聊天前缀与通知标题 6、图腾计数 8、`Total: N xxx` 汇总 6、常量提示（OptiFine / VanillaTweaks）2、中文格式串 5；`en_us.json` 补 238 条（495 → 733）英文基础条目，保证英文与其他语言下 `String.format` 正常。
- 验证：`fabric` 工程 `compileJava`、`build` 通过；脚本校验 265 条消息键 0 缺失、占位符与 `§` 计数全部一致，476 条枚举值无空值，216 条格式键在 `en_us.json` 中均存在。
- 未覆盖：`PossibleKeybind` 的按键描述（如 “Toggle Killaura”）与 `getPrimaryAction()` 按钮文案仍为英文；Hypixel 地图、模型 ID 等专有名词按原样保留。

---

## v1.5.2 简体中文本地化（2026-11-15，源码已改，未发布）

范围：`fabric/`（Minecraft 1.20.1 Fabric，v1.5.2 基线）。

- `assets/wurst/translations/zh_cn.json` 由 2636 键扩到 4567 键：补 179 条设置名、34 条 HUD 模块说明、17 条 Other Feature 名称与 16 条说明，并把 1632 条设置说明译成中文。设置说明的英文原文本身就是翻译键（`WText.translated`），因此只加键、不改调用点。
- v1.5.2 新增界面的残留英文改为翻译键：灵动岛（模块开关通知、搭路/挖掘状态、连接信息）、通知预览卡片、TargetInfo 胜负标签，以及 HUD 兼容层的「单人游戏 / 未连接 / 游戏模式 / 原版资源包」文案；`en_us.json` 同步补 27 条英文，其余语言仍回退到英文。
- `OtherFeature` 新增 `getRenderName()` 翻译（键 `other_feature.name.*`）；`getName()` 仍是原始名，命令、按键绑定与配置文件格式不受影响。
- 顺带补齐 10 条此前残留英文的设置名（Invert Y、Compact、Keyboard、Outline、Gap、Thickness、Image、Trail length、Design、Keybind），并将 6 个 Hack 中 28 条用 `WText.literal` 硬编码的设置说明改为 `WText.translated` 翻译键后补上中文。
- 验证：`fabric` 工程 `compileJava` 与 `build` 通过；覆盖检查确认 433 个 Hack、35 个 HUD 模块、17 个 Other Feature 的名称与说明均有中文键，新增 27 条 UI 键在中英两种语言中都存在，28 条改造后的说明键全部能在 `zh_cn.json` 中解析到。
- 未覆盖：聊天提示与命令提示已在下一节单独处理。

---

## v1.5.1 全版本渲染修复（2026-11-13，源码已修，未发布）

- 将初始化、字体上传和 GL 状态修复适配到其余 63 个工程，覆盖全部 66 个 v1.5.1 工程；同时恢复 Minecraft 纹理绑定缓存，防止 GPU 分配后的缓存失配。
- 33 个延迟 GUI 工程按队列消费时机回收独立快照；修正三端 1.20.3/1.20.4 的列表背景注入目标，移除 Fabric 1.20.1 遗留的仅显示 FPS HUD 测试过滤。
- 七个源码分支的真实 OpenGL 回归共 408 项断言通过，五个代表工程完整构建及归档检查通过；66 工程静态渲染闸门与 12 项闸门测试已接入 CI。尚未完成游戏画面验收，详见[跨版本修复记录](report/RENDER-v1.5.1-matrix-2026-11-13.md)。

---

## 26.3 界面渲染修复（2026-11-12，源码已修，未发布）

- 修复 Forge / NeoForge / Fabric 26.3 的 NanoVG 初始化状态泄漏，补齐 sampler、正反面模板及像素上传状态恢复，清理离屏模板缓冲时使用完整写掩码。
- GUI 延迟合成按提交保留独立纹理，在真实队列清空后回收，避免超过八次绘制后覆盖先前按钮、文字等内容；关闭渲染器时释放快照资源。
- 真实 OpenGL/NanoVG 回归 62 项断言通过；NeoForge 26.3 完整构建及产物检查通过。Fabric、Forge 构建仍受本机缓存/ZIP 环境问题阻塞；尚未进行游戏画面验收，详见[修复与验证记录](report/RENDER-v1.5.1-26.3-2026-11-12.md)。

---

## 崩溃与安全热修（2026-11-12，源码已修，未发布）

- 修复 26.x NanoVG 错误释放、四个 NeoForge 工程的 Baritone 悬空入口、嵌套 Baritone 配置遮蔽、启动期翻译与 OpenOpal 空引用。
- Reach 与其它模组修改同一常量时允许降级；此时超过 6 格的实体搜索可能不可用，游戏不会因该冲突被强制终止。
- Fabric 内嵌 MixinExtras 0.5.5，最低 Fabric Loader 版本提升到 0.17.0。
- 修复 Fullbright / X-Ray 保存设置时的越界亮度，保存结束后恢复渲染效果；限制旧密钥反序列化，防止资源包替换 FFmpeg / Skiko 原生库，并校验、隔离 NanoVG 原生缓存。
- 补齐 NeoForge 1.20.1 的 10 个使用中接口注册，并回填其它版本的 21 个同类遗漏；为 Fabric 1.21.11 新增现代 Sodium 方块、流体 X-Ray 显隐回调。
- 新增覆盖全部工程的回归闸门、发布产物检查及独立行为测试。完整发布包与实机验收仍受构建环境问题阻断，详见[修复与验证记录](report/CRASH-FIXES-2026-11-12.md)。旧 JAR 不会因源码修复而自动更新。

---

## v1.5.1 — OpenOpal KillAura／自动搭路源码移植（2026-11-08，未发布）

- **KillAura**：接入原版输入阶段的点击／按住调度、CPS 与攻击冷却、连击计时、Grim 受伤窗口、武器选择、自动格挡和目标前瞻；目标排序使用生命值加吸收量及距上次攻击的经过时间，FOV 按 OpenOpal 的水平偏航角判断。
- **转向与输入**：移植六种旋转模型、鼠标插值和客户端视角保持；统一转向请求优先级与动作执行时序，清除失效的一次性请求，协调 KillAura、MultiAura 与 Scaffold。
- **自动搭路**：移植候选落点搜索、射线与放置校验、移动预测、同高度搭路、自动／手动跳跃，以及 Telly、Heypixel、Hypixel、SilenceTelly 的对应逻辑；接入 SkipTick 回救、最终发包旋转修正和搭路状态显示。
- **静默切槽与状态清理**：移植 SlotHelper 的 NONE／DEFAULT／FULL 模式，联动快捷栏、手持物品显示与滚轮选槽；切换世界或玩家时清理攻击、放置、转向、槽位及模式状态。
- **源码与许可**：保留来源标注，并在全部目标工程中包含 OpenOpal 的 GPL-3.0 许可；源码对应关系与版本适配详见[移植说明](docs/OPENOPAL_V151_PORT.md)。

### 验证与适配边界

- 移植完成时，66/66 个工程的 `compileJava` 通过，并核对编译输入哈希；23 个 Minecraft 版本共 648 项 Mixin 字节码检查通过。
- 两组 API 版本的生产源码行为测试共 138 项断言通过；11 个核心类在全部工程中的 726 份源码映射对比一致，4508 个输出文件哈希、许可与 Mixin 注册检查通过。
- 好友／机器人过滤和使用物品时攻击分别接入现有好友列表／AntiBot 与 Multitask。独立 Teams、DelayVelocity／NoXZVelocity、Stuck 模块未移植，其额外联动不可用；Halo 与搭路状态显示使用宿主渲染接口，外观并非逐像素一致。
- 上游已注释的 Watchdog／WatchdogTellyBlink 处理逻辑保持停用；Hypixel Boost、Aura Keep Sprint FOV 等上游无有效逻辑的设置不补造行为。
- 尚未进行游戏内战斗、搭路、Mixin 启动及服务器兼容性实测；本次未安装或发布产物。

---

## v1.5.1 — 四客户端移植第 1–18 批与源码基线（2026-11-08）

对照 FO、InvincibleMachineGun、LeavesHack、Meteor 源码，将重复功能合并到已有模块，补齐设置和行为，并新增独有模块。以下记录本地源码与验证结果。

### 源码基线

| 工程范围 | 当前版本 | 第 1–18 批状态 |
| --- | --- | --- |
| 本仓库 `fabric/`、`neoforge/` 及三个 `versions/` 版本树，共 66 个工程 | v1.5.1 | 已合入；第 18 批完成时的全矩阵 `compileJava` 为 66/66 通过 |
| 独立目录 `VAP-v1.5.0-1.20.1` 的根工程，Forge 1.20.1 | v1.5.1 | 已合入 86 个源码／资源文件，并生成生产 JAR；其 8 个子工程未修改 |

### 批次记录

| 批次 | 模块及改动 |
| --- | --- |
| 1 | SoundModifier 增加声音 ID 屏蔽；新增 Slippy 方块摩擦配置、EndermanLook 末影人视线控制、BuildHeight 高度上限交互调整。 |
| 2 | FO AutoLog 合并到 AutoLeave，补齐多条件退出与单次重连；InvincibleMachineGun AutoMessage 合并到 AutoText，支持文件消息序列；Leaves AutoTree 合并到 TreeBot，补齐树苗位置、骨粉和操作频率控制。 |
| 3 | AntiHunger 补齐冲刺、落地与动作条件；AutoRespawn 接入最近死亡点；BreakDelay 合并到 FastBreak；NoStatusEffects 合并到 NoLevitation，支持状态效果 ID 筛选。 |
| 4 | 新增 TridentBoost 激流倍率与离水激流、Multitask 同时使用物品和挖掘、NoInteract 按目标及操作条件过滤交互。 |
| 5 | AutoPlaceBlock 增加方向与半砖选项；AutoTorch 增加黑暗区域预览和放置延迟；DeathRender 合并到 KillFX，SprintPlus 合并到 AutoSprint；补齐 TreeBot 渲染透明度。 |
| 6 | AirJump 增加高度维持选项，AutoJump 增加触发条件与低跳；Spider 支持攀爬速度，ReverseStep 补齐载具与床面判断，HighJump 增加跳跃倍率模式。 |
| 7 | MessageAura 增加进入视距触发消息与好友筛选；EXPThrower 支持副手、朝下使用及槽位恢复；PotionSaver 增加效果选择并限定本地玩家作用范围。 |
| 8 | AntiVoid 补齐跳跃／飞行模式和状态清理；FastClimb 增加计时模式；FastUse 补齐物品及手部筛选；AutoShearer 修复交互前旋转与异常时槽位恢复。 |
| 9 | AutoMount 补齐实体类型和鞍判断；BetterBeacons 增加主／副效果选择；新增 NoMiningTrace 穿过实体选取方块、GhostHand 穿过遮挡与方块实体交互。 |
| 10 | AutoWalk 增加四方向、Baritone 模式、输入／高度变化停止及未加载区块保护；AutoNametag 增加类型、优先级和独立冷却；Rotation 即时应用并同步可见头部／身体角度。 |
| 11 | AutoBreed 增加实体与年龄筛选、单次／重复喂养及间隔；新增 Collisions 方块碰撞选择、岩浆块避让、客户端边界和未加载区块移动保护。 |
| 12 | FO AutoTrash 合并到已有模块，增加黑白名单、精确附魔等级、单件／整组丢弃与快捷栏保护，保留保留名单优先级；空规则、无效规则和容器归属异常时停止丢弃。 |
| 13 | NoRotate 在包处理结束时立即逐字段恢复选定旋转，保留独立历史／头部／身体角度；PortalGodMode 仅保留最近确认并限定原连接释放，避免跨连接重放。 |
| 14 | CustomFov 的世界与手持物品 FOV 接入实际渲染回调，补齐缺失的版本适配；ChatHider 关闭后的迟到回调不再覆盖已恢复的聊天透明度。 |
| 15 | Parkour 增加可选的仅方块碰撞支撑检测；Sneak 飞行暂停同时适用于 Legit／Packet，修复模式切换、玩家更换与关闭时的按键及包状态清理。 |
| 16 | TimeChanger 改为读取时覆盖显示时间，保留服务器计时与停走更新；关闭后立即恢复最新时间，保留 NoWeather 优先级并适配 26.x 时钟。 |
| 17 | Reach 的实体／方块距离独立设置，增加附加距离模式；修复缺失、失效和重复的回调，以及旧版实体搜索固定截断到 6 格的问题。 |
| 18 | WeatherChanger 改为读取时覆盖天气，保留服务器停雨等真实状态；雷暴强度只乘一次雨强，关闭后恢复最新天气，保留 NoWeather 优先级。 |

### 验证与限制

- 第 18 批完成时核对 405 个合并输出文件哈希、66 个工程的注册及对应完整输入的编译记录；第 17／18 批各 23 个游戏版本的回调目标检查和生产源码行为测试通过，见[第 17／18 批报告](report/PORT-v1.5.1-four-clients-batch17-18.md)。
- 独立 Forge 1.20.1 根工程的 `compileJava`、`jarJar`、`reobfJarJar` 与第 13–18 批生产源码行为测试通过；生产 JAR 为 48,664,131 字节。完整测试集在 `compileTestJava` 被 9 个已有 HUD／ScreenRegistry 测试文件阻塞，见[生产包报告](report/PORT-v1.5.1-Forge-1.20.1-external-root.md)。
- 以上移植尚未完成游戏内 Mixin 应用、实际画面与多人服务器验证；编译和行为测试通过不等于全部运行场景已验证。
- 四客户端清单共 427 个具体模块类，整体移植尚未完成。同名模块不代表全部行为一致；NoGhostBlocks 本地预测控制、PacketCanceller 双向选择与优先级、FO 图形化附魔规则编辑及模块联动等仍待实现。

---

## 未发布 — 第三轮审计 6 类问题与 1.21+ 图标修复（2026-11-07）

第三轮 6 类问题（明细见
[report/AUDIT-v1.5.1-upstream-2026-11-07-round3.md](report/AUDIT-v1.5.1-upstream-2026-11-07-round3.md)）：

- **S10**：主密钥与共享旧密钥不同时，迁移单个账号文件不再删除旧密钥，其他安装目录仍可解密。
- **S11**：命令式凭据读取区分“不存在”和“读取失败”，失败抛 `IOException`，不再误建新主密钥。
- **S12**：`Surround` 的 Rotate 关闭时真正不请求静默旋转；`BlockPlacer` 新增带 `rotate` 标志的重载。
- **U6**：`AutoArmor` 延迟换装复验装备槽快照与 `Keep elytra`，旧计划不会脱下后来穿上的鞘翅。
- **U7**：1.20.1 两个构建的 TargetShader post JSON 改用 `wurst:target_effect` 命名空间。
- **U8**：HUD 编辑器左键拖动不再强制开启吸附，保留用户右键设定的开关。

另修复 1.21+ 的 NanoVG 图标不渲染：`HudTextures` 在 50 个构建中是返回 `-1` 的桩，主菜单按钮、
ClickGUI 与 HUD 图片全部被跳过；改为从资源包读取 PNG、`NativeImage` 解码并上传为 GL 纹理缓存。
已在 1.20.2、1.21.6、26.3 实机启动确认主菜单按钮图标正常显示。

验证：`fabric` 4 个版本树、`neoforge`、Forge 2 个版本树与根工程编译通过；465 个改动 Java 文件
语法解析 0 错误；S10 迁移与 S11 退出码的独立测试通过。

---

## 未发布 — 第二轮审计 8 类问题修复（2026-11-07）

对照第二轮审计修复 8 类问题（明细见
[report/AUDIT-v1.5.1-upstream-2026-11-07-round2.md](report/AUDIT-v1.5.1-upstream-2026-11-07-round2.md)）：

- **Backtrack**：换世界/换连接时清空队列；排队包记录所属世界与连接，旧包不再通过新连接回放。
- **NoGhostBlocks**：队列按世界过滤，旧世界的方块更新不会写进新世界。
- **注解事件**：私有/包私有 `@WurstSubscribe` 方法可正常注册；重写方法只登记一次，不再重复触发。
- **Addon**：逐个 provider 隔离，损坏的 Addon 只跳过自身，不再中断后续发现。
- **HUD 配置**：`loadState` 逐模块/逐字段容错，坏值回退默认，初始化不再被异常中断。
- **NanoVG GUI**：每帧按 pass 使用独立纹理槽，背景/侧栏不再被后续 pass 覆盖；延迟物品按入队时的平移绘制。

验证：`fabric` 4 个版本树、`neoforge`、Forge 3 个版本树与根工程编译通过；500 个改动文件语法解析 0 错误；
私有处理器与重写去重的独立测试通过。另修正第一轮误同步到根工程的 HUD 名称解析（根工程 `HudElement`
不是 `Feature`），根工程恢复编译。

---

## 未发布 — 修复 1.21.6–26.3 移动输入读取（2026-11-07）

- **根因**：`MovementPlanner.getMoveVector()` 在 1.21.6+ 反射找错了对象。真实输入在
  `ClientInput.keyPresses`（`net.minecraft.world.entity.player.Input` 记录），而
  `ClientInput` 自身已提供 `getMoveVector()`；旧实现找 `left()/right()/forward()` 与
  `forwardImpulse`，全部命中不到，恒返回 `(0,0)`。
- **症状**：Freecam（灵魂出窍）水平方向完全无法移动；Flight 普通模式水平速度为 0
  （只能升降/滑行，表现为"飞得奇慢"）。同样读取该向量的 Speed、LongJump、Step、
  FastLadder、PacketFly 也受影响。
- **修复**：优先调用 `ClientInput.getMoveVector()`；退化路径支持 `keyPresses` 记录的
  `forward()/backward()/left()/right()`，以及旧版 `forwardImpulse/leftImpulse` 浮点；
  反射访问加 `trySetAccessible()`。33 个工程同步。
- **验证**：`fabric/versions/1.21.6` 编译通过；33 个文件语法解析 0 错误；独立 Java
  测试覆盖三种输入形态全部通过（vanilla / keyPresses / legacy）。

---

## 未发布 — 第二轮上游审计 6 类问题修复（2026-11-07）

对照 Wurst 上游复核 66 个 v1.5.1 工程，修复 6 类问题（明细见
[report/AUDIT-v1.5.1-upstream-2026-11-07.md](report/AUDIT-v1.5.1-upstream-2026-11-07.md)）：

- **渲染入口**：Fabric 26.1–26.3 恢复 `GuiMixin`（GUI 逐帧事件）；26.2／26.3 恢复
  `LevelRendererMixin`（3D `RenderEvent`）。
- **Freecam 相机**：Fabric 26.x 恢复 `freecam/CameraMixin`、`freecam/LocalPlayerMixin`、
  `freecam/ItemInHandRendererMixin` 注册；Forge 1.21.5 补上缺失的 `freecam/CameraMixin`
  （按 1.21.4 移植，已核 `Camera.setup` 与 `detached` 字段）。
- **NoSlowdown**：1.21.6–1.21.11 与 26.x 的三处物品使用包装器改为
  `isEnabled() && shouldBypassUsingItem()`，"Using items"／"Blocking" 开关恢复独立生效。
- **HOLD 双键**：第二个 HOLD 键会登记为同一功能的共同持有者，松开任一键不再提前关闭；
  手动开启的功能仍不会被 HOLD 释放误关。
- **HUD 命令**：`CmdUtils.findFeature` 与 `WurstClient.getFeatureByName` 纳入 HUD 模块与
  HUD 设置，`.setmode hudsettings ...` 可用。
- **嵌套设置**：`CmdUtils.findSetting` 递归查找子设置，支持 `父/子` 路径并对同名歧义报错。

验证：`fabric`、`fabric/versions/1.20.2`、`fabric/versions/1.21.6`、`neoforge`、
`versions/1.20.2`、`versions/1.21.5` 均 `compileJava --offline` 通过；235 个改动 Java 文件
语法解析 0 错误；26.x 的注入目标已用 Loom 缓存的 26.x jar 逐方法核对。

---

## 未发布 — 对照 Wurst 上游的 13 项潜藏 bug 修复（2026-11-07）

对照 Wurst7 v7.50.3-MC1.20.1 审计 v1.5.1，修复 13 项并同步到全部工程（明细与证据见
[report/AUDIT-v1.5.1-vs-upstream.md](report/AUDIT-v1.5.1-vs-upstream.md)）：

- **ST Block Fly**：恢复 16 tick 重武装并记录手部奇偶，禁用时换回，修复服务端主/副手永久错位。
- **Killaura / MultiAura**：接回 `FakePlayerEntity` 与 `AntiBot` 过滤；攻击冷却窗口改为跨 tick 保存，
  SWITCH 排序恢复"最近未命中优先"；FOV 改为 3D 半角语义（30–360）。
- **Scaffold**：右键只在主手持可放置方块时拦截（不再吞掉进食/药水/弓/珍珠）；
  Hypixel 旋转接回 `getVanillaRotation()` 灵敏度栅格；落点预测传入玩家实时 WASD 输入。
- **HUD**：`HudItemIcons` 加 512 条 LRU、淘汰时 `nvgDeleteImage`，捕获异常时保证 `unbindWrite()`。
- **账号加密**：旧格式解密只接受形似 JSON 的明文，读取失败不再立即写回空列表。
- **其它**：`switchAdvantage` 改为 0–1 百分比；`hideFakeSwings` 默认关闭；Blink 空引用不再丢队列；
  Flight 旁观者切换保留飞行状态。
- **验证**：`fabric`、`fabric/versions/1.20.2`、`neoforge`、`versions/1.20.2` 均
  `compileJava --offline` 通过；997 个改动文件语法解析 0 错误。

---

## v1.5.1 — InvWalk accessor Mixin 警告（2026-10-27）

- `CreativeModeInventoryScreenAccessor.getSelectedTab()` 改为 `static`（目标
  `selectedTab` 是静态字段），消除启动时 “should be static as its target is” 警告；
  `InvWalkHack` 的调用点同步改为静态访问，并去掉不再需要的屏幕实例转换。

---

## v1.5.1 — Android / LWJGL 3.3.3 NanoVG 兼容修复（2026-10-27）

> v1.5.1 Release 的资产已于 2026-10-27T12:30Z 再次重传，包含本节、Scaffold 与
> InvWalk accessor 修复（SHA256 `8f3ded5c…a0a8e1`）；Release 记录的提交仍为
> `4686900`，tag 未移动。

- **修复 Android 启动器标题界面崩溃**：1.20.1 Fabric 内嵌的 `lwjgl-nanovg` 从 3.3.2
  升级到 **3.3.3**。LWJGL 3.3.3 的 `Struct` 移除了 `wrap(...)`，3.3.2 的
  `NVGPaint.create()` 在 3.3.3 核心上抛 `NoSuchMethodError`，导致 `NVGPaint` 字段初始化
  时直接崩溃（Zalith/Pojav 的 LWJGL 3.3.3-snapshot）。
- 已验证 **nanovg 3.3.3 兼容 LWJGL core 3.3.1 / 3.3.2 / 3.3.3**；
  `lwjgl-nanovg` 关闭 Gradle 传递依赖，避免 dev 运行时把 core 升到 3.3.3 而
  GLFW 仍是 3.3.2（`GLFWVidMode.wrap` 缺失）。
- `NVGPaint` 改为在 `create()` 的 try 块内创建：版本不匹配时只禁用 HUD，不再在类初始化
  阶段崩游戏。
- Android 上不再解压桌面 glibc 原生库，改用启动器自带的 bionic 库；缺失时 HUD 自动禁用。
- **修复 Windows 颜色缓存清理崩溃**：`NVGColor.create()` 返回的是 GC 管理的 direct
  ByteBuffer，`Struct.free()` 会把该地址交给 jemalloc；颜色缓存达到 1024 时触发清理，
  JVM 在 `jemalloc.dll` 中访问冲突（`EXCEPTION_ACCESS_VIOLATION`）。现在只清空缓存，
  由 GC 回收。
- **验证**：Windows dev 客户端进入世界，日志出现 `NanoVG native extracted ... 3.3.3` 与
  `NanoVG GL3 context created`，无 NoSuchMethodError；`NVGColor.create()` 不再手动 free。

---

## v1.5.1 — Scaffold 搭高与疾跑修复（2026-10-26）

> v1.5.1 Release 的资产已于 2026-10-27T12:30Z 再次重传，当前构建同时包含本节与
> NanoVG 修复（SHA256 `8f3ded5c…a0a8e1`）；Release 记录的提交仍为 `4686900`，tag 未移动。

- **关闭 Auto jump 时手动跳跃也能搭高**：站立按住空格，目标层跟随跳跃高度，
  脚下连续放块（真实客户端实测连续搭高 5 格）。
- **自动跳跃下不再“叠空中楼梯”**：`Auto jump` + 前进时不再把目标层抬到跳跃高度，
  修复边搭桥边在空中爬升的鬼畜行为；站立手动跳仍可正常搭高。
- **双击 W 疾跑**：原版要求第二次按键时在地面，而 Scaffold 的自动/手动连跳让人物
  长期离地，导致双击起跑失败。现在 Scaffold 按原版 Ctrl 疾跑路径的条件直接起跑
  （前进 ≥0.8、非潜行、非使用物品、非骑乘/鞘翅、食物与失明检查）。
- **验证**：Fabric 1.20.1 dev 客户端真实世界，真实输入 + 方块列探测；搭高 5 格，
  自动跳跃前进 2 秒高度稳定在 150–151.25，双击与 Ctrl 疾跑状态均为 true。

---

## 未发布 — Soar v8.0 剩余模块与界面修正（2026-10-26）

- 新增 **BedwarsStatsOverlay**（起床战争统计）：显示标签列表玩家的等级、WLR、FKDR、BBLR；
  fabric 端无 v8 的私有 websocket 后端，改用可选的 Hypixel 公开 API Key，未填 Key 时显示 `-`。
- **BossHealth** 增加“原版位置”开关：关闭后由 HUD 接管并隐藏原版 Boss 条；默认保持原版位置。
- 容器界面（背包、箱子等）恢复原版背景，不再套用主界面渐变。
- **MusicWaveform** 暂未移植：它直接读取 v8 自带音乐播放器的 FFT 频谱，
  而当前 fabric v1.5.1 工程没有该音乐后端；如需一并移植需单独引入整套音乐播放器。
- ProtocolVersion 已有 `ViaVersionMod` 覆盖；CustomHand 已有 `CustomHeldItems`（X/Y/Z + 缩放）覆盖。

---

## 未发布 — Soar v8.0 新增 6 个模块（2026-10-25）

---

## 未发布 — Soar 剩余 30 个模块（2026-10-25）

---

## 未发布 — 许可证切换为 AGPL-3.0（2026-10-24）

- 整体作品改为 **AGPL-3.0-or-later**：`LICENSE.txt` 替换为 AGPL-3.0 全文，原 GPL-3.0 全文保留为
  `LICENSE-GPL-3.0.txt`。
- 原因：后续引入 LeavesHack（AGPL-3.0）的模块；按 GPL/AGPL 的单向兼容规则，GPL-3.0 代码可以并入
  以 AGPL-3.0 发布的整体作品。
- `README.md`、`README.en.md`、`THIRD-PARTY-NOTICES.md` 已同步；v1.5.0 及更早的已发布版本仍按
  GPL-3.0 授权，不可撤回。

---

## 未发布 — B 站反馈修复（2026-10-22）

针对视频 `BV1WLha63E8L` 226 条评论中可核查的 bug 反馈，67 个工程同步修复：

- **KillAura 默认目标选择**：`Priority` 由 `HEALTH` 改为 `ANGLE`，避免转头打血量最低的怪、
  让玩家瞄准的目标只吃到横扫 1 点伤害；攻击/挥手顺序改为与原版一致的 attack → swing；
  补上 `Hit select`（延迟感知的无敌帧窗口）。根工程在 2026-09-23 已修，本次补齐其余 66 个工程。
- **MultiAura 伤害衰减**：同一 tick 攻击多个目标时，第二刀起攻击蓄力已被清零（只剩 20% 伤害）。
  全部 67 个工程改为**轮转攻击**：一次 click 只打一个目标，按列表依次轮换，每刀吃满蓄力；
  同时补上 `Hit select`（延迟感知的无敌帧窗口）与 `CombatActionPolicy.findNextEligibleIndex`
  纯函数实现。
- **自定义主界面开关**：`ClickGuiHack` 新增 `Custom title screen`（默认开）。关闭后
  `TitleScreenMixin` 不再 `clearWidgets()`，恢复原版与第三方 mod（如 ModMenu）的标题界面按钮。
- **26.x 退出看门狗崩溃**：Baritone 的静态 `ThreadPoolExecutor` 是非 daemon，主线程退出后
  JVM 不结束，Minecraft 26.x 在 post-main 15 秒后生成
  `Client shutdown from post-main` 崩溃报告。新增 daemon 监视线程，在 `Minecraft.isRunning()`
  变为 false 时反射调用 `baritone.Baritone.threadPool.shutdownNow()`；保留 shutdown hook 兜底。
  实测 Fabric 26.3：关闭窗口后 8 秒内正常退出，**不再生成新的崩溃报告**。
- **Navigator 设置行叠字**：设置组件被拉伸到面板宽度后，长名称与右侧取值重叠
  （1.21.11 Fabric 截图复现：`Swing hand` 与 `Client-side` 叠在一起）。`ComboBoxComponent`
  现在按可用宽度截断名称；实机截图确认不再重叠。
- **Navigator 左侧异位黑色卡片**：根因不是窗口残留，而是 1.21.6–1.21.11 的延迟 GUI
  渲染状态（`QuadMeshRenderState` / `PolygonRenderState`）保存了**可变的 pose 引用**，
  在 `popMatrix()` 之后才构建顶点，导致所有圆角矩形按局部坐标画到 (0,0)；同时
  `scissorArea()` 返回 `null`，滚动区域外的控件也不会被裁剪。现改为提交时**快照矩阵**
  并由 `GuiGraphicsExtractor` 跟踪当前 scissor。Fabric 1.21.11 实机截图确认左侧卡片消失，
  设置控件回到面板内。覆盖 1.21.6–1.21.11 × 三加载器（18 个工程）。
- **Navigator 残留窗口**：`NavigatorScreen` 不再调用 `ClickGui.render()`（会顺带渲染残留的
  ClickGUI 窗口），改为只渲染 popup 与 tooltip。
- **Freecam 破坏创造飞行**：Freecam 每 tick 把 `abilities.flying` 置 false 后未恢复。
  现在启用时记录 `previousFlying`，关闭时恢复。

验证：
- 编译：根工程、Fabric/NeoForge 1.20.1、Forge/NeoForge/Fabric 1.20.2、Forge 1.21.5、
  Fabric 1.21.11、Fabric 26.1.2、Forge/NeoForge/Fabric 26.3 全部 `compileJava` 通过。
- 单测：根工程 `test` BUILD SUCCESSFUL（JDK 21）。
- 实机：Fabric 1.21.11 进世界、打开 Navigator、右键 Protect、截图确认设置行不重叠；
  Fabric 26.3 进世界后关闭窗口，确认无新增 shutdown watchdog 崩溃报告。

---

## 未发布 — 测试控制口与全 hack 压测（2026-10-22）

### 新增：本地测试控制口

67 个工程新增 `net.wurstclient.util.TestControlServer`，默认关闭；只有创建
`gameDir/wurst/test-control.properties`（或传 `-Dwurst.test.port`）才启动。
仅绑定 `127.0.0.1`，所有请求需带 token。提供：

- `GET /ping`
- `GET /hacks`、`POST /hacks/enable|disable?name=...`
- `POST /hacks/enableAll|disableAll`
- `GET /logs?lines=N&errorsOnly=1`

详见 [docs/TEST-CONTROL.md](docs/TEST-CONTROL.md)。

### 全 hack 压测发现并修复

在 Fabric 1.21.11 的测试世界启用 184 个 hack，连续运行后发现并修复：

- **PacketLogger**：在 Netty 网络线程直接写聊天栏，触发字体纹理上传
  （`Rendersystem called from wrong thread`）导致断线。改为 `MC.execute(...)` 回主线程。
- **PlayerHalo / Breadcrumbs**：1.21.6+ 的 line layer 顶点格式要求
  `Normal`/`LineWidth`，这两个渲染器只写 position/color，抛
  `Missing elements in vertex: Normal, LineWidth`。已补 normal 与 line width。
- **ChatTranslator**：翻译请求失败时 `getHTML()` 返回 null，`parseHTML(null)`
  抛 NPE。已在 `translate()` 中做空值保护。

验证：`enableAll` 报告 184 个启用、9 个因前置条件未启用；45 秒后 145 个保持启用，
客户端在线，`errorsOnly` 日志为空，无崩溃/断线。

### 全版本批量压测（2026-10-22）

新增 `scripts/test-all-hacks.ps1`：逐个真实启动 23 个 Fabric 工程，进入 `xray-test`
单人世界后调用 `enableAll`，settle 25–30 秒，再收集启用数与 `errorsOnly` 日志。
17 个版本 PASS，6 个被下方已知的 Fabric API/Loom 配置阶段断线阻塞。

| 结果 | 版本（启用 / 失败） |
| --- | --- |
| PASS | 1.20.1 (143/12)、1.20.2 (147/8)、1.20.3 (138/9)、1.20.4 (137/9)、1.21.4 (148/8)、1.21.5 (146/9)、1.21.6–1.21.10 (146/9)、1.21.11 (145/9)、26.1–26.1.2 (147/9)、26.2 (144/11)、26.3 (143/11) |
| 阻塞 | 1.20.5、1.20.6、1.21、1.21.1、1.21.2、1.21.3：`configuration/serverbound/minecraft:custom_payload ... found 14 bytes extra`（见 RELEASE.md「已知阻塞」；1.21.3 改用 `-Online` 后构建不再失败） |

本轮压测暴露并修复的运行时问题：

- **Baritone 客户端线程自死锁**：Baritone 1.10.8–1.16.0（MC 1.20.5–1.21.10）的
  `BlockOptionalMeta$ServerLevelStub` 在静态初始化时用 `Minecraft` 作为 executor 调度
  registry 加载，随后在客户端线程 `join()` 该 future。首次 `mine()` 发生在客户端线程时
  永久卡死（1.21.4/1.21.5 的 `enableAll` 500 即此）。`BaritoneUtils` 现在改为后台线程
  预初始化（`isMiningReady()`），`startMining` 在就绪前直接返回、由下一次 update 重试。
- **EasyVertexBuffer 关闭共享缓冲**（1.21.5）：`uploadImmediateVertexBuffer` 返回的是
  `VertexFormat` 复用的 immediate buffer，`close()` 把它关掉后下一次上传抛
  `Buffer already closed` 并把客户端带走。`close()` 不再释放该缓冲。
- **创造模式物品栏包竞态**（1.21.5/1.21.9）：`setCreativeStack` 把同一个 `ItemStack`
  放进背包后立即发 `ServerboundSetCreativeModeSlotPacket`，Netty 编码期间该栈可能被
  丢弃/修改，导致服务端 `found 3 bytes extra` 并断线。现在发送 `stack.copy()` 快照。
- **1.21.6–1.21.10 编译失败**：这些版本的 `VertexConsumer` 没有 `setLineWidth`，
  `PlayerHaloRenderer` / `BreadcrumbsHack` 改为只写 `setNormal`（15 个工程）。
- **1.21.9/1.21.10 `fogless_lines` 顶点着色器**：文件带 UTF-8 BOM，且
  `const float LineWidth` 与 `dynamictransforms.glsl` 的 uniform 重名，1.21.9 起
  管线编译失败。已去 BOM 并改用 `WURST_LINE_WIDTH`（15 个工程）。
- **1.21.4 残留 post shader JSON**：1.21.4 起 `shaders/post/*.json` 被当作
  `ShaderProgramConfig` 解析，旧的 chain JSON 启动即报 `No key fragment/vertex`。
  该版本 LSD 已不加载后期效果，删除 3 个工程里的 5 个无效 JSON。
- **测试控制口**：`onClientThread` 超时 10s → 120s（世界加载期间批量启用会超时），
  并为每个 hack 打印 `[WurstTest] enable <name>` 以便定位卡住的位置。

编译校验：Fabric 1.21.4/1.21.5/1.21.6/1.21.9 实机运行；Forge 1.21.4/1.21.6、
NeoForge 1.21.5/1.21.9、根 NeoForge 1.20.1 `compileJava` 通过。根 Forge 1.20.1
的 `compileJava` 仍卡在既有 ForgeGradle 反编译源码的 `slotClicked` 覆盖错误（与本次改动无关）。

### 6 个“开发环境断线”版本的生产版复测（2026-10-22）

上述 6 个版本在 `gradlew runClient` 下的断线只属于 Loom 开发环境。改用
`.test/versions` 的真实启动器实例复测发布 JAR：

- **1.20.5 / 1.20.6 / 1.21 / 1.21.1 / 1.21.2 / 1.21.3 全部进入世界并保持连接**
  （`run-version-tests.ps1 -QuickPlayWorld xray-test` 均 PASS）。
- **1.21.1 生产版全 hack 压测**：`enableAll` 186 启用、8 个前置条件失败；
  120 秒后客户端仍在线，`lost connection` / `Reported exception` / 崩溃报告均为 0。
- 1.21.3 首次启动曾无日志静默退出一次，重跑 PASS，未复现。

为能构建这些版本的生产 JAR，顺带修掉两处失效的 access-widener 条目：
1.20.5/1.20.6 的 `Entity.maxUpStep` 字段在 1.20.5 起已改为 `maxUpStep()` 方法；
1.21.2/1.21.3 的 `GameRenderer.loadEffect(ResourceLocation)` 已不存在。两处条目在源码中
均无引用，删除后 `validateAccessWidener` 通过。

---

## 未发布 — X-Ray 多版本修复与 Vulkan 支持（2026-10-22）

### 修复

- **NeoForge 26.1–26.3、1.21.6–1.21.11**：区块渲染走 6 参
  `shouldRenderFace(BlockAndTintGetter, BlockPos, BlockState, boolean, Direction, BlockPos)`，
  原 mixin 只覆盖 5 参旧路径，导致 X-Ray 打开后石墙仍然可见（青色像素计数会误报 PASS）。
  已补 6 参 wrap。
- **Fabric/Forge/NeoForge 1.21.2–1.21.5**：补 `Block.shouldRenderFace` 3 参/5 参钩子；
  Fabric 另补 Indigo `BlockRenderInfo` 钩子（1.21.2/1.21.3 为 `shouldDrawFace`，
  1.21.4/1.21.5 为 `shouldDrawSide`）。旧版 Indigo 没有 `XRayHack.shouldDrawSide`，
  改用 `ShouldDrawSideEvent`。
- **`XRayHack.isVisible`**：`oreNamesCache` 为 null 时懒初始化，修复启用 X-Ray 时
  区块构建线程并发调用 `Collections.binarySearch(null)` 的崩溃（Forge 26.1.2/26.2 曾实测崩溃）。
- **Baritone**：
  - 恢复 Forge 26.2 `baritone-forge-1.18.0-26.2.jar` 丢失的
    `MixinConfigs` / `MixinConnector` manifest 属性。
  - 1.21.4–1.21.11 的 Baritone mixin 类版本从 65 降到 61（Mixin 仅接受到 61），
    并从 jar 与 NeoForge 源码资源 `mixins.baritone.json` 移除 `MixinWorldRenderer`，
    避免 `RenderPipelines` → `IRenderer.<clinit>` 强转崩溃。代价是 Baritone 路径可视化关闭，
    寻路/挖掘功能保留。
- **Criticals**：新增 `WURST` / `GRIMAC` PacketProfile，默认包配置改为 `WURST`，
  `Only when ready` 默认为关闭。

### 已验证（真实客户端 + 截图确认石墙消失）

| 加载器 | 版本 |
| --- | --- |
| Fabric | 1.20.2–1.20.4、1.21.4–1.21.11、26.1–26.3 |
| Forge | 1.21.4–1.21.5、1.21.8–1.21.11、26.1–26.3（1.21.6/1.21.7 仅源码覆盖，未启动） |
| NeoForge | 1.21.2–1.21.11、26.1–26.3 |

X-Ray + MobESP 同帧检查：NeoForge 26.3、Fabric 26.2/26.3、Forge 26.2/26.3 的 ESP 红框
均与悬空猪对齐，未发现世界或 2D 叠加层偏移。

### Vulkan（26.2 起实验性，可选）

- 26.2/26.3 视频设置新增 **Graphics API**：`Default`（当前等同 `Prefer OpenGL`）、
  `Prefer Vulkan (Experimental)`、`Prefer OpenGL`。要求 Vulkan 1.2 + dynamic rendering +
  push descriptors；创建失败自动回退 OpenGL。
- 测试命令：`./gradlew runClient -PspWorld=xray-test -PgraphicsBackend=vulkan`
  （build.gradle 会追加 `--graphicsBackend vulkan`）。
- 实测（RTX 5070 / NVIDIA 592.19）：
  - 26.2 Fabric、Forge：Vulkan 下 X-Ray 正常，MobESP 无偏移。
  - 26.3 Fabric、Forge、NeoForge：Vulkan 下 X-Ray 正常，MobESP 无偏移。
  - **NeoForge 26.2**：Vulkan 启动在 GLFW 窗口创建阶段失败：
    `GLFW error 65540: Vulkan: Window surface creation requires the window to have
    the client API set to GLFW_NO_API`。用空 Wurst mixin 配置复现，确认是 NeoForge 26.2
    加载器问题，与本客户端无关；该组合请使用 OpenGL。

### 已知阻塞

- **Fabric 1.20.5 / 1.20.6 / 1.21 / 1.21.1 / 1.21.2 / 1.21.3**：配置阶段断线
  `Packet configuration/serverbound/minecraft:custom_payload ... found 14 bytes extra`。
  首个包是 `minecraft:brand`；与 Fabric Loader 版本、Wurst 连接事件分发、VanillaSpoof
  均无关，属于 Fabric API/Loom 环境问题。1.21.4+ 正常。
- Forge/NeoForge 1.20.x–1.21.1、Forge 1.21.3 本轮未做游戏内启动验证（源码钩子已覆盖）。
- Fabric pre-26 的 Baritone mixin 仍处于禁用状态：不会因 Baritone 崩溃，但 Baritone
  功能本身不可用。

---

## v1.5.0 - 全平台适配与稳定性修复 (2026-08-07)

**NeoForge 1.21.1 启动即崩溃**

- 问题：启动时 `IllegalAccessError: class baritone.api.Settings cannot access class net.minecraft.world.item.Item (in module minecraft)`
- 根因：Baritone 以 jar-in-jar 库形式打包，NeoForge 21.1.x 的 JPMS 模块层将其隔离为独立模块 `baritone.api.forge`，该模块没有对 `minecraft` 模块的 read 边
- 修复：将 Baritone 类直接合并进 `wurstpenguin` 主模块，类归属主模块后即可访问 `minecraft`，不再作为独立库模块加载

**Forge 26.1.2 启动崩溃（JPMS 模块包冲突）**

- 问题：模块解析失败 `Module WurstB.Plus... contains package io.netty.util.concurrent, module io.netty.common exports`
- 根因：旧发布包把整个 netty 传递链（common/buffer/transport 等）扁平合并进主 jar，与游戏自带 netty 4.2.7 模块导出同一包
- 修复：仅合并不与其他模块冲突的 netty-codec-socks / netty-handler-proxy 自身包，依赖版本对齐游戏自带 netty 4.2.7；产物体积由 120MB 降至 32MB

**Forge 26.1.2 依赖声明错误**

- 问题：启动报 `Mod wurstpenguin requires neoforge` 无法加载
- 根因：旧发布包 mods.toml 错误声明依赖 `neoforge [26.1.2,27)`，Forge 64.1.0 环境下不存在该 mod
- 修复：依赖改为 `forge [64.1.0,65)`

**Forge 26.1.2 Baritone 注册失效**

- 问题：baritone-forge-1.18.0.jar 已嵌入但未被加载
- 根因：构建产物丢失 `META-INF/jarjar/metadata.json`（嵌套 jar 的注册清单）
- 修复：恢复 metadata.json，确认 Baritone 嵌套注册完整

**Forge/NeoForge 26.1.2 发布包资源丢失**

- 问题：发布包缺少全部 mod 资源（字体、翻译、shader、图标），Baritone 下界寻路类缺失
- 根因：打包任务沿用了为合并整个 runtimeClasspath 设计的 `exclude 'assets/**'`，把 mod 自身资源一并排除
- 修复：清理打包任务，仅合并 Baritone 类，恢复全部资源与 NetherPathfinder 类

**Fabric 26.1.2 启动崩溃（Mixin 注入失败）**

- 问题：StatusEffectInstanceMixin、ClientPlayerEntityMixin 注入失败导致启动崩溃
- 根因：MC 26.1.2 重命名了 `tickDownDuration()`（→ `mapDuration(Int2IntFunction)`）与 `hasEnoughFoodToStartSprinting()`（→ `canStartSprinting()`），旧注入目标不存在；refmap 声明指向不存在的文件（非混淆环境无需 refmap）
- 修复：更新注入目标，移除 refmap 声明，并将 mixin 清单对齐已验证的 65 项

**Fabric 1.21.1 HUD 渲染顺序错误（仅 Fabric）**

- 问题：打开半透明 ClickGUI 时，HUD 文字（TabGui、HackList 等）渲染在 GUI 下层，透过透明背景可见
- 根因：1.21.1+ 的 HUD 事件改由 `IngameHudMixin` 在 `Gui.renderTabList` HEAD 注入触发，屏幕打开时仍会派发；1.20.1 的 `HudRenderCallback` 在屏幕打开时不触发，故无此问题
- 修复：`renderTabList` 注入点增加 `WurstClient.MC.screen != null` 守卫，屏幕打开时跳过 HUD 渲染，与 1.20.1 行为一致；已同步应用至全部 6 个 1.21.1/26.1.2 工程

**模组图标更新**

- 替换全部 9 个工程的 `assets/wurst/icon.png` 为新版 Logo（等比缩放居中，400×400 透明背景）

**启动早期崩溃防护（全平台）**

- 问题：第三方整合包 / 移动端环境下，WurstClient 初始化前触发 mixin 导致空指针崩溃
- 根因：TextVisitFactoryMixin、LanguageManagerMixin、MinecraftClientMixin、TelemetryManagerMixin 直接访问 `getHax()`/`getTranslator()`/`getOtfs()` 未判空
- 修复：全部增加 null 防护；WurstMixinConfigPlugin 改用类资源存在性检查，避免提前触发类加载（修复 Supplementaries × Embeddium 的 MixinTargetAlreadyLoadedException）

### 构建链

- mixinextras 0.4.1 → 0.5.4，jarJar 版本范围放宽，解决整合包 mixinextras 版本冲突
- Forge 依赖范围 `[47.4.10,48)` → `[47,48)`，兼容 Forge 47.4.0 及更高版本
- 26.1.2 系列内嵌 baritone-forge/neoforge-1.18.0（jar-jar），Baritone 由 `baritone-maven/` 本地仓库提供
- 新增 scripts/build-all.ps1 一键构建 9 个产物；scripts/run-version-tests.ps1 在 PCL 测试环境批量真实启动验证（9/9 通过）

### 文件校验

```
WurstB+ Plus-v1.5.0-Forge-1.20.1.jar: CFC5EF862A0D822E20895AA69D952EB809D253D80CC89B2CB27172A5D2CDB9C0
WurstB+ Plus-v1.5.0-Forge-1.21.1.jar: A78BFEFA7BD7B4220EB7017827742CA477B971450CA08362D4C66C7BD32F00C8
WurstB+ Plus-v1.5.0-Forge-26.1.2.jar: 489DC385B0389AFF604B276829821E354F4FBD1EDBB384EA29708C3B4A27D5A2
WurstB+ Plus-v1.5.0-NeoForge-1.20.1.jar: 0C05A07ED15B5E4B84259D592777A00231A6C8639027E2C0E50C0E91E88ACB4D
WurstB+ Plus-v1.5.0-NeoForge-1.21.1.jar: 87622A2F7BC0692377A58B87D3B1787AC13CC12E7D87D872EB8DBD70E7452343
WurstB+ Plus-v1.5.0-NeoForge-26.1.2.jar: 5B772ED2791B2A1FE3315BF2F032BFFCC5F48B36F6D1EF306F10A85744C70BB3
WurstB+ Plus-1.5.0-Fabric-1.20.1.jar: 5382A8066844B38CE868590714F2B5F1A547DEAFFFD290934CAA3C32340F3727
WurstB+ Plus-1.5.0-Fabric-1.21.1.jar: 0E3838806FC21AC158CB3EB1550AE143F10D6066F84ADE7F73A69134ECCC110B
WurstB+ Plus-1.5.0-Fabric-26.1.2.jar: 8A2B04A5994B585BC3B8B7204DBA45BA3B5B1A6A5078CD8F10CA6F33E26865E0
```
