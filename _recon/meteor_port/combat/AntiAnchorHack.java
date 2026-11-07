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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.util.BlockPlacer;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.InventoryUtils;

@SearchTags({"anti anchor", "anchor aura", "respawn anchor", "AntiAnchor"})
public final class AntiAnchorHack extends Hack implements UpdateListener
{
	private final CheckboxSetting rotate = new CheckboxSetting("Rotate",
		"Makes you rotate when placing.", true);

	private final CheckboxSetting swing = new CheckboxSetting("Swing",
		"Swings your hand when placing.", true);

	public AntiAnchorHack()
	{
		super("AntiAnchor");
		setCategory(Category.COMBAT);
		addSetting(rotate);
		addSetting(swing);
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
		BlockPos head = MC.player.blockPosition().above();

		if(BlockUtils.getBlock(head.above()) != Blocks.RESPAWN_ANCHOR)
			return;

		if(BlockUtils.getBlock(head) != Blocks.AIR)
			return;

		int slot = findSlabSlot();
		if(slot == -1)
			return;

		int prevSlot = MC.player.getInventory().getSelectedSlot();
		InventoryUtils.selectItem(slot);

		if(!(MC.player.getMainHandItem().getItem() instanceof BlockItem))
		{
			MC.player.getInventory().setSelectedSlot(prevSlot);
			return;
		}

		if(place(head) && swing.isChecked())
			MC.player.swing(InteractionHand.MAIN_HAND);

		MC.player.getInventory().setSelectedSlot(prevSlot);
	}

	private int findSlabSlot()
	{
		for(int i = 0; i < 9; i++)
		{
			ItemStack stack = MC.player.getInventory().getItem(i);
			if(stack.isEmpty())
				continue;

			Block block = Block.byItem(stack.getItem());
			if(block instanceof SlabBlock)
				return i;
		}

		return -1;
	}

	private boolean place(BlockPos pos)
	{
		BlockPlacer.BlockPlacingParams params =
			BlockPlacer.getBlockPlacingParams(pos);
		if(params == null)
			return false;

		if(rotate.isChecked())
			WURST.getRotationFaker().faceVectorPacket(params.hitVec());

		IMC.getInteractionManager().rightClickBlock(params.neighbor(),
			params.side(), params.hitVec());
		return true;
	}
}
