状态：不适用

## 对照证据

参考侧没有 ITEMS 类模块（只有 client / combat / misc / movement / player / render / setting 七类）。
最接近的是 `movement/InstantDrop.kt`（快速丢出**手持**物品）与 `misc/Refill.kt`（自动补充方块），
都不是"按物品名单自动清理背包"。本项为自审。

## 自审记录

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 界面门控 | `hacks/AutoDropHack.java:66-69`：只在没有容器界面、或开着自身背包界面时工作 | 正确（在箱子里不会乱丢） |
| 槽位映射 | `:71-76`：容器槽位 9..35 = 主背包 9..35，36..44 = 快捷栏 0..8（`slot >= 36 ? slot - 36 : slot`） | 与 `InventoryMenu` 一致，读到的就是即将被丢的那一格 |
| 丢弃包 | `:87` `windowClick_THROW(slot)` → `mixin/ClientPlayerInteractionManagerMixin.java:117-122`（button=1，丢整组） | 正常 |
| 物品名单 | `:25-33` `ItemListSetting`，默认是花/种子/腐肉等 | 正常 |
| 彩蛋 | `:35-36` `renderName` 有 1% 概率显示 "AutoLinus" | 上游 Wurst 彩蛋，保留 |

无改动。

## 建议（未做）

- 一次 tick 会把**所有**命中物品的槽位各发一个点击包（满背包最多 36 个包/tick）。
  功能上没问题（会迅速清干净），但包量集中在一个 tick；改成分帧限速属于行为变更，未做。
