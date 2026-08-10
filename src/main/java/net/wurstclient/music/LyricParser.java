package net.wurstclient.music;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LyricParser
{
	private static final Pattern TIMESTAMP = Pattern.compile(
		"\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");

	private LyricParser()
	{}

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
			String text = rawLine.substring(textStart).trim();
			if(text.isEmpty())
				continue;
			for(long timestamp : timestamps)
				result.add(new LyricLine(timestamp, text));
		}
		result.sort(Comparator.comparingLong(LyricLine::timeMs));
		return List.copyOf(result);
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
}
