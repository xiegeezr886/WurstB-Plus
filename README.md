# WurstB+ Plus

WurstB+ Plus 是一个基于 Wurst 代码结构扩展的 Minecraft 1.20.1 客户端项目。当前源码包含战斗、移动、渲染、世界交互、GUI、命令、配置、导航和事件系统。

详细文件索引见 [PROJECT_INDEX.md](PROJECT_INDEX.md)。

## 当前状态

| 项目 | 当前值 |
| --- | --- |
| Minecraft | 1.20.1 |
| Java | 17 |
| 当前构建插件 | ForgeGradle 6.0 |
| 当前映射 | Mojang 官方映射 1.20.1 |
| 当前加载器 | Forge 47.4.10 |
| MixinExtras | Forge 0.4.1 |
| 模组 ID | `wurstpenguin` |
| 模组名称 | WurstB+ Plus |
| 开发者署名 | Penguin |
| 构建状态 | Gradle 8.11 `build` 通过 |
| 注册 Hack | 196 |
| 注册命令 | 54 |
| Other Feature | 17 |
| Forge Mixin | 69 |
| 活动 Java 文件 | 729 |

> 根目录是纯 Forge 1.20.1-47.4.10 工程。源码、Mixin 描述符、访问转换、模组元数据和构建脚本均使用 Forge/Mojmap 体系，不依赖 Architectury、Fabric Loader、Fabric API、Yarn 或 Access Widener。Gradle 8.11 完整构建和单元测试已通过，发布包内关键 Mixin 的方法可见性已经字节码核对。

## 架构概览

### 启动流程

当前入口为 `net.wurstclient.WurstForgeInitializer`。入口在 `FMLClientSetupEvent` 的主线程工作队列中调用 `WurstClient.initialize()`；HUD 由 Forge `RenderGuiEvent.Post` 单路派发，`IngameHudMixin` 只处理原版遮罩取消逻辑，避免序号注入失效或重复渲染。初始化顺序如下：

1. 创建客户端配置目录。
2. 初始化事件管理器和功能冲突管理器。
3. 创建 Hack、命令和 Other Feature 注册表。
4. 加载设置、启用状态、按键、好友和 GUI 主题。
5. 初始化 DelayQueue、InventoryActionQueue、MacroManager、WaypointsManager、ProxyManager、AddonManager。
6. 注册客户端指标、实体快照与 HUD 元素（FPS/Coords/Ping/TPS/Speed/Server/Clock/Armor/Inventory/Potion/Combo/Keystrokes/Target HUD）。
7. 启动 Discord RPC 和 Macro 管理器。

核心入口：

- `src/main/java/net/wurstclient/WurstForgeInitializer.java`
- `src/main/java/net/wurstclient/WurstClient.java`
- `src/main/java/net/wurstclient/hack/HackList.java`
- `src/main/java/net/wurstclient/command/CmdList.java`
- `src/main/java/net/wurstclient/other_feature/OtfList.java`

### 事件系统

`EventManager` 以监听器接口类型维护订阅列表，同时支持 `@WurstSubscribe` 注解 + LambdaMetafactory 直接方法调用订阅。Mixin 将 Minecraft 生命周期、输入、网络、渲染和移动节点桥接为 Wurst 事件。事件继承分发使订阅 `PacketEvent` 可同时接收 `Read`/`Send` 子事件。

典型调用链：

```text
Minecraft / Mixin hook
  -> EventManager.fire(...)
  -> Interface Listener + LambdaMetafactory Subscriber
  -> Hack state update / packet action / rendering
```

主要位置：

- `src/main/java/net/wurstclient/event/`
- `src/main/java/net/wurstclient/events/`
- `src/main/java/net/wurstclient/mixin/`
- `src/main/java/net/wurstclient/mixinterface/`

### 设置与配置

所有功能设置继承 `Setting`。当前基础设施支持：

- 设置旧名称别名。
- 条件可见性。
- 任意深度设置树（父级、深度、单一归属、循环检查和递归折叠渲染）。
- 设置变化监听器。
- 单项配置损坏隔离。
- 临时文件写入和原子替换（`JsonUtils.toJson` 使用 `ATOMIC_MOVE`）。
- 设置/按键/启用Hack/TooManyHax 配置档案加载与保存。
- ClickGUI 配置列表可直接选择、加载、覆盖保存、刷新和打开目录。
- GUI 独立偏好保存到 `wurst/gui-preferences.json`，自定义字体放入 `wurst/fonts`。

主要位置：

- `src/main/java/net/wurstclient/settings/Setting.java`
- `src/main/java/net/wurstclient/settings/SettingsFile.java`
- `src/main/java/net/wurstclient/util/json/JsonUtils.java`
- `src/main/java/net/wurstclient/clickgui2/SettingsWindow.java`

## 新吸收功能

### 架构升级

- **LambdaMetafactory 事件分发**：`@WurstSubscribe` 注解方法通过 LambdaMetafactory 生成 `Consumer<Event>`，直接方法调用，消除反射开销。
- **事件继承分发**：订阅父事件类可接收其所有子类事件（如订阅 `PacketEvent` 接收 `Read`/`Send`）。
- **Levenshtein 模糊搜索**：ClickGUI / Navigator 输入错拼仍可匹配功能名和搜索标签。
- **智能绑定**：支持 TOGGLE（默认，`killaura`）、HOLD（按住激活，`+killaura`）、SMART（智能切换，`~killaura`）。
- **延迟操作队列**：`DeferredActionQueue` 按命名队列跨 tick 执行延迟操作。
- **分层设置树**：`Setting.withChildren()` 支持任意深度、父子归属与循环检查，ClickGUI 和 Navigator 均递归渲染。
- **稳定设置绑定**：右 Ctrl 浮窗与右 Shift Navigator 仅在设置展开层级或条件可见性改变时重建布局；滑块、复选框和枚举的普通数值变化保持原组件实例，拖动与弹层会持续写回模块设置。
- **系统凭据保护**：Windows Credential Manager、macOS Keychain 或 Linux Secret Service 只保存随机主密钥；账号文件使用带随机 nonce 的 AES-GCM，并自动读取迁移旧 AES/CFB8 数据。
- **共享旋转仲裁**：`RotationQueue` 按后台、移动、方块放置、战斗和紧急等级仲裁静默旋转，同级请求按最近提交顺序执行；`RotationFaker` 参照 LiquidBounce RotationManager 独立维护当前旋转与已成功发送的服务端旋转，Mixin 仅替换原版 `LocalPlayer.sendPosition()/tick()` 内部的角度 getter 返回值，不写入玩家真实角度、不额外构造旋转包。第一人称相机与移动输入保持客户端方向，第三人称模型仅在静默旋转生效时插值显示服务端方向。
- **库存动作队列**：`InventoryActionQueue` 按菜单 ID、状态验证、优先级和所有者调度原子点击链；AutoTotem 与 AutoArmor 不再留下跨 Tick 光标状态。

### 子系统

- **Macros 宏系统**：`.macros add/remove/list`，按键触发命令序列，支持 `_delay:N` tick 延迟。
- **Waypoints 路径点**：`.waypoints add/remove/list`，3D 十字标记渲染，跨维度持久化。
- **Proxy 代理系统**：`.proxy add/remove/set/clear/list`，通过 Netty 管线为新建服务器连接注入 SOCKS4/SOCKS5 处理器，不修改 JVM 全局代理属性。
- **Addon 扩展系统**：`WurstAddon` + `AddonManager`，ServiceLoader 发现，拒绝 Hack/Command 名称冲突，初始化失败不会残留已加载状态。
- **Brigadier 命令**：`BrigadierCommand` 基类，通过 Forge `RegisterClientCommandsEvent` 注册到客户端 `CommandDispatcher`。
- **Discord RPC**：仅显示单人/多人/主菜单状态、客户端版本和活动 Hack 数量，不泄露服务器地址；IPC 在独立单线程执行器中更新。

### 新增 Hacks (15)

| Hack | 分类 | 功能 |
|------|------|------|
| **AntiBot** | Combat | 检测反作弊假人（Ping/隐身/UUID），自动过滤战斗目标 |
| **BossStack** | Render | Boss 血量条堆叠紧凑渲染 |
| **Breadcrumbs** | Render | 玩家移动轨迹线（渐变色） |
| **DankBobbing** | Fun | 视角晃动 + 移动 bob 增强 |
| **EntityCulling** | Render | 异步 GPU 遮挡查询，跳过被方块完全遮挡的实体 |
| **LightOverlay** | Render | 低亮度刷怪区域黄色覆盖层 |
| **LogoutSpots** | Render | 玩家离线位置红色标记 |
| **Notebot** | Fun | 解析旧版及 NBS v1-v5，按 tick/和弦/音色扫描并播放音符盒歌曲 |
| **PacketCanceller** | Other | 选择性取消 5 种数据包 |
| **PacketLogger** | Other | 网络收发包日志（限流） |
| **PlayerHalo** | Render | 在可见玩家头顶批量绘制跟随主题色的小光环 |
| **PopChams** | Render | Totem 触发时彩色方块升起动画 |
| **TargetShader** | Render | 将当前战斗目标送入独立 FBO，统一执行 Outline/Pulse/Gradient/Smoke 后处理 |
| **Twerk** | Fun | 快速下蹲/起立舞蹈 |
| **Vomit** | Fun | 快速使用食物（呕吐效果） |

### 新增 Commands (3)

| 命令 | 功能 |
|------|------|
| `.macros` | 宏管理（add/remove/list） |
| `.waypoints` | 路径点管理（add/remove/list） |
| `.proxy` | 代理管理（add/remove/set/clear/list） |

### HUD 元素

- `HudManager` 统一完成元素渲染、对齐、生命周期和布局持久化，不再让每个元素单独注册 GUI 事件。
- **FPS / Coordinates / Ping / TPS / Speed / Server / Clock**：独立文本型状态元素；坐标项支持主世界与下界 1:8 换算，TPS 使用时间同步包间隔平滑采样。
- **Armor**：按实际物品图标和耐久信息绘制四件护甲。
- **Potion Effects**：按名称排序显示效果、等级和剩余时间。
- **Combo**：通过 `MultiPlayerGameMode.attack()` 入口 Mixin 监听本地玩家攻击，按目标与三秒窗口统计连续攻击。
- **Keystrokes**：显示实际绑定的 WASD、空格、潜行、疾跑和左右鼠标键，包含原版紧凑样式、按压动画与一秒 CPS 统计；启用后在 HUD 编辑器中直接显示可拖动本体，不再显示占位卡片。
- **Inventory**：显示主背包 `9–35` 槽的 3×9 物品网格，包含数量和耐久叠加；采用与 TargetHUD 一致的深色圆角、细描边及主题色侧条，默认关闭，可在 HUD 编辑器中启用、预览、拖动和切换锚点。
- **Target HUD**：保留 Raven 式攻击目标保持/淡出，显示玩家头像、名称和主题色动画生命条，并在目标持有主手物品或穿戴盔甲时按主手、头盔、胸甲、护腿、靴子顺序显示紧凑装备栏；全空时不渲染装备栏。卡片使用与通知 HUD、HackList 统一的半透明黑底、弱白描边和左侧主题色条，目标来源覆盖最近攻击、Killaura、MultiAura 与准星指向玩家。
- **Minimap**：提供北方固定的纯圆形地形小地图，将已加载区块按 `16x16` 瓦片缓存并在每 Tick 预算内渐进刷新，再合成为动态纹理；显示区块网格、地图内方位、玩家、好友、生物与掉落物，不再绘制地图下方的坐标、维度、生物群系和朝向文字。默认关闭，不会为绘图强制加载区块，静止时也会按瓦片 TTL 更新地形变化。
- **HackList / Logo / Notifications**：纳入统一布局和启用状态管理，兼容现有 `hud-layout.json`；通知卡片底部使用与严重级别一致的 3 秒进度条，读满后进入淡出并从队列移除；HUD2 的 Logo 开关不再被旧版 `Visibility` 设置二次隐藏。
- HUD 编辑器恢复固定信息卡片交互，卡片显示元素名称、ON/OFF 与锚点，使用 `96x44` 命中区域拖动和切换；小地图启用后使用真实尺寸和实时地图预览进行命中、拖动、锚定与边界限制。

### 高级功能

- **点击模式**：`ClickPattern` 按 LiquidBounce 原始 `fill(IntArray)` 语义提供 Stabilized/Efficient/Spamming/DoubleClick/Drag/Butterfly/NormalDistribution 七种技术；`RollingClickArray` 和 `CombatClickScheduler` 使用两个交替的 20 Tick 周期，支持单 Tick 多点击、每秒强制点击检查、原版空挥冷却及 `0..2` 未截断物品冷却阈值。
- **高级瞄准**：`RotationSmoothing`（Linear/EaseInOut/Factor/Instant），供 KillAura/CrystalAura 使用。

## 已重构机制

### 战斗链路

- `AttackSpeedSliderSetting` 使用 `System.nanoTime()`，不再按固定 20 TPS 递减计时。
- `CombatTargetUtils` 使用眼睛到实体 AABB 最近点距离，统一 FOV、过滤器、LOS、距离/角度/生命/受伤时间优先级和稳定排序。
- `EntityUtils.IS_ATTACKABLE` 自动过滤 AntiBot 检测到的假人。
- Hitboxes 仅扩展客户端世界中的非本地目标实体；单人集成服务器的玩家和生物碰撞箱保持原版尺寸，避免步行碰撞结果不一致导致减速与位置回弹。
- Hitboxes 与客户端连接方向判定已移入 `net.wurstclient.util` 普通策略类；Mixin 本体不再暴露包级静态辅助方法，Mixin 专用包也不再混入普通类，兼容 Forge 47.4.10 内置 Mixin 0.8.5 的方法可见性与包隔离校验。
- KillAura 已替换为 LiquidBounce Nextgen 的 Forge 1.20.1 行为等价执行链：3 格原版距离加成、随机扫描距离、交互/穿墙距离分离、HurtTime 与实体过滤、目标及自身运动预测、预测 AABB 命中点采样、Normal/Snap/OnTick 旋转时序、Enemy/All Raycast、逐 Tick 点击数组与未截断物品冷却、Click/Weapon/EmptyHand/VanillaName/NotBreaking 条件、库存模拟关闭、随机 FailSwing 范围、Basic/Interact/Fake 格挡模式、三种解锁方式、随机重挡/暂停、危险判定、KeepSprint、AutoSword/Criticals 协同和每次点击前复验。可关闭的 `Range aura` 使用当前主题色显示实际交互距离，距离设置变化会实时缩放渐变环带。
- CrystalAura 每 Tick 最多执行一次引爆或放置，按最低目标伤害、自伤上限、伤害优势、反自杀和水晶年龄统一评分；无效水晶不再阻塞后续放置。
- ScaffoldWalk 使用 `PlacementPlan` 预测水平落点，对目标位置、支撑面、视线、距离和连续性评分，再通过方块放置优先级旋转执行。
- AutoTotem 使用单次 SWAP 原子交换副手；AutoArmor 支持低耐久保护、鞘翅保持、绑定诅咒过滤和队列化换装。
- AnchorAura 使用 PLACE、CHARGE、DETONATE 单步状态机，等待服务端方块状态确认并按目标伤害、自伤上限和反自杀条件规划锚点。
- `DamageUtils` 区分末影水晶与重生锚爆炸威力，并统一计算难度、护甲韧性、爆炸保护和抗性效果减伤。
- MultiAura 已替换为 FDPClient `KillAura TargetMode=Multi` 的 Forge 1.20.1 行为等价链，并复用 LiquidBounce Nextgen 的双 20 Tick 点击器与未截断物品冷却：按 Type + 12 种 FDP 优先级选择主目标，支持随机攻击距离、扫描/穿墙/疾跑距离修正、目标与自身预测、Normal/Snap/OnTick、Normal/Strict Raytrace、RaycastIgnored、0=无限目标、逐点击世界顺序多目标复验、库存模拟关闭、FailSwing、KeepSprint，以及 Packet/Fake AutoBlock、Stop/Switch/Empty 解锁和 SmartBlock 条件。Packet AutoBlock 仅发送预测序列包并单独维护服务端格挡状态，不再进入客户端使用物品状态或阻塞移动输入；Killaura 与 MultiAura 均区分真实 `1..10` Tick 挥空冷却和 GUI 使用的 `missTime=10000` 哨兵，打开界面不会停止攻击。MultiAura 的 `Range aura` 显示 `Range + Scan range` 与穿墙距离中的真实最大攻击范围。
- ClickAura 统一长按与点击计时，并避免辅助攻击后继续执行同次原版攻击。
- TriggerBot 接入共享目标验证。
- NoVelocity 已替换为 LiquidBounce GPL 模式化实现，提供 Modify/JumpReset、概率与地面状态触发、移动/液体/鞘翅过滤、当前动量保留和负数反向击退；爆炸击退使用 Mixin accessor，不再依赖反射字段名。

本轮优先替换的核心模块：

| 模块 | 当前实现 |
| --- | --- |
| Killaura | LiquidBounce Nextgen Forge 1.20.1 行为等价移植：RollingClickArray、七种原始点击数组算法、ItemCooldown、随机扫描范围、预测命中点、三种旋转时序、Raycast、Requirements、库存模拟关闭、FailSwing、AutoBlock 状态机、KeepSprint 和逐点击复验；上游 Mace、1.21.4 sword blocking、ElytraTarget 及跨协议 Blink 分支在 1.20.1 无对应协议能力 |
| KillauraLegit | 共享目标评分与 AntiBot 过滤、客户端鼠标平滑转向、黏性目标和执行前复验 |
| AimAssist | 共享目标验证、水平/垂直轴控制、四种平滑曲线、目标切换优势阈值 |
| TriggerBot | 原版冷却阈值、武器限制、攻击键条件、使用物品策略和共享目标过滤 |
| Criticals | 攻击包发送前 Mixin 事件、六种 Packet profile、NoGround/Jump/MiniJump、状态与冷却检查、显式停止冲刺包 |
| Reach | 实体与方块距离分离，先按最大距离射线，再按命中类型裁剪结果 |
| NoVelocity | LiquidBounce Modify/JumpReset、概率与触发条件、动量保留、爆炸包 accessor；AntiKnockback 已合并为搜索别名 |
| AutoArmor | 确定性最大收益换装、耐久/绑定诅咒/鞘翅保护、共享库存动作队列 |
| AutoTotem | 单次 SWAP 副手交换、生命与吸收阈值、库存状态复验和动作队列 |
| AntiBot | 每 Tick 重算的组合谓词，覆盖 Tab 信息、游戏模式、延迟、地面、隐身、UUID、非法状态与重复名称 |

### 移动链路

九个常用移动模块已重构，并统一适配 Forge 1.20.1 Mojmap：

| 模块 | 当前实现 |
| --- | --- |
| AutoSprint | 全方向疾跑、饥饿/碰撞/物品使用/潜行/失明条件控制；在原版疾跑判定完成、实际移动计算前使用本 Tick 输入应用状态，只停止模块自身开启的疾跑 |
| KeepSprint | 向前移动期间每 Tick 维持疾跑，不再依赖攻击事件；攻击时在 `Player.attack` 内将原版水平速度倍率由 `0.6` 替换为 `1.0`，并阻止该次 `setSprinting(false)`，不再事后补发疾跑包；纯策略位于普通工具包，Mixin 内只保留私有注入方法 |
| SpeedHack | NCP Bhop、Strafe、LowHop、OnGround、Brutal 五种模式；共享八向速度规划、空中平滑转向与水平限速，LowHop 只限制自身跳跃阶段 |
| Flight | Vanilla、Boost、Rocket 三种模式；独立水平/垂直速度、滑翔、周期 AntiKick，跨世界恢复创造飞行状态且不干预旁观模式 |
| NoFall | OnGround、Position、Smart、GroundSpoof 四种模式；达到跌落阈值后才发包，并只在数据包确认发送且仍为落地状态后清零本地距离 |
| NoSlowdown | 按物品动作区分盾牌与普通使用，覆盖六类减速方块，并重算主手物品的负移动速度属性 |
| Step | Simple 一至五格步高与 Legit 分段位置包；保存/恢复原步高，并限制碰撞、液体、跳跃和执行冷却 |
| SafeWalk | 地面/跳跃条件控制、边缘裁剪和可选可见潜行，释放时恢复真实键盘状态 |
| ScaffoldWalk | 预测落点评分、视线与支撑面检查、最大方块堆选择、静默换槽恢复、交互结果确认、内置 SafeWalk 和 Tower 模式 |
| InvWalk | 统一识别 ClickGUI、Navigator、全部设置子页、HUD 编辑器和客户端选项页；这些页面不暂停单人集成服务器，并按真实键盘状态恢复移动、跳跃、潜行与疾跑输入 |

### ESP 与视觉

- `EntityEspRenderer` 统一实体插值、填充、描边、追踪线和颜色批处理。
- `EntitySnapshotManager` 每 Tick 在客户端线程复制并发布不可变实体、玩家、生物和掉落物列表；PlayerESP、MobESP、ItemESP 不再分别遍历世界实体集合。
- `PostEffectQueue` 按效果分组目标渲染任务，使用 Forge/Mojmap `TextureTarget + PostChain` 处理后以 Alpha 合成回主目标，不依赖 Satin。
- `TargetShader` 提供 Outline、Pulse、Gradient、Smoke 四种目标视觉模式，并支持是否穿墙。
- PlayerESP 支持 3D 批处理与 2D 八角 AABB 屏幕投影；2D 模式统一批次绘制边框、填充、生命条和平均护甲耐久条。
- PlayerHalo 使用实体快照批量绘制主题色头顶光环，第一人称隐藏自身、第三人称显示自身，并保持正常深度遮挡。
- MobESP 支持距离、生命值、自定义颜色、最大距离、透明度、近距离平滑淡出及深度模式。
- NameTags 每 Tick 提取玩家名称、生命、延迟、实体 ID、装备副本和耐久为不可变 `NameTagRenderState`，渲染阶段直接使用快照并可绘制装备与耐久。
- ItemESP 支持最大距离、无效实体过滤、近距离平滑淡出、填充和深度模式。
- ChestESP 和 PortalESP 支持填充/线条透明度及穿墙切换。
- PortalESP 已修复关闭一个分组后错误终止后续分组渲染的问题。
- ClickGUI 使用 `FlatTheme`、`FlatRenderer` 和 `FlatUiRenderer` 绘制实心面板、控件、滑轨、圆角与阴影。
- Radar 作为 ClickGUI 的持久固定窗口注册；启用后可在游戏 HUD 中显示，并在右 Ctrl/右 Shift 菜单内复用同一窗口。内容区使用与 ClickGUI 一致的深黑底、主题色描边与分隔线、圆形扫描环、主题中心指针、圆角实体标记及底部半径/目标计数栏；标题栏固定按钮使用内嵌 SVG 源生成的抗锯齿图钉纹理，固定状态跟随主题色，菜单重建不会再丢失雷达。
- Minecraft 主界面在原版动态全景背景上使用 Raven 式深色中性视觉。
- 两套 GUI 入口：右 Ctrl → BleachHack 式浮窗 ClickGUI，右 Shift → Sinka 风格 Navigator；二者作为瞬时菜单入口不参与普通模块启停通知，打开时只显示一条 `Info` 通知。

### 性能优化

- 高频事件派发使用监听器变更时快照（COW）+ `LongAdder` 计数。
- `RenderScope` 为每个 3D/GUI 监听器恢复 blend、depth、cull、line width、shader、framebuffer、viewport 和共享 `BufferBuilder` 状态，隔离异常渲染污染。
- LambdaMetafactory 注解订阅消除反射扫描开销。
- GUI 功能集合和单行说明按注册数量缓存。
- ClickGUI 与 Navigator 共用相关度模糊搜索：支持英文名、中文显示名、标签、描述和设置名，兼容 CamelCase、缩写、子序列及常见拼写交换；短关键词不启用宽松纠错，多关键词必须全部命中。
- `EntityCulling` 使用跨帧 `GL_SAMPLES_PASSED` 查询，仅读取已就绪结果；查询包围盒复用单个静态 VBO。
- `AsyncTextureLoader` 提供有界后台文件解码、渲染线程纹理注册和每线程直接缓冲；当前保留为文件纹理 API，不宣称替代 Minecraft 的通用资源加载管线。
- Search、Tunneller、MobSpawnESP 和实体遮挡包围盒均复用静态 VBO。
- Forge/Mojmap 1.20.1 的 `setLevel`/`clearLevel` 不包含 `System.gc()`；因此没有添加无效且可能破坏紧急内存恢复路径的 GC Mixin。
- 已删除 GUI 的 Dual-Kawase/液态玻璃管线；`TargetShader` 只按实际使用的效果懒加载并复用独立 RenderTarget。
- 圆角填充继续使用浮点圆弧与透明羽化边缘。

## 纯 Forge 架构

1. ForgeGradle 6.0 和 Forge 47.4.10 提供构建、开发运行配置与重混淆任务。
2. 全部 Minecraft 源码符号使用 Mojang 1.20.1 官方映射。
3. `WurstForgeInitializer` 接入 `FMLClientSetupEvent`、`RegisterClientCommandsEvent` 和 `RenderGuiEvent.Post`；攻击事件由 `ClientPlayerInteractionManagerMixin` 在攻击包发送前派发。
4. `META-INF/accesstransformer.cfg` 提供 32 条 Forge 原生访问转换。
5. MixinGradle 生成 `wurstpenguin-refmap.json`，MixinExtras Forge 通过 JarJar 打包。
6. `PlatformUtils` 直接调用 Forge `ModList` 和 `FMLLoader`，不包含跨加载器反射。
7. `ClientConnectionMixin` 仅为接收方向为 `CLIENTBOUND` 的客户端连接派发 Wurst 收发包事件；单人集成服务器的 `SERVERBOUND` 连接不会进入客户端模块事件链。

## 构建与运行

构建环境需要 Java 17。项目自带 Gradle 8.11 Wrapper，不依赖系统 Gradle：

```powershell
.\gradlew.bat build --console=plain
```

可部署 Forge JarJar 产物位于：

```text
build/libs/WurstB+ Plus-v1.5.0-MC1.20.1-all.jar
```

启动 Forge 1.20.1 开发客户端：

```powershell
.\gradlew.bat runClient --console=plain
```



## 验证状态

项目版本为 `1.5.0`。50 个测试文件中的 133 项单元测试全部通过，Gradle `build`、Forge 重混淆、JarJar 打包、关键 Mixin 方法可见性和 Mixin 包隔离检查成功。`PlayerMixin` 的 KeepSprint 攻击注入使用原生 `@Redirect`，不依赖 Mixin 运行时生成的 `Args` 类，可避免 Forge 47.4.10 + Java 21 启动时的 `Args$1` 类加载崩溃。发布产物为 `build/libs/WurstB+ Plus-v1.5.0-MC1.20.1-all.jar`（29,094,464 bytes），本轮 SHA-256 为 `0B70C7DB27FBBA84D7801F7BEA0E74022043C49D67E92A2818E762C77AE9CED1`。


WurstB+ Plus is a Minecraft 1.20.1 client project built upon the Wurst codebase structure. The current source code encompasses combat, movement, rendering, world interaction, GUI, commands, configuration, navigation, and event systems.

For a detailed file index, see [PROJECT_INDEX.md](PROJECT_INDEX.md).

## Current Status

| Item | Value |
| --- | --- |
| Minecraft | 1.20.1 |
| Java | 17 |
| Current Build Plugin | ForgeGradle 6.0 |
| Current Mappings | Mojang Official Mappings 1.20.1 |
| Current Loader | Forge 47.4.10 |
| MixinExtras | Forge 0.4.1 |
| Mod ID | `wurstpenguin` |
| Mod Name | WurstB+ Plus |
| Developer Credit | Penguin |
| Build Status | Gradle 8.11 `build` Passed |
| Registered Hacks | 196 |
| Registered Commands | 54 |
| Other Features | 17 |
| Forge Mixins | 69 |
| Active Java Files | 729 |

> The root directory is a pure Forge 1.20.1-47.4.10 project. Source code, Mixin descriptors, access transformers, mod metadata, and build scripts all use the Forge/Mojmap system, with no dependency on Architectury, Fabric Loader, Fabric API, Yarn, or Access Widener. The full Gradle 8.11 build and unit tests have passed; key Mixin method visibilities in the release package have been verified against bytecode.

## Architecture Overview

### Startup Process

The current entry point is `net.wurstclient.WurstForgeInitializer`. The entry calls `WurstClient.initialize()` within the main thread work queue of `FMLClientSetupEvent`; HUD is dispatched via a single Forge `RenderGuiEvent.Post` route, with `IngameHudMixin` handling only the vanilla overlay cancellation logic to avoid sequence injection failures or duplicate rendering. The initialization order is as follows:

1. Create client configuration directory.
2. Initialize event manager and feature conflict manager.
3. Create Hack, Command, and Other Feature registries.
4. Load settings, enabled states, keybinds, friends, and GUI themes.
5. Initialize DelayQueue, InventoryActionQueue, MacroManager, WaypointsManager, ProxyManager, AddonManager.
6. Register client metrics, entity snapshots, and HUD elements (FPS/Coords/Ping/TPS/Speed/Server/Clock/Armor/Inventory/Potion/Combo/Keystrokes/Target HUD).
7. Start Discord RPC and Macro manager.

Core entry points:

- `src/main/java/net/wurstclient/WurstForgeInitializer.java`
- `src/main/java/net/wurstclient/WurstClient.java`
- `src/main/java/net/wurstclient/hack/HackList.java`
- `src/main/java/net/wurstclient/command/CmdList.java`
- `src/main/java/net/wurstclient/other_feature/OtfList.java`

### Event System

`EventManager` maintains subscription lists by listener interface type, while also supporting `@WurstSubscribe` annotations with LambdaMetafactory direct method invocation subscriptions. Mixins bridge Minecraft lifecycle, input, network, rendering, and movement nodes into Wurst events. Event inheritance dispatch allows a subscription to `PacketEvent` to receive both `Read`/`Send` sub-events.

Typical call chain:

```text
Minecraft / Mixin hook
  -> EventManager.fire(...)
  -> Interface Listener + LambdaMetafactory Subscriber
  -> Hack state update / packet action / rendering
```

Primary locations:

- `src/main/java/net/wurstclient/event/`
- `src/main/java/net/wurstclient/events/`
- `src/main/java/net/wurstclient/mixin/`
- `src/main/java/net/wurstclient/mixinterface/`

### Settings and Configuration

All feature settings inherit from `Setting`. The current infrastructure supports:

- Setting old-name aliases.
- Conditional visibility.
- Arbitrarily deep setting trees (parent, depth, single ownership, cycle checking, and recursive folding rendering).
- Setting change listeners.
- Isolated corruption handling for individual config entries.
- Temporary file writing and atomic replacement (`JsonUtils.toJson` uses `ATOMIC_MOVE`).
- Loading and saving of settings/keybinds/enabled hacks/TooManyHax configuration profiles.
- ClickGUI configuration list supporting selection, loading, overwrite saving, refresh, and opening directory.
- Independent GUI preferences saved to `wurst/gui-preferences.json`; custom fonts placed in `wurst/fonts`.

Primary locations:

- `src/main/java/net/wurstclient/settings/Setting.java`
- `src/main/java/net/wurstclient/settings/SettingsFile.java`
- `src/main/java/net/wurstclient/util/json/JsonUtils.java`
- `src/main/java/net/wurstclient/clickgui2/SettingsWindow.java`

## New Absorbed Features

### Architecture Upgrades

- **LambdaMetafactory Event Dispatch**: `@WurstSubscribe` annotated methods generate `Consumer<Event>` via LambdaMetafactory for direct method invocation, eliminating reflection overhead.
- **Event Inheritance Dispatch**: Subscribing to a parent event class receives all its subclasses (e.g., subscribing to `PacketEvent` receives `Read`/`Send`).
- **Levenshtein Fuzzy Search**: ClickGUI / Navigator still matches feature names and search tags even with typo input.
- **Smart Binding**: Supports TOGGLE (default, `killaura`), HOLD (hold activation, `+killaura`), SMART (smart switching, `~killaura`).
- **Deferred Action Queue**: `DeferredActionQueue` executes delayed operations across ticks by named queue.
- **Hierarchical Setting Tree**: `Setting.withChildren()` supports arbitrary depth, parent-child ownership, and cycle checking; both ClickGUI and Navigator render recursively.
- **Stable Setting Bindings**: Right Ctrl popup and Right Shift Navigator rebuild layout only when setting expansion levels or conditional visibility change; normal value changes for sliders, checkboxes, and enums preserve component instances, with drag and popup layers continuously writing back to module settings.
- **System Credential Protection**: Windows Credential Manager, macOS Keychain, or Linux Secret Service stores only a random master key; account files use AES-GCM with a random nonce and automatically read and migrate legacy AES/CFB8 data.
- **Shared Rotation Arbitration**: `RotationQueue` arbitrates silent rotations by background, movement, block placement, combat, and emergency priority levels; same-level requests execute in most-recent submission order. `RotationFaker`, referencing LiquidBounce's RotationManager, independently maintains the current rotation and the successfully sent server rotation; Mixins only replace angle getter return values within vanilla `LocalPlayer.sendPosition()/tick()` internals, without writing to the player's real angles or constructing additional rotation packets. First-person camera and movement input retain client-side orientation; third-person model interpolates to show server-side direction only when silent rotation is active.
- **Inventory Action Queue**: `InventoryActionQueue` schedules atomic click chains by menu ID, state validation, priority, and owner; AutoTotem and AutoArmor no longer leave cross-tick cursor states.

### Subsystems

- **Macros System**: `.macros add/remove/list`, key-triggered command sequences supporting `_delay:N` tick delays.
- **Waypoints**: `.waypoints add/remove/list`, 3D cross marker rendering with cross-dimension persistence.
- **Proxy System**: `.proxy add/remove/set/clear/list`, injects SOCKS4/SOCKS5 handlers into new server connections via Netty pipeline without modifying JVM global proxy properties.
- **Addon Extension System**: `WurstAddon` + `AddonManager`, discovered via ServiceLoader, rejects Hack/Command name conflicts; failed initialization does not leave partially loaded state.
- **Brigadier Commands**: `BrigadierCommand` base class, registered to client-side `CommandDispatcher` via Forge `RegisterClientCommandsEvent`.
- **Discord RPC**: Displays only singleplayer/multiplayer/main menu status, client version, and active Hack count; does not leak server addresses; IPC updates in a dedicated single-thread executor.

### New Hacks (15)

| Hack | Category | Function |
|------|------|------|
| **AntiBot** | Combat | Detects anti-cheat bots (Ping/Invisible/UUID), automatically filters combat targets |
| **BossStack** | Render | Compact stacking rendering of boss health bars |
| **Breadcrumbs** | Render | Player movement trail lines (gradient colors) |
| **DankBobbing** | Fun | View bobbing + enhanced movement bob |
| **EntityCulling** | Render | Asynchronous GPU occlusion queries, skips entities completely occluded by blocks |
| **LightOverlay** | Render | Yellow overlay for low-light spawnable areas |
| **LogoutSpots** | Render | Red markers for player logout positions |
| **Notebot** | Fun | Parses legacy and NBS v1-v5, scans and plays note block songs by tick/chord/timbre |
| **PacketCanceller** | Other | Selectively cancels 5 packet types |
| **PacketLogger** | Other | Network packet send/receive logging (rate-limited) |
| **PlayerHalo** | Render | Batch draws small halo rings matching theme color above visible players |
| **PopChams** | Render | Colorful box ascending animation when Totem triggers |
| **TargetShader** | Render | Sends current combat target to independent FBO for unified Outline/Pulse/Gradient/Smoke post-processing |
| **Twerk** | Fun | Rapid crouch/stand dance |
| **Vomit** | Fun | Rapid food consumption (vomit effect) |

### New Commands (3)

| Command | Function |
|------|------|
| `.macros` | Macro management (add/remove/list) |
| `.waypoints` | Waypoint management (add/remove/list) |
| `.proxy` | Proxy management (add/remove/set/clear/list) |

### HUD Elements

- `HudManager` uniformly handles element rendering, alignment, lifecycle, and layout persistence; elements no longer register GUI events individually.
- **FPS / Coordinates / Ping / TPS / Speed / Server / Clock**: Independent text-based status elements; coordinate item supports 1:8 conversion between Overworld and Nether; TPS uses smooth sampling from time synchronization packet intervals.
- **Armor**: Renders four armor pieces with actual item icons and durability information.
- **Potion Effects**: Displays effects, levels, and remaining time sorted by name.
- **Combo**: Listens for local player attacks via `MultiPlayerGameMode.attack()` Mixin entry, counts consecutive attacks by target over a three-second window.
- **Keystrokes**: Displays actual bound WASD, space, sneak, sprint, and left/right mouse buttons, with vanilla compact style, press animation, and one-second CPS statistics; when enabled, shows the draggable element directly in HUD editor, no longer showing placeholder cards.
- **Inventory**: Displays main inventory slots `9–35` in a 3×9 item grid, including quantity and durability overlays; uses consistent dark rounded corners, thin outlines, and theme-colored side bars as TargetHUD; disabled by default, can be enabled, previewed, dragged, and anchor-switched in HUD editor.
- **Target HUD**: Retains Raven-style attack target hold/fade, displays player avatar, name, and theme-color animated health bar, and shows compact equipment bar in order of main hand, helmet, chestplate, leggings, boots when the target holds an item in main hand or wears armor; equipment bar is omitted when completely empty. Cards use uniform semi-transparent black background, weak white outline, and left theme-color stripe consistent with Notification HUD and HackList; target sources cover most recent attack, Killaura, MultiAura, and crosshair-pointing players.
- **Minimap**: Provides a north-fixed pure circular terrain minimap, caching loaded chunks as `16x16` tiles and progressively refreshing within tick budget, then compositing into a dynamic texture; displays chunk grid, in-map orientation, players, friends, mobs, and dropped items, no longer drawing coordinates, dimension, biome, or directional text below the map. Disabled by default, does not force chunk loading for drawing, updates terrain changes by tile TTL even when stationary.
- **HackList / Logo / Notifications**: Integrated into unified layout and enablement management, compatible with existing `hud-layout.json`; notification cards show a 3-second progress bar at bottom matching severity level, fading out and removed from queue when full; HUD2 Logo toggle is no longer secondarily hidden by legacy `Visibility` setting.
- HUD editor restores fixed information card interaction; cards display element name, ON/OFF, and anchor, using `96x44` hit areas for dragging and toggling; when minimap is enabled, uses actual size and real-time map preview for hit testing, dragging, anchoring, and boundary constraints.

### Advanced Features

- **Click Patterns**: `ClickPattern` provides Stabilized/Efficient/Spamming/DoubleClick/Drag/Butterfly/NormalDistribution seven techniques per LiquidBounce's original `fill(IntArray)` semantics; `RollingClickArray` and `CombatClickScheduler` use two alternating 20-tick cycles, supporting multi-click per tick, per-second click enforcement, vanilla swing cooldown, and `0..2` uncapped item cooldown thresholds.
- **Advanced Aiming**: `RotationSmoothing` (Linear/EaseInOut/Factor/Instant), available for KillAura/CrystalAura.

## Refactored Mechanisms

### Combat Pipeline

- `AttackSpeedSliderSetting` uses `System.nanoTime()`, no longer counts down on a fixed 20 TPS basis.
- `CombatTargetUtils` uses eye-to-entity AABB closest point distance, unifying FOV, filters, LOS, distance/angle/health/hurt-time priority, and stable sorting.
- `EntityUtils.IS_ATTACKABLE` automatically filters bots detected by AntiBot.
- Hitboxes only extend non-local target entities in the client world; single-player integrated server player and mob collision boxes remain vanilla size to avoid inconsistent walking collision results causing slowdown and position rebound.
- Hitboxes and client connection direction determination have been moved to ordinary strategy classes in `net.wurstclient.util`; Mixin classes no longer expose package-level static helper methods, and the dedicated Mixin package no longer mixes into ordinary classes, compatible with Forge 47.4.10 built-in Mixin 0.8.5's method visibility and package isolation checks.
- KillAura has been replaced with a Forge 1.20.1 behaviorally equivalent execution chain from LiquidBounce Nextgen: 3-block vanilla distance bonus, random scan distance, interaction/wall distance separation, HurtTime and entity filtering, target and self-motion prediction, predicted AABB hit point sampling, Normal/Snap/OnTick rotation timing, Enemy/All Raycast, per-tick click array with uncapped item cooldown, Click/Weapon/EmptyHand/VanillaName/NotBreaking conditions, inventory simulation closing, random FailSwing range, Basic/Interact/Fake blocking modes, three unlock methods, random re-block/pause, danger detection, KeepSprint, AutoSword/Criticals synergy, and re-validation before each click. Optional `Range aura` displays actual interaction distance with current theme color, and the gradient ring scales in real-time with distance setting changes.
- CrystalAura executes at most one detonation or placement per tick, scoring by lowest target damage, self-damage cap, damage advantage, anti-suicide, and crystal age; invalid crystals no longer block subsequent placements.
- ScaffoldWalk uses `PlacementPlan` to predict horizontal landing points, scores target positions, supporting surfaces, line of sight, distance, and continuity, then executes via block placement priority rotation.
- AutoTotem uses single SWAP atomic exchange for offhand; AutoArmor supports low-durability protection, elytra retention, binding curse filtering, and queued equipping.
- AnchorAura uses PLACE, CHARGE, DETONATE single-step state machine, waits for server block state confirmation and plans anchors by target damage, self-damage cap, and anti-suicide conditions.
- `DamageUtils` distinguishes Ender Crystal and Respawn Anchor explosion power, and uniformly calculates damage reduction by difficulty, armor toughness, blast protection, and resistance effects.
- MultiAura has been replaced with a Forge 1.20.1 behaviorally equivalent chain from FDPClient's `KillAura TargetMode=Multi`, reusing LiquidBounce Nextgen's dual 20-tick clicker and uncapped item cooldown: selects primary target by Type + 12 FDP priority levels, supports random attack distance, scan/wall/sprint distance correction, target and self-prediction, Normal/Snap/OnTick, Normal/Strict Raytrace, RaycastIgnored, 0=unlimited targets, per-tick per-world order multi-target re-validation, inventory simulation closing, FailSwing, KeepSprint, and Packet/Fake AutoBlock, Stop/Switch/Empty unlock, and SmartBlock conditions. Packet AutoBlock sends only predicted sequence packets and separately maintains server blocking state without entering client item-use state or blocking movement input; Killaura and MultiAura both distinguish real `1..10` tick swing cooldown from GUI `missTime=10000` sentinel; opening interfaces does not stop attacks. MultiAura's `Range aura` displays the true maximum attack range from `Range + Scan range` and wall distance.
- ClickAura unifies hold and click timing, avoiding performing the same vanilla attack after auxiliary attacks.
- TriggerBot accesses shared target validation.
- NoVelocity has been replaced with LiquidBounce GPL modular implementation, providing Modify/JumpReset, probability and ground-state triggers, movement/liquid/elytra filtering, current momentum retention, and negative reverse knockback; explosion knockback uses Mixin accessors, no longer relying on reflection field names.

This round prioritizes replacement of core modules:

| Module | Current Implementation |
| --- | --- |
| Killaura | LiquidBounce Nextgen Forge 1.20.1 behaviorally equivalent port: RollingClickArray, seven raw click array algorithms, ItemCooldown, random scan range, predicted hit point, three rotation timings, Raycast, Requirements, inventory simulation closing, FailSwing, AutoBlock state machine, KeepSprint, and per-click re-validation; upstream Mace, 1.21.4 sword blocking, ElytraTarget, and cross-protocol Blink branches have no corresponding protocol capabilities in 1.20.1 |
| KillauraLegit | Shared target scoring and AntiBot filtering, client-side mouse smooth turning, sticky targeting, and pre-execution re-validation |
| AimAssist | Shared target validation, horizontal/vertical axis control, four smoothing curves, target-switching advantage threshold |
| TriggerBot | Vanilla cooldown threshold, weapon restrictions, attack key conditions, item-use strategy, and shared target filtering |
| Criticals | Mixin event before attack packet sending, six Packet profiles, NoGround/Jump/MiniJump, state and cooldown checks, explicit sprint stop packet |
| Reach | Entity and block distance separation, raycast to max distance first, then clip result by hit type |
| NoVelocity | LiquidBounce Modify/JumpReset, probability and trigger conditions, momentum retention, explosion packet accessor; AntiKnockback merged as search alias |
| AutoArmor | Deterministic max-benefit equipping, durability/binding curse/elytra protection, shared inventory action queue |
| AutoTotem | Single SWAP offhand exchange, health and absorption thresholds, inventory state re-validation, and action queue |
| AntiBot | Per-tick recomputed composite predicates, covering Tab information, game mode, latency, ground, invisibility, UUID, invalid states, and duplicate names |

### Movement Pipeline

Nine commonly used movement modules have been refactored and uniformly adapted to Forge 1.20.1 Mojmap:

| Module | Current Implementation |
| --- | --- |
| AutoSprint | Omni-directional sprinting, hunger/collision/item-use/sneak/blindness condition control; applies this tick's input state after vanilla sprint determination, before actual movement calculation; only stops sprint initiated by the module itself |
| KeepSprint | Maintains sprint every tick during forward movement, no longer relies on attack events; during attacks, replaces vanilla horizontal speed multiplier from `0.6` to `1.0` within `Player.attack` and prevents the `setSprinting(false)` call for that attack, no longer re-sends sprint packets afterward; pure strategy in ordinary utility package, Mixin only retains private injection methods |
| SpeedHack | NCP Bhop, Strafe, LowHop, OnGround, Brutal five modes; shared eight-direction speed planning, air smooth turning, and horizontal speed limiting; LowHop only restricts its own jump phase |
| Flight | Vanilla, Boost, Rocket three modes; independent horizontal/vertical speed, gliding, periodic AntiKick; restores creative flight state across worlds without intervening spectator mode |
| NoFall | OnGround, Position, Smart, GroundSpoof four modes; sends packets only after reaching fall threshold, and only clears local distance after packet confirmation and still grounded state |
| NoSlowdown | Distinguishes shield vs. normal use by item action, covers six slowing block types, and recalculates negative movement speed attribute of main-hand items |
| Step | Simple 1-5 block step height and Legit segmented position packets; saves/restores original step height, with collision, liquid, jump, and execution cooldown restrictions |
| SafeWalk | Ground/jump condition control, edge clipping, and optional visible sneaking; restores real keyboard state on release |
| ScaffoldWalk | Predicted landing point scoring, line-of-sight and support surface checks, max block stack selection, silent slot swap restoration, interaction result confirmation, built-in SafeWalk and Tower modes |
| InvWalk | Uniformly recognizes ClickGUI, Navigator, all setting sub-pages, HUD editor, and client options pages; these pages do not pause single-player integrated servers, and restore real keyboard state for movement, jump, sneak, and sprint inputs |

### ESP and Visuals

- `EntityEspRenderer` unifies entity interpolation, fill, outline, tracer lines, and color batching.
- `EntitySnapshotManager` copies and publishes immutable entity, player, mob, and item lists per tick on client thread; PlayerESP, MobESP, ItemESP no longer traverse world entity collections separately.
- `PostEffectQueue` groups target rendering tasks by effect, uses Forge/Mojmap `TextureTarget + PostChain` for processing and alpha-composites back to main target, without Satin dependency.
- `TargetShader` provides Outline, Pulse, Gradient, Smoke four target visual modes, with wall penetration toggle.
- PlayerESP supports 3D batch rendering and 2D octagonal AABB screen projection; 2D mode draws outlines, fill, health bars, and average armor durability bars in unified batches.
- PlayerHalo uses entity snapshots to batch draw theme-colored halo rings above players, hides self in first-person, shows self in third-person, maintaining normal depth occlusion.
- MobESP supports distance, health, custom colors, max distance, opacity, near-distance smooth fade-out, and depth mode.
- NameTags extracts player name, health, latency, entity ID, equipment copy, and durability as immutable `NameTagRenderState` per tick; renders directly from snapshots at render stage with equipment and durability display.
- ItemESP supports max distance, invalid entity filtering, near-distance smooth fade-out, fill, and depth mode.
- ChestESP and PortalESP support fill/outline opacity and wall toggle.
- PortalESP fixed issue where closing one group incorrectly terminated subsequent group rendering.
- ClickGUI uses `FlatTheme`, `FlatRenderer`, and `FlatUiRenderer` for solid panels, controls, sliders, rounded corners, and shadows.
- Radar is registered as a persistent fixed window within ClickGUI; when enabled, displays in the game HUD and reuses the same window in Right Ctrl/Right Shift menus. Content area uses the same dark black background, theme-colored outlines and separators as ClickGUI, circular scan ring, theme pointer, rounded entity markers, and bottom radius/target count bar; title bar fixed button uses anti-aliased pin texture generated from inline SVG source; pinned state follows theme color; menu rebuilds no longer lose radar.
- Minecraft main menu uses Raven-style dark neutral visuals over vanilla dynamic panorama background.
- Two GUI entry points: Right Ctrl → BleachHack-style floating ClickGUI, Right Shift → Sinka-style Navigator; both are transient menu entries not participating in normal module enable/disable notifications, showing only one `Info` notification when opened.

### Performance Optimizations

- High-frequency event dispatch uses copy-on-write snapshots on listener changes + `LongAdder` counts.
- `RenderScope` restores blend, depth, cull, line width, shader, framebuffer, viewport, and shared `BufferBuilder` state for each 3D/GUI listener, isolating rendering pollution from exceptions.
- LambdaMetafactory annotation subscriptions eliminate reflection scanning overhead.
- GUI feature collections and single-line descriptions are cached by registration count.
- ClickGUI and Navigator share relevance fuzzy search: supports English names, Chinese display names, tags, descriptions, and setting names, with CamelCase, abbreviations, subsequences, and common transposition support; short keywords do not enable lenient correction; multiple keywords must all hit.
- `EntityCulling` uses cross-frame `GL_SAMPLES_PASSED` queries, only reading ready results; query bounding boxes reuse a single static VBO.
- `AsyncTextureLoader` provides bounded background file decoding, render-thread texture registration, and per-thread direct buffers; currently retained as file texture API, not claiming to replace Minecraft's general resource loading pipeline.
- Search, Tunneller, MobSpawnESP, and entity occlusion bounding boxes all reuse static VBOs.
- Forge/Mojmap 1.20.1 `setLevel`/`clearLevel` do not contain `System.gc()`; therefore no ineffective GC Mixin that could damage emergency memory recovery paths has been added.
- Dual-Kawase/liquid glass pipeline has been removed from GUI; `TargetShader` lazily loads and reuses independent RenderTargets only for effects actually used.
- Rounded corner fills continue using floating-point arc segments and transparent feathering edges.

## Pure Forge Architecture

1. ForgeGradle 6.0 and Forge 47.4.10 provide build, development run configurations, and reobfuscation tasks.
2. All Minecraft source symbols use Mojang 1.20.1 official mappings.
3. `WurstForgeInitializer` hooks into `FMLClientSetupEvent`, `RegisterClientCommandsEvent`, and `RenderGuiEvent.Post`; attack events are dispatched by `ClientPlayerInteractionManagerMixin` before attack packets are sent.
4. `META-INF/accesstransformer.cfg` provides 32 Forge native access transformations.
5. MixinGradle generates `wurstpenguin-refmap.json`, MixinExtras Forge is packaged via JarJar.
6. `PlatformUtils` directly calls Forge `ModList` and `FMLLoader`, without cross-loader reflection.
7. `ClientConnectionMixin` dispatches Wurst packet send/receive events only for client connections with direction `CLIENTBOUND`; `SERVERBOUND` connections for single-player integrated servers do not enter the client-side module event chain.

## Building and Running

The build environment requires Java 17. The project includes Gradle 8.11 Wrapper, no system Gradle required:

```powershell
.\gradlew.bat build --console=plain
```

Deployable Forge JarJar artifact located at:

```text
build/libs/WurstB+ Plus-v1.5.0-MC1.20.1-all.jar
```

Launch Forge 1.20.1 development client:

```powershell
.\gradlew.bat runClient --console=plain
```

## Verification Status

Project version is `1.5.0`. All 133 unit tests across 50 test files pass; Gradle `build`, Forge reobfuscation, JarJar packaging, key Mixin method visibility, and Mixin package isolation checks succeed. `PlayerMixin` KeepSprint attack injection uses native `@Redirect`, not relying on Mixin runtime-generated `Args` classes, avoiding `Args$1` class loading crashes on Forge 47.4.10 + Java 21 startup. The release artifact is `build/libs/WurstB+ Plus-v1.5.0-MC1.20.1-all.jar` (29,094,464 bytes), SHA-256 for this release is `0B70C7DB27FBBA84D7801F7BEA0E74022043C49D67E92A2818E762C77AE9CED1`.
