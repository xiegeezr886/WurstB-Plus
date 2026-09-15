状态：不适用（参考侧 5 模式的可用部分已被本工程 4 模式覆盖，未发现可修缺陷，零改动）

## 对照证据

参考侧路径前缀 = `_oe_ref\src\main\kotlin\studio\coni\epsilon\`；本工程路径前缀 = `WurstB-Plus-main\src\main\java\net\wurstclient\`。

| 关注点 | 参考（_oe_ref 相对路径:行） | 本工程（相对路径:行） | 判定 |
| --- | --- | --- | --- |
| 触发阈值 | `module/movement/NoFall.kt:25-26` `fallDistance > 2.25f`（Speed 开启时 2.85f）`+ 跳跃提升 amplifier+1` | `hacks/NoFallHack.java:159-163` `dy<0 && fallDistance - dy >= triggerDistance`（默认 2.5，SMART 强制 ≥2.5） | 1.20.2 `LivingEntity.java:1594-1598` 的免伤线为 `fallDistance - 3.0F - (amplifier+1)`，故有跳跃提升时本工程触发**更早**、不漏保护 → 差异无害，非缺陷 |
| 落地保护包 | `NoFall.kt:28` `CPacketPlayer(true)` | `hacks/NoFallHack.java:92` `new StatusOnly(true)` | 等价（同为「仅 onGround=true 的状态包」） |
| 位置包模式 | `NoFall.kt:31` / `:75-76` `CPacketPlayer(true)` / `Position(...)` | `hacks/NoFallHack.java:93` `new Pos(x,y,z,true)`；`:115-116` mixin 改写任意出站移动包（GroundSpoof） | 本工程多一个更通用的 GroundSpoof，参考无 |
| 下方是否方块 | `NoFall.kt:27` 仅 `isBlockUnder` 时置 `fallDistance=0` | `hacks/NoFallHack.java:87-97` 无此分支，统一发包 | 参考更精细；本工程更保守（可能多发包），不构成漏保护 |
| 重置 fallDistance | `NoFall.kt:29` 仅 Packet+方块分支置 0 | `hacks/NoFallHack.java:140-146` `runAfterSend` 内按包 onGround 置 0，`:42-44` 可关 | 等价且更可控 |
| 失效场景排除 | 无（仅 `:91` 判 world） | `hacks/NoFallHack.java:151-157` 排除创造/旁观/载具/攀爬/水/岩浆/无重力/鞘翅 | 本工程更完善 |
| 水桶模式 | `NoFall.kt:35-52` 向下 5.33 射线找方块 → 切水桶右键 | 无 | 不适用（见「故意不搬」1） |
| AAC / AAC2 | `NoFall.kt:66-68` `Double.NaN` 坐标；`:71-77` 清零 motionX/Z | 无 | 不适用（见「故意不搬」2） |
| Hypixel | `NoFall.kt:54-64` 本地直写 `mc.player.onGround = true` | 无 | 不适用（1.20.1 落地由服务端按包判定，本地字段不参与） |
| 与 Speed 耦合 | `NoFall.kt:25` `Speed.isEnabled` → 阈值 2.85f | 无耦合 | 不搬（阈值随另一 hack 变化无可验证依据） |

## 实际改动

无。本次仅审计，未修改任何 `.java`（按任务约束，发现的问题一律进「建议（未做）」）。

## 故意不搬

1. `Bucket`（`NoFall.kt:35-52`）：1.20.1 放置走 `MultiPlayerGameMode#useItem`，且服务端对下落中放水的落地判定已不同，需物品/朝向/主副手多维判断，收益低于误操作风险。
2. `AAC`/`AAC2`（`NoFall.kt:66-77`）：`Double.NaN` 坐标与「清零 motionX/motionZ」是 1.12.2 服务端漏洞面，1.20.1 会被位置校验回弹/踢出。
3. `Hypixel`（`NoFall.kt:54-64`）：靠本地写 `onGround` 伪造落地，1.20.1 客户端该字段不进入包内容，写它不改变服务端判定。
4. `Speed` 耦合（`NoFall.kt:25`）：阈值 2.85f 的来历不可验证。

## 建议（未做）

1. `hacks/NoFallHack.java:38-40` 的 `triggerDistance` 上限 8.0 可被配置成完全无效：取 `triggerDistance=8.0`、`dy=-0.5`、下落途中 `fallDistance=3.0`（5 格高平台起跳下落）→ `:163` 条件 `3.5 >= 8` 为假，不发包，服务端结算 `ceil(5-3)=2` 点伤害；同一输入在默认 2.5 下会正常发包。范围本身不是 bug，但建议在设置描述里注明「>3 格即可能失效」或把上限钳到 3.0（改默认值/范围会动用户配置，故未做）。
2. `hacks/NoFallHack.java:145` 把本地 `fallDistance` 归零后，重发条件是 `fallDistance - dy >= threshold`，需再下落约 2.5 格才会重发。若服务端丢弃了 spoof 包，这段空窗只能靠继续下落补齐；重发密度是否足够需实机（带反作弊的服务器）验证。
3. 未实机验证 1.20.1 服务端 `handleMovePlayer` 对「空中 grounded 状态包」的处置；`GroundSpoof` 模式（`:115-116`）会改写**所有**出站移动包的 onGround，风险高于其他三个模式。
