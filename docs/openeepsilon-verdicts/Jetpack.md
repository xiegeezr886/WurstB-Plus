状态：不适用

## 对照证据

参考侧没有对应模块（参考的 `movement/Flight.kt` 是通用飞行，没有"按住跳跃键持续起跳"这种形式）。
本项为自审，全文读过 `hacks/JetpackHack.java`（45 行）。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 原理 | `:42-43` 按住跳跃键就 `MC.player.jumpFromGround()` | 正确：`jumpFromGround()` 不检查是否落地，空中调用就是向上加速 |
| 冲突组 | `:24` `HackConflictGroup.MOVEMENT_CONTROL` | 已登记（与 CreativeFlight 等互斥） |
| 关闭 | `:33-37` 摘监听；没有需要还原的状态（速度是一次性的） | 正常 |

无改动。

## 建议（未做）

- `SearchTags` 里带了 `"AirJump"`，与独立的 `AirJump` 模块（`hacks/AirJumpHack.java`，双跳/自由跳）
  搜同一个词，搜索结果会同时出现两个模块；两者机制不同（这个靠持续按键，AirJump 靠按键边沿），
  未改。
