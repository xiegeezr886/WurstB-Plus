# WurstB+ Plus 架构审计报告

**审计对象**：`C:\Users\ui863\Documents\trae_projects\VAP`（WurstB+ Plus，基于 Wurst7 扩展的 Minecraft 客户端）
**审计时间**：2026-10-05 快照 · HEAD `aaa55951` · 工作分支 `fix/baritone-1.21.7-official-v2`
**审计方式**：只读静态审计。**未执行任何 Gradle 构建、未启动游戏、未运行测试**。所有数字来自文件系统实测、git plumbing 与哈希比对。
**证据可复现**：度量脚本保存在 `_recon/`（见附录 A），原始对照数据见 `_recon/dup-manifest.csv`。

> 本报告未修改任何源码。审计过程中在 `_recon/` 新建了 6 个分析脚本/数据文件（该目录本就是未跟踪的暂存目录），如不需要可直接删除。

---

## 一、执行摘要

这个项目的**功能实现质量明显高于它的工程结构质量**。1,062 个源文件里有经过真实启动验证的战斗链路、事件系统、周界挖掘与种子矿透算法，代码卫生度罕见地好（0 个 TODO/FIXME、0 个非 ASCII 标识符、176→192 个测试类）；但承载这些代码的**工程结构已经无法继续支撑增长**。

一句话结论：**项目把"版本支持"实现成了"67 份互相独立、彼此漂移的源码副本"，而不是"一份代码 + 版本适配层"。**

### 最严重的五个问题

| # | 问题 | 实测量级 | 后果 |
|---|---|---|---|
| **A1** | 67 个工程各自持有一份完整源码树，**无任何共享 sourceSet、无 buildSrc、无约定插件** | 51,641 个 `.java` 文件承载仅 **2,958 份不同内容**；冗余 **10.9×**，浪费 **153.4 MB** 源码 | 改一处逻辑要手工复制到最多 67 棵树；漂移已发生（同一路径最多 **25 个变体**） |
| **A2** | v1.6 的 19 个新子系统**只存在于根工程**，其余 66 个工程为 **0/13** 包、**0 个**新 Hack | 196 个文件的功能缺口 × 66 个工程 | README 宣称的版本矩阵（23 版本 × 3 加载器）与**实际功能能力严重不符** |
| **A3** | 跟踪的字节里 **76.8%** 是同一个 PNG 字体图集复制 67 份 | 3,428 个 PNG / 1,663 MB，其中**唯一内容仅 45.2 MB** | 仓库跟踪体积 2,117 MB，真实信息量约 1/18 |
| **A4** | 文档与仓库现实**系统性脱节**：项目数在 9 份文档里有 **7 个互斥数值**；测试数有 **6 个**版本 | 唯一正确的组合是 `CHANGELOG.md:116`（192 类/1248 测试）和 `docs/RELEASE.md:15,17,25`（67/64） | 无法判断"当前状态是什么"；`PROJECT_INDEX.md` 整份文件停留在 15 工程时代 |
| **A5** | **新克隆无法构建 11/67 个工程**（Baritone 依赖只存在于本地且被 gitignore） | 最新提交 `aaa55951` 声称"clone 后无需先跑脚本即可构建"，与事实相反 | 供应链/可复现性断裂；同时 `LICENSE.txt` 实为 LGPL-2.1 而非 GPL-3.0 |

### 一个必须先说清楚的前提

审计发现工作区**处于未提交的开发中状态**：10 个已修改文件 + 20 个未跟踪项，其中包括**整个 FFmpeg 视频背景子系统的原生载荷**（`src/main/resources/assets/wurst/ffmpeg/`，7 个 DLL，7.46 MB，未跟踪）、3 个新 Java 类（`background/Ffmpeg*.java`）及其测试。当前 `build/libs/` 的发布 jar（74,010,371 B，2026-10-05 01:11 构建）**早于**这些资源（18:56 写入），因此**该 jar 内不含任何 FFmpeg 条目**（实测 `*ffmpeg*` 匹配数 = 0）。

这与 git 日志自述一致：`feat(background): 视频背景播放（MP4/H.264，JCodec）——实现完成，但开发客户端类路径还没通`。**本报告的结论基于当前工作区快照，包含这批未提交内容。**

---

## 二、项目真实规模（实测）

| 指标 | 实测值 |
|---|---|
| Gradle 独立构建工程 | **67**（根 1 + `fabric/` 1 + `neoforge/` 1 + `versions/` **20** + `fabric/versions/` **22** + `neoforge/versions/` **22**） |
| Minecraft 版本 | **23**（1.20.1–1.20.6、1.21–1.21.11、26.1/26.1.1/26.1.2/26.2/26.3） |
| `src/main/java` 物理文件 | **51,641** |
| 其中**不同内容** | **2,958** |
| `src/main/java` 物理体积 | **168.8 MB** |
| 其中**唯一体积** | **15.4 MB** |
| 冗余倍数 | **10.9×**（浪费 153.4 MB） |
| 不同源文件路径 | **1,191** |
| 跟踪文件 / 跟踪 Java | 61,520 / 53,513 |
| 跟踪字节 | **2,117.1 MB**（PNG 占 1,663.3 MB = **76.8%**） |
| `.git` 体积 | 200.4 MB |
| 工作区体积 | **≈70 GB**（`run/` 34.7 GB + `.test/` 8.7 GB + `_smoke/` 2.8 GB 等，均未跟踪） |
| 分支 | **38**（含 22 个 `origin/<MC版本>` 快照分支） |
| 根工程源码 / 测试 | 1,062 / 192 个 `.java`（157,463 / 24,335 LOC，根工程内 1,248 个 `@Test`） |

### 关键推论：67 个工程里有 66 个是同一份代码的副本

对全部 67 棵 `src/main/java` 做按路径的 MD5 比对：

| 变体数（同一路径下有几种不同内容） | 路径数 | 占比 | 含义 |
|---|---:|---:|---|
| **1 种**（所有出现的工程里逐字节相同） | **683** | **57.3%** | 从未被版本化定制过，**可直接共享** |
| 2–3 种 | 281 | 23.6% | 版本适配 |
| 4–6 种 | 122 | 10.2% | 版本适配 |
| **7 种以上**（严重分叉） | **105** | **8.8%** | 需要逐案重构 |

**57.3% 的源文件在所有出现它的工程里逐字节相同**——这不是"为了适配不同 MC 版本而必须分叉"，这是纯粹的复制粘贴。

分叉最严重的路径（同一文件存在多少个不同版本）：

```
25 variants  net/wurstclient/gui/title/WurstTitleMenu.java
24 variants  net/wurstclient/WurstClient.java
19 variants  net/wurstclient/altmanager/screens/AltManagerScreen.java
18 variants  net/wurstclient/util/RenderUtils.java
16 variants  net/wurstclient/hacks/NoVelocityHack.java
15 variants  net/wurstclient/hacks/KillauraHack.java
15 variants  net/wurstclient/hacks/MultiAuraHack.java
15 variants  net/wurstclient/clickgui2/screens/AddBookOfferScreen.java
```

`WurstClient.java`（客户端生命周期单例）有 **24 个版本**——这是最不该分叉的文件。

### 补丁版本工程是纯副本

同族相邻版本工程的源码差异极小：

| 对比 | 文件数 | 逐字节相同 | 不同 |
|---|---:|---:|---:|
| `versions/26.1` vs `26.1.1` | 790 | **789 (99.9%)** | **1** |
| `versions/26.1.1` vs `26.1.2` | 790 | 786 (99.5%) | 4 |
| `versions/1.20.3` vs `1.20.4` | 767 | **766 (99.9%)** | **1** |
| `neoforge/versions/26.1` vs `26.1.1` | 790 | 789 (99.9%) | 1 |
| `neoforge/versions/1.20.3` vs `1.20.4` | 768 | 767 (99.9%) | 1 |
| `fabric/versions/26.1.x` 同族 | — | 99.9% | 1 |

**为 1 个文件的差异维护一整棵 790 文件的树**（×3 加载器）。`26.1`/`26.1.1`/`26.1.2` 三个工程的 `build.gradle` 甚至逐字节相同。

### 版本快照分支让问题翻倍

`scripts/sync-version-branches.py:37-59` 定义了 **22 个版本分支**，由 `.github/workflows/sync-version-branches.yml` 在每次 push main 时自动重建。实测 `origin/1.21.7`：

- 与 main 同步（落后 0 commit）
- 含 **2,404 个 `.java`**（3 棵工程树）
- **不含任何 v1.6 子系统**（`twilight` 只出现在 `docs/` 里）

即：**同一份代码同时在"目录维度"（67 个目录）和"分支维度"（22 个分支）各存一份**。分支是派生的，但目录是手工维护的——**没有任何机制把源码变更同步到那 66 个目录**（`scripts/` 里只有 `fix-baritone-*`、`patch-baritone-*`、`sync-version-branches`，没有源码同步脚本）。

---

## 三、问题详解

### A1 · 67 份源码副本，零共享机制（P0）

**证据**

- 全部 67 个工程各有独立的 `build.gradle`、`settings.gradle`、`gradle.properties`、`gradle/wrapper/`。
- **不存在 `buildSrc`、不存在约定插件、不存在被 include 的 `gradle/*.gradle`**（仅 `gradle/init-mirrors.gradle`，且必须显式 `--init-script` 才生效）。`buildSrc` 仅出现在 `source/`、`_tools/`、`_artifacts/` 这些被排除的参考树里。
- 67 份 `build.gradle` = 9,883 行 / 302,269 字节，其中 **8,392 行非空行只有 758 种不同文本 → 11.07× 行冗余**（7,634 行是冗余实例）。
- 67 份 `settings.gradle` 只有 **7 种不同内容**；`rootProject.name` 三种写法（`"WurstB+ Plus"` 22 个、`'WurstBPlus'` 5 个、缺失 40 个）。

**升级一个依赖要改多少处（实测字面量出现次数 / 涉及工程数）**

| 依赖 | 出现次数 / 工程数 |
|---|---|
| `Java-WebSocket 1.5.3` | 71 / 67 |
| `jsr305 3.0.2` | 67 / 67 |
| `mixinextras 0.5.4` | 66 / 28 硬编码 + 34 用模板变量 + 5 个都没有 |
| `netty 4.1.82` | 66 / 30 |
| `netty 4.1.118` | 62 / 31 |
| `netty 4.2.7` | 12 / 6 |
| `junit-jupiter 5.10.2` | 38 / 38 |

没有 version catalog（无 `gradle/libs.versions.toml`）、没有 `dependencyResolutionManagement`、没有 BOM、没有 `ext` 块。**同一个 netty 依赖同时存在三个互不兼容的版本**（`4.1.82` / `4.1.118` / `4.2.7`）。

**五种并存的"fat jar"机制**

| 机制 | 工程数 | 工程 |
|---|---:|---|
| ForgeGradle 6 `jarJar` | 8 | `.`、`neoforge`、`versions/1.20.{2,3,4,6}`、`1.21`、`1.21.1` |
| 手写 `allJar` 任务 + `allJarEmbed` 配置 | 14 | `versions/1.21.3…1.21.11`、`versions/26.1…26.3` |
| ModDevGradle `jarJar(...)` | 17 | `neoforge/versions/1.21…26.3` |
| Loom `include implementation(...)` | 23 | 全部 `fabric/` |
| **什么都没有** | **5** | `neoforge/versions/1.20.2…1.20.6` |

**B1 · 5 个 NeoForge 产物的运行时库缺失（顺带发现的功能性缺陷）**

`neoforge/versions/1.20.2 … 1.20.6` 既没有 jarJar 也没有 `include`，只用了 `implementation` + `implementation files("baritone-api-forge-1.20.1.jar")`（`build.gradle:47`，配合 `flatDir { dirs "." }` 于 `:36`）。实际检查已构建 jar 的 ZIP 中央目录：

| jar | 体积 | 内嵌 `META-INF/jarjar\|jars/*.jar` | 合并后的 Baritone 类 |
|---|---:|---:|---:|
| `neoforge/versions/1.20.2…1.20.6` | 26.9 MB | **0** | **0** |
| `versions/1.20.2`（Forge） | 32.2 MB | 2 | 0 |
| `fabric/versions/1.20.2` | 28.6 MB | 4 | 0 |
| `neoforge/versions/26.1` | 32.0 MB | 4 | 0 |

`docs/RELEASE.md:41` 明确写 NeoForge 生产任务是 `jar`。**这 5 个产物缺少 Baritone 运行时依赖**，若 v1.5.0 Release 用同样方式发布，则用户拿到的是启动即崩的包。（受限于离线，未能核对 GitHub Release 上的实际资产。）

**B2 · jar 内容不可复现**

根 `build.gradle:127-128` 声明 `jarJar(... mixinextras-forge, version: "[0.5.4,)")`——**开区间**——而编译用的是 `0.5.4`。实际产物里内嵌的是 **`mixinextras-forge-0.5.5.jar`**，`metadata.json` 记录的也是开区间。任何一次重建都会静默拉取更新的版本，造成"编译期 0.5.4 / 运行期 0.5.5"的 MixinExtras 错配。

另：根 jarJar 内嵌**实际为 18 个依赖 jar**（+`metadata.json` = 19 个条目），而 `PROJECT_INDEX.md:85,350` 与 `CHANGELOG.md:89` 写的是 19 个依赖；`build.gradle:94` 自己的注释写的是"内嵌 17 个库"。三个数字互不相同。

**A2 · v1.6 功能只存在于根工程（P0）**

逐个工程检查 v1.6 的 13 个新包与 13 个真正新增的 Hack：

```
.                    13/13 包   14/14 Hack   192 测试
其余 66 个工程        0/13 包    0/14 Hack    0 或 51 测试
```

`EntityCullingHack` 在 v1.5 就已存在，故真正新增 13 个。**66 个平台工程一个都没有。**

- README 的版本矩阵宣称支持 23 个 MC 版本 × 3 种加载器。
- 实际上：**根工程 Forge 1.20.1 独享全部 v1.6 功能**，其余 66 个工程的用户得到的是 v1.5 能力的客户端。
- `PROJECT_INDEX.md:329` 把"将 v1.6 子系统移植到其余 14 个平台工程"列为待办——但真实待移植目标是 **66 个**，不是 14 个。

这不是"还没做"，而是**工作量被低估了近 5 倍**：把 196 个文件按 66 棵树手工移植，且每棵树都需要针对 MC 版本做适配。

---

### A3 · 仓库体积：76.8% 的跟踪字节是重复 PNG（P0）

按 git blob SHA 分组（`git ls-tree -r -l`，≥100KB）：

- 跟踪载荷 **2,117.1 MB**，其中 **PNG = 3,428 个文件 / 1,663.3 MB**
- 27 个重复组：唯一体积 45.2 MB vs 冗余 **1,696.5 MB**
- 仅 PNG 的冗余字节 = **1,626.8 MB = 仓库的 76.8%**
- 根因：`mcsans_05_00{1..7}.png`（字体图集，单个约 4.16 MB）被复制进全部 67 个工程。`git ls-files -- '*mcsans_05_005.png'` 返回**恰好 67 条路径**。469 个 mcsans 文件其实只有 **7 个不同文件**。

即：仓库的跟踪体积里，**真实唯一信息量约为 1/18**。

### A4 · 文档与现实脱节（P0）

**项目数在 9 份文档里有 7 个互斥数值**：9 / 15 / 46 / 55 / 61 / 64 / 66 / 67。只有 67（工程数）、64（版本工程数）、66（v1.5 重建产物数）是正确的，出自 `docs/RELEASE.md:15,17,25`。

内部的算术错误：

- `README.md:63-71`："**19 + 21 + 21 = 61 个版本工程**，再加 3 …… 合计 **64 个独立 Gradle 构建**"，但同一文件 `:11` 的徽章写 67。**实际是 20 + 22 + 22 = 64 + 3 = 67。**
- `README.md:71` 说 61 个版本工程，`README.en.md` 说 64 个、22 个 MC 版本、且矩阵止于 26.2（实际 23 个版本，有 26.3）。
- `PROJECT_INDEX.md:9,19` 整份文件停留在"**15 个独立构建工程**"+"46 个新版本工程"（=61）；但**它自己的表格有 18 行**（`:23-40`），`:42` 又说"全部 15 个"。
- `PROJECT_INDEX.md:42`："合计 **11,779 个 Java 源文件**"——它自己那 18 行相加是 14,184，实测该 18 个工程为 14,223。

**测试数有 6 个版本**：176 类/1114 测试（`PROJECT_INDEX.md:97,349`、`RELEASE.md:145`、`CHANGELOG.md:114`）、189/1223（`CHANGELOG.md:115`）、**192/1248**（`CHANGELOG.md:116`，**唯一与实测一致**）、112/545（`RELEASE.md:1044`）、123/663、148/905、125/691。

实测：根工程 `src/test/java` 有 **192 个文件、1,248 个 `@Test`**。**"1114 个测试"无法从代码中得出**——不是"另一套套件"，而是不可复现的数字。

**其他确定错误（抽样）**

| 声明 | 位置 | 实测 | 判定 |
|---|---|---|---|
| 根 `src/main/java` = 1046 | `PROJECT_INDEX.md:23,95`、`RELEASE.md:144` | **1062** | 过期 |
| `events` = 38 | `PROJECT_INDEX.md:118` | **49** | 少报 29% |
| `background` = 16 | `PROJECT_INDEX.md:112` | **29** | 少报 81% |
| `util` = 95 | `PROJECT_INDEX.md:147` | **123** | 过期 |
| v1.6 = 191 / 188 / 152 个文件 | 三份文档各说一个 | 同一定义下 204–209 | 三者互斥 |
| 根产物 68.1 MB | `PROJECT_INDEX.md:350` 等 3 处 | **74,010,371 B** | 过期 |
| `RELEASE.md` 的逐资产 SHA-256 表 | `RELEASE.md:1088-1149` | 抽查 6/6 **不匹配**本地 jar | 不可复现 |
| `download/` 不存在 | `RELEASE.md:1084` | 存在，16 个文件 | 反证 |
| 26.3 三工程"尚未编译" | `CLIENT-LAUNCH.md:155` | 3 个 jar + 报告 `OK NeoForge-26.3 … 33.5 MB` | 反证 |
| `scripts/` 11 个脚本 | `PROJECT_INDEX.md:50` | **32** 个 | 过期 |
| `docs/` 4 个文件 | `PROJECT_INDEX.md:51` | **234** 个（跟踪） | 过期 |

**文档结构性问题**：`PORTING_TASK.md`（79,867 B）已经变成**按会话追加的日志**（含"2026-09-25 会话"、"2026-09-26 会话"等章节），不再是任务清单。`docs/RELEASE.md` 达 96,184 B。**10 份文档同时声明项目状态**，没有任何一份是权威来源。

**所有状态证据都未被跟踪**：每个状态声明都引用 `_smoke/`、`.test/`、`tmp-recon/`、`build/release-v1.5/`、`D:\WurstB\…`——这些在克隆里**都不存在**，因此**没有任何一个状态声明可以从仓库复现**。

### A5 · 新克隆无法构建（P0）

最新提交 `aaa55951` 的标题是"内嵌 Baritone 产物入库，clone 后无需先跑脚本即可构建"——**与事实相反**。

`.gitignore:21` 有 `*.jar`，仅开了 4 个例外（`!gradle/wrapper/*.jar`、`!versions/*/libs/*.jar`、`!newforge/*/libs/*.jar`——**`newforge/` 目录不存在，是死规则**、`!baritone-maven/**/*.jar`）。

| 文件 | 被谁引用 | git 状态 |
|---|---|---|
| 根 `baritone-api-forge-1.20.1.jar` | 根 `flatDir{dirs "."}`、`neoforge` `flatDir{dirs "../.."}` | 存在、**被忽略、未跟踪** |
| `neoforge/versions/1.21{,.1}/baritone-api-forge-1.21.1.jar` | `implementation files(...)` + `zipTree`（`:147/:152`） | 存在、**未跟踪** |
| `fabric/libs/baritone-api-fabric-1.10.3.jar` | `flatDir{dirs "libs"}` | 存在、**未跟踪** |
| `fabric/baritone-maven/`（72 个 jar） | 9 个工程用 `uri("baritone-maven")` 解析 | **目录被 gitignore**，0 个跟踪 |

**共 11 个工程在全新克隆下无法解析 Baritone 依赖。** 且 `.gitignore` 没有 `!fabric/versions/*/libs/*.jar` 或 `!neoforge/versions/*/libs/*.jar` 例外，未来放进这些目录的 jar 会被静默忽略。

**离线构建路径同样断裂**：`scripts/seed-gradle-wrapper.ps1:27` 从外部 `tools/` 目录播种 wrapper，而 **`tools/` 在本仓库不存在**（未跟踪、磁盘上也没有），脚本会 `throw "tools/ directory not found next to the project."`。但 `docs/RELEASE.md:152` 把它当作可用流程记录。

**B3 · `neoforge/` 是唯一没有跟踪 wrapper jar 的工程**（66/67 跟踪，而 `README.md:72` 声称"每个工程都有完整的 `gradle-wrapper.jar`"）。

**B4 · 60 个已跟踪的 wrapper jar 只靠"已在索引里"存活**：`.gitignore:21` 的正向规则 `*.jar` 匹配它们，一旦执行 `git add` 级联操作就可能被静默丢弃。而 `doctor.ps1:83` 和 `seed-gradle-wrapper.ps1` 的设计意图正是"给缺失的工程补一个 wrapper jar"——**这种修复对 `git add` 是不可见的**。

### A6 · 根工程源码：没有分层（P1）

对 1,062 个文件的 `net.wurstclient.*` import 建图跑 Tarjan SCC：

**35 个顶层子包中有 29 个属于同一个强连通分量**：

```
ai, altmanager, background, clickgui2, command, commands, compose, events, gui,
hack, hacks, hud, hud2, keybinds, macros, mixin, music, nochatreports, options,
other_feature, other_features, proxy, render, seed, serverfinder, settings,
twilight, util, waypoints
```

另有 3 个二级环：`settings.filterlists ↔ settings.filters`、`render.skia ↔ util.esp`、`seed.search ↔ seed.structure`。

**最严重的一处：模型层反向依赖视图层**

```java
// src/main/java/net/wurstclient/settings/Setting.java
:23   import net.wurstclient.clickgui2.Component;
:210  public abstract Component getComponent();
```

`Setting` 是 **55 个 settings 文件**的基类，其中 **21 个**直接 `import net.wurstclient.clickgui2.*`。这意味着：**配置模型层依赖某一个具体的 GUI 实现**。后果是"重复 GUI 框架"问题**无法通过删除解决**——删掉任何一个 GUI 都会打断 settings 层。

**具体分层违规（部分）**

| 方向 | 证据 |
|---|---|
| `util → clickgui2` | `util/CombatTargetUtils.java:22-23` 读 `GuiPreferences.TargetType`（战斗工具读 GUI 偏好）；`util/ScreenRegistry.java:18-24` import 全部 4 个 ClickGUI 屏幕 |
| `util → hacks` | `util/BlockVertexCompiler.java:16` → `hacks.SearchHack` |
| `hud → hacks` | `hud/IngameHUD.java:14`、`hud/TabGui.java:23` |
| `hack → hacks` | `hack/Hack.java:15-17` 为 `instanceof` 判断 import 了 3 个具体 Hack |
| `hacks ↔ clickgui2` | **双向**：`ClickGuiHack.java:12-13`、`RadarHack.java:21-22` ↔ `ClickGui.java:21`、`components/RadarComponent.java:27` |
| `hacks → mixin` | 8 个文件绕过 `mixinterface/` 直接 import 注入类：`KillauraHack.java:57-58`、`MultiAuraHack.java:58-59`、`NoFallHack.java:21` |
| `hacks → twilight` | `hacks/MusicPlayerHack.java:6` 直接 import 音乐屏幕 |

**注册机制是"硬编码字段 + 按字段名后缀反射"**

```java
// hack/HackList.java:270-277
for(Field field : HackList.class.getDeclaredFields())
    if(!field.getName().endsWith("Hack")) continue;
```

- `HackList` 手写 197 个字段、`CmdList` 57 个字段（后缀 `"Cmd"`）、`OtfList` 18 个字段（后缀 `"Otf"`）。
- 字段名不以 `Hack` 结尾 → **静默不注册**；非 `Hack` 类型但以 `Hack` 结尾 → 构造期 `ClassCastException` → `ReportedException` → **启动崩溃**。
- `hax.put(...)` 遇重名**静默覆盖**，而 addon 路径 `registerAddonHack`（`HackList.java:306-312`）**抛异常**——校验策略不对称。
- **该风险已经发生**：210 个类继承 `Hack`，只注册了 209 个。遗漏的是 `hacks/RadialMenuHack.java`——它有**私有构造器**（`:74`），只能靠 `:96-112` 的懒加载自注册，而调用点在 `hud/IngameHUD.java:34`，**位于 `onRenderGUI` 内，即每帧执行**（每帧取静态监视器锁 + `TreeMap` 查找）。
- 代码自己承认了这个设计缺陷。`RadialMenuHack.java:89-91`："工程的 HackList/OtfList 用的是写死的字段，不编辑 WurstClient.java 就没法把新功能加进去"；`IngameHUD.java:32-33`："就只能在这里补一句"。

**扩展点 `addon/` 是死的**：`AddonManager` 本身写得很好（`ServiceLoader`、重名校验 `:85-121`、逐 addon 故障隔离 `:74-79`），但 `src/main/resources` 下**不存在 `META-INF/services/net.wurstclient.addon.WurstAddon`**，仓库里没有示例 addon，`addon/` 有 **0 个测试**。该扩展点**未被验证**。

**事件系统的注解路径在线上是死代码，却仍在每个开关上付反射成本**

- `@WurstSubscribe` 在 `src/main/java` 出现 **0 次**（实测；仅 2 个测试文件用了 7 次）。
- 但 `hack/Hack.java:206` 每次 enable 都调 `EVENTS.subscribeAnnotated(this)`，`:211` disable 时 `unsubscribeAnnotated`。
- `event/EventManager.java:226-248` 会**遍历整个类继承链**、调 `getDeclaredMethods()`（`:230`）、`isAnnotationPresent`（`:232`）、`method.setAccessible(true)`（`:240`）。对 `hacks/KillauraHack.java`（66 个方法、1,321 LOC）而言，**每次开关分配约 80–120 个 `Method` 对象，而订阅者永远是 0**。
- `event/EventManager.java:79` 每次事件分发都无条件调用 `fireAnnotated`，`:98-99` 分配一个 `new ArrayList<>(...)` 并遍历永远为空的 map。渲染路径事件每 tick 触发多次。
- `PROJECT_INDEX.md:249,338` 宣称"LambdaMetafactory 注解路径消除反射"——**对实现成立，对应用不成立**。

**事件系统其余部分质量是好的**：`WurstSubscriber.java:38-47` 确实构建了真正的 `Consumer` callsite，`:91-94` 分发路径无反射（claim 属实）；`fireImpl` 用变更时不可变快照（`ConcurrentHashMap listenerSnapshots`，`:69-70`），`LongAdder` 计数正确，`remove()` 正确清理三张 map（`:200-208`，附带一个已复现 NPE 的注释）。**失败策略是全崩**：`:83-92` 任何监听器抛 `Throwable` → `printStackTrace` + `ReportedException` → 游戏崩溃，无逐监听器隔离。

### A7 · 四套 GUI + 三代组件层 + 三条渲染路径（P1）

**同时存活、运行期可切换的 GUI 框架**（`clickgui2/ClickGuiScreens.java:29-35` 按 `ClickGuiStyle` 选择）：

| 框架 | 入口 | LOC |
|---|---|---:|
| VAPE | `clickgui2/component/VapeClickGuiScreen.java` | 828 |
| SuperSoft | `clickgui2/component/SuperSoftClickGuiScreen.java` | 934 |
| Epsilon 下拉 | `clickgui2/epsilon/EpsilonDropdownScreen.java` | 801 |
| Epsilon 面板导航 | `clickgui2/epsilon/EpsilonPanelNavigatorScreen.java` | 1,563 |
| Navigator | `clickgui2/NavigatorScreen.java` | 716 |
| HUD 编辑器 | `hud2/HudEditorScreen.java` | 615 |
| 声明式 `compose/` | `compose/UiNode.java` 等 11 个文件 | 1,016（**仅 2 个 import 者**） |

**两代控件层并存，且简单类名重复**：

- `clickgui2/component/`（单数）27 个文件 / 5,976 LOC / 7 个 import 者
- `clickgui2/components/`（复数）12 个文件 / 1,390 LOC / 11 个 import 者
- **`CheckboxComponent`、`ColorComponent`、`SliderComponent` 在两个包里各有一个**；另有 `clickgui2/Window.java` vs `clickgui2/window/Window.java`。**import 错一个包能编译通过，但得到的是不同的控件。**
- 两者都在被活跃使用——这是活的代际分裂，不是死代码。

**渲染路径同样三重**：

| 路径 | 规模 | import 者 |
|---|---|---|
| `util/RenderUtils.java` | 989 LOC | 60 个文件 |
| `clickgui2` 扁平渲染器族（`FlatRenderer`+`FlatUiRenderer`+`RoundedRectRenderer` 693 LOC+`FlatTheme`+`Rise*` 8 个文件） | ≈1,660 LOC | 48 个文件 |
| `render/skia/*`（Skiko） | 1,053 LOC | **4 个文件** |
| `util/render/*`（`RenderScope`、`PostEffectQueue`） | 945 LOC | 10 个文件 |
| `gui/visual/*`（语义 token） | 210 LOC | 27 个文件 |
| 裸 `ShaderInstance` | — | 29 个文件 |

`RoundedRectRenderer`（693 LOC）与 `render/skia`（1,053 LOC）都在画圆角/矢量图形，`FlatRenderer` 也画一遍。**Skiko 只为 4 个调用点服务，却要背负 16.5 MB DLL + 10 MB `icudtl.dat` 的运行时解压成本。**

`compose/`（声明式 UI，1,016 LOC）**实质已废弃**：只有 2 个 import 者，且它自己 11 个文件里有 4 个反过来 import `clickgui2.FlatRenderer`（`ComposeHackList.java:7`、`ComposeNotifications.java:7`、`FlowingGradient.java:3`、`UiText.java:6`），还横向依赖 `other_features.HackListOtf`、`hud2.NotificationContent`、`gui.visual.VisualTheme`——**一个本该取代三套命令式框架的声明式层，依赖了所有三套。**

### A8 · 上帝类集中在 v1.6 新增子系统（P2）

| LOC | 文件 | 评价 |
|---:|---|---|
| 3,031 | `twilight/TwilightShellScreen.java`（85 方法 / 88 字段） | 上帝类：外壳+主页+内容页+沉浸播放页挤在一个 Screen |
| 1,811 | `perimeter/PerimeterAutomation.java`（86 方法 / 44 字段） | 上帝类：28 状态编排器 |
| 1,596 | `music/apple/AppleLyricPlayer.java`（46 方法 / 67 字段） | 上帝类 |
| 1,563 | `clickgui2/epsilon/EpsilonPanelNavigatorScreen.java` | 上帝类（第 4 套 GUI） |
| 1,321 | `hacks/KillauraHack.java`（66 方法） | 上帝类 |
| 1,239 | `hacks/MultiAuraHack.java`（62 方法） | 上帝类 |
| 1,218 | `background/BackgroundVideo.java`（50 方法 / 41 字段） | 视频解码+GL 上传+线程生命周期 |
| 1,146 | `seed/crack/LatticeCracker.java` | **合理**（数学，79 个常量） |
| 989 | `twilight/TwilightTheme.java` | **合理**（214 个调色板常量） |

**重要澄清**：`hacks/` **不是**上帝类问题——278 个文件，平均 **143.5 LOC**，只有 8 个超过 500 LOC。`HackList`（197 字段）/`CmdList`/`OtfList` 是声明式注册表，大是合理的。**真正的上帝类全部是 v1.6 新增的**。

### A9 · 测试覆盖：25% 的代码零覆盖（P1）

框架：JUnit Jupiter **5.10.2**（`build.gradle:226`），`useJUnitPlatform()`（`:262`），**无 Mockito**。

| 子系统 | 主文件 | 测试 | 覆盖率比 |
|---|---:|---:|---:|
| **`hacks`（含 18 子包）** | **278** | **0** | **0%** |
| **`commands`** | **58** | **0** | **0%** |
| `options` / `mixinterface` / `addon` / `macros` / `waypoints` / `serverfinder` / `update` / `discord` | 2–8 各 | 0 | 0% |
| **`mixin`** | **75** | **4** | **5.3%** |
| **`settings`** | **55** | **1** | **1.8%** |
| `events`（49 个监听器接口） | 49 | 1 | 2.0% |
| `altmanager` | 25 | 1 | 4.0% |
| `clickgui2` | 97 | 14 | 14.4% |
| `util` | 123 | 61 | 49.6% |
| `background` | 29 | 17 | 58.6% |
| `music` | 28 | 19 | 67.9% |
| `twilight` | 18 | 19 | **105.6%** |

**关键缺口**：全部战斗链路（`KillauraHack` 1,321 LOC、`MultiAuraHack` 1,239 LOC、`AnchorAuraHack`、`CrystalAuraHack`）**零测试**，尽管 `docs/COMBAT_ARCHITECTURE.md` 详细描述了多阶段战斗管线。75 个 Mixin 文件只有 4 个测试，且这些测试**断言源码文本/结构**（如 `PlayerMixinStructureTest`、`MixinPackageIsolationTest` 从工作目录读 `src/main/java/...`），**不验证运行时注入行为**。

**"0 失败"不等于"1,248 项都执行了"**：至少 3 个测试类可用 `Assumptions.assumeTrue` 静默跳过——`background/FfmpegVideoDecoderNativeTest.java`（6 处 `assumeTrue(FfmpegVideoDecoder.isAvailable(), …)`）、`background/BackgroundVideoTest.java`（15 处）、`render/skia/SkiaPrimitiveSemanticsTest.java:86` 的 `assumeTrue(false, "Skiko natives unavailable")`（**一个可以靠无条件跳过而"通过"的测试**）。

**测试分布不均**：64 个版本工程中 **33 个有 51 个测试文件、31 个是 0 个**。同样功能的工程，测试覆盖随机不同。

值得肯定的一项：`mixin/MixinPackageIsolationTest.java` 加载 `wurst.mixins.json` 并断言 `mixin/` 下每个 `.java` 要么是被配置的 Mixin 要么是配置插件类——**真正在强制架构约束**。

### A10 · 并发与生命周期（P1）

**已确认的客户端线程竞争（2 处）**

- `hacks/AutoStealHack.java:65-69` 起了一个裸 `Thread("AutoSteal")`，`:72-99` 的 `shiftClickSlots` **在该线程上**调 `screen.slotClicked(slot, slot.index, 0, ClickType.QUICK_MOVE)`（`:92`，原版 GUI 回调，会改菜单并发包）并读 `MC.screen`（`:89`）——**全程没有 `MC.execute(...)` 交接**。
- `hacks/ChatTranslatorHack.java:101-104/:133-136` 起 `Thread("ChatTranslator")`，worker 在 `:114` 调 `MC.gui.getChat().addMessage(...)`（改渲染线程正在遍历的聊天队列 → CME 风险），`:148` 调 `MC.getConnection().sendChat(...)`——同样无交接。

其余地方**知道**该用 `Minecraft.execute(...)`（`hacks/PopChamsHack.java:90`、`util/render/AsyncTextureLoader.java:49`、`twilight/TwilightShellScreen.java:414-431` 等），**只是应用得不一致**。

**`AsyncTextureLoader` 有未处理的拒绝策略**

`util/render/AsyncTextureLoader.java:24-26`：`ThreadPoolExecutor(1, 2, 30s, ArrayBlockingQueue(32), …, new AbortPolicy())`。core=1 + 32 槽队列意味着**第 34 个排队的解码任务会抛出 `RejectedExecutionException`**，直接从事 `CompletableFuture.supplyAsync`（`:33`）冒到 `load()` 的调用方。无 handler、无有界提交。`:73` 的 `while(buffer.hasRemaining() && channel.read(buffer) >= 0){}` 在 `read` 返回 0 时自旋。`:80-83 shutdown()` 在 `WurstClient.java:217` 被调用，但 **shutdown 之后任何 `load()` 都永久抛异常**。

**`SkikoNatives` 的安全问题（P1，安全问题而非性能问题）**

`render/skia/SkikoNatives.java`：`:23-24` volatile + `:32` synchronized 的双检锁是**正确**的。但：

- `:72-73 Files.copy(..., REPLACE_EXISTING)` **每次启动无条件重写 16.5 MB DLL + 10 MB `icudtl.dat`**，无存在性检查、无哈希/签名校验、**无平台架构校验**（`EspSkia.java:72` 注释直言"不校验平台"）。
- 写入 `<gameDir>/skiko/` 后设置 `skiko.library.path`，让 Skiko 从**用户可写目录** `System.load()`。
- **任何能写游戏目录的本地进程都能控制客户端下次启动时加载的 DLL。** 这是本地提权/劫持面。

**其余并发发现**

- `EntityOcclusionCuller` **并非异步**——它是渲染线程内的同步 GPU 查询轮询器，`RenderSystem.assertOnRenderThread()`（`:32`）正确，`:55-60` 非阻塞轮询 `GL_QUERY_RESULT_AVAILABLE`（好）。缺陷：`queries` 是 `IdentityHashMap` 持有 **`Entity` 强引用**，仅在 `isRemoved()` 时每 256 次调用修剪一次（`:45-49`、`:109-120`）；`close()` 不幂等；`WurstClient.shutdown()` 不关闭它（但 `EntityCullingHack.java:56/66/76` 在 enable/disable/换世界时管理正确）。
- 全项目约 **22 个后台线程持有者**，但 `WurstClient.java:200` 的 JVM shutdown hook 只覆盖 5–7 个：`discordRpcManager`、`clientMetricsManager`、`entitySnapshotManager`、`inventoryActionQueue`、`NeteaseMusicPlayer`、`AsyncTextureLoader`、`BackgroundManager`。其余依赖各自的 `onDisable()`。
- **存在两套独立的音乐执行器**：`music/NeteaseMusicPlayer.java:29`（fixed pool 2）与 `twilight/TwilightMusicService.java:67`（fixed pool 2）。

### A11 · 代码卫生（P2，总体良好）

| 信号 | 数量 | 说明 |
|---|---:|---|
| `TODO`/`FIXME`/`XXX` 注释 | **0** | 大小写敏感扫描（此前报告的 13 处是 `ToDoubleFunction`/`box.maxY` 误报） |
| 非 ASCII 标识符 | **0** | 中文仅出现在注释和字符串里 |
| `@SuppressWarnings` | 14 | 多数是 `unchecked` |
| `catch` 块总数 | 417 | 其中**空 catch 20 个**（`BackgroundManager.java:220,311`、`NavigatorSettingsPanel.java:520`、`DiscordRpc.java:159`、`MacroManager.java:73`、`PlayerSkinProviderMixin.java:84`）、**仅注释 43 个** |
| `System.out/.err.print*` | **137** | 已发布客户端向 stdout 打日志 |
| `printStackTrace()` | **92** | 与上一条组合是主要运维异味 |
| 反射访问点 | 15 | 注册表、事件系统 |
| 硬编码绝对路径 | 2 | `SteamLocator.java:66-67` 的 Windows Steam 回退路径（可接受） |
| `Thread.sleep` | 3 | **全部不在渲染/tick 线程**（`BackgroundVideo.java:956`、`AutoStealHack.java:87`、`ForceOpHack.java:234`） |
| 自旋等待 | 1 处可疑 | `AsyncTextureLoader.java:73` |

**总体评价**：源码在**卫生**维度异常自律（0 TODO、0 非 ASCII 标识符、风格一致、license 头齐全），在**结构**维度异常失控（A6）。

### A12 · 合规与许可（P0，法律风险）

**`LICENSE.txt` 不是 GPL-3.0**

实测：`LICENSE.txt`（28,255 B / 445 行）是 **Minecraft Forge MDK 文本**。

- 第 3 行："licensed under the terms of the **LGPL 2.1**"
- 第 66、180 行：`GNU LESSER GENERAL PUBLIC LICENSE` 正文
- 第 30–36 行：**不可再分发的 MCP 数据限制**
- 仓库里**不存在根级 GPL-3.0 文本**（唯一的 GPL-3.0 文件是第三方 `.../META-INF/licenses/cozyui/CozyUI-GPL-3.0.txt`，67 份副本）

**但需要公平地指出**：`README.md:95-97` **正确地披露了**这一点（"仓库根目录的 `LICENSE.txt` 是 Forge MDK 模板带来的 LGPL 2.1 文本"），GPL-3.0-or-later 也在 `src/main/resources/META-INF/mods.toml` 与 `fabric.mod.json` 里正确声明了。

问题在于**其余入口互相矛盾**：

- `README.md:13` 的 GPL-3.0 徽章**链接到 `LICENSE.txt`**（该文件是 LGPL-2.1）——链接与实际内容不符；
- `PROJECT_INDEX.md:98` 直接称 `LICENSE.txt` 为"GPL-3.0 许可证"——**与事实相反**；
- 仓库里**不存在根级 GPL-3.0 全文**（唯一的 GPL-3.0 副本是第三方的 `CozyUI-GPL-3.0.txt`）——源码声明 GPL-3.0-or-later 却不附带该许可文本；
- 一个只读 `LICENSE.txt` 的分发者看到的是 **LGPL-2.1 + "MCP 数据不可再分发"限制**（第 30–36 行），而非 GPL-3.0。这个位置放错文件本身就是风险。

**`mod_license` 自相矛盾**

```
22 × mod_license=GPL-3.0-or-later
 5 × mod_license=All Rights Reserved   ← neoforge/ 与 versions/{1.20.2,1.20.3,1.20.4,1.20.6}
```

这 5 个工程的 `gradle.properties` 说"保留所有权利"，**同一个工程的 `mods.toml` 说 `license="GPL-3.0-or-later"`**。`neoforge/gradle.properties` vs `neoforge/src/main/resources/META-INF/mods.toml` 是直接冲突。

**FFmpeg 的 LGPL "对应源码"承诺无法兑现（P0）**

`CHANGELOG.md:48` 声称可复现的 FFmpeg 构建脚本与 JNI shim 源码"在仓库的 `native/ffmpeg/` 下（对应源码提供）"。

实测：`native/` **跟踪文件数 = 0**，且 `git check-ignore` 确认它**没有被 ignore——只是从未提交**。磁盘上确有 `native/ffmpeg/{build-ffmpeg.sh, build-shim.sh, COPYING.GPLv2, COPYING.GPLv3, COPYING.LGPLv2.1, COPYING.LGPLv3, LICENSE.md}` 与 7 个 DLL，但**克隆里一个都没有**。

**LGPL 的"对应源码"义务要求向二进制接收者提供源码。当前状态：违规。**

同样地，7 个 FFmpeg DLL（7,636,368 B）位于 `src/main/resources/assets/wurst/ffmpeg/`，**未跟踪**（`git check-ignore` 确认未被忽略，只是没提交），且**不在已构建的发布 jar 里**（该 jar 建于 01:11，资源写入于 18:56）。附件中还有 `license-ffmpeg.txt`、`copying-lgplv2.1.txt`，同样未跟踪。

**字体许可问题**

- **PingFang TTFs = 31.35 MB**（`pingfang_{light,regular,semibold}.ttf`）随每个 jar 分发，**树内零归属/许可声明**。来源压缩包 `PingFangSC-main.zip` 声明 "MIT License Copyright (c) 2023 refinec"，但其自身 README 称文件为"苹果平方字体（Apple PingFang SC）"——**第三方无权对 Apple 系统字体再许可**。这是一个无法通过加声明解决的实质性风险。

>  **✅ 已解决（2026-10-06）：** 这三个 TTF、三个字体 provider JSON 与从未被调用的 `clickgui2/PingFangFont.java` 已全部删除（−31.3 MB）；`SkiaFontManager` 改为按字重取系统中文面并逐级回退，不再抛异常。详见 [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)。
- 正面例子：`src/main/resources/META-INF/licenses/cozyui/` 在 67 个工程里都正确附带 `ATTRIBUTION.txt` + `CozyUI-GPL-3.0.txt` + `FluentEmoji-MIT.txt` + `NotoSans-OFL-1.1.txt`。

**内嵌依赖无许可声明**

根 jarJar 内嵌的 18 个库在产物里**没有任何 NOTICE/许可文件**。从本地 Gradle 缓存 POM 核实：`skiko-awt 0.8.19` + `kotlin-stdlib` + `kotlinx-coroutines` = Apache-2.0，`mixinextras-forge` + `Java-WebSocket` = MIT，`jcodec` = FreeBSD；而 `vorbis-support` 声明 "GNU LIBRARY GENERAL PUBLIC LICENSE, Version 3.0"——**与项目声称的 GPL-3.0-or-later 不同**；soundlibs 家族（jlayer/mp3spi/tritonus/jorbis）、jflac、jaudiotagger、netty、zxing、java-stream-player 的 POM **没有 `<licenses>` 段、jar 内也没有许可文件**（仅 vorbis-support 带 `META-INF/license.txt`）。抽查的两个已跟踪 Baritone jar 同样**无许可条目**。

### A13 · 供应链（P1）

- **67/67 个 `build.gradle` 都声明 `flatDir`**——根工程用 `dirs "."`，即**把仓库根目录变成依赖仓库**。这是依赖混淆（dependency confusion）与供应链污染的气味。
- 远程仓库：mavenCentral + `maven.aliyun.com`（central/public/gradle-plugin）+ `repo.spongepowered.org` + `jitpack.io` + `maven.minecraftforge.net` + `maven.fabricmc.net`。Gradle 发行版来自 `mirrors.cloud.tencent.com`。
- **零完整性控制**：无 `gradle/verification-metadata.xml`、无 `gradle.lockfile`（0 个跟踪）、CI 里 `actions/checkout@v4` 未固定 SHA。
- **唯一的 CI 不构建任何东西**：`.github/` 只有 **1 个跟踪文件** `.github/workflows/sync-version-branches.yml`（1,419 B，`permissions: contents: write`），仅用于重建 22 个版本分支。**没有 workflow 编译、测试、打包或发布任何一个工程**，也没有 issue/PR 模板、dependabot、CODEOWNERS。
- **无密钥泄漏**：`sk-`/`ghp_`/`xox`/`AIza`/`PRIVATE KEY` 全部 0 匹配；`password` 匹配均为 Alt manager / Microsoft 登录代码路径。
- **遥测与更新检查确已移除**（已核实）：`update/WurstUpdater.java` 在所有 67 个工程里都是 638 字节的 stub（`onUpdate()` 自我注销、`isOutdated()` 返回 false），`update/` 包内 0 个网络调用文件；MC 遥测由 `TelemetryManagerMixin` + `NoTelemetryOtf` 强制关闭。
- **但 `www.wurstclient.net` 仍在活跃调用**：`PlayerSkinProviderMixin.java:148` 下载 `https://www.wurstclient.net/api/v1/capes.json`（另有 `Version.java:149` 的死 URL 构造器）。`PROJECT_INDEX.md:341` 声称"原 Wurst 遥测、官网入口和远程更新检查已从活动路径移除"——**官网调用这条不成立**。
- 其他外部端点（主机名普查）：`p.music.163.com`/`music.163.com`/`p1.music.126.net`（网易云音乐 API，纯手写客户端，**无网易 jar 依赖**）、`127.0.0.1:3000/4000`（Twilight Echo 本地桥）、`api.openai.com`（×2，用户可配置端点 + `ModelSettings.java:197-204` 中的用户密钥）、`translate.google.com`、`login.live.com`/`xboxlive`/`mojang`（账号登录）。

### A14 · 仓库卫生（P1）

**ignore 规则缺口**——以下大目录**未被忽略**（在 `git status` 中显示为 `??`）：

- `nickname/`… 实测为：`official/`（Mojang .class dump）、`native/ffmpeg/`、`wurstb_profiles/`、`free0810/`（含 .docx）、`_errreport/`
- **`.gradle-compose-cache/` 未被忽略，且已有 14 个文件被跟踪**（Gradle 8.11 缓存，含 4 个 `.lock` 与 checksums/executionHistory `.bin`）
- `.claude/settings.local.json` **只靠机器全局的** `C:\Users\ui863/.config/git/ignore:1` 才被忽略——**对其他克隆不可复现**
- 死规则：`.gitignore:24` 的 `!newforge/*/libs/*.jar`（`newforge/` 不存在）

**已正确忽略**：`run/`（`:16`）、`.test/`（`:41`）、`_smoke/`（`:69`）、`download/`（`:30`）、`build/`（`:3`，未锚定 = 覆盖 67 个工程的 build 目录）、`source/`（`:28`）、`_artifacts/`（`:27`）、`_tools/`（`:66`）、`.recon/`（`:74`）、`.reasonix/`（`:80`）。根目录散落的大文件也已忽略：6 个 `.zip`（含 `PingFangSC-main.zip` 73.6 MB、`CozyUI+_v1.10.zip` 41.0 MB）经 `*.zip`，34 个 `*-installer.jar.log`（54.6 MB）经 `*.log`。

**26 个嵌套 `.gitignore`**，一致性存疑（见 B4 的 wrapper jar 问题）。

**未跟踪的参考/供应商材料**（0 个跟踪文件）：`source/`、`_artifacts/`、`_tools/baritone-*-src`、`_recon/`、`free0810/`、`official/`、`forge-mdk/`、`native/`、`.test/`、`_smoke/`、`download/`，以及根目录 6 个 `.zip`。**没有任何文档说明它们的来源、许可或是否可删。**

**293 个被跟踪的 `.disabled` 文件**——通过 `build.gradle` 的 `exclude "…/component.disabled/*.java"` 等排除编译。这是**有意保留的死代码存档**，不是构建垃圾。

**发布的 jar 需要手工修补**：`scripts/replace-jar-entry.ps1` 做构建后 JAR 手术（脚本自己记录了一个 ZipArchive Update 模式导致文件损坏的 bug）。`README.md:49-52` 也承认 v1.5.0 的 1.20.1 jar"无法再逐字节重建"。**产物不可复现。**

**工作区 70 GB 中约 46 GB 是可再生的本地环境**：`run/` 34.7 GB（`run/wurst` 34.4 GB 是开发客户端运行时）、`.test/` 8.7 GB、`_smoke/` 2.8 GB。这些都已正确忽略，但**任何文档都没提**，也没有"哪些可以安全删除"的指引。

### A15 · 工具脚本覆盖面（P2）

- `scripts/build-all.ps1`（26 KB）只构建 **18/67** 个工程（6 个 MC 版本 × 3 加载器）。
- `scripts/common.ps1` 的 `Get-WurstbJdkHome` 只为 6 个 MC 版本映射 JDK，其他版本直接 `throw "Unknown Minecraft version for JDK lookup: …"`（`:107`）。
- `scripts/verify-all-projects.ps1` 覆盖 20 个目标，且**硬编码** `JAVA_HOME="C:\Program Files\Java\jdk-21"`（`:29`）。
- `scripts/build-v1.5-release.py` 是唯一**动态发现**工程的脚本（glob），`projects()` 实际产出 66 个——但它的文件头注释写 63 个。
- **49/67 个工程没有任何自动化构建/校验覆盖。**

**版本号会在多处静默失配**：`mod_version` 有 **63 个不同值**对应 67 个工程（5 个 NeoForge 26.x 是裸 `1.5.0`）；`neoforge/versions/{1.21,1.21.1,1.21.11}` **硬编码** `archiveFileName = "WurstB+ Plus-v1.5.0-NeoForge-1.21.11.jar"`，而 `build-v1.5-release.py` 的 `find_jar` 只校验文件名里含 MC 版本和加载器——**版本号升了以后它仍能找到旧 jar 并当作有效资产发布，不报错**。

其余一致性缺口：`mod_group_id` 10 个工程用 `net.wurstclient`（其余 17 个用 `net.wurstpenguin`）；34 个工程带 `LICENSE.txt` 而 **33 个没有**，却仍执行 `from("LICENSE.txt")`（静默空操作）；`neoforge/versions/26.x` 设置了 Java 25 toolchain 但**漏了 `options.release`**（其余 18 个 neoforge 工程两者都有）。

---

## 四、优先级与风险矩阵

| 级别 | 问题 | 影响 | 修复难度 |
|---|---|---|---|
| **P0** | FFmpeg 对应源码未提交（LGPL 违规）+ FFmpeg 资源未跟踪且不在产物中 | 法律风险；v1.6 视频功能无法从克隆构建或交付 | 低（提交文件） |
| **P0** | `LICENSE.txt` 是 LGPL-2.1 但被当作 GPL-3.0 引用；5 个工程 `mod_license=All Rights Reserved` 与自身 `mods.toml` 冲突 | 法律风险；用户权利不明 | 低 |
| ~~**P0**~~ | ~~PingFang 字体（31 MB）无许可、来源声明无效~~ **已删除** | ~~法律风险（Apple 字体再许可）~~ **已消除** | ✅ 2026-10-06 完成 |
| **P0** | 新克隆无法构建 11/67 工程 | 可复现性断裂；最新提交标题与事实相反 | 中 |
| **P0** | v1.6 功能只覆盖 1/67 工程 | README 能力声明与交付物不符 | 高 |
| **P0** | 67 份源码副本、零共享机制 | 增长已到极限；漂移已发生（25 变体） | 高 |
| **P1** | 5 个 NeoForge 产物缺 Baritone 运行时库 | 用户启动即崩 | 低 |
| **P1** | Skiko DLL 从用户可写目录无校验加载 | 本地提权/劫持 | 低（加哈希校验） |
| **P1** | 2 处已确认的客户端线程竞争（AutoSteal / ChatTranslator） | 随机崩溃、数据错乱 | 低（`MC.execute`） |
| **P1** | 文档状态有 7 个项目数、6 个测试数版本 | 无法判断真实状态 | 中（脚本生成） |
| **P1** | 76.8% 跟踪字节是重复 PNG | 仓库/克隆成本 | 低（LFS 或去重） |
| **P1** | 无任何构建/测试 CI | 无人拦住回归 | 中 |
| **P1** | `hacks/` 278 文件零测试；`mixin/` 5.3% | 25% 代码无保护 | 高 |
| **P1** | `Setting` 基类依赖 `clickgui2.Component` | 阻止 GUI 收敛 | 中 |
| **P2** | 4 套 GUI + 3 条渲染路径并存 | 维护成本 | 高 |
| **P2** | 事件注解路径是死代码但每次开关付反射成本 | 性能、误导 | 低 |
| **P2** | 硬编码注册表（197 字段 + 后缀反射） | 易漏注册（已发生：RadialMenuHack） | 中 |
| **P2** | jar 内容不可复现（开区间 jarJar + 手工 jar 手术） | 无法验证发布物 | 中 |

---

## 五、整改建议

### 阶段 0 · 立即（数小时，止血）

1. **提交 FFmpeg 对应源码与资源**：`native/ffmpeg/**`（构建脚本 + `COPYING.*`）、`src/main/resources/assets/wurst/ffmpeg/**`、`src/test/resources/**.mp4`，并在 `.gitignore` 显式放行 `!src/main/resources/assets/wurst/ffmpeg/**`。这同时修好 A12 与 A5 的一部分。
2. **修正许可**：把根 `LICENSE.txt` 换成真正的 GPL-3.0 全文（保留 Forge MDK 文本为 `LICENSE-Forge-MDK.txt`），修正 README 徽章链接方向，统一 5 个 `mod_license=All Rights Reserved` 为 `GPL-3.0-or-later`，让 `gradle.properties` 与 `mods.toml` 一致。
3. **决策 PingFang 字体**：确认授权链或替换为 OFL 字体（Noto Sans SC 等），并在 `META-INF/licenses/` 补齐归属。
4. **修 5 个 NeoForge 产物的打包**：给 `neoforge/versions/1.20.2…1.20.6` 加 MDG `jarJar` 或 `include`，与 `neoforge/versions/1.21+` 对齐。
5. **补 `neoforge/` 的 wrapper jar**；把 `.gitignore:21` 的 `*.jar` 改为"白名单式"（先忽略全部，再逐目录放行），消除 60 个 wrapper jar 的静默丢失风险。
6. **修 ignore 缺口**：`official/`、`native/`（放行 ffmpeg 源码后再处理 DLL）、`wurstb_profiles/`、`free0810/`、`_errreport/`、`.gradle-compose-cache/`（并 `git rm -r --cached` 那 14 个已跟踪文件）；把 `.claude/settings.local.json` 从机器全局 ignore 移到仓库 `.gitignore`；删掉死规则 `!newforge/*/libs/*.jar`。
7. **修 2 处线程竞争**：`AutoStealHack`、`ChatTranslatorHack` 的 UI/网络操作改走 `MC.execute(...)`。
8. **`SkikoNatives` 加校验**：解压前比对资源哈希，已存在且哈希一致则跳过；写盘后校验；平台/架构不匹配时明确失败而非静默返回 false。

### 阶段 1 · 短期（数天，建立权威来源）

9. **建立唯一状态来源**：写一个脚本从 `gradle.properties`、`build/release-*/_build-report*.txt`、`_smoke/*.tsv` **生成**支持矩阵、测试数、产物清单，产出 `docs/STATUS.md`。文档里的数字只能来自生成，不得手写。
10. **清理文档**：删除或重写 `PROJECT_INDEX.md`（整份停留 15 工程时代）与 `PORTING-NEW-VERSIONS.md`（55/61 时代）；`PORTING_TASK.md` 拆为 `docs/archive/porting-log-2026-09.md` + 一份简短未决清单；`README.en.md` 与 `README.md` 从同一数据源生成。修正 7 个项目数、6 个测试数、SHA-256 表（实测不匹配）与 26.3 状态矛盾。
11. **恢复可复现构建**：把 Baritone jar 以正式坐标入库（或提交 `fabric/baritone-maven`），修 `seed-gradle-wrapper.ps1` 的 `tools/` 依赖（或把所需 zip 纳入版本控制），补 `!fabric/versions/*/libs/*.jar` 等例外。目标是 `git clone && gradlew build` 在 67 个工程全部成立。
12. **消除产物不确定性**：把 jarJar 的 `[0.5.4,)` 改为精确版本；`neoforge/versions/{1.21,1.21.1,1.21.11}` 去掉硬编码 `archiveFileName`；`replace-jar-entry.ps1` 的 jar 手术改为构建期正确处理。
13. **建 CI**：至少一个 workflow 对变更工程跑 `compileJava` + `test`，并用 `build-v1.5-release.py` 的 glob 逻辑做"产物存在性 + 结构校验"（zip 完好、含加载器元数据、含 Mixin 配置、含主类、**含内嵌依赖**）。这会直接拦住 A1-B1 那类缺陷。
14. **提交 FFmpeg 后重新构建并核对** v1.6 产物确实包含 `assets/wurst/ffmpeg/*`。

### 阶段 2 · 中期（数周，消除根本问题）

15. **引入共享源码树——这是全部问题的根**。推荐路径（按风险从低到高）：
    - **先做零风险的**：给 67 份 `build.gradle` 引入 `buildSrc` 或 `gradle/libs.versions.toml` + 一个约定插件，把 758 种非空行收敛成一处。仅此一项就把"改一个依赖要动 62–71 处"变成 1 处，且**不需要动任何 Java 代码**。
    - **再做高收益的**：把 **683 个"1 变体"路径（57.3%）** 提取为共享 sourceSet（`src/common/java`），各工程只保留差异文件。这一步不改任何逻辑，只删重复。
    - **最后处理 105 个"7+ 变体"路径**：这些是真正需要版本适配的，按 MC 版本段抽取版本适配层（interface + 每版本实现）。
    - 前沿做法可参考 **Architectury / NeoForge 多加载器模板 / Stonecutter**（同一份源码编译到多 MC 版本），但迁移到该模型是长期工程，建议在共享 sourceSet 稳定后再评估。
16. **把"67 个工程"降为"3 个加载器 × 若干版本段"**。当前为 1 个文件的差异维护 790 文件的树（`26.1` vs `26.1.1`），这类工程应直接合并或改为构建变体（`-PmcVersion=26.1.1`）。
17. **重新设计注册机制**：改为注解处理器或 ServiceLoader 生成注册表，消除"字段名后缀反射"和 197 个手写字段。顺带删掉 `RadialMenuHack` 的每帧自注册。
18. **解开 `Setting → clickgui2.Component`**：把 `getComponent()` 从 `Setting` 基类拆到 GUI 侧的访问者/映射表，让配置模型不再依赖具体 GUI。这是收敛 4 套 GUI 的前置条件。
19. **GUI 与渲染路径收敛**：明确保留一套 GUI（建议 SuperSoft 或 Epsilon 面板）+ 一套渲染路径，其余标记 deprecated 并给出删除时间表；删掉只有 2 个 import 者的 `compose/`；评估 Skiko（1,053 LOC / 4 个调用点 vs 26.5 MB 原生载荷）的投入产出。
20. **补关键测试**：优先 `hacks/combat`（Killaura、MultiAura、CrystalAura、AnchorAura）的纯策略单测——这些模块的逻辑本就可以脱离游戏运行（项目已有 `CombatActionPolicy`、`CombatAimPointPlanner` 这类纯策略类，正是为可测性准备的）。其次是 `mixin` 的注入点存在性校验（可静态比对目标类字节码，`PORTING_TASK.md` 里已经用过 `javap` 方法，把它固化成测试）。
21. **处理事件系统**：(a) 给 `fireAnnotated` 加 `isEmpty()` 前置判断；(b) 要么让 `@WurstSubscribe` 真正被使用，要么删掉注解路径与 `Hack.java:206/211` 的调用。当前状态是"付了成本但没拿到收益"。

### 阶段 3 · 长期

22. **仓库体积**：把字体图集与原生库迁到 Git LFS（仅 PNG 一项就能把跟踪字节从 2,117 MB 降到约 490 MB）。`mcsans_*.png` 的 67 份副本随共享源码树方案一并消失。
23. **清理未跟踪残留**：为 `official/`、`forge-mdk/`、`free0810/`、`_errreport/`、`source/`、`_artifacts/`、`_tools/`、根目录 6 个 `.zip` 建立清单文档（来源/许可/可否删除），随后删除可删部分。为 `run/`（34.7 GB）补一份"可安全删除"说明。
24. **补第三方许可清单**：为 18 个内嵌依赖建 `THIRD-PARTY-NOTICES.md`，把各库许可文本打进产物。重点核实 `vorbis-support`（自称 LGPL-3.0）与 `soundlibs`/`jflac`/`jaudiotagger` 家族（POM 与 jar 内均无许可声明）。

---

## 六、值得肯定的部分

审计不应只剩批评。以下方面质量确实高于同类项目：

1. **事件系统实现**：`WurstSubscriber` 用 `LambdaMetafactory` 构建真实的 `Consumer` callsite，分发路径无反射——claim 属实。变更时不可变快照（`ConcurrentHashMap` + 写时换新 `ArrayList`）+ `LongAdder` 计数是正确且高效的并发设计。`remove()` 清理三张 map 并附带了已复现 NPE 的注释。
2. **代码卫生**：1,062 个文件里 **0 个 TODO/FIXME**、**0 个非 ASCII 标识符**、风格一致、license 头齐全。这在同规模客户端项目里罕见。
3. **真正在起作用的架构测试**：`MixinPackageIsolationTest` 加载 `wurst.mixins.json` 强制"`mixin/` 包只放 Mixin"的规则——不是形式化测试，是有效的边界守卫。
4. **`addon/` 扩展点设计**：`ServiceLoader` 发现、重名冲突校验、逐 addon 故障隔离（`RuntimeException|LinkageError` 捕获后继续）、`Objects.requireNonNull` 契约。唯一问题是没被验证和使用（缺 service 文件、缺示例、缺测试）。
5. **`AsyncTextureLoader` 的两阶段设计**：后台解码 + 客户端线程 GL 上传，64 MB 输入上限、daemon 线程、上传失败时 `image.close()`——设计是对的，只是漏了拒绝策略的处理。
6. **`EntityOcclusionCuller` 的线程亲和**：`RenderSystem.assertOnRenderThread()` 显式断言，`GL_QUERY_RESULT_AVAILABLE` 非阻塞轮询（不 stall），`close()` 在非渲染线程时正确改走 `recordRenderCall`。
7. **遥测与更新检查确已移除**：67 个工程的 `WurstUpdater` 都是自我注销的 stub，MC 遥测由 Mixin 强制关闭。
8. **CozyUI 的许可处理**：`META-INF/licenses/cozyui/` 在 67 个工程里都完整附带归属与三份第三方许可文本——这是仓库里做得最规范的一处合规实践，应作为其他第三方内容的模板。
9. **`docs/` 的工程量**：234 个跟踪文件，其中 203 个是逐 Hack 的移植裁决记录（`docs/openeepsilon-verdicts/`），说明移植过程是逐个核对过的，不是拍脑袋。

---

## 七、结论

项目的**技术判断力**是够的：它吸收 LiquidBounce / Meteor / FrogClient 的成熟设计并重写为 Forge 1.20.1 Mojmap，把战斗、旋转、库存、放置拆成可测的纯策略类，测试也主要集中在这些地方（`util` 49.6%、`music` 67.9%、`twilight` 105.6%）。

它缺的是**工程化的版本策略**。当前 67 个独立工程 + 22 个快照分支 + 手工复制源码的组合，使得：

- 每一个功能变更的成本随版本数线性增长（v1.6 的 196 个文件 × 66 棵树）；
- 每一次修改都会累积漂移（`WurstClient.java` 已有 24 个版本）；
- 状态无法被任何单一文档或脚本描述（7 个项目数、6 个测试数）；
- 正确性无法被自动化保证（CI 不构建任何东西，49/67 工程无脚本覆盖）。

**建议的决策点是阶段 2 的第 15 项**：是否把 67 份源码副本换成"共享源码树 + 版本适配层"。在这之前，阶段 0 和阶段 1 的措施（合规修补、可复现构建、脚本生成状态、建 CI）成本低、收益立竿见影，**且不与任何长期方案冲突**——无论最终选哪条路，这些都必须做。

---

## 附录 A · 可复现的度量脚本

审计过程中创建的脚本（未跟踪，位于 `_recon/`，可直接删除）：

| 文件 | 用途 |
|---|---|
| `_recon/dedup-audit.ps1` | 计算 67 棵源码树的物理 vs 唯一体积、冗余倍数 |
| `_recon/dup-audit.ps1` | 逐工程与根工程做 MD5 比对（同/异/独有/缺失） |
| `_recon/dup-manifest.csv` | 上者的原始输出数据 |
| `_recon/sibling-diff.ps1` | 同族相邻版本工程的逐文件差异比对 |
| `_recon/feature-gap.ps1` | 逐工程检查 v1.6 包与 Hack 的存在性 |
| `_recon/shareability.ps1` | 计算"每个路径有几种不同内容"，得出可共享比例 |

运行方式（受执行策略限制，需在进程内执行）：

```powershell
Get-Content _recon/shareability.ps1 -Raw | Invoke-Expression
```

## 附录 B · 本次审计未能验证的事项

1. **未执行任何 Gradle 构建**，因此"哪些工程当前能编译"未经实测。所有 LOC / 文件数 / 结构结论均为静态分析结果。
2. **未启动游戏**，所有运行时行为判断（性能、竞争、崩溃）为静态分析或推断，非实测。
3. **未访问网络**，因此 GitHub Release 的实际资产清单/哈希、上游 Wurst7 的 hack/command 计数无法核对。A1-B1 的"5 个 NeoForge 产物缺运行时库"仅在**本地 `build/libs` 产物**上得到证实。
4. `docs/ANTICHEAT.md` 的反作弊统计指向一个不在仓库中的第三方 jar，无法核实。
5. `CHANGELOG.md` 的视频解码基准（3.8 → 95–235 fps）为硬件相关实测值，且 JCodec 路径已删除，无法复现。
6. `soundlibs`/`jflac`/`jaudiotagger`/`netty`/`zxing`/`java-stream-player` 的上游许可身份（其 POM 与 jar 内均无声明）只能依据外部知识推断，**标记为推断而非事实**。
7. 本地 jar 中的 FFmpeg 是 GPL 还是 LGPL 构建未经确认（`license-ffmpeg.txt` 存在但未跟踪，且未读取）。
8. 方法/字段计数基于正则（缩进与 Allman 花括号感知），非 AST 解析；相对排名可靠，绝对总数约有 ±5% 误差。
9. Mixin 的运行时正确性未验证：75 个文件含 123 个 `@Inject`、56 个 `@Shadow`、145 个 `@At`、5 个 `@WrapOperation`、4 个 `@Redirect`、0 个 `@Overwrite`，**未逐一验证注入点是否匹配真实的 Forge 1.20.1 目标**。
10. `hacks/CaveFinderHack` 与 `SearchHack` 的 `ForkJoinPool → BlockVertexCompiler → EasyVertexBuffer` 路径是否把 VBO 上传交接给渲染线程，**未追踪确认**，标记为未验证风险而非确认缺陷。
