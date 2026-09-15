状态：不适用

## 对照证据

参考侧有 `movement/AutoWalk.kt`（按键保持前进）。本工程 `hacks/AutoWalkHack.java`（43 行）与之等价：
每 tick `MC.options.keyUp.setDown(true)`，`onDisable` 用
`IKeyBinding.get(MC.options.keyUp).resetPressedState()` 按玩家**真实**按键状态还原
（不是 `setDown(false)`，所以松开模块时你正按着的 W 不会被吃掉）。

无改动（这是本工程里按键恢复写法的正确样板，AntiAim/AntiVoid/Twerk/Vomit 的修法都参照它）。

## 建议（未做）

- 与 `BaritoneWalk`、`AntiAfk` 的非 AI 模式功能重叠（都在抢前进键），没有互斥组；三个模块同时开
  会互相覆盖 `keyUp` 状态。属于配置问题，未改。
