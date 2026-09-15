状态：已优化

## 对照证据

参考侧没有 CHAT 类模块，也没有 AI 补全聊天（参考只有 `misc/ClientSpoof.kt`、`misc/AntiCrasher.kt`
这类与聊天沾边的模块）。本项为自审。

## 自审记录

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| API 调用节流 | `hacks/AutoCompleteHack.java:86-96`：刷新至少 300ms、API 调用至少 3s | 正常 |
| 线程模型 | `:106-147` 上一个线程还活着就不开新线程（`isAlive()`）、`MIN_PRIORITY`、`daemon`、把会变的字段先抄进局部变量 | 正常 |
| 内存泄漏 | `:74,80` 关闭模块/发出消息时 `clearSuggestions()` | 正常 |
| 无密钥处理 | `:55-62` 找不到 `WURST_OPENAI_KEY` 时提示并自动关闭 | 逻辑正常，但**提示文案是半中半英**，见下 |

## 实际改动：把报错文案补成完整中文

```java
			ChatUtils.error("未找到API密钥。请设置"
				+ " WURST_OPENAI_KEY environment variable and reboot.");
```

`WURST_OPENAI_KEY 环境变量`、`environment variable and reboot` 是英文残留在中文句子里。
本工程其它用户可见文案（例如 `FollowHack:127`、`ForceOpHack:284`）都已经是中文，这里显然是漏译。
改为：`未找到 API 密钥。请设置 WURST_OPENAI_KEY 环境变量，然后重启游戏。`

反例（说明这不是纯粹的口味问题）：用户在中国区看到的是半句中文加半句英文，
"environment variable and reboot" 恰恰是最关键的操作说明（去哪设、设完要重启），
与工程里其它已经本地化的提示不一致。

## 未搬 / 建议（未做）

- `onDisable`（`:68-75`）不会中断正在跑的那条 HTTP 线程，只清空建议。线程是 daemon 且最长 3s 节流一次，
  影响有限，未改。
- 参考侧没有对应实现，所以不存在"照参考补齐功能"的空间；`ModelSettings` 的模型/温度参数沿用本工程实现。
