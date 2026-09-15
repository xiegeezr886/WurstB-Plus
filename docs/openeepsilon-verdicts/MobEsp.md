状态：不适用（参考侧只有一个粗粒度的通用实体 ESP，本工程实现是其超集，零改动）

## 对照证据

参考侧：`_oe_ref/src/main/kotlin/studio/coni/epsilon/module/render/EntityESP.kt`（没有单独的 MobESP 模块）。

| 关注点 | 参考（EntityESP.kt:行） | 本工程（hacks/MobEspHack.java:行） | 判定 |
| --- | --- | --- | --- |
| 类型划分 | `:28-34` 只有三个粗分组（`Players`/`Mobs`/`Animals`）+ 三种散装实体（EndCrystal/Item/ExpBottle/Pearl）；`:102` 用 `is EntityMob \|\| Villager \|\| Slime \|\| Ghast \|\| Dragon` 判"怪"、`is EntityAnimal \|\| Squid \|\| Golem \|\| Bat` 判"动物" | `:76-98` 用 `EntityFilterList` 挂了 **18 个**过滤器（敌对/中立/被动/水生被动/蝙蝠/史莱姆/宠物/村民/僵尸村民/傀儡/猪灵/僵尸猪灵/末影人/潜影贝/悦灵/隐身/命名/盔甲架），其中中立类还有 `AttackDetectingEntityFilter.Mode` 三态 | 本工程远细于参考；参考的分组没有可搬之处 |
| 玩家 | `:28,102` 用 `players` 开关把玩家并进同一个 ESP | `:145` 无条件排除 `Player`（玩家交给 `PlayerEspHack`） | 本工程按职责拆分，更清楚 |
| 距离 | 无上限（`:47` 直接遍历 `loadedEntityList`） | `:43-45,141,147` `Max distance`（16..512，默认 256，平方比较） | 本工程更可控 |
| 数据来源 | `:47` 每帧遍历 `mc.world.loadedEntityList` | `:142-143` 读 `EntitySnapshotManager.getCurrent().livingEntities()`（每 tick 一次的共享快照） | 本工程把遍历挪到 tick 线程，避免渲染时并发改列表 |
| 死亡/移除 | 未过滤（`loadedEntityList` 里本就不会有已移除实体） | `:145-146` 额外排除 `isRemoved()` 与 `getHealth() <= 0` | 更稳（抹除动画期间血量 0 的实体会被排除） |
| 渲染 | `:42-62` 双次渲染 + `:51-61,79-91` `renderEntityStatic`/`modelBase.render` 各画 4~5 遍（chams） | `:166-169` 交给共享的 `EntityEspRenderer`（样式/方框尺寸/描边浓度/近距离淡出/穿墙） | 参考的 GL11 整模型描边在 1.20.1 不成立，不搬 |
| 颜色 | `:36` 单一 `Alpha` + GUI 主题色（`PlayerEsp` 那一篇已核对） | `:47-58,172-176` `ColorMode.DISTANCE/HEALTH/CUSTOM` + `Color range` + `Mob color` | 本工程更丰富 |
| 视摇 | 无 | `:155-161` 画线时取消视角摇晃 | 本工程独有且合理 |

## 实际改动

无。逐条核对后没有可举证的新旧行为差异，按简报第 9 条不动代码。

## 故意不搬

1. `EntityESP.kt:28-34,102` 的粗分组：本工程的 18 个过滤器是它的严格超集（含三种"被攻击后才显示"的中立怪模式），
   换过去只会丢功能。
2. `EntityESP.kt:51-61,79-91` 的整模型描边（chams）：1.12.2 的 `GlStateManager`/立即模式管线在 1.20.1 不存在；
   本工程对应的功能是 `TrueSightHack`。
3. 把玩家并进 MobESP：本工程玩家有自己的 hack 与自己的设置，不合并。

## 建议（未做）

1. `:82,96` 蝙蝠与盔甲架都默认关闭（`genericVision(false)`/`(true)` 的默认值沿自 Wurst 既有口径）。
   盔甲架默认被过滤掉意味着"展示用盔甲架"不会显示提示框——这是既有默认值，改动会影响用户配置，未动。
2. `:148` 用 `MC.player.distanceToSqr(mob)`（脚底到脚底）而不是相机位置：与 `PlayerEsp` 同一口径，
   构造不出因此出错的反例（同一设置下远近排序不会有可观察差异），仅留档。
3. 验证边界：零改动，只做了静态核对；渲染效果没有实机确认。
