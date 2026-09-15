状态：不适用（参考侧无对应模块；自审未发现可举证的行为差异，零改动）

## 参考侧核对

参考侧没有"一键搬空容器 / 一键存入"的模块。`misc/Refill.kt` 是"快捷栏补货"（见 `Restock.md`），
`combat/AutoTrap`/`AutoWeb` 是放置方块，都与容器搬运无关。故判「不适用」，主体是自审。

## 本工程现状（证据表）

`hacks/AutoStealHack.java`，108 行（另有 `GenericContainerScreenMixin` / `ShulkerBoxScreenMixin` 往界面里加按钮，
见 `:107` 注释）。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 设置 | `:27-36` `Delay`（点击间隔）、`Steal/Store buttons`、`Reverse steal order` | 三个设置，语义清楚 |
| 取物品 | `:49-51` `steal(screen, rows)`：点第 `0 .. rows*9` 个槽，`steal = true` | 只动容器侧的行 |
| 存物品 | `:54-56` `store(screen, rows)`：点第 `rows*9 .. rows*9+36` 个槽，`steal = false` | 只动玩家背包侧 |
| 反向顺序 | `:79` `Collections.reverse(slots)` | `Reverse steal order` 生效 |
| 点击 | `:81-96` 逐格 `screen.slotClicked(slot, slot.index, 0, ClickType.QUICK_MOVE)`，空槽跳过（`:84-85`），每格之间按 `Delay` 睡 | `slot.index` 是 menu 内的槽位 id，与 `handleInventoryMouseClick` 需要的 slotId 语义一致，这里没有"menu 索引 / 网络槽位"混用 |
| 执行线程 | `:65` 每次按钮点击 `new Thread(() -> shiftClickSlots(...), "AutoSteal").start()` | 见「建议」1 |
| 按钮可见性 | `:102-105 areButtonsVisible()` | 由 `Steal/Store buttons` 控制 |

## 实际改动

无。逐条核对没有找到可举证的新旧行为差异，按简报第 9 条不动代码。

## 建议（未做）

1. **点击发生在后台线程（按简报第 9 条本轮不据此改代码）**：`:65` 每次按钮点击新建一个线程，`:92` 在该线程上调用
   `screen.slotClicked(...)`，而它会一路走到 `MultiPlayerGameMode.handleInventoryMouseClick`（发包 + 改客户端 menu）；
   参考侧所有容器点击都在主线程 tick 里做（`Scaffold.kt` 里到处是 `runSafe { }`）。潜在后果是断开连接后线程仍在
   点击（`MC.gameMode` 为 null 时 NPE）以及渲染线程同时读 menu。**但这属于竞态，本轮给不出确定性的反例输入**
   （构造不出"必然出错"的输入），所以只记录、不改。真要改，正确做法是把 `Delay` 变成 tick 上的延时计数
   （本工程 `util/inventory/InventoryActionQueue` 已有现成模式），顺带去掉每次点击的线程创建。
2. `:75-76` 把 menu 的全部 `Slot` 对象先 `toList()` 再遍历：点击期间槽位内容会变，但 `Slot` 对象本身不变，
   所以不存在"读到过期槽位"的问题；仅是每次点击一次小分配，量级可忽略。留档。
3. 验证边界：零改动，只做静态核对；没有连服务器验证过大规模搬运。
