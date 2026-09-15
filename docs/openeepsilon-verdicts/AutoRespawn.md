状态：已优化

## 对照证据

| 关注点 | 参考（`module/misc/AutoRespawn.kt:行`） | 本工程（`hacks/AutoRespawnHack.java:行`） | 判定 |
| --- | --- | --- | --- |
| 触发点 | `:21-22` `GuiEvent.Displayed` 且 `it.screen is GuiGameOver` | `:44-52 onDeath`，事件由 `mixin/DeathScreenMixin.java:31-35` 在死亡界面**每 tick** 触发（`@At("TAIL") method="tick()V"`） | 等价（参考是"界面显示时"，本工程是"界面存续期间每 tick"，对本功能同义） |
| 自动重生 | `:16 respawn`（默认 true） | 原来只有 hack 总开关，开着就必定重生 | 已补 `Respawn` 设置 |
| 假死界面 | `:18,29` `antiGlitchScreen && player.health > 0` → 也重生并关界面 | 原来没有 | 已补 `Anti glitch screen` 设置 |
| 死亡坐标 | `:17,24-27` `Save Death Coords` → 聊天里打印死亡坐标 | 无 | 未搬（见下） |
| 死亡界面按钮 | 无 | `:20-22` + `DeathScreenMixin:37-54` 的"自动重生: 关闭"按钮 | 本工程独有 |

## 实际改动

按参考 `AutoRespawn.kt:29-32` 的条件补两个设置并短路：

```java
		if(!respawn.isChecked() && !(antiGlitchScreen.isChecked()
			&& MC.player.getHealth() > 0))
			return;

		respawnNow();
```

**反例（Rule 9）**：旧版本无法表达"关掉自动重生、但假死界面仍要关掉"这个状态——
hack 关掉就什么都不做，开着就必定 `respawn()`。服务端回滚/假死（死亡界面出现了而 `getHealth() > 0`）
时，旧版本只有"完全不管"这一条路。新版本在这个状态下仍然会 `respawn() + setScreen(null)`，
与参考一致。

同时把无条件重生抽成 `respawnNow()`，`mixin/DeathScreenMixin.java:52` 的死亡界面按钮改调它——
否则新加的 `Respawn=false` 会把按钮也一起挡掉（那条路径上用户是主动点的按钮）。
`onDeath()` 被 `DeathScreenMixin:31-35` 每 tick 调用，所以条件判断是持续的，不是只有一帧。

## 故意不搬

- `Save Death Coords`（`AutoRespawn.kt:24-27`）：参考只是一句 `ChatUtil.printChatMessage(...)`，
  本工程要新开一条聊天输出通道 + 死亡位置快照，属于新功能而不是修复，未做。
  需要的话建议做成独立的 `DeathCoords` 功能，不要塞进 AutoRespawn。
- 参考把它放在 `Category.Misc`：本工程沿用 `Category.COMBAT`，属分类差异，不改（改了会让老用户找不到）。

## 建议（未做）

- 参考的 `respawn` 与 `antiGlitchScreen` 是"任一满足即重生"；本工程照搬了这个语义。
  如果你觉得"关掉 Respawn 时连 respawn 包也不该发"（只关界面），那需要把
  `respawnNow()` 拆成"关界面"和"发重生包"两步，属行为取舍，未做。
