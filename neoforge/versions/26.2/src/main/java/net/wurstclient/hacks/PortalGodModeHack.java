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

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.hack.Hack;

@SearchTags({"portal god mode", "portal godmode", "god mode", "portal"})
public final class PortalGodModeHack extends Hack
	implements PacketOutputListener
{
	private final List<ServerboundAcceptTeleportationPacket> packets =
		new ArrayList<>();

	public PortalGodModeHack()
	{
		super("PortalGodMode");
		setCategory(Category.OTHER);
	}

	@Override
	protected void onEnable()
	{
		packets.clear();
		EVENTS.add(PacketOutputListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(PacketOutputListener.class, this);

		if(MC.getConnection() != null && !packets.isEmpty())
			MC.getConnection().send(packets.get(packets.size() - 1));

		packets.clear();
	}

	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		if(!(event.getPacket() instanceof ServerboundAcceptTeleportationPacket packet))
			return;

		packets.add(packet);
		event.cancel();
	}
}
