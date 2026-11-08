/* Adapted directly from OpenOpal (GPL-3.0); see META-INF/licenses/openopal/LICENSE. */
package net.wurstclient.opal.rotation;

public final class MouseUpdateEvent {
    private double deltaX, deltaY;
    private final double sensitivityMultiplier;
    private final boolean unlockCursorRun;
    private boolean handled;

    public MouseUpdateEvent(double deltaX, double deltaY, double sensitivityMultiplier, boolean unlockCursorRun) {
        this.sensitivityMultiplier = sensitivityMultiplier;
        this.deltaY = deltaY;
        this.deltaX = deltaX;
        this.unlockCursorRun = unlockCursorRun;
    }

    public double getDeltaX() {
        return deltaX;
    }

    public void setDeltaX(double deltaX) {
        this.deltaX = deltaX;
    }

    public double getDeltaY() {
        return deltaY;
    }

    public void setDeltaY(double deltaY) {
        this.deltaY = deltaY;
    }

    public double getSensitivityMultiplier() {
        return sensitivityMultiplier;
    }

    public boolean isHandled() {
        return handled;
    }

    public void setHandled() {
        this.handled = true;
    }

    public boolean isUnlockCursorRun() {
        return unlockCursorRun;
    }
}
