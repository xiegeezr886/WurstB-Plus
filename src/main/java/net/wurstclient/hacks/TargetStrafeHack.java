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

@SearchTags({"target strafe", "strafe", "circle"})
public final class TargetStrafeHack extends Hack implements UpdateListener
{
	private final SliderSetting range = new SliderSetting("Range",
		"Distance to maintain from the target.", 2.5, 0.5, 6, 0.05,
		ValueDisplay.DECIMAL);

	private final SliderSetting searchRange = new SliderSetting("Search range",
		"How far away a target can be before it's ignored."
			+ " KillAura's own target always wins while KillAura is on.",
		8, 1, 32, 0.5, ValueDisplay.DECIMAL.withSuffix(" blocks"));

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

	/** 参考侧 TargetStrafe 的 direction 会在撞墙时取反（`TargetStrafe.kt:19,67-69`）。 */
	private int directionSign = 1;
	private boolean wasColliding;

	public TargetStrafeHack()
	{
		super("TargetStrafe");
		setCategory(Category.COMBAT);
		addSetting(range);
		addSetting(searchRange);
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

		/*
		 * 撞墙时把环绕方向反过来（对应参考 `TargetStrafe.kt:31-35` 的 invertStrafe，
		 * 参考里 Scaffold 开着就不翻）。只在"刚撞上"的那一 tick 翻转，避免贴着墙来回抖。
		 */
		if(MC.player.horizontalCollision && !wasColliding
			&& !WURST.getHax().scaffoldWalkHack.isEnabled())
			directionSign = -directionSign;
		wasColliding = MC.player.horizontalCollision;

		double moveAngle;
		if(dist > range.getValue() + 0.5)
			moveAngle = angleToTarget;
		else if(dist < range.getValue() - 0.5)
			moveAngle = angleToTarget + Math.PI;
		else
		{
			double sign = direction.getSelected() == Direction.RIGHT ? 1 : -1;
			moveAngle = angleToTarget + sign * directionSign * (Math.PI / 2);
		}

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
		Entity killauraTarget = getKillauraTarget();
		if(killauraTarget != null)
			return killauraTarget;

		double searchRangeSq = searchRange.getValueSq();
		return StreamSupport
			.stream(MC.level.entitiesForRendering().spliterator(), false)
			.filter(EntityUtils.IS_ATTACKABLE)
			.filter(e -> e instanceof LivingEntity living
				&& living.getHealth() > 0)
			.filter(e -> playersOnly.isChecked() ? e instanceof Player : true)
			.filter(e -> !ignoreFriends.isChecked()
				|| !WURST.getFriends().contains(e.getScoreboardName()))
			.filter(e -> MC.player.distanceToSqr(e) <= searchRangeSq)
			.min(Comparator.comparingDouble(e -> MC.player.distanceToSqr(e)))
			.orElse(null);
	}

	/**
	 * 参考侧 TargetStrafe 只有在 KillAura 有目标时才工作（`TargetStrafe.kt:57-63` 的
	 * canStrafe() 要求 KillAura 开着且有 currentTarget）。本工程沿用同样的优先关系：
	 * KillAura 开着且有目标就围着它转，否则退回到"Search range 内最近的敌人"。
	 */
	private Entity getKillauraTarget()
	{
		KillauraHack killaura = WURST.getHax().killauraHack;
		if(!killaura.isEnabled())
			return null;

		Entity target = killaura.getCurrentTarget();
		if(target == null || !target.isAlive())
			return null;
		if(playersOnly.isChecked() && !(target instanceof Player))
			return null;
		if(ignoreFriends.isChecked()
			&& WURST.getFriends().contains(target.getScoreboardName()))
			return null;
		return target;
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
