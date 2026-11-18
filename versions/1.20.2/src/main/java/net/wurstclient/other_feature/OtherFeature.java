/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.other_feature;

import java.util.Locale;
import net.wurstclient.Feature;

public abstract class OtherFeature extends Feature
{
	private final String name;
	private final String description;
	
	public OtherFeature(String name, String description)
	{
		this.name = name;
		this.description = description;
	}
	
	@Override
	public final String getName()
	{
		return name;
	}
	
	/**
	 * Returns the display name translated into the current language, or the
	 * raw name when no translation exists. The raw {@link #getName()} is what
	 * commands and the settings file use, so it must never be translated.
	 */
	@Override
	public String getRenderName()
	{
		String key = "other_feature.name." + translationKey();
		String translated = WURST.translate(key);
		return translated.equals(key) ? name : translated;
	}

	private String translationKey()
	{
		String key = name.toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
		// Names that are already Chinese (e.g. ReconnectOtf) have no ASCII
		// part left, so fall back to their class' well-known key.
		return key.isEmpty() ? "reconnect" : key;
	}

	@Override
	public String getDescription()
	{
		return WURST.translate(description);
	}
	
	@Override
	public boolean isEnabled()
	{
		return false;
	}
	
	@Override
	public String getPrimaryAction()
	{
		return "";
	}
	
	@Override
	public void doPrimaryAction()
	{
		
	}
}
