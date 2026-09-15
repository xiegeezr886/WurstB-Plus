状态：不适用（参考侧无对应模块，本工程实现已自洽，零改动）

## 参考侧核对

`_oe_ref/src/main/kotlin/studio/coni/epsilon/module/render/` 共 24 个模块：Animations, AntiOverlay, BreakESP, CameraClip, Chams, ChinaHat, CityESP, Crosshair, EntityESP, ESP2D, FullBright, HealthParticle, HoleESP, ItemESP, Nametags, NoRender, Skeleton, SoulESP, TextPopper, Tracers, Trajectories, ViewModel, WallHack, Waypoint。没有遮挡剔除（occlusion culling）模块；`NoRender` 是"不画某些东西"（关闭渲染），与"用 GPU 遮挡查询决定要不要画"不是一回事，方向也不同（一个是无条件跳过，一个是条件判定）。故判「不适用」，主体是自审。

## 本工程现状（自审）

`hacks/EntityCullingHack.java`，99 行；实际判定逻辑在 `util/render/EntityOcclusionCuller`（GPU 遮挡查询，属渲染层，本文件只做接线与过滤）。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 永不剔除的对象 | `:82-85` `culler == null \|\| MC.player == null \|\| entity == MC.player \|\| entity == MC.getCameraEntity() \|\| entity.isCurrentlyGlowing() \|\| !targets.isChecked()` | 正确：自己、相机实体（第三人称下的自己）、发光（`Glowing` 效果通常意味着"要看见"）都排除，`targets` 关掉就整体失效 |
| 分组开关 | `:86-88` `Player` 走 `players`，其它走 `otherEntities`；`:26-28` 由 `targets` 用 `withChildren` 挂在下面 | 用 `withChildren` 表达从属关系，比参考那种平铺设置更清楚 |
| 近距离豁免 | `:89-90` `distanceToSqr(MC.player) < minimumDistance.getValueSq()`（默认 4 格，`:38-40`） | 近距离不剔除是对的（贴着你的实体不能被查询延迟藏掉）。注意这里量的是"玩家脚底"到目标脚底（`Entity#distanceToSqr(Entity)` 用的是 `getX/Y/Z`），而 `:96` 的查询用的是相机坐标——两个参考系不同。构造不出"因此某实体被错误剔除"的输入（近距离豁免只会让判定更保守），不记为缺陷 |
| 查询节流 | `:92-95` `queryTiming` 打开时用 `visibleDelay`/`hiddenDelay`，**关掉时硬编码 50/250** | 与两个滑块的默认值（`:29-34` 的 50 与 250）相同，所以"关掉"= "重置为默认值"，不是"关闭节流"。属既有语义，见「建议」1 |
| 世界切换 | `:71-77` `onWorldChange` 关掉旧 culler、新世界重建；`:60-69` `onDisable` 也要 `close()` | 资源释放完整，不会跨世界泄漏 GL 查询对象 |

## 实际改动

无。没有可举证的新旧行为差异，按简报第 9 条不动代码。

## 故意不搬

- 参考的 `NoRender.kt`：它是"按类型关掉原版渲染"（火、方块、实体…），与本 hack 的"按可见性条件剔除"是两种东西；本工程若要那个功能应另开 hack，不塞进这里。

## 建议（未做）

1. `:92-95` 的 `Query timing` 取消勾选后并不是"不节流"，而是回到 50/250 这两个字面量。如果用户先把两个滑块拉大再取消勾选，观感是"取消勾选反而更频繁地重测"。要让它真的"关掉节流"应当传 0（每帧重测），但那是性能与正确性的取舍，且描述本身没承诺"关掉=每帧重测"，属可选改进，未做。
2. `:89` 用玩家脚底做距离、`:96` 用相机坐标做查询，严格说应该统一到相机（剔除判定本来就发生在相机空间）。因为近距离豁免只会更保守，构造不出错误剔除的反例，仅留档。
3. 验证边界：零改动，只做了静态核对；GPU 遮挡查询的实际表现必须在实机开游戏才能评估（不在本轮验证范围内）。
