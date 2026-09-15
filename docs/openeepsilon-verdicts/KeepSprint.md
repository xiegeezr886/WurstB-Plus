状态：已重构

## 对照证据

**参考项目里没有 KeepSprint 模块。** `_oe_ref/src/main/kotlin/studio/coni/epsilon/module/` 下与
「疾跑」有关的只有 `movement/Sprint.kt`（自动疾跑：`Sprint.kt:12` 的 name/description、
`:22-27` 的前置条件、`:30` 的 `mc.player.isSprinting = sprinting`）与 `movement/Strafe.kt`
（用疾跑状态挑速度常数）。参考里没有「攻击时不要把疾跑关掉」这个概念——1.12.2 的
`EntityPlayer#attackTargetEntityWithCurrentItem` 同样会 `setSprinting(false)`，参考是直接吃这个原版行为。

所以本项不存在「从参考搬什么」的问题，本轮核对的是**本工程自己的实现是否已经完整**：结论是
**已经完整**（30 行的 hack 只是策略类的转发壳，三个判定都有 JUnit 5 用例，mixin 侧两个重定向目标唯一）。

| 关注点 | 参考（_oe_ref 相对路径:行） | 本工程（相对路径:行） | 判定 |
| --- | --- | --- | --- |
| 是否有对应模块 | 无（最接近的是自动疾跑 `module/movement/Sprint.kt:12-48`，与本工程 `AutoSprintHack` 对应） | `hacks/KeepSprintHack.java`（30 行，`Category.COMBAT`，无任何设置） | 参考无可搬项 |
| 决策逻辑是否已抽成纯类 | 参考无对应物 | `util/KeepSprintPolicy.java:9-13`（`attackMotionMultiplier`）、`:15-19`（`shouldApplySprintChange`）、`:21-25`（`shouldPreserveSprint`）——无任何 import、无 MC 类型 | 已重构 |
| 是否有单测 | 参考无（该模块的判定内联在 `Sprint.kt:22-27`） | `src/test/java/net/wurstclient/util/KeepSprintPolicyTest.java:11-17,19-25,27-34`（3 个用例覆盖三个函数，含「不会帮你开疾跑」「只拦取消、不拦开启」两个边界） | 已重构（可单测） |
| hack 侧做了什么 | — | `KeepSprintHack.java:25-29`：`shouldPreserveSprint(isEnabled(), player == MC.player, player.isSprinting())` 一行转发；**没有**事件监听、没有状态、没有设置 | 正确（开关语义收敛在策略类里） |
| 真正的注入点 | — | `mixin/PlayerMixin.java:20-29` 重定向 `Player.attack` 里的 `Vec3.multiply(DDD)`（原版攻击后的自我减速 0.6/1.0/0.6）；`:31-39` 重定向同一方法里的 `Player.setSprinting(Z)`（原版「攻击即停疾跑」）；`:41-46` `isKeepSprintActive()` 取出 hack 再问策略类 | 这里是必须用 mixin 的场景（`Player.attack` 是原版核心路径，没有对应事件） |
| 两个重定向目标是否唯一（mixin 歧义会**运行时**报错） | — | 仓库内可读的反编译源（1.20.2）：`.../world/entity/player/Player.java:1214-1215` = `this.setDeltaMovement(this.getDeltaMovement().multiply(0.6, 1.0, 0.6)); this.setSprinting(false);`；整个 `Player.java` 里 `multiply(` 只出现 1 次、`setSprinting` 也只出现 1 次 | 无歧义（1.20.1 无法本地反编译，但这两行自 1.16 起未变，且本文件是初始提交既有代码、上线以来一直在跑） |
| 只对本地玩家生效 | — | `KeepSprintPolicy.java:21-25` 要求 `localPlayer`（调用方传的是 `player == MC.player`，`KeepSprintHack.java:28`） | 必要且已实现：`PlayerMixin` 注入在 `Player` 上，**单机集成服里的 `ServerPlayer` 也是 `Player`**，若不判本地玩家，服务端侧也会保持疾跑，导致击退/减伤与客户端不一致 |
| 前置条件（饥饿、有输入、未潜行…） | `Sprint.kt:22-27`：自动疾跑的 5 条前置 | 策略类不做这些，只看 `player.isSprinting()`（`KeepSprintPolicy.java:21-25`） | 正确：KeepSprint 只负责「原版攻击时别把疾跑关掉」，前置已经包含在原版 `isSprinting()` 的当前值里 |

## 实际改动

无。`KeepSprintHack.java` 已经是 `KeepSprintPolicy` 的纯转发壳（`:25-29`），三个判定函数各自都有
JUnit 5 用例，mixin 侧的两个 `@Redirect`（`PlayerMixin.java:20-29,31-39`）目标在 `Player.attack` 里
唯一、且策略类强制只对本地玩家生效。按 brief「**不要为了改而改**」直接判「已重构」，一行未动。

（核对时确认的一件事实：这个 hack 之所以看起来「什么都没做」，是因为真正的行为改在 mixin 里——
`KeepSprint` 这个 hack 本身只需要回答「现在要不要保持疾跑」这一个问题，答案就是
`KeepSprintPolicy.shouldPreserveSprint` 的三个输入。）

## 故意不搬

- `movement/Sprint.kt` 的自动疾跑（`:12-48`）：那是本工程 `AutoSprintHack` 的对应物，不是 KeepSprint；
  而且参考 `Sprint.kt:22-27` 的前置条件本工程已有严格超集（见 `docs/openeepsilon-refactor.md:124`
  的 AutoSprint 行）。
- `Sprint.kt:30` 的 `mc.player.isSprinting = sprinting`（直接写字段/属性）：1.20.1 必须走
  `Player#setSprinting`（它同时处理属性修饰符与网络同步），直接写字段是错的。
- 参考 `player/WTap.kt` 的 `START_SPRINTING → STOP_SPRINTING → START_SPRINTING` 三连包：与「攻击不掉
  疾跑」是两件事（那是取消击退的模拟），本工程 WTap 用模拟按键实现，已有 `WTapHack`，与本模块无关。

## 建议（未做）

- 无。若日后要给 KeepSprint 加设置（例如「只在命中时保持」「排除空挥」），新规则应当落进
  `util/KeepSprintPolicy` 里作为纯函数 + 单测，而不是塞回 `KeepSprintHack` 或 `PlayerMixin`——这是当前
  这个模块唯一值得保留的形状，本轮无需变动。
