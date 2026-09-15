/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.commands;

import net.wurstclient.command.CmdException;
import net.wurstclient.command.CmdSyntaxError;
import net.wurstclient.command.Command;
import net.wurstclient.twilight.TwilightShellScreen;
import net.wurstclient.util.ChatUtils;

/**
 * Opens the Twilight Echo style music interface.
 *
 * <p>
 * The port lives in {@code net.wurstclient.twilight} and deliberately does not
 * touch the previous music interface, so both can be opened side by side while
 * the port is being finished.
 */
public final class TwilightCmd extends Command
{
	public TwilightCmd()
	{
		super("twilight", "Opens the Twilight Echo style music interface.",
			".twilight");
	}
	
	@Override
	public void call(String[] args) throws CmdException
	{
		if(args.length > 0)
			throw new CmdSyntaxError();
		
		MC.setScreen(new TwilightShellScreen());
		ChatUtils.message("正在打开 Twilight Echo 界面…");
	}
}
