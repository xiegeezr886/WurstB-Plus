状态：不适用

## 对照证据

参考侧有 `player/HandNoClip.kt`（1.12.2 让手里能穿墙点到特定方块）。本工程
`hacks/HandNoClipHack.java`（49 行）用 `BlockListSetting` 指定方块，消费点
`mixin/AbstractBlockStateMixin.java`（`onGetOutlineShape`）。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 判定 | `:43-46` `isBlockInList(pos)` = 名单里包含该方块名 | 正常 |
| 默认名单 | `:21-33` 容器类方块（箱子/桶/潜影盒/漏斗等 25 种） | 与"隔墙点容器"的用途一致 |
| 开关 | 由 mixin 侧判断（`handNoClipHack` 被 `AbstractBlockStateMixin` 引用，已核对） | 正常 |

无改动。

## 建议（未做）

- `isBlockInList()` 自身不判 `isEnabled()`，依赖 mixin 侧判断。本次核对了该调用点有判断；
  但这种"约定"容易在新增调用点漏掉，未改（与 `NoSlowdown` 的建议同类）。
