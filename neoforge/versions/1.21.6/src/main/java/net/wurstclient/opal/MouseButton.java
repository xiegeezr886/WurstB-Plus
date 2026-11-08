/* Adapted directly from OpenOpal (GPL-3.0); see META-INF/licenses/openopal/LICENSE. */
package net.wurstclient.opal;

import net.minecraft.client.KeyMapping;
import net.wurstclient.mixin.OpenOpalKeyAccessor;

public final class MouseButton {

    private final KeyMapping keyBinding;

    public MouseButton(KeyMapping keyBinding) {
        this.keyBinding = keyBinding;
    }

    private boolean pressed;
    private boolean disabled;
    private int holdTicks;

    public void setDisabled() {
        this.pressed = false;
        this.holdTicks = 0;
        this.disabled = true;
    }

    public void setPressed() {
        this.setPressed(true, 0);
    }

    public void setPressed(boolean pressed, int holdTicks) {
        this.pressed = pressed;
        this.holdTicks = holdTicks;
    }

    public boolean wasPressed() {
        if (this.disabled) {
            return false;
        }
        boolean pressed = false;
        if (this.pressed) {
            pressed = true;
            this.pressed = false;
        }
        return this.keyBinding.consumeClick() || pressed;
    }

    public boolean isPressed() {
        if (this.disabled) {
            return false;
        }
        return this.keyBinding.isDown() || this.pressed || this.holdTicks > 0;
    }

    public boolean isForcePressed() {
        return this.pressed;
    }

    public void tick() {
        if (this.holdTicks > 0) {
            this.holdTicks--;
        }
        if (this.keyBinding.isDown() || this.holdTicks == 0) {
            this.showSwings = true;
        }
        this.pressed = false;
        this.disabled = false;
    }

    public int getHoldTicks() {
        return holdTicks;
    }

    public boolean isDisabled() {
        return disabled;
    }

    private boolean showSwings = true;

    public boolean isShowSwings() {
        return showSwings || ((OpenOpalKeyAccessor) this.keyBinding).opal$getClickCount() > 0;
    }

    public void setShowSwings(boolean showSwings) {
        this.showSwings = showSwings;
    }

    public KeyMapping getKeyMapping() {
        return keyBinding;
    }
}
