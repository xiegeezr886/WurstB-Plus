状态：已重构

## 对照证据

参考侧 `Critical.kt` 是 1.12.2 的**包级**手法（拦下 ATTACK 包、补发位置包）；本工程这个文件已经不再是
参考的移植，而是 **LiquidBounce 版 Criticals 的 Forge/Mojmap 移植**（文件头 `CriticalsHack.java:1-11`
写明），并且决策部分早已抽成纯逻辑类 `util/CombatActionPolicy`。本轮的核对任务是「1.20.1 上还成不成立」，
逐条核完的结论是**成立且无需改动**（证据见下表第 3、5、6 行）。

### 1. 判定逻辑与政策类

| 关注点 | 参考（_oe_ref 相对路径:行） | 本工程（相对路径:行） | 判定 |
| --- | --- | --- | --- |
| 暴击前置条件放在哪 | `module/combat/Critical.kt:123-125` `canCrit()` 内联在模块里，**无法单测** | `util/CombatActionPolicy.java:20-29`（`canCritical`）、`:31-36`（`canStartSpoofedCritical`）、`:38-43`（`CriticalState` record）；消费点 `hacks/CriticalsHack.java:94-96` | 本工程已重构（纯类 + 可单测，无 MC import） |
| 条件清单 | `Critical.kt:124`：`onGround && !isInWeb && !isInWater && !isInLava && ridingEntity == null` —— **缺** `!isSprinting`、`fallDistance > 0`、`!onClimbable`、失明 | `CombatActionPolicy.java:23-28`：`ignoreOnGround || !onGround`、非液体（`isInWaterOrBubble \|\| isInLava`）、非攀爬、非骑乘、非飞行/滑翔/无重力/双手占用/失明/漂浮/缓降、`attackStrength > 0.9`、疾跑由 `Stop sprinting` 决定 | 本工程是参考的**严格超集**：参考在 1.12.2 上疾跑时照样会误判成能暴击 |
| 攻击冷却 | 参考无此概念（1.12.2 没有 attack strength） | `CriticalsHack.java:46-47`（`Only when ready` 默认开）、`:132-133`（`getAttackStrengthScale(0.5F) > 0.9`，关掉时喂 1.0） | 1.20.1 独有且正确（对应原版的 `f2 > 0.9F`） |
| 停疾跑 | 参考完全没有处理 | `CriticalsHack.java:49-51`（`Stop sprinting` 默认开）、`:97-102`：先发 `ServerboundPlayerCommandPacket(STOP_SPRINTING)` 再 `setSprinting(false)`；策略侧 `CombatActionPolicy.java:28,31-36` | 必要且已实现（`!isSprinting` 是原版硬条件） |
| 只打生物 | `Critical.kt:38`：目标必须是 `EntityLivingBase` | `CriticalsHack.java:89-90`：`target instanceof LivingEntity` | 等价（原版硬条件 `target instanceof LivingEntity`） |

### 2. 原版 1.20.1 真正判什么

| 关注点 | 参考（_oe_ref 相对路径:行） | 本工程（相对路径:行） | 判定 |
| --- | --- | --- | --- |
| 服务端暴击判定 | 1.12.2 的对应逻辑（参考靠 `onGround` 猜） | 仓库内可读的反编译源（1.20.2，`attack` 段自 1.16 起未变）：`neoforge/versions/1.20.2/build/neoForm/neoFormJoined1.20.2-20231019.002635/steps/unzipSources/unpacked/net/minecraft/world/entity/player/Player.java:1161-1169`：`fallDistance > 0.0F && !onGround() && !onClimbable() && !isInWater() && !hasEffect(BLINDNESS) && !isPassenger() && target instanceof LivingEntity`，再 `:1169 && !isSprinting()`；外层前置 `:1151 flag = f2 > 0.9F` | 与 `CombatActionPolicy` 一一对应，没有多也没有少 |
| 客户端粒子 | `Critical.kt:71,81`：`mc.player.onCriticalHit(target)` | `CriticalsHack.java:126-127`：`MC.player.crit(target)`；`.../client/player/LocalPlayer.java:614-615` 在客户端生成 `ParticleTypes.CRIT`（`Player.crit` 在 `Player.java:1330` 是空实现，`ServerPlayer.crit` 在 `ServerPlayer.java:1267` 只广播事件） | 等价 |
| 1.20.1 里这些 API 都存在 | — | `%USERPROFILE%\.gradle\caches\forge_gradle\minecraft_repo\versions\1.20.1\client_mappings.txt:20790`（`isHandsBusy`）、`:71292`（`getAttackStrengthScale(float)`）、`:58361`（`isNoGravity`）、`:58373`（`isInWaterOrBubble`）、`:59648`（`isFallFlying`）、`:59565`（`jumpFromGround`）、`:41836-41837`（`ServerboundMovePlayerPacket$Pos(double,double,double,boolean)`）、`:41944`/`:41958`（`ServerboundPlayerCommandPacket(Entity,Action)` / `Action.STOP_SPRINTING`） | 全部存在，本文件在 1.20.1 上可编译 |

### 3. 靠跳还是靠包？包在 1.20.1 上还成立吗

| 关注点 | 参考（_oe_ref 相对路径:行） | 本工程（相对路径:行） | 判定 |
| --- | --- | --- | --- |
| 模式 | 参考 5 种：`Packet`（默认）/`NCP`/`AAC`/`Hypixel`/`Jump`（`Critical.kt:24,132-134`） | 本工程 4 种：`PACKET`（默认）/`NO_GROUND`/`MINI_JUMP`/`JUMP`（`CriticalsHack.java:35-36,178-204`） | 两条路都有，默认都是**包** |
| 跳 | `Critical.kt:111-115`：`motionY = 0.1; fallDistance = 0.1f; onGround = false` | `CriticalsHack.java:107-116`（MINI_JUMP，默认 `Jump height = 0.1`，`:42-44`，与参考的 0.1 一致）、`:117-123`（JUMP = 原版 `jumpFromGround()` 0.42） | 等价且更完整（多了真正的原版跳跃模式） |
| 包序列 | `Critical.kt:46-110`：`Packet` 1~4 连、`NCP` 三个（`:78-80` 0.11 / 0.1100013579 / 1.3579E-6）、`Hypixel`/`AAC` 抖动序列 | `CriticalsHack.java:145-170`：VANILLA `:149-152`（0.2 → 0.01）、NO_CHEAT_PLUS `:153-157`（0.11 / 0.1100013579 / 0.0000013579，与参考 `:78-80` 同形）、FALLING `:158-162`、LOW `:163-166`、DOWN `:167`、GRIM `:168`；`sendOffset` 全部 `onGround=false`（`:172-176`） | 本工程的注释口径是 LiquidBounce 的 6 个 profile；参考那三个是 1.12.2 反作弊专用（见「故意不搬」） |
| **包为什么在 1.20.1 上仍然有效** | 1.12.2 的服务端同样在 `processPlayer` 里用客户端上报的位移调用 `moveEntity()`，机制同源 | 服务端链路（1.20.2 反编译源逐行；1.20.1 同名方法的存在性见 `server_mappings.txt:21788` `handleMovePlayer`、`:21236` `doCheckFallDamage`、`:32344` `setOnGroundWithKnownMovement`、`:32550` `resetFallDistance`）：<br>`.../server/network/ServerGamePacketListenerImpl.java:923-929`（`flag = d7 > 0`；服务端以为在地面而包说 `!onGround` 且在上移 → `jumpFromGround()`；随后 `player.move(MoverType.PLAYER, 包位移)`）→ `:951` `absMoveTo(包坐标)` → `:962` `player.doCheckFallDamage(dx,dy,dz, packet.isOnGround())` → `.../server/level/ServerPlayer.java:960-966` → `.../world/entity/Entity.java:1125-1141`：**落地 → `resetFallDistance()`（`:1136-1137`）；否则 `dy < 0` → `fallDistance -= dy`（`:1138-1139`）**；另 `ServerGamePacketListenerImpl.java:967-969` **只有上移（`flag`）才 `resetFallDistance()`** | **成立**：每个 profile 都是「先上后下、全程 `onGround=false`」，**最后一条一定是相对上一条的下移**（`:151` 0.2→0.01 差 −0.19；`:156` →0.0000013579 差 −0.11；`:161` 同形；`:165` 1E-9→0 差 −1E-9；`:167` −1E-9；`:168` −1E-6），于是攻击包到达 `Player.attack` 时 `fallDistance > 0 && !onGround()` 同时成立<br>（反之，若最后一个包是上移，`:967-969` 会把刚攒的 `fallDistance` 清成 0——这正是这套 profile 必须「上-下」配对的原因） |
| 包与攻击包的先后 | `Critical.kt:30-38`：在 `onPacketSend` 里拦 ATTACK 包，先把伪造包发掉再放行 | `mixin/ClientPlayerInteractionManagerMixin.java:52-58`：在 `MultiPlayerGameMode.attack` 的 **HEAD** 触发 `PlayerAttacksEntityEvent`；`.../client/multiplayer/MultiPlayerGameMode.java:406-412` 才发 `ServerboundInteractPacket.createAttackPacket` | 顺序正确（伪造包先入队），不需要参考的截包层 |

## 实际改动

无。本文件在本轮之前已经完成重构（`CombatActionPolicy` 纯类 + LiquidBounce 的 6 个 profile +
`stopSprinting`/`onlyReady` 两个正确性前置），本轮逐条核对后的结论是：

- 包路在 1.20.1 上**仍然成立**，而且原因与 1.8/1.12.2 同源（服务端把上报位移交给 `move()` →
  `checkFallDamage()`，见上表第 3 节）；
- 四个模式用到的每个工程侧 API 在 1.20.1 都存在（`client_mappings.txt` 行号见上），文件可编译；
- 参考 `Critical.kt:123-125` 的判定是本工程政策类的真子集，没有可搬的判断；
- 没有发现可修的缺陷，因此按 brief 第 7 条**一行未改**（没有为了「看起来改过」去动 profile 数值或
  重排 switch）。

## 故意不搬

- `SwordOnly`（`Critical.kt:22,32-35`，默认 true）：只有手持 `ItemSword` 才伪造暴击，会把斧、三叉戟、
  空手的暴击一起关掉。本工程没有这个设置，加它是**新增设置**（牵动配置与翻译），而且它不是正确性修复。
- `SyncKillAura`（`Critical.kt:23,39-43`）：参考需要它，因为它是**截包**——得自己判断这一下打的是不是
  KillAura 的目标。本工程挂在真实攻击事件上（`ClientPlayerInteractionManagerMixin.java:54-57`），
  打谁就只对谁发，开关没有意义。
- `Hypixel`（`Critical.kt:84-97`）与 `AAC`（`:98-110`）模式：这两段是按 1.12.2 时代的
  Hypixel/AAC 写的抖动序列（`ThreadLocalRandom` 随机量 + `ticksExisted % 0.00715` 这种按 tick 变化的
  偏移）。1.20.1 上那两个反作弊的这套绕过已不适用（Hypixel 现在是 Watchdog/NCP 新版，AAC 也换代了），
  照搬只会多几个可疑包；本工程改用的 NoCheatPlus/Falling/Grim profile 是 1.20.x 上仍在用的那套。
- `delay` 计时器（`Critical.kt:26,76,85,99`）：参考是「攻击后隔 `delay` 才允许下一次伪造」；本工程每次
  真实攻击都发一次，频率由 `Only when ready`（攻击冷却 > 0.9）限制，语义更直接，不需要再加一个毫秒计时器。
- 「补一个 `onGround=true` 的位置包」：参考和本工程都没做，也**不能**做——`onGround=true` 会让服务端
  走到 `Entity.java:1136-1137` 的 `resetFallDistance()`，把刚攒出来的 `fallDistance` 清零。

## 建议（未做）

1. **上移包会顺带让服务端真的跳一下**：`ServerGamePacketListenerImpl.java:923-926`——服务端以为你在地面、
   包说 `!onGround` 且在上移时会调用 `player.jumpFromGround()`，给服务端侧玩家一个 0.42 的竖直速度。
   这是 `VANILLA`/`NO_CHEAT_PLUS`/`FALLING`/`LOW` 都会触发的副作用（`DOWN`/`GRIM` 不会）。要不要为
   「上-下」型 profile 改成「只发下移包」（即全部退化成 `DOWN`/`GRIM` 那种单包）需要实机对比反作弊表现，
   属行为取舍，本次不动。
2. **`Only when ready` 的 0.9 只是本地阈值**：服务端按它自己那一 tick 的 `getAttackStrengthScale` 判
   （`Player.java:1151`）。高延迟下存在「本地刚过 0.9、服务端收到时不到 0.9」的窗口，此时包白发了
   （只浪费一次位置包，不会误伤其他功能）。要彻底对齐得按 `getCurrentItemAttackStrengthDelay` 提前一点
   发，参考没有这个概念，属行为改动，未做。
3. **`Packet` 模式全程 `onGround=false`** 会同时命中服务端的落地/浮空统计与移动校验
   （`ServerGamePacketListenerImpl.java:948-960`、`:971` 的 `checkMovementStatistics`）。在个别反作弊上
   可能表现为「空中挥击」。缓解方案（发完包补一个真实位置+`onGround=true` 的包）会清掉 `fallDistance`
   （见「故意不搬」最后一条），需要另一套顺序（先攻击包后落地包），属于要实机验证的改动，未做。
