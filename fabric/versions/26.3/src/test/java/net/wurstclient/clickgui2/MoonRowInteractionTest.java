package net.wurstclient.clickgui2;

import static net.wurstclient.clickgui2.MoonRowInteraction.Action.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class MoonRowInteractionTest
{
    @Test void wholeModuleRowTogglesWithoutAnInvisibleExpandRegion()
    {
        assertEquals(TOGGLE, MoonRowInteraction.action(133.99, 152, com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, true, true, true));
        assertEquals(TOGGLE, MoonRowInteraction.action(134, 152, com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, true, true, true));
        assertEquals(TOGGLE, MoonRowInteraction.action(151.99, 152, com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, true, true, true));
        assertEquals(NONE, MoonRowInteraction.action(152, 152, com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, true, true, true));
        assertEquals(NONE, MoonRowInteraction.action(-1, 152, com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, true, true, true));
    }

    @Test void rightClickAndMiddleClickKeepTheirSeparateActions()
    {
        assertEquals(EXPAND, MoonRowInteraction.action(40, 152, com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT, true, true, true));
        assertEquals(BIND, MoonRowInteraction.action(145, 152, com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_MIDDLE, true, true, true));
        assertEquals(NONE, MoonRowInteraction.action(145, 152, com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_MIDDLE, true, false, true));
        assertEquals(NONE, MoonRowInteraction.action(145, 152, 4, true, true, true));
    }

    @Test void settingsOnlyFeaturesAndModulesWithoutSettingsRemainUsable()
    {
        assertEquals(EXPAND, MoonRowInteraction.action(40, 152, com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, true, false, false));
        assertEquals(TOGGLE, MoonRowInteraction.action(145, 152, com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, false, false, true));
        assertEquals(NONE, MoonRowInteraction.action(145, 152, com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT, false, false, true));
        assertEquals(NONE, MoonRowInteraction.action(40, 152, com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, false, false, false));
    }

    @Test void compactLabelsKeepModifierAndMouseIdentity()
    {
        assertEquals("R SHIFT", MoonRowInteraction.keyLabel("key.keyboard.right.shift"));
        assertEquals("L CTRL", MoonRowInteraction.keyLabel("key.keyboard.left.control"));
        assertEquals("R CTRL", MoonRowInteraction.keyLabel("key.keyboard.right.control"));
        assertEquals("PGDN", MoonRowInteraction.keyLabel("key.keyboard.page.down"));
        assertEquals("M4", MoonRowInteraction.keyLabel("key.mouse.4"));
        assertEquals("", MoonRowInteraction.keyLabel(null));
    }
}
