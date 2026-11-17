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
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"auto jump", "autojump"})
public final class AutoJumpHack extends Hack implements UpdateListener
{
	private final EnumSetting<JumpWhen> jumpWhen = new EnumSetting<>("Jump when",
		"Condition for automatic jumps.", JumpWhen.values(), JumpWhen.OBSTACLE);
	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		Mode.values(), Mode.JUMP);
	private final SliderSetting velocityHeight = new SliderSetting("Velocity height",
		"Vertical velocity in Low hop mode.", 0.25, 0, 2, 0.01,
		ValueDisplay.DECIMAL);

	public AutoJumpHack()
	{
		super("AutoJump");
		setCategory(Category.MOVEMENT);
		addSetting(jumpWhen);
		addSetting(mode);
		addSetting(velocityHeight);
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
		LocalPlayer player = MC.player;
		if(player == null || MC.level == null || MC.screen != null
			|| WURST.getHax().freecamHack.isEnabled())
			return;

		if(!player.onGround())
			return;

		if(player.isShiftKeyDown() || MC.options.keyJump.isDown())
			return;

		if(player.isSpectator() || player.isPassenger()
			|| player.isFallFlying() || player.isInWater()
			|| player.isInLava() || player.onClimbable())
			return;

		boolean moving = player.zza != 0 || player.xxa != 0;
		boolean shouldJump = switch(jumpWhen.getSelected())
		{
			case OBSTACLE -> moving && player.horizontalCollision;
			case SPRINTING -> moving && player.isSprinting();
			case WALKING -> moving;
			case ALWAYS -> true;
		};
		if(!shouldJump)
			return;

		if(mode.getSelected() == Mode.JUMP)
			player.jumpFromGround();
		else
		{
			Vec3 velocity = player.getDeltaMovement();
			player.setDeltaMovement(velocity.x, velocityHeight.getValue(), velocity.z);
		}
	}

	private enum JumpWhen
	{
		OBSTACLE, SPRINTING, WALKING, ALWAYS
	}

	private enum Mode
	{
		JUMP, LOW_HOP
	}
}
