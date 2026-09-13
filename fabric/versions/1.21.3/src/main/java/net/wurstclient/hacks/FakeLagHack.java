/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"fake lag", "fakelag", "lag switch"})
public final class FakeLagHack extends Hack
	implements UpdateListener, PacketOutputListener
{
	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"\u00a7lAlways\u00a7r - Never sends movement packets.\n"
			+ "\u00a7lPulse\u00a7r - Pulses packets every N seconds.",
		Mode.values(), Mode.ALWAYS);

	private final SliderSetting pulseInterval = new SliderSetting(
		"Pulse interval", "How often (in seconds) to flush packets.",
		1, 0.1, 5, 0.1, ValueDisplay.DECIMAL);

	private final List<ServerboundMovePlayerPacket> queue =
		new ArrayList<>();
	private long startTime;

	public FakeLagHack()
	{
		super("FakeLag");
		setCategory(Category.MOVEMENT);
		addSetting(mode);
		addSetting(pulseInterval);
	}

	@Override
	protected void onEnable()
	{
		startTime = System.currentTimeMillis();
		queue.clear();
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PacketOutputListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PacketOutputListener.class, this);
		sendPackets();
	}

	@Override
	public void onUpdate()
	{
		if(mode.getSelected() == Mode.PULSE)
		{
			long elapsed =
				System.currentTimeMillis() - startTime;
			if(elapsed > pulseInterval.getValue() * 1000)
			{
				sendPackets();
				startTime = System.currentTimeMillis();
			}
		}
	}

	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		if(event.getPacket() instanceof ServerboundMovePlayerPacket
			&& !(event.getPacket() instanceof ServerboundMovePlayerPacket.Rot))
		{
			queue.add((ServerboundMovePlayerPacket)event.getPacket());
			event.cancel();
		}
	}

	private void sendPackets()
	{
		List<ServerboundMovePlayerPacket> pending =
			new ArrayList<>(queue);
		queue.clear();
		for(ServerboundMovePlayerPacket p : pending)
			MC.player.connection.send(p);
	}

	private enum Mode
	{
		ALWAYS("Always"),
		PULSE("Pulse");

		private final String name;

		Mode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}
