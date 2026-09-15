# 第二轮重构：参考 OpenOpal（ZSZ7/OpenOpal）

状态：进行中（第 1 项已落地并测过，其余为待办清单）

参考项目：[ZSZ7/OpenOpal](https://github.com/ZSZ7/OpenOpal)（Fabric 客户端，包名 `wtf.opal`，
`src/client/java` 下 506 个 `.java` + 68 个 mixin；模块采用「主模块 + 每个模式一个实现类」的结构，
事件系统是注解驱动带优先级的 `EventRegistry`，旋转子系统是 `RotationHelper` + `IRotationModel`
（Instant / Linear / Organic / Sideways / HeyPixel））。

本轮不是照搬移植（OpenOpal 面向 Hypixel/GrimAC 专用绕过，我们是对 1.20.1 Forge 的通用客户端），
而是按「它有什么、我们缺什么、缺的那个能不能在我们这套架构里以纯逻辑方式补齐」来挑。

## 差异盘点

| 能力 | OpenOpal | 我们（现状） | 本轮结论 |
| --- | --- | --- | --- |
| 朝向吸附到鼠标灵敏度栅格 | `RotationUtility.patchConstantRotation()`（栅格步长 `(s*0.6+0.2)^3*8*0.15`） | **完全没有**（全仓库只有 `ZoomOtf` 读过灵敏度设置） | ✅ 已补齐（见下） |
| 旋转模型 | `IRotationModel#tick(from,to,timeDelta)`，5 种模型 | `util/RotationSmoothing`（INSTANT/LINEAR/EASE_IN_OUT/FACTOR）+ `CombatRotationController` | ✅ 已新增 PROPORTIONAL，其余模式行为不变（见第 2 项） |
| 事件优先级 | `@Subscribe(priority=…)` + `EventRegistry` 按优先级排序 | `EVENTS` 注册表，**无优先级**（按注册顺序） | ⏳ 影响面大，单独立项 |
| 事件粒度 | 60+ 事件（Pre/PostMovementPacket、Jump、Step、BlockPlaced、AttackDelay、ItemUse、VisualSwing…） | 事件数明显更少 | ⏳ 按需补，不做全量 |
| 模块结构 | 每个模式一个类（`criticals/impl/*`、`velocity/impl/*`、`flight/impl/*`…） | 枚举模式 + `switch` | ⏳ 架构性改动，最后做 |
| 服务端状态预测 | `LocalDataWatch`(243)、`TransactionStreamValidator`(45)、`packet/blockage/*` | 无 | ❌ 不采纳：Hypixel 专用 |
| 反作弊针对性绕过 | HeyPixel/Grim 专用（`HeypixelCriticals`(143)、`VelocityModule`(94) 等） | 通用实现 | ❌ 不采纳：定位不符 |

### 移植时不能照抄的一处（有据可查）

OpenOpal `event/subscriber/Subscribe.java` 的注释写的是 *"higher priorities are called before
lower priorities"*，但 `event/registry/EventRegistry.java` 的 `sortSubscribers` 用的是
`Comparator.comparingInt(ListenerMethod::getPriority)`（**升序**）——两者矛盾，实际执行顺序与注释
相反。我们将来做事件优先级时按注释的语义实现，不复制这个顺序。

## 第 1 项（已完成）：`util/RotationGcdPolicy`

**为什么**：原版的每一度旋转都只能来自鼠标像素位移 —— `MouseHandler` 用
`multiplier = (sensitivity * 0.6 + 0.2)^3 * 8`，视角变化量 = `像素数 * multiplier * 0.15`。
所以真人发出去的朝向一定落在「以当前朝向为锚点、步长 `multiplier * 0.15` 的整数倍」上。
客户端直接写任意小数角度时没有这个性质，服务端可以据此分辨。

**旧实现 vs 新实现**：

| | 旧 | 新 |
| --- | --- | --- |
| 能力 | 不存在（全仓库无任何朝向栅格化代码） | `RotationGcdPolicy.gridStep(灵敏度)` / `patch(value, previous, gridStep)` / `patch(Rotation, Rotation, gridStep)` |
| 测试 | — | `RotationGcdPolicyTest`：8 个用例（与 `(s*0.6+0.2)^3*8*0.15` 逐点比对、灵敏度越界/NaN、锚点非 0、已落栅格不变、步长为 0/NaN 时恒等、NaN/Inf 输入、双轴 + null、吸附误差 ≤ 半个像素） |
| 副作用 | — | 无（纯函数，本类不引用任何 Minecraft 类型；目前**尚未接到发送路径**，见第 2 项） |

**验证**：`gradlew compileJava compileTestJava test --tests net.wurstclient.util.RotationGcdPolicyTest`。

## 第 2 项（已完成）：`RotationSmoothing.PROPORTIONAL`

**为什么**：`smooth()` 原本是**逐轴**限速 —— 偏航拿 `maxChange`、俯仰只拿 `maxChange * 0.7`
（`smooth()` 的第 105-108 行）。目标偏航 90°、俯仰 30°、每 tick 30° 时，一 tick 走 `(30, 21)`：
偏航先到位、俯仰还在慢慢补，轨迹是一条折线；OpenOpal 的 `LinearRotationModel` 是把这一步的
长度当总步长、按 `|Δyaw| : |Δpitch|` 分配（`maxYaw = speed * |Δyaw| / |Δ|`），两轴同时到位。

**旧实现 vs 新实现**：

| | 旧 | 新 |
| --- | --- | --- |
| 逐轴限速 | `smooth()` 里直接调两次 `mode.apply(...)`，俯仰乘 0.7 | 抽成 `mode.applyVector(current, target, maxChange)`，**默认实现与旧代码逐位相同**（仍是两次 `apply`，俯仰仍是 0.7 倍）；`smooth()` 改调 `applyVector` |
| 两轴同步 | 无此能力 | 新枚举常量 `PROPORTIONAL("Proportional")`，覆盖 `applyVector`：`distance = |Δ|`，`distance <= maxChange` 时直接返回目标，否则按 `maxChange / distance` 缩放两轴 |
| 手感影响 | — | **零**：默认值仍是 `EASE_IN_OUT`，且旧模式走默认 `applyVector`，数值不变（有回归测试） |
| 接入方式 | — | `Killaura`/`AimAssist`/`MultiAura` 的 `EnumSetting<RotationSmoothing>` 用的是 `values()`，新常量自动出现在设置里 |
| 测试 | — | `RotationSmoothingProportionalTest`：6 个用例（旧模式逐位回归、步长正好等于 maxChange、两轴按 90:30 分配且与 Linear 的 30:21 对照、一步到位、yaw 走短路径、俯仰夹紧、多 tick 收敛不超步长） |

**已知限制**（如实记录）：`smoothWithAcceleration()` 会在 `smooth()` 之后再用
`approach(previous, desired, acceleration * 0.7)` 限一次俯仰的**变化率**，
所以加速阶段两轴仍不同步；等加速度饱和后才恢复同步。要不要把这个 0.7 也做成可覆盖，留到下一轮定。

## 待办（按建议顺序）

1. **把栅格接到发送路径**：`RotationFaker.setRotationPacket()` / 真正发包前，用上一次发出去的朝向
   作为锚点 patch 一次；配一个全局开关（默认关，避免改变现有手感）。这是本次唯一会改变服务器可见
   行为的改动，需要单独提交 + 逐条对照旧行为。
2. ~~旋转模型对齐~~ **已完成（见第 2 项）**。剩下的是把栅格与模型接到真正发包的地方（第 1 项）。
3. **事件优先级**：给 `EVENTS` 加优先级排序（按 `Subscribe` 注释语义，即高优先级先跑），
   给现有监听器保留默认 0。改动面约 200 处引用，需要一次性提交。
4. **按需补事件**：优先 `PostMovementPacketEvent`、`JumpEvent`、`AttackDelayEvent`、
   `ItemUseEvent`、`VisualSwingEvent`（这几个能把「视觉挥手/真实挥手」「跳跃包/跳跃逻辑」分开，
   是多个常用 hack 目前只能靠 mixin 硬插的地方）。
5. **模块模式类化**：`Criticals`、`Velocity`、`Flight`、`Speed`、`NoSlow` 这几组模式多的模块，
   每个模式拆成一个类（对应 OpenOpal 的 `impl` 目录）。
