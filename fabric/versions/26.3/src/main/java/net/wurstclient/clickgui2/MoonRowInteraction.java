package net.wurstclient.clickgui2;

/** Module toggle, context expansion and binding actions with no arrow buttons. */
final class MoonRowInteraction
{
    enum Action { NONE, TOGGLE, EXPAND, BIND }

    private MoonRowInteraction() {}

    static Action action(double localX, float width, int button,
        boolean expandable, boolean bindable, boolean hasPrimaryAction)
    {
        if(localX < 0 || localX >= width) return Action.NONE;
        if(button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_MIDDLE) return bindable ? Action.BIND : Action.NONE;
        if(button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) return expandable ? Action.EXPAND : Action.NONE;
        if(button != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) return Action.NONE;
        if(expandable && !hasPrimaryAction)
            return Action.EXPAND;
        return hasPrimaryAction ? Action.TOGGLE : Action.NONE;
    }

    static String keyLabel(String key)
    {
        if(key == null) return "";
        return key.replace("key.keyboard.", "").replace("key.mouse.", "M")
            .replace("left.", "L ").replace("right.", "R ")
            .replace("control", "ctrl").replace("backspace", "back")
            .replace("page.up", "pgup").replace("page.down", "pgdn")
            .replace("caps.lock", "caps").replace("num.lock", "num")
            .toUpperCase(java.util.Locale.ROOT);
    }
}
