/* Adapted directly from OpenOpal (GPL-3.0); see META-INF/licenses/openopal/LICENSE. */
package net.wurstclient.opal;


import static net.wurstclient.WurstClient.MC;

public final class SwingDelay {

    private final Stopwatch swingStopwatch = new Stopwatch();

    public static void reset() {
        instance.swingStopwatch.reset();
    }

    private static SwingDelay instance = new SwingDelay();

    public static void setInstance() {
        instance = new SwingDelay();
    }

    public static boolean isSwingAvailable(final CPSProperty cpsProperty, final boolean reset) {
        if (cpsProperty.isModernDelay() && MC.player != null) {
            return MC.player.getAttackStrengthScale(0.5F) >= 1.0F;
        }
        if (instance.swingStopwatch.hasTimeElapsed(cpsProperty.getNextClick())) {
            if (reset) {
                cpsProperty.resetClick();
                reset();
            }
            return true;
        }
        return false;
    }

    public static boolean isSwingAvailable(final CPSProperty cpsProperty) {
        return isSwingAvailable(cpsProperty, true);
    }
}
