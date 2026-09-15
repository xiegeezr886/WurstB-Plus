状态：已优化

# AutoCity 对照裁决（OpenEpsilon `AutoCity.kt` → 本工程 1.20.1 `AutoCityHack`）

- 目标：`src/main/java/net/wurstclient/hacks/AutoCityHack.java`（159 → 171 行）
- 参考：`_oe_ref/src/main/kotlin/studio/coni/epsilon/module/combat/AutoCity.kt`（146 行，1.12.2 / Kotlin / MCP）
- 结论：参考里**只有一条**东西在 1.20.1 上仍然成立且有意义——`checkPos` 的「挖掉这一面之后，目标身边是否真的出现一个水晶位」。
  它被搬成**优先级**（不是硬过滤），抽成纯类 + JUnit 5 测试。其余一概不搬。

## 1. 改动清单

| 文件 | 动作 |
| --- | --- |
| `src/main/java/net/wurstclient/util/CityBlockPlanner.java` | 新增（103 行，纯逻辑，无任何 Minecraft import） |
| `src/main/java/net/wurstclient/hacks/AutoCityHack.java` | 修改（候选扫描 + 1 个新私有方法 + import） |
| `src/test/java/net/wurstclient/util/CityBlockPlannerTest.java` | 新增（6 个用例） |

### 1.1 `AutoCityHack.java` 到底改了什么

1. 候选装配：原来的 `ArrayList<BlockPos> toMine` + `cityOffsets` 数组 + `stream().min(距离)`（旧 :88-123）换成四个等长数组
   `candidates/usable/punishable/distanceSq` + `CityBlockPlanner.plan(...)`（新 :89-116）。四个水平偏移改由
   `CityBlockPlanner.offsetX/offsetZ` 提供，顺序与旧 `cityOffsets` **逐项一致**（东→西→南→北），并列时的取用顺序也不变。
2. 新增 `punishable[i] = opensCrystalSpot(pos)`（新 :109）与私有方法 `opensCrystalSpot(BlockPos)`（新 :146-153）：
   下方是黑曜石/基岩且上方可替换的墙块，挖掉后就是一个能直接砸到目标的水晶位，排在候选最前面。
3. import：删 `java.util.ArrayList`；加 `net.minecraft.world.level.block.Block`、`net.wurstclient.util.CityBlockPlanner`。
4. **未动**：`Range` / `Auto switch` / `Ignore own surround` / `Swing hand` 四个设置的名称、默认值与说明；`findTarget()`；
   转向、自动换工具、`breakTimer`、`startDestroyBlock`/`continueDestroyBlock`、`onEnable`/`onDisable` 全部一行未改。
   没有增删设置，没有改任何公开签名。

## 2. 为什么这条值得搬，以及为什么只做成"优先级"

参考 `AutoCity.kt:119-141` 的 `findHoleBlock`/`checkPos` 不是"看到黑曜石就挖"，而是先问一句：
**挖掉这一面墙之后，目标脚边会不会真的出现一个水晶位？** 参考把它写成了独立函数 `findHoleBlock`，
并让每个候选都过一遍 `checkPos`——函数名本身就是这个意思。

在 1.20.1 上这条判断的每个零件都还成立，而且与本工程的水晶逻辑是同一套：

- 基底判定：参考 `CrystalUtils.isValidBasePos`（`CrystalUtils.kt:38-41`）= 黑曜石/基岩；
  本工程 `CrystalAuraHack.hasCrystalBase`（:400-404）= 基岩/黑曜石——**同一个定义**。
- 空间判定：参考 `CrystalUtils.isValidSpace`（`CrystalUtils.kt:44-47`）= `!isLiquid && isReplaceable`；
  本工程 `CrystalAuraHack.isReplaceable`（:395-398）= `BlockState#canBeReplaced()`。
- 水晶实体高 2 格，所以「墙块挖掉后自己变空气 + 墙块上方可替换」正好凑够 2 格空间，对应参考那句
  `isValidSpace(world, pos.up())`（`AutoCity.kt:134`）。
- 因果链在本工程里真的闭合：挖掉这面墙之后，`CrystalAuraHack` 的候选收集（:378-385）会在这一格通过
  `isReplaceable` + `hasCrystalBase` + `isCrystalSpaceClear` 三道过滤，也就是这一下真的能换成伤害。

**为什么不照搬成硬过滤（关键）**：参考的 `findHoleBlock` 只在"目标确实在坑里"时工作——它的第一分支硬要求墙块**下方**
是抗爆基底。本工程 AutoCity 一直对任意黑曜石环绕生效，包括玩家在泥土/石头地面上放满一圈黑曜石 Surround 的场景
（此时墙块下方不是基底）。照搬硬过滤会让它在这些场景里一个候选都认不出来、直接不动（参考那边就是 `disable()`，
见 `AutoCity.kt:67-70`），属于**删功能**，违反本任务约束。所以做成"优先级"：

- 有能换来水晶位的墙 → 先挖它（把有限的手速/时间花在能换成伤害的那一面，不再白送对方一条出路）；
- 一面都没有 → 完全保持原来的"离玩家最近的墙"行为。

### 2.1 实战效果与边界（如实说明）

- 死角场景（四堵墙全都坐在黑曜石/基岩地面上，即"真正的坑"）：**四面都算可换水晶位，排序退化为原来的最近优先，行为零变化**。
- 只有部分墙能换水晶位时才生效，典型是：坑在平台/方块簇边缘、某一面墙下面是空气（挖了只是把人放下平台），
  或者地面只有一部分是抗爆材质。此时旧代码可能先挖那一面"挖了也没法惩罚"的墙，新代码先挖有收益的那面。
- 因此这是一条**低风险、非普适**的改进：它不会在任何场景下让 AutoCity 变得更差，但也不是每局都会体现。

## 3. 证据表

### 3.1 `AutoCity.kt` ↔ `AutoCityHack.java`

| 参考构造（`AutoCity.kt:行`） | 本工程对应（`AutoCityHack.java:行`） | 说明 |
| --- | --- | --- |
| `range` / `autoSwitch` / `message` 设置（:38-40） | `Range`（:37-39）/ `Auto switch`（:41-42）/ 无 `message` | 前两个等价；`message` 不搬（见 §4） |
| `miningPos` / `target` 状态字段（:42-43） | 除 `breakTimer`（:51）外无跨 tick 状态；候选每 tick 从目标当前位置重算（:86-97） | 见 §4「候选过期」 |
| `safeListener<OnUpdateWalkingPlayerEvent.Pre>`（:46） | `UpdateListener#onUpdate`（:79-80） | 事件模型不同，等价入口 |
| `findClosestTarget()`（:47, :92-116） | `findTarget()`（:82, :155-169） | 都是"最近的、非自己、非好友、活着的目标" |
| 好友过滤 `FriendManager.isFriend`（:99） | `WURST.getFriends().contains(...)`（:165） | 同义 |
| 无目标 → 提示并返回（:49-52） | 无目标 → 直接 return（:83-84） | 无提示语设置 |
| `findHoleBlock(target)`（:54, :119-131） | 候选扫描循环（:89-110） | 同一件事：目标脚边四个水平邻块 |
| `for (facing in EnumFacing.HORIZONTALS)`（:122） | `CityBlockPlanner.offsetX/offsetZ`（调用 :96-97，定义 `CityBlockPlanner.java:43-51`） | 顺序同为 东→西→南→北 |
| `if (dist > range) continue`（:125） | 无（只按 `Range` 卡目标距离 :166） | 不搬（见 §4） |
| `if (block == Blocks.BEDROCK) continue`（:126） | `!BlockUtils.isUnbreakable(pos)`（:104） | 1.20.1 用 `defaultDestroyTime() < 0` 覆盖基岩，且顺带覆盖其它不可破坏方块 |
| **`checkPos(offsetPos, facing)`（:127, :133-141）** | **`opensCrystalSpot(pos)`（:109, :146-153）** | **本次搬的就是这条** |
| `isValidBasePos(pos.down())`（:134） | `BlockUtils.getBlock(pos.below())` 为 BEDROCK/OBSIDIAN（:148-150） | 与本工程 `CrystalAuraHack.hasCrystalBase`（:400-404）同定义 |
| `isValidSpace(pos.up())`（:134） | `BlockUtils.getState(pos.above()).canBeReplaced()`（:152） | 等价 |
| `pos 自身挖掉后变空气`（隐含） | 注释说明（:137-139） | 水晶位需要「墙块自身 + 上方」两格 |
| `checkPos` 第二分支：水平邻块是基底（:135-139） | 无 | 不搬（见 §4） |
| 取最近候选 `114.514 to BlockPos.ORIGIN` 哨兵（:121, :128-130） | `CityBlockPlanner.plan()` + `order[0]`（:112-116） | 纯类里用插入排序，并列稳定 |
| `mc.player.distanceTo(offsetPos)`（:124） | `MC.player.distanceToSqr(Vec3.atCenterOf(pos))`（:99） | 同一把尺子，本工程沿用原来的平方距离 |
| `PacketMine.mineBlock(...)`（:84） | `continueDestroyBlock`（:132-133） | 参考是包级手法，不搬（见 §4） |
| 顺带挖 `center`（:85-87） | 无 | 不搬（见 §4） |
| 两个 air / 目标位移 > 2 → `disable()`（:72-82） | 无（:86-97 每 tick 重算） | 见 §4 |
| `onDisable { PacketMine.reset }`（:143-145） | `onDisable { stopDestroyBlock() }`（:72-77） | 等价收尾 |
| `ItemUtil.findItemInHotBar(DIAMOND_PICKAXE)` + `swapToSlot`（:56-65） | `AutoToolHack.equipIfEnabled(closest)`（:121-122，`AutoToolHack.java:114`） | 本工程更通用 |

### 3.2 参考工具类 ↔ 本工程既有等价物

| 参考（`_oe_ref` 行） | 本工程（行） | 说明 |
| --- | --- | --- |
| `CrystalUtils.isPlaceable/isValidBasePos/isValidSpace`（`util/combat/CrystalUtils.kt:24-47`） | `CrystalAuraHack.java:388-404`（`isCrystalSpaceClear`/`isReplaceable`/`hasCrystalBase`） | 水晶位三件套，本工程已有 |
| `SurroundUtils.checkHole` / `HoleUtils.checkHole`（`SurroundUtils.kt:30-48`、`HoleUtils.kt:106-115`） | `HoleFillerHack.java:115-132`（`scanHoles`）、`HoleEspHack.java:167-187` | 洞型判定已有；AutoCity 只需脚边四邻，用不到 |
| `HoleUtils.checkSurroundPos` 用 `CrystalUtils.isResistant`（`HoleUtils.kt:235-248`） | `HoleEspHack.isObsidianLike`（`HoleEspHack.java:182-187`）、`HoleFillerHack.findBlockSlot` 的黑曜石族判定（`HoleFillerHack.java:142-148`） | 同义（1.12 的 `isResistant` 在 1.20.1 要构造爆炸上下文，故文献里已记为刻意近似） |
| `CalcContext` 暴露采样伤害模型（`util/combat/CalcContext.kt:22-140`） | `DamageUtils.calculateDamage`（`DamageUtils.java:137-171`，用原版 `Explosion.getSeenPercent`，:145） | AutoCity 不参与伤害计算，用不到 |
| `Interact.getNeighborSequence` 可达面序列（`util/world/Interact.kt:24-77`） | `BlockBreaker.java:65-138`（`getBlockBreakingParams`）+ `BlockUtils.java:156-165`（`hasLineOfSight`） | AutoCity 也没用 `Interact`（它走 PacketMine），与本 hack 无关 |
| 参考自己实现的选人 `findClosestTarget`（`AutoCity.kt:92-116`；该文件 import 了 `CombatUtils` 却没用） | `CombatTargetUtils.get/getList`（`CombatTargetUtils.java:32-62`） | 本工程的 AutoCity 仍用自己的 `findTarget()`：改成通用选人会改变现有选人行为（本任务禁止） |

### 3.3 本次复用的本工程既有惯例

| 惯例 | 出处 | 本次用法 |
| --- | --- | --- |
| 纯类 + 静态方法 + 无 MC import | `SurroundPlanner.java:26-107`、`CrystalAuraPlanner.java:10-68` | `CityBlockPlanner.java` 照此写 |
| 纯类配套 JUnit 5 测试（无 GPL 头、包级 final、TABS、中文注释） | `SurroundPlannerTest.java:1-75` | `CityBlockPlannerTest.java` 照此写 |
| 候选下标排序返回 `int[]` | `SurroundPlanner.plan`（:62-89） | `CityBlockPlanner.plan`（:64-93） |
| 相邻块的纯数组偏移 | `SurroundPlanner.OFFSET_X/OFFSET_Z`（:33-34） | `CityBlockPlanner.OFFSET_X/OFFSET_Z`（:40-41） |
| 破坏方块走 `continueDestroyBlock` / `stopDestroyBlock` | `NukerHack.java:135-143, 148-158`、`BlockBreaker.java:44-57` | 保持原样未改 |

## 4. 刻意没有搬的东西

| 参考里的东西 | 为什么不要 |
| --- | --- |
| `PacketMine.mineBlock`（`AutoCity.kt:84,86`） | 1.12.2 的包级提前破坏手法：连发 START/STOP 动作包来"换"进度。1.20.1 的破坏必须按 tick 累计进度（服务端也会复核），这类序列要么无效要么明显异常；本工程统一走逐 tick 的 `continueDestroyBlock`（`AutoCityHack.java:132-133`），与 `NukerHack`/`BlockBreaker` 一致。 |
| `ItemUtil.findItemInHotBar(Items.DIAMOND_PICKAXE)` + `swapToSlot`（:56-65） | 硬编码钻石镐。本工程有 `AutoToolHack.equipIfEnabled`（`AutoToolHack.java:114`）+ 现成的 `Auto switch` 设置，能按方块选最佳工具，比参考强，换掉是退步。 |
| 顺带挖 `center`（目标自己所在的方块，:85-87） | 那是"目标把自己埋进方块里时把他挖出来"，与本 hack「拆黑曜石环绕」的定位不同；而且会让 hack 去挖目标脚下的方块，改变现有行为（可能把人放下平台）。 |
| `checkPos` 的第二分支（:135-139） | 它只看「墙块的水平邻块是不是抗爆基底」，**没有校验那块基底上方的空间**（缺 `isValidSpace(pos.offset(facing).up())`），在 1.20.1 会误判成"能换来水晶位"。宁可少认一个，也不认错一个——所以本次只收第一分支。它那句 `if (facing == facingIn.opposite) continue` 也只是 1.12 的写法细节（这个方向的邻块就是目标自己所在的空气），一并不要。 |
| `message` 设置 + `ChatUtil` 提示（:38-40, :50, :60） | 1.12.2 的提示语与本工程的设置/本地化体系无关；加新设置会牵动翻译资源，而本任务只允许改三个文件。 |
| "两个方块都变空气 → `disable()`"（:72-75）、"目标位移 > 2 → `disable()`"（:77-82） | 本工程每 tick 从目标**当前位置**重算候选（`AutoCityHack.java:86-97`），不存在"候选过期"这个前提；而且"没活干就自动关掉自己"是删除现有行为的另一种形式。 |
| `if (dist > range) continue`（:125） | 本工程的 `Range` 说明是"How far to search for targets"（:38），拿它去卡墙块会改变该设置的含义（等于改设置语义）；而且参考默认值 5 本身也大于 1.20.1 的 4.5 格交互距离，并不是一个正确的"可达性"检查。真要修可达性，应该用 `BlockBreaker.getBlockBreakingParams(...).lineOfSight()` 这类本工程已有的东西，那是另一件事，不在本次范围内。 |
| `Interact.kt` / `CalcContext.kt` / `HoleUtils.kt` 的算法 | AutoCity 根本没用到它们（AutoCity 只调 `findHoleBlock` + `PacketMine`），且本工程已有对应实现（见 §3.2）。为对照而改是"为改而改"。 |

## 5. 测试

`src/test/java/net/wurstclient/util/CityBlockPlannerTest.java`，JUnit 5，6 个用例：

1. `mapsIndexesToLegacyOffsets`：`COUNT` 与四个偏移逐项等于旧 `cityOffsets`（东→西→南→北）。
2. `keepsClosestFirstWhenNoWallIsPunishable`：没有一个能换水晶位时，退回原来的纯最近优先。
3. `prefersTheWallThatOpensACrystalSpot`：最远但唯一能换水晶位的墙排第一；两个都能换时它们之间再按距离排。
4. `keepsOriginalOrderOnTies`：距离全等 → 保持原下标顺序；优先级 + 距离双双并列 → 同样稳定。
5. `filtersOutUnusableWalls`：不可挖的候选被剔除；"能换水晶位但不能挖"的候选不会因为优先级而混进来。
6. `handlesEmptyAndShortInput`：空数组返回空数组；三个数组长度不一致时按最短处理，不越界。

## 6. 未验证项（如实说明）

- 按任务约束**没有运行 gradle/maven 或任何构建命令**，因此本改动未经过编译与测试运行；纯类 `CityBlockPlanner.plan`
  的每个断言结果都已按插入排序逐步手工推演核对过。
- §2.1 里"常见真坑场景下行为零变化"的结论来自逻辑推演（四堵墙下方都是基底时四个 `punishable` 同为 true），
  没有实机验证。
