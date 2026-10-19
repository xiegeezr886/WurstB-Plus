# 工作区卫生：`reference/` 的来源、许可与可否删除

本文件回答三个问题：仓库里那些**未被跟踪的大目录是什么**、**它们的许可状态**
、以及**哪些可以直接删**。

背景：本仓库是 67 个独立 Gradle 构建的多版本工程（见 [PROJECT-STRUCTURE.md](PROJECT-STRUCTURE.md)），
开发过程中积累了大量参考材料 —— 上游客户端源码、反编译产物、崩溃日志、逆向证据。
它们**一律不进版本库**（不构成再分发），但此前散落在仓库根目录，与 67 个工程目录
和构建输入混在同一层。

2026-10-13 起全部收进单一的 **`reference/`** 树，由 `.gitignore` 的 `/reference/`
一条规则覆盖。**新增此类材料请放进 `reference/`，不要放回根目录。**

> ⚠️ **许可边界**：`reference/upstream-sources/` 与 `reference/decompiled/` 含
> **无许可证的反编译源码**与**竞品客户端产物**。项目既有的移植优先级
> （见 [RELEASE.md](RELEASE.md)）已声明"无许可证反编译源码只核对行为，不直接复制"。
> **共享或发布工作区前，先删掉这两个目录**（见 [§3](#3-可以安全删除的部分)）。

---

## 一、路径映射（旧 → 新）

| 旧位置（根目录） | 新位置 | 文件数 | 体积 |
| --- | --- | ---: | ---: |
| `source/` | [`reference/upstream-sources/`](../reference/upstream-sources) | 39,653 | 1,928.7 MB |
| `_tools/` | [`reference/tools/`](../reference/tools) | 11,484 | 501.3 MB |
| `_smoke/` | [`reference/smoke/`](../reference/smoke) | 1,549 | 2,723.6 MB |
| `_artifacts/` | [`reference/artifacts/`](../reference/artifacts) | 1,863 | 377.3 MB |
| `_recon/` | [`reference/recon/`](../reference/recon) | 371 | 112.3 MB |
| `.recon/` | [`reference/recon-wallpaper/`](../reference/recon-wallpaper) | 116 | 16.4 MB |
| `_errreport/` | [`reference/errreport/`](../reference/errreport) | 19 | 13.2 MB |
| `free0810/` | [`reference/vendor/free0810-configs/`](../reference/vendor/free0810-configs) | 33 | 12.7 MB |
| `wurstb_profiles/` | [`reference/vendor/free0810-profiles/`](../reference/vendor/free0810-profiles) | 4 | 0.02 MB |
| `_porting_stale/` | [`reference/porting-stale/`](../reference/porting-stale) | 472 | 1.7 MB |
| `official/` | [`reference/decompiled/mojang-1.21.2-dump/`](../reference/decompiled/mojang-1.21.2-dump) | 16 | 0.1 MB |
| （新建，非迁移，**已含在上面 `upstream-sources` 行内**） | `reference/upstream-sources/FatalityClickGui/`（66 / 14.7 MB）与 `Reversal/`（7,597 / 462.5 MB） | — | — |
| `forge-mdk/` | **已删除**（见 §2） | — | — |
| **合计** | `reference/` | **64,076** | **≈5.9 GB**（6,006 MB） |

> **目录分类说明**
>
> - [`reference/clients/`](../reference/clients) —— 行为对照用的客户端源码集（OpenEpsilon、
>   OpenOpal、OpenZen、OpenMyau、OpenVape、Rise），索引见
>   [clients/README.md](../reference/clients/README.md)。
> - `reference/upstream-sources/FatalityClickGui/` —— **fatality.win 风格的 ClickGUI
>   独立组件库**：31 个 `Fa*.java`（3,645 行）+ `fa_model.fsh` + 4 个字体，
>   **无构建文件**，按仓库自带 README 的接入指南使用。它是本项目 GUI 的参考实现。
> - `reference/upstream-sources/Reversal/` —— **基于 MCP 1.8.9 的 PVP 客户端**
>   （`Stars-Azusa/Reversal`，仓库自述"供纪念使用"），**GPLv3**，162 个提交，
>   2,907 个 `.java`。它的 `.git/` 占 **300.3 MB**（462.5 MB 里的大头），
>   净内容只有 162.2 MB —— 只读参考时可删 `.git/`。
>
> 上述两项都是 `upstream-sources/` 的**子目录**，文件数与体积**已计入上表
> `upstream-sources` 一行**，不要重复相加。

---

## 二、已删除的内容（2026-10-13）

三项，均经确认**不被任何 `build.gradle`、脚本或文档引用**：

| 路径 | 是什么 | 依据 |
| --- | --- | --- |
| `gradle/gradle/wrapper/gradle-wrapper.jar` | 意外重复的 wrapper 目录 | `.gitignore` 原本就注明"accidental duplicate"；真正的 wrapper 在 `gradle/wrapper/` |
| `libs/` | 空目录 | 0 个文件；`.gitignore` 的 `/libs/` 规则同为死规则 |
| `forge-mdk/gradle/wrapper/gradle-wrapper.jar` | 只剩一个 wrapper jar 的残留目录 | `forge-mdk/` 无任何跟踪文件，仅此一个 jar |

### 顺带修掉的死引用

| 位置 | 问题 | 处理 |
| --- | --- | --- |
| `.gitignore` | `source_archives/` 规则指向**从不存在的目录** | 删除规则并注明原因 |
| `.gitignore` | 注释引用 `docs/REPO-HYGIENE.md`，而该文件不存在 | 即本文件，已补齐 |
| `docs/PROJECT-STRUCTURE.md` §四 | 指向已迁走的 `_porting_stale` | 改为 `reference/porting-stale` |
| 根目录 `ARCHITECTURE_AUDIT.md` / `AUDIT-ROUND2.md` | 历史审计报告与正式文档并列在根目录 | `git mv` 到 [`docs/audit/`](audit/)，两轮报告互为链接 |

### 未删除、但已标注为"可删"的根目录残留

根目录仍有 5 个 `.zip`（共 ≈59 MB，经 `*.zip` 忽略）。`THIRD-PARTY-NOTICES.md`
原先记作 6 个，实际为 5 个（`PingFangSC-main.zip` 已被删除）：

| 文件 | 体积 | 说明 |
| --- | ---: | --- |
| `CozyUI+_v1.10.zip` | 39.1 MB | 字体资源来源包；**许可文本已随 67 个工程跟踪**（`META-INF/licenses/cozyui/`），删包不影响合规 |
| `free0810[暴力and安全]..zip` | 12.24 MB | 与 `reference/vendor/free0810-configs/` 内容重叠 |
| `Mineradio-main.zip` | 4.96 MB | 上游源码包 |
| `SuperSoftClient-Compose-for-MC-1.21.4-master.zip` | 2.19 MB | 上游源码包；已解包在 `reference/upstream-sources/` |
| `baritone-1.10.3.zip` | 0.69 MB | Baritone 上游产物 |

**本次未删除**——如需彻底清空根目录，这 5 个包可整体移入 `reference/upstream-sources/`。

---

## 三、可以安全删除的部分

按"删了要不要重建"分三类。

### 3.1 可自由删除（可再生或纯证据）

| 目录 | 体积 | 重建方式 |
| --- | ---: | --- |
| `reference/smoke/` | 2.7 GB | `python scripts/smoke-launch.py --all`（日志、截图、探针结果，删了只丢历史证据） |
| `reference/artifacts/` | 377 MB | 各工具重跑（解包产物） |
| `reference/recon-wallpaper/` | 16 MB | 逆向脚本可重跑；结论已固化在 [wallpaper-engine-scene.md](wallpaper-engine-scene.md) |
| `reference/recon/` | 112 MB | 审计度量脚本；结论已固化在 [audit/](audit/) |
| `logs/` | 0.4 MB | Gradle 自动重建 |
| `build/`（67 个工程各自的） | 2.5 GB | `gradlew build` |

### 3.2 删除前请确认（含唯一副本）

| 目录 | 体积 | 风险 |
| --- | ---: | --- |
| `reference/upstream-sources/` | 1.45 GB | **无许可证的反编译源码与竞品产物**。删了不可从本仓库恢复；共享工作区前**应当**删除 |
| `reference/tools/` | 501 MB | 含 baritone 各版本源码树与 `baritone-26.2-compat` 补丁器；`scripts/patch-baritone-26.2.ps1` 依赖它 |
| `reference/errreport/` | 13 MB | 崩溃日志与 5 份缺陷报告（`issues/`），是复现依据 |
| `reference/vendor/` | 12.7 MB | 第三方配置与 profile dump，外部来源 |
| `reference/decompiled/` | 0.1 MB | Mojang 字节码 dump，删了需重新 `javap` |
| `reference/porting-stale/` | 1.7 MB | 已被取代的移植产物，仅供比对 |
| `download/` | 526 MB | v1.5/v1.6 发布产物；**发布件不要随手删** |
| `baritone-maven/` | 310 MB | **构建输入**，见 §4，不要删 |
| `native/` | 7.5 MB | 部分跟踪（LGPL "corresponding source" 义务），见 `.gitignore` |

### 3.3 本地环境（占绝大部分体积，全部可再生）

| 目录 | 体积 | 说明 |
| --- | ---: | --- |
| `.test/` | ≈40.1 GB | PCL 测试实例、库、原版资源 |
| `run/` | ≈35.0 GB | 开发客户端运行时 |
| 其余 | — | `.gradle/`、`.kotlin/`、`.gradle-compose-cache/` 等 Gradle 缓存 |

> **工作区约 105 GB，其中 ≈77 GB 是这两个可再生的本地环境目录。**
> 需要腾空间先删它们，代价只是下次启动要重新下载/解压。**不要**用 `gradlew clean`
> 清缓存 —— Loom / NeoGradle 缓存会被破坏，见 [RELEASE.md](RELEASE.md)。

---

## 四、不能移动的路径（硬编码依赖）

整理时确认以下路径被代码或脚本**硬编码**，改动它们会静默破坏工具：

| 路径 | 谁在依赖 | 后果 |
| --- | --- | --- |
| `gradle/wurstb-versions.properties` | 5 个 neoforge 工程（1.20.2–1.20.6）把它当作**仓库根哨兵**向上查找 | 移动 → 这些工程找不到仓库根 |
| `baritone-maven/**` | 各工程的 `flatDir` / maven 依赖声明 | 移动 → 构建解析失败 |
| `native/ffmpeg/`（跟踪部分） | LGPL-2.1 "corresponding source" 义务 | 移动 → 合规义务落空 |
| `/config/` | MC 客户端从仓库根启动时写入 | 仅位置约定 |

以下路径的引用**已随本次整理同步更新**（改了脚本，不是改路径）：

| 脚本 | 改动 |
| --- | --- |
| `scripts/generate-status.ps1` | 排除模式由 `source\|_tools\|_artifacts\|forge-mdk` 改为 `reference`（**关键**：`reference/` 下有 40+ 个自带 `build.gradle` 的树，不排除会让 67 工程的计数漂移） |
| `scripts/smoke-launch.py` | `LOGDIR` → `reference/smoke` |
| `scripts/provision-smoke-clients.py` | `WORK` → `reference/smoke/provision` |
| `scripts/audit-mixin-registration.ps1` | `-TsvPath` 默认值 → `reference/recon/mixin-gaps.tsv` |
| `scripts/compare-upstream-mixins.ps1` | `_recon` → `reference/recon`（git 目录、输出目录、CSV 路径） |
| `scripts/patch-baritone-26.2.ps1` | `$toolRoot` → `reference/tools/baritone-26.2-compat` |
| `scripts/build-all.ps1` | 注释里的路径 |

**限制**：`PORTING_TASK.md` 正文中的 `_smoke/*`、`_tools/*` 引用**未逐条改写** ——
它是历史工作记录，条数多且与当时结论绑定。按上表映射换算即可。

---

## 五、放进 `reference/` 的规矩

1. **未跟踪**：`reference/` 整棵树由 `.gitignore` 忽略，不要 `git add -f`。
2. **不参与构建**：任何工程都不得把 `reference/` 当依赖源。构建输入放
   `baritone-maven/`、`versions/*/libs/`、`native/` 等已声明的跟踪位置。
3. **不随包分发**：这里的东西不进 jar。新第三方资源的许可处理照
   `src/main/resources/META-INF/licenses/` 的既有做法（归属 + SPDX + 证据 + 正文）。
4. **写清来源**：新增子目录时在本文件的 §1 表格补一行 —— 来源、许可、可否删除。
