状态：不适用

## 对照证据

参考侧有对应模块 `module/misc/AutoReconnect.kt`（53 行）。

| 关注点 | 参考（`AutoReconnect.kt:行`） | 本工程 | 判定 |
| --- | --- | --- | --- |
| 延迟设置 | `:20` `Delay` 0.5..100 秒，默认 5 | `hacks/AutoReconnectHack.java:21-23` `Wait time` 0..60 秒，默认 5 | 等价 |
| 倒计时显示 | `:46-51` 在被踢界面上画 "Reconnecting in Xms" | `mixin/DisconnectedScreenMixin.java:122-129` 把倒计时写进"自动重连"按钮文字（"自动重连 (N)"） | 等价（显示位置不同） |
| 触发时机 | `:29-35` 显示被踢界面时替换成自己的界面 | 同 mixin 里 `tick()` 倒计时到 0 后重连 | 等价 |
| 手动开关 | 无（模块开关即状态） | `:80-83` 界面上多一个"自动重连"按钮，可当场切换 | 本工程更多 |
| 复用上次服务器 | `:22,26,42` 记住 `prevServerDate`，`currentServerData` 为空时回退 | `:75-78` 用 `LastServerRememberer.reconnect(parent)` | 等价（本工程有独立的"上次服务器"记录） |
| 互斥 | 无 | `hacks/AutoLeaveHack.java:148` 主动离开时关掉自动重连 | 合理 |

无改动。

## 建议（未做）

- `hacks/AutoReconnectHack.java:27` 模块名是中文"自动重连"，而工程里其它模块名都保持英文
  （`SearchTags` 里补了英文别名）。属于本地化策略问题，牵涉 `HackList` 与命令补全，
  本次不动。
