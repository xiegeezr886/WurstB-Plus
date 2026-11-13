/* Adapted directly from OpenOpal (GPL-3.0); see META-INF/licenses/openopal/LICENSE. */
package net.wurstclient.opal;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/** CPSProperty's original timer state; settings are supplied by the host UI. */
public final class CPSProperty {
    private final IntSupplier cps;
    private final BooleanSupplier modernDelay;
    private long nextClick;
    public CPSProperty(IntSupplier cps, BooleanSupplier modernDelay) {
        this.cps = cps;
        this.modernDelay = modernDelay;
    }
    public boolean isModernDelay() { return modernDelay.getAsBoolean(); }
    public int getCPS() { return cps.getAsInt(); }
    public int getClickDelay() { return 1000 / getCPS(); }
    public void resetClick() { nextClick = getClickDelay(); }
    public long getNextClick() { return nextClick; }
}
