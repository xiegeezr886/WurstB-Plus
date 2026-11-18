package net.wurstclient.epsilon;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.junit.jupiter.api.Test;

final class EpsilonClickStateTest {
  private static EpsilonClickState.Config config(boolean burst, boolean doubles, boolean miss) {
    return new EpsilonClickState.Config(
        10, 10, burst, 100, 200, 2, 200, 1, doubles, 100, 40, miss, 100);
  }

  @Test
  void normalTimingAndNoCatchUp() {
    var state = new EpsilonClickState(new Random(1));
    var c = config(false, false, false);
    assertEquals(1, state.tick(0, c));
    assertEquals(0, state.tick(99, c));
    assertEquals(1, state.tick(100, c));
    assertEquals(1, state.tick(10000, c));
    assertEquals(0, state.tick(10000, c));
  }

  @Test
  void delayedDoubleClickIsIndependentOfNextRegularClick() {
    var state = new EpsilonClickState(new Random(1));
    var c = config(false, true, false);
    assertEquals(1, state.tick(0, c));
    assertEquals(0, state.tick(39, c));
    assertEquals(1, state.tick(40, c));
    assertEquals(0, state.tick(41, c));
    assertEquals(1, state.tick(100, c));
  }

  @Test
  void resetDropsPendingDoubleClick() {
    var state = new EpsilonClickState(new Random(1));
    state.tick(0, config(false, true, false));
    state.reset();
    assertEquals(1, state.tick(1000, config(false, true, false)));
    assertEquals(0, state.tick(1001, config(false, true, false)));
  }

  @Test
  void missDoesNotQueueADoubleClick() {
    var state = new EpsilonClickState(new Random(1));
    var c = config(false, true, true);
    for (int t = 0; t < 1000; t += 20) assertEquals(0, state.tick(t, c));
  }

  @Test
  void burstEndsAndDecayReturnsToNormal() {
    var state = new EpsilonClickState(new Random(1));
    var c = config(true, false, false);
    assertEquals(1, state.tick(0, c));
    assertEquals(1, state.tick(50, c));
    assertEquals(0, state.tick(99, c));
    assertEquals(1, state.tick(200, c));
    assertEquals(1, state.tick(300, c));
    assertEquals(0, state.tick(350, c));
    assertEquals(1, state.tick(400, c));
    assertEquals(0, state.tick(450, config(false, false, false)));
  }

  @Test
  void disablingDoubleClickCancelsQueuedClick() {
    var state = new EpsilonClickState(new Random(1));
    state.tick(0, config(false, true, false));
    assertEquals(0, state.tick(40, config(false, false, false)));
  }
}
