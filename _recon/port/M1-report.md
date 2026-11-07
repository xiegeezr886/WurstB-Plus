# M1: NanoVG feasibility on Minecraft 26.3

Date: 2026-10-26

## Result: PASS on Fabric, Forge and NeoForge (OpenGL backend)

All three 26.3 projects were given a minimal port of:

- `fabric/src/main/java/net/wurstclient/hud2/nanovg/*` (7 classes)
- the four MiSans/Icon fonts
- `hud2/compat/HudTextures` stub (image lookup not needed by the probe)
- `NanoVgSpike` plus a `HudManager.onRenderGUI` call
- `org.lwjgl:lwjgl-nanovg` 3.4.3 and its natives, unpacked into the mod jar

The probe draws one green rectangle and text into NanoVG's offscreen framebuffer,
reads the pixels back, copies the framebuffer texture into a RenderPearl
`GpuTexture` with `glCopyTexSubImage2D`, and submits a vanilla
`BlitRenderState(RenderPipelines.GUI_TEXTURED, ...)` through `GuiRenderState`.

## Evidence

| Loader | Instance | Markers | Screenshot |
| --- | --- | --- | --- |
| Fabric 26.3 | `.test/versions/26.3-fabric-0.19.5` | `NanoVG GL3 context created`, `pixels=86400/409920` | `_recon/port/evidence/26.3-nanovg-blit2.png` |
| Forge 26.3 | `.test/versions/26.3-forge-66.0.3` | same | `_recon/port/evidence/26.3-forge-nanovg5.png` |
| NeoForge 26.3 | `.test/versions/26.3-neoforge-26.3.0.16-beta` | same | `_recon/port/evidence/26.3-neoforge-nanovg.png` |

The world `WurstSmokeFresh` in each 26.3 test instance was replaced with the
26.3 singleplayer world from `fabric/versions/26.3/run/saves` because the
pre-provisioned worlds failed with `Overworld settings missing`. Forge and
NeoForge show the world-upgrade confirmation and the "join now?" dialog; both
were accepted with a synthetic left click.

## Important findings

1. `RenderSystem.getDevice().createSampler(..., maxAnisotropy, ...)` requires
   `maxAnisotropy >= 1`.
2. Forwarding `hudNatives` must not unpack the transitive `org.lwjgl` core jar.
   Forge 26.3 fails module resolution with
   `Modules wurstpenguin and org.lwjgl export package org.lwjgl.system.jni`.
   Restrict the unpack to `org/lwjgl/nanovg/**` and the native directories.
3. The vanilla blit currently appears vertically flipped and stretched; the
   full HUD will use GUI-space coordinates and corrected V coordinates.
4. `HudManager` is only called while the world HUD renders, so the probe needs
   a real world. `run-version-tests.ps1` minimizes 26.x windows, which breaks
   RenderPearl surface acquisition; manual launches and screenshots were used.

## Stop condition

The highest-risk item from the plan (NanoVG on the 26.x render pipeline) is
cleared. Proceed with the family canaries.
