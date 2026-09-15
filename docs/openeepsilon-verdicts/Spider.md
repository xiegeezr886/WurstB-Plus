状态：不适用

## 对照证据
| 关注点 | 参考（_oe_ref 相对路径:行） | 本工程（相对路径:行） | 判定 |
| --- | --- | --- | --- |
| 参考侧有无对应模块 | `_oe_ref/src/main/kotlin/studio/coni/epsilon/management/ModuleManager.kt:84-109`（Movement 注册表逐条读过；整个 `_oe_ref/src` 也 grep 不到 spider / climb 类模块名） | `src/main/java/net/wurstclient/hacks/SpiderHack.java:16-22` | **无对应**。任务给的 `AntiWeb.kt`、`AutoJump.kt`、`EntitySpeed.kt` 分别是防走网、自动跳、骑乘加速，与贴墙爬升无关，不硬凑 |
| 最接近的参考代码 | `_oe_ref/src/main/kotlin/studio/coni/epsilon/module/movement/Step.kt:106-111`（AAC 模式：`collidedHorizontally` 时 `motionY = 0.4322`，否则 `+= 0.0122`） | `SpiderHack.java:40-47` | 不搬：那是 1.12.2 的 NCP 数值，而且只是上台阶模块的一个分支（`Step.kt:117-119` 的 Vanilla 分支只管 `stepHeight`） |
| 爬墙常量 | 无 | `SpiderHack.java:44-47`（`y >= 0.2` 就不覆盖，否则写 0.2） | 与原版自己的规则**同常量**：1.20.2 `world/entity/LivingEntity.java:2263-2266`（水平碰撞或跳跃、且可攀爬或可站细雪 ⇒ `y = 0.2`） |
| 每 tick 净上升量 | 无 | `SpiderHack.java:47`；写入点在 `UpdateEvent`（`src/main/java/net/wurstclient/mixin/ClientPlayerEntityMixin.java:64-70`，在 `LocalPlayer.tick()` 之前 fire，所以本 tick 的物理会消费它） | 与原版梯子**完全同速**：0.2 先被重力减 0.08、再乘 0.98（1.20.2 `world/entity/LivingEntity.java:2086,2209-2216`）⇒ 两边都是每 tick 0.1176 格；原版 `:2265` 的 0.2 也要过同一段重力 |
| 触发条件 | `Step.kt:54`、`Step.kt:106`（`collidedHorizontally`） | `SpiderHack.java:40`（`player.horizontalCollision`） | 同名同义（1.20.2 `world/entity/Entity.java` 的公开字段，原版用法见 `LivingEntity.java:2124,2263`） |
| 守卫与失效场景 | 无 | `SpiderHack.java:40-47`（只有水平碰撞与 `y < 0.2` 两条） | 无可举证缺陷：梯子 / 脚手架本来就走原版同一条 0.2 分支（1.20.2 `LivingEntity.java:2263-2266`）；网内减速由另一个 hack 负责（`src/main/java/net/wurstclient/hacks/NoWebHack.java:36-39` 把 `stuckSpeedMultiplier` 清零） |
| 设置项 | 无 | `SpiderHack.java:18-22`（没有设置项） | 不适用 |

## 实际改动
无。参考侧的爬墙手法（`Step.kt:106-111` 的 0.4322 / +0.0122）是 1.12.2 NCP 数值，而本工程这一行本来就精确复刻了原版自己的 0.2 爬墙规则，没有可验证的改进点。

## 故意不搬
- `Step.kt:106-111` 的 `motionY = 0.4322` / `+= 0.0122`：只对 1.12.2 的 NCP 有意义；照搬后每 tick 净上升约 0.35 格，是原版梯子（0.1176）的 3 倍，纯属改坏。
- `Step.kt:15-17` + `:58-103` 的三组硬编码包坐标表（`CPacketPlayer.Position` 连发）：包级传送绕过，1.20.1 服务端逐步校验移动，属典型的「不适用」。
- `.../movement/AutoJump.kt:21-22`（水里给 `motionY = 0.1`、地面定时跳）与 `.../movement/EntitySpeed.kt:82-102`（载具转向）：与贴墙垂直运动无关，不在本文件范围。

## 建议（未做）
- 可选硬化，**明确不是缺陷**：`SpiderHack.java:40-47` 不看 `onClimbable` / `isFallFlying`。滑翔时撞墙会把 +0.2 直接注入滑翔物理的输入（1.20.2 `world/entity/LivingEntity.java:2165` 取 `getDeltaMovement()` 再叠加，`:2180-2181` 减速后 `move`），表现为贴墙滑翔被抬起。但我构造不出「旧行为有害、新行为无害」的具体输入，按规范不记为 bug，不建议本轮改。
- `SpiderHack.java:39-40` 未判 `MC.player == null`：`UpdateEvent` 由 `ClientPlayerEntityMixin.java:64-70` 在 `LocalPlayer.tick()` 内触发，本轮找不到可达的 NPE 路径；补空判不改变任何可达行为，属无效改动，故未做（若父 agent 要求全员统一防御式写法，加一行即可，不影响功能）。
