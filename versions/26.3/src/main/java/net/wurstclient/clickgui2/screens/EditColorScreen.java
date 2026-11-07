/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clickgui2.screens;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.wurstclient.hud2.nanovg.NanoVGUI;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.util.ColorUtils;

public final class EditColorScreen extends Screen
{
	private final Screen prevScreen;
	private final ColorSetting colorSetting;
	private Color color;
	
	private EditBox hexValueField;
	private EditBox redValueField;
	private EditBox greenValueField;
	private EditBox blueValueField;
	
	private Button doneButton;
	
	private final Identifier paletteIdentifier =
		Identifier.fromNamespaceAndPath("wurst", "colorpalette.png");
	private BufferedImage paletteAsBufferedImage;
	
	private int paletteX = 0;
	private int paletteY = 0;
	
	private final int paletteWidth = 200;
	private final int paletteHeight = 84;
	
	private int fieldsX = 0;
	private int fieldsY = 0;
	
	private boolean ignoreChanges;
	
	public EditColorScreen(Screen prevScreen, ColorSetting colorSetting)
	{
		super(Component.literal(""));
		this.prevScreen = prevScreen;
		this.colorSetting = colorSetting;
		color = colorSetting.getColor();
	}
	
	@Override
	public void init()
	{
		// Cache color palette
		try(InputStream stream = minecraft.getResourceManager()
			.getResourceOrThrow(paletteIdentifier).open())
		{
			paletteAsBufferedImage = ImageIO.read(stream);
			
		}catch(IOException e)
		{
			paletteAsBufferedImage = null;
			e.printStackTrace();
		}
		
		Font tr = minecraft.font;
		paletteX = width / 2 - 100;
		paletteY = 32;
		fieldsX = width / 2 - 100;
		fieldsY = 129 + 5;
		
		hexValueField =
			new EditBox(tr, fieldsX, fieldsY, 92, 20, Component.literal(""));
		hexValueField.setValue(ColorUtils.toHex(color).substring(1));
		hexValueField.setMaxLength(6);
		hexValueField.setResponder(s -> updateColor(true));
		
		// RGB fields
		redValueField = new EditBox(tr, fieldsX, fieldsY + 35, 50, 20,
			Component.literal(""));
		redValueField.setValue("" + color.getRed());
		redValueField.setMaxLength(3);
		redValueField.setResponder(s -> updateColor(false));
		
		greenValueField = new EditBox(tr, fieldsX + 75, fieldsY + 35,
			50, 20, Component.literal(""));
		greenValueField.setValue("" + color.getGreen());
		greenValueField.setMaxLength(3);
		greenValueField.setResponder(s -> updateColor(false));
		
		blueValueField = new EditBox(tr, fieldsX + 150, fieldsY + 35,
			50, 20, Component.literal(""));
		blueValueField.setValue("" + color.getBlue());
		blueValueField.setMaxLength(3);
		blueValueField.setResponder(s -> updateColor(false));
		
		addWidget(hexValueField);
		addWidget(redValueField);
		addWidget(greenValueField);
		addWidget(blueValueField);
		
		setFocused(hexValueField);
		hexValueField.setFocused(true);
		hexValueField.setCursorPosition(0);
		hexValueField.setHighlightPos(6);
		
		doneButton = Button.builder(Component.literal("完成"), b -> done())
			.bounds(fieldsX, height - 30, 200, 20).build();
		addRenderableWidget(doneButton);
	}
	
	private void updateColor(boolean hex)
	{
		if(ignoreChanges)
			return;
		
		Color newColor;
		
		if(hex)
			newColor = ColorUtils.tryParseHex("#" + hexValueField.getValue());
		else
			newColor = ColorUtils.tryParseRGB(redValueField.getValue(),
				greenValueField.getValue(), blueValueField.getValue());
		
		if(newColor == null || newColor.equals(color))
			return;
		
		color = newColor;
		ignoreChanges = true;
		hexValueField.setValue(ColorUtils.toHex(color).substring(1));
		redValueField.setValue("" + color.getRed());
		greenValueField.setValue("" + color.getGreen());
		blueValueField.setValue("" + color.getBlue());
		ignoreChanges = false;
	}
	
	private void done()
	{
		colorSetting.setColor(color);
		minecraft.setScreenAndShow(prevScreen);
	}
	
	@Override
	public void tick()
	{
	}
	
	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY,
		float partialTicks)
	{
		extractBackground(context, mouseX, mouseY, partialTicks);
		hexValueField.extractRenderState(context, mouseX, mouseY, partialTicks);
		redValueField.extractRenderState(context, mouseX, mouseY, partialTicks);
		greenValueField.extractRenderState(context, mouseX, mouseY, partialTicks);
		blueValueField.extractRenderState(context, mouseX, mouseY, partialTicks);
		super.extractRenderState(context, mouseX, mouseY, partialTicks);

		NanoVGUI.pass(context, () -> {
			NanoVGUI.centeredText(colorSetting.getTranslatedName(), width / 2,
				16, 0xF0F0F0, 9F);

			// Draw palette
			NanoVGUI.image(paletteIdentifier, paletteX, paletteY,
				paletteWidth, paletteHeight, 0, 0xFFFFFFFF);

			// RGB letters
			NanoVGUI.text("#",
				fieldsX - 3 - NanoVGUI.width("#", 9F), fieldsY + 6,
				0xF0F0F0, 9F);
			NanoVGUI.text("\u7EA2:",
				fieldsX - 3 - NanoVGUI.width("\u7EA2:", 9F),
				fieldsY + 6 + 35, 0xFF0000, 9F);
			NanoVGUI.text("\u7EFF:",
				fieldsX + 75 - 3 - NanoVGUI.width("\u7EFF:", 9F),
				fieldsY + 6 + 35, 0x00FF00, 9F);
			NanoVGUI.text("\u84DD:",
				fieldsX + 150 - 3 - NanoVGUI.width("\u84DD:", 9F),
				fieldsY + 6 + 35, 0x0000FF, 9F);

			// Color preview
			int borderSize = 1;
			int boxWidth = 92;
			int boxHeight = 20;
			int boxX = width / 2 + 8;
			int boxY = fieldsY;

			// Border
			NanoVGUI.fill(boxX - borderSize, boxY - borderSize,
				boxX + boxWidth + borderSize, boxY + boxHeight + borderSize,
				0xFFAAAAAA);

			// Color box
			NanoVGUI.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight,
				color.getRGB());
		});
	}
	
	@Override
	public void resize(int width, int height)
	{
		String hex = hexValueField.getValue();
		String r = redValueField.getValue();
		String g = greenValueField.getValue();
		String b = blueValueField.getValue();
		
		init(width, height);
		
		hexValueField.setValue(hex);
		redValueField.setValue(r);
		greenValueField.setValue(g);
		blueValueField.setValue(b);
	}
	
	@Override
	public boolean keyPressed(KeyEvent context)
	{
		switch(context.key())
		{
			case InputConstants.KEY_RETURN:
			done();
			break;
			
			case InputConstants.KEY_ESCAPE:
			minecraft.setScreenAndShow(prevScreen);
			break;
		}
		
		return super.keyPressed(context);
	}
	
	@Override
	public boolean mouseClicked(MouseButtonEvent context, boolean doubleClick)
	{
		double mouseX = context.x();
		double mouseY = context.y();
		if(mouseX >= paletteX && mouseX <= paletteX + paletteWidth
			&& mouseY >= paletteY && mouseY <= paletteY + paletteHeight)
		{
			if(paletteAsBufferedImage == null)
				return super.mouseClicked(context, doubleClick);
			
			int x = (int)Math.round((mouseX - paletteX) / paletteWidth
				* paletteAsBufferedImage.getWidth());
			int y = (int)Math.round((mouseY - paletteY) / paletteHeight
				* paletteAsBufferedImage.getHeight());
			
			if(x > 0 && y > 0 && x < paletteAsBufferedImage.getWidth()
				&& y < paletteAsBufferedImage.getHeight())
			{
				int rgb = paletteAsBufferedImage.getRGB(x, y);
				Color color = new Color(rgb, true);
				
				// Set color if pixel has full alpha
				if(color.getAlpha() >= 255)
					setColor(color);
			}
		}
		
		return super.mouseClicked(context, doubleClick);
	}
	
	private void setColor(Color color)
	{
		hexValueField.setValue(ColorUtils.toHex(color).substring(1));
		redValueField.setValue("" + color.getRed());
		greenValueField.setValue("" + color.getGreen());
		blueValueField.setValue("" + color.getBlue());
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
