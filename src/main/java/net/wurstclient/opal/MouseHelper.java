/* Adapted directly from OpenOpal (GPL-3.0); see META-INF/licenses/openopal/LICENSE. */
package net.wurstclient.opal;

import net.minecraft.client.KeyMapping;

import java.util.HashMap;
import java.util.Map;

import static net.wurstclient.WurstClient.MC;

public final class MouseHelper {

    private final Map<KeyMapping, MouseButton> mouseButtonMap = new HashMap<>();
    private final MouseButton leftButton, rightButton;

    private MouseHelper() {
        this.leftButton = this.register(new MouseButton(MC.options.keyAttack));
        this.rightButton = this.register(new MouseButton(MC.options.keyUse));
    }

    private <T extends MouseButton> T register(T button) {
        this.mouseButtonMap.put(button.getKeyMapping(), button);
        return button;
    }

    public void tick() {
        this.leftButton.tick();
        this.rightButton.tick();
    }

    public static MouseButton getButtonFromBinding(KeyMapping binding) {
        return getInstance().mouseButtonMap.get(binding);
    }

    public static MouseButton getLeftButton() {
        return getInstance().leftButton;
    }

    public static MouseButton getRightButton() {
        return getInstance().rightButton;
    }

    private static MouseHelper instance;

    public static void setInstance() {
        instance = new MouseHelper();
    }

    public static MouseHelper getInstance() {
        if (instance == null) setInstance();
        return instance;
    }
}
