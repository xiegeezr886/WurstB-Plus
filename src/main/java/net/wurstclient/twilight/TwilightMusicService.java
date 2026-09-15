/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.twilight;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.wurstclient.music.NeteasePlaylist;
import net.wurstclient.music.NeteaseSong;

/**
 * Client for NeteaseCloudMusicApiEnhanced, the local service the reference
 * project (Twilight Echo) talks to over {@code http://127.0.0.1:3000}.
 *
 * <p>
 * The repository's existing {@code NeteaseCloudApi} speaks the <i>direct</i>
 * encrypted weapi/eapi endpoints on {@code music.163.com}. That is a different
 * protocol against a different host, so aligning the data layer with the
 * reference means adding this client rather than redirecting that one. Both
 * identify songs by the same Netease ids, so a song fetched here can be handed
 * straight to {@code NeteaseMusicPlayer}, which keeps resolving stream URLs and
 * lyrics its own way.
 *
 * <p>
 * Login is the one place where the two must agree: a QR login performed here
 * yields a cookie header which is handed to the player so both sides are logged
 * into the same account.
 *
 * <p>
 * Deliberately free of Minecraft and Skia imports so the response parsing can be
 * unit tested without a game instance. Nothing here touches the network until a
 * method is called, so constructing the class is always safe.
 */
public final class TwilightMusicService implements AutoCloseable
{
	public static final String USER_AGENT =
		"Mozilla/5.0 WurstBPlus/1.6 TwilightEcho";

	private static final Duration TIMEOUT = Duration.ofSeconds(12);

	private final ExecutorService executor = Executors.newFixedThreadPool(2,
		newThreadFactory());

	private final CookieManager cookieManager =
		new CookieManager(null, CookiePolicy.ACCEPT_ALL);

	private final HttpClient client = HttpClient.newBuilder()
		.connectTimeout(TIMEOUT).cookieHandler(cookieManager).build();

	private volatile String base;
	private volatile boolean reachable;
	private volatile String lastError = "";
	private volatile String loginCookie = "";

	public TwilightMusicService()
	{
		this(TwilightApiEndpoint.DEFAULT_BASE);
	}

	public TwilightMusicService(String base)
	{
		this.base = TwilightApiEndpoint.normalize(base);
	}

	public String getBase()
	{
		return base;
	}

	/** Accepts anything a user would type; see {@link TwilightApiEndpoint}. */
	public void setBase(String value)
	{
		base = TwilightApiEndpoint.normalize(value);
	}

	/**
	 * Whether the last call actually reached the service. The UI uses this to
	 * decide between the local service and the repository's direct API.
	 */
	public boolean isReachable()
	{
		return reachable;
	}

	public String getLastError()
	{
		return lastError;
	}

	/**
	 * Cookie header captured from a QR login, for handing to
	 * {@code NeteaseMusicPlayer.loginWithCookie(String)}. Empty until login.
	 */
	public String getLoginCookie()
	{
		return loginCookie;
	}

	@Override
	public void close()
	{
		executor.shutdownNow();
	}

	/**
	 * Cheap liveness check. Never throws: a failure just marks the service
	 * unreachable so callers can fall back.
	 */
	public CompletableFuture<Boolean> probe()
	{
		return supply(() -> {
			try
			{
				get(TwilightApiEndpoint.url(base,
					TwilightApiEndpoint.LOGIN_STATUS));
				succeed();
				return true;
			}catch(Exception e)
			{
				fail(e);
				return false;
			}
		});
	}

	/** Daily recommendations: {@code /recommend/songs}. */
	public CompletableFuture<List<NeteaseSong>> dailySongs()
	{
		return songs(TwilightApiEndpoint.url(base,
			TwilightApiEndpoint.DAILY_SONGS));
	}

	/** Personal FM: {@code /personal_fm}. */
	public CompletableFuture<List<NeteaseSong>> personalFm()
	{
		return songs(TwilightApiEndpoint.url(base,
			TwilightApiEndpoint.PERSONAL_FM));
	}

	public CompletableFuture<List<NeteaseSong>> search(String keywords,
		int limit)
	{
		String url = TwilightApiEndpoint.url(base, TwilightApiEndpoint.SEARCH,
			"keywords", keywords == null ? "" : keywords, "limit",
			Integer.toString(clamp(limit, 1, 100)), "type", "1");
		return songs(url);
	}

	/**
	 * Full playlist contents. {@code /playlist/track/all} is the complete
	 * listing; {@code /playlist/detail} only carries the first batch, so it is
	 * used as a fallback when the service predates the former.
	 */
	public CompletableFuture<List<NeteaseSong>> playlistTracks(long playlistId,
		int limit)
	{
		String id = Long.toString(playlistId);
		String amount = Integer.toString(clamp(limit, 1, 1000));

		return supply(() -> {
			try
			{
				JsonObject root = get(TwilightApiEndpoint.url(base,
					TwilightApiEndpoint.PLAYLIST_TRACKS, "id", id, "limit",
					amount, "offset", "0"));
				succeed();
				return songsAt(root, "songs");
			}catch(Exception first)
			{
				try
				{
					JsonObject root = get(TwilightApiEndpoint.url(base,
						TwilightApiEndpoint.PLAYLIST_DETAIL, "id", id));
					succeed();
					return songsAt(root, "playlist.tracks");
				}catch(Exception second)
				{
					fail(second);
					return List.<NeteaseSong>of();
				}
			}
		});
	}

	public CompletableFuture<List<NeteasePlaylist>> recommendedPlaylists(
		int limit)
	{
		String url = TwilightApiEndpoint.url(base,
			TwilightApiEndpoint.RECOMMEND_PLAYLISTS, "limit",
			Integer.toString(clamp(limit, 1, 50)));

		return supply(() -> {
			try
			{
				JsonObject root = get(url);
				succeed();
				return playlistsAt(root, "result", "data.result");
			}catch(Exception e)
			{
				fail(e);
				return List.<NeteasePlaylist>of();
			}
		});
	}

	/**
	 * The logged-in account's liked songs. The service exposes the id list and
	 * the song details through separate routes, so this is two round trips; the
	 * query counts as one call as far as callers are concerned.
	 */
	public CompletableFuture<List<NeteaseSong>> likedSongs(int limit,
		int offset)
	{
		int amount = clamp(limit, 1, 1000);
		int from = Math.max(0, offset);

		return supply(() -> {
			try
			{
				JsonObject idsRoot = get(TwilightApiEndpoint.url(base,
					TwilightApiEndpoint.LIKED_IDS, "timestamp",
					Long.toString(System.currentTimeMillis())));
				long[] ids = idsAt(idsRoot, "ids", "data.ids");
				if(ids.length == 0)
				{
					// Older services expose the liked list as a playlist.
					ids = idsAt(get(TwilightApiEndpoint.url(base,
						TwilightApiEndpoint.LIKED_LIST, "uid",
						Long.toString(number(idsRoot, "uid")))), "ids",
						"data.ids");
				}

				if(ids.length == 0)
				{
					succeed();
					return List.<NeteaseSong>of();
				}

				StringBuilder query = new StringBuilder("[");
				for(int i = from; i < ids.length && i < from + amount; i++)
				{
					if(query.length() > 1)
						query.append(',');
					query.append(ids[i]);
				}
				query.append(']');

				if(query.length() <= 2)
				{
					succeed();
					return List.<NeteaseSong>of();
				}

				List<NeteaseSong> songs = songsAt(
					get(TwilightApiEndpoint.url(base,
						TwilightApiEndpoint.SONG_DETAIL, "ids",
						query.toString())),
					"songs");
				succeed();
				return songs;
			}catch(Exception e)
			{
				fail(e);
				return List.<NeteaseSong>of();
			}
		});
	}

	/**
	 * Asks the service for a QR code and the PNG that encodes it. The image
	 * arrives as a {@code data:image/png;base64,...} URL, which is what the
	 * GUI renders - no QR encoder is needed on this side.
	 */
	public CompletableFuture<QrSession> createQr()
	{
		return supply(() -> {
			try
			{
				JsonObject keyRoot = get(TwilightApiEndpoint.url(base,
					TwilightApiEndpoint.QR_KEY, "timestamp",
					Long.toString(System.currentTimeMillis())));
				String key = firstString(keyRoot, "data.unikey", "unikey");
				if(key.isEmpty())
					return QrSession.failed("音乐服务没有返回二维码 key");

				JsonObject createRoot = get(TwilightApiEndpoint.url(base,
					TwilightApiEndpoint.QR_CREATE, "key", key, "qrimg",
					"true"));
				succeed();
				return new QrSession(key,
					firstString(createRoot, "data.qrurl", "qrurl"),
					firstString(createRoot, "data.qrimg", "qrimg"), "");
			}catch(Exception e)
			{
				fail(e);
				return QrSession.failed(lastError);
			}
		});
	}

	/** Polls one QR code; codes match the service ({@code 800..803}). */
	public CompletableFuture<QrState> checkQr(String key)
	{
		return supply(() -> {
			try
			{
				JsonObject root = get(TwilightApiEndpoint.url(base,
					TwilightApiEndpoint.QR_CHECK, "key", key, "timestamp",
					Long.toString(System.currentTimeMillis())));
				int code = (int)number(root, "code");
				String cookie = firstString(root, "cookie");

				if(code == 803 && !cookie.isEmpty())
					loginCookie = cookie;

				succeed();
				return new QrState(code, firstString(root, "message", "msg"),
					cookie);
			}catch(Exception e)
			{
				fail(e);
				return new QrState(0, lastError, "");
			}
		});
	}

	private CompletableFuture<List<NeteaseSong>> songs(String url)
	{
		return supply(() -> {
			try
			{
				JsonObject root = get(url);
				succeed();
				return songsAt(root, "data.dailySongs", "result.songs",
					"data", "songs", "recommend");
			}catch(Exception e)
			{
				fail(e);
				return List.<NeteaseSong>of();
			}
		});
	}

	private <T> CompletableFuture<T> supply(Supplier<T> supplier)
	{
		return CompletableFuture.supplyAsync(supplier, executor);
	}

	private JsonObject get(String url) throws IOException, InterruptedException
	{
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
			.timeout(TIMEOUT).header("User-Agent", USER_AGENT)
			.header("Accept", "application/json").GET().build();
		HttpResponse<String> response = client.send(request,
			HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

		if(response.statusCode() / 100 != 2)
			throw new IOException("音乐服务返回 HTTP " + response.statusCode());

		return parse(response.body());
	}

	private void succeed()
	{
		reachable = true;
		lastError = "";
	}

	private void fail(Exception e)
	{
		reachable = false;
		lastError = describe(e);
	}

	private static String describe(Exception e)
	{
		String message = e.getMessage();
		return message == null || message.isBlank() ? e.getClass().getSimpleName()
			: message;
	}

	private static int clamp(int value, int min, int max)
	{
		return Math.max(min, Math.min(max, value));
	}

	private static ThreadFactory newThreadFactory()
	{
		AtomicInteger counter = new AtomicInteger();
		return runnable -> {
			Thread thread = new Thread(runnable,
				"TwilightMusic-" + counter.incrementAndGet());
			thread.setDaemon(true);
			return thread;
		};
	}

	// ------------------------------------------------------------------
	// Response parsing. Everything below is pure and unit tested.
	// ------------------------------------------------------------------

	/**
	 * The service wraps results differently across versions ({@code data},
	 * {@code result}, or the array at the top level), so callers pass the paths
	 * they accept and the first array wins.
	 */
	static JsonArray firstArray(JsonObject root, String... paths)
	{
		for(String path : paths)
		{
			JsonElement element = at(root, path);
			if(element != null && element.isJsonArray())
				return element.getAsJsonArray();
		}

		return new JsonArray();
	}

	static String firstString(JsonObject root, String... paths)
	{
		for(String path : paths)
		{
			JsonElement element = at(root, path);

			// 布尔值不是合法的 key/qrurl/qrimg，直接跳过而不是读成 "true"
			if(element == null || element.isJsonNull()
				|| !element.isJsonPrimitive()
				|| element.getAsJsonPrimitive().isBoolean())
				continue;

			try
			{
				String value = element.getAsString();
				if(value != null && !value.isBlank())
					return value;
			}catch(RuntimeException ignored)
			{
				// 与 string() 一致：任何解析失败都当作字段不存在
			}
		}

		return "";
	}

	/** Walks {@code data.dailySongs}-style dotted paths; null when absent. */
	static JsonElement at(JsonObject root, String path)
	{
		if(root == null || path == null || path.isBlank())
			return null;

		JsonElement current = root;

		for(String segment : path.split("\\."))
		{
			if(!current.isJsonObject())
				return null;

			current = current.getAsJsonObject().get(segment);

			if(current == null || current.isJsonNull())
				return null;
		}

		return current;
	}

	static List<NeteaseSong> songsAt(JsonObject root, String... paths)
	{
		List<NeteaseSong> songs = new ArrayList<>();

		for(JsonElement element : firstArray(root, paths))
		{
			if(!element.isJsonObject())
				continue;

			NeteaseSong parsed = song(element.getAsJsonObject());
			if(parsed.id() > 0)
				songs.add(parsed);
		}

		return List.copyOf(songs);
	}

	static List<NeteasePlaylist> playlistsAt(JsonObject root, String... paths)
	{
		List<NeteasePlaylist> playlists = new ArrayList<>();

		for(JsonElement element : firstArray(root, paths))
		{
			if(!element.isJsonObject())
				continue;

			NeteasePlaylist parsed = playlist(element.getAsJsonObject());
			if(parsed.id() > 0)
				playlists.add(parsed);
		}

		return List.copyOf(playlists);
	}

	static long[] idsAt(JsonObject root, String... paths)
	{
		JsonArray array = firstArray(root, paths);
		long[] ids = new long[array.size()];
		int count = 0;

		for(JsonElement element : array)
		{
			long id = primitive(element);
			if(id > 0)
				ids[count++] = id;
		}

		if(count == ids.length)
			return ids;

		long[] trimmed = new long[count];
		System.arraycopy(ids, 0, trimmed, 0, count);
		return trimmed;
	}

	/**
	 * Reads one song. Only the fields the shell draws are kept; artist credit
	 * stays a single line like the reference, which joins the first artist.
	 */
	static NeteaseSong song(JsonObject item)
	{
		JsonObject album = object(item, "al");
		if(album.size() == 0)
			album = object(item, "album");

		JsonArray artists = array(item, "ar");
		if(artists.isEmpty())
			artists = array(item, "artists");

		String artist = "";
		for(JsonElement element : artists)
		{
			if(!element.isJsonObject())
				continue;

			artist = string(element.getAsJsonObject(), "name");
			if(!artist.isEmpty())
				break;
		}

		long duration = number(item, "dt");
		if(duration <= 0)
			duration = number(item, "duration");

		return new NeteaseSong(number(item, "id"), string(item, "name"),
			artist, string(album, "name"), imageUrl(string(album, "picUrl")),
			duration);
	}

	static NeteasePlaylist playlist(JsonObject item)
	{
		String cover = string(item, "picUrl");
		if(cover.isEmpty())
			cover = string(item, "coverImgUrl");

		return new NeteasePlaylist(number(item, "id"), string(item, "name"),
			imageUrl(cover), number(item, "playCount"));
	}

	/**
	 * Cover URLs arrive without a size parameter; the renderer wants a small
	 * square, so one is appended once. Blank stays blank.
	 */
	static String imageUrl(String url)
	{
		if(url == null || url.isBlank())
			return "";

		if(url.contains("?param="))
			return url;

		return url + "?param=200y200";
	}

	/**
	 * Decodes the {@code data:image/png;base64,} URL the QR route returns.
	 * Malformed input yields an empty array so the caller draws a placeholder
	 * instead of throwing on the render thread.
	 */
	static byte[] decodeDataUrl(String dataUrl)
	{
		if(dataUrl == null || dataUrl.isBlank())
			return new byte[0];

		String payload = dataUrl.trim();

		if(payload.startsWith("data:"))
		{
			int comma = payload.indexOf(',');
			if(comma < 0)
				return new byte[0];

			payload = payload.substring(comma + 1).trim();
		}

		if(payload.isEmpty())
			return new byte[0];

		try
		{
			return Base64.getDecoder().decode(payload);
		}catch(IllegalArgumentException e)
		{
			// 带换行的 base64 只有 MIME 解码器能读
			try
			{
				return Base64.getMimeDecoder().decode(payload);
			}catch(IllegalArgumentException ignored)
			{
				return new byte[0];
			}
		}
	}

	static JsonObject parse(String body) throws IOException
	{
		if(body != null && !body.isBlank())
			try
			{
				JsonElement parsed = JsonParser.parseString(body);

				if(parsed.isJsonObject())
					return parsed.getAsJsonObject();
			}catch(RuntimeException ignored)
			{
				// 交给下面的统一错误
			}

		throw new IOException("音乐服务返回了无法解析的内容");
	}

	static JsonObject object(JsonObject parent, String key)
	{
		JsonElement value = parent == null ? null : parent.get(key);
		return value != null && value.isJsonObject() ? value.getAsJsonObject()
			: new JsonObject();
	}

	static JsonArray array(JsonObject parent, String key)
	{
		JsonElement value = parent == null ? null : parent.get(key);
		return value != null && value.isJsonArray() ? value.getAsJsonArray()
			: new JsonArray();
	}

	static String string(JsonObject parent, String key)
	{
		JsonElement value = parent == null ? null : parent.get(key);

		if(value == null || value.isJsonNull() || !value.isJsonPrimitive())
			return "";

		try
		{
			return value.getAsString();
		}catch(RuntimeException e)
		{
			return "";
		}
	}

	static long number(JsonObject parent, String key)
	{
		return primitive(parent == null ? null : parent.get(key));
	}

	private static long primitive(JsonElement value)
	{
		if(value == null || value.isJsonNull() || !value.isJsonPrimitive())
			return 0;

		try
		{
			return value.getAsLong();
		}catch(RuntimeException e)
		{
			return 0;
		}
	}

	/** One QR code: the polling key plus the PNG to draw. */
	public record QrSession(String key, String qrUrl, String imageDataUrl,
		String error)
	{
		public static QrSession failed(String message)
		{
			return new QrSession("", "", "",
				message == null || message.isBlank() ? "无法获取二维码" : message);
		}

		public boolean isUsable()
		{
			return !key.isBlank();
		}

		public byte[] imageBytes()
		{
			return decodeDataUrl(imageDataUrl);
		}
	}

	/** One poll result; the service uses the same 800..803 codes. */
	public record QrState(int code, String message, String cookie)
	{
		public boolean isExpired()
		{
			return code == 800;
		}

		public boolean isWaiting()
		{
			return code == 801;
		}

		public boolean isScanned()
		{
			return code == 802;
		}

		public boolean isSuccess()
		{
			return code == 803;
		}
	}
}
