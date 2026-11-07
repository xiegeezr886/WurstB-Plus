/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from InvincibleMachineGun v1.9.0 (GPL-3.0).
package net.wurstclient.util;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;
import net.wurstclient.hacks.TPServerEquivalentHack;

/**
 * Simplified port of IMG's {@code GcoreTpUtil} movement helpers.
 *
 * <p>When the {@link TPServerEquivalentHack} gate is enabled for a module, the
 * teleport is split into several movement packets so that the server can
 * simulate the whole path instead of seeing one impossible jump. With the gate
 * disabled a single position packet is sent, which matches a plain TP.
 */
public final class ServerEquivTp
{
	private ServerEquivTp() {}

	public static void teleport(String module, Vec3 to, double step)
	{
		LocalPlayer player = WurstClient.MC.player;
		if(player == null)
			return;

		sendPath(module, player.position(), to, step);
		player.setPos(to.x, to.y, to.z);
		player.fallDistance = 0;
	}

	public static void sendPath(String module, Vec3 from, Vec3 to, double step)
	{
		if(WurstClient.MC.getConnection() == null)
			return;

		int packets;
		if(TPServerEquivalentHack.isEnabledFor(module) && step > 0.01)
		{
			double distance = from.distanceTo(to);
			packets = Math.max(1, (int)Math.ceil(distance / step));
			for(int i = 1; i <= packets; i++)
			{
				Vec3 pos = from.lerp(to, (double)i / packets);
				sendPos(pos.x, pos.y, pos.z, false);
			}
		}else
		{
			packets = 1;
			sendPos(to.x, to.y, to.z, false);
		}

		TPServerEquivalentHack.logDecision(module, from, to, packets);
	}

	public static void sendPos(double x, double y, double z, boolean onGround)
	{
		if(WurstClient.MC.getConnection() == null)
			return;
		WurstClient.MC.getConnection()
			.send(new ServerboundMovePlayerPacket.Pos(x, y, z, onGround));
	}

	public static boolean canStand(Vec3 pos)
	{
		if(WurstClient.MC.level == null)
			return false;
		AABB box = new AABB(pos.x - 0.3, pos.y, pos.z - 0.3, pos.x + 0.3,
			pos.y + 1.8, pos.z + 0.3);
		return WurstClient.MC.level.noCollision(box);
	}
}
