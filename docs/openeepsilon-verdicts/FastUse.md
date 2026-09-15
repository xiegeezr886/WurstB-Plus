状态：已优化

## 参考侧核对

参考侧没有对应模块（`misc/` 里没有 FastUse 一类的东西，`misc/AutoFish.kt`、`combat/AutoOffhand.kt` 都不同）。
故对照部分判「不适用」，改动来自自审。

## 本工程现状（证据表）

`hacks/FastUseHack.java`。

| 关注点 | 位置（改动前行号） | 结论 |
| --- | --- | --- |
| 设置 | `:30-43` `Mode`(Single/Multi)、`Multi`(1..100，默认 20)、`Throwables only`(默认开)、`XP only`(默认关) | 四个设置，后两个是**各自独立**的过滤开关 |
| Single 模式 | `:77` `MC.rightClickDelay = 0` | 去掉原版右键使用间隔（`Minecraft.rightClickDelay` 是原版字段，投掷物/药水靠它做节流） |
| Multi 模式 | `:79-84` 按住右键时每 tick 调 `MC.startUseItem()` 共 `Multi` 次 | 就是"一 tick 投 N 个" |
| 可投掷物名单 | `:25-28` 雪球 / 鸡蛋 / **经验瓶** / 末影之眼 / 末影珍珠 / 喷溅药水 / 滞留药水 | 名单里本来就含经验瓶 |

## 实际改动

`onUpdate()` 里的两个过滤开关原来是**嵌套**的：

```java
		if(throwablesOnly.isChecked()
			&& !(THROWABLE.contains(MC.player.getMainHandItem().getItem())
				&& (!xpOnly.isChecked()
					|| MC.player.getMainHandItem()
						.getItem() == Items.EXPERIENCE_BOTTLE)))
			return;
```

即 `xpOnly` 只在 `throwablesOnly` 为真时才会被求值。改成两个并列条件：

```java
		Item heldItem = MC.player.getMainHandItem().getItem();
		if(throwablesOnly.isChecked() && !THROWABLE.contains(heldItem))
			return;
		if(xpOnly.isChecked() && heldItem != Items.EXPERIENCE_BOTTLE)
			return;
```

新旧行为差异（具体输入）：`Throwables only = 关`、`XP only = 开`、手里拿的是**方块**、`Mode = Single`、按住右键：

- 旧：`throwablesOnly.isChecked()` 为 false，整个 `if` 短路 → 直接执行 `MC.rightClickDelay = 0`；Multi 模式下更会
  `MC.startUseItem()` 20 次。也就是"只勾 XP only"与"两个都不勾"**完全等价**，与 `XP only` 的描述
  （"Only fast-uses XP bottles"）矛盾，手里的方块、食物都会被加速使用。
- 新：第二个条件命中 → 直接 `return`，什么都不做。

顺带：`THROWABLE`（`:26`）本来就包含 `Items.EXPERIENCE_BOTTLE`，所以旧代码里
`|| getItem() == Items.EXPERIENCE_BOTTLE` 这半个条件在 `throwablesOnly` 分支内永远为真、纯属冗余；拆开后自然消失。

## 故意不搬

参考侧无对应模块。

## 建议（未做）

1. 两个开关**同时勾选**时的语义现在是"交集"（只有经验瓶生效）。如果本意是"经验瓶即使不在可投掷物名单里也允许"，
   那应该是并集——属产品决策，本轮按"两个独立过滤器"的直白语义实现，未另行改动。
2. `:83` Multi 模式一 tick 调用 `startUseItem()` 达 100 次：这是刻意的（"一秒投 20 个"），但会在一 tick 内产生
   大量 `ServerboundUseItemPacket`。加节流属新增设置，未做。
3. 验证边界：只做了 `compileJava`/`compileTestJava`/`test`（全绿）；实际"只勾 XP only 时不再乱用物品"没有连服验证。
