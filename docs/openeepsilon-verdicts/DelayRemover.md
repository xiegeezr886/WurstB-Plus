状态：已优化

## 对照证据

参考侧 `_oe_ref/src/main/kotlin` 全目录搜 `missTime|resetAttackStrengthTicker` **0 命中**，
`module/combat/` 26 个模块里也没有 NoHitDelay / DelayRemover 之类的对应物。所以这一项没有可搬的
实现，属于自审 + 修既有实现里的死代码。

> 下文的原版行号取自本仓库唯一解压好的原版源：
> `neoforge/versions/1.20.2/build/neoForm/.../unzipSources/unpacked/net/minecraft/**`（1.20.2）。

## 实际改动：反射改成直接调用

旧实现（本次改动前）：

```java
			resetAttackStrengthTicker = LocalPlayer.class
				.getDeclaredMethod("resetAttackStrengthTicker");
			...
		}catch(Exception e)
		{
			// method not found in this mapping
		}
```

`resetAttackStrengthTicker()` 是 **public** 且**声明在 `Player` 上**（`Player.java:2032-2033`），
`LocalPlayer` 只是继承它；而 `Class.getDeclaredMethod()` 按定义只看本类声明的成员，不查父类 ⇒
每次都抛 `NoSuchMethodException`，被空 catch 吞掉 ⇒ **这个 hack 一直是个彻底的空操作**
（反例：开着 DelayRemover 打任何生物，`attackStrengthTicker` 从来不会被这段代码碰过）。
现在改成直接调用 `MC.player.resetAttackStrengthTicker()`（`hacks/DelayRemoverHack.java:52`）。

## 但要如实说清楚：1.20.1 里这一步没有可观察效果

- `MultiPlayerGameMode.attack()` 每次攻击都会调 `resetAttackStrengthTicker()`
  （`MultiPlayerGameMode.java:195`），原版 `Player.attack()` 里也调了一次
  （`Player.java:1307`，带 FORGE 注释），所以"攻击后重置"是重复动作。
- 攻击冷却造成的伤害系数由**服务端**计算，客户端改不了。
- 客户端侧真正还存在的攻击延迟只有一个：`Minecraft.missTime`
  （`Minecraft.java:1669` 是唯一的 `missTime > 0` 门槛；`:1674` 与 `:1704` 在未命中时置 10；
  `:1835` 打开界面时置 10000）。这一项由 `NoMissCooldown` 负责。

也就是说：这个 hack 保持"开着也不会改变任何可观察行为"的事实，本次只把"静默抛异常的反射"
换成能真正执行的直接调用，不再留一条永远走不通的代码路径。结论记在文档里，避免以后又当成 bug 排查。

## 建议（未做）

- 如果希望这个 hack 在 1.20.1 真的有用，唯一能做的是清零 `missTime`，但那与 `NoMissCooldown`
  完全重复（`hacks/NoMissCooldownHack.java:53-58`），不建议做两份。
- `SearchTags` 里的 `"no cooldown"`（`hacks/DelayRemoverHack.java:14`）会让搜索 `no cooldown` 时
  同时命中两个 hack，属提示文本问题，未改。
