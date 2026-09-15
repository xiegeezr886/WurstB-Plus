状态：已优化

## 对照证据

参考侧有 `render/PortalESP.kt`（高亮下界传送门）。本工程 `hacks/PortalEspHack.java`（223 行）
覆盖下界门/末地门/末地门框/末地折跃门四类，并用 `EspStyleSetting` 控制"方框/连线"。

## 实际改动：Line opacity 滑块在"只有方框"的风格下仍然显示

`fillOpacity`（`:42-44`）带 `visibleWhen(style::hasBoxes)`，而 `lineOpacity`（`:46-48`）**没有**
任何可见性条件：

```java
	private final SliderSetting lineOpacity = new SliderSetting("Line opacity",
		"Opacity of portal outlines and tracer lines.", 0.5, 0, 1, 0.01,
		ValueDisplay.PERCENTAGE);
```

**反例（Rule 9）**：把 `Style` 选成"只有方框"（`style.hasLines()` 为假）后，设置列表里仍然显示
"Line opacity" 滑块，拖动它不会有任何效果 —— `renderTracers()` 只在 `style.getSelected().hasLines()`
时才被调用（`:157-158`），该滑块在 `renderBoxes()` 里也没有被读取。已补上
`.visibleWhen(style::hasLines)`，与 `fillOpacity` 的写法保持一致（`hasLines()` 在同一文件
`:136`/`:157` 已被使用，方法存在）。

## 自审记录

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 区块搜索 | `:90-91,143-148` `ChunkSearcherCoordinator`，只在 `isDone()` 后重建方框 | 正确 |
| 分组 | `:207-222` 按方块类型把命中分到四个组，`break` 避免重复 | 正确 |
| 开关 | `:110-130` 四个监听器注册/摘除对称，关闭时 `coordinator.reset()` + 各组 `clear()` | 正确 |
| 视线摇晃 | `:132-138` 只有连线风格时才取消视角摇晃（否则连线会跟着抖） | 正确 |
| 透明度 | `:202-205` `(int)(opacity * 255)` | 正常 |

验证边界：只做了编译验证，没有实机打开 GUI 确认滑块的显示条件。
