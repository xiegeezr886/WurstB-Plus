/* Adapted directly from OpenOpal (GPL-3.0); see META-INF/licenses/openopal/LICENSE. */
package net.wurstclient.hacks.scaffold;
import net.wurstclient.util.Rotation;
import net.wurstclient.opal.rotation.RotationHelper;
import net.wurstclient.opal.rotation.HypixelRotationModel;

/** Host setting adapter; all mouse state lives in the original OpenOpal handler. */
public final class ScaffoldRotationHandler {
    private static ScaffoldRotationHandler owner;
    public void rotate(Rotation target, ScaffoldRotationModel model, float speed) {
        rotate(target, model, speed, 1.2D, 0.12D);
    }
    public void rotate(Rotation target, ScaffoldRotationModel model, float speed,
        double drift, double jitter) {
        owner = this;
        RotationHelper.getHandler().rotate(target, model.create(speed, drift, jitter));
    }
    public void rotateHypixel(Rotation target, boolean diagonalYaw, int airTicks) {
        owner = this;
        RotationHelper.getHandler().rotate(target, new HypixelRotationModel(diagonalYaw, airTicks));
    }
    public void reverse() { if(owner == this) RotationHelper.getHandler().reverse(); }
    public void tickReverse() { /* RotationMouseHandler.onPreTick owns this phase. */ }
    public void reset() { if(owner == this) { RotationHelper.getHandler().reset(); owner = null; } }
    public boolean isActive() { return owner == this && RotationHelper.getHandler().isActive(); }
    public Rotation getRotation() { return RotationHelper.getPlayerRotation(); }
}
