状态：已优化

## 对照证据

参考侧没有对应模块（参考没有 Baritone 集成）。本项审计方式：**结构核对 + 关键区段精读**，
未逐行精读全部 300 行。

| 关注点 | 结论 |
| --- | --- |
| 事件对称 | `:57` 加 `UpdateListener`，`:65` 摘 —— 成对 |
| 关闭清理 | `:63-68` 摘监听 + `BaritoneUtils.stop()` + `currentTree = null` | 正确 |
| 前置条件 | `:47-53` `canEnable()` 要求 Baritone 已加载 | 正确 |
| 中途卸载 Baritone | `:49`、`:73-75` 检测到不可用就自动关闭 | 正确 |
| Baritone 调用 | `:84` `stop()`、`:87` 判断是否在寻路、`:106` `walkTo`、`:199` `startMining` | 用法一致 |
| 补种 | `:80-84` 砍完后按 `Replant` 开关补种树苗 | 与设置说明一致 |
| 名牌 | `:269` 只在 `isEnabled() && isPathing()` 时加 `[Mining]` 之类的后缀 | 正确 |
| 未发现 | 未发现反射、未捕获异常、未受控线程、写死的世界边界常量、按键泄漏 |

无改动。

## 建议（未做）

- 未逐行审计砍伐目标的识别与补种细节；没有实机验证（需要 Baritone）。

## 第二轮精读（发现并修掉一个"开关完全无效"的缺陷）

读完 `:71-300`（onUpdate / findTree / isLog / isTreeBase / collectTreeLogs / mineTreeLogs / replantSapling /
getSaplingForLog / findSaplingInInventory / TreeTarget）。

### 实际改动：`Replant` 开关是空操作

旧实现：

```java
	private void replantSapling(BlockPos pos)
	{
		Block block = BlockUtils.getBlock(pos);      // ← 砍完之后这里已经是空气
		Block sapling = getSaplingForLog(block);     // ← 空气查表 ⇒ null
		if(sapling == null)
			return;                                  // ← 永远从这里返回
```

调用点在 `:79-85`：`if(currentTree != null && !currentTree.hasLogs())` —— 也就是**树已经被砍光之后**才调用
`replantSapling(currentTree.basePos)`，此时 `basePos` 上的原木早没了（`TreeTarget.hasLogs()` 判定"空气"才算砍完），
`BlockUtils.getBlock(pos)` 返回 `Blocks.AIR`，而 `getSaplingForLog()` 只认 8 种原木、对空气返回 null
⇒ **勾上 Replant 也永远种不下树苗**（反例：开着 Replant 用 BaritoneTreeBot 砍一棵橡树，全程不会出现补种动作）。

修法：在发现树的时候就把树根处的原木种类记进 `TreeTarget`（`:128` `new TreeTarget(pos, block)`，
新增字段 `baseLog`），`replantSapling(BlockPos pos, Block baseLog)` 改用这个记录值查表。
顺带说明：`:218-230` 直接改 `inventory.selected` 换手苗再改回来是安全的
（原版 `MultiPlayerGameMode.ensureHasSentCarriedItem()` 在每次交互前补发换手包，`useItemOn` 正是走这条路）。

### 已核对无问题 / 留档

- `mineTreeLogs()` 传的是"方块种类"给 `BaritoneUtils.startMining()`，所以它砍的是**该种类所有原木**而不只是这一棵：
  这是 Baritone 的接口语义，属设计取舍。
- `getSaplingForLog()` 覆盖 8 种主世界原木 + 红树胎生苗；下界菌柄（`CRIMSON_STEM`/`WARPED_STEM`）在
  `isLog()` 里算原木但查表返回 null ⇒ 不补种，正确（它们本来就没有树苗）。
- `isTreeBase()`（`:148-157`）：`return !isLog(below) || below == DIRT || ...` 里 `||` 右边那一串是**死代码**
  —— 如果 `!isLog(below)` 为假，说明 below 是原木，而原木不可能等于 DIRT/GRASS_BLOCK/…，所以右边永远不改变结果。
  实际语义是"下面不是原木就算树根"，这会让"砍掉底下一截后悬空的原木"也被当成新树根 ⇒ 对 TreeBot 来说反而有用
  （会继续去砍它），没有反例说明它是错的，按第 9 条只记录、不改。