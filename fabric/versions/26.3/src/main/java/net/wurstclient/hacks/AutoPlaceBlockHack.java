/*
 * Copyright (c) 2025 LeavesHack contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from LeavesHack (AGPL-3.0).
package net.wurstclient.hacks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.StreamSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.BlockSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockPlacer;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.FakePlayerEntity;
import net.wurstclient.util.InventoryUtils;
import net.wurstclient.util.RotationUtils;
import net.wurstclient.util.text.WText;

@SearchTags({"auto place block", "auto place", "scaffold", "obsidian", "web",
	"AutoPlaceBlock"})
public final class AutoPlaceBlockHack extends Hack implements UpdateListener
{
	private final BlockSetting block = new BlockSetting("Block",
		WText.literal("Which block to place."), "minecraft:obsidian", false);

	private final EnumSetting<PlaceMode> mode = new EnumSetting<>("Mode",
		WText.literal("Where to place the block."), PlaceMode.values(),
		PlaceMode.UNDER_PLAYER);

	private final SliderSetting range = new SliderSetting("Range",
		WText.literal("How far away blocks can be placed."), 4.5, 1, 6, 0.1,
		ValueDisplay.DECIMAL);

	private final SliderSetting delay = new SliderSetting("Delay",
		WText.literal("Ticks to wait between placements."), 2, 0, 20, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private int timer;

	public AutoPlaceBlockHack()
	{
		super("AutoPlaceBlock");
		setCategory(Category.BLOCKS);
		addSetting(block);
		addSetting(mode);
		addSetting(range);
		addSetting(delay);
	}

	@Override
	protected void onEnable()
	{
		timer = 0;
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
		if(MC.player == null || MC.level == null || MC.player.isSpectator())
			return;

		if(timer > 0)
		{
			timer--;
			return;
		}

		Item item = block.getBlock().asItem();
		if(item == Items.AIR)
			return;
		if(InventoryUtils.indexOf(item, 36) == -1)
			return;

		BlockPos pos = findPlacement();
		if(pos == null)
			return;

		int oldSlot = MC.player.getInventory().getSelectedSlot();
		InventoryUtils.selectItem(item, 36);
		if(!MC.player.getMainHandItem().is(item))
			return;

		if(BlockPlacer.placeOneBlock(pos))
			timer = delay.getValueI();

		MC.player.getInventory().setSelectedSlot(oldSlot);
	}

	private BlockPos findPlacement()
	{
		switch(mode.getSelected())
		{
			case UNDER_PLAYER:
			return canPlace(BlockPos.containing(MC.player.position()).below())
				? BlockPos.containing(MC.player.position()).below() : null;

			case NEAR_TARGET:
			return findPlacementNearTarget();

			case AROUND_PLAYER:
			return BlockUtils
				.getAllInBoxStream(
					BlockPos.containing(MC.player.position()),
					range.getValueCeil())
				.filter(this::canPlace)
				.min(Comparator.comparingDouble(
					p -> MC.player.distanceToSqr(Vec3.atCenterOf(p))))
				.orElse(null);

			default:
			return null;
		}
	}

	private BlockPos findPlacementNearTarget()
	{
		LivingEntity target = findTarget();
		if(target == null)
			return null;

		BlockPos base = target.blockPosition();
		ArrayList<BlockPos> candidates = new ArrayList<>();
		candidates.add(base.below());
		candidates.add(base);

		for(Direction side : Direction.values())
			if(side.getAxis() == Direction.Axis.X
				|| side.getAxis() == Direction.Axis.Z)
				candidates.add(base.relative(side));

		return candidates.stream().filter(this::canPlace)
			.min(Comparator.comparingDouble(
				p -> MC.player.distanceToSqr(Vec3.atCenterOf(p))))
			.orElse(null);
	}

	private LivingEntity findTarget()
	{
		double rangeSq = range.getValueSq();

		return StreamSupport
			.stream(MC.level.entitiesForRendering().spliterator(), false)
			.filter(e -> e instanceof LivingEntity
				&& ((LivingEntity)e).getHealth() > 0)
			.filter(e -> e != MC.player)
			.filter(e -> !(e instanceof FakePlayerEntity))
			.filter(e -> !WURST.getFriends().contains(e.getScoreboardName()))
			.filter(e -> MC.player.distanceToSqr(e) <= rangeSq)
			.map(e -> (LivingEntity)e)
			.min(Comparator.comparingDouble(e -> MC.player.distanceToSqr(e)))
			.orElse(null);
	}

	private boolean canPlace(BlockPos pos)
	{
		if(!MC.level.isInWorldBounds(pos))
			return false;
		if(!BlockUtils.getState(pos).canBeReplaced())
			return false;
		if(MC.player.getBoundingBox().intersects(new AABB(pos)))
			return false;
		if(!MC.level.getEntities((Entity)null, new AABB(pos)).isEmpty())
			return false;
		if(RotationUtils.getEyesPos()
			.distanceToSqr(Vec3.atCenterOf(pos)) > range.getValueSq())
			return false;

		return hasClickableNeighbor(pos);
	}

	private boolean hasClickableNeighbor(BlockPos pos)
	{
		for(Direction side : Direction.values())
		{
			BlockPos neighbor = pos.relative(side);
			if(BlockUtils.canBeClicked(neighbor)
				&& !BlockUtils.getState(neighbor).canBeReplaced())
				return true;
		}

		return false;
	}

	public enum PlaceMode
	{
		UNDER_PLAYER("Under player"),
		NEAR_TARGET("Near target"),
		AROUND_PLAYER("Around player");

		private final String name;

		PlaceMode(String name)
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
