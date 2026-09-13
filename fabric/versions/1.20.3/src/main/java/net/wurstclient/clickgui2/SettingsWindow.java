/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clickgui2;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.wurstclient.Feature;
import net.wurstclient.WurstClient;
import net.wurstclient.settings.Setting;

public final class SettingsWindow extends Window
{
	private final List<Setting> settings;
	private List<Setting> renderedSettings = List.of();

	public SettingsWindow(Feature feature, Window parent, int buttonY)
	{
		this(feature);
		setInitialPosition(parent, buttonY);
	}

	public SettingsWindow(Feature feature, int x, int y)
	{
		this(feature);
		setInitialPosition(x, y);
	}

	private SettingsWindow(Feature feature)
	{
		super(feature.getDisplayName() + " 设置");
		settings = new ArrayList<>(feature.getSettings().values());
		
		setClosable(true);
		setMinimizable(false);
		setPinnable(false);
		setMaxHeight(200);

		refreshSettings(SettingTreeLayout.flatten(settings));
	}

	private void refreshSettings(List<Setting> visibleSettings)
	{
		clear();
		for(Setting setting : visibleSettings)
			addSetting(setting);
		renderedSettings = visibleSettings;
		pack();
	}

	private void addSetting(Setting setting)
	{
		Component component = setting.getComponent();
		component.setIndent(setting.getDepth() * 12);
		add(component);
	}

	@Override
	public void prepareForRender()
	{
		List<Setting> visibleSettings = SettingTreeLayout.flatten(settings);
		if(!renderedSettings.equals(visibleSettings))
			refreshSettings(visibleSettings);
	}
	
	private void setInitialPosition(Window parent, int buttonY)
	{
		int scroll = parent.isScrollingEnabled() ? parent.getScrollOffset() : 0;
		int x = parent.getX() + parent.getWidth() + 5;
		int y = parent.getY() + 12 + buttonY + scroll;
		
		com.mojang.blaze3d.platform.Window mcWindow = WurstClient.MC.getWindow();
		if(x + getWidth() > mcWindow.getGuiScaledWidth())
			x = parent.getX() - getWidth() - 5;
		if(y + getHeight() > mcWindow.getGuiScaledHeight())
			y -= getHeight() - 14;
		
		x = Mth.clamp(x, 0, mcWindow.getGuiScaledWidth());
		y = Mth.clamp(y, 0, mcWindow.getGuiScaledHeight());
		
		setX(x);
		setY(y);
	}

	private void setInitialPosition(int x, int y)
	{
		com.mojang.blaze3d.platform.Window mcWindow = WurstClient.MC.getWindow();
		if(x + getWidth() > mcWindow.getGuiScaledWidth())
			x -= getWidth() + 10;
		if(y + getHeight() > mcWindow.getGuiScaledHeight())
			y = mcWindow.getGuiScaledHeight() - getHeight();

		if(getWidth() <= mcWindow.getGuiScaledWidth())
			setX(Mth.clamp(x, 0,
				Math.max(0, mcWindow.getGuiScaledWidth() - getWidth())));
		else
			setX(Math.max(0, x));
		if(getHeight() <= mcWindow.getGuiScaledHeight())
			setY(Mth.clamp(y, 0,
				Math.max(0, mcWindow.getGuiScaledHeight() - getHeight())));
		else
			setY(Math.max(0, y));
	}
}
