/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clickgui2.component;

import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.settings.CheckboxSetting;

/**
 * 仿 VAPE BooleanToggleComponent 的开关行。
 */
public class CheckboxComponent extends ValueRowComponent
{
	private final CheckboxSetting checkbox;
	
	public CheckboxComponent(CheckboxSetting checkbox)
	{
		super(checkbox);
		this.checkbox = checkbox;
	}
	
	@Override
	protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		drawLabel(graphics, mouseX, mouseY);
		
		// 开关
		boolean checked = checkbox.isChecked();
		int switchX = (int)(x + getWidth() - 24);
		int switchY = (int)y + 3;
		int bg = checked ? 0xFFD61846 : 0xFF363536;
		FlatRenderer.fillRoundedRect(graphics, switchX, switchY,
			switchX + 20, switchY + 10, 5, bg);
		int knobX = checked ? switchX + 10 : switchX + 2;
		FlatRenderer.fillRoundedRect(graphics, knobX, switchY + 2,
			knobX + 6, switchY + 8, 3, 0xFFF0F0F0);
	}
	
	@Override
	protected boolean onClick(double mouseX, double mouseY, int button)
	{
		if(button != 0)
			return false;
		checkbox.setChecked(!checkbox.isChecked());
		return true;
	}
}
