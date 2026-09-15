状态：不适用

## 对照证据

参考侧有 `movement/SafeWalk.kt`（Eagle/边缘潜行）。本工程 `hacks/SafeWalkHack.java`（109 行）实现更细。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 边缘裁剪 | `:65-85` 由 `ClientPlayerEntityMixin` 调用：把碰撞箱向内缩 `edgeDistance`（默认 0.05m）后若下方无碰撞则判定"在边缘"并裁剪移动 | 正常（真正的"剪移动"而不是只按潜行键） |
| 可见潜行（可选） | `:69-84` 勾选 `Sneak at edges` 时用 `IKeyBinding.setPressed(true)` 做出潜行动作，`:96-106` 统一走 `setPressed`/`resetPressedState` | 正确（`resetPressedState()` 按玩家真实按键还原） |
| 生效条件 | `:87-94` 排除旁观/飞行/鞘翅；`Only on ground`（默认开）与 `While jumping`（默认关） | 正常 |
| 关闭时还原 | `:58-63` 只在自己造成潜行时还原 | 正确（不会误清玩家自己的潜行键） |
| 与 Parkour 互斥 | `:54` 开启时关闭 Parkour | 合理 |

无改动（本行此前在覆盖表里是"待办"，代码已经是新写法，属重复列出）。
