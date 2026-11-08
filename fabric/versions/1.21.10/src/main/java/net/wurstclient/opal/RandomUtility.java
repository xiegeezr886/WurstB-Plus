/*
 * Ported directly from OpenOpal: utility/misc/math/RandomUtility.java
 * Original source: GPL-3.0; see META-INF/licenses/openopal/LICENSE.
 * Minecraft names and host integration adapted for WurstB+ Plus.
 */
package net.wurstclient.opal;

import java.util.Random;

public final class RandomUtility {
    private RandomUtility() {
    }

    public static final Random RANDOM = new Random();

    public static int getRandomInt(final int min, final int max) {
        return min == max ? min : min + RANDOM.nextInt(max - min);
    }

    public static boolean chance(final int percentChance) {
        return RANDOM.nextInt(100) < percentChance;
    }

    public static int getRandomInt(final int bound) {
        return RANDOM.nextInt(bound);
    }

    public static double getRandomDouble(final double min, final double max) {
        return getRandomDouble(min, max, RANDOM.nextDouble());
    }

    public static float getRandomFloat(final float min, final float max) {
        return getRandomFloat(min, max, RANDOM.nextFloat());
    }

    public static float getRandomFloat(final float min, final float max, final float rand) {
        return min == max ? min : min + (max - min) * rand;
    }

    public static double getRandomDouble(final double min, final double max, final double rand) {
        return min == max ? min : min + (max - min) * rand;
    }

    public static double getJoinRandomDouble(final double min, final double max) {
        return getRandomDouble(min, max, JOIN_RANDOM);
    }

    public static double JOIN_RANDOM = RandomUtility.RANDOM.nextDouble();

    public static void resetJoinRandom() {
        JOIN_RANDOM = RandomUtility.RANDOM.nextDouble();
    }
}
