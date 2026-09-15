状态：已优化

## 对照证据

参考侧没有对应模块（参考没有"反复蹲起"这种玩笑模块）。本项为自审 + 一处按键恢复写法修正。

## 自审记录

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 节奏 | `hacks/TwerkHack.java:42-43`：`tick % 4 < 2` → 每 4 tick 蹲 2 tick | 与模块用途一致 |
| 关闭时恢复按键 | `:33-37` | **写法有误**，见下 |

## 实际改动：`setDown(false)` 改成 `resetPressedState()`

旧 `onDisable` 是 `MC.options.keyShift.setDown(false)`。

**反例（Rule 9）**：按住 Shift 潜行，同时开着 Twerk，然后关闭 Twerk。旧实现无条件把潜行键置为"未按下"，
而这与玩家真实按键状态无关 ⇒ 表现为"手没松却站起来了"，必须松开 Shift 再按一次才能恢复潜行。
（同一批还有 `VomitHack` 的 `keyUse`；`MileyCyrusHack:46` 早就是正确写法，本次统一。）

修法：

```java
		IKeyBinding.get(MC.options.keyShift).resetPressedState();
```

`resetPressedState()` 按玩家**真实**按键状态恢复（`mixin/KeyBindingMixin.java:29-38` 直接查 GLFW），
玩家确实按着 Shift 时恢复后仍然是蹲着的。

## 建议（未做）

- 与 `MileyCyrus`/`Vomit` 会互相抢按键，未加互斥（三个都是玩笑模块）。
