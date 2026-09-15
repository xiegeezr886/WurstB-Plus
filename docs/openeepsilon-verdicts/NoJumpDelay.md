状态：已优化

## 对照证据

参考侧没有对应模块。本项为自审，全文读过 `hacks/NoJumpDelayHack.java`（79 行旧版）。

## 实际改动：这个模块从来没生效过（反射找错类）

旧实现用反射找跳跃冷却字段：

```java
		String[] names =
			{"jumpDelay", "noJumpDelay", "jumpingCooldown", "autoJumpTime"};
		Field field = LocalPlayer.class.getDeclaredField(name);
```

**反例（Rule 9）**：1.20 的跳跃冷却是 `LivingEntity.noJumpDelay`
（1.20.2 反编译源 `world/entity/LivingEntity.java:220` 声明 `private int noJumpDelay;`，
`:2565` 递减、`:2632` 判定 `noJumpDelay == 0` 才 `jumpFromGround()`、`:2644` 归零），
它**不在** `LocalPlayer` 上。`Class.getDeclaredField()` 只查本类、不查父类 ⇒ 四个候选名字全部抛
`NoSuchFieldException`，被 `catch(Exception e)` 静默吞掉 ⇒ `jumpDelayField` 永远是 `null`，
`onUpdate` 里那句 `setInt` 永远不执行。表现为：按住空格也不会连跳，模块等于没用。

修法：改用本工程已有的 accessor（`mixin/LivingEntityAccessor.java`，本来就登记在
`wurst.mixins.json:56`）：

```java
	@Accessor("noJumpDelay")
	public void wurst_setNoJumpDelay(int noJumpDelay);
```

```java
		((LivingEntityAccessor)player).wurst_setNoJumpDelay(0);
```

比反射更好的地方：mixin 的 `@Accessor` 会经过 refmap 重映射，运行时（字段名被映射成
SRG 名时）也不会像反射那样找不到；同时删掉了 40 行反射与静默 catch。

时序说明：UpdateEvent 在 `LocalPlayer.tick()` 的 `super.tick()` 之前触发
（`mixin/ClientPlayerEntityMixin.java:64-70`），而 `aiStep()` 里的递减与判定都在那之后，
所以"每 tick 归零"能生效。

## 未做

- 没有加"只在按住跳跃键时归零"之类的限制：原版 `aiStep()` 在没按跳跃时本来就会把
  `noJumpDelay` 归零（`:2644`），所以每 tick 归零不会改变其它行为。
