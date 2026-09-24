/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.client.entity.ClientAvatarState;
import net.wurstclient.WurstClient;
import net.wurstclient.hacks.DankBobbingHack;

@Mixin(ClientAvatarState.class)
public abstract class ClientAvatarStateMixin
{
	/**
	 * Lets DankBobbing drive the view bob phase. 1.21.9 moved walkDist /
	 * walkDistO into this class as private fields, so the hack can no longer
	 * write walkDistO directly.
	 */
	@ModifyReturnValue(method = "getBackwardsInterpolatedWalkDistance(F)F",
		at = @At("RETURN"))
	private float onGetBackwardsInterpolatedWalkDistance(float original)
	{
		DankBobbingHack dankBobbing =
			WurstClient.INSTANCE.getHax().dankBobbingHack;
		if(!dankBobbing.shouldOverrideWalkDistance())
			return original;

		return dankBobbing.getForcedWalkDistance();
	}
}
