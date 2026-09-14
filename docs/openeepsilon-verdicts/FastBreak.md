状态：不适用（参考侧无同功能模块；自审未找到可举证的行为差异，零改动）

## 参考侧核对

`_oe_ref` 里最接近的是 `player/PacketMine.kt`（"用包挖方块"），但那是"绕过原版破坏流程直接发包"，
与 FastBreak 的"缩短原版间隔 + 多发完成包"不是同一件事，本轮不把它当对应物。故判「不适用」，主体是自审。

## 本工程现状（证据表）

`hacks/FastBreakHack.java`，109 行。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 设置 | `:32-38` `Acceleration` 滑条、`Legit mode` 复选框 | 两个设置 |
| 间隔 | `:78-82` `onUpdate()` → `MC.gameMode.destroyDelay = 0` | 去掉原版"两次破坏之间的延迟"（原版这个字段用来限制连续挖方块的速度） |
| 加速 | `:84-108` 在 `BlockBreakingProgressEvent` 里：`legitMode` 勾选 → 直接 return；`destroyProgress >= 1` → return；`BlockUtils.isUnbreakable(pos)` → return；否则发 `packetCount = max(1, round(acceleration))` 个 `STOP_DESTROY_BLOCK` 包 | 在同一个方块上重复发"完成破坏"包，让服务端反复结算，从而更快挖掉 |
| 防护 | `:95-96` 用 `BlockUtils.isUnbreakable` 跳过基岩等不可破坏方块 | 不会对着基岩刷包 |

## 实际改动

无。没有可举证的新旧行为差异，按简报第 9 条不动代码。

## 建议（未做）

1. `:87-88` `legitMode` 只挡"额外发包"，**不挡** `:81` 的 `destroyDelay = 0`。如果 `Legit mode` 的描述是
   "完全按原版速度、只是不额外发包"，那 `onUpdate` 也应当在勾选时跳过。本轮没有读到 `:38-40` 的描述文本，
   按简报第 9 条不据此改代码；建议把描述改清楚（或让 `legitMode` 同时跳过 `destroyDelay`）。
2. `:99` `packetCount = Math.max(1, Math.round(acceleration))`：`Acceleration` 调到最低（0）时仍会发 1 个包，
   即"滑条归零也还是加速"。是否让 0 表示"完全不额外发包"属产品决策，未做。
3. 验证边界：只做了静态核对；`STOP_DESTROY_BLOCK` 重复包在 1.20.1 服务端的实际效果（是否被忽略）没有连服验证。
