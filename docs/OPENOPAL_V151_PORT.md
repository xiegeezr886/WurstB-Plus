# OpenOpal KillAura / Scaffold source port for v1.5.1

This port uses the checked-in OpenOpal Java sources under
`reference/clients/OpenOpal/src/client/java/wtf/opal`. The reference targets
Minecraft 1.21.10 with Yarn mappings. The destination uses Mojmap and the host
module/settings/event/rendering infrastructure across 66 v1.5.1 projects.
Root Forge v1.6.0 and the separate v1.5.0 checkout are outside this change.

The original GPL-3.0 license is included in every destination at
`src/main/resources/META-INF/licenses/openopal/LICENSE`. Copied classes identify
their origin. The exact reference and destination hashes are recorded under
`.planning/2026-11-08-openopal-combat-v151/`.

## Source mapping

Paths in the destination column are relative to `src/main/java/net/wurstclient/`.

| OpenOpal source | Destination and adaptation |
| --- | --- |
| `combat/killaura/KillAuraModule`, `KillAuraSettings` | `hacks/KillauraHack.java`: original input click scheduling, attack burst/cooldown, Grim windows, weapon selection, autoblock, lookahead; host settings and event adapters |
| `combat/killaura/target/*` | `hacks/aura/OpenAura.java`, `OpenAuraTargeting.java`, `opal/LastAttackData.java`: targeting, damage window, health/elapsed-time sorting; entity identity and world lifecycle guards |
| `player/rotation/model/*` | `opal/rotation/*RotationModel.java`, `IRotationModel.java`: directly copied six models, mapped math/vector types; Hypixel context passed as constructor parameters |
| `player/rotation/handler/*` | `opal/rotation/RotationMouseHandler.java`, `ClientRotationHandler.java`: original mouse interpolation and preserved client view; version-specific timer/invert-axis adapters |
| `player/mouse/*`, `player/swing/*` | `opal/MouseButton.java`, `MouseHelper.java`, `SwingDelay.java`, `CPSProperty.java`: original clicks, holds, hidden swings and shared stopwatch; settings supplied by the host |
| `player/slot/SlotHelper` | `opal/SlotHelper.java`: original one-tick lease and NONE/DEFAULT/FULL semantics, hotbar/hand/scroll integration; context reset added |
| `world/scaffold/ScaffoldModule`, `ScaffoldSettings` | `hacks/ScaffoldWalkHack.java`: original candidate footprints, bounded search, raycast/placement validation, simulation, same-Y/autojump and movement intelligence |
| `world/scaffold/mode/{Vanilla,AntiGamingChair,Bloxd,Watchdog}Scaffold` | `hacks/scaffold/SimpleTowerMode.java`: mode actions; Bloxd runs at post-move |
| `world/scaffold/mode/TellyScaffold` | `hacks/scaffold/TellyScaffoldMode.java` |
| `world/scaffold/mode/{Heypixel,Hypixel}Scaffold` | `hacks/scaffold/{Heypixel,Hypixel}ScaffoldMode.java`: source rotation/placement and recovery bodies, input jump, reverse and up-telly state |
| `world/scaffold/mode/silencetelly/*` | `hacks/scaffold/SilenceTelly*`: candidate/raycast/falling prediction, rescue, eagle/jump, packet rotation, next-input armed placement, block fly and slot modes |
| Scaffold SkipTick recovery methods, `SkipTickUtility` | `hacks/scaffold/OpenOpalScaffoldRecovery.java`, `opal/SkipTickUtility.java`, `mixin/OpenOpalSkipTickMixin.java` |
| `world/scaffold/ScaffoldIsland`, `Animation`, `Easing` | `hacks/scaffold/ScaffoldIsland.java`, `opal/{Animation,Easing}.java`: source count/speed/bar animation; host NanoVGUI/fonts and top-center positioning |
| Reference input, mouse, camera, hands and slot mixins | `mixin/OpenOpal*.java`: selectors verified against each supported named Minecraft jar |
| `InstantaneousSendPacketEvent` | Existing connection event's final uncancelled dispatch and `opal/OpenOpalPacketBridge.java`: SilenceTelly observes/corrects the final movement packet |

## Host integration boundaries

These are deliberate adaptations, not claims that the entire OpenOpal client
has been imported:

- Friend and bot checks use the host lists and AntiBot. OpenOpal's separate
  Teams module is not included.
- KillAura Hit Select uses the original `VelocityMode` contract. The host's
  simple AntiKnockback mode has the original default values (zero skips and
  `isAttacking=false`). OpenOpal's separate DelayVelocity and NoXZVelocity modes
  are not included, so their extra Hit Select behavior is unavailable.
- Swing while using an item uses the existing Multitask setting. OpenOpal's
  separate Animations module is not included.
- Native SkipTick scaffold rescue is included. The optional interaction with
  an independently enabled OpenOpal Stuck module is unavailable because the
  host has no Stuck module. Scaffold never enables Stuck in the reference.
- Watchdog/WatchdogTellyBlink event bodies are commented out in the supplied
  reference. They remain inactive. Hypixel Boost and Aura Keep Sprint FOV are
  also declared without active behavior in the supplied source.
- `ST Keep FOV` preserves the reference's literal return value (`ST FOV`,
  default 1.1, plus speed-effect increments); it is not converted into a
  multiplier. Host CustomFov and Zoom retain their existing priority.
- Target box uses the source's filled style. Halo and island use the host
  theme/rendering APIs; glow line width, fonts and island placement are not
  pixel-identical to OpenOpal's renderer/DynamicIsland system.
- World/player replacement invalidates queued actions, slots, rotation and
  per-mode state without sending cleanup packets from the departed world.
- The host shared rotation dispatcher resolves after all pre-motion listeners;
  losing one-shot requests cannot replay on a later tick.

## Validation

Run from the repository root:

```text
python scripts/port/test_openopal_state.py
python scripts/port/audit_openopal_mixins.py
python scripts/port/verify_openopal_combat.py --workers 2 --reuse --repair-cache
python scripts/port/verify_openopal_manifest.py
```

The behavior harness compiles actual production Java classes from 1.20.1 and
26.3 with Minecraft boundary stubs. It checks one-shot plans, tick/identity
invalidation, rotation arbitration and event ordering, mouse holds/clicks,
slot leases and visual selection, packet replacement/cancellation, attack and
swing timing, rotation model bounds and independent state, inverted mouse
axes, and reset behavior. It does not simulate Minecraft physics.
The final harness has 69 assertions per API family, 138 in total. Eleven core
source classes also match the direct source mapping in every project (726
exact source comparisons).

Mixin verification checks target methods, field/invoke references and
accessor/invoker members against cached named bytecode for 23 Minecraft
versions. The same-version OpenOpal mixin sources match across loaders. This
is not a game-startup test of interactions with all other installed mixins.

Compilation records every project's source/build hashes before and after
`compileJava`. A result is reusable only when the entire project input set
still matches. Current evidence is in the task's `compile/` result files,
`mixin-audit.json` and `behavior-tests.json`.

No in-game bridging/combat test or server compatibility test has been run.
No release artifact has been installed or published.

## Migration scripts

The `port_openopal_*`, `align_openopal_behavior.py`, `complete_openopal_combat.py`
and `finish_openopal_*` files record incremental one-shot migration stages.
They are not an idempotent regeneration pipeline; do not rerun them against
the finished source tree. Stage backups preserve the pre-edit files while
unrelated existing edits remain in place. Use the verification scripts above
for repeatable checks.
