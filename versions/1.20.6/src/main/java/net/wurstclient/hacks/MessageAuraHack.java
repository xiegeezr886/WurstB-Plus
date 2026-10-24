/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
// Ported from Meteor Client (GPL-3.0).
package net.wurstclient.hacks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.TextFieldSetting;

@SearchTags({"message aura", "AutoMessage", "auto message", "chat timer",
	"message timer", "advertise", "chat spam"})
public final class MessageAuraHack extends Hack implements UpdateListener
{
	private final TextFieldSetting messages = new TextFieldSetting("Messages",
		"Messages to send, separated by |.\n"
			+ "Messages starting with / are sent as commands.",
		"WurstB+ Plus on top!");

	private final SliderSetting delay = new SliderSetting("Delay",
		"Ticks to wait between messages.", 100, 20, 7200, 20,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final CheckboxSetting randomOrder = new CheckboxSetting(
		"Random order", "Sends the messages in a random order each cycle.",
		true);

	private final CheckboxSetting disableOnDisconnect = new CheckboxSetting(
		"Disable on disconnect",
		"Disables this hack when you leave the server.", true);

	private final ArrayDeque<String> queue = new ArrayDeque<>();
	private int timer;

	public MessageAuraHack()
	{
		super("MessageAura");
		setCategory(Category.CHAT);
		addSetting(messages);
		addSetting(delay);
		addSetting(randomOrder);
		addSetting(disableOnDisconnect);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		queue.clear();
		timer = 0;
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		queue.clear();
		timer = 0;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null || MC.getConnection() == null)
		{
			if(disableOnDisconnect.isChecked())
				setEnabled(false);
			return;
		}

		if(timer > 0)
		{
			timer--;
			return;
		}

		if(queue.isEmpty())
			fillQueue();

		String message = queue.poll();
		if(message == null)
			return;

		// Prevent getting kicked for sending an oversized message.
		if(message.length() > 256)
			message = message.substring(0, 256);

		ClientPacketListener connection = MC.getConnection();
		if(message.startsWith("/"))
			connection.sendCommand(message.substring(1));
		else
			connection.sendChat(message);

		timer = delay.getValueI();
	}

	private void fillQueue()
	{
		List<String> parsed = new ArrayList<>();

		for(String part : messages.getValue().split("\\|"))
		{
			String message = part.trim();
			if(!message.isEmpty())
				parsed.add(message);
		}

		if(parsed.isEmpty())
			return;

		if(randomOrder.isChecked())
			Collections.shuffle(parsed);

		queue.addAll(parsed);
	}
}
