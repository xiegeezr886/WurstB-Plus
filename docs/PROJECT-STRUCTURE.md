# 工程结构：67 个独立 Gradle 构建

本文件解释**为什么**仓库长成现在这样、这套结构的代价是什么、以及新增一个 Minecraft 版本时该怎么做。

数字请看自动生成的 **[STATUS.md](STATUS.md)**——本文只写机制与决策。

---

## 一、布局

```
.
├── src/                    根工程 · Forge 1.20.1 · v1.6.0（唯一带 v1.6 子系统的工程）
├── fabric/                 Fabric 1.20.1
├── neoforge/               NeoForge 1.20.1
├── versions/<mc>/          Forge 新版本工程        20 个
├── fabric/versions/<mc>/   Fabric 新版本工程       22 个
├── neoforge/versions/<mc>/ NeoForge 新版本工程     22 个
└── gradle/
    ├── wurstb-versions.properties   ← 依赖版本单一来源
    └── wrapper/
```

合计 **67 个独立 Gradle 构建**（3 个根工程 + 64 个版本工程），覆盖 23 个 Minecraft 版本。

**"独立"是字面意思**：每个工程都有自己的 `settings.gradle`、`build.gradle`、`gradle.properties`
和 `gradle/wrapper/`。根 `settings.gradle` 只设置 `rootProject.name`，**没有** `include` 任何子工程——
它们不是 composite build，`gradlew` 也不能从根目录一次构建全部。

其中 `versions/1.20.5` 与 `versions/1.21.2` **不存在**，因为 Forge 没有发布这两个版本；
对应 MC 版本只有 `fabric/versions/` 与 `neoforge/versions/` 两侧。

## 二、为什么是"完整复制的源码树"

每个工程都持有一份**完整复制**的 `src/main/java`（约 740–795 个文件），
**不存在共享 sourceSet**。这是历史选择：早期为了让每个版本能独立改代码、独立构建、独立打包，
直接复制整棵树是最省事的路。

### 代价（实测）

| 指标 | 实测值 |
| --- | --- |
| 67 棵 `src/main/java` 的物理文件总数 | 见 [STATUS.md](STATUS.md) |
| 其中**不同内容**的文件数 | 2,958 |
| 冗余倍数 | **10.9×**（唯一体积 15.4 MB vs 物理 168.8 MB） |
| 不同源文件**路径**数 | 1,191 |
| 其中在所有出现它的工程里**逐字节相同** | **683 个（57.3%）** |
| 同一路径下的**不同内容**最多 | **25 种**（`gui/title/WurstTitleMenu.java`） |
| `WurstClient.java` 的不同版本 | **24 种** |

**57.3% 的源文件从未被版本化定制过**——它们不是"为了适配不同 MC 版本而必须分叉"，
而是纯粹的复制粘贴。

同族相邻版本的差异极小：`versions/26.1` vs `26.1.1` 有 **789/790 个文件逐字节相同**（只差 1 个），
`versions/1.20.3` vs `1.20.4` 同样是 766/767。为 1 个文件的差异维护一整棵 790 文件的树，×3 个加载器。

### 另外还有一层重复：版本快照分支

`scripts/sync-version-branches.py` 定义 **22 个版本分支**，由
`.github/workflows/sync-version-branches.yml` 在每次 push `main` 时自动重建——
每个分支从 `main` 过滤出该版本的工程目录（约 2,400 个 `.java`）。

所以同一份代码**同时在"目录维度"（67 个目录）和"分支维度"（22 个分支）各存一份**。
分支是派生的，目录是手工维护的，**没有任何机制把源码变更同步到那 66 个目录**。

### 现状结论

> 这是本项目最大的结构性债务。它使得每个功能变更的成本随版本数线性增长
> （v1.6 的 196 个文件 × 66 棵树），每次修改都会累积漂移。
>
> 收拢路径（按风险从低到高）：
> 1. **依赖版本**已收拢到 `gradle/wurstb-versions.properties` + `scripts/dependency-versions.ps1`（见下）。
> 2. 把 **683 个"零变体"文件**提取为共享 sourceSet，各工程只保留差异文件——不改逻辑，只删重复。
> 3. 处理剩下的 **105 个重度分叉**（7+ 变体）路径，按 MC 版本段抽适配层。
> 4. 评估 Architectury / Stonecutter 等多加载器单源码方案（长期）。

## 三、依赖版本：单一来源

67 个工程各自声明依赖，此前升级一个依赖要手工改最多 67 个文件，且没有东西发现漂移。

现在：

```powershell
# 一致性闸门（CI 用；漂移则退出码 1）
pwsh -File scripts/dependency-versions.ps1 -Check

# 查看坐标/版本/工程矩阵
pwsh -File scripts/dependency-versions.ps1 -Report

# 升级：先预演，加 -Apply 才写盘
pwsh -File scripts/dependency-versions.ps1 -Bump "io.netty:netty-codec-socks" "4.1.82.Final" "4.1.118.Final"
pwsh -File scripts/dependency-versions.ps1 -Bump "io.netty:netty-codec-socks" "4.1.82.Final" "4.1.118.Final" -Apply
```

`gradle/wurstb-versions.properties` 是声明式的"允许值"清单。工具会读它并核对全部 67 个
`build.gradle`；**未登记**的坐标/版本、以及**登记了却没人用**的条目都会报错。

> **为什么要保留 `build.gradle` 里的字面量**，而不是让每个工程 `apply from:` 一个共享脚本？
> 因为一个共享脚本出错会同时弄坏 67 个无法快速验证的构建（ForgeGradle 要反编译 Minecraft，
> 单工程构建以十分钟计）。当前方案拿到同样的收益（一处编辑、一条命令升级、CI 可拦截漂移），
> 而把风险留在纯文本层面。

### 已知的、**有意保留**的多版本坐标

工具在 `-Check` 通过时会列出它们。当前有 7 个：

| 坐标 | 为什么保留多版本 |
| --- | --- |
| `io.netty:netty-codec-socks` / `netty-handler-proxy` | **必须与各 MC 版本自带的 netty 一致**，否则运行期会撞 API 差异。分三个版本带：`4.1.82.Final`（1.20.x / 早期 1.21）、`4.1.118.Final`（1.21.5–1.21.11）、`4.2.7.Final`（26.x）。**不要合并。** |
| `baritone:baritone-*`（4 个坐标） | Baritone 每个 MC 版本有各自的构建产物，按版本固定。 |
| `org.spongepowered:mixin` | ⚠ **真实冲突，待决策**：根工程用 `0.8.5`，其余 12 个用 `0.8.7`（其中 26.x 的 5 个是**注释掉**的）。二者必有一个过期，但改它需要实际构建验证 Mixin refmap 生成结果。 |

## 四、如何新增一个 Minecraft 版本

以新增 MC `X` 的 Forge 为例：

1. **建目录**：复制一个最接近的现有工程（例如 `versions/1.21.7/` → `versions/X/`），
   连同 `gradlew`、`gradlew.bat`、`gradle/wrapper/`（**必须含 `gradle-wrapper.jar`**）一起复制。
2. **改 `gradle.properties`**：`minecraft_version`、`forge_version`、`mod_version`（格式
   `v<版本>-Forge-X`）、`mod_id`、`maven_group`。
3. **改 `build.gradle`**：MC/Forge 版本、Java toolchain（1.20.2–1.20.4 → 17，1.20.6–1.21.x → 21，
   26.x → 25）、加载器插件版本。**依赖版本不要写新数字**——先去
   `gradle/wurstb-versions.properties` 登记，再用 `dependency-versions.ps1 -Check` 核对。
4. **跟着改源码**：新 MC 版本几乎总有被移除/改签名的原版方法。参考
   [PORTING_TASK.md](../PORTING_TASK.md) 里按 `javap` 核对真实 jar 的做法，
   不要凭"版本号相邻"推断 API 相邻。
5. **`_porting_stale` / `scripts/audit-mixin-*.py`**：已有离线审计脚本可一次性扫三棵树找失效注入点。
6. **打包校验**：`zip` 完好、含加载器元数据、含 Mixin 配置、含主类、**含内嵌依赖**
   （`neoforge/versions/1.20.2…1.20.6` 曾经 5 个工程完全没有内嵌 Baritone 运行时，
   产物体积 26.9 MB 却没有任何 `META-INF/jarjar/` 条目）。
7. **注册到版本分支同步**：把新版本加进 `scripts/sync-version-branches.py` 的 `BRANCHES`。
8. **重新生成状态**：`pwsh -File scripts/generate-status.ps1`，提交 `docs/STATUS.md`。

## 五、构建与运行

```powershell
# 单个工程（以根工程为例）
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\gradlew.bat compileJava --offline
.\gradlew.bat test --offline
.\gradlew.bat build --offline

# 批量（注意：只覆盖 18 个"已发布"工程，不是全部 67 个）
pwsh -File scripts/build-all.ps1 -Offline
```

**已知的环境陷阱**

| 陷阱 | 说明 |
| --- | --- |
| `scripts/common.ps1` 只映射 6 个 MC 版本的 JDK | 其他版本调 `Get-WurstbJdkHome` 会直接 `throw`，需要 `WURSTBPLUS_JAVA17/21/25` 环境变量 |
| `scripts/build-all.ps1` / `verify-all-projects.ps1` 只覆盖 18 / 20 个工程 | 其余 47 个没有自动化构建覆盖；`build-v1.5-release.py` 是唯一动态发现全部工程的脚本 |
| `verify-all-projects.ps1` 硬编码 `JAVA_HOME` | 指向固定路径的 jdk-21 |
| `scripts/seed-gradle-wrapper.ps1` 依赖仓库外的 `tools/` 目录 | 该目录不在仓库里，离线播种流程不可从克隆复现 |
| `clean` 会破坏 Loom / NeoGradle 缓存 | 见 [RELEASE.md](RELEASE.md) |
| Fabric 用 `remapJar` 而不是 `build` | 见 [PORTING_TASK.md](../PORTING_TASK.md) |

**`.ps1` 脚本必须带 UTF-8 BOM**。本机的 PowerShell 是 **Windows PowerShell 5.1**，
它按系统 ANSI 代码页（本机为 GBK）读取无 BOM 的 `.ps1`；含中文注释或 `✓`/`✗` 这类字符的脚本
会直接解析失败。`scripts/generate-status.ps1` 与 `scripts/dependency-versions.ps1` 都已带 BOM，
新增脚本请照做。

## 六、相关文档

| 文档 | 内容 |
| --- | --- |
| [STATUS.md](STATUS.md) | 权威状态数字（自动生成） |
| [RELEASE.md](RELEASE.md) | 发布与维护手册 |
| [PORTING-NEW-VERSIONS.md](PORTING-NEW-VERSIONS.md) | 逐版本移植状态 |
| [CONFIG-FORMAT.md](CONFIG-FORMAT.md) | 配置文件格式 |
| [../THIRD-PARTY-NOTICES.md](../THIRD-PARTY-NOTICES.md) | 第三方许可与归属 |
