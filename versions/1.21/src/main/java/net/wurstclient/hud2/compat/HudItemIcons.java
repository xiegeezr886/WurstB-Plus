package net.wurstclient.hud2.compat;

import net.minecraft.world.item.ItemStack;
import net.wurstclient.hud2.nanovg.NanoVGContext;

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
