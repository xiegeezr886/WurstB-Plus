/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.keybinds;

import net.wurstclient.WurstClient;

public final class PossibleKeybind
{
	private final String command;
	private final String description;
	private final String translationKey;
	private final Object[] args;
	
	public PossibleKeybind(String command, String description)
	{
		this.command = command;
		this.description = description;
		translationKey = null;
		args = null;
	}

	/**
	 * Creates a keybind whose description is translated into the current
	 * language. The key doubles as the English text and may contain
	 * {@code %s} placeholders.
	 */
	public PossibleKeybind(String command, String key, Object... args)
	{
		this.command = command;
		description = key;
		translationKey = key;
		this.args = args;
	}
	
	public String getCommand()
	{
		return command;
	}
	
	public String getDescription()
	{
		return translationKey == null ? description
			: WurstClient.INSTANCE.translate(translationKey, args);
	}
}
