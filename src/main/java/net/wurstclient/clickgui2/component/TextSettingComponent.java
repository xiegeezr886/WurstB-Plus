/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clickgui2.component;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.clickgui2.supersoft.EpsilonMd3Theme;
import net.wurstclient.settings.Setting;

/**
 * 其他未覆盖设置类型的通用文本行。
 * 显示名称 + 当前值字符串。
 */
public class TextSettingComponent extends ValueRowComponent
{
	public TextSettingComponent(Setting setting)
	{
		super(setting);
	}
	
	@Override
	protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		drawLabel(graphics, mouseX, mouseY);
		Font font = Minecraft.getInstance().font;
		String value = getSettingValueString();
		graphics.drawString(font, value,
			(int)(x + getWidth() - font.width(value) - 4), (int)y + 3,
			EpsilonMd3Theme.TEXT_MUTED, false);
	}
	
	private String getSettingValueString()
	{
		if(setting instanceof net.wurstclient.settings.CheckboxSetting c)
			return c.isChecked() ? "ON" : "OFF";
		return "";
	}
}
