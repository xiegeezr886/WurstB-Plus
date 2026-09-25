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

### 界面

- **三套 ClickGUI**（`clickgui2`），设置里可切换：**Epsilon** · **SuperSoft**（MD3 TonalSpot 调色板 +
  磨砂玻璃）· **Vape**。含窗口、设置树、下拉与弹出层、圆角矩形渲染、导航页、图标与字体动画。
- **HUD 编辑系统**（`hud2`）：31 个元素，支持锚点定位、缩放、逐元素设置；编辑器带屏幕中心吸附与
  元素互相吸附。
- **统一强调色** `#007CFF`（`RiseTheme.ACCENT`），音乐、通知、PvP 相关 UI 共用同一语义色。
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
| `music` + `twilight` | 网易云音乐播放器：`music` 负责 API、账号与歌词解析；`twilight` 是它的 Skia 视觉外壳（主题、外壳/首页/列表布局、封面缓存与适配、缓动几何、圆角遮罩）。`.twilight` 打开界面 |
| `render/skia` | Skia / Skiko 矢量渲染后端：GL 后端、区域渲染、字体管理、ESP 字形，以及 Skiko 原生库的加载（`skiko-windows-x64.dll` 作为普通 mod 资源打包，**不走 jarJar**，否则会重定位资源路径导致找不到原生库；运行时解压）。这也是根工程产物偏大的原因 |
| `compose` | 声明式 UI 层：`UiRow` / `UiColumn` / `UiBox` / `UiText` / `UiSpacer` 布局原语、`AnimFloat` 动画、`ComposeHackList`、`ComposeNotifications` |
| `perimeter` | 周界挖掘自动化 `PerimeterDigger`，社区模组 [Perimeter Digger](https://github.com/HackerRouter/Perimeter-Digger) 的原生等价移植：闭区间矩形与**不规则区域边界检测**、液体 `avoid` / `replace` / `seal_boundary` 策略、批次上限与背包满自动暂停、自动拾取与多卸货点、工具/鞘翅耐久替换、自动进食补给睡觉、跨维度熔炉修复、寻路、按服务器/存档分存配置、中英双语。`.perimeter` 负责规划与 start / pause / resume / stop / status / clear，`.perimeterdig` 负责规划、检测、执行 |
| `seed` | 种子相关能力的总入口，详见下方「种子功能」。`.seed get / set / clear / list / structures / mine / structesp`、`observe *`、`search / crack` |

### 种子功能

| 项 | 说明 |
| --- | --- |
| **种子矿透** `SeedOreESP` | 按服务器/存档分存种子；纯 Java 复刻原版矿物生成数学（Xoroshiro + 逐条 `placed_feature` 规则表）预测矿物坐标并在客户端渲染 ESP，可选交给官方 Baritone 挖掘。**零新增依赖**：不内置 Meteor、不引入 Cubiomes / seedfinding |
| **结构定位** `SeedStructureESP` | 直接调用原版 public 的 `RandomSpreadStructurePlacement#getPotentialStructureChunk`，与原版构造上一致；19 个原版结构中支持 18 个（要塞的 `concentric_rings` 算法不同，跳过） |
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
