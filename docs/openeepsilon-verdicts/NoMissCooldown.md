状态：不适用

## 对照证据

参考侧 `_oe_ref/src/main/kotlin` 全目录搜 `missTime` **0 命中**，`module/combat/` 里也没有对应模块
（26 个模块里没有 NoHitDelay / NoMissCooldown 一类）。参考不适用，属自审。

> 下文的原版行号取自本仓库唯一解压好的原版源（1.20.2）：
> `neoforge/versions/1.20.2/build/neoForm/.../unzipSources/unpacked/net/minecraft/client/Minecraft.java`。

## 实际改动

无（零改动）。逐条核对：

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 主逻辑 | `hacks/NoMissCooldownHack.java:53-58`：每 tick `((IMinecraftClient)MC).setMissTime(0)` | 正确。`Minecraft.startAttack()` 的第一道门槛就是 `if (this.missTime > 0) return false;`（`Minecraft.java:1669`），而 `continueAttack` 也要求 `missTime <= 0`（`:1643`）；每 tick 清零 ⇒ 这两处都不会再拦你 |
| 未命中延迟的来源 | `:1702-1707` `case MISS:` → `missTime = 10; resetAttackStrengthTicker();`，另有 `:1671-1674` 的 `hitResult == null` 分支也置 10 | 与设置描述"Removes the 10-tick delay after a missed hit"一致 |
| `Cancel attack on miss` | `:60-66` 在 `LeftClickEvent` 里判断 `MC.hitResult.getType() == MISS` 后 `event.cancel()` | 取消点在整个 `startAttack()` 之前，连 `missTime = 10` 和挥手动画都不会发生；与设置描述"cancels empty swings before vanilla applies its delay"一致 |
| 界面造成的 10000 | `:1835` 打开界面时 `missTime = 10000`，`:1849-1850` 每 tick 自减 | 本 hack 每 tick 清零也会一并清掉它；不会造成副作用（`HandleInput` 阶段本来也不该挥空） |

**与 Killaura 的关系（说明，不是缺陷）**：`hacks/KillAuraHack.java` 的 `Attack cooldown`
（默认开）会通过 `util/CombatActionPolicy` 读同一个 `missTime` 并**遵守**它。两个模块语义互补——
一个"制造/尊重冷却"，一个"清零冷却"。同时开着时 `missTime` 恒为 0，Killaura 的那条门槛也就永不触发，
属于预期组合，不是冲突。

两个设置都打开时：前者每 tick 兜底清零，后者连那一下空挥都不发（连带动画）。只开 `Remove attack cooldown`
更接近原版手感（挥手照常、只是不再被拦），只开 `Cancel attack on miss` 则完全不碰原版状态。

## 建议（未做）

- 没找到可举证的缺陷，所以零改动；如果以后要给 `NoMissCooldown` 加"只在单击时清，不每 tick 清"之类的
  行为，需要先有能举证的问题，否则属于无依据的行为改动。
