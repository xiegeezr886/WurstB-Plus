# Forge 26.2 Port Status

Updated: 2026-08-09

## Current State

- Target: Minecraft 26.2 / Forge 65.1.0 / Java 25.
- `clean build`: passed.
- `allJar`: passed.
- Development client: reached the main menu.
- WurstB+ Plus and Baritone both initialize in the development runtime.

## Key Fixes

- Migrated rendering, GUI, input, networking, entity, registry, and Mixin targets to the 26.2 API.
- Added Baritone level-renderer and GUI compatibility Mixins.
- Added null-safe guards for early telemetry, client, and chat initialization.
- Removed the redundant `GLOBALS_SNIPPET` because 26.2 `MATRICES_FOG_SNIPPET` already includes it.
- Kept JarJar metadata out of development resources and added it only to the all jar.

## Artifact

- `build/libs/WurstB+ Plus-v1.5.0-Forge-26.2.jar`
- Contains `META-INF/jarjar/metadata.json`.
- Contains `META-INF/jarjar/baritone-forge-1.18.0-26.2.jar` (`5,057,344` bytes).
- Contains Java-WebSocket and required Netty proxy classes.

## Validation Commands

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.4"
.\gradlew.bat clean build --no-configuration-cache --no-daemon --console=plain
.\gradlew.bat allJar --no-configuration-cache --no-daemon --console=plain
.\gradlew.bat runClient --no-configuration-cache --no-daemon --console=plain
```

## Remaining Validation

- Main-menu startup passed; world loading and all gameplay modules were not exhaustively tested in this round.
- Baritone initialization passed; an actual pathfinding command was not executed.

