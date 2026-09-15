状态：已重构

## 对照证据

参考侧是 `_oe_ref/src/main/kotlin/studio/coni/epsilon/module/misc/AntiBot.kt`（注意：**不在** `module/impl/combat/` 下，
参考的 combat 目录里根本没有 AntiBot，它在 `module/misc/`）。

### 1. 判据逐条比对

| 关注点 | 参考（_oe_ref 相对路径:行） | 本工程（相对路径:行，改动前） | 判定 |
| --- | --- | --- | --- |
| tab 列表成员 | `misc/AntiBot.kt:126-134`：按**显示名**与 tab 里每个条目的名字做 Equals/Contains 匹配，匹配不到才算 bot；`tabValue` 默认 **true** | `hacks/AntiBotHack.java:131-132`：`PlayerInfo == null` 即 bot，默认 true；用 UUID 查表而不是名字 | 等价且更稳（UUID 不会因计分板队伍前缀 / 颜色码而对不上）。参考的 `contains` 反而会把「名字是别人子串」的真人放过 |
| tab 条目无游戏模式 | 参考无此判据 | `AntiBotHack.java:133-134`，默认 true | 本工程独有；语义正确 |
| ping 为 0 | `misc/AntiBot.kt:121-123`：`getPlayerInfo(uuid).responseTime == 0`，默认 false，且没查空指针（查不到 tab 条目直接 NPE） | `AntiBotHack.java:135-136`：`info != null && getLatency() <= 0`，默认 false | 等价，且本工程不会 NPE |
| **无敌帧 / 伤害历史** | 参考 **没有**。它跟踪的是「挨打历史」：`AntiBot.kt:49-56`（记在 `:53`）拦 `CPacketUseEntity(ATTACK)` 记 `hitted`，配 `needHitValue`("NeedHit", `:35`, 默认 false) 判「没被我打过的就是 bot」（消费点 `:124`） | 无 | **不搬**。这是「打到才算真人」的反向判据，第一次出手前人人都是 bot，本工程又是每 tick 重建列表，搬过来只会让 combat hack 集体不打人 |
| **移动模式 / 位置历史** | `AntiBot.kt:40-45,57-69,125`：拦 `SPacketEntity` 记 `ground`/`air`（该实体是否**曾经**上报过落地 / 悬空）、并用 `invalidGround` 计数器（落地却 `prevPosY != posY`，每 tick +1、悬空时 -1）判 `>= 10`；对应 `groundValue`(:97,默认 **true**)、`airValue`(:27,默认 false)、`invalidGroundValue`(:28,默认 true) | 只有单 tick 的 `checkGround`：`onGround() && |deltaY| > 0.1`（`AntiBotHack.java:137-139`，默认 false） | **部分搬**：把「落地却仍有竖直位移」由单 tick 观测升格为连续成立（见「实际改动」第 1 条）。参考的 `ground`/`air` 两兄弟是「从没落地过/从没悬空过」，语义脆弱（挂机玩家一整局不动就会命中），不搬 |
| 挥臂（swing）历史 | `AntiBot.kt:74-77,113`：拦 `SPacketAnimation(type=0)` 记 `swing`，没挥过臂算 bot，默认 false | 无 | 不搬。本工程没有包监听层，且「没在我面前挥过手」会把挂机真人判成 bot |
| 隐身 | `AntiBot.kt:70,117`：`wasInvisibleValue` 是「**曾经**隐身过」，默认 **false** | `AntiBotHack.java:140-141`：`isInvisible()`（当前隐身），默认 **true** | **本工程更激进**：默认就开，任何喝隐身药水的真人在药水期间会被排除出所有 combat hack 的目标（详见「误判风险」F1） |
| 俯仰角 | `AntiBot.kt:116`：`rotationPitch > 90 || < -90`，默认 **true** | `AntiBotHack.java:142-143`：`|getXRot()| > 90`，默认 true | 语义等价 |
| 生命值 | `AntiBot.kt:114`：`health > 20F`（写死 20），默认 false | `AntiBotHack.java:144-147`：`!isFinite || < 0 || > getMaxHealth()`，默认 true | 本工程更严密（参考对 30 血上限的服务器会误判，本工程用实体自己的 `getMaxHealth()`） |
| 实体 ID | `AntiBot.kt:115`：`>= 1000000000 || <= -1`，默认 true | `AntiBotHack.java:148-150`：`< 0 || > 1_000_000_000` | 等价（边界差 1 无实际影响） |
| 重名（世界内） | `AntiBot.kt:135`：`loadedEntityList.filter{...}.count() > 1` | `AntiBotHack.java:151-153`：名字计数 > 1，默认 true | 参考这一行**是坏的**：lambda 里外都叫 `it`，于是 `it.displayNameString == it.displayNameString` 恒真，只要世界里有 2 个以上玩家就全员判 bot（`duplicateInWorldValue` 默认 false 所以没炸）。本工程实现才是对的，**不能照搬** |
| 重名（tab 内） | `AntiBot.kt:137-139`，默认 false | 无 | 不搬（世界内重名已覆盖，且 tab 重名会因 NPC 插件大面积误判） |
| 名字为空 / 等于自己 | `AntiBot.kt:140`：`entity.name.isEmpty() || entity.name == mc.player.name` | 无 | 不搬。1.20.1 客户端玩家不可能有空名字；「与我同名」在正版服不可能，离线服会误判 |
| UUID 模式 | 参考无 | `AntiBotHack.java:157-160`，默认 true | 本工程独有 |
| 颜色码 | `AntiBot.kt:109`：显示名里没有 `§` 算 bot，默认 false | 无 | 不搬（1.20.1 用 `getGameProfile().getName()` 天然无颜色码） |
| 护甲为空 | `AntiBot.kt:118-120`，默认 false | 无 | 不搬（裸装真人满地都是） |
| 最小存在时间 | `AntiBot.kt:110`：`ticksExisted < livingTimeTicksValue`，默认 false | `AntiBotHack.java:154-155`：`tickCount < minimumAge`，默认 **5 ticks** | 两边都是「太年轻就算 bot」；本工程默认开着，于是有 F2 那个洞 |

### 2. 列表维护与消费方

| 关注点 | 参考 | 本工程（改动前） | 判定 |
| --- | --- | --- | --- |
| 列表结构 | 6 个 `Int` 列表（`ground`/`air`/`invalidGround`/`swing`/`invisible`/`hitted`，`AntiBot.kt:40-45`），只在 `onDisable` 清空（`:144-146,157-163`） | `Set<UUID> detectedBots`，每 tick 由 `MC.level.players()` 全量重建（`AntiBotHack.java:113-126`），换世界 / 关 hack 清空 | 本工程更不容易泄漏 |
| 实体死亡 / 卸载是否泄漏 | **会**：`invalidGround` 等按 `entityId` 累积，只有关 hack 才清；换世界、实体卸载都不清 | **不会**：每 tick `clear()` + 只装当轮在线的玩家；实体一卸载就不在 `players()` 里，下一 tick 自动消失 | 本工程已正确 |
| `isBot()` 有没有消费方 | 有 6 处：`module/setting/CombatSetting.kt:254`、`module/render/Tracers.kt:47`、`module/render/Nametags.kt:103,276`、`module/combat/TargetStrafe.kt:58`、`module/combat/KillAura.kt:291`、`module/combat/AutoLog.kt:96` | **有，而且是最关键的一处**：`util/EntityUtils.java:45` 把它编进 `IS_ATTACKABLE`；该谓词被 `CombatTargetUtils.java:58,165`、`TpAuraHack.java:118`、`TargetStrafeHack.java:114`、`BowAimbotHack.java:180`、`ProtectHack.java:213`、`FightBotHack.java:141`、以及 `AutoSwordHack.java:87` 消费 | **不是死代码**，但对整个包是单点：一次误判 = 所有 combat hack 同时不打这个人（见下） |

### 3. 误判风险（本工程判据可能把真人当 bot）

默认值下打开的判据是：`checkPlayerInfo`、`checkGameMode`、`checkInvisible`、`checkUuid`、
`checkIllegalPitch`、`checkIllegalHealth`、`checkEntityId`、`checkDuplicateName`、`minimumAge`。

| 编号 | 判据 | 误判场景 | 处置 |
| --- | --- | --- | --- |
| **F1** | `checkInvisible`（默认 true，`AntiBotHack.java:140-141`） | **喝隐身药水的真人**：1.20.1 隐身是正常机制，药水期间该玩家会被 `IS_ATTACKABLE` 排除，Killaura / ClickAura / TriggerBot / AutoSword … 全部不打他。参考的同类设置默认是 **false**（`AntiBot.kt:32`，而且是更弱的「曾经隐身」） | **只记录，不改默认值**（brief 第 5 条：不许改 setting 默认值）。这是当前默认配置下**最高频**的真人漏打来源，用户应在设置里关掉它 |
| **F2** | `minimumAge`（默认 5 ticks，`AntiBotHack.java:154-155`） | 任何**刚进服务器 / 刚被传送进视野**的真人，前 5 tick（250 ms）被判成 bot；如果 combat hack 恰好在窗口内取目标，就会跳过他 | **保持原样**（本轮曾改成 `TOO_YOUNG`，经父 agent 复核为行为回退，已撤销，见下） |
| **F3** | `checkGround`（默认 false，改动前的 `AntiBotHack.java:137-139`） | 单 tick 观测 `onGround() && |deltaY| > 0.1`。客户端位置插值、载具 / 船（`ClientboundMoveVehiclePacket` 不带 onGround 位）、被活塞推动、丢包，都能让真人某一 tick 落进这个组合；判定阈值 0.1 又低于原版重力步进（`0.08 * 0.98`），一次跳跃的落地 tick 附近也可能命中 | **已修**（见「实际改动」第 1 条：连续 2 tick 才认定） |
| **F4** | `checkDuplicateName`（默认 true） | 服务器上的 NPC 插件（Citizens 等）常复制真人名字；两个同名 NPC 会**同时**被判 bot。参考把同名判据默认关掉（`duplicateInWorld` / `duplicateInTab` 都是 false） | 只记录。语义本身是「名字撞车」，改它会重新定义设置含义，留待用户决定 |
| F5 | `checkPlayerInfo`（默认 true） | 加入 / 重连时 tab 条目可能比实体晚到 1 tick，此窗口内真人会被判 bot | 影响极小（`minimumAge` 窗口已覆盖同一段时间），不改 |
| — | `checkPing`（默认 false） | 局域网 / 单机主机玩家延迟确实是 0 | 默认关闭，无影响 |

结论：默认配置下 **F1 是最大风险且未修（属设置默认值，不许动）**，F2/F3 是两个能修的误判源，已修。

## 实际改动

1. **`checkGround` 由单 tick 观测升格为连续成立**（新文件 `util/AntiBotPredicate.java`、
   `util/AntiBotTracker.java`；`AntiBotHack.snapshot` 接线）。原判据只看这一 tick，真人只要有一 tick
   落进「onGround 为真 + deltaY 非零」就被整个客户端当成 bot；现在要求连续
   `IMPOSSIBLE_GROUND_GRACE_TICKS = 2` 个 tick 成立。**默认配置下 `checkGround` 是关的，所以这条改动对默认用户
   零影响**；打开该判据的用户从「可能漏打真人」变成「只漏打持续伪造竖直位移的假人」。
   可验证：`AntiBotTrackerTest`（8 个用例：第 N 个连续 tick 才成立、一次干净 tick 清零、按玩家独立、下线即丢、reset、grace 下限 1、null UUID）与
   `AntiBotPredicateTest.impossibleGroundIsFirstDerived`（阈值边界 0.1 不算、0.42 算、不落地不算）。
2. **（已撤销，留档）`minimumAge` 与「是 bot」解耦**：本轮一度新增 `Verdict.TOO_YOUNG`，让「太年轻」不再返回
   `BOT`。**父 agent 复核后判定这是行为回退，已改回 `BOT`，枚举值与用例一并删除。** 理由：
   - `AntiBotHack.isBot()`（`AntiBotHack.java:190-198`）**只读** `detectedBots`，而
     `util/EntityUtils.IS_ATTACKABLE` 只问 `isBot()`（`EntityUtils.java:45`）。所以「不返回 BOT」的真实后果
     不是「不标注」，而是**刚进服的真人立刻可被所有 combat hack 攻击**——与设置项说明
     「Players younger than this many ticks are temporarily ignored（暂时忽略）」正好相反。
   - `detectedBots` 的唯一消费方就是 `isBot()`（全仓库 grep 确认：只有 `AntiBotHack.java:192,197`），
     所以「不进名单但行为不变」这个说法在本工程里不可能成立：名单就是行为本身。
   - 若要真做到「暂时忽略但不标注」，必须让 `isBot()` 也参与最小年龄判断（而 `isBot(UUID)` 拿不到 tickCount），
     属于要改公开 API 的改动，且没有任何消费方受益，因此不做。当前实现回到与原版逐字节相同的语义。
   回归锁定：`AntiBotPredicateTest.treatsTooYoungAsBotSoCombatStillIgnoresThem`（tick 0/4 → `BOT`，tick 5 → `HUMAN`）。
3. **判定逻辑整体抽成纯类 `AntiBotPredicate`**（判据顺序与原 `isBot` 逐条一致，见
   `AntiBotPredicate.classify` 的 if 链 vs 原 `AntiBotHack.java:131-160`），并把 10 个开关装进
   `AntiBotSettings` record。收益只有一个但很实际：这些判据以前**无法单测**（全部内联在 hack 里、依赖
   `Player`/`PlayerInfo`），现在每条判据都有用例（`AntiBotPredicateTest` 共 11 个用例）。没有引入任何
   新抽象层给外部用——`AntiBotPredicate` 只有 `AntiBotHack` 一个调用方。
4. **跨 tick 状态不会泄漏**：`AntiBotTracker` 每 tick `retainOnly(当轮在线 UUID)`，玩家下线 / 换世界 /
   关 hack 立即丢弃；用例 `AntiBotTrackerTest.dropsOfflinePlayers` / `resetClearsEverything`。
   （说明：改动前的 `detectedBots` 本身也不泄漏，这一步是为了新加的「连续成立」状态不引入泄漏。
   另外新代码统一用 `player.getGameProfile().getName()` 取名字，不再依赖显示名，重名判据与参考
   `AntiBot.kt:128,152-154`（`stripColor` 后取档案名）语义对齐，但 UUID→档案名比字符串剥色更可靠。）

`isBot()` 的两个公开重载（`AntiBotHack.java:191-199`）签名、语义、消费方（`EntityUtils.java:45`）均未改动。

## 故意不搬

- `NeedHit` / `hitted` 挨打历史（`AntiBot.kt:35,49-56,124`）：第一次出手前人人命中，而本工程每 tick 重建名单，
  搬过来等于「不先打一下就不许打」。
- `Swing` 挥臂历史（`AntiBot.kt:29,74-77`）：挂机、蹲坑、只放方块的真人都不挥臂；而且需要包监听层。
- `Ground` / `Air`「从未落地 / 从未悬空」（`AntiBot.kt:26-27,40-45,111-112`）：整局不下地的真人（坐船、
  鞘翅、站在方块上跳）会被误判，且两者同时打开互斥。
- `invalidGround` 的 ±1 / /2 计数器（`AntiBot.kt:64-69,125`）：它是一个「每 tick 累计、悬空时折半」的软计数，
  真正让它成立的阈值是 10，等价于「持续 10 tick 的同一现象」；本工程用的是连续 tick 判定，不需要两级计数。
- `DuplicateInWorld`（`AntiBot.kt:135`）：参考这一行的 lambda 变量遮蔽使它恒真（见上表），是 bug 不是特性。
- `DuplicateInTab`、`Color`、`Armor`、`LivingTime` 之外的其余开关：反复核对后都属于「真人常态也会命中」的一类。
- `remove`（`AntiBot.kt:19,81-98`）：参考会**从世界里删掉**被判 bot 的实体（只在客户端生效，还会让实体模型
  凭空消失）。本工程只做「不打」，这是更安全的选择，不搬。

## 建议（未做）

1. **`checkInvisible` 的默认值 true 建议改为 false**（与参考 `AntiBot.kt:32` 一致）。这是默认配置下唯一
   还在生效的高频误判源（F1：隐身药水的真人被所有 combat hack 漏打）。我按 brief 第 5 条没有动它，
   因为它会改变已有用户配置的语义。
2. **`checkDuplicateName` 建议降级为「打日志 / 只标不排除」**：NPC 插件与多个离线服同名账号会大面积命中（F4）。
3. `AntiBotTracker` 的 `graceTicks` 目前写死 2（`AntiBotHack.IMPOSSIBLE_GROUND_GRACE_TICKS`）。如果实测发现
   载具 / 高延迟下仍会误判，可以把它提成一个隐藏的 SliderSetting——但那要新增设置项，本次不做。
4. 若将来有别的 hack 想显示「这是 bot」标记（参考的 `Nametags.kt:276` / `Tracers.kt:47` 那样），
   直接消费 `isBot()` 即可。注意 `detectedBots` 里同时包含「判据命中」与「太年轻（< minimumAge）」两类，
   两者都会让 combat hack 不打他——这与本轮之前的语义一致，是刻意保留的（见「实际改动」第 2 条）。
