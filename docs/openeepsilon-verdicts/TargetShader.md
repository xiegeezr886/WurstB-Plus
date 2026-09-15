状态：不适用

## 对照证据

参考侧没有对应模块（参考没有"给当前战斗目标加后处理特效"）。本项为自审，全文读过
`hacks/TargetShaderHack.java`（79 行）。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 目标解析 | `:60-72` 依次取 KillAura → MultiAura → 准星命中实体 | 正常（覆盖三种"当前目标"来源） |
| 有效性校验 | `:74-78` 非空、不是自己、同世界、未移除、存活 | 正确（`entity.level() == MC.level` 这一条避开了跨维度残留） |
| 渲染 | `:50-57` 交给 `WURST.getPostEffectQueue().queue(...)`，用插值后的包围盒 | 正常（特效走队列，不在渲染线程里同步等待） |
| 事件 | `:35-45` 注册/摘除 | 平衡 |

无改动（本文件没有 GPL 头注释，与工程多数文件相比少了版权行；未擅自补）。

## 建议（未做）

- 依赖 `WURST.getHax().killauraHack/multiAuraHack` 的 `getCurrentTarget()`；这两个模块未启用时返回 null，
  已由 `isUsable` 兜住。未改。
