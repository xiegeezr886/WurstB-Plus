# 项目索引

本文档记录当前工作区的实际结构。统计基于 `src/main`，不包含根目录参考压缩包中的源码文件。

## 根目录

| 路径 | 说明 |
| --- | --- |
| `build.gradle` | ForgeGradle 6.0、MixinGradle、JarJar 和 Forge 运行配置 |
| `gradle.properties` | Minecraft、Forge、MixinExtras 和项目版本 |
| `settings.gradle` | ForgeGradle 插件仓库配置 |
| `gradle/` | Gradle 8.11 wrapper |
| `src/main/java/` | 纯 Forge/Mojmap Java 源码，共 729 个文件 |
| `src/main/resources/` | Mixin、Access Transformer、Forge 元数据、图像和翻译资源 |
| `versions/1.21.1/` | 独立 Minecraft 1.21.1、Forge 52.1.16、Java 21 工程；不改变根目录 1.20.1 工程 |
| `source/` | 外部客户端与库的静态参考源码，不参与活动项目编译 |
| `WurstForge-Decompiled/` | CFR 反编译的 Forge Wurst 参考源码，共 558 个 Java 文件 |
| `LICENSE.txt` | GPL-3.0 许可证 |
| `README.md` | 项目架构与状态说明 |

## 源码包索引

| 包 | 文件数 | 职责 |
| --- | ---: | --- |
| `net.wurstclient` | 11 | 客户端生命周期、功能基类、翻译和共享状态 |
| `net.wurstclient.addon` | 2 | Addon 扩展系统（WurstAddon、AddonManager） |
| `net.wurstclient.ai` | 8 | 路径搜索、Spider 垂直节点规划和路径执行 |
| `net.wurstclient.altmanager` | 25 | 账号、登录、系统原生凭据主密钥和账号管理界面 |
| `net.wurstclient.clickgui2` | 43 | 双 GUI、实心主题、模板板块、字体偏好、缩放搜索框、窗口、控件和编辑界面 |
| `net.wurstclient.command` | 7 | 命令基础设施、BrigadierCommand 和处理器 |
| `net.wurstclient.commands` | 54 | 具体命令实现（含 .macros .waypoints .proxy） |
| `net.wurstclient.discord` | 2 | Discord RPC IPC 客户端和管理器 |
| `net.wurstclient.event` | 6 | EventManager、WurstSubscriber（LambdaMetafactory）、注解 |
| `net.wurstclient.events` | 38 | 输入、移动、网络、渲染等事件接口 |
| `net.wurstclient.hack` | 7 | Hack 基类、注册表、冲突和生命周期 |
| `net.wurstclient.hacks` | 240 | 196 个 Hack 及其内部辅助实现 |
| `net.wurstclient.hud` | 4 | HUD 和 TabGUI 渲染 |
| `net.wurstclient.hud2` | 23 | HUD2 系统（统一渲染、指标采样、卡片编辑器及 14 个独立元素） |
| `net.wurstclient.gui.title` | 2 | Raven + BleachHack 风格标题界面控制器与自绘按钮 |
| `net.wurstclient.keybinds` | 6 | 按键绑定、智能绑定（TOGGLE/HOLD/SMART）、配置和执行 |
| `net.wurstclient.macros` | 2 | Macro 定义和 MacroManager |
| `net.wurstclient.mixin` | 70 | 69 个 Minecraft/Forge 注入点及 Mixin 配置插件；普通运行类禁止放入该专用包 |
| `net.wurstclient.mixinterface` | 7 | Mixin 暴露接口 |
| `net.wurstclient.nochatreports` | 2 | 聊天报告相关数据处理 |
| `net.wurstclient.options` | 8 | 客户端选项与配置管理界面 |
| `net.wurstclient.other_feature` | 2 | Other Feature 基础设施 |
| `net.wurstclient.other_features` | 17 | 非 Hack 功能入口 |
| `net.wurstclient.proxy` | 2 | ProxyConfig 和 ProxyManager |
| `net.wurstclient.serverfinder` | 3 | 服务器扫描和清理界面 |
| `net.wurstclient.settings` | 55 | 设置类型、过滤器和配置文件 |
| `net.wurstclient.update` | 3 | 更新与资源包问题检测 |
| `net.wurstclient.util` | 73 | 渲染、实体/名称标签快照、屏幕投影、放置规划、战斗/击退规划、库存队列、异步纹理、GPU 遮挡和 JSON 工具 |
| `net.wurstclient.waypoints` | 2 | Waypoint 定义和 WaypointsManager |

## 功能注册表

### Hack

注册入口：`src/main/java/net/wurstclient/hack/HackList.java`

| 分类 | 数量 |
| --- | ---: |
| Blocks | 30 |
| Chat | 7 |
| Combat | 33 |
| Fun | 12 |
| Items | 10 |
| Movement | 41 |
| Other | 14 |
| Render | 48 |

总计注册 197 个 Hack。195 个属于上表分类，`ClickGuiHack` 与 `NavigatorHack` 作为两个 GUI 入口不设置常规分类，也不发送普通模块的 Enabled/Disabled 通知；打开界面时只发送单条 `Info` 通知。原 `AntiKnockbackHack` 已合并进 `NoVelocityHack`，旧名称保留为搜索标签。

**新增 14 个 Hack**：AntiBot (Combat), EntityCulling/BossStack/PopChams/Breadcrumbs/LightOverlay/LogoutSpots/PlayerHalo (Render), DankBobbing/Notebot/Twerk/Vomit (Fun), PacketCanceller/PacketLogger (Other)。

### 命令

注册入口：`src/main/java/net/wurstclient/command/CmdList.java`

共 54 个命令，覆盖按键、设置、好友、路径、物品、传送、NBT、XRay、宏、路径点、代理和功能管理。

**新增 3 个命令**：`.macros` `.waypoints` `.proxy`

### Other Feature

注册入口：`src/main/java/net/wurstclient/other_feature/OtfList.java`

共 17 个，包括 Changelog、Disable、HackList、KeybindManager、NoChatReports、NoTelemetry、ServerFinder、TabGUI、Translations、VanillaSpoof、Zoom 等。

## 核心文件导航

| 领域 | 文件 |
| --- | --- |
| 客户端生命周期 | `src/main/java/net/wurstclient/WurstClient.java` |
| 当前 Forge 入口 | `src/main/java/net/wurstclient/WurstForgeInitializer.java` |
| Hack 基类 | `src/main/java/net/wurstclient/hack/Hack.java` |
| Hack 注册表 | `src/main/java/net/wurstclient/hack/HackList.java` |
| 事件管理器 | `src/main/java/net/wurstclient/event/EventManager.java` |
| LambdaMetafactory 订阅器 | `src/main/java/net/wurstclient/event/WurstSubscriber.java` |
| 设置基类 | `src/main/java/net/wurstclient/settings/Setting.java` |
| 设置持久化 | `src/main/java/net/wurstclient/settings/SettingsFile.java` |
| ClickGUI 主屏幕 | `src/main/java/net/wurstclient/clickgui2/ClickGuiScreen.java` |
| GUI 偏好与字体资源包 | `src/main/java/net/wurstclient/clickgui2/GuiPreferences.java` |
| Navigator 主屏幕 | `src/main/java/net/wurstclient/clickgui2/NavigatorScreen.java` |
| ClickGUI 窗口管理 | `src/main/java/net/wurstclient/clickgui2/ClickGui.java` |
| HUD | `src/main/java/net/wurstclient/hud/IngameHUD.java` |
| HUD2 管理器 | `src/main/java/net/wurstclient/hud2/HudManager.java` |
| 客户端 TPS 指标 | `src/main/java/net/wurstclient/hud2/ClientMetricsManager.java` |
| 每 Tick 实体快照 | `src/main/java/net/wurstclient/util/EntitySnapshotManager.java` |
| 渲染状态作用域 | `src/main/java/net/wurstclient/util/render/RenderScope.java` |
| 目标后处理队列 | `src/main/java/net/wurstclient/util/render/PostEffectQueue.java` |
| 屏幕注册抽象 | `src/main/java/net/wurstclient/util/ScreenRegistry.java` |
| 模糊搜索 | `src/main/java/net/wurstclient/util/FuzzySearch.java` |
| 延迟操作队列 | `src/main/java/net/wurstclient/util/DeferredActionQueue.java` |
| 点击模式 | `src/main/java/net/wurstclient/util/ClickPattern.java` |
| 高级瞄准 | `src/main/java/net/wurstclient/util/RotationSmoothing.java` |
| 旋转协调 | `src/main/java/net/wurstclient/RotationFaker.java` |
| 通用渲染 | `src/main/java/net/wurstclient/util/RenderUtils.java` |
| 实体工具（含 AntiBot 过滤） | `src/main/java/net/wurstclient/util/EntityUtils.java` |
| 旋转工具 | `src/main/java/net/wurstclient/util/RotationUtils.java` |
| JSON 写入（原子替换） | `src/main/java/net/wurstclient/util/json/JsonUtils.java` |
| 宏管理器 | `src/main/java/net/wurstclient/macros/MacroManager.java` |
| 路径点管理器 | `src/main/java/net/wurstclient/waypoints/WaypointsManager.java` |
| 代理管理器 | `src/main/java/net/wurstclient/proxy/ProxyManager.java` |
| Addon 管理器 | `src/main/java/net/wurstclient/addon/AddonManager.java` |
| Discord RPC | `src/main/java/net/wurstclient/discord/DiscordRpcManager.java` |
| Forge 平台工具 | `src/main/java/net/wurstclient/util/PlatformUtils.java` |

## 新增架构文件

| 文件 | 职责 | 来源 |
|------|------|------|
| `event/WurstSubscriber.java` | LambdaMetafactory 直接方法调用订阅器 | BleachHack |
| `util/FuzzySearch.java` | Unicode/CamelCase 规范化、缩写/子序列/Damerau-Levenshtein 匹配与相关度评分 | Meteor 思路，按当前 GUI 重写 |
| `util/DeferredActionQueue.java` | 跨 tick 延迟操作队列 | BleachHack |
| `util/ClickPattern.java` | 7 种 LiquidBounce 原始 20 Tick 数组填充算法（Stabilized/Efficient/Spamming/DoubleClick/Drag/Butterfly/NormalDistribution） | LiquidBounce |
| `util/RollingClickArray.java` + `CombatClickScheduler.java` | 双 20 Tick 循环点击数组、单 Tick 多点击、每秒强制检查、空挥冷却和未截断物品冷却 | LiquidBounce |
| `util/RotationSmoothing.java` | 5 种旋转平滑算法 | LiquidBounce |
| `keybinds/KeyAction.java` | 智能绑定枚举（TOGGLE/HOLD/SMART） | LiquidBounce |
| `macros/Macro.java` + `MacroManager.java` | 宏定义与管理 | Meteor |
| `waypoints/Waypoint.java` + `WaypointsManager.java` | 路径点定义/渲染 | Meteor |
| `proxy/ProxyConfig.java` + `ProxyManager.java` | SOCKS4/5 代理 | Meteor |
| `addon/WurstAddon.java` + `AddonManager.java` | 第三方模块扩展 | Meteor |
| `command/BrigadierCommand.java` | Minecraft 原生命令框架 | Meteor |
| `discord/DiscordRpc.java` + `DiscordRpcManager.java` | Discord Rich Presence | Meteor |
| `hud2/elements/FpsHudElement.java` | FPS HUD 元素 | Meteor |
| `hud2/elements/CoordsHudElement.java` | 坐标 HUD 元素 | Meteor |
| `hud2/elements/PingHudElement.java` | Ping HUD 元素 | Meteor |
| `hud2/elements/ArmorHudElement.java` | 护甲物品与耐久 HUD | LabyMod |
| `hud2/elements/InventoryHudElement.java` | 3×9 主背包、物品数量/耐久与编辑器实时预览 | FDP/Meteor/BleachHack 融合后按 HUD2 重写 |
| `hud2/elements/PotionHudElement.java` | 药水效果与持续时间 HUD | LabyMod |
| `hud2/elements/ComboHudElement.java` | 连击事件 HUD | LabyMod |
| `hud2/elements/KeystrokesHudElement.java` | WASD、功能键、鼠标 CPS、按压动画、原版紧凑样式及编辑器本体预览 | Clean Keystrokes 思路，保留原样式并接入 HUD2 编辑器 |
| `hud2/elements/TargetHudElement.java` | 统一通知/HackList 卡片风格的目标头像、名称、主题色生命条、条件主手/盔甲装备栏、目标保持和淡出 | RavenBS++ + LiquidBounce/FDP |
| `hud2/elements/MinimapHudElement.java` + `MinimapTerrainCache.java` | 北方固定的纯圆形地形小地图；使用有界 `16x16` 区块瓦片缓存、渐进刷新预算、动态纹理合成、区块网格和实体分层，不带底部坐标/位置文字，并支持 HUD 编辑器真实预览和拖动 | JourneyMap Legacy 瓦片/分层思路 + Xaero 中文术语参考，按 Forge/Mojmap 原生 API 独立实现 |
| `hud2/HudNotificationRenderer.java` | 通知队列、淡入淡出、底部 3 秒主题色/白色读条和读满移除 | HUD2 统一视觉实现 |
| `hud2/ClientMetricsManager.java` + `hud2/elements/*HudElement.java` | TPS、速度、服务器、时间及维度坐标换算 | FrogClient HUD 思路，按 HUD2 元素重写 |
| `util/EntitySnapshotManager.java` | 客户端线程每 Tick 发布不可变实体分类快照 | FrogClient 快照思路，移除自旋后台线程 |
| `util/RotationQueue.java` + `RotationFaker.java` + `mixin/ClientPlayerEntityMixin.java` | 多模块静默旋转优先级仲裁、原版角度 getter 注入、成功发包后的服务端旋转追踪和本地相机分离 | LiquidBounce RotationManager 等价架构；不修改玩家真实角度、不额外发送旋转包，取消包不更新服务端状态 |
| `util/inventory/InventoryActionQueue.java` | 菜单校验、状态复验和优先级原子库存动作链 | LiquidBounce InventoryManager 思路，按 Forge 菜单 API 重写 |
| `util/PlacementPlan.java` + `ScaffoldPlacementPlanner.java` | 预测落点、支撑面、视线、距离和连续性评分 | LiquidBounce Scaffold 规划思路，按 Mojmap 重写 |
| `util/MovementPlanner.java` | 标准化八向输入、世界方向速度、平滑转向和水平限速 | LiquidBounce/FrogClient MovementUtil 思路，按 Mojmap Vec3 重写 |
| `util/AttributeValuePlanner.java` | 按 UUID 排除指定属性修饰符并复现原版三阶段属性计算 | NoSlow 主手负移动速度属性旁路 |
| `util/WorldToScreen.java` | AABB 八角屏幕投影与裁剪 | FrogClient ESP 思路，按 JOML/Forge 重写 |
| `util/NameTagRenderState.java` | 名称、生命、延迟、装备和耐久不可变渲染状态 | LiquidBounce NameTag RenderState 思路 |
| `util/render/RenderScope.java` | 异常安全恢复 GL/FBO/BufferBuilder 状态 | EMC RenderStack 思路，按 Mojmap 重写 |
| `util/render/PostEffectQueue.java` + `hacks/TargetShaderHack.java` | 独立 FBO 任务分组、四种 PostChain 效果及 Alpha 合成 | FrogClient ShaderManager 思路，不引入 Satin |
| `util/ScreenRegistry.java` | 原版与客户端屏幕识别、创建和打开 | EMC ScreenRegistry 思路，按 Forge 屏幕类型重写 |
| `altmanager/credentials/` | Windows/macOS/Linux 原生凭据主密钥 | LabyMod |
| `util/render/AsyncTextureLoader.java` | 后台解码、渲染线程上传的纹理管线 | LabyMod |
| `util/render/ThreadLocalPixelBuffer.java` | 每线程可增长直接像素缓冲 | LabyMod |
| `util/render/EntityOcclusionCuller.java` | 异步 GPU 查询与缓存单位立方体 VBO | LabyMod |
| `util/NbsSong.java` | 有边界校验的旧版及 NBS v1-v5 解析器 | 项目重构 |

## 重构文件导航

### 事件与搜索

| 文件 | 当前职责 |
| --- | --- |
| `event/EventManager.java` | 接口监听器 + LambdaMetafactory 双路分发，支持事件继承 |
| `clickgui2/FeatureMenuSupport.java` | 集成模糊搜索退路 |
| `clickgui2/ClickGuiScreen.java` | 委托到统一搜索逻辑 |

### 按键绑定

| 文件 | 当前职责 |
| --- | --- |
| `keybinds/KeybindProcessor.java` | 支持 TOGGLE/HOLD/SMART 三种模式 + 按键释放追踪 |

### 战斗

| 文件 | 当前职责 |
| --- | --- |
| `util/CombatTargetUtils.java` | AABB 最近点距离、全局过滤、四种优先级、评分和数量限制 |
| `util/CombatClickScheduler.java` | CPS 区间、点击技术、原版武器冷却、点击前瞻和成功点击计时 |
| `util/CombatAimPointPlanner.java` | 预测 AABB 多点采样、可见/穿墙独立距离与最小转角命中点选择 |
| `util/EntityUtils.java` | IS_ATTACKABLE 自动过滤 AntiBot 检测的假人 |
| `settings/AttackSpeedSliderSetting.java` | 单调时钟攻击调度 |
| `util/TargetTracker.java` | LiquidBounce 风格的黏性目标、切换冷却和评分优势阈值 |
| `util/CombatActionPolicy.java` | Criticals 的冷却、移动状态与异常状态纯策略检查 |
| `hacks/KillauraHack.java` | LiquidBounce Nextgen Forge 1.20.1 行为等价链：原版距离加成、随机扫描范围、预测命中点、逐 Tick 点击数组、ItemCooldown、Normal/Snap/OnTick、Enemy/All Raycast、Requirements、库存模拟关闭、FailSwing、完整 AutoBlock 状态、KeepSprint、逐点击复验和随交互距离缩放的主题色范围光环 |
| `hacks/KillauraLegitHack.java` | 共享目标评分、客户端鼠标转向和执行前复验 |
| `hacks/AimAssistHack.java` | 双轴辅助瞄准、平滑曲线、目标黏性与切换优势 |
| `hacks/CriticalsHack.java` | 攻击前位置包、六种 Packet profile、NoGround/Jump/MiniJump 与停止冲刺同步 |
| `hacks/ReachHack.java` + `mixin/GameRendererMixin.java` | 实体/方块双距离射线与按命中类型裁剪 |
| `hacks/AntiBotHack.java` | 每 Tick 重算的 Tab、状态、UUID、名称和实体合法性组合谓词 |
| `hacks/CrystalAuraHack.java` + `util/CrystalAuraPlanner.java` | 单 Tick 单动作、伤害优势/反自杀/年龄过滤、无效水晶旁路和两格实体占用检查 |
| `hacks/NoVelocityHack.java` + `util/VelocityPlanner.java` | LiquidBounce Modify/JumpReset 模式、触发过滤、动量保留及无反射爆炸击退修改 |
| `hacks/ScaffoldWalkHack.java` | 预测落点并执行评分后的 PlacementPlan |
| `hacks/AutoTotemHack.java` / `AutoArmorHack.java` | 原子副手交换、耐久/诅咒/鞘翅策略与队列化换装 |
| `hacks/AnchorAuraHack.java` + `util/DamageUtils.java` | PLACE/CHARGE/DETONATE 分阶段锚爆、状态确认与完整爆炸减伤计算 |
| `ai/SpiderPathPlanner.java` + `WalkPathProcessor.java` | 识别可攀爬墙面并执行 Spider 垂直路径节点 |
| `hacks/MultiAuraHack.java` + `util/MultiTargetAttackPlanner.java` | FDPClient Multi 模式 + LiquidBounce Tick Clicker 的 Forge 1.20.1 等价链：12 种优先级、随机/扫描/穿墙/疾跑距离、预测旋转、Normal/Strict Raytrace、0=无限目标、逐点击世界顺序复验、库存模拟关闭、FailSwing、KeepSprint、不污染本地使用物品状态的 Packet/Fake SmartBlock，以及按真实最大攻击范围缩放的主题色光环 |
| `hacks/ClickAuraHack.java` | 点击/长按统一调度 |
| `hacks/TriggerBotHack.java` | 准星目标共享验证、冷却/武器/攻击键条件与物品使用策略 |
| `hacks/HitboxesHack.java` + `mixin/EntityMixin.java` + `util/HitboxExpansionPolicy.java` | 仅扩展客户端世界中的非本地目标碰撞箱，隔离单人集成服务器玩家碰撞；判定移出 Mixin 专用包，避免 Mixin 0.8.5 方法可见性与包隔离错误 |

### 移动模块

| 文件 | 当前职责 |
| --- | --- |
| `hacks/AutoSprintHack.java` + `mixin/ClientPlayerEntityMixin.java` | 原版疾跑状态机完成后、实际移动前按本 Tick 输入应用全方向疾跑，并处理饥饿、碰撞、物品使用、潜行、失明和状态所有权 |
| `hacks/KeepSprintHack.java` + `mixin/PlayerMixin.java` + `util/KeepSprintPolicy.java` | 只保留攻击前已经存在的疾跑，不主动启动疾跑；攻击时原位保留 100% 水平速度并阻止原版取消疾跑；策略移出 Mixin，避免 Mixin 0.8.5 方法可见性错误 | LiquidBounce/FDP Mixin 思路，按 Forge 1.20.1 Mojmap 重写 |
| `hacks/SpeedHackHack.java` + `util/MovementPlanner.java` | 五种速度模式、标准化八向速度、空中平滑转向与阶段化 LowHop |
| `hacks/FlightHack.java` | 三种飞行模式、滑翔、AntiKick、旁观模式隔离和跨玩家飞行状态恢复 |
| `hacks/NoFallHack.java` + `mixin/ServerboundMovePlayerPacketMixin.java` | 四种跌落保护模式、阈值触发、GroundSpoof 与确认发送后距离重置 |
| `hacks/NoSlowdownHack.java` + `mixin/EntityMixin.java` + `mixin/LivingEntityMixin.java` | 物品动作减速、六类方块旁路和主手负移动速度属性重算 |
| `hacks/StepHack.java` | 一至五格 Simple 步高、Legit 分段包、原步高恢复与冷却 |
| `hacks/SafeWalkHack.java` + `mixin/ClientPlayerEntityMixin.java` | 条件化边缘裁剪、可见潜行与真实按键恢复 |
| `hacks/ScaffoldWalkHack.java` | 预测落点评分、静默换槽恢复、InteractionResult 确认、内置 SafeWalk 与 Tower 搭柱 |
| `hacks/InvWalkHack.java` + `util/ScreenRegistry.java` | 统一识别客户端主界面、设置子页、HUD 编辑器和选项页，保持单人集成服务器运行并恢复真实移动键状态 |

### ESP 与 GUI

| 文件 | 当前职责 |
| --- | --- |
| `util/EntityEspRenderer.java` | 实体 ESP 填充、描边、追踪线批处理和近距平滑淡出 |
| `util/WorldToScreen.java` | 世界 AABB 八角投影到 GUI 缩放坐标 |
| `hacks/PlayerEspHack.java` | 3D 批处理或 2D 边框、生命条、护甲耐久条 |
| `hacks/PlayerHaloHack.java` + `util/render/PlayerHaloRenderer.java` | ClickGUI/Navigator 共用开关、实体快照批处理、主题色头顶光环与深度遮挡 |
| `hacks/NameTagsHack.java` | 每 Tick 发布不可变玩家标签状态并控制装备/耐久显示 |
| `hacks/MobEspHack.java` | 生物过滤、颜色模式、距离限制和近距淡出设置 |
| `clickgui2/ClickGuiScreen.java` | 右 Ctrl 抗锯齿小圆角多浮窗模板 |
| `clickgui2/SettingTreeLayout.java` + `SettingsWindow.java` | 浮窗设置的稳定组件绑定；仅在展开层级或条件可见项变化时刷新布局 |
| `clickgui2/GuiPreferences.java` | 客户端指令开关、字体启用与选择、五类全局目标偏好 |
| `clickgui2/NavigatorScreen.java` | 右 Shift 居中面板、侧栏分类、单列模块、搜索和内嵌设置 |
| `clickgui2/NavigatorSettingsPanel.java` | Navigator 内嵌设置稳定绑定，普通参数变化不销毁拖动和弹层状态 |
| `hacks/RadarHack.java` + `clickgui2/components/RadarComponent.java` | Radar 实体快照、范围/旋转设置，以及与 ClickGUI 同款的深黑主题面板、圆形扫描环、圆角实体标记和状态栏 |
| `clickgui2/ClickGui.java` + `hud/IngameHUD.java` | 持久注册 Radar 窗口，并在菜单外单独绘制可见的固定窗口 |
| `clickgui2/FlatRenderer.java` | 实心面板、弹窗、控件、滑轨、圆角和阴影绘制 |
| `clickgui2/theme/FlatTheme.java` | 黑色浮窗、`#006366` 强调色、文字和交互状态配色 |
| `gui/title/WurstTitleMenu.java` | 原版全景上的响应式实心深色主菜单 |
| `mixin/ClientConnectionMixin.java` + `util/ClientConnectionPolicy.java` | 仅在客户端 `CLIENTBOUND` 连接派发收发包事件，跳过单人集成服务器 `SERVERBOUND` 连接；方向判定移出 Mixin 专用包 |

### 设置

| 文件 | 当前职责 |
| --- | --- |
| `settings/Setting.java` | 任意深度父子树、单一归属和循环检查 |
| `clickgui2/SettingsWindow.java` | 递归子设置缩进渲染与折叠 |
| `clickgui2/Component.java` | 支持 indent 属性 |
| `clickgui2/Window.java` | validate() 使用缩进布局 |
| `clickgui2/components/CheckboxComponent.java` | 展开/折叠箭头按钮 |

## 资源索引

| 路径 | 说明 |
| --- | --- |
| `src/main/resources/META-INF/mods.toml` | 当前 Forge 模组元数据，模组 ID 为 `wurstpenguin` |
| `src/main/resources/wurst.mixins.json` | 69 个客户端 Mixin 和 `wurstpenguin-refmap.json` 配置 |
| `src/main/resources/META-INF/accesstransformer.cfg` | 32 条 Forge SRG 访问转换 |
| `src/main/resources/assets/wurst/` | 兼容原资源标识的图像和图标命名空间 |
| `src/main/resources/assets/wurst/shaders/core/` | 液态玻璃顶点、片元和 shader 描述资源 |
| `src/main/resources/assets/wurst/shaders/program/` | LSD 视觉功能使用的 PostPass shader |
| `src/main/resources/assets/wurst/translations/` | 16 个语言 JSON、WurstCN 原始名称表和中文功能名资源 |
| `src/main/resources/assets/minecraft/font/` | CozyUI+ v1.10 的 `default` 与 `uniform` 位图字体定义 |
| `src/main/resources/assets/minecraft/textures/font/` | 19 张字体图集 |
| `src/main/resources/META-INF/licenses/cozyui/` | CozyUI 归属说明及许可证 |

## 外部参考包

| 文件 | 说明 |
| --- | --- |
| `source/Wurst7_1_20_1_src/` | 原始 Wurst7 Fabric 源码 (604 文件) |
| `source/WurstCN_main_src/` | WurstCN 中文化 Fork 源码 |
| `source/bleachhack_src/` | BleachHack Fabric 源码 (208 文件) |
| `source/meteor_client_master_src/` | Meteor Client Fabric 源码 (950 文件) |
| `source/liquidbounce_src/` | LiquidBounce Nextgen 源码 (1608 文件) |
| `source/FDPClient_main_src/` | FDPClient Kotlin 源码 (1071 文件) |
| `source/RavenBS_Plus_Plus_main_src/` | RavenBS++ Forge 源码 |
| `source/sinka_decompiled/` | Sinka 客户端反编译 JAR |
| `source/labymod_full/` | LabyMod 旧版 Forge 反编译源码 |
| `source/Aristois_1.20.1_forge/` | Aristois + EMC 1.20.1 Forge 反编译源码，共 449 个 Java 文件；未发现许可证文件 |
| `source/FrogClient/` | FrogClient Fabric 1.21.1 完整项目，共 441 个 Java 文件；MIT 许可证 |
| `clean-keystrokes-1.20.0-4.zip` | Clean Keystrokes HUD 源码参考 |
| `YetAnotherConfigLib-main.zip` | YACL 设置模型参考，不作为活动依赖 |
| `xaero-minimap-translations-main.zip` | Xaero Minimap 的 CC0 翻译文本，仅用于中文术语参考；不包含地图源码、纹理或渲染实现 |
| `journeymap-legacy-master.zip` | JourneyMap Legacy 参考源码；仅用于研究瓦片缓存、分层绘制和标签布局，不直接复制其旧版渲染代码或主题资源 |

## 新增源码审阅

### Aristois / EMC

`source/Aristois_1.20.1_forge/` 共 451 个文件，其中 Aristois 40 个 Java 文件、EMC 409 个 Java 文件。EMC 通过 `@Mod("emc")` 和 `FMLClientSetupEvent` 初始化 Forge 客户端，并在启动时为主 RenderTarget 启用 stencil，版本和加载器方向与当前工程接近。

| 子系统 | 规模/入口 | 审阅结论 |
| --- | --- | --- |
| 事件 | `framework/event/`，63 个 Java 文件 | 支持优先级、继承类方法扫描和渲染异常后的 BufferBuilder 清理；当前 `EventManager` 已有 LambdaMetafactory 与继承分发，只值得补充统一渲染恢复边界 |
| 实体抽象 | `framework/entity/`，27 个 Java 文件 | 多层 wrapper 适合跨版本框架，当前项目固定 Forge 1.20.1，整体引入会增加无效间接层 |
| 网络 | `framework/network/`，23 个 Java 文件 | 适合平台框架，不替换当前 Forge/Mixin 数据包事件链 |
| 渲染 | `framework/render/`，19 个 Java 文件 | `RenderStack` 将 quad/line/cube/font 顶点集中提交，批次生命周期值得借鉴；混淆方法名和共享 BufferBuilder 不可直接复制 |
| GUI | `framework/gui/`，16 个 Java 文件 | `ScreenRegistry`、`Component`、`SelectableList` 提供统一屏幕和控件抽象；当前 ClickGUI2 已有更完整的窗口、设置树和双界面体系 |
| Shader/FBO | `Shader`、`Framebuffer`、`EntityShader` | 基于 Minecraft `PostChain` 与命名 RenderTarget，适合研究资源重载和 resize 生命周期；当前已按需求移除玻璃 GUI，不重新引入模糊管线 |

可移植重点是增加一个 Forge/Mojmap 原生的渲染批次作用域：保证 begin/end 成对、异常时关闭正在构建的 BufferBuilder，并在 `finally` 中恢复 blend、depth、line width 和 framebuffer。由于目录为 CFR 反编译结果且未发现许可证文件，只允许行为级研究，不复制大段实现或资源。

### FrogClient

`source/FrogClient/` 共 484 个文件、441 个 Java 文件，包含 134 个模块实现、70 个 Mixin、53 个事件和 12 个设置相关类。它是 Minecraft 1.21.1、Fabric Loader 0.16.10、Yarn、Java 21 工程，并依赖 Satin、Sodium、Baritone 与 Nether Pathfinder；许可证为 MIT。

| 子系统 | 参考价值 | Forge 1.20.1 处理方式 |
| --- | --- | --- |
| Module/Setting | 条件可见性、父级折叠、页面分组、颜色附加开关 | 当前 `Setting` 已支持任意深度父子树和条件可见性，只参考页面分组与组合控件，不降级为 Frog 的单层 `BooleanSupplier` 模型 |
| HUD | 信息项排序、上下方向渲染、统一字体/阴影/间距选项 | 当前 HUD2 已是可拖动独立元素体系；可增加 TPS、速度、服务器信息等独立元素，不回退到单个巨型 HUD 模块 |
| ESP | 2D 屏幕包围盒、生命条、护甲耐久；3D 实体/方块实体分类颜色 | 扩展现有 `EntityEspRenderer` 的 2D 投影层与附属条目，继续使用 Mojmap、Forge 渲染事件和现有批处理 |
| ShaderManager | 先把目标绘制到独立 FBO，再统一执行 PostEffect | 仅供实体轮廓特效研究；Satin API、Yarn 名称和 Fabric Mixin hook 不进入活动依赖 |
| BlurManager | 矩形区域 uniform 裁剪 | 当前设计已放弃 glass GUI，不迁移背景模糊 |
| EventBus | LambdaMetafactory、优先级和 CopyOnWriteArrayList | 当前事件系统功能更完整，Frog 只按事件精确类型分发且无统一异常隔离，不替换现有实现 |
| 实体快照 | tick 后复制实体/玩家列表供渲染和后台计算读取 | 可按需求引入每 tick 不可变快照，但必须使用有界执行器和明确生命周期 |

明确不采用的实现：`ThreadManager.ClientService` 在空闲时持续 `Thread.onSpinWait()`，会长期占用 CPU；`ShaderManager` 的 FBO 切换缺少完整 `try/finally` 状态恢复；静态可变着色任务列表不是线程安全队列；`ColorSetting.getValue()` 在读取时修改值，破坏只读语义；事件总线不支持父事件分发。这些部分只能作为反例或重写依据。

### 移植优先级

核心模块替换顺序固定为：LiquidBounce（水影）优先；Aristois 与 FrogClient 同级补充；前三者缺失或实现不适配时才使用 Meteor。GPL/MIT 源码保留来源与许可证声明；无许可证反编译源码只核对行为，不直接复制。战斗模块共享目标追踪、评分和库存动作队列；移动模块共享 `MovementPlanner`，NoFall 使用移动包 accessor，NoSlowdown 通过 Entity/Block Mixin 处理卡滞与速度因子。全部接入层均按 Forge 1.20.1 Mojmap 重写。

1. **已完成**：`RenderScope` 已为每个 GUI/3D 监听器隔离 GL、FBO 和共享 BufferBuilder 状态。
2. **已完成**：PlayerESP 已增加可选 2D AABB 投影、生命条和护甲耐久条，并保持单次 GUI 批次提交。
3. **已完成**：TPS、速度、服务器、时间与坐标换算均为可独立拖动开关的 HUD2 元素。
4. **已完成**：每 Tick 不可变实体快照已供 PlayerESP、MobESP、ItemESP 使用。
5. **已完成**：`TargetShader` 使用原生 Forge/Mojmap FBO 与 PostChain，不依赖 Satin。
6. **排除**：Satin/Sodium/Fabric 依赖、玻璃 GUI 模糊、无限自旋后台线程和反编译代码直接复制。

## Forge 平台边界

- `WurstForgeInitializer` 使用 `FMLClientSetupEvent` 初始化，通过 `RegisterClientCommandsEvent` 注册客户端 Brigadier 命令，由 `RenderGuiEvent.Post` 单路派发 HUD；攻击监听由 `ClientPlayerInteractionManagerMixin` 在攻击包发送前派发，`IngameHudMixin` 仅保留遮罩控制。
- 全部 729 个活动 Java 文件使用 Mojang 官方映射。
- `PlatformUtils` 直接调用 Forge `ModList` 和 `FMLLoader`。
- Fabric 入口、Fabric E2E 测试、Indigo Mixin、Fabric 元数据和 Access Widener 已从项目删除。
- `EventManager` 使用变更时监听器快照与 `LongAdder` 计数，LambdaMetafactory 注解路径消除反射。
- `JsonUtils.toJson` 使用 `ATOMIC_MOVE` 原子文件替换。
- 69 个 Mixin 目标由 MixinGradle 生成 `wurstpenguin-refmap.json`，本轮编译验证通过。
- 32 条 Access Transformer 已对照 Forge 47.4.10 SRG JAR 验证。
- 原 Wurst 遥测、官网入口和远程更新检查已从活动路径移除。
- 原 `net.wurstclient.clickgui` 与 `net.wurstclient.navigator` 源码包已经删除，两套新 GUI 均位于 `net.wurstclient.clickgui2`。

Killaura 与 MultiAura 的攻击冷却现在通过 `CombatActionPolicy` 区分原版真实 `1..10` Tick 挥空冷却和 Screen 设置的 `missTime=10000` 哨兵，打开 ClickGUI、Navigator、HUD 编辑器或其他界面时不再被永久跳过攻击。

项目版本为 `1.5.0`。Forge 47.4.10、Java 17、Gradle 8.11。50 个测试文件中的 134 项测试、完整构建、重混淆、JarJar 打包、关键 Mixin 方法可见性及 Mixin 包隔离检查通过；69 个 Mixin 已写入发布包。KeepSprint 的 `PlayerMixin` 已改用原生 `@Redirect`，只保留攻击前已经存在的疾跑，不再逐 Tick 主动启动疾跑，发布包也不引用运行时生成的 Mixin `Args` 类。发布产物为 `build/libs/WurstB+ Plus-v1.5.0-MC1.20.1-all.jar`（29,094,213 bytes），SHA-256 为 `E8FD5AC885FA160007245ABEB08DBB902C3135AF5FC30BC22741E3D4F8095FB8`。

`versions/1.21.1/` 保持相同的 `1.5.0` 功能版本，使用 Minecraft 1.21.1、Forge 52.1.16、Mojang 官方映射、Java 21、Gradle 8.11、Mixin 0.8.7 和 Baritone Forge 1.11.2。该工程已完成 GUI、渲染、动态注册表、数据组件、药水、属性、伤害、网络、假玩家、HUD、Forge 事件和 Mixin 目标迁移；HUD 事件由 `IngameHudMixin` 在原版 `Gui.renderTabList` 层单路派发，避免 Forge 自定义覆盖层注册失效导致全部 HUD 消失。135 项测试、`clean build jarJar` 和客户端启动验证通过。发布产物为 `versions/1.21.1/build/libs/WurstB+ Plus-v1.5.0-MC1.21.1-all.jar`（30,129,813 bytes），SHA-256 为 `406D985F55B9A76F09648058315BC91F3CDB0D4AA8F29AF53C77770851A599A5`。
