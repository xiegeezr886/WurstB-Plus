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
import net.minecraft.network.chat.Component;
import net.wurstclient.clickgui2.supersoft.SuperSoftTheme;
import net.wurstclient.clickgui2.supersoft.UiTween;
import net.wurstclient.settings.Setting;

/**
 * 仿 VAPE 值组件行的基础类。
 * 显示设置名称 + 值，右侧可放置控件。
 */
public abstract class ValueRowComponent extends GuiComponent
{
	private static final float SETTING_TEXT_SCALE = 0.67F;
	private static final float DESCRIPTION_TEXT_SCALE = 0.45F;
	protected final Setting setting;
	private final UiTween hoverMotion = new UiTween(0, 150);
	
	protected ValueRowComponent(Setting setting)
	{
		this.setting = setting;
		height = minHeight();
	}
	
	public Setting getSetting()
	{
		return setting;
	}
	
	protected void drawLabel(GuiGraphics graphics, int mouseX, int mouseY)
	{
		Font font = Minecraft.getInstance().font;
		String label = setting.getName();
		float hover = hoverMotion.update(isHovered(mouseX, mouseY) ? 1 : 0);
		int labelColor = SuperSoftTheme.mix(SuperSoftTheme.TEXT_SECONDARY,
			SuperSoftTheme.TEXT, hover);
		if(hover > 0.001F)
			graphics.fill((int)x, (int)y, (int)(x + getWidth()),
				(int)(y + getHeight()), SuperSoftTheme.mix(0x003C3C3C,
					0x443C3C3C, hover));
		if(usesSuperSoftTheme())
		{
			drawScaled(graphics, label, (int)x + 4, (int)y + 3, labelColor,
				SETTING_TEXT_SCALE);
			String description = font.plainSubstrByWidth(setting.getDescription(),
				Math.max(20, Math.round(((int)getWidth() - 10)
					/ DESCRIPTION_TEXT_SCALE)));
			drawScaled(graphics, description, (int)x + 4, (int)y + 10,
				0x80FFFFFF, DESCRIPTION_TEXT_SCALE);
		}else
		{
			graphics.drawString(font, label, (int)x + 4, (int)y + 3,
				labelColor, false);
		}
	}
	
	protected void drawValue(GuiGraphics graphics, String value)
	{
		Font font = Minecraft.getInstance().font;
		if(usesSuperSoftTheme())
		{
			int valueWidth = Math.round(font.width(value) * SETTING_TEXT_SCALE);
			drawScaled(graphics, value,
				(int)(x + getWidth() - valueWidth - 4), (int)y + 3,
				SuperSoftTheme.ACCENT, SETTING_TEXT_SCALE);
		}else
		{
			int valueWidth = font.width(value);
			graphics.drawString(font, value,
				(int)(x + getWidth() - valueWidth - 4), (int)y + 3,
				VapePalette.TEXT, false);
		}
	}
	
	@Override
	protected double minHeight()
	{
		return 15;
	}

	@Override
	public double getHeight()
	{
		return usesSuperSoftTheme() ? Math.max(16, super.getHeight())
			: super.getHeight();
	}

	protected void drawScaled(GuiGraphics graphics, String text, int x, int y,
		int color, float scale)
	{
		graphics.pose().pushPose();
		graphics.pose().translate(x, y, 0);
		graphics.pose().scale(scale, scale, 1);
		graphics.drawString(Minecraft.getInstance().font, text, 0, 0, color,
			false);
		graphics.pose().popPose();
	}
	
	protected Component asComponent(String s)
	{
		return Component.literal(s);
	}
}
