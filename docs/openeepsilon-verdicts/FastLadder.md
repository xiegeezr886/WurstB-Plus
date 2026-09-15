状态：不适用（参考侧无对应模块，本工程实现自洽，零改动）

## 参考侧核对

`_oe_ref/.../module/movement/` 25 个模块里没有 FastLadder。最接近的 `FastSwim.kt` 是水中加速，`AntiWeb.kt` 是抗蛛网减速，都不是"爬梯加速"。故判「不适用」，主体是自审。

## 本工程现状（自审）

`hacks/FastLadderHack.java`，53 行。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 生效条件 | `:43` `!onClimbable() \|\| !horizontalCollision` 返回 | **与原版同形**：1.20.2 真源 `world/entity/LivingEntity.java:2124-2126` 就是 `if (this.horizontalCollision && this.onClimbable()) vec36 = new Vec3(vec36.x, 0.2, vec36.z);`（原版自己在水里"贴着梯子/藤蔓上浮"就是这条）。所以"要推着梯子才加速"不是本工程的额外限制，而是原版判定 |
| 梯子有没有碰撞箱 | —— | 有：`Blocks.java:1538` 的 `LADDER` 用 `Properties.of().forceSolidOff()...`，**没有** `noCollission()`（对比下一行 `:1540` 的 `RAIL` 才写了 `noCollission()`），梯子有 3/16 厚的碰撞板，所以推进梯子会置 `horizontalCollision`；悬空独立的梯子也照样生效 |
| 需要输入 | `:46-48` `forwardImpulse == 0 && leftImpulse == 0` 返回 | 站定不加速，正确 |
| 爬升速度 | `:51` `setDeltaMovement(v.x, 0.2872, v.z)` | 0.2872 是原版 0.2（`LivingEntity.java:2125`）的约 1.44 倍。**用固定常数是对的**：原版爬梯速度来自 climbable 分支里的字面量，不经过 `Attributes.MOVEMENT_SPEED`——`handleOnClimbable`（`LivingEntity.java:2286-2301`）只做 `Mth.clamp(x, -0.15F, 0.15F)`/`Mth.clamp(z, ...)` 与 `Math.max(y, -0.15F)`，全是字面量钳制。所以这里**不该**像 `SpeedHackHack` 那样乘速度药水倍率（速度药水对爬梯无效） |
| 水平速度 | `:51` 原样保留 `v.x`/`v.z` | 正确：`handleOnClimbable` 会把水平钳到 ±0.15，本 hack 不去碰它 |

## 实际改动

无。逐条核对后没有可举证的新旧行为差异，按简报第 9 条不动代码。

## 故意不搬

参考侧无对应模块。

## 建议（未做）

1. `Mth.clamp` 的水平 ±0.15 意味着爬到梯子顶端翻上去时水平速度会被原版压住，本 hack 只改竖直分量，无法影响这一点，也不该影响。
2. 0.2872 与 `hacks/SpeedHackHack.java:28` 的 `BASE_SPEED = 0.2873` 数值几乎相同但没有共用：一个是爬梯竖直速度、一个是地面水平速度，语义无关，抽成共享常量只会制造耦合，未做。
3. 验证边界：零改动，只做了静态核对。
