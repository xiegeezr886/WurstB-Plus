/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * Ported from Meteor Client (GPL-3.0).
 */
package net.wurstclient.hacks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.util.BlockPlacer;

@SearchTags({"self anvil", "anvil", "block hole", "SelfAnvil"})
public final class SelfAnvilHack extends Hack implements UpdateListener
{
	public SelfAnvilHack()
	{
		super("SelfAnvil");
		setCategory(Category.COMBAT);
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
		int slot = findAnvilSlot();
		if(slot == -1)
			return;

		int prevSlot = MC.player.getInventory().getSelectedSlot();
		MC.player.getInventory().setSelectedSlot(slot);

		BlockPos pos = MC.player.blockPosition().above(2);
		BlockPlacer.BlockPlacingParams params =
			BlockPlacer.getBlockPlacingParams(pos);

		if(params != null)
		{
			WURST.getRotationFaker().faceVectorPacket(params.hitVec());
			IMC.getInteractionManager().rightClickBlock(params.neighbor(),
				params.side(), params.hitVec());
			MC.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
			MC.player.getInventory().setSelectedSlot(prevSlot);
			setEnabled(false);
			return;
		}

		MC.player.getInventory().setSelectedSlot(prevSlot);
	}

	private int findAnvilSlot()
	{
		for(int i = 0; i < 9; i++)
		{
			ItemStack stack = MC.player.getInventory().getItem(i);
			if(stack.isEmpty())
				continue;

			Block block = Block.byItem(stack.getItem());
			if(block instanceof AnvilBlock)
				return i;
		}

		return -1;
	}
}
