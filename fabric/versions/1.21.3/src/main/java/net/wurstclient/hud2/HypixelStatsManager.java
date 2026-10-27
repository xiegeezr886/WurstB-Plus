/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hud2;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Asynchronous Bedwars stats cache for the Bedwars Stats overlay.
 *
 * <p>Soar v8 receives these values from its private websocket backend. The
 * standalone client has no such backend, so this implementation uses the
 * public Hypixel API with a user-supplied API key. Without a key (or on a
 * non-Hypixel server) the overlay simply shows dashes.
 */
public final class HypixelStatsManager
{
	private static final long SUCCESS_TTL_MS = 10 * 60 * 1000L;
	private static final long FAILURE_TTL_MS = 60 * 1000L;
	private static final long REQUEST_INTERVAL_MS = 520;

	private static final Map<UUID, CachedStats> CACHE =
		new ConcurrentHashMap<>();
	private static final Set<UUID> PENDING = ConcurrentHashMap.newKeySet();
	private static final ExecutorService WORKER =
		Executors.newSingleThreadExecutor(runnable -> {
			Thread thread = new Thread(runnable, "Wurst-HypixelStats");
			thread.setDaemon(true);
			return thread;
		});
	private static final HttpClient CLIENT = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(5)).build();

	private static volatile String apiKey = "";

	private HypixelStatsManager() {}

	public static void setApiKey(String key)
	{
		apiKey = key == null ? "" : key.trim();
	}

	public static Stats get(UUID uuid)
	{
		if(uuid == null)
			return Stats.EMPTY;

		long now = System.currentTimeMillis();
		CachedStats cached = CACHE.get(uuid);
		if(cached != null && now < cached.expiresAt())
			return cached.stats();

		schedule(uuid);
		return cached == null ? Stats.EMPTY : cached.stats();
	}

	private static void schedule(UUID uuid)
	{
		if(apiKey.isEmpty() || !PENDING.add(uuid))
			return;

		WORKER.submit(() -> {
			try
			{
				Thread.sleep(REQUEST_INTERVAL_MS);
				fetch(uuid);
			}catch(InterruptedException ignored)
			{
				Thread.currentThread().interrupt();
			}finally
			{
				PENDING.remove(uuid);
			}
		});
	}

	private static void fetch(UUID uuid)
	{
		long now = System.currentTimeMillis();
		try
		{
			String id = uuid.toString().replace("-", "");
			HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(
					"https://api.hypixel.net/v2/player?uuid=" + id))
				.header("API-Key", apiKey)
				.timeout(Duration.ofSeconds(8)).GET().build();
			HttpResponse<String> response = CLIENT.send(request,
				HttpResponse.BodyHandlers.ofString());

			if(response.statusCode() != 200)
			{
				CACHE.put(uuid,
					new CachedStats(Stats.EMPTY,
						now + FAILURE_TTL_MS));
				return;
			}

			JsonObject root = JsonParser.parseString(response.body())
				.getAsJsonObject();
			if(!root.has("success") || !root.get("success").getAsBoolean())
			{
				CACHE.put(uuid,
					new CachedStats(Stats.EMPTY,
						now + FAILURE_TTL_MS));
				return;
			}

			JsonElement player = root.get("player");
			if(player == null || player.isJsonNull()
				|| !player.getAsJsonObject().has("stats"))
			{
				CACHE.put(uuid, new CachedStats(Stats.EMPTY,
					now + SUCCESS_TTL_MS));
				return;
			}

			JsonObject stats = player.getAsJsonObject()
				.getAsJsonObject("stats");
			if(!stats.has("Bedwars")
				|| !stats.get("Bedwars").isJsonObject())
			{
				CACHE.put(uuid, new CachedStats(Stats.EMPTY,
					now + SUCCESS_TTL_MS));
				return;
			}

			JsonObject bedwars = stats.getAsJsonObject("Bedwars");
			CACHE.put(uuid, new CachedStats(parse(bedwars),
				now + SUCCESS_TTL_MS));
		}catch(Exception e)
		{
			CACHE.put(uuid,
				new CachedStats(Stats.EMPTY, now + FAILURE_TTL_MS));
		}
	}

	private static Stats parse(JsonObject bedwars)
	{
		int experience = getInt(bedwars, "Experience");
		int level = experience <= 0 ? 0 : 1 + experience / 5000;
		return new Stats(level + "\u2605",
			ratio(bedwars, "wins_bedwars", "losses_bedwars"),
			ratio(bedwars, "final_kills_bedwars", "final_deaths_bedwars"),
			ratio(bedwars, "beds_broken_bedwars", "beds_lost_bedwars"));
	}

	private static String ratio(JsonObject json, String numerator,
		String denominator)
	{
		double top = getDouble(json, numerator);
		double bottom = getDouble(json, denominator);
		if(top < 0 || bottom < 0)
			return "-";
		double value = top / Math.max(1, bottom);
		return String.format(Locale.ROOT, "%.2f", value);
	}

	private static int getInt(JsonObject json, String key)
	{
		try
		{
			return json.has(key) ? json.get(key).getAsInt() : 0;
		}catch(Exception e)
		{
			return 0;
		}
	}

	private static double getDouble(JsonObject json, String key)
	{
		try
		{
			return json.has(key) ? json.get(key).getAsDouble() : -1;
		}catch(Exception e)
		{
			return -1;
		}
	}

	public record Stats(String level, String wlr, String fkdr, String bblr)
	{
		public static final Stats EMPTY = new Stats("-", "-", "-", "-");
	}

	private record CachedStats(Stats stats, long expiresAt)
	{
	}
}
