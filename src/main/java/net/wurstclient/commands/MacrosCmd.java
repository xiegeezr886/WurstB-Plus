/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.commands;

import java.util.Arrays;
import java.util.List;

import net.wurstclient.DontBlock;
import net.wurstclient.command.CmdError;
import net.wurstclient.command.CmdException;
import net.wurstclient.command.CmdSyntaxError;
import net.wurstclient.command.Command;
import net.wurstclient.macros.Macro;
import net.wurstclient.util.ChatUtils;

@DontBlock
public final class MacrosCmd extends Command
{
	public MacrosCmd()
	{
		super("macros", "Allows you to manage command macros.",
			".macros add <name> <key> <commands>",
			".macros remove <name>", ".macros list [<page>]",
			"Multiple commands must be separated by ';'.",
			"Use _delay:<tick> between commands for pauses.");
	}

	@Override
	public void call(String[] args) throws CmdException
	{
		if(args.length < 1)
			throw new CmdSyntaxError();

		switch(args[0].toLowerCase())
		{
			case "add":
			add(args);
			break;

			case "remove":
			remove(args);
			break;

			case "list":
			list(args);
			break;

			default:
			throw new CmdSyntaxError();
		}
	}

	private void add(String[] args) throws CmdException
	{
		if(args.length < 4)
			throw new CmdSyntaxError(".macros add <name> <key> <commands>");

		String name = args[1];
		String key = args[2];
		String cmdsStr = String.join(" ", Arrays.copyOfRange(args, 3,
			args.length));
		List<String> commands = parseCommands(cmdsStr);

		WURST.getMacroManager().addMacro(new Macro(name, key, commands));
		ChatUtils.message("Macro added: " + name);
	}

	private void remove(String[] args) throws CmdException
	{
		if(args.length < 2)
			throw new CmdSyntaxError(".macros remove <name>");

		WURST.getMacroManager().removeMacro(args[1]);
		ChatUtils.message("Macro removed: " + args[1]);
	}

	private void list(String[] args) throws CmdException
	{
		int page = parsePage(args);
		List<Macro> macros = WURST.getMacroManager().getAllMacros();

		int pages = Math.max(1, (int)Math.ceil(macros.size() / 8.0));
		page = Math.max(0, Math.min(page, pages - 1));
		int rows = Math.min(8, Math.max(0, macros.size() - page * 8));

		ChatUtils.message("Total: " + macros.size()
			+ (macros.size() == 1 ? " macro" : " macros"));
		for(int i = page * 8; i < page * 8 + rows; i++)
		{
			Macro m = macros.get(i);
			String status = m.isEnabled() ? "" : "\u00a77[OFF]\u00a7r ";
			ChatUtils.message(status + m.getName() + ": " + m.getKey()
				+ " -> " + String.join(" ; ", m.getCommands()));
		}

		ChatUtils.message("Page: " + (page + 1) + "/" + pages);
	}

	private int parsePage(String[] args) throws CmdSyntaxError
	{
		if(args.length < 2)
			return 0;
		try
		{
			return Integer.parseInt(args[1]) - 1;
		}catch(NumberFormatException e)
		{
			throw new CmdSyntaxError("Invalid page number.");
		}
	}

	private static List<String> parseCommands(String cmds)
	{
		return Arrays.asList(cmds.split(";")).stream()
			.map(String::trim).filter(s -> !s.isEmpty()).toList();
	}
}
