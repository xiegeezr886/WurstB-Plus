/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
/*
 * Ported from Meteor Client (GPL-3.0).
 */
package net.wurstclient.hacks;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.MovementPlanner;

@SearchTags({"long jump", "longjump"})
public final class LongJumpHack extends Hack implements UpdateListener
{
	private final SliderSetting boostFactor = new SliderSetting(
		"Boost Factor", "The amount by which to boost the jump.", 1.261,
		0.1, 5, 0.001, ValueDisplay.DECIMAL);

	private final CheckboxSetting disableAfterJump = new CheckboxSetting(
		"Disable after jump",
		"Automatically disables the hack after boosting a jump.", false);

	private boolean wasOnGround;

	public LongJumpHack()
	{
		super("LongJump");
		setCategory(Category.MOVEMENT);
		addSetting(boostFactor);
		addSetting(disableAfterJump);
	}

	@Override
	protected void onEnable()
	{
		wasOnGround = MC.player != null && MC.player.onGround();
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		wasOnGround = false;
	}

	@Override
	public void onUpdate()
	{
		LocalPlayer player = MC.player;
		if(player == null || MC.level == null)
			return;

		if(player.isSpectator() || player.isPassenger()
			|| player.isFallFlying())
		{
			wasOnGround = player.onGround();
			return;
		}

		boolean onGround = player.onGround();
		Vec3 velocity = player.getDeltaMovement();

		if(!onGround && wasOnGround && velocity.y > 0
			&& MC.options.keyJump.isDown())
			boost(player, velocity);

		wasOnGround = onGround;
	}

	private void boost(LocalPlayer player, Vec3 velocity)
	{
		float forward = player.input.forwardImpulse;
		float sideways = player.input.leftImpulse;

		if(!MovementPlanner.isMoving(forward, sideways))
			return;

		Vec3 movement = MovementPlanner.horizontalMotion(forward,
			sideways, player.getYRot(), boostFactor.getValue());
		player.setDeltaMovement(movement.x, velocity.y, movement.z);

		if(disableAfterJump.isChecked())
			setEnabled(false);
	}
}
