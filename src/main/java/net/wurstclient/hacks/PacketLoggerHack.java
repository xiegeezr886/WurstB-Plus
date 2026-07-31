/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.network.protocol.Packet;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.util.ChatUtils;

@SearchTags({"packet log", "network debug", "packet spy"})
public final class PacketLoggerHack extends Hack
	implements PacketInputListener, PacketOutputListener
{
	private final CheckboxSetting logReceived =
		new CheckboxSetting("Log received", true);
	private final CheckboxSetting logSent =
		new CheckboxSetting("Log sent", true);
	private final CheckboxSetting showData =
		new CheckboxSetting("Show data", false);

	private int packetCount;
	private long lastReset;

	public PacketLoggerHack()
	{
		super("PacketLogger");
		setCategory(Category.OTHER);
		addSetting(logReceived);
		addSetting(logSent);
		addSetting(showData);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(PacketInputListener.class, this);
		EVENTS.add(PacketOutputListener.class, this);
		packetCount = 0;
		lastReset = System.currentTimeMillis();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(PacketInputListener.class, this);
		EVENTS.remove(PacketOutputListener.class, this);
	}

	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		if(!logReceived.isChecked())
			return;
		logPacket("<-", event.getPacket());
	}

	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		if(!logSent.isChecked())
			return;
		logPacket("->", event.getPacket());
	}

	private void logPacket(String dir, Packet<?> packet)
	{
		packetCount++;
		if(packetCount > 100)
		{
			long elapsed = System.currentTimeMillis() - lastReset;
			if(elapsed < 1000)
				return;
			packetCount = 0;
			lastReset = System.currentTimeMillis();
		}

		String msg = dir + " " + packet.getClass().getSimpleName();
		if(showData.isChecked())
			msg += " " + packet.toString();
		ChatUtils.message(msg);
	}
}
