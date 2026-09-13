# Forge 1.21.11 Port Status

Updated: 2026-08-09

## Current State

- Target: Minecraft 1.21.11 / Forge 61.2.0 / Java 21 bytecode.
- Build JDK: 25.0.4.
- Initial compiler errors: `1964`.
- Current compiler errors: `0`.
- `clean build`: passed.
- `allJar`: passed.
- Tests: 135/135 passed.
- Baritone 1.17.0 for Minecraft 1.21.11 is embedded through Forge JarJar metadata.

## Latest Compatibility Fixes

- Moved AntiWaterPush to Forge's patched `updateFluidHeightAndDoFluidPushing(Predicate)` implementation.
- Restored PortalGUI's injection to the `Minecraft.screen` field access.
- Restored the 1.21.11 `pickBlock()` and `renderTransparentBackground(GuiGraphics)` targets.
- Replaced stale 26.x camera, fog, game renderer, level renderer, screen effect, and Freecam Mixin signatures with the actual 1.21.11 runtime signatures.

## Validation Commands

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.4"
.\gradlew.bat clean build --no-configuration-cache --no-daemon --console=plain
.\gradlew.bat allJar test --no-configuration-cache --no-daemon --console=plain
```

## Artifact

- `build/libs/WurstB+ Plus-v1.5.0-Forge-1.21.11.jar`
- Contains `META-INF/jarjar/metadata.json`.
- Contains `META-INF/jarjar/baritone-forge-1.17.0-1.21.11.jar` (`5,048,425` bytes).
- The embedded Baritone artifact was built from the official Baritone `1.21.11` branch.

## Remaining Validation

- The synchronized compatibility fixes compile and pass all tests, and the final single-jar package contains Baritone.
- Forge 1.21.11 client startup was not rerun in this round because it is outside the current five-target migration scope.
- An actual Baritone pathfinding command has not been executed on this packaged build yet.
