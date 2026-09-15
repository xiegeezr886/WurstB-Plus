状态：不适用（参考侧无对应模块；本工程实现自洽，零改动）

## 参考侧核对

`D:\WurstB\_oe_ref` 没有 NoClip 模块。名字最接近的是 render 分类的 `CameraClip.kt`（让**相机**能穿墙，与玩家碰撞无关）和 player 分类的 `Freecam.kt`（把玩家实体留在原地、只让相机飞走）。两者的语义都与「让玩家本体不碰撞」不同，因此没有可对照实现，判「不适用」。

## 本工程现状（自审）

`hacks/NoClipHack.java`，95 行，走的是「关掉玩家自己的物理」而不是发包：

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 核心 | `:58` `player.noPhysics = true`（`Entity#noPhysics` 会让 `move()` 跳过全部碰撞） | 与 1.20.1 的物理入口一致 |
| 配套状态 | `:59` `fallDistance = 0`（免摔伤）、`:60` `setOnGround(false)`、`:62` `flying = false`（避免和创造飞行打架）、`:63` `setDeltaMovement(0,0,0)`（消除惯性） | 四项配套齐全 |
| 垂直移动 | `:65-69` 跳跃键 `push(0,0.2,0)`、潜行键 `push(0,-0.2,0)` | 不依赖原版跳跃（`noPhysics` 下原版跳跃判定不可用） |
| 渲染/光照 | `:85-91` 拦 `IsNormalCubeEvent`/`SetOpaqueCubeEvent` | 让客户端不再把方块当不透明体，避免穿墙时一片黑 |
| 每 tick 重申 | `:79-81` `PlayerMoveListener` 里再设一次 `noPhysics = true` | 防止被别的逻辑改回去 |
| 关 hack | `:42-51` 摘 5 个监听器 + `noPhysics = false` | 与 `onEnable`（`:31-39`）对称 |

## 故意不搬

- 参考无此模块；`CameraClip.kt` 的相机穿墙与本 hack 无关（本工程另有 `FreecamHack`）。
- 1.12.2 的「发 `CPacketPlayer` 到方块里让服务端自己把玩家推出去」手法在 1.20.1 上会被服务端 `handleMovePlayer` 的碰撞校验直接拉回，且本工程的 `noPhysics` 是客户端物理层就生效，不需要包。

## 建议（未做）

1. `:50` 的 `MC.player.noPhysics = false` 没有 null 保护。若在断线/回主菜单后触发 `onDisable`（`MC.player == null`）会 NPE。经核对，这是**全仓一致的既有写法**（例如 `AntiAfkHack.java:124` 还原 `getAbilities().flying` 同样不判空），说明本工程的 `onDisable` 调用时机保证了玩家存在；按一致风格未单独加判空。
2. 没有实机验证：`noPhysics` 下与服务端的位置校验（`handleMovePlayer`）会不会把玩家拉回、以及创造模式之外的飞行是否被服务端接受，需要真人跑一次。
