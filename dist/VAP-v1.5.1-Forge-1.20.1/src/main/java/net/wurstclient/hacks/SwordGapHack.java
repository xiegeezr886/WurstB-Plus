/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Ported from InvincibleMachineGun (GPL-3.0).
 */
package net.wurstclient.hacks;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.util.InventoryUtils;

@SearchTags({"sword gap", "sword gapple", "SwordGap", "offhand gap"})
public final class SwordGapHack extends Hack implements UpdateListener
{
	private final CheckboxSetting allowGoldenApple = new CheckboxSetting(
		"Golden apples", "Allow regular golden apples.", true);

	private final CheckboxSetting allowEnchantedGoldenApple = new CheckboxSetting(
		"Enchanted golden apples", "Allow enchanted golden apples.", true);

	private boolean eating;
	private int lastFoodSlot = -1;

	public SwordGapHack()
	{
		super("SwordGap");
		setCategory(Category.COMBAT);
		addSetting(allowGoldenApple);
		addSetting(allowEnchantedGoldenApple);
	}

	@Override
	protected void onEnable()
	{
		eating = false;
		lastFoodSlot = -1;
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		if(eating)
			restoreOffhand();
		eating = false;
		lastFoodSlot = -1;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		boolean holdingSword =
			MC.player.getMainHandItem().is(ItemTags.SWORDS);
		boolean pressingUse = MC.options.keyUse.isDown();

		if(holdingSword && pressingUse)
		{
			if(!eating)
				startEating();
		}else if(eating)
			restoreOffhand();
	}

	private void startEating()
	{
		int foodSlot = findFoodSlot();
		if(foodSlot == -1)
			return;

		if(isAllowedFood(MC.player.getOffhandItem()))
		{
			eating = true;
			return;
		}

		lastFoodSlot = foodSlot;
		IMC.getInteractionManager().windowClick_SWAP(
			InventoryUtils.toNetworkSlot(foodSlot), 40);
		eating = true;
	}

	private void restoreOffhand()
	{
		if(lastFoodSlot != -1)
			IMC.getInteractionManager().windowClick_SWAP(
				InventoryUtils.toNetworkSlot(lastFoodSlot), 40);
		eating = false;
		lastFoodSlot = -1;
	}

	private int findFoodSlot()
	{
		return InventoryUtils.indexOf(this::isAllowedFood, 36);
	}

	private boolean isAllowedFood(ItemStack stack)
	{
		if(stack.is(Items.GOLDEN_APPLE))
			return allowGoldenApple.isChecked();
		return stack.is(Items.ENCHANTED_GOLDEN_APPLE)
			&& allowEnchantedGoldenApple.isChecked();
	}
}
