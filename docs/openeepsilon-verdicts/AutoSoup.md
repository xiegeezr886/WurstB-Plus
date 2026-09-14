状态：已优化

## 对照证据

参考侧没有 AutoSoup：`module/combat/` 里 `AutoMend.kt`（经验修补）、`AutoOffhand.kt`（自动副手）
机制都不同。所以没有可搬实现，本项为自审 + 一处小修。

本工程实现逐条核对：

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 空碗整理 | `hacks/AutoSoupHack.java:64-85`：把 0..35 里的空碗用 2~3 次 `PICKUP` 点击集中到主背包第 9 槽，并把该槽里的非碗物品换回原位 | 槽位映射正确（容器 0 的 36..44 = 快捷栏，9..35 = 主背包；`i < 9 ? 36 + i : i`） |
| 吃汤条件 | `:137-148 shouldEatSoup()`：血量 ≤ 阈值（默认 6.5 心）且准星没对着"可点击对象" | 正常 |
| 吃汤动作 | `:105-110` 切到汤所在槽 + `keyUse.setDown(true)` + `rightClickItem()` | 与 Wurst 原做法一致 |
| 收尾 | `:176-188 stopIfEating()`：松开使用键、恢复原槽位、`oldSlot = -1` | 正常 |

**已核实不是问题**（避免以后重复排查）：`windowClick_PICKUP` / `windowClick_QUICK_MOVE` 固定使用
containerId **0**（`mixin/ClientPlayerInteractionManagerMixin.java:103-115`），所以开着箱子/工作台时这些
点击作用在**玩家自己的背包**上，不会误动容器里的物品（客户端界面可能显示不同步，但不会丢东西）。

## 实际改动：`isClickable` 用参数而不是字段

```java
		if(hitResult instanceof EntityHitResult)
			Entity entity = ((EntityHitResult)MC.hitResult).getEntity();   // ← 旧代码
```

方法签名是 `isClickable(HitResult hitResult)`，但两处解引用（`:157` 的实体分支、`:164` 的方块分支）
写的是字段 `MC.hitResult`。目前唯一调用点是 `isClickable(MC.hitResult)`，所以**没有可观察的行为差异**，
但这是"参数被忽略"的隐患：以后若从别处传入别的 `HitResult`，判断结果会莫名其妙地对不上。
已改为使用参数 `hitResult`（`:157,164`）。

## 故意保留

`onEnable` 里的 `WURST.getHax().autoEatHack.setEnabled(false)`（`:50`）**保留**，理由与 AutoWeb 那次
删掉 `killauraHack.setEnabled(false)` 不同：

- AutoEat 与 AutoSoup 是真正互斥的：两者都会"切换手持物品 → 按住使用键"，同时开着会每 tick 互相抢槽位
  与按键，谁都吃不成。
- `HackConflictGroup` 目前只有 COMBAT_TARGETING / MOVEMENT_CONTROL / BLOCK_BREAKING_AUTOMATION 三组
  （`hack/HackConflictGroup.java:3-8`），没有"进食"这一类，所以没有更规范的机制可用。

（仍然要说明：这条会写进配置，用户下次进游戏 AutoEat 仍是关闭状态，UI 上不会有提示。）

## 建议（未做）

- `stopIfEating()` 里的 `MC.options.keyUse.setDown(false)` 会覆盖玩家真实的按键状态（你正按着右键时，
  得松开再按才能恢复响应）。Wurst 原设计（AutoEat 同样如此），未改。
- 空碗整理每 tick 都会跑一遍：只在"有碗不在第 9 槽"时才发包，收敛后自然停止，未加节流。
- `stack == null`（`:69`）是死判断（`Inventory.getItem` 返回 AIR，不会是 null），属于无害冗余，未动。
