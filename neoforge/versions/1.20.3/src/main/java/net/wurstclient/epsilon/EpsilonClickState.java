/* Epsilon AutoClicker.ButtonState adaptation, GPL-3.0. */
package net.wurstclient.epsilon;

import java.util.Random;

public final class EpsilonClickState {
  public record Config(
      int min,
      int max,
      boolean burst,
      int burstChance,
      int duration,
      double multiplier,
      int decay,
      double curve,
      boolean doubleClick,
      int doubleChance,
      int doubleDelay,
      boolean miss,
      int missChance) {}

  private final Random random;
  private long next, burstEnd, decayEnd, doubleAt;
  private boolean bursting, decaying, pendingDouble;

  public EpsilonClickState(Random random) {
    this.random = random;
  }

  public int tick(long now, Config c) {
    if (!c.burst) {
      bursting = false;
      decaying = false;
    } else if (bursting && now >= burstEnd) {
      bursting = false;
      decaying = true;
    } else if (decaying && now >= decayEnd) decaying = false;
    else if (!bursting && !decaying && random.nextInt(100) < c.burstChance) {
      bursting = true;
      burstEnd = now + c.duration;
      decayEnd = burstEnd + c.decay;
    }
    if (!c.doubleClick) pendingDouble = false;
    int clicks = 0;
    if (now >= next) {
      if (!c.miss || random.nextInt(100) >= c.missChance) {
        clicks++;
        if (c.doubleClick && !pendingDouble && random.nextInt(100) < c.doubleChance) {
          pendingDouble = true;
          doubleAt = now + c.doubleDelay;
        }
      }
      double cps =
          Math.max(1, Math.min(c.min, c.max)) + random.nextDouble() * Math.abs(c.max - c.min);
      double multiplier =
          bursting
              ? c.multiplier
              : decaying
                  ? 1
                      + (c.multiplier - 1)
                          * Math.pow(
                              Math.max(0, 1 - (double) (now - burstEnd) / Math.max(1, c.decay)),
                              c.curve)
                  : 1;
      next = now + Math.max(1, (long) (1000 / (cps * multiplier)));
    }
    if (pendingDouble && now >= doubleAt) {
      pendingDouble = false;
      clicks++;
    }
    return clicks;
  }

  public void reset() {
    next = burstEnd = decayEnd = doubleAt = 0;
    bursting = decaying = pendingDouble = false;
  }
}
