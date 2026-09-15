状态：不适用

## 对照证据

参考侧有 `render/NoRender.kt`（1.12.2 里把水/雪/火等覆盖层一起关掉）。
本工程 `hacks/NoOverlayHack.java`（26 行）是标记模块，对应三个 mixin，已逐个核对都受开关控制：

- `CameraMixin.java:50`（水中/岩浆中的 `getSubmersionType`）
- `IngameHudMixin.java:55`（`renderOverlay` 的方块覆盖层）
- `InGameOverlayRendererMixin.java:39`（水下覆盖层）

无改动。

## 说明

- 1.20 把"覆盖层"拆成了好几处（水下、细雪、方块、传送门），本模块覆盖了其中三处；
  传送门覆盖层由 `Overlay` 模块另行处理。未合并。
