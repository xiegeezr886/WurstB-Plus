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
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.WorldChangeListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"weather changer", "client weather", "rain", "thunder"})
public final class WeatherChangerHack extends Hack
	implements UpdateListener, PacketInputListener, WorldChangeListener
{
	private final SliderSetting rainLevel = new SliderSetting("Rain level",
		"The rain level to display on the client.", 0, 0, 1, 0.01,
		ValueDisplay.PERCENTAGE);

	private final SliderSetting thunderLevel =
		new SliderSetting("Thunder level",
			"The thunder level to display on the client.", 0, 0, 1, 0.01,
			ValueDisplay.PERCENTAGE);

	private float oldRainLevel;
	private float oldThunderLevel;
	private boolean hasOldWeather;

	public WeatherChangerHack()
	{
		super("WeatherChanger");
		setCategory(Category.RENDER);
		addSetting(rainLevel);
		addSetting(thunderLevel);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PacketInputListener.class, this);
		EVENTS.add(WorldChangeListener.class, this);
		rememberWeather(MC.level);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PacketInputListener.class, this);
		EVENTS.remove(WorldChangeListener.class, this);

		ClientLevel level = MC.level;
		if(level != null && hasOldWeather)
		{
			level.setRainLevel(oldRainLevel);
			level.setThunderLevel(oldThunderLevel);
		}
		hasOldWeather = false;
	}

	@Override
	public void onUpdate()
	{
		ClientLevel level = MC.level;
		if(level == null)
			return;

		level.setRainLevel(rainLevel.getValueF());
		level.setThunderLevel(thunderLevel.getValueF());
	}

	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		if(!(event.getPacket() instanceof ClientboundGameEventPacket packet))
			return;

		ClientboundGameEventPacket.Type type = packet.getEvent();
		if(type != ClientboundGameEventPacket.START_RAINING
			&& type != ClientboundGameEventPacket.STOP_RAINING
			&& type != ClientboundGameEventPacket.RAIN_LEVEL_CHANGE
			&& type != ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE)
			return;

		if(type == ClientboundGameEventPacket.RAIN_LEVEL_CHANGE)
			oldRainLevel = packet.getParam();
		else if(type == ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE)
			oldThunderLevel = packet.getParam();

		hasOldWeather = true;
		event.cancel();
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		rememberWeather(world);
	}

	private void rememberWeather(ClientLevel level)
	{
		if(level == null)
		{
			hasOldWeather = false;
			return;
		}

		oldRainLevel = level.getRainLevel(1F);
		oldThunderLevel = level.getThunderLevel(1F);
		hasOldWeather = true;
	}
}
