状态：不适用

## 对照证据

参考侧有 `movement/Sprint.kt`（自动疾跑）。本工程 `hacks/AutoSprintHack.java`（121 行）实现更细。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 判定 | `:84-102` `canSprint()` 逐条排除骑乘/鞘翅/水中、水平碰撞、潜行、用物品、失明、饥饿、方向 | 六个开关各自对应一条，逻辑清晰 |
| 只停自己造成的疾跑 | `:39,65-82,104-110` `sprintOwner` 记录"是我们开的疾跑"，关闭时只有"是我们开的、且玩家自己没按疾跑键"才 `setSprinting(false)` | 正确（不会误停玩家自己按出来的疾跑） |
| 换世界/重生 | `:68-70` 玩家对象变了就清空 `sprintOwner` | 正常 |
| 被 mixin 消费 | `:112-120` `shouldOmniSprint()` / `shouldSprintHungry()` 给 mixin 用 | 正常 |

无改动（本行此前在覆盖表里是"待办"，代码已经是新写法，属重复列出）。

## 建议（未做）

- `:99` 用 `player.input.forwardImpulse <= 0` 判"有没有前进"：该值在 UpdateEvent 时点是上一 tick 的
  （原版 `input.tick()` 在 `aiStep()` 里），对疾跑判定有一 tick 滞后；没有构造出可触发的反例，只记录。
