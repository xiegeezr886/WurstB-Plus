/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.BlockPlacer;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.FakePlayerEntity;

@SearchTags({"auto trap", "trap", "AutoTrap", "cage"})
public final class AutoTrapHack extends Hack implements UpdateListener
{
	private final SliderSetting range = new SliderSetting("Range",
		"How far to search for targets.", 5, 1, 8, 0.5,
		ValueDisplay.DECIMAL);

	private final CheckboxSetting onlyObsidian = new CheckboxSetting(
		"Only obsidian", "Only uses obsidian blocks.", true);

	private final CheckboxSetting disableAfter = new CheckboxSetting(
		"Disable after", "Disables after trapping a target.", true);

	private final SwingHandSetting swingHand =
		new SwingHandSetting(this, SwingHand.CLIENT);

	private int placementTimer;

	public AutoTrapHack()
	{
		super("AutoTrap");
		setCategory(Category.COMBAT);
		addSetting(range);
		addSetting(onlyObsidian);
		addSetting(disableAfter);
		addSetting(swingHand);
	}

	@Override
	protected void onEnable()
	{
		WURST.getHax().killauraHack.setEnabled(false);
		WURST.getHax().crystalAuraHack.setEnabled(false);
		placementTimer = 0;
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
		placementTimer++;
		if(placementTimer < 2)
			return;
		placementTimer = 0;

		Entity target = findTarget();
		if(target == null)
		{
			if(disableAfter.isChecked())
				setEnabled(false);
			return;
		}

		BlockPos targetPos = target.blockPosition();
		ArrayList<BlockPos> toPlace = new ArrayList<>();

		BlockPos[] trapOffsets = {
			new BlockPos(1, 0, 0), new BlockPos(-1, 0, 0),
			new BlockPos(0, 0, 1), new BlockPos(0, 0, -1),
			new BlockPos(1, 1, 0), new BlockPos(-1, 1, 0),
			new BlockPos(0, 1, 1), new BlockPos(0, 1, -1),
			new BlockPos(1, 2, 0), new BlockPos(-1, 2, 0),
			new BlockPos(0, 2, 1), new BlockPos(0, 2, -1),
		};

		for(BlockPos offset : trapOffsets)
		{
			BlockPos pos = targetPos.offset(offset);
			if(BlockUtils.getState(pos).canBeReplaced()
				&& !MC.player.getBoundingBox().intersects(new AABB(pos)))
				toPlace.add(pos);
		}

		if(toPlace.isEmpty())
		{
			if(disableAfter.isChecked())
				setEnabled(false);
			return;
		}

		BlockPos closest = toPlace.stream()
			.min(Comparator.comparingDouble(
				p -> MC.player.distanceToSqr(Vec3.atCenterOf(p))))
			.orElse(null);

		if(closest == null)
			return;

		placeBlock(closest);
	}

	private Entity findTarget()
	{
		double rangeSq = Math.pow(range.getValue(), 2);

		return StreamSupport
			.stream(MC.level.entitiesForRendering().spliterator(), false)
			.filter(e -> e instanceof LivingEntity
				&& ((LivingEntity)e).getHealth() > 0)
			.filter(e -> e != MC.player)
			.filter(e -> !(e instanceof FakePlayerEntity))
			.filter(e -> !WURST.getFriends().contains(e.getScoreboardName()))
			.filter(e -> MC.player.distanceToSqr(e) <= rangeSq)
			.min(Comparator.comparingDouble(
				e -> MC.player.distanceToSqr(e)))
			.orElse(null);
	}

	private void placeBlock(BlockPos pos)
	{
		int slot = findBlockSlot();
		if(slot == -1)
			return;

		int oldSlot = MC.player.getInventory().selected;
		MC.player.getInventory().selected = slot;

		if(BlockPlacer.place(pos, false, false))
			swingHand.swing(InteractionHand.MAIN_HAND);

		MC.player.getInventory().selected = oldSlot;
	}

	private int findBlockSlot()
	{
		for(int i = 0; i < 9; i++)
		{
			ItemStack stack = MC.player.getInventory().getItem(i);
			if(stack.isEmpty() || !(stack.getItem() instanceof BlockItem))
				continue;

			Block block = Block.byItem(stack.getItem());

			if(onlyObsidian.isChecked())
			{
				if(block != Blocks.OBSIDIAN && block != Blocks.CRYING_OBSIDIAN
					&& block != Blocks.BEDROCK)
					continue;
			}

			if(block == Blocks.TNT || block == Blocks.RESPAWN_ANCHOR
				|| block == Blocks.COBWEB)
				continue;

			return i;
		}
		return -1;
	}
}
