状态：不适用

## 对照证据

参考侧有 `player/Nuker.kt`（1.12.2 的快速破坏）。本工程 `hacks/SpeedNukerHack.java`（110 行）
用 `CommonNukerSettings` 做公共筛选，本模块只负责"发破坏包"。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 目标筛选 | `:89-101` 取范围内、可点击、`commonSettings.shouldBreakBlock`、可选球形范围，按距离从近到远 | 正确（速度型从近到远，和 Kaboom 的从远到近是不同用途） |
| 范围 | `:86-87` `range.getValueSq()` / `getValueCeil()` | 正确（避免每次循环都取一次设置值） |
| `ID mode with air` | `:81-82` 该组合直接返回 | 正确（对着空气发破坏包没有意义） |
| 自动换工具 | `:106` `autoToolHack.equipIfEnabled(blocks.get(0))` | 正常 |
| 关闭 | `:69-76` 摘两个监听器 + `commonSettings.reset()` | 正确 |
| 冲突组 | `:50` `BLOCK_BREAKING_AUTOMATION` | 已登记 |
| 不存档 | `:34` `@DontSaveState` | 合理 |

无改动（`Nuker`/`NukerLegit`/`SpeedNuker` 三个模块共用 `CommonNukerSettings`，筛选逻辑只有一份实现）。
