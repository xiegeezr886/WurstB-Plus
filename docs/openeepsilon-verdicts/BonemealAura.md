状态：不适用

## 对照证据

参考侧有 `player/BonemealAura.kt`（1.12.2 的骨粉光环）。本工程 `hacks/BonemealAuraHack.java`
（281 行）实现相同，并多了"自动化等级"和按作物类型分开关。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 触发时机 | `:101-108` `HandleInputListener`，先看 `MC.rightClickDelay`、挖掘中、手忙 | 正确 |
| 可催熟判定 | `:174-204` `instanceof BonemealableBlock` + `isBonemealSuccess(...)` 双重校验，再按 树苗/作物/茎/可可 分类套各自的开关 | **正确**：`isBonemealSuccess` 由原版按随机数与状态判断，能过滤掉"已经长满"的作物 |
| 草方块 | `:184-185` 显式排除 | 正确（草方块催熟是长草/花，不属于"作物"，已写进代码注释） |
| 从远到近 | `:161-166` 远处的先催熟 | 正确（作物长大后会挡住后面植物的视线，代码里有注释说明） |
| 自动化等级 | `:121-126` 手上没有骨粉时按 `AutomationLevel.maxInvSlot`（0/9/36）从背包里找 | 正确 |
| 与 AutoFarm 互斥 | `:117-118` `autoFarmHack.isBusy()` 时不动 | 正确 |
| Fast/Legit | `:128-151`、`:206-236` | 与设置说明一致（Legit 才做距离+视线检查） |
| 关闭 | `:94-98` 摘监听 | 平衡 |

无改动。

## 说明（共享观察）

- `:107` 的 `isHandsBusy()` 在 1.20 里只表示"驾驶船并按方向键"（`LocalPlayer.java:877-888`）；
  没有构造出可触发的反例，只记录。
