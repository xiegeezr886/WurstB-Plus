# ClickGUI Opai 外观移植（v1.5.2）

目标：Fabric 1.20.1 / Java 17。参考本地 `reference/clients/OpaiBounce` 的 `6a6bc99`。
用户已确认保留当前分类，仅替换 Opai 外观；字体沿用现有 MiSans。

## 保留的功能

- 分类：HUD、视觉类、移动类、客户端、战斗类、世界类、其他类。
- 辅助面板：目标、配置、字体、设置、主界面。
- 模块开关、按键绑定、原有设置与配置加载/保存入口。
- Navigator 的 Rise 界面是独立入口。

## 已移植的外观与交互

参考 `Panel.svelte`、`Module.svelte`、`Description.svelte`、`OpaiSetting.svelte`、
`Reveal.svelte`、`motion.ts` 和 `opai.scss`，用现有 NanoVG 渲染体系实现。
坐标采用 2 CSS px = 1 Minecraft GUI px：

| 项目 | 参数 |
| --- | --- |
| 面板 | 宽 152，间距 8，圆角 7，标题高 21 |
| 模块行 | 高 19.6，左右留白 10，字号 8.3 |
| 颜色 | 标题 #161110，面板 #1a1413b0，设置区 #211b1a |
| 启用状态 | 背景 #df9d95，文字 #5f150f，控件强调色 #ffb4aa |
| 控件 | 粉色滑块/开关、棕色输入框、内联选项列表、HSV 颜色选择 |
| 动画 | 320 ms 打开缩放/淡入，指数展开，点击波纹 |

- 左键模块执行原有主操作；右键展开内联设置；中键绑定按键。
- 绑定时 Delete/Backspace 清除绑定，Esc 取消。
- 标题左键拖动、右键折叠；拖动默认吸附 5 GUI px 网格，Shift 可绕过。
- 每个面板独立滚动；底部细滚动条、Shift + 滚轮及方向键可浏览横向画布。
- Ctrl+F 打开搜索；Esc 先退出输入/搜索，再关闭界面。
- 右下角入口打开原有 HUD 编辑器。
- 面板位置、开合、滚动、显隐、层级及模块展开状态保存在 `wurst/clickgui-opai.json`。
- 方块、物品列表、交易列表、文件等设置复用现有专用编辑屏幕。

`OpaiPanelLayout` 让绘制和点击检测共享尺寸，覆盖展开动画与滚动后的变高模块行。
不引入网页/CEF 运行时；Opai 的网页专用音乐、账号、实体预览等不属于此次 ClickGUI 外观替换。

## 验证

构建与客户端检查结果在完成后补充。交互手感仍需人工操作检查；未使用鼠标/键盘自动输入。

来源和原始许可证随资源打包至 `assets/wurstpenguin/opai/`。
