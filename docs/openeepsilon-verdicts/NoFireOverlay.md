状态：不适用

## 对照证据

参考侧有 `render/NoRender.kt` 的火焰部分。本工程 `hacks/NoFireOverlayHack.java`（36 行）
用"把火焰覆盖层往下移"的方式实现（不是取消），逻辑在
`InGameOverlayRendererMixin.java:29`：

```java
		return original - WurstClient.INSTANCE.getHax().noFireOverlayHack.getOverlayOffset();
```

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 关掉后恢复 | `:30-33` `getOverlayOffset()` 在未启用时返回 0（而不是返回滑块值） | 正确（这是"关掉仍生效"的常见坑，这里避开了） |
| 偏移范围 | `:19-21` 0.01 ~ 0.6，默认 0.6（最大=完全移出视野） | 正常 |

无改动。
