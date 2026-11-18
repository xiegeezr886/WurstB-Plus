/* Music player adapted from NeoRaven (GPL-3.0). See META-INF/licenses/neoraven/MUSIC-NOTICE.txt. */
package net.wurstclient.neoraven.music;

import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * NetEase Cloud Music "weapi" request encryption.
 *
 * <p>Ported verbatim from Melodify's {@code NeteaseCrypto}: the payload is AES-CBC encrypted twice
 * (first with the well-known preset key, then with a random 16-character secret), and the secret
 * itself is RSA-encrypted so only the server can unwrap it. The secret is reversed before the RSA
 * step, which is what the web client does.
 */
public final class NeteaseCrypto {

  private static final Charset UTF8 = Charset.forName("UTF-8");
  private static final String PRESET_KEY = "0CoJUm6Qyw8W8jud";
  private static final String IV = "0102030405060708";
  private static final String RSA_EXPONENT = "010001";
  private static final String RSA_MODULUS =
      "00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7";
  private static final char[] BASE62 =
      "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
  private static final SecureRandom RANDOM = new SecureRandom();

  private NeteaseCrypto() {}

  /** Builds the {@code params} / {@code encSecKey} pair for one weapi request. */
  public static Map<String, String> weapi(String json) throws Exception {
    String secret = randomString(16);
    String once = aesCbc(json, PRESET_KEY);
    Map<String, String> form = new LinkedHashMap<String, String>();
    form.put("params", aesCbc(once, secret));
    form.put("encSecKey", rsa(secret));
    return form;
  }

  private static String aesCbc(String data, String key) throws Exception {
    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
    cipher.init(
        Cipher.ENCRYPT_MODE,
        new SecretKeySpec(key.getBytes(UTF8), "AES"),
        new IvParameterSpec(IV.getBytes(UTF8)));
    return Base64.getEncoder().encodeToString(cipher.doFinal(data.getBytes(UTF8)));
  }

  private static String rsa(String secret) {
    String reversed = new StringBuilder(secret).reverse().toString();
    BigInteger message = new BigInteger(1, reversed.getBytes(UTF8));
    BigInteger exponent = new BigInteger(RSA_EXPONENT, 16);
    BigInteger modulus = new BigInteger(RSA_MODULUS, 16);
    String hex = message.modPow(exponent, modulus).toString(16);
    StringBuilder out = new StringBuilder(256);
    for (int i = hex.length(); i < 256; i++) {
      out.append('0');
    }
    return out.append(hex).toString();
  }

  private static String randomString(int length) {
    char[] out = new char[length];
    for (int i = 0; i < length; i++) {
      out[i] = BASE62[RANDOM.nextInt(BASE62.length)];
    }
    return new String(out);
  }

  /** Lower-case MD5 hex, used by the eapi id digests. */
  public static String md5(String text) {
    try {
      MessageDigest digest = MessageDigest.getInstance("MD5");
      byte[] hash = digest.digest(text.getBytes(UTF8));
      StringBuilder out = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
        out.append(Character.forDigit((b >> 4) & 0xF, 16));
        out.append(Character.forDigit(b & 0xF, 16));
      }
      return out.toString();
    } catch (Exception e) {
      return "";
    }
  }
}
