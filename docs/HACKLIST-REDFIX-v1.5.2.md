# RedFix HackList 与模块分类（Fabric 1.20.1 / v1.5.2）

采用本地 `reference/clients/RedFix-repaired` 的 ArrayList 主题与模块分类。

- HUD `ArrayList` 与兼容入口 `ModuleList` 共用原生 NanoVG 渲染器；同时启用时只显示 ModuleList，避免重复列表。
- 条目按含模式标签的实际文字宽度排序；默认右对齐、宽度递减、显示标签，可切换排序、对齐与名称分词。
- 背景由分类色的 62% 不透明度渐变到 10%，左侧分类色边框，白色名称、灰色标签，圆角与间距依照主题 CSS 换算。
- CSS 坐标按上游 `NativeHudComponent.WEB_HUD_BASE_SCALE = 2` 换算为 Minecraft GUI 单位。14px 字号 → 7 GUI 单位，4px 圆角 → 2，3px 色条 → 1.5，2px 行间隔 → 1。
- 200ms cubic-out 进入、退出和位置变化；快速反向切换保留当前动画进度；关闭最后一个模块后不显示空背景。编辑器空列表使用示例预览。
- 文字测量按模块缓存，只有名称/标签变化时重新测量；不使用整屏模糊或浏览器组件。
- 字体沿用当前客户端 MiSans Medium，模式标签来自当前 1.20.1 模块；不是浏览器像素级复刻。

## 模块分类

战斗 Combat、玩家 Player、移动 Movement、渲染 Render、世界 World、其他 Misc、漏洞 Exploit、娱乐 Fun。

同名模块优先采用 RedFix `ClientModule` 声明的分类。例如 Blink / NoFall / AntiVoid → Player，AntiHunger / ClickTP / GhostHand / BookBot → Exploit，AntiBot → Misc，Surround / AutoTrap / HoleFiller / Timer → World。

本客户端独有模块按功能归类：旧 Blocks → World、Items → Player、Chat / Other → Misc，并调整相关物品漏洞、建造和玩家自动化模块。现有模块实现、模块名、开关、设置键不变。

CGUI、导航器、TabGUI 使用同一 Category 元数据。HUD 和客户端工具保留独立入口；CGUI 新增玩家、漏洞、娱乐面板，旧面板位置继续有效，新面板位于右侧，可横向滚动访问。

## 配置兼容

`ArrayList` ID、HUD 保存的位置/缩放/启用状态继续有效。`ModuleList` 保留 X / Y / Additional info / OnlyBind；旧的任意配色与阴影由 RedFix 固定分类配色替代，旧设置加载器会忽略已移除项。分类未序列化为枚举名，因此无需改写用户模块配置。

## 来源

`assets/index-B5FiXQoV.js` 的 ArrayList 组件、`assets/index-BG7KxVN5.css`、`components/arraylist.json`、`ModuleCategories.kt`。漏洞图标来自 `img/hud/tabgui/exploit.svg`。许可证和署名随包位于 `META-INF/licenses/redfix-hud/`。

验证：71 项自动测试通过（含排序、退出、反向切换、标签宽度变化与导航器分类）；隔离客户端内检查了 ArrayList、ModuleList、Navigator 和 CGUI 新分类面板截图。434 个可静态识别的模块完成分类核对，其中 85 个直接匹配 RedFix 同名模块。
