/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.Comparator;
import java.util.stream.StreamSupport;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.FakePlayerEntity;

@SearchTags({"target strafe", "strafe", "circle"})
public final class TargetStrafeHack extends Hack implements UpdateListener
{
	private final SliderSetting range = new SliderSetting("Range",
		"Distance to maintain from the target.", 2.5, 0.5, 6, 0.05,
		ValueDisplay.DECIMAL);

	private final SliderSetting speed = new SliderSetting("Speed",
		"Strafing speed multiplier.", 1, 0.1, 2, 0.05,
		ValueDisplay.DECIMAL);

	private final EnumSetting<Direction> direction =
		new EnumSetting<>("Direction", "Strafing direction around the target.",
			Direction.values(), Direction.RIGHT);

	private final CheckboxSetting autoJump = new CheckboxSetting("Auto jump",
		"Automatically jumps while strafing.", true);

	private final CheckboxSetting playersOnly = new CheckboxSetting(
		"Players only", "Only strafe around players.", false);

	private final CheckboxSetting ignoreFriends = new CheckboxSetting(
		"Ignore friends", "Doesn't strafe around your friends.", true);

	public TargetStrafeHack()
	{
		super("TargetStrafe");
		setCategory(Category.COMBAT);
		addSetting(range);
		addSetting(speed);
		addSetting(direction);
		addSetting(autoJump);
		addSetting(playersOnly);
		addSetting(ignoreFriends);
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
		if(MC.player == null || MC.level == null)
			return;

		Entity target = findTarget();
		if(target == null)
			return;

		double dx = target.getX() - MC.player.getX();
		double dz = target.getZ() - MC.player.getZ();
		double dist = Math.sqrt(dx * dx + dz * dz);
		double angleToTarget = Math.atan2(dz, dx);

		double moveAngle;
		if(dist > range.getValue() + 0.5)
			moveAngle = angleToTarget;
		else if(dist < range.getValue() - 0.5)
			moveAngle = angleToTarget + Math.PI;
		else
			moveAngle = angleToTarget
				+ (direction.getSelected() == Direction.RIGHT ? Math.PI / 2
					: -Math.PI / 2);

		double baseSpeed = 0.28 * speed.getValue();
		double motionX = -Math.sin(moveAngle) * baseSpeed;
		double motionZ = Math.cos(moveAngle) * baseSpeed;

		MC.player.setDeltaMovement(motionX, MC.player.getDeltaMovement().y,
			motionZ);

		if(autoJump.isChecked() && MC.player.onGround())
			MC.player.jumpFromGround();
	}

	private Entity findTarget()
	{
		return StreamSupport
			.stream(MC.level.entitiesForRendering().spliterator(), false)
			.filter(EntityUtils.IS_ATTACKABLE)
			.filter(e -> e instanceof LivingEntity living
				&& living.getHealth() > 0)
			.filter(e -> e != MC.player)
			.filter(e -> !(e instanceof FakePlayerEntity))
			.filter(e -> playersOnly.isChecked() ? e instanceof Player : true)
			.filter(e -> !ignoreFriends.isChecked()
				|| !WURST.getFriends().contains(e.getScoreboardName()))
			.min(Comparator.comparingDouble(e -> MC.player.distanceToSqr(e)))
			.orElse(null);
	}

	private enum Direction
	{
		RIGHT("Right"),
		LEFT("Left");

		private final String name;

		private Direction(String name)
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
