/* Music player adapted from NeoRaven (GPL-3.0). See META-INF/licenses/neoraven/MUSIC-NOTICE.txt. */
package net.wurstclient.neoraven.music;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal HTTP + cookie plumbing for the NetEase web API on Java 8.
 *
 * <p>Melodify used {@code java.net.http.HttpClient}; that class only exists from Java 11 and the
 * 1.8.9 client has to keep running on Java 8, so every request goes through {@link
 * HttpURLConnection}. The cookie jar is shared and thread safe: the QR login flow writes into it
 * from the polling thread while the UI reads the csrf token from the render thread.
 */
public final class NeteaseHttp {

  private static final Charset UTF8 = Charset.forName("UTF-8");
  private static final String USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
          + "Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0";
  private static final Map<String, String> COOKIES = new ConcurrentHashMap<String, String>();
  private static volatile String anonymousCookie = "";
  private static long sessionVersion;

  static synchronized long sessionVersion() {
    return sessionVersion;
  }

  static synchronized void invalidatePendingCookies() {
    sessionVersion++;
  }

  static synchronized void storeAllIfCurrent(String raw, long version) {
    if (version == sessionVersion) storeAll(raw);
  }

  private NeteaseHttp() {}

  public static final class Response {
    public final int status;
    public final String body;

    Response(int status, String body) {
      this.status = status;
      this.body = body == null ? "" : body;
    }

    public boolean ok() {
      return status >= 200 && status < 300;
    }
  }

  /** POSTs a url-encoded form to the weapi endpoint for {@code path}. */
  public static Response weapi(String path, String json) throws Exception {
    Map<String, String> form = NeteaseCrypto.weapi(json);
    String suffix = path.startsWith("/") ? path : "/" + path;
    return post("https://music.163.com/weapi" + suffix, encode(form));
  }

  /** POSTs a url-encoded form to an arbitrary endpoint (the plaintext API family). */
  public static Response post(String url, String form) throws Exception {
    return request("POST", url, form);
  }

  public static Response get(String url) throws Exception {
    return request("GET", url, null);
  }

  private static Response request(String method, String url, String form) throws Exception {
    long version = sessionVersion();
    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
    try {
      connection.setConnectTimeout(8000);
      connection.setReadTimeout(12000);
      connection.setInstanceFollowRedirects(false);
      connection.setRequestMethod(method);
      connection.setRequestProperty("User-Agent", USER_AGENT);
      connection.setRequestProperty("Referer", "https://music.163.com");
      connection.setRequestProperty("Accept", "*/*");
      connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
      String cookie = cookieHeader();
      if (!cookie.isEmpty()) {
        connection.setRequestProperty("Cookie", cookie);
      }
      if (form != null) {
        byte[] bytes = form.getBytes(UTF8);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setRequestProperty("Content-Length", Integer.toString(bytes.length));
        OutputStream out = connection.getOutputStream();
        try {
          out.write(bytes);
        } finally {
          out.close();
        }
      }
      int status = connection.getResponseCode();
      synchronized (NeteaseHttp.class) {
        if (version == sessionVersion) captureCookies(connection.getHeaderFields());
      }
      InputStream in;
      try {
        in = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
      } catch (Exception e) {
        in = connection.getErrorStream();
      }
      String body = read(in);
      connection.disconnect();
      return new Response(status, body);
    } finally {
      connection.disconnect();
    }
  }

  private static String read(InputStream in) {
    if (in == null) {
      return "";
    }
    try {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      byte[] chunk = new byte[8192];
      int read;
      while ((read = in.read(chunk)) > 0) {
        if (buffer.size() + read > 8 * 1024 * 1024)
          throw new java.io.IOException("Response too large");
        buffer.write(chunk, 0, read);
      }
      return new String(buffer.toByteArray(), UTF8);
    } catch (Exception e) {
      return "";
    } finally {
      try {
        in.close();
      } catch (Exception ignored) {
      }
    }
  }

  private static void captureCookies(Map<String, java.util.List<String>> headers) {
    if (headers == null) {
      return;
    }
    for (Map.Entry<String, java.util.List<String>> entry : headers.entrySet()) {
      String name = entry.getKey();
      if (name == null || !name.equalsIgnoreCase("Set-Cookie") || entry.getValue() == null) {
        continue;
      }
      for (String header : entry.getValue()) {
        store(header);
      }
    }
  }

  /** Parses one {@code Set-Cookie} header into the jar. */
  public static void store(String header) {
    if (header == null) {
      return;
    }
    int semicolon = header.indexOf(';');
    String pair = semicolon >= 0 ? header.substring(0, semicolon) : header;
    int equals = pair.indexOf('=');
    if (equals <= 0) {
      return;
    }
    String name = pair.substring(0, equals).trim();
    String value = pair.substring(equals + 1).trim();
    if (name.isEmpty()) {
      return;
    }
    if (value.isEmpty() || value.equals("deleted")) {
      COOKIES.remove(name);
      return;
    }
    COOKIES.put(name, value);
  }

  /** Feeds a raw {@code name=value; name2=value2} cookie string into the jar. */
  public static void storeAll(String raw) {
    if (raw == null || raw.isEmpty()) {
      return;
    }
    for (String part : raw.split(";")) {
      String piece = part.trim();
      int equals = piece.indexOf('=');
      if (equals > 0) {
        COOKIES.put(piece.substring(0, equals).trim(), piece.substring(equals + 1).trim());
      }
    }
  }

  public static boolean hasCookie(String name) {
    return COOKIES.containsKey(name);
  }

  /** Serialises the jar into a {@code Cookie} header, adding the fields the web client sends. */
  public static String cookieHeader() {
    StringBuilder out = new StringBuilder();
    if (!anonymousCookie.isEmpty()) {
      out.append(anonymousCookie);
    }
    for (Map.Entry<String, String> entry : COOKIES.entrySet()) {
      if (out.length() > 0) {
        out.append("; ");
      }
      out.append(entry.getKey()).append('=').append(entry.getValue());
    }
    if (out.length() > 0) {
      out.append("; ");
    }
    out.append("os=pc; appver=8.9.70; channel=netease;");
    return out.toString();
  }

  /** The csrf token is the {@code __csrf} cookie (or an empty string when logged out). */
  public static String csrf() {
    String value = COOKIES.get("__csrf");
    return value == null ? "" : value;
  }

  public static String musicU() {
    return COOKIES.get("MUSIC_U");
  }

  public static boolean loggedIn() {
    return musicU() != null && !musicU().isEmpty();
  }

  public static void setAnonymousCookie(String value) {
    anonymousCookie = value == null ? "" : value;
  }

  /** Drops the login cookies but keeps the anonymous device cookie. */
  public static synchronized void logout() {
    sessionVersion++;
    COOKIES.remove("MUSIC_U");
    COOKIES.remove("__csrf");
    COOKIES.remove("NMTID");
    COOKIES.remove("MUSIC_A_T");
    COOKIES.remove("MUSIC_R_T");
  }

  public static synchronized void clear() {
    sessionVersion++;
    COOKIES.clear();
    anonymousCookie = "";
  }

  static String encode(Map<String, String> form) {
    StringBuilder out = new StringBuilder();
    for (Map.Entry<String, String> entry : form.entrySet()) {
      if (out.length() > 0) {
        out.append('&');
      }
      out.append(percent(entry.getKey())).append('=').append(percent(entry.getValue()));
    }
    return out.toString();
  }

  private static String percent(String value) {
    try {
      return URLEncoder.encode(value == null ? "" : value, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      return "";
    }
  }
}
