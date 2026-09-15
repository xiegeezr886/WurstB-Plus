状态：不适用

## 对照证据

参考侧没有对应模块（1.12.2 没有副手盾牌）。本项为自审，全文读过
`hacks/NoShieldOverlayHack.java`（48 行）。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 关掉后恢复 | `:38-39` `adjustShieldPosition` 开头判 `!isEnabled()` 直接返回 | 正确 |
| 举盾/非举盾两档偏移 | `:41-44` 两个滑块 | 正常 |
| 消费点 | `HeldItemRendererMixin.java:71,86` | 已核对 |

无改动。
