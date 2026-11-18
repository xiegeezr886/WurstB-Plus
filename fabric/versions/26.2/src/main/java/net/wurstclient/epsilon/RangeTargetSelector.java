/* Epsilon Backtrack range selection adaptation, GPL-3.0. */
package net.wurstclient.epsilon;

import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

public final class RangeTargetSelector {
  private RangeTargetSelector() {}

  /** Filter before choosing nearest, so an excluded close entity cannot hide a valid target. */
  public static <T> T nearest(
      Iterable<T> candidates,
      Predicate<T> allowed,
      ToDoubleFunction<T> distanceSquared,
      double rangeA,
      double rangeB) {
    double min = Math.min(rangeA, rangeB), max = Math.max(rangeA, rangeB);
    double minSquared = min * min, maxSquared = max * max;
    T nearest = null;
    double best = Double.POSITIVE_INFINITY;
    for (T candidate : candidates) {
      if (!allowed.test(candidate)) continue;
      double distance = distanceSquared.applyAsDouble(candidate);
      if (!Double.isFinite(distance)
          || distance < minSquared
          || distance > maxSquared
          || distance >= best) continue;
      best = distance;
      nearest = candidate;
    }
    return nearest;
  }
}
