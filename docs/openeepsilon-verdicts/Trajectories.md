状态：已重构

## 结论先说

参考侧 `render/Trajectories.kt` 的**积分方式是对的**（每 tick 一次 `pos += motion; motion *= 0.99; motionY -= gravity`，
`:109-118`，与 1.20.2 `AbstractArrow` 完全同序），重力表也基本对（`:62-65` 药水 0.05、弓 0.05、其余 0.03）。
**反而是本工程旧实现错得更多**：积分被拆成"每 tick 十次 `p += v*0.1; v *= 0.999; v.y -= g*0.1`"，
并且常量表里有三个值与真源不符（药水重力 0.4 vs 0.05、鱼漂 0.15 vs 0.03、三叉戟 0.015 vs 0.05）、
两个出手速度不符（药水 1.5 vs 0.5、三叉戟 1.5 vs 2.5），还缺了药水/经验瓶的 -20° 抬角与鱼漂的 0.92 阻力。
本轮按 1.20.2 真源把整个预测器重写，并把可单测的部分下沉到 `util/ProjectilePhysics`。

## 对照证据

| 关注点 | 参考（Trajectories.kt:行） | 1.20.2 真源（file:行） | 本工程旧值 → 新值 | 判定 |
| --- | --- | --- | --- | --- |
| 积分方式 | `:109-118` 每 tick：`posX += motionX`（三轴）→ `motion *= 0.9900000095367432` → `motionY -= gravity` | `AbstractArrow.java:240-257`（先 `setDeltaMovement(scale(0.99))` 再 `y - 0.05F`，最后 `setPos`）、`ThrowableProjectile.java:82-91` | 旧：每 tick 十次 `arrowPos += arrowMotion*0.1; arrowMotion *= 0.999; arrowMotion.y -= g*0.1` → 新：每 tick 一次 `p += v; v = dragAndGravity(v)`；十个细分点只落在这一 tick 的直线段上（渲染与碰撞用），不再改变轨迹 | **已修**（详见「新旧数值差异」） |
| 空气阻力 | `:112` `0.9900000095367432`（float 0.99） | `AbstractArrow.java:240` `f = 0.99F`；`FishingHook.java:232-233` `scale(0.92)` | 旧：所有投射物都用 `0.999`/子步（每 tick 等效 `0.999^10 = 0.9900449`），鱼漂也是 → 新：`ProjectilePhysics.DRAG = 0.99`、鱼漂 `FISHING_DRAG = 0.92` | **已修** |
| 重力 | `:62-65` `is ItemPotion -> 0.05`、`is ItemBow -> 0.05`、`else -> 0.03`（**参考这里是对的**） | `ThrownPotion.java:55-56` = 0.05、`ThrownExperienceBottle.java:33-34` = 0.07、`ThrowableProjectile.java:94-95` = 0.03、`AbstractArrow.java:241,254` = 0.05、`FishingHook.java:223` = 0.03 | 旧：药水 `0.4`、鱼漂 `0.15`、三叉戟 `0.015` → 新：药水 0.05、经验瓶 0.07、弓/弩/三叉戟 0.05、其余 0.03 | **已修**（参考对、本工程错） |
| 出手速度 | `:70-72` 统一 `pow * 1.5`（鱼漂/经验瓶用 `0.75`） | `BowItem.java:47` (`f * 3.0`)、`CrossbowItem.java:43,80-81` (`3.15`)、`TridentItem.java:69` (`2.5`，投掷分支 `j == 0`)、`SnowballItem.java:33`/`EggItem.java:33`/`EnderpearlItem.java:34` (`1.5`)、`ThrowablePotionItem.java:21` (`0.5`)、`ExperienceBottleItem.java:38` (`0.7`)、`FishingHook.java:92-97` (`0.6` 再乘均值 0.5 的随机因子 ≈ 0.3) | 旧：非投掷武器一律 1.5，弩也走弓的蓄力公式（得 3.0）→ 新：逐项照真源 | **已修** |
| 俯仰角抬角 | 无 | `ThrowablePotionItem.java:21`、`ExperienceBottleItem.java:38` 的 `-20.0F` 会加进俯仰角（`shootFromRotation` 里 `f1 = -sin((pX + pZ) * π/180)`） | 旧：无 → 新：药水/经验瓶 `getPitchOffset() = -20` | **已修** |
| 出手点 | 未核对（1.12.2 的生成逻辑不同，参考里直接读 `mc.player` 的位置自己加偏移） | `AbstractArrow.java:79`、`ThrowableProjectile.java:27`：`(getX(), getEyeY() - 0.1F, getZ())`——**没有横向偏移**；`FishingHook.java:88-91`：`(getX() - sin(yaw)*0.3, getEyeY(), getZ() + cos(yaw)*0.3)` | 旧：所有投射物都加 `±0.16` 横向手部偏移 → 新：箭/投掷物无横向偏移、鱼漂前方 0.3 格 | **已修** |
| 弓蓄力 | 参考不区分蓄力（只有 `pow * 1.5`） | `BowItem.java:40-41` (`getPowerForTime` 归一化后 `< 0.1` 直接不发射)、`:47` (`* 3.0`) | 旧：`bowPower <= 0.3` 时硬改成 3（满蓄力）→ 新：如实使用当前蓄力；只有"根本没在拉弓"时才沿用满蓄力假设弧线 | **已修** |
| 经验瓶 | 参考支持（`:70` 显式判 `Items.EXPERIENCE_BOTTLE`） | `ExperienceBottleItem.java:38`、`ThrownExperienceBottle.java:33-34` | 旧：`isThrowable` 不含经验瓶，完全不显示 → 新：纳入（0.7 / 0.07 / -20°） | **已修** |
| 实体碰撞 | `:93-107` 自己算 AABB 后遍历区块找实体（`:164-178` `getEntitiesWithinAABB`，用 `EntitySelectors` 风格过滤） | 本工程用 `ProjectileUtil.getEntityHitResult`（原版同款，含 `getPickRadius` 与最近命中择优） | 保留本工程做法 | 不搬 |
| 渲染 | `:143` `GlStateManager.scale(0.05f,...)` + 立即模式画线 | 本工程 `RenderUtils.drawCurvedLine` + `drawSolidBox`/`drawOutlinedBox` | 保留 | 不搬 |
| 颜色 | `:24-25` 单一 `Color` + `ColorMode` | 本工程三个 `ColorSetting`（未命中/命中实体/命中方块） | 保留（更丰富） | 不搬 |

## 实际改动

**新增** `src/main/java/net/wurstclient/util/ProjectilePhysics.java`：把"每 tick 的速度更新"下沉成可单测的纯计算
（`DRAG = 0.99`、`FISHING_DRAG = 0.92`、`SUB_STEPS = 10`、`dragAndGravity` / `gravity` / `drag` + `Velocity` record），
类注释里逐条标注原版出处。**新增** `src/test/java/net/wurstclient/util/ProjectilePhysicsTest.java`（5 个用例）。

**重写** `hacks/TrajectoriesHack.java` 的 `getTrajectory` 主循环与整张参数表：

- `getThrowPower(ItemStack)`：弩 3.15、弓 `getPowerForTime * 3.0`、三叉戟 2.5、药水 0.5、经验瓶 0.7、鱼漂 0.3、其余 1.5。
- `getProjectileGravity(ItemStack)`：药水 0.05、经验瓶 0.07、弓/弩/三叉戟 0.05、其余 0.03。
- 新增 `getProjectileDrag(ItemStack)`（仅鱼漂 0.92）与 `getPitchOffset(ItemStack)`（药水/经验瓶 -20）。
- `getHandOffset(ItemStack, yaw)`：去掉原版不存在的 ±0.16 横向偏移；鱼漂按 `FishingHook.java:88-91` 前移 0.3。
- 主循环改为每 tick 一次原版推进 + 十个细分点；细分点由 `arrowPos + tickMotion * step/10` 直接得出，
  所以折线上的每个点都严格落在原版离散轨迹上（旧的 0.1 tick 自积分会让整条线系统性偏短）。
  碰撞射线也从"上一个细分点"出发，不再像旧代码那样第一段从**眼睛**射到枪口（贴墙时会在眼睛处误判命中）。
- `isThrowable` 纳入 `ExperienceBottleItem`，与参考一致；同时删掉因此不再使用的 `InteractionHand`/`HumanoidArm`/`RotationUtils` 导入。

### 新旧数值差异（可复算）

| 场景 | 旧实现 | 新实现（= 原版逐步迭代） |
| --- | --- | --- |
| 满蓄力弓 `v0 = 3.0`、水平、20 tick（1 秒）后的水平位移 | 54.405 格 | **54.628 格**（= `3.0 * (1 - 0.99^20) / 0.01`） |
| 每 tick 保留的速度倍率 | `0.999^10 = 0.9900449`（多保留 4.5e-5） | `0.99`（精确） |
| 喷溅药水、平视、`v0` | 1.5、俯仰角 0° | **0.5、俯仰角 -20°**（原版抬头 20° 抛出） |
| 药水下落加速度 | 0.4/tick | **0.05/tick**（`ThrownPotion`） |
| 三叉戟 | `v0 = 1.5`、重力 0.015 | **`v0 = 2.5`、重力 0.05** |
| 鱼漂 | `v0 = 1.5`、重力 0.15、阻力 0.99 | **`v0 ≈ 0.3`、重力 0.03、阻力 0.92** |
| 弓拉到 0.2 蓄力（`bowPower = 0.2`）时 | 画出满蓄力（3.0）弧线 | 画出 0.2 蓄力的短弧线（原版此时归一化威力 0.067 < 0.1，**根本不会发射**） |

### 验证

`ProjectilePhysicsTest` 5 个用例全绿：`vanillaHorizontalRangeMatchesClosedForm`（20 tick 位移 = 闭式解 54.628）、
`oldSubStepSchemeFallsShort`（旧方案 54.405，差 0.223 格）、`perSubStepDragWasNotExact`（`0.999^10 > 0.99`）、
`dragIsAppliedBeforeGravity`（`3*0.99 - 0.05 = 2.92`，不是 `(3-0.05)*0.99`）、
`fishingBobberUsesItsOwnDragAndOrder`（先重力 0.03、后阻力 0.92）。
整体 `compileJava`/`compileTestJava`/`test` 全绿。

**验证边界**：只做了编译 + JUnit；轨迹的实机观感（尤其药水 -20° 抬角与鱼漂随机初速）没有在游戏里核对过。
鱼漂的初速在原版里带一个均值 0.5、spread 0.0103365 的三角分布随机因子，本实现取均值（0.3），单次抛竿会有细微偏差。

## 故意不搬

1. `Trajectories.kt:93-107,164-178` 自建实体 AABB 扫描：1.12.2 的 `getEntitiesWithinAABB` 在 1.20.1 要换成
   `Level.getEntities`（本就存在），而本工程用 `ProjectileUtil.getEntityHitResult`（原版同款）已覆盖
   `getPickRadius`、旁观者/可拾取过滤与最近命中，无需自建。
2. `Trajectories.kt:24-25` 单一颜色 + `ColorMode`：本工程三色（未命中/实体/方块）更细，不合并。
3. `Trajectories.kt:143` 立即模式画线：见前几篇（1.20.1 无 GL11 立即模式通道），不搬。

## 建议（未做）

1. 弩的烟花火箭原版是 `1.6F`（`CrossbowItem.java:81`），本实现统一按箭的 3.15 处理。
   构造不出"因此显示错误"的现实输入（烟花火箭的轨迹还受爆炸影响，本来就不该按抛物线预测），故只留档。
2. 三叉戟的 `2.5 + j * 0.5`（`TridentItem.java:69`）里 `j` 是有激流附魔时的等级，但那一分支只在
   `j == 0`（即没有激流）时才会真正生成投掷物（`:63-64,67`），所以固定 2.5 是正确的，未做附魔分支。
3. 水中阻力原版另有取值（箭 `getWaterInertia()`、投掷物 `0.8`、鱼漂水中上浮 `FishingHook.java:198`），
   本实现只按空气阻力预测，且轨迹在接触水面时就终止（`getFluidHandling` 对鱼漂返回 `Fluid.ANY`），
   所以水下段不会画错，但"入水之后的路径"是没有预测的，未做。
4. 原版还有 `inaccuracy = 1.0F` 的随机散布（`BowItem.java:47` 最后一个参数），本预测器画的是无散布的理想线。
   参考侧同样不建模散布，未做。
