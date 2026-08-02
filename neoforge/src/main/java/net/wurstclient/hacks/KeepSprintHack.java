/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.world.entity.player.Player;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.util.KeepSprintPolicy;

@SearchTags({"keep sprint"})
public final class KeepSprintHack extends Hack
{
	public KeepSprintHack()
	{
		super("KeepSprint");
		setCategory(Category.COMBAT);
	}

	public boolean shouldKeepSprint(Player player)
	{
		return KeepSprintPolicy.shouldPreserveSprint(isEnabled(),
			player == MC.player, player.isSprinting());
	}
}
