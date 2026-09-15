状态：不适用（参考侧无对应模块，本工程实现自洽，零改动）

## 参考侧核对

`_oe_ref/.../module/movement/` 的 25 个模块里没有"悬崖边起跳"类功能。最接近的 `AutoJump.kt`（25 行）是**无条件**的"能跳就跳"（`AutoJump.kt:22` `player.onGround && timer.tickAndReset(delay)`），没有边缘检测、没有下落深度判定，与 Parkour 的"只在真正会掉下去的边缘跳"不是一回事。故判「不适用」，主体是自审。

## 本工程现状（自审）

`hacks/ParkourHack.java`，79 行。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 生效条件 | `:63` `!onGround() \|\| MC.options.keyJump.isDown()` 直接返回 | 手动跳跃优先，正确 |
| 潜行 | `:66-68` 潜行键**按下状态或按键状态**任一成立就返回，除非 `sneak` 开关（`:31-36`，默认 false） | 同时看 `isShiftKeyDown()`（含被其他 hack 强制潜行）与 `keyShift.isDown()`（键盘），比只看按键更严格；与 `hacks/SneakHack.java` 的 PACKET 模式（不改本地潜行状态）不冲突 |
| 边缘检测 | `:70-75` `box.expandTowards(0, -minDepth, 0).inflate(-edgeDistance, 0, -edgeDistance)`，`MC.level.noCollision` 通过才跳 | 判定形状正确：向下扩 `minDepth`（默认 0.5，`:20-24`）找落脚点，水平内缩 `edgeDistance`（默认 0.001，`:26-29`）避免"脚尖刚出边缘就跳" |
| 起跳方式 | `:77` `MC.player.jumpFromGround()` | 1.20.2 真源 `world/entity/LivingEntity.java:2054-2058`：只写竖直速度 `getJumpPower()`，不碰水平，符合"跳过缺口"的意图 |
| 与 SafeWalk 冲突 | `:50` `onEnable` 强制 `safeWalkHack.setEnabled(false)`；`:55-58` `onDisable` **不恢复** | 上游 Wurst 的既定行为（Parkour 跳边缘与 SafeWalk 防掉边缘语义互斥）。这是事实陈述，不是本轮发现的缺陷 |

## 实际改动

无。没有可举证的新旧行为差异（判定形状与起跳方式都与原版语义一致），按简报第 9 条不动代码。

## 故意不搬

- `AutoJump.kt:15` 的 `Tick Delay`：Parkour 是事件驱动（贴边才跳），加节流只会让它在边缘停一拍再跳，反而掉下去，不搬。
- `AutoJump.kt:21` 的液体抬升：属 `hacks/JesusHack.java`。

## 建议（未做）

1. `:50` 关掉 SafeWalk 却不在 `:55-58` 恢复：如果用户是"手动开着 SafeWalk + 临时开 Parkour"，关掉 Parkour 后 SafeWalk 会保持关闭。要恢复必须新增字段记录开 Parkour 之前的 SafeWalk 状态，属行为变更（而且与上游 Wurst 不同），未做。
2. `minDepth` 的注释（`:21-23`）已经解释了 0.5 这个默认值与楼梯/地毯的关系，属既有设置，不动。
3. 验证边界：零改动，只做了静态核对。
