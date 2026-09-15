状态：已优化

## 对照证据

| 关注点 | 参考（_oe_ref 相对路径:行） | 本工程（相对路径:行） | 判定 |
| --- | --- | --- | --- |
| 参考是否有重生锚模块 | 全仓无：`Get-ChildItem -Recurse *.kt` 的 449 个 .kt 里 `(?i)anchor` 零匹配，`*.md` 里 `(?i)anchor\|respawn` 也零匹配 | — | 不适用（没有可对照物；下面用最接近的 `BedAura.kt`（床炸）代替） |
| 时序：充能与引爆能否落在同一 tick | `module/combat/BedAura.kt:312-319` `instantTiming` 在同一 tick 里 `placeBed()` + `breakBed()`；`:323-334` `syncTiming` 也是同 tick（1.12.2 的床不需要等 tick） | `hacks/AnchorAuraHack.java:237-258` 充能成功后只把状态切到 DETONATE 并 `return`，引爆只可能发生在**后续** `onUpdate`（`:267`）；`:261` 再查一次 `isChargedAnchor` | 约束满足（本工程比参考严格：放置/充能与引爆天然跨 tick）。注意客户端对充能有预测（`mixin/ClientPlayerInteractionManagerMixin.java:137-145` → `useItemOn`），所以保证来自 tick 分离而非服务端往返 |
| 自伤门槛 | `BedAura.kt:558-559` `scaledHealth - selfDamage > noSuicide`（吸收按血量占比折算） | `AnchorAuraHack.java:340-344` `DamageUtils.isDamageWorthwhile(...maxSelfDamage)` + `selfDamage < health + absorption` | 本工程更保守（total ≥ scaled），不搬 |
| 「自己站在爆炸范围内」的保护 | `BedAura.kt:552-553` 自伤取两种上下文（`false`/`true`）的最大值 | `:339` 用 `DamageUtils.calculateDamage(中心, MC.player, 5)`，内部含 `Explosion.getSeenPercent` 暴露度与距离衰减，挡墙/远离都能反映；`:222-223` 延迟期间每 tick 复核，玩家走进伤害范围就 `clearPending` | 满足 |
| 候选评分与排序稳定性 | `BedAura.kt:460` 以眼睛为球心、range 为半径枚举候选；`:467-470` `maxWithOrNull(compareBy{targetDamage}.thenByDescending{eyePos.distanceSqTo(basePos)})`，同分有**显式** tie-break | 旧：`:291-293`/`:302-305` 只过滤 `isDamageSafe` 后 `Stream.max`；粗筛在 `:466-481`/`:508-530`（半径 `range+0.5`、且眼睛先 `-0.5`），点不到的位置也能入选 | **有真实缺陷，已修**（见下） |
| 同分候选的 tie-break | `BedAura.kt:467-470` 显式「更远的 basePos 优先」 | 锚列表 `:473-481` 已按「离玩家更远优先」排序，`findBestUsable` 同分取下标最小 ⇒ 与参考同向；放置列表经 `HashSet`（`:311`）丢掉了 `:528` 的排序 | 一致 / 仅放置侧无显式 tie-break（同分候选期望伤害相同 ⇒ 非可证 bug，见「建议（未做）」） |
| 爆炸伤害是否复用工具 | `BedAura.kt:548-558` 走 `CombatUtils`/`CalcContext.calcDamage`（1.12.2 的 ArmorInfo 缓存） | `:326-328`、`:339`、`:354` 全部走 `DamageUtils.calculateDamage`（`util/DamageUtils.java:137-171`，含护甲/保护/抗性/难度），门槛走 `DamageUtils.isDamageWorthwhile`（`DamageUtils.java:198-202`） | 已复用，无自算一套 |
| 参考的交互包构造 | `BedAura.kt:386`、`:478-479` 固定用 `EnumFacing.UP` 面发 `CPacketPlayerTryUseItemOnBlock`，不检查面是否可达 | `:405-442` 要求相邻方块可点击 + 面心在 range 内 + 面朝玩家 +（可选）视线 | 本工程更稳，反向不搬 |

## 实际改动

1. **新增 `util/AnchorAuraInteractPlanner.java`（纯逻辑，零 `net.minecraft` 依赖）+ `src/test/java/net/wurstclient/util/AnchorAuraInteractPlannerTest.java`（7 个用例）**
   - `canClickFace(面心距², 中心距², rangeSq)`、`canPlaceFace(面心距², 相邻中心距², 中心距², rangeSq)`：把两处内联的几何规则写成可单测的形式。
   - `findBestUsable(scores, usable)`：只在 `usable[i]` 的候选里取分数最大者，同分取下标最小者（与原 `Stream.max` 的「首个最大值」语义逐值一致），全不可用返回 -1。

2. **修真实缺陷：候选资格缺「能不能点到」这一条**
   - 位置：`hacks/AnchorAuraHack.java` `findBestAnchor`（`:289-307`）、`findBestPlacement`（`:309-333`）。
   - 根因：粗筛用 `eyesVec = 眼睛 - (0.5,0.5,0.5)` 到方块**最小角**的距离 ≤ `(range+0.5)²`（`:510-511`、`:525`），动作函数却要求**面心**到眼睛 ≤ `range²`（`:371-377`、`:424-432`）。两个半径不同，存在「粗筛通过、六面全点不到」的一整圈位置。
   - **反例（可复现的输入）**：玩家站在方块 (5,10,5)，眼睛 (5,11.62,5)，`range = 6`（默认）⇒ `rangeSq = 36`、粗筛半径² `= 42.25`。取该圈内的 (0,11,9)：
     - 旧：粗筛距离² = `(0-4.5)² + (11-11.12)² + (9-4.5)² = 40.51 ≤ 42.25` → 通过；`findBestPlacement` 只过滤 `isDamageSafe` → 它入选并因分数最高而胜出；`placeAnchor` 六个面的面心距眼最近 `36.26 > 36` → 返回 false。下一 tick 重算，分数函数不变、它仍是最高分 ⇒ **每 tick 重复同一个失败，永远不放置任何锚**。
     - 新：`usable[i] = findPlaceSide(pos) != null && isDamageSafe(...)` ⇒ (0,11,9) 被跳过，改用次高分的**可放置**候选。同样输入下旧行为是"什么都不发生"，新行为是"放下锚"。
     - 同一根因对已存在的锚更严重：把唯一一个**已充能**锚放在这一圈内（例如 (0,11,9)，配一个紧邻它的敌人，玩家穿甲使自伤 ≤ `maxSelfDamage`）。旧行为：`getNearbyAnchors`（`:466-481`，同样是 42.25 的粗筛）把它选为最高分 → `beginPending(DETONATE)` → `rightClickBlock` 永远 false → `pendingTicks > 20` 超时（`:222`）→ `clearPending` → 下一 tick 又选中它；`:181-187` 的 charged 分支优先 return，于是**充能其它锚、放置新锚全部被饿死**，每 20 tick 空转一次。新行为：该锚不具备资格，代码继续处理其它锚/放置。
     - 该圈不是罕见几何：按上述参数枚举，通过 42.25 粗筛而六面面心都 > 36 的方块位置共 **48** 个（例：(0,11,9) 面心最近 36.26；(0,12,5) 一类斜角区）。越靠外自伤越小，放置打分里的 `- 0.25 × 自伤` 反而**奖励**这些更远的位置。

3. **等价重构（为使第 2 条复用同一规则，行为逐值不变）**：把 `rightClickBlock` 的面选择抽成 `findClickSide`（`:358-387`），把 `placeAnchor` 的面选择抽成 `findPlaceSide`（`:405-442`），`rightClickBlock`（`:389-403`）/`placeAnchor`（`:444-464`）改为先取面再 `face()`+发包。
   - 面遍历顺序（`Direction.values()`）、每个判断的顺序与比较式一一对应（`面心距 > rangeSq → 跳过`、`面心距 >= 中心距 → 跳过` ⇒ `canClickFace`；`中心距 > 相邻中心距 → 跳过` ⇒ `canPlaceFace`）。
   - `placeAnchor` 的 `selectItem`/`isHolding` 从"循环内首个通过几何检查的面之后"移到"几何检查之后"，对同一输入取到的面与是否继续执行完全相同（旧代码也只在第一个几何合法的面上选物品）。
   - 单独看这一条不是 bug 修复：旧新在**任何**输入下选出的面与发包内容都相同。

## 故意不搬

- 参考的同 tick 放床+炸床（`BedAura.kt:312-319`、`:323-334`）：1.20.1 的重生锚必须跨 tick，本工程状态机已更严格，搬过来是退化。
- 参考的一整套 TimingMode / slowMode / forcePlace / motionDetect / damageBalance（`BedAura.kt:85-120`、`:576-586`）：要新增大量设置项并改手感，且是无单测可验的启发式，收益不可证。
- 参考固定用 UP 面发交互包、不检查面朝向与可达（`BedAura.kt:386`、`:478-479`）：1.12.2 的床写法，本工程的「面朝玩家 + range + 视线」更稳。
- 参考以眼睛为球心枚举候选（`BedAura.kt:460`）：本工程的「目标邻域 2 格 + 可替换 + 有可点击邻居」更贴锚的用法。
- 参考的自伤口径 `scaledHealth`（`BedAura.kt:558`）：本工程用 `health + absorption`，更保守；为对齐而放松安全阈属于行为退化风险。

## 建议（未做）

- **同分候选仍由 `HashSet` 迭代序决定**：`findBestPlacement`（`:311`）把 `getFreeBlocksNear` 在 `:528` 排好的顺序丢掉了。参考用显式 tie-break（`BedAura.kt:467-470`）。未做的原因：打分已含实际爆炸伤害与自伤惩罚，同分候选的期望伤害相同，改不出「旧 X / 新 Y」的可验证差别；若要做，建议 `LinkedHashSet` 保序 + 给 `findBestUsable` 加次要键（更远的自身距离 / 更低自伤）。
- `isDamageSafe`（`:343-344`）自写的 `selfDamage < getHealth() + getAbsorptionAmount()` 可直接换成 `DamageUtils.totalHealth(MC.player)` / `DamageUtils.isLethal`（`DamageUtils.java:89-110`）。未做：血量/吸收非负时两式逐值等价，属等价替换。
- `checkLOS` 打开时，可达性过滤会给每个候选多做至多 6 次射线检测（旧实现只对最终选中的 1 个候选做）。默认 `checkLOS = false`（`:67-69`），未做优化；如担心性能可给 `findClickSide` 加每 tick 的位置缓存。
- `processPending`（`:222-223`）的每 tick 复核只查 `isDamageSafe`，不复查可达性：若玩家在延迟期间被击退到面不可达处，会走满 20 tick 超时（会自愈，不会卡死）。未做。
- 本工程 `findBestUsable` 的同分语义靠"调用方按原顺序传入"维持；若以后有调用方传入无序集合，同分行为会变。目前两处调用方都保持了原顺序。
