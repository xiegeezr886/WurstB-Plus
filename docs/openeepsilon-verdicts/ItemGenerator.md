状态：已优化

## 对照证据

参考侧没有 ITEMS 类模块（`_oe_ref` 的 `module/` 只有 client / combat / misc / movement / player /
render / setting 七类，共 110 个模块），与创造模式物品生成相关的只有 `misc/Crasher.kt`
（发崩溃包）与 `misc/FakePlayer.kt`，没有"随机生成物品并丢出去"的实现。本项为自审。

## 自审记录

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 创造模式检查 | `hacks/ItemGeneratorHack.java:63-67` | **缺 return**，见下 |
| 生成随机物品 | `:69-82` 逐个槽位 `BuiltInRegistries.ITEM.getRandom` + `InventoryUtils.setCreativeStack` | 正常 |
| 丢出物品 | `:84-85` 对同一批槽位发 `windowClick_THROW` | 正常 |
| 警告文案 | `:28-30` 明确写了高速度会卡顿/崩溃（默认 1 = 每 tick 一个槽位） | 正常 |

## 实际改动：非创造模式下缺 `return`

```java
		if(!MC.player.getAbilities().instabuild)
		{
			ChatUtils.error("仅限创造模式。");
			setEnabled(false);          // ← 旧实现到此为止
		}
		
		int stacks = speed.getValueI();     // 下面照常执行
		...
		for(int i = 9; i < 9 + stacks; i++)
			IMC.getInteractionManager().windowClick_THROW(i);   // 真发包
```

同工程的 `KillPotionHack:44-49`、`TrollPotionHack:42-47`、`CrashChestHack:33-38` 在同样的检查后
都写了 `return;`，只有这里漏了。

**反例（Rule 9）**：生存模式下把物品放在主背包第一格（容器槽位 9），然后开启 ItemGenerator。
旧实现：提示"仅限创造模式"、调用 `setEnabled(false)`，但**本次 `onUpdate` 继续执行**——
`setEnabled(false)` 只是把监听器从事件表里摘掉，当前这次 `UpdateEvent` 分发已经进了本方法。
于是同一 tick 里仍然向服务端发出 `windowClick_THROW(9)`：服务端按**它自己**的槽位内容处理，
把你在那一格真实存在的物品丢了出去（客户端 `setCreativeStack` 生成的那份假物品只在本地，
服务端并不知道）。`Speed` 调高时丢掉的格子更多。

修法：补上 `return;`（`:63-72`）。

## 未搬 / 建议（未做）

- `Speed` 上限 36（一次处理整个主背包）会在一 tick 内发出最多 36 个点击包；模块自己的提示已声明风险，
  默认值 1，未改。
- 参考侧没有对应实现，不存在"照参考补齐"的空间。
