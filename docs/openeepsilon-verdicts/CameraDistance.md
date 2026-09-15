状态：不适用

## 对照证据

参考侧有 `render/Zoom.kt`（变焦），与"相机距离"不同。本项为自审，全文读过
`hacks/CameraDistanceHack.java`（35 行）。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 滑块范围 | `:19-20` -0.5 ~ 150（允许负值=反向） | 正常 |
| 消费点 | `CameraMixin.java:28-29` 取 `cameraDistanceHack` 实例，`:40` 一带判 `cameraNoClipHack.isEnabled()` | 已核对 |

无改动。

## 建议（未做）

- `CameraMixin` 里对 `cameraDistanceHack` 是"取实例后调 getter"，getter 自身不判 `isEnabled()`；
  当前相机距离只在 mixin 内部按需读取，没有构造出"关掉后仍生效"的反例（`changeClipToSpaceDistance`
  是每帧重算的），只记录。
