/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.options;

import net.minecraft.client.gui.GuiGraphics;

import com.mojang.blaze3d.platform.InputConstants;
import net.wurstclient.util.render.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import org.lwjgl.glfw.GLFW;

public class PressAKeyScreen extends Screen
{
	private PressAKeyCallback prevScreen;
	
	public PressAKeyScreen(PressAKeyCallback prevScreen)
	{
		super(Component.literal(""));
		
		if(!(prevScreen instanceof Screen))
			throw new IllegalArgumentException("prevScreen is not a screen");
		
		this.prevScreen = prevScreen;
	}
	
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers)
	{
		if(keyCode != GLFW.GLFW_KEY_ESCAPE)
			prevScreen.setKey(InputConstants.getKey(keyCode, scanCode).getName());
		
		minecraft.setScreen((Screen)prevScreen);
		return true;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button)
	{
		prevScreen.setKey(
			InputConstants.Type.MOUSE.getOrCreate(button).getName());
		minecraft.setScreen((Screen)prevScreen);
		return true;
	}
	
	@Override
	public boolean shouldCloseOnEsc()
	{
		return false;
	}

	@Override
	public boolean isPauseScreen()
	{
		return false;
	}
@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks)
	{
		extractContents(new GuiGraphicsExtractor(graphics), mouseX, mouseY, partialTicks);
	}

	private void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY,
		float partialTicks)
	{
		context.centeredText(font, "请按下键盘按键或鼠标按钮", width / 2,
			height / 4 + 48, CommonColors.WHITE);

		for(Renderable drawable : renderables)
			drawable.render(context.getInner(), mouseX, mouseY, partialTicks);
	}
}
