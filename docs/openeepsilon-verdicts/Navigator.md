状态：不适用

## 对照证据

参考侧没有对应模块（参考的 `client/HUDEditor.kt` 是拖拽 HUD 元素，不是"搜索式导航面板"）。
本项为自审，全文读过 `hacks/NavigatorHack.java`（46 行）。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 打开界面 | `:29-45` `onEnable` 里 `MC.setScreen(NavigatorScreens.create(MC.screen))` 然后自我关闭 | 正常 |
| 重复打开保护 | `:38-39` 只在新界面类型与当前界面类型不同时才 `setScreen` | 正常（注释里说明"模式可能在别处被切过，所以按类型判断"，避免把同类界面重开会丢状态） |
| 空界面处理 | `:38` `MC.screen == null` 时也直接打开 | 正常 |
| 不存档/不进 blocker | `:19-20` `@DontSaveState` `@DontBlock` | 合理 |

无改动：该模块依赖 `net.wurstclient.clickgui2/Navigator*`（另一路并行开发中），本次只审计不修改。

## 记录

- 与 `ClickGui` 一样没有调用 `setCategory(...)`，分类为 null（覆盖清单里列在"?"段）；只记录，未改。
