package net.wurstclient.seed;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.OptionalLong;

/** Connection-scoped result storage; worker results must match the server's biome seed hash. */
public final class SeedSession {
  private long generation;
  private Long hash;
  private Long seed;

  public synchronized long begin(long hashedSeed) {
    generation++;
    hash = hashedSeed;
    seed = null;
    return generation;
  }

  public synchronized void clear() {
    generation++;
    hash = seed = null;
  }

  public synchronized void changeDimension(long hashedSeed) {
    generation++;
    if (hash == null || hash != hashedSeed) seed = null;
    hash = hashedSeed;
  }

  public synchronized long generation() {
    return generation;
  }

  public synchronized boolean accept(long token, long candidate) {
    if (token != generation || hash == null || obfuscate(candidate) != hash) return false;
    seed = candidate;
    return true;
  }

  public synchronized OptionalLong seed() {
    return seed == null ? OptionalLong.empty() : OptionalLong.of(seed);
  }

  public static long obfuscate(long seed) {
    try {
      byte[] input = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(seed).array();
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(input);
      return ByteBuffer.wrap(digest).order(ByteOrder.LITTLE_ENDIAN).getLong();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
