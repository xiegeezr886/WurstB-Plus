状态：已优化

## 对照证据

参考侧没有对应模块（参考的 `player/NoVoid.kt` 是"掉到虚空前传送回来"，机制不同）。
本项为自审，全文读过 `hacks/AntiVoidHack.java`（54 行旧版）。

## 实际改动 1：跳跃键会被永久按下

旧实现（`:47-52`）在虚空里 `MC.options.keyJump.setDown(true)`，但**没有任何地方还原**。

**反例（Rule 9）**：掉进虚空触发一次 AntiVoid（跳跃键被强制按下）→ 被 `/tp` 或被服务器拉回地面
→ 关闭 AntiVoid。跳跃键仍然是"按下"状态（`isDown()` 为真与玩家真实输入无关），玩家落地后会
一直自动跳，直到手动按一次空格。已在 `onDisable` 和所有"不需要助推"的分支里补
`IKeyBinding.get(MC.options.keyJump).resetPressedState();`。

## 实际改动 2：`if(getY() > getBlockY() + 3) return;` 是死代码

`getBlockY()` 就是 `floor(getY())`（1.20.2 `Entity.getBlockY()`），所以
`getY() - getBlockY()` 恒在 `[0,1)`，这个条件**永远不成立**，那个 `return` 从来没执行过。
已删除，行为不变（并在注释里写明）。

## 实际改动 3：写死的 `-60`

旧实现：`if(MC.player.getY() > -60) MC.options.keyJump.setDown(true);`
-60 是主世界最低点 -64 的近似值，但下界/末地的最低点是 0。

**反例（Rule 9）**：同样是"距世界底部 1 格"的位置，主世界 y=-63 时 `-63 > -60` 为假 ⇒ 不按跳跃；
下界 y=1 时 `1 > -60` 为真 ⇒ 按跳跃。同一个模块在不同维度行为不一致。改成按
`MC.level.getMinBuildHeight()` 计算（主世界结果与原来完全相同）。

## 未做 / 建议

- `Only in hole`（默认开）的判定是 `getBlockY() > minBuildHeight + 10`，即"离世界底部超过 10 格就不生效"，
  与"是否在洞里"没有关系（代码里没有任何"洞"的判定）。改这一条要重新定义模块语义（是新功能，
  不是修 bug），本次只记录。
