<div align="center">

[简体中文](README.md) · English

<img src="logo.png" alt="WurstB+ Plus" width="620"/>

[![Release](https://img.shields.io/github/v/release/xiegeezr886/WurstB-Plus?style=flat-square&label=release&color=007CFF)](https://github.com/xiegeezr886/WurstB-Plus/releases)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1~26.2-3C8527?style=flat-square)](#version-support)
[![Loaders](https://img.shields.io/badge/Loaders-Forge%20%7C%20NeoForge%20%7C%20Fabric-6E6E6E?style=flat-square)](#version-support)
[![Java](https://img.shields.io/badge/Java-17%20%7C%2021%20%7C%2025-E76F00?style=flat-square)](#version-support)
[![Gradle projects](https://img.shields.io/badge/Gradle_projects-64-4C1D95?style=flat-square)](#repository-layout)
[![preview](https://img.shields.io/badge/preview-v1.6.0-8A2BE2?style=flat-square)](https://github.com/xiegeezr886/WurstB-Plus/releases/tag/v1.6.0)
[![License](https://img.shields.io/badge/license-GPL--3.0-2E7D32?style=flat-square)](LICENSE.txt)

</div>

**WurstB+ Plus** is a Minecraft utility mod built on the code structure of
[Wurst](https://github.com/Wurst-Imperium/Wurst7). The same feature set is carried to three mod
loaders — **Forge / NeoForge / Fabric** — and **22 Minecraft versions**, maintained as 64
independent Gradle projects.

---

## Version support

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

<sub>Minecraft **1.20.5** and **1.21.2** never had an official Forge release, so those two versions
ship NeoForge and Fabric only.</sub>

**Asset naming** (the version number carries no `v`):

```text
WurstB+.Plus-<version>-<loader>-<mc>.jar      e.g. WurstB+.Plus-1.5.0-Forge-1.21.5.jar
```

| Release | Contents | Notes |
| --- | --- | --- |
| [**v1.5.0**](https://github.com/xiegeezr886/WurstB-Plus/releases/tag/v1.5.0) | 64 assets | The main release, covering every version |
| [**v1.6.0**](https://github.com/xiegeezr886/WurstB-Plus/releases/tag/v1.6.0) | Forge 1.20.1 | Prerelease, carries the new v1.6 subsystems |

> **Note:** Not all three 1.20.1 projects are v1.5. The **root Forge 1.20.1 project is v1.6.0**
> (the only one with the v1.6 subsystems), while `fabric/` and `neoforge/` are still v1.5.0. The
> `WurstB+.Plus-1.5.0-Forge-1.20.1.jar` asset in the v1.5.0 release is the artifact from that
> release (the project has since moved on, so it cannot be rebuilt byte-for-byte) and is kept
> as-is. For a v1.6 Forge 1.20.1 build, use the v1.6.0 release.

---

## Repository layout

```text
.
├── src/                    root project  · Forge 1.20.1   · v1.6.0
├── fabric/                 Fabric 1.20.1                  · v1.5.0
├── neoforge/               NeoForge 1.20.1                · v1.5.0
├── versions/               Forge new-version projects      19
├── fabric/versions/        Fabric new-version projects     21
├── neoforge/versions/      NeoForge new-version projects   21
├── docs/                   design, porting and verification docs
├── scripts/                build, test and diagnostic scripts
└── build.gradle            root project build config
```

**19 + 21 + 21 = 61 version projects**, plus the 3 root 1.20.1 projects, for **64 independent
Gradle builds** in total. Every project carries a complete `gradle-wrapper.jar`, so the Gradle
distribution resolves even offline.

---

## Documentation

| Document | Contents |
| --- | --- |
| [CHANGELOG.md](CHANGELOG.md) | Version history, including known limitations and deviations |
| [PROJECT_INDEX.md](PROJECT_INDEX.md) | Per-project index: toolchain, source file count, entry classes, artifact names |
| [docs/RELEASE.md](docs/RELEASE.md) | **Release and maintenance handbook** (the original README): **feature documentation** (new subsystems, the new-hack inventory, HUD elements, refactored internals, architecture), **installation**, **building and running**, artifact matrix, packaging validation, verification status |
| [docs/PORTING-NEW-VERSIONS.md](docs/PORTING-NEW-VERSIONS.md) | Porting plan and per-version status for the new-version projects |
| [docs/PORTING-1.21.11-26.2.md](docs/PORTING-1.21.11-26.2.md) | Render pipeline and porting notes for 1.21.11 / 26.2 |
| [PORTING_TASK.md](PORTING_TASK.md) | Porting tasks and open items |
| [docs/openaopal-hud-research.md](docs/openaopal-hud-research.md) | Research and decisions behind the ESP / HUD visual rework |
| [docs/CONFIG-FORMAT.md](docs/CONFIG-FORMAT.md) | Configuration file format |
| [docs/COMBAT_ARCHITECTURE.md](docs/COMBAT_ARCHITECTURE.md) | Combat pipeline architecture |
| [docs/ANTICHEAT.md](docs/ANTICHEAT.md) | Notes on anti-cheat |

---

## Licence

**The source is [GPL-3.0](LICENSE.txt), inherited from Wurst.** The `LICENSE.txt` at the repository
root is the LGPL 2.1 text that ships with the Forge MDK template and applies to the Minecraft Forge
/ FML parts described in it.

> **Note:** only the **six 1.21.11 and 26.2 projects** have been verified by **launching the game**.
> Every other artifact has passed compilation and **packaging validation** (intact zip, loader
> metadata, mixin config, main class present) but has **not** been launched in game. Per-version
> status is in [docs/RELEASE.md](docs/RELEASE.md) and
> [docs/PORTING-NEW-VERSIONS.md](docs/PORTING-NEW-VERSIONS.md).

<div align="center">
<sub>WurstB+ Plus · mod id <code>wurstpenguin</code> · by Penguin</sub>
</div>
