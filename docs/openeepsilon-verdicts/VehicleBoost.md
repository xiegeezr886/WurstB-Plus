状态：不适用

## 对照证据

参考侧的 `movement/EntitySpeed.kt` 是持续加速载具；本工程 `VehicleBoost` 是"下车瞬间获得一个初速度"
（Boost 类），机制不同。本项为自审，全文读过 `hacks/VehicleBoostHack.java`（74 行）。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 触发 | `:68-71` 上一 tick 在载具上、这一 tick 不在 ⇒ 按当前朝向给一个速度 | 正常 |
| 数值 | `util/VehicleBoostPolicy.velocity(yaw, 水平, 垂直)`（纯类，可单测） | 正常 |
| 换世界/重生 | `:59-64` `player != trackedPlayer` 时重新记录并跳过一 tick | 正常（不会用旧玩家的状态误判） |
| 关闭 | `:47-53` 摘监听并清空引用 | 正常 |

无改动（本行此前在覆盖表里是"待办"，代码已经是抽出 `*Policy` 的新写法，属重复列出）。
