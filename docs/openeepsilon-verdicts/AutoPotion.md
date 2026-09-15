状态：已优化

## 对照证据

参考侧没有 AutoPotion：`module/combat/` 26 个模块里没有对应物，`AutoOffhand.kt`（自动把图腾/物品换到副手）
与 `AutoMend.kt`（经验修补）机制都不同。所以没有可搬实现，本项为自审 + 修一处会持续发包的问题。

本工程实现逐条核对：

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 只认治疗溅射药水 | `hacks/AutoPotionHack.java:103-119 findPotion` + `:121-133 hasEffect`：`Items.SPLASH_POTION` 且带 `MobEffects.HEAL` | 与设置/名称一致；再生药水不会触发，属取舍 |
| 投掷姿势 | `:80` `new Rotation(yaw, 90).sendPlayerLookPacket()`（朝正下方 90° 丢脚下），`:85-86` 投完把原 pitch 发回去 | 与 Wurst 原做法一致 |
| 触发阈值 | `:28-30,72` `Health`（默认 6 心，0.5..9.5） | 正常 |
| 冷却 | `:65-69,89` 10 tick 计时器 | 正常 |
| 背包→快捷栏 | `:94-100` shift-click | **有问题**，见下 |

## 实际改动：给"背包→快捷栏"那一步加空位判断

```java
		int potionInInventory = findPotion(9, 36);
		if(potionInInventory != -1)
			IMC.getInteractionManager().windowClick_QUICK_MOVE(potionInInventory);
```

**反例（Rule 9）**：快捷栏 9 格全满（剑、食物、方块……），主背包里放着一瓶治疗溅射药水。
旧实现每 tick 都满足 `potionInHotbar == -1` 且 `potionInInventory != -1`，于是：
**每 tick 发一次 `windowClick_QUICK_MOVE`（20 次/秒，无限持续）**，而
`InventoryMenu.quickMoveStack` 在快捷栏没有空位时不会移动任何东西（返回 `EMPTY`）——
药水永远进不了快捷栏，点击包也永远停不下来。这既是无意义的包量（很容易被反作弊盯上），
也会让服务器的容器状态每 tick 被点一次。

修法：新增 `hasEmptyHotbarSlot()`（`:107-115`），只有快捷栏确实有空位时才发那一下点击。
仍然每 tick 重试，所以一旦空出一个槽位就会立刻把药水移过来（没有引入新的延迟）。

## 未搬 / 说明

- 参考的 `AutoOffhand.kt` 是"把指定物品自动保持在副手"，与本模块（丢自己脚下的溅射药水）不是一回事，
  没有等价合并。
- 只认 `MobEffects.HEAL`：如果你想要"再生药水也能触发"，那是新增功能（还要考虑药水等级/持续时间），未做。

## 建议（未做）

- `timer` 只在快捷栏里已经有药水时才递减（`:65-69` 的判断在 `potionInHotbar != -1` 分支内），
  所以第一次投掷前最多可能多等 10 tick。无碍，未改。
- `findPotion(0, 9)` 每 tick 都重新扫描快捷栏，属于常量级开销，未优化。
