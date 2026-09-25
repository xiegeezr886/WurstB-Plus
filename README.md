<div align="center">

简体中文 · [English](README.en.md)

<img src="logo.png" alt="WurstB+ Plus" width="620"/>

[![Release](https://img.shields.io/github/v/release/xiegeezr886/WurstB-Plus?style=flat-square&label=release&color=007CFF)](https://github.com/xiegeezr886/WurstB-Plus/releases)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1~26.2-3C8527?style=flat-square)](#版本支持矩阵)
[![Loaders](https://img.shields.io/badge/Loaders-Forge%20%7C%20NeoForge%20%7C%20Fabric-6E6E6E?style=flat-square)](#版本支持矩阵)
[![Java](https://img.shields.io/badge/Java-17%20%7C%2021%20%7C%2025-E76F00?style=flat-square)](#版本支持矩阵)
[![Gradle projects](https://img.shields.io/badge/Gradle_projects-64-4C1D95?style=flat-square)](#仓库结构)
[![preview](https://img.shields.io/badge/preview-v1.6.0-8A2BE2?style=flat-square)](https://github.com/xiegeezr886/WurstB-Plus/releases/tag/v1.6.0)
[![License](https://img.shields.io/badge/license-GPL--3.0-2E7D32?style=flat-square)](LICENSE.txt)

</div>

**WurstB+ Plus** 是一个基于 [Wurst](https://github.com/Wurst-Imperium/Wurst7) 代码结构扩展的
Minecraft 客户端（utility mod）。同一套功能被移植到 **Forge / NeoForge / Fabric** 三种加载器、
**22 个 Minecraft 版本**上，并以 64 个彼此独立的 Gradle 工程维护。

---

## 版本支持矩阵

| Minecraft | Forge | NeoForge | Fabric | Java |
| --- | :---: | :---: | :---: | :---: |
| **1.20.1** | ✓ <sup>v1.6.0</sup> | ✓ | ✓ | 17 |
| 1.20.2 · 1.20.3 · 1.20.4 | ✓ | ✓ | ✓ | 17 |
| 1.20.5 | — | ✓ | ✓ | 17 |
| 1.20.6 | ✓ | ✓ | ✓ | 17 |
| 1.21 · 1.21.1 | ✓ | ✓ | ✓ | 21 |
| 1.21.2 | — | ✓ | ✓ | 21 |
| 1.21.3 → 1.21.11 | ✓ | ✓ | ✓ | 21 |
| 26.1 · 26.1.1 · 26.1.2 · 26.2 | ✓ | ✓ | ✓ | 25 |

<sub>Minecraft **1.20.5** 与 **1.21.2** 没有官方 Forge，故这两个版本只有 NeoForge 与 Fabric。</sub>

**下载命名规则**（版本号不带 `v`）：

```text
WurstB+.Plus-<版本>-<加载器>-<mc>.jar        例：WurstB+.Plus-1.5.0-Forge-1.21.5.jar
```

| Release | 内容 | 说明 |
| --- | --- | --- |
| [**v1.5.0**](https://github.com/xiegeezr886/WurstB-Plus/releases/tag/v1.5.0) | 64 个资产 | 全部版本的主力发布 |
| [**v1.6.0**](https://github.com/xiegeezr886/WurstB-Plus/releases/tag/v1.6.0) | Forge 1.20.1 | 预发布（prerelease），含 v1.6 新子系统 |

> **说明：** 1.20.1 的三个工程并不都是 v1.5。**根目录 Forge 1.20.1 是 v1.6.0**（唯一带 v1.6 新子系统的
> 工程），`fabric/` 与 `neoforge/` 仍是 v1.5.0。v1.5.0 Release 里的
> `WurstB+.Plus-1.5.0-Forge-1.20.1.jar` 是 1.5.0 时期的产物（该工程已升级，无法再逐字节重建那一份），
> 予以保留；想要 v1.6 的 1.20.1 Forge，请取 v1.6.0 Release。

---

## 安装

把 jar 丢进 `.minecraft/mods/` 即可。**Java 版本必须与上表一致**，否则加载器起不来。

<details open>
<summary><b>Forge</b></summary>

1. 装对应版本的 Forge：1.20.1 → `47.4.10`，1.21.1 → `52.1.16`，1.21.11 → `61.2.0`，
   26.1.2 → `64.1.0`，26.2 → `65.1.0`。
2. 把 `WurstB+.Plus-<版本>-Forge-<mc>.jar` 放进 `.minecraft/mods/`。
3. 用对应版本的 Java 启动。

</details>

<details>
<summary><b>NeoForge</b></summary>

1. 装对应版本的 NeoForge：1.20.1 → `47.1.3`，1.21.1 → `21.1.244`，1.21.11 → `21.11.45`，
   26.1.2 → `26.1.2.87`，26.2 → `26.2.0.53-beta`。
2. 把 `WurstB+.Plus-<版本>-NeoForge-<mc>.jar` 放进 `.minecraft/mods/`。

> **注意：** NeoForge 1.21.1 若报 `baritone.api.forge does not read module minecraft`，
> 说明同时存在旧包或单独的 Baritone JAR；删掉它们，只留对应版本的一个 jar。

</details>

<details>
<summary><b>Fabric</b></summary>

1. 装 Fabric Loader：1.20.1 / 1.21.1 → `0.16.14`，1.21.11 / 26.1.2 / 26.2 → `0.19.3`。
2. **同时装上匹配的 [Fabric API](https://modrinth.com/mod/fabric-api)**：
   1.20.1 → `0.92.6`，1.21.1 → `0.115.0`，1.21.11 → `0.141.6`，26.1.2 → `0.155.2`，26.2 → `0.156.0`。
3. 把 `WurstB+.Plus-<版本>-Fabric-<mc>.jar` 放进 `.minecraft/mods/`。

</details>

<details>
<summary><b>关于 Baritone（不要额外再放一个）</b></summary>

部分平台把 Baritone 以 Jar-in-Jar 打包，部分把 Baritone 类合并进主模块（为规避 JPMS 模块读取错误）。
**不要**再额外放单独的 Baritone JAR——会与内置版本冲突。各版本的依赖方式见
[docs/RELEASE.md](docs/RELEASE.md) 的「Baritone 依赖兼容性」一节。

</details>

---

## 功能

规模（根目录 Forge 1.20.1 工程实测）：

| Hack | 命令 | Other Feature | HUD 元素 | Java 源文件 | 单元测试 |
| ---: | ---: | ---: | ---: | ---: | ---: |
| **209** | **57** | **18** | **31** | **1040** | 162 类 / 1040 个 `@Test` |

<sub>Hack 类共 210 个，其中 209 个已在 `HackList` 注册（`RadialMenuHack` 未注册，游戏内不会出现）。</sub>

Hack 按 8 个分类组织：渲染 50 · 移动 45 · 战斗 37 · 方块 31 · 其他 16 · 娱乐 12 · 物品 10 · 聊天 7。

### 新增 Hack

相对上游 Wurst 共新增 57 个 Hack。以下 **42 个在所有工程里都有**：

| Hack | 分类 | 功能 |
| --- | --- | --- |
| `AntiBot` | Combat | 识别反作弊假人（玩家信息 / 游戏模式 / Ping / 落地 / 隐身 / UUID），并从战斗目标里过滤掉 |
| `Anchor` | Movement | 洞内锚定：被击退时把自己拉回原位 |
| `AntiAim` | Fun | 反瞄准：旋转 / 抖动 / 反转 / 俯视 / 倒退，可静默旋转 |
| `AntiVoid` | Movement | 掉进虚空时自救，可限定只在下界洞里生效 |
| `AutoCity` | Combat | 自动挖开对手的城防，可自动换工具、忽略自己的包围 |
| `AutoTrap` | Combat | 自动用方块困住对手，可只放黑曜石 |
| `AutoWeb` | Combat | 自动在对手身上放蜘蛛网，可只放脚部 |
| `BaritoneClearArea` | Blocks | 调用 Baritone 清空一片区域 |
| `BaritoneMine` | Blocks | 调用 Baritone 自动挖指定矿物，可选走回家 / 自动下线 |
| `BaritoneTreeBot` | Blocks | 调用 Baritone 自动砍树，可自动补种 |
| `BaritoneWalk` | Movement | 调用 Baritone 沿朝向自动行走指定距离 |
| `BossStack` | Render | Boss 血条堆叠紧凑渲染 |
| `Breadcrumbs` | Render | 移动轨迹线，可设最大点数与颜色 |
| `Burrow` | Combat | 瞬间把自己埋进方块，可优先黑曜石 |
| `CityESP` | Render | 标出可挖的城防位置，可只标玩家 / 忽略好友 |
| `DankBobbing` | Fun | 增强视角晃动 |
| `DelayRemover` | Combat | 去掉攻击冷却 |
| `ElytraFly` | Movement | 鞘翅飞行：速度、垂直速度、即时起飞、入水停止 |
| `FakeLag` | Movement | 制造假延迟（延迟开关），可设脉冲间隔 |
| `FastUse` | Items | 加速使用物品，可只对投掷物或经验瓶生效 |
| `Hitboxes` | Combat | 放大实体碰撞箱 |
| `HoleESP` | Render | 标出安全洞，可只标基岩 / 黑曜石并分别设色 |
| `HoleFiller` | Combat | 自动填洞 |
| `KeepSprint` | Combat | 攻击后保持疾跑 |
| `LightOverlay` | Render | 低亮度刷怪区域的覆盖层 |
| `LogoutSpots` | Render | 标出玩家下线位置，可显示名字 |
| `NoJumpDelay` | Movement | 去掉跳跃间隔 |
| `NoVelocity` | Movement | 防击退，可只改水平 / 垂直并保留原动量 |
| `Notebot` | Fun | 解析 NBS 曲谱并自动演奏音符盒 |
| `PacketCanceller` | Other | 选择性取消数据包（Boss 事件 / 实体数据 / 移动 / 玩家信息 / 乘客） |
| `PacketFly` | Movement | 基于数据包的飞行，可设水平 / 垂直速度与下落 |
| `PacketLogger` | Other | 网络收发包日志（限流），可分别开关收发与数据 |
| `PlayerHalo` | Render | 在可见玩家头顶绘制跟随主题色的光环 |
| `PopChams` | Render | 图腾触发时的彩色升起动画 |
| `RotationSnap` | Render | 按设定间隔快速吸附视角 |
| `SelfTrap` | Combat | 自动用方块把自己围起来 |
| `SpeedMine` | Blocks | 加速挖掘，可设急迫等级与冷却 |
| `Surround` | Combat | 自动在自己周围放黑曜石，可自动居中 |
| `TargetShader` | Render | 把当前战斗目标送进独立 FBO，统一做描边 / 脉冲 / 渐变 / 烟雾后处理 |
| `TargetStrafe` | Combat | 围绕目标走位，可自动跳跃 |
| `Twerk` | Fun | 快速下蹲舞蹈 |
| `Vomit` | Fun | 快速进食 |

以下 **15 个只在根目录 Forge 1.20.1（v1.6.0）里实现**：

| Hack | 分类 | 功能 |
| --- | --- | --- |
| `AirJump` | Movement | 空中跳跃 / 多段跳，可设模式 |
| `EntityCulling` | Render | 异步遮挡查询，跳过被方块完全遮挡的实体，可分组控制与延迟 |
| `MusicPlayer` | Other | 网易云音乐播放器 |
| `NoMissCooldown` | Combat | 去掉空挥冷却，可取消落空的攻击 |
| `NoRotate` | Movement | 忽略服务端的视角纠正，可分别保留 yaw / pitch |
| `PerimeterDigger` | Blocks | 周界挖掘自动化，详见「新增子系统」 |
| `ProjectilePuncher` | Combat | 击打飞来的投射物 |
| `RadialMenu` | Other | 长按 Tab 的圆盘菜单（未在 `HackList` 注册，游戏内不会出现） |
| `ReverseStep` | Movement | 快速下坠，可设模式、倍率与最大下落距离 |
| `RightClicker` | Combat | 右键连点，可设 CPS 上下限与启动延迟 |
| `SeedOreESP` | Render | 种子矿透，详见「种子功能」 |
| `SeedStructureESP` | Render | 种子结构定位，详见「种子功能」 |
| `SuperKnockback` | Combat | 增强自己造成的击退，可设受伤时间与触发条件 |
| `VehicleBoost` | Movement | 载具加速，可设水平 / 垂直速度 |
| `WTap` | Combat | 自动 W 敲击以重置疾跑，可设概率与按键时序 |

### 界面

- **三套 ClickGUI**（`clickgui2`），可在设置里循环切换，默认 **Epsilon**：
  - **Epsilon** —— 拉式下拉面板
  - **SuperSoft** —— MD3 TonalSpot 调色板 + 磨砂玻璃
  - **Vape** —— VAPE 风格组件层
- **HUD 编辑系统**（`hud2`）：31 个元素，支持锚点定位、缩放、逐元素设置；编辑器带屏幕中心吸附与
  元素互相吸附。
- **统一强调色** `#007CFF`（`VisualTheme.ACCENT`），音乐、通知、PvP 相关 UI 共用该语义色。
- 自定义标题界面（`gui/title`）与共用视觉层（`gui/visual`：`VisualTheme` / `VisualRenderer` /
  屏幕切换动效）。

### 新增子系统

对比上游 [Wurst](https://github.com/Wurst-Imperium/Wurst7)（基准为上游 `master`：157 个 hack /
52 个命令 / 18 个 Other Feature），本仓库新增 **14 个包、57 个 hack、7 个命令**；
Other Feature 数量与上游相同（18 个），没有新增项。其中 8 个包在所有工程里都有，
另外 6 个只在根目录 Forge 1.20.1（v1.6.0）里实现。

**所有工程都可用** —— 8 个包、42 个 hack、3 个命令：

| 子系统 | 说明 |
| --- | --- |
| `clickgui2` | 三套 ClickGUI 皮肤与组件库：窗口、设置树、下拉与弹出层、圆角矩形渲染、导航页、图标、字体与动画 |
| `hud2` | 31 个 HUD 元素与带吸附的编辑器，支持锚点定位、缩放、逐元素设置 |
| `gui` | 自定义标题界面（`gui/title`）与共用视觉层（`gui/visual`） |
| `addon` | 第三方附加包 API：继承 `WurstAddon` 声明名称/版本/作者并注册自己的 hack 与命令，`AddonManager` 负责发现与加载 |
| `macros` | 命令宏：`.macros add <名称> <按键> <命令…>`，多条命令用 `;` 分隔 |
| `proxy` | SOCKS4 / SOCKS5 代理管理：`.proxy add / set / remove / clear / list` |
| `waypoints` | 路径点管理：`.waypoints add / remove / list`，支持命名与颜色 |
| `discord` | Discord Rich Presence：经 `\\.\pipe\discord-ipc-N` 命名管道与桌面客户端通信 |

**仅根目录 Forge 1.20.1（v1.6.0）** —— 6 个包、15 个 hack、4 个命令：

| 子系统 | 说明 |
| --- | --- |
| `music` + `twilight` | 网易云音乐播放器：`music` 负责 API、账号与歌词解析；`twilight` 是它的 Skia 视觉外壳（主题、外壳/首页/列表布局、封面缓存与适配、缓动几何、圆角遮罩）。歌词为逐字动画，视觉层与 AMLL 对齐（弹簧、遮罩、强调、优化、分行平衡）。`.twilight` 打开界面 |
| `render/skia` | Skia / Skiko 矢量渲染后端：GL 后端、区域渲染、字体管理、ESP 字形，以及 Skiko 原生库的加载（`skiko-windows-x64.dll` 作为普通 mod 资源打包，**不走 jarJar**，否则会重定位资源路径导致找不到原生库；运行时解压）。根工程产物因此约 69 MB |
| `compose` | 声明式 UI 层：`UiRow` / `UiColumn` / `UiBox` / `UiText` / `UiSpacer` 布局原语、`AnimFloat` 动画、`ComposeHackList`、`ComposeNotifications` |
| `perimeter` | 周界挖掘自动化 `PerimeterDigger`，社区模组 [Perimeter Digger](https://github.com/HackerRouter/Perimeter-Digger) 的原生等价移植：闭区间矩形与**不规则区域边界检测**、液体 `avoid` / `replace` / `seal_boundary` 策略、批次上限与背包满自动暂停、自动拾取与多卸货点、工具/鞘翅耐久替换、自动进食补给睡觉、跨维度熔炉修复、寻路、按服务器/存档分存配置、中英双语。`.perimeter` 负责规划与 start / pause / resume / stop / status / clear，`.perimeterdig` 负责规划、检测、执行 |
| `seed` | 种子相关能力的总入口，详见下方「种子功能」。`.seed get / set / clear / list / structures / mine / structesp`、`observe *`、`search / crack` |

### 种子功能

| 项 | 说明 |
| --- | --- |
| **种子矿透** `SeedOreESP` | 按服务器/存档分存种子；纯 Java 复刻原版矿物生成数学（Xoroshiro + 逐条 `placed_feature` 规则表）预测矿物坐标并在客户端渲染 ESP，可选交给官方 Baritone 自动挖掘。**零新增依赖**：不内置 Meteor、不引入 Cubiomes / seedfinding |
| **结构定位** `SeedStructureESP` | 直接调用原版 public 的 `RandomSpreadStructurePlacement#getPotentialStructureChunk`，与原版构造上一致；19 个原版结构支持其中 18 个（要塞的 `concentric_rings` 算法不同故跳过） |
| **种子反解** `seed.search` / `seed.crack` | 用实际观测到的结构反推候选种子；热循环 LCG 与频率削减与原版**逐位对照**，搜索前有运行时自检。**注意**：不做格基归约，无法在 2⁴⁸ 全域内无范围求解，通常返回多个候选需逐步收敛 |

根工程新增的 15 个 hack：
`AirJump` · `EntityCulling` · `MusicPlayer` · `NoMissCooldown` · `NoRotate` · `PerimeterDigger` ·
`ProjectilePuncher` · `RadialMenuHack`（未注册） · `ReverseStep` · `RightClicker` · `SeedOreESP` ·
`SeedStructureESP` · `SuperKnockback` · `VehicleBoost` · `WTap`

上游有、本仓库没有：4 个 hack（`AntiKnockback` · `AttributeSwap` · `KillauraLegit` · `MaceDmg`）、
1 个命令（`ViewComp`）、3 个包——`clickgui` 与 `navigator` 已由 `clickgui2` 取代；`analytics`
是上游的 Plausible 遥测上报，本仓库没有移植，另有 `NoTelemetry` / `NoChatReports` 两个
Other Feature 用于关闭遥测与聊天上报。

> **重要：** 已知局限与偏差（例如种子矿透不按生物群系过滤、未在真实存档逐格校验）都写在
> [CHANGELOG.md](CHANGELOG.md) 里，没有藏起来。

### ESP 与视觉

名字标签、装备栏、附魔短名、血条等视觉部分参考了 [OpenOpal](https://github.com/ZSZ7/OpenOpal)
（GPL-3.0）的设计，并在本工程的渲染后端（Skia / `GuiGraphics`）上重新实现，同时保留原生绘制兜底路径。
缓动曲线库（28 条）移植时修掉了参考实现里的两处错误，过程记录在
[docs/openaopal-hud-research.md](docs/openaopal-hud-research.md)。

### v1.5 新增功能

**架构升级**

- **LambdaMetafactory 事件分发**：`@WurstSubscribe` 注解方法在运行期生成
  `Consumer<Event>`，直接方法调用，消除反射开销。
- **事件继承分发**：订阅父事件类即可收到其所有子类事件。
- **Levenshtein 模糊搜索**：ClickGUI 与 Navigator 里拼错也能匹配到功能名与搜索标签。
- **智能绑定**：TOGGLE（默认，`killaura`）、HOLD（按住激活，`+killaura`）、
  SMART（智能切换，`~killaura`）。
- **延迟操作队列**：`DeferredActionQueue` 按命名队列跨 tick 执行。
- **分层设置树**：`Setting.withChildren()` 支持任意深度与父子归属，并带循环检查。
- **稳定设置绑定**：浮窗与 Navigator 只在展开层级或可见性变化时重建布局，拖动滑块时组件实例不变。
- **系统凭据保护**：账号密码交给 Windows Credential Manager / macOS Keychain /
  Linux Secret Service 保存，本地再以带随机 nonce 的 AES-GCM 加密，并自动迁移旧格式。
- **共享旋转仲裁**：`RotationQueue` 按后台 / 移动 / 放方块 / 战斗 / 紧急分级仲裁静默旋转；
  `RotationFaker` 只改混入里角度 getter 的返回值，不写玩家真实角度、不额外发旋转包。
- **库存动作队列**：`InventoryActionQueue` 按菜单 ID、状态校验、优先级与所有者调度原子点击链。

**子系统**

- **Macros**：`.macros add / remove / list`，按键触发命令序列，支持 `_delay:N` tick 延迟。
- **Waypoints**：`.waypoints add / remove / list`，3D 十字标记渲染，跨维度持久化。
- **Proxy**：`.proxy add / remove / set / clear / list`，经 Netty 管线给新建服务器连接注入
  SOCKS4 / SOCKS5 处理器，不改 JVM 全局代理属性。
- **Addon**：`WurstAddon` + `AddonManager`，ServiceLoader 发现，拒绝 Hack / Command 名称冲突。
- **Brigadier 命令**：`BrigadierCommand` 基类，注册进客户端 `CommandDispatcher`。
- **Discord RPC**：只显示单人 / 多人 / 主菜单状态、客户端版本与已启用 Hack 数量，不泄露服务器地址。

**HUD 元素**

- `HudManager` 统一负责渲染、对齐、生命周期与布局持久化。
- 文本类：FPS / 坐标（含主世界与下界 1:8 换算）/ Ping / TPS（按时间同步包间隔平滑采样）/
  速度 / 服务器 / 时钟 / 游戏模式 / 内存 / 游戏时长 / 玩家数。
- 面板类：护甲（按实际图标与耐久）、药水效果（按名称排序）、连击数（经攻击入口监听，
  三秒窗口统计）、按键显示（真实绑定键位 + 按压动画 + 一秒 CPS）、背包网格（主背包 9–35 槽
  的 3×9 网格，含数量与耐久叠加）、目标 HUD（头像 + 主题色血条 + 紧凑装备栏）、小地图
  （北方固定，按 16×16 瓦片渐进刷新的纯圆形地形图，含区块网格与实体标记）。
- 通知卡片底部带与严重级别一致的三秒进度条，读满后淡出移除。

**高级功能**

- **点击模式**：`ClickPattern` 提供 Stabilized / Efficient / Spamming / DoubleClick / Drag /
  Butterfly / NormalDistribution 七种技术，供 Killaura、MultiAura 等使用。
- **高级瞄准**：`RotationSmoothing` 提供 Linear / EaseInOut / Factor / Instant 四种平滑方式。

### 已重构机制

| 机制 | 说明 |
| --- | --- |
| **战斗链路** | 攻击目标解析、点击调度、旋转时序与失败复验统一到一套策略对象（`CombatTargetUtils` / `CombatActionPolicy` / `CombatClickScheduler`），Killaura 与 MultiAura 共用 |
| **移动链路** | `MovementPlanner` 统一移动意图判断，供 AutoSprint、AutoArmor 等复用；静默旋转与移动输入解耦，第一人称方向不受影响 |
| **ESP 与视觉** | ESP 与 HUD 的视觉设计参考 OpenOpal（GPL-3.0）并在本工程的 Skia / `GuiGraphics` 后端上重新实现，保留原生绘制兜底路径；移植其 28 条缓动曲线时修掉了参考实现里的两处错误 |
| **性能优化** | 实体遮挡查询异步化（`EntityCulling`）；小地图按瓦片预算渐进刷新，不为绘图强制加载区块；HUD 与 ClickGUI 只在必要时重建布局 |

---

## 从源码构建

每个工程都是**独立的 Gradle 构建**，自带 wrapper，相互之间**没有共享 sourceSet**。

### 根工程：Forge 1.20.1

需要 **JDK 17**：

```powershell
.\gradlew.bat clean jarJar --console=plain
```

产物 `build/libs/WurstB+ Plus-v1.6.0-Forge-1.20.1.jar`。

<details>
<summary>开发客户端与测试</summary>

```powershell
.\gradlew.bat runClient --console=plain     # 启动开发客户端
.\gradlew.bat test --offline --console=plain # 跑单元测试
```

> **说明：** 首次 `runClient` **必须联网**：ForgeGradle 要拉 Minecraft 资源与 `commons-io`
> 等原版库。`compileJava` / `test` 能完全离线跑，但 `runClient` 加 `--offline` 会失败在
> `:minecraftLibraryCopy`（`commons-io:commons-io:2.6` 不在离线缓存里）。
>
> `jarJar` 结束后会自动把产物复制到本地测试实例的 `mods/`
> （`.test/versions/1.20.1-Forge_47.4.22/mods/`，见 `build.gradle` 的 `copyJarToTestMods`），
> 并清掉该目录里旧的 WurstB+ jar，避免 Forge 重复加载。

</details>

### 其他版本工程

生产任务因加载器而异：

| 加载器 | 任务 |
| --- | --- |
| Forge | `jarJar`（1.20.2 – 1.21.1） / `allJar`（1.21.3+） |
| NeoForge | `jar` |
| Fabric | `remapJar`（26.x 为 `jar`） |

```powershell
cd versions\1.21.5
..\..\gradlew.bat clean allJar --console=plain
```

逐工程的工具链（JDK / Gradle / 加载器版本）见 [PROJECT_INDEX.md](PROJECT_INDEX.md)；
批量构建与打包校验脚本见 [docs/RELEASE.md](docs/RELEASE.md)。

---

## 仓库结构

```text
.
├── src/                    根工程 · Forge 1.20.1 · v1.6.0
├── fabric/                 Fabric 1.20.1 · v1.5.0
├── neoforge/               NeoForge 1.20.1 · v1.5.0
├── versions/               Forge 新版本工程        19 个
├── fabric/versions/        Fabric 新版本工程       21 个
├── neoforge/versions/      NeoForge 新版本工程     21 个
├── docs/                   设计、移植与验证文档
├── scripts/                构建、测试与诊断脚本
└── build.gradle            根工程构建配置
```

**19 + 21 + 21 = 61 个版本工程**，再加 3 个 1.20.1 根工程，合计 **64 个独立 Gradle 构建**。
每个工程都有完整的 `gradle-wrapper.jar`，离线环境也能解析 Gradle 发行版。

---

## 文档索引

| 文档 | 内容 |
| --- | --- |
| [CHANGELOG.md](CHANGELOG.md) | 版本变更，含已知局限与偏差 |
| [PROJECT_INDEX.md](PROJECT_INDEX.md) | 逐工程索引：工具链、源码文件数、入口类、产物名 |
| [docs/RELEASE.md](docs/RELEASE.md) | **发布与维护手册**（原 README）：产物矩阵、构建脚本、打包校验、验证状态 |
| [docs/PORTING-NEW-VERSIONS.md](docs/PORTING-NEW-VERSIONS.md) | 新版本工程的移植计划与逐版本状态 |
| [docs/PORTING-1.21.11-26.2.md](docs/PORTING-1.21.11-26.2.md) | 1.21.11 / 26.2 的渲染管线与移植说明 |
| [PORTING_TASK.md](PORTING_TASK.md) | 移植任务与未完成项 |
| [docs/openaopal-hud-research.md](docs/openaopal-hud-research.md) | ESP / HUD 视觉重构的调研与决策记录 |
| [docs/CONFIG-FORMAT.md](docs/CONFIG-FORMAT.md) | 配置文件格式 |
| [docs/COMBAT_ARCHITECTURE.md](docs/COMBAT_ARCHITECTURE.md) | 战斗链路架构 |
| [docs/ANTICHEAT.md](docs/ANTICHEAT.md) | 反作弊相关说明 |

---

## 许可

**源码采用 [GPL-3.0](LICENSE.txt)（继承自 Wurst）**。
仓库根目录的 `LICENSE.txt` 是 Forge MDK 模板带来的 LGPL 2.1 文本，适用于其中所述的
Minecraft Forge / FML 部分。

> **注意：** 只有 **1.21.11 与 26.2 的六个工程**做过**游戏内启动**验证；其余工程的产物通过的是
> 编译与**打包校验**（zip 完好、含加载器元数据与 Mixin 配置、含主类），**没有游戏内启动验证**。
> 逐版本状态见 [docs/RELEASE.md](docs/RELEASE.md) 与
> [docs/PORTING-NEW-VERSIONS.md](docs/PORTING-NEW-VERSIONS.md)。

<div align="center">
<sub>WurstB+ Plus · mod id <code>wurstpenguin</code> · 作者 Penguin</sub>
</div>
