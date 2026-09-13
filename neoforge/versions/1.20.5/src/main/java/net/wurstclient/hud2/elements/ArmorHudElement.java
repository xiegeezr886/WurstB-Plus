package net.wurstclient.hud2.elements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.wurstclient.WurstClient;
import net.wurstclient.hud2.HudElement;
import net.wurstclient.hud2.HudLayout.HudElementConfig;

public final class ArmorHudElement extends HudElement
{
	public ArmorHudElement()
	{
		super("armor", "Armor");
	}

	@Override
	public int getWidth()
	{
		return 72;
	}

	@Override
	public int getHeight()
	{
		return 18;
	}

	@Override
	public void render(GuiGraphics graphics, int x, int y, float partialTicks)
	{
		if(WurstClient.MC.player == null)
			return;
		List<ItemStack> armor = new ArrayList<>();
		WurstClient.MC.player.getArmorSlots().forEach(armor::add);
		Collections.reverse(armor);
		for(int index = 0; index < armor.size(); index++)
		{
			ItemStack stack = armor.get(index);
			int itemX = x + index * 18;
			graphics.renderItem(stack, itemX, y);
			graphics.renderItemDecorations(WurstClient.MC.font, stack, itemX, y);
		}
	}

	@Override
	public HudElementConfig getDefaultLayout()
	{
		return new HudElementConfig(HudElementConfig.HORIZONTAL_LEFT,
			HudElementConfig.VERTICAL_BOTTOM, 3, 22);
	}
}
