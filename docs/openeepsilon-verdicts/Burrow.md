状态：已优化

# Burrow 对照裁决（OpenEpsilon `Burrow.kt` → 本工程 1.20.1 `BurrowHack`）

- 目标：`src/main/java/net/wurstclient/hacks/BurrowHack.java`（259 → 238 行）
- 参考：`_oe_ref/src/main/kotlin/studio/coni/epsilon/module/combat/Burrow.kt`（291 行，1.12.2 / Kotlin / MCP）
- 原生真源：本仓库自带的 1.20.2 反编译源码（下称「1.20.2 源码树」，
  `neoforge/versions/1.20.2/build/neoForm/neoFormJoined1.20.2-20231019.002635/steps/unzipSources/unpacked/net/minecraft/...`）。
- 结论：参考那套**包级时序技巧**（1.12 的一串 `CPacketPlayer.Position` 硬编码高度、假潜行、`autoCenter`
  处理 `SPacketPlayerPosLook`、`breakCrystal`）在 1.20.1 上不成立，全部不搬。
  但本次在这个 hack 里找到并修掉了 **3 个真实缺陷**：选块过滤太宽（会把火把/台阶当埋人方块用、
  白耗物品还自动关闭）、三份手写的放置几何对非满方块算错命中点且不看视线、以及
  `Disable after` 关掉之后其实仍然会自我关闭（设置失效）。改动都落在 hack 文件本身，
  没有新增纯类（剩下的部分没有可单测的独立算法）。

## 对照证据

### 表 1：参考（`_oe_ref`）↔ 本工程

| 关注点 | 参考（_oe_ref 相对路径:行） | 本工程（相对路径:行） | 判定 |
| --- | --- | --- | --- |
| 目标方块 | `Burrow.kt:132`：`override ?: world.getGroundPos(player).up()`（玩家脚下地面之上那一格） | `BurrowHack.java:79`：`BlockPos.containing(MC.player.position())`（玩家脚所在的那一格 = 同一个位置） | 等价 |
| 「已经埋好了」的判定 | `Burrow.kt:128-130`：不在`onGround` 或竖直位移过大就跳过；`tryRunTick` 返回 false 累计到 `timeoutTicks` 就 `disable()`（`:122-124`） | `BurrowHack.java:81-88`：脚下那一格不可替换就当「已完成/已在方块里」，`Disable after` 打开时关闭自己 | **本次修正**（旧代码无条件关闭，见「实际改动」第 3 条） |
| 居中判定 | `Burrow.kt:162`：`abs(posX-(x+0.5))<0.79 && abs(posZ-(z+0.5))<0.79` | 没有居中判定 | **不搬且不需要**：本工程的目标格就是 `BlockPos.containing(player.position())` 那一格，按构造 abs(Δx)≤0.5、abs(Δz)≤0.5，这个条件恒真（0.5 < 0.79）；参考需要它是因为它还会拿 `override` 指定的别的格子、而且它的 Jump 模式要把人抬起来 |
| 下方必须不可替换 | `Burrow.kt:162`：`!world.getBlockState(pos.down()).isReplaceable` | 没有显式检查（只靠玩家站在地面上这个前提） | 部分不搬：参考要它是因为它要「站在实心地面上才能塞方块」。本工程 `Mode.INSTANT` 默认直接放，没有地面检查也不会误放（下面那条「放置面」自然要求有一个可点的邻块，多数情况下就是地面）。加硬过滤会改变现有效果（例如在水里/半空中也能埋），属于删功能 |
| 「上方两格无碰撞」 | `Burrow.kt:162-163`：`pos.up(2)` 的碰撞箱必须为 null | 没有 | 不搬：本工程塞的是**自己的脚下方块**，头上一格（`pos.up(1)`）就是玩家头部所在，本来就是空的；参考多要的那一格是给它「跳起来再放」的手法预留下的高度 |
| 目标格里不能有活着的实体 / 打掉水晶 | `Burrow.kt:164-168`（`canPlace` 第二段）、`Burrow.kt:248-260`（`breakCrystal`） | 没有 | 不搬：本工程不拆水晶、也不做「实体占位」检查（放置本身不受实体阻挡，见 `EntityGetter.java:36-51`）；搬过来要连带新功能与新的攻击行为 |
| 选块（挑哪个方块） | `Burrow.kt:226-240`：只在**黑曜石 / 末影箱**里选（`Block` 设置），都没有就聊天栏报错、不动作 | `BurrowHack.java:185-216`：任何 `BlockItem`（排除 TNT/重生锚/蛛网/空气），`Prefer obsidian`（默认开）优先黑曜石/哭泣的黑曜石 | 本工程更宽（保留），但**过滤太宽是真 bug**（见「实际改动」第 1 条）；本次加的那条「必须是满方块」与参考的白名单思路一致，只是用形状而不是方块名 |
| 放置的命中点 | `Burrow.kt:262-267`：写死 `blockPos.down()` + `UP` 面 + `(0.5, 1.0, 0.5)`（1.12 的 `CPacketPlayerTryUseItemOnBlock`） | 旧代码（三处重复循环）：`Vec3.atCenterOf(neighbor) + 0.5 × 面方向` | **两边都是「假设满方块」**；本次改用共享的 `BlockPlacer.getBlockPlacingParams`（按实际形状算，见「实际改动」第 2 条） |
| 交互/挥手的写法 | `Burrow.kt:262-277`：手动发 `CPacketPlayerTryUseItemOnBlock` + `CPacketAnimation`，前后各发一次真假潜行包 | 旧代码：`RotationUtils...sendPlayerLookPacket()` + `IMC.rightClickBlock(...)`（INSTANT 模式还 `MC.player.swing`） | 本次改为 `InteractionSimulator.rightClickBlock(..., SwingHand.CLIENT)`（本工程既有的「模拟原版 `startUseItem`」工具，`InteractionSimulator.java:83-95,131-150`），挥手行为与旧代码一致（`SwingHand.CLIENT` = `MC.player.swing`，见 `SwingHandSetting.java:104`） |
| 选槽 / 换槽 | `Burrow.kt:269-277`：`ItemUtil.swapToSlot(slot)` 并还原 | `BurrowHack.java:174-181`：改 `inventory.selected` 并还原（客户端会在交互前自动补发 `ServerboundSetCarriedItemPacket`，见 `ClientPlayerInteractionManagerMixin.java:137-145` 与 `MultiPlayerGameMode.ensureHasSentCarriedItem`） | 等价（本工程沿用原写法，未动） |
| 模式 | `Burrow.kt:148-151,283-285`：`Instant` / `Jump` | `BurrowHack.java:31-35,218-236`：`INSTANT` / `SMOOTH` / `RUBBERBAND`（默认 INSTANT） | 命名与实现都不同；本工程三个模式保留不动 |
| 1.12 的手工位置包序列 | `Burrow.kt:205-219`：`position.y + 0.41999808688698 / 0.7500019 / 0.9999962 / 1.17000380178814 / 1.2426308013947485 / 2.3400880035762786 / 3.3400880035762786 / -1.0` | 本工程 SMOOTH/RUBBERBAND 只做一次 `+0.42`（`BurrowHack.java:127-132,146-149`） | 不搬（那些常数是 1.12 反作弊的穿墙值，1.20.1 服务端会按 `handleMovePlayer` 拉回） |
| 居中/拉回的处理 | `Burrow.kt:74-88`：收到 `SPacketPlayerPosLook` 且 `autoCenter` 时把玩家居中到方块中心 | 没有 | 不搬（1.20.1 对应 `ClientboundPlayerPositionPacket`；把玩家位置直接改成方块中心是明显的位移改写，需要实机验证，超出本次范围） |
| 位移清零 | `Burrow.kt:153-156`：`motionX/Z = 0` + `cancelMotion` 监听里清零 | 没有 | 不搬（本工程不在 `PlayerMoveEvent` 层拦截移动） |
| 失败重试上限 | `Burrow.kt:41`（`Timeout Ticks` 默认 10）+ `:122-124`（累计到上限就 `disable()`） | 没有（`BurrowHack.java:76-111` 每 tick 重试到成功或设置关闭） | 部分不搬：本工程 `INSTANT` 默认放完就关，不会无限重试；只有用户手动关掉 `Disable after` 且放置持续失败时才会每 tick 重试一次（与旧行为相同）。加计数上限要动设置或新增私有常量，见「建议（未做）」 |
| 设置项 | `Block`/`breakCrystal`/`autoCenter`/`timeoutTicks`/`timer`/`rubberY`…（`Burrow.kt:38-45`） | `Mode`/`Prefer obsidian`/`Disable after`（`BurrowHack.java:31-41`） | 不增不减、不改名、不改默认值 |

### 表 2：1.20.2 源码树（原生真源）核对

| 事实 | 出处（1.20.2 源码树行） | 本次用在哪 |
| --- | --- | --- |
| 放置时要求「目标格没被实体挡住」，但只统计 `blocksBuilding == true` 的实体；玩家为 false，所以可以把方块放进自己脚下 | `.../world/level/EntityGetter.java:36-51`、`.../world/entity/Entity.java:150`、`.../world/item/BlockItem.java:150-155` | 说明这个 hack 的成立前提；也说明「实体占位检查」不需要（表 1 第 6 行） |
| `BlockItem.mustSurvive()` 默认 true → 放火把这类方块时真的会走 `canSurvive`（火把要求下方是实心支撑面） | `.../world/item/BlockItem.java:150-159`、`.../world/block/TorchBlock.java:39-42`（`canSurvive` = `canSupportCenter(level, pos.below(), Direction.UP)`） | 「旧实现可能选中火把」这条结论的支撑之一（火把能被放进脚下那一格） |
| `BlockState.isCollisionShapeFullBlock(BlockGetter, BlockPos)` 判的是**碰撞形状**是不是整块 | `.../world/level/block/state/BlockBehaviour.java:306-307`（`Block.isShapeFullBlock(getCollisionShape(...))`）、缓存分支 `:843-844,912` | `BurrowHack.java:199-205` 的选块过滤 |
| 本工程已有同样的「必须是满方块/整块」过滤写法 | `src/main/java/net/wurstclient/hacks/TunnellerHack.java:492-497`、`src/main/java/net/wurstclient/hacks/ScaffoldWalkHack.java:236-239` | 照抄这两个邻居的写法（`block.defaultBlockState()` + `EmptyBlockGetter.INSTANCE` + `BlockPos.ZERO`） |
| 共享的放置几何：按方块实际 shape 算命中点、跳过可替换的邻块、优先有视线的面、给出 `toHitResult()` | `src/main/java/net/wurstclient/util/BlockPlacer.java:167-273`（shape 命中点 `:192-211`、视线/距离 `:221-239`、选面 `:241-264`、记录 `:275-282`） | `BurrowHack.java:158-183` 的 `placeBurrowBlock` |
| 共享的交互一行式：复刻 `Minecraft.startUseItem()`，按 `InteractionResult.shouldSwing()` 决定挥手 | `src/main/java/net/wurstclient/util/InteractionSimulator.java:83-95,131-150` | 同上 |

## 实际改动

### 1. `findBlockSlot()` 加上「必须是满方块」这一条（`BurrowHack.java:199-205`）

**旧行为（bug）**：只要槽位里是 `BlockItem` 且不是 TNT/重生锚/蛛网/空气就收。具体输入：
快捷栏第 0 格放火把（或石台阶、雪层、栅栏），第 1 格没有黑曜石，其余格子什么都行，
`Prefer obsidian` 打开（默认）→ `findBlockSlot()` 返回 0 → `placeBurrowBlock` 把**火把**放进
玩家脚下那一格（火把的 `canSurvive` 只要求下方有支撑面，而玩家脚下是地面，所以放得上去，
见 `BlockItem.java:150-159`）→ 物品被消耗一格、脚下那一格变成火把、玩家并没有被围住，
而 `Disable after` 默认打开，hack 就此关闭，用户看到的是「hack 自己关了但人没埋」。
`Prefer obsidian` 关闭时同样会先撞上火把（它拿的是第一个 `BlockItem`）。

**新行为**：用本工程两个邻居已有的写法
（`TunnellerHack.java:492-497`、`ScaffoldWalkHack.java:236-239`）：
`block.defaultBlockState().isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)`
为 false 的直接跳过。同样输入下 → 火把被跳过；如果整条快捷栏只有火把，`findBlockSlot()`
返回 −1 → `BurrowHack.java:90-95` 的既有分支直接关闭 hack，**不消耗任何物品、也不假装成功**；
如果后面还有黑曜石/圆石，则用它正常埋。

**能验证什么**：这是「错误启发式 → 正确的量化规则」（整块碰撞 = 能围住人）。副作用是
`haste`/`dirt path` 这类 15/16 高的方块也从可用集合里去掉了——它们同样围不住玩家，属同向收紧。
另一个如实说明的副作用：无效方块被拒后 `findBlockSlot()` 会返回 −1，于是走到 `BurrowHack.java:90-95`
那条既有分支，**即使 `Disable after` 关着也会关闭 hack**（旧代码会留下「已开启但每 tick 重扫」
的状态）。这是有意的——手里没有能用的方块时留在开启状态只会每 tick 白扫一遍——
但它确实是与旧行为不同的一处。

### 2. 三份重复的手写放置循环 → 共享的 `BlockPlacer.getBlockPlacingParams`（`BurrowHack.java:158-183`）

**旧行为（bug/隐患）**：`INSTANT`/`SMOOTH`/`RUBBERBAND` 三个模式各自抄了一遍
「遍历 `Direction.values()`，取第一个 `BlockUtils.canBeClicked(neighbor)` 的邻块，
命中点 = 邻块中心 + 0.5 × 面方向」。具体输入：玩家站在一块**下半砖**上（下方是台阶），
脚下那一格是空气，其余五面都是空气 → 邻块只有 DOWN 合格，`hitVec = Vec3.atCenterOf(台阶) + 0.5×UP`
= (x+0.5, y, z+0.5)，而台阶的顶面在 y−0.5 —— 这个命中点在台阶上方 0.5 格的**空气里**，
既不是方块表面，也没有做视线/可达性检查。同时因为没检查 `canBeReplaced()`，当唯一的合格邻块
是**可替换**方块（例如旁边的草）时，点击会落在那个可替换方块上，方块被放进邻格而不是脚下那一格
（旧代码只在 `canBeClicked` 为真的邻块里取第一个，`Direction.values()` 顺序是 DOWN 优先，
所以这个错位需要地面不可点（半空中/水里）才出现）。

**新行为**：统一走共享设施 `BlockPlacer.getBlockPlacingParams(pos)`
（`BlockPlacer.java:167-273`）：按方块**实际 shape** 算命中点（`:192-211`）、跳过可替换的邻块
（`:199-200`）、优先有视线的面（`:221-239,251-259`）、并给出 `toHitResult()`；交互改用
`InteractionSimulator.rightClickBlock(params.toHitResult(), SwingHand.CLIENT)`
（复刻原版 `startUseItem` 的挥手判定，`InteractionSimulator.java:131-150`）。
同样输入下 → 命中点变成台阶顶面中心 (x+0.5, **y−0.5**, z+0.5)（真的落在面上）；
「唯一邻块是可替换方块」时不再误放，而是 `params == null` → 直接不交互。
常见情形（下方是满方块地面）下选中的仍是 DOWN 面、命中点与旧代码逐位相同
（`Vec3.atCenterOf(neighbor) + 0.5` 对满方块就等于 shape 版的中心+半高），所以正常场景行为不变。

**能验证什么**：这是「把错误的几何假设换成共享设施里正确的量化几何」，而且是删重复
（三份循环合一），不是换个写法。

### 3. `Disable after` 关掉之后不再是死的（`BurrowHack.java:81-88`）

**旧行为（bug）**：`onUpdate` 第一段是
`if(!BlockUtils.getState(playerPos).canBeReplaced()) { setEnabled(false); return; }` —— 无条件关闭。
具体输入：`Disable after` **不勾**，开启 Burrow，埋成功后（脚下那一格变成方块）→ 下一 tick
走这一支 → 旧代码照样 `setEnabled(false)`。也就是说这个设置只管 `doInstantBurrow` 末尾那一处，
「埋完之后」这条常规路径上它是失效的。

**新行为**：同样输入 → 因为 `disableAfter.isChecked()` 为 false，hack 保持开启并空转
（这一支不做任何交互，不会发包）；等玩家被挖出来（脚下那一格又能替换）时它会再埋一次——
这正是这个设置名字的意思。勾着时行为与旧代码完全一致（关闭）。

**能验证什么**：设置语义恢复（`CheckboxSetting` 的说明是 "Disables after burrowing."，
`BurrowHack.java:40-41`）。注意这条不会引入重试风暴：`Disable after` 关掉、放置失败时
新旧都是每 tick 重试一次（旧代码在放置失败后也走同一个末尾分支）。

### 4. 顺带清理

- 删掉 `doInstantBurrow` 里从未使用的 `Vec3 posVec = Vec3.atCenterOf(pos);`（旧 `:112`）。
- import 调整：删 `Direction`/`InteractionHand`/`Vec3`，加 `EmptyBlockGetter`/`BlockState`/
  `SwingHand`/`BlockPlacer`/`BlockPlacingParams`/`InteractionSimulator`。
- 未动：`Mode`/`Prefer obsidian`/`Disable after` 三个设置的名称、默认值、说明；
  `stage` 状态机与三个模式的时序；`getRenderName`；`onEnable/onDisable`。

## 故意不搬

| 参考里的东西 | 为什么不搬 |
| --- | --- |
| `Burrow.kt:205-219` 的一串硬编码高度包（`0.41999808688698`…`3.3400880035762786`） | 1.12 NCP/AAC 的穿墙时序值。1.20.1 的 `handleMovePlayer` 会按服务端位置复核并拉回，这串数字没有意义；本工程 SMOOTH/RUBBERBAND 只做一次 `+0.42`，是它自己的做法（本次未改）。 |
| `Burrow.kt:264-275` 的假潜行（`START_SNEAKING`/`STOP_SNEAKING`） | 1.12 里潜行 + 右键是为了绕开一些交互；1.20.1 的放置不需要，多发包只会更像外挂。 |
| `Burrow.kt:74-88` `autoCenter` + `SPacketPlayerPosLook` + `EntityUtil.centerPlayer` | 需要监听 1.20.1 的 `ClientboundPlayerPositionPacket` 并直接改写玩家坐标（把玩家吸到方块中心），是明显的行为改变，而且必须实机验证是否会被服务端再拉回。 |
| `Burrow.kt:164-168,248-260` 实体占位检查 + `breakCrystal` | 本工程不拆水晶；放置本身不受实体阻挡（`EntityGetter.java:36-51` 只统计 `blocksBuilding` 实体），所以这条检查在本工程里没有对应的必要动作。搬 `breakCrystal` 等于新增一个自动攻击功能。 |
| `Burrow.kt:162` 的 `pos.up(2)` 两格净空 | 见「表 1 第 4 行」：本工程塞的是自己的脚下方块，头上一格本来就是空的，多要的那一格是给参考的 Jump 模式预留的。 |
| `Burrow.kt:162` 的 0.79 居中判定 | 见「表 1 第 3 行」：本工程的 `BlockPos.containing(player.position())` 让 abs(Δ)≤0.5 恒成立，这个条件在本工程里恒真、等于没写；参考的 0.79 本身也是「0.5 + 玩家半宽 0.3」的经验值。 |
| `Burrow.kt:226-240` 的「只认黑曜石/末影箱」白名单 | 本工程一直支持任何 `BlockItem`（`BurrowHack.java:185-216`），照搬会删掉用户可用方块。本次只加「必须真能围住人」的形状条件。 |
| `Burrow.kt:41,122-124` 的 `Timeout Ticks` 设置 | 不新增设置（任务约束），而且本工程 `INSTANT` 默认放完即关，不存在参考那种「一直不满足 `onGround`→ 一直等」的状态；见「建议（未做）」。 |
| `Burrow.kt:128` 的 `player.onGround` 硬性前提 | 本工程的三个模式都不要求站在地面上（`RUBBERBAND` 明确要在空中/下落时用）。加这个前提会改变现有效果。 |

## 建议（未做）

- **放置失败的重试上限**：`Disable after` 关掉且放置持续失败（例如脚下那一格一直是空气而
  四周都不可点）时，本工程会每 tick 重试一次。参考的做法是 `Timeout Ticks`（默认 10）
  累计到一个上限就 `disable()`（`Burrow.kt:41,122-124`）。要照做需要新增一个私有常量或新设置
  （任务不允许增设置），而且与「用户主动关掉自动关闭」的意图有冲突，故只记录。
- **`onGround`/居中这一类前提没有加**：上面说了为什么不搬；如果以后实机发现某些服务器
  对「空中埋方块」有额外判定，那时再补地面检查更合适（需要实机证据，超出本次范围）。
- **`ServerboundSetCarriedItemPacket` 依赖**：本工程靠 `inventory.selected = slot` +
  客户端在交互前自动补发选槽包（`ClientPlayerInteractionManagerMixin.java:137-145`）。
  这在原版 1.20.1 客户端成立；如果将来要支持「服务端只认显式选槽包」的场景，需要在这里
  显式发一次包（属于另一件事）。
- **`SMOOTH`/`RUBBERBAND` 的时序没有实机验证**：本次只动了它们内部那次放置调用的实现
  （改成共享设施），模式本身的 `stage` 与 `+0.42` 包序列保持原样；这两个模式（以及
  `RUBBERBAND` 里 `MC.player.setPos` 与后续 `ServerboundMovePlayerPacket` 的一致性）需要实机确认。

## 测试

本次**没有新增单测**：剩下的改动分别是「调用本工程既有的共享放置几何」
（`BlockPlacer.getBlockPlacingParams`，已有实现、非新算法）、「用 `isCollisionShapeFullBlock`
过滤方块」（一行纯 API 判定）与「让设置真正生效」（分支条件），没有可独立单测的纯算法，
按任务要求「不要为了看起来重构过而抽没有消费方的抽象」，故不新建纯类。
验证方式是逐个打开文件核对 API（见「表 2」）并手工推演三组「旧 vs 新」输入（见「实际改动」）。

## 未验证项（如实说明）

- 按任务约束**没有运行 gradle/maven/git**，所以 `BurrowHack.java` 没有经过编译；本次用到的
  每个 API（`BlockPlacer.getBlockPlacingParams`/`BlockPlacingParams.toHitResult`/
  `InteractionSimulator.rightClickBlock`/`SwingHand.CLIENT`/`isCollisionShapeFullBlock`/
  `EmptyBlockGetter.INSTANCE`）都是在「表 2」列出的文件里逐行核对过的。
- 「旧实现会把火把放进脚下」这条结论是从原版代码链路推出来的
  （`BlockItem.java:150-159` 的 `mustSurvive` → 火把 `canSurvive` 只看下方支撑），
  没有实机放置验证；但即使火把因为别的原因放不上去，「白耗一次交互 + 自动关闭 + 人没埋」
  这个结果同样成立。
