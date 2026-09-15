状态：不适用（参考侧无对应模块；自审未找到可举证的行为差异，零改动）

## 参考侧核对

`_oe_ref` 的 `combat/` 里没有 Nuker（`ZealotCrystal*` 是水晶、`AutoHoleFill` 是填洞），`misc/` 也没有。
故判「不适用」，主体是自审。

## 本工程现状（证据表）

`hacks/NukerHack.java`，165 行。它与 `NukerLegitHack`、`SpeedNukerHack` 共享同一套东西：
`CommonNukerSettings`（`:42`）+ `BlockBreakingParams` + `BlockBreakingCache`，这正是本工程"常用 hack 共享核心"的做法。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 设置 | `:39` `Range`(5，1..6)、`:42` `CommonNukerSettings`（与另外两个 Nuker 共用一组设置/渲染）、`:44` `SwingHandSetting` | 设置集中在共享类里 |
| 触发 | `:95-99` 需要按住左键，或 `commonSettings.isIdModeWithAir()` 为真 | 与共享设置一致 |
| 目标 | `:123` 用 `cache`（`BlockBreakingCache`）过滤掉刚刚挖过的方块，避免反复盯同一格 | 共享缓存 |
| 破坏方式 | `:148-152` `MC.gameMode.continueDestroyBlock(params.pos(), ...)` | 走**原版**破坏路径（不是发包），因此与 `NukerLegit` 的行为差异只在选目标/速度上 |
| 停止 | `:84-85`（关闭时）与 `:120,140`（没目标时）都调用 `MC.gameMode.stopDestroyBlock()` | 不会出现"关了 hack 手还在挖" |
| 渲染 | `:161+` `onRender` 画当前目标 | 与共享设置一致 |

## 实际改动

无。没有可举证的新旧行为差异，按简报第 9 条不动代码。

## 建议（未做）

1. `CommonNukerSettings` 被三个 Nuker 共用：共享是好事，但意味着改动它要同时考虑三个消费方。
   本轮只核对了 `NukerHack` 的调用面（`:42,71-72,79,99`），**没有**逐个核对 `NukerLegitHack`/`SpeedNukerHack`
   的用法，留档（这两个在覆盖表里仍是 `待办`）。
2. `:99` `commonSettings.isIdModeWithAir()`：名字里的 "Air" 暗示"把空气也当成可挖目标"，那是给"空手挖"
   之类的模式用的；本轮没有读 `CommonNukerSettings` 的实现，不下结论。
3. 验证边界：零改动，只做了静态核对。
