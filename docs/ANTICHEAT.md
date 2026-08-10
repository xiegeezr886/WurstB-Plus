# WurstB+ Plus 反作弊兼容研究报告

> 审计日期：2026-08-09
> 研究对象：Grim 2.0 分支、历史 NoCheatPlus 与 EdGrim 开源源码
> 目标：解释检测模型、标注客户端兼容风险，并为本地、授权测试环境建立可复现基线

## 0. 范围、快照与证据等级

本文只对仓库内三份源码快照负责，不把默认配置阈值称为任何服务器上的“安全值”。服务端可修改配置、叠加其他插件、使用私有检查或更换 fork；ViaVersion、服务端版本和客户端协议也会改变分支路径。

| 快照 | 可验证身份 | 归档时间 | SHA-256 |
|---|---|---|---|
| `source/Grim-2.0.zip` | `baseVersion=2.3.74`，构建描述支持 MC 1.8-26.2；压缩包不含 Git 元数据 | 2026-08-04 | `91D6A7FC254C031A2CF1B018114665D94C272886650F014EB6B6BEAE0ADCECB7` |
| `source/NoCheatPlus-master.zip` | `NoCheatPlus 3.16.1-SNAPSHOT`，历史 POM 面向 Bukkit 1.9；不是现代 NCP 的充分代表 | 2018-08-25 | `6915EE267686D2319783746B0B142C64E33C70E2CAEFDFA8B617E2B5DBF883ED` |
| `source/EdGrim-main.zip` | `baseVersion=26.06.10`，README 声明大量 AI 生成代码；压缩包不含可验证的上游提交号 | 2026-07-03 | `7F536732CEB3CAEF64F54E659D16987853B5F5D490A2C632CF88B2B7421C45C1` |

现代 NCP 维护仓库 `Updated-NoCheatPlus/NoCheatPlus` 在审计时的 `master` 为 `c67e4a4`。受网络限制只完成了 `fight/Reach.java` 抽样核对：动态 `reachMod` 结构仍存在，但不能据此推定其余历史阈值未变。

证据等级：

- **A（源码事实）**：当前快照中的控制流、常量或默认配置，可由附录路径复核。
- **B（静态推导）**：从源码计算出的边界，尚未在真实服务端验证。
- **C（实验假设）**：依赖包序、延迟、协议转换、服务端配置或多插件组合，必须在隔离环境抓包验证。

下文出现“阈值”“允许量”时，默认只是对应快照与默认配置的观察值，不代表规避检测、不会回弹或不会被其他检查命中。

---

## 1. 概述：两种检测哲学

| | GrimAC 2.0 | NoCheatPlus |
|---|---|---|
| 检测方式 | **预测器/穷举仿真**：枚举所有合法输入组合 → 完整物理仿真（重力/碰撞/摩擦）→ 与客户端实际位移比对 | **启发式包络**：重力/跳跃包络模型 + 频率计数 + 旋转统计，VL 软积累 |
| 时间基准 | 事务（transaction）往返时钟，目标是降低 ping 波动影响 | `TickTask.getLag()` 粗略 lag 容忍 |
| 惩罚 | Setback 回弹 + 优势衰减（软） | VL 分档：cancel → kick |
| 主杀器 | `Simulation (OffsetHandler)`：offset > 0.001 | SurvivalFly 包络 + MorePackets(22pps) + Angle/YawRate |
| 主要局限 | 候选集、补偿状态和协议翻译错误会造成误差；配置可调 | 大量规则依赖历史 Bukkit 事件语义、配置和 VL 累积 |

**总结论先行**：

- Grim 对持续位移偏差、Timer、Reach 和击退有相互独立的检测链。0.03 与不确定性代码是误报补偿，不是可直接相加的通用移动预算。
- 本地 NCP 快照适合研究经典包络、VL 和频率模型，但年代过旧；任何面向现代服务器的结论都需要用实际插件版本重做。
- EdGrim 扩大了旋转、点击和延迟检测面，但该快照存在实验代码、不可达分支和默认阈值放宽，必须逐检查验证，不能只看类名数量。

---

## 2. GrimAC 2.0 深度分析

### 2.1 架构与线程模型

- 核心全部在 `common/src/main/java/ac/grim/grimac/`（**Java**，非 Kotlin）
- 所有移动/战斗检测在玩家连接的 **Netty 线程**上同步执行（`CheckManagerListener` → `MovementCheckRunner`）
- 服务器 50ms tick 仅驱动：`TickManager`（重置标记）、末尾事务包、实体插值
- 时间基准是**事务往返**（`GrimPlayer.sendTransaction()`，每 tick 至少一次，负 ID 自增；`getPlayerClockAtLeast()` 用事务往返校准玩家时钟；`max-transaction-time: 60s` 超时踢出）
- 服务端 tick 末尾注入事务包（`BukkitTickEndEvent`，Paper 用 `ServerTickEndEvent`，否则 Unsafe 反射 Hook 连接列表）

### 2.2 预测引擎原理（`predictionengine/`）

对每个带坐标的移动包：
1. 计算实际位移 `actualMovement`
2. 豁免判定：spectator / 飞行（源码注释自认 `"LUNAR HAS FLYING CHEATS!!! HOW CAN I CHECK..."`）/ 死亡 / elytra → 不预测
3. `fetchPossibleStartTickVectors`：构建起始速度候选集合（Normal / Swimhop(y=0.3) / Climbable(y=0.2) / Trident / Knockback / Explosion / SlimePistonBounce）
4. `applyInputsToVelocityPossibilities` → `loopVectors`：**暴力枚举输入**（forward/strafe × 慢速翻转 ×2 × 使用物品翻转 ×2 × stuck-speed 位掩码）
5. 碰撞仿真（`doSeekingWallCollisions` → `Collisions.collide`，`COLLISION_EPSILON = 1.0E-7`）
6. 选最小误差向量：`resultAccuracy = distanceSquared(actualMovement)`，早停阈值 `1e-5²`
7. `offset = predictedVelocity.vector.distance(actualMovement)` → `reduceOffset(offset)`
8. 分发 `PredictionComplete` 到所有 PostPrediction 检测器
9. 结转 `clientVelocity`（碰撞摩擦 + 重力 + 空气阻力 0.98）

### 2.3 0.03 协议兼容机制

**背景**：1.9 移除了 idle 包，客户端不发"零位移"包 → 位移 < 0.03（旧版协议精度限制）时服务器无法区分"真没动"和"跳过了一 tick 物理演化"。1.18.2 修复此问题。

**阈值**（`GrimPlayer.getMovementThreshold()` :655）：`isPointThree() ? 0.03 : 0.0002`（客户端协议 <1.18.2 时为 0.03）。

**判定**（`PointThreeEstimator.determineCanSkipTick` :343-396）：满足任一即认为"本 tick 位移可能 <0.03"：
- 最后移动包未含位置（`didLastMovementIncludePosition == false`）
- 近爬梯 / 被实体推挤 / 烟花可用
- 滑翔中或刚结束
- 任意起始向量碰撞仿真后总位移 < 0.03
- 载具内**无** 0.03

**竖直方向自由度**（`getAdditionalVerticalUncertainty` :414-467）：
```
minMovement = 0.003 (1.9+) / 0.005 (1.8)   // 竖直速度 < 此值直接归零
循环: yVel = (yVel - gravity) × 0.98，直到累计位移 ≥ 0.03
```
即：0.03 判定成立时，竖直位移允许在"重力演化曲线"上自由取值——只要每 tick 增量 ≤0.03。

**0.03 候选向量集**（`addZeroPointThreeToPossibilities` :237-288）：

| 候选 | 值 | 条件 |
|---|---|---|
| 通用 0.03 | Y = `clientVelocity.y`（竖直不可控时沿用）| 恒有 |
| 沿用 Y | `(0, clientVelocity.y, 0)` | `controlsVerticalMovement()` = 近水/爬梯/流动液/气泡柱/滑翔/粘液/kb/爆炸 |
| **Swimhop** | `(0, 0.3, 0)` | 近流体 + 不贴地 |
| **爬梯** | `(0, 0.2×摩擦后, 0)` | 近爬梯 |
| **跳跃** | `JumpPower.jumpFromGround`（0.42×跳速倍率）| `addJumpsToPossibilities` |
| Riptide | 三叉戟完整向量 | `tryingToRiptide` |

**审计结论（A/B）**：

- `0.03` 是“客户端可能跳过位置更新”时扩大候选状态的条件，不是任意方向、任意时刻可额外移动 0.03。`determineCanSkipTick()` 仍会对起始速度、输入、碰撞和介质做仿真。
- `controlsVerticalMovement()` 只改变候选与额外竖直不确定量的构造；它没有让 Y 轴完全自由，结果仍受 `VectorData` 类型、重力迭代、碰撞和后续检查约束。
- 无位置包携带 `onGround=true` 时，`CheckManagerListener` 会调用 `slowCouldPointThreeHitGround()`；不可行时强制重同步。之后 `groundspoof/NoFall` 还会检查脚部碰撞盒并可改写包。因此这只是邻地形条件分支，不足以推出通用 NoFall。
- `0.003/0.005` 是客户端速度归零规则，与位置包阈值 `0.03` 不是同一个预算，不能相加使用。

### 2.4 不确定性箱参数（`handleStartingVelocityUncertainty` :489-719）

| 窗口 | 豁免 | 源码行 |
|---|---|---|
| 飞行状态切换后 **4 tick** | **水平 ±0.3 + Y +0.3**（Grim 自认 hack）| :511-514 |
| 水下飞行 hack 后 9 tick | Y +0.2 | :516-518 |
| 硬碰撞实体（船/潜影贝）后 **2 tick** | 水平 +0.1、Y +0.1 | :520-523 |
| 同上 **3 tick** | 该不确定性分支的额外碰撞盒扩展量可降至 0；不是“任意碰撞” | :677-679 |
| 活塞推拉 | 水平 +0.1、Y +0.1 + 碰撞盒按活塞向量展开 | :525-528, 707-718 |
| 0.03 流体 | 水平/垂直 +0.028；气泡柱 **±0.35** | :568-574 |
| 0.03 竖直演化 | Y ±重力迭代 | :560-565 |
| `onGroundUncertain` | Y 落地重置 + 反弹速度 | :549-557 |
| 载具切换 **1 tick** | 碰撞盒 ±speed×friction + 0.05 | :683-698 |
| 载具切换 **10 tick** | ±0.001 | :700-702 |
| 实体推挤 | 水平 `avgColliding × 0.08` | :540 |
| 头顶方块 + 1.13+ 0.03 向上 | 额外碰撞盒扩展量可为 0 | :677 |
| slime 隐藏弹跳 | `thisTickSlimeBlockUncertainty` | :583-590 |
| 不可预测重力（levitation 变化）| Y ±gravity | :578-580 |

**解释边界（B/C）**：表中数值会参与候选箱或 offset 缩减，不等于客户端可以直接使用同等幅度。尤其“碰撞盒扩展到 0,0,0”表示该分支不再增加额外扩展量，并不表示任意穿墙；飞行切换、载具和气泡柱还分别受状态同步、碰撞、Phase、GroundSpoof 与包序检查约束。

### 2.5 Simulation 违规数学模型（`OffsetHandler.java` 全文）

```
flag:      offset ≥ threshold(0.001)
违规 tick: advantage += offset
合法 tick: advantage ×= 0.999（setback-decay-multiplier）
setback:   advantage ≥ max-advantage(1) 或 offset ≥ immediate-setback-threshold(0.1)，且 violations ≥ 1
ceiling:   advantage ≤ max-ceiling(4)
```

**修正后的数学（A/B）**：

- 连续违规 tick 不执行 `0.999` 衰减。若每次 offset 固定为 `o`，则 `A_n=min(A_0+n×o, 4)`；默认配置下 `o=0.001` 最迟约 1000 个连续违规 tick 累积到 `max-advantage=1`，不是永不回弹。
- 只有合法 tick 才满足 `A_n=A_0×0.999^n`。从 `A_0` 衰减到目标 `A_t` 需要 `n≥ln(A_t/A_0)/ln(0.999)`；浮点值只趋近于 0，不会按固定 tick “回零”。
- 单 tick `offset≥0.1` 可直接满足立即回弹条件，但仍受 `flag()`、权限和 `setback-violation-threshold` 控制。

**`giveOffsetLenienceNextTick` 复核（A）**：当前 `2.3.74` 快照在同一次 `onPredictionComplete()` 中先写入 `lastHorizontalOffset/lastVerticalOffset`，随后立即由 `removeOffsetLenience()` 清零；`MovementCheckRunner` 下一轮也会再次清零。按当前控制流看不到跨 tick 自我放大，方法名和注释不能作为行为证据。若要确认事件总线插件是否能在中间观察该值，需要运行时插桩。

**回弹是软惩罚**：`SetbackTeleportUtil.executeViolationSetback()` 回弹到 `lastKnownGoodPosition`（最近预测通过的位置），默认不踢；回弹后 `blockOffsets → offset = 0`（首 tick 免费）。

### 2.6 输入枚举与跳跃状态机

**`loopVectors`（:727-812）**：
- 枚举空间：`forward ∈ {-1,1}`（sprint 时强制 `{1}`，防 omni-sprint）× `strafe ∈ {-1,1}` × 慢速翻转 ×2 × 使用物品翻转 ×2 × stuck-speed 位掩码
- **1.21.2+ `KnownInput`**：精确收窄到已知按键（`knownInput.jump()/forward()/backward()/left()/right()`），减少旧协议下的候选歧义；它约束输入与位移一致性，但不能单独证明所有输入自动化不可行
- 1.8~1.21.1 靠"位移必须落在合法输入集合内"隐含校验

**跳跃候选条件**（`PredictionEngineNormal.addJumpsToPossibilities` :46-76）：
```
(jumpBoost ≥ 0 && player.onGround) || !player.lastOnGround   // 才允许跳
1.21.2+: knownInput.jump() 必须为 true
```
→ 跳跃候选依赖 Grim 的地面状态与协议输入。仅伪造 `onGround` 还会经过强制重同步、GroundSpoof/NoFall 和预测结算，不能从该条件推出稳定 AirJump。

### 2.7 Timer 数学（`Timer.java` 全文）

```
每移动包: timerBalanceRealTime += 50ms
flag:      balance > System.nanoTime()   // 即领先真实时间
drift:     120ms（clockDrift）           // 允许的最大落后量
```
事务锚定玩家时钟（`knownPlayerClockTime = lastMovementPlayerClock`），显著降低单纯 ping 波动造成的影响；源码注释声称可避免 ping 波动误报，但这不是对 Netty 调度、代理排队或插件交互“免疫”的形式化证明。

**爆发式 timer 极限**：
| 倍率 | 最大爆发时长（120ms 内）|
|---|---|
| 1.5× | 0.24s（4.8 tick）|
| 1.1× | 1.2s（24 tick）|
| 1.05× | 2.4s（48 tick）|
| 1.01× | 12s（240 tick）|

这些数值是把 `drift=120ms` 简化为理想均匀发包模型得到的上界（B）。实际还受事务锚点、静止重置、`TimerLimit`、包取消和回弹影响，不能当作可用爆发配额。工程结论仍是：不要为 Grim 画像提供主动 Timer 增益。

### 2.8 Reach 精确模型（`combat/Reach.java` 全文）

**检测时机**：攻击包记录攻击时刻位置，**下一 tick 包**做完整 raycast（:69-94, :228-256）。

**maxReach 计算**（`applyReachModifiers` :344-372）：
```
maxReach = ENTITY_INTERACTION_RANGE 属性（默认 3.0）
hitboxMargin = threshold(0.0005) + 1.7/1.8 协议 +0.1 + movementThreshold(0.03/0.0002)
```

**默认几何容差公式（A/B，不是安全距离）**（眼睛 → hitbox 拦截点）：

`targetBox.expand(Reach.threshold + legacyHitboxMargin + conditionalMovementThreshold + itemHitboxMargin)`

| 客户端协议 | 默认分支 |
|---|---|
| `<1.9` | 基础 legacy margin 为 0.1，加 `Reach.threshold=0.0005`；只有特定无位置包路径再加 0.03，因此是 3.1005 或条件性的 3.1305，不应固定写成后者 |
| 1.9-1.18.1 | 通常 `canSkipTicks()`，加 0.03 与 0.0005，静态几何值约 3.0305 |
| 1.18.2-1.21.1 | movement threshold 变为 0.0002，静态几何值约 3.0007 |
| 1.21.2+ | 支持 end-tick 时通常不再无条件增加 movement threshold；1.21.11+ 还可能由物品 `ATTACK_RANGE` 组件改变 max range 和 hitbox margin |

**其他关键**：
- raycast 射线长度为 `maxReach + 3`，只是保证可搜索到“未命中 hitbox”情形，不会把合法攻击距离增加 3 格
- 3 个候选视线向量（当前 / lastYaw+currentPitch / lastYaw+lastPitch，兼容 MC-67665）→ 攻击前 1 tick 内转头被兼容
- `isKnownInvalid` 实时预判：`getMinReachToBox > maxReach` 时可直接取消包（`block-impossible-hits` 默认开）；注释只称实时路径“大约取消 3.05+”，精确的下一 tick raycast 仍可能对更小超距 flag
- 完全未命中 hitbox → 转发 `Hitboxes` flag
- 豁免：创造/旁观者、载具内、目标骑乘、船/潜影贝、死亡实体
- 回弹期间攻击包直接取消（`shouldBlockMovement()`）

### 2.9 击退跟踪模型（`velocity/KnockbackHandler.java`）

**事务夹层**（:74-77）：`ENTITY_VELOCITY` 发送前 `sendTransaction()`，发送完成后 `tasksAfterSend` 再发一个 → kb 向量被精确锁定在两个事务之间。事务应答时（`tickKnockback` :125-151）kb 移入"必须已吸收"队列。

**判定**（:179-231）：
```
likelyKB.offset（kb 候选向量与实际位移的最小误差）> 0.001 → flag
threshold = min(threshold + offset, 4)；offset ≥ 0.1 或 threshold ≥ 1 → setback
完全未吸收（offset = MAX_VALUE）→ "ignored knockback" 标记
```

**结论（A/B）**：`knockbackPointThree` 会让相关预测 offset 进入最小值比较，并非无条件豁免；默认 `threshold=0.001`。能否命中取决于完整击退向量、碰撞、药水、爆炸和候选速度，不能把“减免百分比”直接换算为 flag。工程上应默认关闭主动击退缩放，并通过授权测试确认服务端画像，而不是依赖未经验证的自动指纹。

### 2.10 基础旋转与包序检查

Grim `2.3.74` 并非“没有旋转检测”。`checks/impl/aim/` 至少注册了：

- `AimModulo360`：检测前一帧转角较小、当前 yaw 仍在 `(-360,360)`，但单次 delta yaw 超过 320° 的异常包装。
- `AimDuplicateLook`：在排除传送、1.17 重复包和载具后，检测 from/to 完全相同的 look 更新。
- `AimProcessor`：从小角度样本的 GCD 众数估计灵敏度，当前主要作为其他检查的处理器。

此外还有 26 个 `BadPacketsA-Z`、`PacketOrderA-P`、`MultiInteractA/B` 和 `Hitboxes`。因此“没有高级目标跟踪统计”不能简化为“旋转和战斗包序不受检查”。

---

## 3. NoCheatPlus 深度分析

> 本章分析的是 2018 年的 `3.16.1-SNAPSHOT`。除非目标服务端明确使用该版本或同源代码，否则所有默认值最多是历史参考。现代 Updated-NoCheatPlus、定制 NCP fork、ProtocolLib 版本和服务端事件语义都可能改变结果。

### 3.1 架构与检查体系

- 核心：`NCPCore/src/main/java/fr/neatmonster/nocheatplus/checks/`
- 触发：Bukkit 事件（`PlayerMoveEvent` LOWEST、`EntityDamageByEntityEvent` LOWEST、`InventoryClickEvent` 等）+ ProtocolLib 数据包（`MovingFlying`、`UseEntityAdapter`、`KeepAliveAdapter`、`CatchAllAdapter`）
- 数据模型：每玩家 `*Data`（VL 字段）+ 每世界 `*Config`（阈值，构造时一次性读）；`executeActions` 按 `vl>N` 分档动作（log/cancel/cmd:kick）
- 延迟容忍：`TickTask.getLag(ms)`（1.0=20TPS），各检查按需除以 lag 或跳过（`lag < 1.5`）

### 3.2 SurvivalFly 包络模型（`moving/player/SurvivalFly.java` 2230 行）

**物理常量**（`moving/magic/Magic.java`）：重力 `GRAVITY_MAX=0.0834` / `GRAVITY_MIN=0.0624` / `GRAVITY_SPAN=0.021`；空气摩擦 `0.98`、水 `0.89`、岩浆 `0.535`；`WALK_SPEED=0.221`；潜行 `0.13/0.221`、格挡 `0.16/0.221`、游泳 `0.115/0.221`、蛛网 `0.105/0.221`；冰面 ×2.5；攀爬上 `0.119`、下 `0.151`。

**合法运动模拟**：
- 下落包络：`vAllowedDistance = lastMove.yDistance × lastFrictionVertical − GRAVITY_MIN`
- 跳跃包络（`LiftOffEnvelope`）：NORMAL（起跳增益 0.42、最大高度 1.35、**最大跳相 6**）、LIMIT_NEAR_GROUND、LIMIT_LIQUID（0.1/0.27/3）、NO_JUMP
- 水平：`hAllowedDistance = walkSpeed × 速度药水 × 冲刺 1.30000002 × 介质修正`

**违规量**：`result = (max(hDistAboveLimit,0) + max(vDistAboveLimit,0)) × 100`；`survivalFlyVL` 累积，每 40 移动次 ×0.95 衰减；`hbuf` 水平缓冲上限 1.0。

**子检测**：
- `maxphase`：`sfJumpPhase > 6` 且不下降 → 违规（空中停留过长）
- `lowjump`：由升转降时 setBackYDistance < 1.15(+跳药水 0.5)
- `waterwalk`：y=0 且 h>0.1 且前后在液体
- `sprintback`：倒退冲刺且 h > walkSpeed
- `step`：yDistance > 0.5/0.6 且 < minJumpGain
- `hacc`：30 tick 平均水平 > 1.34×（NORMAL）→ 违规

### 3.3 bunnyhop 模型（`SurvivalFly.java` :1685-1814）

| 条件 | 源码 |
|---|---|
| 合法峰值窗口 | `1.314×hDistanceBaseRef < h < 2.15×hDistanceBaseRef`（:1767-1768）|
| 峰值相对上一帧 | `1.314×lastMove.h < h < 2.15×lastMove.h`（:1770）|
| 起跳增益 | `yDistance > minJumpGain − GRAVITY_SPAN(0.021)`（:1782）|
| 跳相条件 | `sfJumpPhase==0 && from.onGround` 或 `sfJumpPhase≤1 && touchedGround`（:1796-1799）|
| bunnyfriction（落地后）| `hDistDiff ≥ lastMove.h/160` 或 `≥ (h−hBaseRef)×0.02`（:1698-1699）|
| bunnyslope（落地首 tick）| `hDistDiff ≥ 0.66×(lastMove.h − hBaseRef)`（:1692）|
| **doublebunny 触发**（需避开）| `h−lastH ≥ 0.5×hBaseRef` 且 `lastY∈[−0.0417,0]` 且 `y ≥ 0.4` 且 `touchedGround`（:1725-1731）|
| ediblebunny | `bunnyhopDelay ≤ 6`（吃食物后）|
| headbangbunny | 撞头后，h 在 1.24~1.34× 区间 |

**结论（B）**：上述条件只是 `bunnyHop()` 将一次水平超限归零的必要组合之一；同一移动仍可能进入 `hacc`、`maxphase`、介质、速度药水、方块摩擦和 MorePackets 分支。因此不能据此称某套时序“完全合法”。

### 3.4 Angle 模型（`fight/Angle.java` 全文）

1 秒滑窗（`maxTimeDiff=1000ms`）内攻击对统计：

| 统计量 | 触发条件 | 分数 |
|---|---|---|
| `averageMove`（**平方**距离）| < 0.2（即 < **0.447 格**）| +20×(0.2−avg)/0.2 |
| `averageTime`（攻击间隔）| < 150ms（>6.67 cps）| +30×(150−avg)/150 |
| `averageYaw`（平均转角）| > 50° | +30×yaw/180 |
| `averageSwitching`（换目标）| > 0（且 yawDiff>30°）| +20×avg |

`violation > 50` 且 `lag < 1.5` → `angleVL += violation`；否则 `angleVL ×= 0.98`。actions：`cancel vl>100`。

### 3.5 YawRate 模型（`combined/Combined.java` :73-209）

- `stationary = 32°`：单次转角 <32° 仅累积（sumYaw），累计 ≥32° 后记录单次 dNorm
- `dNorm = |yawDiff| / (1 + elapsed_ms)`（度/ms）
- 桶：`ActionFrequency(3, 333)`（3 桶 × 333ms ≈ 1s）
- 判定：`bucketScore(0)×3 > 380`（**333ms 窗 > 126.7°**）或 `score(1) > 380`（**1s 窗 > 380°**）
- 惩罚（无 VL 软惩罚）：`timeFreeze.applyPenalty(clamp(1.0×(total−380)/380×1000, 250ms, 2000ms))`——**冻结期间所有攻击被取消**
- >999ms 无输入自动重置

**设计含义（B）**：`32°` 是聚合小转角的 stationary 阈值，不是“每步低于 32° 就不记录”；累计 `sumYaw` 达到阈值后仍会入桶。将 380 反推为固定转向用时也忽略了桶边界、经过时间归一化和其他 Angle/Direction 检查，只适合作为测试用例生成依据。

### 3.6 攻击链与惩罚（`fight/FightListener.java`）

**检查顺序**：非法附魔 → SelfHit → WrongTurn(pitch>90°) → Speed(≤15/s) → Critical → NoSwing → 格挡 → Reach+Direction（locationTraceChecks）→ **YawRate → Angle** → lostsprint → attackPenalty

**软惩罚链**：
- Reach/Direction 违规 → `attackPenalty.applyPenalty(500ms)`（期间所有攻击取消，不记 VL）
- YawRate → timeFreeze（250-2000ms）
- Improbable 全局累计（reach/2、yawrate/100、fastclick 0.7× 等喂入，短程 `bucketScore(0)×0.8 > level/20` 或全长 `score > 300`）

**补偿机制**：

1. `lostsprint`（:418-442）：特定近战移动条件下把 `lostSprintCount` 设为 7，用于处理冲刺状态不同步。它改变后续 SurvivalFly 的状态解释，不是独立的速度额度。
2. 受害者位置回溯（:475-509）：Reach/Direction 最多遍历 `loopMaxLatencyTicks` 个历史条目，寻找与延迟一致的位置。通过任一历史位置只说明该次几何检查通过，不能推出移动目标“几乎免疫”，也不会绕过 FightSpeed、Angle、YawRate 或 Improbable。

### 3.7 频率墙（默认配置 `DefaultConfig.java`）

| 检测 | 限制 |
|---|---|
| MorePackets | **22 pps** 移动包（ideal 20）|
| FlyingFrequency | 60 pps（宽松）|
| FightSpeed | **15 次/s** 攻击（短程 7 tick 内 6 次）|
| AttackFrequency | 0.5s/10、1s/15、2s/30… |
| FastClick | 短程 4/s、全程 15/s（物品）|
| FastPlace | 22/s |
| Frequency（挖掘）| 45/s 生存、95/s 创造（短程 5tick/7）|
| FastBreak | FINISHED 间隔 <275ms 记罚分桶，grace 2000 |
| PacketFrequency | 200 pps（仅 1.9 前）|

### 3.8 NoFall / Velocity / GodMode 补充

- **NoFall**：NCP 不信任客户端 fallDistance，自己算伤害（`noFallMaxY` 累积，落地 `dealFallDamage` = fallDist − 3.0）；`noFallAntiCriticals`：mcFallDistance < 0.75 时重置服务器 fall distance。**客户端 NoFall 在 NCP 上无效**
- **Velocity**：击退向量入队（`activationCounter=80` 移动包 / `activationTicks=140` tick 内有效），`getOrUseVerticalVelocity` 匹配则免罚 → **必须"消费"向量，减免不可行**；80 包内不用则队列失效
- **GodMode**：该历史快照在 keepalive 估算年龄为 1100~5000ms 时走 lag 豁免分支；但 `lastKeepAliveTime` 也会被移动和交互适配器更新，并存在 KeepAliveFrequency 与超时行为。静态源码不足以证明人为延迟应答可以稳定进入该窗口。

---

## 4. 风险矩阵（不是安全参数表）

| 领域 | Grim 2.3.74 源码事实 | 历史 NCP 3.16.1 源码事实 | 结论与证据等级 |
|---|---|---|---|
| 常规移动 | 最小预测 offset 进入 Simulation，默认 flag 阈值 0.001 | SurvivalFly 组合水平/竖直超限并累积 VL | 不应提供固定加速倍率；A |
| 0.03 | 仅客户端协议 `<1.18.2`，且必须满足可跳 tick、候选速度和碰撞条件 | 无等价预测器 | 只能作为协议兼容分支测试，不能作为额外位移预算；A |
| NoFall | 无位置 ground 包有强制重同步和 `groundspoof/NoFall` 双层约束 | 服务端维护 `noFallMaxY` 并计算伤害 | 不支持从静态源码推出通用客户端 NoFall；A/B |
| Reach | 默认属性距离 + hitbox margin；1.7/1.8 与可跳 tick 客户端有额外几何容差 | 默认 4.4，并由 `reachMod` 动态向 3.5 收敛 | 表中数字是几何/配置值，不是稳定攻击距离；A/B |
| 战斗时序 | Reach、Hitboxes、MultiInteract、BadPackets、PacketOrder 和基础 Aim 可同时生效 | FightSpeed、Angle、YawRate、Direction、Improbable 和 attackPenalty 可叠加 | 单一 CPS/角速度阈值不足以建立画像；A |
| 击退 | 事务夹层跟踪向量，默认 offset 阈值 0.001 | 速度条目有激活窗口并参与移动包络 | 默认关闭主动缩放；是否兼容需场景测试；A/B |
| Timer/包率 | TimerA 默认 drift 120ms，另有 TimerLimit、NegativeTimer 和包取消 | MorePackets ideal 20、max 22，另有 burst 条件 | 不能把 drift 或 pps 单值解释为可用额度；A/B |
| 载具/流体/活塞 | 会扩大候选不确定性，同时仍经过碰撞和专项检查 | 由介质与 VehicleEnvelope 等分支处理 | 只适合构造回归场景；B/C |

## 5. 工程落地建议

### 5.1 画像与观测架构

```text
AntiCheatCompatibility
├── ProfileSelection
│   ├── MANUAL_GRIM / MANUAL_NCP / CONSERVATIVE
│   └── AUTO 只输出置信度，不自动放宽行为
├── ProtocolContext
│   ├── 实际协商的客户端/服务端协议
│   └── ViaVersion/代理存在性（未知时采用保守路径）
├── ShadowValidator
│   ├── 记录输入、位置、碰撞、包序和服务端修正
│   └── 离线重放后比较预测与实际结果
├── Guardrails
│   ├── 包率、攻击距离、旋转速率和动作冲突硬限制
│   └── 回弹/取消/位置修正后自动降级或停用
└── EvidenceLog
    ├── 服务端修正、断连原因、时间戳与场景标签
    └── 不记录服务器地址、账号令牌或聊天内容
```

不建议用“事务包频率”“负 teleportId”或命令可见性做确定性指纹：这些特征并非 Grim/NCP 独占，也可能被代理和其他插件改变。协议版本必须来自真实协商结果；ViaVersion 是服务端/代理协议转换设施，不能在报告中简写成客户端任意伪装能力。

复刻完整 Grim 仿真器的维护成本很高，且必须与目标服务端的具体提交、配置、PacketEvents 和 ViaVersion 组合一致。更实际的第一步是做只记录不改包的 `ShadowValidator`，用服务端修正和本地重放验证模型误差。

### 5.2 当前客户端实现审计

| 报告概念 | 当前根工程状态 |
|---|---|
| NCP bhop | `SpeedHackHack.NCP_BHOP` 已存在，但只是客户端运动模式；没有证据表明它覆盖 NCP 的完整包络和组合检查 |
| Grim critical | `CriticalsHack.GRIM` 发送极小 Y offset；尚无针对目标 Grim 版本的回归证据 |
| 服务器画像 | 未发现 `ServerFingerprint` / `AntiCheatProfile` 实现 |
| 0.03 协议上下文 | 未发现 `PointThree`、ViaFabricPlus 或协议协商适配层 |
| 合法仿真与重放 | 未发现 `LegalSimulator` / `ShadowValidator` |
| 行为统计 | 已加入共享点击调度、目标滞回、旋转加速度约束和旋转请求租期；AimAssist、KillauraLegit、Killaura、MultiAura 已统一约束相邻旋转增量，`RotationSmoothing` 会将非有限起点/终点/增量参数回退到上一个有限状态并约束 pitch，但仍没有服务端等价的序列验证器，不能据此宣称通过 Aim/Autoclicker 检查 |
| 战斗状态所有权 | 参考 OpenVape/OpenMyau/OpenOpal/LiquidBounce Nextgen/Rise 的职责边界后，目标连续性、旋转请求生命周期和 update→input 攻击意图分别由 `CombatTargetSession`、`CombatRotationController`、`CombatIntentQueue` 负责；Killaura/MultiAura 不再各自保存可过期的 pending attack/fail-swing 字段，AnchorAura/CrystalAura/FightBot/TP-Aura 不再各自管理 combat-priority `RotationQueue` |
| 点击时序边界 | 旧攻击计时器已封闭非有限 CPS/随机偏移、空玩家和 `nanoTime` 回绕；点击模式处理空/短周期、负 CPS 并保证 Efficient 每周期只采样一次 CPS；滚动数组支持任意迭代轮数及大相对索引，下一次点击预测会扫描完整滚动缓冲（当前默认 40 tick），不再只查看迭代次数 |
| 目标筛选边界 | `CombatTargetUtils` 对非有限范围/FOV、瞄准点、包围盒、距离和评分采取 fail-closed，并用实体 ID 作为最终稳定排序键；`TargetTracker` 每次更新对每个不同目标最多校验一次，非有限切换优势按 0 处理，非有限候选评分拒绝切换；这些措施防止异常输入扩大候选集，不构成任何检测规避保证 |
| 状态生命周期 | Killaura、KillauraLegit、MultiAura 和 TriggerBot 在新目标或目标切换时重置相应点击计时；AimAssist/KillauraLegit 在暂停时释放目标与旋转历史；JumpReset 会在离地、停止移动、停止冲刺或超时后取消；Criticals 在模式资格确认前不再停止冲刺 |
| 多目标计划 | MultiAura 候选计划保持输入顺序，在校验前移除空值和重复实体，并将异常负 hurtTime 归一为 0；这只保证客户端计划确定性，不代表服务端会接受同 tick 多目标攻击 |
| 瞄准点规划 | Killaura/MultiAura 的共享采样会去除重复点、跳过范围外射线，并拒绝非有限几何和评分；这是输入封闭与计算稳定性改进，不是 Reach/Hitbox 豁免 |
| 路径战斗状态 | FightBot/Protect 在容器暂停、失去目标和 AI 模式切换时释放路径控制并丢弃旧路径；Protect 不再构造空目标路径或在跟随攻击距离外调用攻击；TP-Aura 在本地位置变更前检查原版攻击冷却 |
| 移动与距离边界 | Speed 保留超过模块上限的既有外部冲量，并避开使用物品/能力飞行；Reach 可在非冲刺或流体中回退原版实体距离，方块距离独立 |

### 5.3 最小验证矩阵

每条结论至少需要下列维度的隔离测试，结果应记录“通过、flag、取消、回弹、断连”，不能只看客户端是否还能移动：

| 维度 | 最小取值 |
|---|---|
| 服务端 | 原版 Paper + 指定反作弊单插件；再测试实际插件组合 |
| 协议 | 原生协议；经 ViaVersion 的旧协议；1.18.2、1.21.2、1.21.11 边界 |
| 网络 | 本机、固定 RTT、抖动、丢包；事务与 keepalive 分开记录 |
| 场景 | 平地、台阶、墙角、流体、气泡柱、活塞、载具、击退、爆炸 |
| 观测 | 客户端包、服务端包、反作弊 verbose、位置修正、最终伤害 |
| 重复 | 预热后至少 1000 tick；跨 tick 边界重复，并报告置信区间 |

## 6. 审计结论

**高置信结论（A）**：

- Grim 的 Simulation 优势值在违规 tick 线性累加，只在合法 tick 衰减。
- 0.03 分支是条件性候选扩展，不是固定移动额度；GroundSpoof/NoFall 仍会处理 ground 包。
- Grim 基础版包含旋转、Hitboxes、BadPackets 与 PacketOrder 检查，不能描述为“无旋转检测”。
- 本地 NCP 快照来自 2018 年，默认阈值不可直接代表现代服务器。
- EdGrim 默认把 Simulation/Knockback 阈值从 0.001 放宽到 0.01，但额外战斗检测显著增多。

**静态源码不支持的旧结论**：

- “持续 0.001 offset 永不回弹”与“offset 下一 tick 自我放大”。
- “任意每 tick ≤0.03 位移合法”或“只改 onGround 即可 NoFall”。
- “Grim 3.03、NCP 3.5 是安全 Reach”。
- “NCP keepalive 1.1-5s 可稳定 GodMode”。
- “随机化点击或旋转即可通过所有统计检查”。

**后续研究优先级**：先建立可复现服务端和被动观测，再验证现有 `NCP_BHOP` / `GRIM` 模式；在没有目标插件版本、配置和测试证据前，不新增基于固定阈值的自动画像或主动协议降级。

---

## 7. EdGrim 分支差异研究（GrimAC 2.0 fork）

> 源码：`source/EdGrim-main.zip`，版本 `26.06.10`，包名 `tech.zkmjnic.edgrim`。
> 压缩包没有 Git 元数据，也没有文本能证明旧报告所称的上游提交 `2b62148`，因此删除该基线断言。README 自称“100+ checks”，并明确声明含大量 AI 生成代码。`experimental-checks` 默认关闭；但 27 个直接 `Aim*.java` 中只有 AimD、AimJ 标记为 experimental，不能概括成“多数新 Aim 检查默认关闭”。

### 7.1 核心机制对比

| 机制 | EdGrim 变化 | 结论 |
|---|---|---|
| **0.03 机制**（`determineCanSkipTick` / `getAdditionalVerticalUncertainty`）| 保留与 Grim 同源的主体结构，并有若干局部条件差异 | 由于缺少可验证共同提交，只能做符号级比较；不能称“逐行无变化” |
| **输入枚举**（`loopVectors` / KnownInput）| 保留输入候选枚举；相对 2026-08-04 的 Grim 2.3.74 缺少部分更新 | 这是快照时间差或 fork 差异，不能直接解释为检测放宽 |
| **Simulation（OffsetHandler）**| 默认 `Simulation.threshold` 为 0.01，上游快照为 0.001；其余优势值参数仍为 0.999 / 0.1 / 1 / 4 | 默认 offset flag 门槛确实放宽 10 倍，但优势值仍在线性累加 |
| **VelocityA** | 默认 `Knockback.threshold` 为 0.01，`setbackvl=0`；上游快照为 0.001 | 默认配置更宽松，不代表固定比例击退缩放不会被其他预测或专项检查发现 |
| **Reach** | 缺少当前 Grim 2.3.74 的 1.21.11 `ATTACK_RANGE` 组件处理 | 对 1.21.11+ 是版本支持缺口；不能把缺口写成稳定距离增益 |
| **Timer** | `drift=120ms` | 与上游默认值相同，仍需结合 TimerLimit 与包取消测试 |
| **不确定性箱** | 保留飞行、硬实体、流体、活塞、粘液和推挤等同源分支 | 数值是 offset 缩减输入，不是独立可用窗口 |

### 7.2 新增检测族与质量风险

**aim/（27 个直接 `Aim*.java`，另有 processor/heuristic/trajectory 支持类）**：

| 检测器 | 原理 | 关键阈值 | 审计关注点 |
|---|---|---|---|
| AimA / AimQ / AimV / AimR | 准星-目标角度锁定统计（common/predictive/generic）| 150 样本中 ≤2°（AimA）/ ≤1.5°（AimQ）≥110 次；AimR 含目标速度外推，100 样本 yaw≤1.5°≥75 次 | 样本窗口、实体插值和低 TPS 下的误报率未给出测试证据 |
| AimB / AimZ | 灵敏度/取整与熵分析 | 灵敏度、pitch 差、熵变化等组合阈值 | 多处使用浮点精确比较，需要真人数据集验证稳定性 |
| AimC / AimE / AimY | yaw/pitch 标准差、分布与排名分析 | `(yawStd<0.25 && pitchStd>2.85)` 等 | 需要区分鼠标 DPI、手柄、触控和辅助功能输入 |
| AimD / AimI / AimZ | 香农熵、kurtosis、spikes、pearson 复合统计 | `shannonYaw>3.1 && shannonPitch<2.0`；kurtosis>15；distinct<20 | AimD 默认 experimental；多重子缓冲的可达性需逐分支测试 |
| AimF / AimO / AimK / AimX | GCD 量化分析 | GCD、modulo、小 delta 计数、像素方差 | 必须用真实灵敏度与不同帧率数据验证，而不是假设非整步长一定异常或正常 |
| AimG / AimM / AimS / AimT | 微旋转平滑、线性、极端轴比和轨迹 | 连续 streak、轴比与变化率 | 检查间共享缓冲和 `mitigateDamage()` 会放大单次误判影响 |
| AimN / AimJ / AimP | 目标角度锁定、最优 yaw 偏差、360° 包装 | `deltaYaw>3.5 && angleDiff≤0.075` 等 | AimJ 默认 experimental；AimP 与上游 AimModulo360 同源 |
| AimH | 重复 look 包 | from==to | 与上游 AimDuplicateLook 功能重叠，需验证豁免条件是否一致 |

**aim/heuristic（AimAA 容器，7 组件）**：精确 GCD 值（yawChange==0.1 计数 +1000 VL）、robotized/constantRotations（`|Δy−首Δy|<0.99` 且 >4°）、interpolation 无穷值、跨窗口模式（PATTERN_LENGTH=3）。**灵敏度 >65 时阈值更严**。

**aim/trajectory（AimW，5 策略）**：角加速度 Z-score（`pitchZ<0.05&&yawZ<0.05&&|mean|∈(200,800)`）、路径线性度（`linearity>0.7 && smoothness<-0.2` 连续 13 次）、yaw/pitch 相关（`corr<0.2 && maxYaw>3`）、friction 骤停（>30→<5）。多分支空实现（AI 生成痕迹，只加 buffer 不 flag）。

**AutoclickerA**：挥臂间隔量化到 tick，收集 100 样本后使用 kurtosis、熵和 jiff-delta 等统计量；flag 后可调用 `mitigateDamage()`。随机化并不自动使这些统计量正常，必须用数据集测量真阳性与误报率。

**PingSpoofA~D**：A 比较 keepalive 与事务 ping；B 比较战斗期、短期和总体事务 ping；C 使用延迟直方图；D 检查攻击时实体事务挂起量。该快照的 **PingSpoofC flag 分支不可达**：先执行 `buffer=Math.min(buffer+addedVL,3)`，随后要求 `buffer>3`。因此 C 只维护状态而不会按该分支报警，报告不能称其“专抓”某种模式。

**其他**：InteractA/B（交互可见性射线，取消）、StuckA（交互包迟到 >avgPing+250ms 取消）、InventoryA~G（背包状态动作检测，InventoryD 为预测型）、ScaffoldA~D（GodBridge/KeepY 拖拽点击 50ms 间隔）、AnalysisA（攻击窗签名 vs 模板库相似度，需管理员录真人样本，无模板自动禁用）、Baritone 检测 = 上游旧检查且默认禁用（作者自注会误报电影镜头）、Cinematic（熵 + GCD<1/128° 计数，多数 aim 检查用 isCinematic2 豁免）。

### 7.3 EdGrim 审计结论

1. **移动层**：默认 Simulation/Knockback flag 阈值确实放宽到 0.01，但优势值仍按违规 tick 线性累加，且 Phase、GroundSpoof、Timer、BadPackets 等检查继续存在。不存在 `99×offset` 稳态或可直接换算的“安全偏移预算”。
2. **战斗层**：27 个直接 Aim 检查、Autoclicker、Interact、Analysis、Scaffold 与 PacketOrder 形成重叠检测面。静态阈值无法推导一套通用旋转或点击序列；应先验证每个检查是否注册、是否 experimental、样本窗是否可达及真实输入误报率。
3. **延迟层**：PingSpoofA/B/D 有可达 flag 路径；PingSpoofC 当前分支不可达。任何结论都要同时记录事务 ping、keepalive ping、实体跟踪事务和代理行为。
4. **惩罚通道**：45 个检查/支持类调用 `mitigateDamage()`；默认开启后将未来 2 秒伤害乘以 0.05。该效果由 Bukkit `DamageMitigationEvent` 实现，属于可验证的服务端后果。
5. **质量风险**：README 主动声明大量 AI 生成代码；结合 PingSpoofC 不可达分支和若干空/只加 buffer 的策略，类数量不能代替测试覆盖率。

---

## 8. 附录：源码索引

### GrimAC 2.0（`common/src/main/java/ac/grim/grimac/`）

| 文件 | 内容 |
|---|---|
| `predictionengine/MovementCheckRunner.java` | 移动包处理总控、offset 结算、回弹后跳禁止 |
| `predictionengine/PointThreeEstimator.java` | 0.03 机制核心（482 行全文）|
| `predictionengine/predictions/PredictionEngine.java` | 穷举预测、输入枚举、不确定性箱（890 行）|
| `predictionengine/predictions/PredictionEngineNormal.java` | 跳跃/爬梯/端 tick 重力 |
| `predictionengine/UncertaintyHandler.java` | reduceOffset、不确定量管理 |
| `checks/impl/prediction/OffsetHandler.java` | Simulation 检测（0.001/0.1/1/4）|
| `checks/impl/groundspoof/NoFall.java` | 无位置 ground 包的碰撞检查与包改写 |
| `checks/impl/timer/Timer.java` | 事务时钟 Timer（drift 120ms）|
| `checks/impl/combat/Reach.java` | 攻击距离 raycast（404 行全文）|
| `checks/impl/velocity/KnockbackHandler.java` | AntiKB 事务夹层（262 行全文）|
| `manager/SetbackTeleportUtil.java` | 回弹执行与接受 |
| `checks/impl/packetorder/PacketOrderProcessor.java` | 包序状态机 |

### NoCheatPlus（`NCPCore/src/main/java/fr/neatmonster/nocheatplus/`）

| 文件 | 内容 |
|---|---|
| `checks/CheckType.java` | 全部检查类型 |
| `checks/moving/player/SurvivalFly.java` | 重力/跳跃包络（2230 行）|
| `checks/moving/magic/Magic.java` | 物理常量 |
| `checks/moving/model/LiftOffEnvelope.java` | 跳跃包络模型 |
| `checks/moving/player/MorePackets.java` | 移动包频率 |
| `checks/moving/player/NoFall.java` | 服务器自算摔伤 |
| `checks/fight/Angle.java` | 站桩/快速攻击统计（193 行全文）|
| `checks/fight/Reach.java` | 4.4 格 + 动态收敛 |
| `checks/fight/Direction.java` | 视线偏差 |
| `checks/fight/FightListener.java` | 攻击链（893 行）|
| `checks/combined/Combined.java` | YawRate + timeFreeze |
| `checks/combined/Improbable.java` | 全局累计器 |
| `utilities/TickTask.java` | lag 容忍 |
| `config/DefaultConfig.java` | 默认阈值总表 |

### EdGrim（`common/src/main/java/tech/zkmjnic/edgrim/`）

| 文件 | 内容 |
|---|---|
| `checks/impl/prediction/OffsetHandler.java` | Simulation 优势值、默认配置读取 |
| `predictionengine/PointThreeEstimator.java` | 0.03 同源分支 |
| `checks/impl/aim/` | 27 个直接 Aim 检查及 heuristic/trajectory 支持代码 |
| `checks/impl/autoclicker/AutoclickerA.java` | 100 样本点击统计 |
| `checks/impl/pingspoof/PingSpoofA.java` | keepalive/transaction ping 对比 |
| `checks/impl/pingspoof/PingSpoofC.java` | 延迟直方图；当前快照存在不可达 flag 条件 |
| `player/PlayerData.java` | `mitigateDamage()` 的 2 秒状态和默认开关 |
| `bukkit/src/main/java/tech/zkmjnic/edgrim/platform/bukkit/events/DamageMitigationEvent.java` | 伤害乘以 0.05 的 Bukkit 实现 |

### 复核规则

- 优先引用符号路径和配置键；行号只对当前 ZIP 有效，源码更新后必须重新生成。
- 对所有“安全、免疫、完全、任意、必然”表述要求运行时证据；只有静态控制流时使用“可能、默认、当前快照”。
- 新快照进入 `source/` 后先记录版本、归档 SHA-256、上游提交（若可验证）和默认配置差异，再更新结论。
- 真实服务器测试仅限本地或明确授权环境，并保存服务端插件版本与配置副本，否则结果不可复现。
