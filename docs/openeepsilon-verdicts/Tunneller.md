状态：已优化

## 对照证据

参考侧没有对应模块（参考没有挖隧道机器人）。本项目文件 925 行，属 BLOCKS 里最大的模块。
本次审计方式是**全文模式扫描 + 生命周期与按键区段精读**（未逐行精读全部 925 行，下面写明覆盖范围）。

## 实际改动 1：强制按下的移动键从不复位

扫描全文件确认：这个模块**没有任何** `resetPressedState()`/`releaseControls()` 调用，但会强制按键：

| 位置 | 写入 |
| --- | --- |
| `:173`（旧） | 每 tick 把 前进/后退/左/右/跳跃/潜行 六个键 `setDown(false)` |
| `:400` | `keyUp.setDown(true)`（DigTunnelTask） |
| `:455`、`:694` | `keyShift.setDown(true)` |
| `:602`、`:611` | `forward.setDown(true)`（WalkForwardTask） |
| `:618` | `forward.setDown(false)` |

**反例（Rule 9）**：任务刚把前进键按下去（例如 `WalkForwardTask` 正在走），此时关掉 Tunneller ——
`onDisable` 只摘监听器、停挖、释放顶点缓冲，**没有任何一处松开按键**，角色会一直往前走，
直到玩家自己按一下 W 再松开。另一个反例：玩家正实体按着 W 时，`:173` 的 `setDown(false)` 会把
按键标记成"未按下"，而键盘只在**状态变化**时才会重新写这个标记，所以玩家明明按着 W 却不走，
必须松手重按。

修法（沿用工程里 AntiAim/Twerk/Vomit/Follow 的写法）：

- `:173` 改成 `IKeyBinding.get(binding).resetPressedState();`（按玩家真实按键状态恢复）；
- `:618`（走完最后一段、要停下时）同样改成 `resetPressedState()`；
- `onDisable` 里新增 `releaseMovementKeys()`，把六个控制键按真实状态复位；
- `:400`/`:455`/`:694` 等**主动按下**的地方保持不变（模块就是靠它们驱动移动）。

## 自审记录（覆盖到的部分）

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 开启时互斥 | `:108-111` 关闭 FightBot/Follow/InstantBunker/Protect | 合理 |
| 与世界交互的任务 | `:124-126` 任务表：躲液体、补地板、插火把、等落沙、挖隧道、前进 | 结构清晰（每个任务 `canRun()`/`run()`） |
| 关闭清理 | `:132-154` 摘监听 + `overlay.resetProgress()` + `stopDestroyBlock()` + 释放全部顶点缓冲 | 完整（本次补上了按键复位） |
| 危险方块/掉落物 | 扫描未发现未受控的线程、未捕获的异常、写死的世界边界常量 | 正常 |

## 未做 / 边界

- 未逐行核对 6 个任务内部的挖掘/寻路判定（`DodgeLiquidTask`/`FillInFloorTask`/`PlaceTorchTask`/
  `WaitForFallingBlocksTask`/`DigTunnelTask`/`WalkForwardTask` 的细节逻辑）。
- 没有实机验证（挖隧道、插火把、躲避液体都只能在游戏内观察）。
