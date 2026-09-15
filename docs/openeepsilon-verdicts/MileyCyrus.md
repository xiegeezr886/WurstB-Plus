状态：不适用

## 对照证据

参考侧没有对应模块（`movement/AutoJump.kt` 是自动跳跃，与"反复蹲起"不同）。本项为自审。

## 自审记录

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 蹲起节奏 | `hacks/MileyCyrusHack.java:52-58`：每 `11 - speed` tick 切换一次潜行键 | 速度 1..10 对应约 10..1 tick |
| 按键恢复 | `:46` `onDisable` 用 **`resetPressedState()`**（按玩家真实按键状态恢复） | 正确 —— 这是本工程里写法正确的样板，Twerk/Vomit 沿用同一修法 |

无改动。

## 建议（未做）

- 与 `Twerk`/`Vomit` 会互相抢 `keyShift`，没有互斥；三个都是玩笑模块，未加。
