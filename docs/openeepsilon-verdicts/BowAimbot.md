状态：已重构

# BowAimbot 对照裁决（OpenEpsilon `AimBot.kt` 的弓分支 → 本工程 1.20.1 `BowAimbotHack`）

- 目标：`src/main/java/net/wurstclient/hacks/BowAimbotHack.java`（261 → 266 行）
- 参考：`_oe_ref/src/main/kotlin/studio/coni/epsilon/module/combat/AimBot.kt`（142 行，1.12.2 / Kotlin / MCP）
- 原生真源：本仓库自带的 1.20.2 反编译源码（下称「1.20.2 源码树」，路径
  `neoforge/versions/1.20.2/build/neoForm/neoFormJoined1.20.2-20231019.002635/steps/unzipSources/unpacked/net/minecraft/...`）
  与 1.20.1 的 `%USERPROFILE%\.gradle\caches\forge_gradle\mcp_repo\net\minecraft\joined\1.20.1-20230612.114412\joined-1.20.1-20230612.114412-srg.jar`
  （用 `javap` 核对过常量：`AbstractArrow` 的 `0.99f`/`0.05f`、构造器的 `0.1`、`BowItem` 的 `3.0f`、`CrossbowItem` 的 `3.15f`/`1.6f`）。
- 结论：**参考的弓弹道公式与本工程原来用的公式是同一个**（`g = 0.006` 的无阻力抛物线，
  `AimBot.kt:105-107` ↔ 旧 `BowAimbotHack.java:160-168`），两边都漏掉了 1.20.1 箭的真实积分。
  其中真正致命的是「**箭会继承射手当 tick 的速度**」这一条（`Projectile.java:142-143`）：
  旧实现完全没有这一项，走路射 20 格会横向偏 **1.45 格**（目标宽 0.6 格，等于必空）。
  本次把弹道解算抽成纯逻辑类 `BowAimbotTrajectory`（可单测），hack 只负责组装输入与下发角度；
  设置项、目标选择、ESP/渲染一律未动。

## 对照证据

### 表 1：参考（`_oe_ref`）↔ 本工程

| 关注点 | 参考（_oe_ref 相对路径:行） | 本工程（相对路径:行） | 判定 |
| --- | --- | --- | --- |
| 弓分支的进入条件 | `AimBot.kt:72`：`itemInUseCount > 0` 且主/副手是弓 | `BowAimbotHack.java:106-121`：主手是 `BowItem`/`CrossbowItem`，弓还要求正在拉弓 | 等价（本工程多了弩分支） |
| 拉弓力度（力度→初速倍率） | `AimBot.kt:100-102` 算完 `(v²+2v)/3` 后，**`:103` 直接 `rangeAimVelocity = 1f` 写死** | `BowAimbotHack.java:140-144`：`(72000-getUseItemRemainingTicks())/20` 再 `(v²+2v)/3` 并 clamp 到 1 | **本工程本来就正确**（与 `BowItem.java:97-99` 的 `getPowerForTime` 逐字一致）；参考那条写死是缺陷，不搬 |
| 弓的箭初速常量 | `AimBot.kt:105-107`：`g = 0.006` 配力度 1，隐含初速 3.0 | `BowAimbotHack.java:146-149`：`velocity * 3`（= `BowItem.java:47` 的 `f * 3.0F`） | 弓一致 |
| 弩的箭初速 | 参考没有弩分支（1.12 的 `ItemBow` 之外无处理） | `BowAimbotHack.java:149`：`CrossbowItem` 用 3.15 | **本次新增**（原来弩走 `velocity=1`→3.0，偏慢 5%）；依据 `CrossbowItem.java:43,64,81` |
| 弹道模型 | `AimBot.kt:105-107`：无阻力抛物线 `g=0.006` | 旧 `BowAimbotHack.java:160-168`：同一个公式 | **两边同错**；1.20.1 真实模型见 `AbstractArrow.java:208-257` |
| 箭的运动积分顺序 | 无（参考假设无阻力） | 新 `BowAimbotTrajectory.heightAtDistance`（`BowAimbotTrajectory.java:162-196`） | 逐 tick「先按当前速度位移 → `*=0.99` → `-=0.05`」，与 `AbstractArrow.java:227-229,240-257` 一致 |
| 箭的生成点 | `AimBot.kt:114` 里手写 `- 0.15`（1.12 的近似） | 旧实现按眼睛高度；新实现 `BowAimbotHack.java:163-166` 用「眼睛 − 0.1」 | `AbstractArrow.java:78-79` 是 `getEyeY() - 0.1F`；参考那个 0.15 是另一个近似，不搬 |
| **射手自身速度补偿** | `AimBot.kt:113-115`：把射手自己的一 tick 位移从相对坐标里减掉一次（`predict` 打开时） | 旧实现：**完全没有**；新实现 `BowAimbotHack.java:162-168` + `BowAimbotTrajectory.solve` | **本次修的主缺陷**（见「实际改动」第 2 条） |
| 目标提前量 | `AimBot.kt:113-115`：`(pos - prevPos) * 1.35`，开关是布尔 `Predict`（`AimBot.kt:38`） | `BowAimbotHack.java:145-153,154-157`：`d = 眼睛到目标中心距离 * predictMovement`（百分比滑块，默认 20%） | **语义不同，保留本工程语义**（改设置语义=破坏用户配置，任务禁止） |
| 瞄准点 | `AimBot.kt:114`：`minY + eyeHeight - 0.15`（上胸/头） | `BowAimbotHack.java:155-156`：`getY() + bbHeight * 0.5`（碰撞箱中心，与 ESP 框、`faceVectorClient` 回退同一处） | 不搬（换瞄准点是行为改变，不是修 bug） |
| 俯仰角解的选择 | `AimBot.kt:107`：`atan((v² - sqrt(tmp))/(g·D))` 只有一个根（`tmp<0` 时 `sqrt` 出 NaN） | 新 `BowAimbotTrajectory.solve`（`BowAimbotTrajectory.java:102-155`）：从 +89.9° 往 −89.9° 扫，取**第一个**穿越（最平直解），再二分细化 | 旧实现与参考一样只取平直解；新实现保证取的是平直解而不是高抛解（有测试） |
| 无解/超射程时的行为 | `AimBot.kt:106-107`：`tmp < 0` → `sqrt` 得 NaN → 角度 NaN（1.12 里会把 NaN 直接塞进 `rotationPitch`） | 旧实现：`Float.isNaN(neededPitch)` → `faceVectorClient` 直接对着目标看（旧 `:171-175`）；新实现：`solve` 返回 null 才走这条回退，否则给「飞得最高」的尽力解（`BowAimbotHack.java:170-176`） | 本工程的回退更稳；超射程时新的尽力解比直接对着目标看更接近目标 |
| 转向通道 | `AimBot.kt:80-89`：`Side.Client`（默认 `AimBot.kt:25`）**直接改 `mc.player.rotationYaw/rotationPitch`**；`Side.Server` 才走包管理器的静默转向 | `BowAimbotHack.java:178-180`：`MC.player.setYRot(limitAngleChange(...))` + `setXRot(...)`，即客户端真实转向 | **与参考默认值一致，保持**；不走 `RotationQueue`（理由见「故意不搬」） |
| 随机散布 | 参考无处理 | 无处理（`Projectile.java:120-128` 的 `±0.0172275 rad` 是每发随机的，无法修正） | 如实记录为残余误差（见「建议（未做）」） |
| 目标选择 | `AimBot.kt:44-63`：自己遍历 `loadedEntityList`，按 `Priority.Distance/Health` 排序取最后一个，好友过滤用 `FriendManager` | `BowAimbotHack.java:130-136,183-189`：`EntityUtils.IS_ATTACKABLE` + `EntityFilterList.genericCombat()` + 自己的 `Priority` 枚举（DISTANCE/ANGLE/ANGLE_DIST/HEALTH） | 不搬（本工程有过滤列表设置，换成参考的选人会改掉用户可见行为） |
| 射程门限 | `AimBot.kt:37,58`：`bowRange` 默认 30 格，超出直接跳过 | `BowAimbotHack.java:58-59,183-189`：没有距离过滤（`EntityFilterList.java:61-90` 的通用过滤器里也没有距离项） | 不搬（会凭空缩小现有生效范围）；但因为**没有**门限，超射程分支不是死代码 |
| 设置项 | `Side`/`Priority`/`Players`/`Animals`/`Mobs`/`Page`/`AimBot`/`Range`/`Factor`/`BowAimBot`/`BowRange`/`Predict`（`AimBot.kt:25-38`） | `Priority`（`BowAimbotHack.java:45-51`）/`Predict movement`（`:53-56`）/`ESP color`（`:63-64`）/`entityFilters`（`:58-61`） | 不增不减、不改名、不改默认值 |

### 表 2：1.20.2 源码树（原生真源）核对

| 事实 | 出处（1.20.2 源码树行） | 本次用在哪 |
| --- | --- | --- |
| 箭逐 tick：速度取自 tick 开头 → 位移用**未衰减**的速度 → `v *= 0.99` → `v.y -= 0.05` → `setPos` | `.../world/entity/projectile/AbstractArrow.java:208-211,227-229,240-241,251-257` | `BowAimbotTrajectory.heightAtDistance`（`BowAimbotTrajectory.java:162-196`） |
| 箭生成于 `getX(), getEyeY() - 0.1F, getZ()` | `.../projectile/AbstractArrow.java:78-79` | `BowAimbotHack.java:163-166` 的 `+ 0.1` |
| `shootFromRotation` 的方向式 `(-sin(yaw)cos(pitch), -sin(pitch), cos(yaw)cos(pitch))`，**再叠加射手的 `getDeltaMovement()`（竖直分量只在射手离地时加）** | `.../projectile/Projectile.java:137-144`（方向 `:138-140`、继承速度 `:142-143`） | `BowAimbotTrajectory` 的解算前提与测试的独立模拟 |
| `shoot` 里有一项每发随机的 `triangle(0, 0.0172275 × inaccuracy)`（约 ±1°） | `.../projectile/Projectile.java:120-128` | 如实记录为不可修正的残余误差 |
| 弓：`f = getPowerForTime(i)`，`shootFromRotation(..., f * 3.0F, 1.0F)`；`getPowerForTime = (f² + 2f)/3`，`f = (72000 - useDuration)/20` | `.../world/item/BowItem.java:40,47,97-99` | `BowAimbotHack.java:140-149` 的 `velocity` 与 `velocity * 3` |
| 弩：`ARROW_POWER = 3.15F`；`getShootingPower` 对烟花火箭返回 1.6F；`performShooting(..., getShootingPower(stack), 1.0F)` | `.../world/item/CrossbowItem.java:43,64,81` | `BowAimbotHack.java:149` 的 3.15（火箭见「建议（未做）」） |
| 箭从玩家的**视角朝向**射出，且客户端也会生成一支自己的箭（释放逻辑两端都跑） | `.../world/entity/LivingEntity.java:2966-2977,3094-3112,3126-3130`（`completeUsingItem` 只挡「客户端且未在使用物品」） | 支撑「转向必须改客户端真实朝向」的判断（表 1 转向行） |

## 实际改动

### 1. 新增纯逻辑类 `src/main/java/net/wurstclient/util/BowAimbotTrajectory.java`（280 行，无任何 `net.minecraft.` import）

- `solve(speed, dx, dy, dz, shooterVx, shooterVy, shooterVz, shooterOnGround)`
  （`BowAimbotTrajectory.java:102-155`）：按「箭的水平速度方向必须指向目标」这一约束反解。
  水平阻力是同一个 0.99 等比衰减，所以水平轨迹必为直线：设水平初速大小为 `h`、方向为单位向量
  `u`，则 `h·u = speed·cos(pitch)·aim + v`，对瞄准向量取模长得二次方程
  `h² - 2h(u·v) + |v|² - (speed·cos(pitch))² = 0`，取正根（`horizontalSpeed`，`:264-283`）。
  于是每个候选俯仰角唯一确定偏航角（`yawAt`，`:238-256`）与竖直初速，再用 `heightAtDistance`
  算「到达目标水平距离时的高度」，误差函数 `errorAt`（`:221-236`）。
- 扫角策略：从 +89.9° 向 −89.9° 以 0.25° 步进，取第一个「低于目标 → 不低于目标」的穿越
  （这就是最平直解，正是旧公式与参考所取的那一支），再二分 60 次细化（`refine`，`:199-219`）。
  一次都没穿越（超出射程）时返回扫描中「到达高度最高」的那个角度；只有几何退化
  （水平距离 < 1e-6 或初速非正）才返回 `null`。
- 复用了本工程既有的纯类写法：`enum` + 静态方法（对照 `HoleFillPolicy.java:24-26`、`CityBlockPlanner.java:33-35`），
  常量集中定义（`DRAG = 0.99`、`GRAVITY = 0.05`、`MAX_TICKS = 400`…）。

### 2. `BowAimbotHack.onUpdate` 的瞄准段改为调这个解算器

改动位置：`BowAimbotHack.java:146-180`（原 `:144-175`，同时新增 `Vec3`/`BowAimbotTrajectory` 两个 import）。

- 新增弩的初速（`:146-149`）；弓仍用原有的 `velocity * 3`。
- 瞄准点仍按老的 `predictMovement` 语义算（`:151-157`），但**不再在相对坐标里预扣眼睛高度**，
  而是把「箭的真实生成点 = 眼睛 − 0.1」与「射手当前速度」一起交给解算器（`:159-168`）。
- 下发角度仍是 `limitAngleChange(MC.player.getYRot(), yaw)` + `setXRot(pitch)`（`:178-180`）；
  `solve` 返回 null（目标几乎在正上/正下方）时才走原来的 `faceVectorClient` 回退（`:170-176`）。
- 未动：`Priority`、`Predict movement`、`color`、`entityFilters` 四个设置的名称/默认值/说明；
  `filterEntities`、`onRender`、`onGuiRender`、`onEnable/onDisable` 一行未改；没有增删设置。

### 3. 具体输入 → 旧行为 vs 新行为（数字都来自本次验证）

验证方式：纯类用一段独立的 Java 验证程序跑通（见「测试」），另外用一份独立的 Python 实现
（同一套 `Projectile`/`AbstractArrow` 规则）做交叉核对，两者结果一致。所有「落点误差」都是
「箭的水平轨迹与目标水平距离相交的那一点」到目标点的距离。

| 输入（射手用满蓄力弓，即 `speed = 3.0`；目标静止） | 旧：偏航/俯仰 | 旧落点误差 | 新：偏航/俯仰 | 新落点误差 |
| --- | --- | --- | --- | --- |
| 射手静止，目标 20 格、同高度 | yaw −90.000°，pitch −3.4461° | 0.2073 格（**偏高**） | yaw −90.000°，pitch −2.8505° | 1.2e-15 格 |
| 射手静止，目标 30 格、同高度 | yaw −90.000°，pitch −5.1849° | 0.2778 格（偏高） | yaw −90.000°，pitch −4.6501° | 1.9e-15 格 |
| 射手**侧走**（vz = 0.2158 格/tick），目标 20 格、同高度 | yaw −90.000°，pitch −3.4461° | **1.4537 格**（落点 (19.95, +0.21, **+1.44**)） | yaw −94.1302°，pitch −2.8585° | 1.3e-15 格 |
| 射手**疾跑侧向**（vz = 0.2806），目标 20 格 | yaw −90.000°，pitch −3.4461° | **1.8798 格** | yaw −95.3736°，pitch −2.8641° | 3.7e-15 格 |

也就是说：静立时旧实现因为漏了阻力而固定偏高 0.1–0.28 格（这个偏差本身不足以解释「打不中」，
因为目标高 1.8 格）；**只要射手在移动，旧实现就必空**——走路 20 格的横向偏差 1.44 格已经是
目标宽度（0.6 格）的 2.4 倍。新实现在这四组输入下都能落在目标中心 1e-14 格以内。

## 故意不搬

| 参考里的东西 | 为什么不搬 |
| --- | --- |
| `rangeAimVelocity = 1f`（`AimBot.kt:103`） | 把拉弓力度写死成满蓄力。本工程原来的力度计算与 `BowItem.getPowerForTime` 逐字一致（`BowAimbotHack.java:140-144`），是更正确的实现；照搬会让半蓄力射击全部偏高。 |
| `Predict` 布尔开关 + `*1.35`（`AimBot.kt:38,113-115`） | 本工程对应的是 `Predict movement` 百分比滑块（默认 20%，`BowAimbotHack.java:53-56`），语义是「提前量 = 距离 × 比例」。改语义会动到用户已有配置（任务禁止），而 1.35 tick 也只是 1.12 的经验常数。 |
| 瞄准点 `minY + eyeHeight - 0.15`（`AimBot.kt:114`） | 本工程一直瞄碰撞箱中心（`getY() + bbHeight*0.5`，与 ESP 框和回退分支同一处）。改成瞄上胸是行为改变，不是修 bug。 |
| `bowRange`（30 格，`AimBot.kt:37,58`） | 新增射程门限 = 凭空缩小现有生效范围（本工程的通用实体过滤器里本来就没有距离项，见 `EntityFilterList.java:61-90`）。超射程时本次给的是「飞得最高」的尽力解，比直接不打更接近原行为。 |
| 参考自己写的选人循环（`AimBot.kt:44-63`） | 本工程用 `EntityFilterList.genericCombat()` + `EntityUtils.IS_ATTACKABLE` + 四个 `Priority` 枚举，用户可以在 GUI 里配置过滤；换成参考的 `FriendManager` + 距离/血量排序会删掉这些设置。 |
| `Side.Server` 的静默转向（`AimBot.kt:85-89`） | 弓的射出方向由**玩家朝向**决定，而客户端自己也会按客户端朝向生成一支箭（`LivingEntity.java:3094-3112,3126-3130` 说明释放逻辑两端都跑）。只改包里的朝向会让本地看到的那支箭与服务端那支分叉；参考的默认值 `Side.Client`（`AimBot.kt:25,81-83`）与本工程一致，所以保持 `setYRot/setXRot`。本工程的 `RotationQueue`/`CombatRotationController` 静默转向是给近战/放置用的（`Rotation.java:31` 那套），本次不引入。 |
| `Priority.Health` 用 `health + absorptionAmount`（`AimBot.kt:121`） | 本工程的 `Priority` 枚举里本来就有 HEALTH 分支，用的是本工程现有的实体血量读取方式；为对照去改它是纯装饰。 |

## 建议（未做）

- **每发 ±1° 的随机散布无法修正**：`Projectile.java:120-128` 给每个轴加
  `triangle(0, 0.0172275 × inaccuracy)`（弓/弩的 inaccuracy 都是 1.0），30 格处折合
  ±0.52 格。也就是说即使解算精确到 1e-15，远距离仍有约半格的随机误差——这是原版机制，
  任何 aimbot 都改不了，只能如实记录。
- **弩装烟花火箭时模型不适用**：`CrossbowItem.getShootingPower` 对火箭返回 1.6
  （`CrossbowItem.java:81`），而 `FireworkRocketEntity` 是「无重力、每 tick 反向加速度 0.04、
  水平按比例衰减」的另一套物理（`.../projectile/FireworkRocketEntity.java:136`）。本 hack 目前
  对所有已装填的弩都用 3.15 算，装火箭时角度会偏。要修需要一个「检测到火箭就不按箭弹道瞄」
  的分支（`CrossbowItem.containsChargedProjectile` 在 1.20.1 存在），但这会改变装火箭时的现有
  行为（从「瞄得不准」变成「不瞄」），且需要实机确认，故只记录不动手。
- **没加「超出射程就不要开火」的门限**：现在超射程会给一个尽力而为的角度（见「实际改动」），
  是否要提示/拒绝开火属于产品决策，且需要新设置（任务不允许增设置）。
- **没有换用共享选人设施**：`CombatTargetUtils`/`TargetTracker`/`CombatTargetSession` 是并行
  agent 正在改的共享区（`CombatTargetUtils.java:32-62`），而 BowAimbot 现在这套选人是用户可配的，
  换过去会改变行为，属于另一件事。
- **弹道解算没有被其它 hack 复用**：`ProjectileThreatPolicy.java:40-42` 判断来袭弹道用的是
  「位置 + 速度 × N」的直线外推（不含阻力/重力），与本解算器不是一回事，本次没有去动共享文件。

## 测试

`src/test/java/net/wurstclient/util/BowAimbotTrajectoryTest.java`（319 行，12 个用例）：

1. `anchorsTheIntegrationToTheGameRule`：把积分结果钉在手工可推的锚点上——初速 3、水平射时
   第 1 tick 位移 (3, 0)；第 2 tick 累计 (5.97, −0.05)（`(0+5)·1.99 − 5·2 = −0.05`，同时证明了
   「先乘 0.99 再减 0.05」的顺序）；5.0 落在第 2 tick 内，按水平方向线性插值得
   `−0.05 × 2/2.97`。
2. `hitsEveryStationaryTarget`：224 组（初速 3.0/3.15/2.4/1.8/0.9 × 距离 1–60 × 高度 −20…+20），
   全部要求命中误差 < 1e-6。
3. `hitsWhileTheShooterIsMoving`：32 组（侧走/疾跑/反向/斜向的 `vz`，以及离地时的 `vy`）。
4. `keepsTheYawOnTheTargetWhenStandingStill`：射手静止时偏航角必须正好等于 `atan2(dz,dx) − 90`。
5. `addsTheMovementLeadWhileTheShooterIsMoving`：侧走 0.2158 时偏航角必须提前
   `atan(0.2158/3) ≈ 4.11°`（容差 0.05°），并且仍然命中。
6. `beatsTheOldDragFreeFormula`：静立 30 格，旧公式（测试里独立重写了一遍）误差 > 0.2 格，
   新解算命中。
7. `fixesTheMissWhileWalking`：走路 20 格，旧实现（正对目标 + 旧公式）误差 > 1 格，新解算命中。
8. `prefersTheFlatTrajectory`：20 格同高度的解必须在 (−10°, 0°) 之间（即平直解，不是高抛解）。
9. `fallsBackToTheHighestArrivalWhenOutOfRange`：初速 1.2 打 40 格（够不到）时，返回角度的到达
   高度必须与 0.1° 网格扫出的最高到达高度相差 < 1e-3，且确实不算命中。
10. `returnsNullOnlyWhenTheGeometryIsDegenerate`：水平距离为 0、初速为 0 → `null`。
11. `survivesFloatRoundedRotations`：角度转成 `float` 下发后，误差仍 < 1e-5 格。
12. `ignoresTheShootersFallSpeedWhileOnTheGround`：同样输入下 `shooterOnGround` 为 true/false 时
    解不同（离地时俯仰角要抬高 ~20° 抵消继承的下落速度），且两种情形各自都命中。

测试里**故意不复用**被测类的模拟，而是按 1.20.2 源码另写了一份
`Projectile.shootFromRotation` 方向 + 速度继承、`AbstractArrow.tick` 的逐 tick 推进，
再求「箭的水平距离跨越目标距离」时的三维距离。本次还把同样的断言搬进一个临时的
`java` 单文件程序跑过一遍（`javac`/`java` 直接编译纯类，不涉及 gradle）：
**12 组断言全部通过**，224 + 32 组网格输入里没有一例命中误差超过 1e-6 的门槛
（作为对照，同一批输入在另一份独立的 Python 实现里最大命中误差是 1.8e-13；
用 1500 组随机输入（含随机仰角、随机射手速度）跑该 Python 实现时也是 0 例失败）。

## 未验证项（如实说明）

- 按任务约束**没有运行 gradle/maven/git**，所以 `BowAimbotHack.java` 本身没有经过编译；
  纯类 `BowAimbotTrajectory` 与测试里引用的每个 API 都逐个打开文件核对过（见上表）。
- 表「实际改动」第 3 条里的数字来自「精确的 1.20.1 箭积分 + 本工程的瞄准点语义」的离线复算，
  没有实机射击验证。
- 未覆盖：目标在高速移动时 `predictMovement` 是否够用（那是预测问题，不是弹道问题）；
  水中/岩浆中的箭（`AbstractArrow.java:242-249` 的水中阻力 `getWaterInertia`）没有建模，
  本 hack 也没有对应的使用场景。
