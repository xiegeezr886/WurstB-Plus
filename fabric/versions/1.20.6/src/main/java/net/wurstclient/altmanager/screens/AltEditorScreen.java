/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.altmanager.screens;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.lwjgl.glfw.GLFW;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.wurstclient.WurstClient;
import java.util.ArrayList;
import java.util.List;
import net.wurstclient.gui.MainMenuBackground;
import net.wurstclient.hud2.nanovg.NanoVGUI;
import net.wurstclient.altmanager.NameGenerator;
import net.wurstclient.altmanager.SkinStealer;

public abstract class AltEditorScreen extends Screen
{
	private final Path skinFolder =
		WurstClient.INSTANCE.getWurstFolder().resolve("skins");

	protected final Screen prevScreen;

	private EditBox nameOrEmailBox;
	private EditBox passwordBox;

	private Button doneButton;
	private Button stealSkinButton;

	protected String message = "";
	private final List<MoonAccountButton> controls = new ArrayList<>();
	private int formTop;
	private String savedName, savedPassword;

	public AltEditorScreen(Screen prevScreen, Component title)
	{
		super(title);
		this.prevScreen = prevScreen;
	}

	@Override
	public final void init()
	{
		controls.clear();
		formTop = Math.max(42, height / 2 - 66);
		int x = width / 2 - 130;
		nameOrEmailBox = new EditBox(font, x, formTop + 18, 260, 20,
			Component.literal("用户名或邮箱"));
		nameOrEmailBox.setMaxLength(48);
		nameOrEmailBox.setValue(savedName == null ? getDefaultNameOrEmail() : savedName);
		addWidget(nameOrEmailBox);
		passwordBox = new EditBox(font, x, formTop + 54, 260, 20,
			Component.literal("密码（离线账号留空）"));
		passwordBox.setMaxLength(256);
		passwordBox.setValue(savedPassword == null ? getDefaultPassword() : savedPassword);
		passwordBox.setFormatter((text, start) ->
			FormattedCharSequence.forward("*".repeat(text.length()), Style.EMPTY));
		addWidget(passwordBox);
		doneButton = button(x, formTop + 84, 260, getDoneButtonText(), b -> pressDoneButton());
		button(x, formTop + 108, 128, "随机名称",
			b -> nameOrEmailBox.setValue(NameGenerator.generateName()));
		button(x + 132, formTop + 108, 128, "返回", b -> onClose());
		stealSkinButton = button(8, height - 28, 100, "保存皮肤",
			b -> message = stealSkin(getNameOrEmail()));
		button(width - 108, height - 28, 100, "皮肤文件夹", b -> openSkinFolder());
		setInitialFocus(nameOrEmailBox);
		tick();
	}

	private MoonAccountButton button(int x, int y, int w, String label, Button.OnPress action)
	{
		MoonAccountButton button = new MoonAccountButton(x, y, w, label, action);
		controls.add(button);
		return addRenderableWidget(button);
	}

	private void openSkinFolder()
	{
		createSkinFolder();
		Util.getPlatform().openFile(skinFolder.toFile());
	}

	private void createSkinFolder()
	{
		try
		{
			Files.createDirectories(skinFolder);

		}catch(IOException e)
		{
			e.printStackTrace();
			message = "\u00a74\u00a7lSkin folder could not be created.";
		}
	}

	@Override
	public final void tick()
	{

		savedName = nameOrEmailBox.getValue();
		savedPassword = passwordBox.getValue();


		String nameOrEmail = nameOrEmailBox.getValue().trim();
		boolean alex = nameOrEmail.equalsIgnoreCase("Alexander01998");

		doneButton.active = !nameOrEmail.isEmpty()
			&& !(alex && passwordBox.getValue().isEmpty());
		doneButton.setMessage(Component.literal(getDoneButtonText()));

		stealSkinButton.active = !alex;
	}

	/**
	 * @return the user-entered name or email. Cannot be empty when pressing the
	 *         done button. Cannot be null.
	 */
	protected final String getNameOrEmail()
	{
		return nameOrEmailBox.getValue();
	}

	/**
	 * @return the user-entered password. Can be empty. Cannot be null.
	 */
	protected final String getPassword()
	{
		return passwordBox.getValue();
	}

	protected String getDefaultNameOrEmail()
	{
		return minecraft.getUser().getName();
	}

	protected String getDefaultPassword()
	{
		return "";
	}

	protected abstract String getDoneButtonText();

	protected abstract void pressDoneButton();

	protected final void doErrorEffect()
	{
		// Errors are displayed in the status line, without a full-screen flash.
	}

	private final String stealSkin(String name)
	{
		createSkinFolder();
		Path path = skinFolder.resolve(name + ".png");

		try
		{
			URL url = SkinStealer.getSkinUrl(name);

			try(InputStream in = url.openStream())
			{
				Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
			}

			return "\u00a7a\u00a7lSaved skin as " + name + ".png";

		}catch(IOException e)
		{
			e.printStackTrace();
			return "\u00a74\u00a7lSkin could not be saved.";

		}catch(NullPointerException e)
		{
			e.printStackTrace();
			return "\u00a74\u00a7lPlayer does not exist.";
		}
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int int_3)
	{
		if(keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
		{
			if(doneButton.active) doneButton.onPress();
			return true;
		}

		return super.keyPressed(keyCode, scanCode, int_3);
	}

	@Override
	public boolean mouseClicked(double x, double y, int button)
	{
		nameOrEmailBox.mouseClicked(x, y, button);
		passwordBox.mouseClicked(x, y, button);

		if(nameOrEmailBox.isFocused() || passwordBox.isFocused())
			message = "";

		if(button == GLFW.GLFW_MOUSE_BUTTON_4)
		{
			onClose();
			return true;
		}

		return super.mouseClicked(x, y, button);
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY,
		float partialTicks)
	{
		super.render(context, mouseX, mouseY, partialTicks);
		NanoVGUI.pass(context, () -> {
			MainMenuBackground.render(width, height, 255);
			NanoVGUI.fillGradient(0, 0, width, height, 0x50121724, 0xB0080B12);
			NanoVGUI.centeredText(title.getString(), width / 2F, 20, 0xFFECF3FF, 9);
			NanoVGUI.text("用户名或邮箱", width / 2F - 130, formTop + 4, 0xFFADBED4, 8);
			NanoVGUI.text("密码（离线账号留空）", width / 2F - 130, formTop + 40, 0xFFADBED4, 8);
			String[] lines = message.split("\n");
			for(int i = 0; i < lines.length; i++)
				NanoVGUI.centeredText(NanoVGUI.substrByWidth(lines[i], width - 24, 8),
					width / 2F, formTop + 136 + i * 10, 0xFFA7BBD3, 8);
			for(MoonAccountButton button : controls) button.draw(mouseX, mouseY);
		});
		nameOrEmailBox.render(context, mouseX, mouseY, partialTicks);
		passwordBox.render(context, mouseX, mouseY, partialTicks);
	}

	@Override
	public final void onClose()
	{
		minecraft.setScreen(prevScreen);
	}
}
