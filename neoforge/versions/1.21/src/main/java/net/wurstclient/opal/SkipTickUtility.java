/*
 * Ported directly from OpenOpal: utility/player/SkipTickUtility.java
 * Original source: GPL-3.0; see META-INF/licenses/openopal/LICENSE.
 * Minecraft names and host integration adapted for WurstB+ Plus.
 */
package net.wurstclient.opal;

public final class SkipTickUtility {

    private static int skipTicks;

    private SkipTickUtility() {
    }

    public static void addSkipTicks(final int ticks) {
        if (ticks <= 0) {
            return;
        }
        skipTicks += ticks;
    }

    public static boolean consumeSkipTick() {
        if (skipTicks > 0) {
            skipTicks--;
            return true;
        }
        return false;
    }

    public static void reset() {
        skipTicks = 0;
    }
}
