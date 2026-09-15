状态：已优化

## 对照证据

参考侧没有对应模块（参考没有"洞内防击退"）。本项为自审，全文读过 `hacks/AnchorHack.java`（68 行）。

## 自审记录

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 洞的判定 | `:49-52` 四个水平方向相邻方块都不可替换 | 合理（`canBeReplaced()` 对空气/水/植物/雪都为真） |
| 洞内固定 | `:54-58` 每 tick 把水平速度清零、坐标拉回方块中心 | 正常（本模块用途） |
| 只在落地时生效 | `:44-45` `!onGround()` 直接返回 | 正常 |
| 无效字段 | 旧 `:20` `private int holeCheck;` | 声明后从未使用，已删除 |

## 实际改动：设置的说明与实现不符

旧描述是 `"Pulls you back into the hole when pushed out."`（被推出洞后把你拉回洞里），
但 `:59-66` 的实现是：

```java
			double dx = MC.player.getX() - (pp.getX() + 0.5);
			double dz = MC.player.getZ() - (pp.getZ() + 0.5);
			if(Math.abs(dx) > 0.3 || Math.abs(dz) > 0.3)
				MC.player.setDeltaMovement(-dx * 0.3, ..., -dz * 0.3);
```

其中 `pp` 是**玩家当前所在方块**（`:47` `BlockPos.containing(MC.player.position())`）。

**反例（Rule 9）**：站在洞里被击退 2 格、落在洞外另一个方块上（此时 `inHole` 为假）→
拉的方向是"朝你**现在**脚下的方块中心"，也就是说它会把你按在你被击退后落地的那个位置附近，
**不会**把你送回原来那个洞。代码里也没有任何字段记录过洞的坐标，所以这个功能在实现上不可能做到
描述里说的事。已把描述改成实情（并注明为什么改），未改动行为（改行为要新增"洞记忆"，那是新功能）。

## 未做 / 建议

- `:57` 用 `setPosRaw(...)` 而不是 `setPos(...)`：前者不会重算碰撞箱（`Entity.setPos()` 才会调用
  `setBoundingBox()`）。原版 `Entity.move()` 每 tick 末尾会 `setPos`，所以碰撞箱最迟在同一 tick 内被纠正，
  没有构造出实际可触发的反例，本次只记录。
