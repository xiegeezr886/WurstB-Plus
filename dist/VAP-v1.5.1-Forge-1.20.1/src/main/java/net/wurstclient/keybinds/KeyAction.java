/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.keybinds;

public enum KeyAction
{
	TOGGLE,
	HOLD,
	SMART;

	public static KeyAction fromPrefix(String cmd)
	{
		if(cmd.startsWith("+"))
			return HOLD;
		if(cmd.startsWith("~"))
			return SMART;
		return TOGGLE;
	}

	public static String stripPrefix(String cmd)
	{
		if(cmd.startsWith("+") || cmd.startsWith("~"))
			return cmd.substring(1);
		return cmd;
	}
}
