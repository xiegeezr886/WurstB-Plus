状态：不适用

## 对照证据

参考侧 `module/combat/` 里**没有** TP-Aura（26 个模块里没有对应物；1.12.2 的同类手法是包级
"blink/瞬移"，参考也没实现）。所以没有可搬的实现，只能做"这个机制在 1.20.1 成不成立"的自审。

## 实际改动

无（零改动）。

## 为什么这个 hack 在 1.20.1 上不成立（自审结论）

`TpAuraHack.java:145-154` 的做法是：

```java
		// teleport
		player.setPos(entity.getX() + random.nextInt(3) * 2 - 2,
			entity.getY(), entity.getZ() + random.nextInt(3) * 2 - 2);
		...
		MC.gameMode.attack(player, entity);
```

1. `setPos` 只是**客户端**位置，不发任何包。
2. 原版攻击包（`ServerboundInteractPacket`）**本身不带坐标**，服务端用的是它自己那份玩家坐标
   做 reach 校验（`ServerPlayer` 与目标的距离）。
3. 客户端位置每 tick 才由 `LocalPlayer.sendPosition()` 上报一次，而本工程的 `onUpdate` 跑在它**之前**：
   `mixin/ClientPlayerEntityMixin.java:64-70` 把 `UpdateEvent` 挂在 `ClientPlayerEntity.tick()` 里
   `AbstractClientPlayer.tick()`（`super.tick()`）那一处，`sendPosition()` 在这之后才执行。
   ⇒ 同一 tick 内，攻击包**先**到服务端，位置包**后**到。

结论：服务端收到攻击时仍然认为你站在传送前的位置，于是"先瞬移再打"这一下按旧坐标被拒（超出 reach）。
顺序反过来的话（先发位置包再攻击）确实能骗过没有反作弊的服务端，那就是 desync/blink 一类包操作，
和"TP-Aura"名义上的东西已经不是一回事，而且会被主流反作弊直接标记。另外 `:146-147` 直接把玩家的 Y
设成目标的 Y，可能把客户端玩家放进方块里（客户端 `setPos` 不做碰撞检查）。

这与本文件其余 hack 里"参考是 1.12.2 手法、1.20.1 没有等价物"是同一类结论（见
`docs/openeepsilon-refactor.md` §0.3），所以状态记 **不适用**、不动代码。

## 建议（未做）

- `TpAuraHack:166-191` 自带一套 `Priority`（DISTANCE/ANGLE/HEALTH），与
  `util/CombatTargetUtils.Priority` 重复，且选择时没有 FOV / LOS 口径（`:117-125` 只用
  `distanceToSqr`）。统一到共享实现属于行为取舍（会改变这个 hack 的选人结果），未做。
- 如果以后真要保留这个功能，正确的实现是"发包把位置同步过去再攻击"，并且应当在设置里明确写出
  "只对无反作弊服务器有意义"，避免被当成 bug 反复排查。
