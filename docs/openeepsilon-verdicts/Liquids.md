状态：不适用

## 对照证据

参考侧没有对应模块（1.12.2 的"对着液体也能选中"靠 `getMouseOver` 覆盖，没有独立 Module）。
本项为自审，全文读过 `hacks/LiquidsHack.java`（41 行）。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 机制 | `:35-40` 收到 `HitResultRayTraceListener` 时用自己的射线追踪替换 `MC.hitResult`，`pick(reach, partialTicks, true)` 的第三个参数是 `includeFluids` | 正确（`true` 就是"把液体算进命中"） |
| 触发点 | `mixin/GameRendererMixin.java:125-127` 每次射线追踪前发事件 | 已核对，事件确实会被发出，模块不是死代码 |
| 开关 | `:22-32` 事件加/摘平衡 | 正确 |
| 交互范围 | `:37` 用 `MC.gameMode.getPickRange()` | 正确（跟随服务器/属性给的实际触及距离） |

无改动。

## 说明

- 覆盖清单里曾把 Liquids 标为"疑似只注册没用"：全仓库只有 `HackList` 引用它。核对后确认是误判，
  它通过 `HitResultRayTraceListener` 事件被 `GameRendererMixin` 消费。
