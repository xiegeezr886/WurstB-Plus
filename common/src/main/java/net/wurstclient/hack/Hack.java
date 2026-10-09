/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hack;

import java.util.EnumSet;
import java.util.Objects;

import net.wurstclient.Category;
import net.wurstclient.Feature;
import net.wurstclient.hacks.ClickGuiHack;
import net.wurstclient.hacks.NavigatorHack;
import net.wurstclient.hacks.TooManyHaxHack;
import net.wurstclient.settings.Setting;

public abstract class Hack extends Feature
{
	private final String name;
	private final String description;
	private Category category;
	private final EnumSet<HackConflictGroup> conflictGroups =
		EnumSet.noneOf(HackConflictGroup.class);
	
	private boolean enabled;
	private final boolean stateSaved =
		!getClass().isAnnotationPresent(DontSaveState.class);
	
	public Hack(String name)
	{
		this.name = Objects.requireNonNull(name);
		description = "description.wurst.hack." + name.toLowerCase();
		addPossibleKeybind(name, "Toggle " + name);
	}

	public Hack(String name, Setting... settings)
	{
		this(name);
		for(Setting s : settings)
			addSetting(s);
	}
	
	@Override
	public final String getName()
	{
		return name;
	}
	
	@Override
	public String getRenderName()
	{
		return getTranslatedName();
	}

	@Override
	public final String getDisplayName()
	{
		return localizeRenderName(name, getTranslatedName(), getRenderName());
	}

	protected final String getTranslatedName()
	{
		String key = "hack.name." + name.toLowerCase();
		String translated = WURST.translate(key);
		if(translated.equals(key))
			return name;
		return translated;
	}

	static String localizeRenderName(String name, String translatedName,
		String renderName)
	{
		if(translatedName.equals(name) || !renderName.startsWith(name))
			return renderName;

		return translatedName + renderName.substring(name.length());
	}

	public String getUntranslatedName()
	{
		return name;
	}

	@Override
	public final String getDescription()
	{
		return WURST.translate(description);
	}

	public final String getDescriptionKey()
	{
		return description;
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

	protected final void addConflictGroup(HackConflictGroup group)
	{
		conflictGroups.add(Objects.requireNonNull(group));
	}

	final EnumSet<HackConflictGroup> getConflictGroups()
	{
		return conflictGroups.clone();
	}
	
	@Override
	public final boolean isEnabled()
	{
		return enabled;
	}
	
	public final void setEnabled(boolean enabled)
	{
		if(this.enabled == enabled)
			return;
		
		if(enabled && !canEnable())
			return;

		TooManyHaxHack tooManyHax = WURST.getHax().tooManyHaxHack;
		if(enabled && tooManyHax.isEnabled() && tooManyHax.isBlocked(this))
			return;
		
		if(enabled)
		{
			WURST.getHackConflictManager().acquire(this);
			this.enabled = true;

			try
			{
				updateHudState();
				onEnable();
			}catch(RuntimeException | Error e)
			{
				this.enabled = false;
				WURST.getHackConflictManager().release(this);
				updateHudState();
				throw e;
			}
		}
		else
		{
			this.enabled = false;

			try
			{
				updateHudState();
				onDisable();
			}finally
			{
				WURST.getHackConflictManager().release(this);
			}
		}
		
		if(stateSaved)
			WURST.getHax().saveEnabledHax();

		if(WURST.getHudManager() != null
			&& !(this instanceof ClickGuiHack || this instanceof NavigatorHack))
			WURST.getHudManager().addNotification(this);
	}

	private void updateHudState()
	{
		if(!(this instanceof ClickGuiHack || this instanceof NavigatorHack))
			WURST.getHud().getHackList().updateState(this);
	}
	
	@Override
	public final String getPrimaryAction()
	{
		return enabled ? "\u5173\u95ED" : "\u5F00\u542F";
	}
	
	@Override
	public final void doPrimaryAction()
	{
		setEnabled(!enabled);
	}
	
	public final boolean isStateSaved()
	{
		return stateSaved;
	}
	
	protected boolean canEnable()
	{
		return true;
	}

	protected final void subscribeEvents()
	{
		EVENTS.subscribeAnnotated(this);
	}

	protected final void unsubscribeEvents()
	{
		EVENTS.unsubscribeAnnotated(this);
	}

	protected void onEnable()
	{

	}
	
	protected void onDisable()
	{
		
	}
}
