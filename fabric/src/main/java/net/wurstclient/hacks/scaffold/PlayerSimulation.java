/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hacks.scaffold;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;

/**
 * OpenOpal's {@code PlayerSimulation}, adapted to 1.20.1 mappings. It clones
 * the local player into a client-side entity and advances it with
 * {@link net.minecraft.world.entity.LivingEntity#travel(Vec3)} so Scaffold can
 * look for a placement below where the player is about to fall.
 */
public final class PlayerSimulation
{
	private final RemotePlayer simulated;

	public PlayerSimulation(LocalPlayer player)
	{
		if(WurstClient.MC.level == null)
		{
			simulated = null;
			return;
		}

		GameProfile profile =
			new GameProfile(UUID.randomUUID(), "Simulated Player");
		simulated = new RemotePlayer(WurstClient.MC.level, profile) {};
		cloneStates(player);
	}

	private void cloneStates(LocalPlayer player)
	{
		simulated.setPos(player.position());
		simulated.setBoundingBox(player.getBoundingBox());
		simulated.setDeltaMovement(player.getDeltaMovement());
		simulated.setYRot(player.getYRot());
		simulated.setXRot(player.getXRot());
		simulated.setShiftKeyDown(player.isShiftKeyDown());
		simulated.setOnGround(player.onGround());
		simulated.setSprinting(player.isSprinting());
		simulated.setSpeed(player.getSpeed());
		simulated.fallDistance = player.fallDistance;
		for(MobEffectInstance effect : player.getActiveEffects())
			simulated.addEffect(new MobEffectInstance(effect));
	}

	public void simulateTick()
	{
		if(simulated == null)
			return;
		simulated.travel(new Vec3(simulated.xxa, simulated.yya,
			simulated.zza));
	}

	public RemotePlayer getSimulatedEntity()
	{
		return simulated;
	}
}
