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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.wurstclient.clickgui2.animation.HoverAnimation;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.clickgui2.ClickGui;
import net.wurstclient.clickgui2.Component;
import net.wurstclient.clickgui2.screens.EditColorScreen;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.util.ColorUtils;
import net.wurstclient.util.RenderUtils;

public final class ColorComponent extends Component
{
	private static final ClickGui GUI = WURST.getGui();
	private static final Font TR = MC.font;
	private static final int TEXT_HEIGHT = 11;
	
	private final ColorSetting setting;
	private final HoverAnimation hoverAnimation = new HoverAnimation();
	
	public ColorComponent(ColorSetting setting)
	{
		this.setting = setting;
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
			MC.setScreenAndShow(new EditColorScreen(MC.gui.screen(), setting));
			break;
			
			case GLFW.GLFW_MOUSE_BUTTON_RIGHT:
			setting.setColor(setting.getDefaultColor());
			break;
		}
	}
	
	@Override
	public void render(GuiGraphicsExtractor context, int mouseX, int mouseY,
		float partialTicks)
	{
		int x1 = getX();
		int x2 = x1 + getWidth();
		int y1 = getY();
		int y2 = y1 + getHeight();
		int y3 = y1 + TEXT_HEIGHT;
		
		boolean hovering = isHovering(mouseX, mouseY);
		boolean hText = hovering && mouseY < y3;
		boolean hColor = hovering && mouseY >= y3;
		
		if(hText)
			GUI.setTooltip(setting.getWrappedDescription(200));
		else if(hColor)
			GUI.setTooltip(getColorTooltip());
		
		float hover = hoverAnimation.update(hovering);
		FlatRenderer.drawControl(context, x1, y1, x2, y2, 3,
			GUI.getTheme(), hover, false);
		
		// box
		FlatRenderer.fillRoundedRect(context, x1 + 1, y3, x2 - 1, y2 - 1,
			2, setting.getColorI(hovering ? 1F : GUI.getOpacity()));
		FlatRenderer.drawRoundedOutline(context, x1, y3, x2, y2, 3,
			GUI.getTheme().controlBorder(hover, false));
		
		// text
		String name = setting.getName();
		String value = ColorUtils.toHex(setting.getColor());
		int valueWidth = TR.width(value);
		int txtColor = GUI.getTxtColor();
		context.text(TR, name, x1, y1 + 2, txtColor, false);
		context.text(TR, value, x2 - valueWidth, y1 + 2, txtColor, false);
	}
	
	private String getColorTooltip()
	{
		String tooltip = "\u00a7c\u7EA2:\u00a7r" + setting.getRed();
		tooltip += " \u00a7a\u7EFF:\u00a7r" + setting.getGreen();
		tooltip += " \u00a79\u84DD:\u00a7r" + setting.getBlue();
		tooltip += "\n\n\u00a7e[\u5DE6\u952E]\u00a7r \u7F16\u8F91";
		tooltip += "\n\u00a7e[\u53F3\u952E]\u00a7r \u91CD\u7F6E";
		return tooltip;
	}
	
	@Override
	public int getDefaultWidth()
	{
		return TR.width(setting.getName() + "#FFFFFF") + 6;
	}
	
	@Override
	public int getDefaultHeight()
	{
		return TEXT_HEIGHT * 2;
	}
}
