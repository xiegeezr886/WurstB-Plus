状态：不适用

## 对照证据

参考侧没有对应模块（`render/` 里的颜色/主题模块（`ThemeSetting.kt`）是渲染层的主题设置，
不是"客户端 UI 变彩虹"）。本项为自审。

## 自审记录

模块本身只是一个开关标记：`hacks/RainbowUiHack.java` 全文 24 行，没有设置项、不监听事件，
真正的取色在消费方。已核实调用点：

| 调用点 | 位置 | 行为 |
| --- | --- | --- |
| HUD 里的 Wurst 标志 | `hud/WurstLogo.java:36` | 开启时彩虹色 |
| HUD hack 列表 | `hud/HackListHUD.java:68,82` | 颜色模式切 RAINBOW |
| 新 ClickGUI | `clickgui2/ClickGui.java:437` | 强调色跟随彩虹 |
| `.taco` 命令输出 | `commands/TacoCmd.java:84` | 彩虹文字 |

无改动（该模块跨 HUD/渲染层，改取色逻辑要动 `hud/**` 与 `clickgui2/**`，后者正由另一路并行开发）。

## 建议（未做）

- 彩虹速度/饱和度写死在各个消费方里，没有统一设置项；要加应做成一个共享的取色工具类，
  属于跨模块重构，未做。
