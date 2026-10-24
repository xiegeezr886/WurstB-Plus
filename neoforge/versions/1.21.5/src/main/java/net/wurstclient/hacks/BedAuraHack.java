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

import java.util.Comparator;
import java.util.stream.StreamSupport;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hack.HackConflictGroup;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockPlacer;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.FakePlayerEntity;
import net.wurstclient.util.InventoryUtils;
import net.wurstclient.util.Rotation;
import net.wurstclient.util.RotationUtils;

@SearchTags({"bed aura", "BedAura", "bed bomb", "bed trap"})
public final class BedAuraHack extends Hack implements UpdateListener
{
	private static final Direction[] HORIZONTAL = {Direction.NORTH,
		Direction.EAST, Direction.SOUTH, Direction.WEST};

	private final SliderSetting range = new SliderSetting("Range",
		"How far to search for targets and to place beds.", 4.5, 1, 6, 0.1,
		ValueDisplay.DECIMAL);

	private final SliderSetting placeDelay = new SliderSetting("Place delay",
		"Ticks to wait between bed placements.", 9, 0, 20, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final SliderSetting detonateDelay = new SliderSetting(
		"Detonate delay", "Ticks to wait before right-clicking a placed bed.",
		2, 0, 20, 1, ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final CheckboxSetting autoSwitch = new CheckboxSetting(
		"Auto switch", "Selects a bed from your inventory when needed.", true);

	private BlockPos placedBed;
	private int placeTimer;
	private int detonateTimer;

	public BedAuraHack()
	{
		super("BedAura");
		setCategory(Category.COMBAT);
		addConflictGroup(HackConflictGroup.COMBAT_TARGETING);
		addSetting(range);
		addSetting(placeDelay);
		addSetting(detonateDelay);
		addSetting(autoSwitch);
	}

	@Override
	protected void onEnable()
	{
		placedBed = null;
		placeTimer = 0;
		detonateTimer = 0;
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		placedBed = null;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null || MC.player.isSpectator())
			return;

		if(MC.level.dimension() == Level.OVERWORLD)
		{
			ChatUtils.error("BedAura only works in the Nether or the End.");
			setEnabled(false);
			return;
		}

		if(MC.screen instanceof AbstractContainerScreen)
			return;

		if(placedBed != null)
		{
			handleDetonation();
			return;
		}

		if(placeTimer > 0)
		{
			placeTimer--;
			return;
		}

		Entity target = findTarget();
		if(target == null)
			return;

		BedPlacement placement = findPlacement(target);
		if(placement == null)
			return;

		if(!isHoldingBed())
		{
			if(!autoSwitch.isChecked())
				return;

			InventoryUtils.selectItem(
				stack -> stack.getItem() instanceof BedItem, 36);
			if(!isHoldingBed())
				return;
		}

		if(!placeBed(placement))
			return;

		MC.player.swing(InteractionHand.MAIN_HAND);
		placedBed = placement.foot().immutable();
		detonateTimer = detonateDelay.getValueI();
		placeTimer = placeDelay.getValueI();
	}

	private Entity findTarget()
	{
		double rangeSq = range.getValueSq();

		return StreamSupport
			.stream(MC.level.entitiesForRendering().spliterator(), false)
			.filter(e -> e instanceof LivingEntity living
				&& living.getHealth() > 0)
			.filter(e -> e != MC.player)
			.filter(e -> !e.isRemoved())
			.filter(e -> !(e instanceof FakePlayerEntity))
			.filter(e -> !WURST.getFriends().contains(e.getScoreboardName()))
			.filter(e -> MC.player.distanceToSqr(e) <= rangeSq)
			.min(Comparator.comparingDouble(e -> MC.player.distanceToSqr(e)))
			.orElse(null);
	}

	private BedPlacement findPlacement(Entity target)
	{
		double maxDistanceSq = range.getValueSq();
		Vec3 eyesPos = RotationUtils.getEyesPos();

		for(int y = 0; y <= 1; y++)
		{
			BlockPos head = target.blockPosition().above(y);

			for(Direction direction : HORIZONTAL)
			{
				BlockPos foot = head.relative(direction.getOpposite());

				if(!isReplaceable(head) || !isReplaceable(foot))
					continue;

				// beds need a solid block below their foot part
				if(isReplaceable(foot.below()))
					continue;

				if(eyesPos.distanceToSqr(Vec3.atCenterOf(foot)) > maxDistanceSq)
					continue;

				return new BedPlacement(foot, direction);
			}
		}

		return null;
	}

	private boolean placeBed(BedPlacement placement)
	{
		BlockPlacer.BlockPlacingParams params =
			BlockPlacer.getBlockPlacingParams(placement.foot());
		if(params == null || !params.lineOfSight())
			return false;

		// The bed's head part extends in the direction the player is facing,
		// so the server-side yaw must match the chosen direction.
		float pitch =
			RotationUtils.getNeededRotations(params.hitVec()).pitch();
		Rotation rotation = new Rotation(yawFor(placement.direction()), pitch);
		rotation.applyToClientPlayer();
		rotation.sendPlayerLookPacket();

		IMC.getInteractionManager().rightClickBlock(params.neighbor(),
			params.side(), params.hitVec());
		return true;
	}

	private void handleDetonation()
	{
		if(!(BlockUtils.getBlock(placedBed) instanceof BedBlock))
		{
			placedBed = null;
			return;
		}

		if(detonateTimer > 0)
		{
			detonateTimer--;
			return;
		}

		BlockPos bedPos = placedBed;
		placedBed = null;

		if(RotationUtils.getEyesPos()
			.distanceToSqr(Vec3.atCenterOf(bedPos)) > 4.5 * 4.5)
			return;

		boolean wasSneaking = MC.player.isShiftKeyDown();
		if(wasSneaking)
			MC.player.setShiftKeyDown(false);

		WURST.getRotationFaker().faceVectorPacket(Vec3.atCenterOf(bedPos));
		IMC.getInteractionManager().rightClickBlock(bedPos, Direction.UP,
			Vec3.atCenterOf(bedPos));
		MC.player.swing(InteractionHand.MAIN_HAND);

		if(wasSneaking)
			MC.player.setShiftKeyDown(true);
	}

	private boolean isHoldingBed()
	{
		return MC.player.getMainHandItem().getItem() instanceof BedItem;
	}

	private boolean isReplaceable(BlockPos pos)
	{
		return BlockUtils.getState(pos).canBeReplaced();
	}

	private float yawFor(Direction direction)
	{
		return switch(direction)
		{
			case NORTH -> 180F;
			case EAST -> -90F;
			case SOUTH -> 0F;
			case WEST -> 90F;
			default -> 0F;
		};
	}

	private record BedPlacement(BlockPos foot, Direction direction)
	{
	}
}
