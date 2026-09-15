状态：不适用

## 对照证据

参考侧没有对应模块（参考的 `misc/AutoFish.kt` 是自动钓鱼，不画"是否开阔水域"）。
本项为自审，全文读过 `hacks/OpenWaterEspHack.java`（71 行）。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 判定 | `:67-70` 直接用原版 `FishingHook.calculateOpenWater(blockPos)` | 正确（与服务器判定同源） |
| 渲染 | `:51-65` 以浮标所在方块为中心画 5×3×5 的框，开阔=绿色、浅水=红色，浅水额外画十字 | 正常 |
| 无浮标时 | `:53-55` 直接返回 | 正确 |
| 名牌提示 | `:28-36` 显示 `[open]`/`[shallow]` | 正常 |
| 事件 | `:38-48` 注册/摘除 `RenderListener` | 平衡 |

无改动。

## 建议（未做）

- `getRenderName()`（`:31`）直接访问 `MC.player.fishing`，没有判 `MC.player == null`。
  HUD 只在游戏内渲染，没有构造出可触发的反例，只记录。
