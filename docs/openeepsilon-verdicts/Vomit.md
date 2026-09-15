状态：已优化

## 对照证据

参考侧没有对应模块。本项为自审 + 一处按键恢复写法修正。

## 自审记录

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 节奏 | `hacks/VomitHack.java:42-46`：每 3 tick 按一下右键（吃/吐动作） | 与模块用途一致 |
| 关闭时恢复按键 | `:33-37` | **写法有误**，见下 |

## 实际改动：`setDown(false)` 改成 `resetPressedState()`

旧 `onDisable` 是 `MC.options.keyUse.setDown(false)`。

**反例（Rule 9）**：按住右键（吃东西 / 举盾 / 拉弓）时关闭 Vomit，旧实现会把右键强制置为"未按下",
与玩家真实输入无关 ⇒ 表现为动作被打断、"手没松却停止使用物品"。
与 `TwerkHack` 是同一类问题（`MileyCyrusHack:46` 早就是正确写法）。

修法：

```java
		IKeyBinding.get(MC.options.keyUse).resetPressedState();
```

## 建议（未做）

- 启用期间每 3 tick 强制按/松右键，会覆盖玩家自己的右键输入（这是模块用途），但也会让
  "吃东西"这类需要持续按住的动作用不出来；没有开关可以只做动作不抢输入，未改。
