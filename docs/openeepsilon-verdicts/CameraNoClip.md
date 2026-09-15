状态：不适用

## 对照证据

参考侧没有单独对应模块（参考的 `render/FreeCam.kt` 是自由视角，1.12.2 的相机穿墙靠它）。
本工程 `hacks/CameraNoClipHack.java`（24 行）是标记模块，逻辑在
`CameraMixin.java:40`（`if(...cameraNoClipHack.isEnabled())`），已核对受开关控制。

无改动。
