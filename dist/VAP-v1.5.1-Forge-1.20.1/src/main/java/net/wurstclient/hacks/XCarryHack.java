/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from InvincibleMachineGun (GPL-3.0).
package net.wurstclient.hacks;

import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.hack.Hack;

@SearchTags({"x carry", "crafting carry", "inventory carry", "keep items"})
public final class XCarryHack extends Hack implements PacketOutputListener
{
	public XCarryHack()
	{
		super("XCarry");
		setCategory(Category.ITEMS);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(PacketOutputListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(PacketOutputListener.class, this);
	}

	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		// syncId 0 is the player's own inventory. Not closing it server-side
		// lets items stay in the 2x2 crafting grid after the GUI is closed.
		if(event.getPacket() instanceof ServerboundContainerClosePacket packet
			&& packet.getContainerId() == 0)
			event.cancel();
	}
}
