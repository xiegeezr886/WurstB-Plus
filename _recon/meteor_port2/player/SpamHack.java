/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
// Ported from Meteor Client (GPL-3.0).
package net.wurstclient.hacks;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.TextFieldSetting;

@SearchTags({"spammer", "auto spam", "chat spam", "advertise"})
public final class SpamHack extends Hack implements UpdateListener
{
	private final TextFieldSetting message = new TextFieldSetting("Message",
		"The chat message to send repeatedly.\n\n"
			+ "Messages starting with \"/\" are sent as commands.",
		"WurstB+ Plus on top!");

	private final SliderSetting delay = new SliderSetting("Delay",
		"Delay between messages in ticks.", 20, 1, 200, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final CheckboxSetting disableOnDisconnect = new CheckboxSetting(
		"Disable on disconnect",
		"Disables this hack when you leave the server.", true);

	private int timer;

	public SpamHack()
	{
		super("Spam");
		setCategory(Category.CHAT);
		addSetting(message);
		addSetting(delay);
		addSetting(disableOnDisconnect);
	}

	@Override
	protected void onEnable()
	{
		timer = delay.getValueI();
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
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

		String text = message.getValue().trim();
		if(text.isEmpty())
			return;

		// Prevent getting kicked for sending an oversized message.
		if(text.length() > 256)
			text = text.substring(0, 256);

		ClientPacketListener connection = MC.getConnection();
		if(text.startsWith("/"))
			connection.sendCommand(text.substring(1));
		else
			connection.sendChat(text);

		timer = delay.getValueI();
	}
}
