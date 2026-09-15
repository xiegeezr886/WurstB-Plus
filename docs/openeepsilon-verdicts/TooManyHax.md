状态：不适用

## 对照证据

参考侧没有对应模块（参考没有"屏蔽功能列表"这一层）。本项为自审，全文读过
`hacks/TooManyHaxHack.java`（159 行）。

## 自审记录

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 屏蔽集合 | `:33` `blockedFeatures` + `:104-107` `isBlocked()` 用 `contains`（Feature 是单例，等于同一性） | 正常 |
| 防止屏蔽自己 | `:113-114,137-138` 只允许 `isSafeToBlock()` 的功能 | 正常（`@DontBlock` 的模块不会被屏蔽） |
| 真正生效的地方 | `hack/Hack.java:134` 开启时检查 `tooManyHax.isBlocked(this)`；另外 `commands/CmdProcessor.java:51`、`commands/TCmd.java:60`、`hud/TabGui.java:282`、`keybinds/KeybindProcessor.java:141,199`、`clickgui2/FeatureMenuSupport.java:109` 也会拦 | 覆盖完整（**不是**只在加载时关一次） |
| 存档 | `:46` `TooManyHaxFile`，`:123,146,152` 改动后立即保存 | 正常 |
| 配置档案 | `:77-102` 列出/加载/保存 `toomanyhax/` 下的档案 | 正常 |

无改动（本行此前是"待办"，实际代码是 1.20.1 移植版的新写法）。

## 建议（未做）

- `:77-91` `listProfiles()` 不按扩展名过滤，`toomanyhax/` 目录下任何普通文件都会出现在档案列表里；
  属显示取舍，未改。
- `:87-90` 读目录失败时抛 `RuntimeException`，会让调用方（命令/GUI）直接崩；改成返回空列表并提示更稳，
  但需要在 GUI 侧配合，未做。
