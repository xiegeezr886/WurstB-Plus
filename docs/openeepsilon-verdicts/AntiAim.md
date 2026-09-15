状态：已优化

## 对照证据

参考侧有对应模块 `module/player/AntiAim.kt`（78 行）。

| 关注点 | 参考（`AntiAim.kt:行`） | 本工程（`hacks/AntiAimHack.java`） | 判定 |
| --- | --- | --- | --- |
| 模式 | `:30-54` Spin / Reverse / ReverseJitter / FakeJitter / Zero / Clown | Spin / Jitter / Invert / Down / Backwards | 各自实现，不一一对应 |
| 旋转速度 | `:16` `SpinSpeed` 1..50 度 | `:36-38` `Spin Speed` 1..180 度/tick | 本工程范围更大 |
| 服务端/本地 | `:18,56-64` `ServerSide` 开关：关掉只改 `rotationYawHead`/`renderYawOffset`（本地渲染），打开才发旋转包 | **原来没有**，总是改自己的真实视角 | 已补 `Silent rotation` |
| 俯仰锁定 | `:17,24-28` `LockPitch`（pitch 固定 90） | `Down` 模式 | 等价 |
| 生效时机 | `:23` 在 `OnUpdateWalkingPlayerEvent.Pre`（发移动包之前） | UpdateEvent（`LocalPlayer.tick()` 的 `super.tick()` 之前） | 都早于发包 |

## 实际改动 1：新增 `Silent rotation`（对照参考的 `ServerSide`）

参考默认只改**渲染用**的头部朝向、不动玩家真实视角；本工程原来一律
`MC.player.setYRot/setXRot`，等于把自己的镜头也拧着转。新增开关（默认关 = 保持旧行为）：

```java
	private void applyRotation(float newYaw, float newPitch)
	{
		if(silent.isChecked())
		{
			new Rotation(newYaw, newPitch).sendPlayerLookPacket();  // 只发给服务器
			MC.player.setYHeadRot(newYaw);
			MC.player.yBodyRot = newYaw;
			return;
		}
		MC.player.setYRot(newYaw);
		...
```

用的是本工程 `DerpHack` / `HeadRollHack` / `TiredHack` 已经在用的 `Rotation#sendPlayerLookPacket()`：
只发旋转包，本地视角不动；原版不会因此补发真实朝向（`sendPosition()` 比较的是客户端自己的朝向，
而我们没改它）。

## 实际改动 2：`Invert` 每 tick 翻 180°（20Hz 抖动）

旧实现：

```java
			MC.player.setYRot(MC.player.getYRot() + 180);
```

**反例（Rule 9）**：选 `Invert` 模式。第 1 tick 朝向变成"原方向 +180"，第 2 tick 读到的
`getYRot()` 正是自己写回的值，再加 180 ⇒ 又转回原方向，如此反复。结果不是"面朝反方向"，
而是视角以 20Hz 在两个相反方向之间抖动（发给服务器的朝向同样来回跳）。
改为以开启时朝向为基准稳定朝反方向（与 `Spin` 一样锁定视角）：`applyRotation(baseYaw + 180, ...)`。
代价是 Invert 模式下鼠标不再改变朝向，与 Spin 一致，已在代码注释里写明。

## 实际改动 3：`Backwards` 模式实际无效

旧实现直接取反 `player.input.forwardImpulse / leftImpulse`。但原版在同一个 tick 稍后会用
**按键状态**把这些值整个重算（`LocalPlayer.aiStep()` → `KeyboardInput.tick()`，
1.20.2 反编译源 `LocalPlayer.java:692`），而 UpdateEvent 是在 `LocalPlayer.tick()` 的
`super.tick()` **之前**触发的（`mixin/ClientPlayerEntityMixin.java:64-70`）⇒ 旧写法必然被覆盖。

**反例（Rule 9）**：选 `Backwards` 模式按住 W。按模式说明应该"看着前方往后退"，
实际是照常向前走 —— 取反的值在 `input.tick()` 里被重置为 +1，玩家永远不会后退。

修法：改为镜像移动键（与本工程 `AutoWalkHack` 设置 `keyUp` 是同一机制）——
先把四个移动键按玩家**真实**按键状态复位（`resetPressedState()`），再两两交换，
这样每 tick 都从物理输入重新推导，不会自己跟自己反复交换。关闭模块时同样复位。

## 未搬 / 建议（未做）

- 参考的 `ReverseJitter`、`FakeJitter`、`Zero`、`Clown` 四个模式没有搬（本工程的 5 个模式已覆盖主要用途）；
  `Reverse` 同时把 pitch +180 的做法在 1.20.1 会超出 ±90，未照搬。
- 参考在 `OnUpdateWalkingPlayerEvent.Pre` 里工作，本工程没有等价的原版事件；沿用 UpdateEvent 即可。
