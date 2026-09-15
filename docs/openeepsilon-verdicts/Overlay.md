状态：不适用

## 对照证据

参考侧有 `render/BreakProgress.kt`（显示挖掘进度）。本工程 `hacks/OverlayHack.java`（67 行）
用 `OverlayRenderer` 画挖掘进度，实现更完整。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 只在挖掘时绘制 | `:48-51` 每 tick 同步 `renderer.updateProgress()`/`resetProgress()`；`:57-58` 渲染前再判一次 | 正确 |
| 命中结果校验 | `:60-62` 必须是 `BlockHitResult` 且类型为 `BLOCK` | 正确（避免对着实体/空气时画错位置） |
| 关闭清理 | `:42` `renderer.resetProgress()` | 正确（否则下次开启会从上次的进度继续画） |

无改动。
