/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clickgui2.screens;

import net.minecraft.client.gui.GuiGraphics;

import org.lwjgl.glfw.GLFW;
import net.minecraft.client.gui.Font;
import net.wurstclient.util.render.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.wurstclient.hud2.nanovg.NanoVGUI;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.MathUtils;

public final class EditSliderScreen extends Screen
{
	private final Screen prevScreen;
	private final SliderSetting slider;
	
	private EditBox valueField;
	private Button doneButton;
	
	public EditSliderScreen(Screen prevScreen, SliderSetting slider)
	{
		super(Component.literal(""));
		this.prevScreen = prevScreen;
		this.slider = slider;
	}
	
	@Override
	public void init()
	{
		int x1 = width / 2 - 100;
		int y1 = 60;
		int y2 = height / 3 * 2;
		
		Font tr = minecraft.font;
		ValueDisplay vd = ValueDisplay.DECIMAL;
		String valueString = vd.getValueString(slider.getValue());
		
		valueField = new EditBox(tr, x1, y1, 200, 20, Component.literal(""));
		valueField.setValue(valueString);
		valueField.setCursorPosition(0);
		
		addWidget(valueField);
		setFocused(valueField);
		valueField.setFocused(true);
		
		doneButton = Button.builder(Component.literal("完成"), b -> done())
			.bounds(x1, y2, 200, 20).build();
		addRenderableWidget(doneButton);
	}
	
	private void done()
	{
		String value = valueField.getValue();
		
		if(MathUtils.isDouble(value))
			slider.setValue(Double.parseDouble(value));
		
		minecraft.setScreen(prevScreen);
	}
	
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers)
	{
		switch(keyCode)
		{
			case GLFW.GLFW_KEY_ENTER:
			done();
			break;
			
			case GLFW.GLFW_KEY_ESCAPE:
			minecraft.setScreen(prevScreen);
			break;
		}
		
		return super.keyPressed(keyCode, scanCode, modifiers);
	}
	
	@Override
	public void tick()
	{
	}
@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks)
	{
		extractContents(new GuiGraphicsExtractor(graphics), mouseX, mouseY, partialTicks);
	}

	private void extractContents(GuiGraphicsExtractor context, int mouseX,
		int mouseY, float partialTicks)
	{
		renderBackground(context.getInner(), mouseX, mouseY, partialTicks);
		valueField.render(context.getInner(), mouseX, mouseY, partialTicks);
		super.render(context.getInner(), mouseX, mouseY, partialTicks);

		NanoVGUI.pass(context, () -> NanoVGUI.centeredText(
			slider.getTranslatedName(), width / 2, 20, 0xFFFFFFFF, 9F));
	}
	
	@Override
	public boolean isPauseScreen()
	{
		return false;
	}
	
	@Override
	public boolean shouldCloseOnEsc()
	{
		return false;
	}
}
