状态：已优化

（参考侧无对应模块，主体是自审；本轮补了一处可举证的按键复位缺陷，见「实际改动」。）

## 参考侧核对

参考侧没有"自动进食"模块（`misc/AutoFish.kt` 是钓鱼、`combat/AutoMend`/`AutoOffhand` 是修装备/副手），
故判「不适用」，主体是自审。

## 本工程现状（证据表）

`hacks/AutoEatHack.java`。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 触发阈值 | `:48-63` `Target hunger`(默认 10) / `Min hunger`(6.5) / `Injured hunger`(默认值见 `:56-59`) / `Injury threshold` | 三档阈值：普通、重伤、以及"补到多少" |
| 饱食度单位换算 | `:140-142` 三个滑条的值 `*2` 后与 `FoodData.getFoodLevel()`（0..20）比较 | 滑条是"鸡腿数 / 2"的口径，换算正确（0..10 → 0..20） |
| 受伤判定 | `:322-326 isInjured()`：`health < maxHealth - injuryThreshold*2` | 与 `Injured hunger` 配合 |
| 选食物 | `:202-244 findBestFoodSlot(maxPoints)`：按 `FoodProperties::getSaturationModifier` 取最优；`:233` 跳过"营养超过还能吃下的量"（`maxPoints`）的食物，避免浪费；`:229-233` 过滤非食物 | 逻辑完整 |
| 食物白/黑名单 | `:272-289 isAllowedFood`：`:274` 合唱果（开关）、`:277-286` 检查 `food.getEffects()` 里的 `MobEffects.HUNGER`（开关）与 `POISON`（开关） | 与设置一一对应 |
| 取物来源 | `:65,208` `TakeItemsFrom`（`maxInvSlot`）决定搜索范围；`:210-216` 收集"当前格 / 副手(40) / 0..maxInvSlot" | 与设置对应 |
| 进食与复位 | `:163-200 eat()`：快捷栏格直接改 `inventory.selected` 并记 `oldSlot`、副手格(40) 不用换手、其它走 `InventoryUtils.selectItem`；`:265-270 stopEating()` 把 `selected` 复位并把 `oldSlot` 置 -1（`:291-293 isEating()` 基于它） | 换手/复位闭环完整，不会留下"吃完了手还在食物上"的状态 |
| 走路时进食 | `:73-75 eatWhileWalking` + `:296-320 isClickable(HitResult)` | 与设置对应 |
| 副作用防护 | `:126-138` 每 tick 先做前置判定再决定是否进食 | 不会在界面/死亡等状态下乱吃 |

## 实际改动：`stopEating()` 会按掉玩家真实按着的右键

旧实现（`:265-270`）：

```java
	private void stopEating()
	{
		MC.options.keyUse.setDown(false);
		MC.player.getInventory().selected = oldSlot;
	}
```

`setDown(boolean)` 只写"按键是否按下"这一个标记（`KeyMapping.setDown`），键盘**只在状态变化时**
才会重写它。`stopEating()` 既在吃到一半时调用，也在 `onDisable()` 里调用（`:onDisable` →
`if(isEating()) stopEating();`）。

**反例（Rule 9）**：玩家自己按着右键用盾/吃东西，同时 AutoEat 因为饥饿值够了自己结束进食 ——
`setDown(false)` 把按键标记改成"没按"，而玩家的手指还按着，于是他继续举盾/进食的动作被取消，
必须松手再按一次才恢复。这正是本工程 `mixinterface/IKeyBinding` 的 javadoc 里写明的区别
（`resetPressedState()` 按玩家真实按键恢复，`setDown(false)` 不管玩家是否真按着）。

改成按真实状态恢复：

```java
		net.wurstclient.mixinterface.IKeyBinding.get(MC.options.keyUse)
			.resetPressedState();
```

玩家没按右键时行为与旧实现完全一致（进食照常结束）；玩家真按着时不再被按掉。

## 建议（未做）

1. `:218-219` 用 `getSaturationModifier()` 单独排序：更稳的口径通常是"先看营养（能不能吃够）、再看饱和度"，
   而本工程已经用 `maxPoints` 把"吃不完的"过滤掉了，所以剩下的按饱和度挑是合理的；属调参，不改。
2. `:210-216` 每次进食都重新构造 `ArrayList<Integer>` 并 `Stream.iterate(...).forEach(...)`：每 tick 一次小分配，
   量级可忽略。留档。
3. 验证边界：零改动，只做静态核对；没有在"饥饿/中毒/合唱果"等边界条件下实机验证。
