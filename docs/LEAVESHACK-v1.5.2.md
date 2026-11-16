# LeavesHack → WurstB+ Plus v1.5.2

## 范围

来源：[MrBZBZ/LeavesHack](https://github.com/MrBZBZ/LeavesHack)，本地快照 `reference/clients/LeavesHack/`，提交 `13daf96`。
目标：`fabric/`，Minecraft 1.20.1、Java 17。遵循 [v1.5.2 开发基线](DEVELOPMENT-v1.5.2.md)，不改其他版本工程。

上游入口注册 31 个模块。本次移植其中 29 个；按要求排除 **Aura（杀戮光环）和 ScaffoldPlus（自动搭路）**，保留目标工程的 KillAura、Scaffold 及其模式实现。
已有对应模块直接替换，不增加并列的旧、新实现开关。事件、设置、GUI、好友列表和物品栏操作接入 Wurst；不依赖 Meteor。

## 模块映射

| LeavesHack | v1.5.2 模块 | 移植内容 |
| --- | --- | --- |
| AutoAnchor | AutoAnchor | 伤害位置搜索、放置/充能/引爆、支撑、目标与自身伤害限制、动画 |
| AutoArmorPlus | AutoArmorPlus | 护甲评分、绑定诅咒、鞘翅协同、细雪皮靴、满背包换装 |
| AutoLogin | AutoLogin | 按服务器和用户保存账号、登录/注册/改密识别、延迟登录、账号编辑页 |
| AutoMine | AutoMine | Burrow/Surround 目标、伤害过滤、PacketMine 联动、腾空延迟 |
| AutoOffHand | AutoOffHand | 主副手金苹果、图腾、持物与血量条件、关闭后的物品恢复 |
| AutoPlaceBlock | AutoPlaceBlock | 方向铺设、上下范围、台阶、切槽和转向恢复 |
| AutoRefreshTrade | AutoRefreshTrade | 村民交易筛选、附魔选择页、等级判断、讲台破坏/重放、退出条件 |
| SprintPlus | AutoSprint | Strict/Rage、静止与水中条件、飞行协同 |
| AutoTorch | AutoTorch | 光照扫描、仅渲染、穿墙条件、放置和颜色 |
| AutoWeb | AutoWeb | 目标预测、脚/身体/头部蛛网、背包取物、暂停和渲染 |
| BreakESP | BreakEsp | 玩家主次挖掘跟踪、时长估算、状态/名字/进度/颜色 |
| CrystalAuraPlus | CrystalAuraPlus | 目标种类、预测伤害、底座、反虚弱、切换、强制放置、生成即攻击、频率/尝试/年龄/暂停限制 |
| DeathRender | DeathRender | 玩家死亡状态触发客户端闪电 |
| ElytraGrimAccelerate | ElytraGrimAccelerate | 速度过滤、上下限、加速模式、回弹停止 |
| FireworkElytraFly | FireworkElytraFly | Legit/GrimDurability、胸甲交换、烟花检测/时序、方向控制、站姿、潜行恢复 |
| Follower | Follow | 最近或指定玩家、飞行方向协同、攻击距离和返航；保留 `.follow` 入口 |
| FriendsManager | FriendsManager（新增） | Alien/Hachimi/Mio 前缀、增删差集、手动同步、TXT 导入 |
| GlassFiller | GlassFiller | 目标周边玻璃、卡墙过滤、水晶/锚优先、背包切换 |
| GlobalSetting | GlobalSetting（新增） | 挥手方式/手、FOV、切槽去重、包解码兼容、服务端槽位显示、转向与回转 |
| ModuleList | ModuleList（新增） | 模块排序、动画、附加信息、按键过滤和颜色；启用时避免与现有 HUD 列表重复绘制 |
| NukerPlus | Nuker | Cube/UniformCube/Sphere、Flatten/All/Smash、方向范围、名单、排序、时序、PacketMine 和渲染 |
| PacketLogger | PacketLogger | 19 类数据包独立过滤及字段详情、收发开关；另保留其他包开关和输出限速 |
| PacketMine | PacketMine | 双目标、工具与状态挖掘时长、提前 STOP、瞬挖、切槽、暂停/优先级、动画 |
| PlaceRender | PlaceRender | 实际放置记录、手动放置检测、形状、透明度、动画 |
| Printer | Printer | 真实 Litematica 投影、名单、方向/轴/台阶校验、潜行等待、防掉落、背包取物 |
| PushCrystal | PushCrystal | 活塞/红石构建路径搜索、虚拟支撑、方向、伤害/范围约束、推后攻击、挖活塞、渲染 |
| Replenish | Replenish | 九槽快照、耗尽/消耗后匹配物品及 NBT、药水/工具/名单过滤、使用后暂停 |
| SelfTrap | SelfTrap | 头/脚/胸防护、扩展和挖掘保护、居中、关闭条件、清理水晶、背包与发包放置 |
| AutoTree | TreeBot / SaplingFarm | 选定种植点、树苗/骨粉、时序、数量和渲染 |

实现位于 `fabric/src/main/java/net/wurstclient/hacks/`；共享适配位于 `leaves/`；专用注入以 `Leaves` 为前缀。
颜色设置保留 RGBA，包括原 Wurst RGB 设置无法表达的透明度。模块名、说明、新设置和账号界面补充中英文翻译。

## 版本适配与配置

- **AutoSpear 不适用于 1.20.1**：上游使用 1.21.11 的矛与相关组件；目标版本没有这些物品/协议，因此不伪造该功能。FireworkElytraFly 的其余飞行逻辑独立移植。
- 1.21.11 的玩家输入同步包没有直接的 1.20.1 等价物，改用本地移动输入与已有移动/状态协议。上游 MoveFix 的消费者仅为被排除的 Aura/ScaffoldPlus，不接管它们的转向或移动。
- 上游声明但没有实际消费的选项（如部分速度/Baritone/最低烟花字段）不添加无效开关；ElytraGrimAccelerate 按上游实际逻辑处理垂直速度。
- GlobalSetting 的设置始终生效。切槽去重限于移植模块发出的槽位操作，避免改变原有其他模块的发包语义。ClientSwitch 控制服务端槽位指示及 PacketMine 的客户端延迟切槽；同步放置为预测必须临时选中物品，并立即恢复。
- Printer 需要另行安装 **Minecraft 1.20.1 对应的 Litematica 和 MaLiLib**。未安装时提示并停用，不把普通世界当作投影。桥接通过映射后的 `BlockGetter` 读取投影，避免开发环境类名反射在正式 jar 中失效。
- 好友 TXT 导入使用可编辑文件路径和导入按钮；每行一个名字，UTF-8。同步命令只在启用且名单变化或点击同步时排队发送，前缀应匹配实际装载的客户端。
- 水晶目标类型使用逗号分隔的实体 ID 设置；强制放置按键使用 Minecraft 按键 ID，例如 `key.keyboard.r`。
- PushCrystal 的广度优先构建搜索分帧执行，最多 100,000 个访问状态；Nuker 范围上限为 16，避免客户端长时间阻塞。它们不扩大服务器允许的交互距离。
- 更新后保留相同模块 ID。旧配置中已删除的设置不再生效；同名设置会继续读取，首次使用应核对新设置。RGBA 设置兼容旧 RGB 值。
- AutoLogin 改用按服务器与用户名分组的账号文件，旧的全局密码不会自动迁移到其他服务器。编辑页明确显示本地明文保存方式，密码输入隐藏显示；损坏的账号文件不会自动覆盖。
- 放置、切槽、双挖、耐久飞行等能否被接受仍取决于服务端规则。此移植不等于对所有反作弊版本的兼容保证。

## 验证

验证在独立 `.dsh-tmp/leaves-runtime/` 客户端及复制的 `leaves-qa` 单人世界进行，未连接多人服务器。

- 自动测试覆盖 29 模块构造/设置注册、账号分组与持久化/损坏文件保护、活塞构建路径、最大构建数、已有结构复用、附魔等级、投影方向、好友差集、飞行方向和 RGBA 序列化。
- 运行检查覆盖移植模块启停和实际世界加载；缺少 Litematica 时 Printer 正确提示并停用。
- 行为检查已验证：Replenish 恢复耗尽栏位、AutoOffHand 装图腾/右键金苹果/关闭恢复武器、Nuker 与 PacketMine 挖石及恢复原栏位。
- 扩展行为检查已验证：AutoArmorPlus 换上更好的胸甲、SelfTrap 从背包取材并构建头顶防护、FireworkElytraFly 进入服务端确认的滑翔并向前飞行；确认渲染模块正常绘制。
- Litematica 实际投影、多人玩家战斗、跨客户端好友命令和各服务端反作弊行为未在该单人环境中验证。

完整 `build`（含重映射）通过；自动测试 **63 项通过，0 失败，0 跳过**。测试运行显式启用本机发布初始化脚本默认关闭的 test 任务。
客户端在 Java 17 下成功启动并加载单人世界，新增 Mixin 无应用错误。构建使用本机已有的目录限定 ZIP 可写探测兼容代理；客户端原生库使用独立临时目录。
许可证及上游模块 SHA-256 随 jar 位于 `META-INF/licenses/leaves-hack/`。
