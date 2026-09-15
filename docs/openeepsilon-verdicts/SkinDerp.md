状态：已优化

## 对照证据

参考侧有对应模块 `module/misc/SkinBlinker.kt`（126 行，"Toggle your skin layers rapidly"）。

| 关注点 | 参考（`SkinBlinker.kt:行`） | 本工程（改前） | 判定 |
| --- | --- | --- | --- |
| 逐层开关 | `:17-23,91-124` Cape/Jacket/左右袖/左右裤腿/Hat 七个复选框 + `enabledParts` 集合 | 无设置，一次切换全部 7 层 | 未搬（见下） |
| 闪烁模式 | `:24,27-31,78-88` HORIZONTAL / VERTICAL / RANDOM，每次只切换**一层**，按固定顺序推进 | 无，永远"全部一起切" | 已补 |
| 间隔 | `:25,76` `Delay` 毫秒（默认 10ms），`TickTimer` 判定 | 写死"每 tick 有 1/4 概率"（平均 4 tick） | 已补 `Delay` |
| 关闭时恢复 | `:68-72` 把所有层重新打开 | `onDisable` 同样把所有层打开 | 等价（保留） |

## 实际改动：补上参考的闪烁模式与间隔设置

- 新增 `Mode`（`ALL` / `HORIZONTAL` / `VERTICAL` / `RANDOM`），默认 `ALL` = 旧行为（一次切换全部层），
  保证老用户行为不变。
- 新增 `Delay`（1..20 tick，默认 4 tick ≈ 旧实现的平均间隔）。
- `HORIZONTAL_ORDER` / `VERTICAL_ORDER` 的顺序逐条照搬参考（`SkinBlinker.kt:42-59`）。

反例（Rule 9，说明这不是纯口味问题）：旧实现每 tick 有 25% 概率把**全部**皮肤层一起切换，
玩家会在"完整皮肤 / 只剩头（其他层全关）"两个状态之间闪，速度不可调、也不像参考那样
一层一层扫过去；参考的实现每次只动一层、按固定顺序推进，视觉上是流动的。

## 未搬 / 建议（未做）

- 参考的七个"参与闪烁的层"复选框没有搬（`enabledParts` 那套）：本工程 `Mode=ALL` 时全部层参与，
  `HORIZONTAL`/`VERTICAL` 按参考顺序逐层扫。要精确复刻可以参考的复选框集合，属新增 UI，未做。
- 参考用毫秒计时（默认 10ms ≈ 每 tick 一次），本工程用 tick 计数（原版逻辑帧），更符合客户端节拍。
