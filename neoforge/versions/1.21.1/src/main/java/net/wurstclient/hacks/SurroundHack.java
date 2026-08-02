/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.BlockPlacer;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.RotationQueue;
import net.wurstclient.util.RotationUtils;

@SearchTags({"surround", "AutoSurround", "auto surround", "CityBlock"})
public final class SurroundHack extends Hack implements UpdateListener
{
	private final EnumSetting<SupportMode> support = new EnumSetting<>(
		"Support", "\u00a7lPlace\u00a7r - Normal placement.\n"
			+ "\u00a7lAirPlace\u00a7r - Place in air.\n"
			+ "\u00a7lSkip\u00a7r - Skip unsupported positions.",
		SupportMode.values(), SupportMode.PLACE);

	private final SliderSetting bpt = new SliderSetting("BPT",
		"Blocks per tick.", 2, 1, 8, 1, ValueDisplay.INTEGER);

	private final CheckboxSetting autocenter = new CheckboxSetting(
		"Autocenter", "Teleports to block center before placing.", true);

	private final CheckboxSetting keepOn = new CheckboxSetting("Keep on",
		"Keeps the module enabled after placing all blocks.", false);

	private final CheckboxSetting jumpDisable = new CheckboxSetting(
		"Jump disable", "Disables if you jump.", true);

	private final CheckboxSetting rotate = new CheckboxSetting("Rotate",
		"Silently rotates your movement packets when placing.", true);

	private final SwingHandSetting swingHand =
		new SwingHandSetting(this, SwingHand.CLIENT);

	private static final BlockPos[] SURROUND_POS = {
		new BlockPos(1, 0, 0), new BlockPos(-1, 0, 0),
		new BlockPos(0, 0, 1), new BlockPos(0, 0, -1)
	};

	private RotationQueue rotationQueue;

	public SurroundHack()
	{
		super("Surround");
		setCategory(Category.COMBAT);
		addSetting(support);
		addSetting(bpt);
		addSetting(autocenter);
		addSetting(keepOn);
		addSetting(jumpDisable);
		addSetting(rotate);
		addSetting(swingHand);
	}

	@Override
	protected void onEnable()
	{
		rotationQueue = new RotationQueue(
			RotationQueue.Priority.BLOCK_PLACEMENT);
		rotationQueue.start();
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		rotationQueue.stop();
		rotationQueue = null;
	}

	@Override
	public void onUpdate()
	{
		if(jumpDisable.isChecked() && !MC.player.onGround()
			&& MC.player.getDeltaMovement().y > 0)
		{
			setEnabled(false);
			return;
		}

		if(autocenter.isChecked())
		{
			BlockPos cp = MC.player.blockPosition();
			MC.player.setPos(cp.getX() + 0.5, MC.player.getY(),
				cp.getZ() + 0.5);
		}

		int slot = findBlockSlot();
		if(slot == -1)
		{
			if(!keepOn.isChecked())
				setEnabled(false);
			return;
		}

		int prevSlot = MC.player.getInventory().selected;
		MC.player.getInventory().selected = slot;

		BlockPos playerPos = MC.player.blockPosition();
		List<BlockPos> toPlace = new ArrayList<>();
		for(BlockPos offset : SURROUND_POS)
		{
			BlockPos pos = playerPos.offset(offset);
			AABB box = MC.player.getBoundingBox();
			if(BlockUtils.getState(pos).canBeReplaced()
				&& !(box.intersects(new AABB(pos)) || box.intersects(
					new AABB(pos.above()))))
				toPlace.add(pos);
		}

		if(toPlace.isEmpty())
		{
			if(!keepOn.isChecked())
				setEnabled(false);
			MC.player.getInventory().selected = prevSlot;
			return;
		}

		int cap = 0;
		boolean anyPlaced = false;
		for(BlockPos pos : toPlace)
		{
			if(cap >= bpt.getValueI())
				break;

			SupportMode mode = support.getSelected();
			boolean placed = false;

			if(mode == SupportMode.PLACE)
				placed = BlockPlacer.place(pos, false, false);
			else if(mode == SupportMode.AIRPLACE)
				placed = BlockPlacer.place(pos, true, false);
			else
				placed = BlockPlacer.place(pos, false, false);

			if(placed)
			{
				swingHand.swing(InteractionHand.MAIN_HAND);
				cap++;
				anyPlaced = true;
			}
		}

		if(!anyPlaced && !keepOn.isChecked())
			setEnabled(false);

		MC.player.getInventory().selected = prevSlot;
	}

	private int findBlockSlot()
	{
		Block[] allowed = {Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN,
			Blocks.BEDROCK, Blocks.ENDER_CHEST, Blocks.RESPAWN_ANCHOR,
			Blocks.ANCIENT_DEBRIS, Blocks.NETHERITE_BLOCK};

		for(int i = 0; i < 9; i++)
		{
			ItemStack stack =
				MC.player.getInventory().getItem(i);
			if(stack.isEmpty() || !(stack.getItem() instanceof BlockItem))
				continue;

			Block block = Block.byItem(stack.getItem());
			for(Block allowedBlock : allowed)
			{
				if(block == allowedBlock)
					return i;
			}
		}
		return -1;
	}

	private enum SupportMode
	{
		PLACE("Place"),
		AIRPLACE("AirPlace"),
		SKIP("Skip");

		private final String name;

		SupportMode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}
