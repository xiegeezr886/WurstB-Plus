状态：不适用

## 对照证据

| 关注点 | 参考（_oe_ref 相对路径:行） | 本工程（相对路径:行） | 判定 |
| --- | --- | --- | --- |
| 默认 `Priority` 键 | 参考无 Priority（`AutoClicker.kt` 只打准星下的实体，不做目标选择） | `hacks/ClickAuraHack.java:43-49` 默认 `Priority.ANGLE`；键集见 `util/CombatTargetUtils.java:268-283`（DISTANCE/ANGLE/HEALTH/HURT_TIME/SCORE） | **不改**：`docs/openeepsilon-refactor.md:47` 已明确记录"默认值仍是各自原来的（ClickAura 是 ANGLE），升级不会改变任何人的选人行为"，新增的 `SCORE` 是 opt-in |
| 每 tick 攻击次数 | `module/combat/AutoClicker.kt:68` `repeat(2)` → 每 tick 最多 2 次 `attackEntity`（`:72-73`） | `hacks/ClickAuraHack.java:103-117`：`onUpdate` 每 tick 1 次 + `onLeftClick` 每次点击 1 次，`attack()` 内被 `speed` 与冷却拦住 → **每 tick ≤ 1 次** | 参考的"每 tick 2 发"是配合它自己 8~12 CPS 的墙钟延迟（`:69`），在 1.20.1 只会撞上受击无敌窗口，属浪费；不搬 |
| 是否遵守原版攻击冷却 | `AutoClicker.kt:69,79-80` 纯墙钟随机延迟，完全不等原版攻击强度 | `settings/AttackSpeedSliderSetting.java:59-68`：`Speed=0`（默认，`:26` 标签 "auto"）时要求 `player.getAttackStrengthScale(0) < 1` 不成立才放行；`Speed>0` 时按 `1000/Speed` ms 放行。调用点 `hacks/ClickAuraHack.java:122` | 默认（`Speed=0`）完全遵守；`Speed>0` 是**设置的既有语义**（自动冷却 ↔ 固定 CPS 二选一），不是实现疏忽，见下 |
| 是否已走 `CombatClickScheduler` | 参考无 `ClickScheduler`（注释掉的 `CPS.INSTANCE`，`AutoClicker.kt:78`） | ClickAura 未用；用它的只有 `hacks/KillauraHack.java:241-242,444-446` 与 `hacks/MultiAuraHack.java:244-245` | 不接：`util/CombatClickScheduler.java` 是"点击模式 + min/max CPS + 每 tick 多发"（`ClickPattern`），接进来要删掉现有 `Speed` 设置、换成新设置 → 违反「不改 setting」 |
| 是否重造共享设施 | 参考无对应设施 | 选人 `CombatTargetUtils.get`（`:125-127`）、瞄点 `AimAtSetting`（`:54-55,133`）、挥臂 `SwingHandSetting`（`:61-62,136`）、暂停 `PauseAttackOnContainersSetting`（`:64-65`）、时序 `AttackSpeedSliderSetting`、旋转 `RotationUtils`（`:134`） | **无重造**，全部是共享件 |
| 视线 / 墙后目标 | `AutoClicker.kt:72` 只打准星下实体（隐含方块遮挡） | `hacks/ClickAuraHack.java:57-59` `Check line of sight`（默认关）→ `:126` 传给 `CombatTargetUtils.get` → `util/CombatTargetUtils.java:177` `!checkLOS \|\| BlockUtils.hasLineOfSight(hitVec)` | 默认关是设置本意（opt-in 加严），且判定用的瞄点与实际转过去的瞄点是同一个：两处都由 `AimAtSetting.getAimPoint` 产出，四个模式都是纯函数（`settings/AimAtSetting.java:61-90`），不存在"按 A 点过检、向 B 点转头" |
| 失效场景 | `AutoClicker.kt:39-42` jitter 前提（按住攻击键 / 用物品 + 手上是方块） | `hacks/ClickAuraHack.java:106` 只在按住攻击键时自动打；`:121-122` 玩家/世界/gameMode 为空与容器界面暂停 | 覆盖等价或更强 |

## 实际改动

无。

逐条核对后**没有**找到能用"新旧行为差异"证明的缺陷，因此没改。曾重点核对但**判定不是 bug** 的两处，留档避免下轮重复劳动：

1. **`Speed>0` 时不查原版攻击强度**（`settings/AttackSpeedSliderSetting.java:64` 只在 `value <= 0` 时查 ticker）。看着像缺陷，但它是这个设置的既有语义：`0` = "auto"（等原版冷却），非 0 = "按这个 CPS 打"。同口径的兄弟 hack 也都只调 `isTimeToAttack()`：`hacks/FightBotHack.java:235`、`hacks/KillauraLegitHack.java:263`、`hacks/ProtectHack.java:312`、`hacks/ProjectilePuncherHack.java:99`。
   - 反向证据：`hacks/TpAuraHack.java:138-140` 在 `isTimeToAttack()` 后补了 `getAttackStrengthScale(0) < 1`，而这等价于"必须满蓄力"→ 原版速率，等于让 TpAura 的 `Speed > 0` **完全失效**。把这个抄进 ClickAura 会重定义现有设置，属于简报第 5 条禁止的动作，故不改（见「建议（未做）」1）。
   - 说明：在 1.9+ 战斗里，`Speed>0` 的连点每次都按 `0.2 + 0.8f²` 结算、且会撞上受击无敌窗口，因此在**伤害**上劣于默认的 `Speed=0`；这是"用户自选的行为"，不是"实机会做错动作的疏忽"。
2. **`hurtTime`（受击无敌窗口）过滤**：Killaura/MultiAura 有 `Hurt time` 滑块（`hacks/KillauraHack.java:140-141,741`、`hacks/MultiAuraHack.java:118-119,573`），ClickAura 没有。但这两者的默认值都是 10，而 `hurtTime` 上限也是 10（原版 `hurtDuration`），即**默认不拒绝任何目标**；ClickAura 没有该设置与它们的默认行为一致，属功能缺口而非缺陷（加设置=新增 UI，未做）。

## 故意不搬

- `AutoClicker.kt:44-63` JitterClick：直接 `mc.player.rotationYaw/rotationPitch += ±(0..1)°` 并钳制 ±90。本工程 ClickAura 走的是静默发包（`hacks/ClickAuraHack.java:134` `sendPlayerLookPacket()`），搬进来只会让玩家真实准星乱抖，1.20.1 的反作弊不会因此改判。
- `AutoClicker.kt:68` `repeat(2)` 每 tick 双发：见上表。
- `AutoClicker.kt:83-95` 右击自动点击 / `BlockOnly`：属"自动点击器"功能，ClickAura 是"按住攻击键时把攻击导向最优目标"，语义不同。
- `AutoClicker.kt:26-27,101-103` MinCPS/MaxCPS 墙钟延迟：本工程的 `AttackSpeedSliderSetting`（0 = auto）在 1.20.1 上更正确（见「实际改动」1 的说明）。

## 建议（未做）

1. **请父 agent 复核 `hacks/TpAuraHack.java:140`**：`getAttackStrengthScale(0) < 1` 让 TpAura 的 `Speed > 0` 变成死设置（门槛=满蓄力=原版速率）。若那是上一轮的误改，应回退；若是有意为之，则它和 ClickAura/FightBot/KillauraLegit/Protect/ProjectilePuncher 的口径不一致，需要统一。这不在我的两个文件范围内，故未动。
2. **`Priority` 描述不全**（纯文案）：`hacks/ClickAuraHack.java:44-48` 只列了 Distance/Angle/Health/Score，漏了 `HURT_TIME`（`util/CombatTargetUtils.java:273` 确实存在该常量，而 `:49` 用的是 `Priority.values()`，所以界面上会出现一个没有说明的 "Hurt time"）。上游遗留 + 本轮加 `SCORE` 时未补齐；属文案缺口，未改。
3. **极窄的同 tick 双发窗口**：`Speed=20`（50 ms）且某一 tick 耗时 > 50 ms（卡顿）时，`onLeftClick`（`:115`）与 `onUpdate`（`:109`）可能在同一 tick 各打一次同一实体（第二次落在受击无敌窗口里，属浪费与不必要的行为特征）。依赖真实帧时长才能触发，本环境无法单测，未改。
4. 若要与 Killaura/MultiAura 的 `Attack cooldown`（`hacks/KillauraHack.java:111-113`，默认 true）口径一致，ClickAura 需要**新增**一个 opt-in 设置来遵守 `missTime`；但 ClickAura 走 `gameMode.attack`，该冷却并不会吞掉攻击，加了只降低出手频率，故不建议无理由加。
5. `Speed>0` 在伤害上劣于默认值这件事，若要真正解决，方向是"按攻击强度倍率决定是否出手"而不是"照抄 TpAura 的满蓄力门槛"；这需要重新定义 `Speed` 的语义与量程（属重定义现有设置），超出本轮范围。
