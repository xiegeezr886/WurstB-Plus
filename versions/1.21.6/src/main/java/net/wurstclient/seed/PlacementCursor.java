package net.wurstclient.seed;

import java.util.*;
import java.util.function.Function;

/** Resumable depth-first flatMap. Does not prefetch placements past the current vein. */
public final class PlacementCursor<T> {
  private final List<Function<T, Iterator<T>>> modifiers;
  private final List<Iterator<T>> stack = new ArrayList<>();

  public PlacementCursor(T origin, List<Function<T, Iterator<T>>> modifiers) {
    this.modifiers = List.copyOf(modifiers);
    stack.add(Collections.singleton(origin).iterator());
  }

  public T nextOrNull() {
    while (!stack.isEmpty()) {
      int depth = stack.size() - 1;
      Iterator<T> iterator = stack.get(depth);
      if (!iterator.hasNext()) {
        stack.remove(depth);
        continue;
      }
      T pos = iterator.next();
      if (depth == modifiers.size()) return pos;
      stack.add(modifiers.get(depth).apply(pos));
    }
    return null;
  }
}
