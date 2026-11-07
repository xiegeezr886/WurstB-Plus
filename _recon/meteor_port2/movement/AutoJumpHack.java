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
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;

@SearchTags({"auto jump", "autojump"})
public final class AutoJumpHack extends Hack implements UpdateListener
{
	public AutoJumpHack()
	{
		super("AutoJump");
		setCategory(Category.MOVEMENT);
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
		if(player == null || MC.level == null)
			return;

		// only jump while moving into an obstacle
		if(!player.onGround() || !player.horizontalCollision)
			return;

		if(player.isShiftKeyDown() || MC.options.keyJump.isDown())
			return;

		if(player.isSpectator() || player.isPassenger()
			|| player.isFallFlying() || player.isInWater()
			|| player.isInLava() || player.onClimbable())
			return;

		if(player.zza == 0 && player.xxa == 0)
			return;

		player.jumpFromGround();
	}
}
