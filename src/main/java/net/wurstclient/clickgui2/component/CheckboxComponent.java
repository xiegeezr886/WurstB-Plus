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
import net.wurstclient.clickgui2.supersoft.SuperSoftRenderer;
import net.wurstclient.clickgui2.supersoft.UiTween;
import net.wurstclient.settings.CheckboxSetting;

/**
 * 仿 VAPE BooleanToggleComponent 的开关行。
 */
public class CheckboxComponent extends ValueRowComponent
{
	private final CheckboxSetting checkbox;
	private final UiTween toggleMotion;
	
	public CheckboxComponent(CheckboxSetting checkbox)
	{
		super(checkbox);
		this.checkbox = checkbox;
		toggleMotion = new UiTween(checkbox.isChecked() ? 1 : 0, 150);
	}
	
	@Override
	protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		drawLabel(graphics, mouseX, mouseY);
		
		float checked = toggleMotion.update(checkbox.isChecked() ? 1 : 0);
		int switchX = (int)(x + getWidth() - (usesSuperSoftTheme() ? 28 : 16));
		int switchY = (int)(y + getHeight() / 2
			- (usesSuperSoftTheme() ? 6 : 3));
		if(usesSuperSoftTheme())
			SuperSoftRenderer.settingSwitch(graphics, switchX, switchY,
				net.wurstclient.clickgui2.supersoft.SuperSoftTheme.ACCENT,
				checked);
		else
			SuperSoftRenderer.switchControl(graphics, switchX, switchY,
				VapePalette.ACCENT, checked);
	}
	
	@Override
	protected boolean onClick(double mouseX, double mouseY, int button)
	{
		if(button != 0)
			return false;
		checkbox.setChecked(!checkbox.isChecked());
		return true;
	}

	@Override
	protected double minHeight()
	{
		return 15;
	}
}
