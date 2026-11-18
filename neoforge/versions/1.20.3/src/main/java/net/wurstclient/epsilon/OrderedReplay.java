/* Copyright (c) 2026 WurstB+ Plus contributors. SPDX-License-Identifier: AGPL-3.0-only */
package net.wurstclient.epsilon;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** One ordered drain, even when the executor runs client-thread submissions inline. */
public final class OrderedReplay<T> {
  private final ArrayDeque<T> released = new ArrayDeque<>();
  private final Executor executor;
  private final Consumer<T> handler;
  private boolean scheduled;

  public OrderedReplay(Executor executor, Consumer<T> handler) {
    this.executor = executor;
    this.handler = handler;
  }

  public void submit(Collection<T> batch) {
    synchronized (this) {
      released.addAll(batch);
      if (scheduled || released.isEmpty()) return;
      scheduled = true;
    }
    try {
      executor.execute(this::drain);
    } catch (RuntimeException failure) {
      synchronized (this) {
        scheduled = false;
        released.clear();
      }
      throw failure;
    }
  }

  private void drain() {
    try {
      while (true) {
        T item;
        synchronized (this) {
          item = released.pollFirst();
          if (item == null) {
            scheduled = false;
            return;
          }
        }
        handler.accept(item);
      }
    } catch (RuntimeException | Error failure) {
      synchronized (this) {
        scheduled = false;
        released.clear();
      }
      throw failure;
    }
  }
}
