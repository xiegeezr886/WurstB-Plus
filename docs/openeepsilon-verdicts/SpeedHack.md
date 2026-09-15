状态：不适用

## 对照证据
| 关注点 | 参考（_oe_ref 相对路径:行） | 本工程（相对路径:行） | 判定 |
|---|---|---|---|
| 基准速度常数 | `src/main/kotlin/.../movement/Speed.kt:42` `moveSpeed = 0.2873`、`:199` `var base = 0.2873` | `src/main/java/net/wurstclient/hacks/SpeedHackHack.java:28` `BASE_SPEED = 0.2873` | 数值同源，但语义不同：参考把它当**初始值**、每 tick 用 `lastDist - (lastDist - base)*k` 衰减（`Speed.kt:126-136`）；本工程把它当**恒定目标速度** |
| 加速落在哪一环 | 参考改 `PlayerMoveEvent.Pre` 的 event.x/z（`Speed.kt:52,176-177`），即改本 tick 位移 | 本工程改 `deltaMovement`（`SpeedHackHack.java:119,147,164,180,184`）；`UpdateEvent` 在 `LocalPlayer.tick()` 的 `super.tick()` 前触发（`mixin/ClientPlayerEntityMixin.java:64-70`），随后 `aiStep()→travel()` 照原版再跑一遍 | 等价手法（动能级），1.20.1 成立，无需搬 Pre/Post 事件 |
| 地面摩擦常数 | 1.12.2 旧值 | 原版 1.20.2 `LivingEntity.java:2197-2199`：`f2 * 0.91F`，`f2` 来自方块 `getFriction`（默认 0.6）；`:2303-2304` `0.21600002F/(f^3)` | **未照抄旧常数**，代码里根本没有摩擦常数，摩擦由原版 `travel()` 施加 |
| 跳跃初速 | `Speed.kt:115` `0.399399995803833`/`0.42`、`:104` `0.08` | `SpeedHackHack.java:110,124,164` `0.42`、`:180` `0.2`、`:184` `-0.08` | 与 1.20.2 一致：`LivingEntity.java:2056` `getJumpPower()`（默认 0.42）、`:2265` 撞墙 `0.2`；1.12.2 与 1.20.1 此处无差异 |
| 是否按 TPS 缩放 | 参考用 `WorldTimer` 改 tick 长度（`Speed.kt:39,58-59`） | 全部为「每 tick 速度」量，20 TPS 基准，无 ms/冷却换算 | 不需要缩放：TPS 补偿见 `docs/openeepsilon-refactor.md` §0.3（TpsCompensation 面向延迟类逻辑）；定时器法不搬 |
| 是否存在 1.12.2 旧常数 | `Speed.kt` 的 0.2873 是 1.12.2 弹跳引擎稳态值 | 唯一沿用点即 `:28` | 它是**速度参数**，非「摩擦/跳跃公式的旧常数」；0.2873×20=5.75 格/秒，仍随本工程的 `speed` 滑块同向线性变化，故不算照抄错误常数 |

## 实际改动
无。
逐项排查后**找不到能给出「旧 X / 新 Y」输入的确定性缺陷**（按 _BRIEF 第 9 条，写不出反例即不许改）：
- `applyHop/applyStrafe/applyLowHop` 的 Y 分量：`blendHorizontal`/`clampControlledHorizontal` 都只处理 X/Z（`util/MovementPlanner.java:51-61,78-86`），Y 原样透传，无覆盖 bug。
- 方向公式 `MovementPlanner.horizontalMotion`（`:34-41`）经代入验证：forward=1 → (-sin,cos)，sideways=1 → (cos,sin)，与 1.20.2 `LocalPlayer.aiStep` 的输入向量一致；对角按 `max(1,length)` 归一化同原版。
- `lowHopActive` 状态、模式切换、`canControl` 各开关均无状态泄漏路径。
- `applyLowHop` 的 `:183` Y 钳制在只读遍历中不改变 X/Z：`(±0.4, -0.5)` → 旧 `(0.1149, -0.5)`，新 `(0.1149, -0.08)`，是设计意图（滞空），不是 bug。

## 故意不搬
- `Timer`/`WorldTimer` 分包变速（`Speed.kt:39,58-59`）：1.20.1 上属高危包级手法，且需与全局 tick 链路联动，本工程无对应设施。
- `Hypixle` 分档 + `lastDist` 反馈环（`Speed.kt:63-92`）：依赖 1.12.2 的 `motionY`/`MoveEvent` 语义，1.13+ 游泳与 1.14+ 跳跃改了速度模型，照抄得不到同一曲线。
- `TargetStrafe` 接管 forward/strafe/yaw（`Speed.kt:144-147`）：本工程由 `HackConflictGroup.MOVEMENT_CONTROL` + 各 aura hack 自行处理转向，不做跨模块耦合。
- `HurtBoost`（`Speed.kt:34-36,87-89`）：属于额外特性而非正确性缺失，且 `hurtTime > hurtTime` 那类写法在 `_BRIEF` 第 9 条已有前例，本轮不加。

## 建议（未做）
1. `BASE_SPEED` 不随 `Attributes.MOVEMENT_SPEED` 缩放：原版 1.20.2 `Player.java:1522-1523` `getSpeed()=getAttributeValue(MOVEMENT_SPEED)`、`:214` 默认 0.1，速捷/缓慢药水都通过该属性生效。故速捷 II 玩家开启本 hack 后水平速度仍是 0.5746（speed=2.0），不随药水提高，药水因此失去意义；缓慢同理被完全忽略。要改需引入 `player.getSpeed()`（1.20.1 `client_mappings.txt:2366` 已确认存在），属行为变更，需实机确认后再定。
2. `whileUsingItems`（`:48-50,134`）实际是空开关：`onUpdate` 在 `aiStep()` 之前跑，而 1.20.2 的物品减速发生在 `LocalPlayer.java:695-697`（`input.leftImpulse *= 0.2F`）之后，所以 `:96-97` 读到的永远是**未减速**的输入，勾不勾都一样。修法是改读减速后输入或直接在 `travel()` 层拦截，会触碰 `ClientPlayerEntityMixin`（共享文件），本轮不动。
3. `clampControlledHorizontal`（`MovementPlanner.java:81-85`）：当当前水平速度 > 目标速度时直接返回当前 X/Z、丢弃新方向。`speed=1.0` 时阈值 0.2874，被爆炸击退到 0.5 的空中 tick 内按 W 不会立刻改向（旧：瞬间转向 0.2873；新：不转向）。这是共享文件且是「不减速」策略的代价，留给 MovementPlanner 的负责人评估。
