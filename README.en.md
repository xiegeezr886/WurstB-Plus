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

> **Just want to play?** Grab the jar for your game version and loader from
> [Releases](https://github.com/xiegeezr886/WurstB-Plus/releases), then read
> [Installation](#installation). **Make sure your Java version matches the table below.**

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

## Installation

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

> **Note:** if NeoForge 1.21.1 fails with `baritone.api.forge does not read module minecraft`, an
> older package or a standalone Baritone JAR is present as well; remove it and keep exactly one jar.

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

## Features

Scale (measured on the root Forge 1.20.1 project):

| Hacks | Commands | Other features | HUD elements | Java sources | Unit tests |
| ---: | ---: | ---: | ---: | ---: | ---: |
| **209** | **57** | **18** | **31** | **1040** | 162 classes / 1040 `@Test` |

<sub>There are 210 hack classes; 209 are registered in `HackList` (`RadialMenuHack` is not
registered and never appears in game).</sub>

### Interface

- **Three ClickGUI styles** (`clickgui2`), switchable in the settings: **Epsilon** · **SuperSoft**
  (MD3 TonalSpot palette + frosted glass) · **Vape**. Includes the window, settings tree,
  combo boxes and popups, rounded-rectangle renderer, navigator pages, icons, fonts and animation.
- **HUD editor** (`hud2`): 31 elements with anchor positioning, scaling and per-element settings;
  the editor snaps to the screen centre and to other elements.
- **One accent colour**, `#007CFF` (`RiseTheme.ACCENT`), shared by the music player, notifications
  and the PvP-related UI.
- A custom title screen (`gui/title`) and a shared visual layer (`gui/visual`: `VisualTheme` /
  `VisualRenderer` / screen-transition motion).

### New subsystems

Compared with upstream [Wurst](https://github.com/Wurst-Imperium/Wurst7) (baseline: upstream
`master`, 157 hacks / 52 commands / 18 other features) this repository adds **14 packages,
57 hacks and 7 commands**; the other-feature count is the same as upstream (18), with no additions.
Eight of the packages exist in every project, the other six only in the root Forge 1.20.1 project
(v1.6.0).

**Available in every project** — 8 packages, 42 hacks, 3 commands:

| Subsystem | Description |
| --- | --- |
| `clickgui2` | The three ClickGUI styles and their component library: windows, settings tree, combo boxes and popups, rounded-rectangle renderer, navigator pages, icons, fonts and animation |
| `hud2` | 31 HUD elements and the snapping editor, with anchor positioning, scaling and per-element settings |
| `gui` | The custom title screen (`gui/title`) and the shared visual layer (`gui/visual`) |
| `addon` | Third-party addon API: extend `WurstAddon` to declare a name, version and author and to register your own hacks and commands; `AddonManager` discovers and loads them |
| `macros` | Command macros: `.macros add <name> <key> <commands…>`, separate multiple commands with `;` |
| `proxy` | SOCKS4 / SOCKS5 proxy management: `.proxy add / set / remove / clear / list` |
| `waypoints` | Waypoint management: `.waypoints add / remove / list`, with names and colours |
| `discord` | Discord Rich Presence, talking to the desktop client over the `\\.\pipe\discord-ipc-N` named pipe |

**Root Forge 1.20.1 only (v1.6.0)** — 6 packages, 15 hacks, 4 commands:

| Subsystem | Description |
| --- | --- |
| `music` + `twilight` | The NetEase Cloud Music player: `music` covers the API, account login and lyric parsing; `twilight` is its Skia visual shell (theme, shell/home/list layouts, cover cache and fitting, easing and geometry, corner masks). `.twilight` opens the interface |
| `render/skia` | The Skia / Skiko vector rendering backend: GL backend, region rendering, font management, ESP glyphs, and loading of the Skiko native library (`skiko-windows-x64.dll` ships as a plain mod resource, **not** through jarJar, which would relocate the resource path and break native lookup; it is unpacked at runtime). This is also why the root project's artifact is unusually large |
| `compose` | The declarative UI layer: `UiRow` / `UiColumn` / `UiBox` / `UiText` / `UiSpacer` primitives, `AnimFloat` animation, `ComposeHackList`, `ComposeNotifications` |
| `perimeter` | The `PerimeterDigger` automation, a native port of the community mod [Perimeter Digger](https://github.com/HackerRouter/Perimeter-Digger): closed-interval rectangles and **irregular boundary detection**, liquid `avoid` / `replace` / `seal_boundary` strategies, batch limits with auto-pause on a full inventory, auto-pickup and multiple drop-off points, tool/elytra durability swapping, auto-eat and resupply, cross-dimension furnace repair, pathfinding, per-server/per-world config, Chinese and English. `.perimeter` handles planning plus start / pause / resume / stop / status / clear, `.perimeterdig` plans, detects and runs |
| `seed` | The entry point for everything seed-related, detailed under "Seed features" below. `.seed get / set / clear / list / structures / mine / structesp`, `observe *`, `search / crack` |

### Seed features

| Item | Description |
| --- | --- |
| **`SeedOreESP`** | Per-server/per-world seeds; reimplements vanilla ore generation maths in pure Java (Xoroshiro + a per-`placed_feature` rule table) to predict ore positions and draw them client-side, optionally handing them to Baritone. **No new dependencies**: no bundled Meteor, no Cubiomes or seedfinding |
| **`SeedStructureESP`** | Calls vanilla's public `RandomSpreadStructurePlacement#getPotentialStructureChunk`, so it matches vanilla by construction; 18 of the 19 vanilla structures are supported (strongholds use a different `concentric_rings` algorithm and are skipped) |
| **`seed.search` / `seed.crack`** | Reconstructs candidate seeds from structures you actually observe; the LCG hot loop and frequency reduction are compared **bit for bit** against vanilla, with a runtime self-check before searching. **Caveat**: no lattice reduction, so it cannot solve the full 2⁴⁸ space without bounds — expect several candidates that have to be narrowed down |

The 15 root-project hacks:
`AirJump` · `EntityCulling` · `MusicPlayer` · `NoMissCooldown` · `NoRotate` · `PerimeterDigger` ·
`ProjectilePuncher` · `RadialMenuHack` (not registered) · `ReverseStep` · `RightClicker` ·
`SeedOreESP` · `SeedStructureESP` · `SuperKnockback` · `VehicleBoost` · `WTap`

Upstream has, this repository does not: 4 hacks (`AntiKnockback` · `AttributeSwap` ·
`KillauraLegit` · `MaceDmg`), 1 command (`ViewComp`) and 3 packages — `clickgui` and `navigator`
have been superseded by `clickgui2`, and `analytics` is upstream's Plausible telemetry reporting,
which was not ported here; this repository instead provides the `NoTelemetry` and `NoChatReports`
other features for turning telemetry and chat reporting off.

> **Important:** known limitations and deviations (for example, `SeedOreESP` does not filter by
> biome and has not been verified block-by-block in a real world) are all written down in
> [CHANGELOG.md](CHANGELOG.md) rather than hidden.

### ESP and visuals

Name tags, the armour strip, short enchantment names and health bars follow the design of
[OpenOpal](https://github.com/ZSZ7/OpenOpal) (GPL-3.0), reimplemented on this project's rendering
backend (Skia / `GuiGraphics`) while keeping the native drawing path as a fallback. Two bugs in the
reference implementation were fixed while porting its 28 easing curves; the process is recorded in
[docs/openaopal-hud-research.md](docs/openaopal-hud-research.md).

---

## Building from source

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

> **Note:** the first `runClient` **needs a network connection**: ForgeGradle has to fetch Minecraft
> assets and vanilla libraries such as `commons-io`. `compileJava` / `test` run fully offline, but
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
| [docs/RELEASE.md](docs/RELEASE.md) | **Release and maintenance handbook** (the original README): artifact matrix, build scripts, packaging validation, verification status |
| [docs/PORTING-NEW-VERSIONS.md](docs/PORTING-NEW-VERSIONS.md) | Porting plan and per-version status for the new-version projects |
| [docs/PORTING-1.21.11-26.2.md](docs/PORTING-1.21.11-26.2.md) | Render pipeline and porting notes for 1.21.11 / 26.2 |
| [PORTING_TASK.md](PORTING_TASK.md) | Porting tasks and open items |
| [docs/openaopal-hud-research.md](docs/openaopal-hud-research.md) | Research and decisions behind the ESP / HUD visual rework |
| [docs/CONFIG-FORMAT.md](docs/CONFIG-FORMAT.md) | Configuration file format |
| [docs/COMBAT_ARCHITECTURE.md](docs/COMBAT_ARCHITECTURE.md) | Combat pipeline architecture |
| [docs/ANTICHEAT.md](docs/ANTICHEAT.md) | Notes on anti-cheat |

---

## Credits and licence

This project builds on the work of many others:

- [**Wurst**](https://github.com/Wurst-Imperium/Wurst7) — the code structure and most of the features
- [**Baritone**](https://github.com/cabaletta/baritone) — pathfinding and automation
- [**OpenOpal**](https://github.com/ZSZ7/OpenOpal) — the ESP / HUD visual design
- [**Perimeter Digger**](https://github.com/HackerRouter/Perimeter-Digger) — the reference behaviour for perimeter digging
- **Skiko / Skia** — the vector rendering backend

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
