状态：不适用

## 对照证据

参考侧有 `player/Nuker.kt` 的合法模式。本工程 `hacks/NukerLegitHack.java`（205 行）用"瞄准+逐步挖掘"
的方式做合法挖掘，并额外画挖掘进度。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 关掉后复位 | `:92-97` `resetPressedState()`（左键）+ `stopDestroyBlock()` + `overlay.resetProgress()` + 清 `currentBlock` + `commonSettings.reset()` | 完整 |
| 只在合法距离内 | `:123-125` 非球形模式用 `MC.gameMode.getPickRange() + 1` 作为上限 | 正确（合法模式不能超距） |
| 视线要求 | `:132` `BlockBreakingParams::lineOfSight` | 正确（合法模式必须看得见） |
| 与准星一致才挖 | `:155-171` 要求 `MC.hitResult` 是方块、且方块与面都和本次候选一致，否则 `stopDestroyBlock()` | 正确（避免"挖的和瞄的不是同一个"） |
| 重复发包 | `:192-198` 只在 `currentBlock != null` 时取消原版破坏 | 正确（没在挖时把控制权还给原版） |
| 按键 | `:186` 需要时 `setDown(true)`；`:93`/`:143` 两处 `resetPressedState()` | 正确 |
| 用物品时不打断 | `:175-177` | 正确 |
| 冲突组 | `:63` | 已登记 |

无改动。

## 说明（共享观察）

- `:114` 的 `isHandsBusy()` 在 1.20 里只表示"驾驶船并按方向键"（`LocalPlayer.java:877-888`），
  比注释窄；没有构造出可触发的反例，只记录。
