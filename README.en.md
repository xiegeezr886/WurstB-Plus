<div align="center">

[简体中文](README.md) · English

<img src="logo.png" alt="WurstB+ Plus" width="620"/>

[![Release](https://img.shields.io/github/v/release/xiegeezr886/WurstB-Plus?style=flat-square&label=release&color=007CFF)](https://github.com/xiegeezr886/WurstB-Plus/releases)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1~26.3-3C8527?style=flat-square)](#version-support)
[![Loaders](https://img.shields.io/badge/Loaders-Forge%20%7C%20NeoForge%20%7C%20Fabric-6E6E6E?style=flat-square)](#version-support)
[![Java](https://img.shields.io/badge/Java-17%20%7C%2021%20%7C%2025-E76F00?style=flat-square)](#version-support)
[![Gradle projects](https://img.shields.io/badge/Gradle_projects-67-4C1D95?style=flat-square)](#repository-layout)
[![v1.5.2](https://img.shields.io/badge/version-v1.5.2-8A2BE2?style=flat-square)](docs/RELEASE-v1.5.2.md)
[![License](https://img.shields.io/badge/license-AGPL--3.0-2E7D32?style=flat-square)](LICENSE.txt)

</div>

**WurstB+ Plus** is a Minecraft utility mod built on the code structure of
[Wurst](https://github.com/Wurst-Imperium/Wurst7). The same feature set is carried to three mod
loaders — **Forge / NeoForge / Fabric** — and **23 Minecraft versions**, maintained as 67
independent Gradle projects.

---

## Version support

| Minecraft | Forge | NeoForge | Fabric | Java |
| --- | :---: | :---: | :---: | :---: |
| **1.20.1** | ✓ | ✓ | ✓ | 17 |
| 1.20.2 · 1.20.3 · 1.20.4 | ✓ | ✓ | ✓ | 17 |
| 1.20.5 | — | ✓ | ✓ | 21 |
| 1.20.6 | ✓ | ✓ | ✓ | 21 |
| 1.21 · 1.21.1 | ✓ | ✓ | ✓ | 21 |
| 1.21.2 | — | ✓ | ✓ | 21 |
| 1.21.3 → 1.21.11 | ✓ | ✓ | ✓ | 21 |
| 26.1 · 26.1.1 · 26.1.2 · 26.2 · 26.3 | ✓ | ✓ | ✓ | 25 |

<sub>Minecraft **1.20.5** and **1.21.2** never had an official Forge release, so those two versions
ship NeoForge and Fabric only.</sub>

**Asset naming** (the version number carries no `v`):

```text
WurstB+.Plus-<version>-<loader>-<mc>.jar      e.g. WurstB+.Plus-1.5.2-Fabric-1.20.1.jar
```

| Release | Contents | Notes |
| --- | --- | --- |
| [**v1.5.0**](https://github.com/xiegeezr886/WurstB-Plus/releases/tag/v1.5.0) | 67 assets | The historical release of the 1.5.0 line |
| [**v1.5.1**](https://github.com/xiegeezr886/WurstB-Plus/releases/tag/v1.5.1) | 67 assets | Previous full-matrix release |
| [**v1.5.2**](docs/RELEASE-v1.5.2.md) | 67 source projects | Stable version number; see GitHub Releases for available assets |

Fabric 1.20.1 is the v1.5.2 baseline. The table describes source coverage; match the actual JAR to your Minecraft version and loader.
See the [v1.5.2 release notes](docs/RELEASE-v1.5.2.md) for changes and validation, and the [porting plan](docs/PORTING-v1.5.2.md) for per-generation results.

---

## Repository layout

```text
.
├── src/                    root project  · Forge 1.20.1   · v1.5.2
├── fabric/                 Fabric 1.20.1                  · v1.5.2
├── neoforge/               NeoForge 1.20.1                · v1.5.2
├── versions/               Forge new-version projects      20
├── fabric/versions/        Fabric new-version projects     22
├── neoforge/versions/      NeoForge new-version projects   22
├── docs/                   design, porting and verification docs
├── scripts/                build, test and diagnostic scripts
└── build.gradle            root project build config
```

**20 + 22 + 22 = 64 version projects**, plus the 3 root 1.20.1 projects, for **67 independent
Gradle builds** in total. Every project carries a complete `gradle-wrapper.jar`, so the Gradle
distribution resolves even offline.

---

## Documentation

| Document | Contents |
| --- | --- |
| [docs/STATUS.md](docs/STATUS.md) | Generated project, toolchain, source and test counts |
| [docs/PORTING-v1.5.2.md](docs/PORTING-v1.5.2.md) | Scope, batches and acceptance criteria for the full v1.5.2 port |
| [CHANGELOG.md](CHANGELOG.md) | Version history, including known limitations and deviations |
| [PROJECT_INDEX.md](PROJECT_INDEX.md) | Per-project index: toolchain, source file count, entry classes, artifact names |
| [docs/RELEASE.md](docs/RELEASE.md) | **Release and maintenance handbook** (the original README): **feature documentation** (new subsystems, the new-hack inventory, HUD elements, refactored internals, architecture), **installation**, **building and running**, artifact matrix, packaging validation, verification status, **rendering compatibility (NanoVG / X-Ray / Vulkan)** |
| [docs/PORTING-NEW-VERSIONS.md](docs/PORTING-NEW-VERSIONS.md) | Porting plan and per-version status for the new-version projects |
| [docs/PORTING-1.21.11-26.2.md](docs/PORTING-1.21.11-26.2.md) | Render pipeline and porting notes for 1.21.11 / 26.2 |
| [PORTING_TASK.md](PORTING_TASK.md) | Porting tasks and open items |
| [docs/openaopal-hud-research.md](docs/openaopal-hud-research.md) | Research and decisions behind the ESP / HUD visual rework |
| [docs/CONFIG-FORMAT.md](docs/CONFIG-FORMAT.md) | Configuration file format |
| [docs/COMBAT_ARCHITECTURE.md](docs/COMBAT_ARCHITECTURE.md) | Combat pipeline architecture |
| [docs/ANTICHEAT.md](docs/ANTICHEAT.md) | Notes on anti-cheat |

---

## Licence

**The combined work is [AGPL-3.0-or-later](LICENSE.txt)**; `LICENSE.txt` is the full AGPL-3.0 text.
Parts inherited from Wurst remain GPL-3.0-or-later and are kept in
[LICENSE-GPL-3.0.txt](LICENSE-GPL-3.0.txt); GPL-3.0 code may be combined into an AGPL-3.0 work.

[LICENSE-Forge-MDK.txt](LICENSE-Forge-MDK.txt) is the separate LGPL 2.1 text that ships with the
Forge MDK template and applies to the Minecraft Forge / FML parts described in it (including the
no-redistribution restriction on MCP data).

Third-party components distributed with the mod (FFmpeg, Skiko, Kotlin, Baritone, fonts) are
inventoried in [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md). FFmpeg is an LGPL-2.1 build and its
**corresponding source** (build scripts, `COPYING.*`, the JNI shim source) ships in
[`native/ffmpeg/`](native/ffmpeg).

> **Verification scope:** see the [v1.5.2 release notes](docs/RELEASE-v1.5.2.md) for available builds and runtime coverage.
> Vulkan is available only on 26.2/26.3, and NeoForge 26.2's
> Vulkan startup is blocked by a loader bug. Per-version status is in the "Rendering
> compatibility (X-Ray / Vulkan)" section of [docs/RELEASE.md](docs/RELEASE.md) and in
> [docs/PORTING-NEW-VERSIONS.md](docs/PORTING-NEW-VERSIONS.md).

---

## Downloader

Use **WDownloader** to select a release, Minecraft version and mod loader. It saves files to your Downloads folder by default, checks download routes and verifies file integrity. See the [downloader guide](downloader/README.md) for setup and build instructions.

<div align="center">
<sub>WurstB+ Plus · mod id <code>wurstpenguin</code> · by Penguin</sub>
</div>
