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
| 朝向吸附到鼠标灵敏度栅格 | `RotationUtility.patchConstantRotation()`（栅格步长 `(s*0.6+0.2)^3*8*0.15`） | 原本**完全没有**（全仓库只有 `ZoomOtf` 读过灵敏度设置） | ✅ 已补齐并接到发包路径（第 1、3 项） |
| 旋转模型 | `IRotationModel#tick(from,to,timeDelta)`，5 种模型 | `util/RotationSmoothing`（INSTANT/LINEAR/EASE_IN_OUT/FACTOR）+ `CombatRotationController` | ✅ 已新增 PROPORTIONAL，其余模式行为不变（见第 2 项） |
| 事件优先级 | `@Subscribe(priority=…)` + `EventRegistry` 按优先级排序 | `EVENTS` 注册表，**无优先级**（按注册顺序） | ⏳ 影响面大，单独立项 |
| 事件粒度 | 60+ 事件（Pre/PostMovementPacket、Jump、Step、BlockPlaced、AttackDelay、ItemUse、VisualSwing…） | 事件数明显更少 | ⏳ 按需补，不做全量 |
| 模块结构 | 每个模式一个类（`criticals/impl/*`、`velocity/impl/*`、`flight/impl/*`…） | 枚举模式 + `switch` | ⏳ 架构性改动，最后做 |
| 服务端状态预测 | `LocalDataWatch`(243)、`TransactionStreamValidator`(45)、`packet/blockage/*` | `RotationFaker` 记录服务器上一次看到的朝向；`BlinkHack`/`FakeLagHack`/`PacketCancellerHack` 已经能扣包/延迟发包；`ConnectionPacketOutputListener`/`PacketInputListener` 提供包级钩子 | ❌ 不整类移植：实现绑定 Hypixel 事务包语义、能力我们已有等价物（见下文专节） |
| 反作弊针对性绕过 | HeyPixel/Grim 专用（`HeypixelCriticals`(143)、`VelocityModule`(94) 等） | `CriticalsHack`/`NoVelocityHack`/`ReachHack` 等通用实现 | ❌ 不整类移植：无法在本环境验证、且会替换掉现有通用实现（见下文专节） |

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
| 副作用 | — | 无（纯函数，本类不引用任何 Minecraft 类型；接线见第 3 项，默认关闭） |

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

## 第 3 项（已完成）：接线到发包路径（默认关闭）

**接在哪**：`RotationFaker.setRotationPacket(Rotation)` —— 这是所有「伪造朝向」的公共入口
（`faceVectorPacket()` 也走它），`currentRotation` 就是随后被发出去的旋转，所以在这里 patch
等于直接改服务器看到的朝向。

**旧实现 vs 新实现**：

| | 旧 | 新 |
| --- | --- | --- |
| `setRotationPacket` | 只做 `normalize()` 后存进 `currentRotation` | 之后若开关打开，再 `RotationGcdPolicy.patch(currentRotation, new Rotation(getServerYaw(), getServerPitch()), MC.options.sensitivity().get())` |
| 锚点 | — | 服务器**上一次真正看到的**朝向（`getServerYaw()/getServerPitch()`，由 `onSentPacket`+`trackSentRotation` 维护），与 OpenOpal 的 `patchConstantRotation(rotation, getRotation())` 同构 |
| 开关 | — | 新 OTF `Rotation Grid`（`other_features/RotationGridOtf.java`，`OtfList` 靠字段名 `*Otf` 反射自动注册）里的 `Sensitivity grid` 复选框，**默认 false** |
| 默认行为 | — | **完全不变**：开关关闭时这段代码不执行，所有现有手感、所有既有测试逐位不变 |
| 测试 | — | `RotationGcdPolicyTest` 新增 `chainedPatchingKeepsEveryStepOnThePixelGrid`：把「每 tick 以上一次发送值为锚点 patch 一次」连做 40 次，断言每一步都是整数个鼠标像素、且收敛到目标半个像素内 |

**为什么要默认关闭**：这是本轮唯一会改变「服务器看到什么」的改动。客户端侧渲染、`getServerYaw()`
等仍然按原样工作，但开启后发给服务器的旋转会比 hack 请求的值偏差 < 1 个像素
（灵敏度 0.5 时 0.15°，灵敏度 1.0 时 0.768°）——这是把它做成可选开关而不是默认行为的原因。

## 关于 Hypixel / GrimAC 专用件为什么不整类移植

先把范围说清楚：**不移植的是"针对某个服务端/某个反作弊的具体实现"，不是这些能力类别**。
这几类能力我们本来就有通用版本，照搬 OpenOpal 的专用版反而是降级。

| OpenOpal 的东西 | 我们已有的对应物 | 结论 |
| --- | --- | --- |
| `packet/blockage/*`（按住/改写发出的包） | `BlinkHack`、`FakeLagHack`、`PacketCancellerHack` + `PacketOutputListener`（`runAfterSend`）、`ConnectionPacketOutputListener`、`PacketInputListener` | 能力已有；OpenOpal 那套是"按方向/按类型筛包"的另一种组织方式，不是新能力 |
| `TransactionStreamValidator`（45 行） | 无对等物 | 它校验的是 **Hypixel 的事务包流**（用服务端确认序号推算"服务端处理到哪一 tick"）。换服务器后这个包流要么不存在、要么语义不同 ⇒ 结论全错，属于纯负担 |
| `LocalDataWatch`（243 行） | `RotationFaker` 已经维护"服务器上一次看到的朝向/俯仰"（`serverYaw`/`serverPitch` + `onSentPacket`→`trackSentRotation`），`getServerYaw()` 就是"我上一次真正发出去的值" | 可移植的只是**思路**（把"服务端眼里的我"收拢到一处）；按它的实现照搬会造出第二份真相来源，和 `RotationFaker` 的数据互相漂移 |
| `HeypixelCriticals`、`HypixelRotationModel`、`velocity/impl/*`、`disabler/impl/*` | `CriticalsHack`、`NoVelocityHack`、`ReachHack` 等通用实现（各有模式枚举） | 这些代码的价值 100% 是"某个反作弊的某个版本接受它"。本环境只能编译 + 单元测试，没有可连的真实服务器/反作弊实例 ⇒ **无法验证**，按 Rule 9 不能声称它有效 |

三条更硬的理由：

1. **验证边界**：本会话的验证手段只有 `gradlew compileJava` + JUnit。旋转栅格那类"数学上等价于真人鼠标输入"的东西可以离线证明；"GrimAC 会不会判我"不行。
2. **维护成本不对称**：反作弊逻辑在服务端随时改，过期的绕过比没有更糟（会被标记）。而栅格/模型/事件优先级这些"与具体服务端无关"的改进不会过期。
3. **风险不对称**：扣包/延迟技巧在管理规范的服务器上属于会被追责的行为。我们已经把它们做成**显式命名的可选 hack**；把它们改造成"默认就替用户扣包"的形态不是我们想要的默认值。

**什么情况下我会改主意**：如果明确要针对某个服务端做模式，合理做法是 ——（a）只挑一个具体行为（例如某反作弊下的击退），（b）做成该 hack 里**新增的模式枚举**、默认仍是通用模式，（c）在文档里写明"未在真实服务器验证"。这条路随时可以走，但它需要一台能连的测试服务器，而不是照着别人的代码抄。

## 待办（按建议顺序）

1. ~~把栅格接到发送路径~~ **已完成（见第 3 项）**。
2. ~~旋转模型对齐~~ **已完成（见第 2 项）**。剩下的是把栅格与模型接到真正发包的地方（第 1 项）。
3. **事件优先级**：给 `EVENTS` 加优先级排序（按 `Subscribe` 注释语义，即高优先级先跑），
   给现有监听器保留默认 0。改动面约 200 处引用，需要一次性提交。
4. **按需补事件**：我们已有 38 个监听器（含 `PreMotion`/`PostMotion`、`Knockback`、
   `VelocityFromEntityCollision`、`VelocityFromFluid`、`PlayerMove`、`HandleInput`、
   `MouseUpdate`、`PacketInput`/`PacketOutput`）。OpenOpal 有而我们**确实缺**的是：
   `PostMovementPacketEvent`（移动包发完之后的时点）、`AttackDelayEvent`、`ItemUseEvent`、
   `SwingEvent`/`VisualSwingEvent`（真挥手 vs 视觉挥手分离）、`JumpEvent`、`StepEvent`、
   `ClipAtLedgeEvent`、`PushOutOfBlocksEvent`、`StuckInBlockEvent`、`SprintEvent`/`KeepSprintEvent`、
   `SlowdownEvent`、`SlotChangeEvent`、`ChatReceivedEvent`。按"有 hack 需要"逐个补，不做全量。
5. **模块模式类化**：`Criticals`、`Velocity`、`Flight`、`Speed`、`NoSlow` 这几组模式多的模块，
   每个模式拆成一个类（对应 OpenOpal 的 `impl` 目录）。
