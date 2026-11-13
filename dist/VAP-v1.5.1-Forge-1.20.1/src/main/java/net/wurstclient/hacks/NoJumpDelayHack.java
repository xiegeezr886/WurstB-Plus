/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.lang.reflect.Field;
import net.minecraft.client.player.LocalPlayer;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;

@SearchTags({"no jump delay", "jump delay"})
public final class NoJumpDelayHack extends Hack implements UpdateListener
{
	private Field jumpDelayField;
	
	public NoJumpDelayHack()
	{
		super("NoJumpDelay");
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
		try
		{
			if(jumpDelayField == null)
				jumpDelayField = findJumpDelayField();
			
			if(jumpDelayField != null)
				jumpDelayField.setInt(MC.player, 0);
			
		}catch(Exception e)
		{
			// field not found in this mapping
		}
	}
	
	private Field findJumpDelayField()
	{
		String[] names =
			{"jumpDelay", "noJumpDelay", "jumpingCooldown", "autoJumpTime"};
		
		for(String name : names)
		{
			try
			{
				Field field = LocalPlayer.class.getDeclaredField(name);
				field.setAccessible(true);
				if(field.getType() == int.class)
					return field;
				
			}catch(Exception e)
			{
				// try next name
			}
		}
		
		return null;
	}
}
