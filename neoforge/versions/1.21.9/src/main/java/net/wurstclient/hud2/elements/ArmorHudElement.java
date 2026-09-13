package net.wurstclient.hud2.elements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.wurstclient.util.render.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.wurstclient.WurstClient;
import net.wurstclient.util.RenderUtils;
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
	public void render(GuiGraphicsExtractor graphics, int x, int y, float partialTicks)
	{
		if(WurstClient.MC.player == null)
			return;
		List<ItemStack> armor = new ArrayList<>();
		for(var slot : new net.minecraft.world.entity.EquipmentSlot[]{
			net.minecraft.world.entity.EquipmentSlot.FEET,
			net.minecraft.world.entity.EquipmentSlot.LEGS,
			net.minecraft.world.entity.EquipmentSlot.CHEST,
			net.minecraft.world.entity.EquipmentSlot.HEAD})
		{
			ItemStack stack = WurstClient.MC.player.getItemBySlot(slot);
			if(!stack.isEmpty())
				armor.add(stack);
		}
		Collections.reverse(armor);
		for(int index = 0; index < armor.size(); index++)
		{
			ItemStack stack = armor.get(index);
			int itemX = x + index * 18;
			RenderUtils.drawItem(graphics, stack, itemX, y, false);
		}
	}

	@Override
	public HudElementConfig getDefaultLayout()
	{
		return new HudElementConfig(HudElementConfig.HORIZONTAL_LEFT,
			HudElementConfig.VERTICAL_BOTTOM, 3, 22);
	}
}
