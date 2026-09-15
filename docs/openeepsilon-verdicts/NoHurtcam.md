状态：不适用

## 对照证据

参考侧没有对应模块（参考的 `render/AntiHurtCam.kt`? 实际参考里没有单独模块）。
本工程 `hacks/NoHurtcamHack.java`（24 行）是标记模块，逻辑在
`GameRendererMixin.java:186`（`if(...noHurtcamHack.isEnabled())` 时跳过受伤抖动），已核对受开关控制。

无改动。
