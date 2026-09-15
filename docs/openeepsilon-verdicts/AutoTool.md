状态：不适用（参考侧 `misc/AutoTool.kt` 只有两个设置、逻辑全在共享 util 里；本工程实现是它的超集，零改动）

## 对照证据

参考侧：`_oe_ref/src/main/kotlin/studio/coni/epsilon/module/misc/AutoTool.kt`（全文约 30 行）。

| 关注点 | 参考（AutoTool.kt:行） | 本工程（hacks/AutoToolHack.java:行） | 判定 |
| --- | --- | --- | --- |
| 规模 | 全文约 30 行，`:18-19` 两个设置（`Switch Weapon`、`Prefer`） | 289 行，`:37-56` 四个设置（`Use swords` / `Use hands` / `Repair mode` / `Switch back`） | 本工程是超集 |
| 选工具 | `:10,24` 委托给 `util.inventory.equipBestTool(world.getBlockState(pos))` | `:114-146 equipBestTool` → `:148-181 getBestSlot`：遍历快捷栏 0..8、跳过当前格、`:183-196 getMiningSpeed` 把 `EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_EFFICIENCY, stack)` 算进比较 | 本工程把附魔效率纳入比较（参考那个共享 util 的内部实现本轮**没有**核对，此判定的依据只在本文件） |
| 损坏保护 | 无 | `:198-206 isDamageable/isTooDamaged` + `:208-247 putAwayDamagedTool`（优先空格 → 不可损坏物品 → 没坏到阈值的物品 → 兜底与左上角交换）+ `:45-50 Repair mode` 阈值滑条 | 本工程独有 |
| 挖完复位 | 无 | `:51-55 Switch back` + `:85-97` 在 `BlockBreakingProgressListener` 里记下"挖之前选的格子"，`:99-112` 停止挖掘且不再挖掘时切回；记的是 `prevSelectedSlot`，`onEnable` 置 -1 | 本工程独有，状态机自查无泄漏（关闭/停止后都会复位） |
| 触发时机 | `:22-24` 每 tick 看准星指向的方块 | `:85-97` 只在**开始挖掘**事件里触发，而不是每 tick | 本工程更省，也不会"只是看着方块就换手" |
| 剑/空手 | `:18-19,29` `Switch Weapon` + `Prefer SWORD/AXE`：攻击生物时切武器 | `:37-43` `Use swords`（挖方块时是否允许用剑）/ `Use hands` | 参考那条是"打怪自动换武器"，本工程这类逻辑在战斗模块里；在 AutoTool 里加属新增设置，不搬 |

## 实际改动

无。没有可举证的新旧行为差异，按简报第 9 条不动代码。

## 故意不搬

`AutoTool.kt:18-19,29` 的 `Switch Weapon`/`Prefer`：新增设置（简报第 5 条），不搬。

## 建议（未做）

1. `:216-246` 把快坏的工具收进背包时用 `im.windowClick_SWAP(slot, selectedSlot)`：这是"整格互换"，一旦那几秒里
   客户端与服务端的背包视图不同步（例如正被别的动作占用），服务端会把**它那边**该格的内容换出来，可能换错东西。
   本工程已有 `util/inventory/InventoryActionQueue`（带提交时间、重试窗口、菜单 id 校验），更稳的做法是走队列并等确认；
   但这要改动 AutoTool 整套"同步换手"路径，未做。
2. `:183-196` 只比较"当前挖掘速度"，没有考虑"耐久还能挖几个方块再换更划算"；属策略增强，未做。
3. 验证边界：零改动，只做了静态核对；挖掘手感与"快坏工具自动收走"没有实机验证。
