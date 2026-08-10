/*
 * Copyright (c) 2015-2026 CCBlueX
 * Copyright (c) 2025-2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.client.player.LocalPlayer;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.util.AirJumpPolicy;
import net.wurstclient.util.AirJumpPolicy.State;

@SearchTags({"air jump", "double jump", "midair jump"})
public final class AirJumpHack extends Hack implements UpdateListener
{
	private final EnumSetting<Mode> mode =
		new EnumSetting<>("Mode", Mode.values(), Mode.FREE);

	private boolean jumpWasDown;
	private boolean doubleJumpAvailable;

	public AirJumpHack()
	{
		super("AirJump");
		setCategory(Category.MOVEMENT);
		addSetting(mode);
	}

	@Override
	protected void onEnable()
	{
		jumpWasDown = MC.options.keyJump.isDown();
		doubleJumpAvailable = true;
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		jumpWasDown = false;
		doubleJumpAvailable = true;
	}

	@Override
	public void onUpdate()
	{
		LocalPlayer player = MC.player;
		if(player == null)
		{
			jumpWasDown = MC.options.keyJump.isDown();
			return;
		}

		if(player.onGround())
			doubleJumpAvailable = true;
		boolean jumpDown = MC.options.keyJump.isDown();
		boolean pressedEdge = jumpDown && !jumpWasDown;
		jumpWasDown = jumpDown;

		boolean doubleMode = mode.getSelected() == Mode.DOUBLE;
		State state = new State(pressedEdge, player.onGround(), doubleMode,
			doubleJumpAvailable, player.isSpectator(), player.isPassenger(),
			player.getAbilities().flying, player.isFallFlying(),
			player.isInWaterOrBubble() || player.isInLava(), player.onClimbable());
		if(!AirJumpPolicy.canJump(state))
			return;

		boolean wasOnGround = player.onGround();
		player.jumpFromGround();
		player.setOnGround(false);
		if(doubleMode && !wasOnGround)
			doubleJumpAvailable = false;
	}

	private enum Mode
	{
		FREE("Jump freely"),
		DOUBLE("Double jump");

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
