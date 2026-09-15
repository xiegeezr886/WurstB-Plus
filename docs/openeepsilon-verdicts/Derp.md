状态：不适用

## 对照证据

参考侧没有对应模块（`player/AntiAim.kt` 是反瞄准，`misc/SkinBlinker.kt` 是皮肤层闪烁，
都不是"随机转头"）。本项为自审。

## 自审记录

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 随机朝向范围 | `hacks/DerpHack.java:48-49`：yaw 相对当前 ±180、pitch 在 ±90 内 | 都是合法角度 |
| 发包方式 | `:51` `new Rotation(yaw, pitch).sendPlayerLookPacket()` | 只发旋转包，本地视角不动（与 Tired/HeadRoll 一致） |
| 与同类模块互斥 | `:33-34` 开启时关闭 HeadRoll / Tired | 三者都在改同一个"渲染/发送朝向"，互斥合理（`HackConflictGroup` 没有对应类别） |

无改动。

## 建议（未做）

- 每 tick 一个固定朝向（没有平滑），服务器端看到的是 20Hz 随机跳变；这是模块本身的用途，未改。
- 与 `AntiAim` 模式叠加时会互相覆盖朝向（Derp 发旋转包、AntiAim 改真实视角），没有互斥。未做。
