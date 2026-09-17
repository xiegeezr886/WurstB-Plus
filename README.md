<p align="center">
  <img src="logo.png" alt="WurstB+ Plus" width="768"/>
</p>

# WurstB+ Plus

一个基于 [Wurst](https://github.com/Wurst-Imperium/Wurst7) 代码结构扩展的 Minecraft 客户端（utility mod）项目，
支持 **Forge / NeoForge / Fabric** 三种加载器，覆盖 **Minecraft 1.20.1 到 26.2 共 22 个版本**。

> **想直接玩**：去 [Releases](https://github.com/xiegeezr886/WurstB-Plus/releases) 下载对应版本的 jar，
> 看下面的[安装](#安装)一节。

---

## 目录

- [项目概况](#项目概况)
- [下载](#下载)
- [安装](#安装)
- [功能](#功能)
- [从源码构建](#从源码构建)
- [仓库结构](#仓库结构)
- [文档索引](#文档索引)
- [许可与署名](#许可与署名)

---

## 项目概况

| 项 | 值 |
| --- | --- |
| 模组 ID | `wurstpenguin` |
| 模组名称 | WurstB+ Plus |
| 作者 | Penguin |
| 当前版本 | 根工程 **v1.6.0**（Forge 1.20.1）；其余工程 v1.5.0 |
| 加载器 | Forge、NeoForge、Fabric |
| 映射 | Mojang 官方映射（Fabric 侧走 Loom + 官方映射） |
| 许可 | 源码 **GPL-3.0**（继承自 Wurst），详见[许可与署名](#许可与署名) |

**规模**（根目录 Forge 1.20.1 工程）：

| 项 | 数量 |
| --- | ---: |
| Java 源文件（`src/main/java`） | 1040 |
| Hack 类（`hacks/` 下 `extends Hack`） | 211 |
| 命令类（`extends Command`） | 57 |
| HUD 元素类（`hud2/elements` 下 `extends HudElement`） | 31 |
| 单元测试 | 161 个测试类 / 1024 项，0 失败 |

> **v1.6 新子系统目前只实现在根目录 Forge 1.20.1 工程里**，其余 63 个工程是 v1.5.0 形态。
> 下载时请注意版本号。

---

## 下载

全部产物发布在 GitHub Release **[v1.5.0](https://github.com/xiegeezr886/WurstB-Plus/releases/tag/v1.5.0)**
（62 个资产），命名规则为：

```text
WurstB+.Plus-<版本>-<加载器>-<mc>.jar
```

例如 `WurstB+.Plus-v1.5.0-Forge-1.20.1.jar`。
根工程 v1.6.0 的产物名为 `WurstB+ Plus-v1.6.0-Forge-1.20.1.jar`。

### 宣传片

Release 里还带了一支 v1.5.0 的宣传片（**88 秒 / 1080p / 30 fps**）：

| 项 | 值 |
| --- | --- |
| 文件 | [`WurstB+.Plus-v1.5.0-Promo.mp4`](https://github.com/xiegeezr886/WurstB-Plus/releases/download/v1.5.0/WurstB%2B.Plus-v1.5.0-Promo.mp4)（22.2 MB） |
| 时长 / 分辨率 | 88 秒 / 1920×1080 / 30 fps，H.264 + AAC 立体声 |
| 封面 / 片尾 | `WurstB+.Plus-v1.5.0-Promo-Cover.png`、`WurstB+.Plus-v1.5.0-Promo-Outro.png` |
| 生成脚本 | [`scripts/make-promo.py`](scripts/make-promo.py)（需 Pillow + imageio-ffmpeg） |

片子里的数字全部取自本仓库的真实内容，没有编造：8 个场景分别是
标题、统计计数器、Hack 分类、使用流程、自动化功能、**21 个 MC 版本 × 3 加载器的支持矩阵**、
197 个 Hack 名单滚动、下载信息页。数据由脚本从工程里现读（`scripts/collect-promo-facts.py`）。

> 画面是**信息动画**（卡片、计数器、矩阵、名单滚动），不是游戏内录屏——
> 本仓库里没有可用的游戏画面素材，所以没有拿别的视频冒充。

### 版本支持矩阵

| Minecraft | Forge | NeoForge | Fabric | Java |
| --- | :---: | :---: | :---: | --- |
| 1.20.1 | ✅ **v1.6.0** | ✅ | ✅ | 17 |
| 1.20.2 / 1.20.3 / 1.20.4 | ✅ | ✅ | ✅ | 17 |
| 1.20.5 | — | ✅ | ✅ | 17 |
| 1.20.6 | ✅ | ✅ | ✅ | 17 |
| 1.21 / 1.21.1 | ✅ | ✅ | ✅ | 21 |
| 1.21.2 | — | ✅ | ✅ | 21 |
| 1.21.3 – 1.21.11 | ✅ | ✅ | ✅ | 21 |
| 26.1 / 26.1.1 / 26.1.2 / 26.2 | ✅ | ✅ | ✅ | 25 |

Minecraft 1.20.5 与 1.21.2 没有官方 Forge 版本，因此这两个版本只有 NeoForge 与 Fabric。

> ⚠️ **验证状态**：只有 **1.21.11 / 26.2 的六个工程**做过游戏内启动验证；
> 其余工程的产物通过的是编译与**打包校验**（zip 完好、含加载器元数据与 Mixin 配置、含主类），
> **没有游戏内启动验证**。逐版本状态见 [docs/RELEASE.md](docs/RELEASE.md) 与
> [docs/PORTING-NEW-VERSIONS.md](docs/PORTING-NEW-VERSIONS.md)。

---

## 安装

先装好对应版本的加载器，再把 jar 放进 `.minecraft/mods/`。**注意 Java 版本必须匹配**（见上表）。

### Forge

1. 安装对应版本的 Forge（1.20.1 → 47.4.10，1.21.1 → 52.1.16，1.21.11 → 61.2.0，
   26.1.2 → 64.1.0，26.2 → 65.1.0）。
2. 把 `WurstB+.Plus-<版本>-Forge-<mc>.jar` 放进 `.minecraft/mods/`。
3. 用对应 Java 启动。

### NeoForge

1. 安装对应版本的 NeoForge（1.20.1 → 47.1.3，1.21.1 → 21.1.244，1.21.11 → 21.11.45，
   26.1.2 → 26.1.2.87，26.2 → 26.2.0.53-beta）。
2. 把 `WurstB+.Plus-<版本>-NeoForge-<mc>.jar` 放进 `.minecraft/mods/`。

> NeoForge 1.21.1 若启动时报 `baritone.api.forge does not read module minecraft`，
> 说明同时存在旧版本包或单独的 Baritone JAR；删掉它们，只保留对应版本的一个 jar。

### Fabric

1. 安装 Fabric Loader（1.20.1 / 1.21.1 → 0.16.14，1.21.11 / 26.1.2 / 26.2 → 0.19.3）。
2. **同时安装匹配的 [Fabric API](https://modrinth.com/mod/fabric-api)**：
   1.20.1 → 0.92.6，1.21.1 → 0.115.0，1.21.11 → 0.141.6，26.1.2 → 0.155.2，26.2 → 0.156.0。
3. 把 `WurstB+.Plus-<版本>-Fabric-<mc>.jar` 放进 `.minecraft/mods/`。

### 关于 Baritone

部分平台把 Baritone 以 Jar-in-Jar 形式打包，部分把 Baritone 类合并进主模块（为规避 JPMS
模块读取错误）。**不要**再额外放一个单独的 Baritone JAR——会与内置版本冲突。
各版本的依赖方式见 [docs/RELEASE.md](docs/RELEASE.md) 的「Baritone 依赖兼容性」一节。

---

## 功能

### 客户端核心

- **211 个 Hack**、**57 个命令**、**18 个 Other Feature**，分类包括战斗、移动、渲染、世界、
  物品、聊天、Fun 等。
- **三套 ClickGUI**，可在设置里循环切换（默认 Epsilon）：
  - **Epsilon** —— 拉式下拉面板
  - **SuperSoft** —— MD3 TonalSpot 调色板 + 磨砂玻璃
  - **Vape** —— VAPE 风格组件层
- **统一强调色** `#007CFF`（`VisualTheme.ACCENT`），音乐、通知、PvPUtils 等共用该语义 token。
- **HUD 编辑系统**（`hud2`）：31 个 HUD 元素，支持锚点定位、缩放、逐元素设置；
  带吸附的编辑器（屏幕中心吸附 + 元素互相吸附）。
- **Skiko 矢量渲染**：`skiko-windows-x64.dll` 随 mod 资源打包（不走 jarJar，否则会重定位资源
  路径导致定位不到原生库），运行时解压加载。这也是根工程产物约 68 MB 的主要原因。
- **声明式 UI 层**（`compose`）：`UiRow` / `UiColumn` / `UiBox` / `UiText` / `UiSpacer` 等布局原语 + 动画。

### v1.6 新增（**仅根目录 Forge 1.20.1**）

| 子系统 | 说明 |
| --- | --- |
| **网易云音乐播放器** | 网易云 API、账号登录、主页 / 搜索 / 我喜欢 / 歌单 / 播放详情页，逐字歌词动画与 AMLL 一比一视觉层（弹簧、遮罩、强调、优化、分行平衡） |
| **周界挖掘** `PerimeterDigger` | 社区模组 [Perimeter Digger](https://github.com/HackerRouter/Perimeter-Digger) 的原生等价移植：闭区间矩形与**不规则区域边界检测**、液体 `avoid` / `replace` / `seal_boundary` 策略、批次上限与背包满自动暂停、自动拾取与多卸货点、工具/鞘翅耐久替换、自动进食补给睡觉、跨维度熔炉修复、寻路、按服务器/存档分存配置、中英双语 |
| **种子矿透** `SeedOreESP` | 按服务器/存档分存种子；纯 Java 复刻原版矿物生成数学（Xoroshiro + 逐条 `placed_feature` 规则表）预测矿物坐标并在客户端渲染 ESP，可选交给官方 Baritone 自动挖掘。**零新增依赖**：不内置 Meteor、不引入 Cubiomes / seedfinding |
| **结构定位** `SeedStructureESP` | 直接调用原版 public 的 `RandomSpreadStructurePlacement#getPotentialStructureChunk`，与原版构造上一致；19 个原版结构支持 18 个（要塞的 `concentric_rings` 算法不同故跳过） |
| **种子反解** `seed.search` / `seed.crack` | 用实际观测到的结构反推候选种子；热循环 LCG 与频率削减与原版**逐位对照**，搜索前有运行时自检。**注意**：不做格基归约，无法在 2^48 全域内无范围求解，通常返回多个候选需逐步收敛 |

新增 Hack（14 个）：`AirJump`、`EntityCulling`、`MusicPlayer`、`NoMissCooldown`、`NoRotate`、
`PerimeterDigger`、`ProjectilePuncher`、`ReverseStep`、`RightClicker`、`SeedOreESP`、
`SeedStructureESP`、`SuperKnockback`、`VehicleBoost`、`WTap`。

> 已知局限与偏差（例如种子矿透不按生物群系过滤、未在真实存档逐格校验）写在
> [CHANGELOG.md](CHANGELOG.md) 里，没有藏起来。

### ESP 与视觉

ESP 名字标签、装备栏、附魔短名、血条等视觉部分参考了
[OpenOpal](https://github.com/ZSZ7/OpenOpal)（GPL-3.0）的设计，并在本工程的渲染后端
（Skia / `GuiGraphics`）上重新实现，同时保留了原生绘制兜底路径。
缓动曲线库（28 条）移植时修掉了参考实现里的两处错误（见 [docs/openaopal-hud-research.md](docs/openaopal-hud-research.md)）。

---

## 从源码构建

### 根工程：Forge 1.20.1

需要 **JDK 17**。项目自带 Gradle 8.11 Wrapper，不依赖系统 Gradle：

```powershell
.\gradlew.bat clean jarJar --console=plain
```

产物：

```text
build/libs/WurstB+ Plus-v1.6.0-Forge-1.20.1.jar
```

启动开发客户端：

```powershell
.\gradlew.bat runClient --console=plain
```

> 首次 `runClient` **需要联网**：ForgeGradle 要拉取 Minecraft 资源与 `commons-io` 等原版库。
> `compileJava` / `test` 可以完全离线跑，但 `runClient` 加 `--offline` 会失败在
> `:minecraftLibraryCopy`（`commons-io:commons-io:2.6` 不在离线缓存里）。

> `jarJar` 结束后会自动把产物复制到本地测试实例的 `mods/` 目录
> （`.test/versions/1.20.1-Forge_47.4.22/mods/`，见 `build.gradle` 的 `copyJarToTestMods`），
> 并清掉该目录里旧的 WurstB+ jar，避免 Forge 重复加载。

跑测试：

```powershell
.\gradlew.bat test --offline --console=plain
```

### 其他版本工程

`versions/`（Forge）、`fabric/versions/`、`neoforge/versions/` 下每个 MC 版本都是**独立工程**，
各自有 `build.gradle` 与 wrapper，**不存在共享 sourceSet**。生产任务因版本而异：

| 加载器 | 任务 |
| --- | --- |
| Forge | `jarJar`（1.20.2–1.21.1）/ `allJar`（1.21.3+） |
| NeoForge | `jar` |
| Fabric | `remapJar`（26.x 为 `jar`） |

```powershell
cd versions\1.21.5
..\..\gradlew.bat clean allJar --console=plain
```

构建工具链（JDK / Gradle / 加载器版本）逐工程见 [PROJECT_INDEX.md](PROJECT_INDEX.md)，
批量构建与逐版本启动测试脚本见 [docs/RELEASE.md](docs/RELEASE.md)。

---

## 仓库结构

```text
.
├── src/                    根工程（Forge 1.20.1，v1.6.0）
├── fabric/                 Fabric 1.20.1 工程
├── neoforge/               NeoForge 1.20.1 工程
├── versions/               Forge 新版本工程（19 个）
├── fabric/versions/        Fabric 新版本工程（21 个）
├── neoforge/versions/      NeoForge 新版本工程（21 个）
├── docs/                   设计、移植与验证文档
├── scripts/                构建、测试与诊断脚本
└── build.gradle            根工程构建配置
```

**19 + 21 + 21 = 61 个版本工程**，加上 3 个 1.20.1 根工程，合计 **64 个独立 Gradle 构建**。
每个工程都有完整的 `gradle-wrapper.jar`，离线环境下也能解析 Gradle 发行版。

---

## 文档索引

| 文档 | 内容 |
| --- | --- |
| [CHANGELOG.md](CHANGELOG.md) | 版本变更，含已知局限与偏差 |
| [PROJECT_INDEX.md](PROJECT_INDEX.md) | 逐工程索引：工具链、源码文件数、入口类、产物名 |
| [docs/RELEASE.md](docs/RELEASE.md) | **发布与维护手册**（原 README）：产物矩阵、构建脚本、打包校验、验证状态 |
| [docs/PORTING-NEW-VERSIONS.md](docs/PORTING-NEW-VERSIONS.md) | 新版本工程移植计划与逐版本状态 |
| [docs/PORTING-1.21.11-26.2.md](docs/PORTING-1.21.11-26.2.md) | 1.21.11 / 26.2 的渲染管线与移植说明 |
| [PORTING_TASK.md](PORTING_TASK.md) | 移植任务与未完成项 |
| [docs/openaopal-hud-research.md](docs/openaopal-hud-research.md) | ESP / HUD 视觉重构的调研与决策记录 |
| [docs/CONFIG-FORMAT.md](docs/CONFIG-FORMAT.md) | 配置文件格式 |
| [docs/ANTICHEAT.md](docs/ANTICHEAT.md) | 反作弊相关说明 |
| [docs/COMBAT_ARCHITECTURE.md](docs/COMBAT_ARCHITECTURE.md) | 战斗链路架构 |

---

## 许可与署名

- **源码**：继承自 Wurst，采用 **GNU General Public License v3.0**。根工程 1040 个 Java 文件中
  830 个带有 GPL-3.0 文件头。详见 `src/main/java` 各文件头部。
- **`LICENSE.txt`**：是 Minecraft Forge / Forge Mod Loader 的 **LGPL 2.1** 声明文本
  （上游随附），不是本项目的许可。
- **[Wurst](https://github.com/Wurst-Imperium/Wurst7)**（Wurst-Imperium）—— 本项目的代码结构基础。
- **[Baritone](https://github.com/cabaletta/baritone)** —— 寻路与自动挖掘。
- **[Perimeter Digger](https://github.com/HackerRouter/Perimeter-Digger)**（HackerRouter）—— 周界挖掘功能的语义来源。
- **[OpenOpal](https://github.com/ZSZ7/OpenOpal)**（ZSZ7）—— ESP / HUD 视觉设计的参考。

---

## 安全与免责

- 这是**作弊 / 辅助客户端**。在多人服务器上使用可能违反服务器规则并导致封禁，
  **请自行承担风险**，并只在允许的服务器或单人世界中使用。
- 项目**不提供任何反检测保证**。`docs/ANTICHEAT.md` 记录的是已知情况，不是免封承诺。
- 部分功能（种子矿透、种子反解）依赖对原版世界生成数学的复刻，其**已知偏差与能力边界**
  写在 [CHANGELOG.md](CHANGELOG.md) 中；不要把它们当成精确无误的结论。
