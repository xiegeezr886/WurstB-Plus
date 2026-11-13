/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from InvincibleMachineGun (GPL-3.0).
package net.wurstclient.hacks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.TextFieldSetting;
import net.wurstclient.util.ChatUtils;

@SearchTags({"auto server", "auto login", "auto command", "server commands",
	"join commands"})
public final class AutoServerHack extends Hack implements UpdateListener
{
	private final TextFieldSetting serverAddress = new TextFieldSetting(
		"Server address",
		"Part of the server IP to match. Leave empty to run on any server.",
		"");

	private final TextFieldSetting commands = new TextFieldSetting("Commands",
		"Commands to run after joining, separated by |.\n"
			+ "The leading slash is optional.",
		"");

	private final SliderSetting initialDelay = new SliderSetting(
		"Initial delay", "Ticks to wait after a new connection before the first"
			+ " command.",
		40, 0, 200, 5, ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final SliderSetting commandInterval = new SliderSetting(
		"Command interval", "Ticks to wait between commands.", 20, 0, 100, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final CheckboxSetting showFeedback = new CheckboxSetting(
		"Show feedback", "Shows which commands were sent in chat.", true);

	private ClientPacketListener activeConnection;
	private final List<String> queue = new ArrayList<>();
	private boolean sessionScheduled;
	private int index;
	private int timer;

	public AutoServerHack()
	{
		super("AutoServer");
		setCategory(Category.CHAT);
		addSetting(serverAddress);
		addSetting(commands);
		addSetting(initialDelay);
		addSetting(commandInterval);
		addSetting(showFeedback);
	}

	@Override
	protected void onEnable()
	{
		reset();
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		reset();
	}

	@Override
	public void onUpdate()
	{
		ClientPacketListener connection = MC.getConnection();
		if(MC.player == null || MC.level == null || connection == null)
		{
			activeConnection = null;
			queue.clear();
			index = 0;
			return;
		}

		if(connection != activeConnection)
		{
			activeConnection = connection;
			queue.clear();
			sessionScheduled = false;
			index = 0;
			timer = 0;
		}

		// The server address can still be null for a few ticks after joining,
		// so keep retrying until it becomes available.
		if(!sessionScheduled && matchesCurrentServer())
		{
			sessionScheduled = true;
			queue.addAll(parseCommands());
			timer = initialDelay.getValueI();

			if(showFeedback.isChecked() && !queue.isEmpty())
				ChatUtils.message("AutoServer: running " + queue.size()
					+ " command(s) on this connection.");
		}

		if(queue.isEmpty() || index >= queue.size())
			return;

		if(timer > 0)
		{
			timer--;
			return;
		}

		String command = queue.get(index++).trim();
		if(!command.isEmpty())
		{
			sendCommand(command);
			if(showFeedback.isChecked())
				ChatUtils.message("AutoServer: " + redact(command));
		}

		if(index < queue.size())
			timer = commandInterval.getValueI();
	}

	private void reset()
	{
		activeConnection = null;
		queue.clear();
		sessionScheduled = false;
		index = 0;
		timer = 0;
	}

	private boolean matchesCurrentServer()
	{
		if(MC.getCurrentServer() == null)
			return false;

		String current = MC.getCurrentServer().ip == null ? ""
			: MC.getCurrentServer().ip.toLowerCase(Locale.ROOT);
		String configured = serverAddress.getValue().trim()
			.toLowerCase(Locale.ROOT);

		return configured.isEmpty() || current.contains(configured);
	}

	private List<String> parseCommands()
	{
		List<String> result = new ArrayList<>();
		for(String part : commands.getValue().split("\\|"))
		{
			String command = part.trim();
			if(!command.isEmpty())
				result.add(command);
		}
		return result;
	}

	private void sendCommand(String command)
	{
		String clean = command.startsWith("/") ? command.substring(1) : command;
		MC.getConnection().sendCommand(clean);
	}

	private String redact(String command)
	{
		if(command.matches("(?i)^/?login\\s+.*"))
			return "login ********";

		return command;
	}
}
