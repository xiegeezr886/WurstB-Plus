/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import java.util.List;

import net.minecraft.client.multiplayer.ServerData;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.ChatInputListener;
import net.wurstclient.events.ChatInputListener.ChatInputEvent;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.TextFieldSetting;

@SearchTags({"auto gg", "autogg", "gg", "hypixel", "end game"})
public final class AutoGGHack extends Hack
	implements ChatInputListener, UpdateListener
{
	private static final List<String> WIN_MARKERS = List.of(
		"1st Killer -", "1st Place -", "Winner:", " - Damage Dealt -",
		"Winning Team -", "1st -", "Winners:", "Winning Team:",
		" won the game!", "Top Seeker:", "1st Place:",
		"Last team standing!", "Winner #1 (", "Top Survivors",
		"Winners -", "Sumo Duel -", "Most Wool Placed -",
		"Your Overall Winstreak:");

	private final SliderSetting delay = new SliderSetting("Delay",
		"Seconds to wait before sending the message.", 0, 0, 5, 1,
		ValueDisplay.INTEGER.withSuffix("s"));

	private final TextFieldSetting message = new TextFieldSetting("Message",
		"Message sent after a win.", "gg");

	private final CheckboxSetting hypixelOnly = new CheckboxSetting(
		"Hypixel only", "Only sends on Hypixel.", true);

	private int pendingTicks;
	private long lastSent;

	public AutoGGHack()
	{
		super("AutoGG");
		setCategory(Category.CHAT);
		addSetting(delay);
		addSetting(message);
		addSetting(hypixelOnly);
	}

	@Override
	protected void onEnable()
	{
		pendingTicks = 0;
		lastSent = 0;
		EVENTS.add(ChatInputListener.class, this);
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(ChatInputListener.class, this);
		EVENTS.remove(UpdateListener.class, this);
		pendingTicks = 0;
	}

	@Override
	public void onReceivedMessage(ChatInputEvent event)
	{
		if(pendingTicks > 0 || event.getComponent() == null)
			return;

		ServerData server = MC.getCurrentServer();
		if(hypixelOnly.isChecked() && (server == null || server.ip == null
			|| !server.ip.toLowerCase().contains("hypixel")))
			return;

		String text = event.getComponent().getString();
		for(String marker : WIN_MARKERS)
			if(text.contains(marker))
			{
				pendingTicks = Math.max(1,
					delay.getValueI() * 20);
				break;
			}
	}

	@Override
	public void onUpdate()
	{
		if(pendingTicks <= 0)
			return;

		if(--pendingTicks > 0)
			return;

		if(System.currentTimeMillis() - lastSent < 3000)
			return;

		String text = message.getValue();
		if(text == null || text.isBlank() || MC.getConnection() == null)
			return;

		if(text.startsWith("/"))
			MC.getConnection().sendCommand(text.substring(1));
		else
			MC.getConnection().sendChat(text);

		lastSent = System.currentTimeMillis();
	}
}
