状态：不适用

## 对照证据

参考侧没有对应模块（参考的 `render/OreSim.kt` 之类是 1.12.2 的矿洞模拟，机制不同）。
本项为**结构审计 + 关键区段核对**（文件 541 行，属另一路并行开发维护的 `seed/**` 相关模块，
本次只审计不修改）。

结构（方法清单）：`predictChunks` / `pruneCache` / `distanceSq` / `updateShown` /
`isOreEnabled` / `updateMining` / `nearestTargets` / `isMinable` / `showNothing` / `clearCache` /
`closeBuffer` / `rebuildBuffer` + 内部类 `WorldOreContext implements OrePredictor.OreContext`
（`minY`/`maxY`/`isAir`）。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 事件对称 | `:152-153` 添加、`:159-160` 移除（Update/Render 各一） | 平衡 |
| 关闭清理 | `:157-166` 摘监听 + `job.cancel()` + `predictor = null` + `clearCache()` | 完整 |
| 启用初始化 | `:146-151` `clearCache()` + 复位 `predictionFailed`/`noSeedTicks` | 正确（重开不会沿用上次的失败状态） |
| 预测上下文 | `:514-541` 通过 `OrePredictor.OreContext` 接口把世界数据（minY/maxY/isAir）喂给预测器 | 设计合理：预测逻辑与客户端世界访问解耦，可单测 |

无改动。

## 未做 / 边界

- 未逐行审计 `OrePredictor`（在 `seed`/`util` 包内，由另一路并行开发维护），只核对了本模块的事件与生命周期。
- 没有实机验证渲染结果。
