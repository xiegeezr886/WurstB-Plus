<div align="center">

[简体中文](README.md) · **English**

<img src="logo.png" alt="WurstB+ Plus" width="620"/>

**Wurst's code base, carried to new mod loaders and new Minecraft versions**

[![Release](https://img.shields.io/github/v/release/xiegeezr886/WurstB-Plus?style=flat-square&label=release&color=007CFF)](https://github.com/xiegeezr886/WurstB-Plus/releases)
[![Downloads](https://img.shields.io/github/downloads/xiegeezr886/WurstB-Plus/total?style=flat-square&label=downloads&color=007CFF)](https://github.com/xiegeezr886/WurstB-Plus/releases)
[![Stars](https://img.shields.io/github/stars/xiegeezr886/WurstB-Plus?style=flat-square&label=stars&color=007CFF)](https://github.com/xiegeezr886/WurstB-Plus/stargazers)
[![Forks](https://img.shields.io/github/forks/xiegeezr886/WurstB-Plus?style=flat-square&label=forks)](https://github.com/xiegeezr886/WurstB-Plus/forks)
[![Last commit](https://img.shields.io/github/last-commit/xiegeezr886/WurstB-Plus?style=flat-square&label=last_commit)](https://github.com/xiegeezr886/WurstB-Plus/commits/main)
[![Contributors](https://img.shields.io/github/contributors/xiegeezr886/WurstB-Plus?style=flat-square&label=contributors)](https://github.com/xiegeezr886/WurstB-Plus/graphs/contributors)

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1~26.2-3C8527?style=flat-square)](#-version-support)
[![Loaders](https://img.shields.io/badge/Loaders-Forge%20%7C%20NeoForge%20%7C%20Fabric-6E6E6E?style=flat-square)](#-version-support)
[![Java](https://img.shields.io/badge/Java-17%20%7C%2021%20%7C%2025-E76F00?style=flat-square)](#-version-support)
[![Projects](https://img.shields.io/badge/Gradle_projects-64-4C1D95?style=flat-square)](#-repository-layout)
[![Preview](https://img.shields.io/badge/preview-v1.6.0-8A2BE2?style=flat-square)](https://github.com/xiegeezr886/WurstB-Plus/releases/tag/v1.6.0)
[![License](https://img.shields.io/badge/license-GPL--3.0-2E7D32?style=flat-square)](LICENSE.txt)

</div>

**WurstB+ Plus** is a Minecraft utility mod built on the code structure of
[Wurst](https://github.com/Wurst-Imperium/Wurst7). The same feature set is carried to three mod
loaders — **Forge / NeoForge / Fabric** — and **22 Minecraft versions**, maintained as 64
independent Gradle projects.

> [!TIP]
> **Just want to play?** Grab the jar for your game version and loader from
> [Releases](https://github.com/xiegeezr886/WurstB-Plus/releases), then read
> [Installation](#-installation). **Make sure your Java version matches the table below.**

---

## 📦 Version support

| Minecraft | Forge | NeoForge | Fabric | Java |
| --- | :---: | :---: | :---: | :---: |
| **1.20.1** | ✅ <sup>v1.6.0</sup> | ✅ | ✅ | 17 |
| 1.20.2 · 1.20.3 · 1.20.4 | ✅ | ✅ | ✅ | 17 |
| 1.20.5 | — | ✅ | ✅ | 17 |
| 1.20.6 | ✅ | ✅ | ✅ | 17 |
| 1.21 · 1.21.1 | ✅ | ✅ | ✅ | 21 |
| 1.21.2 | — | ✅ | ✅ | 21 |
| 1.21.3 → 1.21.11 | ✅ | ✅ | ✅ | 21 |
| 26.1 · 26.1.1 · 26.1.2 · 26.2 | ✅ | ✅ | ✅ | 25 |

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

> [!NOTE]
> Not all three 1.20.1 projects are v1.5: the **root Forge 1.20.1 project is v1.6.0** (the only one
> with the v1.6 subsystems), while `fabric/` and `neoforge/` are still v1.5.0. The
> `WurstB+.Plus-1.5.0-Forge-1.20.1.jar` asset in the v1.5.0 release is the artifact from that
> release (the project has since moved on, so it cannot be rebuilt byte-for-byte) and is kept
> as-is. For a v1.6 Forge 1.20.1 build, use the v1.6.0 release.

---

## 🚀 Installation

Drop the jar into `.minecraft/mods/`. **The Java version must match the table above**, otherwise
the loader will not start.

<details open>
<summary><b>Forge</b></summary>

1. Install the matching Forge: 1.20.1 → `47.4.10`, 1.21.1 → `52.1.16`, 1.21.11 → `61.2.0`,
   26.1.2 → `64.1.0`, 26.2 → `65.1.0`.
2. Put `WurstB+.Plus-<version>-Forge-<mc>.jar` into `.minecraft/mods/`.
3. Launch with the matching Java.

</details>

<details>
<summary><b>NeoForge</b></summary>

1. Install the matching NeoForge: 1.20.1 → `47.1.3`, 1.21.1 → `21.1.244`, 1.21.11 → `21.11.45`,
   26.1.2 → `26.1.2.87`, 26.2 → `26.2.0.53-beta`.
2. Put `WurstB+.Plus-<version>-NeoForge-<mc>.jar` into `.minecraft/mods/`.

> [!WARNING]
> If NeoForge 1.21.1 fails with `baritone.api.forge does not read module minecraft`, an older
> package or a standalone Baritone JAR is present as well; remove it and keep exactly one jar.

</details>

<details>
<summary><b>Fabric</b></summary>

1. Install Fabric Loader: 1.20.1 / 1.21.1 → `0.16.14`, 1.21.11 / 26.1.2 / 26.2 → `0.19.3`.
2. **Also install the matching [Fabric API](https://modrinth.com/mod/fabric-api)**:
   1.20.1 → `0.92.6`, 1.21.1 → `0.115.0`, 1.21.11 → `0.141.6`, 26.1.2 → `0.155.2`, 26.2 → `0.156.0`.
3. Put `WurstB+.Plus-<version>-Fabric-<mc>.jar` into `.minecraft/mods/`.

</details>

<details>
<summary><b>Baritone (do not add a second copy)</b></summary>

Some platforms bundle Baritone as a Jar-in-Jar, others merge the Baritone classes into the main
module (to dodge a JPMS module read error). **Do not** drop a standalone Baritone JAR next to it —
it will clash with the bundled version. Per-version details are in
[docs/RELEASE.md](docs/RELEASE.md).

</details>

---

## 🧩 Features

Scale (measured on the root Forge 1.20.1 project):

| Hacks | Commands | Other features | HUD elements | Java sources | Unit tests |
| ---: | ---: | ---: | ---: | ---: | ---: |
| **209** | **57** | **18** | **31** | **1040** | 162 classes / 1040 `@Test` |

<sub>There are 210 hack classes; 209 are registered in `HackList` (`RadialMenuHack` is not
registered and never appears in game).</sub>

### Interface

- **Three ClickGUI styles**, switchable in the settings: **Epsilon** · **SuperSoft** (MD3
  TonalSpot palette + frosted glass) · **Vape**.
- **HUD editor** (`hud2`): 31 elements with anchor positioning, scaling and per-element settings;
  the editor snaps to the screen centre and to other elements.
- **Declarative UI layer** (`compose`): `UiRow` / `UiColumn` / `UiBox` / `UiText` / `UiSpacer`
  layout primitives plus animation.
- **One accent colour**, `#007CFF` (`RiseTheme.ACCENT`), shared by the music player, notifications
  and the PvP-related UI.
- **Skiko vector rendering**: `skiko-windows-x64.dll` ships as a plain mod resource (**not** through
  jarJar, which would relocate the resource path and break native lookup) and is unpacked at
  runtime. This is also why the root project's artifact is unusually large.

### New in v1.6 <sub>root Forge 1.20.1 only</sub>

| Subsystem | Description |
| --- | --- |
| 🎵 **NetEase Cloud Music player** | NetEase API and account login; home / search / liked / playlists / now-playing pages; word-by-word lyric animation with an AMLL-grade visual layer (springs, masking, emphasis, line balancing) |
| ⛏ **`PerimeterDigger`** | A native port of the community mod [Perimeter Digger](https://github.com/HackerRouter/Perimeter-Digger): closed-interval rectangles and **irregular boundary detection**, liquid `avoid` / `replace` / `seal_boundary` strategies, batch limits with auto-pause on a full inventory, auto-pickup and multiple drop-off points, tool/elytra durability swapping, auto-eat and resupply, cross-dimension furnace repair, pathfinding, per-server/per-world config, Chinese and English |
| 💎 **`SeedOreESP`** | Per-server/per-world seeds; reimplements vanilla ore generation maths in pure Java (Xoroshiro + a per-`placed_feature` rule table) to predict ore positions and draw them client-side, optionally handing them to Baritone. **No new dependencies**: no bundled Meteor, no Cubiomes or seedfinding |
| 🏛 **`SeedStructureESP`** | Calls vanilla's public `RandomSpreadStructurePlacement#getPotentialStructureChunk`, so it matches vanilla by construction; 18 of the 19 vanilla structures are supported (strongholds use a different `concentric_rings` algorithm and are skipped) |
| 🔑 **`seed.search` / `seed.crack`** | Reconstructs candidate seeds from structures you actually observe; the LCG hot loop and frequency reduction are compared **bit for bit** against vanilla, with a runtime self-check before searching. **Caveat**: no lattice reduction, so it cannot solve the full 2⁴⁸ space without bounds — expect several candidates that have to be narrowed down |

14 new hacks:
`AirJump` · `EntityCulling` · `MusicPlayer` · `NoMissCooldown` · `NoRotate` · `PerimeterDigger` ·
`ProjectilePuncher` · `ReverseStep` · `RightClicker` · `SeedOreESP` · `SeedStructureESP` ·
`SuperKnockback` · `VehicleBoost` · `WTap`

> [!IMPORTANT]
> Known limitations and deviations (for example, `SeedOreESP` does not filter by biome and has not
> been verified block-by-block in a real world) are all written down in
> [CHANGELOG.md](CHANGELOG.md) rather than hidden.

### ESP and visuals

Name tags, the armour strip, short enchantment names and health bars follow the design of
[OpenOpal](https://github.com/ZSZ7/OpenOpal) (GPL-3.0), reimplemented on this project's rendering
backend (Skia / `GuiGraphics`) while keeping the native drawing path as a fallback. Two bugs in the
reference implementation were fixed while porting its 28 easing curves; the process is recorded in
[docs/openaopal-hud-research.md](docs/openaopal-hud-research.md).

---

## 🛠️ Building from source

Every project is an **independent Gradle build** with its own wrapper; there is **no shared
source set** between them.

### Root project: Forge 1.20.1

Requires **JDK 17**:

```powershell
.\gradlew.bat clean jarJar --console=plain
```

Artifact: `build/libs/WurstB+ Plus-v1.6.0-Forge-1.20.1.jar`.

<details>
<summary>Dev client and tests</summary>

```powershell
.\gradlew.bat runClient --console=plain      # launch the development client
.\gradlew.bat test --offline --console=plain # run the unit tests
```

> [!NOTE]
> The first `runClient` **needs a network connection**: ForgeGradle has to fetch Minecraft assets
> and vanilla libraries such as `commons-io`. `compileJava` / `test` run fully offline, but
> `runClient --offline` fails in `:minecraftLibraryCopy` (`commons-io:commons-io:2.6` is not in the
> offline cache).
>
> When `jarJar` finishes it copies the artifact into the local test instance's `mods/`
> (`.test/versions/1.20.1-Forge_47.4.22/mods/`, see `copyJarToTestMods` in `build.gradle`) and
> removes older WurstB+ jars from that directory so Forge does not load two copies.

</details>

### Other version projects

The production task differs per loader:

| Loader | Task |
| --- | --- |
| Forge | `jarJar` (1.20.2 – 1.21.1) / `allJar` (1.21.3+) |
| NeoForge | `jar` |
| Fabric | `remapJar` (`jar` on 26.x) |

```powershell
cd versions\1.21.5
..\..\gradlew.bat clean allJar --console=plain
```

Per-project toolchains (JDK / Gradle / loader versions) are listed in
[PROJECT_INDEX.md](PROJECT_INDEX.md); batch build and packaging validation scripts are in
[docs/RELEASE.md](docs/RELEASE.md).

---

## 📁 Repository layout

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

## 📚 Documentation

| Document | Contents |
| --- | --- |
| [CHANGELOG.md](CHANGELOG.md) | Version history, including known limitations and deviations |
| [PROJECT_INDEX.md](PROJECT_INDEX.md) | Per-project index: toolchain, source file count, entry classes, artifact names |
| [docs/RELEASE.md](docs/RELEASE.md) | **Release and maintenance handbook** (the original README): artifact matrix, build scripts, packaging validation, verification status |
| [docs/PORTING-NEW-VERSIONS.md](docs/PORTING-NEW-VERSIONS.md) | Porting plan and per-version status for the new-version projects |
| [docs/PORTING-1.21.11-26.2.md](docs/PORTING-1.21.11-26.2.md) | Render pipeline and porting notes for 1.21.11 / 26.2 |
| [PORTING_TASK.md](PORTING_TASK.md) | Porting tasks and open items |
| [docs/openaopal-hud-research.md](docs/openaopal-hud-research.md) | Research and decisions behind the ESP / HUD visual rework |
| [docs/CONFIG-FORMAT.md](docs/CONFIG-FORMAT.md) | Configuration file format |
| [docs/COMBAT_ARCHITECTURE.md](docs/COMBAT_ARCHITECTURE.md) | Combat pipeline architecture |
| [docs/ANTICHEAT.md](docs/ANTICHEAT.md) | Notes on anti-cheat |

---

## ⚖️ Credits and licence

This project builds on the work of many others:

- [**Wurst**](https://github.com/Wurst-Imperium/Wurst7) — the code structure and most of the features
- [**Baritone**](https://github.com/cabaletta/baritone) — pathfinding and automation
- [**OpenOpal**](https://github.com/ZSZ7/OpenOpal) — the ESP / HUD visual design
- [**Perimeter Digger**](https://github.com/HackerRouter/Perimeter-Digger) — the reference behaviour for perimeter digging
- **Skiko / Skia** — the vector rendering backend

**The source is [GPL-3.0](LICENSE.txt), inherited from Wurst.** The `LICENSE.txt` at the repository
root is the LGPL 2.1 text that ships with the Forge MDK template and applies to the Minecraft Forge
/ FML parts described in it.

> [!WARNING]
> **Verification status**: only the **six 1.21.11 and 26.2 projects** have been verified by
> **launching the game**. Every other artifact has passed compilation and **packaging
> validation** (intact zip, loader metadata, mixin config, main class present) but has **not** been
> launched in game. Per-version status is in [docs/RELEASE.md](docs/RELEASE.md) and
> [docs/PORTING-NEW-VERSIONS.md](docs/PORTING-NEW-VERSIONS.md).

<div align="center">
<sub>WurstB+ Plus · mod id <code>wurstpenguin</code> · by Penguin</sub>
</div>
