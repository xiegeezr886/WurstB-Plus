状态：不适用

## 对照证据

参考侧有 `render/BlockESP.kt` / `render/LightLevel.kt`（光照等级叠加层）。
本工程 `hacks/MobSpawnEspHack.java`（193 行）用区块级顶点缓冲画"可刷怪点"。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 可刷怪判定 | `:145-160` `NaturalSpawner.isSpawnPositionOk(CREEPER)` + 可选碰撞箱检查 + 方块光 < 1 | 正确（方块光 < 1 是原版刷怪的必要条件） |
| 顶点缓冲 | `:70-72,121,130-140` 交给 `ChunkVertexBufferCoordinator`，按区块/区域分段绘制 | 正常（不会每帧重建全部顶点） |
| 颜色缓存 | `:96-97,113-119` 缓存颜色值，改动时 `coordinator.reset()` | 正确（颜色改了就重建缓冲） |
| **白天/夜晚颜色** | `:185-186` `skyLight < 8 ? cachedDayColor : cachedNightColor` | **核对后确认正确**：语言文件里 `day_color` 的说明是 "positions where mobs can always spawn"、`night_color` 是 "positions where mobs can spawn at night"（`lang/en_us.json:161-162`）。天空光 < 8 = 见不到天 = 白天黑夜都能刷（dayColor）；≥ 8 = 只有夜里能刷（nightColor）。 |
| 透明度 | `:127` 用 `setShaderColor` 统一乘 alpha，`:142` 还原 | 正确 |
| 关闭 | `:101-108` 摘监听 + `coordinator.reset()` | 正确 |

无改动。

## 建议（未做）

- `:150-151` 固定用 `EntityType.CREEPER` 作为"典型生物"来套原版的生成位置判定。1.20 里不同
  生物类型的判定条件确实不同（`SpawnPlacements.Type`），用苦力怕等价于"标准陆生敌对生物"，
  与说明一致；若要支持更多类型需要额外的设置项，属功能扩展，未做。
