/* Adapted directly from OpenOpal (GPL-3.0); see META-INF/licenses/openopal/LICENSE. */
package net.wurstclient.opal.rotation;

import net.wurstclient.util.Rotation;

import static net.wurstclient.WurstClient.MC;

public final class ClientRotationHandler {

    public ClientRotationHandler() {
    }

    private Rotation rotation;
    private boolean ticking;

    public void onMouseUpdate(MouseUpdateEvent event) {
        if (MC.player != null && !event.isUnlockCursorRun()) {
            if (this.rotation == null) {
                this.initializeRotation();
            }

            final double multiplier = event.getSensitivityMultiplier();
            final double cursorX = event.getDeltaX() * multiplier;
            final double cursorY = event.getDeltaY() * multiplier;

            int yMultiplier = 1;
            if (MC.options.invertYMouse().get()) {
                yMultiplier = -1;
            }

            final float deltaYaw = (float) cursorX * 0.15F;
            final float deltaPitch = (float) (cursorY * (double) yMultiplier) * 0.15F;
            final float yaw = this.rotation.yaw() + deltaYaw;
            final float pitch = this.rotation.pitch() + deltaPitch;
            this.rotation = new Rotation(yaw, net.minecraft.util.Mth.clamp(pitch % 360.0F, -90.0F, 90.0F));
        }
        this.ticking = true;
    }

    private void initializeRotation() {
        this.rotation = RotationHelper.getPlayerRotation();
        this.lastRenderYaw = MC.player.yBobO;
        this.renderYaw = MC.player.yBob;
        this.lastRenderPitch = MC.player.xBobO;
        this.renderPitch = MC.player.xBob;
    }

    private float lastRenderYaw, renderYaw;
    private float lastRenderPitch, renderPitch;

    public void tickCamera() {
        if (this.rotation != null) {
            this.lastRenderYaw = this.renderYaw;
            this.lastRenderPitch = this.renderPitch;
            this.renderPitch = this.renderPitch + (this.rotation.pitch() - this.renderPitch) * 0.5F;
            this.renderYaw = this.renderYaw + (this.rotation.yaw() - this.renderYaw) * 0.5F;
        }
    }

    public void onPostMouseUpdate() {
        this.ticking = false;
    }

    public void onRotationSet() {
        if (!this.ticking) {
            this.rotation = null;
        }
    }

    public float getYawOr(float fallback) {
        return this.rotation == null ? fallback : this.rotation.yaw();
    }

    public float getPitchOr(float fallback) {
        return this.rotation == null ? fallback : this.rotation.pitch();
    }

    public float getLastRenderYawOr(float fallback) {
        return this.rotation == null ? fallback : this.lastRenderYaw;
    }

    public float getLastRenderPitchOr(float fallback) {
        return this.rotation == null ? fallback : this.lastRenderPitch;
    }

    public float getRenderYawOr(float fallback) {
        return this.rotation == null ? fallback : this.renderYaw;
    }

    public float getRenderPitchOr(float fallback) {
        return this.rotation == null ? fallback : this.renderPitch;
    }

    public Rotation getRotation() {
        return rotation;
    }

    public void setRotation(Rotation rotation) {
        this.rotation = rotation;
    }

    public void setTicking(boolean ticking) {
        this.ticking = ticking;
    }
}
