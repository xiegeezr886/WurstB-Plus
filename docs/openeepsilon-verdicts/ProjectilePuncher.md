状态：不适用

## 对照证据

参考侧 `module/combat/` 没有对应模块（最近的是 `AntiCev.kt` / `AutoCev.kt`，那是反过来"打水晶"的，
机制不同），所以没有可搬实现，只做自审。本工程这个 hack 是 LiquidBounce 的 `ProjectilePuncher`
改写（文件头已注明 CCBlueX / Penguin），核心在 `util/ProjectileThreatPolicy`。

## 实际改动

无（零改动）。逐条核对结果：

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 目标类型 | `hacks/ProjectilePuncherHack.java:127-135`：`LargeFireball` 或 `ShulkerBullet`，且 `isAlive()` | 与 LiquidBounce 的两类一致；凋灵头颅/雪球等按来源不处理，属取舍 |
| 威胁判定 | `util/ProjectileThreatPolicy.isThreat(position, delta, box, playerBox)` | 不只按类型，还判"是否朝我来"（用速度与相对位置），比"看到火球就打"精确 |
| 距离口径 | `:118-119` 用**预测后**的碰撞箱（`box.move(delta)`）到眼睛的距离，再与 `range²` 比较 | 会提前打"这一 tick 之后才进入范围"的火球，方向正确 |
| 视线 | `:120` `MC.player::hasLineOfSight` | 隔着墙不打，合理 |
| 攻击门槛 | `:96-100` `rotationController.request(...)` + `RotationUtils.isFacingBox(box, range)` + `speed.isTimeToAttack()` | 与其它 combat hack 同一套时序 |
| 容器暂停 | `:108-112` + `PauseAttackOnContainersSetting(true)` | 已有 |

**一处看着不一致、但核实后不是 bug 的地方**：选目标用预测位置（`:118-119`）、瞄点却用当前碰撞箱中心
（`:96-97`）。原版实体攻击包**不带朝向**，服务端只用距离（≤36）做 reach 校验，所以"打不着移动中的火球"
不会发生；客户端自己的 `isFacingBox` 门是当 tick 的几何检查，用当前箱子才对。故不改。

## 建议（未做）

- 如果想让"转向"与"选目标"用同一份预测，可以把 `rotationController.request(...)` 的点也换成
  `box.move(delta).getCenter()`。纯手感改动（会让准星提前一点），未做。
- `range` 滑条量程 3..6（`:39-40`）：下限 3 是原版 reach，上限 6 已经超出服务端会接受的实体攻击距离，
  实际能打到的上限由服务端决定；没有可举证的错误，只是量程偏宽，未动。
