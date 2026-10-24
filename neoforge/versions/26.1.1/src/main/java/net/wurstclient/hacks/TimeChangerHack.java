/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
/* Ported from Meteor Client (GPL-3.0). */
package net.wurstclient.hacks;

import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.world.clock.ClockNetworkState;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.WorldChangeListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"time changer", "client time", "day time"})
public final class TimeChangerHack extends Hack implements UpdateListener,
	PacketInputListener, WorldChangeListener
{
	private final SliderSetting time = new SliderSetting("Time",
		"The time of day to display on the client.", 0, -20000, 20000, 100,
		ValueDisplay.INTEGER);

	private long oldTime;
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
			applyTime(level, oldTime);
		hasOldTime = false;
	}

	@Override
	public void onUpdate()
	{
		ClientLevel level = MC.level;
		if(level == null)
			return;

		applyTime(level, time.getValueI());
	}

	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		if(!(event.getPacket() instanceof ClientboundSetTimePacket))
			return;

		// Keep our own time instead of letting the server overwrite it.
		event.cancel();
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		rememberTime(world);
	}

	/**
	 * Sets the overworld clock to the given time. 26.x no longer stores the
	 * day time in {@code LevelData}; it lives in the client clock manager.
	 */
	private void applyTime(ClientLevel level, long value)
	{
		Registry<WorldClock> registry = level.registryAccess()
			.lookup(Registries.WORLD_CLOCK).orElse(null);
		if(registry == null)
			return;

		Holder<WorldClock> clock =
			registry.get(WorldClocks.OVERWORLD).orElse(null);
		if(clock == null)
			return;

		level.clockManager().handleUpdates(level.getGameTime(),
			Map.of(clock, new ClockNetworkState(value, 0F, 1F)));
	}

	private void rememberTime(ClientLevel level)
	{
		if(level == null)
		{
			hasOldTime = false;
			return;
		}

		oldTime = level.getOverworldClockTime();
		hasOldTime = true;
	}
}
