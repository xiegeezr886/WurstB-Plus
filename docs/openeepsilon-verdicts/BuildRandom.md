状态：已优化

## 对照证据

参考侧有 `player/BuildRandom.kt`（1.12.2 随机位置放置）。本项为自审，全文读过
`hacks/BuildRandomHack.java`（206 行）。

## 实际改动：`Place while riding` 用的判断在 1.20 里不表示"骑乘"

```java
		if(!placeWhileRiding.isChecked() && MC.player.isHandsBusy())
			return;
```

`LocalPlayer.isHandsBusy()` 在 1.20.2 里的含义是 **"正在驾驶船并按下方向键"**
（反编译源 `client/player/LocalPlayer.java:877-888`：每个 `rideTick()` 先把 `handsBusy` 置 false，
只有受控载具是 `Boat` 时才按 `input.left/right/up/down` 置位）。

**反例（Rule 9）**：设置里 `Place while riding` 未勾选（默认），骑**马/驴/矿车/猪**时 `isHandsBusy()`
恒为 false ⇒ BuildRandom 仍在放置方块，与设置说明 "Builds even while you are riding a vehicle"
（未勾选=骑乘时不建）相反；反过来，骑着船但没按方向键时它也**不**拦截。已改成
`MC.player.isPassenger()`，覆盖所有载具（且包含原来的船+方向键情况）。

## 自审记录

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 触发间隔 | `:136` 未勾选 `Always FastPlace` 时看 `MC.rightClickDelay`；`:179` 放置后设 4 | 正确 |
| 检查手持物 | `:139-141` `isHolding(非空 且 是 BlockItem)` | 与设置说明一致 |
| 挖掘中不建 | `:143-145` 未勾选 `Place while breaking` 时看 `gameMode.isDestroying()` | 正确 |
| 随机尝试 | `:150-165` `do{...}while(attempts < maxAttempts && !tryToPlaceBlock(pos))` | 正确（最大尝试次数可配 1~1024） |
| 位置校验 | `:170-177` 可替换 + 有放置参数 + 距离 + 可选视线 | 正确 |
| 朝向 | `:180` `facing.getSelected().face(...)`（`FacingSetting.withoutPacketSpam()`） | 正常 |
| 指示框 | `:188-205` 只在 `lastPos != null` 且勾选 `Indicator` 时画 | 正确 |
| 关闭 | `:120-126` 清 `lastPos` + 摘监听 | 正确（但 `:121-126` 的顺序是先清 `lastPos` 再摘监听，无影响） |

## 建议（未做）

- `:41` `private SliderSetting maxAttempts` 没有 `final`（同文件其它设置项都是 `final`）。
  只是风格不一致，未改。
