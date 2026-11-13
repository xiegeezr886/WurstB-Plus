package net.wurstclient.clickgui2;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.wurstclient.WurstClient;

final class NavigatorPreferences
{
	static final String[] LANGUAGES = {"en_US", "fr_FR", "es_ES", "sv_SE", "pl_PL", "ru_RU", "zh_ZH"};
	private static final Map<String, Properties> STRINGS = new HashMap<>();
	private static boolean loaded;
	static NavigatorTheme theme = NavigatorTheme.BLEND;
	static String language = "en_US";

	static void load()
	{
		String selected = WurstClient.MC.getLanguageManager().getSelected();
		language = "en_US";
		for(String locale : LANGUAGES)
			if(minecraftLanguage(locale).equalsIgnoreCase(selected)) language = locale;
		if(loaded) return;
		loaded = true;
		var path = WurstClient.INSTANCE.getWurstFolder().resolve("navigator.json");
		if(Files.isRegularFile(path))
			try
			{
				JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
				theme = NavigatorTheme.valueOf(json.get("theme").getAsString());
			}catch(IOException | RuntimeException e)
			{
				System.err.println("[Navigator] Could not load preferences: " + e.getMessage());
			}
	}

	static String text(String key) { return text(key, language); }

	static String text(String key, String locale)
	{
		Properties properties = STRINGS.computeIfAbsent(locale, value -> {
			Properties result = new Properties();
			try(var stream = NavigatorPreferences.class.getClassLoader().getResourceAsStream(
				"assets/wurstpenguin/navigator/text/" + value + ".properties"))
			{
				if(stream != null) result.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
			}catch(IOException e) { System.err.println("[Navigator] Missing language " + value); }
			return result;
		});
		String result = properties.getProperty(key);
		return result != null ? result : locale.equals("en_US") ? key : text(key, "en_US");
	}

	static void selectTheme(NavigatorTheme value) { theme = value; save(); }

	private static String minecraftLanguage(String locale)
	{
		return locale.equals("zh_ZH") ? "zh_cn" : locale.toLowerCase(java.util.Locale.ROOT);
	}

	private static void save()
	{
		JsonObject json = new JsonObject();
		json.addProperty("theme", theme.name());
		try { Files.writeString(WurstClient.INSTANCE.getWurstFolder().resolve("navigator.json"), json.toString()); }
		catch(IOException e) { System.err.println("[Navigator] Could not save preferences: " + e.getMessage()); }
	}
}
