状态：不适用

## 对照证据

| 关注点 | 参考（_oe_ref 相对路径:行） | 本工程（相对路径:行） | 判定 |
| --- | --- | --- | --- |
| 1.20.1 靠什么飞 | `Flight.kt:36-37,48-53` 直接写 `player.motionX/Y/Z`（1.12.2 字段直写），不发包 | `hacks/FlightHack.java:134-137,149` 用 `setDeltaMovement` 注入，并在 `:117` 每 tick 把 `getAbilities().flying` 压回 `false` | **必须在 delta 层做**：1.20.1 的 `flying` 只在 `Player.travel` 分支里生效（1.20.2 `Player.java:1494-1503`，命中时 `:1498` 还会把 `deltaY` 改成 `*0.6`），且 `LocalPlayer.aiStep` 在 `onGround() && flying` 时自行清它（1.20.2 `LocalPlayer.java:831-833`）。所以生存/创造都只能像本工程这样注入 delta；参考的裸字段写法在 1.20.1 不成立（也无法用创造能力位代替） |
| 垂直速度 vs `noGravity` | `Flight.kt:46-54` 只写 `motionY`，不用 `noGravity` | `FlightHack.java:134-137,149`：每 tick 用绝对 deltaY 覆盖重力；`:150` `player.fallDistance = 0` | 正确且**不需要 `noGravity`**：重力是 `travel` 末尾才施加的（1.20.2 `LivingEntity.java:2209-2217`，`:2216` 的 `d2 * 0.98F`），本 hack 在 `UpdateListener` 里重写 delta，等于抵消而非关闭重力；`fallDistance=0` 使 `checkFallDamage`（`LivingEntity.java:303-308`）永不触发。引入 `noGravity` 会额外改同步与 `Entity.move` 的空中刻行为，无收益 |
| 落地时的抖动 | 参考同样存在（1.12.2 里 `motionY` 被它写完后，原版也会在 tick 末尾加一次重力），故参考没有对应处理 | `FlightHack.java:136-137`（`glide` 默认 0）+ `:149` | **不是可修缺陷**：`glide=0` 时 `y` 已经是 `0.0`，"贴地时跳过下压"的新旧代码写出的是**同一个** `setDeltaMovement(x, 0.0, z)`，即任何输入下新旧行为完全相同。真正发生的是原版在该 tick 末尾把 deltaY 改成 -0.08（`LivingEntity.java:2210,2216`），`move()` 消费这 -0.08 后被碰撞夹回地面、`onGround` 保持 true、`fallDistance` 由 `Entity.move` 归零；玩家只在"贴地 → 下陷约 8cm → 弹回"间抖动，不穿地、不摔伤、不影响起跳（`onGround()` 由碰撞置位，与 y 偏移无关）。要真正消除必须改成"贴地时不注入（连水平也停）"，那是重定义 `glide=0` 的语义，且无任何功能收益 |
| `anti-kick` 手法 | `Flight.kt:57-63`：`sendPlayerPacket { move(y-0.04) }`，且**先**做 `!checkBlockCollision(bb.grow(0.0625).expand(0,-0.55,0))` | `FlightHack.java:142-147`：把 deltaY 设为 `-antiKickDistance`（默认 0.04），无碰撞检测 | 参考那套是 1.12.2 的"外观位置包回退"，1.20.1 无 `PlayerPacketManager` 对应物，不搬；改成真实 delta 后它的落地保护也不再必要（贴地下压会被碰撞夹回，见上格）。**属可讨论点，见「建议（未做）」1**，但没有可举证的新旧行为差异，故不算缺陷 |
| 关 hack / 换世界 / 落地残留 | `Flight.kt` 无状态保存（1.12.2 靠原版下一 tick 覆盖） | `FlightHack.java:153-168` 记录并恢复 `getAbilities().flying`；退出世界由 `hack/HackLifecycleManager.java:25-36` 调 `setEnabled(false)`（`hack/Hack.java:161` 触发 `onDisable`）；死亡重生走 `:153-161` 的实例比对 | 覆盖到位：`getAbilities()` 返回活对象 `Player.abilities`（1.20.1 `client_mappings.txt:71205`），恢复有效；`fallDistance` 是纯数值，本 hack 每 tick 清零，无残留可恢复。唯一副作用是 `:117` 会吃掉创造模式双跳飞行（`LocalPlayer.java:754-763`），这是 hack 生效的必要条件，见「建议（未做）」2 |
| 方向解算与潜行 | `Flight.kt:34-41` `calcMoveYaw()` + `isInputting` | `FlightHack.java:118-122,124-132` 读 `player.input.forwardImpulse/leftImpulse` 与 `MC.options.keyShift.isDown()`，交给 `util/MovementPlanner.java:24-48` | 1.20.1 的 `Input` 才暴露 `forwardImpulse/leftImpulse`（1.12.2 用键位布尔），`x = -sin(yaw)`、`z = cos(yaw)` 的弧度约定与原版 `calcMoveRelativeSpeed` 一致；潜行 0.3 倍是参考没有的额外交互，保留 |

## 实际改动

无。

**撤回记录（避免下轮重复劳动）**：本轮曾把 `y == 0.0 && player.onGround()` 时的垂直注入改成"直接不动"，随后自检发现：`glide=0`（默认）时 `y` 本就等于 `0.0`，该分支写出的仍是 `setDeltaMovement(x, 0.0, z)`，**逐字节等价**，属简报第 9 条禁止的"等价换写法"。已把 `hacks/FlightHack.java` 完整还原为 189 行的原样（未提交、未编译），本轮不产出代码改动。

判定为**不是 bug**、但容易被误报成 bug 的一处留档：`glide=0` 贴地时每 tick 的"下陷约 8cm 再弹回"。证据链——`LivingEntity.travel:2216` 在 `:2210` 的 `d2 -= d0` 之后把 deltaY 乘 0.98，本 hack 写下的 `0.0` 因此变成 `-0.08`；`Entity.move` 的碰撞把 y 夹回方块顶面并重置 `fallDistance`（`Entity.java:1127-1137` 的 `resetFallDistance`）。所以它只是同一 tick 内的位置抖动，没有落地伤害、没有掉出 `onGround`、紧接的起跳不受影响。

## 故意不搬

- `Flight.kt:36-41` "无输入即 `motionX/Z = 0`"：本工程 `MovementPlanner.setHorizontal`（`util/MovementPlanner.java:44-49`）在无输入时已返回 `Vec3.ZERO`，而 `BOOST` 模式**:129-131 有意保留惯性，照抄会删掉 Boost 的既有语义。
- `Flight.kt:46-54` 的 `jump xor sneak`：本工程是"跳跃优先，否则潜行，否则 glide"（`FlightHack.java:136-137`）。两者只在"同时按住跳跃+潜行"时不同（参考→滑翔下沉，本工程→上升），属行为选择，改它等于重定义三个现有设置的语义（简报第 5 条）。
- `Flight.kt:57-63` 发假位置包：1.20.1 无该体系，本工程 anti-kick 改真实 delta，见上表。
- `Flight.kt:24-27` 的 Speed/Up/Down/Glide 滑块与 `:22` `Priority=500`：本工程 `horizontalSpeed/verticalSpeed/glide` 已覆盖同功能（默认值不同属各自设置），事件优先级由本工程 `EVENTS` 体系管理，无对应物。

## 建议（未做）

1. `Anti-Kick` 可加"仅离地时脉冲"：参考在 `Flight.kt:59` 有落地保护，本工程 `FlightHack.java:142-147` 无条件下压，贴地时这段脉冲会被碰撞吃掉、空转（无副作用）。改法只需 `&& !player.onGround()`，但这是反作弊口径问题、需实机验证，且无新旧行为差异可举证，故留给实机测试轮。
2. 创造模式双跳飞行失效（`FlightHack.java:117` 覆盖 `LocalPlayer.java:754-763`）是换取生存可飞的**必要代价**，不建议改；若要让创造玩家用原版双跳，应改用 `hacks/CreativeFlightHack.java:73-85` 的能力位路线，那是另一个 hack 的职责。
3. 若考虑"贴地时完全不注入"以消除抖动，需要同时冻结水平输入（否则贴地会漂移），等于把 `glide=0` 从"悬浮"改成"落地即停"，属重定义设置语义，未做。
