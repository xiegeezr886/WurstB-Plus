状态：已优化

## 对照证据

参考侧没有 Protect：`module/combat/` 里名字接近的都不等价 —— `AntiAntiBurrow.kt` / `AutoBurrow.kt` /
`Burrow.kt` 是"把自己埋进方块 / 防被埋"，`AutoCev.kt` / `AntiCev.kt` 是晶体爆头防护，
都属于自保操作；参考侧没有"指定一个队友、跟着他并替他打人"的模块。所以本项为自审。

本工程实现逐条核对：

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| friend 来源 | `commands/ProtectCmd.java:35-56`（`.protect <名字>` → `setFriend(entity)` + `setEnabled(true)`）；没有指定时 `onEnable` 取最近的活体（排除自己 / 假人，`ProtectHack.java:133-151`） | 正常 |
| 敌我判定 | `:213-222`：6 格内（`distanceToSqr <= 36`）可攻击实体、排除 friend、再过 `EntityFilterList`（含 `FilterCrystals(true)` 等 24 项）、取最近；`:224-226` `target = enemy != null && friend 距离 < 24 ? enemy : friend` | 正常 |
| 攻击 | `:307-321`：只有 `target == enemy` 才攻击，且要过 AttackSpeed 计时与 `distanceE * distanceE` 距离检查；`:228-232` 换目标时重置计时 | 正常 |
| 冲突 | `addConflictGroup(COMBAT_TARGETING)` | 正常 |
| 强制按键 | 跟随/高度控制设置 `keyUp` / `keyShift` / `keyJump` | **有问题**，见下 |

## 实际改动 1：按键复位

旧 `onDisable`：

```java
		if(friend != null)
		{
			MC.options.keyUp.setDown(false);   // 只复位 keyUp，而且只在 friend != null 时
			friend = null;
		}
```

两个问题：

1. **只复位 `keyUp`**，而飞行高度控制用的是 `keyShift` / `keyJump`（`:291-293`），它们从来不复位
   → 关掉 Protect 后可能一直潜行 / 一直跳。
2. **用 `setDown(false)` 是错的工具**：它直接覆盖按键的按下状态，而不管玩家是否真的按着这个键。

**反例（Rule 9）**：按住 W 前进、同时开着 Protect，然后关闭 Protect → 旧实现执行
`keyUp.setDown(false)`，把玩家**真实按着**的 W 也取消了，表现为"手没松却走不动"，
要松开再按一次 W 才恢复。开着箱子触发 `pauseOnContainers`（走 `suspendAutomation()`）时同样不复位。

修法：新增 `releaseMovementKeys()`，用 `IKeyBinding.resetPressedState()`
（`mixin/KeyBindingMixin.java:29-38`：直接查 GLFW 得到该键的**真实**状态再 `setDown`），
在 `onDisable` 与 `suspendAutomation()` 里调用。因为恢复的是真实状态，暂停路径反复调用也不会
破坏玩家自己的输入；玩家真按着 W 时它在暂停后仍然是按下的。

## 实际改动 2：把寻路重置条件抽成 `needsNewPath(target)`

`ProtectHack.java` 与 `FightBotHack.java` 里有逐字相同的长表达式（`A || B || (C && D)`，
靠 `&&` 优先级高于 `||` 才能读懂）。抽成同名私有方法，语义逐字保持，javadoc 里写明意图
（处理器完成 / 超时 / 路径失效，**且**寻路器已完成或失败时才重来，避免打断还在思考的搜索）。
两个类各自持有私有方法，没有合并成公共 util（见建议）。

## 故意保留

`onEnable` 里的 `followHack.setEnabled(false)` 与 `tunnellerHack.setEnabled(false)`（`:129-130`）。
这套互斥在本工程里是网状的、方向一致的：`FollowHack:78-80` 会关掉 FightBot / Protect / Tunneller，
`TunnellerHack:111`、`FeedAuraHack:86` 会关掉 Protect。三个 hack 都会直接按下移动键
（`FollowHack:221-234`、`TunnellerHack:400/455/694`），功能也高度重叠，同时开启只会互相抢输入。
`HackConflictGroup` 只有三组（`hack/HackConflictGroup.java`），没有能表达"自动移动"的类别，
所以沿用现有做法、保持整网一致。（仍然要说明：这会写进配置，UI 无提示。）

## 已核实不是问题

- `friend` 取"最近的活体"看起来可能选中牛或僵尸，但 `.protect <name>` 会先 `setFriend()` 再启用
  （`ProtectCmd.java:55-56`），"取最近活体"只是没指定时的回退，属上游设计。
- 渲染名里读 `friend.getName()` 前有 `friend != null` 判断（`:112-118`），不会 NPE。

## 建议（未做）

- `distanceF = 2` / `distanceE = 3` 是硬编码字段（`:94-95`），没有设置项；FightBot 有
  `Distance` 滑条 + 提示"应小于 Range"。Protect 的攻击距离检查用的是 `distanceE`，
  想调只能改代码。是否补一个滑条属于功能新增，未做。
- 跟随转向仍用老接口 `WURST.getRotationFaker().faceVectorClient(...)`（`:253-254`、`:301-302`），
  而 FightBot / Killaura 等已迁移到 `CombatRotationController` + `RotationQueue`。
  统一是行为改动（静默转向的时机与优先级不同），且 `RotationFaker` 在工程里仍被 20 多处使用
  （BlockPlacer / BlockBreaker / Tunneller / Nuker …），本次未动。
