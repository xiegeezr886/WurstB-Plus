/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.InventoryUtils;

@SearchTags({"item generator", "drop infinite"})
public final class ItemGeneratorHack extends Hack implements UpdateListener
{
	private final SliderSetting speed = new SliderSetting("Speed",
		"\u00a74\u00a7lWARNING:\u00a7r High speeds will cause a ton of lag and can easily crash the game!",
		1, 1, 36, 1, ValueDisplay.INTEGER);
	
	private final SliderSetting stackSize = new SliderSetting("Stack size",
		"How many items to place in each stack.\n"
			+ "Doesn't seem to affect performance.",
		1, 1, 64, 1, ValueDisplay.INTEGER);
	
	private final RandomSource random = RandomSource.createNewThreadLocalInstance();
	
	public ItemGeneratorHack()
	{
		super("ItemGenerator");
		
		setCategory(Category.ITEMS);
		addSetting(speed);
		addSetting(stackSize);
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
	}
	
	@Override
	public void onUpdate()
	{
		if(!MC.player.getAbilities().instabuild)
		{
			ChatUtils.error("仅限创造模式。");
			setEnabled(false);
			// 必须立刻返回：setEnabled(false) 只是把监听器摘掉，本次
			// UpdateEvent 的分发还在继续，旧实现少了这个 return，于是
			// 生存模式下"报错并自动关闭"的同一 tick 仍会往下执行，
			// 向服务端发出丢弃物品的点击包（丢的是你背包里真实存在的物品）。
			return;
		}
		
		int stacks = speed.getValueI();
		for(int slot = 9; slot < 9 + stacks; slot++)
		{
			// Not sure if it's possible to get an empty optional here,
			// but if so it will just retry.
			Optional<Holder.Reference<Item>> optional = Optional.empty();
			while(optional.isEmpty())
				optional = BuiltInRegistries.ITEM.getRandom(random);
			
			Item item = optional.get().value();
			ItemStack stack = new ItemStack(item, stackSize.getValueI());
			
			InventoryUtils.setCreativeStack(slot, stack);
		}
		
		for(int i = 9; i < 9 + stacks; i++)
			IMC.getInteractionManager().windowClick_THROW(i);
	}
}
