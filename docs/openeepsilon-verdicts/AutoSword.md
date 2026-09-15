状态：已优化

## 对照证据

参考侧**没有 AutoSword 模块**：`_oe_ref/src/main/kotlin/studio/coni/epsilon/module/` 下 combat / misc / player 等全部目录都没有
`AutoSword.kt`，全仓库 grep `AutoSword` / `AutoWeapon` 零命中。等价能力散在两处：

| 关注点 | 参考（_oe_ref 相对路径:行） | 本工程（相对路径:行） | 判定 |
| --- | --- | --- | --- |
| 换剑入口 | `module/combat/KillAura.kt:229-232`：**只在 `attack(e)` 里**、发攻击包之前 `equipBestWeapon()`；`AutoTool.kt:30` 也复用同一函数 | `hacks/AutoSwordHack.java:82-92`：不是攻击前换，而是「准星指向可攻击实体」时换，且由 10 个 combat hack 在攻击路径上主动调 `setSlot()`（`KillauraHack.java:396`、`AimAssistHack.java:207`、`TriggerBotHack.java:170`、`ClickAuraHack.java:131`、`MultiAuraHack.java:386`、`ProtectHack.java:309`、`TpAuraHack.java:143`、`FightBotHack.java:161`、`KillauraLegitHack.java:240` 等） | 本工程覆盖面更大（攻击前那一 tick 必然已经调用过），**不改** |
| 换回旧槽 | 参考**完全没有** switch-back；`KillAura.kt:243-264` 换完就留在武器上 | `AutoSwordHack.java:39-49`：`Switch back`(默认 true) + `Release time`(默认 10 ticks) | 本工程独有功能 |
| 候选武器 | `KillAura.kt:246-262`：`for(i in 0..8)`，只收 `ItemTool` / `ItemSword` | `AutoSwordHack.java:156-167`：同样只扫 0..8，`TieredItem`（=剑/斧/镐/铲/锄）或 `TridentItem` | 等价（参考漏了三叉戟） |
| 空槽 | `KillAura.kt:248` `continue` | `AutoSwordHack.java:162-163` 返回哨兵 | 等价 |
| 评分（伤害） | `KillAura.kt:250,256`：`attackDamage + EnchantmentHelper.getModifierForCreature(stack, UNDEFINED)`；`util/items/Item.kt:33-44` 把剑的总伤害算成 `attackDamage + 4.0`（1.12.2 剑是纯 `attackDamage`，而 1.20.1 的 `SwordItem.getDamage()` 是**不含**玩家基础攻击力的数值） | `AutoSwordHack.java:179-189`：`EnchantmentHelper.getDamageBonus(stack, mobType)` + `SwordItem.getDamage()` / `DiggerItem.getAttackDamage()` / `TridentItem.BASE_DAMAGE` | **数值上差一个常数 1.0**：1.20.1 的 `net.minecraft.world.entity.player.Player.createAttributes()` 里 `ATTACK_DAMAGE` 基础值就是 1.0（字节码 `dconst_1`），武器只是加算 modifier，所以钻石剑真实伤害 7、本工程评 6。因为**所有候选都同样少 1.0**，相对排序不变（`AutoSwordWeaponScorerTest.damagePriorityOrderIsUnaffectedByTheSharedMissingBaseDamage` 把这个前提钉住）。**不改数值**：改成 7 会让 `Priority=DAMAGE` 的既有选择结果在边界同分场景下漂移，属于不可实机验证的手感改动 |
| 评分（攻速 / 冷却） | 参考**完全没有**攻速维度（`KillAura.kt:243-264` 里没有 ATTACK_SPEED） | `AutoSwordHack.java:39-41,176-177`：`Priority` 枚举默认 `SPEED`，取 `ItemUtils.getAttackSpeed(item)`（走 `EquipmentSlot.MAINHAND` 的 `Attributes.ATTACK_SPEED`，`ItemUtils.java:58-63`） | 本工程多一个维度。**不改成 DPS**：1.20.1 的冷却还受挖掘疲劳 / 潮涌能量等效果影响，DPS 需要实机调参才能定基准，见「建议（未做）」 |
| 附魔 | `KillAura.kt:250,256` 用 `getModifierForCreature`（1.12.2 的节肢杀手/亡灵杀手） | `EnchantmentHelper.getDamageBonus(stack, group)`（`AutoSwordHack.java:182`，`group` 取目标 `getMobType()`） | 等价且正确（1.20.1 里 Sharpness/Smite/Bane 全在这个方法里） |
| 槽位来源 | `util/inventory/operation/Hotbar.kt:135-139`：`swapToSlot(slot)` 先 `if (slot !in 0..8) return` 再写 `inventory.currentItem` | `AutoSwordHack.java:141` 直接写 `MC.player.getInventory().selected`；扫描范围 0..8 | **`+36` 偏移不存在，也不是 bug**：`Inventory#getItem(int)`（Mojmap 1.20.1）的 0..8 就是快捷栏本身，9..35 是主背包，网络槽位还要再 +36；本 hack 从头到尾只用 0..8，写进 `selected` 的是同一个坐标系（`util/InventoryUtils.java:171,197,218` 那套 `+36` 是给容器点击用的，本 hack 不碰容器）。新抽的纯类在签名层面固化「只接受快捷栏下标」 |
| 并发写 `selected` | 参考在 `swapToSlot` 里调 `playerController.syncCurrentPlayItems()` | 本工程直接改字段——`updateSelectedItem` 那条链在客户端 tick 里跑，`HeldItemRendererMixin.java:28` 也是拿 `AbstractClientPlayer.isUsingItem()` 做渲染判定；服务端在下一个出站包才看到换手 | 属于既有实现细节，不改 |

### 打断玩家动作（brief 第 4 点）

改动前，`setSlot` 只挡了 AutoEat 一个来源（`AutoSwordHack.java:108` `autoEatHack.isEating()`），
于是「正在使用物品」的其它情形（拉弓、举盾、喝药水、投掷、AutoEat 之外的进食）都会被换手打断。
原版使用中的物品一旦不在手上就会中断，本工程自己的代码也是按这个前提写的：

- `KillauraHack.java:904-908`：`if(MC.player.isUsingItem()) { hasBlockedSinceAttack = true; return false; }` ——「已经在用物品就不要再发起格挡」；
- `KillauraHack.java:515,762,1004,1034`、`MultiAuraHack.java:461,682-693,963`、`KillauraLegitHack.java:206,257`、
  `AimAssistHack.java:183`、`TriggerBotHack.java:167,202`、`NukerLegitHack.java:175`、`AutoMineHack.java:93`、
  `RightClickerHack.java:93`、`BowAimbotHack.java:117`、`NoSlowdownHack.java:79`、`SpeedHackHack.java:134`、
  `AutoSprintHack.java:92` 共 14 处外部 hack 都按 `isUsingItem()` 判断「手上有事」。

所以我用项目里统一的 `isUsingItem()` 作为守卫（`AutoSwordHack.java:151-154`），并且**对换手与换回两个方向都生效**
（`setSlot` 与 `resetSlot`）。只挡 `setSlot` 是不够的：如果玩家是在 AutoSword 换到武器之后才开始吃/拉弓，
计时结束时的 `resetSlot()` 照样会把物品从手上换走、打断动作。

打表结论：**AutoEat 本身不受影响**——`AutoEatHack.isEating()` 是 `oldSlot != -1`（`AutoEatHack.java:291-293`），
它在换到食物那一 tick 就为真（`:180-182`），而它自己的收尾 `:268` 不经过 AutoSword；AutoEat 停用后
AutoSword 的守卫随即放行。

## 实际改动

1. **新增 `setSlot` 守卫：正在使用物品时既不换手也不换回**（`AutoSwordHack.java:125-129`、`151-154`、`195-225`）。
   改前只挡 `autoEatHack.isEating()`，于是准星指向敌人时换剑会打断拉弓 / 举盾 / 喝药水；
   改后 `resetSlot()` 在手上忙时**推迟**收尾（`weaponSlot` 保留），停手后下一 tick 照常还槽。
   可验证：`AutoSwordWeaponScorerTest` 覆盖评分/选槽部分；守卫本身是「读 `isUsingItem()` 后早退」，
   其正确性依据是本工程 14 处同款判断（见上）。
2. **玩家自己换手后不再被拽回武器槽**（`AutoSwordHack.java:94-102`）。这是换回功能的真实边界 bug：
   `oldSlot` 一旦被记录就再没做过失效处理（改动前行号 `:136-138`，只在 `oldSlot == -1` 时写；
   改动后为 `:137-138`），
   所以玩家在 `Release time`（默认 10 ticks）内主动切到金苹果 / 盾牌时，`resetSlot()` 会把他切回换剑前的槽，
   而且这次多余的切换本身就是一次**打断**（正好落在那 10 tick 的窗口里）。
   现在检测到「当前槽 ≠ AutoSword 设的槽」就立刻停止接管并按正常流程还槽。
   可验证：`AutoSwordWeaponScorerTest` 不覆盖这段（需要世界状态），证据是 `onUpdate` 的判定链
   `weaponSlot != -1 && selected != weaponSlot`（`weaponSlot` 只在 `:145` 与 `resetSlot`(`:211`) 里写）。
3. **选槽循环抽成纯类 `util/AutoSwordWeaponScorer.java`**，把「只扫快捷栏 0..8 / 严格大于才替换 / 并列取下标小的 /
   哨兵 `Integer.MIN_VALUE` 永不被选中」四条原实现的隐含规则变成带单测的契约
   （`AutoSwordWeaponScorerTest` 6 个用例）。**行为逐条保持**：`selectBestSlot` 的比较算子、
   初始 `bestValue = Integer.MIN_VALUE`、`-1` 返回值都与 `AutoSwordHack.java` 改动前的循环一致。
   顺带修掉一个只有抽出来才会暴露的坑：新 API 的评分类型定为 `float` 而不是 `double`——
   哨兵 `Integer.MIN_VALUE` 经 `double` 往返会被舍入成一个**大于**哨兵的值，从而让空槽被选中
   （`AutoSwordWeaponScorerTest.refusesToPickUpTheSentinelValue` 钉住）。
4. **`resetSlot()` 增加「无接管则不动」的短路**（`AutoSwordHack.java:202-203`）：原实现每次没目标时都会写
   `weaponSlot = -1` 并可能读 `switchBack`，现在没有接管时直接返回，`onUpdate` 的收尾分支不再空转。

未改动：`Priority` / `Switch back` / `Release time` 三个设置的名字、默认值、取值范围全部原样；
`getValue()` 的评分数值与分支原样（含那个不可达的 `return Integer.MIN_VALUE;`，见「建议（未做）」）；
`setSlot(Entity)` 的公开签名原样（9 个外部调用方 `WURST.getHax().autoSwordHack.setSlot(...)` 不受影响）。

## 故意不搬

- **参考把换武器放在 `attack()` 里**（`KillAura.kt:229-232`）：本工程有 10 个攻击路径主动调 `setSlot()`，
  改成「只在自己 attack 时换」会让 ClickAura / TriggerBot / AimAssist 这些不走 attack 路径的 hack 失去换剑。
- **参考的伤害基线 `Item.kt:39-44`（剑 = `attackDamage + 4.0`）**：那是 1.12.2 的常量（剑的 `attackDamage`
  不含基础 1.0 + 物品 3.0 系），1.20.1 的攻击力走 `Attributes.ATTACK_DAMAGE` 属性，照抄常量必错。
- **参考 `KillAura.kt:243-264` 的 `maxDamage = 0.0` 起点**：它会让「评分全为 0 的候选」一个都选不上；
  本工程用 `Integer.MIN_VALUE` 起步，语义更干净，不换。
- `util/inventory/*` 那套 Task/Step 队列：本 hack 只改 `selected`，不做任何容器点击，
  没有可确认的事务要交给队列（见「建议（未做）」第 1 条）。

## 建议（未做）

1. **和 `InventoryActionQueue` 没有配合——这是设计如此，不是缺陷**。队列（`util/inventory/InventoryActionQueue.java:43,57,73`）
   处理的是「容器点击的事务确认与重试」，而本 hack 只写 `inventory.selected`，不点格子、不需要确认，
   接进队列只会让它多等一个 tick。真正值得做的是相反方向：让队列/hack 在换手前后知道
   `AutoSword` 曾临时占用过槽位（现在是靠 `oldSlot` 私有字段，外部拿不到）。
   这需要改共享文件或给 hack 加公开 API，本次不动。
2. **`Priority=DAMAGE` 不折算攻击冷却**（`AutoSwordHack.java:179-189`）：斧头单次伤害更高但攻速只有 1.0，
   DPS 可能低于剑。要做对得引入 1.20.1 的 `attackStrengthScale` 与受挖掘疲劳等影响的冷却速率，
   并需要实机标定权重——不可单测，故留待办。
3. **非 `TieredItem` 的高伤武器拿不到评分**：附了锋利 V 的木棍 / 自定义武器因为不是 `TieredItem`
   会被直接判成 `NOT_A_WEAPON`（`AutoSwordHack.java:171-172`）。正确做法是读
   `ItemStack.getAttributeModifiers(EquipmentSlot.MAINHAND)` 里的 `Attributes.ATTACK_DAMAGE`
   （签名已核对：`ItemStack.getAttributeModifiers`、`Attributes.ATTACK_DAMAGE` 均存在于 1.20.1）。
   它会同时消掉附表那 1.0 的常数差。没做的原因：这是**评分语义变更**，会改变既有用户的选择结果，
   且旧写法在纯原版武器上的排序与它一致，收益只在模组服体现。建议单独一轮做，并配 `AttributeValuePlanner` 风格的单测。
4. `getValue()` 里 switch 之后的 `return Integer.MIN_VALUE;`（`AutoSwordHack.java:192`）是不可达代码，
   纯装饰性清理，未动。
