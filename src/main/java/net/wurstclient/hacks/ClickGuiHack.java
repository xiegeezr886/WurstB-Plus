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
import net.wurstclient.clickgui2.ClickGuiStyle;
import net.wurstclient.hack.DontSaveState;
import net.wurstclient.hack.Hack;
import net.wurstclient.hud2.NotificationSeverity;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.ScreenRegistry;

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
	
	private final EnumSetting<ClickGuiStyle> style = new EnumSetting<>(
		"GUI style",
		"Epsilon: dropdown panels.\nSuperSoft: card windows.\nVape: sidebar frames.",
		ClickGuiStyle.values(), ClickGuiStyle.EPSILON);
	
	public ClickGuiHack()
	{
		super("ClickGUI");
		addSetting(ttOpacity);
		addSetting(maxHeight);
		addSetting(maxSettingsHeight);
		addSetting(style);
		style.addChangeListener(() -> {
			if(WURST.getGuiPreferences() == null
				|| WURST.getGuiPreferences().getClickGuiStyle() == style
					.getSelected())
				return;
			boolean reopen = MC != null
				&& ScreenRegistry.CLICK_GUI.matches(MC.screen);
			ClickGuiScreens.setStyle(style.getSelected(), reopen);
		});
	}

	public ClickGuiStyle getStyle()
	{
		return style.getSelected();
	}

	public void applyStyle(ClickGuiStyle value)
	{
		style.setSelected(value);
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
