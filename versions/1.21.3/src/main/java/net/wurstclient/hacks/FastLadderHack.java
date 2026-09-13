/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.util.MovementPlanner;

@SearchTags({"FastClimb", "fast ladder", "fast climb"})
public final class FastLadderHack extends Hack implements UpdateListener
{
	public FastLadderHack()
	{
		super("FastLadder");
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
		
		if(!player.onClimbable() || !player.horizontalCollision)
			return;
		
		Vec2 moveVector = MovementPlanner.getMoveVector(player.input);
		if(moveVector.y == 0 && moveVector.x == 0)
			return;
		
		Vec3 velocity = player.getDeltaMovement();
		player.setDeltaMovement(velocity.x, 0.2872, velocity.z);
	}
}
