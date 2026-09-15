状态：不适用（参考 `movement/Scaffold.kt` 是 1.12.2 的反作弊绕过版；本工程已把放置/转向抽成共享的 planner + 队列，核心差异属"故意不做"）

## 对照证据

参考侧：`_oe_ref/src/main/kotlin/studio/coni/epsilon/module/movement/Scaffold.kt`（约 290 行，1.12.2 / NCP+Hypixel 时代）。

| 关注点 | 参考（Scaffold.kt:行） | 本工程（hacks/ScaffoldWalkHack.java:行） | 判定 |
| --- | --- | --- | --- |
| 设置 | `:37-49` Mode / Desync Placement / Delay / Smooth Rotation / YawSpeed / PitchSpeed / SafeWalk / Swing / Tower / Place Timeout / Rotation Timeout / KeepRotation / Render Block | `:42-77` Mode / Place delay / Silent swap / Search range / Prediction / Require line of sight / Safe walk / Tower motion / Swing hand | 两边都成体系；本工程的 `Prediction`（按速度预测落点）、`Require line of sight` 是参考没有的，参考的平滑转向参数在本工程由共享的 `RotationQueue` 统一处理 |
| 选放置点 | `:252-281 find()`：玩家脚下开始、六面 + 8 个水平外扩偏移里挑，用 `:235-250 rayTrace`（10 步采样）做视线检查 | `:166` `ScaffoldPlacementPlanner.find(predictedPosition, ...)`、`:192` 塔用 `findAt(...)`；planner 是 `util` 下的**纯逻辑类**（本工程共有 20 余个这类 `*Planner`/`*Policy`） | 本工程把选择逻辑抽成可单测的纯类；参考是内联实现 |
| 转向 | `:97-127` 自己算目标角 + Hypixel 模式做逐帧插值；`:145` 用 `prevRotation.y == 82.545698881269785867f` 这种**写死的浮点特征值**判断"上一 tick 是否已在 Hypixel 的合法角度上" | `:295 rotationQueue.setRotation(plan.rotation())`，队列带优先级 `RotationQueue.Priority.BLOCK_PLACEMENT`，`onEnable/onDisable` 起停（`:111-125`） | 本工程统一走转向队列；参考那条魔数是针对特定反作弊的特征，不搬 |
| 客户端侧放块 | `:174` `world.setBlockState(blockData.position, blockState)`：**只在客户端放**（`Desync Placement`），制造幽灵方块来"看起来像放下了" | 无 | 1.20.1 上这样做会留下真·幽灵方块并与后续移动/挖掘不同步（服务端没有这个方块）；本工程刻意不做，属正确取舍 |
| 塔 | `:111` 按住跳、且没有斜向移动时放块；上升靠外部 Tower 逻辑 | `:188-212 doTower()` + `:214-222 updateTowerMotion()`：`player.setDeltaMovement(movement.x, towerMotion.getValue(), movement.z)`，`towerMotion` 是 0.2..1.0 的滑条 | 本工程把"每 tick 给的上升速度"做成显式设置，语义比参考清楚；`:146` 只有在快捷栏确实有方块时才起塔 |
| 潜行/安全走 | 无独立设置：`:135-137,163` 只在遇到黑名单方块时临时改 `isSneaking` | `:69 safeWalk` 独立开关（与 `SafeWalkHack` 同样的语义） | 本工程更清楚 |
| 换手 | `:155-176` 直接操作 `EntityEquipmentSlot.MAINHAND` 的当前物品 | `:225-284 findBlockSlot/swapToSlot/resetSlot`，`silentSwap` 决定是否用静默换手 | 两边的"从快捷栏找方块"都在自己文件里；本工程多了静默换手开关 |

## 实际改动

无。参考侧能搬的只剩两类：1.12.2 的客户端侧幽灵方块（本工程刻意不做）、针对 Hypixel 的写死角度特征（属
反作弊对抗，不是算法改进）。没有可举证的新旧行为差异，按简报第 9 条不动代码。

## 故意不搬

1. `Scaffold.kt:174` 的 `world.setBlockState(...)` 客户端侧放块（见上表）。
2. `Scaffold.kt:145` 的 `82.545698881269785867f` 角度特征、`:41-42` 的 Yaw/Pitch 平滑速度：前者是特定反作弊的
   绕过特征，后者是 1.12.2 逐帧插值的产物（本工程由 `RotationQueue` 的插值统一负责）。
3. `Scaffold.kt:37` 的 `Mode.NCP/Hypixel` 枚举值：本工程的 `Mode` 语义不同（搭桥/塔），不搬。

## 建议（未做）

1. `:255-284` 的换手是"直接改 `inventory.selected`"（可能还有静默换手包）：若服务端在同一 tick 内对背包做校验，
   与 `AutoToolHack` 是同一类风险点。本工程已有 `util/inventory/InventoryActionQueue`，但换到队列上会改动
   Scaffold 的时序（搭桥对延迟极敏感），未做。
2. `:199-212` 的扫描范围（`Search range`）默认值是否覆盖"边跳边搭"的所有落点，本轮没有做数值核对（属调参，
   不影响正确性）。留档。
3. 验证边界：零改动；搭桥手感、`Prediction` 的命中率都没有实机验证。
