状态：不适用（参考侧无对应模块；自审未发现可举证的行为差异，零改动）

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

## 实际改动

无。没有可举证的新旧行为差异，按简报第 9 条不动代码。

## 建议（未做）

1. `:218-219` 用 `getSaturationModifier()` 单独排序：更稳的口径通常是"先看营养（能不能吃够）、再看饱和度"，
   而本工程已经用 `maxPoints` 把"吃不完的"过滤掉了，所以剩下的按饱和度挑是合理的；属调参，不改。
2. `:210-216` 每次进食都重新构造 `ArrayList<Integer>` 并 `Stream.iterate(...).forEach(...)`：每 tick 一次小分配，
   量级可忽略。留档。
3. 验证边界：零改动，只做静态核对；没有在"饥饿/中毒/合唱果"等边界条件下实机验证。
