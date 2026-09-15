状态：不适用

## 对照证据

参考侧有 `movement/AntiHunger.kt`（33 行）：取消疾跑开始/结束的动作包（`Cancel Movement State`），
并把出站 `CPacketPlayer.onGround` 改成"只在鞘翅飞行时为真"。

本工程 `hacks/AntiHungerHack.java`（55 行）做的是同一件事的 1.20 版本：
在 `PacketOutputListener` 里把 `ServerboundMovePlayerPacket` 的 onGround 改成 false
（`PacketUtils.modifyOnGround`），条件更保守（`:47` 落地且 `fallDistance <= 0.5`、`:50` 没在挖方块）。

| 关注点 | 参考 | 本工程 | 判定 |
| --- | --- | --- | --- |
| 改 onGround | 只在鞘翅飞行时为真 | 站立/行走时为假 | 都能达到"服务端认为你在空中"的效果 |
| 取消疾跑包 | 有（可关） | 无 | 见建议 |
| 与 NoFall 互斥 | 无 | `:31` 开启时关闭 NoFall | 合理（两者都在改同一个 onGround 字段，会互相打架） |
| 不存档 | 无 | `:18` `@DontSaveState` | 合理 |

无改动。

## 建议（未做）

- 参考的"取消 START_SPRINTING/STOP_SPRINTING 动作包"没有搬：1.20 里服务端对疾跑包的依赖比
  1.12 弱（饥饿消耗主要看服务端的移动距离），搬过来收益不明确，且会引入"服务端认为你没在疾跑"的
  额外不确定性，未做。
