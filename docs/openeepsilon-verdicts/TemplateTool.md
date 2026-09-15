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