# v1.5.1 Fabric 1.20.1 content flat-port — result

Date: 2026-11-07

## Scope

Spread the v1.5.1 Fabric content (the fullported Fabric trees of every MC
version) to all Forge/NeoForge v1.5.1 projects:

- Forge `versions/*` — 20 projects
- NeoForge `neoforge` root (1.20.1) + `neoforge/versions/*` — 23 projects
- Fabric `fabric/versions/*` — 22 projects (already contained the content;
  only cross-version bugs were fixed)
- Root Forge 1.20.1 (`v1.6.0`) intentionally untouched

## Method

`_recon/port/cross_loader_sync.py` performs a same-version 3-way merge:

    base   = HEAD:fabric/versions/<mc>/<path>   (old Fabric content)
    ours   = Forge/NeoForge working file        (loader-adapted)
    theirs = Fabric working file                (new v1.5.1 content)

- EOL/BOM normalized; whitespace-only conflicts auto-resolved.
- Wholesale rewrites (KillauraHack, MultiAuraHack, RoundedRectRenderer) are
  copied instead of merged.
- Files absent from a same-version sibling are treated as loader-specific and
  skipped; the lagging `neoforge` root uses `--copy-missing`.
- `merge_mixin_config.py` adds only mixins newly introduced by the port.
- `resolve_cross_conflicts.py` resolves residual conflicts (HackList keeps
  loader-only hacks; title menu keeps the loader version string).
- `delete_obsolete_hacks.py` deletes hack classes removed by the HUD rewrite.
- `force_content_neoforge.py` brings the pre-v1.5.1 NeoForge root up to date.

## Bugs found in the Fabric source while porting (fixed in every affected tree)

Mixin targets are not validated by Fabric/NeoForge compilation; these only
surfaced on launch or on Forge's refmap AP.

- `ScreenMixin` injected into `Screen.renderDirtBackground`, removed in 1.20.5
  (and renamed `extractRenderState` in 26.x).
- `GameRendererMixin` injected into `GameRenderer.render(FJZ)V`, which became
  `render(DeltaTracker, boolean)` in 1.21–26.2 and `render()` in 26.3.
- `OptionsSubScreenMixin` targeted `basicListRender`, which only exists in
  1.20.1–1.20.2; unregistered on 1.20.3+ (redirect was already redundant with
  ScreenMixin).
- `SelectWorldScreenMixin` targeted `SelectWorldScreen.render`, which no longer
  exists from 1.21.9; unregistered there.
- 26.x: `AbstractWidgetMixin`/`AbstractSelectionListMixin` retargeted to
  `extractWidgetRenderState`; `ScreenMixin` retargeted to `extractRenderState`.
- 26.x Fabric configs carried stale mixin names; aligned to the loader-adapted
  set used by Forge/NeoForge (`LocalPlayerMixin`, `ChatComponentMixin`,
  `KeyboardHandlerMixin`, `MouseHandlerMixin`, `FogRendererMixin`,
  `ScreenEffectRendererMixin`, `MobRendererMixin`,
  `CreativeInventoryScreenMixin`, `BlockStateBaseMixin`, `LevelRendererMixin`).
- 26.3: removed the deleted `LivingEntity.swingTime` accessor.
- 17 Fabric projects listed mixin classes that no longer exist; entries removed.

## Verification

Compile: `scripts/port/Verify-CompileMatrix.ps1` — **65/65 PASS, 0 FAIL**
(JDK17 10, JDK21 40, JDK25 15). Evidence: `matrix-final.csv`,
`final-fabric-{1x,26}.log`, `final-forge-{1x,26}.log`,
`final-neoforge-{1x,26,root}.log`.

Smoke launches (`scripts/smoke-launch.py`, settle 45):

| Version | Fabric | Forge | NeoForge |
| --- | --- | --- | --- |
| 1.20.2 | LAUNCHED | LAUNCHED | CRASHED — bundled Baritone 1.20.1 jar |
| 1.21.9 | LAUNCHED | CRASHED — dev-run nanovg module duplication | LAUNCHED |
| 26.3 | LAUNCHED | not run | LAUNCHED |

The two failures are pre-existing environment/packaging blockers (Baritone
per-version jar; Forge dev-run `org.lwjgl.nanovg` module duplication), not
content-port regressions. The full 65-project launch matrix has not been run.

## Content-parity audit (after the first pass)

Two independent checks were run because `compileJava` cannot prove content
parity:

1. **File-set audit** (`audit_stale_content.py` companion check): every live
   non-loader file present in the same-version Fabric tree exists in the
   target. Remaining "missing" files are only `.disabled`/`component.disabled`
   dead code, Fabric-only entry points/resources (`WurstInitializer`,
   `fabric.mod.json`, mixin config/accesswidener, `baritone-api-fabric`
   licences) and renamed loader mixins (`KeyboardMixin` → `KeyboardHandlerMixin`
   etc.). Unreferenced leftovers (`DoubleAnimation`, `EntityOcclusionCuller`,
   1.21.4 post-shader JSONs) are dead code in the versions that lack them.

2. **Stale-content audit** (`audit_stale_content.py` / `update_stale_content.py`):
   for every target file that differs from same-version Fabric, the check
   compares it against the last 8 Fabric revisions of that path. **84 files**
   exactly matched an older Fabric revision, i.e. were silently stale
   (EventManager listener-leak fix, HUD preview suppression, Radar ordering,
   26.x render-API moves, ...). All 84 were updated to current Fabric content.
   One further lag (`HudManager.scaleMod`) was found by recompilation and
   patched in Forge/NeoForge 1.21.11. Re-running the audit reports **0 stale
   files**.

   Exception: `IngameHudMixin` in Forge/NeoForge 1.21 and 1.21.1 keeps the
   loader-adapted HEAD version — current Fabric targets `renderHotbar(F,
   GuiGraphics)` / `renderEffects(GuiGraphics)`, which do not exist in 1.21.1
   (methods take `DeltaTracker`). That is a Fabric-side latent bug, not a
   target gap.

Remaining differing files are loader adaptations (PlatformUtils, BaritoneUtils,
xray mixins, input/option screens, WurstShaderPipelines, ...) or target-side
newer code (e.g. Forge FreecamHack). Byte-for-byte equality across loaders is
therefore not claimed; the verified invariant is complete file coverage plus
current content for every file that was provably stale.

## Launch matrix (runClient, settle 60, 8-way parallel)

67 projects: **45 LAUNCHED / 22 CRASHED**.

| Loader | LAUNCHED | CRASHED | Notes |
| --- | ---: | ---: | --- |
| Fabric | 23/23 | 0 | includes the 1.20.1 source project |
| NeoForge | 20/23 | 3 | 1.20.2/1.20.3 bundled Baritone; 1.20.4 `Failed to find system mod: minecraft` |
| Forge | 2/21 | 19 | 1.20.1 root + 1.20.2 pass; 1.20.3+ ForgeGradle dev-run `build/resources/main constructed 0 mods` / nanovg module duplication |

Two real content bugs were found and fixed by the launch run:
`IngameHudMixin` targeted the pre-1.20.5 `renderHotbar`/`renderEffects`
signatures (fixed 1.20.5–1.21.5), and `KeyboardInputMixin` targeted
`tick(ZF)V`, which became `tick()` in 1.21.4.

Caveat: `runClient` failures for Forge are dev-environment failures, not
necessarily shipped-jar failures. The 2026-11-02 verification used packaged
test jars and reported Forge 20/20 PASS; a packaged-jar re-run is still needed
before claiming Forge parity for this port.

Evidence: `reference/smoke/results-v151-flat-20261107.tsv`,
`_recon/port/cross-sync/full-launch-parallel8.log`, `reference/smoke/*.log`.

### NeoForge follow-up

- 1.20.2/1.20.3 crashed in Baritone's `MixinLootContextBuilder`. Root cause: the
  project-level `src/main/resources/mixins.baritone.json` listed classes from a
  different Baritone build than the bundled jar. The configs were re-synced
  byte-for-byte from each bundled jar (which lists `MixinLootContext`); both
  versions now LAUNCH. The same sync was applied to every project that embeds a
  Baritone jar (14 projects).
- 1.20.4 remains blocked in `runClient`: the locally cached NeoForge 20.4.251
  fails with `Failed to find system mod: minecraft` (and earlier
  `neoFormListLibraries` / fastutil cache errors). No network to switch
  NeoForge versions. Its production jar builds and validates normally.

Launch matrix after fixes: **47/67 LAUNCHED** (Fabric 23/23, Forge 2/21,
NeoForge 22/23). The 20 remaining crashes are environment blockers
(19 ForgeGradle dev-run, 1 NeoForge 1.20.4).

## v1.5.1 production assets

`scripts/build-v1.5-release.py --jobs 4` produced **66 jars** in
`build/release-v1.5/`:

- Fabric 23 (1.20.1 root + 1.20.2–26.3)
- Forge 20 (1.20.2–26.3)
- NeoForge 23 (1.20.1 root + 1.20.2–26.3)
- Root Forge 1.20.1 is **v1.6.0 and intentionally excluded**.
- Previous v1.5.0 assets were preserved in `build/release-v1.5.0-old/`.

Naming: `WurstB+.Plus-1.5.1-<Loader>-<mc>.jar`. Validation:

- `scripts/validate-release-jars.py`: 66/66 pass (zip, loader metadata, mixin
  config, classes).
- Deep check: every jar has HudManager/NanoVGUI, >=62 hud2 modules, 5 fonts,
  6 NanoVG natives, no old `hud2/elements`, no root `module-info.class`;
  Forge/NeoForge jars contain embedded Baritone.
- NeoForge 1.20.2/3/4 jars carry the corrected `mixins.baritone.json`
  (`MixinLootContext`, no `MixinLootContextBuilder`).
- `SHA256SUMS.txt` is next to the jars.
