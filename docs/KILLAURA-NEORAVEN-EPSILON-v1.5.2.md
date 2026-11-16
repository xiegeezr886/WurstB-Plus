# KillAura：Epsilon 主实现 / NeoRaven 完善

当前全体战斗迁移状态以 [战斗实现记录](COMBAT-EPSILON-v1.5.2.md) 为准。下面旧运行数据只对应最初 KillAura 阶段。

基线为 Fabric 1.20.1 / Java 17，分支 `codex/killaura-neoraven-epsilon`。
用户提到的「esploin」按已拉取的 Epsilon 源码处理。

## 来源

| 来源 | 固定版本与文件 | 采用的行为 |
| --- | --- | --- |
| [NeoRaven](https://github.com/Matr1ze/NeoRaven) | `1a4ae731837f28087c263763cf176d9ec0261c37`，`src/main/java/xyz/daikirai/module/combat/KillAuraModule.java` | 距离 / 生命 / 受伤时间 / 偏航角排序、优先攻击范围内目标、按实体身份记录轮换、一次采样的攻击间隔 |
| [Epsilon](https://github.com/NekoyaHouse/Epsilon) | `2fb4aad7d1b7c45480785315543ecc67e2f048fc`，`common/src/main/kotlin/com/github/epsilon/modules/impl/combat/KillAura.kt` | Single / 延时 Switch、搜索与攻击范围分离、1.9+ 武器冷却、转向射线命中后攻击 |

两者上游 GPL-3.0 文本与来源说明随包置于 `META-INF/licenses/neoraven/` 和
`META-INF/licenses/epsilon/`。这是行为适配：NeoRaven 的 1.8.9 剑格挡 / blink 包策略、
Epsilon 的新版 Kotlin 事件与模块依赖不直接移入 1.20.1。
原有 OpenOpal 转向模型、数学与目标特效继续使用并保留原署名。

## 实现

- `RavenTargeting` 每 tick 一次有界实体扫描。保留好友、AntiBot、自身、假玩家、死亡和实体类别过滤；增加隐形与队友开关。
- 可见点采用最近点、中心、眼睛及八个内部采样点，最多 11 点并在命中后提前退出，替代原来的随机三维网格。
- 攻击范围内有可见候选时，排除仅位于搜索范围内的候选；按所选优先级排序，并用实体 ID 稳定处理同分。
- `AuraTargetCycle` 的 Single 每次选排序首项；Switch 用 UUID 保持目标到设定延迟，并让未访问候选依次获得机会。列表重排不改变正在保持的目标，目标消失立即选择有效替代。
- `AuraAttackClock` 在攻击后记录下一次时间，最多每 tick 一击；停顿后不补发积压攻击，渲染 FPS 不参与攻击排队。
- tick HEAD 规划，PreMotion 刷新目标与转向，PostMotion 消费一次性计划。必须赢得共享转向队列，且实际发送角度的射线首先命中所选实体，才调用原版 `gameMode.attack`。
- 射线包含其他可选实体，避免穿过前方好友攻击后方目标；关闭 Through Walls 时检查方块遮挡。预测只改变瞄准点，命中始终检查实时实体盒。
- 不再修改全局 `MC.hitResult`，不再通过模拟左键触发攻击或挖方块。`Override raycast=false` 时额外要求原版鼠标命中当前目标。
- AUTO 或「1.9+ Attack Cooldown」启用时使用实际武器冷却；MANUAL 使用固定 CPS。旧 Heypixel 转向模型已移除；手动 CPS 每次攻击后一次性采样 ±4ms 时序扰动，不代表兼容任何服务器检测。
- Vanilla Autoblock 使用原版盾牌，并在输入阶段保持自己发起的持盾状态。关闭、暂停或失去目标时只释放本模块拥有的使用状态；用户按住使用键时保留用户操作。
- SmartWeapon 对举盾目标切换斧头，重置并等待所选武器冷却（现代模式）；攻击完成、失去目标或关闭时恢复原槽。用户已换槽时不覆盖其选择。
- 开菜单、吃喝、骑乘、旁观、Scaffold、生死 / 玩家 / 世界变化会清理目标与待执行动作；玩家或世界变化额外重置攻击时钟。

## 配置兼容

保留 `Killaura` 标识、仍适用的设置名，当前使用 SILENT / LOCK_VIEW / NONE、Hit Select、Grim Keep Sprint、
Box / Halo 和 TargetHUD 的目标访问接口。新增 Target priority、Switch delay、Target invisible、Target teammates。
FOV 延用旧配置的半角语义，180 表示全方向。隐形与队友开关默认开启，以保持原有过滤范围。
实际搜索半径取攻击、转向、挥手范围的最大值；超出转向范围的目标只用于空挥，不进行转向或攻击。
MultiAura 与其他主要战斗模块已在后续阶段迁移，详见战斗记录。其他 Minecraft 版本工程未修改。

## 验证

新增测试覆盖轮换重排 / 删除 / 模式变更 / 重置、CPS / 抖动 / 冷却 / 防积压，
以及动作计划的跨 tick 单次消费和跨玩家 / 世界失效。
2026-11-16 验证：

- Fabric 1.20.1 `build` 成功，显式开启测试后 **99 项测试，0 失败，0 错误**（新增 14 项）。
- 用旧 QA 世界的独立副本 `.dsh-tmp/killaura-qa-run/saves/killaura-qa` 启动客户端，未连接外部服务器。
- AUTO / Switch / 默认 3 格攻击范围：无 AI 僵尸近距离受到实际伤害，1000 生命降至 965.56；放置遮挡墙后两次间隔 3 秒的生命值均为 428.29596。
- 将目标移到 5 格外后，间隔 3 秒读数均为 993.112，未发生超范围攻击。
- 两个相隔 90° 的目标：服务器 yaw 在 0 / -90 之间随 300ms Switch 延迟切换，两个目标均受到实际伤害。
- 打开账户界面后两次读数均为 1000（本地世界同时暂停，因此不把该项视为多人服不中断 tick 的菜单测试）。
- 关闭模块后，间隔 2 秒目标生命值均为 834.688，攻击停止；测试日志无 ERROR / Exception。
- `git diff --check` 通过。输出 `.dsh-tmp/hud-glass-build/libs/WurstB+ Plus-1.5.2-Fabric-1.20.1.jar`。

尚未实机覆盖盾牌 / SmartWeapon 对玩家举盾、好友 / 队伍组合、多人延迟以及其他转向模块抢占场景；
这些接口经过编译与代码检查，但不宣称完整实战或服务器兼容验证。
