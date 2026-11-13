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

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.hack.Hack;

@SearchTags({"portal god mode", "portal godmode", "god mode", "portal"})
public final class PortalGodModeHack extends Hack
	implements PacketOutputListener
{
	private ServerboundAcceptTeleportationPacket lastPacket;
	private ClientPacketListener packetConnection;

	public PortalGodModeHack()
	{
		super("PortalGodMode");
		setCategory(Category.OTHER);
	}

	@Override
	protected void onEnable()
	{
		lastPacket = null;
		packetConnection = null;
		EVENTS.add(PacketOutputListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(PacketOutputListener.class, this);

		ServerboundAcceptTeleportationPacket packet = lastPacket;
		ClientPacketListener connection = packetConnection;
		lastPacket = null;
		packetConnection = null;
		if(connection != null && connection == MC.getConnection() && packet != null)
			connection.send(packet);
	}

	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		if(!isEnabled() || MC.getConnection() == null
			|| !(event.getPacket() instanceof ServerboundAcceptTeleportationPacket packet))
			return;

		lastPacket = packet;
		packetConnection = MC.getConnection();
		event.cancel();
	}
}
