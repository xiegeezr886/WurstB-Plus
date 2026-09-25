# Forge 26.3 Port Status

Updated: 2026-09-25

## Current State

- Target: Minecraft 26.3 / Forge 66.0.3 / Java 25.
- **Scaffold only.** This project was cloned from `versions/26.2` and its version
  anchors were rewritten for 26.3. Nothing has been compiled, packaged, or
  launched on 26.3 yet.

| Step | Status |
| --- | --- |
| Tree cloned from `versions/26.2` | done |
| Version anchors rewritten (gradle.properties, mods.toml, jarjar metadata, `MC_VERSION`) | done |
| Baritone coordinate repinned to 1.20.0-26.3 | done (not yet resolved by a build) |
| `compileJava` | **not run** |
| `allJar` | **not run** |
| Development client / in-world test | **not run** |

## Inherited from the 26.2 donor tree

These were fixed on the 26.2 donor and are therefore present here, but they have
not been re-verified against 26.3:

- Rendering, GUI, input, networking, entity, registry, and Mixin targets already
  migrated to the 26.x API.
- Baritone level-renderer and GUI compatibility Mixins.
- Null-safe guards for early telemetry, client, and chat initialization.
- No redundant `GLOBALS_SNIPPET` in the fogless-line pipelines (26.2's
  `MATRICES_FOG_SNIPPET` already includes it).
- JarJar metadata kept out of development resources; present only in the all jar.

## Not yet done

- No `compileJava` run, so the 26.3 API surface is unverified. The two 26.3
  changes expected to break things: SDL3 replacing GLFW (input layer) and OIT
  replacing Improved Transparency (render pipelines, blending, render setup).
- `pack.mcmeta` still declares `pack_format` 34, inherited from 26.2.
- No in-game verification, so the artifact must not be described as tested.

## Validation Commands

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-25.0.4.101-hotspot"
$env:_JAVA_OPTIONS = "-Djavax.net.ssl.trustStoreType=Windows-ROOT"
.\gradlew.bat compileJava --no-configuration-cache --no-daemon --console=plain
.\gradlew.bat clean allJar test --no-configuration-cache --no-daemon --console=plain
```

The first run downloads and decompiles Minecraft / Forge and takes 10-45 minutes;
do not pass `--offline` on it.
