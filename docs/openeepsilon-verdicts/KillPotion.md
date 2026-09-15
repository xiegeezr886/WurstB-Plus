状态：不适用

## 对照证据

参考侧没有 ITEMS 类模块，也没有"生成即死药水"的实现（`misc/Crasher.kt` 是发包崩服，
`combat/AutoCev.kt` 是晶体防护，语义都不同）。本项为自审。

## 自审记录

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 前置检查 | `hacks/KillPotionHack.java:41-49`：非创造模式报错、自动关闭并 `return` | 正确 |
| 背包满 | `:56-58` `getFreeSlot() < 0` 时提示"背包已满" | 正常 |
| 药水 NBT | `:96-110``CustomPotionEffects` 里一条 `Id=6`（瞬间伤害）、`Amplifier=125`、`Duration=2000` | 1.20.1 的 NBT 仍用数字 id，格式有效 |
| 命名 | `:112-114` 名称带颜色代码的 "of INSTANT DEATH" | 正常 |
| 箭矢类型 | `:76-77` 注释说明 `TIPPED_ARROW` "does not work" 因而被注释掉（`TrollPotionHack` 保留了它） | 保留原注释 |

无改动。

## 建议（未做）

- 两个药水模块（本模块与 `TrollPotionHack`）除效果 NBT 外几乎逐字相同，可以合并成一个模块的两个预设；
  属于结构重构，本次未做。
- 1.20.5+ 把药水效果 NBT 从数字 `Id` 改成了字符串 id；本文件属 1.20.1 源集，跨版本差异由各版本源集处理。
