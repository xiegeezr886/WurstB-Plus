package net.wurstclient.seed;

import static org.junit.jupiter.api.Assertions.*;
import com.seedfinding.mccore.rand.seed.WorldSeed;
import kaptainwutax.seedcrackerX.cracker.HashedSeedData;
import org.junit.jupiter.api.Test;

class NativeSeedHashTest {
  @Test
  void nativeSolverHashMatchesSessionValidationIncludingSignedSeeds() {
    for (long seed : new long[]{0, 8675309, -1, Long.MIN_VALUE, Long.MAX_VALUE}) {
      long hash = WorldSeed.toHash(seed);
      assertEquals(SeedSession.obfuscate(seed), hash);
      var data = new HashedSeedData(hash);
      assertTrue(data.test(seed, null));
      assertFalse(data.test(seed ^ 1, null));
      var session = new SeedSession();
      long generation = session.begin(hash);
      assertTrue(session.accept(generation, seed));
      session.clear();
      assertFalse(session.accept(generation, seed));
    }
  }

  @Test
  void nativeSolverRecoversWorldSeedFromStructureSeedAndBiomeHash() {
    for (long seed : new long[]{8675309, -9876543212345L}) {
      var results = WorldSeed.fromHash(WorldSeed.toStructureSeed(seed), WorldSeed.toHash(seed));
      assertEquals(java.util.List.of(seed), results);
    }
  }
}
