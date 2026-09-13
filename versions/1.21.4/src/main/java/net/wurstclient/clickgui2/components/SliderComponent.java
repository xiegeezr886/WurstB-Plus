/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clickgui2.components;

import org.lwjgl.glfw.GLFW;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.wurstclient.clickgui2.animation.HoverAnimation;
import net.wurstclient.clickgui2.ClickGui;
import net.wurstclient.clickgui2.Component;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.clickgui2.screens.EditSliderScreen;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.util.RenderUtils;

public final class SliderComponent extends Component
{
	private static final ClickGui GUI = WURST.getGui();
	private static final Font TR = MC.font;
	private static final int TEXT_HEIGHT = 11;
	
	private final SliderSetting setting;
	private final HoverAnimation hoverAnimation = new HoverAnimation();
	private boolean dragging;
	
	public SliderComponent(SliderSetting setting)
	{
		this.setting = setting;
		setWidth(getDefaultWidth());
		setHeight(getDefaultHeight());
	}
	
	@Override
	public void handleMouseClick(double mouseX, double mouseY, int mouseButton)
	{
		if(mouseY < getY() + 11)
			return;
		
		switch(mouseButton)
		{
			case GLFW.GLFW_MOUSE_BUTTON_LEFT:
			if(Screen.hasControlDown())
				MC.setScreen(new EditSliderScreen(MC.screen, setting));
			else
				dragging = true;
			break;
			
			case GLFW.GLFW_MOUSE_BUTTON_RIGHT:
			setting.setValue(setting.getDefaultValue());
			break;
		}
	}
	
	private void handleDragging(int mouseX, int x3, int x4)
	{
		if(!dragging)
			return;
		
		if(!GUI.isLeftMouseButtonPressed())
		{
			dragging = false;
			return;
		}
		
		double sliderStartX = x3;
		double sliderWidth = x4 - x3;
		double mousePercentage = (mouseX - sliderStartX) / sliderWidth;
		
		double min = setting.getMinimum();
		double range = setting.getRange();
		double value = min + range * mousePercentage;
		
		setting.setValue(value);
	}
	
	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY,
		float partialTicks)
	{
		int x1 = getX();
		int x2 = x1 + getWidth();
		int x3 = x1 + 2;
		int x4 = x2 - 2;
		int y1 = getY();
		int y2 = y1 + getHeight();
		int y3 = y1 + TEXT_HEIGHT;
		int y4 = y3 + 4;
		int y5 = y2 - 4;
		
		handleDragging(mouseX, x3, x4);
		
		boolean hovering = isHovering(mouseX, mouseY);
		boolean hText = hovering && mouseY < y3;
		boolean hSlider = hovering && mouseY >= y3 || dragging;
		
		boolean grayedOut = setting.isDisabled() || setting.isLocked();
		float hover = hoverAnimation.update(hSlider);
		
		if(hText)
			GUI.setTooltip(getTextTooltip());
		else if(hSlider && !dragging)
			GUI.setTooltip(getSliderTooltip());
		
		if(grayedOut)
		{
			hovering = false;
			hSlider = false;
		}
		
		// background (around the rail)
		FlatRenderer.drawControl(context, x1, y1, x2, y2, 3,
			GUI.getTheme(), hover, false);
		
		// limit
		float xl1 = x3;
		float xl2 = x4;
		if(!grayedOut && setting.isLimited())
		{
			double ratio = (x4 - x3) / setting.getRange();
			xl1 += ratio * (setting.getUsableMin() - setting.getMinimum());
			xl2 += ratio * (setting.getUsableMax() - setting.getMaximum());
			
			int limitColor = 0xA0F05A67;
			float[][] limitVertices = {{x3, y4}, {x3, y5}, {xl1, y5}, {xl1, y4},
				{xl2, y4}, {xl2, y5}, {x4, y5}, {x4, y4}};
			RenderUtils.fillQuads2D(context, limitVertices, limitColor);
		}
		
		FlatRenderer.drawSliderTrack(context, (int)xl1, y4, (int)xl2, y5,
			(float)setting.getPercentage(), GUI.getTheme(), hover);
		
		PoseStack matrices = context.pose();
		matrices.pushPose();
		matrices.translate(0, 0, 2);
		
		// knob
		float xk1 = x1 + (x2 - x1 - 8) * (float)setting.getPercentage();
		float xk2 = xk1 + 8;
		float yk1 = y3 + 1.5F;
		float yk2 = y2 - 1.5F;
		int knobColor = grayedOut ? 0xC0808080 : RenderUtils
			.toIntColor(setting.getKnobColor(), hSlider ? 1 : 0.75F);
		FlatRenderer.fillRoundedRect(context, (int)xk1, (int)yk1, (int)xk2,
			(int)yk2, 3, knobColor);
		FlatRenderer.drawRoundedOutline(context, (int)xk1, (int)yk1,
			(int)xk2, (int)yk2, 3, GUI.getTheme().highlight(0.48F));
		
		matrices.popPose();
		
		// text
		String name = setting.getName();
		String value = setting.getValueString();
		int valueWidth = TR.width(value);
		int txtColor = GUI.getTxtColor();
		context.drawString(TR, name, x1, y1 + 2, txtColor, false);
		context.drawString(TR, value, x2 - valueWidth, y1 + 2, txtColor, false);
	}
	
	private String getTextTooltip()
	{
		String tooltip = setting.getWrappedDescription(200);
		
		if(setting.isDisabled())
			tooltip += "\n\n\u6B64\u6ED1\u5757\u5DF2\u7981\u7528\u3002";
		else if(setting.isLocked())
		{
			tooltip += "\n\n\u6B64\u6ED1\u5757\u9501\u5B9A\u4E3A ";
			tooltip += setting.getValueString() + "\u3002";
		}
		
		return tooltip;
	}
	
	private String getSliderTooltip()
	{
		String tooltip =
			"\u00a7e[Ctrl]\u00a7r+\u00a7e[\u5DE6\u952E]\u00a7r \u7CBE\u786E\u8F93\u5165\n";
		tooltip += "\u00a7e[\u53F3\u952E]\u00a7r \u91CD\u7F6E";
		return tooltip;
	}
	
	@Override
	public int getDefaultWidth()
	{
		int nameWitdh = TR.width(setting.getName());
		int valueWidth = TR.width(setting.getValueString());
		return nameWitdh + valueWidth + 6;
	}
	
	@Override
	public int getDefaultHeight()
	{
		return TEXT_HEIGHT * 2;
	}
}
