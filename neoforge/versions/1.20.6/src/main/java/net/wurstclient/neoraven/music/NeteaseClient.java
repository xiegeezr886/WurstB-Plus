/* Music player adapted from NeoRaven (GPL-3.0). See META-INF/licenses/neoraven/MUSIC-NOTICE.txt. */
package net.wurstclient.neoraven.music;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The NetEase Cloud Music endpoints the player needs: search, playlists, stream URLs, lyrics, the
 * signed-in profile and the QR login handshake.
 *
 * <p>Everything is synchronous and blocking; {@link MusicManager} calls it from its I/O pool. The
 * request shape (weapi paths and bodies) matches Melodify's {@code NeteaseApi}, which in turn
 * mirrors the web client — the server rejects anything else.
 */
public final class NeteaseClient {

  private static final Gson GSON = new Gson();

  private NeteaseClient() {}

  // ------------------------------------------------------------------ //
  // Search / discovery
  // ------------------------------------------------------------------ //

  public static List<Song> search(String keyword, int limit) throws Exception {
    JsonObject body = new JsonObject();
    body.addProperty("s", keyword);
    body.addProperty("type", 1);
    body.addProperty("limit", limit);
    body.addProperty("offset", 0);
    JsonObject root = weapi("search/get", body);
    List<Song> songs = new ArrayList<Song>();
    JsonObject result = object(root, "result");
    if (result == null) {
      return songs;
    }
    JsonArray array = array(result, "songs");
    if (array == null) {
      return songs;
    }
    for (JsonElement element : array) {
      Song song = parseSong(element);
      if (song != null) {
        songs.add(song);
      }
    }
    fillMissingCovers(songs);
    return songs;
  }

  public static List<Playlist> personalized(int limit) throws Exception {
    JsonObject body = new JsonObject();
    body.addProperty("limit", limit);
    body.addProperty("offset", 0);
    body.addProperty("total", true);
    body.addProperty("n", 1000);
    JsonObject root = weapi("personalized/playlist", body);
    List<Playlist> playlists = new ArrayList<Playlist>();
    JsonArray array = array(root, "result");
    if (array == null) {
      return playlists;
    }
    for (JsonElement element : array) {
      if (playlists.size() >= limit) {
        break;
      }
      Playlist playlist = parsePlaylist(element);
      if (playlist != null) {
        playlists.add(playlist);
      }
    }
    return playlists;
  }

  public static List<Playlist> userPlaylists(long uid, int limit) throws Exception {
    JsonObject body = new JsonObject();
    body.addProperty("uid", uid);
    body.addProperty("limit", limit);
    body.addProperty("offset", 0);
    body.addProperty("includeVideo", true);
    JsonObject root = weapi("user/playlist", body);
    List<Playlist> playlists = new ArrayList<Playlist>();
    JsonArray array = array(root, "playlist");
    if (array == null) {
      return playlists;
    }
    for (JsonElement element : array) {
      if (playlists.size() >= limit) {
        break;
      }
      Playlist playlist = parsePlaylist(element);
      if (playlist != null) {
        playlists.add(playlist);
      }
    }
    return playlists;
  }

  /** Loads a playlist's tracks; falls back to the id list when the response omits them. */
  public static List<Song> playlistTracks(long playlistId, int limit) throws Exception {
    JsonObject body = new JsonObject();
    body.addProperty("id", playlistId);
    body.addProperty("n", 100000);
    body.addProperty("s", 8);
    JsonObject root = weapi("v6/playlist/detail", body);
    JsonObject playlist = object(root, "playlist");
    List<Song> songs = new ArrayList<Song>();
    if (playlist == null) {
      return songs;
    }
    JsonArray tracks = array(playlist, "tracks");
    if (tracks != null && tracks.size() > 0) {
      for (JsonElement element : tracks) {
        if (songs.size() >= limit) {
          break;
        }
        Song song = parseSong(element);
        if (song != null) {
          songs.add(song);
        }
      }
      return songs;
    }
    JsonArray ids = array(playlist, "trackIds");
    if (ids == null) {
      return songs;
    }
    List<Long> list = new ArrayList<Long>();
    for (JsonElement element : ids) {
      if (list.size() >= limit) {
        break;
      }
      JsonObject entry = element.isJsonObject() ? element.getAsJsonObject() : null;
      if (entry != null && entry.has("id") && !entry.get("id").isJsonNull()) {
        list.add(entry.get("id").getAsLong());
      }
    }
    return songDetails(list);
  }

  public static List<Song> songDetails(List<Long> ids) throws Exception {
    List<Song> songs = new ArrayList<Song>();
    if (ids == null || ids.isEmpty()) {
      return songs;
    }
    for (int start = 0; start < ids.size(); start += 500) {
      int end = Math.min(start + 500, ids.size());
      StringBuilder c = new StringBuilder("[");
      for (int i = start; i < end; i++) {
        if (i > start) {
          c.append(',');
        }
        c.append("{\"id\":").append(ids.get(i).longValue()).append('}');
      }
      c.append(']');
      JsonObject body = new JsonObject();
      body.addProperty("c", c.toString());
      JsonObject root = weapi("v3/song/detail", body);
      JsonArray array = array(root, "songs");
      if (array == null) {
        continue;
      }
      for (JsonElement element : array) {
        Song song = parseSong(element);
        if (song != null) {
          songs.add(song);
        }
      }
    }
    return songs;
  }

  // ------------------------------------------------------------------ //
  // Playback
  // ------------------------------------------------------------------ //

  /** Result of a stream URL lookup: the signed URL and the server-side track length. */
  public static final class Stream {
    public final String url;
    public final long durationMs;

    Stream(String url, long durationMs) {
      this.url = url == null ? "" : url;
      this.durationMs = durationMs;
    }

    public boolean valid() {
      return !url.isEmpty();
    }
  }

  /**
   * Resolves a playable URL. {@code level} maps to the API's quality names: {@code standard},
   * {@code higher}, {@code exhigh}, {@code lossless}. The port always asks for {@code mp3} because
   * the bundled JLayer decoder only understands MPEG audio.
   */
  public static Stream streamUrl(long songId, String level) throws Exception {
    Stream first = streamOnce(songId, level == null ? "exhigh" : level);
    if (first.valid() || "standard".equals(level)) {
      return first;
    }
    // Try standard quality; some tracks still require a subscription or have no license.
    return streamOnce(songId, "standard");
  }

  private static Stream streamOnce(long songId, String level) throws Exception {
    JsonObject body = new JsonObject();
    body.addProperty("ids", "[" + songId + "]");
    body.addProperty("level", level);
    body.addProperty("encodeType", "mp3");
    JsonObject root = weapi("song/enhance/player/url/v1", body);
    JsonArray data = array(root, "data");
    if (data == null || data.size() == 0) {
      return new Stream("", 0L);
    }
    JsonObject first = data.get(0).isJsonObject() ? data.get(0).getAsJsonObject() : null;
    if (first == null) {
      return new Stream("", 0L);
    }
    String url = string(first, "url");
    long time =
        first.has("time") && !first.get("time").isJsonNull() ? first.get("time").getAsLong() : 0L;
    return new Stream(url, time);
  }

  /** Raw lyric payload: word-level {@code yrc} plus the plain, translated and romanised LRCs. */
  public static final class LyricPayload {
    public final String yrc;
    public final String lrc;
    public final String translation;
    public final String romanisation;

    LyricPayload(String yrc, String lrc, String translation, String romanisation) {
      this.yrc = yrc;
      this.lrc = lrc;
      this.translation = translation;
      this.romanisation = romanisation;
    }
  }

  public static LyricPayload lyrics(long songId) throws Exception {
    JsonObject body = new JsonObject();
    body.addProperty("id", songId);
    body.addProperty("lv", 0);
    body.addProperty("kv", 0);
    body.addProperty("tv", 0);
    body.addProperty("rv", 0);
    body.addProperty("yv", 0);
    JsonObject root = weapi("song/lyric/v1", body);
    return new LyricPayload(
        lyricField(root, "yrc"),
        lyricField(root, "lrc"),
        lyricField(root, "tlyric"),
        lyricField(root, "romalrc"));
  }

  // ------------------------------------------------------------------ //
  // Account / login
  // ------------------------------------------------------------------ //

  public static Profile account() throws Exception {
    if (!NeteaseHttp.loggedIn()) {
      return null;
    }
    JsonObject root = weapi("w/nuser/account/get", new JsonObject());
    JsonObject profile = object(root, "profile");
    if (profile == null) {
      return null;
    }
    long userId =
        profile.has("userId") && !profile.get("userId").isJsonNull()
            ? profile.get("userId").getAsLong()
            : 0L;
    int vip =
        profile.has("vipType") && !profile.get("vipType").isJsonNull()
            ? profile.get("vipType").getAsInt()
            : 0;
    return new Profile(userId, string(profile, "nickname"), string(profile, "avatarUrl"), vip);
  }

  /** Creates the QR payload; the returned key is what the phone scans. */
  public static String qrKey() throws Exception {
    JsonObject body = new JsonObject();
    body.addProperty("type", 1);
    JsonObject root = weapi("login/qrcode/unikey", body);
    String key = string(root, "unikey");
    return key.isEmpty() ? null : key;
  }

  public static String qrContent(String unikey) {
    return "https://music.163.com/login?codekey=" + unikey;
  }

  /**
   * Polls the QR state.
   *
   * @return 800 expired, 801 waiting, 802 scanned (awaiting confirmation), 803 logged in, -1 on a
   *     transport error
   */
  public static int qrCheck(String unikey) throws Exception {
    long version = NeteaseHttp.sessionVersion();
    JsonObject body = new JsonObject();
    body.addProperty("key", unikey);
    body.addProperty("type", 1);
    JsonObject root = weapi("login/qrcode/client/login", body);
    int code =
        root.has("code") && !root.get("code").isJsonNull() ? root.get("code").getAsInt() : -1;
    if (code == 803) {
      // The successful poll returns the session in the JSON body ("cookie"), not through
      // Set-Cookie. Melodify's original only read "code", so it never actually logged in.
      String cookie = string(root, "cookie");
      if (!cookie.isEmpty()) {
        NeteaseHttp.storeAllIfCurrent(cookie, version);
      }
    }
    return code;
  }

  public static void logout() {
    try {
      JsonObject root = weapi("logout", new JsonObject());
      if (root.has("code") && !root.get("code").isJsonNull()) {
        // 200 means the server dropped the session; the local jar is cleared either way.
        root.get("code").getAsInt();
      }
    } catch (Exception ignored) {
    }
    NeteaseHttp.logout();
  }

  // ------------------------------------------------------------------ //
  // Plumbing
  // ------------------------------------------------------------------ //

  private static JsonObject weapi(String path, JsonObject body) throws Exception {
    String json = GSON.toJson(body);
    String csrf = NeteaseHttp.csrf();
    NeteaseHttp.Response response = NeteaseHttp.weapi(path + "?csrf_token=" + percent(csrf), json);
    if (!response.ok()) {
      throw new IllegalStateException("HTTP " + response.status + " from " + path);
    }
    if (response.body.isEmpty()) {
      return new JsonObject();
    }
    JsonElement parsed = new JsonParser().parse(response.body);
    if (!parsed.isJsonObject()) throw new IllegalStateException("网易云返回了无效响应");
    JsonObject result = parsed.getAsJsonObject();
    int code = result.has("code") ? result.get("code").getAsInt() : 200;
    if (code != 200 && (code < 800 || code > 803))
      throw new IllegalStateException("网易云请求失败 (" + code + ")");
    return result;
  }

  private static String percent(String value) {
    try {
      return java.net.URLEncoder.encode(value == null ? "" : value, "UTF-8");
    } catch (Exception e) {
      return "";
    }
  }

  private static Song parseSong(JsonElement element) {
    if (element == null || !element.isJsonObject()) {
      return null;
    }
    JsonObject song = element.getAsJsonObject();
    try {
      long id = song.get("id").getAsLong();
      String name = string(song, "name");
      String artist = "";
      JsonArray artists = array(song, "ar");
      if (artists == null) {
        artists = array(song, "artists");
      }
      if (artists != null) {
        artist = joinArtists(artists);
      }
      String album = "";
      String cover = "";
      JsonObject al = object(song, "al");
      if (al == null) {
        al = object(song, "album");
      }
      if (al != null) {
        album = string(al, "name");
        cover = string(al, "picUrl");
      }
      long duration = 0L;
      if (song.has("dt") && !song.get("dt").isJsonNull()) {
        duration = song.get("dt").getAsLong();
      } else if (song.has("duration") && !song.get("duration").isJsonNull()) {
        duration = song.get("duration").getAsLong();
      }
      return new Song(id, name, artist, album, cover, duration);
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Search results come back in the "album/artists/duration" shape, which carries no cover art; the
   * detail endpoint does. For the thumbnails in the result list the missing covers are filled with
   * one batched detail call instead of one call per row.
   */
  private static void fillMissingCovers(List<Song> songs) {
    List<Long> missing = new ArrayList<Long>();
    for (Song song : songs) {
      if (song.coverUrl.isEmpty() && missing.size() < 50) {
        missing.add(Long.valueOf(song.id));
      }
    }
    if (missing.isEmpty()) {
      return;
    }
    try {
      List<Song> detailed = songDetails(missing);
      Map<Long, String> covers = new HashMap<Long, String>();
      for (Song song : detailed) {
        if (!song.coverUrl.isEmpty()) {
          covers.put(Long.valueOf(song.id), song.coverUrl);
        }
      }
      for (int i = 0; i < songs.size(); i++) {
        Song song = songs.get(i);
        String cover = covers.get(Long.valueOf(song.id));
        if (cover != null && song.coverUrl.isEmpty()) {
          songs.set(
              i, new Song(song.id, song.name, song.artist, song.album, cover, song.durationMs));
        }
      }
    } catch (Exception ignored) {
    }
  }

  private static Playlist parsePlaylist(JsonElement element) {
    if (element == null || !element.isJsonObject()) {
      return null;
    }
    JsonObject playlist = element.getAsJsonObject();
    try {
      long id = playlist.get("id").getAsLong();
      String name = string(playlist, "name");
      int count = 0;
      if (playlist.has("trackCount") && !playlist.get("trackCount").isJsonNull()) {
        count = playlist.get("trackCount").getAsInt();
      }
      String cover = string(playlist, "picUrl");
      if (cover.isEmpty()) {
        cover = string(playlist, "coverImgUrl");
      }
      return new Playlist(id, name, count, cover);
    } catch (Exception e) {
      return null;
    }
  }

  private static String joinArtists(JsonArray artists) {
    StringBuilder out = new StringBuilder();
    for (JsonElement element : artists) {
      if (!element.isJsonObject()) {
        continue;
      }
      String name = string(element.getAsJsonObject(), "name");
      if (name.isEmpty()) {
        continue;
      }
      if (out.length() > 0) {
        out.append(" / ");
      }
      out.append(name);
    }
    return out.toString();
  }

  private static String lyricField(JsonObject root, String key) {
    JsonObject node = object(root, key);
    return node == null ? null : emptyToNull(string(node, "lyric"));
  }

  private static String emptyToNull(String value) {
    return value == null || value.isEmpty() ? null : value;
  }

  private static String string(JsonObject object, String key) {
    if (object == null || !object.has(key)) {
      return "";
    }
    JsonElement value = object.get(key);
    if (value == null || value.isJsonNull()) {
      return "";
    }
    try {
      return value.getAsString();
    } catch (Exception e) {
      return "";
    }
  }

  private static JsonObject object(JsonObject parent, String key) {
    if (parent == null || !parent.has(key)) {
      return null;
    }
    JsonElement value = parent.get(key);
    return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
  }

  private static JsonArray array(JsonObject parent, String key) {
    if (parent == null || !parent.has(key)) {
      return null;
    }
    JsonElement value = parent.get(key);
    return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
  }

  static List<String> emptyList() {
    return Collections.emptyList();
  }
}
