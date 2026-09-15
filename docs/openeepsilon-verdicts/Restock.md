状态：不适用（参考 `misc/Refill.kt` 是"快捷栏补货"，本工程实现是它的超集：多了物品名单、目标数量、副手、耐久处理）

## 对照证据

参考侧：`_oe_ref/src/main/kotlin/studio/coni/epsilon/module/misc/Refill.kt`（1.12.2，约 130 行，别名 `HotbarReplenish`）。

| 关注点 | 参考（Refill.kt:行） | 本工程（hacks/RestockHack.java:行） | 判定 |
| --- | --- | --- | --- |
| 设置 | `:16-19` `Type`(QuickMove/Pickup) / `Inventory Only` / `Threshold`(默认 32) / `TickDelay`(默认 2) | `:39-56` `Items`(`ItemListSetting`，**哪些物品要补**) / `Slot`(-1 = 当前格，0..8，9 = 副手) / `Restock amount`(目标数量) / `Repair mode`(耐久阈值) | 本工程可指定"补哪些物品、补到多少、补到哪一格"；参考只能按"数量低于阈值"泛化补货 |
| 选目标格 | `:71-86` 遍历快捷栏，找数量低于 `Threshold` 的格 | `:90-94` 由 `Slot` 决定：-1 用当前选中格、9 用副手（`OFFHAND_ID`）、其余用该快捷栏格 | 本工程更明确 |
| 找来源格 | `:88-104` 背包里找同种物品；`:107-124 isCompatibleStacks` 比 item / 方块 / damage | `:107-110,151-169 searchSlotsWithItem(itemName, slotToSkip)`，跳过目标格本身；`:171-172 itemEqual` | 两边都有；参考多比"方块与 damage"，本工程按物品名 + `ItemListSetting` |
| 点击方式 | `:38` `QUICK_MOVE`（shift 点击）；或 `:40-42` 三次 `PICKUP` 手动换格 | `:110-113,127-142` 用 `InventoryUtils.toNetworkSlot(...)` 换算后 `im.windowClick_PICKUP(...)` | 两边的 API 世代不同（`windowClick` → `windowClick_PICKUP`），语义一致 |
| 耐久 | 无 | `:145-149 isTooDamaged` + `Repair mode` 滑条参与挑选 | 本工程独有 |
| 节流 | `:19,26-27` `TickDelay`（默认 2 tick 一次操作） | 无节流：每 tick 都可以发一次点击 | 参考这条是**新增设置**，见「建议」1 |

## 实际改动

无。没有可举证的新旧行为差异，按简报第 9 条不动代码。

## 故意不搬

`Refill.kt:16-19` 的 `Type`（QuickMove/Pickup 二选一）与 `Inventory Only`：本工程已经是固定的 PICKUP 方案，
再加一套 QuickMove 分支属新增设置，不搬。

## 建议（未做）

1. **缺少点击节流**：参考有 `TickDelay`（默认 2），本工程每 tick 都能发一次 `windowClick_PICKUP`。本工程其它
   容器操作已经统一走 `util/inventory/InventoryActionQueue`（带提交时间、重试窗口、菜单 id 校验），只有这里没有。
   要改就是时序改动（并需要新增节流设置），未做。
2. `:110,113,139` 的槽位换算是 `InventoryUtils.toNetworkSlot(...)`；它与 `AutoEatHack:193` 的
   `InventoryUtils.selectItem(...)` 是同一个换算口径。**本轮没有单独核对这个换算函数本身**（只核对了两个调用点
   用法一致、没有混用"menu 索引"与"网络槽位"），留档。
3. 验证边界：零改动，只做静态核对；"补到指定数量/副手格"的实际效果没有实机验证。
