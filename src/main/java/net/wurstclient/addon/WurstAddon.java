/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.addon;

import net.wurstclient.hack.Hack;
import net.wurstclient.command.Command;
import net.wurstclient.command.BrigadierCommand;

public abstract class WurstAddon
{
	public abstract String getName();

	public abstract String getVersion();

	public abstract String getAuthor();

	public void onInitialize() {}

	public Hack[] getHacks()
	{
		return new Hack[0];
	}

	public Command[] getCommands()
	{
		return new Command[0];
	}

	public BrigadierCommand[] getBrigadierCommands()
	{
		return new BrigadierCommand[0];
	}
}
