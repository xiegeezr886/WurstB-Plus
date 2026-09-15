状态：已优化

## 对照证据

参考侧没有独立的右键连点模块：它把右键连点放在 `module/combat/AutoClicker.kt` 里
（`:26-31 rightValue` / `blockOnly`，`:83-95` 的右键分支），实现方式是 `KeyBinding.onTick(keyUse)`
——也就是**模拟原版按键**，会走原版 `rightClickDelay` 的门槛。本工程是直接做交互
（`util/InteractionSimulator`），更接近"发包 + 真实交互"的做法。

| 关注点 | 参考（`AutoClicker.kt:行`） | 本工程 | 判定 |
| --- | --- | --- | --- |
| 触发方式 | `:83-95` `KeyBinding.onTick`，要求按键按下 | `hacks/RightClickerHack.java:110` `Hold to click`（默认 true）可选；关掉就纯发包 | 本工程更灵活 |
| CPS | `:26-27,101-103` min/max + 随机延迟 | `:30-33` min/max CPS + `AttackTimerPolicy.delayNanos` + `Mth.lerp` 随机 | 等价 |
| 停止条件 | `:88` `!player.isHandActive`（正在用物品就不点） | `:93` `MC.player.isUsingItem()` | 等价 |
| 物品门槛 | `:31,83-85` `blockOnly`：手拿方块时**不**右键 | `:38-39` `Only with item`：手上有物品才点 | 语义不同（本工程更直观），参考的 `blockOnly` 未搬 |
| 实体交互 | 走原版按键，所以挤奶/剪羊毛/村民交易都正常 | **原来缺失**（见下） | 已修 |

## 实际改动：补上"瞄着实体"那条分支

旧代码只区分两种命中：

```java
		if(MC.hitResult instanceof BlockHitResult blockHit
			&& blockHit.getType() == HitResult.Type.BLOCK)
			InteractionSimulator.rightClickBlock(blockHit, swingHand.getSelected());
		else
			InteractionSimulator.rightClickItem(swingHand.getSelected());   // ← 实体也会落到这里
```

`InteractionSimulator` 自称"准确复刻 `Minecraft#startUseItem()`"（`util/InteractionSimulator.java:18-26`），
但它只有方块分支和物品分支，**没有 `case ENTITY` 分支**；原版那条分支是先
`gameMode.interactAt(...)`、没被消费再 `gameMode.interact(...)`（1.20.2 反编译源
`Minecraft.java:1739-1753`）。所以`MC.hitResult` 是 `EntityHitResult` 时，旧实现发的是
`useItem` 包 —— 等价于"对着空气使用物品"。

**反例（Rule 9）**：关掉 `Hold to click`（让 RightClicker 自己负责点击），手拿空桶瞄准一头牛。
原版右键会把牛奶进桶；RightClicker 落到 else 分支，只发 `ServerboundUseItemPacket`，
服务端执行 `BucketItem.use`（对空气）什么也不会发生 —— 牛永远不会被挤奶。剪羊毛、拴绳、
村民交易、给动物喂食同理。（打开 `Hold to click` 时你自己的按键会把这件事做掉，
所以这条只在"完全由模块点击"的模式下必现；实体命中时的额外点击本身也都是无效包。）

修法：`util/InteractionSimulator.java` 新增 `rightClickEntity(EntityHitResult, SwingHand)`
（按原版顺序：世界边界检查 → 依次两只手 `interactAt` → `interact` → 被消费才挥手 →
都没消费再试同一只手的 `useItem`），`RightClickerHack:96-108` 增加 `EntityHitResult` 分支。
没有改动 `InteractionSimulator` 已有的方法，其它调用方不受影响。

## 故意不搬

- 参考的 `blockOnly`（`:31,83-85`）：手拿方块时完全不自动右键。本工程已经有
  `Only with item`（手上有物品才点）与 `Hold to click`，再加一个"拿方块就不点"只会让行为更难预测。
- 参考的 `JitterClick`（随机 ±1° 抖动）属于反作弊规避，见 `KillauraLegit.md` 里同样的判断。

## 建议（未做）

- 本工程不设置原版 `rightClickDelay`（`InteractionSimulator` 的 javadoc 明确把这一步留给调用方），
  所以理论上可以以任意 CPS 连点。默认 7..13 CPS 已经是正常人手速，未改。
- `scheduleNextClick` 用 `Mth.lerp(random.nextDouble(), low, high)`，与
  `AttackSpeedSliderSetting` 的随机化口径不完全一致（后者带 ±ms 抖动参数），两者都可用，未统一。
