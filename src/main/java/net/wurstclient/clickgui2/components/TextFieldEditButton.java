/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clickgui2.components;

import java.util.Objects;

import org.lwjgl.glfw.GLFW;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Style;
import net.wurstclient.clickgui2.ClickGui;
import net.wurstclient.clickgui2.Component;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.clickgui2.animation.HoverAnimation;
import net.wurstclient.clickgui2.screens.EditTextFieldScreen;
import net.wurstclient.settings.TextFieldSetting;
import net.wurstclient.util.ChatUtils;

public final class TextFieldEditButton extends Component
{
	private static final ClickGui GUI = WURST.getGui();
	private static final Font TR = MC.font;
	private static final int TEXT_HEIGHT = 11;
	
	private final TextFieldSetting setting;
	private final HoverAnimation hoverAnimation = new HoverAnimation();
	
	public TextFieldEditButton(TextFieldSetting setting)
	{
		this.setting = Objects.requireNonNull(setting);
		setWidth(getDefaultWidth());
		setHeight(getDefaultHeight());
	}
	
	@Override
	public void handleMouseClick(double mouseX, double mouseY, int mouseButton)
	{
		if(mouseY < getY() + TEXT_HEIGHT)
			return;
		
		switch(mouseButton)
		{
			case GLFW.GLFW_MOUSE_BUTTON_LEFT:
			MC.setScreen(new EditTextFieldScreen(MC.screen, setting));
			break;
			
			case GLFW.GLFW_MOUSE_BUTTON_RIGHT:
			setting.resetToDefault();
			break;
		}
	}
	
	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY,
		float partialTicks)
	{
		int x1 = getX();
		int x2 = x1 + getWidth();
		int y1 = getY();
		int y2 = y1 + getHeight();
		int y3 = y1 + TEXT_HEIGHT;
		
		boolean hovering = isHovering(mouseX, mouseY);
		boolean hText = hovering && mouseY < y3;
		boolean hBox = hovering && mouseY >= y3;
		
		if(hText)
			GUI.setTooltip(ChatUtils.wrapText(setting.getDescription(), 200));
		else if(hBox)
			GUI.setTooltip(ChatUtils.wrapText(setting.getValue(), 200));
		
		float hover = hoverAnimation.update(hBox);
		FlatRenderer.drawControl(context, x1, y1, x2, y2, 3,
			GUI.getTheme(), hover, false);
		context.fill(x1 + 2, y3, x2 - 2, y3 + 1,
			GUI.getTheme().accent(0.24F));
		
		// text
		int txtColor = GUI.getTxtColor();
		context.drawString(TR, setting.getName(), x1, y1 + 2, txtColor, false);
		String value = setting.getValue();
		int maxWidth = getWidth() - TR.width("...") - 2;
		int maxLength = TR.getSplitter().formattedIndexByWidth(value,
			maxWidth, Style.EMPTY);
		if(maxLength < value.length())
			value = value.substring(0, maxLength) + "...";
		context.drawString(TR, value, x1 + 2, y3 + 2, txtColor, false);
	}
	
	@Override
	public int getDefaultWidth()
	{
		return TR.width(setting.getName()) + 4;
	}
	
	@Override
	public int getDefaultHeight()
	{
		return TEXT_HEIGHT * 2;
	}
}
