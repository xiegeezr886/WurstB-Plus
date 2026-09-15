状态：已优化

## 参考侧核对

`_oe_ref/.../module/` 下没有对应模块（`render/`、`misc/`、`combat/`、`player/`、`movement/` 全部核过一遍：
`SeedOreEsp` 类的东西不存在，`WallHack`/`HoleESP` 都不是这个功能）。故判「不适用」的对照部分 + 自审为主的「已优化」。

## 本工程现状（证据表）

`hacks/BaseFinderHack.java`。

| 关注点 | 位置（改动前行号） | 结论 |
| --- | --- | --- |
| 判定口径 | `:39-83` `BlockListSetting naturalBlocks`（"哪些算自然生成"的方块名单，默认是石头/泥土/深板岩等）；`:130` 启用时快照成 `blockNames`；`:217-218` 二分查找 | 名单可图形化编辑；快照避免 tick/渲染线程读到用户正在改的列表（`getBlockNames()` 保证有序，见 `XRay.md` 的同一条核对） |
| 扫描节奏 | `:175,199-201` `modulo = tickCount % 64`，`stepSize = level.getHeight()/64`（1.20.1 为 384/64 = 6），每 tick 只扫 6 层：`startY = maxBuildHeight-1-modulo*6` 往下 6 层 | 一次完整纵向扫描被摊到 64 tick，单 tick 约 `6×129×129 ≈ 9.98 万` 次查询 |
| 纵向覆盖 | `:200-201` modulo=0 时从 319 起，modulo=63 时 `y` 到 -64 停 | 正好覆盖 320..-64，不缺层 |
| 水平范围 | `:208-209` 以玩家为中心 x/z 各 `64..-64`（129×129） | 固定窗口 |
| 命中上限 | `:211-212` 到 10000 个 `break loop`；`:228-243` 只提示一次（`messageTimer` 计数） | 有上限也有提示 |
| 一轮边界 | `:196-197` 只在 `modulo == 0` 清空集合；`:224` 只在 `modulo == 63` 编译顶点 | 清空/编译的时机与"64 tick 一轮"吻合 |
| 渲染 | `:151-170` 用当前 region 做偏移、`depthFunc` 默认（不穿墙）、`color.setAsShaderColor(0.25F)` | 与其它 ESP 一致 |
| 生命周期 | `:126-148` 启用时重置计时器+快照名单，关闭时清集合/顶点/缓冲并把 `lastRegion` 置空 | 完整 |

## 实际改动

**修掉"在渲染帧里跑整段世界扫描"**：`onRender()` 在相机换 region 时原来直接调 `onUpdate()`：

```java
		RegionPos region = RenderUtils.getCameraRegion();
		if(!region.equals(lastRegion))
			onUpdate();          // ← 这里会连扫描一起跑
```

抽出一个只负责上传的方法：

```java
	private void updateVertexBuffer(RegionPos region)
	{
		if(vertexBuffer != null)
			vertexBuffer.close();
		vertexBuffer = EasyVertexBuffer.createAndUpload(...);
		lastRegion = region;
	}
```

`onRender()` 与 `onUpdate()` 都改调它（`onUpdate()` 里原来的 `if(modulo == 0 || !region.equals(lastRegion))` 分支整块被这个方法替代）。

新旧行为差异（具体输入）：玩家沿 x 轴从 x=0 走到 x=512（`RegionPos` 从 0 变 1）的那一刻，`onRender` 发现 region 变了：

- 旧：`onRender` → `onUpdate()` → 扫 `6×129×129 = 99,846` 个位置，每个位置都要 `BlockUtils.getName(pos)`（构造一次
  `ResourceLocation` 再 `toString()`）并做一次二分查找；这些工作**发生在渲染帧内**（紧接着还要建顶点缓冲）。
  同一次调用还可能重复执行 `modulo == 0` 的清空与 `modulo == 63` 的顶点编译（那些 tick 路径本来就会做）。
- 新：`onRender` 只做"关旧缓冲 + 上传 + 记 `lastRegion`"，一次方块查询都不做；扫描仍然只由 tick 里的
  `onUpdate()` 完成（它本来就每 tick 都在扫）。

行为等价性：被去掉的只是 tick 路径已经做过（或当 tick 就会做）的重复工作；`lastRegion` 仍在上传成功后更新，
所以不会反复重传；`vertexBuffer` 的可见性/生命周期没有任何变化。

## 故意不搬

参考侧无对应模块。

## 建议（未做）

1. `:217-218` 每个被扫到的位置都做一次 `BlockUtils.getName(pos)`：每 tick 约 10 万个位置 → 约 10 万次字符串
   构造 + 注册表查询。名单快照本来就在 `onEnable` 固定，可以顺手建 `Block -> boolean` 的哈希缓存，把"构字符串 +
   二分"换成一次哈希查找。属纯优化（结果不变），未做。
2. `:208-209` 水平窗口写死 129×129：大于 129 格的基地会扫不全，小基地则白扫。加"半径"设置属新增设置，未做。
3. 纵向从 320 一直扫到 -64，而 Y < -59 已是虚空、几乎不可能有建筑；跳过最低几层约省 5%。会改变语义
   （可能漏掉极端坐标），未做。
4. 验证边界：只做了 `compileJava`/`compileTestJava`/`test`（全绿）；绘制效果与卡顿没有实机测量。
