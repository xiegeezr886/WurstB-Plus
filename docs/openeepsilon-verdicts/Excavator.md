状态：不适用（参考侧无对应模块；自审未找到可举证的行为差异，零改动）

## 参考侧核对

`_oe_ref` 里没有"区域挖掘"模块（`combat/AutoHoleFill`、`misc/AutoTool` 都不是）。
故判「不适用」，主体是自审。

## 本工程现状（证据表）

`hacks/ExcavatorHack.java`，约 550 行，是本工程最大的几个 hack 之一。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 设置 | `:49` `Range`(5，2..6)、`:52` `Mode`(`FAST` 为默认) | 两个设置 |
| 选区 | `:242-260 handlePositionSelection()`：用准星命中的方块设 pos1/pos2；`:234-241 enableWithArea(pos1, pos2)` 是给别的模块调用的入口 | 既可手动选，也可被 API 调起 |
| 选区扫描 | `:275-289 scanArea()`：每 tick 只从 `area.iterator` 取 `area.scanSpeed` 个位置，增量扫描 | 不阻塞 tick（与 `BaseFinder` 的"切片扫描"是同一思路） |
| 选区结构 | `:483-497` `Area` 内部类：`Iterator<BlockPos>` + `ArrayList blocksList` + `HashSet blocksSet` | 列表给渲染/选择用，集合给去重 |
| 目标排序 | `:309-322 excavate()`：先按 `BlockPos::getY` **倒序**（从上往下挖），再按距离 | 从上往下挖可以避免上方方块掉落后再挖一遍 |
| 可挖判定 | `:367` `pBreakable`（区分创造/生存）；`:422-425 getValidBlocks()` | 生存模式只挖挖得动的 |
| 寻路 | `:521-537 ExcavatorPathFinder extends PathFinder`（目标是即将挖的方块） | 够不到时会先走过去，而不是原地干等 |
| 步骤机 | `:90-110` 启停三个监听器（含 `GUIRenderListener`），关闭时遍历 `Step.values()` 复位；`:211-232 onRenderGUI` 在 HUD 上显示当前步骤；`:192-193` 用 `Step.SELECT_POSITION_STEPS` 画选点提示 | 状态机完整，用户能看见"现在处于哪一步" |

## 实际改动

无。没有可举证的新旧行为差异，按简报第 9 条不动代码。

## 建议（未做）

1. `:381` 用 `area.blocksList.parallelStream()` 找最近的方块：候选集可能上万，并行流在这个规模下有意义，
   但每次都重新对流排序/最小化；可以维护一个按 (y, 距离) 排序的优先队列，只在方块被挖掉时增量更新。
   属优化，未做（会改动 `Area` 的数据结构）。
2. `:275-289` 的 `scanSpeed` 与 `BaseFinder` 的"64 切片"是两种节流方案，本工程没有统一的"增量扫描"共享组件；
   如果要抽，先要确认 `SearchHack`/`CaveFinder`（走 `ChunkSearcher`）、`BaseFinder`、`Excavator` 三者能共用
   同一套接口。属重构建议，未做。
3. 验证边界：零改动，只做静态核对；挖大区域时的卡顿与寻路表现没有实机验证。
