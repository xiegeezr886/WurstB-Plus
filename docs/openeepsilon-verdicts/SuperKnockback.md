状态：已优化

## 对照证据

| 关注点 | 参考（_oe_ref 相对路径:行） | 本工程（相对路径:行） | 判定 |
| --- | --- | --- | --- |
| 这个 hack 做什么 | 参考没有独立的 SuperKnockback 模块；它的击退增强是 KillAura 里的疾跑重置 | `hacks/SuperKnockbackHack.java` 靠发 `ServerboundPlayerCommandPacket`（`START_SPRINTING` / `STOP_SPRINTING`）在攻击前把服务端的疾跑状态立起来，从而拿到原版的疾跑击退加成 | 手法不同：本工程是**动作包**而不是 1.12.2 的 `CPacketEntityAction` 变体，1.20.1 上仍然成立，属于合法且更干净的做法 |
| 「这次攻击会不会本来就能暴击」 | —— | `hacks/SuperKnockbackHack.java:105-114`（改后）走 `util/SuperKnockbackCritPolicy.java`；判据逐项对齐原版 `Player#attack` | **真实缺陷，已修**（见下） |
| 原版暴击判据 | 1.12.2 的 `EntityPlayer#attackTargetEntityWithCurrentItem` | 本工程 `hacks/KillauraHack.java:1186-1190` 的 `CriticalsSelectionMode.ALWAYS.allowsAttack` 早就把判据写全了（含 `!isSprinting()`、`!hasEffect(BLINDNESS)`、`!isPassenger`） | 本工程内部本来就有一条正确实现，SuperKnockback 只抄了一半 |
| 设置项 | —— | `skipCriticals`（默认开）等设置名/默认值**一字未动** | 无兼容性影响 |

## 实际改动

修复「`skipCriticals` 在它唯一有用的场景里必然跳过重置」这个真实缺陷。

- 旧实现（`SuperKnockbackHack.java:103-107`，已替换）：
  ```java
  boolean naturalCritical = MC.player.fallDistance > 0
      && !MC.player.onGround() && !MC.player.onClimbable()
      && !MC.player.isInWaterOrBubble() && !MC.player.isInLava();
  boolean critical = skipCriticals.isChecked() && naturalCritical;
  ```
- 新实现：`util/SuperKnockbackCritPolicy.isNaturalCritical(State)`，判据 = 原版 `Player#attack` 的 `flag2`：
  `getAttackStrengthScale(0.5F) > 0.9F && fallDistance > 0 && !onGround && !onClimbable && !isInWater && !isSprinting && !isPassenger && !hasEffect(BLINDNESS)`。
- **反例（新旧行为差别）**：玩家在疾跑中跳跃、滞空下落、攻击充能已满（`fallDistance = 0.5`、`onGround=false`、`sprinting=true`）。
  - 旧：`naturalCritical = true` → 因 `skipCriticals` 默认开启，**跳过疾跑重置**，这一次攻击拿不到疾跑击退，而它也不会暴击（原版 `flag2 = flag2 && !isSprinting()` 使暴击不成立）。
  - 新：`isNaturalCritical = false` → 正常执行重置，拿到疾跑击退。
  也就是说旧实现在「疾跑跳跃攻击」这个本 hack 最常被使用的场景里等于没开。
- 证据：原版 `Player#attack` 的 `flag2` 在 1.20.1 里含 `!isSprinting()`；本工程 `hacks/KillauraHack.java:1186-1190` 的等价判据同样含 `!isSprinting()`，两处互相印证。
- 新增文件：`util/SuperKnockbackCritPolicy.java`（纯类，无 `net.minecraft` 依赖）、`test/.../SuperKnockbackCritPolicyTest.java`（6 个测试，其中一条专门锁住「疾跑跳跃下落不算暴击」这个回归）。
- 调用点在 `MultiPlayerGameMode#attack` 的 HEAD（本工程 `ClientPlayerInteractionManagerMixin`），即原版 `Player#attack` 之前，因此读到的 `getAttackStrengthScale(0.5F)` 与原版随后自算的 `f2` 是同一个值。

## 故意不搬

- 参考没有这个模块，没有可搬的算法；它的疾跑重置写在 KillAura 内部，与本工程「独立 hack + 动作包」的设计不同，不需要对齐。

## 建议（未做）

- 没有实机验证：新旧行为差别是**读原版判据 + 本工程另一处等价实现**推出的，未在游戏里对击退距离做过对照测量。
- `hacks/SuperKnockbackHack.java` 里 `State`（`util/KnockbackBoostPolicy.State`）仍由调用方逐项拼装，与本次新增的 `SuperKnockbackCritPolicy.State` 字段有重叠；合并两个 record 会牵动 `KnockbackBoostPolicy` 与其既有测试，留给后续。
