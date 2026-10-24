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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hack.HackConflictGroup;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"arrow dodge", "ArrowDodge", "dodge arrows", "projectile dodge"})
public final class ArrowDodgeHack extends Hack implements UpdateListener
{
	private final SliderSetting moveSpeed = new SliderSetting("Move speed",
		"How fast to move when dodging a projectile.", 1, 0.05, 5, 0.05,
		ValueDisplay.DECIMAL);

	private final SliderSetting distanceCheck = new SliderSetting(
		"Distance check",
		"How close a projectile's predicted path may get before dodging.", 1,
		0.1, 5, 0.1, ValueDisplay.DECIMAL);

	private final CheckboxSetting groundCheck = new CheckboxSetting(
		"Ground check",
		"Only dodges to positions that still have solid ground below them.",
		true);

	private final CheckboxSetting allProjectiles = new CheckboxSetting(
		"All projectiles", "Dodges all projectiles, not only arrows.", false);

	private final CheckboxSetting ignoreOwn = new CheckboxSetting("Ignore own",
		"Ignores projectiles that you fired yourself.", true);

	private final SliderSetting simulationSteps = new SliderSetting(
		"Simulation steps", "How many ticks to simulate each projectile for.",
		40, 5, 200, 5, ValueDisplay.INTEGER);

	private final List<Vec3> directions = new ArrayList<>(
		Arrays.asList(new Vec3(1, 0, 1), new Vec3(0, 0, 1), new Vec3(-1, 0, 1),
			new Vec3(1, 0, 0), new Vec3(-1, 0, 0), new Vec3(1, 0, -1),
			new Vec3(0, 0, -1), new Vec3(-1, 0, -1)));

	public ArrowDodgeHack()
	{
		super("ArrowDodge");
		setCategory(Category.COMBAT);
		addConflictGroup(HackConflictGroup.MOVEMENT_CONTROL);
		addSetting(moveSpeed);
		addSetting(distanceCheck);
		addSetting(groundCheck);
		addSetting(allProjectiles);
		addSetting(ignoreOwn);
		addSetting(simulationSteps);
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
		if(MC.player == null || MC.level == null || MC.player.isSpectator()
			|| MC.player.isPassenger())
			return;

		ArrayList<Vec3> points = collectProjectilePoints();
		if(points.isEmpty())
			return;

		// no need to move if the current position is already safe
		if(isValidOffset(Vec3.ZERO, points))
			return;

		double speed = moveSpeed.getValue();
		for(int attempt = 0; attempt < 500; attempt++)
		{
			// shuffle so that the dodge direction is unpredictable
			Collections.shuffle(directions);

			for(Vec3 direction : directions)
			{
				Vec3 velocity = direction.scale(speed);
				if(!isValidOffset(velocity, points))
					continue;

				MC.player.setDeltaMovement(velocity.x, 0, velocity.z);
				return;
			}

			// try moving further away
			speed += moveSpeed.getValue();
		}
	}

	private ArrayList<Vec3> collectProjectilePoints()
	{
		ArrayList<Vec3> points = new ArrayList<>();
		AABB relevantArea = MC.player.getBoundingBox().inflate(6);
		int steps = simulationSteps.getValueI();

		for(Entity entity : MC.level.entitiesForRendering())
		{
			if(!(entity instanceof Projectile projectile) || entity.isRemoved())
				continue;

			boolean arrow = projectile instanceof Arrow
				|| projectile instanceof SpectralArrow;
			if(!allProjectiles.isChecked() && !arrow)
				continue;

			if(ignoreOwn.isChecked()
				&& projectile.getOwner() == MC.player)
				continue;

			Vec3 pos = projectile.position();
			Vec3 velocity = projectile.getDeltaMovement();

			for(int i = 0; i < steps; i++)
			{
				pos = pos.add(velocity);

				// simplified arrow physics: drag + gravity
				if(arrow)
					velocity = velocity.scale(0.99).add(0, -0.05, 0);

				if(relevantArea.contains(pos))
					points.add(pos);

				if(pos.y < MC.player.getY() - 6)
					break;
			}
		}

		return points;
	}

	private boolean isValidOffset(Vec3 offset, ArrayList<Vec3> points)
	{
		AABB dangerBox = MC.player.getBoundingBox().move(offset)
			.inflate(distanceCheck.getValue());

		for(Vec3 point : points)
			if(dangerBox.contains(point))
				return false;

		if(offset.equals(Vec3.ZERO))
			return true;

		BlockPos feet = MC.player.blockPosition()
			.offset(BlockPos.containing(offset.x, offset.y, offset.z));

		if(!isPassable(feet) || !isPassable(feet.above()))
			return false;

		return !groundCheck.isChecked() || !isPassable(feet.below());
	}

	private boolean isPassable(BlockPos pos)
	{
		return MC.level.getBlockState(pos)
			.getCollisionShape(MC.level, pos).isEmpty();
	}
}
