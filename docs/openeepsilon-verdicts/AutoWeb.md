状态：已优化

## 对照证据

参考侧 `module/combat/AutoWeb.kt` **全文只有 3 行**：

```kotlin
package studio.coni.epsilon.module.combat

object AutoWeb
```

是个没有任何实现、也没有 `Module(...)` 构造的空壳（连分类都没有），所以这一项没有可搬实现，只能自审。

## 实际改动（3 处）

### 1. 目标选择改用共享的 `EntityUtils.IS_ATTACKABLE`

旧过滤链（`hacks/AutoWebHack.java` 原 `findTarget()`）：

```java
			.filter(e -> e instanceof LivingEntity && ((LivingEntity)e).getHealth() > 0)
			.filter(e -> e != MC.player)
			.filter(e -> !(e instanceof FakePlayerEntity))
			.filter(e -> !WURST.getFriends().contains(e.getScoreboardName()))
```

它漏了两件事：**AntiBot 的机器人判定**和**旁观者**。`EntityUtils.IS_ATTACKABLE`
（`util/EntityUtils.java:38-45`）已经包含"存活 / 不是自己 / 不是假人 / 不是好友 / 玩家要过 AntiBot"，
本工程其它 combat hack 全部用它。

**反例**：一个旁观模式玩家（或服务端放出来、AntiBot 判定为机器人的假玩家）站在 5 格内 →
旧版本会把他选成目标，然后对着他脚边与身位放两块蜘蛛网。你既看不见他，网还会挡住**你自己**的走位，
并白耗蜘蛛网。新版先过 `IS_ATTACKABLE`（排除 bot / 假人 / 好友 / 自己），再补一条 `!e.isSpectator()`。

### 2. `placeWeb` 的槽位恢复改成 try/finally

```java
		int oldSlot = MC.player.getInventory().selected;
		InventoryUtils.selectItem(Items.COBWEB);
		if(!MC.player.isHolding(Items.COBWEB))
			return;                       // ← 提前 return，最后那行 selected = oldSlot 不会执行
		...
		MC.player.getInventory().selected = oldSlot;
```

**反例**：`selectItem` 之后手上仍然不是蜘蛛网（例如数量刚好在这一 tick 被别的模块/服务器同步改掉），
旧代码直接 return ⇒ 快捷栏被留在别的槽位，你下一次攻击/放置用的就不是原本选中的物品，
而且面板上看不出任何异常。改成 `finally` 里无条件还原。

### 3. 删掉 `onEnable` 里对 KillAura 的静默关闭

```java
		WURST.getHax().killauraHack.setEnabled(false);   // 旧代码
```

**反例**：开着 KillAura，再打开 AutoWeb → KillAura 被改写成"关闭"**并保存进配置**，UI 上没有任何提示，
之后必须手动开回来。而同类"在目标位置放方块/放置"的 hack（`Surround`、`AutoTrap`、`SelfTrap`、
`AutoCity`、`HoleFiller`）都不这么做，都是可以和光环并存的。蜘蛛网 + 光环本来就是常见组合
（先网住再打）。参考侧是空壳，没有可对照的取舍，所以按本工程同类模块的既有做法处理。

## 故意不搬

参考没有实现，无内容可搬。参考把这个名字放在 `Category.Combat`，本工程同样在 COMBAT。

## 建议（未做）

- `placeTimer` 每 3 tick 尝试一次（约 6.7 次/秒），没有沿用原版 `rightClickDelay` 的 4 tick；
  与其它放置类 hack 的做法一致，没有可举证的错误，未改。
- `findTarget` 仍然没有接入 `EntityFilterList`（睡眠/隐身/飞行等过滤器）。要接就是给这个 hack
  新增一批 UI 设置，属于功能扩展，未做。
