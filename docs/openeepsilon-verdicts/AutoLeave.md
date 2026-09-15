状态：已优化

## 对照证据

参考侧叫 `module/combat/AutoLog.kt`（136 行，功能等价：危险/低血时自动断开）。

| 关注点 | 参考（`AutoLog.kt:行`） | 本工程（`hacks/AutoLeaveHack.java:行`） | 判定 |
| --- | --- | --- | --- |
| 低血触发 | `:36,59` `health`（6..36，默认 10，按缩放生命） | `:94-97` `Health`（0.5..9.5 心，默认 4 心） | 等价（血量口径差异见下） |
| 图腾触发 | `:41-43,60,72-76` `Totem` + `Min Totems`（默认 2） | `:47-52,98-101` `Totems`（默认 11 = 忽略） | 等价（语义反过来：本工程是"图腾少于 N 才允许离开"） |
| 苦力怕触发 | `:38-40,62-64,83-91` `Creepers`（**默认 true**）+ `Creeper Distance`（默认 5） | 原来**没有** | 已补，默认 false（见「实际改动」） |
| 玩家触发 | `:44-47,65-67,93-104` `Players`（默认 false）+ `Player Distance`（默认 64）+ 好友开关 | 原来**没有** | 已补，默认 false |
| 水晶触发 | `:37,61,78-81` `Crystals`：用 `CombatManager.crystalMap` 的自身伤害估算 | 无 | 未搬（见下） |
| 断开后的提示 | `:106-115` 播放经验球音效 + 自定义 `GuiDisconnected` 界面（写明原因）+ `DisableMode` | `:112-126` `Mode`（Quit/Chars/SelfHurt，来自 Wurst）+ `Disable AutoReconnect` | 本工程提供的是"用什么方式离开"，参考提供的是"离开时怎么提示"，两者不冲突，提示部分未搬 |

## 实际改动

### 1. 补上参考的两个触发器：`Creepers` 与 `Players`

`isCreeperNear()`（`:139-148`）与 `isPlayerNear()`（`:154-170`）直接对应参考的
`checkCreeper()`（`AutoLog.kt:83-91`）与 `checkPlayers()`（`:93-104`），排除项照搬：
自己、假人、`antiBotHack.isBot()` 判定出的机器人、可选的好友。距离用平方比较（默认 5 / 64 格）。

**反例（Rule 9）**：旧版本 100% 只看血量。开着 AutoLeave、满血站在一个地方，一个敌对玩家走到 32 格内
（或一只苦力怕贴到脸上）→ 旧版本什么都不做，等你想起来的时候已经死了。这正是参考 `AutoLog` 的核心功能，
也是 V16_PLAN #9「AutoLeave：血量低于阈值自动断开」之外这类 hack 存在的理由。

**默认值刻意与参考不同**：参考 `Creepers` 默认 **true**，本工程两个都默认 **false**。理由是自动断开
误触发的代价很高（当场掉线、可能丢战斗），而这两个 hacks 之前是纯血量触发；多出一个默认打开的断线条件
会让老用户在没有任何改动的情况下突然掉线。想按参考默认就手动勾上，`creeperDistance` /
`playerDistance` / `Ignore friends` 三个设置只在对应开关打开时显示（`visibleWhen`）。

### 2. `onUpdate` 的控制流重排

```java
		float currentHealth = MC.player.getHealth();
		if(currentHealth <= 0F)
			return;

		boolean inDanger = currentHealth <= health.getValueF() * 2F
			|| creepers.isChecked() && isCreeperNear()
			|| players.isChecked() && isPlayerNear();
		if(!inDanger)
			return;

		// 图腾闸门（原样保留：图腾多于阈值时不离开）
		if(totems.getValueI() < 11 && InventoryUtils
			.count(Items.TOTEM_OF_UNDYING, 40, true) > totems.getValueI())
			return;
```

原来是一行 `if(currentHealth <= 0F || currentHealth > health.getValueF() * 2F) return;`。
把它拆开是因为新增的两个触发器与"是否死亡"无关：合并写法下"已经死了 + 旁边有苦力怕"会在死亡界面上
触发离开。图腾闸门现在对三个触发器统一生效（`Totems` 默认 11 = 忽略，所以默认不参与判断）。

## 未搬（如实记录）

- `Crystals`（`AutoLog.kt:78-81`）：参考用它的 `CombatManager.crystalMap` 里预计算好的"炸自己多少伤害"。
  本工程的水晶自身伤害估算在 `CrystalAura` / `AnchorAura` 内部（不属于共享 util），接进来要先把那部分
  抽成公共 util，属于新功能，本次没做。
- 提示音 + 自定义断线界面（`AutoLog.kt:106-115`）：需要新增一个 Screen 类和原因文案通道，属新功能。
- `DisableMode`（NEVER/ALWAYS/NOT_PLAYER，`:35,114`）：本工程是"离开后一律 `setEnabled(false)`"
  （`:127-128`），没有"保留开启"和"只因玩家触发才关闭"两档。
- `scaledHealth`（`:36,59`）：参考按最大生命缩放后比较，本工程用原始血量对应的"心数"滑条。
  原版最大生命 20 时两者数值一致；只有服务端改动最大生命（例如 40 点）时才会不同，未改。

## 建议（未做）

- `getRenderName()`（`:65-71`）会直接解引用 `MC.player`。目前只有在大厅/主菜单渲染 hack 名字时才会
  NPE，而这些路径在本工程里都要求已经进入世界，所以没有可举证的复现路径，未加保护。
