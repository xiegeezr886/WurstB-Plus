/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.settings;

import java.util.LinkedHashSet;
import java.util.Objects;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.Component;
import net.wurstclient.clickgui2.components.ComboBoxComponent;
import net.wurstclient.keybinds.PossibleKeybind;
import net.wurstclient.util.json.JsonUtils;
import net.wurstclient.util.text.WText;

public class EnumSetting<T extends Enum<T>> extends Setting
{
	private final T[] values;
	private T selected;
	private final T defaultSelected;
	
	public EnumSetting(String name, WText description, T[] values, T selected)
	{
		super(name, description);
		this.values = Objects.requireNonNull(values);
		this.selected = Objects.requireNonNull(selected);
		defaultSelected = selected;
	}
	
	public EnumSetting(String name, String descriptionKey, T[] values,
		T selected)
	{
		this(name, WText.translated(descriptionKey), values, selected);
	}
	
	public EnumSetting(String name, T[] values, T selected)
	{
		this(name, WText.empty(), values, selected);
	}
	
	public T[] getValues()
	{
		return values;
	}
	
	/**
	 * Returns the display name of a value, translated into the current
	 * language. The enum constant name is what gets saved to the settings
	 * file, so {@link #getSelected()} and the ".set" commands keep using the
	 * raw value.
	 */
	public String getTranslatedValueName(T value)
	{
		String key = "setting.value." + translationKey(value.name());
		String translated = WurstClient.INSTANCE.translate(key);
		return translated.equals(key) ? value.toString() : translated;
	}

	public T getSelected()
	{
		return selected;
	}
	
	public T getDefaultSelected()
	{
		return defaultSelected;
	}
	
	public void setSelected(T selected)
	{
		selected = Objects.requireNonNull(selected);
		if(this.selected == selected)
			return;

		this.selected = selected;
		notifyChanged();
		WurstClient.INSTANCE.saveSettings();
	}
	
	public boolean setSelected(String selected)
	{
		for(T value : values)
		{
			if(!value.toString().equalsIgnoreCase(selected))
				continue;
			
			setSelected(value);
			return true;
		}
		
		return false;
	}
	
	public void selectNext()
	{
		int next = selected.ordinal() + 1;
		if(next >= values.length)
			next = 0;
		
		setSelected(values[next]);
	}
	
	public void selectPrev()
	{
		int prev = selected.ordinal() - 1;
		if(prev < 0)
			prev = values.length - 1;
		
		setSelected(values[prev]);
	}
	
	@Override
	public Component getComponent()
	{
		return new ComboBoxComponent<>(this);
	}
	
	@Override
	public void fromJson(JsonElement json)
	{
		if(!JsonUtils.isString(json))
			return;
		
		setSelected(json.getAsString());
	}
	
	@Override
	public JsonElement toJson()
	{
		return new JsonPrimitive(selected.toString());
	}
	
	@Override
	public JsonObject exportWikiData()
	{
		JsonObject json = new JsonObject();
		json.addProperty("name", getName());
		json.addProperty("description", getDescription());
		json.addProperty("type", "Enum");
		
		JsonArray values = new JsonArray();
		for(T value : this.values)
			values.add(value.toString());
		json.add("values", values);
		json.addProperty("defaultValue", defaultSelected.toString());
		
		return json;
	}
	
	@Override
	public LinkedHashSet<PossibleKeybind> getPossibleKeybinds(
		String featureName)
	{
		String fullName = featureName + " " + getName();
		
		String command = ".setmode " + featureName.toLowerCase() + " "
			+ getName().toLowerCase().replace(" ", "_") + " ";
		
		LinkedHashSet<PossibleKeybind> pkb = new LinkedHashSet<>();
		pkb.add(new PossibleKeybind(command + "next", "Next %s", fullName));
		pkb.add(new PossibleKeybind(command + "prev", "Previous %s",
			fullName));
		
		for(T v : values)
		{
			String vName = v.toString().replace(" ", "_").toLowerCase();
			pkb.add(new PossibleKeybind(command + vName, "Set %s to %s",
				fullName, v.toString()));
		}
		
		return pkb;
	}
}
