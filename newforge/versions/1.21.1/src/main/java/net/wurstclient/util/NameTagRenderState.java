package net.wurstclient.util;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public record NameTagRenderState(Component label, List<ItemStack> equipment,
	List<Integer> durability)
{
	public NameTagRenderState
	{
		equipment = equipment.stream().map(ItemStack::copy).toList();
		durability = List.copyOf(durability);
	}
}
