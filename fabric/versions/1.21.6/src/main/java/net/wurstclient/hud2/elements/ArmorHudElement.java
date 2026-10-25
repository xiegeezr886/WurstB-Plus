package net.wurstclient.hud2.elements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.world.item.ItemStack;
import net.wurstclient.WurstClient;
import net.wurstclient.hud2.HUDMod;
import net.wurstclient.util.RenderUtils;

public final class ArmorHudElement extends HUDMod
{
	public ArmorHudElement()
	{
		super("armor", "Armor");
		setDefaultPosition(3, 500);
	}

	@Override
	protected void draw()
	{
		if(WurstClient.MC.player == null)
			return;
		List<ItemStack> armor = getArmor();
		if(armor.isEmpty())
			return;

		int width = armor.size() * 20 + 8;
		int height = 28;
		drawBackground(width, height);

		graphics.pose().pushMatrix();
		graphics.pose().translate(getX(), getY());
		graphics.pose().scale(getScale(), getScale());
		for(int index = 0; index < armor.size(); index++)
			RenderUtils.drawItem(graphics, armor.get(index), 6 + index * 20, 6,
				false);
		graphics.pose().popMatrix();

		setSize(width, height);
	}

	private List<ItemStack> getArmor()
	{
		if(WurstClient.MC.player == null)
			return Collections.emptyList();
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
		return armor;
	}
}
