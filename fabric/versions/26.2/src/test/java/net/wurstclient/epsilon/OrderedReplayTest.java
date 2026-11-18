package net.wurstclient.epsilon;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

final class OrderedReplayTest {
  @Test
  void clientFlushCannotOvertakeScheduledNetworkFlush() {
    var scheduled = new ArrayDeque<Runnable>();
    var applied = new ArrayList<Integer>();
    boolean[] onClient = {false};
    var replay =
        new OrderedReplay<Integer>(
            task -> {
              if (onClient[0]) task.run();
              else scheduled.add(task);
            },
            applied::add);
    replay.submit(List.of(1, 2));
    onClient[0] = true;
    replay.submit(List.of(3, 4));
    assertTrue(applied.isEmpty());
    assertEquals(1, scheduled.size());
    scheduled.remove().run();
    assertEquals(List.of(1, 2, 3, 4), applied);
  }

  @Test
  void subsequentBatchCanDrainInlineAfterPreviousBatchFinished() {
    var applied = new ArrayList<Integer>();
    var replay = new OrderedReplay<Integer>(Runnable::run, applied::add);
    replay.submit(List.of(1));
    replay.submit(List.of());
    replay.submit(List.of(2));
    assertEquals(List.of(1, 2), applied);
  }

  @Test
  void handlerFailureDropsRemainingStaleBatchAndAllowsRecovery() {
    var applied = new ArrayList<Integer>();
    var replay =
        new OrderedReplay<Integer>(
            Runnable::run,
            value -> {
              if (value == 1) throw new IllegalStateException();
              applied.add(value);
            });
    assertThrows(IllegalStateException.class, () -> replay.submit(List.of(1, 2)));
    replay.submit(List.of(3));
    assertEquals(List.of(3), applied);
  }

  @Test
  void reentrantReleaseStaysBehindAlreadyReleasedPackets() {
    var applied = new ArrayList<Integer>();
    @SuppressWarnings("unchecked")
    OrderedReplay<Integer>[] holder = new OrderedReplay[1];
    holder[0] =
        new OrderedReplay<>(
            Runnable::run,
            value -> {
              applied.add(value);
              if (value == 1) holder[0].submit(List.of(3));
            });
    holder[0].submit(List.of(1, 2));
    assertEquals(List.of(1, 2, 3), applied);
  }
}
