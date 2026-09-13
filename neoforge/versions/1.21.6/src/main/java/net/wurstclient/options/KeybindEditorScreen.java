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
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.wurstclient.WurstClient;

public final class KeybindEditorScreen extends Screen
	implements PressAKeyCallback
{
	private final Screen prevScreen;
	
	private String key;
	private final String oldKey;
	private final String oldCommands;
	
	private EditBox commandField;
	
	public KeybindEditorScreen(Screen prevScreen)
	{
		super(Component.literal(""));
		this.prevScreen = prevScreen;
		
		key = "NONE";
		oldKey = null;
		oldCommands = null;
	}
	
	public KeybindEditorScreen(Screen prevScreen, String key, String commands)
	{
		super(Component.literal(""));
		this.prevScreen = prevScreen;
		
		this.key = key;
		oldKey = key;
		oldCommands = commands;
	}
	
	@Override
	public void init()
	{
		addRenderableWidget(Button
			.builder(Component.literal("更改按键"),
				b -> minecraft.setScreen(new PressAKeyScreen(this)))
			.bounds(width / 2 - 100, 60, 200, 20).build());
		
		addRenderableWidget(Button.builder(Component.literal("保存"), b -> save())
			.bounds(width / 2 - 100, height / 4 + 72, 200, 20).build());
		
		addRenderableWidget(Button
			.builder(Component.literal("取消"), b -> minecraft.setScreen(prevScreen))
			.bounds(width / 2 - 100, height / 4 + 96, 200, 20).build());
		
		commandField = new EditBox(font, width / 2 - 100, 100,
			200, 20, Component.literal(""));
		commandField.setMaxLength(65536);
		addWidget(commandField);
		setFocused(commandField);
		commandField.setFocused(true);
		
		if(oldCommands != null)
			commandField.setValue(oldCommands);
	}
	
	private void save()
	{
		if(oldKey != null)
			WurstClient.INSTANCE.getKeybinds().remove(oldKey);
		
		WurstClient.INSTANCE.getKeybinds().add(key, commandField.getValue());
		minecraft.setScreen(prevScreen);
	}
	
	@Override
	public boolean mouseClicked(MouseButtonEvent context, boolean doubleClick)
	{
		commandField.mouseClicked(context, doubleClick);
		return super.mouseClicked(context, doubleClick);
	}
@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks)
	{
		renderContents(new GuiGraphicsExtractor(graphics), mouseX, mouseY, partialTicks);
	}

	private void renderContents(GuiGraphicsExtractor context, int mouseX, int mouseY,
		float partialTicks)
	{
		context.centeredText(font, oldKey != null ? "编辑键位" : "添加键位",
			width / 2, 20, 0xFFFFFFFF);
		
		context.text(font, "按键: " + getDisplayKey(),
			width / 2 - 100, 47, 0xFFA0A0A0);
		context.text(font, "命令（使用 ';' 分隔）", width / 2 - 100, 87,
			0xFFA0A0A0);
		
		commandField.render(context.getInner(), mouseX, mouseY, partialTicks);
		for(Renderable drawable : renderables)
			drawable.render(context.getInner(), mouseX, mouseY, partialTicks);
	}
	
	@Override
	public void onClose()
	{
		minecraft.setScreen(prevScreen);
	}

	private String getDisplayKey()
	{
		if("NONE".equals(key))
			return "无";
		try
		{
			return InputConstants.getKey(key).getDisplayName().getString();
		}catch(IllegalArgumentException e)
		{
			return key;
		}
	}

	@Override
	public void setKey(String key)
	{
		this.key = key;
	}
}
