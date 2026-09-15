状态：已优化

## 对照证据

参考项目 `_oe_ref/src/main/kotlin/studio/coni/epsilon/module/` 下**没有 SelfTrap 模块**（combat 只有
`AutoTrap.kt` / `Surround.kt` / `Burrow.kt`，没有 SelfTrap / Self Trap / cocoon 之类），本工程这个模块是
Wurst 原生模块。所以下表的「参考侧」对照的是参考里**解决同一个问题的两条实现**——AutoTrap 的
`placeBlockInRange`（给别人下笼）与 Surround 的 `checkColliding`（给自己环绕）——它们和本工程
`AutoTrapHack` / `SurroundHack` 是同一件事的三种写法。

### 1. 「放不上去的坐标被当成放上去了」——本模块还残留这一条

| 关注点 | 参考（_oe_ref 相对路径:行） | 本工程（相对路径:行） | 判定 |
| --- | --- | --- | --- |
| 候选格是否被实体占住 | `module/combat/AutoTrap.kt:247-252`：放进一格前先 `world.getEntitiesWithinAABBExcludingEntity(null, AxisAlignedBB(pos))`，除掉落物/经验球外任一实体即 `return false`（照抄 1.12.2 原版 `World#checkNoEntityCollision`） | **改动前**：只有自身碰撞箱检查（旧 `SelfTrapHack.java:117-118`），没有实体占用检查 → **缺口**<br>**改动后**：`SelfTrapHack.java:121-122`（调用）+ `:134-164`（`isOccupiedByEntity`） | **已补**（本次改动） |
| 同一道守卫在兄弟模块里的现状 | `module/combat/Surround.kt:322-328` `checkColliding`：`isEntityAlive && preventEntitySpawning && entityBoundingBox.intersects(box)` | `AutoTrapHack.java:104-106,177-182`；`SurroundHack.java:141-144,229-233`；`HoleFillerHack.java:94,143-147`（走 `HoleFillPolicy.isOccupiedBy`） | 这两处第 12 轮已补，SelfTrap 是漏掉的那个 |
| 「方块真的放上去了吗」 | `AutoTrap.kt:298` 点击后无条件 `return true`（参考在这里同样不确认）；但 `Surround.kt:117-138,285-296` 用 `SPacketBlockChange` + `pendingPlacing/placed` 做**确认** | `BlockPlacer.java:53-58` 只查「在世界内 + 可替换」，`:60-89` 找到可点面就发交互包并 `return true`（`:86-88`）；`SelfTrapHack.java:166-181` 直接采信这个返回值 | 参考两条路各有一处：一条前置检查、一条事后确认；SelfTrap 原先两者都没有 → 这就是 bug 的根 |
| 因而产生的错误动作 | — | 旧循环 `SelfTrapHack.java:112-128`：被占住的格子 `placeBlock()` 返回 true → 当场 `break`（旧 `:123`）→ 后面未补的格子永远轮不到，且 `placed` 恒为 true，旧 `:127-128` 的自动关闭永远不会触发 | **已修**：被占住的格子改为 `continue`（`:121-122`） |

### 2. 1.20.1 服务端到底为什么拒绝

| 关注点 | 参考（_oe_ref 相对路径:行） | 本工程（相对路径:行） | 判定 |
| --- | --- | --- | --- |
| 服务端放置路径 | 1.12.2 `World#checkNoEntityCollision`（`AutoTrap.kt:248` 直接照抄此判定） | 本地可读的反编译源（仓库内 1.20.2，放置逻辑与 1.20.1 同）：`neoforge/versions/1.20.2/build/neoForm/neoFormJoined1.20.2-20231019.002635/steps/unzipSources/unpacked/net/minecraft/world/item/BlockItem.java:150-155` → `.../world/level/CollisionGetter.java:29-32` → `.../world/level/EntityGetter.java:36-51` | 判定链完整成立 |
| 拒绝条件 | 实体碰撞箱与格子相交即不能放 | `EntityGetter.java:40-44`：`!isRemoved() && blocksBuilding && Shapes.joinIsNotEmpty(方块形状, 实体碰撞箱)` → `isUnobstructed` 为 false；`EntityGetter.java:32-34` 的实体查询用 `EntitySelector.NO_SPECTATORS` | 与参考同义 |
| 哪些实体算 `blocksBuilding` | — | `.../world/entity/LivingEntity.java:240`（`this.blocksBuilding = true`）+ 船 `vehicle/Boat.java:96`、矿车 `vehicle/AbstractMinecart.java:99`、盔甲架 `decoration/ArmorStand.java:771`、末影水晶 `boss/enderdragon/EndCrystal.java:31`、TNT `item/PrimedTnt.java:26` | 本次过滤取「非旁观者 + 非已移除 + `LivingEntity`」，与 `AutoTrapHack.java:177-182` 逐字一致（玩家和生物是实战里唯一会出现的占用者） |
| 这几条在 1.20.1 上确实存在 | — | `%USERPROFILE%\.gradle\caches\forge_gradle\minecraft_repo\versions\1.20.1\client_mappings.txt:78526-78528`（`isUnobstructed(Entity,VoxelShape)` / `(BlockState,BlockPos,CollisionContext)` / `(Entity)`）、`:58174`（`blocksBuilding` 字段） | 是（1.20.1 侧以官方映射为证） |
| 玩家自己的身体也会挡 | — | `CollisionGetter.java:31` 给 `isUnobstructed` 传的是 **null**，即**不排除放置者**；所以「自己站在那一格里就不能放」是原版行为 | SelfTrap 原有的 `SelfTrapHack.java:119`（自身 AABB 相交就跳过）**必须保留**，它本来就写对了 |

### 3. 位置集合与 1.8 格碰撞箱是否吻合

| 关注点 | 参考（_oe_ref 相对路径:行） | 本工程（相对路径:行） | 判定 |
| --- | --- | --- | --- |
| 基准点 | 参考按**目标**的 `positionVector.down()` 起算（`AutoTrap.kt:152,160`：`BlockPos(target.positionVector).down().add(offset)`） | `SelfTrapHack.java:87`：`BlockPos.containing(MC.player.position())`（玩家脚所在格；与 `MC.player.blockPosition()` 等价，`Entity#blockPosition` 就是三个坐标取 floor） | 合理，等价于参考的 down() 写法 |
| 玩家碰撞箱高度 | 1.12.2 玩家 0.6×1.8 | `.../world/entity/player/Player.java:133`：`STANDING_DIMENSIONS = EntityDimensions.scalable(0.6F, 1.8F)`；1.20.1 的 AABB 构造见 `client_mappings.txt:30546-30547`（`BlockPos.containing` 存在） | 1.8，与题目前提一致 |
| FULL 的 13 格 | 参考 TRAP 是给目标下笼（`AutoTrap.kt:59-73`，14 格含 dy=3） | `SelfTrapHack.java:92-100`：dy=0 四角、dy=1 四角、dy=2 四角 + `(0,2,0)` 顶盖 | **吻合**：脚在 py、身高 1.8 → 身体占住 dy=0（py..py+1）与 dy=1（py+1..py+2，头顶 1.8 在这一层内），头正上方唯一能被封的一层就是 dy=2；dy=2 的四角 + 顶盖正好把它封死，不需要 dy=3 |
| TOP / FEET | 参考 `TRAPFEET`（`AutoTrap.kt:75`）是给目标做的另一种图案 | `SelfTrapHack.java:102-106`（TOP = dy=2 四角 + 顶盖）、`:108-111`（FEET = dy=0 四角） | 与模块描述（只盖头 / 只围脚）一致 |
| 每 tick 节奏 | 参考 `tickDelay` 默认 2 / `blocksPerTick` 默认 2（`AutoTrap.kt:40-41`） | `SelfTrapHack.java:82-85`：每 2 tick 放 1 格（`placeTimer < 2` 直接 return） | 时序等价（本工程更慢一档，且没有 Instant/BlocksPerTick 设置） |
| 方块选择 | `AutoTrap.kt:268-275` 写死 `Blocks.OBSIDIAN` + `findBlockInHotBar` | `SelfTrapHack.java:183-202`：`Only obsidian` 默认开时只认黑曜石/哭泣黑曜石/基岩，并排除 TNT 与重生锚（关掉时用任意 `BlockItem`） | 本工程更细，且不是本次改动点 |
| 关闭条件 | `AutoTrap.kt:126-134,330-343`：`isTrapped()` 按世界方块状态判断整个笼子是否已完整，完整才 `toggle()` | `SelfTrapHack.java:130-131`：这一轮一格都没放下就 `setEnabled(false)` | 语义不同（见「建议（未做）」第 2 条），本次不动 |

## 实际改动

1. **新增实体占用前置检查（本次唯一的正确性修复）**。`SelfTrapHack.java:121-122` 增加
   `if(isOccupiedByEntity(pos)) continue;`，并在 `:134-164` 新增与 `AutoTrapHack.java:177-182`
   逐字相同的私有方法。
   - 为什么是 bug：`BlockPlacer.place()` 的返回值只代表「找到了可点的面并把交互包发出去了」
     （`BlockPlacer.java:60-88`），1.20.1 服务端还会在 `BlockItem#canPlace` → 原版 `isUnobstructed`
     里因为「格子被 `blocksBuilding` 的实体占住」直接拒绝（证据见上表 §2）。旧代码采信返回值 → 在
     那一格 `break`（旧 `:119-123`），于是：**A.** 同一侧被敌人占住时，另外 12 格再也不尝试；
     **B.** `placed` 恒为 true，`!placed → setEnabled(false)` 永远不成立，模块永远开着，每 2 tick
     发一次无效交互包（还额外多一次角度伪造）。PvP 贴脸时被占住的恰好是 dy=0（脚下圈）或 dy=1
     （身体圈），也就是这个模块的主战场。
   - 改动后的行为：被占住的格子被跳过，剩下的格子照常补；只有「所有格子都已填满 / 都被占住 /
     没有方块」时才满足 `!placed` 并自动关闭——这正是 `SurroundHack.java:187-188` 的既有语义。
   - 与既有实现的一致性：判据是「非旁观者 + 非已移除 + `LivingEntity`」，与 `AutoTrapHack.java:177-182`
     完全一致；`getEntities((Entity)null, new AABB(pos))` 的写法也一致。
2. **顺手去掉重复的可替换性判断**：旧 `:119-120` 的 `if(BlockUtils.getState(pos).canBeReplaced() && placeBlock(pos))`
   变成 `if(placeBlock(pos))`。这不是风格调整：`BlockPlacer.place()` 第一件事就是重查同一条
   （`BlockPlacer.java:56-58`，不满足直接 `return false`），而 `:117` 已经查过一次，所以删除后
   **行为逐字节不变**（只是少两次方块状态查询）。
3. 未改动：三个设置的名称/默认值/说明（`Mode` 默认 `FULL`、`Only obsidian` 默认 true、
   `Swing hand` 默认 `CLIENT`，`SelfTrapHack.java:33-45`）；位置集合（`:90-112`）；2 tick 节奏
   （`:82-85`）；`placeBlock`/`findBlockSlot`/`swingHand` 的调用与槽位保存-恢复（`:166-202`）；
   `onEnable`/`onDisable`/`getRenderName`；公开 API（只有 `onUpdate` 与 `getRenderName`）。
4. 可验证性说明：这条判断是「3 个布尔与运算 + 一次实体查询」，没有值得抽成纯类的算法，因此
   **没有为了凑单测去抽 `SelfTrapPlanner`**（brief 第 7 条）。证据是：参考 `AutoTrap.kt:247-252`
   的同一道守卫 + 原版 `isUnobstructed` 的判定条件（本地反编译源逐行可查）+ 兄弟模块
   `AutoTrapHack`/`SurroundHack` 已经按同一口径修过。改动未编译、未运行（brief 第 1 条禁止跑 gradle）。

## 故意不搬

- 参考 AutoTrap 的六种笼子图案（`AutoTrap.kt:59-78`：TRAP / TRAPFULLROOF / TRAPFEET / CRYSTAL /
  CRYSTALFULLROOF / HEADBLOCK）与 `Cage` 设置（`:37`）：那是**给别人**下笼（含水晶位图案、4 格高的
  HeadBlock），SelfTrap 是给自己封顶；照搬会直接改模块语义。
- `Instant`（放满一整圈）与 `BlocksPerTick`（默认 2）/`TickDelay`（默认 2）设置（`AutoTrap.kt:39-41`）：
  本工程每 2 tick 只放 1 格是刻意的节奏（`SelfTrapHack.java:82-85`），一次放 13 格会瞬间触发服务端
  的交互频率检查；新增设置也会牵动配置与翻译资源。
- `NoGlitchBlocks`（`AutoTrap.kt:46,292-297`）：参考在放置后再补一个
  `CPacketPlayerDigging(START_DESTROY_BLOCK)` 来清掉客户端的预测方块。1.20.1 里这个包是**真的开始
  破坏**（服务端会按 tick 累计并可能真把方块挖了），照搬是引入 bug。
- `AutoTrap.kt:276-279` 的 `isSneaking` + `START_SNEAKING`（对着黑名单方块/潜影盒时）：1.20.1 放置
  本身不需要潜行（潜行只影响「右键容器是否打开界面」），本工程 `BlockPlacer` 也从不潜行。
- `AutoTrap.kt:280-288` 的 `PlayerPacketManager` 旋转（`sendPacket(801)` 排队旋转）：本工程
  `BlockPlacer.java:84` 已经通过 `RotationFaker.faceVectorPacket` 做了同一件事（SelfTrap 走的就是
  这条），不需要再引入参考的包队列。
- `Surround.kt:117-138` 的 `SPacketBlockChange` 放置确认状态机：见「建议（未做）」第 5 条。

## 建议（未做）

1. **`docs/openeepsilon-refactor.md:110` 的那句括注与代码不符**：「同一道守卫 `SelfTrap` / `AutoWeb` /
   `AutoTrap` / `InstantBunker` 都已经有了」——本轮改动前只有 `AutoTrap` 有实体占用检查（`Surround`
   是第 12 轮才补的），`SelfTrap`（本轮补上）、`AutoWebHack.java:109-110`、`InstantBunkerHack.java:109`
   都只有自身碰撞箱检查。该文件是共享文件，按 brief 第 4 条未改，请父 agent 决定是改口径还是分派后续
   子任务；`AutoWeb` / `InstantBunker` 属同一类 bug，未在本次分配范围内、未动。
   **父 agent 复核后的结论（口径已改）**：这两个**都不是同一类 bug**，不需要修。
   - `AutoWeb`：它放的是蜘蛛网，而蜘蛛网注册时带 `.noCollission()`（1.20.2 反编译源
     `world/level/block/Blocks.java:715-726`，`.noCollission()` 在 :721）。`CollisionGetter.java:31` 的
     `isUnobstructed(BlockState, BlockPos, CollisionContext)` 是
     `voxelshape.isEmpty() || isUnobstructed(null, ...)`，碰撞箱为空的方块**直接放行、根本不查格子里的实体**。
     所以 `AutoWeb` 往目标身上放网在原版就是允许的，缺这条守卫不影响任何行为。
   - `InstantBunker`：确实只看自身碰撞箱（`InstantBunkerHack.java:107-110`），但它的循环既不 `break`
     也不依据返回值重试，被占的格子最多浪费一次交互包/一次朝向，没有「永远补不上」的后果，属无害。
2. **关闭条件可以更准**：参考用「整个笼子是否已完整」（`AutoTrap.kt:330-343`）决定收工，本工程是
   「这一轮一格都没放下就关」。后者在半砖/楼梯上会提前关闭（见下一条）。改成前者是行为改动（会让
   SelfTrap 在部分完成时保持开启、等位置可放再补），需要实机确认，未做。
3. **站位不在整格上时会留下头顶缺口**：玩家脚 Y 不是整数（站在半砖/楼梯上、或下落中）时，dy=2 那层
   被自己的碰撞箱挡住（`:119` 跳过），模块补完 dy=0/dy=1 后就因 `!placed` 关闭，头顶仍敞着。要处理
   需要「等玩家回到整格再补顶盖」的策略，属行为改动，未做。
4. **守卫比原版略严**：本守卫以「整格 AABB 与实体相交」判占住，而原版看的是**方块的碰撞形状**
   （`EntityGetter.java:44` 的 `Shapes.joinIsNotEmpty(方块形状, 实体碰撞箱)`）。对黑曜石（满格）两者
   等价；但用户关掉 `Only obsidian` 后若用半砖之类，只占半格的实体会让这一格被多跳过。与
   `AutoTrapHack` / `SurroundHack` 的既有取舍一致，要精确对齐需要三处一起改（共享口径），未做。
5. **放置确认架构**：本工程已有 `PacketInputListener`（`events/PacketInputListener.java`，消费方包括
   `AutoFishHack`、`NoRotateHack` 等），技术上可以把参考 `Surround.kt:117-138` 的
   `SPacketBlockChange` 确认搬过来，做成一个共享的「放置是否真的落地」工具。那是一次跨多个 hack 的
   架构改动（AutoTrap / Surround / InstantBunker / AutoWeb 都要换），不该在单个模块的审计里做，未做。
6. `SelfTrapHack.java:48` 的 `private int step;` 只在 `:69` 被写入、从未被读取（死状态）。删掉是纯
   清理、无行为变化，按 brief「不做纯装饰性改动」保留原样，仅记录。
