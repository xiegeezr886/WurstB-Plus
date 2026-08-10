/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clickgui2.screens;

import org.joml.Matrix3x2fStack;
import java.util.List;
import java.util.Objects;

import org.lwjgl.glfw.GLFW;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.wurstclient.settings.BlockListSetting;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.RenderUtils;

public final class EditBlockListScreen extends Screen
{
	private final Screen prevScreen;
	private final BlockListSetting blockList;
	
	private ListGui listGui;
	private EditBox blockNameField;
	private Button addButton;
	private Button removeButton;
	private Button doneButton;
	
	private Block blockToAdd;
	
	public EditBlockListScreen(Screen prevScreen, BlockListSetting blockList)
	{
		super(Component.literal(""));
		this.prevScreen = prevScreen;
		this.blockList = blockList;
	}
	
	@Override
	public void init()
	{
		listGui = new ListGui(minecraft, this, blockList.getBlockNames());
		addWidget(listGui);
		
		blockNameField = new EditBox(minecraft.font,
			width / 2 - 152, height - 55, 150, 18, Component.literal(""));
		addWidget(blockNameField);
		blockNameField.setMaxLength(256);
		
		addRenderableWidget(
			addButton = Button.builder(Component.literal("添加"), b -> {
				blockList.add(blockToAdd);
				minecraft.setScreenAndShow(EditBlockListScreen.this);
			}).bounds(width / 2 - 2, height - 56, 30, 20).build());
		
		addRenderableWidget(removeButton =
			Button.builder(Component.literal("移除选中"), b -> {
				blockList
					.remove(blockList.indexOf(listGui.getSelectedBlockName()));
				minecraft.setScreenAndShow(EditBlockListScreen.this);
			}).bounds(width / 2 + 52, height - 56, 100, 20).build());
		
		addRenderableWidget(Button.builder(Component.literal("恢复默认"),
			b -> minecraft.setScreenAndShow(new ConfirmScreen(b2 -> {
				if(b2)
					blockList.resetToDefaults();
				minecraft.setScreenAndShow(EditBlockListScreen.this);
			}, Component.literal("恢复默认"),
				Component.literal("确定吗？"))))
			.bounds(width - 108, 8, 100, 20).build());
		
		addRenderableWidget(doneButton = Button
			.builder(Component.literal("完成"), b -> minecraft.setScreenAndShow(prevScreen))
			.bounds(width / 2 - 100, height - 28, 200, 20).build());
	}
	
	@Override
	public boolean mouseClicked(MouseButtonEvent context, boolean doubleClick)
	{
		blockNameField.mouseClicked(context, doubleClick);
		return super.mouseClicked(context, doubleClick);
	}
	
	@Override
	public boolean keyPressed(KeyEvent context)
	{
		switch(context.key())
		{
			case GLFW.GLFW_KEY_ENTER:
			if(addButton.active)
				addButton.onPress(context);
			break;
			
			case GLFW.GLFW_KEY_DELETE:
			if(!blockNameField.isFocused())
				removeButton.onPress(context);
			break;
			
			case GLFW.GLFW_KEY_ESCAPE:
			doneButton.onPress(context);
			break;
			
			default:
			break;
		}
		
		return super.keyPressed(context);
	}
	
	@Override
	public void tick()
	{
		
		String nameOrId = blockNameField.getValue();
		blockToAdd = BlockUtils.getBlockFromNameOrID(nameOrId);
		addButton.active = blockToAdd != null;
		
		removeButton.active = listGui.getSelected() != null;
	}
	
	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY,
		float partialTicks)
	{
		Matrix3x2fStack matrixStack = context.pose();
		listGui.extractRenderState(context, mouseX, mouseY, partialTicks);
		
		context.centeredText(minecraft.font,
			blockList.getName() + " (" + blockList.size() + ")", width / 2, 12,
			0xFFffffff);
		
		matrixStack.pushMatrix();
		matrixStack.translate(0, 0);
		
		blockNameField.extractRenderState(context, mouseX, mouseY, partialTicks);
		super.extractRenderState(context, mouseX, mouseY, partialTicks);
		
		matrixStack.pushMatrix();
		matrixStack.translate(-64 + width / 2 - 152, 0);
		
		if(blockNameField.getValue().isEmpty() && !blockNameField.isFocused())
			context.text(minecraft.font, "方块名称或ID",
				68, height - 50, 0xFF808080);
		
		int border = blockNameField.isFocused() ? 0xFFFFFFFF : 0xFFA0A0A0;
		int black = 0xFF000000;
		
		context.fill(48, height - 56, 64, height - 36, border);
		context.fill(49, height - 55, 64, height - 37, black);
		context.fill(214, height - 56, 244, height - 55, border);
		context.fill(214, height - 37, 244, height - 36, border);
		context.fill(244, height - 56, 246, height - 36, border);
		context.fill(214, height - 55, 243, height - 52, black);
		context.fill(214, height - 40, 243, height - 37, black);
		context.fill(214, height - 55, 216, height - 37, black);
		context.fill(242, height - 55, 245, height - 37, black);
		
		matrixStack.popMatrix();
		
		RenderUtils.drawItem(context,
			blockToAdd == null ? ItemStack.EMPTY : new ItemStack(blockToAdd),
			width / 2 - 164, height - 52, false);
		
		matrixStack.popMatrix();
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
	
	private final class Entry
		extends ObjectSelectionList.Entry<EditBlockListScreen.Entry>
	{
		private final String blockName;
		
		public Entry(String blockName)
		{
			this.blockName = Objects.requireNonNull(blockName);
		}
		
		@Override
		public Component getNarration()
		{
			Block block = BlockUtils.getBlockFromName(blockName);
			ItemStack stack = new ItemStack(block);
			
			return Component.translatable("narrator.select",
				"方块 " + getDisplayName(stack) + ", " + blockName + ", "
					+ getIdText(block));
		}
		
		@Override
		public boolean mouseClicked(MouseButtonEvent context, boolean doubleClick)
		{
			return context.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT;
		}
		
		@Override
		public void extractContent(GuiGraphicsExtractor context, int mouseX,
			int mouseY, boolean hovered, float tickDelta)
		{
			int x = getContentX();
			int y = getContentY();
			Block block = BlockUtils.getBlockFromName(blockName);
			ItemStack stack = new ItemStack(block);
			Font tr = minecraft.font;
			
			RenderUtils.drawItem(context, stack, x + 1, y + 1, true);
			context.text(tr, getDisplayName(stack), x + 28, y, 0xFFF0F0F0,
				false);
			context.text(tr, blockName, x + 28, y + 9, 0xFFA0A0A0, false);
			context.text(tr, getIdText(block), x + 28, y + 18, 0xFFA0A0A0,
				false);
		}
		
		private String getDisplayName(ItemStack stack)
		{
			return stack.isEmpty() ? "\u00a7o\u672A\u77E5\u65B9\u5757\u00a7r"
				: stack.getHoverName().getString();
		}
		
		private String getIdText(Block block)
		{
			return "\u65B9\u5757ID: " + Block.getId(block.defaultBlockState());
		}
	}
	
	private final class ListGui
		extends ObjectSelectionList<EditBlockListScreen.Entry>
	{
		public ListGui(Minecraft minecraft, EditBlockListScreen screen,
			List<String> list)
		{
			super(minecraft, screen.width, screen.height - 96, 32, 30);
			
			list.stream().map(EditBlockListScreen.Entry::new)
				.forEach(this::addEntry);
		}
		
		public String getSelectedBlockName()
		{
			EditBlockListScreen.Entry selected = getSelected();
			return selected != null ? selected.blockName : null;
		}
	}
}
