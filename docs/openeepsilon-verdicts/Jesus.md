状态：已优化

## 对照证据

参考侧：`_oe_ref/src/main/kotlin/studio/coni/epsilon/module/movement/Jesus.kt`（两个模式：Solid 加碰撞箱、Dolphin 抬升）。

| 关注点 | 参考（Jesus.kt:行） | 本工程（hacks/JesusHack.java:行） | 判定 |
| --- | --- | --- | --- |
| 核心手法 | `:67-80` 监听 `AddCollisionBoxEvent`，在水面上叠一个 `1.0×0.99×1.0` 的碰撞箱（`:31`），真的"踩"在水上 | `:74-80` 在水/岩浆里把 `vy` 设成 `0.11`；`:82-87` 出水后 `tickTimer==0` 给 `vy=0.30`（模拟跃出水面） | 不搬碰撞箱：1.20.1 没有 `AddCollisionBoxEvent` 等价物（本工程 `events/` 无碰撞箱事件），要做得新增 mixin 与事件总线，属功能新增而非重构既有实现，见「故意不搬」 |
| Dolphin 模式 | `:45-46` `player.motionY += 0.04` | 无对应模式，本工程是"入水抬升 0.11 + 出水跃起 0.30" | 1.12.2 的 `motionY += 0.04` 是往上一档；本工程 `0.11` 是绝对赋值（`setDeltaMovement(v.x, 0.11, v.z)`），二者在 1.20.1 的液体物理下不是同一件事，保留本工程口径 |
| 位置包伪造 | `:56-65` 每 2 tick 把发出的 `CPacketPlayer.y += 0.02`（`isAboveLiquid` 判水面） | `:94-139` 取消并重发 `ServerboundMovePlayerPacket`，`bypass` 打开时 `tickCount % 2 == 0` 才抬 Y，另有 `packetTimer < 4` 节流 | 同一思路的 1.20.1 版本，且多了节流与 `packet instanceof Pos/PosRot` 过滤；保留 |
| 长按潜行 | `:43,69` 潜行直接 `return` | `:68-69` 潜行 `return` | 一致 |
| 摔落保护 | `:43,73` `fallDistance > 3` 直接 `return` | `:112-113` `fallDistance > 3F` 直接 `return` | 一致（数值也一样） |
| 计时器状态 | 1.12.2 无此状态机 | `:37` `tickTimer = 10`；`:78` 在水里每 tick 归零；`:58-62` `onDisable()` **不重置** | **有真实缺陷**，见下 |

## 实际改动

`hacks/JesusHack.java` `onEnable()` 增加 `tickTimer = 10;`（与字段初值一致）。

新旧行为差异（可举反例）：
1. 开启 Jesus → 走进水里（`:74-79` 每 tick 把 `tickTimer` 归零）；
2. **在水里**关掉 Jesus（`:58-62` 只摘监听器，`tickTimer` 保持 0）；
3. 走回岸上；
4. 再开 Jesus → 第一帧 `onUpdate()`：不在水里 → 直接落到 `:84 if(tickTimer == 0)` → `setDeltaMovement(v.x, 0.30, v.z)`。

也就是凭空一次 `vy = 0.30` 的上抛。按原版竖直积分（`vy = (vy - 0.08) * 0.98`）这次上抛约 0.65 格，视觉上就是"刚开启就自己跳了一下"。这个冲量本该只在「刚从水里出来」时给，岸上给是没有依据的。改成 `10`（与 `:37` 的字段初值相同，说明本来就是初始状态）后该分支只在真正出水的那一 tick 命中。

## 故意不搬

- `Jesus.kt:31,67-80` 的碰撞箱水上行走：需要 1.20.1 的 `Entity` 碰撞箱注入 + 新事件类型，属新增功能；且会改变走水的物理表现（能站在水面上不被拽下去），与现有「入水抬升」是两个不同的 hack 语义，不做。
- `Jesus.kt:33-39` 的 `BaritoneUtils.settings.assumeWalkOnWater`：本工程没有 Baritone 集成，无对应物。
- `Jesus.kt:50-52` 骑乘实体时给坐骑 `motionY = 0.3`：本工程未处理骑乘（`:130-139` 只处理玩家自身的位置包），属功能新增，不做。

## 建议（未做）

1. `packetTimer`（`:38`）同样不在 `onEnable()` 重置。它不是状态机而是节流计数（`:126-128` 只用来"每 4 个包才抬一次 Y"），残留值只会让开启后的第一次抬升早一点发生，不改变正确性，未动。
2. `:119-123` `MC.player.input == null` 时直接 `event.cancel()`：这会连带取消掉位置包，等于在那一 tick 把自己钉在原地。1.20.1 里 `input` 为 null 只在极少数初始化窗口出现，未发现可复现反例，留档不修。
3. 验证边界：本次只做了 `compileJava`/`test`，没有实机入水/出水跑过，也没有连服务器验证位置包抬升的通过率。
