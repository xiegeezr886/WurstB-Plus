/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.lang.reflect.Method;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PlayerAttacksEntityListener;
import net.wurstclient.hack.Hack;

@SearchTags({"delay remover", "no cooldown", "attack speed"})
public final class DelayRemoverHack extends Hack
	implements PlayerAttacksEntityListener
{
	private Method resetAttackStrengthTicker;
	
	public DelayRemoverHack()
	{
		super("DelayRemover");
		setCategory(Category.COMBAT);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(PlayerAttacksEntityListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(PlayerAttacksEntityListener.class, this);
	}
	
	@Override
	public void onPlayerAttacksEntity(Entity target)
	{
		try
		{
			if(resetAttackStrengthTicker == null)
			{
				resetAttackStrengthTicker = LocalPlayer.class
					.getDeclaredMethod("resetAttackStrengthTicker");
				resetAttackStrengthTicker.setAccessible(true);
			}
			
			resetAttackStrengthTicker.invoke(MC.player);
			
		}catch(Exception e)
		{
			// method not found in this mapping
		}
	}
}
