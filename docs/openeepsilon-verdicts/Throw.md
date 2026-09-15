状态：不适用

## 对照证据

参考侧没有对应模块（参考没有"一次点击用很多次"的连点器）。本项为自审，全文读过
`hacks/ThrowHack.java`（71 行）。

## 自审记录

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 触发 | `:50-56` `RightClickListener` + 原版 `rightClickDelay > 0` 提前返回 + 必须真的按着右键 | 正常（不会凭空发包） |
| 行为 | `:58-69` 循环 `Amount` 次：命中方块先 `rightClickBlock`，再 `rightClickItem` | 见下 |
| 关闭 | `:44-47` 摘监听 | 正常 |
| 设置范围 | `:20-21` 2..1000000 | 极端值会在一 tick 内发上百万次交互，属模块用途（"卡服"式玩笑），未改 |

无改动。

## 建议（未做，两处）

1. `:60-66` 与原版 `Minecraft.startUseItem()` 的 BLOCK 分支不一致：原版在
   `useItemOn` 返回 `consumesAction()` 时会**停止**（不再尝试用物品），这里无论是否消耗都继续
   `rightClickItem()`。反例是"对着箱子右键并且手上拿着可放置方块"时会同时发出 `USE_ITEM_ON` 与
   `USE_ITEM` 两个动作（箱子被打开 + 方块被放下），而原版只会发前者。
   本次未改，因为这与本模块"疯狂连点"的用途纠缠在一起，改了会改变它的既有手感；
   如果要改，正确做法是读 `rightClickBlock` 的返回值（`IClientPlayerInteractionManager.java:30`
   返回 `InteractionResult`），消耗了就 `continue`。
2. `:60` 没有判空 `MC.hitResult`：注入点
   （`mixin/MinecraftClientMixin.java:104-117`，`startUseItem()` 里对 `rightClickDelay` 的第一次访问）
   在原版的 `hitResult != null` 检查**之前**，理论上可能为 null 而 NPE。
   本次没有构造出可达反例（`hitResult` 每帧由 `pick()` 更新，进入游戏后基本不会为 null），
   所以只记录、按"无实据不改"的规矩不动。
