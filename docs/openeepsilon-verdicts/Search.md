状态：不适用（参考侧无对应模块；本工程已是共享核心实现，零改动）

## 参考侧核对

`_oe_ref/.../module/` 下没有"按方块类型搜索并高亮"的模块（`render/` 24 个模块、`misc/` 21 个、
`combat/`/`player/`/`movement/` 全部核过一遍）。`render/Waypoint.kt` 是手工坐标点，
`render/BreakESP.kt` 是破坏进度，都与"扫区块找方块"无关。故判「不适用」，主体是自审。

## 本工程现状（自审）

`hacks/SearchHack.java`，256 行。**真正的扫描逻辑不在这个文件里**，而在共享的
`util/chunk/ChunkSearcher` + `ChunkSearcherCoordinator`（`CaveFinderHack`、`PortalEspHack` 用的是同一套，
其中 `PortalEspHack` 也走 `BiPredicate<BlockPos, BlockState>` 那个构造函数）。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 扫描线程 | `ChunkSearcher.java:29,36,47` 后台线程池 + `CompletableFuture` | 区块遍历不在 tick/渲染线程上 |
| tick 状态机 | `SearchHack.java:125-171` `coordinator.update()` → 未完成直接 `return`；`getMatchingBlocksTask`/`compileVerticesTask` 各自 `isDone()` 才推进；只有 `!bufferUpToDate` 才上传顶点 | 全程非阻塞，不会因为"匹配很多"卡住主线程 |
| 失效检测 | `:127-142` 目标方块变了、或 coordinator 报告区块变化 → `stopBuildingBuffer()`（取消两个任务并把 buffer 置脏） | 不会用过期数据渲染 |
| 结果裁剪 | `:208-218` 以眼睛位置为基准、按曼哈顿距离排序后 `limit(limit.getValueLog())` | 只画最近的 N 个（`Limit` 滑条） |
| 超限提示 | `:220-232` 命中数 ≥ Limit 时 `ChatUtils.warning` 一次（`notify` 标志），低于上限时重置标志 | 用户不会误以为"世界上只有这么多" |
| 渲染 | `:174-193` `depthFunc(GL_ALWAYS)`（穿墙）+ 彩虹色 + `RenderUtils.applyRegionalRenderOffset(matrixStack, bufferRegion)` | 顶点按**构建时**的 region 存、也用同一个 region 做偏移，两者配套，相机走远不会错位（顶点坐标始终相对 region 原点，浮点精度也不受影响） |
| 顶点生成 | `:234-235` `BlockVertexCompiler.compile(matchingBlocks)`（共享类，`CaveFinder` 复用） | 与其它 ESP 共用同一套"方块→可见面顶点"逻辑 |
| 生命周期 | `:91-122` `onEnable` 建 `ForkJoinPool`、重置 `prevLimit`/`notify`；`onDisable` 取消任务并 `close()` 顶点缓冲 | 资源释放完整 |

## 实际改动

无。没有可举证的新旧行为差异，按简报第 9 条不动代码。

## 建议（未做）

1. `:214-217` 先把**所有**匹配项 `sorted(comparator)` 再 `limit(...)`：`CaveFinder` 那种查询命中量可达
   数十万，全排序是 O(n log n)。改成"容量 limit 的最大堆"或先按曼哈顿距离粗筛能省一截。
   属纯性能优化（结果集合在 HashSet 里本就无序，渲染不受影响），未做。
2. `ChunkSearcher.java:68-76` 三轴遍历区域内**每个**位置（每个位置还 `new BlockPos`），没有跳过空 section
   （`LevelChunkSection.hasOnlyAir()` 之类的短路）。这是共享扫描器的优化，会同时惠及 `CaveFinder`/`PortalEsp`，
   但改动影响面较大，未做。
3. `:212` 用曼哈顿距离而不是欧氏距离做"最近"排序：只是排序口径，不影响正确性，未做。
4. 验证边界：零改动，只做了静态核对；扫描性能与渲染效果没有实机测量。
