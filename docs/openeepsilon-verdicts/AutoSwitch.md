状态：不适用

## 对照证据

参考侧没有 ITEMS 类模块，也没有"自动轮换快捷栏"这种功能（`movement/AutoTool.kt` 是按方块换工具，
`misc/Refill.kt` 是补货，语义都不同）。本项为自审。

## 自审记录

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 轮换逻辑 | `hacks/AutoSwitchHack.java:38-46`：每 tick `selected` 0→…→8→0 | 与模块名一致 |
| 是否发给服务端 | 只改 `inventory.selected` 字段。原版 `MultiPlayerGameMode.tick()` 里的
`ensureHasSentCarriedItem()` 会比对上次发送的槽位并补发 `ServerboundSetCarriedItemPacket` | 所以服务端能看到换手，功能成立（这也是 Wurst 各处换槽位用的同一机制） |
| 界面/暂停 | 没有界面门控 | 见建议 |

无改动。

## 建议（未做）

- 每秒 20 次换手 = 20 个换手包/秒，且没有任何开关或延迟设置。这是模块本身的用途（快速轮换），
  加节流会改变它的行为，未做。
- 与 `AutoTool`/`AutoSword` 这类"按需换手"的模块同时开启时会互相抢 `selected`；
  参考侧没有等价模块、本工程也没有对应的冲突组，未加互斥。
