状态：已优化

## 对照证据

参考侧没有 CHAT 类模块，也没有 AuthMe 密码爆破（参考的 `misc/Crasher.kt` / `misc/PingSpoof.kt`
是别的东西）。本项为自审 + 修一处"关闭后仍会发包"的问题。

本工程实现：`onEnable` 起一个 `ForceOpDialog` 子进程（Swing 界面，`util/ForceOpDialog`）负责展示进度，
主进程线程 `handleDialogOutput` 读子进程输出，收到 `start` 就开线程 `runForceOP` 逐个尝试
`/login <密码>`，`onReceivedMessage` 用多语言关键词判断"密码错误/成功/已登录/服务器没有 AuthMe"。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 进程生命周期 | `:61-75` 起子进程；`:150-164` `onDisable` 里 `destroyForcibly` + `waitFor` | 正常 |
| 爆破节奏 | `:180-217` 支持"等密码错误消息"与固定延迟两种模式 | 正常 |
| 成功判定 | `:263-280` 用 `lastPW` 反查是哪个密码成功 | 边界安全（`lastPW == -1` 直接 return） |
| 断开重连 | `:188-198` 断开时把 `gotWrongPwMsg` 置真，避免重连后继续等"密码错误" | 设计合理（20Hz 轮询，开销可忽略） |

## 实际改动：内层重试循环不检查开关状态

```java
			boolean sent = false;
			while(!sent)
				try
				{
					MC.getConnection().sendCommand("login " + passwords[i]);   // 断开时 NPE
					sent = true;
				}catch(Exception e)
				{
					sleep(50);       // ← 只有这里，没有 isEnabled() 检查
				}
```

**反例（Rule 9）**：开启 ForceOP、点开始爆破，然后在它发送某条 `/login` 的瞬间断开连接
（被踢/掉线）→ `MC.getConnection()` 返回 null → NPE 被这个 `catch(Exception)` 吞掉 → `sleep(50)` → 再试，
如此循环。此时关闭 ForceOP：外层循环有 `if(!isEnabled()) return;`，但**这个内层循环没有**，
所以线程会一直转下去；等你重新连上服务器后，它会**在模块已关闭的状态下**把 `/login <密码>` 发出去，
然后才回到外层循环退出。旧实现里这个线程既是被关闭的模块仍在发包，也是一条退不出去的线程。

修法：在内层循环体开头加 `if(!isEnabled()) return;`（`:202-217`）。

## 建议（未做）

- `onEnable` 里子进程启动失败时是 `throw new RuntimeException(e)`（`:69-72`），会从 `setEnabled(true)`
  往上抛（GUI/命令侧的调用栈），而不是"提示 + 自动关闭"。无头环境或 java 可执行文件缺失才会触发，
  没有可举证的常见复现路径，未改。
- 关键词表只有英/德/法/西/意，没有中文；中文服务器的"密码错误"不会被识别。属于新功能，未做。
- `util/ForceOpDialog`（Swing 界面）在 macOS 上的窗口焦点问题属于平台问题，未验证。
