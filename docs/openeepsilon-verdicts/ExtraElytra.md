状态：不适用

## 对照证据

参考侧有 `movement/ElytraFlight.kt` 与 `movement/ElytraReplace.kt`（前者是自动飞行控制，后者是
鞘翅损坏后换新）。本工程 `hacks/ExtraElytraHack.java`（147 行）对应前者的一部分：
"一按跳跃就起飞（Instant fly）+ 用前进/后退键调速 + 用跳跃/潜行键调高度"。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 即时起飞 | `:91-92,132-146` 按住跳跃、鞘翅可用时先 `setJumping(false)` + `setSprinting(true)` + `jumpFromGround()`，再发 `START_FALL_FLYING`，并有 20 tick 冷却 | 正常 |
| 起飞/停止用同一个包 | `:95-100` `sendStartStopPacket()` 发的是 `START_FALL_FLYING`。已核对原版是**切换**语义：1.20.2 反编译源 `server/network/ServerGamePacketListenerImpl.java:1426-1428` 里该分支在玩家已滑翔时调用 `stopFallFlying()` ⇒ 水中停止滑翔有效 | 正确 |
| 调速 | `:115-130` 水平 ±0.05/tick、`:102-113` 垂直 +0.08/-0.04/tick | 正常 |
| 水中停止 | `:80-84` | 正常 |

无改动。

## 建议（未做）

- 参考的 `ElytraReplace`（鞘翅快坏时自动换新）在本工程里没有对应模块（本工程有 `AutoMend`，
  但那是经验修补）；是否新增属于功能决策，未做。
