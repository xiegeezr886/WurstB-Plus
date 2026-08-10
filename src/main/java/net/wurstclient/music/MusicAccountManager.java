package net.wurstclient.music;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.wurstclient.WurstClient;
import net.wurstclient.util.json.JsonException;
import net.wurstclient.util.json.JsonUtils;

public final class MusicAccountManager
{
	private final Map<MusicProvider, StoredSession> sessions =
		new EnumMap<>(MusicProvider.class);

	public MusicAccountManager()
	{
		load();
	}

	public synchronized ImportResult importCookie(MusicProvider provider,
		String rawCookie)
	{
		if(provider == MusicProvider.NETEASE)
			return new ImportResult(false, "\u8bf7\u4f7f\u7528\u7f51\u6613\u4e91\u767b\u5f55\u9762\u677f");
		ParsedCookie parsed = parseCookie(provider, rawCookie);
		if(!parsed.valid())
			return new ImportResult(false, parsed.message());

		String cookie = normalizeCookie(rawCookie);
		String nickname = provider.getDisplayName() + " " + parsed.userId();
		StoredSession previous = sessions.put(provider,
			new StoredSession(cookie, parsed.userId(),
			nickname, System.currentTimeMillis()));
		if(!save())
		{
			if(previous == null)
				sessions.remove(provider);
			else
				sessions.put(provider, previous);
			return new ImportResult(false, "\u65e0\u6cd5\u4fdd\u5b58\u767b\u5f55\u4f1a\u8bdd");
		}
		return new ImportResult(true, provider.getDisplayName() + " \u4f1a\u8bdd\u5df2\u4fdd\u5b58");
	}

	public synchronized boolean isConnected(MusicProvider provider)
	{
		return sessions.containsKey(provider);
	}

	public synchronized AccountStatus getStatus(MusicProvider provider)
	{
		StoredSession session = sessions.get(provider);
		if(session == null)
			return new AccountStatus(provider, false, "", "", 0);
		return new AccountStatus(provider, true, session.userId(),
			session.nickname(), session.connectedAt());
	}

	public synchronized String getCookie(MusicProvider provider)
	{
		StoredSession session = sessions.get(provider);
		return session == null ? "" : session.cookie();
	}

	public synchronized void logout(MusicProvider provider)
	{
		if(sessions.remove(provider) != null)
			save();
	}

	static ParsedCookie parseCookie(MusicProvider provider, String rawCookie)
	{
		String cookie = normalizeCookie(rawCookie);
		if(cookie.isBlank())
			return new ParsedCookie(false, "", "\u8bf7\u5148\u7c98\u8d34 Cookie");
		if(cookie.length() > 16_384)
			return new ParsedCookie(false, "", "Cookie \u8fc7\u957f");

		Map<String, String> values = splitCookie(cookie);
		if(provider == MusicProvider.QQ)
		{
			String userId = first(values, "uin", "p_uin", "wxuin");
			userId = userId.replaceFirst("^[oO]", "");
			String key = first(values, "qqmusic_key", "qm_keyst", "p_skey",
				"skey");
			if(!userId.matches("\\d+") || key.isBlank())
				return new ParsedCookie(false, "",
					"QQ Cookie \u7f3a\u5c11 uin \u6216 qqmusic_key");
			return new ParsedCookie(true, userId, "");
		}
		if(provider == MusicProvider.KUGOU)
		{
			String compound = first(values, "kugoo");
			Map<String, String> combined = new java.util.LinkedHashMap<>(values);
			if(!compound.isBlank())
				combined.putAll(splitCompound(compound));
			String userId = first(combined, "userid", "kugooid",
				"kugouid");
			String token = first(combined, "token", "kugoopwd", "key");
			if(userId.isBlank() || token.isBlank())
				return new ParsedCookie(false, "",
					"\u9177\u72d7 Cookie \u7f3a\u5c11 userid \u6216 token");
			return new ParsedCookie(true, userId, "");
		}
		return new ParsedCookie(false, "", "\u4e0d\u652f\u6301\u7684\u5e73\u53f0");
	}

	private static String normalizeCookie(String rawCookie)
	{
		return rawCookie == null ? ""
			: rawCookie.replace('\r', ' ').replace('\n', ' ').trim();
	}

	private static Map<String, String> splitCookie(String cookie)
	{
		Map<String, String> values = new java.util.LinkedHashMap<>();
		for(String part : cookie.split(";"))
		{
			int equals = part.indexOf('=');
			if(equals <= 0)
				continue;
			values.put(part.substring(0, equals).trim().toLowerCase(Locale.ROOT),
				part.substring(equals + 1).trim());
		}
		return values;
	}

	private static Map<String, String> splitCompound(String value)
	{
		return splitCookie(value.replace('&', ';'));
	}

	private static String first(Map<String, String> values, String... keys)
	{
		for(String key : keys)
		{
			String value = values.get(key);
			if(value != null && !value.isBlank())
				return value;
		}
		return "";
	}

	private void load()
	{
		Path file = accountFile();
		if(!Files.isRegularFile(file))
			return;
		try
		{
			JsonElement rootElement = JsonUtils.parseFile(file);
			if(!rootElement.isJsonObject())
				return;
			JsonObject root = rootElement.getAsJsonObject();
			for(MusicProvider provider : MusicProvider.values())
			{
				if(provider == MusicProvider.NETEASE
					|| !root.has(provider.name()))
					continue;
				JsonObject value = root.getAsJsonObject(provider.name());
				String cookie = text(value, "cookie");
				ParsedCookie parsed = parseCookie(provider, cookie);
				if(!parsed.valid())
					continue;
				String userId = text(value, "userId");
				String nickname = text(value, "nickname");
				sessions.put(provider, new StoredSession(cookie,
					userId.isBlank() ? parsed.userId() : userId,
					nickname.isBlank() ? provider.getDisplayName() : nickname,
					number(value, "connectedAt")));
			}
		}catch(IOException | JsonException | RuntimeException ignored)
		{}
	}

	private boolean save()
	{
		JsonObject root = new JsonObject();
		for(Map.Entry<MusicProvider, StoredSession> entry : sessions.entrySet())
		{
			JsonObject value = new JsonObject();
			value.addProperty("cookie", entry.getValue().cookie());
			value.addProperty("userId", entry.getValue().userId());
			value.addProperty("nickname", entry.getValue().nickname());
			value.addProperty("connectedAt", entry.getValue().connectedAt());
			root.add(entry.getKey().name(), value);
		}
		try
		{
			Path file = accountFile();
			Files.createDirectories(file.getParent());
			Files.writeString(file, root.toString(), StandardCharsets.UTF_8);
			return true;
		}catch(IOException ignored)
		{
			return false;
		}
	}

	private Path accountFile()
	{
		return WurstClient.INSTANCE.getWurstFolder()
			.resolve("music-accounts.json");
	}

	private static String text(JsonObject object, String key)
	{
		return object.has(key) ? object.get(key).getAsString() : "";
	}

	private static long number(JsonObject object, String key)
	{
		return object.has(key) ? object.get(key).getAsLong() : 0;
	}

	public record AccountStatus(MusicProvider provider, boolean connected,
		String userId, String nickname, long connectedAt)
	{}

	public record ImportResult(boolean success, String message)
	{}

	record ParsedCookie(boolean valid, String userId, String message)
	{}

	private record StoredSession(String cookie, String userId, String nickname,
		long connectedAt)
	{}
}
