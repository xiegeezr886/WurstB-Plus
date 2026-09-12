package net.wurstclient.music;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 网易云歌词解析：优先 YRC 逐字 JSON，其次逐字增强 LRC（{@code <ms,dur>}），
 * 最后回退标准 LRC。输出供 Apple Music 风格歌词播放器使用。
 */
public final class LyricParser
{
	private static final Pattern TIMESTAMP = Pattern.compile(
		"\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
	private static final Pattern YRC_LINE = Pattern.compile(
		"^\\[(\\d+),(\\d+)\\]");
	private static final Pattern YRC_WORD_HEAD = Pattern.compile(
		"^(.*?)\\((\\d+),(\\d+),0\\)");
	private static final Pattern ENHANCED_WORD = Pattern.compile(
		"<(\\d+),(\\d+)(?:,\\d+)?>");

	private LyricParser()
	{}

	public static List<LyricLine> parseBest(String yrc, String lrc)
	{
		return parseBest(yrc, lrc, null);
	}

	public static List<LyricLine> parseBest(String yrc, String lrc,
		String translation)
	{
		return parseBest(yrc, lrc, translation, null);
	}

	/**
	 * @param translation 翻译歌词（{@code tlyric}）
	 * @param romanization 音译歌词（{@code romalrc}），网易云中文歌曲常带
	 */
	public static List<LyricLine> parseBest(String yrc, String lrc,
		String translation, String romanization)
	{
		List<LyricLine> fromYrc = parseYrc(yrc);
		List<LyricLine> lines = fromYrc.isEmpty() ? parse(lrc) : fromYrc;
		List<LyricLine> translated = attachTranslations(lines,
			parse(translation));
		return attachRomanizations(translated, parse(romanization));
	}

	public static List<LyricLine> parseYrc(String source)
	{
		if(source == null || source.isBlank())
			return List.of();
		String trimmed = source.trim();
		if(trimmed.startsWith("{") || trimmed.startsWith("[{"))
		{
			List<LyricLine> json = parseYrcJson(trimmed);
			if(!json.isEmpty())
				return json;
		}
		return parseYrcText(source);
	}

	private static List<LyricLine> parseYrcJson(String source)
	{
		try
		{
			JsonElement root = JsonParser.parseString(source);
			JsonArray array = null;
			if(root.isJsonArray())
				array = root.getAsJsonArray();
			else if(root.isJsonObject())
			{
				JsonObject object = root.getAsJsonObject();
				for(String key : List.of("lyric", "lrc", "yrc", "data"))
				{
					JsonElement value = object.get(key);
					if(value != null && value.isJsonArray())
					{
						array = value.getAsJsonArray();
						break;
					}
					if(value != null && value.isJsonPrimitive())
						return parseYrcText(value.getAsString());
				}
			}
			if(array == null)
				return List.of();
			List<LyricLine> result = new ArrayList<>();
			for(JsonElement element : array)
			{
				if(element == null || !element.isJsonObject())
					continue;
				JsonObject line = element.getAsJsonObject();
				long start = jsonLong(line, "startTime", "start", "t", "time");
				StringBuilder text = new StringBuilder();
				List<LyricWord> words = new ArrayList<>();
				JsonArray wordArray = jsonArray(line, "words", "c", "chars");
				if(wordArray != null)
					for(JsonElement wordElement : wordArray)
					{
						if(wordElement == null || !wordElement.isJsonObject())
							continue;
						JsonObject word = wordElement.getAsJsonObject();
						String wordText = jsonString(word, "text", "c", "word");
						if(wordText.isEmpty())
							continue;
						long wordStart = jsonLong(word, "startTime", "start",
							"t", "time");
						long duration = jsonLong(word, "duration", "d", "dur");
						if(wordStart == 0 && start > 0 && words.isEmpty())
							wordStart = start;
						long wordEnd = duration > 0 ? wordStart + duration
							: jsonLong(word, "endTime", "end");
						if(wordEnd <= wordStart)
							wordEnd = wordStart + Math.max(80,
								wordText.length() * 80L);
						words.add(new LyricWord(wordText, wordStart, wordEnd));
						text.append(wordText);
					}
				String lineText = text.toString();
				if(lineText.isBlank())
					lineText = jsonString(line, "text", "lyric", "lrc");
				if(lineText.isBlank())
					continue;
				if(start == 0 && !words.isEmpty())
					start = words.get(0).startMs();
				result.add(new LyricLine(start, lineText, words));
			}
			result.sort(Comparator.comparingLong(LyricLine::timeMs));
			return List.copyOf(result);
		}catch(RuntimeException ignored)
		{
			return List.of();
		}
	}

	/**
	 * applemusic-like-lyrics {@code packages/lyric/src/formats/yrc.ts}
	 * {@code parseYrc}：行头 {@code [start,duration]}，词为
	 * {@code (start,duration,0)word}；首尾括号行标为背景人声。
	 */
	private static List<LyricLine> parseYrcText(String source)
	{
		List<LyricLine> result = new ArrayList<>();
		for(String rawLine : source.split("\\R"))
		{
			String lineStr = rawLine.trim();
			if(lineStr.isEmpty())
				continue;
			Matcher lineMatcher = YRC_LINE.matcher(lineStr);
			if(!lineMatcher.lookingAt())
				continue;
			long start = Long.parseLong(lineMatcher.group(1));
			long duration = Long.parseLong(lineMatcher.group(2));
			String lineContent = lineStr.substring(lineMatcher.end()).trim();
			if(lineContent.isEmpty())
				continue;
			List<LyricWord> words = new ArrayList<>();
			long lastStart = -1;
			long lastEnd = -1;
			while(true)
			{
				Matcher wordMatch = YRC_WORD_HEAD.matcher(lineContent);
				if(!wordMatch.find())
					break;
				String lastText = wordMatch.group(1);
				if(!lastText.isEmpty() && lastStart != -1)
					words.add(new LyricWord(lastText, lastStart, lastEnd));
				lastStart = Long.parseLong(wordMatch.group(2));
				lastEnd = lastStart + Long.parseLong(wordMatch.group(3));
				lineContent = lineContent.substring(wordMatch.end());
			}
			if(lastStart != -1 && !lineContent.isEmpty())
				words.add(new LyricWord(lineContent, lastStart, lastEnd));
			if(words.isEmpty())
				continue;
			boolean background = isBackgroundWords(words);
			if(background)
				words = trimBackgroundParentheses(words);
			StringBuilder text = new StringBuilder();
			for(LyricWord word : words)
				text.append(word.text());
			result.add(new LyricLine(start, text.toString(), words,
				start + Math.max(1, duration), "", background));
		}
		result.sort(Comparator.comparingLong(LyricLine::timeMs));
		return List.copyOf(result);
	}

	private static boolean isBackgroundWords(List<LyricWord> words)
	{
		if(words.isEmpty())
			return false;
		String first = words.get(0).text();
		String last = words.get(words.size() - 1).text();
		return (first.startsWith("(") || first.startsWith("（"))
			&& (last.endsWith(")") || last.endsWith("）"));
	}

	private static List<LyricWord> trimBackgroundParentheses(List<LyricWord> words)
	{
		List<LyricWord> trimmed = new ArrayList<>(words.size());
		for(int i = 0; i < words.size(); i++)
		{
			LyricWord word = words.get(i);
			String text = word.text();
			if(i == 0 && (text.startsWith("(") || text.startsWith("（")))
				text = text.substring(1);
			if(i == words.size() - 1 && (text.endsWith(")") || text.endsWith("）")))
				text = text.substring(0, text.length() - 1);
			trimmed.add(new LyricWord(text, word.startMs(), word.endMs()));
		}
		return trimmed;
	}

	/** 翻译 / 音译行与主歌词行的时间戳容差。 */
	static final long SUB_LINE_MATCH_MS = 800;

	static List<LyricLine> attachTranslations(List<LyricLine> lines,
		List<LyricLine> translations)
	{
		if(lines.isEmpty() || translations.isEmpty())
			return lines;
		List<LyricLine> result = new ArrayList<>(lines.size());
		int cursor = 0;
		for(LyricLine line : lines)
		{
			cursor = advance(translations, cursor, line.timeMs());
			LyricLine match = translations.get(cursor);
			long delta = Math.abs(match.timeMs() - line.timeMs());
			if(delta <= SUB_LINE_MATCH_MS)
				result.add(line.withTranslation(match.text()));
			else
				result.add(line);
		}
		return List.copyOf(result);
	}

	/** 音译行（{@code romalrc}）按时间戳就近匹配，规则与翻译行一致。 */
	static List<LyricLine> attachRomanizations(List<LyricLine> lines,
		List<LyricLine> romanizations)
	{
		if(lines.isEmpty() || romanizations.isEmpty())
			return lines;
		List<LyricLine> result = new ArrayList<>(lines.size());
		int cursor = 0;
		for(LyricLine line : lines)
		{
			cursor = advance(romanizations, cursor, line.timeMs());
			LyricLine match = romanizations.get(cursor);
			long delta = Math.abs(match.timeMs() - line.timeMs());
			if(delta <= SUB_LINE_MATCH_MS)
				result.add(line.withRomanization(match.text()));
			else
				result.add(line);
		}
		return List.copyOf(result);
	}

	private static int advance(List<LyricLine> candidates, int cursor,
		long timeMs)
	{
		while(cursor + 1 < candidates.size()
			&& candidates.get(cursor + 1).timeMs() <= timeMs)
			cursor++;
		return cursor;
	}

	public static List<LyricLine> parse(String source)
	{
		if(source == null || source.isBlank())
			return List.of();

		List<LyricLine> result = new ArrayList<>();
		for(String rawLine : source.split("\\R"))
		{
			Matcher matcher = TIMESTAMP.matcher(rawLine);
			List<Long> timestamps = new ArrayList<>();
			int textStart = 0;
			while(matcher.find())
			{
				long minutes = Long.parseLong(matcher.group(1));
				long seconds = Long.parseLong(matcher.group(2));
				long fraction = parseFraction(matcher.group(3));
				timestamps.add((minutes * 60 + seconds) * 1000 + fraction);
				textStart = matcher.end();
			}
			if(timestamps.isEmpty())
				continue;
			String remainder = rawLine.substring(textStart);
			ParsedBody body = parseEnhancedBody(remainder, timestamps.get(0));
			if(body.text().isEmpty())
				continue;
			for(long timestamp : timestamps)
			{
				List<LyricWord> words = body.words();
				if(timestamp != timestamps.get(0) && !words.isEmpty())
				{
					long delta = timestamp - timestamps.get(0);
					List<LyricWord> shifted = new ArrayList<>(words.size());
					for(LyricWord word : words)
						shifted.add(new LyricWord(word.text(),
							word.startMs() + delta, word.endMs() + delta));
					words = shifted;
				}
				result.add(new LyricLine(timestamp, body.text(), words));
			}
		}
		result.sort(Comparator.comparingLong(LyricLine::timeMs));
		return List.copyOf(result);
	}

	private static ParsedBody parseEnhancedBody(String remainder, long lineStart)
	{
		String trimmed = remainder.trim();
		if(trimmed.isEmpty())
			return new ParsedBody("", List.of());
		Matcher matcher = ENHANCED_WORD.matcher(trimmed);
		if(!matcher.find())
			return new ParsedBody(trimmed, List.of());

		List<LyricWord> words = new ArrayList<>();
		StringBuilder text = new StringBuilder();
		int cursor = 0;
		long pendingStart = -1;
		long pendingDuration = 0;
		matcher.reset();
		while(matcher.find())
		{
			if(pendingStart >= 0)
			{
				String wordText = trimmed.substring(cursor, matcher.start());
				appendWord(words, text, wordText, pendingStart, pendingDuration);
			}else if(matcher.start() > cursor)
			{
				String prefix = trimmed.substring(cursor, matcher.start());
				if(!prefix.isBlank())
					appendWord(words, text, prefix, lineStart,
						Math.max(80, prefix.length() * 80L));
			}
			pendingStart = Long.parseLong(matcher.group(1));
			pendingDuration = Long.parseLong(matcher.group(2));
			cursor = matcher.end();
		}
		if(pendingStart >= 0)
			appendWord(words, text, trimmed.substring(cursor), pendingStart,
				pendingDuration);
		if(text.length() == 0)
			return new ParsedBody(trimmed.replaceAll("<[^>]+>", "").trim(),
				List.of());
		return new ParsedBody(text.toString(), words);
	}

	private static void appendWord(List<LyricWord> words, StringBuilder text,
		String wordText, long start, long duration)
	{
		if(wordText == null || wordText.isEmpty())
			return;
		text.append(wordText);
		words.add(new LyricWord(wordText, start,
			start + Math.max(1, duration)));
	}

	private static long parseFraction(String value)
	{
		if(value == null || value.isEmpty())
			return 0;
		return switch(value.length())
		{
			case 1 -> Long.parseLong(value) * 100;
			case 2 -> Long.parseLong(value) * 10;
			default -> Long.parseLong(value.substring(0, 3));
		};
	}

	public static int findCurrentIndex(List<LyricLine> lyrics, long timeMs)
	{
		if(lyrics == null || lyrics.isEmpty())
			return -1;
		int low = 0;
		int high = lyrics.size() - 1;
		int result = -1;
		while(low <= high)
		{
			int middle = (low + high) >>> 1;
			if(lyrics.get(middle).timeMs() <= timeMs)
			{
				result = middle;
				low = middle + 1;
			}else
				high = middle - 1;
		}
		return result;
	}

	private static long jsonLong(JsonObject object, String... keys)
	{
		for(String key : keys)
		{
			JsonElement value = object.get(key);
			if(value == null || value.isJsonNull() || !value.isJsonPrimitive())
				continue;
			try
			{
				return value.getAsLong();
			}catch(RuntimeException ignored)
			{}
		}
		return 0;
	}

	private static String jsonString(JsonObject object, String... keys)
	{
		for(String key : keys)
		{
			JsonElement value = object.get(key);
			if(value == null || value.isJsonNull() || !value.isJsonPrimitive())
				continue;
			return value.getAsString();
		}
		return "";
	}

	private static JsonArray jsonArray(JsonObject object, String... keys)
	{
		for(String key : keys)
		{
			JsonElement value = object.get(key);
			if(value != null && value.isJsonArray())
				return value.getAsJsonArray();
		}
		return null;
	}

	private record ParsedBody(String text, List<LyricWord> words)
	{}
}
