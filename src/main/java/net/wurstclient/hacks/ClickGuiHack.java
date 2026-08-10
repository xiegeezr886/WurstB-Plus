/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.wurstclient.DontBlock;
import net.wurstclient.SearchTags;
import net.wurstclient.clickgui2.ClickGuiScreens;
import net.wurstclient.hack.DontSaveState;
import net.wurstclient.hack.Hack;
import net.wurstclient.hud2.NotificationSeverity;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@DontSaveState
@DontBlock
@SearchTags({"click gui", "WindowGUI", "window gui", "HackMenu", "hack menu"})
public final class ClickGuiHack extends Hack
{
	private final SliderSetting ttOpacity = new SliderSetting("Tooltip opacity",
		0.9, 0.15, 1, 0.01, ValueDisplay.PERCENTAGE);
	
	private final SliderSetting maxHeight = new SliderSetting("Max height",
		"Maximum window height\n" + "0 = no limit", 200, 0, 1000, 50,
		ValueDisplay.INTEGER);
	
	private final SliderSetting maxSettingsHeight =
		new SliderSetting("Max settings height",
			"Maximum height for settings windows\n" + "0 = no limit", 200, 0,
			1000, 50, ValueDisplay.INTEGER);
	
	private final CheckboxSetting vapeMode = new CheckboxSetting("Vape mode",
		"Switches the ClickGUI to the Vape-style interface.", false);
	
	public ClickGuiHack()
	{
		super("ClickGUI");
		addSetting(ttOpacity);
		addSetting(maxHeight);
		addSetting(maxSettingsHeight);
		addSetting(vapeMode);
		vapeMode.addChangeListener(() -> ClickGuiScreens.setVapeMode(
			vapeMode.isChecked()));
	}
	
	@Override
	protected void onEnable()
	{
		MC.setScreen(ClickGuiScreens.create());
		if(WURST.getHudManager() != null)
			WURST.getHudManager().addNotification("Info", getDisplayName(),
				NotificationSeverity.INFO);
		setEnabled(false);
	}
	
	public float getTooltipOpacity()
	{
		return ttOpacity.getValueF();
	}
	
	public int getMaxHeight()
	{
		return maxHeight.getValueI();
	}
	
	public int getMaxSettingsHeight()
	{
		return maxSettingsHeight.getValueI();
	}
}
