状态：不适用（参考侧无对应模块；本工程与 Search 共用同一套扫描核心，零改动）

## 参考侧核对

`_oe_ref` 里没有"洞穴高亮"模块：`render/` 的 `HoleESP` 找的是"1x1 可站立的坑"，
`Chams`/`WallHack` 是透视，`SeedOreEsp` 类的东西在本工程里才有。故判「不适用」，主体是自审。

## 本工程现状（自审）

`hacks/CaveFinderHack.java`，246 行。与 `SearchHack` 是同一套骨架（`ChunkAreaSetting` + `Limit` +
`ChunkSearcherCoordinator` + 两个 `ForkJoinTask` + `EasyVertexBuffer` + `RegionPos bufferRegion`），
差别只在判定与颜色：

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 判定 | `:68-70` `new ChunkSearcherCoordinator((pos, state) -> state.getBlock() == Blocks.CAVE_AIR, area)` | **只认 `CAVE_AIR`**：世界生成留下的洞穴会亮，玩家自己挖出来的通道是普通 `AIR`，不会亮。这是上游 Wurst 的既有语义（"找天然洞穴，不把你自己的矿道点亮"），刻意保留，不是缺陷 |
| 扫描/线程/状态机 | 同 `SearchHack`（`:96` `ForkJoinPool`、`:123` tick 状态机、`:198` 排序裁剪） | 共享核心，非阻塞 |
| 顶点构建 | `:210-236` `BlockVertexCompiler.compile(...)` | 与 Search 复用同一套"方块→可见面"逻辑 |
| 颜色与不透明度 | `:58-63` `Color` + `Opacity`（0 = 呼吸动画） | 比 `Search` 多两个设置 |
| 渲染 | `:160-179` 与 `Search` 相同：`depthFunc(GL_ALWAYS)`、`applyRegionalRenderOffset(bufferRegion)` | 一致 |
| 生命周期 | `:91-103` 建池、重置 `prevLimit`/`notify`；`:106-121` 取消任务并释放缓冲 | 完整 |

## 实际改动

无。判定语义经核对是刻意的（见上表第 1 行），没有可举证的新旧行为差异，按简报第 9 条不动代码。

## 故意不搬

参考侧无对应模块。

## 建议（未做）

1. **"只认 CAVE_AIR"值得加一条说明或选项**：用户实测"我挖的洞不亮"时很容易当成 bug。
   最小改动是把设置描述写清楚（`Opacity` 那段文字旁边补一句），而不是改判定——改判定会让
   "找洞"变成"找所有空气"，那才是真的改变语义。属文案/设置改动，未做。
2. 与 `Search` 相同的两条共享核心优化（全量三轴遍历 + 全排序裁剪）同样适用，见
   `openeepsilon-verdicts/Search.md` 的「建议」1、2。未做。
3. 验证边界：零改动，只做了静态核对；没在实机里确认过洞穴高亮的观感。
