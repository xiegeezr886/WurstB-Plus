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
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.KeyPressListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"air jump", "airjump", "double jump"})
public final class AirJumpHack extends Hack implements UpdateListener, KeyPressListener
{
	private final SliderSetting jumpSpeed = new SliderSetting("Jump speed",
		"Upward velocity applied by each mid-air jump.", 0.42, 0.1, 2, 0.01,
		ValueDisplay.DECIMAL);

	private boolean wasJumpDown;
	private boolean wasSneakDown;
	private boolean jumpHandled;
	private boolean sneakHandled;
	private LocalPlayer statePlayer;
	private ClientLevel stateWorld;
	private int level;
	private final CheckboxSetting maintainLevel = new CheckboxSetting("Maintain level",
		"Hold jump to keep the last jump level; press sneak to lower it.", false);
	private final CheckboxSetting nativeJump = new CheckboxSetting("Native jump",
		"Use vanilla jump motion, including sprint and jump effects.", false);

	public AirJumpHack()
	{
		super("AirJump");
		setCategory(Category.MOVEMENT);
		addSetting(jumpSpeed);
		addSetting(maintainLevel);
		addSetting(nativeJump);
	}

	@Override
	protected void onEnable()
	{
		statePlayer = null;
		refreshState();
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(KeyPressListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(KeyPressListener.class, this);
		statePlayer = null;
		stateWorld = null;
		jumpHandled = sneakHandled = false;
	}

	private void refreshState()
	{
		if(statePlayer == MC.player && stateWorld == MC.level)
			return;
		statePlayer = MC.player;
		stateWorld = MC.level;
		level = statePlayer == null ? 0 : statePlayer.blockPosition().getY();
		wasJumpDown = MC.options.keyJump.isDown();
		wasSneakDown = MC.options.keyShift.isDown();
		jumpHandled = sneakHandled = false;
	}

	@Override
	public void onKeyPress(KeyPressEvent event)
	{
		refreshState();
		if(event.getAction() != InputConstants.PRESS || !canJump())
			return;
		if(MC.options.keyJump.matches(new KeyEvent(event.getKeyCode(), event.getScanCode(), event.getModifiers())))
		{
			level = MC.player.blockPosition().getY();
			jump();
			jumpHandled = true;
		}
		else if(MC.options.keyShift.matches(new KeyEvent(event.getKeyCode(), event.getScanCode(), event.getModifiers())))
		{
			level--;
			sneakHandled = true;
		}
	}

	@Override
	public void onUpdate()
	{
		refreshState();
		boolean jumpDown = MC.options.keyJump.isDown();
		boolean sneakDown = MC.options.keyShift.isDown();
		boolean pressed = jumpDown && !wasJumpDown && !jumpHandled;
		boolean lowerLevel = sneakDown && !wasSneakDown && !sneakHandled;
		boolean alreadyJumped = jumpHandled;
		wasJumpDown = jumpDown;
		wasSneakDown = sneakDown;
		jumpHandled = sneakHandled = false;

		LocalPlayer player = MC.player;
		if(player != null && player.onGround())
			level = player.blockPosition().getY();
		if(!canJump())
			return;

		if(lowerLevel)
			level--;
		if(pressed)
		{
			level = player.blockPosition().getY();
			jump();
		}
		else if(!alreadyJumped && maintainLevel.isChecked() && jumpDown
			&& player.blockPosition().getY() <= level && player.getDeltaMovement().y <= 0)
			jump();
	}

	private boolean canJump()
	{
		LocalPlayer player = MC.player;
		if(player == null || MC.level == null || MC.screen != null
			|| WURST.getHax().freecamHack.isEnabled())
			return false;
		return !(player.onGround() || player.isSpectator() || player.isPassenger()
			|| player.isFallFlying() || player.getAbilities().flying
			|| player.isInWater() || player.isInLava()
			|| player.onClimbable());
	}

	private void jump()
	{
		LocalPlayer player = MC.player;
		if(nativeJump.isChecked())
		{
			player.jumpFromGround();
			return;
		}
		Vec3 velocity = player.getDeltaMovement();
		player.setDeltaMovement(velocity.x, jumpSpeed.getValue(), velocity.z);
	}
}
