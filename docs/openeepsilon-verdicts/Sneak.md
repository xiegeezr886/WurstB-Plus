状态：不适用（参考侧无独立模块；顺带修正一处覆盖表的错误说法）

## 参考侧核对

`_oe_ref/.../module/movement/` 没有 Sneak 模块（列表见 `_BRIEF.md` 同目录其它文档）。但**参考把这个能力放在 NoSlowDown 里**：

| 关注点 | 参考（NoSlowDown.kt:行） | 本工程（hacks/SneakHack.java:行） | 判定 |
| --- | --- | --- | --- |
| "潜行但不减速" | `:30` `sneak = setting("Sneak", false)`；`:65-68` `if (sneak && player.isSneaking \|\| items && player.isHandActive) { movementInput.moveStrafe *= 5f; movementInput.moveForward *= 5f }` | `:26-29` 的 `PACKET` 模式：`:93-95` 每个移动 tick 前发 `PRESS_SHIFT_KEY` + `RELEASE_SHIFT_KEY`，服务端以为你在潜行，而客户端从未真正减速 | **同一目标、两种实现**：参考放大输入去抵消减速，本工程用包让服务端看到潜行。本工程的做法不需要知道减速系数，且在 1.20.1 仍然成立 |
| 真潜行 | 参考无（它不真让你潜行） | `:85-90` `LEGIT` 模式直接把潜行键按住 | 本工程多出的模式，保留 |
| 飞行时关闭 | 无 | `:31-37` `offWhileFlying`（默认 false）；`:110-122` 把 `getAbilities().flying`、`Flight`、`Freecam` 都算作"在飞" | 本工程独有，保留 |
| 关闭时复位 | `:38-44` 发 `STOP_SNEAKING` 并清自身标志 | `:61-76` 按模式分别复位：`LEGIT` 走 `IKeyBinding.resetPressedState()`（`:69`），`PACKET` 发 `RELEASE_SHIFT_KEY`（`:73`） | 都做到位；本工程区分两种模式，比参考只清包状态更完整 |
| 动作后补包 | `:77-81` `PostSend` 时补 `CPacketPlayerTryUseItem`（那是 NoSlowDown 的物品逻辑） | `:100-108` `onPostMotion` 在 PACKET 模式再发 `RELEASE` + `PRESS` | 各自服务自己的目的 |

## 需要修正的既有说法

`docs/openeepsilon-refactor.md` 第 125 行（`NoSlowdown` 行）原写「参考的 `Sneak` 开关（潜行时也全速）在本工程没有对应实现——本工程两个 mixin 只处理『用物品』与『方块速度乘数』，潜行减速那条路没有口子」。**这句话不准确**：本工程有 `hacks/SneakHack.java` 的 `PACKET` 模式，它达到的正是「服务端认为你在潜行 + 客户端不减速」。缺的不是能力，而是「把这个开关放进 NoSlowdown 那一页」的位置问题。本轮已把该行改写为指向本文件。

## 实际改动

无（`hacks/SneakHack.java` 零改动）。只修正了 `docs/openeepsilon-refactor.md` 的覆盖表描述。

## 故意不搬

- 参考的 `moveStrafe *= 5.0f; moveForward *= 5.0f`（`NoSlowDown.kt:66-67`）：1.20.1 的输入是 `Input.forwardImpulse/leftImpulse` 且被原版 `LocalPlayer.aiStep` 用来算 `moveRelative`，直接乘 5 会把数值弄成非法量级；而本工程 PACKET 模式已经用更干净的方式达到同一效果，不搬。

## 建议（未做）

1. `SneakHack` 与 `NoSlowdown` 的功能重叠（"潜行不减速"两处都能碰）：如果将来要合并，应该保留 `SneakHack` 的实现、在 `NoSlowdown` 的说明里加一句指向它，而不是把包逻辑复制一份。属文档/UI 层面，未做。
2. 验证边界：零改动，只做了静态核对。PACKET 模式每 tick 两个包的实际反作弊表现没有实机验证过。
