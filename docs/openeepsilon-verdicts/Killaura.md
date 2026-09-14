状态：已优化

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
| 粘性目标 / 切换节奏 | 参考每 tick 纯重算 `minByOrNull { priority(it) }`（`KillAura.kt:104`），没有任何粘性/切换延迟设置 | `hacks/KillauraHack.java:684-693` → `util/TargetTracker.java`（LiquidBounce 的 target tracker 改写成 Forge/Mojmap），带 `Sticky target` / `Switch delay` / `Switch advantage` | 本工程独有；但三者之间有配置矛盾，见「实际改动」 |

## 实际改动

### `Sticky target` 打开时（**默认就是打开**），`Switch delay` / `Switch advantage` 完全失效

`util/TargetTracker.java:36-37`：

```java
		if(sticky && currentValid)
			return target;
```

只要当前目标仍然有效就直接返回，后面读 `switchDelay`（`:45-46`）和 `switchAdvantage`（`:48-60`）的
分支根本走不到。而 `hacks/KillauraHack.java:128-130` 里 `Sticky target` 的默认值是 **true**，也就是
**默认配置下这两个滑条从 0 调到 20、从 0% 调到 100% 都不会产生任何行为变化**——它们只描述"什么时候
允许换目标"，而 sticky 打开时根本不换目标。

修法（不改任何行为，只让设置面板说实话）：给两个滑条加

```java
		.visibleWhen(() -> !stickyTarget.isChecked());
```

`hacks/KillauraHack.java:131-140`。与本工程其它条件设置的做法一致（例如 `MobEspHack.java:54` 的 `colorRange`）。
关掉 `Sticky target` 后它们照旧生效。

## 已核实并否决的一处「疑似 bug」（留档，避免下次重复劳动）

本轮审计中曾得出「`Hurt time` 滑块完全失效」的结论并提交了改动，**复核后判定为误判，改动已全部回退**：

- 原式：`living.hurtTime > hurtTime.getValueI()`（`hacks/KillauraHack.java:747`，已还原）。
- 误判理由：「hurtTime 与滑块量程同为 0..10，所以 `>` 在 11 个取值下全为 false」。这是错的：hurtTime=10、limit=5 时 `10 > 5` 为真，目标被拒绝。该设置在 limit < 10 时确实生效；limit=10（默认值）不拒绝任何目标，是因为默认值等于原版 `hurtDuration` 上限，属于「默认最宽松」的合理取值，不是失效。
- 提出者还声称 `util/MultiTargetAttackPlanner.java:31-32` 用的是另一套「上限」语义，与实际不符：那里是 `max(0,hurtTime) <= limit` 放行，与本处 `hurtTime > limit` 拒绝**完全同义**。
- 该改动附带的单测文件里，我曾以为注释是**乱码**；但随后确认本环境的 PowerShell 5.1 默认编码是 `gb2312`，用 `Get-Content` 读任何 UTF-8 中文文件都会显示成乱码（同一次会话里读我自己写的 UTF-8 文件也是乱码）。**所以这条证据不成立，不作为回退理由**。回退的真实理由只有两条：逻辑与原式逐字节等价（`max(0,hurtTime) > max(0,limit)`），以及它所声称的 bug 并不存在。
- 结论：这 3 个文件（1 个新 util 类、1 个新测试、hack 内 2 行改动）已全部删除，`KillauraHack.java` 用 `git checkout` 还原到 `b32b9ea`。

### 第 14 轮补充：否决结论仍然成立，但那条门限有一个可量化的副作用（未改代码）

这轮又把「锁敌」这条链重读了一遍，结论与上面一致（**不是**失效，也**不是** bug），只是补一条本次新看到的、
上面没有提到的后果，供以后决定要不要调整时参考：

- 事实依据（1.20.2 源码）：`LivingEntity.java:1620-1621` 是 `hurtDuration = 10; hurtTime = hurtDuration;`
  （`:1168-1169` 是客户端收到受击动画时的同一条赋值），所以 **hurtTime 的取值范围就是 0..10**。
- 由此得到的副作用：limit < 10 时，"你自己刚打中的目标"也会命中这条门限——命中瞬间它的 `hurtTime` 就是 10，
  超过 limit → `createPlan` 返回 null → 该目标从 `plans` 里消失 → `TargetTracker` 判定 `!currentValid`
  并 `replace(null, ...)`，于是 KillAura 丢掉目标并停手 `10 - limit` tick。极端取值 `Hurt time = 0` 时，
  同一目标只能每约 10 tick 打中一下。
- 但这条在本工程里是**刻意的设计**，不是疏漏：同一个门限在 `util/MultiTargetAttackPlanner.java:31-32`
  也用同样的方向实现（`max(0,hurtTime) <= limit` 才放行），两处一致；而且**默认配置下它根本不触发**——
  默认 `Hurt time = 10` 等于不过滤，且默认攻击冷却（`Minimum/Maximum cooldown` 默认 1..1，即满蓄力）下
  两次命中的间隔约 12 tick > 10，目标早已不满足门限。
- 所以本轮**只记录、不改行为**：`hacks/KillauraHack.java:747` 原样保留。要改的话属于行为取舍
  （"高 CPS 下命中后是否停手"），而且必须同时改 `MultiTargetAttackPlanner` 才能保持一致。

## 故意不搬

- 参考的 `KillAura` 在 1.12.2 上靠包级手法维持攻击与击退判定，1.20.1 没有等价物（见 §0.3 对包级手法的总述）。
- 参考是**单目标**实现，本工程的多目标调度没有对照对象，只能做工程内部一致性审计（结论见 `MultiAura.md`）。
- 参考 `KillAura.kt:104` 的"每 tick 纯重算最优目标"：本工程有粘性/切换延迟/优势门限，换过去等于把这些设置作废，
  而且同分目标之间会抖动，不搬。

## 建议（未做）

- `util/EntityUtils.IS_ATTACKABLE` 会放行末影水晶与潜影贝子弹，属共享文件，改动要评估全部 combat hack 的影响面，本轮不动。
- `hacks/KillauraHack.java:654` 另有一处硬编码 `living.hurtTime > 7`，与可配置的 `Hurt time` 设置语义重复。不是 bug，合并涉及行为取舍，留给后续。
- `Switch advantage` 的百分比是作用在**名次**上的：`selectTarget()` 传给 `TargetTracker` 的 score 是"在 `plans` 里的下标"
  （`:687-691`），于是 `requiredAdvantage = |当前名次| * pct / 100`——当前目标排第 50 名时"10% 优势"要求新目标
  至少前进 5 名，排第 1 名时几乎任何更好的目标都能换。语义自洽但与"优势 10%"的直觉不符；要精确应把 score
  换成真正的质量分（`CombatTargetUtils.getScore(...)`）。会改变现有手感，未做。
- 验证边界：本轮只有 `compileJava`/`compileTestJava`/`test`（全绿），没有实机战斗验证；
  `Switch delay`/`Switch advantage` 的 `visibleWhen` 只影响设置面板显示，不改变任何选人行为。

## 排查：「有时候打玩家/动物不生效」的已知路径（只定位，不改代码）

按"卡在哪一步"分四组，证据全部在源码里；**没有实机验证**，都是从代码推出来的充分条件
（命中任一条就足以解释现象），不代表这些条件一定会同时出现。

### A. 根本没锁上（表现为这类目标一直不打）

| 触发条件 | 证据 |
| --- | --- |
| 全局设置 Global Settings → Target 里 `Players` / `Animals` 被关掉 | `clickgui2/GuiPreferences.java:45-49`（默认全开，但会写进 config：`:81-90`、`:128-132`）→ `CombatTargetUtils.java:251-266 isGlobalTargetEnabled` → `isValid:167` 直接拒。玩家走 `PLAYERS`，动物走 `ANIMALS` |
| 对方是你的好友（`.friend add`，或在对方身上**中键**，见 `mixin/MinecraftClientMixin.java:129`） | `EntityUtils.java:38-44 IS_ATTACKABLE` 里的 `!WURST.getFriends().isFriend(e)` → `CombatTargetUtils:165` |
| 该玩家与你同队且 `Teams` 被关掉 | `CombatTargetUtils.java:256-257` |
| 你自己勾了 KillAura 的过滤器（`Filter passive mobs` 挡猪牛羊鱼、`Filter neutral mobs` 挡狼/羊驼/蜜蜂、`Filter babies` 等） | `settings/filterlists/EntityFilterList.java:55-56`（**勾上才生效**）+ `FilterPassiveSetting.java:28-41`；`genericCombat()` 里默认全是 `false`/`Mode.OFF`，所以默认不挡动物 |

### B. 锁上了，但这一 tick 不出手 / 只空挥（"有时候"最主要的一组）

| 触发条件 | 证据 |
| --- | --- |
| `Require not breaking`（默认**开**）：你正在破坏方块 | `KillauraHack.java:186-187` + `requirementsMet():599`（`MC.gameMode.isDestroying()`） |
| 你正在使用物品（吃/喝/拉弓/举盾）且 `Attack while using items`（默认关）没勾 | `prepareForAttack():516-517` |
| `Criticals = Always`（默认 SMART）：必须处于下落状态才允许攻击，站在地上一发都不打 | `CriticalsSelectionMode.allowsAttack:1184-1193`，调用点 `canAttackNow:604` |
| `Attack cooldown`（默认开）且原版 `missTime > 0`：你手动挥空后原版会设 `missTime = 10`，这段时间 KillAura 的点击被跳过 | 原版 `Minecraft.java:1702-1705`（MISS → `missTime = 10`）、`:1849-1850`（每 tick 递减）；`CombatActionPolicy.java:15-17`、`KillauraHack.java:451-454` |
| 转向还没对齐：`isLookingAt` 要求"沿本次攻击用的朝向"的射线真的切到目标碰撞箱 | `isLookingAt():860-875`（用 `rotation.toLookVec()` 做 clip）；没切到就走 `performFailSwingAttempt`（`:461`、`:476`），而 `Fail swing`（默认开，`:226`）**只挥手臂、不发攻击**——这正是"看着在打、对方不掉血" |
| 目标被方块挡住 / 在墙后而 `Through walls range` 很小 | `createPlan():712-732` → `CombatAimPointPlanner.find:44-60`；`refreshed == null` → `:459-463` 空挥 |
| 动物乱跑（小鸡/兔子碰撞箱小）时，`Target prediction`(1.5) 把瞄点推到范围/FOV 外，该 tick 判无效 | `CombatTargetUtils.isValid:170-175`（用预测瞄点算 FOV）、`getPredictedPreferredPoint():776-780` |
| 开着背包：`Ignore open inventory`（默认开）会先偷偷发关容器包再打；把它关掉就完全不攻击 | `canAttackNow:606-607`、`prepareForAttack:519-521` |

### C. 打出去了，但对方不掉血（原版机制，客户端无法绕）

| 触发条件 | 证据 |
| --- | --- |
| 目标处于原版无敌帧：10 tick 内刚被任何人打中（`invulnerableTime = 20`，第二下只在伤害更高时结算差值） | 原版 `LivingEntity.java:182`（`invulnerableDuration = 20`）、`:1620-1621`（命中即 `hurtTime = 10`） |
| 目标举盾面朝你、你手上不是斧头：`Ignore target shield` 只影响"要不要锁他"，**不改变原版盾牌结算** | `KillauraHack.java:154-155`、`:747-749`（锁定判定）与 `:642-644`（斧头例外） |
| 目标在创造/无敌，或有吸收/抗性/图腾 | 纯原版结算 |

### D. 打出去了，但打在**别的**实体上（对方自然不掉血）——**本轮已修**

`Raycast` 默认是 **All**（`:157-158`）。All 模式下射线原来只要求实体 `isPickable()`，
**不检查 `isValidScanTarget` / `IS_ATTACKABLE` / 过滤器**：`resolveRaycastTarget():843-857` 的谓词是
`mode == RaycastMode.ALL || isValidScanTarget(entity)`，且 `performScheduledAttacks` 里
`traceAllTarget` 会跳过 `isValidAttackTarget`（`:467-478`）。于是：

- 你和敌人之间站着一个**好友**、假人、或 AntiBot 认定的机器人时，这一下打在它身上，**敌人不掉血**；
- 反过来，All 模式也会打好友，并且**把 AntiBot 的保护整个绕过去了**（`IS_ATTACKABLE` 里
  `antiBotHack.isBot(...)` 那条检查在 `EntityUtils.java:45`）。

**反例输入（Rule 9）**：`Raycast = All`（默认），你锁着敌人 A，好友 B 站在你和 A 之间 3 格内的瞄线上。

| | 旧 | 新 |
| --- | --- | --- |
| `resolveRaycastTarget` | 返回 B（`isPickable()` 为真） | 谓词里 `IS_ATTACKABLE.test(B)` 为假 → 射线不接受 B → 返回 `selected` = A |
| `traceAllTarget` | true → 跳过 `isValidAttackTarget` | false → 走完整校验 |
| 实际攻击 | `attack(player, B)`：A 不掉血、好友被误伤 | `attack(player, A)`：A 掉血、B 不受影响 |
| AntiBot 机器人挡路 | 照打（`antiBotHack.isBot` 被绕过） | 不接受该实体，改用锁定目标 |

改动只有一行谓词（`KillauraHack.java:843-858`）：加 `EntityUtils.IS_ATTACKABLE.test(entity)`。
它只排除"已死/自己/假人/好友/AntiBot 机器人"，盔甲架、水晶、潜行贝子弹仍然算合法目标
（`EntityUtils.java:38-45`），所以 `All` 档"打射线里的任何实体"这个本意没有变，
`Enemy`（只打过滤器允许的）与 `None`（只打锁定目标）也都不受影响。
