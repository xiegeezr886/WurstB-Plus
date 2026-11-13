/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hud2;

import net.wurstclient.Category;
import net.wurstclient.Feature;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

/**
 * Global HUD settings exposed in the ClickGUI.
 *
 * <p>This replaces Soar's {@code GlobalSettingsMod} HUD options without
 * requiring Soar's separate mod menu.
 */
public final class HudSettingsFeature extends Feature
{
	private final EnumSetting<HudTheme> theme =
		new EnumSetting<>("Theme", "HUD background theme.", HudTheme.values(),
			HudTheme.NORMAL);

	private final CheckboxSetting notifications =
		new CheckboxSetting("Notifications", "Shows HUD notifications.", true);

	private final SliderSetting notificationDuration =
		new SliderSetting("Notification duration",
			"How long notifications stay visible.", 3, 1, 10, 0.5,
			ValueDisplay.DECIMAL.withSuffix("s"));

	private final CheckboxSetting hideWithChat =
		new CheckboxSetting("Hide with chat",
			"Hide the HUD while a screen is open.", true);

	private final CheckboxSetting hideWithDebug =
		new CheckboxSetting("Hide with debug",
			"Hide the HUD while the debug overlay is visible.", true);

	public HudSettingsFeature()
	{
		theme.addChangeListener(
			() -> HUDMod.setTheme(theme.getSelected()));
		addSetting(theme);
		addSetting(notifications);
		addSetting(notificationDuration);
		addSetting(hideWithChat);
		addSetting(hideWithDebug);
	}

	@Override
	public String getName()
	{
		return "HUDSettings";
	}

	@Override
	public String getDisplayName()
	{
		return "HUD Settings";
	}

	@Override
	public String getRenderName()
	{
		return "HUD Settings";
	}

	@Override
	public String getDescription()
	{
		return "Global theme, notification and visibility options for the HUD.";
	}

	@Override
	public String getPrimaryAction()
	{
		return "";
	}

	@Override
	public Category getCategory()
	{
		return Category.HUD;
	}

	@Override
	public boolean isEnabled()
	{
		return true;
	}

	public HudTheme getTheme()
	{
		return theme.getSelected();
	}

	public void setTheme(HudTheme theme)
	{
		this.theme.setSelected(theme == null ? HudTheme.NORMAL : theme);
	}

	public boolean showNotifications()
	{
		return notifications.isChecked();
	}

	public long getNotificationDurationMs()
	{
		return Math.round(notificationDuration.getValue() * 1000);
	}

	public boolean hideWithChat()
	{
		return hideWithChat.isChecked();
	}

	public boolean hideWithDebug()
	{
		return hideWithDebug.isChecked();
	}
}
