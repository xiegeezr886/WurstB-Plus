# Combat module architecture

## Scope

The combat modules use one shared execution model while retaining their public
settings and Forge 1.20.1 integration. Reference clients were used to compare
responsibility boundaries, not as drop-in implementations:

| Reference | Adopted design observation |
|---|---|
| OpenVape | Targeting, rotation control and click production have independent lifecycles |
| OpenMyau | Disable and target-loss paths must release blocking and rotation state |
| OpenOpal | A selected target is a result object; attack and rotation targets may be refreshed independently |
| LiquidBounce Nextgen | Candidate collection is deterministic and every scheduled attack is revalidated at execution time |
| Rise | Attack, switch and block timers belong to explicit module state rather than event-local variables |

No protocol-specific threshold or anti-cheat bypass claim is inherited from a
reference client. Runtime behavior still requires testing against the exact
Minecraft, loader and server combination.

## Shared state

`CombatTargetSession<T>` owns target continuity. It wraps `TargetTracker`,
returns an identity-based `Selection` result and increments a generation on
every acquire, switch or release. Modules use `Selection.changed()` to reset
click timers, paths or rotation acceleration exactly once.

`CombatRotationController` owns the planned rotation, acceleration history and
the combat-priority `RotationQueue`. It is the only combat-module abstraction
that starts, clears and stops that queue. Direct block-facing requests and
smoothed entity-facing plans share the same finite-value sanitization path.

`CombatIntentQueue<T>` transfers one decision from the update phase to the
input phase. An attack intent takes precedence over a miss intent, consumption
is destructive, and every update tick clears an unconsumed intent. This keeps
world mutation out of target planning and prevents stale attacks after a pause
or target loss.

## Tick flow

1. Validate the client environment and clear all owned state on pause.
2. Advance target and click timers.
3. Collect, filter and deterministically rank candidates.
4. Select a target and react once to a changed session generation.
5. Build an aim/interaction plan and publish a rotation request.
6. Schedule an attack or miss intent without mutating the world.
7. Consume the intent in the input phase, revalidate range, raycast, cooldown
   and module requirements, then perform the action.
8. On disable, stop rotation ownership and clear target/action state.

## Migrated modules

| Module | Target session | Rotation controller | Intent queue |
|---|---:|---:|---:|
| Killaura | yes | yes | yes |
| MultiAura | yes | yes | yes |
| AimAssist | yes | client mouse rotation remains local | not applicable |
| KillauraLegit | yes | client mouse rotation remains local | not applicable |
| TriggerBot | yes | not applicable | direct input phase |
| FightBot | yes | yes | direct update phase |
| TP-Aura | yes | yes | direct update phase |
| AnchorAura | not applicable | yes | block action state remains local |
| CrystalAura | not applicable | yes | crystal planner remains local |

The remaining local state is domain-specific: AutoBlock state, multi-target
lists, pathfinding, anchor charge/detonate transitions and crystal placement.
Moving those into a generic container would hide behavior instead of reducing
duplication.
