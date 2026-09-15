状态：已优化

## 对照证据

参考侧：`_oe_ref/src/main/kotlin/studio/coni/epsilon/module/movement/ElytraFlight.kt`。

| 关注点 | 参考（ElytraFlight.kt:行） | 本工程（hacks/ElytraFlyHack.java:行） | 判定 |
| --- | --- | --- | --- |
| 起飞时发包 | `:296` `if (player.motionY < 0 …)`（要求已在下落）→ `:308` 发 `CPacketEntityAction(START_FALL_FLYING)` | 旧 `:145` 直接 `jumpFromGround()` 先抬升，`:148`（旧行号）**每 tick 无条件**发 `ServerboundPlayerCommandPacket(START_FALL_FLYING)` | **旧实现有真实缺陷**，见下 |
| 1.20.1 服务端怎么处理这个包 | 1.12.2 旧协议，无对应行可引 | 1.20.2 真源 `server/network/ServerGamePacketListenerImpl.java:1426-1428`：`case START_FALL_FLYING: if (!this.player.tryToStartFallFlying()) { this.player.stopFallFlying(); }` | 服务端在「自己已经认为你在滑翔」时会执行 `stopFallFlying()`（`Player.java:1626-1629` 把 flag 7 先置真再置假）。所以起飞后继续补包 = 把自己刚开始的滑翔取消掉 |
| 1.20.1 客户端原版怎么写这段 | — | 1.20.2 真源 `client/player/LocalPlayer.java:766-771`：`if (this.input.jumping && … && !this.getAbilities().flying && !this.isPassenger() && !this.onClimbable()) { if (itemstack.canElytraFly(this) && this.tryToStartFallFlying()) send(START_FALL_FLYING); }` | 原版自己就是「先本地 `tryToStartFallFlying()`，成功才发包」。本工程改成同一形状即为对齐原版 |
| `tryToStartFallFlying()` 的前置 | 1.12.2 无此方法 | `Player.java:1610-1620`：`!onGround() && !isFallFlying() && !isInWater() && !hasEffect(LEVITATION)` 且胸甲 `canElytraFly(this)` | 它返回 true 时**已经**把本地 flag 置位（`:1614` `startFallFlying()`），之后 `onUpdate()` 的 `:80` `isFallFlying()` 分支接管，不会再发包 |
| 空中调用 `jumpFromGround()` | `:296` 要求 `motionY < 0` 才起飞，不做凭空抬升 | 旧 `:140`（旧行号）`if(jumpTimer <= 0)` 无任何地面判定 | `LivingEntity.java:2054-2058` 的 `jumpFromGround()` 自身不检查 `onGround`，唯一原版调用点 `LivingEntity.java:2632-2634` 是 `if((this.onGround() \|\| 浅水) && this.noJumpDelay == 0)`。空中调用 = 凭空 +0.42 竖直冲量 |
| 起跳节流 | — | 本工程 `jumpTimer = 20`（`:142`），对应原版的 `noJumpDelay = 10`（`LivingEntity.java:2634`） | 保留，语义一致 |

## 实际改动

`hacks/ElytraFlyHack.java`（`doInstantFly()`，两处）：

1. `if(jumpTimer <= 0)` → `if(jumpTimer <= 0 && MC.player.onGround())`。
   证据：`LivingEntity.java:2632-2634`。旧行为下，若 `tryToStartFallFlying()` 因为任何原因失败（不在空中/在水里/有飘浮/胸甲不可飞）而玩家一直按着跳跃键，则每 20 tick 就会在空中白拿一次 0.42 上冲——等于「按住空格无限爬升」。新行为与原版一致：只有站在地面才起跳。
2. `sendStartStopPacket();` → `if(MC.player.tryToStartFallFlying()) sendStartStopPacket();`。
   证据：`ServerGamePacketListenerImpl.java:1426-1428`（服务端收到「已经在滑翔」的包会 `stopFallFlying()`）+ `LocalPlayer.java:766-771`（原版就是这么写的）。
   新旧行为差异可举证：旧代码在起跳后到服务端实体元数据回包之间（本地 `isFallFlying()` 仍为 false，跳跃键还按着）每 tick 都发包，服务端此时 `isFallFlying()` 已是 true → `tryToStartFallFlying()` 返回 false → 每 tick 执行一次 `stopFallFlying()`。表现为瞬时起飞闪烁/起不来（延迟越高越明显）。新代码本地先置位、只发一次包，之后走 `:80` 的 `isFallFlying()` 分支。
   注意 `:92` 已有 `ElytraItem.isFlyEnabled(chest)` 前置，与 `canElytraFly` 同一条件，未重复。

## 故意不搬

- 参考 `:25,100` 的四模式（Boost/Control/Creative/Packet）与 `:39-96` 的三十多个滑块：本工程 `hacks/ElytraFlyHack.java` 只有 `horizontalSpeed/verticalSpeed/stopInWater/instantFly` 一套设置，扩展它等于新增设置项，超出「重构既有实现」的范围。
- `:135-143` 监听 `SPacketEntityMetadata` 自己维护 `isPacketFlying`：本工程用原版 `isFallFlying()`，不需要影子状态。
- `:440-456` Creative 模式走 `setVelocity(0, motionY, 0)` 并清零水平：与 `hacks/CreativeFlightHack.java` 职责重复，不搬。

## 建议（未做）

1. `stopInWater`（`:82-86`）在滑翔中且在水里时发 `START_FALL_FLYING`：这条**依赖**服务端 `stopFallFlying()` 的取消语义（`ServerGamePacketListenerImpl.java:1426-1428`），是「靠非法包达到减速」的写法。功能正确，但如果哪天想让它变成「本地 `setSharedFlag(7, false)`」会更干净——属于同一处的另一条路径，本轮未动。
2. 落地时自动收伞（参考 `:211`、`:250-280` 的 `Auto Landing`）没有对应实现，属功能新增，不做。
3. 关于验证边界：本次改动只做了 `compileJava`/`test` 与源码比对，没有实机用鞘翅飞过，也没有连服务器验证「包只发一次」的实际时序。
