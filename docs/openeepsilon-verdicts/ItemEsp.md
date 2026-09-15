状态：不适用（参考侧有对应模块，但差异全属功能新增，零改动）

## 对照证据

参考侧：`_oe_ref/src/main/kotlin/studio/coni/epsilon/module/render/ItemESP.kt`（123 行）。

| 关注点 | 参考（ItemESP.kt:行） | 本工程（hacks/ItemEspHack.java:行） | 判定 |
| --- | --- | --- | --- |
| 目标类型 | `:49` 固定画 `EntityItem` **与** `EntityArrow`；`:50-52` 可选 `EntityExpBottle` / `EntityXPOrb` / `EntityEnderPearl`（`:30-32` 三个开关，EPearl 默认 true） | `:13,61,100-104` 只处理 `ItemEntity` | 本工程少三类（箭/经验球/珍珠）。这不是"改既有实现"，而是新增设置项才能覆盖（简报第 5 条禁止动设置），见「故意不搬」 |
| 距离限制 | 无（`loadedEntityList` 全量，只做视锥剔除） | `:41-43` `Max distance`（16..512，默认 256，`:99,103` 用 `distanceToSqr` 过滤） | 本工程更严：远处掉落物不会进入渲染列表 |
| 视锥剔除 | `:44-47` `camera.setPosition(...)` + `isBoundingBoxInFrustum(entity.renderBoundingBox)` | 无（交给 `EntityEspRenderer`） | 参考的剔除是性能手段；本工程靠 256 格上限与 `EntitySnapshotManager` 快照已经限制了规模，不构成缺陷 |
| 快照 vs 直读 | `:45` 每帧直接遍历 `mc.world.loadedEntityList` | `:100-101` 读 `WURST.getEntitySnapshotManager().getCurrent().items()`（每 tick 一次的共享快照） | 本工程把实体遍历从渲染线程挪到 tick 线程，避免渲染时并发改列表；这是本工程共享核心的一部分，保留 |
| 样式 | `:33,118-122` `Mode`（Box/FullBox/Line）+ `Width` + `Transparency`（0..255 整数） | `:32-36` `EspStyleSetting`（含方框/描线开关与 Box/Line 形态）+ `boxSize`（Accurate/Fancy）+ `fillOpacity`/`lineOpacity`（百分比）+ `nearFadeDistance` | 本工程多了近距离淡出与"精确/美化方框"两档，保留 |
| 颜色 | `:69-71` 用 `GUIManager.firstColor`（跟随主题） | `:38-39` 独立的 `Color` 设置（默认黄） | 各自口径不同（参考跟随主题色），属设计选择，不搬 |
| 名字文本 | `:57-63,75-115` 在 3D 上方投影出一行 `displayName xN`（`:102-106`），带 `ticksExisted > 1` 过滤（`:84`） | 无 | 新增功能才有的东西，见下 |
| 穿墙 | 参考全程 `depth(false)`（`:79`） | `:58-59` `Through walls` 开关（默认 true）；`:118-122` 把它取反（`!throughWalls.isChecked()`）作为第 9 个参数传给 `EntityEspRenderer.render` | 本工程可切换，保留 |
| 视摇 | 参考无 | `:107-113` 画线时取消视角摇晃（`CameraTransformViewBobbingEvent`），保证 tracer 不跟着屏幕抖 | 本工程独有且合理 |

## 实际改动

无。逐条核对后，差异全部落在"参考有、本工程没有的功能"（箭/经验球/珍珠、掉落物名字文本），而不是"本工程写错了"。按简报第 9 条不动代码。

## 故意不搬

1. `ItemESP.kt:30-32` 的 `EXP` / `XP Orb` / `EnderPearl` 三个开关：要搬就得新增三个设置项并把目标类型从 `ItemEntity` 扩到 `Entity`，属功能新增（简报第 5 条）。
2. `ItemESP.kt:75-115` 的 3D→2D 投影文字：1.12.2 用 `glTranslated`/`glScalef` + `GLU.gluProject`，1.20.1 要走 `PoseStack` 与 `Font`/自绘文本栈；本工程渲染层最近由另一个会话改造过，新增文本渲染路径应当走他们那套组件，不适合在本轮硬塞。
3. `ItemESP.kt:69-71` 用主题色而不是独立颜色设置：会让"物品 ESP 颜色"跟着主题变，属行为变更，不搬。

## 建议（未做）

1. `:102` 只过滤 `isRemoved()`，没有参考 `:84` 的 `ticksExisted > 1`：刚生成/刚合并的掉落物第一 tick 就可能进入列表。构造不出可见差异（一 tick 内渲染不到），仅留档。
2. 验证边界：零改动；`EntityEspRenderer` 的实际画面没有实机确认，本轮只审 hack 层。
