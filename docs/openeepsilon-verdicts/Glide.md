状态：不适用（参考侧无对应模块，本工程实现自洽，零改动）

## 参考侧核对

`_oe_ref/src/main/kotlin/studio/coni/epsilon/module/movement/` 共 25 个模块：AntiHunger, AntiLevitation, AntiWeb, AutoCenter, AutoJump, AutoRemount, AutoWalk, ElytraFlight, ElytraReplace, EntitySpeed, FastSwim, Flight, InstantDrop, InventoryMove, Jesus, LongJump, NoFall, NoSlowDown, SafeWalk, Scaffold, Speed, Sprint, Step, Strafe, Velocity。没有 Glide，也没有"限制下落速度"的同义模块（`AntiLevitation` 是抗飘浮效果，方向相反）。因此判「不适用」，主体是自审。

## 本工程现状（自审）

`hacks/GlideHack.java`，120 行。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 生效条件 | `:90-92` `onGround() \|\| isInWater() \|\| isInLava() \|\| onClimbable() \|\| v.y >= 0` 全部跳过 | 正确：只在下落时接管，冰面/水中/梯子上/上升中都不碰 |
| 下落速度 | `:112` `setDeltaMovement(v.x, Math.max(v.y, -fallSpeed.getValue()), v.z)` | 只封顶、不加速（`Math.max`），默认 `fallSpeed = 0.125`（`:29`，范围 0.005..0.25）。原版竖直方程是每 tick `vy = (vy - 0.08) * 0.98`（1.20.2 真源 `world/entity/LivingEntity.java:2210,2216`），极大值收敛到 -3.92；0.125 相当于 2.5 格/秒，确实是"滑翔"而不是自由落体 |
| 水平速度 | `:116-118` `AirStrafingSpeedEvent` 里 `setSpeed(getDefaultSpeed() * moveSpeed.getValueF())` | 改的是空气加速（默认 1.2 倍），不是硬设速度；原版空气摩擦 0.91 仍在，所以极限速度依旧收敛 |
| 最低高度 | `:94-110` `minHeight > 0` 时把包围盒向下扩 `minHeight`，`MC.level.noCollision` 判定；再逐格 `anyMatch(LiquidBlock.class::isInstance)` 手查液体 | 双保险有存在的必要，`:106` 的注释也写明了原因：原版液体的碰撞箱是空的，`noCollision` 对水/岩浆返回 true，只查 `noCollision` 会让"贴水面滑翔"误判成"离地够高" |
| 潜行暂停 | `:85-86` `pauseOnSneak`（默认 true，`:40-41`） | 与 `hacks/FlightHack.java` 的潜行语义一致 |

## 故意不搬

参考侧没有对应模块，没有可搬项。`AntiLevitation` 虽然也动竖直速度，但它是"有飘浮效果时抵消"，与本 hack 的"无条件下落封顶"不是一件事。

## 建议（未做）

1. `:112` 用 `Math.max(v.y, -fallSpeed)` 时，如果这一 tick 的原版重力已经把 `vy` 压到 -0.08 附近，本 hack 的封顶其实完全不起作用（-0.08 > -0.125），只有连续下落几 tick 后才开始真正生效。这是"滑翔"的正常手感，不是缺陷。
2. `moveSpeed` 只放大空气加速度，不限制最高水平速度。要想真正限速需要改写 `deltaMovement` 的水平分量，那会与 `blendHorizontal` 之类的既有 SharedCore 语义冲突，属重定义设置，未做。
3. 验证边界：只做了静态核对与本轮全量 `compileJava`/`test`（本 hack 零改动），没有实机滑翔。
