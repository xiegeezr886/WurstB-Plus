状态：已优化

## 对照证据

参考侧没有对应模块（`render/Animations.kt` 是手部动画、`render/ViewModel.kt` 是第一人称模型，
都不是视角摇晃）。本项为自审 + 修一处会永久改动玩家设置的写法。

## 自审记录

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 摇晃效果强度 | `hacks/DankBobbingHack.java:53-60`：按 `speed * intensity` 叠加到 `walkDistO` | 正常 |
| 只在地面生效 | `:54-55` `!onGround()` 直接返回 | 正常 |
| 强度范围 | `:21-22` `Intensity` 0..5 | 正常 |
| 关闭视角摇晃 | `:61-64` `shouldDisableViewBob()` | 见下 |

## 实际改动：视图摇晃设置被永久改掉且从不还原

旧实现：

```java
		if(noViewBob.isChecked())
			MC.options.bobView().set(false);      // 只关，不开；且只在勾选时执行
```

**反例（Rule 9）**：你的视频设置里"视角摇晃"本来是开着的，勾上 `No View Bob` 再关掉 DankBobbing
（或只是取消勾选），`MC.options.bobView()` 仍然是 `false` —— 旧代码没有任何还原路径，
而且这个改动会被写进 `options.txt` 持久保留，表现为"关掉客户端功能后视角摇晃再也回不来了，
得手动去视频设置里打开"。

另外全工程搜索确认 `shouldDisableViewBob()` 除了定义没有任何调用点，
也就是说这个功能**完全依赖**上面那行改设置，没有 mixin 兜底。

修法：记住关掉之前的原值，取消勾选或关闭模块时还原（`:49-99`）：

```java
	private void updateViewBobOption()
	{
		if(shouldDisableViewBob())
		{
			if(savedViewBob == null)
			{
				savedViewBob = MC.options.bobView().get();
				MC.options.bobView().set(false);
			}
			return;
		}
		restoreViewBobOption();
	}
```

`onUpdate` 每 tick 调用它（放在"不在空中就 return"之前，保证在地面/空中行为一致），
`onDisable` 也调用一次。

## 建议（未做）

- `shouldDisableViewBob()` 仍然保留为公开方法，方便以后用 mixin 接管（那样就不必动原版设置）；
  目前没有这样的调用点。
- `Intensity` 只作用于 `walkDistO`（老式摇晃），不影响原版 1.20 的 `bobView` 摄像机运动曲线，未改。
