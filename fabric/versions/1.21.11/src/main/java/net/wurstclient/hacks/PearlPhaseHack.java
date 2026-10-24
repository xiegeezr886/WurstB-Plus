/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Ported from InvincibleMachineGun (GPL-3.0).
 */
package net.wurstclient.hacks;

import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.MovementPlanner;

@SearchTags({"pearl phase", "pearlphase", "phase", "pearl"})
public final class PearlPhaseHack extends Hack implements UpdateListener
{
	private final SliderSetting speed = new SliderSetting("Speed",
		"Movement speed multiplier while inside blocks.", 5, 0, 20, 0.5,
		ValueDisplay.DECIMAL);

	public PearlPhaseHack()
	{
		super("PearlPhase");
		setCategory(Category.MOVEMENT);
		addSetting(speed);
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

		if(MC.player != null)
		{
			MC.player.noPhysics = false;
			MC.player.fallDistance = 0;
		}
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		if(!isInsideBlock())
		{
			MC.player.noPhysics = false;
			return;
		}

		MC.player.noPhysics = true;
		MC.player.fallDistance = 0;
		MC.player.setOnGround(true);

		Vec2 move = MC.player.input.getMoveVector();
		if(!MovementPlanner.isMoving(move.y, move.x))
		{
			MC.player.setDeltaMovement(Vec3.ZERO);
			return;
		}

		Vec3 velocity = MovementPlanner.horizontalMotion(move.y, move.x,
			MC.player.getYRot(), 0.0001 * speed.getValue());
		MC.player.setDeltaMovement(velocity);
	}

	private boolean isInsideBlock()
	{
		return MC.level
			.getBlockCollisions(MC.player,
				MC.player.getBoundingBox().contract(0.001, 0.001, 0.001))
			.iterator().hasNext();
	}
}
