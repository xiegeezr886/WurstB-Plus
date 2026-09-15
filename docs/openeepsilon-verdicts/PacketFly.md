状态：不适用（参考侧无对应模块；本工程实现是 1.20.1 的包级直发，零改动）

## 参考侧核对

`D:\WurstB\_oe_ref` 没有 PacketFly 模块。`Select-String -Pattern PacketFly` 只命中 `ElytraFlight.kt` 里的注释（讲鞘翅飞行时的位置包），`Flight.kt` 是改运动而不是发包。因此判「不适用」。

## 本工程现状（自审）

`hacks/PacketFlyHack.java`，147 行左右，靠**自己发 `ServerboundMovePlayerPacket.Pos`** 移动：

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 模式 | `:26-30,140-145` 只有 `Packet` 一种（枚举已收敛，无死值） | 设置表干净 |
| 速度 | `:31-40` `HSpeed`/`VSpeed`/`Fall` 三个既有滑块 | —— |
| 位置缓存 | `:41` `cachedPos`、`:42` `timer` | 自己维护「服务端认为我在哪」 |
| 发包 | `:109-122` 直接 `send(new ServerboundMovePlayerPacket.Pos(...))` | 1.20.1 的合法包 |
| 回包拦截 | `:130-138` 把自己发出的移动包对应的本地处理跳过，防止原版逻辑覆盖 `cachedPos` | 与 blink 类 hack 同款手法 |

## 故意不搬

- 参考无此模块。
- 参考 `Flight.kt`/`ElytraFlight.kt` 里 1.12.2 的 `CPacketPlayer.Position` + `CPacketConfirmTeleport` 组合：1.20.1 的对应物是 `ServerboundMovePlayerPacket` 与 `ClientboundPlayerPositionPacket`（`ServerGamePacketListenerImpl#handleMovePlayer` 里做 `isMovedTooQuickly` / `isMovedWrongly` 校验，超限就直接 `teleport` 拉回）。本工程现版本没有处理「被拉回后怎么办」，见建议。

## 建议（未做）

1. **没有实机验证这是本条目最大的未知**：`ServerboundMovePlayerPacket.Pos` 在 1.20.1 的服务端 `handleMovePlayer` 里会走「移动太快 / 移动非法」两条校验（反编译源 `server/network/ServerGamePacketListenerImpl.java`，可 grep `isMovedTooQuickly` / `isMovedWrongly` / `teleport`），超过阈值会立刻发 `ClientboundPlayerPositionPacket` 把玩家拉回；本工程没有监听这个包来重设 `cachedPos`。真实服务器（尤其带反作弊的）上是否可用，必须实机 + 真服务端验证，本轮只做了静态核对，故不宣称可用、也不据此改代码。
2. 若将来要加「被拉回时重置 `cachedPos`」，入口在 `:130-138` 的包分发处加一个 `ClientboundPlayerPositionPacket` 分支即可；属功能新增，未做。
