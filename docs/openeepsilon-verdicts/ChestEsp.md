状态：不适用（参考侧无对应模块，本工程实现自洽，零改动）

## 参考侧核对

`_oe_ref/.../module/render/` 的 24 个模块里没有箱子/容器 ESP。名字最接近的 `BreakESP.kt` 是"正在被破坏的方块"进度标记，`HoleESP`/`CityESP` 是空间结构（洞/城市），`Waypoint` 是手工坐标点，判据都不是"方块实体类型"。故判「不适用」，主体是自审。

## 本工程现状（自审）

`hacks/ChestEspHack.java`，275 行。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 方块实体来源 | `:176-178` `ChunkUtils.getLoadedBlockEntities()` | 只遍历**已加载区块**的方块实体表，不是自己扫方块；1.20.1 里这是最省的做法（`ClientLevel` 本来就有 `blockEntityList` 之类的结构） |
| 分类顺序 | `:181-198` `TrappedChestBlockEntity` 在 `ChestBlockEntity` 之前、`DropperBlockEntity` 在 `DispenserBlockEntity` 之前 | **顺序正确且必要**：`TrappedChestBlockEntity extends ChestBlockEntity`、`DropperBlockEntity extends DispenserBlockEntity`，反过来的话陷阱箱会被并进普通箱、投掷器会被并进发射器。这是 instanceof 链最容易写错的地方，本工程写对了 |
| 实体类容器 | `:200-206` `MC.level.entitiesForRendering()` 里挑 `MinecartChest`/`MinecartHopper`/`ChestBoat` | 用原版同一条实体列表，不自己遍历世界 |
| 每 tick 重建 | `:174` `groups.forEach(ChestEspGroup::clear)` 开头清空 | 无跨 tick 残留；容器被破坏后提示会立刻消失，不会留下幽灵框 |
| 设置规模 | `:52-131` 13 个分组（普通箱/陷阱箱/末影箱/箱车/箱船/桶/潜影盒/漏斗/漏斗车/投掷器/发射器/熔炉…），每组一个颜色 + 一个 `Include` 复选框 | 其中漏斗/漏斗矿车/投掷器/发射器/熔炉默认关闭、其余默认开启——照搬自 Wurst 既有默认口径，未改 |
| 样式与深度 | `:52-64` `EspStyleSetting` / `Fill opacity` / `Line opacity` / `Through walls`；`:223-228` 按 `style.hasBoxes()`/`hasLines()` 分别渲；`:210-216` 画线时取消视角摇晃 | 与 `PlayerEsp`/`ItemEsp` 共用同一套 `EspStyleSetting`，口径一致 |

## 实际改动

无。没有可举证的新旧行为差异，按简报第 9 条不动代码。

## 故意不搬

- `BreakESP.kt`：破坏进度标记，与本 hack 无关；本工程 `NukerHack`/`FastBreakHack` 是另一条线。
- 参考侧无容器颜色分组体系，无对应物。

## 建议（未做）

1. `:176-178` 每 tick 都把加载区块的方块实体收成一个 `ArrayList` 再逐项 instanceof；区块多时这是一个 O(方块实体数) 的分配。可以改成直接对 stream 做 `forEach` 省掉一次装箱（`Collectors.toCollection(ArrayList::new)` → 直接消费）。属性能微调，构造不出行为差异，未做。
2. `:200` 的实体分类只覆盖 `MinecartChest`/`MinecartHopper`/`ChestBoat`：潜影贝/运输船之类的变体（例如 1.21 才有的新容器）不在内。这是 1.20.1 的完整集合，未做扩展。
3. 验证边界：零改动，只做了静态核对；渲染效果没有实机确认。
