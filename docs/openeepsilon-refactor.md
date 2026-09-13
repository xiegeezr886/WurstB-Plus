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
| 旋转 | 待办 | 参考 `util/math/RotationUtils.kt` + `RotationUtil.kt` 对比本工程 `util/RotationQueue.java`(280) / `RotationSmoothing.java`(161) |
| 背包操作 | 待办 | 参考 `util/inventory/`（`Task`/`Step`/`operation/*`）对比本工程 `InventoryActionQueue.java`(111) / `InventoryUtils.java`(261) |
| 时序 / 暂停 | 待办 | 参考 `util/pause/*`（`HandPause`/`PriorityTimeoutPause`）对比本工程 `HackConflictManager.java`(39) |
| 缓存原语 | 待办 | 参考 `util/delegate/*`（`CachedValue`/`FrameValue`/`ComputeFlag`/`AsyncCachedValue`） |
| 目标选择 | **共享核心已加，尚未接入（行为未变）** | 新增 `util/TargetScore.java`（纯类，13 个单测）：按参考 §7 实现五因子加权打分——`distF`(8 格饱和) + `healthF`(20 血饱和) + `armorF`(20 点爆炸实收伤害) + `holeF`(四水平邻向不抗爆占比) + `crosshairF`(`|相对偏航|` 30° 饱和)，默认权重各 0.5，总分降序。**注意：目前没有任何 hack 调用它**，所以还没有换来行为改善。**接入点已经定好，而且有一个坑必须避开**：

- ❌ **不要给 `CombatTargetUtils.Priority` 加一个「在比较器里现算 score」的分支**。该类的 `getComparator()` 是在比较器内部调用 `getScore()` 的（`CombatTargetUtils.java:179-180`），排序期间会被调用 **O(n log n)** 次；现有四个键（distance / angle / health / hurtTime）都廉价所以没问题，但 `armorF` 要做护甲结算、`holeF` 要查四个相邻方块，放进比较器会成倍放大开销。
- ✅ 正确做法：在**取候选列表时**给每个候选算一次 `TargetScore.Inputs`，再用 `TargetScore.rank(candidates, weights)`（已提供，5 个单测覆盖排序/换位/同分稳定/空表/权重跟随）拿排好的下标。
- 顺带一提，`armorF` 可以用第 1 轮加的 `DamageUtils.profileOf()` 缓存来算"20 点标称伤害实收多少"，**不需要**跑暴露度光线投射，所以五个因子全都可以廉价地每人算一次。
- ✅ **入口已经实现**：`CombatTargetUtils.getListByScore(range, fov, aimPoint, filters, checkLOS, maxCount, weights)`——先按距离拿候选，再给每个候选算一次 `Inputs`，最后 `TargetScore.rank` 排序。**没有改动 `getList` 与任何默认行为**；要用打分排序的 hack 自行改调这个入口。
- ⚠️ **一处刻意的近似**：`holeF` 的"暴露面"参考判的是邻向**抗不抗爆**，本工程用"空气或碰撞箱为空"代替（`countExposedSides`），因为 1.20.1 取爆炸抗性要构造 `Explosion` 上下文。所以洞因子在"水/树叶"这类不抗爆但有碰撞箱的方块上会算得比参考保守。
- ✅ **已经接线（opt-in，默认关闭）**：`CombatTargetUtils.Priority` 新增 `SCORE("Score")`，`getList` 见到它就分流到 `getListByScore`；`getScore`/`getComparator` 里为它留了一个**退化为距离**的分支并注明"正常不会走到这里"，免得有人误在比较器里用打分。所以凡是暴露了 `Priority` 设置的 combat hack（如 ClickAura）现在都能选 Score——**但默认值仍是各自原来的（ClickAura 是 ANGLE），升级不会改变任何人的选人行为**，用户主动在设置里选 Score 才生效。
- ⚠️ **仍需实机调手感**：打分排序的实际观感（是否真的比按角度/距离更"聪明"）我无法验证；权重是参考的 0.5 全等值，未必适合本工程的 hack 组合。

| 移动数学 | 待办 | 参考 `MovementUtils.kt` 对比本工程 `MovementPlanner.java`(87)；注意 1.13+ 游泳/1.14+ 跳跃差异，参数不能照抄 |

**受益的常用 hack**（都走 `DamageUtils`，因此共享这一优化）：`CrystalAura`、`AnchorAura`、
`AutoTotem`、`Protect`、`AutoTrap`、`Surround`、`AutoCity`、`HoleFiller`、`SelfTrap`、`AutoCev`。

## 0.2 对参考「优先移植前 5 名」的核对结果

参考规格（`_oe_ref/core-spec.md`）给出了一份优先榜。**动手前逐条核对，结果有一半本工程已经有了**，
记在这里避免重复劳动：

| 参考优先项 | 核对结论 | 依据 |
| --- | --- | --- |
| ① 爆炸伤害链 + 暴露度采样 | **已等价**，不需移植 | 本工程 `DamageUtils` 的 `(impact²+impact) * 0.5 * 7.0 * diameter + 1.0`，其中 `diameter = power * 2`；power=6 时 `0.5*7*12 = 42`，与参考的 `floor((f²+f)*42+1)` **完全一致**。暴露度用原版 `Explosion.getSeenPercent`（自带采样与面判定），不比参考的 40 点采样差 |
| ④ TpsCalculator + 点击计时对齐 TPS | **已有等价实现** | `hud2/ClientMetricsManager`：由 `PacketInputEvent`（服务端 tick 同步包）驱动，`SMOOTHING = 0.2` 的 EMA、`DEFAULT_TPS = 20`、夹在 `[0,20]`、换世界重置。参考是 20 槽环形缓冲 + 剔除 0 值求均值，两者目的相同，EMA 更省内存 |
| ⑤ DamageReduction 快照 | **已完成** | 见 §0.1；「伤害图增量重算」那半条待核对（本工程可能没有 CombatManager 那种 27³ 全量扫描，若无则**不适用**） |
| ② `PlayerPacketManager` 单槽包合并 + priority 仲裁 | **不适用（本工程已是这个设计）** | `util/RotationQueue.java`：所有队列注册进静态 `ACTIVE`(28)，`RotationDispatcher.onPreMotion()`(224) 每 tick 按 `priority.weight` 比较选**唯一**赢家(236-243)，然后**只调一次** `RotationFaker.setRotationPacket(next)`(248)。每 tick 至多一个朝向包 + 优先级仲裁，正是参考 `PlayerPacketManager` 的做法，**无需移植** |
| ③ 背包 Task/Step/Future 队列 | **部分等价；真缺口 = 「执行后的确认与步进」** | 已有：优先级 + 序号仲裁（`selectNext()` 90-99，同优先级先到先服务）、**容器菜单 id 事务守卫**（提交时记 `menuId` 49，执行前比对 82）、`validator` 前置门（83）、每 tick 只跑一条链（71-88）、每个 owner 只允许一条（45）、换世界清空（101-105）。**缺**：① **执行后的确认 / 重试**——`onUpdate` 是**先移除链(78)再校验(81-84)**，所以 validator 一旦失败或菜单变了，这条链就**被静默丢弃、不会重试**，owner 也拿不到任何回调；② 链内所有动作在**同一 tick 一次性跑完**(86-87)，没有步进间隔；③ 没有 TPS 缩放。参考的「点击序列 + 事务确认 + TPS 缩放步进」补的正是这三点 |

**小结：参考优先榜 5 项里，① 等价、② 不适用、④ 已有、⑤ 本工程已完成。**
③ 的「缺」里第 ① 点（执行后没有确认/重试、失败即静默丢弃）**已修**：

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
| `AutoFarm` | BLOCKS | 379 | 待办 |
| `AutoTool` | BLOCKS | 289 | 待办 |
| `Excavator` | BLOCKS | 549 | 待办 |
| `FastBreak` | BLOCKS | 109 | 待办 |
| `Nuker` | BLOCKS | 165 | 待办 |
| `ScaffoldWalk` | BLOCKS | 332 | 待办 |
| `VeinMiner` | BLOCKS | 246 | 待办 |
| `AimAssist` | COMBAT | 275 | 待办 |
| `AnchorAura` | COMBAT | 547 | 待办 |
| `AntiBot` | COMBAT | 177 | 待办 |
| `AutoArmor` | COMBAT | 262 | 待办 |
| `AutoCity` | COMBAT | 159 | 待办 |
| `AutoSword` | COMBAT | 207 | 待办 |
| `AutoTotem` | COMBAT | 156 | 待办 |
| `AutoTrap` | COMBAT | 191 | 待办 |
| `BowAimbot` | COMBAT | 261 | 待办 |
| `Burrow` | COMBAT | 259 | 待办 |
| `ClickAura` | COMBAT | 139 | 待办 |
| `Criticals` | COMBAT | 228 | 待办 |
| `CrystalAura` | COMBAT | 443 | 待办 |
| `HoleFiller` | COMBAT | 152 | 待办 |
| `KeepSprint` | COMBAT | 30 | 待办 |
| `Killaura` | COMBAT | 1259 | 待办 |
| `MultiAura` | COMBAT | 1162 | 待办 |
| `SelfTrap` | COMBAT | 176 | 待办 |
| `SuperKnockback` | COMBAT | 202 | 待办 |
| `Surround` | COMBAT | 220 | 待办 |
| `TriggerBot` | COMBAT | 233 | 待办 |
| `WTap` | COMBAT | 126 | 待办 |
| `AutoEat` | ITEMS | 351 | 待办 |
| `AutoSteal` | ITEMS | 108 | 待办 |
| `FastUse` | ITEMS | 105 | 待办 |
| `Restock` | ITEMS | 180 | 待办 |
| `AutoSprint` | MOVEMENT | 121 | 待办 |
| `Blink` | MOVEMENT | 198 | 待办 |
| `BunnyHop` | MOVEMENT | 87 | 待办 |
| `ElytraFly` | MOVEMENT | 155 | 待办 |
| `FakeLag` | MOVEMENT | 117 | 待办 |
| `FastLadder` | MOVEMENT | 53 | 待办 |
| `Flight` | MOVEMENT | 189 | 待办 |
| `Glide` | MOVEMENT | 120 | 待办 |
| `HighJump` | MOVEMENT | 36 | 待办 |
| `InvWalk` | MOVEMENT | 129 | 待办 |
| `Jesus` | MOVEMENT | 180 | 待办 |
| `NoClip` | MOVEMENT | 95 | 待办 |
| `NoFall` | MOVEMENT | 186 | 待办 |
| `NoSlowdown` | MOVEMENT | 143 | 待办 |
| `NoVelocity` | MOVEMENT | 256 | 待办 |
| `PacketFly` | MOVEMENT | 158 | 待办 |
| `Parkour` | MOVEMENT | 79 | 待办 |
| `ReverseStep` | MOVEMENT | 141 | 待办 |
| `SafeWalk` | MOVEMENT | 109 | 待办 |
| `Sneak` | MOVEMENT | 150 | 待办 |
| `SpeedHack` | MOVEMENT | 210 | 待办 |
| `Spider` | MOVEMENT | 49 | 待办 |
| `Step` | MOVEMENT | 161 | 待办 |
| `Reach` | OTHER | 67 | 待办 |
| `Timer` | OTHER | 38 | 待办 |
| `BaseFinder` | RENDER | 251 | 待办 |
| `CaveFinder` | RENDER | 246 | 待办 |
| `ChestEsp` | RENDER | 275 | 待办 |
| `EntityCulling` | RENDER | 99 | 待办 |
| `Freecam` | RENDER | 183 | 待办 |
| `Fullbright` | RENDER | 197 | 待办 |
| `HoleEsp` | RENDER | 193 | 待办 |
| `ItemEsp` | RENDER | 124 | 待办 |
| `LightOverlay` | RENDER | 117 | 待办 |
| `LogoutSpots` | RENDER | 199 | 待办 |
| `MobEsp` | RENDER | 177 | 待办 |
| `NameTags` | RENDER | 227 | 待办 |
| `NewChunks` | RENDER | 259 | 待办 |
| `PlayerEsp` | RENDER | 315 | 待办 |
| `Radar` | RENDER | 120 | 待办 |
| `Search` | RENDER | 256 | 待办 |
| `Trajectories` | RENDER | 287 | 待办 |
| `XRay` | RENDER | 244 | 待办 |

## 全部 hack

### ?（2）

| hack | 行数 | 状态 |
| --- | --- | --- |
| `ClickGui` | 96 | 待办 |
| `Navigator` | 44 | 待办 |

### BLOCKS（31）

| hack | 行数 | 状态 |
| --- | --- | --- |
| `AirPlace` | 131 | 待办 |
| `AntiCactus` | 43 | 待办 |
| `AutoBuild` | 315 | 待办 |
| `AutoFarm` | 379 | 待办 |
| `AutoMine` | 115 | 待办 |
| `AutoSign` | 43 | 待办 |
| `AutoTool` | 289 | 待办 |
| `BaritoneClearArea` | 125 | 待办 |
| `BaritoneMine` | 160 | 待办 |
| `BaritoneTreeBot` | 300 | 待办 |
| `BonemealAura` | 281 | 待办 |
| `BuildRandom` | 206 | 待办 |
| `Excavator` | 549 | 待办 |
| `FastBreak` | 109 | 待办 |
| `FastPlace` | 41 | 待办 |
| `HandNoClip` | 49 | 待办 |
| `InstaBuild` | 239 | 待办 |
| `InstantBunker` | 118 | 待办 |
| `Kaboom` | 109 | 待办 |
| `Liquids` | 41 | 待办 |
| `Nuker` | 165 | 待办 |
| `NukerLegit` | 205 | 待办 |
| `PerimeterDigger` | 358 | 待办 |
| `ScaffoldWalk` | 332 | 待办 |
| `SpeedMine` | 85 | 待办 |
| `SpeedNuker` | 110 | 待办 |
| `TemplateTool` | 190 | 待办 |
| `Tillaura` | 171 | 待办 |
| `TreeBot` | 477 | 待办 |
| `Tunneller` | 925 | 待办 |
| `VeinMiner` | 246 | 待办 |

### CHAT（7）

| hack | 行数 | 状态 |
| --- | --- | --- |
| `AntiSpam` | 150 | 待办 |
| `AutoComplete` | 161 | 待办 |
| `ChatTranslator` | 150 | 待办 |
| `FancyChat` | 68 | 待办 |
| `ForceOp` | 305 | 待办 |
| `InfiniChat` | 22 | 待办 |
| `MassTpa` | 170 | 待办 |

### COMBAT（38）

| hack | 行数 | 状态 |
| --- | --- | --- |
| `AimAssist` | 275 | 待办 |
| `AnchorAura` | 547 | 待办 |
| `AntiBot` | 177 | 待办 |
| `ArrowDmg` | 88 | 待办 |
| `AutoArmor` | 262 | 待办 |
| `AutoCity` | 159 | 待办 |
| `AutoLeave` | 138 | 待办 |
| `AutoPotion` | 134 | 待办 |
| `AutoRespawn` | 55 | 待办 |
| `AutoSoup` | 189 | 待办 |
| `AutoSword` | 207 | 待办 |
| `AutoTotem` | 156 | 待办 |
| `AutoTrap` | 191 | 待办 |
| `AutoWeb` | 157 | 待办 |
| `BowAimbot` | 261 | 待办 |
| `Burrow` | 259 | 待办 |
| `ClickAura` | 139 | 待办 |
| `Criticals` | 228 | 待办 |
| `CrystalAura` | 443 | 待办 |
| `DelayRemover` | 61 | 待办 |
| `FightBot` | 303 | 待办 |
| `Hitboxes` | 36 | 待办 |
| `HoleFiller` | 152 | 待办 |
| `KeepSprint` | 30 | 待办 |
| `Killaura` | 1259 | 待办 |
| `KillauraLegit` | 349 | 待办 |
| `MultiAura` | 1162 | 待办 |
| `NoMissCooldown` | 67 | 待办 |
| `ProjectilePuncher` | 136 | 待办 |
| `Protect` | 386 | 待办 |
| `RightClicker` | 124 | 待办 |
| `SelfTrap` | 176 | 待办 |
| `SuperKnockback` | 202 | 待办 |
| `Surround` | 220 | 待办 |
| `TargetStrafe` | 144 | 待办 |
| `TpAura` | 192 | 待办 |
| `TriggerBot` | 233 | 待办 |
| `WTap` | 126 | 待办 |

### FUN（12）

| hack | 行数 | 状态 |
| --- | --- | --- |
| `AntiAim` | 134 | 待办 |
| `DankBobbing` | 65 | 待办 |
| `Derp` | 53 | 待办 |
| `HeadRoll` | 50 | 待办 |
| `Lsd` | 49 | 待办 |
| `MileyCyrus` | 60 | 待办 |
| `Notebot` | 217 | 待办 |
| `RainbowUi` | 24 | 待办 |
| `SkinDerp` | 53 | 待办 |
| `Tired` | 45 | 待办 |
| `Twerk` | 45 | 待办 |
| `Vomit` | 48 | 待办 |

### ITEMS（10）

| hack | 行数 | 状态 |
| --- | --- | --- |
| `AutoDrop` | 90 | 待办 |
| `AutoEat` | 351 | 待办 |
| `AutoSteal` | 108 | 待办 |
| `AutoSwitch` | 47 | 待办 |
| `CrashChest` | 62 | 待办 |
| `FastUse` | 105 | 待办 |
| `ItemGenerator` | 87 | 待办 |
| `KillPotion` | 119 | 待办 |
| `Restock` | 180 | 待办 |
| `TrollPotion` | 117 | 待办 |

### MOVEMENT（45）

| hack | 行数 | 状态 |
| --- | --- | --- |
| `AirJump` | 101 | 待办 |
| `Anchor` | 68 | 待办 |
| `AntiEntityPush` | 45 | 待办 |
| `AntiHunger` | 55 | 待办 |
| `AntiVoid` | 54 | 待办 |
| `AntiWaterPush` | 86 | 待办 |
| `AutoSprint` | 121 | 待办 |
| `AutoSwim` | 51 | 待办 |
| `AutoWalk` | 43 | 待办 |
| `BaritoneWalk` | 90 | 待办 |
| `Blink` | 198 | 待办 |
| `BoatFly` | 90 | 待办 |
| `BunnyHop` | 87 | 待办 |
| `CreativeFlight` | 131 | 待办 |
| `Dolphin` | 49 | 待办 |
| `ElytraFly` | 155 | 待办 |
| `ExtraElytra` | 147 | 待办 |
| `FakeLag` | 117 | 待办 |
| `FastLadder` | 53 | 待办 |
| `Fish` | 49 | 待办 |
| `Flight` | 189 | 待办 |
| `Follow` | 278 | 待办 |
| `Glide` | 120 | 待办 |
| `HighJump` | 36 | 待办 |
| `InvWalk` | 129 | 待办 |
| `Jesus` | 180 | 待办 |
| `Jetpack` | 45 | 待办 |
| `NoClip` | 95 | 待办 |
| `NoFall` | 186 | 待办 |
| `NoJumpDelay` | 79 | 待办 |
| `NoLevitation` | 25 | 待办 |
| `NoRotate` | 68 | 待办 |
| `NoSlowdown` | 143 | 待办 |
| `NoVelocity` | 256 | 待办 |
| `NoWeb` | 40 | 待办 |
| `PacketFly` | 158 | 待办 |
| `Parkour` | 79 | 待办 |
| `ReverseStep` | 141 | 待办 |
| `SafeWalk` | 109 | 待办 |
| `Sneak` | 150 | 待办 |
| `SnowShoe` | 25 | 待办 |
| `SpeedHack` | 210 | 待办 |
| `Spider` | 49 | 待办 |
| `Step` | 161 | 待办 |
| `VehicleBoost` | 74 | 待办 |

### OTHER（15）

| hack | 行数 | 状态 |
| --- | --- | --- |
| `AntiAfk` | 259 | 待办 |
| `AutoFish` | 274 | 待办 |
| `AutoLibrarian` | 528 | 待办 |
| `AutoReconnect` | 38 | 待办 |
| `FeedAura` | 210 | 待办 |
| `MusicPlayer` | 27 | 待办 |
| `PacketCanceller` | 100 | 待办 |
| `PacketLogger` | 91 | 待办 |
| `Panic` | 47 | 待办 |
| `PortalGui` | 24 | 待办 |
| `PotionSaver` | 53 | 待办 |
| `Reach` | 67 | 待办 |
| `Throw` | 71 | 待办 |
| `Timer` | 38 | 待办 |
| `TooManyHax` | 159 | 待办 |

### RENDER（50）

| hack | 行数 | 状态 |
| --- | --- | --- |
| `AntiBlind` | 28 | 待办 |
| `AntiWobble` | 25 | 待办 |
| `BarrierEsp` | 24 | 待办 |
| `BaseFinder` | 251 | 待办 |
| `BossStack` | 74 | 待办 |
| `Breadcrumbs` | 149 | 待办 |
| `CameraDistance` | 35 | 待办 |
| `CameraNoClip` | 24 | 待办 |
| `CaveFinder` | 246 | 待办 |
| `ChestEsp` | 275 | 待办 |
| `CityEsp` | 150 | 待办 |
| `EntityCulling` | 99 | 待办 |
| `Freecam` | 183 | 待办 |
| `Fullbright` | 197 | 待办 |
| `HealthTags` | 54 | 待办 |
| `HoleEsp` | 193 | 待办 |
| `ItemEsp` | 124 | 待办 |
| `LightOverlay` | 117 | 待办 |
| `LogoutSpots` | 199 | 待办 |
| `MobEsp` | 177 | 待办 |
| `MobSpawnEsp` | 193 | 待办 |
| `NameProtect` | 56 | 待办 |
| `NameTags` | 227 | 待办 |
| `NewChunks` | 259 | 待办 |
| `NoBackground` | 46 | 待办 |
| `NoFireOverlay` | 36 | 待办 |
| `NoFog` | 24 | 待办 |
| `NoHurtcam` | 24 | 待办 |
| `NoOverlay` | 26 | 待办 |
| `NoPumpkin` | 24 | 待办 |
| `NoShieldOverlay` | 48 | 待办 |
| `NoVignette` | 24 | 待办 |
| `NoWeather` | 69 | 待办 |
| `OpenWaterEsp` | 71 | 待办 |
| `Overlay` | 67 | 待办 |
| `PlayerEsp` | 315 | 待办 |
| `PlayerHalo` | 47 | 待办 |
| `PopChams` | 181 | 待办 |
| `PortalEsp` | 223 | 待办 |
| `ProphuntEsp` | 73 | 待办 |
| `Radar` | 120 | 待办 |
| `RemoteView` | 180 | 待办 |
| `RotationSnap` | 106 | 待办 |
| `Search` | 256 | 待办 |
| `SeedOreEsp` | 541 | 待办 |
| `SeedStructureEsp` | 265 | 待办 |
| `TargetShader` | 79 | 待办 |
| `Trajectories` | 287 | 待办 |
| `TrueSight` | 56 | 待办 |
| `XRay` | 244 | 待办 |

