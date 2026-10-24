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

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.ChatInputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.TextFieldSetting;
import net.wurstclient.util.ChatUtils;

@SearchTags({"auto tpa accept", "tpa", "teleport accept", "accept tpa",
	"tpa accept"})
public final class AutoTPAcceptHack extends Hack
	implements ChatInputListener, UpdateListener
{
	private final EnumSetting<Action> action =
		new EnumSetting<>("Action", Action.values(), Action.ACCEPT);

	private final TextFieldSetting keywords = new TextFieldSetting("Keywords",
		"Messages containing one of these keywords are scanned for clickable"
			+ " commands. Separate keywords with |.",
		"tpa|teleport|request");

	private final SliderSetting delay = new SliderSetting("Delay",
		"Ticks to wait before clicking the command.", 20, 0, 60, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final TextFieldSetting players = new TextFieldSetting("Players",
		"Only react when the message or command contains one of these names."
			+ " Separate names with |. Leave empty to react to everyone.",
		"");

	private final CheckboxSetting debug = new CheckboxSetting("Debug",
		"Shows extra information about matched messages in chat.", false);

	private final TextFieldSetting customKeywords = new TextFieldSetting(
		"Custom keywords",
		"Keywords for Custom mode, separated by |. Each keyword is paired with"
			+ " the command at the same position.",
		"").visibleWhen(() -> action.getSelected() == Action.CUSTOM);

	private final TextFieldSetting customCommands = new TextFieldSetting(
		"Custom commands",
		"Commands for Custom mode, separated by |. Each command is paired with"
			+ " the keyword at the same position.",
		"").visibleWhen(() -> action.getSelected() == Action.CUSTOM);

	private String pendingCommand;
	private int timer;

	public AutoTPAcceptHack()
	{
		super("AutoTPAccept");
		setCategory(Category.CHAT);
		addSetting(action);
		addSetting(keywords);
		addSetting(delay);
		addSetting(players);
		addSetting(debug);
		addSetting(customKeywords);
		addSetting(customCommands);
	}

	@Override
	protected void onEnable()
	{
		pendingCommand = null;
		timer = 0;
		EVENTS.add(ChatInputListener.class, this);
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(ChatInputListener.class, this);
		EVENTS.remove(UpdateListener.class, this);
		pendingCommand = null;
		timer = 0;
	}

	@Override
	public void onReceivedMessage(ChatInputEvent event)
	{
		Component message = event.getComponent();
		String raw = message.getString();
		String command = findCommand(message, raw);

		if(command == null || !isAllowedPlayer(raw + " " + command))
			return;

		pendingCommand = command;
		timer = delay.getValueI();

		if(debug.isChecked())
			ChatUtils.message("AutoTPAccept: queued /" + stripSlash(command));
	}

	private String findCommand(Component message, String raw)
	{
		if(action.getSelected() == Action.CUSTOM)
			return findCustomCommand(raw);

		if(!containsKeyword(raw))
			return null;

		List<String> found = new ArrayList<>();
		collectCommands(message, found);

		if(debug.isChecked())
			ChatUtils.message("AutoTPAccept: found commands " + found);

		boolean accept = action.getSelected() == Action.ACCEPT;
		for(String command : found)
		{
			String lower = command.toLowerCase(Locale.ROOT);
			boolean isAccept = lower.contains("accept") || lower.contains("yes")
				|| lower.contains("confirm");
			boolean isDeny = lower.contains("deny") || lower.contains("no")
				|| lower.contains("cancel") || lower.contains("reject");

			if(accept ? isAccept : isDeny)
				return command;
		}

		return null;
	}

	private String findCustomCommand(String raw)
	{
		List<String> keywordList = split(customKeywords.getValue());
		List<String> commandList = split(customCommands.getValue());
		int size = Math.min(keywordList.size(), commandList.size());

		for(int i = 0; i < size; i++)
			if(raw.contains(keywordList.get(i)))
				return commandList.get(i);

		return null;
	}

	private boolean containsKeyword(String raw)
	{
		for(String keyword : split(keywords.getValue()))
			if(raw.contains(keyword))
				return true;

		return false;
	}

	private boolean isAllowedPlayer(String text)
	{
		List<String> names = split(players.getValue());
		if(names.isEmpty())
			return true;

		for(String name : names)
			if(text.contains(name))
				return true;

		return false;
	}

	private static List<String> split(String value)
	{
		List<String> result = new ArrayList<>();
		for(String part : value.split("\\|"))
		{
			String entry = part.trim();
			if(!entry.isEmpty())
				result.add(entry);
		}
		return result;
	}

	private static void collectCommands(Component component, List<String> out)
	{
		ClickEvent click = component.getStyle().getClickEvent();
		if(click != null
			&& (click.getAction() == ClickEvent.Action.RUN_COMMAND
				|| click.getAction() == ClickEvent.Action.SUGGEST_COMMAND))
			out.add(click.getValue());

		for(Component sibling : component.getSiblings())
			collectCommands(sibling, out);
	}

	@Override
	public void onUpdate()
	{
		if(pendingCommand == null)
			return;

		if(MC.player == null || MC.getConnection() == null)
		{
			pendingCommand = null;
			return;
		}

		if(timer > 0)
		{
			timer--;
			return;
		}

		String command = pendingCommand;
		pendingCommand = null;
		MC.getConnection().sendCommand(stripSlash(command));

		if(debug.isChecked())
			ChatUtils.message("AutoTPAccept: ran /" + stripSlash(command));
	}

	private static String stripSlash(String command)
	{
		return command.startsWith("/") ? command.substring(1) : command;
	}

	private enum Action
	{
		ACCEPT("Accept"),
		DENY("Deny"),
		CUSTOM("Custom");

		private final String name;

		private Action(String name)
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
