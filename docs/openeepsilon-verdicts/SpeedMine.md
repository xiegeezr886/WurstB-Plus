状态：已优化

## 对照证据

参考侧有 `player/SpeedMine.kt`（1.12.2 改 `PlayerControllerMP.blockHitDelay`）。
本工程 `hacks/SpeedMineHack.java`（85 行旧版）分 `Haste`/`OG` 两档。

## 实际改动 1：OG 模式（以及 Cooldown 滑块）是死代码

旧 `onUpdate()`：

```java
		if(mode.getSelected() == Mode.HASTE)
			MC.player.addEffect(...);
```

- `Mode.OG` 分支**不存在**：选 OG 之后每 tick 什么都不做，模块等于关闭。
- `cooldown` 滑块（1~4 tick，"Ticks between mining blocks."）在整个文件里**从未被读取**。

**反例（Rule 9）**：把 Mode 设为 OG（或把 Cooldown 调到 1），挖掘速度和没开模块完全一样。
已按设置的说明补上实现：

```java
		MC.gameMode.destroyDelay = cooldown.getValueI();
```

`destroyDelay` 是 `MultiPlayerGameMode` 的 **public** 字段（1.20.2 反编译源
`client/multiplayer/MultiPlayerGameMode.java:71`），原版在 `continueDestroyBlock()` 里用它做"两次挖掘
之间的间隔"：`:201-203` 只要 `> 0` 就直接 `return true`（这一 tick 不累积破坏进度），
破坏成功后 `:248` 把它重置为 5。把它按住到 1~4 就是"缩短挖掘间隔"。

## 实际改动 2：Haste 模式会清掉玩家真实的急迫

旧 `onDisable()` 无条件 `MC.player.removeEffect(MobEffects.DIG_SPEED)`。

**反例（Rule 9）**：站在信标（急迫 II）范围内开着 SpeedMine（Haste 模式），然后关掉模块 ——
客户端自己的急迫效果被一起清掉了（服务端要等效果状态变化才会重新同步，所以会一直缺着急迫）。
现在：

- 加效果前先看现有的急迫：已有的不弱于我们时**不覆盖**；
- 覆盖前把原效果存进 `previousHaste`；
- 关闭时只在**确实是我们加的**（`appliedHaste`）情况下移除，并把 `previousHaste` 放回去；
- OG 模式从不加效果，因此关闭时不会误删。

## 未做

- `Mode.OG` 在 1.20 的另一个可能语义是"取消挖掘时的攻击冷却"（1.8 的 `blockHitDelay`），
  本工程对应的就是 `destroyDelay`，已按此实现；没有实机验证实际挖掘速度变化。
