状态：不适用

## 对照证据

参考侧有 `player/FastPlace.kt`（1.12.2 直接调 `mc.rightClickDelay = 0`）。
本工程 `hacks/FastPlaceHack.java`（41 行）写法相同：每 tick `MC.rightClickDelay = 0`。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 是否需要还原 | 不需要：`rightClickDelay` 由原版每 tick 自行递减，模块关掉后下一 tick 就恢复正常节奏 | 无状态泄漏 |
| 事件 | `:24-34` 加/摘 `UpdateListener` | 平衡 |

无改动。

## 说明

- 覆盖清单里曾把 FastPlace 标为"疑似只注册没用"（全仓库只有 `HackList` 引用）。核对后确认是误判：
  这类模块不需要 mixin，靠 `UpdateListener` 直接写原版字段即可生效。
