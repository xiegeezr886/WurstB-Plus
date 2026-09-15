状态：不适用

## 对照证据
| 关注点 | 参考（_oe_ref 相对路径:行） | 本工程（相对路径:行） | 判定 |
| --- | --- | --- | --- |
| 参考侧对应物 | `_oe_ref/src/main/kotlin/studio/coni/epsilon/management/ModuleManager.kt:97`（Movement 注册表 `:84-109` 已逐条读过，没有 ReverseStep / FastFall） | `src/main/java/net/wurstclient/hacks/ReverseStepHack.java:28-29`（`@SearchTags` 里就写着 fast fall） | **有对应，但不是任务里猜的 `Step.kt`**（那是上台阶）；语义对应物是 `_oe_ref/src/main/kotlin/studio/coni/epsilon/module/movement/InstantDrop.kt:18`（"Increase your downwards velocity when falling"） |
| 作用手法 | `.../movement/InstantDrop.kt:24-41`：监听 `PlayerMoveEvent.Pre`，`:37` 执行 `event.y -= speed`（speed 默认 1.0） | `ReverseStepHack.java:90-94`：改 `deltaMovement.y` | 不搬：本工程 `PlayerMoveEvent` 是**无参**通知（`src/main/java/net/wurstclient/events/PlayerMoveListener.java:17-27`，只在 `src/main/java/net/wurstclient/mixin/ClientPlayerEntityMixin.java:172-177` 触发），`event.y` 这条路在 1.20.1 不存在 |
| 触发时机 | `.../InstantDrop.kt:31-32`：`onGround` 且 `event.y ∈ [-0.08, 0]` | `ReverseStepHack.java:79-88`：只在地面**之外**，并用 `initiatedJump` 锁存屏蔽跳跃与上升 | 窗口互补；本工程多覆盖「刚走离边缘的第一 tick」，不欠参考的 |
| 落点量化 | `.../InstantDrop.kt:35-36` + `:43-62`：`world.getGroundLevel()` 逐格下扫，`:50` 靠 `break` 提前退出 | `ReverseStepHack.java:106-117`：`expandTowards` 扫 `maximumFallDistance` 格 + 中心列射线，底层 `src/main/java/net/wurstclient/util/BlockUtils.java:176-184` | 本工程已是等价且更干净的实现（碰撞箱级），参考的扫描更粗 |
| 守卫覆盖面 | `.../InstantDrop.kt:26-34`：sneak/jump/elytra/flight/ladder/water/lava | `ReverseStepHack.java:99-104`：water/lava/climbable/fallflying/passenger/spectator/flight | 本工程是参考的**严格超集**（多 passenger/spectator）；唯一少的 sneak 见「故意不搬」 |
| 危险落点过滤 | 参考**完全没有**这个概念（只有落差判断） | `ReverseStepHack.java:31-32,119-120` | 本工程独有；5 项里有 2 项在 1.20.1 上永远命中不了（见「建议」），但构造不出有害输入，**不记为 bug** |
| 设置项 | `.../InstantDrop.kt:20-21`（Height 0..3、Speed 0..10） | `ReverseStepHack.java:34-44`（Mode/Motion/Factor/Maximum fall distance） | 不搬，也不动设置名与默认值 |

## 实际改动
无。（本轮被限定为只写本文件；参考的 InstantDrop 没有任何一条能在 1.20.1 上产生可验证的正确性改进，本工程这几处本来就更完整。）

## 故意不搬
- `event.y -= speed`（`.../InstantDrop.kt:37`）：1.20.1 的 `PlayerMoveEvent` 没有坐标三元组，只能改成写 `deltaMovement`，本工程已经是这个做法。
- `world.getGroundLevel()`（`.../InstantDrop.kt:43-62`）：1.12.2 的土法逐格扫描 + `break` 提前退出，等价物 `BlockUtils.getBlockCollisions()` 已是碰撞箱级。
- sneak 守卫（`.../InstantDrop.kt:26`）：地面潜行本来就走不下边缘（原版 `isStayingOnGroundSurface`，`ClientPlayerEntityMixin.java:299-306`），空中潜行时「快速落地」正是本模块的目的，加守卫等于删功能。

## 建议（未做）
- `DANGEROUS_LANDINGS` 的 `WATER` / `COBWEB` 两项在 1.20.1 上**任何输入都命中不了**：落点靠 `BlockUtils.raycast(from,to)`（`ReverseStepHack.java:114-115`），它写死 `ClipContext.Block.COLLIDER` + `Fluid.NONE`（`src/main/java/net/wurstclient/util/BlockUtils.java:145-146,151-154`）。`Fluid.NONE` 的 `canPick` 恒假、`getFluidShape` 直接返回空形状（1.20.2 `world/level/ClipContext.java:43-45,66`），所以液体永不成为命中；`COBWEB`（1.20.2 `world/level/block/Blocks.java:715-721`）与 `WATER`（同文件 `:315-322`）都是 `.noCollission()`，而 COLLIDER 射线取的是 `getCollisionShape`（1.20.2 `world/level/ClipContext.java:48`），空形状既进不了 `getBlockCollisions` 也永不被返回。`POWDER_SNOW` 只在 `fallDistance > 2.5` 时才有碰撞（1.20.2 `world/level/block/PowderSnowBlock.java:99-114`），而 `fallDistance > maximumFallDistance`（默认 3，`ReverseStepHack.java:103`）时整条判断又被跳过 ⇒ 只在 (2.5, 3] 这个窗口可达。
- 但**不建议改，也不记为 bug**：能构造出的几何里，1.8 格高的碰撞箱落地后仍与网 / 细雪所在格相交，`Entity#checkInsideBlocks` 按最终碰撞箱逐格回调（1.20.2 `world/entity/Entity.java:984-1013`），`WebBlock#entityInside`（1.20.2 `world/level/block/WebBlock.java:16-18`）照样生效 ⇒ 给不出「旧行为有害、新行为无害」的具体输入，按规范不许写成 bug。
- 若日后仍要按作者意图修：最小改法是在 `canAccelerateFall()` 内沿中心列用 `MC.level.getBlockState` 判 `DANGEROUS_LANDINGS`；想复用原版语义可用 `ClipContext.Block.FALLDAMAGE_RESETTING`（1.20.2 `world/level/ClipContext.java:51`），但 `BlockUtils.raycast` 没暴露该参数，得动共享文件 `src/main/java/net/wurstclient/util/BlockUtils.java`，本轮未动。
