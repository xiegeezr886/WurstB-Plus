状态：不适用

## 对照证据

参考侧没有对应模块。参考的 GUI 是 `client/RootGUI.kt` / `client/HUDEditor.kt`（1.12.2 的固定式界面），
没有"多套 ClickGUI 风格 + 工具提示透明度 + 窗口高度上限"这一层。

本项为自审。`hacks/ClickGuiHack.java`（96 行）不是普通作弊模块，而是"打开 ClickGUI 并把界面设置
挂在它身上"的启动器：

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 打开界面 | `:72-80` `onEnable` 里 `MC.setScreen(ClickGuiScreens.create())`，随后自己关闭模块 | 正常 |
| 设置项 | `:27-42` 工具提示透明度、窗口最大高度、设置窗口最大高度、GUI 风格（Epsilon/SuperSoft/Vape） | 正常 |
| 风格切换 | `:51-59` 变更监听里同步 `GuiPreferences` 并重开界面（只在当前正开着 ClickGUI 时） | 正常 |
| 不存档/不进 blocker | `:22-23` `@DontSaveState` `@DontBlock` | 合理 |

无改动：该模块依赖 `net.wurstclient.clickgui2/**`（正在由另一路并行开发），本次只审计不修改。

## 记录

- 该模块没有调用 `setCategory(...)`（因此分类为 null，覆盖清单里也把它列在"?"段）。
  同段的 `Navigator` 也一样。这是有意还是遗漏无法从代码判断，且改分类会影响 GUI 分组显示，
  本次只记录。
