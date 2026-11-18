package net.wurstclient.clickgui2;

import java.util.List;
import java.util.Locale;

/** Rise's alias substring search: each word and the joined query are alternatives. */
final class NavigatorSearch
{
	private NavigatorSearch() {}

	static boolean matches(List<String> aliases, String query)
	{
		String normalized = query.toLowerCase(Locale.ROOT).trim();
		if(normalized.isEmpty())
			return true;
		String[] terms = (normalized + " " + normalized.replaceAll("\\s+", "")).split("\\s+");
		return aliases.stream().map(alias -> alias.toLowerCase(Locale.ROOT).replaceAll("\\s+", ""))
			.anyMatch(alias -> java.util.Arrays.stream(terms).anyMatch(alias::contains));
	}
}
