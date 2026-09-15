状态：不适用

## 对照证据

参考侧的 `movement/EntitySpeed.kt` 是水平提速；本工程 `BoatFly` 是"载具不落地 + 可控升降 + 可选水平速度"。
本项为自审，全文读过 `hacks/BoatFlyHack.java`（90 行）。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 悬停 | `:66-69` 默认 `motionY = 0` ⇒ 载具不受重力 | 正常（模块用途） |
| 升降 | `:71-75` 跳跃键上升；疾跑键保持当前高度 | 正常 |
| 水平 | `:77-85` 只有勾选 `Change Forward Speed` 才按朝向覆盖水平速度，否则保留原速度（平滑加速） | 正常 |
| 水平分量 | `:81-84` `x = sin(-yaw) * speed`、`z = cos(yaw) * speed` | 与 `VehicleBoostPolicy` 同一套朝向换算 |

无改动。

## 建议（未做）

- `:60` 只判 `isPassenger()`，不判载具类型：骑着马/猪/矿车时也会被"悬停"，模块名叫 BoatFly
  但实际对所有载具生效（`SearchTags` 里也确实带了 `EntitySpeed`）。属功能设计，未改。
- `onDisable` 不还原载具速度（速度是瞬时量，原版下一 tick 会按重力/摩擦自行恢复），未改。
