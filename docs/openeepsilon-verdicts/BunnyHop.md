状态：不适用（参考侧只有部分对应，差异无意义，零改动）

## 对照证据

参考侧：`_oe_ref/src/main/kotlin/studio/coni/epsilon/module/movement/AutoJump.kt`（全文 25 行）。

| 关注点 | 参考（AutoJump.kt:行） | 本工程（hacks/BunnyHopHack.java:行） | 判定 |
| --- | --- | --- | --- |
| 触发条件 | `:22` `player.onGround && timer.tickAndReset(delay.value)` —— 有 `Tick Delay`（`:15` 默认 10，范围 0..40） | `:55-59` `onGround()` 且未潜行，且 `jumpIf` 条件成立就 `jumpFromGround()` | 本工程没有 delay。但**不构成行为差异**：`jumpFromGround()`（1.20.2 `LivingEntity.java:2054-2058`）只写竖直速度，同 tick 的 `travel`/`move` 之后 `onGround()` 即为 false，所以"每 tick 都可能起跳"实际等于"每次落地起跳一次"，参考的 delay=10 只在连续贴地的边界 tick 上有区别 |
| 跳跃方式 | `:22` `player.jump()` | `:59` `player.jumpFromGround()` | 1.20.1 的 `Player.jumpFromGround()` 是 public（1.20.2 `LivingEntity.java:2054`），无需伪造按键；参考的 `player.jump()` 是 1.12.2 封装，不搬 |
| 条件分档 | 无（只有一个 delay 滑块） | `:62-70` `SPRINTING`（疾跑且有输入）/ `WALKING`（有输入）/ `ALWAYS` | 本工程多出的分档，保留 |
| 液体分支 | `:21` `if (player.isInWater \|\| player.isInLava) player.motionY = 0.1` | 无 | **不搬**：这是本工程 `hacks/JesusHack.java:74-80` 的职责（那里是 `vy = 0.11` + 出水跃起 0.30），塞进 BunnyHop 会让两个 hack 抢同一个竖直分量 |
| 潜行 | 无处理 | `:55` 潜行时不跳 | 保留 |

## 实际改动

无。逐条核对后没有可举证的新旧行为差异，按简报第 9 条不动代码。

## 故意不搬

- `AutoJump.kt:15` 的 `Tick Delay` 滑块：新增设置项超出「重构既有实现」范围；而且如上是行为等价的。
- `AutoJump.kt:21` 的液体抬升：属 `JesusHack`，见上表。

## 建议（未做）

1. `JumpIf.ALWAYS`（`:70` `p -> true`）在**站着不动**时也会每 tick 起跳，表现为原地跳、持续消耗饥饿值。这是该选项的既有语义（"Always"），改它等于重定义设置，未动。
2. `:66,68` 读的是 `LocalPlayer.zza` / `xxa`，而 `hacks/SpeedHackHack.java:96-97` 读的是 `player.input.forwardImpulse` / `leftImpulse`。两处取的都是"本 tick 的移动输入"，但走的是两个不同的字段来源（`zza/xxa` 在 `LocalPlayer` 上，`input.*` 在 `Input` 上）。1.20.1 里二者同源，未发现可取反例的差异，仅留档：将来若统一，应统一到 `input.*`（原版 `LocalPlayer.aiStep` 用的是 `input`）。
3. 验证边界：零改动，只做了静态核对。
