/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clickgui2;

import org.lwjgl.glfw.GLFW;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.wurstclient.WurstClient;
import net.wurstclient.settings.EnumSetting;

public final class ComboBoxPopup<T extends Enum<T>> extends Popup
{
	private static final ClickGui GUI = WurstClient.INSTANCE.getGui();
	private static final Font TR = WurstClient.MC.font;
	
	private final EnumSetting<T> setting;
	private final int popupWidth;
	
	public ComboBoxPopup(Component owner, EnumSetting<T> setting,
		int popupWidth)
	{
		super(owner);
		this.setting = setting;
		this.popupWidth = popupWidth;
		
		setWidth(getDefaultWidth());
		setHeight(getDefaultHeight());
		
		setX(owner.getWidth() - getWidth());
		setY(owner.getHeight());
	}
	
	@Override
	public void handleMouseClick(int mouseX, int mouseY, int mouseButton)
	{
		if(mouseButton != GLFW.GLFW_MOUSE_BUTTON_LEFT)
			return;
		
		int yi1 = getY() - 11;
		for(T value : setting.getValues())
		{
			if(value == setting.getSelected())
				continue;
			
			yi1 += 11;
			int yi2 = yi1 + 11;
			
			if(mouseY < yi1 || mouseY >= yi2)
				continue;
			
			setting.setSelected(value);
			close();
			break;
		}
	}
	
	@Override
	public void render(GuiGraphicsExtractor context, int mouseX, int mouseY)
	{
		int x1 = getX();
		int x2 = x1 + getWidth();
		int y1 = getY();
		int y2 = y1 + getHeight();
		
		boolean hovering = isHovering(mouseX, mouseY, x1, x2, y1, y2);
		
		if(hovering)
			GUI.setTooltip("");
		
		int yi1 = y1 - 11;
		for(T value : setting.getValues())
		{
			if(value == setting.getSelected())
				continue;
			
			yi1 += 11;
			int yi2 = yi1 + 11;
			
			boolean hValue = hovering && mouseY >= yi1 && mouseY < yi2;
			FlatRenderer.drawControl(context, x1 + 1, yi1, x2 - 1, yi2,
				2, GUI.getTheme(), hValue ? 1 : 0, false);
			
			context.text(TR, value.toString(), x1 + 2, yi1 + 2,
				GUI.getTxtColor(), false);
		}
	}
	
	private boolean isHovering(int mouseX, int mouseY, int x1, int x2, int y1,
		int y2)
	{
		return mouseX >= x1 && mouseY >= y1 && mouseX < x2 && mouseY < y2;
	}
	
	@Override
	public int getDefaultWidth()
	{
		return popupWidth + 15;
	}
	
	@Override
	public int getDefaultHeight()
	{
		int numValues = setting.getValues().length;
		return (numValues - 1) * 11;
	}
}
