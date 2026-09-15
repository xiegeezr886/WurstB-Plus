状态：已优化

## 对照证据

参考侧没有对应模块（参考没有跟随寻路）。本项为自审（本轮复核了 `hacks/FollowHack.java` 的
`onEnable`/`onDisable`/`onUpdate` 与按键相关路径；文件现为 296 行）。

## 早前轮次已做的改动（本次复核确认仍在）

`:143-148` 新增 `releaseMovementKeys()`，并在 `onDisable` 里调用（`:125`）。

**反例（Rule 9）**：非 AI 模式下 `onUpdate` 会直接设置 `keyUp`（`:252`）与 `keyShift`/`keyJump`
（`:239-241`）。旧 `onDisable` 只调用 `PathProcessor.releaseControls()`，而该方法虽然是按真实
按键状态还原的（`ai/PathProcessor.java:75-80`），但它是在"AI 模式用过 `lockControls()`"的语义下写的；
非 AI 模式下角色会保持最后一次的前进/潜行/跳跃输入 —— 关掉 Follow 之后人还在往前走。
（注：`PathProcessor.CONTROLS` 实际上也包含 keyUp/keyShift/keyJump，所以这条调用在当前代码下是
**冗余**的；保留它是为了让"关掉 Follow 必须复位这三个键"这件事不依赖于另一个类的实现细节。）

`:194-201` 把原来的 `A || B || (C && D)` 拆成了 `needsNewPath(...)` 之类的可读条件（同一轮次），
逻辑未变但不再需要对着括号数。

## 本轮自审记录

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 目标筛选 | `:85-99` 排除已移除、死亡、自己、`FakePlayerEntity`，再套 `FollowFilterList` | 正常 |
| 目标消失后按名字找替代 | `:163-189` 用原名匹配（忽略大小写）最近的同类实体 | 正常 |
| 玩家死亡 | `:154-160` 自动关闭 | 正常 |
| AI 模式 | `:191-219` `PathProcessor.lockControls()` + `think()`/`formatPath()`/`getProcessor()`/`process()` | 与 AntiAfk 同一套；`processor` 为 null 时一定先经过 `:204` 赋值，没有 AntiAfk 那处括号隐患 |
| 非 AI 模式 | `:220-253` 撞墙跳、水中上浮、飞行时配高、按距离决定是否按前进 | 正常 |
| 关闭时清理 | `:115-132` 摘监听、清空 pathFinder/processor/entity、`releaseControls()`、`releaseMovementKeys()` | 正常 |

无改动（本轮未新增代码改动）。
