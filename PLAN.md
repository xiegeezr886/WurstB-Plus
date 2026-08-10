# WurstB+ Plus v1.6 功能移植计划

基于 Vape V4.21、OpenMyau、LiquidBounce Nextgen 三个参考客户端的源码分析，列出 WurstB+ v1.5 完全不具备的功能，按类别排序，标明出处，作为 v1.6 及后续版本的功能规划。

> 总计约 **180+** 个独有功能待移植。

---

## 战斗 Combat

| # | 功能 | Vape | Myau | LiquidBounce | 说明 |
|---|------|:---:|:----:|:-----------:|------|
| 1 | **SilentAura** | ✅ | | | 静默自瞄，头部不转但实际攻击旋转已通过数据包发送 |
| 2 | **Backtrack / LagRange** | ✅ | ✅ | | 延迟回溯攻击：记录玩家历史位置，攻击过去的坐标实现延迟打击 |
| 3 | **WTap** | ✅ | ✅ | ✅ | 攻击后自动松W再按，重置疾跑增加击退 |
| 4 | **HitSelect** | ✅ | ✅ | | 选择性取消攻击包（跳打/暴击/WTap模式） |
| 5 | **MoreKB / SuperKnockback** | | ✅ | ✅ | 增加打出击退（Legit/Packet/DoublePacket/STap模式） |
| 6 | **BlockHit / SwordBlock** | ✅ | | ✅ | 1.7风格格挡攻击 / 1.8剑挡模拟 |
| 7 | **RightClicker** | ✅ | | | 自动右键CPS（盾牌、吃东西、钓竿） |
| 8 | **AutoRod** | | | ✅ | 自动对敌人使用钓竿 |
| 9 | **AutoLeave** | | | ✅ | 血量低于阈值自动断开 |
| 10 | **TimerRange** | | | ✅ | 靠近敌人时加速游戏刻 |
| 11 | **AutoShoot** | | | ✅ | 自动射箭/弩，含弹道预测 |
| 12 | **SpearKill** | | | ✅ | 自动用三叉戟蓄力攻击 |
| 13 | **MaceKill** | | | ✅ | 伪造高空坠落增强重锤伤害 |
| 14 | **NoMissCooldown** | | | ✅ | 禁用攻击未命中的10tick冷却 |
| 15 | **AntiFireball** | | ✅ | | 自动击回火球 |
| 16 | **TickBase** | | | ✅ | 战斗加速刻调用 |

---

## 移动 Movement

| # | 功能 | Vape | Myau | LiquidBounce | 说明 |
|---|------|:---:|:----:|:-----------:|------|
| 17 | **OmniSprint** | ✅ | | | 360度全方向疾跑 |
| 18 | **Phase** | ✅ | | ✅ | 穿墙（多服务器模式绕过） |
| 19 | **Eagle** | | ✅ | ✅ | 自动潜行在方块边缘（搭路辅助） |
| 20 | **Freeze** | | ✅ | ✅ | 原地冻结+幽灵移动模拟 |
| 21 | **Strafe** | ✅ | | ✅ | 移动优化控制器 |
| 22 | **LongJump** | ✅ | | ✅ | 远跳增强 |
| 23 | **ReverseStep** | | | ✅ | 快速下台阶 |
| 24 | **EntityControl** | | | ✅ | 移动键控制骑乘实体 |
| 25 | **BlockWalk** | | | ✅ | 在非完整方块上行走 |
| 26 | **BlockBounce** | | | ✅ | 自动在粘液块上弹跳 |
| 27 | **AvoidHazards** | | | ✅ | 自动躲避火焰/仙人掌/岩浆 |
| 28 | **AirJump** | | | ✅ | 空中跳跃 |
| 29 | **SnapTap** | | | ✅ | SOCD键盘清理（后按优先） |
| 30 | **NoPush** | | | ✅ | 防止实体推动 |
| 31 | **NoPose** | | | ✅ | 防止姿态切换（游泳/爬行） |
| 32 | **VehicleBoost** | | | ✅ | 下坐骑时加速弹出 |
| 33 | **AutoDodge** | | | ✅ | 自动闪避飞行弹射物 |
| 34 | **TerrainSpeed** | | | ✅ | 根据地形自动调节速度 |

---

## 渲染 Render

| # | 功能 | Vape | Myau | LiquidBounce | 说明 |
|---|------|:---:|:----:|:-----------:|------|
| 35 | **Chams** | ✅ | ✅ | | 穿墙实体模型渲染（可见/遮挡双色） |
| 36 | **Animations** | ✅ | | ✅ | 自定义第一人称攻击/格挡/吃动画 |
| 37 | **BedPlates / BedESP** | ✅ | ✅ | ✅ | 床位置发光板/彩色框 |
| 38 | **BedTracker** | | ✅ | | 追踪全图床破坏状态并提示 |
| 39 | **Explosions** | ✅ | | | 水晶/TNT爆炸范围预览球体 |
| 40 | **TargetHUD** | | ✅ | | 目标信息浮窗（血量、盔甲、药水效果） |
| 41 | **SpawnerFinder** | ✅ | | | 扫描刷怪笼方块并用ESP标记 |
| 42 | **Arrows / Indicators** | ✅ | | ✅ | 屏幕边缘方向箭头 / 圆形指示器 |
| 43 | **MurderFinder / MurderMystery** | ✅ | | ✅ | 密室杀手模式检测凶手 |
| 44 | **ItemChams** | | | ✅ | 掉落物穿墙渲染 |
| 45 | **CrystalView** | | | ✅ | 自定义末影水晶显示（大小/颜色） |
| 46 | **CombineMobs** | | | ✅ | 附近怪物视觉合并优化 |
| 47 | **BlockOutline** | | | ✅ | 准星目标方块线框轮廓 |
| 48 | **VoidESP** | | | ✅ | 虚空洞标记 |
| 49 | **TNTTimer** | | | ✅ | 点燃TNT倒计时显示 |
| 50 | **DamageParticles** | | | ✅ | 自定义攻击粒子效果 |
| 51 | **HitFX** | | | ✅ | 自定义攻击视觉效果 |
| 52 | **JumpEffect** | | | ✅ | 自定义跳跃粒子效果 |
| 53 | **Particles** | | | ✅ | 通用自定义粒子系统 |
| 54 | **ProtectionZones** | | | ✅ | 显示WorldGuard等保护区 |
| 55 | **MobOwners** | | | ✅ | 显示驯服生物主人 |
| 56 | **ItemTags** | | | ✅ | 世界内显示物品附魔标签 |
| 57 | **CustomAmbience** | | | ✅ | 自定义环境光照/色彩 |

---

## 渲染·HUD

| # | 功能 | Vape | Myau | LiquidBounce | 说明 |
|---|------|:---:|:----:|:-----------:|------|
| 58 | **Keystrokes** | ✅ | | | 按键可视化（WASD/跳跃/左右键，多风格） |
| 59 | **ArmorStatus** | ✅ | | | 盔甲槽耐久HUD |
| 60 | **CPSMod** | ✅ | | | CPS每秒点击数显示 |
| 61 | **FPS Display** | ✅ | | | 帧率HUD |
| 62 | **Coordinates** | ✅ | | | XYZ坐标显示 |
| 63 | **Clock** | ✅ | | | 模拟/数字时钟+日期 |
| 64 | **Compass** | ✅ | | | 指南针HUD |
| 65 | **PotionEffects** | ✅ | | | 药水效果列表+时长条+过滤 |
| 66 | **ReachDisplay** | ✅ | | | 上次攻击距离显示（方块单位） |
| 67 | **Scoreboard** | ✅ | | | 自定义计分板（单行可控） |
| 68 | **Health** | ✅ | | | 血量HUD（多格式） |
| 69 | **BlockOverlay** | ✅ | | | 准星方块线框 |
| 70 | **BlockhitAnimation** | ✅ | | | 格挡状态可视指示 |
| 71 | **FreeLook** | ✅ | | ✅ | 自由视角（身体不动） |
| 72 | **InventoryBlur** | ✅ | | | 背包/容器模糊背景 |
| 73 | **TimeChanger** | ✅ | | | 客户端修改世界时间 |
| 74 | **WeatherChanger** | ✅ | | | 客户端修改天气 |
| 75 | **BlockRenderColorOverride** | ✅ | | | 全局方块染色 |
| 76 | **SmoothCamera** | | | ✅ | 平滑镜头 |
| 77 | **QuickPerspectiveSwap** | | | ✅ | 快速切换视角 |
| 78 | **AutoF5** | | | ✅ | 自动第三人称切换 |
| 79 | **NoFov** | | | ✅ | 禁用FOV变化 |
| 80 | **NoBob** | | | ✅ | 禁用视角晃动 |
| 81 | **NoSwing** | | | ✅ | 禁用挥臂动画 |
| 82 | **SkinChanger** | | | ✅ | 本地/Mojang API换肤 |
| 83 | **SilentHotbar** | | | ✅ | 客户端伪装热键栏槽位 |
| 84 | **Rotations** | | | ✅ | 旋转变化可视化 |
| 85 | **Zoom** | | | ✅ | 光学变焦望远镜 |

---

## 玩家/工具 Player/Utility

| # | 功能 | Vape | Myau | LiquidBounce | 说明 |
|---|------|:---:|:----:|:-----------:|------|
| 86 | **InvCleaner** | ✅ | | | 高级背包清理（自定义过滤规则/附魔匹配/预设） |
| 87 | **InventoryManager** | ✅ | ✅ | ✅ | 自动整理背包+穿甲+丢垃圾 |
| 88 | **ArmorSwitch** | ✅ | | | 上下文感知换甲（着火换火保、爆炸换爆保） |
| 89 | **AutoPearl** | ✅ | | ✅ | 自动末影珍珠（瞄准锁定/投掷轨迹） |
| 90 | **MLG** | ✅ | | | 落地水/落地桶 |
| 91 | **Clutch** | ✅ | | | 掉落自动脚下放方块 |
| 92 | **Regen / TimeShift** | ✅ | | ✅ | 数据包快速回血/效果清除 |
| 93 | **GhostHand** | | ✅ | ✅ | 穿实体放方块/使用物品 |
| 94 | **AntiDebuff** | ✅ | ✅ | | 移除负面药水效果（失明/反胃） |
| 95 | **AntiFML** | ✅ | | | 屏蔽Forge握手包 |
| 96 | **AntiObfuscate** | | ✅ | | 去掉聊天混淆字符（§k） |
| 97 | **AntiObbyTrap** | | ✅ | | 检测并挖开黑曜石困住 |
| 98 | **AutoAnduril** | | ✅ | | UHC自动合成Anduril速度剑 |
| 99 | **InventoryClicker** | | ✅ | | 背包内自动连点（合成辅助） |
| 100 | **LightningTracker** | | ✅ | | 追踪闪电位置并报告方向+距离 |
| 101 | **NickHider** | | ✅ | | 隐藏真实用户名（聊天/计分板/Tab/告示牌） |
| 102 | **NoRotate / NoRotateSet** | | ✅ | ✅ | 阻止服务端旋转覆盖 |
| 103 | **Spammer** | | ✅ | ✅ | 定时自动发送聊天消息+unicode随机化 |
| 104 | **WindCharge / AutoWindCharge** | ✅ | | ✅ | 自动使用风弹 |
| 105 | **SelfDestruct** | ✅ | | | 紧急自毁隐藏客户端痕迹 |
| 106 | **MouseDelayFix** | ✅ | | | 修复1.8鼠标输入延迟 |
| 107 | **AntiHunger** | | | ✅ | 防止饥饿减少（伪造服务端疾跑状态） |
| 108 | **PingSpoof** | | | ✅ | 伪造延迟（延迟keep-alive包） |
| 109 | **Disabler** | | | ✅ | 反作弊绕过（多种反作弊+BDS） |
| 110 | **AutoRespawn** | | | ✅ | 自动重生 |
| 111 | **AutoCrafter** | | | ✅ | 自动化合成器方块 |
| 112 | **AntiExploit** | | | ✅ | 防护服务端漏洞攻击 |
| 113 | **PotionSpoof** | | | ✅ | 客户端伪药水效果 |
| 114 | **NoSlotSet** | | | ✅ | 阻止服务端设置热键栏位置 |
| 115 | **FastExp** | | | ✅ | 使用经验瓶自动修甲 |
| 116 | **ChestCleaner** | | | ✅ | 自动丢弃箱子内不需要的物品 |
| 117 | **ItemScroller** | | | ✅ | 自动滚动背包物品 |
| 118 | **BookBot** | | | ✅ | 自动写书 |
| 119 | **AutoDisable** | | | ✅ | 特定事件自动禁用模块 |

---

## 世界/建筑 World

| # | 功能 | Vape | Myau | LiquidBounce | 说明 |
|---|------|:---:|:----:|:-----------:|------|
| 120 | **BedBreaker** | ✅ | | | 起床自动拆床（寻路+挖防御+预览） |
| 121 | **BedNuker** | | ✅ | | 快速挖掘床 |
| 122 | **BlockIn / AutoBlockIn** | | | ✅ | 脚下方块围堵自己 |
| 123 | **BlockTrap** | | | ✅ | 敌人脚下放方块困住 |
| 124 | **AutoTrap** | | | ✅ | 点燃+蜘蛛网困住目标 |
| 125 | **Fucker** | | | ✅ | 破坏/使用选中方块（床破坏器/ID Nuker） |
| 126 | **StrongholdFinder** | | | ✅ | 末影之眼贝叶斯推算要塞位置 |
| 127 | **ProjectilePuncher** | | | ✅ | 自动击回弹射物（火球/潜影贝子弹） |
| 128 | **LiquidFiller** | | | ✅ | 自动填充附近液体方块 |
| 129 | **LiquidPlace** | | | ✅ | 允许在液体上方放置方块 |
| 130 | **AirPlace** | | | ✅ | 允许在空中放置方块 |
| 131 | **PacketMine** | | | ✅ | 数据包静默挖掘 |
| 132 | **NoInterpolation** | | | ✅ | 减少实体插值平滑度 |
| 133 | **NoSlowBreak** | | | ✅ | 挖掘疲劳/空中/水下自动调整速度 |
| 134 | **Extinguish** | | | ✅ | 着火时自动放水灭火 |
| 135 | **BedDefender** | | | ✅ | 多层方块保护床 |

---

## 漏洞利用 Exploit

| # | 功能 | Vape | Myau | LiquidBounce | 说明 |
|---|------|:---:|:----:|:-----------:|------|
| 136 | **Dupe** | | | ✅ | 物品复制漏洞（Paper 1.20.4+） |
| 137 | **ServerCrasher** | | | ✅ | 服务端崩溃漏洞（Bundle/Paper/Log4j/TranslationSign等） |
| 138 | **MoreCarry / XCarry** | | | ✅ | 合成槽存储物品 |
| 139 | **Kick** | | | ✅ | 多种方式自行断开 |
| 140 | **Clip** | | | ✅ | 数据包穿墙传送 |
| 141 | **ClickTp** | | | ✅ | 点击传送 |
| 142 | **VehicleOneHit** | | | ✅ | 船/矿车一击破坏 |
| 143 | **Plugins** | | | ✅ | 获取服务端插件列表 |
| 144 | **Damage** | | | ✅ | 自伤模块 |
| 145 | **MultiActions** | | | ✅ | 同时执行多个操作 |
| 146 | **SleepWalker** | | | ✅ | 睡觉时还能走动 |

---

## 宏/杂项 Misc/Macro

| # | 功能 | Vape | Myau | LiquidBounce | 说明 |
|---|------|:---:|:----:|:-----------:|------|
| 147 | **CommandMacro** | ✅ | | | 按键触发聊天命令宏 |
| 148 | **ItemMacro** | ✅ | | | 按键切换指定物品+右键 |
| 149 | **FishingRodMacro** | ✅ | | | 一键钓鱼竿切换+抛竿 |
| 150 | **Macros** | | | ✅ | 通用宏按键系统 |
| 151 | **Teams** | | | ✅ | 多人协调队伍系统 |
| 152 | **TargetLock** | | | ✅ | 锁定特定目标 |
| 153 | **MiddleClickAction** | | | ✅ | 自定义中键操作（加好友/目标等） |
| 154 | **AutoConfig** | | | ✅ | 每个服务器自动加载配置 |
| 155 | **AutoAccount** | | | ✅ | 自动账户登录管理 |
| 156 | **AntiStaff** | | | ✅ | 检测管理员并自动禁用模块 |
| 157 | **AntiCheatDetect** | | | ✅ | 检测服务端使用的反作弊 |
| 158 | **ReportHelper** | | | ✅ | 自动化 /report 命令 |
| 159 | **Notifier** | | | ✅ | 事件通知（玩家靠近等） |
| 160 | **FlagCheck** | | | ✅ | 反作弊标记检查 |
| 161 | **GUICloser** | | | ✅ | 条件触发自动关闭GUI |
| 162 | **AutoChatGame** | | | ✅ | 自动玩聊天游戏 |
| 163 | **DebugRecorder** | | | ✅ | 记录调试数据用于Bug报告 |
| 164 | **ElytraSwap** | | | ✅ | 掉落自动换鞘翅 |
| 165 | **NameCollector** | | | ✅ | 收集服务端玩家名称 |

---

## 娱乐 Fun

| # | 功能 | Vape | Myau | LiquidBounce | 说明 |
|---|------|:---:|:----:|:-----------:|------|
| 166 | **Derp** | | | ✅ | 随机旋转头部（看起来像机器人） |
| 167 | **HandDerp** | | | ✅ | 随机移动/摆动你的手 |

---

## 参考源码位置

| 客户端 | 路径 |
|--------|------|
| Vape V4.21 | `source/VapeV4.21-main/`（未解压ZIP） |
| OpenMyau | `source/OpenMyau-main/`（未解压ZIP） |
| OpenOpal | `source/OpenOpal-main.zip` |
| LiquidBounce Nextgen | `source/liquidbounce_src/LiquidBounce-nextgen/` |
| Rise | `source/Rise 6.1.30..zip` |

---

> **合计：167 个独有功能** 待移植到 WurstB+。按优先级分批实施，v1.6 重点攻克前20个核心功能。

---

## 2026-08-10 实际差异复核

原表是候选功能清单，并不等同于当前代码库的真实缺口。后续实施以
`HackList`、现有设置和五套参考源码的逐项复核结果为准，避免重复模块。

### 本批已实现

| 模块 | 参考来源 | 当前实现 |
|------|----------|----------|
| `WTap` | Vape / Myau / LiquidBounce | 概率、选择有效受击、松键和恢复延迟、禁用恢复物理按键 |
| `RightClicker` | Vape | CPS 区间、按住触发、起始延迟、仅手持物品、容器暂停、双手物品使用 |
| `NoMissCooldown` | LiquidBounce | 清除未命中冷却、可选取消空挥 |
| `ProjectilePuncher` | LiquidBounce / Myau | 火球与潜影贝子弹威胁预测、静默旋转、攻击计时、容器暂停 |
| `ReverseStep` | LiquidBounce | Strict / Accelerator、最大落差、跳跃和移动状态门控、危险落点拒绝 |
| `AirJump` | LiquidBounce | 自由跳跃 / 二段跳、按键边沿触发、载具/飞行/流体/攀爬状态门控 |
| `NoRotate` | Myau / LiquidBounce | 分别保留 yaw / pitch，不中断原版位置修正与传送确认 |
| `SuperKnockback` | Myau / LiquidBounce | Packet / Sprint tap、受击时间、概率、移动/地面/流体/暴击门控 |
| `VehicleBoost` | LiquidBounce | 下车边沿触发、水平和垂直速度独立配置、玩家切换状态隔离 |

### 现有功能等价覆盖

| 候选模块 | 当前覆盖 | 处理 |
|----------|----------|------|
| `SilentAura` | `Killaura` 的 Silent rotation | 增加搜索别名，不重复注册 |
| `Eagle` | `SafeWalk` 的 Sneak at edges | 增加搜索别名，不重复注册 |
| `NoPush` | `AntiEntityPush` | 增加搜索别名，不重复注册 |
| `OmniSprint` | `AutoSprint` 的 Omnidirectional Sprint | 设置层面完整覆盖，不重复注册 |

### 原表中已经存在

`AirPlace`、`AutoLeave`、`AutoRespawn`、`AntiHunger`、`AutoTrap`、`Derp`
等模块已在当前 `HackList` 注册，不再重复移植。

### 不作为普通模块移植

`ServerCrasher`、`Dupe`、`Disabler` 等崩服、复制漏洞、协议破坏或纯规避检测
功能不纳入常规模块迁移范围。

`Freeze` 的参考实现依赖暂停客户端 tick、缓存或取消网络通信以及特定检测规避，
不适合以普通移动模块直接移植；在建立统一的数据包队列与生命周期模型前暂缓实现。
