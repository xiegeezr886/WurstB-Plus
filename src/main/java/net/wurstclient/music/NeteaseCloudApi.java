package net.wurstclient.music;

import java.io.IOException;
import java.math.BigInteger;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.wurstclient.WurstClient;

public final class NeteaseCloudApi implements MusicPlatform
{
	private static final String ORIGIN = "https://music.163.com";
	private static final String USER_AGENT =
		"Mozilla/5.0 WurstBPlus/1.6 NeteaseMusic";
	private static final String WEAPI_NONCE = "0CoJUm6Qyw8W8jud";
	private static final String WEAPI_IV = "0102030405060708";
	private static final String WEAPI_MODULUS =
		"00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7"
			+ "b725152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf6952"
			+ "80104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee2"
			+ "55932575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289"
			+ "dc6935b3ece0462db0a22b8e7";
	private static final Pattern SESSION_COOKIE = Pattern.compile(
		"(?:^|[;,]\\s*)(MUSIC_U|MUSIC_A_T|MUSIC_R_T|__csrf)=([^;,\\s]+)");
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final CookieManager COOKIE_MANAGER =
		new CookieManager(null, CookiePolicy.ACCEPT_ALL);
	private static final HttpClient CLIENT = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.cookieHandler(COOKIE_MANAGER).build();

	private volatile String cookie = "";
	private volatile NeteaseUserProfile userProfile;

	public NeteaseCloudApi()
	{
		loadCookie();
	}

	@Override
	public String providerName()
	{
		return "NETEASE";
	}

	public List<NeteaseSong> search(String query, int limit)
		throws IOException, InterruptedException
	{
		String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
		String url = ORIGIN + "/api/search/get/web?csrf_token=&s=" + encoded
			+ "&type=1&offset=0&total=true&limit=" + Math.max(1,
				Math.min(50, limit));
		JsonObject root = getJson(url, false).body();
		JsonObject result = object(root, "result");
		JsonArray songs = array(result, "songs");
		List<NeteaseSong> parsed = new ArrayList<>(songs.size());
		for(JsonObject song : objects(songs))
			parsed.add(parseSong(song));
		return List.copyOf(parsed);
	}

	/**
	 * 网易云不同接口的歌曲对象字段名不一致（{@code al/ar/dt} 与
	 * {@code album/artists/duration}），且同一接口也可能返回字符串或数字。
	 * 与 {@code search()} 使用同一套安全访问器，避免单个畸形条目中断整页结果。
	 */
	static NeteaseSong parseSong(JsonObject song)
	{
		JsonObject album = object(song, "al");
		if(album.size() == 0)
			album = object(song, "album");
		JsonArray artists = array(song, "ar");
		if(artists.isEmpty())
			artists = array(song, "artists");
		List<JsonObject> artistObjects = objects(artists);
		String artist = artistObjects.isEmpty() ? ""
			: string(artistObjects.get(0), "name");
		long duration = number(song, "dt");
		if(duration <= 0)
			duration = number(song, "duration");
		return new NeteaseSong(number(song, "id"), string(song, "name"),
			artist, string(album, "name"), imageUrl(string(album, "picUrl")),
			duration);
	}

	/**
	 * 网易云封面地址通常不带尺寸参数；已带 {@code ?param=} 的地址原样返回，
	 * 空值返回空串，避免把 {@code null} 传给渲染层。
	 */
	static String imageUrl(String url)
	{
		if(url == null || url.isBlank())
			return "";
		if(url.contains("?param="))
			return url;
		return url + "?param=200y200";
	}

	public List<NeteaseSong> topNewSongs(int limit)
		throws IOException, InterruptedException
	{
		int actualLimit = Math.max(1, Math.min(50, limit));
		JsonObject root = getJson(ORIGIN
			+ "/api/discovery/new/songs?areaId=0&limit=" + actualLimit,
			false).body();
		JsonArray data = array(root, "data");
		List<NeteaseSong> songs = new ArrayList<>();
		for(JsonObject song : objects(data))
		{
			if(songs.size() >= actualLimit)
				break;
			songs.add(parseSong(song));
		}
		return List.copyOf(songs);
	}

	public List<NeteasePlaylist> recommendedPlaylists(int limit)
		throws IOException, InterruptedException
	{
		int actualLimit = Math.max(1, Math.min(20, limit));
		String category = URLEncoder.encode("全部", StandardCharsets.UTF_8);
		JsonObject root = getJson(ORIGIN + "/api/playlist/list?cat=" + category
			+ "&order=hot&offset=0&total=true&limit=" + actualLimit, false)
			.body();
		List<NeteasePlaylist> playlists = new ArrayList<>();
		for(JsonObject item : objects(array(root, "playlists")))
			playlists.add(new NeteasePlaylist(number(item, "id"),
				string(item, "name"), imageUrl(string(item, "coverImgUrl")),
				number(item, "playCount")));
		return List.copyOf(playlists);
	}

	public List<NeteaseSong> playlistSongs(long playlistId, int limit,
		int offset) throws IOException, InterruptedException
	{
		int actualLimit = Math.max(1, Math.min(100, limit));
		int actualOffset = Math.max(0, offset);
		JsonObject root = getJson(ORIGIN + "/api/v3/playlist/detail?id="
			+ playlistId + "&n=100000&s=0", isLoggedIn()).body();
		List<JsonObject> tracks = objects(array(object(root, "playlist"),
			"trackIds"));
		int end = Math.min(tracks.size(), actualOffset + actualLimit);
		if(actualOffset >= end)
			return List.of();
		StringBuilder ids = new StringBuilder("[");
		for(JsonObject track : tracks.subList(actualOffset, end))
		{
			if(ids.length() > 1)
				ids.append(',');
			ids.append(number(track, "id"));
		}
		ids.append(']');
		String encoded = URLEncoder.encode(ids.toString(), StandardCharsets.UTF_8);
		JsonObject details = getJson(ORIGIN + "/api/song/detail/?ids=" + encoded,
			false).body();
		List<NeteaseSong> songs = new ArrayList<>();
		for(JsonObject song : objects(array(details, "songs")))
			songs.add(parseSong(song));
		return List.copyOf(songs);
	}

	public List<NeteaseSong> likedSongs(int limit, int offset)
		throws IOException, InterruptedException
	{
		NeteaseUserProfile profile = userProfile;
		if(profile == null && !refreshUserProfile())
			return List.of();
		profile = userProfile;
		JsonObject root = getJson(ORIGIN + "/api/user/playlist?uid="
			+ profile.userId() + "&limit=1&timestamp=" + System.currentTimeMillis(),
			true).body();
		List<JsonObject> playlists = objects(array(root, "playlist"));
		if(playlists.isEmpty())
			return List.of();
		long playlistId = number(playlists.get(0), "id");
		return playlistSongs(playlistId, limit, offset);
	}

	public SongResource resolve(NeteaseSong song)
		throws IOException, InterruptedException
	{
		String ids = URLEncoder.encode("[" + song.id() + "]",
			StandardCharsets.UTF_8);
		JsonObject root = getJson(ORIGIN
			+ "/api/song/enhance/player/url?id=" + song.id() + "&ids=" + ids
			+ "&br=320000", true).body();
		List<JsonObject> data = objects(array(root, "data"));
		if(data.isEmpty())
			throw new IOException("网易云没有返回播放地址");
		JsonObject item = data.get(0);
		String url = string(item, "url");
		if(url.isBlank())
			throw new IOException("该歌曲受版权或会员限制，当前账号无法播放");
		return new SongResource(URI.create(url), number(item, "size"),
			string(item, "type"));
	}

	public List<LyricLine> lyrics(long songId)
		throws IOException, InterruptedException
	{
		JsonObject root;
		try
		{
			root = getJson(ORIGIN + "/api/song/lyric/v1?id=" + songId
				+ "&lv=-1&kv=-1&tv=-1&rv=-1&yv=-1", false).body();
		}catch(IOException e)
		{
			// 旧接口不返回 romalrc，音译行会缺失，其余照常
			root = getJson(ORIGIN + "/api/song/lyric?id=" + songId
				+ "&lv=-1&kv=-1&tv=-1", false).body();
		}
		return LyricParser.parseBest(string(object(root, "yrc"), "lyric"),
			string(object(root, "lrc"), "lyric"),
			string(object(root, "tlyric"), "lyric"),
			string(object(root, "romalrc"), "lyric"));
	}

	public LoginResult sendCaptcha(String phone, String countryCode)
		throws IOException, InterruptedException
	{
		if(phone == null || !phone.matches("\\d{5,15}"))
			return new LoginResult(false, "手机号格式不正确");
		Map<String, String> form = Map.of("cellphone", phone, "ctcode",
			normalizeCountryCode(countryCode));
		JsonResponse response = postForm(ORIGIN + "/api/sms/captcha/sent",
			form, false);
		return loginResult(response.body(), "验证码已发送", "验证码发送失败");
	}

	public LoginResult loginWithCaptcha(String phone, String captcha,
		String countryCode) throws IOException, InterruptedException
	{
		if(phone == null || !phone.matches("\\d{5,15}"))
			return new LoginResult(false, "手机号格式不正确");
		if(captcha == null || !captcha.matches("\\d{4,8}"))
			return new LoginResult(false, "验证码格式不正确");

		JsonObject data = new JsonObject();
		data.addProperty("phone", phone);
		data.addProperty("countrycode", normalizeCountryCode(countryCode));
		data.addProperty("captcha", captcha);
		data.addProperty("rememberLogin", "true");
		JsonResponse response = postWeapi("/weapi/login/cellphone", data);
		LoginResult result = loginResult(response.body(), "登录成功", "登录失败");
		if(result.success())
		{
			if(!acceptLoginCookie(response))
				return new LoginResult(false, "登录成功，但没有收到登录凭据");
			try
			{
				refreshUserProfile();
			}catch(IOException ignored)
			{}
			catch(InterruptedException e)
			{
				Thread.currentThread().interrupt();
			}
		}
		return result;
	}

	public LoginResult loginWithCookie(String rawCookie)
		throws IOException, InterruptedException
	{
		String imported = filterSessionCookie(rawCookie);
		if(imported.isBlank())
			return new LoginResult(false,
				"Cookie \u4e2d\u672a\u627e\u5230 MUSIC_U \u6216 MUSIC_A_T");
		String previous = cookie;
		NeteaseUserProfile previousProfile = userProfile;
		cookie = imported;
		if(!refreshUserProfile())
		{
			cookie = previous;
			userProfile = previousProfile;
			return new LoginResult(false, "\u7f51\u6613\u4e91 Cookie \u5df2\u5931\u6548");
		}
		saveCookie();
		return new LoginResult(true, "\u7f51\u6613\u4e91\u4f1a\u8bdd\u5df2\u4fdd\u5b58");
	}

	public QrLogin beginQrLogin() throws IOException, InterruptedException
	{
		JsonObject root = getJson(
			ORIGIN + "/api/login/qrcode/unikey?type=1&timestamp="
				+ System.currentTimeMillis(), false).body();
		String key = string(root, "unikey");
		if(number(root, "code") != 200 || key.isBlank())
			throw new IOException(message(root, "无法创建登录二维码"));
		return new QrLogin(key, ORIGIN + "/login?codekey="
			+ URLEncoder.encode(key, StandardCharsets.UTF_8));
	}

	public QrCheck checkQrLogin(String key)
		throws IOException, InterruptedException
	{
		String encoded = URLEncoder.encode(key, StandardCharsets.UTF_8);
		JsonResponse response = getJson(ORIGIN
			+ "/api/login/qrcode/client/login?type=1&key=" + encoded
			+ "&timestamp=" + System.currentTimeMillis(), false);
		int code = (int)number(response.body(), "code");
		return switch(code)
		{
			case 800 -> new QrCheck(QrStatus.EXPIRED, "二维码已过期");
			case 801 -> new QrCheck(QrStatus.WAITING, "等待扫码");
			case 802 -> new QrCheck(QrStatus.SCANNED, "已扫码，请在手机上确认");
			case 803 -> {
				if(!acceptLoginCookie(response))
					yield new QrCheck(QrStatus.ERROR,
						"登录成功，但没有收到登录凭据");
				try
				{
					refreshUserProfile();
				}catch(IOException ignored)
				{}
				catch(InterruptedException e)
				{
					Thread.currentThread().interrupt();
				}
				yield new QrCheck(QrStatus.SUCCESS, "登录成功");
			}
			default -> new QrCheck(QrStatus.ERROR,
				message(response.body(), "二维码登录失败"));
		};
	}

	public boolean refreshUserProfile()
		throws IOException, InterruptedException
	{
		if(cookie.isBlank())
			return false;
		JsonObject root = getJson(ORIGIN + "/api/nuser/account/get?timestamp="
			+ System.currentTimeMillis(), true).body();
		JsonObject profile = object(root, "profile");
		long userId = number(profile, "userId");
		if(userId <= 0)
		{
			userProfile = null;
			return false;
		}
		userProfile = new NeteaseUserProfile(userId,
			string(profile, "nickname"), string(profile, "avatarUrl"));
		return true;
	}

	public boolean isLoggedIn()
	{
		return !cookie.isBlank();
	}

	public NeteaseUserProfile getUserProfile()
	{
		return userProfile;
	}

	public void logout()
	{
		cookie = "";
		userProfile = null;
		COOKIE_MANAGER.getCookieStore().removeAll();
		try
		{
			Files.deleteIfExists(cookieFile());
		}catch(IOException ignored)
		{}
	}

	public Path download(SongResource resource, Path target)
		throws IOException, InterruptedException
	{
		Files.createDirectories(target.getParent());
		Path temporary = target.resolveSibling(target.getFileName() + ".part");
		Files.deleteIfExists(temporary);
		HttpRequest request = request(resource.uri(), false).build();
		HttpResponse<Path> response = CLIENT.send(request,
			HttpResponse.BodyHandlers.ofFile(temporary));
		if(response.statusCode() / 100 != 2)
		{
			Files.deleteIfExists(temporary);
			throw new IOException("歌曲下载失败：HTTP " + response.statusCode());
		}
		long size = Files.size(temporary);
		if(size == 0 || resource.size() > 0 && size < resource.size() / 2)
		{
			Files.deleteIfExists(temporary);
			throw new IOException("歌曲下载不完整");
		}
		try
		{
			return Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
				StandardCopyOption.ATOMIC_MOVE);
		}catch(IOException ignored)
		{
			return Files.move(temporary, target,
				StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private JsonResponse getJson(String url, boolean authenticated)
		throws IOException, InterruptedException
	{
		HttpResponse<String> response = CLIENT.send(
			request(URI.create(url), authenticated).build(),
			HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		return parseResponse(response, "网易云请求失败");
	}

	private JsonResponse postForm(String url, Map<String, String> values,
		boolean authenticated) throws IOException, InterruptedException
	{
		StringBuilder body = new StringBuilder();
		for(Map.Entry<String, String> entry : values.entrySet())
		{
			if(!body.isEmpty())
				body.append('&');
			body.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
				.append('=').append(URLEncoder.encode(entry.getValue(),
					StandardCharsets.UTF_8));
		}
		HttpRequest request = request(URI.create(url), authenticated)
			.header("Content-Type", "application/x-www-form-urlencoded")
			.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
		HttpResponse<String> response = CLIENT.send(request,
			HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		return parseResponse(response, "网易云请求失败");
	}

	private JsonResponse postWeapi(String path, JsonObject data)
		throws IOException, InterruptedException
	{
		try
		{
			String secret = randomSecret();
			String first = aes(data.toString(), WEAPI_NONCE);
			String params = aes(first, secret);
			String reversed = new StringBuilder(secret).reverse().toString();
			BigInteger value = new BigInteger(1,
				reversed.getBytes(StandardCharsets.UTF_8));
			BigInteger encrypted = value.modPow(new BigInteger("010001", 16),
				new BigInteger(WEAPI_MODULUS, 16));
			String encSecKey = String.format(Locale.ROOT, "%0256x", encrypted);
			Map<String, String> form = new LinkedHashMap<>();
			form.put("params", params);
			form.put("encSecKey", encSecKey);
			return postForm(ORIGIN + path + "?csrf_token=" + csrfToken(), form,
				true);
		}catch(GeneralSecurityException e)
		{
			throw new IOException("无法加密网易云登录请求", e);
		}
	}

	private JsonResponse parseResponse(HttpResponse<String> response,
		String failure) throws IOException
	{
		if(response.statusCode() / 100 != 2)
			throw new IOException(failure + "：HTTP " + response.statusCode());
		try
		{
			return new JsonResponse(
				JsonParser.parseString(response.body()).getAsJsonObject(),
				response.headers());
		}catch(RuntimeException e)
		{
			throw new IOException("网易云返回了无法解析的数据", e);
		}
	}

	private HttpRequest.Builder request(URI uri, boolean authenticated)
	{
		HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
			.timeout(Duration.ofSeconds(30)).header("User-Agent", USER_AGENT)
			.header("Referer", ORIGIN + "/").header("Origin", ORIGIN);
		if(authenticated && !cookie.isBlank())
			builder.header("Cookie", cookie);
		return builder;
	}

	private LoginResult loginResult(JsonObject root, String success,
		String fallback)
	{
		boolean ok = number(root, "code") == 200;
		return new LoginResult(ok, ok ? success : message(root, fallback));
	}

	private boolean acceptLoginCookie(JsonResponse response)
	{
		String raw = string(response.body(), "cookie");
		if(raw.isBlank())
			raw = String.join(";", response.headers().allValues("set-cookie"));
		String filtered = filterSessionCookie(raw);
		if(filtered.isBlank())
			return false;
		cookie = filtered;
		saveCookie();
		return true;
	}

	static String filterSessionCookie(String rawCookie)
	{
		if(rawCookie == null || rawCookie.isBlank())
			return "";
		Map<String, String> values = new LinkedHashMap<>();
		Matcher matcher = SESSION_COOKIE.matcher(rawCookie);
		while(matcher.find())
			values.put(matcher.group(1), matcher.group(2));
		return values.entrySet().stream()
			.map(entry -> entry.getKey() + "=" + entry.getValue())
			.reduce((left, right) -> left + "; " + right).orElse("");
	}

	private void loadCookie()
	{
		try
		{
			Path file = cookieFile();
			if(Files.isRegularFile(file))
				cookie = filterSessionCookie(
					Files.readString(file, StandardCharsets.UTF_8));
		}catch(Exception ignored)
		{}
	}

	private void saveCookie()
	{
		try
		{
			Path file = cookieFile();
			Files.createDirectories(file.getParent());
			Files.writeString(file, cookie, StandardCharsets.UTF_8);
		}catch(IOException ignored)
		{}
	}

	private Path cookieFile()
	{
		return WurstClient.INSTANCE.getWurstFolder().resolve("netease_cookie.txt");
	}

	private String csrfToken()
	{
		Matcher matcher = Pattern.compile("(?:^|;\\s*)__csrf=([^;]+)")
			.matcher(cookie);
		return matcher.find() ? matcher.group(1) : "";
	}

	private static String randomSecret()
	{
		byte[] random = new byte[8];
		RANDOM.nextBytes(random);
		StringBuilder result = new StringBuilder(16);
		for(byte value : random)
			result.append(String.format(Locale.ROOT, "%02x", value & 0xFF));
		return result.toString();
	}

	private static String aes(String value, String key)
		throws GeneralSecurityException
	{
		Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
		cipher.init(Cipher.ENCRYPT_MODE,
			new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES"),
			new IvParameterSpec(WEAPI_IV.getBytes(StandardCharsets.UTF_8)));
		return Base64.getEncoder().encodeToString(
			cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
	}

	static String normalizeCountryCode(String value)
	{
		return value == null || !value.matches("\\d{1,4}") ? "86" : value;
	}

	private static String message(JsonObject root, String fallback)
	{
		String message = string(root, "message");
		if(message.isBlank())
			message = string(root, "msg");
		return message.isBlank() ? fallback : message;
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

	/**
	 * 与 {@link #number(JsonObject, String)} 对称的宽松字符串读取：仅在确认是
	 * JSON 字符串/数字/布尔时才解析，对象或数组返回空串，避免 Gson 抛出
	 * {@link UnsupportedOperationException} 中断整页解析。
	 */
	static String string(JsonObject parent, String key)
	{
		JsonElement value = parent == null ? null : parent.get(key);
		if(value == null || value.isJsonNull())
			return "";
		if(value.isJsonPrimitive())
			return value.getAsString();
		return "";
	}

	/**
	 * 宽松数字读取：网易云对同一字段可能返回数字或数字字符串；无法解析时
	 * 返回 0 而不是抛出异常，使调用方可以走各自的空值分支。
	 */
	static long number(JsonObject parent, String key)
	{
		JsonElement value = parent == null ? null : parent.get(key);
		if(value == null || value.isJsonNull() || !value.isJsonPrimitive())
			return 0;
		try
		{
			return value.getAsLong();
		}catch(NumberFormatException | UnsupportedOperationException e)
		{
			return 0;
		}
	}

	/**
	 * 从数组中取出对象元素，跳过 null 或非对象元素。网易云在部分接口中用
	 * {@code null} 占位，直接 {@code getAsJsonObject()} 会抛
	 * {@link IllegalStateException} 并丢掉整页数据。
	 */
	static List<JsonObject> objects(JsonArray array)
	{
		if(array == null || array.isEmpty())
			return List.of();
		List<JsonObject> objects = new ArrayList<>(array.size());
		for(JsonElement element : array)
			if(element != null && element.isJsonObject())
				objects.add(element.getAsJsonObject());
		return objects;
	}

	private record JsonResponse(JsonObject body, HttpHeaders headers)
	{}

	public record SongResource(URI uri, long size, String type)
	{}

	public record LoginResult(boolean success, String message)
	{}

	public record QrLogin(String key, String loginUrl)
	{}

	public record QrCheck(QrStatus status, String message)
	{}

	public enum QrStatus
	{
		WAITING,
		SCANNED,
		SUCCESS,
		EXPIRED,
		ERROR
	}
}
