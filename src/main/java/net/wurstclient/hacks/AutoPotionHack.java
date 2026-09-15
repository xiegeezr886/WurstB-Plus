/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.Rotation;

@SearchTags({"AutoPotion", "auto potion", "AutoSplashPotion",
	"auto splash potion"})
public final class AutoPotionHack extends Hack implements UpdateListener
{
	private final SliderSetting health = new SliderSetting("Health",
		"Throws a potion when your health reaches this value or falls below it.",
		6, 0.5, 9.5, 0.5, ValueDisplay.DECIMAL.withSuffix(" hearts"));
	
	private int timer;
	
	public AutoPotionHack()
	{
		super("AutoPotion");
		
		setCategory(Category.COMBAT);
		addSetting(health);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		timer = 0;
	}
	
	@Override
	public void onUpdate()
	{
		// search potion in hotbar
		int potionInHotbar = findPotion(0, 9);
		
		// check if any potion was found
		if(potionInHotbar != -1)
		{
			// check timer
			if(timer > 0)
			{
				timer--;
				return;
			}
			
			// check health
			if(MC.player.getHealth() > health.getValueF() * 2F)
				return;
			
			// save old slot
			int oldSlot = MC.player.getInventory().selected;
			
			// throw potion in hotbar
			MC.player.getInventory().selected = potionInHotbar;
			new Rotation(MC.player.getYRot(), 90).sendPlayerLookPacket();
			IMC.getInteractionManager().rightClickItem();
			
			// reset slot and rotation
			MC.player.getInventory().selected = oldSlot;
			new Rotation(MC.player.getYRot(), MC.player.getXRot())
				.sendPlayerLookPacket();
			
			// reset timer
			timer = 10;
			
			return;
		}
		
		// search potion in inventory
		int potionInInventory = findPotion(9, 36);
		
		// move potion in inventory to hotbar
		// 只有快捷栏还有空位时 shift-click 才移得动（原版
		// InventoryMenu.quickMoveStack 在目标区满时返回 EMPTY）；旧实现没有这个
		// 判断，快捷栏满时会每 tick 都发一次点击包，药水却永远不动。
		if(potionInInventory != -1 && hasEmptyHotbarSlot())
			IMC.getInteractionManager()
				.windowClick_QUICK_MOVE(potionInInventory);
	}
	
	private boolean hasEmptyHotbarSlot()
	{
		for(int i = 0; i < 9; i++)
			if(MC.player.getInventory().getItem(i).isEmpty())
				return true;
		
		return false;
	}
	
	private int findPotion(int startSlot, int endSlot)
	{
		for(int i = startSlot; i < endSlot; i++)
		{
			ItemStack stack = MC.player.getInventory().getItem(i);
			
			// filter out non-splash potion items
			if(stack.getItem() != Items.SPLASH_POTION)
				continue;
			
			// search for instant health effects
			if(hasEffect(stack, MobEffects.HEAL))
				return i;
		}
		
		return -1;
	}
	
	private boolean hasEffect(ItemStack stack, MobEffect effect)
	{
		for(MobEffectInstance effectInstance : PotionUtils
			.getMobEffects(stack))
		{
			if(effectInstance.getEffect() != effect)
				continue;
			
			return true;
		}
		
		return false;
	}
}
