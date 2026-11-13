/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.macros;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class Macro
{
	private final String name;
	private final String key;
	private final List<String> commands;
	private boolean enabled = true;

	public Macro(String name, String key, List<String> commands)
	{
		this.name = Objects.requireNonNull(name);
		this.key = Objects.requireNonNull(key);
		this.commands = Collections
			.unmodifiableList(new ArrayList<>(commands));
	}

	public String getName()
	{
		return name;
	}

	public String getKey()
	{
		return key;
	}

	public List<String> getCommands()
	{
		return commands;
	}

	public boolean isEnabled()
	{
		return enabled;
	}

	public void setEnabled(boolean enabled)
	{
		this.enabled = enabled;
	}

	@Override
	public boolean equals(Object obj)
	{
		if(obj instanceof Macro other)
			return name.equals(other.name)
				&& key.equalsIgnoreCase(other.key);
		return false;
	}

	@Override
	public int hashCode()
	{
		return name.hashCode();
	}
}
