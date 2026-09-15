状态：不适用（参考侧无对应模块；自审未找到可举证的行为差异，零改动）

## 参考侧核对

`_oe_ref` 里没有"连锁挖矿脉"的模块（`combat/`、`misc/`、`movement/`、`render/`、`player/` 目录全核过）。
故判「不适用」，主体是自审。

## 本工程现状（证据表）

`hacks/VeinMinerHack.java`，246 行。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 设置 | `:52` `Range`(5)、`:54` `Flat mode`、`:58` `NukerMultiIdListSetting`（**哪些方块算同一条矿脉**）、`:60` `SwingHandSetting`、`:68` `Max vein size`、`:72` `Check line of sight` | 六个设置，其中"矿脉成分"是可编辑的方块 id 列表 |
| 触发 | `:194-206` 左键点击时：`currentVein` 非空 → 忽略（正在挖）；点击的方块不在 `multiIdList` 里 → 忽略；否则 `buildVein(点击位置)` | 只在"手动点中矿脉方块"时才开始，不会自己乱开 |
| 建脉 | `:209-234` BFS：`ArrayDeque` 队列 + `currentVein` 集合，`:218` 循环条件带 `currentVein.size() < maxSize`，`:222` 遍历六个方向 | 有明确上限，不会把整片石头都算进来 |
| 失效修剪 | `:118-121` 每 tick `currentVein.removeIf(pos -> BlockUtils.getState(pos).canBeReplaced())` | 挖掉的位置自动退出矿脉（`canBeReplaced` 语义见 `HoleEsp.md` 的同一条核对） |
| 只挖矿脉内 | `:173-181` `shouldBreakBlock(pos) → currentVein.contains(pos)` → `breakOneBlock(...)` | 不会溢出到矿脉外的方块 |
| 渲染 | `:236-243` `currentVein.stream().map(pos -> BLOCK_BOX.move(pos)).toList()` | 直接按集合画 |

## 实际改动

无。没有可举证的新旧行为差异，按简报第 9 条不动代码。

## 建议（未做）

1. `:222-229` 的邻居扩展：`for(Direction direction : Direction.values())` 是六方向，`Flat mode` 的作用点在
   紧随其后的条件里（`:225-229`）——**本轮没有逐行读那段条件**，所以不对 `Flat mode` 的实际语义下结论。
   如果要确认，应补一次针对性核对。
2. `:68 Max vein size` 的默认值与上限：上限越大，一次 BFS 的目标越多（共享 `BlockBreakingCache` 会一起变大）。
   属调参，未动。
3. 验证边界：零改动，只做静态核对；连锁挖掘的实际表现没有实机验证。
