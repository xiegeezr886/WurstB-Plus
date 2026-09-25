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
| [docs/RELEASE.md](docs/RELEASE.md) | **发布与维护手册**（原 README）：**功能说明**（新增子系统、新增 Hack 清单、HUD 元素、已重构机制、架构概览）、**安装**、**构建与运行**、产物矩阵、打包校验、验证状态 |
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
