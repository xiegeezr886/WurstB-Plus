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
import net.wurstclient.settings.SliderSetting;

/**
 * 仿 VAPE NumberSliderComponent 的滑块行。
 */
public class SliderComponent extends ValueRowComponent
{
	private final SliderSetting slider;
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
		
		int trackX = (int)(x + getWidth() * 0.45);
		int trackY = (int)y + 9;
		int trackW = (int)(getWidth() * 0.5) - 4;
		int trackRight = trackX + trackW;
		
		// 轨道
		FlatRenderer.fillRoundedRect(graphics, trackX, trackY - 1,
			trackRight, trackY + 1, 2, 0xFF2A292A);
		
		// 进度
		double min = slider.getMinimum();
		double max = slider.getMaximum();
		double value = slider.getValue();
		float progress = (float)((value - min) / (max - min));
		int progressX = trackX + Math.round(trackW * progress);
		if(progressX > trackX)
			FlatRenderer.fillRoundedRect(graphics, trackX, trackY - 1,
				progressX, trackY + 1, 2, 0xFFD61846);
		
		// 滑块圆点
		FlatRenderer.fillRoundedRect(graphics, progressX - 2, trackY - 4,
			progressX + 2, trackY + 4, 4, 0xFFF0F0F0);
		
		// 值文字
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
		int trackX = (int)(x + getWidth() * 0.45);
		int trackW = (int)(getWidth() * 0.5) - 4;
		float progress = (mouseX - trackX) / (float)trackW;
		progress = Mth.clamp(progress, 0, 1);
		double min = slider.getMinimum();
		double max = slider.getMaximum();
		slider.setValue(min + (max - min) * progress);
	}
}
