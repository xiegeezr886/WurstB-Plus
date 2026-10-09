/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public abstract class BrigadierCommand
{
	private final String name;
	private final String description;

	protected BrigadierCommand(String name, String description)
	{
		this.name = name;
		this.description = description;
	}

	public String getName()
	{
		return name;
	}

	public String getDescription()
	{
		return description;
	}

	public abstract void build(
		LiteralArgumentBuilder<CommandSourceStack> builder);

	public void register(
		CommandDispatcher<CommandSourceStack> dispatcher)
	{
		LiteralArgumentBuilder<CommandSourceStack> builder =
			Commands.literal(name);
		build(builder);
		dispatcher.register(builder);
	}
}
