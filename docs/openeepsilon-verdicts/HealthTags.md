状态：不适用

## 对照证据

参考侧有 `render/HealthTags.kt`（在名牌后加血量）。本工程 `hacks/HealthTagsHack.java`（54 行）
实现相同，并带颜色分级。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 开关 | `:29-30` 未启用时原样返回名牌 | 正确 |
| 颜色分级 | `:39-51` ≤5 深红、≤10 金、≤15 黄、其余绿 | 与参考的分档思路一致 |
| 消费点 | `EntityRendererMixin.java:54` | 已核对 |

无改动。

## 建议（未做）

- `:36` 把入参 `nametag` 直接强转成 `MutableComponent`：原版 `EntityRenderer.renderNameTag` 收到的
  是 `getDisplayName()` 的结果（`PlayerTeam.formatNameForTeam` 返回 `MutableComponent`），所以当前
  路径不会抛异常；但如果将来有 mixin 往这里传不可变 `Component`，会 `ClassCastException`。
  没有构造出可触发的反例，只记录。
