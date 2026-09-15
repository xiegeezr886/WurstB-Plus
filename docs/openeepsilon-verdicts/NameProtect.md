状态：已优化

## 对照证据

参考侧有 `misc/NameProtect.kt`（把聊天里的玩家名替换掉）。本工程 `hacks/NameProtectHack.java`
（56 行旧版）功能相同，消费点在 `TextVisitFactoryMixin.java:28`（每个渲染出来的字符串都会过一遍）。

## 实际改动：子串替换会破坏普通文本

旧实现用 `String.replace(name, ...)`：

```java
			if(string.contains(name))
				return string.replace(name, "\u00a7oPlayer" + i + "\u00a7r");
```

**反例（Rule 9）**：服务器上有一个玩家叫 `Red`，聊天里有人打 "Reduced"（或任何含该名字的单词），
渲染出来会变成 "§oPlayer1§ruced"。原版玩家名允许 3~16 个字符，所以 `The`、`and`、`You` 这类
名字都是合法且常见的，命中普通单词的概率很高。

改成只替换"完整单词"形式（`(?<![A-Za-z0-9_])名字(?![A-Za-z0-9_])`，并用
`Pattern.quote`/`Matcher.quoteReplacement` 处理特殊字符）：

```java
	private static String replaceWholeWord(String text, String word,
		String replacement)
	{
		if(word.isEmpty())
			return text;
		
		return text.replaceAll(
			"(?<![A-Za-z0-9_])" + Pattern.quote(word) + "(?![A-Za-z0-9_])",
			Matcher.quoteReplacement(replacement));
	}
```

保留 `contains()` 作为快速预筛（`protect()` 在文本渲染路径上被高频调用，正则只在确实出现名字时才编译）。

顺带把 `replaceAll("\u00a7(?:\\w|\\d)", "")` 里的 `|\\d` 去掉（`\w` 已包含数字，属冗余）。

## 未做

- 名字本身带颜色代码（`§`）的服务器昵称仍会被替换成占位符，与参考行为一致；未改。
