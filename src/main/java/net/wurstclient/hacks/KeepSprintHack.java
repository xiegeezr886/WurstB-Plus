/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;

@SearchTags({"keep sprint"})
public final class KeepSprintHack extends Hack
{
	public KeepSprintHack()
	{
		super("KeepSprint");
		setCategory(Category.COMBAT);
	}

	public void applySprint()
	{
		LocalPlayer player = MC.player;
		if(!canMaintainSprint(player) || player.isSprinting())
			return;
		player.setSprinting(true);
	}

	public boolean shouldKeepSprint(Player player)
	{
		return isEnabled() && player == MC.player && player.isSprinting();
	}

	private boolean canMaintainSprint(LocalPlayer player)
	{
		return isEnabled() && player != null && !player.isPassenger()
			&& !player.isFallFlying() && !player.getAbilities().flying
			&& !player.isShiftKeyDown() && player.input.hasForwardImpulse();
	}
}
