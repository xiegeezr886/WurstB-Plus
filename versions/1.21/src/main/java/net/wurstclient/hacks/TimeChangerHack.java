/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
/* Ported from Meteor Client (GPL-3.0). */
package net.wurstclient.hacks;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.WorldChangeListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"time changer", "client time", "day time"})
public final class TimeChangerHack extends Hack
	implements UpdateListener, PacketInputListener, WorldChangeListener
{
	private final SliderSetting time = new SliderSetting("Time",
		"The time of day to display on the client.", 0, -20000, 20000, 100,
		ValueDisplay.INTEGER);

	private long oldGameTime;
	private long oldDayTime;
	private boolean hasOldTime;

	public TimeChangerHack()
	{
		super("TimeChanger");
		setCategory(Category.RENDER);
		addSetting(time);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PacketInputListener.class, this);
		EVENTS.add(WorldChangeListener.class, this);
		rememberTime(MC.level);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PacketInputListener.class, this);
		EVENTS.remove(WorldChangeListener.class, this);

		ClientLevel level = MC.level;
		if(level != null && hasOldTime)
		{
			level.getLevelData().setGameTime(oldGameTime);
			level.getLevelData().setDayTime(oldDayTime);
		}
		hasOldTime = false;
	}

	@Override
	public void onUpdate()
	{
		ClientLevel level = MC.level;
		if(level == null)
			return;

		long value = time.getValueI();
		level.getLevelData().setGameTime(value);
		level.getLevelData().setDayTime(value);
	}

	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		if(!(event.getPacket() instanceof ClientboundSetTimePacket packet))
			return;

		oldGameTime = packet.getGameTime();
		oldDayTime = packet.getDayTime();
		hasOldTime = true;
		event.cancel();
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		rememberTime(world);
	}

	private void rememberTime(ClientLevel level)
	{
		if(level == null)
		{
			hasOldTime = false;
			return;
		}

		oldGameTime = level.getLevelData().getGameTime();
		oldDayTime = level.getLevelData().getDayTime();
		hasOldTime = true;
	}
}
