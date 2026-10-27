package net.wurstclient.hud2.compat;

import net.minecraft.world.item.ItemStack;
import net.wurstclient.hud2.nanovg.NanoVGContext;

/**
 * 26.3 item-icon bridge. RenderPearl does not permit the 1.20.1-style offscreen
 * item render plus GL readback, so icons are disabled for now. This keeps the
 * HUD modules compiling and rendering their text/cards; a deferred vanilla item
 * overlay can be added later.
 */
public final class HudItemIcons
{
	private HudItemIcons() {}

	public static int get(ItemStack stack)
	{
		return 0;
	}

	public static void capturePending(NanoVGContext nvg, int budget) {}

	public static void clear() {}
}
