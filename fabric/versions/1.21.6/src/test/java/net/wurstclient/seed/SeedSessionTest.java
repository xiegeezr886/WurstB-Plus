package net.wurstclient.seed;

import static org.junit.jupiter.api.Assertions.*;

import com.google.common.hash.Hashing;
import org.junit.jupiter.api.Test;

class SeedSessionTest {
  @Test
  void matchesVanillaHashForSignedSeeds() {
    for (long seed : new long[] {0, -1, 123456789, Long.MIN_VALUE, Long.MAX_VALUE})
      assertEquals(Hashing.sha256().hashLong(seed).asLong(), SeedSession.obfuscate(seed));
  }

  @Test
  void rejectsWrongAndStaleResults() {
    var s = new SeedSession();
    long old = s.begin(SeedSession.obfuscate(42));
    assertFalse(s.accept(old, 43));
    s.begin(SeedSession.obfuscate(42));
    assertFalse(s.accept(old, 42));
    assertTrue(s.seed().isEmpty());
    assertTrue(s.accept(s.generation(), 42));
    s.clear();
    assertFalse(s.accept(s.generation(), 42));
    assertTrue(s.seed().isEmpty());
  }

  @Test
  void keepsVerifiedSeedAcrossDimensionsButNotConnections() {
    var s = new SeedSession();
    long hash = SeedSession.obfuscate(-100);
    s.accept(s.begin(hash), -100);
    s.changeDimension(hash);
    assertEquals(-100, s.seed().orElseThrow());
    s.changeDimension(SeedSession.obfuscate(11));
    assertTrue(s.seed().isEmpty());
    s.accept(s.generation(), 11);
    s.begin(SeedSession.obfuscate(11));
    assertTrue(s.seed().isEmpty());
  }
}
