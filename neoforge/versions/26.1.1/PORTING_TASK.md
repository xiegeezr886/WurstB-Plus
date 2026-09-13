# NeoForge 26.1.2 Port Status

## Current State

- Target: Minecraft 26.1.2 / NeoForge 26.1.2.87 / Java 25.
- Build system: ModDevGradle 2.0.143 with Gradle 9.4.1.
- `clean build` succeeds.
- Release jar: `build/libs/WurstB+ Plus-v1.5.0-NeoForge-26.1.2.jar`.

## Ported Systems

- Replaced Forge loader imports and entrypoint wiring with NeoForge event buses.
- Registered custom render pipelines through `RegisterRenderPipelinesEvent`.
- Migrated loader environment and version queries to the NeoForge 26 APIs.
- Registered `wurst.mixins.json` and the access transformer in `neoforge.mods.toml`.
- Adapted GUI polygon submission to `submitGuiElementRenderState()`.
- Updated entity water-state checks and changed list cleanup visibility for 26.1.2.
- Embedded WebSocket and Netty proxy dependencies through JarJar.
- Baritone is compile-only because the available Forge artifact is not NeoForge 26.1.2 compatible; related modules degrade safely at runtime.

## Validation

- Java compilation: passed.
- Full Gradle build: passed.
- Jar metadata: includes NeoForge mod metadata, Mixin manifest, access transformer, and nested WebSocket/Netty jars.

## Remaining Validation

- `runClient` is currently blocked before Minecraft starts: NeoForm's JDK 21 asset downloader fails local TLS certificate validation (`PKIX path building failed`). This is an environment trust-store issue, not a Java compile or mod-loading failure.
