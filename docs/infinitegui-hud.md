# HUD 换成无限GUI（源码移植版，v1.6 根工程 Forge 1.20.1）

> 范围：把 InfiniteGUI（无限GUI）**源码里的那几个信息项移植成 Java**，替换掉客户端对应的旧源码。
> 代码在 `net.wurstclient.infinitegui`；移植基准是工作区里的上游克隆
> `InfiniteGUI-Minecraft-DLL/`（MIT，见 [THIRD-PARTY-NOTICES.md](../THIRD-PARTY-NOTICES.md) 第一节）。

## 1. 换哪些、不换哪些（关键约定）

三条口径：**只换无限GUI 真有的**、**尺寸与形状用 WurstB+ 自己的**、**材质统一 liquid glass**；
外加一条用户否决：**无限GUI 的键盘显示不如 WurstB+ 的，不换**。

| 客户端元素 | 无限GUI 有对应项？ | 结果 |
| --- | --- | --- |
| `fps` | FPS显示 | **换成移植版** `FpsWindow` |
| `cps` | CPS显示 | **换成移植版** `CpsWindow` |
| `clock` | 时间显示 | **换成移植版** `TimeWindow` |
| `keystrokes` | 按键显示 | **不换**（用户裁决：WurstB+ 的五排键帽/可调尺寸更好），保留 `KeystrokesHudElement` |
| `notifications` | 提示弹窗 | **不换**（WurstB+ 是 Compose 卡片流，无限GUI 只是一条文字 toast） |
| armor / potions / inventory / minimap / scoreboard / target_hud / compass / coords / ping / tps / speed / memory / reach_display / weather / game_mode / day_counter / player_name / player_counter / play_time / potion_counter / health / combo / music_island / music_lyrics / server / hacklist / logo | **没有** | 继续由客户端画（液态玻璃外观） |

被替换的三个元素类（`FpsHudElement`、`CpsHudElement`、`ClockHudElement`）**已删除**；
上游其余信息项（计数器 / 文件数量 / 文本 / B站粉丝 / B站弹幕 / 音乐信息）本批未移植。

## 2. 从上游照搬的 vs 按本工程改的

**照搬（行为与文案）**：项的 `type` 名（`时间显示`/`FPS显示`/`CPS显示`，配置里就是靠它对上）；
时间 `%Y-%m-%d | %H:%M:%S`（关日期则 `%H:%M:%S`）1000ms 更新；FPS 前后缀默认 `[`…`FPS]`、
开 GUI 帧率时 `"a | b"`；CPS 前后缀默认 `[CPS: `…`]`、左右都显示时中间 `" | "`、最近一秒窗口；
文字用 `TextColoredShadow` 那套 1px 阴影；配置形状 `{"Items":[{"type":…,"isEnabled":…}]}`。

**改掉（本工程口径）**：

1. **尺寸与形状 = WurstB+ 的**：卡片内边距左右 2 / 上下 1（原 `TextHudElement` 就是 `+4`/`+2`）、
   圆角 4（玻璃化 HUD 时用的那个），不是上游的 ImGui 像素（内边距 8、圆角 3）。
2. **材质 = liquid glass**：窗口底走 `GlassPanels`（模糊 4.8 / 折射 24 / 磨砂 1），不是上游的 80% 黑；
   玻璃不可用时回退到上游那个 `(0,0,0,0.8)`。
3. **默认开**：上游这几项默认关（`isEnabled = false`）；这里默认开，否则"替换了源码"会表现成
   "那三项 HUD 凭空消失"。
4. **拖动**：只在有光标的界面里拖（HUD 编辑器 / 暂停菜单…）。游戏内鼠标被锁，不响应；松开写回配置。

## 3. 配置

```text
<gameDir>/wurst/infinitegui/profile.json
{ "Items": [
    { "type": "时间显示", "isEnabled": true, "x": 12, "y": 12, "prefix": "", "suffix": "", "showDate": true },
    { "type": "FPS显示",  "isEnabled": true, "x": 12, "y": 29, "prefix": "[", "suffix": "FPS]", "showGuiFps": false },
    { "type": "CPS显示",  "isEnabled": true, "x": 12, "y": 46, "prefix": "[CPS: ", "suffix": "]", "showLeft": true, "showRight": true } ] }
```

首次启动自动写入默认（三项都开、左上角一列）。这三项**不在 `hud2` 元素表里**，所以 HUD 编辑器看不到它们；
改开关/位置/前后缀靠这个 JSON，或在有光标的界面里拖动。

## 4. 验证状态

**已做**：`compileJava compileTestJava` 通过；`test` **215 个测试类 / 1418 项 / 0 失败**；
新增 2 个测试类 / 9 项（`InfiniteTextTest` 3 项钉住三项文案格式逐字对齐上游；`InfiniteWindowTest` 6 项
钉住配置往返、`type` 名与上游一致、前后缀默认、命中测试、以及"尺寸 = 内容 + WurstB+ 卡片内边距"）。
被替换的三个旧元素类已删除，`HudElementSettingsTest` 里引用它们的断言改用 `HealthHudElement` /
`WeatherHudElement`（同属 `TextHudElement` 基类）。键盘元素按用户要求恢复并重新贴回玻璃画法。

**未做**：

| # | 项 | 缺什么 |
| --- | --- | --- |
| 1 | **实机截图** | 三项移植版在游戏里的样子没有截图；本轮尝试被并行构建/窗口占用打断，只到"能编译、测试全绿" |
| 2 | HUD 编辑器集成 | 编辑器看不到也拖不了这三项（它们不是 hud2 元素） |
| 3 | 上游其余 6 项 | 计数器 / 文件数量 / 文本 / B站粉丝 / B站弹幕 / 音乐信息未移植 |
| 4 | 其它 66 个工程 | 只有根工程有 |