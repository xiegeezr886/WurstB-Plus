状态：不适用（参考侧 `render/WallHack.kt` 是 1.12.2 的旧版透视，机制在 1.20.1 不成立；本工程实现逐条核对无误，零改动）

## 对照证据

参考侧：`_oe_ref/src/main/kotlin/studio/coni/epsilon/module/render/WallHack.kt`。

| 关注点 | 参考（WallHack.kt:行） | 本工程（hacks/XRayHack.java:行） | 判定 |
| --- | --- | --- | --- |
| 生效机制 | `:44-52` `mc.renderChunksMany = true` + `mc.renderGlobal.loadRenderers()` / `markBlockRangeForRenderUpdate(...)`，靠强制重建区块 | `:119-125,137-143` 五个事件（`SetOpaqueCube` / `GetAmbientOcclusionLightLevel` / `ShouldDrawSide` / `RenderBlockEntity`）+ `MC.levelRenderer.allChanged()` 重建 | 1.12.2 的 `renderGlobal` API 在 1.20.1 不存在；本工程的事件式做法是正确对应物，不搬 |
| 可见方块表 | `:17` 硬编码在源码里的方块列表 | `:40-72` `BlockListSetting`（默认约 60 项，含各类矿石/深板岩矿/箱子/传送门等）+ `:238-241` `EditBlockListScreen` 图形化编辑 | 本工程可在游戏里改并可持久化，参考只能改源码，不搬 |
| 亮度 | `:14` `Light` 滑条（0..100） | `:165-170` 固定 `setLightLevel(1)`（环境光遮蔽拉满） | 加滑条属新增设置（简报第 5 条），不做 |
| 不透明度 | `:13` `Opacity` 滑条（0..255） | `:81-85` `Opacity` 滑条 + `:213-221` `isOpacityMode()`/`getOpacityColorMask()` 交给 `RenderLayersMixin`/`AbstractBlockRenderContextMixin`（`:243` 注释） | 两边都有；保留本工程实现 |
| 方块实体 | 未处理 | `:183-189` 非可见方块的方块实体取消渲染（箱子/漏斗等不会在透视下留下幽灵） | 本工程更完整 |
| 只显示暴露矿石 | 无 | `:74-80` `Only exposed` + `:203-211` `isExposed()`（六个方向任一不是实心立方体即算暴露，`BlockUtils.isOpaqueFullCube`） | 本工程独有，语义正确 |
| 与 Fullbright 的关系 | 无 | `:145-149` X-Ray 关闭时把 gamma 还原成 `fullbright.getDefaultGamma()`，但**只在 Fullbright 自己没有在改 gamma 时**；`:152-157` 开启期间每 tick 把 gamma 强制为 16 | 两个 hack 共用 gamma 的处理是自洽的（都不会在对方活动时抢写），保留 |
| OptiFine 警告 | 无 | `:87,227-236` 用 `opti(?:fine\|fabric).*` 匹配已加载 mod id 并 `ChatUtils.warning` | 本工程更完整 |

## 逐条核对过的两处「看起来可疑但其实是正确的」

1. `:194` `Collections.binarySearch(oreNamesCache, name)` 需要列表**有序**。查 `settings/BlockListSetting.java`：
   `:45` 默认项用 `.sorted()` 插入、`:94-95` 新增项后 `Collections.sort`、`:110-111` 重置为默认、
   `:126-154` JSON 读取路径最后 `blockNames.sort(null)`——四条写入路径都维持有序，且类内部 `:65,91`
   自己也用 `binarySearch`。所以 `:115` 的副本是有序的，二分查找成立。
2. `:92,205` 用 `ThreadLocal<BlockPos.MutableBlockPos>` 做暴露判定：区块重建在**工作线程**上进行，
   `isVisible()`/`isExposed()` 会被并发调用，共享一个 `MutableBlockPos` 会串数据。用 ThreadLocal（而不是
   每次 new）既线程安全又不产生垃圾；`:114-115` 的"启用时拷贝一份名单"也是同一个原因（避免工作线程读
   用户可改的设置对象）。这两点都是**刻意为之**，不是冗余。

## 实际改动

无。没有可举证的新旧行为差异，按简报第 9 条不动代码。

## 故意不搬

1. `WallHack.kt:44-52` 的强制重建 + `renderChunksMany` 手法（1.12.2 API）。
2. `WallHack.kt:14` 的 `Light` 滑条、`:15` 的 `Reload` 模式枚举：新增设置，不做。
3. `WallHack.kt:17` 把方块表写死在源码里：本工程已是可编辑的 `BlockListSetting`，退回去是负收益。

## 建议（未做）

1. `:191` `isVisible()` 每次都做一次字符串二分：`BlockUtils.getName(block)` 会构造 `ResourceLocation`
   再 `toString()`，方块渲染时调用频率很高（每个面/每个方块实体）。可以缓存 `Block -> Boolean` 的
   `Reference2BooleanMap`，但方块状态数量不大、字符串注释也短，收益不确定，未做。
2. `:177` 在 `opacity > 0` 时"不设置 rendered"、把决定权交回原版：这依赖 `ShouldDrawSideEvent` 的默认值
   等于原版判断（由 mixin 保证）。若将来有人改那个 mixin 的默认值，这条路会静默变化；建议在事件类里
   写明默认语义，属文档改进，未做。
3. 验证边界：零改动，只做了静态核对；透视画面没有实机确认。
