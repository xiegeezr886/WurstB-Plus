状态：不适用（参考侧无对应模块，本工程实现自洽，零改动）

## 参考侧核对

`_oe_ref/.../module/render/` 的 24 个模块里没有光照覆盖层（LightOverlay / spawn overlay）。参考的 `ESP2D`/`CityESP`/`HoleESP` 都是"标出方块"，但判据是方块类型/空间结构，与"按光照等级决定是否可刷怪"无关。故判「不适用」，主体是自审。

## 本工程现状（自审）

`hacks/LightOverlayHack.java`，117 行。（该文件版权头是 `2025 Penguin`，是本工程自己写的实现，不是从参考搬的。）

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 扫描范围 | `:33-34` `Range` 滑块（1..32，默认 8）；`:77-78` 以玩家为中心 `[-r, r]²` 列 | 与其它 ESP 类 hack 的"以玩家为中心"口径一致 |
| 找落点 | `:80-83` 从 `player.blockPosition().offset(x, -1, z)` 向下走，直到 `getBlockState(pos).isSolid()` 或到 `getMinBuildHeight()` | 用 `isSolid()`（走得过水/岩浆/植物）而不是 `!isAir()`，落点语义正确 |
| 判据 | `:85-88` 取 `getBrightness(LightLayer.BLOCK, pos.above())`，`>= 8` 就不画 | 只算**方块光**，不算天空光。这是"火把够不够"的判据，不是原版刷怪的完整判据——原版 `Monster#isDarkEnoughToSpawn` 还要求 `getBrightness(LightLayer.SKY, pos) <= random(32)`。后果：白天露天也会画黄框（区块方块光 0）。这是此类工具的主流口径（用来找需要插火把的地方），属**已知语义而非缺陷** |
| 透明度 | `:90` `alpha = (8 - light) / 16F` | 光越暗越不透明（0 光 = 0.5，7 光 ≈ 0.06），线性映射合理 |
| 渲染 | `:65-69,110-115` `enableBlend` / `disableDepthTest` / `depthMask(false)`，`finally` 里全部还原 | 状态机还原到位，不会污染后续渲染 |
| 资源 | `:106-109` `endOrDiscardIfEmpty()` + null 判定后再 `drawWithShader` | 空缓冲不会崩 |

## 实际改动

无。判定与渲染都自洽，没有可举证的新旧行为差异，按简报第 9 条不动代码。

## 故意不搬

参考侧无对应模块。

## 建议（未做）

1. **真实性能风险（本轮发现，未修）**：`:81-83` 的下降循环没有任何短路条件，逐列最多走到 `getMinBuildHeight()`（1.20.1 是 -64）。具体输入：`Range = 32`、站在海面上方（y≈62，整列都是水，`isSolid()` 对水为 false）→ 每列约 126 次 `getBlockState`，共 (2×32+1)² = 4225 列，约 **53 万次方块查询/帧**；60 fps 下约 3200 万次/秒，会明显掉帧。最小修法是"落到流体就停"（`getBlockState(pos).getFluidState().isEmpty()` 为 false 时 break）或给结果加缓存/限帧。之所以没直接改：这会改变"水面上方是否画提示"的既有表现（改成不画），属行为变更而非修 bug，留给实机确认。
2. `:80` 用 `player.blockPosition()` 而不是相机位置：第三人称或 Freecam 下提示框会跟着玩家而不是视角。构造不出"错误提示"的反例（提示本来就该跟着玩家走），仅留档。
3. 验证边界：零改动，只做了静态核对；渲染效果（颜色、深度）没有实机看过。
