状态：不适用

## 对照证据

参考侧有 `player/AutoMine.kt`（1.12.2 自动挖准星方块）。本工程 `hacks/AutoMineHack.java`（115 行）
实现相同，并额外有 `Super fast mode`（决定是否取消原版的破坏包）。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 关掉后复位 | `:56-57` `IKeyBinding.get(keyAttack).resetPressedState()` + `stopDestroyBlock()` | **正确**（原版键状态按玩家真实按键恢复，不会把玩家自己按着的左键吃掉） |
| 避免重复发包 | `:108-114` `onHandleBlockBreaking` 在非 superFast 时取消原版破坏，由本模块自己调 `start/continueDestroyBlock` | 正确 |
| 空值/边界 | `:74-89` 命中结果为空、非方块、空气方块都先 `stopDestroyBlock()` 再返回 | 正确 |
| 与 autoTool 的配合 | `:91` `autoToolHack.equipIfEnabled(pos)` | 正常 |
| 用物品时不打断 | `:93-95` `isUsingItem()` 时返回（原版这种情况不会取消挖掘） | 正确 |
| 冲突组 | `:40` `BLOCK_BREAKING_AUTOMATION` | 已登记 |

无改动。

## 说明（共享观察）

- `:68` 的 `MC.player.isHandsBusy()` 在 1.20 里只表示"驾驶船并按了方向键"
  （`LocalPlayer.java:877-888`），比注释的"手忙"要窄；没有构造出可触发的反例，只记录。
