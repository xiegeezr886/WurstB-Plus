状态：已优化

## 对照证据

| 关注点 | 参考（_oe_ref 相对路径:行） | 本工程（相对路径:行） | 判定 |
| --- | --- | --- | --- |
| 准星命中判定用的 API | `module/combat/AutoClicker.kt:72-73` `mc.objectMouseOver.entityHit != null` → 只打**原版准星拾取**到的实体 | `hacks/TriggerBotHack.java:217` `MC.hitResult instanceof EntityHitResult` | 同源且等价：两者都是原版准星 raytrace（`GameRenderer.pick` → `Minecraft.tick`），不是自建 ray |
| 墙后实体是否也打 | 同上（参考也没有额外视线检查；原版拾取本身已做方块 clip） | `hacks/TriggerBotHack.java:221-223` 调 `CombatTargetUtils.isValid(..., false)`（`checkLOS=false`） | **不需要**额外视线检查：`MC.hitResult` 为 `EntityHitResult` 就说明实体比方块近，即视线未被遮挡。`util/CombatTargetUtils.java:177` 的 `!checkLOS \|\| BlockUtils.hasLineOfSight(hitVec)` 是可选加严，不是漏判 |
| 原版攻击冷却 | `AutoClicker.kt:69,79-80,93-94` 纯 `System.currentTimeMillis()` 墙钟延迟，**完全不看原版攻击强度** | `hacks/TriggerBotHack.java:163` `speed.isTimeToAttack()` + `:167` `player.getAttackStrengthScale(0.5F) >= minCooldown`（默认 0.9，`:73-75`） | 本工程更强，已遵守 |
| 打空后的 10 tick 冷却（`getMissTime`） | 参考无此概念 | 模拟点击分支原来不查（`:174-189`）；Killaura `hacks/KillauraHack.java:449-452`、MultiAura `hacks/MultiAuraHack.java:424-427` 都查，且是**默认开启**的设置（`KillauraHack.java:111-113` "Respects Minecraft's cooldown after a missed attack."，默认 true）。共享判定：`util/CombatActionPolicy.java:15-18`，取/设值：`mixinterface/IMinecraftClient.java:22,24`（`@Shadow Minecraft.missTime`，`mixin/MinecraftClientMixin.java:64-65,253-263`） | **已修**（见下） |
| 是否重造本工程已有设施 | 参考无对应设施 | 选人/过滤/时序全部走共享件：`CombatTargetUtils`（`:221`）、`CombatTargetSession`+`TargetTracker`（`:152`）、`AttackSpeedSliderSetting`（`:46`）、`EntityFilterList.genericCombat()`（`:88-89`） | 无重造 |
| 参考的 `AutoClicker` 设置项 | `AutoClicker.kt:26-32` MinCPS/MaxCPS/RightClick/BlockClick/BlockOnly/JitterClick | TriggerBot 无右键、无 jitter，只有 `Range`/`Speed`/`Speed randomization`/武器与挖掘门限 | 两者功能集不同，无可搬项 |

## 实际改动

**1. 模拟鼠标点击分支补上原版打空冷却判定（`hacks/TriggerBotHack.java:176-184`），并新增两行 import（`:25` `IMinecraftClient`、`:34` `CombatActionPolicy`）。**

- 为什么：`Simulate mouse click` 走的是**原版点击路径**（`mixin/MinecraftClientMixin.java:92-102` 在 `startAttack()Z` 里派发可取消的 `LeftClickEvent`，说明原版点击就是由这个方法处理的；`hacks/NoMissCooldownHack.java:24-26` 的 "Removes the 10-tick delay after a missed hit." 与 Killaura/MultiAura 的默认开启项都说明：`missTime ∈ (0,10]` 期间原版不会出手）。原代码按下模拟键后**无条件**执行 `speed.resetTimer(...)`（`:195`），于是这一次攻击被记成"已执行"，实际没有任何攻击包发出。
- 旧行为（`Speed=5` 即 200 ms、`Simulate mouse click` 开、玩家刚对空气挥空一下使 `missTime=10`、准星压着目标、计时器到点）：
  1. `:186` `simulatePress(true)` → 原版 `startAttack()` 因 miss 冷却未走完而不出手（无攻击包），
  2. `:187` 设 `simulatingMouseClick=true`，`:195` 重置计时器 →
  3. 结果：这一轮攻击**丢失**，下一次尝试被推迟整整 200 ms；在 10 tick（500 ms）冷却窗口里最多会这样白丢 2 次尝试。
- 新行为（同一组输入）：`:182-184` 判定为真 → 直接 `return`：**不按键、也不重置计时器** → `missTime` 一清零（最多早 200 ms）就在同一 tick 打出去，且不再产生"幽灵攻击"。
- 为什么只挡模拟分支：自己走 `MC.gameMode.attack` 的分支（`:191`）不受 `missTime` 影响（该冷却只在原版点击路径上），在那里加判定只会平白降低出手频率（是否要按 Killaura 口径也遵守，见「建议（未做）」1）。
- 可验证性：判定本身是既有的纯逻辑 `CombatActionPolicy.isAttackMissCooldownActive(int)`（`missTime > 0 && missTime <= 10`，`:15-18`），本次只消费它、未新增纯类，因此没有新增单测；新旧差异来自上面这一组具体输入下的控制流（写模拟键+重置计时器 ↔ 直接返回）。

未改任何 setting 的名字、默认值、取值范围。

## 故意不搬

- `AutoClicker.kt:26-27` MinCPS/MaxCPS + `AutoClicker.kt:101-103` 的墙钟随机延迟：1.20.1 有原版攻击强度倍率（`0.2 + 0.8f²`）与 10 tick 受击无敌窗口，纯墙钟连点在伤害上是净损失；本工程的 `AttackSpeedSliderSetting`（0 = auto，`settings/AttackSpeedSliderSetting.java:59-68`）+ `Minimum cooldown` 已更强。
- `AutoClicker.kt:28` RightClick / `:87-95` 右击自动点击 / `:30-31` BlockClick、BlockOnly：属"自动左右键"功能，TriggerBot 是条件触发式攻击，语义不同。
- `AutoClicker.kt:32,44-63` JitterClick：参考直接改 `mc.player.rotationYaw/rotationPitch`，1.20.1 反作弊不会因此改判，搬进来只会让真实准星乱抖。
- `AutoClicker.kt:83-85`：`blockOnly` 的早退写成 `return@decentralizedListener`，会连带终止 `repeat(2)` 的剩余迭代（`repeat` 里 return 出的是外层 lambda），是参考自身的不一致，不抄。

## 建议（未做）

1. **非模拟点击分支是否也要遵守 `missTime`**：Killaura/MultiAura 默认遵守（`KillauraHack.java:111-113` 默认 true），但 TriggerBot 直接发 `gameMode.attack` 时该冷却不会吞掉攻击，加了只会降低出手频率。要与 Killaura 口径一致就得**新增**一个 opt-in 设置（新增设置会改动设置表，未擅自做），留给父 agent 决定。
2. **原版依据（已由父 agent 用原版源码核实，本条的残余不确定性已消除）**：仓库内自带反编译源
   `neoforge/versions/1.20.2/build/neoForm/neoFormJoined1.20.2-20231019.002635/steps/unzipSources/unpacked/net/minecraft/client/Minecraft.java`
   就有客户端类，`startAttack()` 在 `:1668-1670` 明确写着 `if (this.missTime > 0) return false;`（**在派发任何攻击之前就返回**），
   而 `missTime = 10` 只在打空时设置（`:1671-1677` 的 `hitResult == null` 与 `:1702-1705` 的 `MISS`，都带 `gameMode.hasMissTime()` 条件），
   `:1849-1850` 每 tick 递减。所以「`missTime ∈ (0,10]` 期间原版点击不会产生攻击包」是原版源码事实，本改动成立、不需要回退。
   （`:1835` 的 `missTime = 10000` 是「有界面打开时点在界面上」用的哨兵值，`CombatActionPolicy.java:17` 的 `<= 10` 上界正是在排除它。）
3. `Range`（默认 4.25，`:43-44`）在目标来自 `MC.hitResult`（原版触及距离）的前提下只有"小于原版距离"时才起作用，超出部分永远选不中（除非 Reach 类 hack 放大触及距离）。属于设置语义而非缺陷，未改。
