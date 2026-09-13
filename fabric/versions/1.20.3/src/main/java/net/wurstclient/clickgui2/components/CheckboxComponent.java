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
import net.wurstclient.clickgui2.animation.HoverAnimation;
import net.wurstclient.clickgui2.ClickGui;
import net.wurstclient.clickgui2.ClickGuiIcons;
import net.wurstclient.clickgui2.Component;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.settings.CheckboxSetting;

public final class CheckboxComponent extends Component
{
	private static final ClickGui GUI = WURST.getGui();
	private static final Font TR = MC.font;
	private static final int BOX_SIZE = 11;

	private final CheckboxSetting setting;
	private final HoverAnimation hoverAnimation = new HoverAnimation();

	public CheckboxComponent(CheckboxSetting setting)
	{
		this.setting = setting;
		setWidth(getDefaultWidth());
		setHeight(getDefaultHeight());
	}

	@Override
	public void handleMouseClick(double mouseX, double mouseY, int mouseButton)
	{
		if(mouseButton == GLFW.GLFW_MOUSE_BUTTON_RIGHT
			&& setting.hasChildren())
		{
			setting.setExpanded(!setting.isExpanded());
			return;
		}

		int expandX = getX() + getWidth() - 10;
		if(setting.hasChildren() && mouseButton == GLFW.GLFW_MOUSE_BUTTON_LEFT
			&& mouseX >= expandX)
		{
			setting.setExpanded(!setting.isExpanded());
			return;
		}

		switch(mouseButton)
		{
			case GLFW.GLFW_MOUSE_BUTTON_LEFT:
			setting.setChecked(!setting.isChecked());
			break;

			case GLFW.GLFW_MOUSE_BUTTON_RIGHT:
			setting.setChecked(setting.isCheckedByDefault());
			break;
		}
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY,
		float partialTicks)
	{
		int x1 = getX();
		int x2 = x1 + getWidth();
		int x3 = x1 + BOX_SIZE;
		int y1 = getY();
		int y2 = y1 + getHeight();

		boolean hovering = isHovering(mouseX, mouseY);
		boolean hText = hovering && mouseX >= x3;
		boolean hasChildren = setting.hasChildren();

		if(hText)
			GUI.setTooltip(getTooltip());

		if(setting.isLocked())
			hovering = false;

		float hover = hoverAnimation.update(hovering);
		FlatRenderer.drawControl(context, x1, y1, x2, y2, 3,
			GUI.getTheme(), hover, false);

		FlatRenderer.drawControl(context, x1, y1, x3, y2, 3,
			GUI.getTheme(), hover, setting.isChecked());

		String name = setting.getName();
		int textRight = hasChildren ? x2 - 14 : x2;
		float scale = 0.75F;
		String trimmedName = TR.plainSubstrByWidth(name,
			Math.round((textRight - x3 - 4) / scale));
		context.pose().pushPose();
		context.pose().scale(scale, scale, 1);
		context.drawString(TR, trimmedName,
			Math.round((x3 + 2) / scale),
			Math.round((y1 + 2) / scale), GUI.getTxtColor(), false);
		context.pose().popPose();

		if(hasChildren)
		{
			int arrowX = x2 - 9;
			int arrowColor = mouseX >= arrowX && hovering
				? 0xFFFFFFFF : 0xFF888888;
			String arrow = setting.isExpanded() ? "\u25BC" : "\u25B6";
			context.drawCenteredString(TR, arrow, arrowX + 3, y1 + 2, arrowColor);
		}
	}

	private String getTooltip()
	{
		String tooltip = setting.getWrappedDescription(200);
		if(setting.isLocked())
		{
			tooltip += "\n\n\u6B64\u590D\u9009\u6846\u9501\u5B9A\u4E3A ";
			tooltip += setting.isChecked() + "\u3002";
		}

		return tooltip;
	}

	@Override
	public int getDefaultWidth()
	{
		int width = BOX_SIZE + TR.width(setting.getName()) + 2;
		if(setting.hasChildren())
			width += 12;
		return width;
	}

	@Override
	public int getDefaultHeight()
	{
		return BOX_SIZE;
	}
}
