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
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.clickgui2.GuiIcon;
import net.wurstclient.clickgui2.supersoft.SuperSoftTheme;
import net.wurstclient.clickgui2.supersoft.UiTween;
import net.wurstclient.settings.EnumSetting;

/**
 * 仿 VAPE CompactListValueComponent 的枚举下拉行。
 */
public class EnumComponent<T extends Enum<T>> extends ValueRowComponent
{
	private final EnumSetting<T> enumSetting;
	private final UiTween expansionMotion = new UiTween(0, 200);
	private final UiTween arrowMotion = new UiTween(0, 200);
	private final UiTween[] rowHoverMotions;
	private final UiTween[] rowSelectionMotions;
	private boolean expanded;
	
	public EnumComponent(EnumSetting<T> enumSetting)
	{
		super(enumSetting);
		this.enumSetting = enumSetting;
		rowHoverMotions = new UiTween[enumSetting.getValues().length];
		rowSelectionMotions = new UiTween[enumSetting.getValues().length];
		for(int index = 0; index < rowHoverMotions.length; index++)
		{
			rowHoverMotions[index] = new UiTween(0, 150);
			rowSelectionMotions[index] = new UiTween(
				enumSetting.getValues()[index] == enumSetting.getSelected()
					? 0.6F : 0, 150);
		}
	}
	
	@Override
	protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		if(usesSuperSoftTheme())
		{
			renderSuperSoft(graphics, mouseX, mouseY);
			return;
		}
		drawLabel(graphics, mouseX, mouseY);
		
		if(!expanded)
		{
			// 值按钮
			int btnX = (int)(x + getWidth() * 0.45);
			int btnW = (int)(getWidth() * 0.5) - 4;
			int btnY = (int)y + 1;
			int btnH = (int)getHeight() - 2;
			int bg = hovered ? 0xFF2F2E2F : 0xFF252426;
			FlatRenderer.fillRoundedRect(graphics, btnX, btnY,
				btnX + btnW, btnY + btnH, 3, bg);
			String value = enumSetting.getSelected().name();
			Font font = Minecraft.getInstance().font;
			graphics.drawString(font, value, btnX + 4, btnY + 3,
				0xFFD1D1D1, false);
			return;
		}
		
		// 展开下拉
		int panelX = (int)x;
		int panelY = (int)y + headerHeight();
		int panelW = (int)getWidth();
		FlatRenderer.fillRoundedRect(graphics, panelX, panelY,
			panelX + panelW, panelY + 16 * enumSetting.getValues().length,
			3, 0xFF1A191A);
		Font font = Minecraft.getInstance().font;
		T[] values = enumSetting.getValues();
		for(int i = 0; i < values.length; i++)
		{
			int rowY = panelY + i * 16;
			boolean rowHover = mouseX >= panelX && mouseX < panelX + panelW
				&& mouseY >= rowY && mouseY < rowY + 16;
			if(rowHover)
				graphics.fill(panelX + 1, rowY, panelX + panelW - 1,
					rowY + 16, 0xFF2F2E2F);
			boolean selected = values[i] == enumSetting.getSelected();
			graphics.drawString(font, values[i].name(), panelX + 4,
				rowY + 3, selected ? 0xFFF0F0F0 : 0xFFA3A3A3, false);
		}
	}

	private void renderSuperSoft(GuiGraphics graphics, int mouseX, int mouseY)
	{
		drawLabel(graphics, mouseX, mouseY);
		Font font = Minecraft.getInstance().font;
		String value = enumSetting.getSelected().name();
		float expansion = expansionMotion.update(expanded ? 1 : 0);
		int right = (int)(x + getWidth()) - 4;
		GuiIcon.CHEVRON.drawRotated(graphics, right - 7, (int)y + 5, 6,
			0x99FFFFFF, arrowMotion.update(expanded ? 90 : 0));
		graphics.drawString(font, value,
			right - 10 - font.width(value), (int)y + 3,
			SuperSoftTheme.ACCENT, false);
		if(expansion <= 0.001F)
			return;

		int panelY = (int)y + headerHeight();
		int panelHeight = Math.round(16 * enumSetting.getValues().length
			* expansion);
		graphics.fill((int)x, panelY, (int)(x + getWidth()),
			panelY + panelHeight, 0xFF1E1E1E);
		graphics.enableScissor((int)x, panelY, (int)(x + getWidth()),
			panelY + panelHeight);
		T[] values = enumSetting.getValues();
		for(int index = 0; index < values.length; index++)
		{
			int rowY = panelY + index * 16;
			boolean rowHover = mouseX >= x && mouseX < x + getWidth()
				&& mouseY >= rowY && mouseY < rowY + 16;
			boolean selected = values[index] == enumSetting.getSelected();
			float hover = rowHoverMotions[index].update(rowHover ? 1 : 0);
			float selectedAlpha = rowSelectionMotions[index]
				.update(selected ? 0.6F : 0);
			float alpha = Math.max(hover, selectedAlpha);
			if(alpha > 0.001F)
				graphics.fill((int)x + 2, rowY, (int)(x + getWidth()) - 2,
					rowY + 16, withAlpha(selected ? SuperSoftTheme.ACCENT
						: SuperSoftTheme.SETTING_HOVER, alpha));
			graphics.drawString(font, values[index].name(), (int)x + 6,
				rowY + 3, selected ? SuperSoftTheme.TEXT : 0xCCFFFFFF, false);
		}
		graphics.disableScissor();
	}
	
	@Override
	protected boolean onClick(double mouseX, double mouseY, int button)
	{
		if(button != 0)
			return false;
		
		if(expanded)
		{
			// 点击下拉项
			int panelY = (int)y + headerHeight();
			int index = (int)((mouseY - panelY) / 16);
			T[] values = enumSetting.getValues();
			if(mouseX >= x && mouseX < x + getWidth()
				&& index >= 0 && index < values.length)
				enumSetting.setSelected(values[index]);
			expanded = false;
			return true;
		}
		if(usesSuperSoftTheme())
		{
			expanded = true;
			return true;
		}
		
		// 点击值按钮展开
		int btnX = (int)(x + getWidth() * 0.45);
		if(mouseX >= btnX)
		{
			expanded = true;
			return true;
		}
		return false;
	}
	
	@Override
	protected double minHeight()
	{
		if(usesSuperSoftTheme())
			return headerHeight() + enumSetting.getValues().length * 16
				* expansionMotion.get();
		return expanded ? headerHeight() + enumSetting.getValues().length * 16
			: headerHeight();
	}

	private int headerHeight()
	{
		return usesSuperSoftTheme() ? 24 : 16;
	}

	private static int withAlpha(int color, float opacity)
	{
		int alpha = Math.round((color >>> 24) * Math.max(0, Math.min(1, opacity)));
		return color & 0x00FFFFFF | alpha << 24;
	}
}
