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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.util.BlockPlacer;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.InventoryUtils;

@SearchTags({"anti anvil", "auto anvil", "anvil aura", "AntiAnvil"})
public final class AntiAnvilHack extends Hack implements UpdateListener
{
	private final CheckboxSetting swing = new CheckboxSetting("Swing",
		"Swings your hand client-side when placing.", true);

	private final CheckboxSetting rotate = new CheckboxSetting("Rotate",
		"Makes you rotate when placing.", true);

	public AntiAnvilHack()
	{
		super("AntiAnvil");
		setCategory(Category.COMBAT);
		addSetting(swing);
		addSetting(rotate);
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
		int range = (int)MC.player.blockInteractionRange();

		for(int i = 0; i <= range; i++)
		{
			BlockPos pos = MC.player.blockPosition().offset(0, i + 3, 0);

			if(BlockUtils.getBlock(pos) != Blocks.ANVIL
				|| !BlockUtils.getState(pos.below()).isAir())
				continue;

			int slot = InventoryUtils.indexOf(Items.OBSIDIAN, 9);
			if(slot == -1)
				return;

			int prevSlot = MC.player.getInventory().getSelectedSlot();
			InventoryUtils.selectItem(slot);

			if(!MC.player.isHolding(Items.OBSIDIAN))
			{
				MC.player.getInventory().setSelectedSlot(prevSlot);
				return;
			}

			if(place(pos.below()) && swing.isChecked())
				MC.player.swing(InteractionHand.MAIN_HAND);

			MC.player.getInventory().setSelectedSlot(prevSlot);
			break;
		}
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
