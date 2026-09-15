状态：不适用（参考侧仅 1.12.2 事件层实现，本工程 Modify/JumpReset 已更完整，且参考自身有确证缺陷，零改动）

## 对照证据

参考侧路径前缀 = `_oe_ref\src\main\kotlin\studio\coni\epsilon\`；本工程路径前缀 = `WurstB-Plus-main\src\main\java\net\wurstclient\`。

| 关注点 | 参考（_oe_ref 相对路径:行） | 本工程（相对路径:行） | 判定 |
| --- | --- | --- | --- |
| 百分比语义 | `module/movement/Velocity.kt:34-36` `(horizontal * packetMotionX / 100.0).toInt()`，1.12.2 的 `packetMotionX` 是 1/8000 单位整型 | `hacks/NoVelocityHack.java:150-151` `xa/8000.0, ya/8000.0, za/8000.0` 还原为格/刻，`:166-169` 再乘 `horizontal.getValue()/100` | 等价：两边都是「原速度 × N%」，1/8000 只影响取整精度；1.20.2 `ClientPacketListener.java:496` 证明原版同样除以 8000 |
| 应用方式 | `Velocity.kt:33-36` 直接改写包字段，由原版包处理器赋给玩家 | `hacks/NoVelocityHack.java:170-171` `setDeltaMovement(modified)` + `event.cancel()` | 等价：1.20.2 `Entity.java:2124` 的 `lerpMotion` 体只有 `setDeltaMovement`，无 hasImpulse 等额外副作用 |
| 双 0 语义 | `Velocity.kt:33`（实体）/`:39`（爆炸）`cancel()` → 保留当前动量 | `hacks/NoVelocityHack.java:166-169` → `util/VelocityPlanner.java:20-25`：`horizontal==0` 取 `current*retain` | `retain=100`（默认）时与参考 cancel 等价；`retain<100` 是本工程多出的可调维度 |
| 爆炸 Z 分量 | `Velocity.kt:43` `packetMotionZ = horizontal * velocity.motionX / 100f`（用 X 算 Z） | `hacks/NoVelocityHack.java:184-185` `wurst_setKnockbackZ(getKnockbackZ() * horizontalMultiplier)`（用 Z 算 Z） | 参考侧确证缺陷，本工程正确（反例见「故意不搬」1） |
| 爆炸包筛选 | `Velocity.kt:38-39` 无 entityId 判定、无开关 | `hacks/NoVelocityHack.java:141-144` 仅 MODIFY + `explosions` 勾选 + `shouldApply()` | 本工程更完善 |
| 触发/概率 | 无 | `hacks/NoVelocityHack.java:58-63` chance/trigger/onlyMoving + `:188-197` 流体与鞘翅门槛 | 本工程更丰富 |
| JumpReset | 参考无此模式 | `hacks/NoVelocityHack.java:155-163, 200-229` + `util/VelocityPlanner.java:46-62` | 本工程多出（含摔伤速度过滤） |
| 实体推挤抑制 | `Velocity.kt:26-27` 每 tick `entityCollisionReduction = 1.0f` | 无 | 不适用：该字段在 1.20.1 已不存在（client_mappings.txt grep 命中 0） |
| 方块/液体推挤 | `Velocity.kt:59-66` 取消 `PlayerPushEvent` 的 BLOCK/LIQUID | 无 | 不适用：1.20.1 无该事件层（见「建议（未做）」1） |
| 设置项 | `Velocity.kt:20-23` Horizontal/Vertical/Blocks/Liquid | `hacks/NoVelocityHack.java:40-85` 13 项 | 功能为本工程超集 |

## 实际改动

无。本次仅审计，未修改任何 `.java`（按任务约束，发现的问题一律进「建议（未做）」）。

## 故意不搬

1. 参考的爆炸分量改写法（`Velocity.kt:41-43`）：`:43` 用 `motionX` 写 `packetMotionZ`。反例——爆炸给出 `motionX=0, motionZ=8.0, horizontal=50` 时，参考写回 `packetMotionZ=0`（Z 击退被清零，X 无损），正确值应为 4.0；对称地 `motionX=8.0, motionZ=0` 时参考会凭空写入 4.0 的 Z 击退。本工程 `:185` 用正确的 Z 分量，故不搬该实现。
2. `Velocity.kt:26-27` 的 `entityCollisionReduction`：1.20.1 无此字段（映射表命中 0），无法编译意义下也不成立。
3. `Velocity.kt:59-66` 的 `PlayerPushEvent` 式方块/液体推挤取消：1.20.1 没有暴露该事件，要复刻需改共享 mixin 文件。
4. `Velocity.kt:22-23` 的 `Blocks`/`Liquid` 两个 bool：本工程无对应设置项，加设置项会改变用户可见配置面。

## 建议（未做）

1. 参考的「方块/液体推挤抑制」在本工程完全缺失：1.20.1 的 `Entity#push` / `isPushedByFluid` 仍存在（client_mappings.txt 分别命中 7/7），可在共享 mixin 里拦，但涉及并行 agent 正在改的文件，未动。
2. `util/VelocityPlanner.java:57` 的 `age > Math.max(0, maximumAge)` 不可达：`hacks/NoVelocityHack.java:222-223` 每 tick 让 `pendingJumpTicks` 与 `pendingJumpAge` 同步 -1/+1，`pendingJumpTicks` 归零时 `age` 恒等于 `jumpDelay`，而 `:161` 设 `maximumAge = jumpDelay + 2`，故该条件在任何输入下都为假。去掉它与保留它行为完全相同 → **不是 bug**，只是冗余分支；未改。
3. `hacks/NoVelocityHack.java:142` 要求 `mode == MODIFY` 才处理爆炸，因此在 JumpReset 模式下 `Explosions` 开关静默失效（爆炸击退通常也不产生 `ClientboundSetEntityMotionPacket`，`:155-163` 的队列不会排入）。若希望 JumpReset 也响应爆炸，需要在服务端 `ClientboundExplodePacket` 里自行推导速度，超出本次范围。
4. 未实机验证：`:170` 用 `setDeltaMovement` + 取消包代替原版 `lerpMotion` 后，在带反作弊的服务器上是否与「改写包字段」产生不同的回弹/否决行为。
