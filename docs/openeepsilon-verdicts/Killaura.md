状态：不适用

## 对照证据

| 关注点 | 参考（_oe_ref 相对路径:行） | 本工程（相对路径:行） | 判定 |
| --- | --- | --- | --- |
| 默认 Priority 键 | `module/combat/KillAura.kt:60` `priorityMode by setting("Priority", Priority.Distance)` | `hacks/KillauraHack.java:126-127` 默认 `Priority.HEALTH`；可选键见 `util/CombatTargetUtils.java:268-283`（DISTANCE/ANGLE/HEALTH/HURT_TIME/SCORE） | 键集更全，默认不同但合法（HEALTH = 血量+吸收，与参考的 `relativeHealth` 同义）。**不改默认值**（会变更所有现有用户的选人行为） |
| 墙后 / 视野外目标 | `KillAura.kt:296` `if(!walls && !canEntityBeSeen) return false`（`walls` 默认 true，等于默认不过滤） | `hacks/KillauraHack.java:722-732` 扫描时走 `CombatAimPointPlanner.find(...)`；`util/CombatAimPointPlanner.java:48-52` 把每个采样点按 `hasLineOfSight` 分成「可见 / 穿墙」两档，**穿墙点只接受到 `getWallRange()`**（默认 3 格） | 无缺失。`checkLOS=false` 是刻意的：墙后由瞄点规划器分级处理，不是漏判 |
| 攻击时序 / CPS 调度 | `KillAura.kt:148-159` 自算 `atkSpeed`；`KillAura.kt:122-133` 用 `getCooledAttackStrength(...) >= 1f` | `util/CombatClickScheduler.java`（滚动点击数组 + 点击模式 + 冷却），调用点 `hacks/KillauraHack.java:396,421,444-446` | 本工程更强（共享调度器 + 点击模式） |
| 原版攻击冷却 | 同上 | `hacks/KillauraHack.java:631-639`：`wurst_getAttackStrengthTicker() / wurst_getCurrentItemAttackStrengthDelay()`，默认冷却区间为 1..1 即满蓄力才打；另查 `getMissTime()`(:449-452) | 等价（1.20.1 的 `getAttackStrengthScale` 正是这两个 accessor 的比值），1.20.1 必需项已具备 |
| 旋转 | `KillAura.kt:317-319` 直接 `PlayerPacketManager.sendPacket { rotate(...) }` | `hacks/KillauraHack.java:247-248` 构造 `CombatRotationController(RotationQueue.Priority.COMBAT)`；SILENT 走 `RotationQueue`，VISIBLE 走 `applyClient()`（可见模式本意），ON_TICK 才额外 `sendFullRotation` | 已走队列，比参考多优先级仲裁与插值曲线。见 §0.2 ② |
| 死亡 / 好友 / AntiBot / 旁观 | `KillAura.kt:289-291`（`isDead`、`isFriend`、`AntiBot.isBot`、`EntityArmorStand`） | `util/EntityUtils.java:38-45` `IS_ATTACKABLE`，所有入口都经过它（`CombatTargetUtils.java:165`、`KillauraHack.java:737`） | 已覆盖，且比参考多 `FakePlayerEntity` |
| 创造模式 / 队伍 | 参考无（只靠 `isFriend`） | `util/CombatTargetUtils.java:251-266` 走 `GuiPreferences.TargetType`；玩家过滤见 `settings/filterlists/EntityFilterList.java:61-90` | 等价或更强 |
| 距离 / 视野 | `KillAura.kt:291` 用球心距离 `getDistance > range` | `util/CombatTargetUtils.java:204-218` 用眼睛到**碰撞箱最近点**；视野用 `RotationUtils.getAngleToLookVec` 分 `fov/2` | 本工程更准确（球心距离会低估贴脸目标） |
| Hurt time 门限 | 参考**没有** hurtTime 过滤 | `hacks/KillauraHack.java:747` 拒绝 `hurtTime > limit`，等价于放行 `hurtTime <= limit`；`util/MultiTargetAttackPlanner.java:31-32` 同为 `<= limit` 放行 | 已核实**不是** bug，详见下节 |

## 实际改动

无。

## 已核实并否决的一处「疑似 bug」（留档，避免下次重复劳动）

本轮审计中曾得出「`Hurt time` 滑块完全失效」的结论并提交了改动，**复核后判定为误判，改动已全部回退**：

- 原式：`living.hurtTime > hurtTime.getValueI()`（`hacks/KillauraHack.java:747`，已还原）。
- 误判理由：「hurtTime 与滑块量程同为 0..10，所以 `>` 在 11 个取值下全为 false」。这是错的：hurtTime=10、limit=5 时 `10 > 5` 为真，目标被拒绝。该设置在 limit < 10 时确实生效；limit=10（默认值）不拒绝任何目标，是因为默认值等于原版 `hurtDuration` 上限，属于「默认最宽松」的合理取值，不是失效。
- 提出者还声称 `util/MultiTargetAttackPlanner.java:31-32` 用的是另一套「上限」语义，与实际不符：那里是 `max(0,hurtTime) <= limit` 放行，与本处 `hurtTime > limit` 拒绝**完全同义**。
- 该改动附带的单测文件里，我曾以为注释是**乱码**；但随后确认本环境的 PowerShell 5.1 默认编码是 `gb2312`，用 `Get-Content` 读任何 UTF-8 中文文件都会显示成乱码（同一次会话里读我自己写的 UTF-8 文件也是乱码）。**所以这条证据不成立，不作为回退理由**。回退的真实理由只有两条：逻辑与原式逐字节等价（`max(0,hurtTime) > max(0,limit)`），以及它所声称的 bug 并不存在。
- 结论：这 3 个文件（1 个新 util 类、1 个新测试、hack 内 2 行改动）已全部删除，`KillauraHack.java` 用 `git checkout` 还原到 `b32b9ea`。

## 故意不搬

- 参考的 `KillAura` 在 1.12.2 上靠包级手法维持攻击与击退判定，1.20.1 没有等价物（见 §0.3 对包级手法的总述）。
- 参考是**单目标**实现，本工程的多目标调度没有对照对象，只能做工程内部一致性审计（结论见 `MultiAura.md`）。

## 建议（未做）

- `util/EntityUtils.IS_ATTACKABLE` 会放行末影水晶与潜影贝子弹，属共享文件，改动要评估全部 combat hack 的影响面，本轮不动。
- `hacks/KillauraHack.java:654` 另有一处硬编码 `living.hurtTime > 7`，与可配置的 `Hurt time` 设置语义重复。不是 bug，合并涉及行为取舍，留给后续。
