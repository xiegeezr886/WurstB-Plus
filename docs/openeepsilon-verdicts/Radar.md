状态：不适用（参考侧无对应模块，本工程实现自洽，零改动）

## 参考侧核对

`_oe_ref/.../module/render/` 的 24 个模块里没有雷达/小地图。最接近的 `ESP2D.kt` 是**把实体投影到屏幕上画 2D 方框**（`ESP2D.kt:86` `project2D`、`:114+` 一堆 `drawRectFilled` 画血条/药水/名字），属于本工程 `PlayerEsp`/`NameTags` 的对应物，不是"以玩家为中心的圆形雷达"。`Waypoint.kt` 是坐标点标记，也不是雷达。故判「不适用」，主体是自审。

## 本工程现状（自审）

`hacks/RadarHack.java`，120 行。**注意：这个 hack 本身只负责收集实体，真正的绘制在 `clickgui2/components/RadarComponent.java`（属渲染层，本轮不审）。**

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 数据来源 | `:89-94` `MC.level.entitiesForRendering()` 流式处理，过滤 `isRemoved()`、排除自己、排除 `FakePlayerEntity`、只留 `LivingEntity`、`getHealth() > 0` | 用原版同一条实体列表（`ClientLevel#entitiesForRendering`），不需要自己遍历区块；`FakePlayerEntity`（本工程其它 hack 的假人）被排除是对的 |
| 每 tick 重建 | `:88,98` `entities.clear()` + `addAll(collect())`，`:106-109` 对外只暴露 `unmodifiableList` | 无跨 tick 残留，且不会把内部列表漏给渲染层改 |
| 半径 | `:38-39` `Radius` 滑块（1..100，默认 100）；`:111-114` `getRadius()` | 本 hack 不按半径过滤，把半径交给 `RadarComponent` 做圆裁剪；因为原版实体列表本来就只在已加载区块内，这样少一次遍历 |
| 随玩家旋转 | `:40-41` `Rotate with player`（默认 true）；`:116-119` `isRotateEnabled()` | 保留 |
| 实体过滤 | `:43-51` `EntityFilterList`：玩家 / 睡觉 / 敌对 / 被动 / 水生被动 / 蝙蝠 / 史莱姆 / 隐身，共 8 个开关，由 `:96` `entityFilters.applyTo(stream)` 统一施加 | 与 `PlayerEsp`/`MobEsp` 用同一套过滤设置类，行为口径一致；其中蝙蝠那一项传的是 `genericVision(true)`、其余传 `false`，即默认值逐项不同（照搬自 Wurst 既有 ESP 的默认口径，未改） |
| 窗口生命周期 | `:62-65` 构造一个 pinned+invisible 的 `Window`；`:72` 启用时显示、`:79` 关闭时隐藏 | 不在 `onEnable` 里重复 new `Window`，避免重开时窗口重复注册；隐藏而不是销毁，保留位置 |

## 实际改动

无。没有可举证的新旧行为差异，按简报第 9 条不动代码。

## 故意不搬

- `ESP2D.kt:33-43` 的 Width/Mode/Fill/FillAlpha/HealthBar/SplitHealth/Health/Potion/Name/Self/Color 一整套：那是 2D 实体方框的渲染配置，对应本工程的 `PlayerEsp`/`NameTags`（各自已有独立设置），硬塞进 Radar 会造出第二套方框渲染路径。属功能新增，不搬。
- `ESP2D.kt:310-315` 的 `GLU.gluProject` 屏幕投影：1.20.1 应走 `PoseStack` + 相机矩阵（本工程 `EntityEspRenderer` 已有），不搬。

## 建议（未做）

1. `:38-39` 的 `Radius` 上限就是 100（最小值 1），而原版实体列表在渲染距离大于 100 时会包含更远的实体——也就是说本 hack 会把它们全部交给 `RadarComponent` 再裁掉。想省掉这部分开销应当在 `:96` 之后再按 `distanceToSqr <= radius²` 过滤一次。属性能优化，构造不出错误显示的反例（组件仍会正确裁剪），未做。
2. 验证边界：零改动；`RadarComponent` 的实际绘制表现没有实机看过，本轮不覆盖渲染层。
