状态：不适用

## 对照证据

参考侧没有对应模块（参考没有"模板工具"这套状态机）。本项审计方式：**结构核对 + 全文模式扫描**，
未逐行精读全部 190 行。

| 关注点 | 结论 |
| --- | --- |
| 事件对称 | `:57-59` 加（Update/Render/GUIRender），`:65-67` 摘 —— 三个都成对 |
| 状态机 | 由 `hacks/…`（`TemplateToolState` 及其子状态 `ChooseNameState`/`CreatingTemplateState`/`ScanningAreaState`/`SelectBoxStartState`/`SelectBoxEndState`/`SelectOriginState`/`SelectPositionState`/`SavingFileState`）驱动 | 结构清晰，被 `AutoBuild`/`InstaBuild`/`Excavator`/`BowAimbot` 等多个模块复用（全仓库 50 处引用） |
| 未发现 | 未发现反射、未捕获异常、未受控线程、写死的世界边界常量、按键泄漏 |

无改动。

## 建议（未做）

- 只核对了本模块（事件/生命周期/设置项）与状态类的引用关系，状态类内部的交互流程未逐行审计；
  没有实机验证（模板选择、框选、保存都需要游戏内操作）。

## 第二轮精读（状态机框架与扫描/选点状态）

本轮读完：`TemplateToolHack.java` 全文的 `:41-120`（构造器/onEnable/onDisable/onUpdate/onRender/onRenderGUI/setState）、
`templatetool/TemplateToolState.java`（62 行）、`templatetool/SelectPositionState.java`（86 行）、
`templatetool/states/ScanningAreaState.java`（97 行），并把整个包的状态转移与容器清理画了出来。未发现可举证缺陷。

| 关注点 | 证据 | 结论 |
| --- | --- | --- |
| 开/关对称 | `:50-53` 开启时关掉 AutoBuild/InstaBuild/BowAimbot/Excavator；`:57-59` 加三个监听器，`:65-67` 全摘；`:69-76` 把 `state`/`startPos`/`endPos`/`originPos`/`nonEmptyBlocks`/`sortedBlocks`/`blockTypesEnabled`/`file` 全部复位 | 完整，重新开启是干净的新流程 |
| 状态机是单向的 | 全包只有 3 处 `setState(new …)`：`ScanningAreaState:61` → `SelectOriginState`、`CreatingTemplateState:101` → `ChooseNameState`、`ChooseNameState:100` → `SavingFileState`；其余靠各状态的 `getNextState()` 前进 | 不存在"回退重扫"的路径，所以 `nonEmptyBlocks` 不会残留上一轮的方块（这点是本轮重点怀疑对象，已排除） |
| 扫描范围 | `ScanningAreaState:37-42` 用 `|Δx|+1 / |Δy|+1 / |Δz|+1` 算 `totalBlocks`，迭代器来自 `BlockUtils.getAllInBox(start, end)` | 与"两端点都包含"的口径一致 |
| 每 tick 预算 | `:41` `clamp(total/30, 1, 1024)`；`:48` 先 `i < blocksPerTick` 再 `hasNext()` | 大范围不会卡帧，也不会越界取下一个 |
| 扫描内容 | `:54-55` 只把 `!state.canBeReplaced()` 的位置记入 `nonEmptyBlocks` | 与模板"只保存非空气/非可替换方块"的口径一致，也是 AutoBuild 那边 `canBeReplaced()` 判断的对偶 |
| 防御性检查 | `SavingFileState:76-81` 保存前逐条校验 `sortedBlocks` 与 `nonEmptyBlocks` 一致，不一致就报错 | 好设计，说明作者考虑过两个容器不同步 |

### 仍未逐行读的部分（如实记录）

`CreatingTemplateState.java`(135，按"每块至少贴着一个已排好的邻居"排建造顺序)、`ChooseNameState.java`(177，
聊天栏输入名字)、`SavingFileState.java`(136，写 JSON + 复制到剪贴板)、`SelectBoxStart/EndState`(60/63)、
`SelectOriginState.java`(40)。这些是交互/落盘逻辑；其中 `CreatingTemplateState` 的排序循环是唯一可能
"永远排不完"的地方（`:100` 只在 `sortedBlocks.size() == totalBlocks` 时前进），本轮没有足够预算逐行验证，
留待下一轮。
## 第三轮精读：`CreatingTemplateState.java`（建造顺序排序，135 行逐行）

上一轮标为「未读、唯一可能永远排不完的地方」，本轮逐行读完 —— **不会死循环**，无缺陷。

| 关注点 | 证据 | 结论 |
| --- | --- | --- |
| 会不会排不完 | Pass 1（`:52-58`）把 `nonEmptyBlocks` 全部搬进 `sortingHelper`，Pass 2（`:72-97`）每轮从 `sortingHelper` 取走一个放进 `sortedBlocks`；两个容器的元素总数守恒，`:100` 判 `sortedBlocks.size() == totalBlocks` | 必然终止，最坏情况就是慢（`:38` 每 tick `clamp(total/15,1,1024)` 个） |
| 排序规则 | `:43-45` 用「到 origin 的距离 + BlockPos 自身」做 TreeSet 比较器（确定性）；`:83-91` 只接受"六邻接里已经有排好的方块"的候选，并在候选中取离"上一次放入的方块"最近的那个（`:79-81`） | 这就是"建造顺序"想要的贪心：贴着已成型的部分往上长 |
| 结构不连通时 | 若没有任何候选贴着已排好的方块，`current` 保持 `sortingHelper.first()`（离 origin 最近的） | 有兜底，不会卡住；悬空的方块最后被排进去 |
| `last1024Added` 的作用 | `:79` 只读 `getLast()`（最近放入的），`:103-104` 裁到最多 1024 个，渲染时只画这批（`:125-126`） | 名字与用法一致，不会无界增长 |
| 单块 / 空选区 | `totalBlocks == 1`：第 1 tick 搬进 helper，第 2 tick 放进 sorted 后即满足 `size == totalBlocks`；`totalBlocks == 0`（选区内没有非空气方块）：Pass 1/2 都跳过，`:99` 算 `0/0f` 得 NaN（只影响扫描面的显示位置），`:100` 判 0 == 0 成立 ⇒ 直接进 `ChooseNameState` | 空选区会一路生成一个"零方块模板"，存盘后由 `AutoBuildTemplate.load()` 用 `JsonException("Template has no blocks!")` 拒掉（AutoBuild/InstaBuild 会提示并自我关闭）。行为可接受，只是没有在源头拦下，留档不改 |
## 第四轮精读：`SavingFileState.java`（存盘路径，136 行逐行）+ 一处实际改动

读完 `onEnter` / `createV2Json` / `createV1Json` / `toTemplatePos`，发现并修掉一处
"模板朝向取决于存盘那一刻的镜头"的问题。

### 实际改动：模板朝向改为「选 origin 时」的朝向

旧实现（`SavingFileState:72` 与 `:105`，v2/v1 各一处）：

```java
		Direction front = MC.player.getDirection();   // ← 存盘那一刻的朝向
```

模板坐标是"以 origin 为原点、按 `front` / `front.getCounterClockWise()` 旋转"存下来的
（`toTemplatePos():122-135` 做点积投影，与 `AutoBuildTemplate.BlockData.toBlockPos()` 互逆）。
但存盘发生在流程的最后（选 origin → 回车 → 排序若干 tick → 输入名字 → 存盘），
**中间排序阶段没有界面挡住视角，玩家可以自由转身**。

反例（Rule 9）：面向北选好 origin，在 "Creating template..." 那几 tick 里把镜头转到东 ——
存出来的模板整体转了 90°，与选 origin 时看到的布局不一致；AutoBuild 之后就会照着这个转过的布局建造。
现在改为 `TemplateToolHack.setOriginPos()` 里记下朝向（`templateFront` 字段），
`getTemplateFront()` 供两个 JSON 构造器使用（为空时回退到当前朝向），`onDisable` 里清空。

### 同一文件其余部分：无缺陷

- v1/v2 的口径与 `AutoBuildTemplate.loadV1/loadV2` 一一对应（v1 只存坐标、v2 存坐标+方块名）。
- `:78-81` 在 `sortedBlocks` 与 `nonEmptyBlocks` 不一致时抛 `IllegalStateException`：
  这是防御性断言，两个容器由 `CreatingTemplateState` 保证同步，构造不出反例 ⇒ 保留不改。
- `:43-53` 用 try-with-resources 写文件，失败时报错并自我关闭；`:56-62` 用可点击的 `OPEN_FILE`
  链接提示保存位置（中文提示）。