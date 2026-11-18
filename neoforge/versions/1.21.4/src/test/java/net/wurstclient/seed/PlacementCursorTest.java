package net.wurstclient.seed;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PlacementCursorTest {
  @Test
  void preservesVanillaForEachRandomConsumptionAcrossPauses() {
    Random vanilla = new Random(42), incremental = new Random(42);
    List<Long> expected = new ArrayList<>(), actual = new ArrayList<>();
    IntStream.range(0, 7)
        .boxed()
        .flatMap(i -> IntStream.range(0, 3).map(j -> vanilla.nextInt(256)).boxed())
        .forEach(p -> expected.add(p * 100000L + vanilla.nextInt(100000)));
    List<Function<Integer, Iterator<Integer>>> modifiers =
        List.of(
            p -> IntStream.range(0, 7).boxed().iterator(),
            p -> IntStream.range(0, 3).map(i -> incremental.nextInt(256)).boxed().iterator());
    PlacementCursor<Integer> cursor = new PlacementCursor<>(0, modifiers);
    Integer p;
    while ((p = cursor.nextOrNull()) != null) actual.add(p * 100000L + incremental.nextInt(100000));
    assertEquals(expected, actual);
    assertNull(cursor.nextOrNull());
  }

  @Test
  void skipsFilteredBranchesWithoutSkippingSiblings() {
    PlacementCursor<Integer> cursor =
        new PlacementCursor<>(
            0,
            List.of(
                p -> List.of(1, 2, 3, 4).iterator(),
                p -> (p % 2 == 0 ? List.of(p) : List.<Integer>of()).iterator()));
    assertEquals(2, cursor.nextOrNull());
    assertEquals(4, cursor.nextOrNull());
    assertNull(cursor.nextOrNull());
  }
}
