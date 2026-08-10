# Six-Version Porting Status

Updated: 2026-08-10

## Scope

This round covers six projects:

1. `versions/1.21.11` - Forge 1.21.11
2. `versions/26.2` - Forge 26.2
3. `fabric/versions/1.21.11` - Fabric 1.21.11
4. `fabric/versions/26.2` - Fabric 26.2
5. `neoforge/versions/1.21.11` - NeoForge 1.21.11
6. `neoforge/versions/26.2` - NeoForge 26.2

## Verification Matrix

| Project | Compile/build | Tests | Client startup | Baritone |
| --- | --- | --- | --- | --- |
| Forge 1.21.11 | `clean allJar test` passed | 135/135 passed | World loaded | `#goto 0 88 0` passed |
| Forge 26.2 | `clean allJar test` passed | No test sources | World loaded | `#goto 0 88 0` passed |
| Fabric 1.21.11 | `clean build` passed | 135/135 passed | World loaded | `#goto 0 88 0` passed |
| Fabric 26.2 | `clean build` passed | No test sources | World loaded | `#goto 0 88 0` passed |
| NeoForge 1.21.11 | `clean build` passed | 135/135 passed | World loaded | `#goto 0 88 0` passed |
| NeoForge 26.2 | `clean build` passed | No test sources | World loaded | `#goto 0 88 0` passed |

## Runtime Fixes

- Removed the duplicate `GLOBALS_SNIPPET` from the three 26.2 fogless-line pipelines. In 26.2, `MATRICES_FOG_SNIPPET` already contains the `Globals` bind group.
- Updated 1.21.11 fluid-push Mixin targets for each patched runtime:
  - Fabric uses `updateFluidHeightAndDoFluidPushing(TagKey, double)`.
  - NeoForge uses `updateFluidHeightAndDoFluidPushing(boolean)`.
  - Forge uses `updateFluidHeightAndDoFluidPushing(Predicate)`.
- Restored the 1.21.11 PortalGUI injection to the `Minecraft.screen` field access.
- Restored the 1.21.11 `pickBlock()` and `renderTransparentBackground(GuiGraphics)` targets.
- Replaced stale 26.x render signatures in the Forge and NeoForge 1.21.11 camera, fog, game renderer, level renderer, screen effect, and Freecam Mixins.
- Retained the 26.2 Baritone renderer compatibility Mixins and null-safe early initialization guards.
- Guarded Forge 1.21.11 chat indicator handling while Wurst's OTF registry is still uninitialized.
- Separated Forge Java and resource output directories to prevent parallel `processResources` from deleting compiled classes before `allJar` runs.
- Updated the runtime test script to wait for the test player to join the world and to deliver commands through the GLFW window even when Windows denies foreground focus.

## Packaging

- Forge 1.21.11: `META-INF/jarjar/baritone-forge-1.17.0-1.21.11.jar` (`5,048,425` bytes).
- Fabric 1.21.11: `META-INF/jars/baritone-api-fabric-1.17.0-1.21.11.jar` (`5,032,387` bytes).
- NeoForge 1.21.11: `META-INF/jarjar/baritone-neoforge-1.17.0-1.21.11.jar` (`5,048,510` bytes).
- Forge 26.2: `META-INF/jarjar/baritone-forge-1.18.0-26.2.jar` (`5,057,971` bytes).
- Fabric 26.2: `META-INF/jars/baritone-api-fabric-1.18.0-26.2.jar` (`5,050,160` bytes).
- NeoForge 26.2: `META-INF/jarjar/baritone-neoforge-1.18.0-26.2.jar` (`5,058,058` bytes).
- All six final client jars contain loader metadata that references their nested Baritone jar; users do not need to install Baritone separately.
- The final jars also contain the required Java-WebSocket and Netty proxy dependencies.

## Final Runtime Validation

- All six release JARs were deployed from `download/`, launched through their real loader instance, and entered the `WurstSmokeFresh` single-player world.
- All six clients logged WurstB+ Plus and Baritone initialization, accepted `#goto 0 88 0`, and logged Baritone's `Going to:` response.
- Build reports: `.test/report-build-20260810-024333.txt`, `.test/report-build-20260810-024630.txt`, and `.test/report-build-20260810-024724.txt`.
- Runtime reports: `.test/report-20260810-024942.txt` and `.test/report-20260810-025205.txt`.
- The six-version startup and Baritone matrix is complete. Individual combat, movement, GUI, and HUD behavior is outside this smoke-test scope.
- Development runs still emit expected offline-session, deprecation, and Java native-access warnings.
