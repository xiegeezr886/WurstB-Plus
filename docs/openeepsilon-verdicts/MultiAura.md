# MultiAura 审计结论

状态：已优化

本 hack 是 FDPClient KillAura 多目标模式 + LiquidBounce 点击调度的移植（见 `MultiAuraHack.java:1-11` 的文件头），
参考项目 OpenEpsilon 的 KillAura **只有单目标**（见下表第 1 行），因此多目标相关差异无法从参考侧对照，
只能做工程内部一致性审计。本次只做可在代码内严密验证的正确性修复，未新增纯逻辑类（下面「实际改动」说明理由）。

## 对照证据

| 关注点 | 参考（_oe_ref 相对路径:行） | 本工程（相对路径:行） | 判定 |
| --- | --- | --- | --- |
| 多目标攻击模型 | `src/main/kotlin/studio/coni/epsilon/module/combat/KillAura.kt:68,104`（`currentTarget` + `targetList.minByOrNull{priority}`，全程只有一个目标） | `hacks/MultiAuraHack.java:416-496`（一次点击扫全部合法目标并逐个 `gameMode.attack`） | 不适用（参考无多目标模式，参考手法无法对照） |
| 每 tick 目标数量上限：超出上限时砍掉谁 | `KillAura.kt:104` 按 priority 取最小的那 1 个 | `hacks/MultiAuraHack.java:546-567`（`collectAttackTargets`）+ `util/MultiTargetAttackPlanner.java:30-34`（`filter(...).limit(maxTargets)` **保持传入顺序**截断），上限设置见 `hacks/MultiAuraHack.java:120-121`（0=unlimited） | **真 bug，已修**：修复前传入的是 `MC.level.entitiesForRendering()` 的原始顺序，`Target limit` 砍掉的是渲染顺序里任意的前 N 个，与主目标的优先级选择（`hacks/MultiAuraHack.java:520-532`）无关 |
| 排序键是否稳定 | `KillAura.kt:280-286` `priority()` 单键 + `minByOrNull` | 修复前：无排序；修复后 `hacks/MultiAuraHack.java:538-544` = 类型权重 → `Priority` → 距离 → `Entity::getId`（与主目标同一键，同分时按 id 稳定） | **真 bug，已修**：比较器以 `Entity::getId` 收尾，截断结果确定、不随实体增删抖动 |
| 每个目标的攻击冷却/间隔是否独立 | `KillAura.kt:266-268`（`hasDelayRun` 用共享的 `attackMS - lastAttackMS`，只服务唯一目标） | `util/CombatClickScheduler.java:107-122`（全局 CPS 调度，不是 per-target）+ `hacks/MultiAuraHack.java:416-496`（每次点击把**所有**合法目标都打一遍） | 非 bug（已核实无饥饿）：计时器确实是共享的，但攻击动作是"一次点击扫全部目标"，不存在轮询式分配，因此不会有目标永远轮不到；唯一 per-target 门限是 `hurtTime`（见下两行），语义正确 |
| 无敌帧 | `KillAura.kt:288-300` `isValid`（只有 `health == 0f`，**没有** hurtTime 判定） | `hacks/MultiAuraHack.java:569-574`（`getHurtTime(entity) <= hurtTime.getValueI()`，设置 `:118-119`，默认 10 = 不过滤）+ `util/MultiTargetAttackPlanner.java:31-32`（同一条门限在 planner 内再判一次） | 本工程更好（参考缺这条），不动 |
| 死亡 / 好友 / 反作弊假人 / 自己 | `KillAura.kt:289-291`（`isDead` / `isFriend` / `AntiBot.isBot` / 自己 / `EntityArmorStand`） | `util/EntityUtils.java:38-45`（`IS_ATTACKABLE`：`isRemoved` / `health > 0` / 非自己 / 非 FakePlayer / 非好友 / 非 AntiBot）+ `hacks/MultiAuraHack.java:571-572`（`entityFilters` 20 项） | 等价，不动 |
| 距离 / 墙 | `KillAura.kt:291,296`（`range` 与 `canEntityBeSeen` 都在 `isValid` 里；`walls` 开关一票否决） | `hacks/MultiAuraHack.java:576-589`（距离门限 + `hasLineOfSight` ∨ `throughWallsRange` 豁免）、`:633-637`（扫描范围）、`:639-656`（攻击范围） | **副目标门限有真 bug，已修**（见下） |
| 副目标攻击距离门限 | —（无多目标） | 修复前 `hacks/MultiAuraHack.java:639-656`：(a) `distance >= throughWallsRange ? range + scanRange : throughWallsRange`，把**扫描**用的 `scanRange` 漏进攻击门限；(b) 该式不是距离的单调函数 | **真 bug，已修**：(a) 默认配置下副目标可打到 3.7+2=5.7 格，而主目标被 `isLookingAtPrimary`(`:822-837`) 用 `rolledRangeFor()`(`:620-631`) 截在 3.7 格 —— 同一 tick 内"副目标比主目标打得远"，且超出了用户配置的 `Range`；(b) 冲刺且 `sprintRangeReduction > 0` 时 `(T-red, T)` 区间内的近目标被拒、更远的目标反而通过 |
| 旋转：多目标时朝向谁 | `KillAura.kt:79-81,303-308`（每个 tick 朝唯一 `currentTarget` 发 silent 旋转） | `hacks/MultiAuraHack.java:752-785`（朝 `selectPrimaryTarget` 的瞄准点 `updateRotation`）、`:787-794` `rotationForAttack`、`:822-837` `isLookingAtPrimary` | 设计事实：**只朝主目标**，副目标不参与朝向、只校验距离+视线，与 `KillauraHack.java:747-750` 的"每个目标各自校验门限、朝向只服务当前目标"设计一致；是否要求每个副目标都在视锥内属反作弊取舍，无工程内证据可判错，不动 |
| 攻击后残留朝向 | `KillAura.kt:335-339` `resetRotation()`（把 yaw/pitch 复位到玩家真实朝向） | `hacks/MultiAuraHack.java:470-489`：`ON_TICK` 时先 `sendFullRotation(attackRotation)` 再攻击，攻击后 `sendFullRotation(new Rotation(MC.player.getYRot(), MC.player.getXRot()))` 复位 | 非 bug（已核实）：`SILENT` 下 `MC.player` 的 yaw/pitch 未被改写，复位发回的是玩家真实朝向；`VISIBLE` 下客户端朝向本就是"看向目标"，一致 |
| 死状态字段 | — | 修复前 `hacks/MultiAuraHack.java:253` 的 `targets` 被 `:379,394,985` 写、**从未被读**；攻击循环用的是 `:473` 当场重算的 `freshTargets` | 已清理：删掉该字段与 `:394` 的无用全量扫描（连带每 tick 一次多余的 LOS 射线），行为不变 |

## 实际改动

1. `src/main/java/net/wurstclient/hacks/MultiAuraHack.java`
   - 抽出 `targetComparator()`（`:534-544`，键 = 类型权重 → `Priority` → 距离 → `Entity::getId`），`selectPrimaryTarget()`（`:526`）与新增的多目标收集共用同一个键。
   - 重写 `collectAttackTargets()`（`:546-567`）：先按 `isValidScanTarget` 粗筛，再按 `targetComparator()` 排序，最后才交给 `MultiTargetAttackPlanner.plan(...)` 过滤 + 按 `Target limit` 截断。
     为什么：`plan` 只保留传入顺序的前 N 个（`util/MultiTargetAttackPlanner.java:33-34`），此前喂的是 `entitiesForRendering()` 的原始顺序，`Target limit` 砍掉的是任意 N 个 —— 设为 1 时打到的甚至不是正在瞄准的主目标。
     能验证什么：上限生效后命中的一定是优先级最高的 N 个，且同分时按实体 id 稳定；粗筛用的 `getMaximumRange()`（`max(range+scanRange, throughWallsRange)`）恒 ≥ 攻击门限（`range - sprintRangeReduction`），所以粗筛不会漏掉任何合法攻击目标 —— 等价性与截断语义由既有单测 `src/test/java/net/wurstclient/util/MultiTargetAttackPlannerTest.java:19,44,51` 覆盖。
   - 修正副目标攻击距离 `getMultiAttackRange()`（`:639-656`，改为无参）：`max(0, range - (冲刺 ? sprintRangeReduction : 0))`；两处调用点同步改为无参（`:581`、`:748-749`）。
     为什么：见上表两行「真 bug」。(a) 副目标不再按扫描范围超打（默认 5.7 → 3.7）；(b) 门限恢复成距离的单调函数，冲刺削减不再制造"近的不能打、远的能打"的死区；(c) `throughWallsRange` 的职责回归为"在该距离内忽略墙体"（`:586-588`）与 `CombatAimPointPlanner` 的可见距离（`:601`），与 `KillauraHack.java:763-774` 的口径一致。副目标门限用未滚动的 `range.getValue()`（而非 `rolledRangeFor()`）是刻意的：`rolledRangeFor()`（`:620-631`）会递减 `rangeRollCounter`，逐候选调用会打乱 `Range chance` 的滚动节奏。
   - 删除死字段 `targets`（原 `:253`）及其三处赋值（原 `:379,394,985`）。`feedAura`/`TargetHud`/`TargetShader` 等外部消费者都只用 `getCurrentTarget()`（`HeldItemRendererMixin.java:57`、`hud2/elements/TargetHudElement.java:190`、`TargetShaderHack.java:65`），行为不变；附带去掉每 tick 一次无人消费的全量候选扫描 + LOS 射线。
   - 未新建纯逻辑类：两个修复点一个是"把已排序的列表喂给既有的、已被单测覆盖的 planner"，另一个是一行 clamp 算术。为后者单独抽 `util/MultiAuraRangePolicy.java` 属纯装饰性抽象（唯一消费方就是本 hack，单测只能断言 `x-y` 与 `max(0,·)`），按 `_BRIEF.md` 第 7 条不做。

2. 未改任何共享文件；未改任何 setting 的名字/默认值/取值范围（`Target limit`、`Range`、`Scan range`、`Through walls range`、`Sprint range reduction` 全部保持原样，只改它们被消费的方式）。

## 故意不搬

- 参考的单目标 silent 旋转状态机（`KillAura.kt:72,335-339` 的 `isSpoofingAngles` + 手动 `resetRotation`）：本工程 `RotationQueue`/`CombatRotationController` 已经是队列化的 silent 旋转（`hacks/MultiAuraHack.java:333,346,752-785`），搬过来会和在跑的旋转队列打架。
- 参考的 `Mode.Hypixel` 先 `swingArm` 后 `attackEntity` 的包序（`KillAura.kt:233-239`）：那是 1.12.2 时代针对特定反作弊的包顺序技巧，1.20.1 不成立。
- 参考在 `isValid` 里内联的 `walls`/`invisible` 判定（`KillAura.kt:296-297`）：本工程由 `EntityFilterList.genericCombat()` 的 20 项过滤器（含 Invisible、Named 等）+ `checkLOS` 覆盖，重复实现只会分叉。
- 参考 `KillAura.kt:201-227` 的 `calculateDamage` 选武器：本工程 `autoSwordHack.setSlot(...)`（`hacks/MultiAuraHack.java:384`）已覆盖。

## 建议（未做）

- `util/EntityUtils.java:38-45` 的 `IS_ATTACKABLE` 未排除**旁观者**与**创造模式玩家**（全工程共享判定，`KillauraHack` 同样受影响）。修它属于跨 hack 的共享文件改动，按 `_BRIEF.md` 第 4 条本次不动。
- `Range chance` 只作用于主目标门限（`rolledRangeFor()` 的有状态滚动使它无法在逐候选的 `isValidAttackTarget` 里安全调用）。若要让它同时约束副目标，需要把每 tick 的滚动结果缓存成字段 —— 属共享状态语义改动，收益不确定，未做。
- `CombatClickScheduler` 允许每 tick 多次点击（`util/CombatClickScheduler.java:107-122`）；当 `Minimum/Maximum item cooldown < 100%` 时，同一 tick 内会对同一实体重复发攻击包（默认配置 100% 时只有第一次能过 `isCooldownPassed`，不会发生）。服务端无敌帧会吸收重复伤害，若要严格去重需要 tick 级 `attacked` 集合，属行为取舍，未做。
- `hacks/MultiAuraHack.java:477` 在攻击循环里又调了一次 `isValidAttackTarget`，而 `collectAttackTargets()` 里的 planner 已经用它过滤过（改动前就是这样，不是本次引入）。每次点击每个候选因此多算一次 `BlockUtils.hasLineOfSight` 射线；删掉它是纯性能优化、且会失去"计划生成到落包之间的极小窗口内失效"的保险，属主观取舍，未做。
- 本轮未编译、未跑单测（按 `_BRIEF.md` 第 1 条由父 agent 统一执行）。
