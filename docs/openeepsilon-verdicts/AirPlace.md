状态：不适用

## 对照证据

参考侧有 `player/AirPlace.kt`（1.12.2 对着空气"放置"）。本工程 `hacks/AirPlaceHack.java`（131 行）
用"射线未命中方块时仍然构造一个 BlockHitResult 去右键"实现。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 取"未命中"的命中结果 | `:105-115` `MC.player.pick(range, 0, false)` 要求类型为 `MISS`，同时必须是 `BlockHitResult` | **核对后确认可行**：原版 `Level.clip()` 未命中时返回的是 `BlockHitResult.miss(...)`（仍是 `BlockHitResult`，带着射线经过的方块坐标），所以 `instanceof` 成立、`getBlockPos()` 可用 —— 这正是 AirPlace 的实现基础 |
| 频率限制 | `:77` 放置后 `MC.rightClickDelay = 4` | 正确（防手速刷包） |
| 手忙时不放置 | `:78-79` `isHandsBusy()` 判断 | 见"说明" |
| 指示框 | `:86-103` 只有勾选 `Guide`、手上拿着东西、且手不忙时才计算 | 正常 |
| 事件 | `:53-68` 加/摘三个监听器 | 平衡 |

无改动。

## 说明（共享观察）

- `:78`/`:97` 用的 `MC.player.isHandsBusy()` 在 1.20 里的真实含义是**"正在驾驶船并按了方向键"**
  （反编译源 `LocalPlayer.java:877-888`：`handsBusy` 只在 `rideTick()` 里、且只在受控载具是
  `Boat` 时被置位）。所以这两处的实际效果比注释写的"手忙"要窄（骑马/坐矿车时不算）。
  没有构造出可触发的反例（对 AirPlace 来说这只是少拦了一种情况），按"没有反例只记录"的规矩不改。
  同一个写法还出现在 `AutoMine`、`NukerLegit`、`Tillaura`、`BonemealAura`、`BuildRandom`
  （`BuildRandom` 那处因为直接对应一个设置项，已单独修复，见 `BuildRandom.md`）。
