状态：已优化

## 对照证据

参考侧没有 CHAT 类模块，也没有聊天翻译。本工程用 Google 网页接口（`hacks/chattranslator/GoogleTranslate`）
做收发双向翻译，参考侧无对应实现，本项为自审 + 修一处必现崩溃。

## 自审记录

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 收：翻译后另发一条 | `hacks/ChatTranslatorHack.java:82-115` | 正常（原文保留，译文带语言前缀） |
| 发：先取消原消息再发译文 | `:117-149`：`event.cancel()` 后异步翻译并 `sendChat` | 逻辑正常，但失败路径会吞掉消息，见下 |
| 语言前缀去重 | `:93-95` 跳过 Wurst 前缀与目标语言前缀 | 正常 |
| 线程 | `:101-104,133-136` 每条消息一个非 daemon 线程 | 见建议 |

## 实际改动：`GoogleTranslate` 的空指针

```java
		String html = getHTML(text, langFrom, langTo);
		String translated = parseHTML(html);          // ← html 可能为 null
		if(simplify(text).equals(simplify(translated)))   // ← translated 可能为 null
```

- `getHTML`（`:72-96`）在 `IOException` 时 **返回 null**；
- `parseHTML`（`:128-144`）在页面里找不到 `class="result-container"` 时也 **返回 null**
  （限流页、同意页、改版后的 HTML 都会这样）。

**反例（Rule 9）**：开启 ChatTranslator，然后在 `translate.google.com` 不可达的网络里（无网/被墙/超时）
发一条聊天消息 → `getHTML` 捕获 `IOException` 返回 null → `parseHTML(null)` 里
`Pattern.matcher(null)` 抛 `NullPointerException` → 异常从 `translate` 抛出，而
`onSentMessage` 已经先执行了 `event.cancel()`，所以**你发的这条消息直接消失**，
只在日志里留一行堆栈（`thread.setUncaughtExceptionHandler(... e.printStackTrace())`）。
即使 HTML 能拿到但结构变了，也会在 `simplify(null)` 处同样 NPE。
`sendTranslated` 里本来有 `if(translated == null) translated = message;` 的兜底，
但因为异常根本走不到那里，兜底形同虚设。

修法（`GoogleTranslate.java:59-75`）：`translate` 里对 `html` 与 `translated` 各加一次空值判断，
返回 null，让调用方既有的"翻译失败就发原文"路径生效。

另外给连接补了读超时：`:120` 原来只有 `setConnectTimeout(5000)`，没有 `setReadTimeout`，
服务器接受连接后不返回数据时这条线程会永久卡住（线程是非 daemon，每条消息一个）。
现在读超时也是 5s，超时按失败处理（发原文）。

## 建议（未做）

- 每条收到的消息都会新建一条线程发一次 HTTPS 请求（`:101-104`），没有去重/合并/上限。
  读超时补上后线程不会再永久卡住，但高频聊天仍会产生大量短命线程，未改。
- 收到的消息不能像发送那样"替换原文"，只能在下面追加一条译文；这是事件接口的限制，未改。
