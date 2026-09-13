/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clickgui2.components;

import org.lwjgl.glfw.GLFW;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.clickgui2.animation.HoverAnimation;
import net.wurstclient.clickgui2.ClickGui;
import net.wurstclient.clickgui2.Component;
import net.wurstclient.clickgui2.screens.SelectFileScreen;
import net.wurstclient.settings.FileSetting;

public final class FileComponent extends Component
{
	private static final ClickGui GUI = WURST.getGui();
	private static final Font TR = MC.font;
	
	private final FileSetting setting;
	private final HoverAnimation hoverAnimation = new HoverAnimation();
	
	public FileComponent(FileSetting setting)
	{
		this.setting = setting;
		setWidth(getDefaultWidth());
		setHeight(getDefaultHeight());
	}
	
	@Override
	public void handleMouseClick(double mouseX, double mouseY, int mouseButton)
	{
		if(mouseButton != GLFW.GLFW_MOUSE_BUTTON_LEFT)
			return;
		
		if(mouseX < getX() + getWidth() - getButtonWidth() - 4)
			return;
		
		MC.setScreen(new SelectFileScreen(MC.screen, setting));
	}
	
	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY,
		float partialTicks)
	{
		int x1 = getX();
		int x2 = x1 + getWidth();
		int x3 = x2 - getButtonWidth() - 4;
		int y1 = getY();
		int y2 = y1 + getHeight();
		
		boolean hovering = isHovering(mouseX, mouseY);
		boolean hText = hovering && mouseX < x3;
		boolean hBox = hovering && mouseX >= x3;
		
		// tooltip
		if(hText)
			GUI.setTooltip(setting.getWrappedDescription(200));
		else if(hBox)
			GUI.setTooltip("\u00a7e[left-click]\u00a7r to select file");
		
		float hover = hoverAnimation.update(hBox);
		FlatRenderer.drawControl(context, x1, y1, x2, y2, 3,
			GUI.getTheme(), hover, false);
		context.fill(x3, y1 + 2, x3 + 1, y2 - 2,
			GUI.getTheme().accent(0.24F));
		
		// text
		int txtColor = GUI.getTxtColor();
		String labelText = setting.getName() + ":";
		String buttonText = setting.getSelectedFileName();
		context.drawString(TR, labelText, x1, y1 + 2, txtColor, false);
		context.drawString(TR, buttonText, x3 + 2, y1 + 2, txtColor, false);
	}
	
	private int getButtonWidth()
	{
		return TR.width(setting.getSelectedFileName());
	}
	
	@Override
	public int getDefaultWidth()
	{
		String text = setting.getName() + ":";
		return TR.width(text) + getButtonWidth() + 6;
	}
	
	@Override
	public int getDefaultHeight()
	{
		return 11;
	}
}
