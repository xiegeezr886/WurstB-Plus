/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clickgui2.screens;

import org.joml.Matrix3x2fStack;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.wurstclient.hud2.nanovg.NanoVGUI;
import net.wurstclient.settings.BlockSetting;
import net.wurstclient.util.BlockUtils;

public final class EditBlockScreen extends Screen
{
	private final Screen prevScreen;
	private final BlockSetting setting;
	
	private EditBox blockField;
	private Button doneButton;
	
	public EditBlockScreen(Screen prevScreen, BlockSetting setting)
	{
		super(Component.literal(""));
		this.prevScreen = prevScreen;
		this.setting = setting;
	}
	
	@Override
	public void init()
	{
		int x1 = width / 2 - 100;
		int y1 = 59;
		int y2 = height / 3 * 2;
		
		Font tr = minecraft.font;
		String valueString = setting.getBlockName();
		
		blockField = new EditBox(tr, x1, y1, 178, 20, Component.literal(""));
		blockField.setValue(valueString);
		blockField.setCursorPosition(0);
		blockField.setMaxLength(256);
		
		addWidget(blockField);
		setFocused(blockField);
		blockField.setFocused(true);
		
		doneButton = Button.builder(Component.literal("Done"), b -> done())
			.bounds(x1, y2, 200, 20).build();
		addRenderableWidget(doneButton);
	}
	
	private void done()
	{
		String nameOrId = blockField.getValue();
		Block block = BlockUtils.getBlockFromNameOrID(nameOrId);
		
		if(block != null)
			setting.setBlock(block);
		
		minecraft.setScreenAndShow(prevScreen);
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
	public void tick()
	{
	}
	
	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX,
		int mouseY, float partialTicks)
	{
		extractBackground(context, mouseX, mouseY, partialTicks);
		blockField.extractRenderState(context, mouseX, mouseY, partialTicks);
		super.extractRenderState(context, mouseX, mouseY, partialTicks);

		String nameOrId = blockField.getValue();
		Block blockToAdd = BlockUtils.getBlockFromNameOrID(nameOrId);

		if(blockToAdd == null)
			blockToAdd = Blocks.AIR;

		Block renderedBlock = blockToAdd;
		NanoVGUI.pass(context, () -> {
			NanoVGUI.centeredText(setting.getTranslatedName(), width / 2, 20,
				0xFFFFFFFF, 9F);

			int baseX = -64 + width / 2 - 100;
			int baseY = 115;

			boolean lblAbove =
				!blockField.getValue().isEmpty() || blockField.isFocused();
			String lblText = lblAbove ? "方块ID或编号:" : "方块ID或编号";
			int lblX = baseX + (lblAbove ? 50 : 68);
			int lblY = baseY + (lblAbove ? -66 : -50);
			int lblColor = lblAbove ? 0xF0F0F0 : 0x808080;
			NanoVGUI.text(lblText, lblX, lblY, lblColor, 9F);

			int border = blockField.isFocused() ? 0xffffffff : 0xffa0a0a0;
			int black = 0xff000000;

			NanoVGUI.fill(baseX + 48, baseY - 56, baseX + 64, baseY - 36,
				border);
			NanoVGUI.fill(baseX + 49, baseY - 55, baseX + 65, baseY - 37,
				black);
			NanoVGUI.fill(baseX + 242, baseY - 56, baseX + 246, baseY - 36,
				border);
			NanoVGUI.fill(baseX + 241, baseY - 55, baseX + 245, baseY - 37,
				black);

			NanoVGUI.item(context, new ItemStack(renderedBlock), baseX + 52,
				baseY - 52, false);
		});
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
