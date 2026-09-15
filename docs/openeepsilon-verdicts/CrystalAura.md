状态：已重构（本轮之前已完成；本轮逐条核对无可移植项，代码未改）

## 对照证据

参考侧对应模块是 `module/combat/ZealotCrystalPlus.kt`（1520 行，1.12.2，**包级**放置/破坏 + 自带伤害数学）。

| 关注点 | 参考（_oe_ref 相对路径:行） | 本工程（相对路径:行） | 判定 |
| --- | --- | --- | --- |
| 候选位选择 | `:671` 过滤 + 手写循环挑最优，逻辑内联在模块里，无法单测 | `hacks/CrystalAuraHack.java:182,207` 走 `util/CrystalAuraPlanner.selectBest`；打分与门限全在 `util/CrystalAuraPlanner.java:14-52`（纯类 + 既有 `CrystalAuraPlannerTest`） | 本工程已重构 |
| 伤害计算 | `:521,705` 自己一套 `calcDamage(...)`（1.12.2 的护甲/附魔公式） | `util/DamageUtils.calculateDamage`（`DamageUtils.java:137-171`）：`Explosion.getSeenPercent` 暴露度 + 原版 `CombatRules.getDamageAfterAbsorb` + 每实体缓存的保护附魔/抗性画像 | 本工程用的是 1.20.1 原版公式，参考那套照抄会算错（§0.2 ① 已记） |
| 爆炸中心 | —— | 已放置的水晶用 `crystal.position()`（`:223`）；待放置的位置用 `(pos.x+0.5, pos.getY(), pos.z+0.5)`（`:253`）。这与原版一致：`EndCrystal` 爆炸中心就是实体坐标，而水晶实体的 Y 等于它所在那一格（基座方块的**上一格**）的 Y，`placeCrystal` 点的也正是基座的顶面（`:292-320`） | 正确，无 off-by-one |
| 自伤取值点 | `:521` 取 `max(calcDamage(脚), calcDamage(眼))` 两个点里更大的那个 | `DamageUtils.java:147-148` 用 `entity.distanceToSqr(explosionPos)`，即原版 `Entity#distanceToSqr(x,y,z)` 的**脚部坐标**，与原版 `Explosion` 的 `Math.sqrt(entity.distanceToSqr(x,y,z)) / (power*2)` 逐字一致 | **本工程更准**：参考的 max(脚,眼) 是保守高估，原版只算脚部。不改 |
| 自杀保护 | `:522` `player.scaledHealth - selfDamage <= noSuicide`，`scaledHealth` = 吸收按血量占比折算 | `hacks/CrystalAuraHack.java:270` 传 `getHealth() + getAbsorptionAmount()`（= `DamageUtils.totalHealth`），`CrystalAuraPlanner.java:51` 用严格 `<` 比较 | **本工程更准**：1.20.1 单次伤害的存活判据就是 `damage < 血量 + 吸收`，也正是本工程 `DamageUtils.isLethal`(`DamageProfile.java:100`) 用的口径；参考的 `scaledHealth` 偏保守。不改 |
| 自伤/敌伤门限 | `:136-137,150-151` 分为 Place/Break 两套 `MinDamage`/`MaxSelfDamage`，另有 `:108` ForcePlace、`:559,728` `targetDamage - selfDamage >= balance` | `hacks/CrystalAuraHack.java:90-105` 单一套 `Min damage`/`Max self damage`/`Min damage advantage`；`CrystalAuraPlanner.java:44-48` 三条门限齐备 | 参考多出「放置/破坏分别设门限」这一层细分，属设置粒度取舍，不是缺陷；不新增设置项 |
| 时序 | `:139` Place Delay 50ms、`:153` Break Delay 100ms、`:586-587` 两个计时器 | `hacks/CrystalAuraHack.java:86-89` Break delay、`:111-114` Place speed（0 = instant），`:172-175` 递减、`:186-189`/`:204-212` 使用；另 `:107-110` `Min crystal age` 防止对刚放下的水晶立刻引爆 | 等价且更完整（多一个水晶年龄门限） |
| 先破还是先放 | `:586-587` 两个 flag 同一 tick 都可成立（可同 tick 一破一放） | `hacks/CrystalAuraHack.java:184-195`：附近有可破的水晶就先破并 `return`，没有才考虑放 | 取舍不同（本工程更保守），不是缺陷 |
| 目标伤害取谁 | `:540-559` `checkBreakDamage` 对每个目标单独校验 | `hacks/CrystalAuraHack.java:274-283` `getBestTargetDamage` 取「所有目标里吃得最多的人」的伤害，再用该值过门限 | 参考更精细（换目标可能不满足门限），但改为逐目标校验会改变「多目标时选谁」的行为，属功能取舍，未做 |

## 原版真源（本次用来核对上面几行，仓库内可直接查）

反编译源根：`neoforge/versions/1.20.2/build/neoForm/neoFormJoined1.20.2-20231019.002635/steps/unzipSources/unpacked/net/minecraft/`

- `world/level/Explosion.java:204` `float f2 = this.radius * 2.0F;`
  `:213` `Vec3 vec3 = new Vec3(this.x, this.y, this.z);`（爆炸中心）
  `:217` `double d12 = Math.sqrt(entity.distanceToSqr(vec3)) / (double)f2;`（**脚部坐标**）
  `:229` `... (float)((int)((d10 * d10 + d10) / 2.0 * 7.0 * (double)f2 + 1.0))`
  → 与 `util/DamageUtils.java:146-154` 的 `diameter`/`dist`/`impact`/伤害式逐项对应。
  （`:220` 用 `getEyeY()` 只是算**击退方向**，不进伤害公式。）
- `world/entity/boss/enderdragon/EndCrystal.java:98` 爆炸中心就是实体自身坐标：
  `this.level().explode(this, damagesource, null, this.getX(), this.getY(), this.getZ(), 6.0F, false, Level.ExplosionInteraction.BLOCK)`
- `world/entity/EntityType.java` 的 `END_CRYSTAL` 是 `.sized(2.0F, 2.0F)` → 水晶碰撞箱 2×2×2（这就是建议第 1 条的依据）。
- `world/entity/Entity.java` 的 `distanceToSqr(double,double,double)` 与 `distanceToSqr(Vec3)` 都用 `getX()/getY()/getZ()`。

## 实际改动

无。逐条核对后没有发现「改了会更正确」的点，按 _BRIEF.md 第 7 条没有为了交差去动代码。

## 故意不搬

- `:711-712` 的 lethal 追踪（`lethalOverride` / `lethalBalance` / `lethalMaxDamage`：允许一次会让自伤超标、但足以打死目标的放置）。这是一个**新功能**（需要新设置项 + 行为变更），不是正确性修复，且必须实机验证「值不值得用命换这一下」。
- `:108` ForcePlace、`:546-559` 的 Place/Break 双套门限：同理，属设置粒度。
- 参考的放置/破坏走 `sendPlayerPacket` + `CPacketUseEntity`/`CPacketPlayerTryUseItemOnBlock`（1.12.2 包级手法），1.20.1 的等价物是本工程已在用的 `IMC.getInteractionManager().rightClickBlock` / `MC.gameMode.attack`（`:237,319`），不需要搬包。
- `:483` 的 `SPacketSpawnObject` 拦截（用生成包来抢在水晶出现前就瞄准）：1.12.2 的抢包手法，1.20.1 上服务端会先校验实体，收益与风险都不明确，不搬。

## 建议（未做）

1. `hacks/CrystalAuraHack.java:388-393` 的 `isCrystalSpaceClear` 用的是 **1×2×1** 的柱子，而末影水晶的碰撞箱是 **2×2×2**（`EndCrystal` 的 `EntityDimensions.scalable(2.0F, 2.0F)`）。它只是客户端用来少浪费一次放置的启发式，放宽到真实尺寸会让「目标贴着基座站」时几乎所有位置都被跳过，反而更差，所以**刻意保持原样**；但如果将来发现「放下去立刻被挤掉」的现象，这里是第一个要看的地方。
2. 目标伤害目前取「所有目标里的最大值」再过门限（上表最后一行）。若要做到参考那样逐目标校验，需要同时定义「多目标时最终打谁」，属功能设计，未做。
