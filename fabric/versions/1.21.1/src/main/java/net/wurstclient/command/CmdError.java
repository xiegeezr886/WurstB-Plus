/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.command;

import net.wurstclient.WurstClient;
import net.wurstclient.util.ChatUtils;

public final class CmdError extends CmdException
{
	public CmdError(String message)
	{
		super(message);
	}
	
	/**
	 * Builds a translated error message. The English text doubles as the
	 * translation key and may contain {@code %s} placeholders.
	 */
	public CmdError(String key, Object... args)
	{
		super(WurstClient.INSTANCE.translate(key, args));
	}

	@Override
	public void printToChat(Command cmd)
	{
		ChatUtils.error(getMessage());
	}
}
