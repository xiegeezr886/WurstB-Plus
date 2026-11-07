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
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"air jump", "airjump", "double jump"})
public final class AirJumpHack extends Hack implements UpdateListener
{
	private final SliderSetting jumpSpeed = new SliderSetting("Jump speed",
		"Upward velocity applied by each mid-air jump.", 0.42, 0.1, 2, 0.01,
		ValueDisplay.DECIMAL);

	private boolean wasJumpDown;

	public AirJumpHack()
	{
		super("AirJump");
		setCategory(Category.MOVEMENT);
		addSetting(jumpSpeed);
	}

	@Override
	protected void onEnable()
	{
		wasJumpDown = MC.options.keyJump.isDown();
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		wasJumpDown = false;
	}

	@Override
	public void onUpdate()
	{
		boolean jumpDown = MC.options.keyJump.isDown();
		boolean pressed = jumpDown && !wasJumpDown;
		wasJumpDown = jumpDown;

		LocalPlayer player = MC.player;
		if(player == null || MC.level == null || !pressed)
			return;

		if(player.onGround() || player.isSpectator() || player.isPassenger()
			|| player.isFallFlying() || player.getAbilities().flying
			|| player.isInWater() || player.isInLava()
			|| player.onClimbable())
			return;

		Vec3 velocity = player.getDeltaMovement();
		player.setDeltaMovement(velocity.x, jumpSpeed.getValue(), velocity.z);
	}
}
