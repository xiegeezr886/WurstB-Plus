/*
 * Ported directly from OpenOpal: utility/misc/time/Stopwatch.java
 * Original source: GPL-3.0; see META-INF/licenses/openopal/LICENSE.
 * Minecraft names and host integration adapted for WurstB+ Plus.
 */
package net.wurstclient.opal;

public final class Stopwatch {

    private long lastMs;

    public Stopwatch(long lastMs) {
        this.lastMs = lastMs;
    }

    public Stopwatch() {
        this.reset();
    }

    public void reset() {
        lastMs = System.currentTimeMillis();
    }

    public boolean hasTimeElapsed(final long time, final boolean reset) {
        if (getTime() > time) {
            if (reset) reset();
            return true;
        }
        return false;
    }

    public boolean hasTimeElapsed(final long time) {
        return hasTimeElapsed(time, false);
    }

    public long getTime() {
        return System.currentTimeMillis() - lastMs;
    }

    public void setTime(final long time) {
        lastMs = time;
    }

    public long remainingUntil(final long time) {
        long rem = time - getTime();
        return Math.max(0L, rem);
    }

    public boolean isWithin(final long time, final long lookaheadMs) {
        if (time <= 0L) return false;
        long now = getTime();
        long threshold = Math.max(0L, time - Math.max(0L, lookaheadMs));
        return now >= threshold;
    }
}
