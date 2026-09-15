状态：不适用

## 对照证据

参考侧有 `render/NoFog.kt`（1.12.2 靠 `EntityViewRenderEvent.FogDensity` 事件）。
本工程 `hacks/NoFogHack.java`（24 行）是标记模块，逻辑在
`BackgroundRendererMixin.java:36`（`if(!noFogHack.isEnabled() ...)` 时才走原版雾），已核对受开关控制。

| 关注点 | 参考 | 本工程 | 判定 |
| --- | --- | --- | --- |
| 机制 | 事件改雾密度 | mixin 跳过整段雾渲染 | 1.20 的雾有多个类型（水/岩浆/细雪/失明等），跳过整段更彻底 |
| 开关 | 有 | 有 | 一致 |

无改动。
