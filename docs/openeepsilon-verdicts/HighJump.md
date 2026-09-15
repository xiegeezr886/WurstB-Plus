状态：已优化

## 参考侧

`_oe_ref` 全仓 `.kt` 文件名匹配 `HighJump` 的结果为空——参考没有这个模块（它的 `LongJump.kt` 是水平跳，语义不同）。所以这一条不是"照搬参考"，而是按「旧实现不准确」自证并修掉。

## 对照证据

| 关注点 | 原版真源（1.20.2 反编译树:行） | 本工程（改前） | 判定 |
| --- | --- | --- | --- |
| 跳跃初速是什么 | `world/entity/LivingEntity.java:2046` `getJumpPower()` = `0.42F * getBlockJumpFactor() + getJumpBoostPower()`（跳跃提升是**加法**，蜂蜜块是 0.5 倍因子） | `hacks/HighJumpHack.java` `getAdditionalJumpMotion() = height * 0.1F`，由 `mixin/ClientPlayerEntityMixin.java:286-293` 加到 `super.getJumpPower()` 上 | 叠加方式本身与原版一致（都是加法），问题在 `height * 0.1` 这个换算 |
| 竖直运动方程 | `LivingEntity.java:2054-2058` `jumpFromGround()` 把竖直速度**直接设为** `getJumpPower()`（`:2056` 的疾跑加成只作用于水平 `vec3.x/vec3.z`）；`travel` 每 tick 先位移、再 `:2210` `d2 -= 0.08`、`:2216` `setDeltaMovement(..., d2 * 0.98F, ...)` | `height * 0.1F` 隐含假设是「无阻力抛物线 `h = v² / (2g)`」（`0.1 ≈ 1/(2·0.08) · 0.016`…即按 `g=0.08` 的连续解凑的） | **不符**：原版是每 tick `vy = (vy - 0.08) * 0.98` 的离散积分，阻力让真实最高点系统性**高于**该公式 |

## 数值反例（新旧行为差异）

`v0 = 0.42 + Height * 0.1`（旧） vs `v0 = requiredVelocity(Height)`（新），最高点按原版逐 tick 积分求和：

| Height | 旧 v0 | 旧真实最高点 | 新 v0 | 新真实最高点 |
| --- | --- | --- | --- | --- |
| 6（默认） | 1.02 | **6.135 格** | 1.007461 | 6.000 格 |
| 20 | 2.42 | **27.718 格** | 1.998827 | 20.000 格 |
| 100 | 10.42 | **269.352 格**（设定值的 2.7 倍） | 5.332548 | 100.000 格 |

自检锚点：`v0 = 0.42` → 最高点 **1.2522 格**，就是公认的原版跳跃高度。这条断言写在测试里（`JumpHeightSolverTest.vanillaJumpGetsTheKnownApex`），用来证明本类的积分与原版竖直运动一致，而不是我自己编的公式。

## 实际改动

1. 新增 `util/JumpHeightSolver.java`（纯数学，无任何 Minecraft 引用）：
   - `apexHeight(v0)`：逐 tick `height += vy; vy = (vy - 0.08) * 0.98`，累加所有正的位移；
   - `requiredVelocity(h)`：对 `apexHeight` 二分反解（上界先翻倍到够高，再 80 次二分）；
   - 常量 `GRAVITY = 0.08`、`DRAG = 0.98`，注释里直接写明对应 `LivingEntity#travel` 的 `d0` 与 `0.98F`。
2. `hacks/HighJumpHack.java`：`getAdditionalJumpMotion()` → `getJumpPowerFor(float baseJumpPower)`，返回**绝对**跳跃力 `requiredVelocity(Height)`；未启用时原样返回基准值。滑块的名称、默认值（6）、范围（1..100）全部未动；描述里那句 "This gets very inaccurate at higher values." 已经不成立，改成 "Solved against vanilla jump physics, so it stays accurate at higher values as well."
3. `mixin/ClientPlayerEntityMixin.java`：`getJumpPower()` 由 `super.getJumpPower() + hack.getAdditionalJumpMotion()` 改成 `hack.getJumpPowerFor(super.getJumpPower())`。
   为什么要把基准值传进去：跳跃提升是加在 `getJumpPower()` 里的（`LivingEntity.java:2046`），如果继续用"增量"语义，带上跳跃提升时就会在药水基础上再加一份 HighJump 的量、把药水那部分重复计入。改成绝对值后「Height = 真实高度」在有药水/蜂蜜块时同样成立。

## 故意不搬

参考没有 HighJump，无对应物可搬。

## 建议（未做）

1. `MAX_TICKS = 100000` 是纯安全上限（滑块最大 100 只需约 65 tick），不参与正常路径，未做优化。
2. 本类只解竖直。原版 `jumpFromGround` 只给竖直动量，所以不需要参数化的水平分量；若将来要让 HighJump 兼作"跳远"，需要另一个水平解算器，不做。
3. 验证边界：只做了 `compileJava`/`test`（含 `JumpHeightSolverTest` 的 4 条断言）。**没有实机跳过**——真实最高点还会受头顶碰撞、`fallDistance`、服务器拉回影响，本轮的"6.000 格"是纯竖直积分的数学结论，不是实测值。
