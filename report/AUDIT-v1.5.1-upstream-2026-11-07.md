# v1.5.1 潜藏 bug 复核：Wurst 上游对照

审计日期：2026-11-07。确认 **6 类可定位的问题**；其中 3 类完成实际生产源码的独立 Java 复现，另 3 类通过活动 Mixin 配置、调用链和版本范围检查确认。没有启动 Minecraft，没有重新构建全部工程，没有修改产品源码。

> **2026-11-07 更新**：本报告所列 6 类问题已在同一天全部修复并同步，验证结果见文末「修复记录」。

## 审计对象与证据边界

- **当前对象**：`main` 的 `d7d4eba9` 加本次检查时已有的工作区改动。核对 66 个标识为 v1.5.1 的工程；排除根目录 Forge 1.20.1 的 v1.6.0。
- **历史对象**：`v1.5.1` 标签，提交 `4686900ec86ecdec88bb4eecfd730c1d74537521`。逐项标注问题是否已在标签中存在，避免把当前未提交的回归说成已发布缺陷。未检查线上发布 jar 是否与标签逐字节对应。
- **官方基准**：本地 `reference/recon/wurst7.git`，来源 `https://github.com/Wurst-Imperium/Wurst7.git`。1.20.1 基准 `d3e6eeb25bfa5c6a2497aeb8c6b3a02ca8e512de`；26.2 基准 `86f5296360cd23a5bc21a78cd6ca66b4413b7eb6`；26.3 基准 `71e3af0f2747937086ef889e34359507d95cc9f8`。
- 上游仓库是部分克隆，部分 Java blob 的补取受代理／证书吊销检查环境阻碍；相应 1.20.1 文件改用已有官方源码，并核验 Git blob 哈希一致。26.2／26.3 官方 Mixin 配置直接读取现有 Git 对象并核验哈希。这里没有声称基准是在线最新上游。
- 本次是行为审计。官方没有的本地扩展，以仓库公开的设置／调用契约核对；不把正常的产品差异当成 bug，也不把上游原有行为自动算成本地回归。

## Standards：本地功能与既有机制的集成

本轴发现 **3 类 P2**。最直接的是 HOLD 状态管理错误；其余两类是新功能未完整接入已有命令机制。没有把命名或重复代码计入缺陷。

### S1 · [P2] 同一功能绑定两个 HOLD 键，释放一个会提前关闭

**影响范围：66 / 66 工程；历史标签中 66 / 66 也存在。**

位置：[KeybindProcessor.java:125](C:/Users/ui863/Documents/trae_projects/VAP/fabric/src/main/java/net/wurstclient/keybinds/KeybindProcessor.java:125)，释放检查在同文件第 101–108 行。

`processHoldEnable()` 只有在 `!hack.isEnabled()` 时才把当前键登记到 `heldHacks`。第一键已启用功能后，第二键不会登记。释放第一键时，`handleRelease()` 认为已没有其他持有者，立即关闭功能。

触发：将 A、B 分别绑定为 `+Fullbright`，依次按住 A、B，再松开 A，保持 B 按下。预期 B 继续持有，实际功能关闭。释放端已有“检查其他持有键”的逻辑，说明多键持有本来就是应处理的状态。

验证：用**未改动的生产 `KeybindProcessor.java` 和 `KeyAction.java`**，配最小 Minecraft／功能对象替身编译，通过真实 `onKeyPress()` 入口执行：

```text
A+B down: enabled=true
A released, B remains down: enabled=false (expected true)
```

上游 1.20.1 只提供切换行为；HOLD 是本地扩展，此问题来自扩展的持有者管理。对应上游文件已与 Git blob `4868aab6ce1fa5f006a2d22a42a25b27e0a7bbe4` 核对。

建议：第二个 HOLD 键应加入已有 HOLD 的持有者集合；同时保留“独立手动开启的功能不被 HOLD 释放误关”的语义，不能简单无条件接管。

证据：[执行结果](C:/Users/ui863/Documents/trae_projects/VAP/.planning/2026-11-07-wurst-v151-upstream-audit/core-harness/result.txt)。

### S2 · [P2] HUD 已进入界面和设置系统，却不能被 `.set*` 命令找到

**影响范围：66 / 66 工程；历史标签中 66 / 66 也存在。**

位置：[CmdUtils.java:30](C:/Users/ui863/Documents/trae_projects/VAP/fabric/src/main/java/net/wurstclient/util/CmdUtils.java:30)。

`findFeature()` 只搜索 Hack、Command、OtherFeature 三个注册表，没有 HUD。与此同时，`FeatureMenuSupport.getAllFeatures()` 已把 HUD 加入界面列表，`SettingsFile` 也收集 HUD 设置；HUD 的 Setting 还会生成 `.setmode`、`.setcheckbox` 等绑定命令。

触发：执行 `.setmode hudsettings theme next`，或者使用 HUD 设置生成的 `.set*` 绑定。命令报告 `A feature named "hudsettings" could not be found.`，即使 HUDSettings 在界面和设置文件里确实存在。

官方 [CmdUtils.java](https://github.com/Wurst-Imperium/Wurst7/blob/d3e6eeb25bfa5c6a2497aeb8c6b3a02ca8e512de/src/main/java/net/wurstclient/util/CmdUtils.java) 从 Navigator 的统一功能列表搜索；本地替换成三个旧注册表后，遗漏了后来引入的 HUD。

验证：编译未改动的生产 `CmdUtils.java`，提供分别包含 Hack 与 HUD 的注册表替身。Hack 查询成功，HUD 查询稳定抛出上述错误。测试仅证明查找行为，不等同于完整游戏内命令测试。

建议：界面、命令、配置及按名称查询统一使用包含 HUD 的功能集合；一并核对 `WurstClient.getFeatureByName()`，它当前也只识别旧三类功能。

### S3 · [P2] 嵌套设置在界面可改，却无法通过设置命令访问

**确认触发范围：42 / 66 工程的 EntityCulling；历史标签中同样是 42 / 66。** 其余工程也使用仅搜索顶层的查找器，但未据此虚增实际受影响数量。

位置：[CmdUtils.java:47](C:/Users/ui863/Documents/trae_projects/VAP/fabric/src/main/java/net/wurstclient/util/CmdUtils.java:47)；设置树在 [EntityCullingHack.java:26](C:/Users/ui863/Documents/trae_projects/VAP/fabric/src/main/java/net/wurstclient/hacks/EntityCullingHack.java:26)。

EntityCulling 将 `Players`／`Other entities` 挂在 `Cull targets` 下，将 `Visible delay`／`Hidden delay` 挂在 `Query timing` 下。只把父节点注册进顶层设置表。`findSetting()` 仅做 `feature.getSettings().get(name)`，不会遍历 `getChildren()`。

触发：`.setcheckbox entityculling players off` 或 `.setslider entityculling visible_delay 100`。查找报错，但相同设置可以在展开后的 GUI 中修改。

这是在上游原本的平面设置查找器上添加树形设置时遗漏的集成。上游没有这套本地子设置结构，不能据“上游也是顶层查找”认定本地行为正确。

验证：同一份实际 `CmdUtils.java` 独立 Java 测试中，顶层 `Cull targets` 查询成功，子项 `Players` 查询失败。

建议：递归查找设置，并为同名子项定义明确的路径／歧义处理；继续支持旧配置别名。

S2／S3 的共同执行证据：[result.txt](C:/Users/ui863/Documents/trae_projects/VAP/.planning/2026-11-07-wurst-v151-upstream-audit/verification/cmdutils/result.txt)。

## Spec：相同 Minecraft 版本的上游行为链路

本轴发现 **2 类 P1、1 类 P2**。最严重的是事件发布入口未加载：功能可以正常启用，但渲染逻辑根本不会被调用。

### U1 · [P1] Fabric 26.x 丢失 GUI 渲染事件入口，26.2／26.3 还丢失 3D 入口

**当前影响：Fabric 26.1、26.1.1、26.1.2、26.2、26.3 共 5 个工程；其中 26.2／26.3 另有 3D 事件缺口。历史标签和当前 HEAD 已提交版本均登记了这些入口，属于本次所见未提交工作区的回归。**

位置：[26.3 活动配置:75](C:/Users/ui863/Documents/trae_projects/VAP/fabric/versions/26.3/src/main/resources/wurstpenguin.mixins.json:75)。该工程 `fabric.mod.json` 只加载这个 Mixin 配置。

5 个工程的活动配置都没有加载 GUI 渲染事件发布者 `GuiMixin`／`IngameHudMixin`；26.2／26.3 还漏掉唯一发布 Wurst `RenderEvent` 的 `LevelRendererMixin`。

调用链已核对：

- [HudManager.java:105](C:/Users/ui863/Documents/trae_projects/VAP/fabric/versions/26.3/src/main/java/net/wurstclient/hud2/HudManager.java:105) 订阅 GUIRenderListener，并在收到事件时调用 `renderFrame()`。
- [GuiMixin.java:40](C:/Users/ui863/Documents/trae_projects/VAP/fabric/versions/26.3/src/main/java/net/wurstclient/mixin/GuiMixin.java:40) 是 GUI 事件发布点，但未加载。
- [LevelRendererMixin.java:52](C:/Users/ui863/Documents/trae_projects/VAP/fabric/versions/26.3/src/main/java/net/wurstclient/mixin/LevelRendererMixin.java:52) 是 3D 事件发布点，但未加载。
- Fabric 初始化器没有替代渲染回调；GameRenderer 调用的 `HudManager.prepareFrame()` 为空方法，不能补上链路。

触发：进入世界，开启 HUD 模块，或在 26.2／26.3 开启依赖 RenderListener 的覆盖层功能。预期收到逐帧事件，当前源码下对应回调不会发生。

同版本官方 [26.3 配置](https://github.com/Wurst-Imperium/Wurst7/blob/71e3af0f2747937086ef889e34359507d95cc9f8/src/main/resources/wurst.mixins.json) 明确登记 HUD 与 LevelRenderer 的事件入口；官方 26.2 同样如此。官方类名与本地适配类名不完全一致，比较依据是事件职责和注入链路。

建议：恢复**当前版本可用**的 GUI／3D 事件入口，并核验真实 Minecraft 方法签名；不要把过时的同名 Mixin 无差别全部加回。补上“活动配置必须存在渲染事件发布者”的检查，再做游戏内显示验证。

验证：解析全部 66 个工程的配置并归一化 `/`、`.` 子包写法；检查活动发布者和替代回调，确认 5／2 个缺口。没有游戏内复现，不声称已经确认最终画面。

### U2 · [P1] Freecam 的独立位置／角度没有接到实际相机

**当前影响：Fabric 上述 5 个 26.x 工程，加 Forge 1.21.5，共 6 个工程。Fabric 5 个是未提交工作区回归；Forge 1.21.5 在历史标签中已经存在。**

位置：Fabric 的 [freecam/CameraMixin.java:39](C:/Users/ui863/Documents/trae_projects/VAP/fabric/versions/26.3/src/main/java/net/wurstclient/mixin/freecam/CameraMixin.java:39) 未登记；Forge 的 [FreecamHack.java:147](C:/Users/ui863/Documents/trae_projects/VAP/versions/1.21.5/src/main/java/net/wurstclient/hacks/FreecamHack.java:147) 更新内部相机位置，却没有活动相机状态消费者。

本地 Freecam 使用独立的 `camPos`、`camYaw`、`camPitch`。Fabric 的 `freecam.CameraMixin` 才会把它们写进 Camera；它和配套的 `freecam.LocalPlayerMixin` 没有进入活动配置。普通的 `CameraMixin` 虽已登记，但没有读取这些 Freecam 状态。Mixin 插件也不动态追加这批类。Forge 1.21.5 则连对应相机消费者源码都没有。

触发：在世界中启用 Freecam，再移动或转动视角。逻辑层更新独立相机状态，渲染相机却不消费这些数据，导致自由相机功能链路不完整。

官方相同版本配置登记 `freecam.CameraMixin` 等配套注入，实际消费对应相机状态。此问题不是普通 CameraMixin 的命名差异，而是注册后的代码确实没有消费者。

建议：Fabric 恢复经过版本核对的成套 Freecam 注入；Forge 1.21.5 补全相机适配。修复后检查移动、转向、退出恢复与切换世界。

验证：扫描全部活动 Mixin 对 `getCamPos`／`getCamYaw`／`getCamPitch` 的消费情况并核对替代路径；静态确认 6 个断链工程，未进行游戏内操作。

### U3 · [P2] NoSlowdown 的 Using items／Blocking 开关在 33 个工程中无效

**影响范围：Forge／Fabric／NeoForge 的 1.21.6–1.21.11 和 5 个 26.x 版本，共 33 个工程；历史标签中全部 33 个也存在。**

位置：[LocalPlayerMixin.java:152](C:/Users/ui863/Documents/trae_projects/VAP/fabric/versions/26.3/src/main/java/net/wurstclient/mixin/LocalPlayerMixin.java:152)，同文件第 135、168 行存在相同判断；对应策略在 [NoSlowdownHack.java:78](C:/Users/ui863/Documents/trae_projects/VAP/fabric/versions/26.3/src/main/java/net/wurstclient/hacks/NoSlowdownHack.java:78)。

三个物品使用相关包装器只检查 `noSlowdownHack.isEnabled()`，随后直接返回 `false`。本地已经定义的 `shouldBypassUsingItem()` 会区分 Using items 与 Blocking 的用户选择，却没有在这些包装器中被调用。

触发：只想使用模块的方块相关选项，开启 NoSlowdown，但关闭 Using items／Blocking，再使用食物或盾牌。物品减速及相关疾跑限制仍被全局模块开关覆盖，两个子选项不起作用。

上游的包装器按全局功能开关工作；fork 新增了细粒度设置后继续照搬全局判断，造成设置契约失效。旧版本地 ClientPlayerEntityMixin 已有调用细粒度策略的实现，可以作为修复参照。

建议：在相关物品处理入口同时判断模块启用状态和本地细粒度策略，验证两个开关的独立组合。

验证：全部工程扫描确认 33 个具有相同三处判断的版本，并逐工程核对历史标签；使用实际缓存的 Minecraft 26.3 jar 执行 `javap`，确认 `modifyInput`、`aiStep`、`canStartSprinting` 中相关调用仍然存在。未在游戏中测量移速。

## 可复查证据

全部临时验证文件位于 `.planning/2026-11-07-wurst-v151-upstream-audit/`，没有向产品源码或测试目录写入修复。

| 证据 | 用途 |
| --- | --- |
| [core_review.md](C:/Users/ui863/Documents/trae_projects/VAP/.planning/2026-11-07-wurst-v151-upstream-audit/core_review.md) | 核心行为独立复核及 HOLD 复现说明 |
| [hook_review.md](C:/Users/ui863/Documents/trae_projects/VAP/.planning/2026-11-07-wurst-v151-upstream-audit/hook_review.md) | 上游注入链路独立复核 |
| [verify_hook_findings.py](C:/Users/ui863/Documents/trae_projects/VAP/.planning/2026-11-07-wurst-v151-upstream-audit/verify_hook_findings.py) | 从仓库根目录运行的只读范围检查 |
| [hook-validation.json](C:/Users/ui863/Documents/trae_projects/VAP/.planning/2026-11-07-wurst-v151-upstream-audit/hook-validation.json) | 逐工程 5／2／6／33 范围清单 |
| [owner-scope.json](C:/Users/ui863/Documents/trae_projects/VAP/.planning/2026-11-07-wurst-v151-upstream-audit/owner-scope.json) | 命令查找缺口与 42 个子设置工程 |
| [owner-tag-classification.json](C:/Users/ui863/Documents/trae_projects/VAP/.planning/2026-11-07-wurst-v151-upstream-audit/owner-tag-classification.json) | 66／42／66 个历史标签问题范围 |
| [hook-tag-classification.json](C:/Users/ui863/Documents/trae_projects/VAP/.planning/2026-11-07-wurst-v151-upstream-audit/hook-tag-classification.json) | Mixin 缺口与历史标签的区别 |
| [upstream-cache-verification.json](C:/Users/ui863/Documents/trae_projects/VAP/.planning/2026-11-07-wurst-v151-upstream-audit/upstream-cache-verification.json) | 官方 1.20.1 缓存源码的 Git blob 一致性 |
| [hook-upstream-verification.json](C:/Users/ui863/Documents/trae_projects/VAP/.planning/2026-11-07-wurst-v151-upstream-audit/hook-upstream-verification.json) | 官方 26.2／26.3 配置的 Git blob 一致性 |

## 未计入确认缺陷的内容

- 旧 `report/AUDIT-v1.5.1-vs-upstream.md` 的 Scaffold 右键拦截、HUD 图标无界缓存、Flight 旁观者状态恢复、Blink 先清队列等条目，当前源码已有针对性变化，本次没有直接复用旧结论。
- Fabric 的 Addon Brigadier 命令存在“收集但没有注册回调”的静态线索；本次没有示例 Addon 的运行验证，列为后续检查项，不计入上述 6 类。
- 没有把全量编译通过或曾经启动成功当成这些行为正确的证明；既有启动矩阵使用过修改内嵌依赖的测试包。
- 这是一轮针对核心机制和上游适配的审计，不能据此断言其余功能不存在潜藏问题。

两轴结论：**Standards 3 类 P2；Spec 2 类 P1、1 类 P2。**

---

## 修复记录（2026-11-07，本轮）

6 类问题全部修复，共改动 **239 个文件**（233 个 Java + 6 个 Mixin 配置，含 1 个新增文件）。

| 编号 | 修复内容 | 覆盖 |
| --- | --- | --- |
| U1 | 5 个 Fabric 26.x 配置加入 `GuiMixin`；26.2／26.3 加入 `LevelRendererMixin`。`IngameHudMixin` 未注册：26.1.1+ 的 `Gui` 已没有 `renderTabList` 等方法，注册会直接启动失败 | 5 |
| U2 | Fabric 26.x 加入 `freecam/CameraMixin`、`freecam/LocalPlayerMixin`、`freecam/ItemInHandRendererMixin`；Forge 1.21.5 按 1.21.4 补 `freecam/CameraMixin` 并注册（已核对 `Camera.setup(BlockGetter, Entity, boolean, boolean, float)` 与 `detached` 字段） | 6 |
| U3 | 三处 `@WrapOperation` 的条件由 `isEnabled()` 改为 `isEnabled() && shouldBypassUsingItem()` | 33 |
| S1 | `processHoldEnable` 在功能已被其他 HOLD 键持有时，把当前键登记为共同持有者；新增 `isHeldByAnyKey`。手动开启的功能（无持有者）不受影响 | 67 |
| S2 | `CmdUtils.findFeature` 并入 `HudManager.getAllFeatures()`；`WurstClient.getFeatureByName` 增加 HUD 查找（根工程 v1.6.0 的 HUD 元素不是 `Feature`，不适用，后续已回退该处修改） | 66 + 66 |
| S3 | `CmdUtils.findSetting` 递归查找 `getChildren()`，支持 `父/子` 路径、别名与同名歧义报错 | 66 |

**修复前的复核结论**

- U1／U2 的 26.x 缺口确认为**工作区未提交回归**：`git show HEAD` 与 `v1.5.1` 标签的配置都包含
  `GuiMixin`／`LevelRendererMixin`／`freecam/CameraMixin`／`freecam/LocalPlayerMixin`／
  `freecam/ItemInHandRendererMixin`，当前工作区配置把它们删掉了。
- 26.x 注入目标用工程 `.gradle/loom-cache` 里的 `minecraft-merged-*.jar` 逐个 `javap -p -s` 核对：
  `Hud.extractTabList/extractTextureOverlay/extractVignette`、`LevelRenderer.render(...)`、
  `Camera.update/alignWithEntity`、`LocalPlayer.pick/isShiftKeyDown`、`ItemInHandRenderer.submitHandsWithItems`
  均存在且描述符一致（26.3 的手部渲染签名已随 26.3 适配）。
- U3 的 33 个工程与报告一致（1.21.6–1.21.11 + 26.1–26.3，三加载器各一份），
  修复后全树 0 处"只检查 `isEnabled()`"的残留。
- S1／S2／S3 的查找/持有逻辑位于共享文件，`KeybindProcessor` 有两种 MC API 变体，
  `WurstClient` 逐版本不同，均已按 hunk 级同步而非整体覆盖。

**验证**

| 检查 | 结果 |
| --- | --- |
| `fabric`、`fabric/versions/1.20.2`、`fabric/versions/1.21.6` | `compileJava --offline` 通过 |
| `neoforge`、`versions/1.20.2`、`versions/1.21.5` | `compileJava --offline` 通过 |
| 235 个改动 Java 文件（Java 21 preview 语法解析） | 0 错误 |
| 全部工程 Freecam 相机消费者扫描 | 0 个缺口 |
| 5 个 26.x 配置 JSON | 可解析、条目无重复、`defaultRequire: 1` |

**未做／保留事项**

- 26.x 工程需要 JDK 25，本机只有 17／21：26.x 只做了注入目标 `javap` 核对与语法解析，**没有实际编译**；
  建议在有 JDK 25 的环境补一次 `compileJava` 和游戏内 HUD／Freecam 验证。
- 26.x 树里的 `IngameHudMixin` 是过时类（目标是旧 `Gui.render*` API），保留在磁盘但**不注册**；
  若确认无用可后续删除。
- 其余 4 类（U3／S1／S2／S3）是行为修复，建议按原报告的触发步骤在客户端复测一遍。

---

## 追加修复：1.21.6–26.3 的 Freecam／Flight 移动输入（2026-11-07）

用户实测反馈「灵魂出窍动不了、普通飞行飞得奇慢」，定位到一个不在上述 6 类内的共享缺陷：

- **位置**：33 个工程（1.21.6–1.21.11、26.1–26.3，三加载器各一份）的
  `util/MovementPlanner.java#getMoveVector(Object)`。
- **根因**：1.21.6 起 `LocalPlayer.input` 是 `ClientInput`，按键状态在
  `input.keyPresses`（`net.minecraft.world.entity.player.Input` 记录），且
  `ClientInput` 自身就有 `public Vec2 getMoveVector()`。旧实现反射查找
  `left()/right()/forward()/backward()` 和 `forwardImpulse()`，在 `ClientInput` 上全部不存在，
  **恒返回 `(0,0)`**。
- **影响**：Freecam 水平位移恒为 0（只能原地转向/升降），Flight 的 Vanilla/Boost 水平速度恒为 0
  （只剩垂直与滑行），Speed、LongJump、Step、FastLadder、PacketFly 等同样读取该向量。
- **修复**：优先调用 `ClientInput.getMoveVector()`；退化路径读取 `keyPresses` 记录的
  `forward()/backward()/left()/right()`；再退化读取旧版 `forwardImpulse/leftImpulse` 浮点；
  反射访问统一 `trySetAccessible()`。33 个文件内容一致，整文件同步。
- **验证**：`fabric/versions/1.21.6` `compileJava` 通过；33 个文件 Java 21 语法解析 0 错误；
  独立 Java 测试（真实 `MovementPlanner` + JUnit 式断言）覆盖 vanilla／keyPresses／legacy
  三种输入形态，全部通过。

> 26.x 仍受 JDK 25 缺失限制，只做了语法解析与同源代码核对；1.21.6 与 26.3 的
> `ClientInput`／`Input` API 已用 Loom 缓存 jar 逐类 `javap` 核对一致。
