/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clickgui2.component;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.clickgui2.supersoft.SuperSoftRenderer;
import net.wurstclient.clickgui2.supersoft.UiTween;
import net.wurstclient.settings.SliderSetting;

/**
 * 仿 VAPE NumberSliderComponent 的滑块行。
 */
public class SliderComponent extends ValueRowComponent
{
	private final SliderSetting slider;
	private final UiTween thumbMotion = new UiTween(1, 150);
	private boolean dragging;
	
	public SliderComponent(SliderSetting slider)
	{
		super(slider);
		this.slider = slider;
	}
	
	@Override
	protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		drawLabel(graphics, mouseX, mouseY);
		
		int trackX = (int)x + 5;
		int trackY = (int)y + 19;
		int trackW = (int)getWidth() - 10;
		int trackRight = trackX + trackW;
		
		double min = slider.getMinimum();
		double max = slider.getMaximum();
		double value = slider.getValue();
		float progress = (float)((value - min) / (max - min));
		SuperSoftRenderer.slider(graphics, trackX, trackRight, trackY,
			usesSuperSoftTheme() ? net.wurstclient.clickgui2.supersoft.SuperSoftTheme.ACCENT
				: VapePalette.ACCENT,
			progress, thumbMotion.update(hovered || dragging ? 1.3F : 1));
		
		drawValue(graphics, slider.getValueString());
		
		if(dragging)
			updateFromMouse(mouseX);
	}
	
	@Override
	protected boolean onClick(double mouseX, double mouseY, int button)
	{
		if(button != 0)
			return false;
		dragging = true;
		updateFromMouse((int)mouseX);
		return true;
	}
	
	@Override
	protected boolean onRelease(double mouseX, double mouseY, int button)
	{
		dragging = false;
		return true;
	}
	
	@Override
	protected boolean onDrag(double mouseX, double mouseY, int button)
	{
		if(button != 0)
			return false;
		updateFromMouse((int)mouseX);
		return true;
	}
	
	private void updateFromMouse(int mouseX)
	{
		int trackX = (int)x + 5;
		int trackW = (int)getWidth() - 10;
		float progress = (mouseX - trackX) / (float)trackW;
		progress = Mth.clamp(progress, 0, 1);
		double min = slider.getMinimum();
		double max = slider.getMaximum();
		slider.setValue(min + (max - min) * progress);
	}

	@Override
	protected double minHeight()
	{
		return 25;
	}
}
