/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.settings;

import java.util.Collections;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.wurstclient.clickgui2.Component;
import net.wurstclient.keybinds.PossibleKeybind;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.text.WText;

public abstract class Setting
{
	private final String name;
	private final WText description;
	private final LinkedHashSet<String> aliases = new LinkedHashSet<>();
	private final LinkedHashSet<Runnable> changeListeners =
		new LinkedHashSet<>();
	private BooleanSupplier visibility = () -> true;
	private List<Setting> children = Collections.emptyList();
	private Setting parent;
	private boolean expanded;
	
	public Setting(String name, WText description)
	{
		this.name = Objects.requireNonNull(name);
		this.description = Objects.requireNonNull(description);
	}
	
	public final String getName()
	{
		return name;
	}
	
	public final String getDescription()
	{
		return description.toString();
	}
	
	public final String getWrappedDescription(int width)
	{
		return ChatUtils.wrapText(getDescription(), width);
	}

	@SuppressWarnings("unchecked")
	public final <T extends Setting> T aliases(String... aliases)
	{
		for(String alias : aliases)
			this.aliases.add(Objects.requireNonNull(alias).toLowerCase());

		return (T)this;
	}

	public final boolean matchesName(String name)
	{
		String key = Objects.requireNonNull(name).toLowerCase();
		return this.name.equalsIgnoreCase(key) || aliases.contains(key);
	}

	@SuppressWarnings("unchecked")
	public final <T extends Setting> T visibleWhen(BooleanSupplier visibility)
	{
		this.visibility = Objects.requireNonNull(visibility);
		return (T)this;
	}

	public final boolean isVisible()
	{
		return visibility.getAsBoolean();
	}

	@SuppressWarnings("unchecked")
	public <T extends Setting> T withChildren(Setting... children)
	{
		Objects.requireNonNull(children);
		Set<Setting> unique = Collections.newSetFromMap(new IdentityHashMap<>());
		ArrayList<Setting> ordered = new ArrayList<>(children.length);
		for(Setting child : children)
		{
			Objects.requireNonNull(child);
			if(!unique.add(child))
				throw new IllegalArgumentException("Duplicate child setting: "
					+ child.getName());
			if(child == this || child.containsDescendant(this))
				throw new IllegalArgumentException("Setting hierarchy cycle: "
					+ getName() + " -> " + child.getName());
			if(child.parent != null && child.parent != this)
				throw new IllegalArgumentException("Setting already belongs to: "
					+ child.parent.getName());
			ordered.add(child);
		}

		for(Setting oldChild : this.children)
			if(!unique.contains(oldChild))
				oldChild.parent = null;
		for(Setting child : children)
			child.parent = this;
		this.children = List.copyOf(ordered);
		return (T)this;
	}

	private boolean containsDescendant(Setting setting)
	{
		if(this == setting)
			return true;
		for(Setting child : children)
			if(child.containsDescendant(setting))
				return true;
		return false;
	}

	public List<Setting> getChildren()
	{
		return children;
	}

	public boolean hasChildren()
	{
		return !children.isEmpty();
	}

	public boolean isExpanded()
	{
		return expanded;
	}

	public final void setExpanded(boolean expanded)
	{
		if(this.expanded == expanded)
			return;
		this.expanded = expanded;
		notifyChanged();
	}

	public Setting getParent()
	{
		return parent;
	}

	public int getDepth()
	{
		int depth = 0;
		for(Setting current = parent; current != null; current = current.parent)
			depth++;
		return depth;
	}

	public final void addChangeListener(Runnable listener)
	{
		changeListeners.add(Objects.requireNonNull(listener));
	}

	public final void removeChangeListener(Runnable listener)
	{
		changeListeners.remove(listener);
	}

	protected final void notifyChanged()
	{
		update();
		for(Runnable listener : changeListeners.toArray(Runnable[]::new))
			listener.run();
	}
	
	public abstract Component getComponent();
	
	public abstract void fromJson(JsonElement json);
	
	public abstract JsonElement toJson();
	
	/**
	 * Exports this setting's data to a {@link JsonObject} for use in the
	 * Wurst Wiki. Must always specify the following properties:
	 * <ul>
	 * <li>name
	 * <li>description
	 * <li>type
	 * </ul>
	 */
	public abstract JsonObject exportWikiData();
	
	public void update()
	{
		
	}
	
	public abstract Set<PossibleKeybind> getPossibleKeybinds(
		String featureName);
}
