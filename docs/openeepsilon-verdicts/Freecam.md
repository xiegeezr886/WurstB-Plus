状态：不适用（参考侧是 1.12.2 的「改写包坐标」手法，本工程的「取消包」自洽，零改动）

## 对照证据

参考侧：`_oe_ref/src/main/kotlin/studio/coni/epsilon/module/player/Freecam.kt`。

| 关注点 | 参考（Freecam.kt:行） | 本工程（hacks/FreecamHack.java:行） | 判定 |
| --- | --- | --- | --- |
| 服务端看到的身体 | `:107-113` **改写**发出去的 `CPacketPlayer` 的 `x/y/z/yaw/pitch` 为克隆体的坐标 | `:127-131` 直接 **cancel** 所有 `ServerboundMovePlayerPacket` | 两种做法都能让服务端认为身体没动。差别在"服务端收到的包长什么样"：参考保持稳定的包流（看起来像原地站着），本工程一个包都不发。这个差别**可能**影响"长时间无移动包"类检测，但构造不出可举证的失败输入，且改写需要给 `ServerboundMovePlayerPacket` 的 final 字段加新 accessor（新增 mixin），见「故意不搬」1 |
| 身体替身 | `:14,50` `EntityOtherPlayerMP(mc.world, mc.session.profile)` 复制玩家位置 | `:47,71` `new FakePlayerEntity()` | 等价：都用"另一个实体占着原位置"。参考用真实玩家模型类，本工程用自建假人（`hacks/BlinkHack.java` 等也在用同一个类） |
| 竖直移动 | `:79-86` 每 tick 清零 motion，然后跳跃 +speed / 潜行 -speed | `:104-118` 同样每 tick `setDeltaMovement(ZERO)`、`setOnGround(false)`，跳跃 `+speed`、潜行 `-speed`（默认 1，0.05..10） | 一致 |
| 水平移动 | `:23` Speed 滑块 | `:120-124` `AirStrafingSpeedEvent` 里 `setSpeed(speed)` | 本工程把水平速度接到共享的空中加速事件上，比直接写 motion 更贴合 1.20.1 的 `moveRelative` 流程 |
| 水的判定 | 无 | `:133-143` 把 `IsPlayerInWater`/`IsPlayerInLava` 事件强行置 false | 1.20.1 的水/岩浆会改 travel 分支并触发游泳姿态，本工程必须屏蔽；参考的 1.12.2 不需要 |
| 透视 | 无 | `:153-163` cancel `IsNormalCube`/`SetOpaqueCube`（见「建议」2） | 本工程多出的功能 |
| 关闭时的世界重建 | 无（`:57-64` 只清 motion） | `:100` `MC.levelRenderer.allChanged()` | 因为要撤销上面的"非实心方块"状态，必须让区块重新烘焙；1.20.1 的正确做法 |
| 键位状态 | 无 | `:73-78` 开启时把 6 个移动键的 pressed 状态复位 | 避免开启瞬间带着旧的按键状态漂移，合理 |
| 状态保存 | 无（Kotlin 模块不持久化） | `:31` `@DontSaveState` | 重启客户端不会带着 Freecam 上线，正确 |

## 实际改动

无。逐条核对后没有可举证的新旧行为差异，按简报第 9 条不动代码。

## 故意不搬

1. `Freecam.kt:107-113` 的「改写包坐标」：1.20.1 的 `ServerboundMovePlayerPacket` 里 `x/y/z/yRot/xRot` 是 final 字段，只有 `getX(float)` 这类读数方法，没有 setter——照搬必须新增一个 accessor mixin。而本工程的 `event.cancel()` 已经达到同样效果（服务端认为身体没动），收益只是"包流看起来更正常"，不足以承担新 mixin 的风险。
2. `Freecam.kt:24-25,116-123` 的 `Cancel Packets` 开关与 `Mode`（NCP/…）：参考用它决定"取消哪些包"（`CPacketUseEntity`/`CPacketVehicleMove`/`CPacketInput`）。本工程只取消 `ServerboundMovePlayerPacket`，交互包照发——这是**有意**的：Freecam 下仍要能右键/攻击。加这个开关属新增设置，不做。
3. `Freecam.kt:91` 骑乘实体时清坐骑 motion：本工程未处理骑乘（`hacks/FreecamHack.java` 不检查 `isPassenger`）。构造不出"因此出错"的输入（骑乘时原版本来就不会让玩家自己移动），仅留档于「建议」。

## 建议（未做）

1. 长时间取消全部移动包是否会被服务端的"无包/超时"逻辑判为掉线，需要实机连服务器验证；如果确有风险，最小修法是每个 N tick 补发一个"停在原点、带当前朝向"的 `ServerboundMovePlayerPacket`（不需要新 mixin），属行为变更，未做。
2. `:153-163` 把 `IsNormalCube`/`SetOpaqueCube` 全部 cancel，等于让**所有**方块在渲染上失去"实心"属性——这是 Freecam 常用的透视手法，但也意味着启用期间无法用视觉判断哪里能站。属既有语义，不改。
3. 验证边界：零改动，只做了静态核对；Freecam 与服务器的交互（尤其是关闭瞬间的位置回拉）没有实机验证。
