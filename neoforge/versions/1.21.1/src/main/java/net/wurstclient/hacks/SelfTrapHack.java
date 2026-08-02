/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.BlockPlacer;
import net.wurstclient.util.BlockUtils;

import java.util.ArrayList;

@SearchTags({"self trap", "selftrap", "SelfTrap"})
public final class SelfTrapHack extends Hack implements UpdateListener
{
	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"\u00a7lFull\u00a7r - Full obsidian cocoon.\n"
			+ "\u00a7lTop\u00a7r - Only covers the head.\n"
			+ "\u00a7lFeet\u00a7r - Only around feet.",
		Mode.values(), Mode.FULL);

	private final CheckboxSetting onlyObsidian = new CheckboxSetting(
		"Only obsidian", "Only use obsidian blocks.", true);

	private final SwingHandSetting swingHand =
		new SwingHandSetting(this, SwingHand.CLIENT);

	private int placeTimer;
	private int step;

	public SelfTrapHack()
	{
		super("SelfTrap");
		setCategory(Category.COMBAT);
		addSetting(mode);
		addSetting(onlyObsidian);
		addSetting(swingHand);
	}

	@Override
	public String getRenderName()
	{
		return getName() + " [" + mode.getSelected() + "]";
	}

	@Override
	protected void onEnable()
	{
		placeTimer = 0;
		step = 0;
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
		placeTimer++;
		if(placeTimer < 2)
			return;
		placeTimer = 0;

		BlockPos p = BlockPos.containing(MC.player.position());
		ArrayList<BlockPos> positions = new ArrayList<>();

		switch(mode.getSelected())
		{
			case FULL:
			positions.add(p.offset(1, 0, 0)); positions.add(p.offset(-1, 0, 0));
			positions.add(p.offset(0, 0, 1)); positions.add(p.offset(0, 0, -1));
			positions.add(p.offset(1, 1, 0)); positions.add(p.offset(-1, 1, 0));
			positions.add(p.offset(0, 1, 1)); positions.add(p.offset(0, 1, -1));
			positions.add(p.offset(1, 2, 0)); positions.add(p.offset(-1, 2, 0));
			positions.add(p.offset(0, 2, 1)); positions.add(p.offset(0, 2, -1));
			positions.add(p.offset(0, 2, 0));
			break;

			case TOP:
			positions.add(p.offset(1, 2, 0)); positions.add(p.offset(-1, 2, 0));
			positions.add(p.offset(0, 2, 1)); positions.add(p.offset(0, 2, -1));
			positions.add(p.offset(0, 2, 0));
			break;

			case FEET:
			positions.add(p.offset(1, 0, 0)); positions.add(p.offset(-1, 0, 0));
			positions.add(p.offset(0, 0, 1)); positions.add(p.offset(0, 0, -1));
			break;
		}

		boolean placed = false;
		for(BlockPos pos : positions)
		{
			if(!BlockUtils.getState(pos).canBeReplaced())
				continue;
			if(MC.player.getBoundingBox().intersects(new AABB(pos)))
				continue;
			if(BlockUtils.getState(pos).canBeReplaced()
				&& placeBlock(pos))
			{
				placed = true;
				break;
			}
		}

		if(!placed)
			setEnabled(false);
	}

	private boolean placeBlock(BlockPos pos)
	{
		int slot = findBlockSlot();
		if(slot == -1)
			return false;

		int oldSlot = MC.player.getInventory().selected;
		MC.player.getInventory().selected = slot;

		boolean placed = BlockPlacer.place(pos, false, false);
		if(placed)
			swingHand.swing(InteractionHand.MAIN_HAND);

		MC.player.getInventory().selected = oldSlot;
		return placed;
	}

	private int findBlockSlot()
	{
		for(int i = 0; i < 9; i++)
		{
			ItemStack s = MC.player.getInventory().getItem(i);
			if(s.isEmpty() || !(s.getItem() instanceof BlockItem))
				continue;
			Block b = Block.byItem(s.getItem());
			if(onlyObsidian.isChecked())
			{
				if(b != Blocks.OBSIDIAN && b != Blocks.CRYING_OBSIDIAN
					&& b != Blocks.BEDROCK)
					continue;
			}
			if(b == Blocks.TNT || b == Blocks.RESPAWN_ANCHOR)
				continue;
			return i;
		}
		return -1;
	}

	private enum Mode
	{
		FULL("Full"), TOP("Top"), FEET("Feet");
		private final String n;
		Mode(String n) { this.n = n; }
		@Override public String toString() { return n; }
	}
}
