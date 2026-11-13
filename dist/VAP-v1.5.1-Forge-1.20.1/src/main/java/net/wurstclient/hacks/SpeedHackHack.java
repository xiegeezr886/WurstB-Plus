/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 * Copyright (c) 2015-2026 CCBlueX
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hack.HackConflictGroup;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.MovementPlanner;

@SearchTags({"speed hack", "bhop", "strafe"})
public final class SpeedHackHack extends Hack implements UpdateListener
{
	private static final double BASE_SPEED = 0.2873;

	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"\u00a7lNCP Bhop\u00a7r - Directional bunny hop.\n"
			+ "\u00a7lStrafe\u00a7r - Smoothly redirects current velocity.\n"
			+ "\u00a7lLowHop\u00a7r - Uses a short jump arc.\n"
			+ "\u00a7lOnGround\u00a7r - Only modifies grounded movement.\n"
			+ "\u00a7lBrutal\u00a7r - Direct high-speed bunny hop.",
		Mode.values(), Mode.LOW_HOP);

	private final SliderSetting speed = new SliderSetting("Speed",
		"Movement speed multiplier.", 2.0, 1.0, 10.0, 0.05,
		ValueDisplay.DECIMAL);

	private final SliderSetting strafeSpeed = new SliderSetting(
		"Strafe strength", "How quickly air movement follows input.", 0.4,
		0.01, 1.0, 0.01, ValueDisplay.DECIMAL);

	private final CheckboxSetting autoJump = new CheckboxSetting("Auto jump",
		"Automatically jumps while moving in air-capable modes.", true);

	private boolean lowHopActive;

	public SpeedHackHack()
	{
		super("SpeedHack");
		setCategory(Category.MOVEMENT);
		addConflictGroup(HackConflictGroup.MOVEMENT_CONTROL);
		addSetting(mode);
		addSetting(speed);
		addSetting(strafeSpeed);
		addSetting(autoJump);
	}

	@Override
	public String getRenderName()
	{
		return getName() + " [" + mode.getSelected() + "]";
	}

	@Override
	protected void onEnable()
	{
		lowHopActive = false;
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		lowHopActive = false;
	}

	@Override
	public void onUpdate()
	{
		LocalPlayer player = MC.player;
		if(!canControl(player))
		{
			lowHopActive = false;
			return;
		}

		float forward = player.input.forwardImpulse;
		float sideways = player.input.leftImpulse;
		if(!MovementPlanner.isMoving(forward, sideways))
		{
			lowHopActive = false;
			return;
		}
		if(mode.getSelected() != Mode.LOW_HOP)
			lowHopActive = false;

		double targetSpeed = BASE_SPEED * speed.getValue();
		switch(mode.getSelected())
		{
			case NCP_BHOP -> applyHop(player, forward, sideways, targetSpeed,
				0.42, 0.35);
			case STRAFE -> applyStrafe(player, forward, sideways, targetSpeed);
			case LOW_HOP -> applyLowHop(player, forward, sideways, targetSpeed);
			case ON_GROUND -> {
				if(player.onGround())
					player.setDeltaMovement(MovementPlanner.setHorizontal(
						player.getDeltaMovement(), forward, sideways,
						player.getYRot(), targetSpeed));
			}
			case BRUTAL -> applyHop(player, forward, sideways,
				Math.max(targetSpeed, speed.getValue() * 0.45), 0.42, 1);
		}
	}

	private boolean canControl(LocalPlayer player)
	{
		return player != null && !player.isShiftKeyDown() && !player.isPassenger()
			&& !player.onClimbable() && !player.isFallFlying()
			&& !player.isInWaterOrBubble() && !player.isInLava();
	}

	private void applyHop(LocalPlayer player, float forward, float sideways,
		double targetSpeed, double jumpMotion, double airStrength)
	{
		Vec3 movement = player.getDeltaMovement();
		if(player.onGround())
		{
			movement = MovementPlanner.setHorizontal(movement, forward, sideways,
				player.getYRot(), targetSpeed);
			if(autoJump.isChecked())
				movement = new Vec3(movement.x, jumpMotion, movement.z);
		}else
			movement = MovementPlanner.blendHorizontal(movement, forward, sideways,
				player.getYRot(), targetSpeed, airStrength);

		player.setDeltaMovement(
			MovementPlanner.clampHorizontal(movement, targetSpeed));
	}

	private void applyStrafe(LocalPlayer player, float forward, float sideways,
		double targetSpeed)
	{
		Vec3 movement = MovementPlanner.blendHorizontal(player.getDeltaMovement(),
			forward, sideways, player.getYRot(), targetSpeed,
			player.onGround() ? 1 : strafeSpeed.getValue());
		if(player.onGround() && autoJump.isChecked())
			movement = new Vec3(movement.x, 0.42, movement.z);
		player.setDeltaMovement(
			MovementPlanner.clampHorizontal(movement, targetSpeed));
	}

	private void applyLowHop(LocalPlayer player, float forward, float sideways,
		double targetSpeed)
	{
		Vec3 movement = MovementPlanner.blendHorizontal(player.getDeltaMovement(),
			forward, sideways, player.getYRot(), targetSpeed,
			player.onGround() ? 1 : strafeSpeed.getValue());
		if(player.onGround())
		{
			lowHopActive = autoJump.isChecked();
			if(lowHopActive)
				movement = new Vec3(movement.x, 0.2, movement.z);
		}else if(!autoJump.isChecked())
			lowHopActive = false;
		else if(lowHopActive && movement.y < -0.08)
			movement = new Vec3(movement.x, -0.08, movement.z);
		player.setDeltaMovement(
			MovementPlanner.clampHorizontal(movement, targetSpeed));
	}

	private enum Mode
	{
		NCP_BHOP("NCP Bhop"),
		STRAFE("Strafe"),
		LOW_HOP("LowHop"),
		ON_GROUND("OnGround"),
		BRUTAL("Brutal");

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
