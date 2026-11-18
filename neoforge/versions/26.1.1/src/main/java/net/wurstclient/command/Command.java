/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.command;

import java.util.Objects;

import net.wurstclient.Category;
import net.wurstclient.Feature;
import net.wurstclient.util.ChatUtils;

public abstract class Command extends Feature
{
	private final String name;
	private final String description;
	private final String[] syntax;
	private Category category;
	
	public Command(String name, String description, String... syntax)
	{
		this.name = Objects.requireNonNull(name);
		this.description = Objects.requireNonNull(description);
		
		// Commands are created before the translator. Keep raw syntax until
		// display time so startup and subsequent language changes both work.
		this.syntax = Objects.requireNonNull(syntax).clone();
	}
	
	public abstract void call(String[] args) throws CmdException;
	
	@Override
	public final String getName()
	{
		return "." + name;
	}
	
	@Override
	public String getPrimaryAction()
	{
		return "";
	}
	
	@Override
	public final String getDescription()
	{
		String description = this.description;
		
		if(syntax.length > 0)
			description += "\n";
		
		for(String line : getSyntax())
			description += "\n" + line;
		
		return description;
	}
	
	public final String[] getSyntax()
	{
		String[] translated = syntax.clone();
		if(translated.length > 0)
			translated[0] = WURST.translate("Syntax: %s", translated[0]);
		return translated;
	}
	
	public final void printHelp()
	{
		for(String line : description.split("\n"))
			ChatUtils.message(line);
		
		for(String line : getSyntax())
			ChatUtils.message(line);
	}
	
	@Override
	public final Category getCategory()
	{
		return category;
	}
	
	protected final void setCategory(Category category)
	{
		this.category = category;
	}
}
