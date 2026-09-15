状态：不适用

## 对照证据

参考侧没有 ITEMS 类模块，也没有"全效果药水"实现。本项为自审。

## 自审记录

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 前置检查 | `hacks/TrollPotionHack.java:41-47`：非创造模式报错、自动关闭并 `return` | 正确 |
| 背包满 | `:54-56` `getFreeSlot() < 0` 时提示"背包已满" | 正常 |
| 效果 NBT | `:97-105` `Id` 从 1 到 23、`Amplifier`/`Duration` 均取 `Integer.MAX_VALUE` | 效果 id 覆盖全部 23 种；`Duration` 上限 3.4 年，属恶作剧用途 |
| 与 KillPotion 的差异 | 本模块保留了 `ARROW`（`:74`），而 `KillPotionHack:76-77` 注释说明箭头"does not work" | 两处不一致，见建议 |

无改动。

## 建议（未做）

- 箭头类型在两个药水模块里的取舍不同（这里保留、`KillPotionHack` 注释掉了）。要不要统一
  （要么都去掉、要么都保留并说明为什么能/不能生效）需要实机验证箭头是否真的带上效果，本次未改。
- 与 `KillPotionHack` 的重复代码（前置检查、发放逻辑、`PotionType` 枚举）可合并；属结构重构，未做。
