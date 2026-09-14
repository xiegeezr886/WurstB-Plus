状态：不适用

## 对照证据

参考侧有 `movement/Flight.kt`（1.12.2 的 packet fly / 飞行组合）。本工程 `CreativeFlight` 是
"让客户端以为自己能像创造模式一样飞"（服务端若按 `mayfly` 判定就会放行），机制完全不同。
本项为自审，全文读过 `hacks/CreativeFlightHack.java`（131 行）。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 开启飞行能力 | `:82-83` 每 tick `abilities.mayfly = true`（不直接置 `flying`，需要玩家自己双击空格） | 正常（更"像"创造模式） |
| 防踢（可选） | `:89-113` 每 `Anti-Kick Interval`（默认 30 tick）做一次 -d/+d/0 的下落抖动 | 正常 |
| 防踢与按键冲突 | `:98-100` 按住潜行且没按跳跃时跳过本周期；`:115-122` 抖动期间先把跳跃/潜行键置为未按下 | 正常 |
| 关闭时还原 | `:65-77` 把 `flying` 按"是否创造模式且不在空中"还原、`mayfly` 还原成创造模式的值，并 `restoreKeyPresses()`（`resetPressedState()`） | 正确 |
| 冲突组 | `:50` `HackConflictGroup.MOVEMENT_CONTROL` | 已登记 |

无改动（本行此前在覆盖表里是"待办"，代码已经是新写法，属重复列出）。
