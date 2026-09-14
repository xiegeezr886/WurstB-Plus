# OpenEpsilon 参考重构 · 覆盖清单

参考：`CakeSlayers/OpenEpsilon`（Kotlin，已 clone 到 `D:\WurstB\_oe_ref`，只读）。
本文件是**进度账本**：每个 hack 一行，状态在重构过程中更新。

## 0. 先说一个决定性事实：参考项目是 1.12.2 的

`_oe_ref/build.gradle.kts:61` 是 `minecraft("net.minecraftforge:forge:1.12.2-14.23.5.2860")`，
Java 8、MCP `39-1.12` 映射、Kotlin 1.6。它是一个 **1.12.2 客户端**（Epsilon 泄露码的重制版），
不是新版本客户端。这决定了「参考」的边界：

| 类别 | 能借鉴吗 | 原因 |
| --- | --- | --- |
| 包级 exploit / 时序绕过 | **不能** | `Critical.kt:44-110` 的 `Packet/NCP/AAC/Hypixel` 四种模式全是 1.8~1.12 的「向上位移包骗暴击」。1.20.1 的暴击判定（`fallDistance>0 && !onGround && !onClimbable && !isInWater && !isPassenger && !isSprinting`）不同，这些包序列在新版本要么无效要么踢人 |
| 渲染实现 | **不能** | 1.12.2 用 GL11 立即模式 + 自写 shader/font atlas；1.20.1 是 `GuiGraphics`/`RenderSystem`/`VertexConsumer`，API 完全不同 |
| MCP 名称 | 需翻译 | `CPacketPlayer`→`ServerboundMovePlayerPacket`、`EntityLivingBase`→`LivingEntity`、`ItemSword`→`SwordItem`、`posX`→`getX()`、`ridingEntity`→`getVehicle()`、`motionY`→`setDeltaMovement`、`windowClick`→`handleInventoryMouseClick` |
| 背包操作流程 | **能** | `util/inventory/`（`Task`/`Step`/`operation/*`）是抽象设计，与版本无关 |
| 战斗目标选择 / 伤害与减伤模型 | **能** | `CombatManager`、`util/combat/*`（`CombatUtils`/`DamageReduction`/`HoleUtils`/`SurroundUtils`/`MotionTracker`/`ExposureSample`）是数学与排序逻辑 |
| 时序 / 暂停 / 缓存抽象 | **能** | `util/pause/*`、`util/delegate/*`、`util/threads/*`、`TimerManager` 是架构 |
| 移动数学 | 部分能 | `MovementUtils`/`Speed`/`Strafe`/`LongJump` 的数学可以，但 1.13+ 游泳、1.14+ 跳跃、鞘翅差异大，参数不能照抄 |

**结论**：只能借**设计与算法**，不能借**包序列与渲染代码**；与协议时序强相关的旧技巧一律标
「不适用」，不硬搬。

另：本工程核心已比预期成熟，所以不做"为改而改"的重写——`util/CombatClickScheduler.java` 已是
LiquidBounce 系的滚动点击数组 + 冷却 + 点击模式；`util/DamageUtils.java` 的爆炸伤害已带暴露采样、
护甲/韧性吸收、保护附魔与抗性。值得动的是它们**周边**缺失的抽象与缓存。

## 0.1 共享核心进度

| 共享核心 | 状态 | 说明 |
| --- | --- | --- |
| 伤害减伤画像 | **已优化** | 新增 `util/DamageProfile.java`（纯类，11 个单测）+ `DamageUtils.profileOf()`：护甲/韧性/保护 EPF/抗性每实体每 250ms 只扫一次，替代原来的「每次调用扫 4 个护甲槽」。换世界自动失效，无需重置钩子。**数值不变**——EPF 仍由原版 `EnchantmentHelper.getDamageProtection` 产出（1.20.1 附魔是数据驱动的，照抄参考的 `2*level` 会算错），护甲吸收仍走原版 `CombatRules`，画像只负责组合系数 |
| 吸收意识 | **已补齐** | 新增 `DamageUtils.totalHealth/scaledHealth/isLethal`。参考有而本工程原来没有；`scaledHealth = health + absorption * (health / maxHealth)` 是判断「能不能打死 / 会不会掉图腾」的基础 |
| 点击调度 | 不适用 | `CombatClickScheduler` 已是 LiquidBounce 系实现，不逊于参考的 `TimerManager`；其 `tick * 50L` 对**客户端** tick 是正确的（客户端恒 20 TPS），不是 bug |
| 旋转 | **已核对，无可移植项（不适用）** | 参考 `util/math/RotationUtils.kt`(145) + `util/RotationUtil.kt`(178) 对比本工程 `util/RotationQueue.java`(280) / `RotationSmoothing.java`(161) / `RotationUtils.java`(201) / `CombatAimPointPlanner.java`(102)。逐条见下 |
| 背包操作 | **重试窗口已补齐；链内步进与 Task/Future 抽象判定不适用** | 参考 `util/inventory/Task.kt`(148) + `Step.kt`(51) + `management/InventoryTaskManager.kt`(123) 对比本工程 `util/inventory/InventoryActionQueue.java`(166) / `ActionRetryPolicy.java`(92) / `InventoryUtils.java`(261)。逐条见下 |
| 时序 / 暂停 | **已核对，暂不移植（无消费方）** | 参考 `util/pause/IPause.kt`(19) / `HandPause.kt`(17) / `PriorityTimeoutPause.kt`(47) + `process/PauseProcess.kt`(60) 对比本工程 `hack/HackConflictManager.java`(39)。逐条见下 |
| 缓存原语 | **不适用（已有针对性缓存，不需要通用原语）** | 参考的 `util/delegate/*` 是一组通用小工具（`CachedValue` 35 / `FrameValue` 67 / `ComputeFlag` 12 / `AsyncCachedValue` 42 / `CachedValueN` 63，合计约 219 行）。本工程走的是**按需专用缓存**：`EntitySnapshotManager`(94)、`BlockBreakingCache`(46)、`MinimapTerrainCache`(221)、`TwilightCoverCache`(216)、以及第 1 轮加的每实体 `DamageProfile` 缓存。既然每个真实需求都已有对应实现，再加一个通用原语就是**没有消费方的抽象**——参考那几个类本身也只有 12~67 行，收益很小 |
| 目标选择 | **共享核心已加，尚未接入（行为未变）** | 新增 `util/TargetScore.java`（纯类，13 个单测）：按参考 §7 实现五因子加权打分——`distF`(8 格饱和) + `healthF`(20 血饱和) + `armorF`(20 点爆炸实收伤害) + `holeF`(四水平邻向不抗爆占比) + `crosshairF`(`|相对偏航|` 30° 饱和)，默认权重各 0.5，总分降序。**注意：目前没有任何 hack 调用它**，所以还没有换来行为改善。**接入点已经定好，而且有一个坑必须避开**：

- ❌ **不要给 `CombatTargetUtils.Priority` 加一个「在比较器里现算 score」的分支**。该类的 `getComparator()` 是在比较器内部调用 `getScore()` 的（`CombatTargetUtils.java:179-180`），排序期间会被调用 **O(n log n)** 次；现有四个键（distance / angle / health / hurtTime）都廉价所以没问题，但 `armorF` 要做护甲结算、`holeF` 要查四个相邻方块，放进比较器会成倍放大开销。
- ✅ 正确做法：在**取候选列表时**给每个候选算一次 `TargetScore.Inputs`，再用 `TargetScore.rank(candidates, weights)`（已提供，5 个单测覆盖排序/换位/同分稳定/空表/权重跟随）拿排好的下标。
- 顺带一提，`armorF` 可以用第 1 轮加的 `DamageUtils.profileOf()` 缓存来算"20 点标称伤害实收多少"，**不需要**跑暴露度光线投射，所以五个因子全都可以廉价地每人算一次。
- ✅ **入口已经实现**：`CombatTargetUtils.getListByScore(range, fov, aimPoint, filters, checkLOS, maxCount, weights)`——先按距离拿候选，再给每个候选算一次 `Inputs`，最后 `TargetScore.rank` 排序。**没有改动 `getList` 与任何默认行为**；要用打分排序的 hack 自行改调这个入口。
- ⚠️ **一处刻意的近似**：`holeF` 的"暴露面"参考判的是邻向**抗不抗爆**，本工程用"空气或碰撞箱为空"代替（`countExposedSides`），因为 1.20.1 取爆炸抗性要构造 `Explosion` 上下文。所以洞因子在"水/树叶"这类不抗爆但有碰撞箱的方块上会算得比参考保守。
- ✅ **已经接线（opt-in，默认关闭）**：`CombatTargetUtils.Priority` 新增 `SCORE("Score")`，`getList` 见到它就分流到 `getListByScore`；`getScore`/`getComparator` 里为它留了一个**退化为距离**的分支并注明"正常不会走到这里"，免得有人误在比较器里用打分。所以凡是暴露了 `Priority` 设置的 combat hack（如 ClickAura）现在都能选 Score——**但默认值仍是各自原来的（ClickAura 是 ANGLE），升级不会改变任何人的选人行为**，用户主动在设置里选 Score 才生效。
- ⚠️ **仍需实机调手感**：打分排序的实际观感（是否真的比按角度/距离更"聪明"）我无法验证；权重是参考的 0.5 全等值，未必适合本工程的 hack 组合。

| 移动数学 | **不适用（本工程更完善，无可移植项）** | 参考只有单个 `MovementUtils.kt`；本工程是按用途拆开的一套：`MovementPlanner`(87)、`VelocityPlanner`(91)、`AirJumpPolicy`(30)、`SpiderPathPlanner`(29)，外加三个路径处理器（`PathProcessor` 81 / `WalkPathProcessor` 157 / `FlyPathProcessor` 159）与 pre/post-motion 监听器。粒度比参考细，**没有发现需要借鉴的算法**；参考里那几处已知 bug（见 §17，例如纯 X/Z 移动时 `isMoving` 返回 false）更不该抄 |

**受益的常用 hack**（都走 `DamageUtils`，因此共享这一优化）：`CrystalAura`、`AnchorAura`、
`AutoTotem`、`Protect`、`AutoTrap`、`Surround`、`AutoCity`、`HoleFiller`、`SelfTrap`、`AutoCev`。

### 0.1.1 旋转 / 背包 / 时序暂停的逐条核对（第 13 轮）

**旋转 → 不适用（本工程已经更强）**

| 关注点 | 参考 | 本工程 | 判定 |
| --- | --- | --- | --- |
| 瞄点选择 | `util/math/RotationUtils.kt:56-61` 把眼睛位置 `coerceIn` 进目标碰撞箱，瞄"最近的那个点"而不是中心 | `util/CombatAimPointPlanner.java:76-89`：同样的 `clamp(box, eyes)`（:80）之外还采 3×3×3 共 27 个盒内点，按「能看见 > 转角最小 > 距离」三级排序（:57-59），并带穿墙射程与 LOS 判定 | 已覆盖且更彻底 |
| 角度插值 | `util/RotationUtil.kt:37-46` 只按 factor 夹住差值 | `util/RotationUtils.java:167-200` 先把当前角 wrap 再算差值（跨 ±180° 不会绕远路），`slowlyTurnTowards`(:117-145) 还按 yaw/pitch 比例分配每 tick 上限 | 本工程更强 |
| 朝向发包 | `util/RotationUtil.kt:48-57` 直接发 `CPacketPlayer.Rotation` | `util/RotationQueue.java:233-248` 单槽 + 8 级 `Priority` 仲裁，每 tick 只发一个朝向包 | §0.2 ② 已记不适用 |
| 角度归一化 | 4 处语义相同的重载/副本 | 统一走 `Mth.wrapDegrees` | 不需要再引入一套 |
| `getRotationsGucel` | `util/RotationUtil.kt:102-139` | —— | **有真实错误，不能抄**：三个 `yDiff` 分支的互斥条件写反（`yDiff > -0.25` 与 `yDiff < 0.25` 会互相覆盖），`entity.eyeHeight / HitLocation.*.offset` 的除数是 1.0 / 1.5 / 3.5 这种无意义常量 |

**背包操作 → 重试窗口已补齐，另外两点判定不适用**

- 参考的 `util/inventory/Task.kt`(148) 是带 `Future` 的异步任务壳，`Step.kt`(51) 是链内一个带延迟的步骤；本工程的链是**同一 tick 把动作一次性跑完**（`util/inventory/InventoryActionQueue.java:87-88`）。
- **已补齐**（第 12 轮）：执行前的条件重试窗口，并按实测 TPS 换算（`ActionRetryPolicy.compensatedWindow`、`InventoryActionQueue.retryWindowMs` :147-153）。原来"先移除再校验"会把条件未满足的链静默丢弃、owner 永远等不到；现在留在队列里重试，受 5 秒窗口限制。
- **不采纳「链内步进间隔」**：给链加 tick 间隔会直接改变既有四个消费方（`AutoTotem` / `AutoArmor` / `AutoSword` / `Restock`）的换装与补货速度。那是手感变更而不是修 bug，而且没有任何 hack 明确需要"两次点击之间隔 N tick"；在没有实机验证的条件下改这个，风险大于收益。
- **不采纳 `Task` / `Future` 抽象**：本工程的链在客户端线程上同步执行，套一层异步壳只会引入线程安全问题。
- **仍留的缺口（有意不修）**：链执行之后**没有"确认动作是否真的生效"**。要做需要把 `Runnable... actions` 换成能回报结果的类型（牵动四个调用方），而且"点完没生效该重试几次"这种行为只能实机验证。记在这里，不硬做。

**时序 / 暂停 → 暂不移植（没有消费方）**

- 参考 `util/pause/PriorityTimeoutPause.kt`(47) 是「按资源（主手 / 副手）的优先级 + 超时租约」：`requestPause(module, timeout)` 只有当前最高优先级（或更高优先级）的模块拿得到，`isOnTopPriority` 顺手清掉已失效的条目(:23-34)；`HandPause.kt`(17) 就是给主手/副手各一个实例。消费方式见 `module/combat/Surround.kt` 的 `MainHandPause.withPause(Surround, 50L) { ... }`——放方块期间占用主手 50ms。
- 本工程**没有**对应原语（`hack/HackConflictManager.java`(39) 是"同一冲突分组只允许一个 hack 开着"，与"暂停某只手 N 毫秒"是两件事）。
- **今天加它会是零消费方的抽象**：本工程需要独占主手的 hack 目前都**自己做"切槽 → 动作 → 切回"**（例如 `SurroundHack.java:125` 存 `prevSlot`、:189 还原）。把租约接进去必须同时重写这些 hack 的切槽时序，属于会改变行为、又只能实机验证的改动。所以维持与"缓存原语"那条相同的判断标准：**没有消费方就不加抽象**。
- 参考 `process/PauseProcess.kt`(60) 是"让 Baritone 暂停寻路"的 `IBaritoneProcess`。本工程的 Baritone 集成走 `util/BaritoneUtils.java`(196)，已有更粗粒度的等价手段（`stop()` / `clearGoal()` / `cancelEverything()`，见 `BaritoneTreeBotHack.java:66,84`、`BaritoneMineHack.java:78`）；没有发现"必须在寻路中途短暂让路、又不能整段取消"的真实场景。

## 0.2 对参考「优先移植前 5 名」的核对结果

参考规格（`_oe_ref/core-spec.md`）给出了一份优先榜。**动手前逐条核对，结果有一半本工程已经有了**，
记在这里避免重复劳动：

| 参考优先项 | 核对结论 | 依据 |
| --- | --- | --- |
| ① 爆炸伤害链 + 暴露度采样 | **已等价**，不需移植 | 本工程 `DamageUtils` 的 `(impact²+impact) * 0.5 * 7.0 * diameter + 1.0`，其中 `diameter = power * 2`；power=6 时 `0.5*7*12 = 42`，与参考的 `floor((f²+f)*42+1)` **完全一致**。暴露度用原版 `Explosion.getSeenPercent`（自带采样与面判定），不比参考的 40 点采样差 |
| ④ TpsCalculator + 点击计时对齐 TPS | **测 TPS 已有；「消费它」本轮补上（opt-in，默认不变）** | `hud2/ClientMetricsManager` 一直在测（`PacketInputEvent` 驱动 + EMA），**但全局只有一个消费方：TPS 的 HUD 显示**——没有任何节奏逻辑用它。参考项目则统一按 `20 / tickRate` 缩放（`MS delay*(20/tickRate)`、`atkSpeed=1000/(speed*(20/tickRate))`、TimerManager 的 tick 长度）。本轮新增纯类 `util/TpsCompensation.java`（10 个单测：方向、边界、与 tick 互逆、未测量时不补偿），并在 `ClientMetricsManager` 上加 `getCompensatedMillis(ms)` / `getTicksFor(ms)`，让任何 hack 一行就能按实际 TPS 换算延迟。**没有改动任何现有冷却**——hack 要自己改调它才生效 |
| ⑤ DamageReduction 快照 | **已完成** | 见 §0.1；「伤害图增量重算」那半条待核对（本工程可能没有 CombatManager 那种 27³ 全量扫描，若无则**不适用**） |
| ② `PlayerPacketManager` 单槽包合并 + priority 仲裁 | **不适用（本工程已是这个设计）** | `util/RotationQueue.java`：所有队列注册进静态 `ACTIVE`(28)，`RotationDispatcher.onPreMotion()`(224) 每 tick 按 `priority.weight` 比较选**唯一**赢家(236-243)，然后**只调一次** `RotationFaker.setRotationPacket(next)`(248)。每 tick 至多一个朝向包 + 优先级仲裁，正是参考 `PlayerPacketManager` 的做法，**无需移植** |
| ③ 背包 Task/Step/Future 队列 | **部分等价；真缺口 = 「执行后的确认与步进」** | 已有：优先级 + 序号仲裁（`selectNext()` 90-99，同优先级先到先服务）、**容器菜单 id 事务守卫**（提交时记 `menuId` 49，执行前比对 82）、`validator` 前置门（83）、每 tick 只跑一条链（71-88）、每个 owner 只允许一条（45）、换世界清空（101-105）。**缺**：① **执行后的确认 / 重试**——`onUpdate` 是**先移除链(78)再校验(81-84)**，所以 validator 一旦失败或菜单变了，这条链就**被静默丢弃、不会重试**，owner 也拿不到任何回调；② 链内所有动作在**同一 tick 一次性跑完**(86-87)，没有步进间隔；③ 没有 TPS 缩放。参考的「点击序列 + 事务确认 + TPS 缩放步进」补的正是这三点 |

**小结：参考优先榜 5 项里，① 等价、② 不适用、④ 已有、⑤ 本工程已完成。**
③ 的「缺」里第 ① 点（执行后没有确认/重试、失败即静默丢弃）**已修**：

## 0.3 逐模块进度（目标第 3 条，76 个常用 hack）

| hack | 状态 | 说明 |
| --- | --- | --- |
| `WTap` | **已优化（只借 1 个前置判断，刻意不换实现）** | 参考拦下 ATTACK 包并补发 `START_SPRINTING → STOP_SPRINTING → START_SPRINTING` 三个动作包，属**包级**手法；本工程是**模拟按键**（松开前进键再重按），更接近真人输入，而且 `chance` / `releaseDelay` / `rePressDelay` / `selectHits` 四个可调项参考都没有。**所以没有换实现**——换成连发疾跑包会在反作弊里明显得多，这是产品取舍不是技术优劣。只借用了参考的一条前置判断：**饥饿值 < 6 时原版根本不会疾跑**，这时松/按前进键只会白白停一下移动（新增 `canSprint()`）。 |
| 其余常用 hack | 见下 | 第 12 轮逐个核对的结果见本节；未列出的仍按「常用清单」标的 `待办` |

**第 12 轮真正改了代码的（6 个 hack + 1 处共享核心）**

| hack | 状态 | 改了什么 / 为什么 |
| --- | --- | --- |
| `Surround` | **已优化** | ①**修掉一个真 bug**：候选位置原来只排除玩家自己的碰撞箱，被实体占住的格子照放。而 `BlockPlacer.place()` 只要找到可点面就返回 true（1.20.1 服务端却按 `Level#isUnobstructed` 拒绝），于是那一格永远放不上、`anyPlaced` 却为真，hack 一直空挥且不自动关闭。现在按原版自己的接受条件过滤（`!isRemoved() && isPickable()`，显式排除旁观者）——既不会漏掉原版能放的格子，也让剩下的面照常补齐。②四个面的顺序从写死的「东南西北」改成**朝向最近敌人先放**（`SurroundPlanner`，纯类 + 4 个单测）。没有敌人时分数全为 NaN，插入排序退化成不动，行为与旧版逐字节相同。<br>**已发现但未改**：`SupportMode.SKIP` 的实现与 `PLACE` 完全相同（描述却说「跳过不支持的格子」），不是参考带来的问题，改它会重新定义现有设置，留给使用者决定。 |
| `HoleFiller` | **已优化** | 参考 `AutoHoleFill.getHoleInfos()` 会丢掉与任何活实体相交的洞；本地原来没有任何占用概念，于是**自己站着的那个洞永远同时是「最近的」和「放不上去的」**——每 tick 都去点一个不可能的坐标，后面的洞永远轮不到。现在先滤掉被占的洞再取最近（`HoleFillPolicy`，纯类 + 5 个单测）。<br>**订正**：这句话原来写成「同一道守卫 `SelfTrap` / `AutoWeb` / `AutoTrap` / `InstantBunker` 都已经有了」，与代码不符——第 12 轮实际只有 `AutoTrap`（和 `Surround`）有实体占用检查；`SelfTrap` 已在第 13 轮补上。剩下两个**已逐条核对，都不是同一类 bug**：`AutoWeb` 放的是蜘蛛网，而蜘蛛网是 `.noCollission()`（1.20.2 反编译源 `world/level/block/Blocks.java:721`），`CollisionGetter.java:31` 的 `voxelshape.isEmpty() \|\| ...` 会让「碰撞箱为空」的方块**无视格子里的实体**，所以往目标身上放网在原版就是允许的，不需要这条守卫；`InstantBunker`(`InstantBunkerHack.java:107-110`) 确实只看自身碰撞箱，但它的循环不 break、也不依赖返回值重试，被占的格子最多浪费一次交互包，属无害，故不改。 |
| `AutoTrap` | **已优化** | 参考把「先放哪一格」当胜负手（`AutoTrap.kt:137` 原话 *sort offsetList by optimal caging success factor*）。1.20.1 上真正有效的只有脚下圈（dy=0）和头顶圈（dy=2）——dy=1 大多数时候被目标自己的碰撞箱占着，服务端根本不放，而旧代码按「离玩家最近」选，常常正好挑中这一圈白打一次并浪费一个 tick。现在先 dy=0/dy=2、同档按目标**正看着的方向**（它准备逃的方向）先补，再按距离（`AutoTrapPlanner`，纯类 + 5 个单测），并补上实体占用前置条件。 |
| `AutoCity` | **已优化** | 参考 `checkPos` 只收「挖掉之后目标脚边真会空出一个水晶位」的墙。本项目**故意做成排序优先级而不是硬过滤**：参考那条分支要求目标本来就在真坑里（墙下必须有抗爆基底），照搬硬过滤会让本 hack 在泥地/石地的环绕上彻底不动，等于删功能。现在「能换水晶位的墙」排前，一面都没有时退回原来的最近优先（`CityBlockPlanner`，纯类 + 6 个单测）。 |
| `AutoTotem` | **已优化** | 它本来就已经走 `InventoryActionQueue`（带容器 id 校验与确认），比参考的三连 `PICKUP` 干净，**不需要换成参考的实现**。只补了参考的 `Soft` 模式：打开后只有副手空着才装，不会顶掉你特意放在副手的物品。**默认关闭 = 行为与升级前完全一致**；参考的 `Force` 在本工程已有等价物（`Health = 0` 即「always active」）。 |
| `AutoArmor` | **已重构** | 参考的 `AutoArmour.kt` 本身比本工程粗（逐件 `护甲值 + 保护等级`、只补空位），照抄是**降级**；真正可搬的是同包的 `util/combat/DamageReduction.kt`——按**整套四件**去量。1.20.1 的减伤是非线性的（`clamp(护甲 - 伤害/(2+韧性/4), 0.2*护甲, 20)/25`，有 20% 硬底），EPF 上限 20 也是**四件求和**后再截断，所以「逐件打分」在结构上就是错的：第 4 件保护 IV 可能一点用都没有，旧公式照样加分（旧实现另外把韧性当线性常数加、把耐久当约等于 1 点护甲的加权项）。现在改用原版 `CombatRules.getDamageAfterAbsorb` + 原版 `EnchantmentHelper.getDamageProtection`（1.20.1 附魔是数据驱动的，照抄参考的每级 EPF 表会算错）量出「换上这一件后整套少挨多少」，经现有 `DamageProfile` 合成，再选提升最大的那件（`ArmorUpgradePlanner`，纯类 + 10 个单测）。<br>**点击顺序、时机、优先级、validator 与所有设置名/默认值一字未动**，只换了「选哪一件」；耐久降级为**仅在同分时**分先后。 |
| 共享核心：背包重试窗口 | **已优化** | 第 4 轮把「先移除再校验导致静默丢弃」修成了有限重试，但窗口写死 500 ms 墙钟。窗口的语义其实是「给容器同步留几个**服务端 tick**」——服务端掉到 10 TPS 时 500 ms 只等于 5 个 tick，容器可能还没同步完就被判超时，这正是参考统一按 `20/tickRate` 缩放的那件事。现在 `InventoryActionQueue` 按实测 TPS 换算窗口（`ActionRetryPolicy.compensatedWindow`，封顶 5 s，TPS 未测出时不补偿），**`TpsCompensation` 至此有了第一个真实消费方**（此前只有 TPS 的 HUD 显示在用它）。 |

**第 12 轮核对后判定「不适用」的（7 个，均有逐条对照证据）**

| hack | 结论 |
| --- | --- |
| `Step` | 参考的前置条件是本工程的**真子集**：头顶空间、在地面、梯子/可攀爬、水/岩浆、有移动输入、未跳跃、水平碰撞 7 条本工程全都有，且额外有 2 tick 冷却与正确的 `maxUpStep` 保存-恢复（参考把 `stepHeight` 硬写成 1.12.2 的 0.5，1.20.1 默认是 0.6，照搬会破坏玩家台阶高度）。参考把头顶查在 +2.0 是因为它的包表按 2 格台阶走；1.20.1 的 1 格台阶照搬 +2.0 会要求 2 格净空，**在 2 格高隧道里直接走不动**——是回归，故不搬。 |
| `SafeWalk` | 参考这个文件里**根本没有几何算法**（连 `util.math` 都没 import）。Normal 模式只是重定向 `Entity.isSneaking()` 去复用原版边缘保护——本工程等价物是 `shouldClipEdges()`（挂在 `isStayingOnGroundSurface()` 上，1.20.1 里正是 `maybeBackOffFromEdge` 的闸门）；Eagle 模式只查玩家**中心列**下方一格是不是空气，本工程按缩小后的整个脚印 + 一个 `maxUpStep` 深度做 `noCollision`，**严格更保守**且边距可调（0.05~0.25 m）。搬 Eagle 是降级。 |
| `AimAssist` | 参考的 wrap + 钳制 = 已有的 `RotationUtils.limitAngleChange`（连「179→-179 应得 181」这种边界都已有单测）；`repeat(10)`/tick 已被 `RotationSmoothing.smoothWithAcceleration` 取代；方形 FOV 不如现有的圆形瞄准锥（`getAngleToLookVec > fov/2`）；`(14..24).random()` 抖动与 `angleMin` 死区是**反作弊式的随机欠冲**，在这里只会让准星永远到不了选定瞄点（本工程刻意相反：亚度 ±1 微调把准星送进碰撞箱）。选人上参考「每 tick 重挑最近的玩家、零粘滞」，本工程有 `TargetTracker` 的粘滞/切换延迟/切换优势。没有可搬的数学。 |
| `AutoSprint` | `canSprint()` 是参考判定规则的**严格超集**（含参考没有的乘客/下落飞行/水中禁用）。参考的 `keep`（拦下收到的 STOP_SPRINTING 包）是包级 exploit；`InventoryMove` 那条分支是 1.12.2「开 GUI 会强放按键」的补偿，1.20.1 照搬会变成「只要开着任意界面就一直疾跑」，是 bug。键位版移动判定在 1.20.1 **更差**：`KeyboardInput` 算的是净输入，同时按 W+S 时 `forwardImpulse == 0`，原版会停、键位版每 tick 重新开启并因此每 tick 发一对 START/STOP_SPRINTING。 |
| `NoSlowdown` | 本工程有 9 个开关（使用物品 / 盾 / 灵魂沙 / 蜜块 / 黏液块 / 蛛网 / 细雪 / 甜浆果 / 手持物减速），覆盖面已超参考；参考剩下的是 `Packet`、`2B2T`（发 START_SNEAKING 与 RELEASE_USE_ITEM 包）以及直接改 `Blocks.SLIME_BLOCK.setDefaultSlipperiness` 这类 1.12.2 手法。<br>**已知唯一缺口**（第 13 轮修正：原文说"没有对应实现"，不准确）：参考的 `Sneak` 开关（潜行时也全速，`NoSlowDown.kt:30,65-68` 用 `moveForward *= 5f` 抵消减速）在本工程**已有等价能力**，只是位置不同——`hacks/SneakHack.java:92-95` 的 `PACKET` 模式每个移动 tick 发 `PRESS_SHIFT_KEY` + `RELEASE_SHIFT_KEY`，服务端认为你在潜行而客户端不减速（详见 `openeepsilon-verdicts/Sneak.md`）。所以这不是能力缺口，只是没有把这个开关画在 NoSlowdown 那一页。 |
| `Reach` | 参考整个文件只有 12 行、一个 `ReachAdd` 滑条；本工程已有 `ReachPolicy` 纯类 + 实体/方块两段距离 + 仅疾跑时生效 + 液体中禁用。没有可搬的判断。 |
| `Timer` | 参考的 `TimerManager` 是一个**给别的模块用**的 tick 长度覆盖栈（带超时、按插入顺序取最后一个），本工程没有任何模块需要「临时改几个 tick 的 tick 长度」这种动作。在没有消费方之前先造这个栈，与之前判定不做 `util/pause/*` 令牌是同一个理由——**凭空抽象**，故不做。 |

**第 12 轮未能完成的（2 个）——第 13 轮已全部补齐**

| hack | 状态 |
| --- | --- |
| `BowAimbot` | 已完成（第 13 轮）：按 1.20.2 真源重写弹道（0.99 阻力 / 0.05 重力 / 继承射手速度 / 弩 3.15 / 眼高 -0.1），见 `openeepsilon-verdicts/BowAimbot.md`；覆盖表标 **已重构**。第 12 轮"两次在读完文件前中断"的记录保留在案，结论以此处为准。 |
| `Burrow` | 已完成（第 13 轮）：整方块过滤 + 共享放置参数 + `Disable after`，见 `openeepsilon-verdicts/Burrow.md`；覆盖表标 **已优化**。 |

**另一条重要结论（影响后续该怎么做）**：本轮 13 个 hack 里只有 6 个找到了真能搬的东西，7 个判定不适用。原因不是核对得粗，而是**本工程在这几处本来就更完整**——它从初始提交起就带着一整套纯逻辑 `*Policy` / `*Planner` 类（`KeepSprintPolicy`、`CrystalAuraPlanner`、`ScaffoldPlacementPlanner`、`ReachPolicy`、`CombatActionPolicy`、`ProjectileThreatPolicy` … 共 20 余个），而参考的强项集中在两处：**包级 exploit**（1.12.2 的 NCP/AAC/Hypixel 绕过，在 1.20.1 上要么无效要么踢人）与 **GL11 立即模式渲染**（1.20.1 已无此渲染管线）。所以后续继续按「每个 hack 都必须改出点什么」推进是错的，正确做法是逐条核对、只搬真能搬的，并把「不适用」连同证据记下来。



| 修复 | 内容 |
| --- | --- |
| 新增 `util/inventory/ActionRetryPolicy.java` | 纯三态判定 `EXECUTE / RETRY / ABORT` + `isExpired`，8 个单测；**不改公开 API** |
| `InventoryActionQueue` | 链记录带上提交时间；`selectNext(now)` 改为用策略判定——条件没通过就**留在队列**里等（窗口 `DEFAULT_RETRY_WINDOW_MS = 500ms`），超时才丢；**菜单 id 变了则立即放弃**（动作是针对某个容器编排的，套到别的容器会点错格子，不能靠重试救）；RETRY 的链**只跳过不参与选择**，避免一条在等的链挡住后面的链（原来没有这个问题，因为原来是直接丢弃） |

③ 仍未做的两点：**链内动作的步进间隔**、**TPS 缩放**。这两点属于「节奏」而非「正确性」，
优先级低于把 per-hack 的旧实现过一遍。


**另外一条被规格点名的「最大差距」经核对并不成立**：规格推荐的 `util/pause/*`（优先级抢占 +
超时暂停令牌）对应本工程 `hack/HackConflictManager.java`(39)，但**两者不是一回事**——
`HackConflictManager` 管的是「互斥 hack 互相关闭」，而参考的 pause 是「作用域内的临时抑制、
带优先级与 50ms 超时」。所以差距是真的，但**不能直接替换**；而且在没有明确消费方（例如
「放方块的瞬间抑制攻击」）之前先加一个没人用的令牌原语，属于凭空造抽象，**暂不做**。


状态含义：`待办` 未动 / `已优化` 改了实现细节 / `已重构` 换了算法或架构 / `不适用` OpenEpsilon 无对应模块或差异无意义 / `共享核心` 主要逻辑已移入共享层。

## 覆盖统计

- hack 总数：**210**
- 单文件行数合计：**33196**
- 标记为「常用」的：**76**

## 常用 hack（优先覆盖）

| hack | 分类 | 行数 | 状态 |
| --- | --- | --- | --- |
| `AutoFarm` | BLOCKS | 379 | 不适用（§0.3） |
| `AutoTool` | BLOCKS | 289 | 不适用（§0.3） |
| `Excavator` | BLOCKS | 549 | 不适用（§0.3） |
| `FastBreak` | BLOCKS | 109 | 不适用（§0.3） |
| `Nuker` | BLOCKS | 165 | 不适用（§0.3） |
| `ScaffoldWalk` | BLOCKS | 332 | 不适用（§0.3） |
| `VeinMiner` | BLOCKS | 246 | 不适用（§0.3） |
| `AimAssist` | COMBAT | 275 | 不适用（§0.3） |
| `AnchorAura` | COMBAT | 547 | 已优化（§0.3） |
| `AntiBot` | COMBAT | 177 | 已重构（§0.3） |
| `AutoArmor` | COMBAT | 262 | 已重构（§0.3） |
| `AutoCity` | COMBAT | 159 | 已优化（§0.3） |
| `AutoSword` | COMBAT | 207 | 已优化（§0.3） |
| `AutoTotem` | COMBAT | 156 | 已优化（§0.3） |
| `AutoTrap` | COMBAT | 191 | 已优化（§0.3） |
| `BowAimbot` | COMBAT | 261 | 已重构（§0.3） |
| `Burrow` | COMBAT | 259 | 已优化（§0.3） |
| `ClickAura` | COMBAT | 139 | 不适用（§0.3） |
| `Criticals` | COMBAT | 228 | 已重构（§0.3） |
| `CrystalAura` | COMBAT | 443 | 已重构（§0.3） |
| `HoleFiller` | COMBAT | 152 | 已优化（§0.3） |
| `KeepSprint` | COMBAT | 30 | 已重构（§0.3） |
| `Killaura` | COMBAT | 1259 | 已优化（§0.3） |
| `MultiAura` | COMBAT | 1162 | 已优化（§0.3） |
| `SelfTrap` | COMBAT | 176 | 已优化（§0.3） |
| `SuperKnockback` | COMBAT | 202 | 已优化（§0.3） |
| `Surround` | COMBAT | 220 | 已优化（§0.3） |
| `TriggerBot` | COMBAT | 233 | 已优化（§0.3） |
| `WTap` | COMBAT | 126 | 已优化（见 §0.3） |
| `AutoEat` | ITEMS | 351 | 不适用（§0.3） |
| `AutoSteal` | ITEMS | 108 | 不适用（§0.3） |
| `FastUse` | ITEMS | 105 | 已优化（§0.3） |
| `Restock` | ITEMS | 180 | 不适用（§0.3） |
| `AutoSprint` | MOVEMENT | 121 | 不适用（§0.3） |
| `Blink` | MOVEMENT | 198 | 不适用（§0.3） |
| `BunnyHop` | MOVEMENT | 87 | 不适用（§0.3） |
| `ElytraFly` | MOVEMENT | 155 | 已优化（§0.3） |
| `FakeLag` | MOVEMENT | 117 | 不适用（§0.3） |
| `FastLadder` | MOVEMENT | 53 | 不适用（§0.3） |
| `Flight` | MOVEMENT | 189 | 不适用（§0.3） |
| `Glide` | MOVEMENT | 120 | 不适用（§0.3） |
| `HighJump` | MOVEMENT | 36 | 已优化（§0.3） |
| `InvWalk` | MOVEMENT | 129 | 不适用（§0.3） |
| `Jesus` | MOVEMENT | 180 | 已优化（§0.3） |
| `NoClip` | MOVEMENT | 95 | 不适用（§0.3） |
| `NoFall` | MOVEMENT | 186 | 不适用（§0.3） |
| `NoSlowdown` | MOVEMENT | 143 | 不适用 + 1 处缺口（§0.3） |
| `NoVelocity` | MOVEMENT | 256 | 不适用（§0.3） |
| `PacketFly` | MOVEMENT | 158 | 不适用（§0.3） |
| `Parkour` | MOVEMENT | 79 | 不适用（§0.3） |
| `ReverseStep` | MOVEMENT | 141 | 不适用（§0.3） |
| `SafeWalk` | MOVEMENT | 109 | 不适用（§0.3） |
| `Sneak` | MOVEMENT | 150 | 不适用（§0.3） |
| `SpeedHack` | MOVEMENT | 210 | 不适用（§0.3） |
| `Spider` | MOVEMENT | 49 | 不适用（§0.3） |
| `Step` | MOVEMENT | 161 | 不适用（§0.3） |
| `Reach` | OTHER | 67 | 不适用（§0.3） |
| `Timer` | OTHER | 38 | 不适用（§0.3） |
| `BaseFinder` | RENDER | 251 | 已优化（§0.3） |
| `CaveFinder` | RENDER | 246 | 不适用（§0.3） |
| `ChestEsp` | RENDER | 275 | 不适用（§0.3） |
| `EntityCulling` | RENDER | 99 | 不适用（§0.3） |
| `Freecam` | RENDER | 183 | 不适用（§0.3） |
| `Fullbright` | RENDER | 197 | 已优化（§0.3） |
| `HoleEsp` | RENDER | 193 | 已优化（§0.3） |
| `ItemEsp` | RENDER | 124 | 不适用（§0.3） |
| `LightOverlay` | RENDER | 117 | 不适用（§0.3） |
| `LogoutSpots` | RENDER | 199 | 不适用（§0.3） |
| `MobEsp` | RENDER | 177 | 不适用（§0.3） |
| `NameTags` | RENDER | 227 | 不适用（§0.3） |
| `NewChunks` | RENDER | 259 | 已优化（§0.3） |
| `PlayerEsp` | RENDER | 315 | 不适用（§0.3） |
| `Radar` | RENDER | 120 | 不适用（§0.3） |
| `Search` | RENDER | 256 | 不适用（§0.3） |
| `Trajectories` | RENDER | 287 | 已重构（§0.3） |
| `XRay` | RENDER | 244 | 不适用（§0.3） |

## 全部 hack

### ?（2）

| hack | 行数 | 状态 |
| --- | --- | --- |
| `ClickGui` | 96 | 不适用（§0.3） |
| `Navigator` | 44 | 不适用（§0.3） |

### BLOCKS（31）

| hack | 行数 | 状态 |
| --- | --- | --- |
| `AirPlace` | 131 | 不适用（§0.3） |
| `AntiCactus` | 43 | 不适用（§0.3） |
| `AutoBuild` | 315 | 不适用（§0.3） |
| `AutoFarm` | 379 | 不适用（§0.3） |
| `AutoMine` | 115 | 不适用（§0.3） |
| `AutoSign` | 43 | 不适用（§0.3） |
| `AutoTool` | 289 | 不适用（§0.3） |
| `BaritoneClearArea` | 125 | 不适用（§0.3） |
| `BaritoneMine` | 160 | 不适用（§0.3） |
| `BaritoneTreeBot` | 300 | 不适用（§0.3） |
| `BonemealAura` | 281 | 不适用（§0.3） |
| `BuildRandom` | 206 | 已优化（§0.3） |
| `Excavator` | 549 | 不适用（§0.3） |
| `FastBreak` | 109 | 不适用（§0.3） |
| `FastPlace` | 41 | 不适用（§0.3） |
| `HandNoClip` | 49 | 不适用（§0.3） |
| `InstaBuild` | 239 | 不适用（§0.3） |
| `InstantBunker` | 118 | 已优化（§0.3） |
| `Kaboom` | 109 | 不适用（§0.3） |
| `Liquids` | 41 | 不适用（§0.3） |
| `Nuker` | 165 | 不适用（§0.3） |
| `NukerLegit` | 205 | 不适用（§0.3） |
| `PerimeterDigger` | 358 | 不适用（§0.3） |
| `ScaffoldWalk` | 332 | 不适用（§0.3） |
| `SpeedMine` | 85 | 已优化（§0.3） |
| `SpeedNuker` | 110 | 不适用（§0.3） |
| `TemplateTool` | 190 | 不适用（§0.3） |
| `Tillaura` | 171 | 不适用（§0.3） |
| `TreeBot` | 477 | 不适用（§0.3） |
| `Tunneller` | 925 | 已优化（§0.3） |
| `VeinMiner` | 246 | 不适用（§0.3） |

### CHAT（7）

| hack | 行数 | 状态 |
| --- | --- | --- |
| `AntiSpam` | 150 | 不适用（§0.3） |
| `AutoComplete` | 161 | 已优化（§0.3） |
| `ChatTranslator` | 150 | 已优化（§0.3） |
| `FancyChat` | 68 | 不适用（§0.3） |
| `ForceOp` | 305 | 已优化（§0.3） |
| `InfiniChat` | 22 | 不适用（§0.3） |
| `MassTpa` | 170 | 不适用（§0.3） |

### COMBAT（38）

| hack | 行数 | 状态 |
| --- | --- | --- |
| `AimAssist` | 275 | 不适用（§0.3） |
| `AnchorAura` | 547 | 已优化（§0.3） |
| `AntiBot` | 177 | 已重构（§0.3） |
| `ArrowDmg` | 88 | 不适用（§0.3） |
| `AutoArmor` | 262 | 已重构（§0.3） |
| `AutoCity` | 159 | 已优化（§0.3） |
| `AutoLeave` | 138 | 已优化（§0.3） |
| `AutoPotion` | 134 | 已优化（§0.3） |
| `AutoRespawn` | 55 | 已优化（§0.3） |
| `AutoSoup` | 189 | 已优化（§0.3） |
| `AutoSword` | 207 | 已优化（§0.3） |
| `AutoTotem` | 156 | 已优化（§0.3） |
| `AutoTrap` | 191 | 已优化（§0.3） |
| `AutoWeb` | 157 | 已优化（§0.3） |
| `BowAimbot` | 261 | 已重构（§0.3） |
| `Burrow` | 259 | 已优化（§0.3） |
| `ClickAura` | 139 | 不适用（§0.3） |
| `Criticals` | 228 | 已重构（§0.3） |
| `CrystalAura` | 443 | 已重构（§0.3） |
| `DelayRemover` | 61 | 已优化（§0.3） |
| `FightBot` | 303 | 已优化（§0.3） |
| `Hitboxes` | 36 | 不适用（§0.3） |
| `HoleFiller` | 152 | 已优化（§0.3） |
| `KeepSprint` | 30 | 已重构（§0.3） |
| `Killaura` | 1259 | 已优化（§0.3） |
| `KillauraLegit` | 349 | 已优化（§0.3） |
| `MultiAura` | 1162 | 已优化（§0.3） |
| `NoMissCooldown` | 67 | 不适用（§0.3） |
| `ProjectilePuncher` | 136 | 不适用（§0.3） |
| `Protect` | 386 | 已优化（§0.3） |
| `RightClicker` | 124 | 已优化（§0.3） |
| `SelfTrap` | 176 | 已优化（§0.3） |
| `SuperKnockback` | 202 | 已优化（§0.3） |
| `Surround` | 220 | 已优化（§0.3） |
| `TargetStrafe` | 144 | 已优化（§0.3） |
| `TpAura` | 192 | 不适用（§0.3） |
| `TriggerBot` | 233 | 已优化（§0.3） |
| `WTap` | 126 | 已优化（见 §0.3） |

### FUN（12）

| hack | 行数 | 状态 |
| --- | --- | --- |
| `AntiAim` | 134 | 已优化（§0.3） |
| `DankBobbing` | 65 | 已优化（§0.3） |
| `Derp` | 53 | 不适用（§0.3） |
| `HeadRoll` | 50 | 不适用（§0.3） |
| `Lsd` | 49 | 不适用（§0.3） |
| `MileyCyrus` | 60 | 不适用（§0.3） |
| `Notebot` | 217 | 已优化（§0.3） |
| `RainbowUi` | 24 | 不适用（§0.3） |
| `SkinDerp` | 53 | 已优化（§0.3） |
| `Tired` | 45 | 不适用（§0.3） |
| `Twerk` | 45 | 已优化（§0.3） |
| `Vomit` | 48 | 已优化（§0.3） |

### ITEMS（10）

| hack | 行数 | 状态 |
| --- | --- | --- |
| `AutoDrop` | 90 | 不适用（§0.3） |
| `AutoEat` | 351 | 不适用（§0.3） |
| `AutoSteal` | 108 | 不适用（§0.3） |
| `AutoSwitch` | 47 | 不适用（§0.3） |
| `CrashChest` | 62 | 不适用（§0.3） |
| `FastUse` | 105 | 已优化（§0.3） |
| `ItemGenerator` | 87 | 已优化（§0.3） |
| `KillPotion` | 119 | 不适用（§0.3） |
| `Restock` | 180 | 不适用（§0.3） |
| `TrollPotion` | 117 | 不适用（§0.3） |

### MOVEMENT（45）

| hack | 行数 | 状态 |
| --- | --- | --- |
| `AirJump` | 101 | 不适用（§0.3） |
| `Anchor` | 68 | 已优化（§0.3） |
| `AntiEntityPush` | 45 | 不适用（§0.3） |
| `AntiHunger` | 55 | 不适用（§0.3） |
| `AntiVoid` | 54 | 已优化（§0.3） |
| `AntiWaterPush` | 86 | 不适用（§0.3） |
| `AutoSprint` | 121 | 不适用（§0.3） |
| `AutoSwim` | 51 | 不适用（§0.3） |
| `AutoWalk` | 43 | 不适用（§0.3） |
| `BaritoneWalk` | 90 | 不适用（§0.3） |
| `Blink` | 198 | 不适用（§0.3） |
| `BoatFly` | 90 | 不适用（§0.3） |
| `BunnyHop` | 87 | 不适用（§0.3） |
| `CreativeFlight` | 131 | 不适用（§0.3） |
| `Dolphin` | 49 | 不适用（§0.3） |
| `ElytraFly` | 155 | 已优化（§0.3） |
| `ExtraElytra` | 147 | 不适用（§0.3） |
| `FakeLag` | 117 | 不适用（§0.3） |
| `FastLadder` | 53 | 不适用（§0.3） |
| `Fish` | 49 | 不适用（§0.3） |
| `Flight` | 189 | 不适用（§0.3） |
| `Follow` | 278 | 已优化（§0.3） |
| `Glide` | 120 | 不适用（§0.3） |
| `HighJump` | 36 | 已优化（§0.3） |
| `InvWalk` | 129 | 不适用（§0.3） |
| `Jesus` | 180 | 已优化（§0.3） |
| `Jetpack` | 45 | 不适用（§0.3） |
| `NoClip` | 95 | 不适用（§0.3） |
| `NoFall` | 186 | 不适用（§0.3） |
| `NoJumpDelay` | 79 | 已优化（§0.3） |
| `NoLevitation` | 25 | 不适用（§0.3） |
| `NoRotate` | 68 | 不适用（§0.3） |
| `NoSlowdown` | 143 | 不适用（§0.3） |
| `NoVelocity` | 256 | 不适用（§0.3） |
| `NoWeb` | 40 | 不适用（§0.3） |
| `PacketFly` | 158 | 不适用（§0.3） |
| `Parkour` | 79 | 不适用（§0.3） |
| `ReverseStep` | 141 | 不适用（§0.3） |
| `SafeWalk` | 109 | 不适用（§0.3） |
| `Sneak` | 150 | 不适用（§0.3） |
| `SnowShoe` | 25 | 不适用（§0.3） |
| `SpeedHack` | 210 | 不适用（§0.3） |
| `Spider` | 49 | 不适用（§0.3） |
| `Step` | 161 | 不适用（§0.3） |
| `VehicleBoost` | 74 | 不适用（§0.3） |

### OTHER（15）

| hack | 行数 | 状态 |
| --- | --- | --- |
| `AntiAfk` | 259 | 不适用（§0.3） |
| `AutoFish` | 274 | 不适用（§0.3） |
| `AutoLibrarian` | 528 | 已优化（§0.3） |
| `AutoReconnect` | 38 | 不适用（§0.3） |
| `FeedAura` | 210 | 不适用（§0.3） |
| `MusicPlayer` | 27 | 不适用（§0.3） |
| `PacketCanceller` | 100 | 已优化（§0.3） |
| `PacketLogger` | 91 | 不适用（§0.3） |
| `Panic` | 47 | 不适用（§0.3） |
| `PortalGui` | 24 | 不适用（§0.3） |
| `PotionSaver` | 53 | 不适用（§0.3） |
| `Reach` | 67 | 不适用（§0.3） |
| `Throw` | 71 | 不适用（§0.3） |
| `Timer` | 38 | 不适用（§0.3） |
| `TooManyHax` | 159 | 不适用（§0.3） |

### RENDER（50）

| hack | 行数 | 状态 |
| --- | --- | --- |
| `AntiBlind` | 28 | 不适用（§0.3） |
| `AntiWobble` | 25 | 不适用（§0.3） |
| `BarrierEsp` | 24 | 不适用（§0.3） |
| `BaseFinder` | 251 | 已优化（§0.3） |
| `BossStack` | 74 | 不适用（§0.3） |
| `Breadcrumbs` | 149 | 不适用（§0.3） |
| `CameraDistance` | 35 | 不适用（§0.3） |
| `CameraNoClip` | 24 | 不适用（§0.3） |
| `CaveFinder` | 246 | 不适用（§0.3） |
| `ChestEsp` | 275 | 不适用（§0.3） |
| `CityEsp` | 150 | 不适用（§0.3） |
| `EntityCulling` | 99 | 不适用（§0.3） |
| `Freecam` | 183 | 不适用（§0.3） |
| `Fullbright` | 197 | 已优化（§0.3） |
| `HealthTags` | 54 | 不适用（§0.3） |
| `HoleEsp` | 193 | 已优化（§0.3） |
| `ItemEsp` | 124 | 不适用（§0.3） |
| `LightOverlay` | 117 | 不适用（§0.3） |
| `LogoutSpots` | 199 | 不适用（§0.3） |
| `MobEsp` | 177 | 不适用（§0.3） |
| `MobSpawnEsp` | 193 | 不适用（§0.3） |
| `NameProtect` | 56 | 已优化（§0.3） |
| `NameTags` | 227 | 不适用（§0.3） |
| `NewChunks` | 259 | 已优化（§0.3） |
| `NoBackground` | 46 | 不适用（§0.3） |
| `NoFireOverlay` | 36 | 不适用（§0.3） |
| `NoFog` | 24 | 不适用（§0.3） |
| `NoHurtcam` | 24 | 不适用（§0.3） |
| `NoOverlay` | 26 | 不适用（§0.3） |
| `NoPumpkin` | 24 | 不适用（§0.3） |
| `NoShieldOverlay` | 48 | 不适用（§0.3） |
| `NoVignette` | 24 | 不适用（§0.3） |
| `NoWeather` | 69 | 不适用（§0.3） |
| `OpenWaterEsp` | 71 | 不适用（§0.3） |
| `Overlay` | 67 | 不适用（§0.3） |
| `PlayerEsp` | 315 | 不适用（§0.3） |
| `PlayerHalo` | 47 | 不适用（§0.3） |
| `PopChams` | 181 | 不适用（§0.3） |
| `PortalEsp` | 223 | 已优化（§0.3） |
| `ProphuntEsp` | 73 | 不适用（§0.3） |
| `Radar` | 120 | 不适用（§0.3） |
| `RemoteView` | 180 | 不适用（§0.3） |
| `RotationSnap` | 106 | 不适用（§0.3） |
| `Search` | 256 | 不适用（§0.3） |
| `SeedOreEsp` | 541 | 不适用（§0.3） |
| `SeedStructureEsp` | 265 | 不适用（§0.3） |
| `TargetShader` | 79 | 不适用（§0.3） |
| `Trajectories` | 287 | 已重构（§0.3） |
| `TrueSight` | 56 | 不适用（§0.3） |
| `XRay` | 244 | 不适用（§0.3） |

