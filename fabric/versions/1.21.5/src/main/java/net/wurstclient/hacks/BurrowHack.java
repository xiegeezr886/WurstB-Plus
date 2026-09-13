/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.RotationUtils;

@SearchTags({"burrow", "InstantBurrow", "block", "ClipIntoBlock"})
public final class BurrowHack extends Hack implements UpdateListener
{
	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"\u00a7lInstant\u00a7r - Teleports you into a block instantly.\n"
			+ "\u00a7lSmooth\u00a7r - Smoothly clips into the block.\n"
			+ "\u00a7lRubberband\u00a7r - Uses rubberband to clip in.",
		Mode.values(), Mode.INSTANT);

	private final CheckboxSetting preferObsidian = new CheckboxSetting(
		"Prefer obsidian", "Prefers obsidian over other blocks.", true);

	private final CheckboxSetting disableAfter = new CheckboxSetting(
		"Disable after", "Disables after burrowing.", true);

	private int stage;

	public BurrowHack()
	{
		super("Burrow");
		setCategory(Category.COMBAT);
		addSetting(mode);
		addSetting(preferObsidian);
		addSetting(disableAfter);
	}

	@Override
	public String getRenderName()
	{
		return getName() + " [" + mode.getSelected() + "]";
	}

	@Override
	protected void onEnable()
	{
		stage = 0;
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
		BlockPos playerPos = BlockPos.containing(MC.player.position());

		if(!BlockUtils.getState(playerPos).canBeReplaced())
		{
			setEnabled(false);
			return;
		}

		int slot = findBlockSlot();
		if(slot == -1)
		{
			setEnabled(false);
			return;
		}

		switch(mode.getSelected())
		{
			case INSTANT:
			doInstantBurrow(playerPos, slot);
			break;

			case SMOOTH:
			doSmoothBurrow(playerPos, slot);
			break;

			case RUBBERBAND:
			doRubberbandBurrow(playerPos, slot);
			break;
		}
	}

	private void doInstantBurrow(BlockPos pos, int slot)
	{
		int oldSlot = MC.player.getInventory().getSelectedSlot();
		MC.player.getInventory().setSelectedSlot(slot);

		Vec3 posVec = Vec3.atCenterOf(pos);

		for(Direction side : Direction.values())
		{
			BlockPos neighbor = pos.relative(side);
			if(!BlockUtils.canBeClicked(neighbor))
				continue;

			Vec3 hitVec = Vec3.atCenterOf(neighbor)
				.add(Vec3.atLowerCornerOf(side.getOpposite().getUnitVec3i()).scale(0.5));

			RotationUtils.getNeededRotations(hitVec).sendPlayerLookPacket();
			IMC.getInteractionManager().rightClickBlock(neighbor,
				side.getOpposite(), hitVec);
			MC.player.swing(InteractionHand.MAIN_HAND);
			break;
		}

		MC.player.getInventory().setSelectedSlot(oldSlot);

		if(disableAfter.isChecked())
			setEnabled(false);
	}

	private void doSmoothBurrow(BlockPos pos, int slot)
	{
		stage++;

		if(stage == 1)
		{
			double x = MC.player.getX();
			double z = MC.player.getZ();
			MC.player.connection.send(
				new ServerboundMovePlayerPacket.Pos(
					x, MC.player.getY() + 0.42, z, true, false));
		}
		else if(stage == 2)
		{
			int oldSlot = MC.player.getInventory().getSelectedSlot();
			MC.player.getInventory().setSelectedSlot(slot);

			for(Direction side : Direction.values())
			{
				BlockPos neighbor = pos.relative(side);
				if(!BlockUtils.canBeClicked(neighbor))
					continue;

				Vec3 hitVec = Vec3.atCenterOf(neighbor)
					.add(Vec3.atLowerCornerOf(side.getOpposite().getUnitVec3i())
						.scale(0.5));

				RotationUtils.getNeededRotations(hitVec)
					.sendPlayerLookPacket();
				IMC.getInteractionManager().rightClickBlock(neighbor,
					side.getOpposite(), hitVec);
				break;
			}

			MC.player.getInventory().setSelectedSlot(oldSlot);
		}
		else if(stage >= 3)
		{
			if(disableAfter.isChecked())
				setEnabled(false);
		}
	}

	private void doRubberbandBurrow(BlockPos pos, int slot)
	{
		if(stage == 0)
		{
			MC.player.setPos(MC.player.getX(),
				MC.player.getY() + 0.42, MC.player.getZ());
			stage++;
			return;
		}

		int oldSlot = MC.player.getInventory().getSelectedSlot();
		MC.player.getInventory().setSelectedSlot(slot);

		for(Direction side : Direction.values())
		{
			BlockPos neighbor = pos.relative(side);
			if(!BlockUtils.canBeClicked(neighbor))
				continue;

			Vec3 hitVec = Vec3.atCenterOf(neighbor)
				.add(Vec3.atLowerCornerOf(side.getOpposite().getUnitVec3i())
					.scale(0.5));

			RotationUtils.getNeededRotations(hitVec)
				.sendPlayerLookPacket();
			IMC.getInteractionManager().rightClickBlock(neighbor,
				side.getOpposite(), hitVec);
			break;
		}

		MC.player.getInventory().setSelectedSlot(oldSlot);

		if(disableAfter.isChecked())
			setEnabled(false);
	}

	private int findBlockSlot()
	{
		int bestNonObsidian = -1;
		for(int i = 0; i < 9; i++)
		{
			ItemStack stack = MC.player.getInventory().getItem(i);
			if(stack.isEmpty() || !(stack.getItem() instanceof BlockItem))
				continue;

			Block block = Block.byItem(stack.getItem());
			if(block == Blocks.TNT || block == Blocks.RESPAWN_ANCHOR
				|| block == Blocks.COBWEB || block == Blocks.AIR)
				continue;

			if(preferObsidian.isChecked()
				&& (block == Blocks.OBSIDIAN
					|| block == Blocks.CRYING_OBSIDIAN))
				return i;

			if(bestNonObsidian == -1)
				bestNonObsidian = i;
		}
		return bestNonObsidian;
	}

	private enum Mode
	{
		INSTANT("Instant"),
		SMOOTH("Smooth"),
		RUBBERBAND("Rubberband");

		private final String name;

		Mode(String name)
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
