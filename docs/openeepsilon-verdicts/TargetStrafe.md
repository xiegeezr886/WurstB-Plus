状态：已优化

## 对照证据

| 关注点 | 参考（`module/combat/TargetStrafe.kt:行`） | 本工程（`hacks/TargetStrafeHack.java:行`） | 判定 |
| --- | --- | --- | --- |
| 触发条件 | `:57-63 canStrafe()`：必须 `KillAura.currentTarget != null`、不是 AntiBot、`Speed` 开着、`KillAura` 开着，`holdSpace` 时还要按住空格 | 原来：全图**任意**"最近的可攻击实体"，与 KillAura 无关联、没有搜索距离 | 已按参考改成"优先 KillAura 当前目标"，其余用 `Search range` 限制 |
| 环绕半径 | `:18 range` 默认 3.0（0.1..15） | `:29-31 Range` 默认 2.5（0.5..6） | 同义（都是保持距离） |
| 距离判定 | `:39-43`：`dist <= range` → `forward = 0`，否则前进 | `:90-97`：`dist > range+0.5` 靠近、`< range-0.5` 后退、其余环绕 | 同义 |
| 撞墙处理 | `:31-35`：`collidedHorizontally && !Scaffold.isEnabled` → `invertStrafe()` | 原来**完全没有** | 已加，见「实际改动」 |
| 移动方式 | 改写 `movementInput`（forward / yaw / direction），依赖它的 `Speed` 模块与 `RotationUtil.getRotationsGucel` | `:103-104` 直接 `player.setDeltaMovement(...)`（不依赖其它模块） | 故意不搬（见下） |
| 空格门槛 | `:17 holdSpace` 默认 true | 无 | 建议（未做） |

## 实际改动

### 1. 目标选择：优先 KillAura 的当前目标，其余受 `Search range` 限制

`findTarget()`（`:110-129`）现在先看 `getKillauraTarget()`（`:137-152`，读
`WURST.getHax().killauraHack.getCurrentTarget()`，要求 KillAura 开着、目标存活、且满足
`Players only` / `Ignore friends`），拿不到才退回到"`Search range`（默认 8 格）内最近的可攻击生物"。

**反例（Rule 9）**：旧版本里 `Range` 只决定"环绕半径"，不限制搜索——目标在 20 格外时
`findTarget()` 依然返回它（只按 `distanceToSqr` 取最小），`dist > range+0.5` 分支于是每 tick 把玩家速度设成
朝它 0.28 → 表现成"自动走过去"，`Range` 滑条怎么调都没用；新版本 8 格外不选，且 KillAura 有目标时
只围着那一个转（与参考 `canStrafe()` 的前提一致）。

### 2. 撞墙反向

新增 `directionSign`（`:64-66`）与 `wasColliding` 字段，环绕分支里：

```java
		if(MC.player.horizontalCollision && !wasColliding
			&& !WURST.getHax().scaffoldWalkHack.isEnabled())
			directionSign = -directionSign;
```

**反例**：旧版本贴着墙环绕时，每 tick 的速度都指向墙面 → 玩家原地卡住、方向永远不换（参考侧
`TargetStrafe.kt:31-35` 正是在处理这个）；新版本在"刚撞上墙"的那一 tick 反向，`ScaffoldWalk`
开着时不反向（避免和搭桥抢移动输入，与参考的 `!Scaffold.isEnabled` 条件一致）。只在上升沿翻转是为了
避免贴墙时每 tick 来回抖。

另外顺手删掉了两个与 `EntityUtils.IS_ATTACKABLE` 重复的过滤（`e != MC.player`、
`!(e instanceof FakePlayerEntity)`），`IS_ATTACKABLE`（`util/EntityUtils.java:38-45`）已经包含这两条。

## 故意不搬

- 参考通过改写 `movementInput`（并依赖 `Speed` 模块）来移动：本工程没有等价的输入改写层，
  直接设速度更自洽，也不强制用户开另一个 hack。代价是参考的"贴墙时由 Scaffold 决定"那套输入协作
  无法照搬，本工程改用"ScaffoldWalk 开着就不反向"来近似。
- `holdSpace`（按住空格才工作）没搬：本工程 `Auto jump`（默认开）已经在处理跳跃，再要求按住空格
  只会让"开了没反应"更难排查。

## 建议（未做）

- 反复"撞上一个角落"时，上升沿反向只能救一次；如果以后要更稳，可以记录最近 N tick 的碰撞次数再决定
  是否反向，或直接沿墙切向滑动。属行为取舍，未做。
- 目标切换时没有重置 `directionSign`（参考也没有），如果你希望每次换目标都从设置里的方向重新开始，
  需要在 `onUpdate` 里比较 `target` 引用。
