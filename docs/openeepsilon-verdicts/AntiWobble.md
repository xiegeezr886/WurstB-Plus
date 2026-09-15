状态：不适用

## 对照证据

参考侧有 `render/AntiNausea.kt` / `render/NoWobble.kt`（取消反胃/视角摇晃）。
本工程 `hacks/AntiWobbleHack.java`（25 行）是标记模块，逻辑在
`GameRendererMixin.java:161`（`!antiWobbleHack.isEnabled()` 时才做 `wurstNauseaLerp()`），
已核对受开关控制。

无改动。

## 建议（未做）

- 模块名/搜索标签把"反胃（nausea）"和"走路摇晃（view bobbing）"混在一起，实际只处理反胃。
  走路摇晃由客户端设置 `View Bobbing` 控制，未改。
