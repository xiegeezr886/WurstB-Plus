状态：不适用

## 对照证据

参考侧有 `module/misc/NoRotate.kt`（28 行）：收到 `SPacketPlayerPosLook` 时把包里的
`rotationYaw`/`rotationPitch` 改成玩家当前朝向（无条件改写，没有开关）。

本工程 `hacks/NoRotateHack.java`（68 行）更细：通过 `ClientboundPlayerPositionPacketMixin` 的
`@Accessor("yRot"/"xRot")` 改包内字段，且分成 `Preserve yaw`（默认开）/`Preserve pitch`（默认开）
两个开关，并用 `util/RotationCorrectionPolicy.packetRotation(...)` 处理"包本身是相对旋转"
（`RelativeMovement.Y_ROT`/`X_ROT`）的情况：

```java
			accessor.wurst_setYRot(RotationCorrectionPolicy.packetRotation(
				MC.player.getYRot(), relatives.contains(RelativeMovement.Y_ROT)));
```

| 关注点 | 参考 | 本工程 | 判定 |
| --- | --- | --- | --- |
| 改写字段 | 直接赋值 | accessor 改包内字段 | 等价 |
| 相对旋转 | 未处理（1.12.2 的 `SPacketPlayerPosLook` 没有相对标志） | 用 `RotationCorrectionPolicy` 处理 | 本工程正确 |
| 分开控制 yaw/pitch | 无 | 两个开关 | 本工程更细 |

无改动（本行此前在覆盖表里是"待办"，代码已经是新写法；与 `Reach`/`Timer`/`TooManyHax` 一样属于重复列出的行）。
