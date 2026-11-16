# v1.5.2 战斗实现迁移

目标：Fabric 1.20.1 / Java 17。分支：`codex/killaura-neoraven-epsilon`。
最新优先级：**Epsilon 主实现 → NeoRaven 完善或补充 → LeavesHack 补充**。
ScaffoldWalk 继续使用 NeoRaven。保留模块 ID、入口分类和 HUD 接口。

## 已接入的替换实现

这是跨 Minecraft 版本的源码行为适配，不是完整复制整套客户端，也不宣称所有上游模式均已移植。

| 当前模块 | 来源 | 上游文件（相对 modules/impl 或 module） |
| --- | --- | --- |
| AntiBot | Epsilon | `combat/AntiBot.kt` |
| Reach | NeoRaven | `combat/ReachModule.java` |
| Killaura | Epsilon | `combat/KillAura.kt` |
| MultiAura | Epsilon | `combat/MultiAura.kt` |
| AimAssist | Epsilon | `combat/AimBot.kt` |
| BowAimbot | Epsilon | `combat/AimBot.kt` |
| Criticals | Epsilon | `combat/Criticals.kt` |
| TriggerBot | Epsilon | `combat/TriggerBot.kt` |
| AutoClicker | Epsilon | `combat/AutoClicker.kt` |
| AutoArmor | Epsilon | `player/AutoArmor.kt` |
| AutoWeapon | Epsilon | `combat/AutoWeapon.kt` |
| AutoTotem | Epsilon | `combat/AutoTotem.kt` |
| KeepSprint | Epsilon | `movement/KeepSprint.kt` |
| AntiKnockback | Epsilon | `movement/Velocity.kt` |
| Backtrack | Epsilon | `combat/Backtrack.kt` |
| CrystalAura | Epsilon | `combat/CrystalAura.kt + combat/crystalaura/` |
| TpAura | NeoRaven | `combat/TPAuraModule.java` |
| Hitboxes | NeoRaven | `combat/HitboxModule.java` |
| AutoGap | NeoRaven | `combat/AutoGappleModule.java` |
| DelayRemover | NeoRaven | `player/DelayRemoverModule.java` |
| ScaffoldWalk | NeoRaven | `player/ScaffoldModule.java + player/TowerModule.java` |

- KillAura 使用 Epsilon 范围/转向/冷却/Hit Select 流程，NeoRaven 补充目标优先级、UUID 轮换、灵敏度量化和平滑。攻击在实际转向提交后单次执行；不依赖 FPS 积压攻击。
- AimAssist、BowAimbot 接入 Epsilon 反应时间、加速瞄准、预测与抛物线。帧相关瞄准归一化到 tick。
- MultiAura 用 CPS/20 预算一次批量攻击；空闲时清空预算。TriggerBot 必须同时满足随机 tick 间隔和武器冷却，修正上游延迟可被绕过的问题。
- AutoClicker 左/右键独立突发、衰减、双击、漏击；保持重绑定按键。按 20 TPS 消费，每侧每 tick 至多 2 次，不承诺界面高 CPS 数值对应实际服务端攻击数。
- AutoWeapon 使用 Epsilon 目标附魔评分、盾牌破坏、Simple/Smart 与延迟归还槽位。修正上游换槽计时未重置及标签判定问题；玩家手动换槽后放弃恢复。
- AutoArmor 使用 `(护甲 + ceil(韧性))*10 + 附魔`，支持各部位保护/爆炸保护和鞘翅优先级。1.20.1 使用三个 PICKUP 交换，满背包也可换装；容器或光标有物品时暂停。
- AutoTotem 使用 Epsilon 血量、空副手、虚空、金苹果条件和严格交换；不干扰打开的容器或已有光标物品。
- Criticals 使用自然下落窗口控制疾跑；KeepSprint 使用 Vanilla/Legit，附加 NeoRaven 减速和跳跃条件。
- Backtrack 仅延迟接收包，按最近攻击、范围、概率、hurtTime、冷却和追踪缓冲决定是否开启；服务端目标位置接近时提前释放。移除旧发送包延迟，避免攻击与保活排队。
- AntiKnockback 接入 Epsilon Cancel/Reduce/Delay。队列限制 2048；断线、重生、换世界清空，回放核对连接和世界并在客户端线程执行。保活和 ping 直接通过。爆炸取消只移除击退，保留世界效果。
- CrystalAura 使用 Epsilon 自伤、伤害比、低血量、破甲和致命阈值，独立攻击/放置间隔、年龄、尝试限制；计划在 PostMotion 再校验。伤害由现有 1.20.1 DamageUtils 计算，保留 CrystalAuraPlus 的 LeavesHack 独立高级实现。
- AntiBot 改为 Epsilon 在线玩家 UUID 检查，不再把正常隐形玩家或短期零延迟直接当作机器人。Reach 使用 NeoRaven 的 Min/Max、Weapon/Moving/Sprint 条件和可选穿墙选择；一 tick 共用一次距离样本，方块距离保持原版。两者保留原来的 Misc / Player 分类。
- NeoRaven Hitboxes 只扩展射线选择半径，不再改真实碰撞盒。TP-Aura 保持上游随机相对偏移及受伤更新行为。
- AutoGap 接入 NeoRaven 血量/手动模式、移动包缓冲、34 tick 持物冷却和槽位归还。1.20.1 通过原版 useItem 使用金苹果；不发送 1.8.9 无效位置包，不伪造即时食用。
- DelayRemover 替换无效反射实现，清除 missTime 并将跳跃间隔限制到 3；不把 1.9+ 攻击冷却误当成鼠标未命中延迟。
- ScaffoldWalk 替换旧 Telly / Silence / Heypixel 专用实现，使用 NeoRaven 相邻支撑搜索、切槽、SafeWalk、基础快速模式和 Tower。放置须通过实时射线、碰撞和共享转向仲裁，边缘预判避免 SafeWalk 卡住。未移入上游全部 Float/Side/Jump 子模式。
- 点击音效：NeoRaven 菜单源码的 `gui.button.press` 对应 `UI_BUTTON_CLICK`，音高 1；接入 CGUI/导航器的自绘交互，原版按钮已有同一声音。

## 上游差异

Epsilon 新版的矛、重锤及其附魔在 1.20.1 不存在，未伪造对应功能。
CrystalAura 未移入上游 CEV、反包围、预测生成 ID、图腾历史/潜影盒联动和并行新版本伤害引擎；AutoArmor 未移入 Epsilon 的 ElytraPlus 专用状态。
Velocity 已接入 Cancel 模式的实体/方块/流水推挤选项；流水只拦截本地玩家的流体加速，不修改整个世界的流场，实体碰撞只拦截施加给本地玩家的冲量。原有 AntiWaterPush / AntiEntityPush 独立模块仍可单独使用。
Backtrack 已接入 Range 模式的 Players、Mobs、Animals、Villagers、Ambient、Water、Others 和 Invisible 筛选；Invisible 与 Epsilon 一致仅控制隐形玩家。按眼睛到碰撞盒距离选择范围内最近目标，先排除小于最小范围及类别不符的目标。独立 ESP 尚未移入。
AutoGap 未移入 invalid-C03、smart-target 等特定服务器策略；Scaffold 是基础行为移植，不保证其所有模式一比一。
AutoGap 缓冲队列另设 128 包上限，避免 Hold packets=80 时在进食逻辑消费前反复清零。KeepSprint 的延迟恢复同时校验玩家与世界。
旧配置只复用仍存在的同名设置；删除的旧设置不再执行。CrystalAura 的 Break delay 现在为毫秒，首次使用应重设相关配置。

## 战斗分类完整审计

以下逐项列出源码中声明 `Category.COMBAT` 的入口；抽象类另行标明。
标为“原有专用实现”的模块没有在这三份源码中确认语义完整对应的模块，保留现有功能，**不能计入已替换**。
LeavesHack 已有移植详见 [LeavesHack 记录](LEAVESHACK-v1.5.2.md)，并非本轮重复新增。

| 模块 | 当前状态 |
| --- | --- |
| AbstractTpAnchorAura | 抽象基类，无独立开关；原有专用实现 |
| AdaManualCrystal | 原有专用实现，未替换 |
| AimAssist | Epsilon 实现，见上表 |
| AnchorAura | 原有专用实现，未替换 |
| AntiAnchor | 原有专用实现，未替换 |
| AntiAnvil | 原有专用实现，未替换 |
| AntiBed | 原有专用实现，未替换 |
| AntiKnockback | Epsilon 实现，见上表 |
| ArrowDmg | 原有专用实现，未替换 |
| ArrowDodge | 原有专用实现，未替换 |
| AttractAura | 原有专用实现，未替换 |
| AutoAnchor | 已有 LeavesHack 移植，保留 |
| AutoAnvil | 原有专用实现，未替换 |
| AutoArmor | Epsilon 实现，见上表 |
| AutoArmorPlus | 已有 LeavesHack 移植，保留 |
| AutoChestAura | 原有专用实现，未替换 |
| AutoChorus | 原有专用实现，未替换 |
| AutoCity | 原有专用实现，未替换 |
| AutoClicker | Epsilon 实现，见上表 |
| AutoDoubleHand | 原有专用实现，未替换 |
| AutoGap | NeoRaven 补充，见上表 |
| AutoLeave | 原有专用实现，未替换 |
| AutoPotion | 原有专用实现，未替换 |
| AutoSoup | 原有专用实现，未替换 |
| AutoSword | 原有专用实现，未替换 |
| AutoTotem | Epsilon 实现，见上表 |
| AutoWeapon | Epsilon 实现，见上表 |
| Backtrack | Epsilon 实现，见上表 |
| BedAura | 原有专用实现，未替换 |
| BowAimbot | Epsilon 实现，见上表 |
| BowSpam | 原有专用实现，未替换 |
| Burrow | 原有专用实现，未替换 |
| ClickAura | 原有专用实现，未替换 |
| Criticals | Epsilon 实现，见上表 |
| CrossbowAura | 原有专用实现，未替换 |
| CrystalAura | Epsilon 实现，见上表 |
| CrystalAuraPlus | 已有 LeavesHack 移植，保留 |
| CrystalMacro | 原有专用实现，未替换 |
| DelayRemover | NeoRaven 补充，见上表 |
| EndermanLook | 原有专用实现，未替换 |
| FakeLag | 原有专用实现，未替换 |
| FastCrossbow | 原有专用实现，未替换 |
| FightBot | 原有专用实现，未替换 |
| FoKillAura | 原有专用实现，未替换 |
| Follow | 已有 LeavesHack 移植，保留 |
| GAntiExplosion | 原有专用实现，未替换 |
| GcoreGTrap | 原有专用实现，未替换 |
| GcoreTpCrystalAura | 原有专用实现，未替换 |
| GcoreWebAura | 原有专用实现，未替换 |
| GTpAura | 原有专用实现，未替换 |
| GTpbot | 原有专用实现，未替换 |
| Hitboxes | NeoRaven 补充，见上表 |
| HitDelayFix | 现有未命中延迟修复，与 NeoRaven hitreg 等价；共用 missTime 注入 |
| KeepSprint | Epsilon 实现，见上表 |
| Killaura | Epsilon 实现，见上表 |
| KillauraLegit | 原有专用实现，未替换 |
| KillFx | 原有专用实现，未替换 |
| KnockbackDirection | 原有专用实现，未替换 |
| MacroAnchor | 原有专用实现，未替换 |
| MultiAura | Epsilon 实现，见上表 |
| PistonCrystal | 原有专用实现，未替换 |
| Pitcher | 原有专用实现，未替换 |
| Protect | 原有专用实现，未替换 |
| PushCrystal | 已有 LeavesHack 移植，保留 |
| Quiver | 原有专用实现，未替换 |
| SelfAnvil | 原有专用实现，未替换 |
| SwordGap | 原有专用实现，未替换 |
| TntBomber | 原有专用实现，未替换 |
| TpAnchor | 原有专用实现，未替换 |
| TpAura | NeoRaven 补充，见上表 |
| TpBowAura | 原有专用实现，未替换 |
| TpMachineGun | 原有专用实现，未替换 |
| TriggerBot | Epsilon 实现，见上表 |

## 验证

测试世界使用 `.dsh-tmp/killaura-qa-run/` 的独立副本；未连接外部服务器。

2026-11-16 运行验证（Java 17、1.20.1）：
- KillAura AUTO：目标生命 1000 → 965.56，关闭后保持 965.56；独立新目标放在墙后，两次间隔读数均为 1000。
- MultiAura 与 TriggerBot：目标生命分别从 965.56 → 954.04 → 926.49。
- AutoWeapon：攻击时使用火焰附加武器，目标出现持续燃烧；停止后恢复原第 1 格。
- AutoTotem：空副手自动补入图腾。AutoArmor：钻石胸甲替换皮革胸甲；再次填满 36 格背包后仍成功，旧皮革胸甲回到原钻石胸甲所在的第 9 号库存槽。
- ScaffoldWalk / SafeWalk：从 z=202.5 前进到 z=214.58，y=201 不变；检查 z=204…209 的 6 格都生成了石头。
- CrystalAura：启用转向及 Safety override 的独立放置测试，16 个水晶减少到 12 个，目标生命 1000 → 708.74；关闭 Safety override 后不继续放置。此结果验证放置/引爆和安全规则开关，不等于默认设置的实战调参验证。
- AutoGap SILENCE_FIX / Hold packets=1：8 个苹果减少到 6 个，关闭后选中栏位恢复到 0，主手为原钻石剑。
- Backtrack 与 TriggerBot 同时启用，移动测试目标、靠近后释放并关闭，客户端继续运行。Velocity 三个模式均完成启停；Cancel 爆炸测试中玩家保持原位置。
- 其余已替换模块完成启停检查。需要玩家对手的 AimAssist、TP-Aura、Reduce 实际反击，以及多人延迟/掉线/反作弊组合，未在此单人环境中完整验证。

上一轮测试进程曾在窗口操作时于 `glfw.dll` 发生原生访问冲突；本轮通过配置关闭失焦暂停后重新启动并完成测试。另有离线开发登录的 Yggdrasil 公钥请求超时，与模块异常分开记录。
本地运行证据：`.dsh-tmp/combat-runtime.log`、`.dsh-tmp/combat-qa-results.jsonl`。
上游来源文件 SHA-256 随 jar 附在 `META-INF/licenses/{epsilon,neoraven}/SOURCES.sha256`。


最终构建验证（2026-11-16）：Fabric 1.20.1 完整 `build` 成功；28 个测试类、120 项测试全部通过，失败、错误和跳过均为 0。`git diff --check` 通过。
产物：`.dsh-tmp/hud-glass-build/libs/WurstB+ Plus-1.5.2-Fabric-1.20.1.jar`。构建日志：`.dsh-tmp/combat-migration-build.log`。

- Reach 最终启动验证：目标位于 4 格，AutoClicker 持续输入下未开启 Reach 时生命保持 1000；Min/Max=4.5 开启后降至 985.98。AntiBot 同时启用，启动与启停无模块异常。
- `OrderedReplay` 修复网络线程已排队的回放被客户端即时回放超越的问题，合并为单个 FIFO 消费任务；包含跨执行线程顺序、重入、异常恢复测试。
- 最终启动日志为 `.dsh-tmp/combat-final-runtime.log`。本轮测试实例已结束，未操作用户的其他游戏实例。

### 后续接入（2026-11-16）

- 补齐上述 Velocity 推挤控制和 Backtrack 目标分类；仅在适用模式显示设置，保留原模块入口。
- Backtrack 模式切换释放旧队列，切换目标立即退出旧追踪状态；修复等待回溯冷却时反复清理目标、导致冷却不断延期的问题。
- 新增范围选择回归测试，覆盖过近目标遮挡、禁用类别遮挡、倒置范围、边界距离、最近目标及无效距离。
- 完整 `build` 通过，29 个测试类共 125 项测试通过，失败/错误/跳过均为 0。日志：`.dsh-tmp/combat-followup-build.log`；产物仍为上述 Fabric 1.20.1 jar。
- 独立 1.20.1 / Java 17 测试实例成功加载新 Mixin。流水相同条件下，选项关闭/开启/再关闭的 2 秒水平位移分别为 2.47341 / 0 / 2.51447 格；Reduce、Delay 模式正常受流水推动，Cancel 模式拦截。
- 方块边缘挤出测试：关闭/开启/再关闭时横向位移 0.41443 / 0 / 0.41443 格。静止猪碰撞测试对应横向位移 -0.56869 / 0 / -0.65302 格。
- Backtrack 新增八个设置均能切换，Range / Attack 往返及关闭正常；类别筛选的多人延迟表现和独立 ESP 尚未验证/接入。
- 运行日志：`.dsh-tmp/combat-followup-runtime.log`，HTTP 测试证据继续写入 `.dsh-tmp/combat-qa-results.jsonl`。测试实例完成后已关闭。

### mc.loyisa.cn 联机测试（2026-11-16）

- 服务器状态查询成功：返回 CCBlueX 亚洲测试服说明，接受协议 763（Minecraft 1.20.1）。
- 使用全部模块关闭的独立 Java 17 / Fabric 1.20.1 开发实例连接。服务器在登录阶段返回 `disconnect.loginFailedInfo.invalidSession`，当前离线开发身份未能进入世界。
- 需要有效正版账号会话后继续。此次只确认服务器可达及登录失败原因，不能计为战斗模块联机验证通过。
- 状态记录：`.dsh-tmp/loyisa-status.json`；客户端日志：`.dsh-tmp/loyisa-runtime.log`。
