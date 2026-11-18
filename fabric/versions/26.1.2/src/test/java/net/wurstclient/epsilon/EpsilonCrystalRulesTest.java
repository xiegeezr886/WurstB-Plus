package net.wurstclient.epsilon;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class EpsilonCrystalRulesTest {
  private static EpsilonCrystalRules rules(boolean override) {
    return new EpsilonCrystalRules(4, 8, 1.2, true, override, true, 8, 2, true, 5, 1.5);
  }

  private final EpsilonCrystalRules.Target healthy = new EpsilonCrystalRules.Target(20, 100, 100);

  @Test
  void selfKillAndMaximumSelfDamageAreRejected() {
    assertFalse(rules(false).accepts(30, 8, 8, false, healthy));
    assertFalse(rules(false).accepts(30, 9, 20, false, healthy));
    assertTrue(rules(false).accepts(30, 9, 20, true, healthy));
  }

  @Test
  void zeroSelfDamageIsAcceptedButZeroTargetDamageIsNot() {
    assertTrue(rules(false).accepts(5, 0, 20, false, healthy));
    assertFalse(rules(false).accepts(0, 0, 20, false, healthy));
  }

  @Test
  void damageRatioRejectsPoorTrades() {
    assertFalse(rules(false).accepts(5, 5, 20, false, healthy));
    assertTrue(rules(false).accepts(6, 5, 20, false, healthy));
  }

  @Test
  void nonFiniteScoresNeverBypassSafety() {
    assertFalse(rules(true).accepts(Double.NaN, 0, 20, false, healthy));
    assertFalse(rules(true).accepts(30, Double.POSITIVE_INFINITY, 20, false, healthy));
  }

  @Test
  void lethalForceAndArmorBreakerLowerMinimum() {
    assertEquals(1.5, rules(false).minimum(healthy, 10));
    assertEquals(2, rules(false).minimum(new EpsilonCrystalRules.Target(8, 100, 100), 2));
    assertEquals(1.5, rules(false).minimum(new EpsilonCrystalRules.Target(20, 4, 100), 2));
    assertEquals(4, rules(false).minimum(healthy, 2));
  }

  @Test
  void overrideIsExplicit() {
    assertFalse(rules(false).accepts(30, 19, 20, false, healthy));
    assertTrue(rules(true).accepts(30, 19, 20, false, healthy));
  }
}
