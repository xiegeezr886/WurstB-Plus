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

## 第 4 项（已完成，部分范围）：事件监听器优先级

OpenOpal 的 `@Subscribe(priority = …)` 是按优先级派发；我们的 `EventManager` 原本只有
"注册顺序"（`listeners.add(listener)` 后整表复制成快照），同一个事件上不同监听器之间的先后
要求只能靠"谁先被 new 出来"这种隐含顺序。

**改法（对约 200 处既有调用零影响）**：

| | 旧 | 新 |
| --- | --- | --- |
| 注册 | `add(Class<L>, L)`：追加到列表尾部 | 保留该重载，内部转调 `add(type, listener, 0)`；新增 `add(Class<L>, L, int priority)`，按"数值大的先调用、同优先级保持注册顺序"插入 |
| 优先级存储 | — | 新增 `priorityMap`，与 `listenerMap` 的列表**下标一一对应** |
| 移除 | `listeners.remove(listener)` 后重建快照 | 改成按 `indexOf` 定位、同时摘掉 `priorities` 的同一格，列表空了连带清掉 `priorityMap`（否则删中间一个会让后续优先级错位） |
| 可观测性 | 只有 `getListenerCount` | 新增 `getListeners(Class<L>)`（只读快照，按调用顺序） |
| 第一个真实使用点 | `RotationFaker` 的 `PostMotionListener` 靠"客户端初始化时最先注册"排在前面 | 显式注册为优先级 **1000**：`onPostMotion()` 会清空本 tick 的朝向请求，必须早于其它 PostMotion 监听器，否则 hacks 同 tick 设的朝向会被抹掉 |
| 测试 | — | `EventManagerPriorityTest` 4 个用例（高优先级在前 + 同级保持插入顺序、默认重载等于 0、删中间后优先级仍对齐、删空后类型被清理） |

**范围说明**：注解订阅路径（`@WurstSubscribe` / `WurstSubscriber`）**还没有**优先级，
目前只有测试在用；本轮不动它，等有真实订阅者需要排序时再加（见待办）。

## 第 5 项（已开始）：按需补事件，第一个是 `StuckInBlockListener`

OpenOpal 有 `StuckInBlockEvent`，我们原本没有 —— 缺事件的代价是 **mixin 里直接写死某个 hack**。
`mixin/EntityMixin.onMakeStuckInBlock()` 原来是：

```java
		if(WurstClient.INSTANCE.getHax().noSlowdownHack.isEnabled()
			&& WurstClient.INSTANCE.getHax().noSlowdownHack
				.shouldBypassStuckBlock(state))
			ci.cancel();
```

同一个文件里紧挨着的 `VelocityFromFluidEvent`/`VelocityFromEntityCollisionEvent` 早就走事件了，
所以这是**同一文件内两种风格并存**的不一致：新增一个能实现同样效果的 hack，就得回头改 mixin。

**改法**：

| | 旧 | 新 |
| --- | --- | --- |
| 事件 | 无 | `events/StuckInBlockListener.java`（含嵌套 `StuckInBlockEvent extends CancellableEvent`，与 `VelocityFromFluidListener` 同构） |
| mixin | 直接查 `noSlowdownHack` 与 `getHax()` | `EventManager.fire(new StuckInBlockEvent(state))` + `if(event.isCancelled()) ci.cancel();`，**不再 import 任何 hack 或 HackList** |
| 消费者 | `NoSlowdownHack` 被 mixin 反向调用 | `NoSlowdownHack implements StuckInBlockListener`，`onEnable/onDisable` 里注册/注销，`onStuckInBlock()` 用**原来那条一模一样的条件** `isEnabled() && shouldBypassStuckBlock(state)` 决定是否 `cancel()` |
| 行为 | 缠网/细雪/甜浆果丛三个开关生效 | 逐位相同（同一个 `shouldBypassStuckBlock()`、同一个取消点、同一 tick 触发），但现在任何 hack 都能挂上去 |
| 验证 | — | `gradlew compileJava` 通过；无 JUnit（mixin + MC 单例，无法离线触发） |

### 第 5 项第二个：`ClipAtLedgeListener`

`ClientPlayerEntityMixin.maybeBackOffFromEdge()` 原来直接调
`WurstClient.INSTANCE.getHax().safeWalkHack.onClipAtLedge(...)`（这是全仓库唯一的调用点）。
现在改成发 `ClipAtLedgeEvent`（**不可取消**的通知型事件，携带"这一步的移动是否真被边缘收缩修正过"），
`SafeWalkHack implements ClipAtLedgeListener` 并在 `onEnable/onDisable` 注册注销，
`onClipAtLedge(ClipAtLedgeEvent)` 内部第一行取 `event.isClipping()`，**其余逻辑一字未动**。

| | 旧 | 新 |
| --- | --- | --- |
| 触发 | mixin 直接调 SafeWalk 的方法 | `EventManager.fire(new ClipAtLedgeEvent(...))` |
| 消费 | SafeWalk 被反向调用 | SafeWalk 自行订阅；`EventManager` 的优先级机制（第 4 项）现在也适用于它 |
| 行为 | SafeWalk 的"边缘显示潜行" | 逐位相同（同一条件、同一时机、同一个 `setSneaking`） |

### 第 5 项第三个：`StayingOnGroundSurfaceListener`

`ClientPlayerEntityMixin.isStayingOnGroundSurface()` 原来直接问两个 hack：

```java
		HackList hax = WurstClient.INSTANCE.getHax();
		return super.isStayingOnGroundSurface()
			|| hax != null && (hax.safeWalkHack.shouldClipEdges()
				|| hax.scaffoldWalkHack.shouldSafeWalk());
```

这次用的是我们**已有的"投票式"事件形态**（与 `IsPlayerInWaterEvent` 同构：可读、可改、
并保留 `normally…` 原值），而不是 OpenOpal 那套。mixin 变成：

```java
		StayingOnGroundSurfaceEvent event =
			new StayingOnGroundSurfaceEvent(super.isStayingOnGroundSurface());
		EventManager.fire(event);
		return event.isStayingOnGroundSurface();
```

`SafeWalkHack` 贡献 `shouldClipEdges()`、`ScaffoldWalkHack` 贡献 `shouldSafeWalk()` —— 与原来
逐位等价（同样的两个判断、同样的 `||` 语义），但 mixin 里再没有 hack 名字，两个 hack 也都
和 `ClipAtLedge` 一样在 `onEnable/onDisable` 里自行订阅注销。

### 第 5 项第四个：`JumpPowerListener`

`ClientPlayerEntityMixin.getJumpPower()` 原来直接调 `highJumpHack.getJumpPowerFor(super.getJumpPower())`
（全仓库唯一调用点，已确认无其它引用）。改成 `JumpPowerEvent`（投票式，同时带 `getBaseJumpPower()`：
跳跃提升、蜂蜜块都会改基础值，监听器应基于它算），`HighJumpHack` 改为订阅并实现
`onEnable/onDisable` 注册注销；原来的 `getJumpPowerFor()` 随之删掉（否则就是没人调的代码）。

| | 旧 | 新 |
| --- | --- | --- |
| mixin | `WurstClient.INSTANCE.getHax().highJumpHack.getJumpPowerFor(super.getJumpPower())` | `EventManager.fire(new JumpPowerEvent(super.getJumpPower()))` 后返回 `event.getJumpPower()` |
| hack 侧 | 被 mixin 反向调用 | `HighJumpHack implements JumpPowerListener`，`onJumpPower()` 里 `isEnabled()` + 同一个 `JumpHeightSolver.requiredVelocity(height)` |
| 删除的代码 | — | `getJumpPowerFor()`（迁移后无引用） |
| 行为 | 未开 HighJump 时原样返回基础值 | 逐位相同（事件初值就是基础值，没监听器改它就是原值） |

### 第 5 项第五个：`ItemUseSlowdownListener`（对应 OpenOpal 的 `ItemUseEvent`）

`ClientPlayerEntityMixin.onTickMovementItemUse()` 原来直接问 NoSlowdown：

```java
		if(WurstClient.INSTANCE.getHax().noSlowdownHack.isEnabled()
			&& WurstClient.INSTANCE.getHax().noSlowdownHack.shouldBypassUsingItem())
			hideNextItemUse = true;
```

这里是**两步式**的：这个注入点在原版调用 `isUsingItem()` **之前**跑，真正拦截的是后面
`onIsUsingItem()` 里那次调用，中间靠 `hideNextItemUse` 字段传递，所以事件必须承载"要不要绕过"
这个决定，而不是直接返回布尔值。改成投票式事件后：

```java
		ItemUseSlowdownEvent event = new ItemUseSlowdownEvent(false);
		EventManager.fire(event);
		if(event.isBypass())
			hideNextItemUse = true;
```

`NoSlowdownHack implements ItemUseSlowdownListener`，`onItemUseSlowdown()` 里用**原来那条一模一样的
条件**（`isEnabled() && shouldBypassUsingItem()`）设 `setBypass(true)`。mixin 里 `noSlowdownHack`
的引用从 3 处降到 1 处（剩下的 `shouldBypassStuckBlock` 已在第 5 项第一个里切走）。

### 第 5 项第六个：`ForwardImpulseListener` + `SprintHungerListener`（对应 OpenOpal 的 `SprintEvent`/`KeepSprintEvent`）

AutoSprint 在 mixin 里有三处引用，语义并不相同，所以**没有硬塞进一个事件**：

| mixin 位置 | 语义 | 处理 |
| --- | --- | --- |
| `@WrapOperation` 包住 `Input#hasForwardImpulse()` | "这次移动算不算有前进输入"（决定要不要冲刺） | → `ForwardImpulseListener`（投票式，初值就是 `original.call(input)` 的原版结果） |
| `@Inject` 在 `AbstractClientPlayer;aiStep()` 之后 | "原版检查跑完了，现在把冲刺打开" | **保持直接调用**，并在注释里写明理由：这是执行动作、没有第二个参与者，做成事件只是形式主义 |
| `@Inject(HEAD)` 打在 `hasEnoughFoodToStartSprinting()` | "饿着肚子能不能开始冲刺" | → `SprintHungerListener`（投票式） |

`AutoSprintHack` 改为实现这两个监听器（`onEnable/onDisable` 注册注销），原来的
`shouldOmniSprint()`/`shouldSprintHungry()` 被对应的事件处理器取代（只有 mixin 调它们，已确认无其它引用）。

**一个必须记录的坑**：`hasEnoughFoodToStartSprinting()` 的注入点是 `@At("HEAD")`，
`CallbackInfoReturnable#getReturnValueZ()` 在 HEAD **读不到原版返回值**（会抛异常），
所以事件初值只能给 `false`，语义是"只有监听器明确投票 true 才会改结果" ——
这与原逻辑（只有 `shouldSprintHungry()` 为真时才 `setReturnValue(true)`）逐位一致。

### 第 5 项第七个：`HasEffectListener`（一次迁移三个 hack）

`ClientPlayerEntityMixin.hasEffect(MobEffect)` 是第 5 项里最划算的一处：**一个方法里塞了三个 hack**
（Fullbright 的夜视、NoLevitation 的漂浮、AntiBlind 的黑暗），而且都是"按效果类型投票"的同一种语义，
所以用一个事件一次解决：

| | 旧 | 新 |
| --- | --- | --- |
| mixin | `HackList hax = …` + 三段 `if(effect == X && hax.Y...)` | `new HasEffectEvent(effect, super.hasEffect(effect))` → `event.hasEffect()` |
| Fullbright | 被 mixin 调 `isNightVisionActive()` | 订阅；`NIGHT_VISION` 且 `isNightVisionActive()` 时设 true（沿用原判断，不改方法本身） |
| NoLevitation | 被 mixin 调 `isEnabled()` | 订阅；`LEVITATION` 时设 false，新增 `onEnable/onDisable` |
| AntiBlind | 被 mixin 调 `isEnabled()` | 订阅；`DARKNESS` 时设 false，新增 `onEnable/onDisable` |

行为逐位等价：事件初值就是 `super.hasEffect(effect)`，三个监听器用的是各自原来那条一模一样的条件，
且**按效果对象分别判断**，所以多个 hack 同时开会各自生效、不会互相覆盖。
至此 `ClientPlayerEntityMixin` 里 `HackList` 只剩 SafeWalk/ScaffoldWalk 之外的 3 处引用
（`applySprint` 有意保留、`stepHack`、`portalGuiHack`/`freecamHack`）。

### 第 5 项第八个：`AutoJumpListener`

`ClientPlayerEntityMixin.onIsAutoJumpEnabled()`（`@Inject(HEAD)` 打在 `isAutoJumpEnabled()`）原来直接问
`stepHack.isAutoJumpAllowed()`（已确认全仓库只有这一处调用）。改成 `AutoJumpEvent`：
**同样因为注入点在 HEAD 读不到原版结果**，事件初值取 `true`（允许），只接受否决 ——
`if(!event.isAutoJumpAllowed()) cir.setReturnValue(false);`，与原逻辑（只有 `!isAutoJumpAllowed()`
时才关掉）逐位一致。`StepHack` 订阅后仍然复用自己那条 `!isEnabled() && !goToCmd.isActive()` 判断，
条件只有一份，mixin 里不再出现 hack 名字。

至此第 5 项已完成 8 个事件、迁移 10 个 hack；`ClientPlayerEntityMixin` 只剩两处直接引用
（`applySprint()` 有意保留 + PortalGUI/Freecam）。

### 第 5 项第九、十个：`PortalNauseaListener` + `IsSpectatorListener`（mixin 里最后两处直连）

| | 旧 | 新 |
| --- | --- | --- |
| PortalGUI | `beforeUpdateNausea()` 里 `if(!portalGuiHack.isEnabled()) return;`，然后藏屏幕 | 先发 `PortalNauseaEvent`（初值 false），`shouldKeepScreen()` 为真才藏屏幕；`PortalGuiHack` 订阅后设 true（顺带补上它缺的 `onEnable/onDisable`） |
| Freecam | `isSpectator()` 里 `return super.isSpectator() \|\| freecamHack.isEnabled();` | `IsSpectatorEvent(super.isSpectator())` → 监听器可改 → 返回 `event.isSpectator()`；`FreecamHack` 订阅（它本来就有全套 `EVENTS` 注册，加一行即可），并保留 `isNormallySpectator()` 供以后判断"本来是不是旁观者" |

事件名刻意**不含 hack 名字**（不是 `PortalGuiEvent`/`FreecamEvent`）：PortalGUI 只是"要求保住界面"的
第一个用户，Freecam 只是"要求当旁观者"的第一个用户，语义留给事件本身。

**至此 `ClientPlayerEntityMixin` 的直接 hack 引用只剩 `applySprint()` 一处（有意保留、已注明理由）。**

## 第 6 项：模块模式类化（参考 OpenOpal 的 `criticals/impl/*`、`velocity/impl/*`）

### 第一个模块：Criticals（已完成）

原来 `CriticalsHack.onPlayerAttacksEntity()` 里是一个 `switch(selectedMode)`，四个模式的动作
（发什么包、要不要真跳）全写在 hack 里，模式自己的元数据（`requiresGround`）又是枚举构造参数。
现在拆成 `net.wurstclient.hacks.criticals`：

| 文件 | 内容 |
| --- | --- |
| `CriticalsMode` | 接口：`getName()` / `requiresGround()`（默认 false）/ `doCriticals(CriticalsHack)` |
| `PacketCriticalsMode` | `hack.sendPacketProfile(hack.getPacketProfile())` |
| `NoGroundCriticalsMode` | `hack.sendOffset(-0.000001, false)` |
| `MiniJumpCriticalsMode` | `requiresGround()=true`；不在ground 返回 false；push + fallDistance + 两个位移动包 |
| `JumpCriticalsMode` | `requiresGround()=true`；不在ground 返回 false；`jumpFromGround()` + 两个位移动包 |

hack 侧只留通用流程：**状态检查 → 停冲刺 → 交给模式 → 粒子**；`Mode` 枚举变成
"名字 + 一个 `CriticalsMode` 实例"的登记表，`requiresGround()`/`doCriticals()` 都转发给实例
（单一事实来源，不再有重复的布尔字段）。

**行为等价性**（Rule 9 口径）：四个模式的动作逐行照搬；原来 MINI_JUMP/JUMP 在 `!onGround()` 时
`return` 会跳过粒子，现在由 `doCriticals()` 返回 false 让调用方跳过粒子，等价 ✓；
`requiresGround` 仍在停冲刺之前用于 `CombatActionPolicy` 前置检查，位置和语义不变 ✓；
`PacketProfile` 枚举与六个 profile 的包内容一字未改（只是为了让模式类能引用而改成 public）✓。

为模式类开放了三个 public 成员：`getPacketProfile()`、`getJumpHeight()`、`sendOffset()`/`sendPacketProfile()`
（`PacketProfile` 也随之 public）。这是"把 hack 当上下文对象传"的取舍：比新建一个 context 类少一层，
代价是 hack 的 API 变宽 —— 已在代码注释里写明用途。

### 后续（未做）

Velocity / Flight 的模式也还是 `EnumSetting + switch`（`NoVelocityHack` 256 行、`FlightHack` 189 行，
后者是一个 `switch` 表达式算移动向量，拆法与 Criticals 不同：要看它是否依赖同一批局部变量）。
按 Criticals 这套模板逐个拆即可，我打算一次只动一个模块并单独编译验证。

### 第二个模块：Velocity（已完成）

`NoVelocityHack` 原来是**散开的三处模式判断**：收到速度包时 `mode.getSelected() == Mode.JUMP_RESET`、
收到爆炸包时 `== Mode.MODIFY`、每 tick 时 `!= Mode.JUMP_RESET`，外加挂在 hack 上的三个
`pendingJump*` 字段（其实只有 JumpReset 用）。现在拆成 `net.wurstclient.hacks.velocity`：

| 文件 | 内容 |
| --- | --- |
| `VelocityMode` | 接口：`getName()` / `onEntityVelocity(hack, event, incoming)`（返回 true = 取消包）/ `handlesExplosions()` / `onExplosion(packet, hMult, vMult)` / `onUpdate(hack)` / `reset()`，后四个都有默认实现 |
| `ModifyVelocityMode` | 按 Horizontal/Vertical/Retain 改速度包 → `setDeltaMovement` → 返回 true（由 hack 取消）；`handlesExplosions()=true`，爆炸击退按同样比例缩放 |
| `JumpResetVelocityMode` | 不改包（返回 false）；把"刚被击退"记成待跳计数，每 tick 用 `VelocityPlanner.evaluateJumpReset` 决定等/跳；**三个 `pendingJump*` 字段随模式一起搬进来** |

hack 侧只留通用流程与设置读取：`onReceivedPacket` 分派、`shouldApply()` 前置检查、以及 7 个
只读 getter（4 个倍率 + `getJumpDelay()` + `isOnlyMoving()` + `isRequireSprint()`），
`Mode` 枚举同样是"名字 + 一个 `VelocityMode` 实例"的登记表。

**行为等价性**（Rule 9 口径）：`shouldApply()` 仍在两个模式之前调用（原来也在 JUMP_RESET 分支之前）✓；
Modify 的"改包 → 设速度 → 取消包"顺序等价（原来是先设速度再 `event.cancel()`，现在是模式设完速度、
由 hack 取消）✓；爆炸分支仍是 `爆炸开关 && 只有 Modify 处理 && shouldApply()`，操作数顺序未变 ✓；
JumpReset 的 `isFallDamageVelocity` 早退仍是"不计时也不取消包"✓。唯一有意的结构差异：原来
MODIFY 每 tick 调一次 `clearPendingJump()`，现在状态只存在于 JumpReset 模式里，别处**没有**可清的东西，
因此该调用被去掉（不是行为变化，是状态归属变化的自然结果）✓。

### 第三个模块：Flight（已完成）

`FlightHack` 的模式与前面两个不同：它不是一个 `switch` 语句去"做动作"，而是一个 `switch`
**表达式**参与计算"这一 tick 的移动向量"，而且 `VANILLA` 与 `ROCKET` 的水平部分完全相同、
只有垂直倍率不同（原来写成 `mode.getSelected() == Mode.ROCKET ? 3 : 1`，把两个模式的差异
藏在了模式枚举之外）。接口因此设计成两件事：

| 文件 | 内容 |
| --- | --- |
| `FlightMode` | `getName()` / `getHorizontalMovement(delta, forward, sideways, yRot, horizontal)` / `getVerticalMultiplier()`（默认 1） |
| `VanillaFlightMode` | `MovementPlanner.setHorizontal(...)` |
| `RocketFlightMode` | 水平同 Vanilla，`getVerticalMultiplier()=3` |
| `BoostFlightMode` | `clampHorizontal(blendHorizontal(..., 0.1), horizontal)` |

**这个接口不接收 hack 引用**：参数由调用方给全，模式是纯函数，比 Criticals/Velocity 那套
"把 hack 当上下文传"更干净 —— 只有确实需要读写 hack 状态的模式（JumpReset 的待跳计数）才该拿 hack。

**行为等价性**：三种模式的公式逐字照搬；`ROCKET` 的 3 倍从三元表达式改成
`getVerticalMultiplier()`，含义、位置（同一个 `verticalSpeed * …` 表达式）都未变；`VANILLA`/`ROCKET`
共用同一段水平逻辑这一点也仍然成立（两个类各自调用同一个 `MovementPlanner.setHorizontal`）。

### 第四个模块：Speed（已完成）+ NoSlow 的结论（不适用）

本仓库的 Speed 模块文件名是 `SpeedHackHack.java`（232 行，类名 `SpeedHackHack`、`getName()` 是
"SpeedHack"），五个模式也是 `EnumSetting + switch`。拆成 `net.wurstclient.hacks.speed`：

| 文件 | 内容 |
| --- | --- |
| `SpeedMode` | `getName()` / `apply(hack, player, forward, sideways, targetSpeed)` / `reset()`（默认空） |
| `NcpBhopSpeedMode` | `hack.applyHop(..., 0.42, 0.35)` |
| `StrafeSpeedMode` | blend（地面 1、空中 Strafe strength）+ Auto jump 时 y=0.42 |
| `LowHopSpeedMode` | 落地 y=0.2、空中把下沉限制到 -0.08；**`lowHopActive` 状态搬进本类** |
| `OnGroundSpeedMode` | 只在地面把水平速度设成目标速度 |
| `BrutalSpeedMode` | `hack.applyHop(..., max(targetSpeed, Speed×0.45), 0.42, 1)` |

hack 侧留：`canControl()`、`isMoving` 检查、`targetSpeed` 计算（`BASE_SPEED × Speed × 药水倍率`）、
`applyHop()`（NCP Bhop 与 Brutal 共用，所以留在 hack 上并改成 public）与三个只读 getter
（`isAutoJump()`、`getStrafeSpeed()`、`getSpeedSetting()`）。

**状态归属的处理**：原来 `lowHopActive` 挂在 hack 上，而清除它的地方有三处 ——
失去控制、没在移动、以及"当前模式不是 LowHop"（后一处是为了切走模式后不留脏状态）。
现在状态在 `LowHopSpeedMode` 里，hack 用一个 `resetModeState()`（`for(Mode m : Mode.values())
m.impl().reset();`）罩住这三处：只有 LowHop 有状态，其余是空操作，语义与原来"总是把
`lowHopActive` 置回 false"一致 ✓；`onEnable/onDisable` 也改用它 ✓。

**NoSlow 的结论：不适用**。`NoSlowdownHack` 没有模式枚举，它是"若干独立开关 + 事件订阅"的组合
（物品减速、缠网/细雪/甜浆果丛、以及第 5 项里刚接的两个事件），没有"同一件事的多种实现"可拆；
硬套模式类化只会平白多出一层。按 Rule 9，这里不做改动，只记录结论。

### 第五个模块：SpeedMine（已完成）

`SpeedMineHack`（122 行）只有两个模式，但它的**状态归属于 hack**这一点和 Speed 一样
（`appliedHaste`/`previousHaste` 是 Haste 模式的一次性状态）。拆成 `net.wurstclient.hacks.speedmine`：

| 文件 | 内容 |
| --- | --- |
| `SpeedMineMode` | `getName()` / `onUpdate(hack)` / `onDisable()`（默认空） |
| `HasteSpeedMineMode` | 维持我们自己加的急迫；**`appliedHaste`/`previousHaste` 随模式搬进来**，`onDisable()` 负责"只清掉我们加的那一份、把原有急迫放回去" |
| `OgSpeedMineMode` | `MC.gameMode.destroyDelay = hack.getCooldown()` |

**一个容易拆错的点**：原来的清理代码在 hack 的 `onDisable()` 里，**与当前选中的模式无关** ——
即使玩家用 Haste 模式加过效果、然后切到 OG 再关闭，也必须在关闭时清掉。状态搬进 Haste 模式后，
如果只调"当前模式"的 `onDisable()`，这一路就会漏掉。所以 hack 侧写成
`for(Mode m : Mode.values()) m.impl().onDisable();`：Haste 会收拾，OG 是空操作，
与"关闭时总是收拾"逐位一致。原注释（为什么要 `appliedHaste` 兜底）也一并搬进了模式类。

### 第六个模块：Step（已完成）

`StepHack` 的 Simple/Legit 差异很大：Simple 只是把 `maxUpStep` 设成 Height，Legit 则是
一整套"撞墙 + 0.5~1 格台阶"的判断加两次位移动包。拆成 `net.wurstclient.hacks.step`：

| 文件 | 内容 |
| --- | --- |
| `StepMode` | `getName()` / `onUpdate(hack, player)` |
| `SimpleStepMode` | `player.maxUpStep = hack.getHeight();` |
| `LegitStepMode` | 先把 `maxUpStep` 还原成原值，然后冷却/撞墙/在地面/非梯子非水/有移动输入/没按跳跃/头顶不卡/台阶 0.5~1 的九道检查，再发 0.42 与 0.753 两个位移动包、本地 `setPos` 抬升，最后 `hack.setStepCooldown(2)` |

hack 留通用部分：玩家/世界非空检查、`trackPlayer()`（记录原 `maxUpStep`）、`stepCooldown`
每 tick 递减，以及 `getHeight()` / `getPreviousStepHeight()` / `getStepCooldown()` /
`setStepCooldown()` 四个访问器（Legit 模式要读要写冷却）。**检查顺序原样保留** ——
Legit 的九道 return 是顺序敏感的（先冷却再撞墙……），搬进模式类时逐行照抄，没有合并成一条布尔表达式。

### 另两个候选的结论（本轮不做，附证据）

**ScaffoldWalk（已完成，参考 OpenOpal ScaffoldModule）**：不再沿用原来的
`EnumSetting + switch`，而是按 OpenOpal 的「模块 + 每个模式一个类」结构拆成
`hacks.scaffold` 包：

| 文件 | 内容 |
| --- | --- |
| `ScaffoldWalkHack` | 共享机械：候选点生成、BFS 搜索/预算、支撑面排序、`getFastRotation` + 面内穷举、放置校验、同 Y 自动跳、Movement intelligence、槽位切换、渲染 overlay |
| `ScaffoldMode` | 模式生命周期接口（`onTick` / `onPostMotion` / `onBlockPlaced` / `getPacketRotation`） |
| `SimpleTowerMode` | Vanilla / Anti Gaming Chair / Watchdog / Bloxd（塔跳动作与 Bloxd 额外交互包、+2 脱困） |
| `TellyScaffoldMode` | Telly：目标 Y 层、地面/空中旋转规则、自动跳、灵敏度栅格化 |
| `HeypixelScaffoldMode` | Heypixel 完整流程：眼位扫描（d ≤ 6 的菱形）、正/反 yaw 射线、偏移回退、面内穷举、duplicate rot jitter、telly/bridge 两条路径、UpTellyBypass |
| `HypixelScaffoldMode` | 继承 Heypixel，常开 telly、5 ticks、180°/tick、Sideways 基准 yaw |
| `SilenceTellyScaffoldMode` | 完整的 request → 写入移动包 → PostMotion 校验 → armed placement 状态机，以及 rescue、eagle、parkour jump、block fly、rotation abuse |
| `ScaffoldUtils` / `ScaffoldRotationModel` | 旋转/射线/方块筛选与 OpenOpal 的五种旋转模型（Scaffold 用到的 Instant / Heypixel / Linear / Sideways） |
| `SilenceTellyRaycast` / `SilenceTellyRotation` / `SilenceTellyFallingPlayer` | SilenceTelly 的体素 DDA、面内最近旋转、下落预测 |

**有意不搬的部分（有据可查）**：
1. **StuckModule / SkipTickUtility 的 Self rescue**：Wurst 没有 stuck/skip-tick 模块，
   `Self rescue mode` 设置也没有保留，避免留下永远不生效的开关。
2. **OutboundNetworkBlockage**：OpenOpal 的 SilenceTelly 在扣包期间会丢弃已 arm 的放置；
   Wurst 有 `BlinkHack` 等扣包能力，但没有同口径的 `isAnyBlockages()` 查询，因此这里不拦截。
3. **MoveInput 取消**：没有 OpenOpal 的 `MoveInputEvent`，`ST` 的 `movementCancelTicks`
   只能直接把 `input.forwardImpulse/leftImpulse` 置 0；随后原版 `input.tick()` 可能覆盖，
   属于**尽力而为**，不是逐位等价。
4. **`ST Keep FOV` / `ST FOV`**：OpenOpal 是在 `GameRendererMixin` 里乘 FOV，
   本工程没有对应 mixin；为避免死开关，未加入这两个设置。
5. **面内穷举的采样密度**：OpenOpal 的 `getRotationFromRaycastedBlock` 在每个轴取
   24 个点（24³ ≈ 13.8k 次世界射线）。作为 fast path 失败后的兜底，这个密度在
   1.20.1 单线程客户端上可能单独吃掉一个 tick；这里改成每轴 12 个点（12³ ≈ 1.7k），
   仍是同一套穷举结构，只降低采样密度。
6. **`ST Spoof Item` 的静默切槽**：OpenOpal 的 SlotHelper 不改客户端槽位；这里在
   `tryPlaceArmed()` 内临时把客户端选中槽切到方块槽，调用 `useItemOn` 后立即切回并
   补发 `SetCarriedItem` 包。效果等价（同一 tick 内客户端看不到切槽），但实现路径不同。

**验证边界**：`compileJava` 通过（JDK 21 运行 Gradle、release 17）；真实客户端到达主菜单
并稳定运行 25 秒（无 mixin/启动错误）。**进世界验证被仓库既有的 Baritone Fabric mixin
问题挡住**（`mixins.baritone.json:MixinPalettedContainer$Data` 的 `@Accessor("palette")`
找不到目标，`class_2837` 未重映射），该崩溃发生在 Scaffold 代码执行之前，与本次改动无关；
因此各模式的实战手感（telly 提前量、SilenceTelly 的 arm 时机、Hypixel 落点）**尚未实机确认**。

**BunnyHop（不适用）**：`JumpIf` 的枚举常量自带 `Predicate<LocalPlayer> condition`
（L58 直接 `jumpIf.getSelected().condition.test(player)`），这已经是"每个模式自带行为"的形态，
既没有 switch 也没有 hack 内部状态可搬 —— 再套一层模式类只会多一层间接。

## 第 7 项：注解订阅路径的优先级（已完成）

前面一直把这项挂着的原因是"没有真实订阅者就无法验证"，这次查清了：注解订阅机制**一直都在**——
`@WurstSubscribe` + `WurstSubscriber` + `EventManager.subscribeAnnotated()`，
而且 `Hack` 在 `onEnable/onDisable` 里就会调用它，所以每个 hack 都是潜在订阅者；
现有测试 `EventManagerTest` 也已经在用它。缺的只是**顺序**：`subscribeAnnotated()` 原来只是
`subscribers.add(subscriber)`，注解里没法表达"我要先跑"。

改动：

| 文件 | 改动 |
| --- | --- |
| `WurstSubscribe` | 新增 `int priority() default 0`，javadoc 写明"数值越大越先调用，同优先级按注册顺序"（与第 4 项给 `EventManager.add(type, listener, priority)` 定的约定一致） |
| `WurstSubscriber` | 新增 `priority` 字段 + `getPriority()`；优先级在构造时从方法上的注解读出，**没有注解时是 0**（这样 3 参数构造器那条路径也不受影响） |
| `EventManager` | `subscribeAnnotated()` 改为 `insertByPriority()`：从高到低找到第一个优先级更低的位置插入，同优先级插在已有者**之后** ⇒ 稳定；另加 `getAnnotatedSubscribers(eventType)` 只读快照（与第 4 项加的 `getListeners(type)` 对称） |

测试：`EventManagerAnnotatedPriorityTest` 四个用例（乱序注册后按优先级排、同优先级保序、
默认 0 高于 -5、注销后计数归零）。

### 这一项暴露出的测试陷阱（值得记下来）

第一版测试是"直接 `fire(new DummyEvent())` 然后断言回调顺序"，结果三个用例全部失败，
实际值是 `[]` —— 追下去发现 `EventManager.fireImpl()` 开头有 `if(!wurst.isEnabled()) return;`，
离线单测里客户端没启用，**事件根本不会派发**。所以改成断言
`getAnnotatedSubscribers()` 的列表顺序（那正是 `fireAnnotated()` 的迭代顺序），
并把原因写在测试类的 javadoc 里。

已知限制（没做）：`fireAnnotated()` 是按 `annotatedSubscribers` 这个 **HashMap 的 entrySet**
迭代的，所以"同一个事件同时命中多个不同事件类（例如事件类与它的子类各注册了订阅者）"时，
**类与类之间**的先后由 map 顺序决定，优先级只在**同一个事件类内部**保证。要彻底确定，
得把 key 也排序；目前没有这种订阅者，先记录、不猜。

验证边界：本项有真正跑起来的 JUnit（`net.wurstclient.event.*` 全部通过）——
但如上一段所述，端到端的"事件确实被派发到注解订阅者"在这套离线测试里做不了（被
`wurst.isEnabled()` 挡住），只能验证派发**顺序**。

## 待办（按建议顺序）

1. ~~把栅格接到发送路径~~ **已完成（第 3 项）**；~~旋转模型对齐~~ **已完成（第 2 项）**。
2. **注解订阅路径的优先级**：`@WurstSubscribe` 加 `int priority() default 0`，`WurstSubscriber`
   读取并存下来，`subscribeAnnotated` 按优先级（大的先）稳定排序。等出现第一个真实订阅者再做，
   避免造出没人用的机制。
3. ~~事件优先级~~ **已完成（第 4 项）**：经典注册表已支持，`RotationFaker` 是第一个使用点。
4. **按需补事件**（`StuckInBlockListener` 已完成，见第 5 项）：我们已有 38 个监听器（含 `PreMotion`/`PostMotion`、`Knockback`、
   `VelocityFromEntityCollision`、`VelocityFromFluid`、`PlayerMove`、`HandleInput`、
   `MouseUpdate`、`PacketInput`/`PacketOutput`）。OpenOpal 有而我们**确实缺**的是：
   `PostMovementPacketEvent`（移动包发完之后的时点）、`AttackDelayEvent`、`ItemUseEvent`、
   `SwingEvent`/`VisualSwingEvent`（真挥手 vs 视觉挥手分离）、`JumpEvent`、`StepEvent`、
   `ClipAtLedgeEvent`、`PushOutOfBlocksEvent`、`StuckInBlockEvent`、`SprintEvent`/`KeepSprintEvent`、
   `SlowdownEvent`、`SlotChangeEvent`、`ChatReceivedEvent`。按"有 hack 需要"逐个补，不做全量。
5. **模块模式类化**：`Criticals`、`Velocity`、`Flight`、`Speed`、`NoSlow` 这几组模式多的模块，
   每个模式拆成一个类（对应 OpenOpal 的 `impl` 目录）。

## 第 8 项：Scaffold 逐行对齐 OpenOpal（进行中）

第 7 项之前 Scaffold 虽然已经按 OpenOpal 拆好了类，但为了适配 Wurst 的
`RotationFaker`/事件系统做了若干近似，用户实机反馈「不是脚底下搭方块，而是乱搭」。
本轮按 `reference/clients/OpenOpal`（与上游 `ZSZ7/OpenOpal` 当前 HEAD 内容一致）
逐段对齐，并用真实客户端 + 真实世界验证。

### 已对齐/新增的基础设施

| 内容 | 文件 | 说明 |
| --- | --- | --- |
| 移动输入事件 | `events/MoveInputListener` + `mixin/KeyboardInputMixin` | 在 `KeyboardInput.tick()` 之后派发，可改 forward/sideways/jump/sneak，对应 OpenOpal 的 `MoveInputEvent` |
| 假旋转跟踪 | `hacks/scaffold/ScaffoldRotationHandler` | 只维护「要发给服务端的旋转」，**不改真实客户端朝向**。OpenOpal 改真实朝向再用 Movement Fix 把移动方向拉回来；Wurst 的 `RotationFaker` 本来就是发包层伪造，这样移动方向与 OpenOpal 的最终效果一致且不会偏航 |
| 下落模拟 | `hacks/scaffold/PlayerSimulation` | 克隆本地玩家为 `RemotePlayer`，用 `travel()` 推进；取代之前的弹道近似 |
| 旋转数学 | `ScaffoldUtils` | 新增 `getVanillaRotation` / `getSentRotation` / `getCursorDelta` / `getDuplicateWrapped` / `getRotationDifference`，射线候选按 OpenOpal 的 `getRotationDifference` 排序（yaw 取绝对值） |
| 旋转模型 | `ScaffoldRotationModel` | Instant / Heypixel / Linear / Organic / Sideways 五种；另加 Hypixel 动态速度模型 |
| 真实堆叠数量 | `ScaffoldWalkHack.realStackSizeMap` | 放置扣 1、拾取包加回，`getPlaceableBlock()` 用它而不是直接读堆叠数 |
| 交互 CPS | `ScaffoldWalkHack` + 「Interact CPS」设置 | 没有缓存目标时按 CPS 模拟右键（对应 OpenOpal 的 `simulateClick`） |
| 右键抑制 | `ScaffoldWalkHack.onRightClick` | 启用时屏蔽原版右键，对应 OpenOpal 的 `MouseButton.setDisabled()` |

### 关键行为修正（附原因）

1. **放置时点**：从 `PostMotion`（移动包之后）改到 `HandleInput`（玩家 tick 之前，
   等价于 OpenOpal 的 `PreGameTick` + `MouseHandleInputEvent` 顺序）。
2. **不再覆盖 `MC.hitResult`**：之前用 `blockCache.hit()` 覆盖准星并在后续 tick
   被 `simulateClick` 当成真实准星，导致在玩家没看的地方放方块。现在通过参数
   直接使用缓存命中；准星保持原版。
3. **同一个 Y 层**：非 Telly 模式的候选点只取「玩家所在柱 + 移动方向前方 1/2/3 格」，
   搜索深度改为 0，并禁止搜索比目标层更高的位置。OpenOpal 原始的 3×3 脚部候选 + 深度 4
   BFS 在 1.20.1 实机里会合法地选中侧方/上方方块，表现为 3 格宽铺路和「楼梯」。
   这是**有意偏离**，目的是保证用户要求的「脚底下搭」。
4. **缓存层校验**：`sameYPos` 改变后，旧 `blockCache` 若不在当前层会被丢弃，避免
   一次意外高处的放置被持续复用。
5. **下落模拟只在真正下落时运行**，且目标 Y 不高于 `sameYPos - 1`；否则跳跃上升阶段
   会在跳跃高度铺一层，然后踩着它继续升高（实机复现到的「楼梯」根因之一）。

### 实机验证（2026-10-26，Fabric 1.20.1 dev 客户端）

- 先修了 dev 环境的 Baritone 映射问题：`fabric/build.gradle` 把 Baritone 从
  `include implementation(...)` 改成 `modImplementation` + `include`，并把
  `dev.babbaj:nether-pathfinder` 单独放到 dev classpath。不改的话进世界会崩在
  `MixinPalettedContainer$Data` 的 `@Accessor("palette")`（Yarn 名在 Mojmap dev
  环境解析不到）。生产包仍然把 Baritone 作为 JiJ 打进 jar。
- 测试环境：`xray-test` 世界，独立平台 `y=100`，用 `TestControlServer` 新增的
  测试端点驱动输入：
  - `POST /input?key=forward&down=true|false`（模拟按键）
  - `POST /rotation?yaw=&pitch=`（仅测试用，直接设客户端旋转）
  - `GET /player`（客户端位置/朝向/选中槽/主手）
  - `POST /setting?hack=ScaffoldWalk&name=mode&value=Watchdog`（运行时改设置）
- **Watchdog**：从平台边缘向东走 1.2 秒，桥面为 `x=2001..2009, y=100, z=2014`
  的单宽直线，探针 `Y_100` 命中、`Y_101/102/103` 全部未命中；玩家始终在这条桥上方，
  没有侧向漂移或爬升。
- **Telly**：同场景开 `Auto jump`，桥面同样是 y=100 的直线，玩家在 y≈102 的跳跃高度，
  无异常。
- **Heypixel**（关闭 Uitems Telly）：桥面 `x=2001..2006, y=100, z=2011`，无异常。
- **SilenceTelly**：最初只能放前 5 格后坠落的根因是 **用移动后的眼位去验证移动前算出的旋转**，
  射线必然打偏，`onPostMotion` 每次都判定 “sent rot misses”。现在 request 时记录眼位，
  post-movement 校验/武装/放置都用这个眼位（与 OpenOpal 的 `packetEyePos` 同构），
  实机从平台边缘连续放置到 `x=2009`，玩家稳定站在 `y=101` 的桥上，不再坠落。

### 尚未对齐 / 已知限制

- **SilenceTelly 高级开关**：`StuckModule`/`SkipTickUtility` 的 Self rescue、
  `OutboundNetworkBlockage` 的「扣包期间不放置」、`ST Keep FOV`/`ST FOV` 的
  `GameRenderer` mixin 仍未接入；默认 Telly 路径已实机验证。
- **SlotHelper**：目前是「切到方块槽并保持」，OpenOpal 是每 tick 切换再还原、
  并且有 NORMAL/HOTBAR/FULL 的视觉槽位静默；服务端看到的放置槽一致，但发包序列不同。
- **Hypixel addons「Boost」**：仅在检测到 Hypixel 时显示，尚未接入实际行为。
- **Organic 模型**：目前用共享随机源近似，OpenOpal 每个实例有独立的频率/相位。

### 复现命令

```powershell
# 只编译
.\fabric\gradlew.bat -p fabric compileJava

# 真实客户端（需要 JDK 21；本机 JAVA_HOME 默认是 25，Gradle 8.11 不认）
$env:JAVA_HOME='C:\Users\ui863\.jdks\jdk21.0.12_12'
.\fabric\gradlew.bat -p fabric runClient "-PspWorld=xray-test" "-PspLog=$env:TEMP\scaffold-sp.json"
```
