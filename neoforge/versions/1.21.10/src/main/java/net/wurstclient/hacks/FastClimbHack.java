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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"fast climb", "fast ladder", "climb"})
public final class FastClimbHack extends Hack implements UpdateListener
{
	private final SliderSetting speed = new SliderSetting("Climb Speed",
		"Your climb speed.", 0.2872, 0, 1, 0.001, ValueDisplay.DECIMAL);

	public FastClimbHack()
	{
		super("FastClimb");
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
	}

	@Override
	public void onUpdate()
	{
		LocalPlayer player = MC.player;
		if(player == null || !isClimbing(player))
			return;

		Vec3 velocity = player.getDeltaMovement();
		player.setDeltaMovement(velocity.x, speed.getValue(), velocity.z);
	}

	private boolean isClimbing(LocalPlayer player)
	{
		boolean onClimbableBlock = player.onClimbable()
			|| (player.getInBlockState().is(Blocks.POWDER_SNOW)
				&& PowderSnowBlock.canEntityWalkOnPowderSnow(player));

		return onClimbableBlock
			&& (player.horizontalCollision || MC.options.keyJump.isDown());
	}
}
