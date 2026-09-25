# NeoForge 26.3 Port Status

Updated: 2026-09-25

## Current State

- Target: Minecraft 26.3 / NeoForge 26.3.0.16-beta / Java 25.
- Build system: ModDevGradle with Gradle 9.4.1.
- **Scaffold only.** This project was cloned from `neoforge/versions/26.2` and its
  version anchors were rewritten for 26.3. Nothing has been compiled, packaged,
  or launched on 26.3 yet.

| Step | Status |
| --- | --- |
| Tree cloned from `neoforge/versions/26.2` | done |
| Version anchors rewritten (gradle.properties, neoforge.mods.toml, archiveFileName, `MC_VERSION`) | done |
| Baritone coordinate repinned to 1.20.0-26.3 | done (not yet resolved by a build) |
| `compileJava` / `build` | **not run** |
| Development client / in-world test | **not run** |

## Inherited from the 26.2 donor tree

These were fixed on the 26.2 donor and are therefore present here, but they have
not been re-verified against 26.3:

- Custom render pipelines registered through `RegisterRenderPipelinesEvent`
  (NeoForge differs from Forge/Fabric here, which mutate
  `RenderPipelines.PIPELINES_BY_LOCATION` directly).
- Baritone level-renderer and GUI compatibility Mixins.
- Null-safe guards for early telemetry, client, and chat initialization.
- No redundant `GLOBALS_SNIPPET` in the fogless-line pipelines; this fixed the
  required-shader failures for `wurst_esp_lines` and `wurst_depth_test_lines`.
- NeoForge-compatible Baritone artifact embedded through JarJar.
- `GuiGraphicsExtractorAccessor` mixin present and registered (Forge's config
  omits it).

## Not yet done

- No `compileJava` run, so the 26.3 API surface is unverified. Expected breakage
  sources: SDL3 replacing GLFW (input layer) and OIT replacing Improved
  Transparency (render pipelines, blending, render setup).
- `pack.mcmeta` still declares `pack_format` 34, inherited from 26.2.
- No in-game verification, so the artifact must not be described as tested.

## Validation Commands

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-25.0.4.101-hotspot"
$env:_JAVA_OPTIONS = "-Djavax.net.ssl.trustStoreType=Windows-ROOT"
.\gradlew.bat compileJava --no-configuration-cache --no-daemon --console=plain
.\gradlew.bat clean build --no-configuration-cache --no-daemon --console=plain
```

The first run downloads and decompiles Minecraft through NeoForm and takes
10-45 minutes; do not pass `--offline` on it.

Note: the donor's `PORTING_TASK.md` referenced NeoForm assets cached under
`C:\Users\ui863\.gradle\caches\neoformruntime\assets` — that is an artifact of the
machine the 26.2 port was done on, not a requirement.
