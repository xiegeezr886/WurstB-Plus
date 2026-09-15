状态：不适用

## 对照证据

参考侧没有对应模块（参考没有 Baritone 集成）。本项为自审，全文读过
`hacks/BaritoneMineHack.java`（160 行）。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 前置条件 | `:58-64` `canEnable()` 要求 Baritone 已加载且玩家存在 | 正确 |
| 矿种名单 | `:23-37` 默认 18 种矿石（含深层变种与远古残骸），可编辑 | 合理 |
| 找不到矿 | `:113-122` 不在寻路状态超过 40 tick 就重新发起挖掘 | 正确 |
| 背包满 | `:90-111` 按设置分三档：`Log out` 断线（覆盖 `Walk home`）→ `Walk home` → 都不勾选则关闭模块 | 与设置说明的优先级一致 |
| 断线实现 | `:95-98` `connection.getConnection().disconnect(Component.literal("BaritoneMine: Inventory full"))` | 正确（文本是英文，属断线原因提示，可接受） |
| 背包判定 | `:139-151` 遍历 36 个主背包/快捷栏槽位，任一为空格即视为未满 | 正确 |
| 名称解析 | `:127-136` `BlockUtils.getBlockFromNameOrID` 解析失败的条目直接跳过 | 正确（不会因为名单里有错词就整个不工作） |
| 关闭 | `:74-79` 摘监听 + `BaritoneUtils.stop()` | 正确 |
| 不存档 | `:20` `@DontSaveState` | 合理 |

无改动。

## 建议（未做）

- 该文件没有 GPL 头注释；未擅自补版权声明。
- 背包满时 `Walk home` 只调用 `BaritoneUtils.walkHome()`（回家点由 Baritone 自己的配置决定），
  本工程内无法校验该点是否设置；未改。
