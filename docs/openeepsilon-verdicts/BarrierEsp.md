状态：不适用

## 对照证据

参考侧有 `render/BlockESP.kt`（把屏障显示成方块）。本工程 `hacks/BarrierEspHack.java`（24 行）
是标记模块，逻辑在 `ClientWorldMixin.java:39`：

```java
		if(!WurstClient.INSTANCE.getHax().barrierEspHack.isEnabled())
			return ...;
```

已核对受开关控制；屏障方块用 `Blocks.BARRIER` 的完整碰撞箱渲染（1.20 里屏障的
`getCollisionShape` 是空的，必须靠 mixin 替换，这个实现路径正确）。

无改动。
