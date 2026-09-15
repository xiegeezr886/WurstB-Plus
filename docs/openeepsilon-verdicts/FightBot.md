状态：已优化

## 对照证据

参考侧没有 FightBot：`module/combat/` 26 个模块里没有对应物。名字接近的都不等价 ——
`AimBot.kt` 只做瞄准（不寻路、不追击、不控制移动），`AutoLog.kt` 是危险时断开，`AntiBot.kt` 是把
疑似机器人排除出目标列表。本工程 FightBot 是"寻路追击 + 自动攻击"的战斗 bot，参考侧没有实现。
所以本项为自审。

本工程实现逐条核对：

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 目标选择 | `hacks/FightBotHack.java` 用 `EntityUtils` 共享过滤 + `EntityFilterList.genericCombat()` + `CombatTargetSession`（目标粘性、切换时才重置攻击计时） | 已是新版公共实现 |
| 转向 | `CombatRotationController(RotationQueue.Priority.COMBAT)`（静默转向，与 Killaura 同一套） | 已是新版公共实现 |
| 寻路 | 内嵌 `EntityPathFinder extends PathFinder`（`checkDone()` 用 `distance` 判定）+ `PathProcessor.lockControls()` | 正常，但重置条件难读，见下 |
| 冲突 | `addConflictGroup(COMBAT_TARGETING)` | 正常 |
| 强制按键 | 非 AI 模式下设置 `keyUp` / `keyShift` / `keyJump` | **有问题**，见下 |

## 实际改动 1：松开被强制按下的移动键（真 bug）

非 AI 模式下本 hack 会直接驱动移动键：`keyUp` 按"距离是否大于 `distance`"设置、飞行时按高度差
设置 `keyShift` / `keyJump`。但旧的 `onDisable` 只做 `EVENTS.remove(...)` + `suspendTargeting(false)`，
**从来不复位这些键**。（对照：`ProtectHack` 原来至少有 `keyUp.setDown(false)`，FightBot 一处都没有。）

**反例（Rule 9）**：开着 FightBot（`Use AI` 关掉），目标在 10 格外 → `keyUp` 被置为按下。
此时关闭 FightBot → 键状态停在"按下"，而原版每 tick 读的就是 `options.keyUp.isDown()`，
于是**角色自己继续往前走**，直到你敲一下 W（真正的键盘事件才会刷新该键状态）。
同理：打开箱子触发 `pauseOnContainers` 暂停（调用 `suspendTargeting`）时也停不下来；
飞行中控制高度用到的 `keyShift` / `keyJump` 会一直潜行 / 一直跳。

修法：新增 `releaseMovementKeys()`，在 `onDisable` 与 `suspendTargeting()` 里调用。用的是
`IKeyBinding.resetPressedState()` 而不是 `setDown(false)` —— 前者按玩家**真实**按键状态恢复
（`mixin/KeyBindingMixin.java:29-38` 直接查 GLFW），后者会把玩家此刻正按着的键一起取消。
因为它是"按真实状态恢复"，暂停路径每 tick 调用也不会把玩家的正常输入弄坏。

## 实际改动 2：把寻路重置条件抽成 `needsNewPath(target)`

原条件是一个靠运算符优先级才能读懂的长表达式：

```java
	if(pathFinder == null || pathFinder.entity != entity
		|| (processor == null || processor.isDone() || ticksProcessing >= 10
		|| !pathFinder.isPathStillValid(processor.getIndex()))
			&& (pathFinder.isDone() || pathFinder.isFailed()))
```

它等价于 `A || B || (C && D)`（`&&` 优先级高于 `||`）。抽成私有方法后语义**逐字保持**，
并在 javadoc 里写清意图：处理器已完成 / 超时 / 路径失效，**且**寻路器已完成或失败时才重来，
也就是"寻路器还在思考时不打断"（否则每 tick 都会重启一次搜索）。
`ProtectHack` 里有一模一样的表达式，同一处理（见 `Protect.md`）。

## 故意保留

`onEnable` 里 `WURST.getHax().tunnellerHack.setEnabled(false)`（保留）。理由已核实：
Tunneller 同样会直接按下移动键（`TunnellerHack.java:400` `keyUp.setDown(true)`、
`:455` / `:694` `keyShift.setDown(true)`），和 FightBot 会互相抢同一套输入；
而 `HackConflictGroup` 只有 COMBAT_TARGETING / MOVEMENT_CONTROL / BLOCK_BREAKING_AUTOMATION 三组
（`hack/HackConflictGroup.java`），没有"自动移动"这一类可用。
（附带更正一个我先前写过的说法：Tunneller **不**使用 `PathProcessor` 控制锁，
互斥的真实理由是上面这条输入抢占。）

## 顺带修掉的同类问题

同样是"强制按键不复位"，`FollowHack.onDisable`（`FollowHack.java:115-130`）也只调用了
`PathProcessor.releaseControls()`，没有复位它自己按下的 `keyUp` / `keyShift` / `keyJump`。
本次一并加上同样的 `releaseMovementKeys()`。FollowHack 不在 COMBAT 段，它的完整判定文档仍是待办，
修正在提交信息与本文件里都有记录。

## 建议（未做）

- FightBot 与 Protect 内嵌的 `EntityPathFinder` + `PathProcessor` + `ticksProcessing` 样板几乎重复
  （差别只是 FightBot 的距离固定取设置值、Protect 可按跟随/攻击切换）。可以抽成
  `util/` 下的共享类，属于跨模块重构，本次没做。
- `Use AI` 仍然标着 "experimental"，默认关闭；AI 关闭时纯靠"跳跃 + 强制前进"跟人，
  遇到墙只会跳，没有绕路能力 —— 与原设计一致，未改。
