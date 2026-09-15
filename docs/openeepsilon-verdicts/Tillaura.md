状态：不适用

## 对照证据

参考侧没有对应模块（参考的 `player/AutoFarm.kt` 是自动种收，不含锄地光环）。
本项为自审，全文读过 `hacks/TillauraHack.java`（171 行）。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 触发时机 | `:78-113` `HandleInputListener`（输入阶段），先看 `MC.rightClickDelay`、挖掘中、手忙 | 正确 |
| 手持物 | `:90-91` 必须拿着 `HoeItem` | 与"锄地光环"语义一致 |
| 候选方块 | `:130-139` 只处理草方块/土径/泥土/砂土，且上方必须是空气 | 正确（1.20 里 `Blocks.DIRT` 用锄头只能变耕地） |
| 两种模式 | `:96-112` MultiTill 用 `SwingHand.OFF` 自己挥一次臂；非 MultiTill 用 `faceVectorPacket` 走 `InteractionSimulator.rightClickBlock` | 与设置说明（MultiTill 快但会被 NoCheat+ 抓）一致 |
| 距离/视线 | `:145-148`、`:161-164` | 正确 |
| 关闭 | `:72-76` 摘监听 | 平衡 |

无改动。

## 说明（共享观察）

- `:86` 的 `isHandsBusy()` 在 1.20 里只表示"驾驶船并按方向键"（`LocalPlayer.java:877-888`），
  注释写的"挖掘中或骑乘"里"骑乘"这半只覆盖了船；没有构造出可触发的反例，只记录。
