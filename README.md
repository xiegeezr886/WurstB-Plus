<p align="center">
  <img src="logo.png" alt="WurstB+ Plus" width="512"/>
</p>

# WurstB+ Plus

WurstB+ Plus 是一个基于 Wurst 代码结构扩展的 Minecraft 客户端项目，提供 1.20.1、1.21.1、1.21.11、26.1.2 和 26.2 的 Forge、NeoForge 与 Fabric 版本。

详细文件索引见 [PROJECT_INDEX.md](PROJECT_INDEX.md)。

## 支持版本

| Minecraft | 加载器 | 加载器版本 | Java | 工程目录 | 发布产物 |
| --- | --- | --- | --- | --- | --- |
| 1.20.1 | Forge | 47.4.10 | 17 | 根目录 | `build/libs/WurstB+ Plus-v1.5.0-Forge-1.20.1.jar` |
| 1.20.1 | NeoForge | 47.1.3 | 17 | `neoforge/` | `neoforge/build/libs/WurstB+ Plus-v1.5.0-NeoForge-1.20.1.jar` |
| 1.20.1 | Fabric | Loader 0.16.14 / API 0.92.6 | 17 | `fabric/` | `fabric/build/libs/WurstB+ Plus-1.5.0-Fabric-1.20.1.jar` |
| 1.21.1 | Forge | 52.1.16 | 21 | `versions/1.21.1/` | `versions/1.21.1/build/libs/WurstB+ Plus-v1.5.0-Forge-1.21.1.jar` |
| 1.21.1 | NeoForge | 21.1.244 | 21 | `neoforge/versions/1.21.1/` | `neoforge/versions/1.21.1/build/libs/WurstB+ Plus-v1.5.0-NeoForge-1.21.1.jar` |
| 1.21.1 | Fabric | Loader 0.16.14 / API 0.115.0 | 21 | `fabric/versions/1.21.1/` | `fabric/versions/1.21.1/build/libs/WurstB+ Plus-1.5.0-Fabric-1.21.1.jar` |
| 1.21.11 | Forge | 61.2.0 | 21 | `versions/1.21.11/` | `versions/1.21.11/build/libs/WurstB+ Plus-v1.5.0-Forge-1.21.11.jar` |
| 1.21.11 | NeoForge | 21.11.45 | 21 | `neoforge/versions/1.21.11/` | `neoforge/versions/1.21.11/build/libs/WurstB+ Plus-v1.5.0-NeoForge-1.21.11.jar` |
| 1.21.11 | Fabric | Loader 0.19.3 / API 0.141.6 | 21 | `fabric/versions/1.21.11/` | `fabric/versions/1.21.11/build/libs/WurstB+ Plus-1.5.0-Fabric-1.21.11.jar` |
| 26.1.2 | Forge | 64.1.0 | 25 | `versions/26.1.2/` | `versions/26.1.2/build/libs/WurstB+ Plus-v1.5.0-Forge-26.1.2.jar` |
| 26.1.2 | NeoForge | 26.1.2.87 | 25 | `neoforge/versions/26.1.2/` | `neoforge/versions/26.1.2/build/libs/WurstB+ Plus-v1.5.0-NeoForge-26.1.2.jar` |
| 26.1.2 | Fabric | Loader 0.19.3 / API 0.155.2 | 25 | `fabric/versions/26.1.2/` | `fabric/versions/26.1.2/build/libs/WurstB+ Plus-1.5.0-Fabric-26.1.2.jar` |
| 26.2 | Forge | 65.1.0 | 25 | `versions/26.2/` | `versions/26.2/build/libs/WurstB+ Plus-v1.5.0-Forge-26.2.jar` |
| 26.2 | NeoForge | 26.2.0.53-beta | 25 | `neoforge/versions/26.2/` | `neoforge/versions/26.2/build/libs/WurstB+ Plus-v1.5.0-NeoForge-26.2.jar` |
| 26.2 | Fabric | Loader 0.19.3 / API 0.156.0 | 25 | `fabric/versions/26.2/` | `fabric/versions/26.2/build/libs/WurstB+ Plus-1.5.0-Fabric-26.2.jar` |
> 26.2 为最新适配版本。六个 1.21.11/26.2 工程的最终状态见 [PORTING_TASK.md](PORTING_TASK.md)。全平台变更记录见 [CHANGELOG.md](CHANGELOG.md)。

### Baritone 依赖兼容性

- Forge/NeoForge 1.20.1 使用 `baritone-api-forge-1.20.1`。
- Forge/NeoForge 1.21.1 使用 `baritone-api-forge-1.21.1`，不能替换为 1.21.2。
- Forge、NeoForge 和 Fabric 1.21.11 均内嵌 Baritone 1.17.0-1.21.11。
- Forge、NeoForge 和 Fabric 26.1.2 均内嵌由 `baritone-26.1.zip` 源码构建的 Baritone 1.18.0，并保留其独立加载器元数据、Mixin 配置与 Mixin Connector。
- Forge、NeoForge 和 Fabric 26.2 均内嵌经过 26.2 渲染 API 兼容补丁的 Baritone 1.18.0-26.2。
- Fabric 1.20.1 内嵌官方 `baritone-api-fabric-1.10.3`，仅匹配 MC 1.20-1.20.1。
- Fabric 1.21.1 内嵌 `baritone-api-fabric-1.11.2`，仅匹配 MC 1.21-1.21.1。

> 注意：为避免 JPMS 模块读取错误（`baritone.api.forge does not read module minecraft`），NeoForge 1.21.1、Forge/NeoForge 26.1.2 的发布包已将 Baritone 类直接合并进 WurstB+ Plus 主模块（类归属 `wurstpenguin` 模块，可访问 `minecraft` 模块）；Forge 1.20.1/1.21.1 仍以 Jar-in-Jar 形式打包。Fabric 版本不受 JPMS 模块读取限制，仍按各自版本内嵌 Baritone JAR。

NeoForge 1.21.1 若启动时出现 `baritone.api.forge does not read module minecraft`，说明仍加载了旧的 1.21.2 包。请删除旧包和单独的 Baritone JAR，只保留对应版本的 `*.jar`。

## 根工程状态

| 项目 | 当前值 |
| --- | --- |
| Minecraft | 1.20.1 / 1.21.1 / 1.21.11 / 26.1.2 / 26.2 |
| Java | 17 (1.20.1) / 21 (1.21.1、1.21.11) / 25 (26.1.2、26.2) |
| 当前构建插件 | ForgeGradle 6.x / ForgeGradle 7.x / NeoForge ModDevGradle 2.0.143 / Fabric Loom 1.9.2、1.17.17、1.17.19 |
| 当前映射 | Mojang 官方映射 |
| 当前加载器 | Forge 47.4.10、52.1.16、61.2.0、64.1.0、65.1.0 / NeoForge 47.1.3、21.1.244、21.11.45、26.1.2.87、26.2.0.53-beta / Fabric Loader 0.16.14、0.19.3 |
| MixinExtras | Forge/NeoForge/Fabric 0.5.4（根 Forge 工程保留兼容配置） |
| 模组 ID | `wurstpenguin` |
| 模组名称 | WurstB+ Plus |
| 开发者署名 | Penguin |
| 构建状态 | 15 个 v1.5 发布产物均已生成；1.21.11/26.2 六工程已通过构建、包结构、进世界和 Baritone `#goto` 测试 |
| 注册 Hack | 197 |
| 注册命令 | 54 |
| Other Feature | 2 |
| Forge Mixin | 70 (1.20.1) / 72 (1.21.1) / 74 (26.1.2) |
| 活跃 Java 文件 | 738 (1.20.1) / 741 (1.21.1) / 740+ (26.1.2) |
| 单元测试 | 50 个测试文件，134 项 (1.20.1) / 135 项 (1.21.1) |

> 根目录是 Forge 1.20.1-47.4.10 工程；`neoforge/`、`versions/` 和 `fabric/` 是独立版本工程，不共享加载器运行时。Forge/NeoForge 使用 Mojang 官方映射，Fabric 使用 Fabric Loom + 官方映射；Fabric 版本通过 Access Widener 和 Fabric API 适配，不代表根工程是 Fabric 项目。

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

### 多加载器目录

```text
src/                         Forge 1.20.1 根工程的共享源码
versions/1.21.1/             Forge 1.21.1
versions/1.21.11/            Forge 1.21.11
versions/26.1.2/             Forge 26.1.2
versions/26.2/               Forge 26.2
neoforge/                    NeoForge 1.20.1
neoforge/versions/1.21.1/    NeoForge 1.21.1
neoforge/versions/1.21.11/   NeoForge 1.21.11
neoforge/versions/26.1.2/    NeoForge 26.1.2
neoforge/versions/26.2/      NeoForge 26.2
fabric/                      Fabric 1.20.1
fabric/versions/1.21.1/      Fabric 1.21.1
fabric/versions/1.21.11/     Fabric 1.21.11
fabric/versions/26.1.2/      Fabric 26.1.2
fabric/versions/26.2/        Fabric 26.2
download/                    全加载器发布 JAR 聚合目录
```

各加载器工程拥有独立的构建脚本、Mixin 配置和平台适配层。不要把 Fabric JAR、Forge JAR 或 NeoForge JAR 混放到同一个实例中。

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

## 新增功能

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

九个常用移动模块已按 LiquidBounce 优先、FrogClient/Aristois 补充、Meteor 兜底的顺序重构，并统一适配 Forge 1.20.1 Mojmap：

| 模块 | 当前实现 |
| --- | --- |
| AutoSprint | 全方向疾跑、饥饿/碰撞/物品使用/潜行/失明条件控制；在原版疾跑判定完成、实际移动计算前使用本 Tick 输入应用状态，只停止模块自身开启的疾跑 |
| KeepSprint | 只保留攻击前已经存在的疾跑状态，不主动启动疾跑；攻击时在 `Player.attack` 内将原版水平速度倍率由 `0.6` 替换为 `1.0`，并阻止该次 `setSprinting(false)`，不再事后补发疾跑包；纯策略位于普通工具包，Mixin 内只保留私有注入方法 |
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

## Forge 1.20.1 根工程架构

1. ForgeGradle 6.0 和 Forge 47.4.10 提供构建、开发运行配置与重混淆任务。
2. 全部 Minecraft 源码符号使用 Mojang 1.20.1 官方映射。
3. `WurstForgeInitializer` 接入 `FMLClientSetupEvent`、`RegisterClientCommandsEvent` 和 `RenderGuiEvent.Post`；攻击事件由 `ClientPlayerInteractionManagerMixin` 在攻击包发送前派发。
4. `META-INF/accesstransformer.cfg` 提供 32 条 Forge 原生访问转换。
5. MixinGradle 生成 `wurstpenguin-refmap.json`，MixinExtras Forge 通过 JarJar 打包。
6. `PlatformUtils` 直接调用 Forge `ModList` 和 `FMLLoader`，不包含跨加载器反射。
7. `ClientConnectionMixin` 仅为接收方向为 `CLIENTBOUND` 的客户端连接派发 Wurst 收发包事件；单人集成服务器的 `SERVERBOUND` 连接不会进入客户端模块事件链。

## 构建与运行

### Forge 1.20.1
根目录 1.20.1 工程需要 Java 17。项目自带 Gradle 8.11 Wrapper，不依赖系统 Gradle：

```powershell
.\gradlew.bat clean jarJar --console=plain
```

可部署 Forge JarJar 产物位于：

```text
build/libs/WurstB+ Plus-v1.5.0-Forge-1.20.1.jar
```

启动 Forge 1.20.1 开发客户端：

```powershell
.\gradlew.bat runClient --console=plain
```

### NeoForge 1.20.1

`neoforge/` 目录为 NeoForge 1.20.1 分支，使用 ForgeGradle 6.x + MixinGradle 0.7 + Mixin 0.8.7，需要 Java 17：

```powershell
cd neoforge
..\gradlew.bat -p . clean jarJar --console=plain
```

可部署 NeoForge JarJar 产物位于：

```text
neoforge/build/libs/WurstB+ Plus-v1.5.0-NeoForge-1.20.1.jar
```

### Forge 1.21.1

1.21.1 工程需要 Java 21，在独立目录中构建和运行：

```powershell
cd versions\1.21.1
..\gradlew.bat -p . clean jarJar --console=plain
..\gradlew.bat -p . runClient --console=plain
```

1.21.1 可部署 Forge JarJar 产物位于：

```text
versions/1.21.1/build/libs/WurstB+ Plus-v1.5.0-Forge-1.21.1.jar
```

### NeoForge 1.21.1

`neoforge/versions/1.21.1/` 目录为 NeoForge 1.21.1 分支，使用 ModDevGradle `net.neoforged.moddev` 2.0.143，需要 Java 21：

```powershell
cd neoforge\versions\1.21.1
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
..\..\..\gradlew.bat -p . clean build --console=plain
```

可部署 NeoForge 产物位于（已内嵌 baritone jarJar）：

```text
neoforge/versions/1.21.1/build/libs/WurstB+ Plus-v1.5.0-NeoForge-1.21.1.jar
```

SHA-256: `3666C972CCCBBE95303DEF35CA63CA505DF3FDFEEADC8CAEABB306403A86547C`

### Fabric 1.20.1、1.21.1、1.21.11、26.1.2 和 26.2

Fabric 版本使用独立的 Fabric Loom 工程。1.20.1 使用 Java 17，1.21.1/1.21.11 使用 Java 21，26.1.2/26.2 使用 Java 25。五个版本均将匹配的 Fabric API、MixinExtras、WebSocket、代理 Netty 和 Baritone 依赖打入发布包。

```powershell
cd fabric
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
..\gradlew.bat -p . build --console=plain

cd versions\1.21.1
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
..\..\..\gradlew.bat -p . build --console=plain

cd ..\1.21.11
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
..\..\..\gradlew.bat -p . clean build --console=plain

cd ..\26.1.2
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.4"
..\..\..\gradlew.bat -p . build --console=plain

cd ..\26.2
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.4"
..\..\..\gradlew.bat -p . clean build --console=plain
```

Fabric 发布包由各 Fabric 工程自行输出到各自的 `build/libs/` 目录：

```text
fabric/build/libs/WurstB+ Plus-1.5.0-Fabric-1.20.1.jar
fabric/versions/1.21.1/build/libs/WurstB+ Plus-1.5.0-Fabric-1.21.1.jar
fabric/versions/1.21.11/build/libs/WurstB+ Plus-1.5.0-Fabric-1.21.11.jar
fabric/versions/26.1.2/build/libs/WurstB+ Plus-1.5.0-Fabric-26.1.2.jar
fabric/versions/26.2/build/libs/WurstB+ Plus-1.5.0-Fabric-26.2.jar
```

Fabric 不加载 Forge Baritone JAR，也不会触发 NeoForge 的 `baritone.api.forge` 模块读取错误。26.1.2 Fabric 发布包通过 Loom `include` 内嵌 `baritone-api-fabric-1.18.0.jar`。

### Forge 26.1.2

`versions/26.1.2/` 目录为 Minecraft 26.1.2 / Forge 64.1.0 分支，需要 Java 25（JDK 25 SSL 问题需设置 `_JAVA_OPTIONS`）：

```powershell
cd versions\26.1.2
$env:_JAVA_OPTIONS = "-Djavax.net.ssl.trustStoreType=Windows-ROOT"
..\gradlew.bat -p . allJar --console=plain
..\gradlew.bat -p . runClient --console=plain
```

可部署产物：

```text
versions/26.1.2/build/libs/WurstB+ Plus-v1.5.0-Forge-26.1.2.jar
```

> 26.1.2 使用 ForgeGradle 7.x、Gradle 9.4.1、Mojang 官方映射，内置 Mixin 0.8.7 + MixinExtras。Baritone 1.18.0 的类文件直接合并进主模块；netty-codec-socks/netty-handler-proxy 仅合并其自身包（版本对齐 MC 自带的 netty 4.2.7），不再把整个 netty 传递链展开进主 jar，避免模块包冲突。
> 渲染管线从 `render()` 迁移到 `extractRenderState()`，文字颜色需 8 位 Alpha（`0xFFxxxxxx`），`blit` 需 `RenderPipelines` 参数。`BufferUploader` 已移除，三角扇批量渲染暂不可用。更新日志见 `versions/26.1.2/CHANGELOG.md`，迁移详情见 `versions/26.1.2/PORTING_TASK.md`。

### NeoForge 26.1.2

`neoforge/versions/26.1.2/` 目录为 Minecraft 26.1.2 / NeoForge 26.1.2.87 分支，使用 ModDevGradle 2.0.143、Gradle 9.4.1 和 Java 25：

```powershell
cd neoforge\versions\26.1.2
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.4"
$env:_JAVA_OPTIONS = "-Djavax.net.ssl.trustStoreType=Windows-ROOT"
..\..\..\gradlew.bat -p . clean build --console=plain
```

可部署的全依赖产物位于：

```text
neoforge/versions/26.1.2/build/libs/WurstB+ Plus-v1.5.0-NeoForge-26.1.2.jar
```

SHA-256: `2C92F68247C79C387191DD1659777092C1D47E99319D0AAE0DF576E741BC6CAA`

该产物通过 NeoForge JarJar 内嵌 Baritone 1.18.0、Java-WebSocket 和所需 Netty 组件；Baritone 保持独立 NeoForge 模组元数据与 Mixin Connector，不会触发旧 Forge Baritone 包的模块读取错误。

### 1.21.11 和 26.2 新版本工程

六个新工程统一由批量脚本构建，避免手工使用错误 JDK、漏跑 Baritone 兼容补丁或生成缺少主类的 Forge 包：

```powershell
# 1.21.11 三加载器
powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1 `
  -Version 1.21.11 -Clean -PublishToDownload

# 26.2 三加载器
powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1 `
  -Version 26.2 -Clean -PublishToDownload
```

Forge 1.21.11/26.2 使用 `allJar`，NeoForge 与 Fabric 使用 `build`。所有六个最终 JAR 均包含 Wurst 核心类和独立的内嵌 Baritone JAR，不需要额外安装 Baritone。

## 批量构建与版本启动测试

### 一键构建 15 个发布产物

`scripts/build-all.ps1` 依次构建 15 个版本（5 个 MC 版本 × Forge/NeoForge/Fabric），每个工程的产物输出到各自的 `build/libs/`：

```powershell
# 全量构建
powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1

# 只构建某个 MC 版本
powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1 -Version 1.21.1

# 只构建某个加载器
powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1 -Loader NeoForge

# 先 clean 再构建（-Clean）；跳过匹配工程（-Skip 26.1.2）
powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1 -Clean -Skip 26.1.2

# 构建后同步到 download/
powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1 -Version 26.2 -Clean -PublishToDownload
```

脚本自动完成：按 MC 版本选择 JDK（17/21/25，可用 `WURSTBPLUS_JAVA17/21/25` 环境变量覆盖）、使用 Windows 根证书库兼容本地 TLS 代理、在 26.2 构建前重建 Baritone 兼容包、以 `--no-daemon` 构建，并校验核心类、内嵌 Baritone API、26.2 兼容类及 Mixin Manifest。报告保存到 `.test/report-build-<时间戳>.txt`。

产物清单：

| 工程 | 任务 | 产物 |
| --- | --- | --- |
| 根目录 (Forge 1.20.1) | `jarJar` | `build/libs/WurstB+ Plus-v1.5.0-Forge-1.20.1.jar` |
| `versions/1.21.1` | `jarJar` | `build/libs/WurstB+ Plus-v1.5.0-Forge-1.21.1.jar` |
| `versions/1.21.11` | `allJar test` | `build/libs/WurstB+ Plus-v1.5.0-Forge-1.21.11.jar` |
| `versions/26.1.2` | `allJar` | `build/libs/WurstB+ Plus-v1.5.0-Forge-26.1.2.jar` |
| `versions/26.2` | `allJar test` | `build/libs/WurstB+ Plus-v1.5.0-Forge-26.2.jar` |
| `neoforge/` | `jarJar` | `build/libs/WurstB+ Plus-v1.5.0-NeoForge-1.20.1.jar` |
| `neoforge/versions/1.21.1` | `jar` | `build/libs/WurstB+ Plus-v1.5.0-NeoForge-1.21.1.jar` |
| `neoforge/versions/1.21.11` | `build` | `build/libs/WurstB+ Plus-v1.5.0-NeoForge-1.21.11.jar` |
| `neoforge/versions/26.1.2` | `jar` | `build/libs/WurstB+ Plus-v1.5.0-NeoForge-26.1.2.jar` |
| `neoforge/versions/26.2` | `build` | `build/libs/WurstB+ Plus-v1.5.0-NeoForge-26.2.jar` |
| `fabric/` | `build -x test` | `build/libs/WurstB+ Plus-1.5.0-Fabric-1.20.1.jar` |
| `fabric/versions/1.21.1` | `build -x test` | `build/libs/WurstB+ Plus-1.5.0-Fabric-1.21.1.jar` |
| `fabric/versions/1.21.11` | `build` | `build/libs/WurstB+ Plus-1.5.0-Fabric-1.21.11.jar` |
| `fabric/versions/26.1.2` | `build -x test` | `build/libs/WurstB+ Plus-1.5.0-Fabric-26.1.2.jar` |
| `fabric/versions/26.2` | `build` | `build/libs/WurstB+ Plus-1.5.0-Fabric-26.2.jar` |

### 批量版本启动测试

`scripts/run-version-tests.ps1` 批量把发布 JAR（5 个 MC 版本 × Fabric/Forge/NeoForge）部署到 `.test/versions/` 下对应实例，逐个真实启动游戏。除主菜单测试外，还可通过 Quick Play 进入指定单人世界并发送 Baritone 命令，验证 Wurst、Baritone、世界加载和命令响应。脚本默认以仓库根目录作为路径基准。

### 前置条件

- `.test/` 是 PCL 启动器格式的测试环境，包含 `assets/`、`libraries/` 和 `versions/`；
- 每个版本实例（如 `1.20.1-Forge_47.4.22/`）内含 `<版本名>.json`（Mojang 格式版本描述，已含完整启动参数与 FML 参数）和 `<版本名>.jar`（客户端）；
- 待测 JAR 与实例按“加载器 + MC 版本”自动匹配（例如 `WurstB+ Plus-v1.5.0-NeoForge-1.21.1.jar` → `1.21.1-NeoForge_21.1.248`）；默认从 `download/` 读取（Forge/NeoForge 聚合目录），Fabric 产物可用 `-DownloadDir` 指向对应的 `build/libs`（或先复制到 `download/`）；
- JDK 按 MC 版本自动选择：1.20.1 → `jdk-17`，1.21.1/1.21.11 → `jdk-21`，26.1.2/26.2 → `jdk-25.0.4`（可通过 `WURSTBPLUS_JAVA17/21/25` 覆盖）。

### 用法

```powershell
# 全量测试 15 个版本
powershell -ExecutionPolicy Bypass -File scripts\run-version-tests.ps1

# 只测某个 MC 版本
powershell -ExecutionPolicy Bypass -File scripts\run-version-tests.ps1 -Version 1.21.1

# 只测某个加载器
powershell -ExecutionPolicy Bypass -File scripts\run-version-tests.ps1 -Loader NeoForge

# 组合过滤 + 自定义超时（秒）
powershell -ExecutionPolicy Bypass -File scripts\run-version-tests.ps1 -Version 26.1.2 -Loader Fabric -TimeoutSeconds 300

# 进入单人世界并验证 Baritone 命令
powershell -ExecutionPolicy Bypass -File scripts\run-version-tests.ps1 -Version 26.2 `
  -QuickPlayWorld WurstSmokeFresh -BaritoneCommand '#goto 0 88 0'
```

### 参数

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `-VersionsRoot` | `.test\versions` | 测试实例根目录 |
| `-DownloadDir` | `download` | 待测 JAR 目录 |
| `-LibrariesDir` | `.test\libraries` | 依赖库目录 |
| `-AssetsDir` | `.test\assets` | 游戏资源目录 |
| `-ProjectRoot` | 自动检测 | 手动指定仓库根目录 |
| `-TimeoutSeconds` | 240 | 单个版本的最长等待秒数 |
| `-SettleSeconds` | 20 | 出现启动标志后还需稳定运行的秒数 |
| `-Version` | 空 | 过滤 MC 版本（1.20.1 / 1.21.1 / 1.21.11 / 26.1.2 / 26.2） |
| `-Loader` | 空 | 过滤加载器（Fabric / Forge / NeoForge） |
| `-QuickPlayWorld` | 空 | 启动后直接进入指定单人世界 |
| `-BaritoneCommand` | 空 | 进入世界后发送命令并验证预期响应 |
| `-KeepOldJars` | 关 | 保留旧 WurstB JAR（可能导致实例同时加载多个版本） |
| `-Quiet` | 关 | 不输出过程信息 |

### 判定逻辑

- **PASS**：主菜单模式要求 Wurst 与所需 Baritone 初始化并稳定运行；Quick Play 模式还要求玩家加入世界，设置命令时必须出现对应 Baritone 响应；
- **FAIL**：进程提前退出（非零退出码）、`crash-reports/` 出现新报告（附崩溃摘要）、或日志出现 `FATAL` / `Mod Loading has failed`；
- **TIMEOUT**：超时未完成启动标志与窗口检查（进程会被强制结束）；
- **ERROR**：实例缺失、客户端 jar 缺失、依赖缺失或 natives 提取失败（不启动）。

### 输出

- 控制台实时进度与汇总表（PASS 绿色 / FAIL 红色 / TIMEOUT 黄色）；
- `.test/report-<时间戳>.txt` 详细报告（每个版本的 JAR、加载器、状态、耗时、备注）；
- 每个实例的游戏输出保存在 `.test/out/<实例名>/stdout.log`、`stderr.log`，游戏日志在实例 `logs/latest.log`。

### 脚本行为说明

- 自动解析版本 JSON：按启动器的“最后一个匹配规则”语义过滤依赖与参数、展开 `${...}` 变量，按 `natives` classifier 提取 natives 到 `.test/natives/<实例名>/`；
- 自动修复 JPMS 启动参数：module path（`-p`）上的 JAR 会从 `-cp` 中剔除，classpath 去重，避免 `bootstraplauncher already on module path` 与 `Duplicate key` 崩溃；
- Quick Play 命令通过 GLFW 窗口消息投递，Windows 拒绝前台焦点时仍可在后台完成自动输入；
- 默认部署前把实例 `mods/` 中已有的 WurstB JAR 备份为 `.backup-<时间戳>.jar.disabled`；启用 `-KeepOldJars` 时不改动旧文件；
- 启动前只清理脚本自己记录且命令行确实指向该实例的 PID，不扫描或终止其他 Minecraft 进程；
- 窗口以最小化方式启动（854×480），测试结束后进程被强制结束。

### 辅助脚本

`scripts/replace-jar-entry.ps1` 用于在不重建整个 JAR 的情况下替换其中的单个条目（例如修改 mixins JSON 后直接更新发布包）：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\replace-jar-entry.ps1 `
  -JarPath "fabric\versions\26.1.2\build\libs\WurstB+ Plus-1.5.0-Fabric-26.1.2.jar" `
  -EntryToReplace "wurstpenguin.mixins.json" `
  -ContentFile "C:\path\to\new\wurstpenguin.mixins.json"
```

> 注意：多行内容必须通过 `-ContentFile` 从文件读取，不要用命令行参数直接传递（会被换行截断）。
> 默认会先创建带时间戳的 `.bak` 备份；确认不需要备份时才使用 `-NoBackup`。

`scripts/upgrade-lwjgl.ps1` 用于升级指定实例的 LWJGL native DLL。目标目录必须显式传入，脚本会校验 Maven Central 的 SHA-256/SHA-1，并在覆盖前创建备份：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\upgrade-lwjgl.ps1 `
  -NativesDir "D:\.penguin\.minecraft\versions\1.20.1-Forge_47.4.10\1.20.1-Forge_47.4.10-natives"
```

`scripts/ccswitch-guardian.ps1` 默认只监控本地端口并按配置尝试启动 CC Switch，不会自动覆盖用户级 `OPENAI_BASE_URL`。需要使用本地代理时显式添加 `-UseLocalProxy`；需要持久化环境变量时再添加 `-PersistUserEnvironment`。安装登录自启动任务使用 `scripts\install-ccswitch-guardian.ps1`，更新已有任务必须显式传入 `-Replace`。

## 安全提示

`WurstForge-1.20.1.jar` 和 `WurstForge-Decompiled/` 内含 HWID、MAC 地址和 Webhook 代码，已从活动源码中完全移除。

`Client.zip` 的 `Launcher.bat` 会执行 `compiler.exe conf.txt`。在完成二进制审计和 Lua 解混淆前，不应执行其中的 EXE。

## 验证状态

项目版本为 `1.5.0`。当前发布矩阵包含 15 个加载器/游戏版本组合。本轮对新增的 1.21.11 与 26.2 六个版本完成 clean 构建、核心类检查、内嵌 Baritone 检查、真实客户端启动、单人世界加载及 `#goto 0 88 0` 命令验证，测试范围内未发现阻塞发布的问题。该结论不代表所有战斗、移动、GUI 和 HUD 功能均已穷举测试。

- 1.20.1 Forge 47.4.10：`build/libs/WurstB+ Plus-v1.5.0-Forge-1.20.1.jar`
- 1.20.1 NeoForge 47.1.3：`neoforge/build/libs/WurstB+ Plus-v1.5.0-NeoForge-1.20.1.jar`
- 1.21.1 Forge 52.1.16：`versions/1.21.1/build/libs/WurstB+ Plus-v1.5.0-Forge-1.21.1.jar`
- 1.21.1 NeoForge 21.1.244：`neoforge/versions/1.21.1/build/libs/WurstB+ Plus-v1.5.0-NeoForge-1.21.1.jar`
- 1.21.11 Forge 61.2.0：`versions/1.21.11/build/libs/WurstB+ Plus-v1.5.0-Forge-1.21.11.jar`
- 1.21.11 NeoForge 21.11.45：`neoforge/versions/1.21.11/build/libs/WurstB+ Plus-v1.5.0-NeoForge-1.21.11.jar`
- 26.1.2 Forge 64.1.0：`versions/26.1.2/build/libs/WurstB+ Plus-v1.5.0-Forge-26.1.2.jar`（文字颜色、图标渲染、圆角抗锯齿、Mixin 启动崩溃已修复）
- 26.1.2 NeoForge 26.1.2.87：`neoforge/versions/26.1.2/build/libs/WurstB+ Plus-v1.5.0-NeoForge-26.1.2.jar`（Java-WebSocket 与 Netty 运行依赖已内嵌）
- 26.2 Forge 65.1.0：`versions/26.2/build/libs/WurstB+ Plus-v1.5.0-Forge-26.2.jar`
- 26.2 NeoForge 26.2.0.53-beta：`neoforge/versions/26.2/build/libs/WurstB+ Plus-v1.5.0-NeoForge-26.2.jar`
- 1.20.1 Fabric Loader 0.16.14：`fabric/build/libs/WurstB+ Plus-1.5.0-Fabric-1.20.1.jar`
- 1.21.1 Fabric Loader 0.16.14：`fabric/versions/1.21.1/build/libs/WurstB+ Plus-1.5.0-Fabric-1.21.1.jar`
- 1.21.11 Fabric Loader 0.19.3：`fabric/versions/1.21.11/build/libs/WurstB+ Plus-1.5.0-Fabric-1.21.11.jar`
- 26.1.2 Fabric Loader 0.19.3：`fabric/versions/26.1.2/build/libs/WurstB+ Plus-1.5.0-Fabric-26.1.2.jar`
- 26.2 Fabric Loader 0.19.3：`fabric/versions/26.2/build/libs/WurstB+ Plus-1.5.0-Fabric-26.2.jar`

### 发布包校验

以下 SHA-256 对应项目 `download/` 目录中的当前 15 个 v1.5 发布文件：

| 文件 | SHA-256 |
| --- | --- |
| `WurstB+ Plus-1.5.0-Fabric-1.20.1.jar` | `5382A8066844B38CE868590714F2B5F1A547DEAFFFD290934CAA3C32340F3727` |
| `WurstB+ Plus-1.5.0-Fabric-1.21.1.jar` | `0E3838806FC21AC158CB3EB1550AE143F10D6066F84ADE7F73A69134ECCC110B` |
| `WurstB+ Plus-1.5.0-Fabric-1.21.11.jar` | `A9BF64ABC3674F859E42759038FDE65A4857EFCACF0E35D2DAAA61295B0E5ECD` |
| `WurstB+ Plus-1.5.0-Fabric-26.1.2.jar` | `8A2B04A5994B585BC3B8B7204DBA45BA3B5B1A6A5078CD8F10CA6F33E26865E0` |
| `WurstB+ Plus-1.5.0-Fabric-26.2.jar` | `D5F1E340A9B47B28512C0DDC989EF327BC6876E7C6DCE2211284053015A809C5` |
| `WurstB+ Plus-v1.5.0-Forge-1.20.1.jar` | `CFC5EF862A0D822E20895AA69D952EB809D253D80CC89B2CB27172A5D2CDB9C0` |
| `WurstB+ Plus-v1.5.0-Forge-1.21.1.jar` | `A78BFEFA7BD7B4220EB7017827742CA477B971450CA08362D4C66C7BD32F00C8` |
| `WurstB+ Plus-v1.5.0-Forge-1.21.11.jar` | `ABEB6903587A70D1EBC2F13D6E24381145943359CC705CDFD8AC1DE3EB11834D` |
| `WurstB+ Plus-v1.5.0-Forge-26.1.2.jar` | `489DC385B0389AFF604B276829821E354F4FBD1EDBB384EA29708C3B4A27D5A2` |
| `WurstB+ Plus-v1.5.0-Forge-26.2.jar` | `E9921F1847E3F58802FF0E1B1D9182DE2885CF4BF65300DEEC6E9EBBB7C4A35A` |
| `WurstB+ Plus-v1.5.0-NeoForge-1.20.1.jar` | `0C05A07ED15B5E4B84259D592777A00231A6C8639027E2C0E50C0E91E88ACB4D` |
| `WurstB+ Plus-v1.5.0-NeoForge-1.21.1.jar` | `87622A2F7BC0692377A58B87D3B1787AC13CC12E7D87D872EB8DBD70E7452343` |
| `WurstB+ Plus-v1.5.0-NeoForge-1.21.11.jar` | `BD0003E63077383C8429DE28BB69214C11C7EE3C948558C96AF6B288B66D3BB9` |
| `WurstB+ Plus-v1.5.0-NeoForge-26.1.2.jar` | `5B772ED2791B2A1FE3315BF2F032BFFCC5F48B36F6D1EF306F10A85744C70BB3` |
| `WurstB+ Plus-v1.5.0-NeoForge-26.2.jar` | `08A2DF2A6DF6A5A947905B8648D33F931FB628794BD439098D75A94F5884701C` |

### 1.21.1 渲染管线说明

1.21.1 的世界叠加层渲染通过 `RenderLevelStageEvent.Stage.AFTER_LEVEL` 驱动，在 `WurstForgeInitializer` 中构造 PoseStack：

```java
// 从 Forge 事件获取相机和投影矩阵
Camera camera = event.getCamera();
Quaternionf cameraRotation = camera.rotation().conjugate(new Quaternionf());
Matrix4f viewMatrix = new Matrix4f().rotate(cameraRotation);

// PoseStack 只包含相机旋转（view matrix），投影由 shader 的 ProjMat 处理
PoseStack poseStack = new PoseStack();
poseStack.mulPose(viewMatrix);
```

关键设计：
- **PoseStack 只放 view matrix（相机旋转）**，不放 projection。`BufferBuilder.addVertex()` 在存储前已用 matrix 变换顶点，`BufferUploader.drawWithShader()` 的 shader 会再施加 `ProjMat`，双投影会导致渲染偏移
- 在触发事件前通过 `event.getCamera()` 设置 `BlockEntityRenderDispatcher.camera`，确保 `RenderUtils.getCameraPos()` 返回正确的相机位置
- `RenderListener.fire()` 不包装 `RenderScope`，由各模块自行管理 GL 状态

### 26.1.2 渲染管线说明

26.1.2 使用全新的 extract/render 分离管线（`extractRenderState`），主要变更：

- **Screen API**：`render(GuiGraphics, int, int, float)` → `extractRenderState(GuiGraphicsExtractor, int, int, float)`
- **填充**：`fill()` 调用在 extract 阶段记录指令，后续批量渲染。逐像素大量调用会产生缓冲区膨胀
- **文字**：`centeredText()`/`text()` 处理 Alpha 通道，颜色需 8 位 hex（`0xFFxxxxxx`），6 位色（`0xffffff`）视为透明
- **纹理**：`blit()` 需 `RenderPipelines.GUI_TEXTURED` 第一参数
- **BufferUploader**：已移除，使用 `GpuBuffer`/`RenderPass` 替代，GUI 三角扇批量渲染暂不可用
- **圆角**：使用 4×4 超采样抗锯齿像素覆盖，大量圆角矩形有性能开销
