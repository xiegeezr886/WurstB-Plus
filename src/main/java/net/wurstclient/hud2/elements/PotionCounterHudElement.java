package net.wurstclient.hud2.elements;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.wurstclient.WurstClient;
import net.wurstclient.hud2.HudLayout.HudElementConfig;

public final class PotionCounterHudElement extends SoarTextHudElement
{
	public PotionCounterHudElement()
	{
		super("potion_counter", "\u6cbb\u7597\u836f\u6c34",
			HudElementConfig.HORIZONTAL_RIGHT, 110, 152);
	}

	@Override
	protected String getText()
	{
		LocalPlayer player = WurstClient.MC.player;
		int count = 0;
		if(player != null)
			for(int slot = 0; slot < player.getInventory().getContainerSize();
				slot++)
			{
				ItemStack stack = player.getInventory().getItem(slot);
				if(stack.getItem() == Items.SPLASH_POTION
					&& PotionUtils.getMobEffects(stack).stream().anyMatch(
						effect -> effect.getEffect() == MobEffects.HEAL))
					count += stack.getCount();
			}
		return count + (count == 1 ? " pot" : " pots");
	}
}
