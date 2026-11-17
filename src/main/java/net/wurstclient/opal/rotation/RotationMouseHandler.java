/* Adapted directly from OpenOpal (GPL-3.0); see META-INF/licenses/openopal/LICENSE. */
package net.wurstclient.opal.rotation;

import net.minecraft.util.Mth;
import net.wurstclient.util.Rotation;

import static net.wurstclient.WurstClient.MC;

public final class RotationMouseHandler {

    public RotationMouseHandler() {
    }

    private IRotationModel rotationModel;
    private Rotation targetRotation;
    private boolean active, forward;
    private Rotation originalRotation;

    public void onMouseUpdate(MouseUpdateEvent event) {
        if (this.tickRotation == null || this.targetRotation == null || MC.player == null || !this.active) {
            this.ticked = false;
            return;
        }

        final double sensitivityMultiplier = event.getSensitivityMultiplier();

        if (this.originalRotation != null) {
            final double deltaX = event.getDeltaX() * sensitivityMultiplier;
            double deltaY = event.getDeltaY() * sensitivityMultiplier;

            final double m = 0.15D;

            if (MC.options.invertYMouse().get()) {
                deltaY *= -1.0;
            }
            this.originalRotation = new Rotation(
                    this.originalRotation.yaw() + (float) (deltaX * m),
                    Mth.clamp(this.originalRotation.pitch() + (float) (deltaY * m), -90.0F, 90.0F)
            );
        }

        if (!this.forward) {
            this.resetToClient();
            if (this.targetRotation == null) {
                this.ticked = false;
                return;
            }
        }

        float tickDelta;
        if (this.ticked) { // Fixes rotation tick interpolation since tickDelta otherwise never reaches 1
            tickDelta = 1.F;
            this.ticked = false;
        } else {
            tickDelta = RotationHelper.getPartialTick();
        }
        final Rotation tickedRotation = this.rotationModel.tick(this.tickRotation, this.targetRotation, tickDelta);

        final double deltaYaw = tickedRotation.yaw() - MC.player.getYRot();
        final double cursorDeltaX = RotationHelper.getCursorDelta(deltaYaw, sensitivityMultiplier);
        final double deltaPitch = tickedRotation.pitch() - MC.player.getXRot();
        double cursorDeltaY = RotationHelper.getCursorDelta(deltaPitch, sensitivityMultiplier);
        if (MC.options.invertYMouse().get()) {
            cursorDeltaY *= -1.D;
        }

        event.setDeltaX(cursorDeltaX);
        event.setDeltaY(cursorDeltaY);
        event.setHandled();

        if (!this.forward && net.wurstclient.hacks.scaffold.ScaffoldUtils.getRotationDifference(tickedRotation, this.targetRotation) == 0.D) {
            this.rotationModel = null;
            this.targetRotation = null;
            this.active = false;
        }
    }

    private Rotation tickRotation;
    private boolean ticked, unlockCursor;

    public void onPreTick() {
        if (MC.player == null) {
            return;
        }

        this.forceTick();
        this.reverse();

        this.setTickRotation(RotationHelper.getPlayerRotation());
        this.unlockCursor = false;
    }

    public boolean isForcingTick() { return this.ticked; }

    public boolean isUnlockCursor() {
        return this.unlockCursor && this.ticked;
    }

    public void setTickRotation(Rotation tickRotation) {
        this.tickRotation = tickRotation;
    }

    public void reverse() {
        if (this.forward) {
            this.resetToClient();
            this.forward = false;
        }
    }

    public void forceTick() {
        if (this.active) {
            this.ticked = true;
            RotationHelper.forceMouseTick();
            this.ticked = false;
        }
    }

    private void resetToClient() {
        final ClientRotationHandler clientHandler = RotationHelper.getClientHandler();
        this.targetRotation = clientHandler.getRotation();
        if(this.targetRotation == null) this.targetRotation = RotationHelper.getPlayerRotation();
    }

    public void discard() {
        this.active = false;
        this.forward = false;
        this.targetRotation = null;
        this.rotationModel = null;
        this.originalRotation = null;
        this.tickRotation = null;
        this.unlockCursor = false;
    }

    public void reset() {
        if (this.active && this.originalRotation != null && MC.player != null) {
            MC.player.setYRot(this.originalRotation.yaw());
            MC.player.setXRot(this.originalRotation.pitch());
            MC.player.setYBodyRot(this.originalRotation.yaw());
            MC.player.setYHeadRot(this.originalRotation.yaw());
        }
        this.active = false;
        this.forward = false;
        this.targetRotation = null;
        this.rotationModel = null;
        this.unlockCursor = false;
        this.originalRotation = null;
    }

    public void rotate(Rotation targetRotation, IRotationModel rotationModel) {
        if (!this.active && MC.player != null) {
            this.originalRotation = new Rotation(MC.player.getYRot(), MC.player.getXRot());
        }
        if(this.tickRotation == null) this.tickRotation = RotationHelper.getPlayerRotation();
        this.targetRotation = targetRotation;
        this.rotationModel = rotationModel;
        this.forward = true;
        this.active = true;
        this.forceTick();
    }

    public void unlockCursor() {
        this.unlockCursor = true;
    }

    public Rotation getTargetRotation() {
        return targetRotation;
    }

    public IRotationModel getRotationModel() {
        return rotationModel;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isForward() {
        return forward;
    }
}
