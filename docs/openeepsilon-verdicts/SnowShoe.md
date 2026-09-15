状态：不适用

## 对照证据

参考侧没有对应模块（1.12.2 没有细雪）。本项为自审（模块全文 25 行，是标记式功能）。

标记模块 + mixin：`hacks/SnowShoeHack.java` 只有开关，真正的逻辑在
`mixin/PowderSnowBlockMixin.java:35`（`snowShoeHack.isEnabled()` 时跳过细雪的
`makeStuckInBlock`，即 `PowderSnowBlock.entityInside` 给玩家的 (0.9, 1.5, 0.9) 速度乘数）。

无改动。

## 建议（未做）

- 只处理了"站进细雪里不粘"，没有处理"细雪里不能跳跃"（原版另有
  `PowderSnowBlock.canEntityWalkOnPowderSnow` / 跳跃限制）；Wurst 的同类模块普遍只做前者，未改。
