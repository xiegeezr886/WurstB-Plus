状态：不适用

## 对照证据

参考侧有 `render/BossStack.kt`（把 Boss 血条堆叠显示）。本工程 `hacks/BossStackHack.java`（74 行）
实现相同（自绘紧凑血条）。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 避免与原版血条重叠 | `mixin/BossHealthOverlayMixin.java:26-32` 在本模块启用时 `ci.cancel()` 掉原版渲染 | 正确（否则会画出两套血条） |
| 数据来源 | `:47-48` 通过 `IBossHealthOverlay.wurst_getEvents()` 拿原版的事件表 | 正确（复用原版同步的数据，不自己解析包） |
| 空表短路 | `:50-51` | 正确 |
| 布局 | `:53-56,71` 居中、每条高 12 + 间距 2 | 正常 |
| 进度条宽度 | `:64-66` `1 + 180 * progress` | 正常 |

无改动。
