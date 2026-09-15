状态：不适用

## 对照证据

参考侧有 `player/AntiCactus.kt`（1.12.2 改仙人掌的碰撞箱）。本工程 `hacks/AntiCactusHack.java`
（43 行）用事件实现：实现 `CactusCollisionShapeListener`，把碰撞形状换成
`Shapes.block()`（完整方块），触发点在 `mixin/CactusBlockMixin.java:40`。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 开关 | `:26-36` 事件在 `onEnable` 加、`onDisable` 摘 | 平衡，能真正关掉（不是标记模块） |
| 碰撞形状 | `:41` `Shapes.block()` | 正确（仙人掌原版是 15/16 缩进的柱子，换成整块后不会碰到刺） |
| 覆盖面 | 只替换碰撞形状，不改伤害逻辑 | 服务端仍会算伤害，但客户端不会把自己挤进仙人掌 —— 与参考的"客户端侧"性质一致 |

无改动。

## 说明

- 覆盖清单里曾把 AntiCactus 标为"疑似只注册没用"（全仓库只有 `HackList` 引用它）。
  核对后确认这是误判：该模块通过**事件接口**（`CactusCollisionShapeListener`）被 mixin 消费，
  事件式消费不会出现 `xxxHack` 字段名，所以引用扫描看不到。
