/*
 * Copyright (c) 2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */

/* Ported from LeavesHack (AGPL-3.0). */
package net.wurstclient.hacks;

import java.util.ArrayList;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.BlockSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.BaritoneUtils;
import net.wurstclient.util.BlockPlacer;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.InventoryUtils;

/**
 * Simplified port of LeavesHack's Printer. The original prints a Litematica
 * schematic; this version prints the selected block in front of the player
 * while walking, optionally asking Baritone to keep moving forward.
 */
@SearchTags({"printer", "auto build", "block printer"})
public final class PrinterHack extends Hack implements UpdateListener
{
	private final BlockSetting block = new BlockSetting("Block",
		"The block that Printer places.", "minecraft:cobblestone", false);

	private final SliderSetting radius = new SliderSetting("Radius",
		"How far around the player to look for placement positions.", 3, 1, 6,
		1, ValueDisplay.INTEGER);

	private final SliderSetting delay = new SliderSetting("Delay",
		"Ticks to wait between printing attempts.", 1, 0, 10, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final SliderSetting blocksPerTick =
		new SliderSetting("Blocks per tick", "Maximum blocks placed per attempt.",
			2, 1, 8, 1, ValueDisplay.INTEGER);

	private final SwingHandSetting swingHand =
		new SwingHandSetting(this, SwingHand.CLIENT);

	private final CheckboxSetting onlyWhileMoving = new CheckboxSetting(
		"Only while moving",
		"Only place blocks while the player is actually moving.", true);

	private final CheckboxSetting baritoneWalk = new CheckboxSetting(
		"Baritone walk",
		"Uses Baritone to keep walking forward while Printer is enabled.",
		false);

	private final SliderSetting walkDistance = new SliderSetting(
		"Walk distance", "How far ahead Baritone should path.",
		32, 8, 128, 1, ValueDisplay.INTEGER)
			.visibleWhen(baritoneWalk::isChecked);

	private int timer;
	private int baritoneTimer;

	public PrinterHack()
	{
		super("Printer");
		setCategory(Category.BLOCKS);
		addSetting(block);
		addSetting(radius);
		addSetting(delay);
		addSetting(blocksPerTick);
		addSetting(swingHand);
		addSetting(onlyWhileMoving);
		addSetting(baritoneWalk);
		addSetting(walkDistance);
	}

	@Override
	protected void onEnable()
	{
		timer = 0;
		baritoneTimer = 0;
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
		if(MC.player == null || MC.level == null)
			return;

		if(baritoneWalk.isChecked())
			updateBaritone();

		if(timer > 0)
		{
			timer--;
			return;
		}
		timer = delay.getValueI();

		printBlocks();
	}

	private void updateBaritone()
	{
		if(!BaritoneUtils.IS_AVAILABLE)
			return;

		if(baritoneTimer > 0)
		{
			baritoneTimer--;
			return;
		}
		if(BaritoneUtils.isPathing())
			return;

		BaritoneUtils.walkDirection(MC.player.getYRot(),
			walkDistance.getValue());
		baritoneTimer = 40;
	}

	private void printBlocks()
	{
		Vec3 velocity = MC.player.getDeltaMovement();
		if(onlyWhileMoving.isChecked()
			&& velocity.x * velocity.x + velocity.z * velocity.z < 1.0E-4)
			return;

		Block wanted = block.getBlock();
		Item item = wanted.asItem();
		if(item == Items.AIR || InventoryUtils.indexOf(item, 36) == -1)
			return;

		int previousSlot = MC.player.getInventory().selected;
		InventoryUtils.selectItem(item, 36);
		if(!MC.player.getMainHandItem().is(item))
		{
			MC.player.getInventory().selected = (previousSlot);
			return;
		}

		Direction facing = MC.player.getDirection();
		BlockPos origin = MC.player.blockPosition();
		Vec3 front = MC.player.position()
			.add(facing.getStepX() * 1.5, -0.5, facing.getStepZ() * 1.5);

		ArrayList<BlockPos> candidates = new ArrayList<>();
		for(BlockPos pos : BlockUtils.getAllInBox(origin,
			radius.getValueI()))
		{
			int dy = pos.getY() - origin.getY();
			if(dy > 0 || dy < -1)
				continue;

			double dx = pos.getX() + 0.5 - MC.player.getX();
			double dz = pos.getZ() + 0.5 - MC.player.getZ();
			if(dx * facing.getStepX() + dz * facing.getStepZ() < 0)
				continue;

			BlockState state = BlockUtils.getState(pos);
			if(!state.canBeReplaced())
				continue;

			AABB box = new AABB(pos);
			if(MC.player.getBoundingBox().intersects(box))
				continue;
			if(!MC.level.getEntities((Entity)null, box).isEmpty())
				continue;

			candidates.add(pos.immutable());
		}

		candidates.sort(Comparator.comparingDouble(
			pos -> front.distanceToSqr(Vec3.atCenterOf(pos))));

		int placed = 0;
		int limit = blocksPerTick.getValueI();
		for(BlockPos pos : candidates)
		{
			if(placed >= limit)
				break;
			if(!BlockPlacer.placeOneBlock(pos))
				continue;

			swingHand.swing(InteractionHand.MAIN_HAND);
			placed++;
		}

		MC.player.getInventory().selected = (previousSlot);
	}
}
