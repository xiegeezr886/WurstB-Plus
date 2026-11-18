package net.wurstclient.epsilon;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

final class RangeTargetSelectorTest {
  private record Target(String name, double distance, boolean allowed) {}

  private Target select(List<Target> candidates, double min, double max) {
    return RangeTargetSelector.nearest(
        candidates, Target::allowed, t -> t.distance() * t.distance(), min, max);
  }

  @Test
  void tooCloseTargetDoesNotMaskTargetInsideRange() {
    var close = new Target("too close", 1, true);
    var valid = new Target("valid", 3, true);
    assertSame(valid, select(List.of(close, valid), 2.5, 5));
  }

  @Test
  void excludedCategoryDoesNotMaskAllowedTarget() {
    var excluded = new Target("excluded", 3, false);
    var allowed = new Target("allowed", 4, true);
    assertSame(allowed, select(List.of(excluded, allowed), 2.5, 5));
  }

  @Test
  void swappedBoundsAndExactBoundariesAreSupported() {
    var min = new Target("min", 2.5, true);
    var max = new Target("max", 5, true);
    assertSame(min, select(List.of(max, min), 5, 2.5));
    assertSame(max, select(List.of(max), 5, 2.5));
  }

  @Test
  void nearestIsIndependentOfIterationOrder() {
    var far = new Target("far", 4, true);
    var near = new Target("near", 3, true);
    assertSame(near, select(List.of(far, near), 2.5, 5));
    assertSame(near, select(List.of(near, far), 2.5, 5));
  }

  @Test
  void noValidTargetReturnsNull() {
    assertNull(select(List.of(), 2.5, 5));
    assertNull(
        select(
            List.of(
                new Target("too far", 6, true),
                new Target("invalid", Double.NaN, true),
                new Target("infinity", Double.POSITIVE_INFINITY, true)),
            2.5,
            5));
  }
}
