状态：不适用（参考侧 `movement/InventoryMove.kt` 的机制在 1.20.1 已由本工程用等价且更稳的方式实现，零改动）

## 对照证据

参考侧：`_oe_ref/src/main/kotlin/studio/coni/epsilon/module/movement/InventoryMove.kt`。

| 关注点 | 参考（InventoryMove.kt:行） | 本工程（hacks/InvWalkHack.java:行） | 判定 |
| --- | --- | --- | --- |
| 生效机制 | `:22,52` 在开界面时按条件重新同步按键状态（1.12.2 直接写 `keyBind.pressed`） | `:94-95` 对每个键调 `IKeyBinding.resetPressedState()`，其实现在 `mixin/KeyBindingMixin.java:29-38`：**从物理设备重新读取**（`InputConstants.isKeyDown(handle, code)` / 鼠标用 `glfwGetMouseButton`）再 `setDown(...)` | 同一思路（绕过"开界面时 `KeyMapping.releaseAll()` 把按键全放开"），本工程读的是真实输入源，更可靠 |
| 界面范围 | `:22,52` 只分"非聊天界面"与"聊天界面（由 `Chat` 开关放行）" | `:98-113` 分三类：① 背包类界面（`EffectRenderingInventoryScreen`，含创造背包，但**创造搜索栏打开时不生效** `:100-102,115-122`）；② Wurst 自己的界面（`allowClickGUI`）；③ 其它容器界面（`allowOther`，但**界面里有文本框时不生效** `:108-110,124-128`） | 本工程的判定细得多，且刻意避开"打字时人还往前走" |
| 允许的键 | 未区分按键（只有 `Sneak` 开关） | `:81-92` WASD 恒放行；潜行/疾跑/跳跃各一个开关（`allowSneak`/`allowSprint`/`allowJump`，默认 true） | 本工程更细；**刻意不碰** `keyAttack`/`keyUse`，所以在界面里不会误挖/误放 |
| 转头 | `:17-18,76` `PitchSpeed`/`YawSpeed`（在界面里用键转头） | 无 | 1.20.1 的视角由鼠标处理、界面里本就可以自由转视角；要"用键转头"得新增设置，不搬 |
| 聊天 | `:16` `Chat` 开关 | 不在放行范围（聊天不是容器界面） | 参考能"边打字边走"，本工程不能；加开关属新增设置，见「建议」 |

## 实际改动

无。两边的做法在语义上等价（把按键状态重新同步为物理真实状态），没有可举证的新旧行为差异，按简报第 9 条不动代码。

## 故意不搬

1. `InventoryMove.kt:17-18,76` 的 `PitchSpeed`/`YawSpeed` 与 `:16` 的 `Chat` 开关：都要新增设置项（简报第 5 条），且 1.20.1 的视角/聊天行为与 1.12.2 不同，不搬。
2. 直接写 `keyBind.pressed` 的写法：本工程走 `IKeyBinding` 接口 + mixin，读的是物理设备状态，不搬旧写法。

## 建议（未做）

1. 聊天界面里不能行走：如果用户确实需要"边打字边走"，最小做法是把 `ChatScreen` 也纳入放行范围（而不是加开关）。这会改变"打字时人物会动"的既有默认行为，属行为变更，未做。
2. `:81-83` 每 tick 都新建一个 `ArrayList`：一次分配/帧，量级可忽略；改成静态数组反而要处理"加了潜行键后长度变化"，未做。
3. 验证边界：零改动；只做了静态核对，没有在实机里验证"开箱子界面里能正常走位、打字时不会乱走"。
