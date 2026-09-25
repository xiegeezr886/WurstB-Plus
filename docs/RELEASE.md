# 发布与维护手册（原 README）

> 本文件是 WurstB+ Plus 的**维护与发布手册**：早期由原来的根 README 搬移而来，之后持续更新，
> 现在同时承担**功能说明**（新增子系统、新增 Hack 清单、HUD 元素、已重构机制、架构概览）与
> **维护者 / 发布文档**（64 个工程的产物矩阵、构建与运行、打包校验、逐版本移植状态、
> Baritone 依赖兼容性、验证状态）。
> 面向用户的介绍、下载与安装请看根目录 [README.md](../README.md)。

<p align="center">
  <img src="../logo.png" alt="WurstB+ Plus" width="768"/>
</p>

## 项目概览

WurstB+ Plus 是一个基于 Wurst 代码结构扩展的 Minecraft 客户端项目。工作区内共有 **64 个独立 Gradle 构建工程**，全部已有打包产物：

- **61 个版本工程**：`versions/` 19（Forge）+ `fabric/versions/` 21 + `neoforge/versions/` 21，覆盖 MC 1.20.2–1.20.6、1.21–1.21.11、26.1、26.1.1、26.1.2、26.2（1.20.5 与 1.21.2 无官方 Forge，只有 NeoForge / Fabric）；
- **3 个 1.20.1 根工程**：根目录 Forge（**v1.6.0**）、`fabric/` 与 `neoforge/`（后两个是 **v1.5.0**）。

**64 个工程中，63 个的 v1.5.0 产物已按当前源码重新构建**，并与留存的原 1.20.1 Forge 产物一起发布在 GitHub Release
[**WurstB+ Plus 1.5.0**](https://github.com/xiegeezr886/WurstB-Plus/releases/tag/v1.5.0)（**共 64 个资产**，命名统一为
`WurstB+.Plus-<版本>-<加载器>-<mc>.jar`）。每个 jar 都做过打包校验（zip 完好、含加载器元数据与 Mixin 配置、含主类），
但**除 1.21.11 / 26.2 的六个工程外均无游戏内启动验证**——详见[多版本平行移植](#多版本平行移植)与[验证状态](#验证状态)。

> 计数口径：**61 个工程目录** = `versions/` 19（Forge）+ `fabric/versions/` 21 + `neoforge/versions/` 21。加上 3 个 1.20.1 根工程（根目录 Forge、`neoforge/`、`fabric/`）合计 **64 个独立 Gradle 构建**，与「多加载器目录」一节的 64 一致。

详细文件索引见 [PROJECT_INDEX.md](../PROJECT_INDEX.md)，版本变更见 [CHANGELOG.md](../CHANGELOG.md)，移植计划与逐版本状态见 [docs/PORTING-NEW-VERSIONS.md](PORTING-NEW-VERSIONS.md)。

> **版本分布**：只有根目录 **Forge 1.20.1 工程（v1.6.0）** 包含 v1.6 新子系统；
> 其余 **63 个工程均为 v1.5.0 形态**，v1.6 子系统尚未移植，见「v1.6 新增子系统」。

## 发布产物矩阵（63 个重建 + 1 个留存）

下表是 GitHub Release **v1.5.0** 的资产清单，文件名规则为
`WurstB+.Plus-<版本>-<加载器>-<mc>.jar`（本地文件名用空格，见下）。

| 加载器 | 覆盖的 MC 版本 | 工程数 | 生产任务 |
| --- | --- | --- | --- |
| Forge | 1.20.2–1.20.4、1.20.6、1.21、1.21.1、1.21.3–1.21.11、26.1、26.1.1、26.1.2、26.2 | 19 | `jarJar`（1.20.2–1.21.1）/ `allJar`（1.21.3+） |
| Fabric | 1.20.2–1.20.6、1.21、1.21.1、1.21.2–1.21.11、26.1、26.1.1、26.1.2、26.2 | 21 | `remapJar`（26.x：`jar`） |
| NeoForge | 1.20.2–1.20.6、1.21、1.21.1、1.21.2–1.21.11、26.1、26.1.1、26.1.2、26.2 | 21 | `jar` |

Forge 无官方 1.20.5 与 1.21.2，故这两个版本只有 Fabric / NeoForge。

> **Release 里 64 个资产 = 本轮重建的 63 个 + 留存的 1 个**（`WurstB+.Plus-1.5.0-Forge-1.20.1.jar`，
> 命名未改）。三个 1.20.1 工程里**只有根目录 Forge 是 v1.6.0 形态**，无法再逐字节重建 1.5.0 的它，
> 故保留发布当时的产物；`fabric/` 与 `neoforge/` 仍是 v1.5.0，本轮按当前源码重建并替换了同名旧资产。
> 计数口径：上表 **61 个版本工程**（`versions/` 19 + `fabric/versions/` 21 + `neoforge/versions/` 21）
> 全部重建，再加 `fabric/`、`neoforge/` 两个 1.20.1 根工程，共 **63 个重建**；加上留存的根 Forge 产物，
> Release 合计 **64 个资产**。

### 本地构建路径

构建脚本把每个工程的产物收敛到 `build/release-v1.5/`（该目录在 `.gitignore` 内），日志在
`build/release-v1.5-logs/`，逐工程结果在 `build/release-v1.5/_build-report.txt`。脚本本身位于
`tmp-recon/`（仓库外，不随仓库分发），逻辑是「读 `gradle.properties` 的 `mod_version` → 选生产任务 → 跑 Gradle → 收集并重命名 jar」：

```powershell
# 构建全部 v1.5 工程（联网；JDK 21 用于 MC ≤ 1.21.11，JDK 25 用于 26.x）
python D:\WurstB\tmp-recon\build-v1.5-release.py

# 只重试指定工程
python D:\WurstB\tmp-recon\build-v1.5-release.py Forge-1.21.5 NeoForge-26.2

# 打包校验（zip 完好 / 加载器元数据 / Mixin 配置 / 主类）
python D:\WurstB\tmp-recon\validate-jars.py
```

各工程自身产物仍落在自己的 `build/libs/`，例如
`versions/1.21.5/build/libs/WurstB+ Plus-v1.5.0-Forge-1.21.5.jar`。

## 原发布版本矩阵（15 个工程）

| Minecraft | 加载器 | 加载器版本 | Java | 工程目录 | 项目版本 | 发布产物 |
| --- | --- | --- | --- | --- | --- | --- |
| 1.20.1 | Forge | 47.4.10 | 17 | 根目录 | **v1.6.0** | `build/libs/WurstB+ Plus-v1.6.0-Forge-1.20.1.jar` |
| 1.20.1 | NeoForge | 47.1.3 | 17 | `neoforge/` | v1.5.0 | `neoforge/build/libs/WurstB+ Plus-v1.5.0-NeoForge-1.20.1.jar` |
| 1.20.1 | Fabric | Loader 0.16.14 / API 0.92.6 | 17 | `fabric/` | v1.5.0 | `fabric/build/libs/WurstB+ Plus-1.5.0-Fabric-1.20.1.jar` |
| 1.21.1 | Forge | 52.1.16 | 21 | `versions/1.21.1/` | v1.5.0 | `versions/1.21.1/build/libs/WurstB+ Plus-v1.5.0-Forge-1.21.1.jar` |
| 1.21.1 | NeoForge | 21.1.244 | 21 | `neoforge/versions/1.21.1/` | v1.5.0 | `neoforge/versions/1.21.1/build/libs/WurstB+ Plus-v1.5.0-NeoForge-1.21.1.jar` |
| 1.21.1 | Fabric | Loader 0.16.14 / API 0.115.0 | 21 | `fabric/versions/1.21.1/` | v1.5.0 | `fabric/versions/1.21.1/build/libs/WurstB+ Plus-1.5.0-Fabric-1.21.1.jar` |
| 1.21.11 | Forge | 61.2.0 | 21 | `versions/1.21.11/` | v1.5.0 | `versions/1.21.11/build/libs/WurstB+ Plus-v1.5.0-Forge-1.21.11.jar` |
| 1.21.11 | NeoForge | 21.11.45 | 21 | `neoforge/versions/1.21.11/` | v1.5.0 | `neoforge/versions/1.21.11/build/libs/WurstB+ Plus-v1.5.0-NeoForge-1.21.11.jar` |
| 1.21.11 | Fabric | Loader 0.19.3 / API 0.141.6 | 21 | `fabric/versions/1.21.11/` | v1.5.0 | `fabric/versions/1.21.11/build/libs/WurstB+ Plus-1.5.0-Fabric-1.21.11.jar` |
| 26.1.2 | Forge | 64.1.0 | 25 | `versions/26.1.2/` | v1.5.0 | `versions/26.1.2/build/libs/WurstB+ Plus-v1.5.0-Forge-26.1.2.jar` |
| 26.1.2 | NeoForge | 26.1.2.87 | 25 | `neoforge/versions/26.1.2/` | v1.5.0 | `neoforge/versions/26.1.2/build/libs/WurstB+ Plus-v1.5.0-NeoForge-26.1.2.jar` |
| 26.1.2 | Fabric | Loader 0.19.3 / API 0.155.2 | 25 | `fabric/versions/26.1.2/` | v1.5.0 | `fabric/versions/26.1.2/build/libs/WurstB+ Plus-1.5.0-Fabric-26.1.2.jar` |
| 26.2 | Forge | 65.1.0 | 25 | `versions/26.2/` | v1.5.0 | `versions/26.2/build/libs/WurstB+ Plus-v1.5.0-Forge-26.2.jar` |
| 26.2 | NeoForge | 26.2.0.53-beta | 25 | `neoforge/versions/26.2/` | v1.5.0 | `neoforge/versions/26.2/build/libs/WurstB+ Plus-v1.5.0-NeoForge-26.2.jar` |
| 26.2 | Fabric | Loader 0.19.3 / API 0.156.0 | 25 | `fabric/versions/26.2/` | v1.5.0 | `fabric/versions/26.2/build/libs/WurstB+ Plus-1.5.0-Fabric-26.2.jar` |

> 26.2 为最新的 **v1.5 形态**适配版本。6 个 1.21.11/26.2 工程的最终状态见 [PORTING_TASK.md](../PORTING_TASK.md)。
> **v1.6 新子系统目前只在根目录 Forge 1.20.1 工程中实现**，见「v1.6 新增子系统」。
> 上表以外的 46 个新版本工程**已全部打包并上传到 v1.5.0 Release**，但无启动验证，状态见[多版本平行移植](#多版本平行移植)。

### Baritone 依赖兼容性

- Forge/NeoForge 1.20.1 使用 `baritone-api-forge-1.20.1`。
- Forge/NeoForge 1.21.1 使用 `baritone-api-forge-1.21.1`，不能替换为 1.21.2。
- Forge、NeoForge 和 Fabric 1.21.11 均内嵌 Baritone 1.17.0-1.21.11。
- Forge、NeoForge 和 Fabric 26.1.2 均内嵌由 `baritone-26.1.zip` 源码构建的 Baritone 1.18.0，并保留其独立加载器元数据、Mixin 配置与 Mixin Connector。
- Forge、NeoForge 和 Fabric 26.2 均内嵌经过 26.2 渲染 API 兼容补丁的 Baritone 1.18.0-26.2。
- Fabric 1.20.1 内嵌官方 `baritone-api-fabric-1.10.3`，仅匹配 MC 1.20-1.20.1。
- Fabric 1.21.1 内嵌 `baritone-api-fabric-1.11.2`，仅匹配 MC 1.21-1.21.1。
- 新版本工程沿用各自源码基线的 Baritone 依赖。Forge 1.21 与 1.21.1 已在 `build.gradle` 中启用 `flatDir { dirs "libs" }`，由工程内的 `libs/baritone-api-forge-1.21.1.jar` 提供依赖，避开 ForgeGradle 重映射仓库解析失败的问题。
- **每个工程内嵌的 Baritone 都必须声明它自己那个 MC 版本**。加载器会校验内嵌 mod 自身的 `minecraft` 依赖，声明不匹配的 Baritone 会让游戏在启动前就被拒绝，表现为「Baritone 要求的 Minecraft 版本不符」，而不是编译错误。历史上一个 Baritone 构建被整条 MC 线共用（例：1.21.11 的 jar 被 1.21.3…1.21.11 复用），于是只有 1.21.11 能通过校验。

  > 修复脚本：`scripts/fix-baritone-mc-declaration.ps1`（幂等，可反复运行；`-WhatIf` 只打印计划，`-SelfTest` 跑断言）。
  >
  > 判定规则：内嵌 jar 的声明若已接受本工程的 MC 版本则保持不动，工程继续依赖共享的基准 jar；否则在同一个本地 Maven 仓库里生成一份 `<版本>-mc<MC>` 副本（连同改写过的 `.pom`），并把 `build.gradle` 的坐标、`from(...)` 硬编码路径、签入的 `jarjar/metadata.json` 一并指向该副本。基准 jar 本身永不被改写，因此重跑始终得到同一结果。
  >
  > 末行输出 `updated / already-correct / unresolved / failed` 四项。`unresolved` 表示**根本找不到内嵌 jar**，与「已经正确」是两回事，因此它单独计数；只要它不为 0 脚本就以非 0 退出。新克隆仓库时 `baritone-maven/` 并不存在，属于预期情况，`build-all.ps1` 因此传 `-AllowUnresolved` 把它降级为警告。
  >
  > 注意：这只是**元数据修复**，字节码仍是原来那个构建。1.21.3–1.21.5、1.20.5/1.20.6、1.21.2 等版本内嵌的仍是别的 MC 世代的 Baritone 代码，改声明并不能解决代码层面的不兼容，只能让加载器不再拒绝它。
  >
  > `scripts/build-all.ps1` 的 `Test-EmbeddedBaritone` 会在打包后校验内嵌 Baritone 的声明是否接受本工程的 MC 版本，不匹配直接判 FAIL，防止该问题回归。

> **注意**：`baritone-maven/` 在 `.gitignore` 中，不随仓库分发。Fabric 26.2 依赖的 `baritone-api-fabric-1.18.0-26.2.jar` 曾因 `META-INF/MANIFEST.MF` 把 `MixinConfigs` 放到空行之后而非法，导致 javac 对每个 `net.minecraft.*` 导入报 `invalid manifest format`（101 个假错误）。本地已重打包修复；克隆仓库后如遇同样报错，需按 [docs/PORTING-NEW-VERSIONS.md](PORTING-NEW-VERSIONS.md) 的说明重建该 jar。

> 注意：为避免 JPMS 模块读取错误（`baritone.api.forge does not read module minecraft`），NeoForge 1.21.1、Forge/NeoForge 26.1.2 的发布包已将 Baritone 类直接合并进 WurstB+ Plus 主模块（类归属 `wurstpenguin` 模块，可访问 `minecraft` 模块）；Forge 1.20.1/1.21.1 仍以 Jar-in-Jar 形式打包。Fabric 版本不受 JPMS 模块读取限制，仍按各自版本内嵌 Baritone JAR。

NeoForge 1.21.1 若启动时出现 `baritone.api.forge does not read module minecraft`，说明仍加载了旧的 1.21.2 包。请删除旧包和单独的 Baritone JAR，只保留对应版本的 `*.jar`。

## 工程状态

> 下表前六行（Minecraft / Java / 构建插件 / 映射 / 加载器 / MixinExtras）描述的是**原发布矩阵**（5 个 MC 版本 × Forge / NeoForge / Fabric，即「原发布版本矩阵」一节那 15 个工程）的工具链；其余各行是根目录 Forge 1.20.1（v1.6.0）工程的实测值。

| 项目 | 当前值 |
| --- | --- |
| Minecraft | 1.20.1 / 1.21.1 / 1.21.11 / 26.1.2 / 26.2 |
| Java | 17 (1.20.1) / 21 (1.21.1、1.21.11) / 25 (26.1.2、26.2) |
| 当前构建插件 | ForgeGradle 6.x / ForgeGradle 7.x / NeoForge ModDevGradle 2.0.143 / Fabric Loom 1.9.2、1.17.17、1.17.19 |
| 当前映射 | Mojang 官方映射 |
| 当前加载器 | Forge 47.4.10、52.1.16、61.2.0、64.1.0、65.1.0 / NeoForge 47.1.3、21.1.244、21.11.45、26.1.2.87、26.2.0.53-beta / Fabric Loader 0.16.14、0.19.3 |
| MixinExtras | Forge/NeoForge/Fabric 0.5.4（根 Forge 工程保留兼容配置） |
| 模组 ID | `wurstpenguin` |
| 模组名称 | WurstB+ Plus |
| 开发者署名 | Penguin |
| 构建状态 | **64 个工程全部产出打包 jar**，其中 63 个按当前源码重建并已上传 v1.5.0 Release（另 1 个是留存的根 Forge 1.20.1 产物）；根目录 **v1.6.0** 另通过 `compileJava` + `test` 验证。除 1.21.11 / 26.2 的六个工程外均无游戏内启动验证（详见 [PORTING_TASK.md](../PORTING_TASK.md) 与 [PROJECT_INDEX.md](../PROJECT_INDEX.md)） |
| 注册 Hack | **209** 个（根工程 v1.6.0；`hacks/` 下声明 `extends Hack` 的类共 210 个，其中 `RadialMenuHack` 未注册） |
| 注册命令 | **57** |
| Other Feature | **18** |
| Forge Mixin | 74 (1.20.1 根工程，见 `wurst.mixins.json`) |
| Java 文件 | **1040**（根 1.20.1 `src/main/java`，含 v1.6 子系统） |
| 单元测试 | **162 个测试类 / 1040 项 / 0 失败**（根工程 1.20.1，2026-09-25 以 Java 17 跑 `gradlew test --offline`），覆盖 v1.6 音乐解析、AMLL 歌词流水线与布局、Compose 动画、MD3 主题、周界挖掘全套（区域几何、边界检测、液体策略、配置迁移、双语文本）、种子矿透（抽样契约、预测确定性、种子存储）、结构定位（区域扫描、频率削减速率）、种子反解（LCG 逐位一致性、搜索闭环、无范围反解还原）与结构扫描器 |

> 根目录是 Forge 1.20.1-47.4.10 工程；`neoforge/`、`versions/` 和 `fabric/` 是独立版本工程，不共享加载器运行时。Forge/NeoForge 使用 Mojang 官方映射，Fabric 使用 Fabric Loom + 官方映射；Fabric 版本通过 Access Widener 和 Fabric API 适配，不代表根工程是 Fabric 项目。

### Gradle wrapper

64 个工程全部含完整的 `gradle-wrapper.jar`（43,764 bytes，含 `Main-Class: org.gradle.wrapper.GradleWrapperMain`）。
四种发行版（8.11 / 8.14.4 / 9.4.1 / 9.6.0）可用 `scripts/seed-gradle-wrapper.ps1` 从工作区 `tools/` 播种到 `~/.gradle/wrapper/dists/`，因此 `gradlew.bat` 在离线环境下可直接解析发行版，无需联网下载。
`scripts/doctor.ps1` 检查 JDK、wrapper 发行版和 v1.6 基准测试是否齐全；它依据 `scripts/common.ps1` 的工程表工作，目前只覆盖已发布的 15 个工程，新版本工程需手工指定 JDK。根工程单测用：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-unit-tests.ps1 -Offline
```

## 相对上游 Wurst 的新增清单

对比方式：用 GitHub 的 Git Trees API 拉取上游 `Wurst-Imperium/Wurst7` 的 `master` 全量文件
清单（157 个 hack / 52 个命令 / 18 个 Other Feature），再逐类求差。结论：本仓库新增
**14 个包、57 个 hack、7 个命令**；Other Feature 与上游数量相同（18 个），没有新增。
14 个包中 8 个在所有工程里都有，另外 6 个只在根目录 Forge 1.20.1（v1.6.0）里实现：

| 范围 | 包 | Hack | 命令 |
| --- | ---: | ---: | ---: |
| 所有工程都可用 | 8 | 42 | 3 |
| 仅根目录 Forge 1.20.1（v1.6.0） | 6 | 15 | 4 |

- 所有工程都有的 8 个包：`clickgui2`、`hud2`、`gui`、`addon`、`macros`、`proxy`、
  `waypoints`、`discord`
- 仅根工程的 6 个包：`music` + `twilight`、`render/skia`、`compose`、`perimeter`、`seed`
- 所有工程都有的 3 个命令：`.macros`、`.proxy`、`.waypoints`
- 仅根工程的 4 个命令：`.perimeter`、`.perimeterdig`、`.seed`、`.twilight`

### 新增 Hack（57 个，分类取自 `setCategory`）

**所有工程都可用（42 个）**

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

**仅根目录 Forge 1.20.1（v1.6.0，15 个）**

| Hack | 分类 | 功能 |
| --- | --- | --- |
| `AirJump` | Movement | 空中跳跃 / 多段跳，可设模式 |
| `EntityCulling` | Render | 异步遮挡查询，跳过被方块完全遮挡的实体，可分组控制与延迟 |
| `MusicPlayer` | Other | 网易云音乐播放器 |
| `NoMissCooldown` | Combat | 去掉空挥冷却，可取消落空的攻击 |
| `NoRotate` | Movement | 忽略服务端的视角纠正，可分别保留 yaw / pitch |
| `PerimeterDigger` | Blocks | 周界挖掘自动化，见「v1.6 新增子系统」 |
| `ProjectilePuncher` | Combat | 击打飞来的投射物 |
| `RadialMenu` | Other | 长按 Tab 的圆盘菜单（未在 `HackList` 注册，游戏内不会出现） |
| `ReverseStep` | Movement | 快速下坠，可设模式、倍率与最大下落距离 |
| `RightClicker` | Combat | 右键连点，可设 CPS 上下限与启动延迟 |
| `SeedOreESP` | Render | 种子矿透，见「v1.6 新增子系统」 |
| `SeedStructureESP` | Render | 种子结构定位，见「v1.6 新增子系统」 |
| `SuperKnockback` | Combat | 增强自己造成的击退，可设受伤时间与触发条件 |
| `VehicleBoost` | Movement | 载具加速，可设水平 / 垂直速度 |
| `WTap` | Combat | 自动 W 敲击以重置疾跑，可设概率与按键时序 |

> 本节数字与分类均按当前源码重新核对（分类取自各类的 `setCategory(Category.X)`，
> 功能取自其设置项与 `@SearchTags`）。注意「v1.5 新增功能」一节里把 `EntityCulling`
> 记在 v1.5 批次、且只列了 15 个 hack，那是当时的状态；实际全工程新增为 42 个。

### 上游有、本仓库没有

| 类型 | 项 |
| --- | --- |
| Hack | `AntiKnockback`、`AttributeSwap`、`KillauraLegit`、`MaceDmg` |
| 命令 | `ViewComp` |
| 包 | `clickgui` 与 `navigator` 已由 `clickgui2` 取代；`analytics`（Plausible 遥测上报）未移植，本仓库另有 `NoTelemetry` / `NoChatReports` 两个 Other Feature 用于关闭遥测与聊天上报 |

## v1.6 新增子系统

以下内容**只存在于根目录 Forge 1.20.1 工程**，其余 63 个工程（61 个版本工程，外加 `fabric/` 与 `neoforge/` 两个 1.20.1 根工程）均未移植。

### 源码包（152 个 Java 文件）

| 包 | 文件数 | 说明 |
| --- | ---: | --- |
| `clickgui2/component` | 27 | VAPE 风格组件层 + `SuperSoft*` 窗口与卡片 |
| `music` + `music/apple` | 28 | 网易云 API、播放器、账号、逐字歌词动画（`AppleLyricPlayer` / `AppleTimeline` / `AppleLayout` / `Spring`）与 AMLL 一比一视觉层（`AmlEasing` / `AmlVisual` / `AmlEmphasize` / `AmlOptimize` / `AmlLayoutReason` / `AmlTween` / `AmlLineBalancer` / `AmlMask`） |
| `clickgui2/music` | 13 | 音乐页面：主页 / 搜索 / 我喜欢 / 歌单 / 登录 / 播放详情 / 底部播放条 / 星河背景 / 封面粒子与涟漪 |
| `compose` | 11 | 声明式 UI 布局树（`UiRow` / `UiColumn` / `UiBox` / `UiText` / `UiSpacer`、`AnimFloat`、`FlowingGradient`） |
| `clickgui2/epsilon` | 5 | Epsilon 拉式下拉 GUI |
| `clickgui2/supersoft` | 5 | MD3 TonalSpot 调色板（`EpsilonMd3Theme`）、`SuperSoftTheme` / `SuperSoftRenderer` / `UiMotion` / `UiTween` |
| `render/skia` | 4 | Skiko 矢量渲染（`SkikoNatives` / `SkiaGlBackend` / `SkiaFontManager` / `SkiaRegionRenderer`） |
| `gui/visual` | 3 | `VisualTheme` 语义色 token、`VisualRenderer`、`VisualScreenMotion` |
| `hud2/render` | 2 | `RiseFrostedGlass` 磨砂玻璃、`RiseHudFont` |
| `perimeter` | 35 | 周界挖掘全套：区域模型与游标、液体策略与边界封闭、方块限制与批次、导航 / 交互 / 装备 / 补给策略、28 状态自动化编排、双语文本 |
| `perimeter.config` | 11 | 按服务器 / 存档分存的配置模型、迁移与原子写盘（`PerimeterConfigStore`） |
| `perimeter.detect` | 2 | 不规则区域边界检测（`BoundaryDetector` / `PerimeterGrid`） |
| `seed` | 8 | 种子矿透核心：种子存储、纯 Java 矿物预测（含逐条 placed_feature 规则表）、Baritone 目标挖掘作业 |
| `seed.structure` | 3 | 结构定位：直接调用原版公开放置方法（构造上与原版一致）、频率削减、已加载区块群系校验 |
| `seed.search` | 4 | 种子反解：结构观测 + 零分配 LCG 候选搜索（快路径先与原版自检） |
| `seed.crack` | 2 | 无范围反解：位块分解 + 陪集求交（弱约束集不保证唯一） |
| `seed.scan` | 1 | 自动观测：方块特征识别 6 类结构（阈值未在存档标定） |

新增 Hack（14 个）：`AirJump`、`EntityCulling`、`MusicPlayer`、`NoMissCooldown`、`NoRotate`、`PerimeterDigger`、`ProjectilePuncher`、`ReverseStep`、`RightClicker`、`SeedOreESP`、`SeedStructureESP`、`SuperKnockback`、`VehicleBoost`、`WTap`。

其中 `PerimeterDigger`（Blocks 分类）配合 `.perimeter` 与 Brigadier `/perimeterdig`（含 Tab 补全）在 v1.6 **原生等价移植**了社区模组 [Perimeter Digger](https://github.com/HackerRouter/Perimeter-Digger) 的全部功能：闭区间矩形规划与**不规则区域边界检测**、**液体 avoid / replace / seal_boundary** 策略与边界封堵、批次上限与背包满自动暂停、**自动拾取与多卸货点卸货**、**工具/鞘翅耐久替换**、**自动进食、补给、睡觉**、**跨维度熔炉修复**、**行走与鞘翅寻路**、**按服务器/存档分存配置**、**中英双语文本与命令**。原模组为 Fabric / MC 26.1.1 / Java 25 且依赖修改版 Baritone，无法直接内置，故本实现把区域挖掘语义落在本项目自己的 `PerimeterMiningSchematic` 上、交给官方 Baritone 的 `BuilderProcess` 执行，并按同样判定复刻液体与边界语义（具体偏差见 `CHANGELOG.md`）。

其中 `SeedOreESP`（Render 分类）配合 `.seed` 实现**种子矿透**：按服务器 / 存档分存已知种子（单人模式直接读取集成服务器种子），用纯 Java 复刻原版矿物生成数学（Xoroshiro 随机源 + `setDecorationSeed` / `setFeatureSeed` + 逐条 placed_feature 规则表，含 count / rarity / height_range / 矿脉椭圆与 `discardOnAirChance`）预测区块内矿物坐标，在客户端渲染 ESP，并可选把预测目标批量交给官方 Baritone 的 `GoalComposite` 自动挖掘（`.seed mine` 切换）。该路线**零新增依赖**：不内置 Meteor Client（Fabric 专用）、不引入 Cubiomes 与 seedfinding/latticg，也不注入 Baritone 内部类（只用公开 API）。已知局限：不按生物群系过滤规则（结果是超集）、不反推种子、未在真实世界生成中逐格校验坐标。

同一份种子还被 `SeedStructureESP`（Render 分类）用于**结构定位**：`.seed structures [半径]` 列出候选结构，`.seed structesp` 在已加载区块渲染标记。放置数学**不重写**——直接调用原版 public 的 `RandomSpreadStructurePlacement#getPotentialStructureChunk(long,int,int)`，因此与原版构造上一致，数据包 / 模组新增结构同样生效；原版 19 个结构集中 18 个 `random_spread` 全部支持，`concentric_rings`（要塞）使用另一套算法故跳过。稀有度所依赖的 `frequency` / `frequency_reducer`（埋藏的宝藏 0.01、废弃矿井 0.004、掠夺者前哨站 0.2）通过放置编解码器导出 JSON 读取，并按字节码复刻四种削减器。

结构观测还能反过来**反解种子**（`seed.search`）：`.seed observe <结构集> <x> <z>` 记录你实际看到的结构，`.seed search <from> <to>` 在后台线程用零分配 LCG 遍历该范围，找出所有能复现全部观测的种子，并给出 `.seed set` 命令。热循环的 LCG 与频率削减通过单测与原版 `WorldgenRandom` **逐位对照**，搜索前还会用原版放置方法对 32 个采样区块做一次运行时自检，不一致就拒绝搜索。**能力边界**：这不是「从零反推」——不做格基归约，无法在 2^48 全域内无范围求解；同集合的多个观测并不独立，因此通常返回多个候选，需要玩家用更多结构逐步收敛（诚实说明见 `CHANGELOG.md`）。已知局限：忽略 `exclusion_zone`、要塞不参与、未在真实存档中验证反解结果。

### GUI 入口切换

`clickgui2/ClickGuiScreens.java` 按 `ClickGuiStyle` 在三套界面间切换（默认 Epsilon）：

```java
public static Screen create(Screen parent)
{
    return switch(WurstClient.INSTANCE.getGuiPreferences()
        .getClickGuiStyle())
    {
        case VAPE -> new VapeClickGuiScreen();
        case SUPERSOFT -> new SuperSoftClickGuiScreen(parent);
        case EPSILON -> new EpsilonDropdownScreen(parent);
    };
}
```

ClickGUI 设置项「GUI style」与主面板同一开关会循环 **Epsilon → SuperSoft → Vape**。旧 `gui-preferences.json` 的 `vapeMode: true` 仍映射为 Vape。

### 强调色约定

客户端统一强调色为 **`#007CFF`**（`VisualTheme.ACCENT` = `EpsilonMd3Theme.PRIMARY`），Music、Rise、PvPUtils、`NotificationSeverity.INFO` 均引用该值。

### Skiko 原生库打包

`skiko-windows-x64.dll`（16.5 MB）与 `icudtl.dat`（10.0 MB）**不使用 jarJar**——jarJar 会重定位资源路径，导致 Skiko 无法在 jar 内定位原生库。
改为随 mod 资源打包到 `assets/wurst/skiko/`，运行时由 `SkikoNatives` 解压到 gameDir，并通过 `skiko.library.path` / `skiko.data.path` 系统属性显式加载。
这也是根工程产物从 v1.5 的约 29 MB 增长到 **68.1 MB** 的主要原因。

## 架构概览

### 启动流程

当前入口为 `net.wurstclient.WurstForgeInitializer`。入口在 `FMLClientSetupEvent` 的主线程工作队列中调用 `WurstClient.initialize()`；HUD 由 Forge `RenderGuiEvent.Post` 单路派发，`IngameHudMixin` 只处理原版遮罩取消逻辑，避免序号注入失效或重复渲染。初始化顺序如下：

1. 创建客户端配置目录。
2. 初始化事件管理器和功能冲突管理器。
3. 创建 Hack、命令和 Other Feature 注册表。
4. 加载设置、启用状态、按键、好友和 GUI 主题。
5. 初始化 DelayQueue、InventoryActionQueue、MacroManager、WaypointsManager、ProxyManager、AddonManager。
6. 注册客户端指标、实体快照与 HUD 元素（FPS/Coords/Ping/TPS/Speed/Server/Clock/Armor/Inventory/Potion/Combo/Keystrokes/Target HUD）。
7. 启动 Discord RPC 和 Macro 管理器。

核心入口：

- `src/main/java/net/wurstclient/WurstForgeInitializer.java`
- `src/main/java/net/wurstclient/WurstClient.java`
- `src/main/java/net/wurstclient/hack/HackList.java`
- `src/main/java/net/wurstclient/command/CmdList.java`
- `src/main/java/net/wurstclient/other_feature/OtfList.java`

### 多加载器目录

```text
src/                                   Forge 1.20.1 根工程（v1.6.0，独立完整源码树）
versions/<mc>/                         Forge 子工程，19 个（1.20.2–1.20.6、1.21–1.21.10、26.1–26.2；无 1.20.5 / 1.21.2）
neoforge/                              NeoForge 1.20.1 根工程
neoforge/versions/<mc>/                NeoForge 子工程，21 个
fabric/                                Fabric 1.20.1 根工程
fabric/versions/<mc>/                  Fabric 子工程，21 个
scripts/                               构建、诊断与批量测试脚本
docs/                                  反作弊、战斗架构、配置格式与移植文档
.test/                                 PCL 格式本地测试环境（实例、libraries、assets、natives、报告）
gradle/ 与 ~/.gradle/wrapper/dists/     Gradle 8.11 / 8.14.4 / 9.4.1 / 9.6.0 离线发行版
download/                              可选发布聚合目录，仅在 build-all.ps1 -PublishToDownload 时创建
```

按 MC 版本统计：Forge 19 个（1.20.5 与 1.21.2 无官方 Forge）、Fabric 21 个、NeoForge 21 个，加上 3 个 1.20.1 根工程，合计 **64 个工程**。每个工程拥有独立的 `build.gradle`、`settings.gradle`、gradle wrapper 与**完整复制的源码树**，不存在共享 sourceSet。

各加载器工程拥有独立的构建脚本、Mixin 配置和平台适配层。不要把 Fabric JAR、Forge JAR 或 NeoForge JAR 混放到同一个实例中。

### 事件系统

`EventManager` 以监听器接口类型维护订阅列表，同时支持 `@WurstSubscribe` 注解 + LambdaMetafactory 直接方法调用订阅。Mixin 将 Minecraft 生命周期、输入、网络、渲染和移动节点桥接为 Wurst 事件。事件继承分发使订阅 `PacketEvent` 可同时接收 `Read`/`Send` 子事件。

典型调用链：

```text
Minecraft / Mixin hook
  -> EventManager.fire(...)
  -> Interface Listener + LambdaMetafactory Subscriber
  -> Hack state update / packet action / rendering
```

主要位置：

- `src/main/java/net/wurstclient/event/`
- `src/main/java/net/wurstclient/events/`
- `src/main/java/net/wurstclient/mixin/`
- `src/main/java/net/wurstclient/mixinterface/`

### 设置与配置

所有功能设置继承 `Setting`。当前基础设施支持：

- 设置旧名称别名。
- 条件可见性。
- 任意深度设置树（父级、深度、单一归属、循环检查和递归折叠渲染）。
- 设置变化监听器。
- 单项配置损坏隔离。
- 临时文件写入和原子替换（`JsonUtils.toJson` 使用 `ATOMIC_MOVE`）。
- 设置/按键/启用Hack/TooManyHax 配置档案加载与保存。
- ClickGUI 配置列表可直接选择、加载、覆盖保存、刷新和打开目录。
- GUI 独立偏好保存到 `wurst/gui-preferences.json`，自定义字体放入 `wurst/fonts`。

主要位置：

- `src/main/java/net/wurstclient/settings/Setting.java`
- `src/main/java/net/wurstclient/settings/SettingsFile.java`
- `src/main/java/net/wurstclient/util/json/JsonUtils.java`
- `src/main/java/net/wurstclient/clickgui2/SettingsWindow.java`

### Hack 名称是标识符，不是显示名

`Hack` 构造函数的那个字符串不只是界面上显示的名字，它同时是：

- 注册键与查表键 —— `HackList.hax.put(hack.getName(), hack)` / `getHackByName()`，**大小写敏感的精确匹配**；
- 快捷键绑定的目标名与启用状态存档里的键（`KeybindProcessor`、`EnabledHacksFile`）；
- 翻译键 —— `Hack.java:66` 取 `"hack.name." + name.toLowerCase()`，`Hack.java:35` 取 `"description.wurst.hack." + name.toLowerCase()`。

所以**名称必须是 ASCII 英文**，中文只放在翻译文件里。全部 196 个 hack 中曾经只有 `SearchHack`、`AutoReconnectHack` 写成了 `super("搜索")` / `super("自动重连")`，后果是派生出的键变成 `hack.name.搜索` / `description.wurst.hack.搜索`，而 `en_us.json`、`zh_cn.json` 里存的是 `hack.name.search` / `description.wurst.hack.search`，两者永远匹配不上——这两个 hack 的描述文字因此完全丢失，界面只能退回显示键名本身。

修复：把标识符改回 `Search` / `AutoReconnect`。中文显示不受影响，`hack.name.search` → 「方块搜索」、`hack.name.autoreconnect` → 「自动重连」本来就在翻译文件里。

顺带清掉了每个工程 `zh_cn_names.json` 里永远不可能被任何类命中的死键（`hack.name.antiknockback` —— 类已改名为 `NoVelocity`；`hack.name.entityculling` —— 仅根工程 v1.6.0 还有这个类，那里保留）。判断死键要**按工程逐个看**有没有对应类，不能只看到键存在就删。

> 不变式：`zh_cn_names.json` 的键集合应与该工程 `hacks/` 下所有 `*Hack.java` 的 `super()` 名称一一对应，且 `WurstCnNamesTest` 断言的数字等于该键数（现为 197 或 196，根工程 210）。这条不变式曾被 `ce794b2` 破坏——它把 183 键扩到 198 键却没同步测试断言，导致 34 个工程 `:test` 失败、`build` 挂掉。

## v1.5 新增功能

### 架构升级

- **LambdaMetafactory 事件分发**：`@WurstSubscribe` 注解方法通过 LambdaMetafactory 生成 `Consumer<Event>`，直接方法调用，消除反射开销。
- **事件继承分发**：订阅父事件类可接收其所有子类事件（如订阅 `PacketEvent` 接收 `Read`/`Send`）。
- **Levenshtein 模糊搜索**：ClickGUI / Navigator 输入错拼仍可匹配功能名和搜索标签。
- **智能绑定**：支持 TOGGLE（默认，`killaura`）、HOLD（按住激活，`+killaura`）、SMART（智能切换，`~killaura`）。
- **延迟操作队列**：`DeferredActionQueue` 按命名队列跨 tick 执行延迟操作。
- **分层设置树**：`Setting.withChildren()` 支持任意深度、父子归属与循环检查，ClickGUI 和 Navigator 均递归渲染。
- **稳定设置绑定**：右 Ctrl 浮窗与右 Shift Navigator 仅在设置展开层级或条件可见性改变时重建布局；滑块、复选框和枚举的普通数值变化保持原组件实例，拖动与弹层会持续写回模块设置。
- **系统凭据保护**：Windows Credential Manager、macOS Keychain 或 Linux Secret Service 只保存随机主密钥；账号文件使用带随机 nonce 的 AES-GCM，并自动读取迁移旧 AES/CFB8 数据。
- **共享旋转仲裁**：`RotationQueue` 按后台、移动、方块放置、战斗和紧急等级仲裁静默旋转，同级请求按最近提交顺序执行；`RotationFaker` 参照 LiquidBounce RotationManager 独立维护当前旋转与已成功发送的服务端旋转，Mixin 仅替换原版 `LocalPlayer.sendPosition()/tick()` 内部的角度 getter 返回值，不写入玩家真实角度、不额外构造旋转包。第一人称相机与移动输入保持客户端方向，第三人称模型仅在静默旋转生效时插值显示服务端方向。
- **库存动作队列**：`InventoryActionQueue` 按菜单 ID、状态验证、优先级和所有者调度原子点击链；AutoTotem 与 AutoArmor 不再留下跨 Tick 光标状态。

### 子系统

- **Macros 宏系统**：`.macros add/remove/list`，按键触发命令序列，支持 `_delay:N` tick 延迟。
- **Waypoints 路径点**：`.waypoints add/remove/list`，3D 十字标记渲染，跨维度持久化。
- **Proxy 代理系统**：`.proxy add/remove/set/clear/list`，通过 Netty 管线为新建服务器连接注入 SOCKS4/SOCKS5 处理器，不修改 JVM 全局代理属性。
- **Addon 扩展系统**：`WurstAddon` + `AddonManager`，ServiceLoader 发现，拒绝 Hack/Command 名称冲突，初始化失败不会残留已加载状态。
- **Brigadier 命令**：`BrigadierCommand` 基类，通过 Forge `RegisterClientCommandsEvent` 注册到客户端 `CommandDispatcher`。
- **Discord RPC**：仅显示单人/多人/主菜单状态、客户端版本和活动 Hack 数量，不泄露服务器地址；IPC 在独立单线程执行器中更新。

### 新增 Hacks（15 个）

| Hack | 分类 | 功能 |
|------|------|------|
| **AntiBot** | Combat | 检测反作弊假人（Ping/隐身/UUID），自动过滤战斗目标 |
| **BossStack** | Render | Boss 血量条堆叠紧凑渲染 |
| **Breadcrumbs** | Render | 玩家移动轨迹线（渐变色） |
| **DankBobbing** | Fun | 视角晃动 + 移动 bob 增强 |
| **EntityCulling** | Render | 异步 GPU 遮挡查询，跳过被方块完全遮挡的实体 |
| **LightOverlay** | Render | 低亮度刷怪区域黄色覆盖层 |
| **LogoutSpots** | Render | 玩家离线位置红色标记 |
| **Notebot** | Fun | 解析旧版及 NBS v1-v5，按 tick/和弦/音色扫描并播放音符盒歌曲 |
| **PacketCanceller** | Other | 选择性取消 5 种数据包 |
| **PacketLogger** | Other | 网络收发包日志（限流） |
| **PlayerHalo** | Render | 在可见玩家头顶批量绘制跟随主题色的小光环 |
| **PopChams** | Render | Totem 触发时彩色方块升起动画 |
| **TargetShader** | Render | 将当前战斗目标送入独立 FBO，统一执行 Outline/Pulse/Gradient/Smoke 后处理 |
| **Twerk** | Fun | 快速下蹲/起立舞蹈 |
| **Vomit** | Fun | 快速使用食物（呕吐效果） |

### 新增 Commands（3 个）

| 命令 | 功能 |
|------|------|
| `.macros` | 宏管理（add/remove/list） |
| `.waypoints` | 路径点管理（add/remove/list） |
| `.proxy` | 代理管理（add/remove/set/clear/list） |

### HUD 元素

- `HudManager` 统一完成元素渲染、对齐、生命周期和布局持久化，不再让每个元素单独注册 GUI 事件。
- **FPS / Coordinates / Ping / TPS / Speed / Server / Clock**：独立文本型状态元素；坐标项支持主世界与下界 1:8 换算，TPS 使用时间同步包间隔平滑采样。
- **Armor**：按实际物品图标和耐久信息绘制四件护甲。
- **Potion Effects**：按名称排序显示效果、等级和剩余时间。
- **Combo**：通过 `MultiPlayerGameMode.attack()` 入口 Mixin 监听本地玩家攻击，按目标与三秒窗口统计连续攻击。
- **Keystrokes**：显示实际绑定的 WASD、空格、潜行、疾跑和左右鼠标键，包含原版紧凑样式、按压动画与一秒 CPS 统计；启用后在 HUD 编辑器中直接显示可拖动本体，不再显示占位卡片。
- **Inventory**：显示主背包 `9–35` 槽的 3×9 物品网格，包含数量和耐久叠加；采用与 TargetHUD 一致的深色圆角、细描边及主题色侧条，默认关闭，可在 HUD 编辑器中启用、预览、拖动和切换锚点。
- **Target HUD**：保留 Raven 式攻击目标保持/淡出，显示玩家头像、名称和主题色动画生命条，并在目标持有主手物品或穿戴盔甲时按主手、头盔、胸甲、护腿、靴子顺序显示紧凑装备栏；全空时不渲染装备栏。卡片使用与通知 HUD、HackList 统一的半透明黑底、弱白描边和左侧主题色条，目标来源覆盖最近攻击、Killaura、MultiAura 与准星指向玩家。
- **Minimap**：提供北方固定的纯圆形地形小地图，将已加载区块按 `16x16` 瓦片缓存并在每 Tick 预算内渐进刷新，再合成为动态纹理；显示区块网格、地图内方位、玩家、好友、生物与掉落物，不再绘制地图下方的坐标、维度、生物群系和朝向文字。默认关闭，不会为绘图强制加载区块，静止时也会按瓦片 TTL 更新地形变化。
- **HackList / Logo / Notifications**：纳入统一布局和启用状态管理，兼容现有 `hud-layout.json`；通知卡片底部使用与严重级别一致的 3 秒进度条，读满后进入淡出并从队列移除；HUD2 的 Logo 开关不再被旧版 `Visibility` 设置二次隐藏。
- HUD 编辑器恢复固定信息卡片交互，卡片显示元素名称、ON/OFF 与锚点，使用 `96x44` 命中区域拖动和切换；小地图启用后使用真实尺寸和实时地图预览进行命中、拖动、锚定与边界限制。

### 高级功能

- **点击模式**：`ClickPattern` 按 LiquidBounce 原始 `fill(IntArray)` 语义提供 Stabilized/Efficient/Spamming/DoubleClick/Drag/Butterfly/NormalDistribution 七种技术；`RollingClickArray` 和 `CombatClickScheduler` 使用两个交替的 20 Tick 周期，支持单 Tick 多点击、每秒强制点击检查、原版空挥冷却及 `0..2` 未截断物品冷却阈值。
- **高级瞄准**：`RotationSmoothing`（Linear/EaseInOut/Factor/Instant），供 KillAura/CrystalAura 使用。

## 已重构机制

### 战斗链路

- `AttackSpeedSliderSetting` 使用 `System.nanoTime()`，不再按固定 20 TPS 递减计时。
- `CombatTargetUtils` 使用眼睛到实体 AABB 最近点距离，统一 FOV、过滤器、LOS、距离/角度/生命/受伤时间优先级和稳定排序。
- `EntityUtils.IS_ATTACKABLE` 自动过滤 AntiBot 检测到的假人。
- Hitboxes 仅扩展客户端世界中的非本地目标实体；单人集成服务器的玩家和生物碰撞箱保持原版尺寸，避免步行碰撞结果不一致导致减速与位置回弹。
- Hitboxes 与客户端连接方向判定已移入 `net.wurstclient.util` 普通策略类；Mixin 本体不再暴露包级静态辅助方法，Mixin 专用包也不再混入普通类，兼容 Forge 47.4.10 内置 Mixin 0.8.5 的方法可见性与包隔离校验。
- KillAura 已替换为 LiquidBounce Nextgen 的 Forge 1.20.1 行为等价执行链：3 格原版距离加成、随机扫描距离、交互/穿墙距离分离、HurtTime 与实体过滤、目标及自身运动预测、预测 AABB 命中点采样、Normal/Snap/OnTick 旋转时序、Enemy/All Raycast、逐 Tick 点击数组与未截断物品冷却、Click/Weapon/EmptyHand/VanillaName/NotBreaking 条件、库存模拟关闭、随机 FailSwing 范围、Basic/Interact/Fake 格挡模式、三种解锁方式、随机重挡/暂停、危险判定、KeepSprint、AutoSword/Criticals 协同和每次点击前复验。可关闭的 `Range aura` 使用当前主题色显示实际交互距离，距离设置变化会实时缩放渐变环带。
- CrystalAura 每 Tick 最多执行一次引爆或放置，按最低目标伤害、自伤上限、伤害优势、反自杀和水晶年龄统一评分；无效水晶不再阻塞后续放置。
- ScaffoldWalk 使用 `PlacementPlan` 预测水平落点，对目标位置、支撑面、视线、距离和连续性评分，再通过方块放置优先级旋转执行。
- AutoTotem 使用单次 SWAP 原子交换副手；AutoArmor 支持低耐久保护、鞘翅保持、绑定诅咒过滤和队列化换装。
- AnchorAura 使用 PLACE、CHARGE、DETONATE 单步状态机，等待服务端方块状态确认并按目标伤害、自伤上限和反自杀条件规划锚点。
- `DamageUtils` 区分末影水晶与重生锚爆炸威力，并统一计算难度、护甲韧性、爆炸保护和抗性效果减伤。
- MultiAura 已替换为 FDPClient `KillAura TargetMode=Multi` 的 Forge 1.20.1 行为等价链，并复用 LiquidBounce Nextgen 的双 20 Tick 点击器与未截断物品冷却：按 Type + 12 种 FDP 优先级选择主目标，支持随机攻击距离、扫描/穿墙/疾跑距离修正、目标与自身预测、Normal/Snap/OnTick、Normal/Strict Raytrace、RaycastIgnored、0=无限目标、逐点击世界顺序多目标复验、库存模拟关闭、FailSwing、KeepSprint，以及 Packet/Fake AutoBlock、Stop/Switch/Empty 解锁和 SmartBlock 条件。Packet AutoBlock 仅发送预测序列包并单独维护服务端格挡状态，不再进入客户端使用物品状态或阻塞移动输入；Killaura 与 MultiAura 均区分真实 `1..10` Tick 挥空冷却和 GUI 使用的 `missTime=10000` 哨兵，打开界面不会停止攻击。MultiAura 的 `Range aura` 显示 `Range + Scan range` 与穿墙距离中的真实最大攻击范围。
- ClickAura 统一长按与点击计时，并避免辅助攻击后继续执行同次原版攻击。
- TriggerBot 接入共享目标验证。
- NoVelocity 已替换为 LiquidBounce GPL 模式化实现，提供 Modify/JumpReset、概率与地面状态触发、移动/液体/鞘翅过滤、当前动量保留和负数反向击退；爆炸击退使用 Mixin accessor，不再依赖反射字段名。

本轮优先替换的核心模块：

| 模块 | 当前实现 |
| --- | --- |
| Killaura | LiquidBounce Nextgen Forge 1.20.1 行为等价移植：RollingClickArray、七种原始点击数组算法、ItemCooldown、随机扫描范围、预测命中点、三种旋转时序、Raycast、Requirements、库存模拟关闭、FailSwing、AutoBlock 状态机、KeepSprint 和逐点击复验；上游 Mace、1.21.4 sword blocking、ElytraTarget 及跨协议 Blink 分支在 1.20.1 无对应协议能力 |
| KillauraLegit | 共享目标评分与 AntiBot 过滤、客户端鼠标平滑转向、黏性目标和执行前复验 |
| AimAssist | 共享目标验证、水平/垂直轴控制、四种平滑曲线、目标切换优势阈值 |
| TriggerBot | 原版冷却阈值、武器限制、攻击键条件、使用物品策略和共享目标过滤 |
| Criticals | 攻击包发送前 Mixin 事件、六种 Packet profile、NoGround/Jump/MiniJump、状态与冷却检查、显式停止冲刺包 |
| Reach | 实体与方块距离分离，先按最大距离射线，再按命中类型裁剪结果 |
| NoVelocity | LiquidBounce Modify/JumpReset、概率与触发条件、动量保留、爆炸包 accessor；AntiKnockback 已合并为搜索别名 |
| AutoArmor | 确定性最大收益换装、耐久/绑定诅咒/鞘翅保护、共享库存动作队列 |
| AutoTotem | 单次 SWAP 副手交换、生命与吸收阈值、库存状态复验和动作队列 |
| AntiBot | 每 Tick 重算的组合谓词，覆盖 Tab 信息、游戏模式、延迟、地面、隐身、UUID、非法状态与重复名称 |

### 移动链路

九个常用移动模块已按 LiquidBounce 优先、FrogClient/Aristois 补充、Meteor 兜底的顺序重构，并统一适配 Forge 1.20.1 Mojmap：

| 模块 | 当前实现 |
| --- | --- |
| AutoSprint | 全方向疾跑、饥饿/碰撞/物品使用/潜行/失明条件控制；在原版疾跑判定完成、实际移动计算前使用本 Tick 输入应用状态，只停止模块自身开启的疾跑 |
| KeepSprint | 只保留攻击前已经存在的疾跑状态，不主动启动疾跑；攻击时在 `Player.attack` 内将原版水平速度倍率由 `0.6` 替换为 `1.0`，并阻止该次 `setSprinting(false)`，不再事后补发疾跑包；纯策略位于普通工具包，Mixin 内只保留私有注入方法 |
| SpeedHack | NCP Bhop、Strafe、LowHop、OnGround、Brutal 五种模式；共享八向速度规划、空中平滑转向与水平限速，LowHop 只限制自身跳跃阶段 |
| Flight | Vanilla、Boost、Rocket 三种模式；独立水平/垂直速度、滑翔、周期 AntiKick，跨世界恢复创造飞行状态且不干预旁观模式 |
| NoFall | OnGround、Position、Smart、GroundSpoof 四种模式；达到跌落阈值后才发包，并只在数据包确认发送且仍为落地状态后清零本地距离 |
| NoSlowdown | 按物品动作区分盾牌与普通使用，覆盖六类减速方块，并重算主手物品的负移动速度属性 |
| Step | Simple 一至五格步高与 Legit 分段位置包；保存/恢复原步高，并限制碰撞、液体、跳跃和执行冷却 |
| SafeWalk | 地面/跳跃条件控制、边缘裁剪和可选可见潜行，释放时恢复真实键盘状态 |
| ScaffoldWalk | 预测落点评分、视线与支撑面检查、最大方块堆选择、静默换槽恢复、交互结果确认、内置 SafeWalk 和 Tower 模式 |
| InvWalk | 统一识别 ClickGUI、Navigator、全部设置子页、HUD 编辑器和客户端选项页；这些页面不暂停单人集成服务器，并按真实键盘状态恢复移动、跳跃、潜行与疾跑输入 |

### ESP 与视觉

- `EntityEspRenderer` 统一实体插值、填充、描边、追踪线和颜色批处理。
- `EntitySnapshotManager` 每 Tick 在客户端线程复制并发布不可变实体、玩家、生物和掉落物列表；PlayerESP、MobESP、ItemESP 不再分别遍历世界实体集合。
- `PostEffectQueue` 按效果分组目标渲染任务，使用 Forge/Mojmap `TextureTarget + PostChain` 处理后以 Alpha 合成回主目标，不依赖 Satin。
- `TargetShader` 提供 Outline、Pulse、Gradient、Smoke 四种目标视觉模式，并支持是否穿墙。
- PlayerESP 支持 3D 批处理与 2D 八角 AABB 屏幕投影；2D 模式统一批次绘制边框、填充、生命条和平均护甲耐久条。
- PlayerHalo 使用实体快照批量绘制主题色头顶光环，第一人称隐藏自身、第三人称显示自身，并保持正常深度遮挡。
- MobESP 支持距离、生命值、自定义颜色、最大距离、透明度、近距离平滑淡出及深度模式。
- NameTags 每 Tick 提取玩家名称、生命、延迟、实体 ID、装备副本和耐久为不可变 `NameTagRenderState`，渲染阶段直接使用快照并可绘制装备与耐久。
- ItemESP 支持最大距离、无效实体过滤、近距离平滑淡出、填充和深度模式。
- ChestESP 和 PortalESP 支持填充/线条透明度及穿墙切换。
- PortalESP 已修复关闭一个分组后错误终止后续分组渲染的问题。
- ClickGUI 使用 `FlatTheme`、`FlatRenderer` 和 `FlatUiRenderer` 绘制实心面板、控件、滑轨、圆角与阴影。
- Radar 作为 ClickGUI 的持久固定窗口注册；启用后可在游戏 HUD 中显示，并在右 Ctrl/右 Shift 菜单内复用同一窗口。内容区使用与 ClickGUI 一致的深黑底、主题色描边与分隔线、圆形扫描环、主题中心指针、圆角实体标记及底部半径/目标计数栏；标题栏固定按钮使用内嵌 SVG 源生成的抗锯齿图钉纹理，固定状态跟随主题色，菜单重建不会再丢失雷达。
- Minecraft 主界面在原版动态全景背景上使用 Raven 式深色中性视觉。
- 两套 GUI 入口：右 Ctrl → BleachHack 式浮窗 ClickGUI，右 Shift → Sinka 风格 Navigator；二者作为瞬时菜单入口不参与普通模块启停通知，打开时只显示一条 `Info` 通知。

### 性能优化

- 高频事件派发使用监听器变更时快照（COW）+ `LongAdder` 计数。
- `RenderScope` 为每个 3D/GUI 监听器恢复 blend、depth、cull、line width、shader、framebuffer、viewport 和共享 `BufferBuilder` 状态，隔离异常渲染污染。
- LambdaMetafactory 注解订阅消除反射扫描开销。
- GUI 功能集合和单行说明按注册数量缓存。
- ClickGUI 与 Navigator 共用相关度模糊搜索：支持英文名、中文显示名、标签、描述和设置名，兼容 CamelCase、缩写、子序列及常见拼写交换；短关键词不启用宽松纠错，多关键词必须全部命中。
- `EntityCulling` 使用跨帧 `GL_SAMPLES_PASSED` 查询，仅读取已就绪结果；查询包围盒复用单个静态 VBO。
- `AsyncTextureLoader` 提供有界后台文件解码、渲染线程纹理注册和每线程直接缓冲；当前保留为文件纹理 API，不宣称替代 Minecraft 的通用资源加载管线。
- Search、Tunneller、MobSpawnESP 和实体遮挡包围盒均复用静态 VBO。
- Forge/Mojmap 1.20.1 的 `setLevel`/`clearLevel` 不包含 `System.gc()`；因此没有添加无效且可能破坏紧急内存恢复路径的 GC Mixin。
- 已删除 GUI 的 Dual-Kawase/液态玻璃管线；`TargetShader` 只按实际使用的效果懒加载并复用独立 RenderTarget。
- 圆角填充继续使用浮点圆弧与透明羽化边缘。

## Forge 1.20.1 根工程架构

1. ForgeGradle 6.0 和 Forge 47.4.10 提供构建、开发运行配置与重混淆任务。
2. 全部 Minecraft 源码符号使用 Mojang 1.20.1 官方映射。
3. `WurstForgeInitializer` 接入 `FMLClientSetupEvent`、`RegisterClientCommandsEvent` 和 `RenderGuiEvent.Post`；攻击事件由 `ClientPlayerInteractionManagerMixin` 在攻击包发送前派发。
4. `META-INF/accesstransformer.cfg` 提供 32 条 Forge 原生访问转换。
5. MixinGradle 生成 `wurstpenguin-refmap.json`，MixinExtras Forge 通过 JarJar 打包。
6. `PlatformUtils` 直接调用 Forge `ModList` 和 `FMLLoader`，不包含跨加载器反射。
7. `ClientConnectionMixin` 仅为接收方向为 `CLIENTBOUND` 的客户端连接派发 Wurst 收发包事件；单人集成服务器的 `SERVERBOUND` 连接不会进入客户端模块事件链。

## 构建与运行

### Forge 1.20.1
根目录 1.20.1 工程需要 Java 17。项目自带 Gradle 8.11 Wrapper，不依赖系统 Gradle：

```powershell
.\gradlew.bat clean jarJar --console=plain
```

可部署 Forge JarJar 产物位于：

```text
build/libs/WurstB+ Plus-v1.6.0-Forge-1.20.1.jar
```

启动 Forge 1.20.1 开发客户端：

```powershell
.\gradlew.bat runClient --console=plain
```

跑单元测试（可完全离线；**必须用 Java 17**——wrapper 是 Gradle 8.11，用 JDK 25 会在配置阶段就以
`Could not create task ':test' > Type T not present` 失败）：

```powershell
.\gradlew.bat test --offline --console=plain
```

> **首次 `runClient` 必须联网**：ForgeGradle 要拉取 Minecraft 资源与 `commons-io` 等原版库。
> `compileJava` / `test` 能离线跑，但 `runClient --offline` 会失败在 `:minecraftLibraryCopy`
> （`commons-io:commons-io:2.6` 不在离线缓存里）。
>
> `jarJar` 结束后会自动把产物复制到本地测试实例的 `mods/`
> （`.test/versions/1.20.1-Forge_47.4.22/mods/`，见 `build.gradle` 的 `copyJarToTestMods`），
> 并清掉该目录里旧的 WurstB+ jar，避免 Forge 重复加载。

### NeoForge 1.20.1

`neoforge/` 目录为 NeoForge 1.20.1 分支，使用 ForgeGradle 6.x + MixinGradle 0.7 + Mixin 0.8.7，需要 Java 17：

```powershell
cd neoforge
..\gradlew.bat -p . clean jarJar --console=plain
```

可部署 NeoForge JarJar 产物位于：

```text
neoforge/build/libs/WurstB+ Plus-v1.5.0-NeoForge-1.20.1.jar
```

### Forge 1.21.1

1.21.1 工程需要 Java 21，在独立目录中构建和运行：

```powershell
cd versions\1.21.1
..\gradlew.bat -p . clean jarJar --console=plain
..\gradlew.bat -p . runClient --console=plain
```

1.21.1 可部署 Forge JarJar 产物位于：

```text
versions/1.21.1/build/libs/WurstB+ Plus-v1.5.0-Forge-1.21.1.jar
```

### NeoForge 1.21.1

`neoforge/versions/1.21.1/` 目录为 NeoForge 1.21.1 分支，使用 ModDevGradle `net.neoforged.moddev` 2.0.143，需要 Java 21：

```powershell
cd neoforge\versions\1.21.1
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
..\..\..\gradlew.bat -p . clean build --console=plain
```

可部署 NeoForge 产物位于（已内嵌 baritone jarJar）：

```text
neoforge/versions/1.21.1/build/libs/WurstB+ Plus-v1.5.0-NeoForge-1.21.1.jar
```

SHA-256: `3666C972CCCBBE95303DEF35CA63CA505DF3FDFEEADC8CAEABB306403A86547C`

### Fabric 1.20.1、1.21.1、1.21.11、26.1.2 和 26.2

Fabric 版本使用独立的 Fabric Loom 工程。1.20.1 使用 Java 17，1.21.1/1.21.11 使用 Java 21，26.1.2/26.2 使用 Java 25。五个版本均将匹配的 Fabric API、MixinExtras、WebSocket、代理 Netty 和 Baritone 依赖打入发布包。

```powershell
cd fabric
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
..\gradlew.bat -p . build --console=plain

cd versions\1.21.1
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
..\..\..\gradlew.bat -p . build --console=plain

cd ..\1.21.11
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
..\..\..\gradlew.bat -p . clean build --console=plain

cd ..\26.1.2
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.4"
..\..\..\gradlew.bat -p . build --console=plain

cd ..\26.2
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.4"
..\..\..\gradlew.bat -p . clean build --console=plain
```

Fabric 发布包由各 Fabric 工程自行输出到各自的 `build/libs/` 目录：

```text
fabric/build/libs/WurstB+ Plus-1.5.0-Fabric-1.20.1.jar
fabric/versions/1.21.1/build/libs/WurstB+ Plus-1.5.0-Fabric-1.21.1.jar
fabric/versions/1.21.11/build/libs/WurstB+ Plus-1.5.0-Fabric-1.21.11.jar
fabric/versions/26.1.2/build/libs/WurstB+ Plus-1.5.0-Fabric-26.1.2.jar
fabric/versions/26.2/build/libs/WurstB+ Plus-1.5.0-Fabric-26.2.jar
```

Fabric 不加载 Forge Baritone JAR，也不会触发 NeoForge 的 `baritone.api.forge` 模块读取错误。26.1.2 Fabric 发布包通过 Loom `include` 内嵌 `baritone-api-fabric-1.18.0.jar`。

### Forge 26.1.2

`versions/26.1.2/` 目录为 Minecraft 26.1.2 / Forge 64.1.0 分支，需要 Java 25（JDK 25 SSL 问题需设置 `_JAVA_OPTIONS`）：

```powershell
cd versions\26.1.2
$env:_JAVA_OPTIONS = "-Djavax.net.ssl.trustStoreType=Windows-ROOT"
..\gradlew.bat -p . allJar --console=plain
..\gradlew.bat -p . runClient --console=plain
```

可部署产物：

```text
versions/26.1.2/build/libs/WurstB+ Plus-v1.5.0-Forge-26.1.2.jar
```

> 26.1.2 使用 ForgeGradle 7.x、Gradle 9.4.1、Mojang 官方映射，内置 Mixin 0.8.7 + MixinExtras。Baritone 1.18.0 的类文件直接合并进主模块；netty-codec-socks/netty-handler-proxy 仅合并其自身包（版本对齐 MC 自带的 netty 4.2.7），不再把整个 netty 传递链展开进主 jar，避免模块包冲突。
> 渲染管线从 `render()` 迁移到 `extractRenderState()`，文字颜色需 8 位 Alpha（`0xFFxxxxxx`），`blit` 需 `RenderPipelines` 参数。`BufferUploader` 已移除，三角扇批量渲染暂不可用。更新日志见 `versions/26.1.2/CHANGELOG.md`，迁移详情见 `versions/26.1.2/PORTING_TASK.md`。

### NeoForge 26.1.2

`neoforge/versions/26.1.2/` 目录为 Minecraft 26.1.2 / NeoForge 26.1.2.87 分支，使用 ModDevGradle 2.0.143、Gradle 9.4.1 和 Java 25：

```powershell
cd neoforge\versions\26.1.2
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.4"
$env:_JAVA_OPTIONS = "-Djavax.net.ssl.trustStoreType=Windows-ROOT"
..\..\..\gradlew.bat -p . clean build --console=plain
```

可部署的全依赖产物位于：

```text
neoforge/versions/26.1.2/build/libs/WurstB+ Plus-v1.5.0-NeoForge-26.1.2.jar
```

SHA-256: `2C92F68247C79C387191DD1659777092C1D47E99319D0AAE0DF576E741BC6CAA`

该产物通过 NeoForge JarJar 内嵌 Baritone 1.18.0、Java-WebSocket 和所需 Netty 组件；Baritone 保持独立 NeoForge 模组元数据与 Mixin Connector，不会触发旧 Forge Baritone 包的模块读取错误。

### 1.21.11 和 26.2 新版本工程

六个新工程统一由批量脚本构建，避免手工使用错误 JDK、漏跑 Baritone 兼容补丁或生成缺少主类的 Forge 包：

```powershell
# 1.21.11 三加载器
powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1 `
  -Version 1.21.11 -Clean -PublishToDownload

# 26.2 三加载器
powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1 `
  -Version 26.2 -Clean -PublishToDownload
```

Forge 1.21.11/26.2 使用 `allJar`，NeoForge 与 Fabric 使用 `build`。所有六个最终 JAR 均包含 Wurst 核心类和独立的内嵌 Baritone JAR，不需要额外安装 Baritone。

## 多版本平行移植

已发布矩阵之外，工作区为 **1.20.2 / 1.20.3 / 1.20.4 / 1.20.5 / 1.20.6 / 1.21 / 1.21.2 / 1.21.3 / 1.21.4 / 1.21.5 / 1.21.6 / 1.21.7 / 1.21.8 / 1.21.9 / 1.21.10 / 26.1 / 26.1.1** 建好了加载器工程，共 **46 个**（1.20.5 与 1.21.2 无官方 Forge，只有 NeoForge / Fabric）。做法是只从最近的 v1.5 工程拷贝源码，再按目标 MC API 修编译，**不引入根工程的 v1.6 GUI/音乐/Skia 子系统**。

### 编译状态：46 / 46 通过，打包状态：46 / 46 通过

早前的完整验收（61 个工程目录、`compileJava`，有测试源码的工程加跑 `compileTestJava`）：**0 失败**。验收方式为避免 `clean` 抹掉构建中间产物的增量编译；逐工程日志与 `BUILD SUCCESSFUL` 原文留存在本地 `tmp-recon/acceptance/`。

随后一轮是**打包验收**：61 个工程目录各自跑生产 jar 任务，**0 失败**，产物上传 v1.5.0 Release。打包必须**联网**（`--offline` 会因 `com.mojang:jtracy` 等依赖无本地缓存而失败），且不能先 `clean`。

| MC 版本 | Forge | NeoForge | Fabric |
| --- | --- | --- | --- |
| 1.20.2 / 1.20.3 / 1.20.4 | ✅ | ✅ | ✅ |
| 1.20.5 | 无官方包 | ✅ | ✅ |
| 1.20.6 | ✅ | ✅ | ✅ |
| 1.21 | ✅ | ✅ | ✅ |
| 1.21.1 | ✅ | ✅ | ✅ |
| 1.21.2 | 无官方包 | ✅ | ✅ |
| 1.21.3 / 1.21.4 | ✅ | ✅ | ✅ |
| 1.21.5 | ✅ | ✅ | ✅ |
| 1.21.6 / 1.21.7 / 1.21.8 | ✅ | ✅ | ✅ |
| 1.21.9 / 1.21.10 | ✅ | ✅ | ✅ |
| 26.1 / 26.1.1 | ✅ | ✅ | ✅ |

产出的类文件数（`compileJava` 产物，不含依赖）：Forge 993–1065、Fabric 994–1053、NeoForge 1036–1052（NeoForge 1.20.x 的 `build/` 另含整套反编译 MC 的 class，故目录总数上万）。

### 打包阶段修掉的构建阻塞

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| Forge 1.21.3–1.21.11 报 `Task 'jarJar' not found` | 这一代工程不再用 JarJar 插件，而是自己注册 `allJar` | 按 `build.gradle` 动态选择任务：1.20.2–1.21.1 用 `jarJar`，1.21.3+ 用 `allJar` |
| Fabric 26.x 报 `Task 'remapJar' not found` | MC 26.x 不再混淆，Looms 不生成 remap 任务 | 26.x 改用普通 `jar` |
| NeoForge 1.20.2 `processResources` 报 `neoforge.mods.toml is a duplicate` | `build.gradle` 把 `src/main/templates` 也加进资源源集，而 `META-INF/neoforge.mods.toml` 在 `resources` 与 `templates` 各存一份 | 删除 `src/main/resources/META-INF/neoforge.mods.toml` |
| 9 个 NeoForge 工程的 jar 名带错 MC 版本（1.21.5 打出 `...-NeoForge-1.21.11.jar`，26.1.1 打出 `...-26.1.2.jar`） | 从克隆源带过来的硬编码 `archiveFileName` 没改；26.1.2 与 26.2 因此输出同名互相覆盖 | 删掉这些覆盖，回落到默认 `<archivesName>-<project.version>` |
| `versions/1.20.6` 配置 `jarJar` 即失败：`Task with path 'reobfJarJar' not found` | 该工程声明了不存在的 finalizer | 删掉 `finalizedBy "reobfJarJar"` |
| Forge 1.21.3 配置阶段失败：`Failed to download .../server.jar`（`Connection reset`） | Mojang 下载瞬时中断 | 重试即通过 |

### 本轮完成的关键工程与做法

| 工程 | 做法 | 结果 |
| --- | --- | --- |
| Forge 1.21.7 | 用 NeoForge 1.21.7 覆盖 + 从 Forge 1.21.6 还原 29 个 loader 文件 | 编译+测试通过，995 类 |
| Forge 1.21.5 | 用 NeoForge 1.21.5 覆盖 + 从 Forge 1.21.4 还原 glue | 84 → 47 → 7 → 4 → 0 错 |
| NeoForge 1.21.5 | 用 Fabric 1.21.5 覆盖 + 还原 NeoForge glue | 135 → 28 → 0 错，135 用例全绿 |
| Forge 1.21.4 / 1.21.3 | 用 Fabric 1.21.4 覆盖 + 手写 Forge mixin，改正 `ClientInput.tick` 签名差异 | 编译+测试通过 |
| NeoForge 1.20.5 / 1.20.6 | 反向还原到 Fabric 1.20.6 + 改 Java 21 toolchain | 编译通过，994 类 |
| Forge 1.20.6 | 以 Forge 1.20.4 源码为底补 1.20.5/1.20.6 API delta | 编译通过 |

### 移植要点（踩过的坑）

1. **同 MC 版本的兄弟加载器可直接互相移植。** 同一 MC 版本下 Forge/NeoForge/Fabric 的源码树差别几乎只在 loader glue 上（Forge 1.21.6 与 1.21.7 只差 `mods.toml`；Forge 与 NeoForge 1.21.7 只差 3 个文件）。把已通过的同版本兄弟工程 `src/main` 整体覆盖、再还原 loader 专属文件，比手改 API 快一个数量级。
2. **供体必须选对"代"。** 1.21.5 的 loader glue 若从 1.21.6/1.21.7 搬，会带进该代才有的 `GuiGraphicsExtractor`、`SubmitNodeStorage` 和 Forge 56 event bus，反而从 0 错变成 42 错。"同 MC 版本的另一个加载器" > "同加载器的邻近版本"。
3. **1.20.5 / 1.20.6 的 API 贴近 1.20.4**（`Tesselator.getBuilder()` + `begin(mode,format)`、`BufferBuilder.RenderedBuffer`、`ResourceLocation.tryParse/tryBuild`、普通 `Enchantment`）。历史上这两个树被"过度移植"成 1.21 风格，需要反向还原。
4. **1.20.5 / 1.20.6 必须用 JDK 21**，JDK 17 会在 `neoFormRecompile` 编译 MC 自身源码时死在 `List.getFirst()` / `List.reversed()` / `MatchException`。
5. **Forge 侧访问私有成员优先用 accessor mixin**；FG7 的 `accesstransformer.cfg` 只在 `minecraft { accessTransformer = true }` 且 AT 使用 official（mojmap）名字时生效。
6. **`AbstractSelectionList$Entry#render` 参数顺序是 `(GuiGraphics, index, Y, X, ...)`——Y 在 X 前**。历史上有一版树写反了，因为全是 `int` 所以 javac 无法发现，列表渲染整体错位。
7. **`gradlew clean` 会删掉 Loom/NeoGradle 放在 `build/` 的中间产物**，离线重跑必失败；NeoForge 1.20.2–1.20.6 的 MC 产物没有本地缓存，必须联网跑。

### 尚未完成的部分

- **没有任何游戏内运行验证**（1.21.11 / 26.2 的六个工程除外）：未启动客户端、未做 Mixin 应用校验。已知的运行时债务（Mixin 目标描述符不匹配、部分 1.21.6–1.21.8 姓名牌渲染调整失效、1.21.5 的 `PostEffectQueue`/`LsdHack` 降级等）逐条记在 [docs/PORTING-NEW-VERSIONS.md](PORTING-NEW-VERSIONS.md) 的「剩余工作」。
- v1.6 子系统（GUI / 音乐 / Skia / 周界挖掘 / 种子矿透）在这 46 个工程中均未移植。
- `scripts/build-all.ps1` / `run-version-tests.ps1` 的工程表仍只覆盖已发布的 15 个工程；新版本工程的批量打包目前靠 `tmp-recon/build-v1.5-release.py`（未纳入仓库，逻辑见[本地构建路径](#本地构建路径)）。

> `scripts/common.ps1` 的工程表与 `scripts/build-all.ps1` / `scripts/run-version-tests.ps1` 的过滤范围目前仍只覆盖已发布的 15 个工程，新版本工程需要手工执行 Gradle 任务。

## 批量构建与版本启动测试

### 一键构建 15 个发布产物

`scripts/build-all.ps1` 依次构建 15 个原发布版本（5 个 MC 版本 × Forge/NeoForge/Fabric），每个工程的产物输出到各自的 `build/libs/`。61 个工程的 v1.5.0 全量打包已单独完成并上传 Release，走的是工作区 `tmp-recon/build-v1.5-release.py`（仓库外，不随仓库分发）；新版本工程的批量构建因此仍需手工执行各自的生产任务。

```powershell
# 全量构建
powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1

# 只构建某个 MC 版本
powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1 -Version 1.21.1

# 只构建某个加载器
powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1 -Loader NeoForge

# 先 clean 再构建（-Clean）；跳过匹配工程（-Skip 26.1.2）
powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1 -Clean -Skip 26.1.2

# 构建后同步到 download/
powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1 -Version 26.2 -Clean -PublishToDownload
```

脚本自动完成：按 MC 版本选择 JDK（17/21/25；扫描 Microsoft / Temurin / Corretto 安装目录，也可用 `WURSTBPLUS_JAVA17/21/25` 覆盖）、使用 Windows 根证书库兼容本地 TLS 代理、在 26.2 构建前重建 Baritone 兼容包、以 `--no-daemon` 构建，并校验核心类（根工程额外校验 v1.6 音乐/Skia 条目）、内嵌 Baritone API、26.2 兼容类及 Mixin Manifest。离线构建加 `-Offline`。报告保存到 `.test/report-build-<时间戳>.txt`。

产物清单：

| 工程 | 任务 | 产物 |
| --- | --- | --- |
| 根目录 (Forge 1.20.1) | `jarJar test` | `build/libs/WurstB+ Plus-v1.6.0-Forge-1.20.1.jar` |
| `versions/1.21.1` | `jarJar` | `build/libs/WurstB+ Plus-v1.5.0-Forge-1.21.1.jar` |
| `versions/1.21.11` | `allJar test` | `build/libs/WurstB+ Plus-v1.5.0-Forge-1.21.11.jar` |
| `versions/26.1.2` | `allJar` | `build/libs/WurstB+ Plus-v1.5.0-Forge-26.1.2.jar` |
| `versions/26.2` | `allJar test` | `build/libs/WurstB+ Plus-v1.5.0-Forge-26.2.jar` |
| `neoforge/` | `jarJar` | `build/libs/WurstB+ Plus-v1.5.0-NeoForge-1.20.1.jar` |
| `neoforge/versions/1.21.1` | `jar` | `build/libs/WurstB+ Plus-v1.5.0-NeoForge-1.21.1.jar` |
| `neoforge/versions/1.21.11` | `build` | `build/libs/WurstB+ Plus-v1.5.0-NeoForge-1.21.11.jar` |
| `neoforge/versions/26.1.2` | `jar` | `build/libs/WurstB+ Plus-v1.5.0-NeoForge-26.1.2.jar` |
| `neoforge/versions/26.2` | `build` | `build/libs/WurstB+ Plus-v1.5.0-NeoForge-26.2.jar` |
| `fabric/` | `build -x test` | `build/libs/WurstB+ Plus-1.5.0-Fabric-1.20.1.jar` |
| `fabric/versions/1.21.1` | `build -x test` | `build/libs/WurstB+ Plus-1.5.0-Fabric-1.21.1.jar` |
| `fabric/versions/1.21.11` | `build` | `build/libs/WurstB+ Plus-1.5.0-Fabric-1.21.11.jar` |
| `fabric/versions/26.1.2` | `build -x test` | `build/libs/WurstB+ Plus-1.5.0-Fabric-26.1.2.jar` |
| `fabric/versions/26.2` | `build` | `build/libs/WurstB+ Plus-1.5.0-Fabric-26.2.jar` |

### 批量版本启动测试

`scripts/run-version-tests.ps1` 批量把发布 JAR（5 个 MC 版本 × Fabric/Forge/NeoForge）部署到 `.test/versions/` 下对应实例，逐个真实启动游戏。除主菜单测试外，还可通过 Quick Play 进入指定单人世界并发送 Baritone 命令，验证 Wurst、Baritone、世界加载和命令响应。脚本默认以仓库根目录作为路径基准。

### 前置条件

- `.test/` 是 PCL 启动器格式的测试环境，包含 `assets/`、`libraries/` 和 `versions/`；
- 每个版本实例（如 `1.20.1-Forge_47.4.22/`）内含 `<版本名>.json`（Mojang 格式版本描述，已含完整启动参数与 FML 参数）和 `<版本名>.jar`（客户端）；
- 待测 JAR 与实例按“加载器 + MC 版本”自动匹配（例如 `WurstB+ Plus-v1.5.0-NeoForge-1.21.1.jar` → `1.21.1-NeoForge_21.1.248`）；默认从 `download/` 读取（Forge/NeoForge 聚合目录，由 `build-all.ps1 -PublishToDownload` 按需创建），Fabric 产物可用 `-DownloadDir` 指向对应的 `build/libs`（或先复制到 `download/`）；
- JDK 按 MC 版本自动选择：1.20.1 → `jdk-17`，1.21.1/1.21.11 → `jdk-21`，26.1.2/26.2 → `jdk-25.0.4`（可通过 `WURSTBPLUS_JAVA17/21/25` 覆盖）。

### 用法

```powershell
# 全量测试 15 个版本
powershell -ExecutionPolicy Bypass -File scripts\run-version-tests.ps1

# 只测某个 MC 版本
powershell -ExecutionPolicy Bypass -File scripts\run-version-tests.ps1 -Version 1.21.1

# 只测某个加载器
powershell -ExecutionPolicy Bypass -File scripts\run-version-tests.ps1 -Loader NeoForge

# 组合过滤 + 自定义超时（秒）
powershell -ExecutionPolicy Bypass -File scripts\run-version-tests.ps1 -Version 26.1.2 -Loader Fabric -TimeoutSeconds 300

# 进入单人世界并验证 Baritone 命令
powershell -ExecutionPolicy Bypass -File scripts\run-version-tests.ps1 -Version 26.2 `
  -QuickPlayWorld WurstSmokeFresh -BaritoneCommand '#goto 0 88 0'
```

### 参数

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `-VersionsRoot` | `.test\versions` | 测试实例根目录 |
| `-DownloadDir` | `download` | 待测 JAR 目录 |
| `-LibrariesDir` | `.test\libraries` | 依赖库目录 |
| `-AssetsDir` | `.test\assets` | 游戏资源目录 |
| `-ProjectRoot` | 自动检测 | 手动指定仓库根目录 |
| `-TimeoutSeconds` | 240 | 单个版本的最长等待秒数 |
| `-SettleSeconds` | 20 | 出现启动标志后还需稳定运行的秒数 |
| `-Version` | 空 | 过滤 MC 版本（1.20.1 / 1.21.1 / 1.21.11 / 26.1.2 / 26.2） |
| `-Loader` | 空 | 过滤加载器（Fabric / Forge / NeoForge） |
| `-QuickPlayWorld` | 空 | 启动后直接进入指定单人世界 |
| `-BaritoneCommand` | 空 | 进入世界后发送命令并验证预期响应 |
| `-KeepOldJars` | 关 | 保留旧 WurstB JAR（可能导致实例同时加载多个版本） |
| `-Quiet` | 关 | 不输出过程信息 |

### 判定逻辑

- **PASS**：主菜单模式要求 Wurst 与所需 Baritone 初始化并稳定运行；Quick Play 模式还要求玩家加入世界，设置命令时必须出现对应 Baritone 响应；
- **FAIL**：进程提前退出（非零退出码）、`crash-reports/` 出现新报告（附崩溃摘要）、或日志出现 `FATAL` / `Mod Loading has failed`；
- **TIMEOUT**：超时未完成启动标志与窗口检查（进程会被强制结束）；
- **ERROR**：实例缺失、客户端 jar 缺失、依赖缺失或 natives 提取失败（不启动）。

### 输出

- 控制台实时进度与汇总表（PASS 绿色 / FAIL 红色 / TIMEOUT 黄色）；
- `.test/report-<时间戳>.txt` 详细报告（每个版本的 JAR、加载器、状态、耗时、备注）；
- 每个实例的游戏输出保存在 `.test/out/<实例名>/stdout.log`、`stderr.log`，游戏日志在实例 `logs/latest.log`。

### 脚本行为说明

- 自动解析版本 JSON：按启动器的“最后一个匹配规则”语义过滤依赖与参数、展开 `${...}` 变量，按 `natives` classifier 提取 natives 到 `.test/natives/<实例名>/`；
- 自动修复 JPMS 启动参数：module path（`-p`）上的 JAR 会从 `-cp` 中剔除，classpath 去重，避免 `bootstraplauncher already on module path` 与 `Duplicate key` 崩溃；
- Quick Play 命令通过 GLFW 窗口消息投递，Windows 拒绝前台焦点时仍可在后台完成自动输入；
- 默认部署前把实例 `mods/` 中已有的 WurstB JAR 备份为 `.backup-<时间戳>.jar.disabled`；启用 `-KeepOldJars` 时不改动旧文件；
- 启动前只清理脚本自己记录且命令行确实指向该实例的 PID，不扫描或终止其他 Minecraft 进程；
- 窗口以最小化方式启动（854×480），测试结束后进程被强制结束。

### 辅助脚本

`scripts/doctor.ps1` 做工具链体检：列出本机可用的 JDK、按 MC 版本解析 `JAVA_HOME`、检查四种 Gradle wrapper 发行版是否已播种、校验工程 wrapper jar 是否完整，以及 v1.6 基准测试是否齐全。任一项缺失时以非零码退出：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\doctor.ps1
```

`scripts/seed-gradle-wrapper.ps1` 从工作区 `tools/` 把 Gradle 发行版与解压目录播种进 `~/.gradle/wrapper/dists/`，用于离线环境：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\seed-gradle-wrapper.ps1
```

`scripts/run-unit-tests.ps1` 用 JDK 17 跑根工程单测，可只跑单个测试类：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-unit-tests.ps1 -Offline
powershell -ExecutionPolicy Bypass -File scripts\run-unit-tests.ps1 -Class net.wurstclient.music.NeteaseCloudApiTest
```

`scripts/patch-baritone-26.2.ps1` 为 26.2 重建 Baritone 渲染 API 兼容包，由 `build-all.ps1` 在构建 26.2 前自动调用。

`scripts/replace-jar-entry.ps1` 用于在不重建整个 JAR 的情况下替换其中的单个条目（例如修改 mixins JSON 后直接更新发布包）：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\replace-jar-entry.ps1 `
  -JarPath "fabric\versions\26.1.2\build\libs\WurstB+ Plus-1.5.0-Fabric-26.1.2.jar" `
  -EntryToReplace "wurstpenguin.mixins.json" `
  -ContentFile "C:\path\to\new\wurstpenguin.mixins.json"
```

> 注意：多行内容必须通过 `-ContentFile` 从文件读取，不要用命令行参数直接传递（会被换行截断）。
> 默认会先创建带时间戳的 `.bak` 备份；确认不需要备份时才使用 `-NoBackup`。

`scripts/upgrade-lwjgl.ps1` 用于升级指定实例的 LWJGL native DLL。目标目录必须显式传入，脚本会校验 Maven Central 的 SHA-256/SHA-1，并在覆盖前创建备份：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\upgrade-lwjgl.ps1 `
  -NativesDir "D:\.penguin\.minecraft\versions\1.20.1-Forge_47.4.10\1.20.1-Forge_47.4.10-natives"
```

`scripts/ccswitch-guardian.ps1` 默认只监控本地端口并按配置尝试启动 CC Switch，不会自动覆盖用户级 `OPENAI_BASE_URL`。需要使用本地代理时显式添加 `-UseLocalProxy`；需要持久化环境变量时再添加 `-PersistUserEnvironment`。安装登录自启动任务使用 `scripts\install-ccswitch-guardian.ps1`，更新已有任务必须显式传入 `-Replace`。

## 安全提示

- 早期导入来源中的 `WurstForge-1.20.1.jar` 与 `WurstForge-Decompiled/` 内含 HWID、MAC 地址和 Webhook 代码；相关代码已从活动源码中完全移除，这两个文件在当前工作区中**均已不存在**。
- 同为早期导入来源的 `Client.zip`，其 `Launcher.bat` 会执行 `compiler.exe conf.txt`。在完成二进制审计和 Lua 解混淆前不应执行其中的 EXE；该文件在当前工作区中**同样不存在**。
- 若日后重新导入以上来源，请先核对哈希与来源，并继续保持以上限制。

## 验证状态

**原发布矩阵（15 个工程）**

版本分布为 **根目录 Forge 1.20.1 = v1.6.0**，其余 14 个工程 = **v1.5.0**。

根目录 v1.6.0（本轮实际验证）：

- `gradlew.bat compileJava`：通过
- `gradlew.bat test`：通过，112 个测试类 / 545 项测试 / 0 失败
- 产物 `build/libs/WurstB+ Plus-v1.6.0-Forge-1.20.1.jar`（68.1 MB）：含全部 v1.6 子系统、`assets/wurst/skiko/` 原生库（`skiko-windows-x64.dll` 16.51 MB + `icudtl.dat` 9.98 MB）、`META-INF/jarjar/metadata.json` 记录的 17 个内嵌 jarJar 依赖
- **未做游戏内运行验证**：v1.6 的 GUI / 音乐 / Skia 子系统与 12 个新 Hack 只经过编译与单元测试，尚未进入世界实测

其余 14 个 v1.5.0 工程：

- 本轮**全部重新打包**并上传 v1.5.0 Release（1.21.11 / 26.2 的六个此前已有 `build/libs/` 产物，其余为新建产物）
- 1.21.11 与 26.2 六个版本完成过 clean 构建、核心类检查、内嵌 Baritone 检查、真实客户端启动、单人世界加载及 `#goto 0 88 0` 命令验证
- 上述启动验证结论来自工作区导入前的记录，本轮**未重新复现**；1.20.1 的三个 jar 保留发布当时产物

任一结论都不代表所有战斗、移动、GUI 和 HUD 功能均已穷举测试。

**新版本矩阵（46 个工程）**

- **46 / 46 个工程 `compileJava` 通过**；其中 34 个带测试源码的工程 `compileTestJava` 也通过，`neoforge/versions/1.21.5` 另跑通 JUnit（51 测试类 / 135 用例 / 0 失败）
- **46 / 46 个工程打包通过**，jar 已上传 v1.5.0 Release；打包必须联网（`--offline` 会因部分 MC 依赖无本地缓存而失败），NeoForge 1.20.2–1.20.6 另需联网完成 MC 产物解压/反编译
- 46 个新版本工程 + 14 个非根目录的原发布工程 = **60 个 jar 本轮重建**（根目录 Forge 1.20.1 的 v1.5 产物无法重建，沿用发布当时的那一个）；**没有游戏内启动验证**，也未移植 v1.6 子系统；运行时债务逐条见 [docs/PORTING-NEW-VERSIONS.md](PORTING-NEW-VERSIONS.md)

打包校验口径（`tmp-recon/validate-jars.py`，61/61 通过）：zip 完好、含加载器元数据（`mods.toml` / `neoforge.mods.toml` / `fabric.mod.json`）、含 Mixin 配置（Forge/NeoForge 为 `wurst.mixins.json`，Fabric 为 `wurstpenguin.mixins.json`）、含 `net/wurstclient/WurstClient.class`、条目数 ≥ 200。

发布产物清单：

- 1.20.1 Forge 47.4.10：`build/libs/WurstB+ Plus-v1.6.0-Forge-1.20.1.jar`
- 1.20.1 NeoForge 47.1.3：`neoforge/build/libs/WurstB+ Plus-v1.5.0-NeoForge-1.20.1.jar`
- 1.21.1 Forge 52.1.16：`versions/1.21.1/build/libs/WurstB+ Plus-v1.5.0-Forge-1.21.1.jar`
- 1.21.1 NeoForge 21.1.244：`neoforge/versions/1.21.1/build/libs/WurstB+ Plus-v1.5.0-NeoForge-1.21.1.jar`
- 1.21.11 Forge 61.2.0：`versions/1.21.11/build/libs/WurstB+ Plus-v1.5.0-Forge-1.21.11.jar`
- 1.21.11 NeoForge 21.11.45：`neoforge/versions/1.21.11/build/libs/WurstB+ Plus-v1.5.0-NeoForge-1.21.11.jar`
- 26.1.2 Forge 64.1.0：`versions/26.1.2/build/libs/WurstB+ Plus-v1.5.0-Forge-26.1.2.jar`（文字颜色、图标渲染、圆角抗锯齿、Mixin 启动崩溃已修复）
- 26.1.2 NeoForge 26.1.2.87：`neoforge/versions/26.1.2/build/libs/WurstB+ Plus-v1.5.0-NeoForge-26.1.2.jar`（Java-WebSocket 与 Netty 运行依赖已内嵌）
- 26.2 Forge 65.1.0：`versions/26.2/build/libs/WurstB+ Plus-v1.5.0-Forge-26.2.jar`
- 26.2 NeoForge 26.2.0.53-beta：`neoforge/versions/26.2/build/libs/WurstB+ Plus-v1.5.0-NeoForge-26.2.jar`
- 1.20.1 Fabric Loader 0.16.14：`fabric/build/libs/WurstB+ Plus-1.5.0-Fabric-1.20.1.jar`
- 1.21.1 Fabric Loader 0.16.14：`fabric/versions/1.21.1/build/libs/WurstB+ Plus-1.5.0-Fabric-1.21.1.jar`
- 1.21.11 Fabric Loader 0.19.3：`fabric/versions/1.21.11/build/libs/WurstB+ Plus-1.5.0-Fabric-1.21.11.jar`
- 26.1.2 Fabric Loader 0.19.3：`fabric/versions/26.1.2/build/libs/WurstB+ Plus-1.5.0-Fabric-26.1.2.jar`
- 26.2 Fabric Loader 0.19.3：`fabric/versions/26.2/build/libs/WurstB+ Plus-1.5.0-Fabric-26.2.jar`

### 发布包校验

以下 SHA-256 对应 **v1.5.0 Release 的 62 个资产**：本轮由工作区 `main` 重新构建并上传的 60 个 jar，加上发布当时留存、本轮未重建的 `WurstB+.Plus-v1.5.0-Forge-1.20.1.jar`（旧命名，未改名）。`download/` 聚合目录由 `build-all.ps1 -PublishToDownload` 按需创建，当前工作区中不存在。

| 文件 | 大小 | SHA-256 |
| --- | --- | --- |
| `WurstB+.Plus-1.5.0-Fabric-1.20.2.jar` | 28.6 MB | `9C59EB7B697640D8043988E1C15A238C03D9A2E4831D2CF27484F0FFB18BE3D8` |
| `WurstB+.Plus-1.5.0-Fabric-1.20.3.jar` | 28.6 MB | `DEA3960BBA798A33C2D9C94DB5CAACE1107D79EBC2C160D7FC5A1D45E378BB02` |
| `WurstB+.Plus-1.5.0-Fabric-1.20.4.jar` | 28.6 MB | `BC6562DCAA13040B4D98382A38A2BA3B34D7EFF970EFAAC9326983D8809F92BD` |
| `WurstB+.Plus-1.5.0-Fabric-1.20.5.jar` | 28.6 MB | `1BDE056441E55357F0DC2A8F3331DF500ACEB9C66E9F342CE86643BD58D3895B` |
| `WurstB+.Plus-1.5.0-Fabric-1.20.6.jar` | 28.6 MB | `4029050B0DC41E2D972BF898F0F47A74CF7F54144EB4FAD8177FB85194DD5DAE` |
| `WurstB+.Plus-1.5.0-Fabric-1.21.1.jar` | 28.6 MB | `5E0366F434501B79D9BB478282EF89DEB9EB281BDE637A3CB4B3E6C56BBFD825` |
| `WurstB+.Plus-1.5.0-Fabric-1.21.10.jar` | 31.7 MB | `9066A69ACB48D46EFBF3BE2E5C47F1140A4563B771CAA3890FE08021579467F6` |
| `WurstB+.Plus-1.5.0-Fabric-1.21.11.jar` | 31.7 MB | `9C48DADD814A911062601CB845F433737A86EB6184EC0ED116B285ABEDACB3DA` |
| `WurstB+.Plus-1.5.0-Fabric-1.21.2.jar` | 28.6 MB | `98122244F087BEA50852B317DD34CB62AC26DE09657F164BB22C20B5E3FCE852` |
| `WurstB+.Plus-1.5.0-Fabric-1.21.3.jar` | 31.7 MB | `2BED1A955B14491AB7C144A811480D069174B95841D282009D40E4C3F23A0689` |
| `WurstB+.Plus-1.5.0-Fabric-1.21.4.jar` | 31.7 MB | `5AEB7991ACF75CA3BA8497189B21B888D5A47E69C0FF92F10D35F7B9FEA42D1F` |
| `WurstB+.Plus-1.5.0-Fabric-1.21.5.jar` | 31.7 MB | `58836F6A0BB408D91020D94BA64F05C8B669F08C0F53F88C6D776D0454BAD6F9` |
| `WurstB+.Plus-1.5.0-Fabric-1.21.6.jar` | 31.7 MB | `5CDA2DA2A7D1CEE2A8A4DAAAA53EB99EC3081011A064EEA60E9C55C2863F8B62` |
| `WurstB+.Plus-1.5.0-Fabric-1.21.7.jar` | 31.7 MB | `2C205627CB56E8ED090321ABF71BE3AF002213B348CFA6769CD774D6668AEFE9` |
| `WurstB+.Plus-1.5.0-Fabric-1.21.8.jar` | 31.7 MB | `15990573230F56BCEC1364947166458A68B64648735EBB75B2C6E12904A61DE6` |
| `WurstB+.Plus-1.5.0-Fabric-1.21.9.jar` | 31.7 MB | `7A40971C312BF2E27511EB1A0F5278A0F625269D103A1BB8F8A7498E5930A041` |
| `WurstB+.Plus-1.5.0-Fabric-1.21.jar` | 28.6 MB | `9D57D9BFC9A3B874BE81146944A3BD1B7D9A959A7B6C2E6A311D9C2ECB974C92` |
| `WurstB+.Plus-1.5.0-Fabric-26.1.1.jar` | 31.7 MB | `9DE019A698E1A9F4D0B47635692D9550313503999F3210A72A02213D7CEC86E6` |
| `WurstB+.Plus-1.5.0-Fabric-26.1.2.jar` | 31.7 MB | `4ACB3C8E9DED42E27320B0820BD466F79D42F3CBA598ED1AF9490E25801461B0` |
| `WurstB+.Plus-1.5.0-Fabric-26.1.jar` | 31.7 MB | `7979EF3771ECFA09026F9996598CDDD0AD58A1B740862888F25D140C93C889B9` |
| `WurstB+.Plus-1.5.0-Fabric-26.2.jar` | 31.7 MB | `283B4EB9D7AF544D9482686C5732567CB7084BEE5F2B21863B403F77ED8F088C` |
| `WurstB+.Plus-1.5.0-Forge-1.20.2.jar` | 29.0 MB | `FC1F277785EAD86537085E2B2BEA982A1C17E0C565D92C62A7CAE4B0A7B271FC` |
| `WurstB+.Plus-1.5.0-Forge-1.20.3.jar` | 29.0 MB | `48FFC8EE9B135C52DF380690A0AE4A9F7F2E578B5AEC8F9777D02381FA856DEA` |
| `WurstB+.Plus-1.5.0-Forge-1.20.4.jar` | 29.0 MB | `0B459F84C1B3A2908A369DA1E84694917A2150ABF1A389FF87A6F3055F137004` |
| `WurstB+.Plus-1.5.0-Forge-1.20.6.jar` | 28.9 MB | `AC7941D0987D0C32F30AC3A1F899649558746FBF40ACB914221FC15AA01D5DC0` |
| `WurstB+.Plus-1.5.0-Forge-1.21.1.jar` | 29.2 MB | `DA3B5ECF47AE592F954F78E02323C0950B180184CD558389CFA57C57A2A541D1` |
| `WurstB+.Plus-1.5.0-Forge-1.21.10.jar` | 31.7 MB | `812F14B0766B2BE49762A53AA0B10A9226B9D7D06DCDF4B79CBB7CFD62CE2C83` |
| `WurstB+.Plus-1.5.0-Forge-1.21.11.jar` | 31.7 MB | `AA83D792B8FF54EF2C17BFAC82D0ED977677EEC19ADD62EA2B91C60A0EA61A35` |
| `WurstB+.Plus-1.5.0-Forge-1.21.3.jar` | 31.7 MB | `DA621D39CF989B8CCED1BBDE2A0DCEBAF225FFD20A8533EE78F5C3A815E52EA3` |
| `WurstB+.Plus-1.5.0-Forge-1.21.4.jar` | 31.7 MB | `94BE45FB4AE24FFA165C7B2A056146A7137C8CED6685A78DD15524A561208C53` |
| `WurstB+.Plus-1.5.0-Forge-1.21.5.jar` | 31.7 MB | `B49F4935612F9CFCE533569484B351C11BA3F9F9168A310FCE769E88456E8F38` |
| `WurstB+.Plus-1.5.0-Forge-1.21.6.jar` | 31.7 MB | `F25CD9523C4B299A66FBDA12B13B56E7684F4BC767D5DB257B5D74B71F7EF5DD` |
| `WurstB+.Plus-1.5.0-Forge-1.21.7.jar` | 31.7 MB | `E3917BA17598CB76EE71C5F7CF320E440C59B33187F9CA6EBA5E97FC6DDC8782` |
| `WurstB+.Plus-1.5.0-Forge-1.21.8.jar` | 31.7 MB | `F07ACEA0F6AE88F333C95045CD65D8E742CA9F03E5385DA76DA8EACC1056A3A0` |
| `WurstB+.Plus-1.5.0-Forge-1.21.9.jar` | 31.7 MB | `C5DAB27A54E3B77594BF361BBE7C72F9609964E179131A4CA1924337E90E218C` |
| `WurstB+.Plus-1.5.0-Forge-1.21.jar` | 29.2 MB | `D1C9E7A33DEFEFF11EA71A26415CD38B441700B916A8734AF3F27547DDBA113F` |
| `WurstB+.Plus-1.5.0-Forge-26.1.1.jar` | 31.8 MB | `7210B3295E153295CFF8CF878167EB6B72EA81D86BEEAC10D5BC0AC27F15D6AD` |
| `WurstB+.Plus-1.5.0-Forge-26.1.2.jar` | 31.8 MB | `8C80DDC62DB217CD46AFCCF2B4CAA570B8DC25EE3429FC73F4191170DBAD1BA6` |
| `WurstB+.Plus-1.5.0-Forge-26.1.jar` | 31.8 MB | `3E72FB3E1246A8D45214D17C7FDF52A5977F57E36E6B72927D4854634C843DBC` |
| `WurstB+.Plus-1.5.0-Forge-26.2.jar` | 31.8 MB | `F6D8588C2EC0F6FD6F8DB741EB3ECA521BA90443A8DEEE4C18AE3E74BDFD5275` |
| `WurstB+.Plus-1.5.0-NeoForge-1.20.2.jar` | 26.9 MB | `99616B20C50C13AE10B9FD977DB69486135A776393BEC2DC339601C8DA88A50B` |
| `WurstB+.Plus-1.5.0-NeoForge-1.20.3.jar` | 26.9 MB | `D463018642F356EDF7F18BEFB2CCDBABDBE8AE883285693425F72FDA9D2D4F22` |
| `WurstB+.Plus-1.5.0-NeoForge-1.20.4.jar` | 26.9 MB | `3A8180585AFD8C38F304A75325432F8599FE70B708E9C3F6363993C410FACAB2` |
| `WurstB+.Plus-1.5.0-NeoForge-1.20.5.jar` | 26.9 MB | `ADEA9816CA5DA5EB202A4FF8963FB53414AA30AC76A94F11BCDCE25E78D0639E` |
| `WurstB+.Plus-1.5.0-NeoForge-1.20.6.jar` | 26.9 MB | `019E4073D5FA38935588568F67FDCA69C3F5FAE6AEAEE7AA611E79C1C6CD14B6` |
| `WurstB+.Plus-1.5.0-NeoForge-1.21.1.jar` | 28.6 MB | `B26AB9D4DB9177A1277C41BAE342FE216F2130F84529D35033817DE3F9E0F97C` |
| `WurstB+.Plus-1.5.0-NeoForge-1.21.10.jar` | 31.7 MB | `19600C00CA1E33446D3BE8422C595F4BB293A89A42D7FE1E9406F7EE5175D4B7` |
| `WurstB+.Plus-1.5.0-NeoForge-1.21.11.jar` | 31.7 MB | `F034C15D039A6F225267CD65C62F67C3C2A61A31814DFB7CFB771B0303CAFAEE` |
| `WurstB+.Plus-1.5.0-NeoForge-1.21.2.jar` | 28.6 MB | `BA0A0B0E6EBD1D12690C1B855CA42D485D2B6FAF9A623CB4B159DDBCB2412868` |
| `WurstB+.Plus-1.5.0-NeoForge-1.21.3.jar` | 31.7 MB | `820FEFFEBABA5D4234D0113B5075FF8B2333032216DEC5DE4CB4FF35A71A9F1E` |
| `WurstB+.Plus-1.5.0-NeoForge-1.21.4.jar` | 31.7 MB | `B3235F0B17783FD5D08C95624705EA43225B25DFD234B149A22CFDAF4F676CCC` |
| `WurstB+.Plus-1.5.0-NeoForge-1.21.5.jar` | 31.7 MB | `72CACA7CE3828A690EC5CDD4E30EDD5743230F7A62D589819BD56187587836EB` |
| `WurstB+.Plus-1.5.0-NeoForge-1.21.6.jar` | 31.7 MB | `761B0A3427C4804468B0CFAC3214F11C8072C7844E988E1700E87478ADFB94AE` |
| `WurstB+.Plus-1.5.0-NeoForge-1.21.7.jar` | 31.7 MB | `38E5A2DC1EB3690506248EC699A275B1D0C58091C035589186F685D0282084A8` |
| `WurstB+.Plus-1.5.0-NeoForge-1.21.8.jar` | 31.7 MB | `34A10AF2A57FA3F35C5760F4CFFD79F9373E4EDF5C36CCECBECD6C6C707ED60A` |
| `WurstB+.Plus-1.5.0-NeoForge-1.21.9.jar` | 31.7 MB | `64977BC9062FE701674D30532C8ADFC8C802D4B3AE9E1E603A7E57C76196B8DB` |
| `WurstB+.Plus-1.5.0-NeoForge-1.21.jar` | 28.6 MB | `1A9DA225D917D6F46DFB2A08BE57AF36F457097B4F7BE4B0B5B3A38D61031E7C` |
| `WurstB+.Plus-1.5.0-NeoForge-26.1.1.jar` | 31.7 MB | `5F62F2C679109E594D8B43E09575698B0A640B7D0369D05452320864BA2D01C7` |
| `WurstB+.Plus-1.5.0-NeoForge-26.1.2.jar` | 31.7 MB | `B2E6517E39B0483E1422AFA27C666818C96FDD5F69DE598589E710F3618A57EA` |
| `WurstB+.Plus-1.5.0-NeoForge-26.1.jar` | 31.7 MB | `E47F1F335D90A67969A48567CFC7273B8A371753A5494EA1CF8DDE00ECFFE7A7` |
| `WurstB+.Plus-1.5.0-NeoForge-26.2.jar` | 31.7 MB | `DC7F0D54A72EA560A649BB4A8A98F3BC5032300B905E147C94C23D9140A9D283` |
| `WurstB+.Plus-v1.5.0-Forge-1.20.1.jar`（发布当时产物，本轮未重建） | 28.2 MB | `CFC5EF862A0D822E20895AA69D952EB809D253D80CC89B2CB27172A5D2CDB9C0` |

### 1.21.1 渲染管线说明

1.21.1 的世界叠加层渲染通过 `RenderLevelStageEvent.Stage.AFTER_LEVEL` 驱动，在 `WurstForgeInitializer` 中构造 PoseStack：

```java
// 从 Forge 事件获取相机和投影矩阵
Camera camera = event.getCamera();
Quaternionf cameraRotation = camera.rotation().conjugate(new Quaternionf());
Matrix4f viewMatrix = new Matrix4f().rotate(cameraRotation);

// PoseStack 只包含相机旋转（view matrix），投影由 shader 的 ProjMat 处理
PoseStack poseStack = new PoseStack();
poseStack.mulPose(viewMatrix);
```

关键设计：
- **PoseStack 只放 view matrix（相机旋转）**，不放 projection。`BufferBuilder.addVertex()` 在存储前已用 matrix 变换顶点，`BufferUploader.drawWithShader()` 的 shader 会再施加 `ProjMat`，双投影会导致渲染偏移
- 在触发事件前通过 `event.getCamera()` 设置 `BlockEntityRenderDispatcher.camera`，确保 `RenderUtils.getCameraPos()` 返回正确的相机位置
- `RenderListener.fire()` 不包装 `RenderScope`，由各模块自行管理 GL 状态

### 26.1.2 渲染管线说明

26.1.2 使用全新的 extract/render 分离管线（`extractRenderState`），主要变更：

- **Screen API**：`render(GuiGraphics, int, int, float)` → `extractRenderState(GuiGraphicsExtractor, int, int, float)`
- **填充**：`fill()` 调用在 extract 阶段记录指令，后续批量渲染。逐像素大量调用会产生缓冲区膨胀
- **文字**：`centeredText()`/`text()` 处理 Alpha 通道，颜色需 8 位 hex（`0xFFxxxxxx`），6 位色（`0xffffff`）视为透明
- **纹理**：`blit()` 需 `RenderPipelines.GUI_TEXTURED` 第一参数
- **BufferUploader**：已移除，使用 `GpuBuffer`/`RenderPass` 替代，GUI 三角扇批量渲染暂不可用
- **圆角**：使用 4×4 超采样抗锯齿像素覆盖，大量圆角矩形有性能开销
