状态：已优化

## 参考侧核对

`_oe_ref/.../module/` 下没有对应模块（最接近的 `player/LagBackCheck.kt`、`misc/ClientSpoof.kt` 都不是"判断区块新旧"）。
故对照部分判「不适用」，主体是自审 + 一处真实修复。

## 本工程现状（证据表，行号为改动后）

`hacks/NewChunksHack.java`。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 判定原理 | `:222-232` 扫描区块整列，看到**流动**（非源方块）液体就判为老区块；`:226-229` 注释写明依据：“世界生成时液体总是静止的，流动只可能来自之后的方块更新” | 与 2b2t 系客户端的既有启发式一致，注释在案 |
| 新区块 | `:253-262` `afterUpdateBlock` 里看到方块更新产生了流动液体 → 记为新区块（并记原因坐标） | 需要 mixin 钩子喂事件 |
| 只查一次 | `:235-240` 整列扫完没发现流动液体 → 加进 `dontCheckAgain`；`:206-209` 开头的三重命中检查 | 避免同一区块被反复全列扫描 |
| 并发容器 | `:75-80` 五个 `ConcurrentHashMap.newKeySet()` | 扫描线程与主线程并发写是安全的 |
| 渲染 | `:155-185` 每 tick 重建 4 个缓冲（新/旧区块 + 原因），`drawDistance` 限制范围，`show`/`style` 决定画哪些 | 与 `NewChunksRenderer`/`NewChunksChunkRenderer` 分工清晰 |
| 线程模型 | `:197-204`，改动前是 `new Thread(...).start()` | **本次修掉**，见下 |

## 实际改动

**修掉"每个加载的区块开一个新线程"**。改动前：

```java
	public void afterLoadChunk(int x, int z)
	{
		if(!isEnabled())
			return;
		LevelChunk chunk = MC.level.getChunk(x, z);
		new Thread(() -> checkLoadedChunk(chunk), "NewChunks " + chunk.getPos())
			.start();
	}
```

现在改为 `onEnable` 建一个**有界、daemon** 的线程池（`:113-125`，大小 `min(4, 核数/2)`，至少 1），
`onDisable` 里 `shutdownNow()` 并置空（`:147-151`），`afterLoadChunk` 提交任务（`:197-204`，额外判空 `chunkCheckPool`）。

新旧行为差异（具体输入）：在末地以约 40 m/s 飞行，1 分钟大约加载 1000~1200 个区块，每个区块触发一次
`afterLoadChunk`：

- 旧：创建约 1000~1200 个线程（每个默认 1 MB 栈 ≈ 1 GB 以上虚拟内存承诺），线程的创建/销毁本身就是开销；
  而且这些线程**不是 daemon**，一旦扫描还没跑完就退出游戏，进程会被这些线程拖住（`checkLoadedChunk` 一个区块
  要扫约 3.8 万个位置，不会立刻结束）。
- 新：现场最多只有 `min(4, 核数/2)` 个常驻工作线程 + 一个任务队列；线程创建次数从"每个区块一次"降到"每局最多一次"，
  且线程是 daemon，退出游戏不受影响。

行为等价性：`checkLoadedChunk` 的判断逻辑一个字没改（每个区块最多检查一次、命中即写并发集合、无命中写
`dontCheckAgain`），任务顺序与原来一致；`MC.level.getChunk(x, z)` 仍然在**主线程**（mixin 回调里）取，与原来相同。

## 故意不搬

参考侧无对应模块。

## 建议（未做）

1. **已知的良性竞态（未修）**：`checkLoadedChunk:206-209` 与 `afterUpdateBlock:257-259` 都是"先查集合、再写集合"。
   极端情况下同一区块可以既进 `oldChunks` 又进 `newChunks`，画面上会同时出现两种颜色的层。根治办法是把五个集合
   换成 `ConcurrentHashMap<ChunkPos, 标记>` + `putIfAbsent`，但要一起改渲染侧的四个缓冲与原因集合，牵动面大，未做。
2. `checkLoadedChunk:210-242` 全列扫描（16×16×(最高非空段+16-最低段) ≈ 3.8 万位置/区块）没有做 section 级短路；
   `LevelChunkSection.maybeHas(...)`（只按调色板判断"这一段有没有可能含流动液体"）可以整段跳过。属优化，未做。
3. `:217` `maxY = 最高非空 section 偏移 + 16` 是刻意的截断（避免扫空气）；代价是"高空的人工水电梯"可能漏判。留档。
4. `:232,262` `logChunks` 勾选时用 `System.out.println` 输出调试信息：调试用途，保留。
5. 验证边界：只做了 `compileJava`/`compileTestJava`/`test`（全绿）；线程数变化与"新区块/老区块"判定的实际准确率
   都没有在实机/服务器上核对过。
