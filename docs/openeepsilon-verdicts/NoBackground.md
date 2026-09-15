状态：不适用

## 对照证据

参考侧没有对应模块（参考的 GUI 是自绘界面，没有"去掉原版 GUI 背景"这一项）。
本项为自审，全文读过 `hacks/NoBackgroundHack.java`（46 行）。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 判定入口 | `:31-43` `shouldCancelBackground(Screen)` 自带 `isEnabled()` 判断 | 正确（不依赖调用方） |
| 主菜单保护 | `:36-37` `MC.level == null` 时不取消背景 | 正确（否则主菜单会变成全透明） |
| 只作用于容器界面 | `:39-40` 未勾选 `All GUIs` 时只对 `AbstractContainerScreen` 生效 | 与设置说明一致 |
| 消费点 | `ScreenMixin.java:89` | 已核对 |

无改动。
