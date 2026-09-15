状态：已优化

## 对照证据

参考侧没有对应模块（参考的 `misc/AntiCrasher.kt` 是防崩溃包过滤，不是"按类型取消原版包"）。
本项为自审，全文读过 `hacks/PacketCancellerHack.java`（100 行）。

## 实际改动：`Cancel Movement` 一个包都取消不了

旧实现按**类的精确相等**判断：

```java
		if(cancelledTypes.contains(event.getPacket().getClass()))
			event.cancel();
```

而 "Cancel Movement" 加进集合的是抽象类 `ClientboundMoveEntityPacket`（`:78`），
服务器真正发出来的是它的三个子类 `ClientboundMoveEntityPacket.Pos` / `.Rot` / `.PosRot`
（1.20.2 反编译源 `net/minecraft/network/protocol/game/ClientboundMoveEntityPacket.java` 里
`Pos`/`Rot`/`PosRot` 都是独立的 `static class`）。

**反例（Rule 9）**：勾上 `Cancel Movement`，进入有其它实体的服务器。
所有实体移动包照常被处理（`getClass()` 返回的是子类，永远不等于抽象父类），
表现为"开关勾了完全没有效果"，而其它四个开关（都是非抽象类）是正常工作的。

修法：改成 `isInstance` 判断（`:85-104`），父类能匹配到子类：

```java
	private boolean isCancelled(Packet<?> packet)
	{
		for(Class<?> type : cancelledTypes)
			if(type.isInstance(packet))
				return true;
		
		return false;
	}
```

## 建议（未做）

- `onReceivedPacket`/`onSentPacket` 每收到一个包都调用一次 `updateCancelledTypes()`
  （清空 + 最多 5 次判断）。功能正确但每个包都白做一遍，正确做法是监听设置变化；
  本工程 `CheckboxSetting` 没有变更回调，加回调属于跨模块改动，未做。
- `cancelPlayerInfo` 只匹配 `ClientboundPlayerInfoUpdatePacket`（原版还有
  `ClientboundPlayerInfoRemovePacket`），要一起取消才算彻底；属功能取舍，未改。
