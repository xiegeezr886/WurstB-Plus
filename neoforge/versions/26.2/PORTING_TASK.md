# NeoForge 26.2 Port Status

Updated: 2026-08-09

## Current State

- Target: Minecraft 26.2 / NeoForge 26.2.0.53-beta / Java 25.
- Build system: ModDevGradle with Gradle 9.4.1.
- `clean build`: passed.
- Development client: reached the main menu.
- WurstB+ Plus and Baritone both initialize in the development runtime.

## Key Fixes

- Registered custom render pipelines through `RegisterRenderPipelinesEvent`.
- Added Baritone level-renderer and GUI compatibility Mixins.
- Added null-safe guards for early telemetry, client, and chat initialization.
- Removed the redundant `GLOBALS_SNIPPET` from fogless-line pipelines. This fixed the required shader failures for `wurst_esp_lines` and `wurst_depth_test_lines`.
- Embedded the NeoForge-compatible Baritone 1.18.0-26.2 artifact through JarJar.

## Artifact

- `build/libs/WurstB+ Plus-v1.5.0-NeoForge-26.2.jar`
- Baritone artifact source: `baritone-maven/baritone/baritone-neoforge/1.18.0-26.2/baritone-neoforge-1.18.0-26.2.jar`

## Validation Commands

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.4"
$env:JAVA_TOOL_OPTIONS = '-Djavax.net.ssl.trustStore="C:\Program Files\Java\jdk-25.0.4\lib\security\cacerts" -Djavax.net.ssl.trustStorePassword=changeit'
.\gradlew.bat clean build --no-configuration-cache --no-daemon --console=plain
.\gradlew.bat runClient --no-configuration-cache --no-daemon --console=plain
```

NeoForm assets are cached under `C:\Users\ui863\.gradle\caches\neoformruntime\assets` because the JDK 21 downloader previously failed local TLS certificate validation.

## Remaining Validation

- Main-menu startup passed; world loading and all gameplay modules were not exhaustively tested in this round.
- Baritone initialization passed; an actual pathfinding command was not executed.

