状态：不适用

## 对照证据

参考侧没有对应模块。本项为自审。

## 自审记录

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 点头曲线 | `hacks/HeadRollHack.java:45-46`：`tickCount % 20 / 10F` → `sin(timer * π) * 90` | 一个 2 秒周期、0→90→0 的点头，角度合法 |
| 发包方式 | `:48` `new Rotation(getYRot(), pitch).sendPlayerLookPacket()` | 只发旋转包，本地视角不动 |
| 与同类模块互斥 | `:30-31` 开启时关闭 Derp / Tired | 一致 |

无改动。

## 建议（未做）

- 与 `Derp` 一样，每 tick 一个离散角度、无平滑；属模块用途，未改。
