状态：不适用

## 对照证据

参考侧的 `movement/AntiWeb.kt`（61 行）做的是另一件事：用 `AddCollisionBoxEvent` 与
`PlayerMoveEvent.Pre`（优先级 -2000）处理**走在蛛网上面**时的减速（1.12.2 的特性）。

本工程的 `hacks/NoWebHack.java`（40 行）是每 tick 把
`MC.player.stuckSpeedMultiplier = Vec3.ZERO`，目标是**身在蛛网里**不减速。

## 时序核对（确认它真的有用）

`stuckSpeedMultiplier` 的写入与消费都在同一个 tick 内：

- 消费：`Entity.move()`（1.20.2 反编译源 `Entity.java:624-628`）读到非零值就乘到位移上，然后
  **立刻把它归零**；`tryCheckInsideBlocks()` 在同一方法里稍后被调用（`Entity.java:718`），
  它触发 `WebBlock.entityInside` → `makeStuckInBlock(..., (0.25, 0.05, 0.25))`
  （`WebBlock.java:17`）。
- 我们的写入：UpdateEvent 在 `LocalPlayer.tick()` 的 `super.tick()` **之前**
  （`mixin/ClientPlayerEntityMixin.java:64-70`），也就是在 `move()` 之前。

所以每个 tick 的顺序是：我们写 ZERO → `move()` 读到 0（不减速）→ 蛛网把 0.25 写回去 →
下一 tick 我们再把 ZERO 覆盖上去。**不会**出现"被原版覆盖掉"的问题（这与 AntiAim 的
`input.forwardImpulse` 情况不同：那个是原版在**我们之后**重算，这里是原版在我们之前消费）。

无改动。

## 建议（未做）

- 参考那种"站在蛛网**上面**也减速"的情况在 1.20 已经不存在（`makeStuckInBlock` 只在实体
  与蛛网碰撞箱相交时触发），所以参考那个分支不适用。
- 甜浆果丛与细雪用的是同一个字段（`SweetBerryBushBlock.java:75`、`PowderSnowBlock.java:59`），
  本工程把它们放在 `NoSlowdown` 的开关里；两个模块同时开时 `NoSlowdown` 的 mixin 会在更底层
  跳过 `makeStuckInBlock`，两者不冲突。未改。
