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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.wurstclient.settings.ItemListSetting;
import net.wurstclient.util.ItemUtils;
import net.wurstclient.util.RenderUtils;

public final class EditItemListScreen extends Screen
{
	private final Screen prevScreen;
	private final ItemListSetting itemList;
	
	private ListGui listGui;
	private EditBox itemNameField;
	private Button addButton;
	private Button removeButton;
	private Button doneButton;
	
	private Item itemToAdd;
	
	public EditItemListScreen(Screen prevScreen, ItemListSetting itemList)
	{
		super(Component.literal(""));
		this.prevScreen = prevScreen;
		this.itemList = itemList;
	}
	
	@Override
	public void init()
	{
		listGui = new ListGui(minecraft, this, itemList.getItemNames());
		addWidget(listGui);
		
		itemNameField = new EditBox(minecraft.font,
			width / 2 - 152, height - 55, 150, 18, Component.literal(""));
		addWidget(itemNameField);
		itemNameField.setMaxLength(256);
		
		addRenderableWidget(
			addButton = Button.builder(Component.literal("添加"), b -> {
				itemList.add(itemToAdd);
				minecraft.setScreen(EditItemListScreen.this);
			}).bounds(width / 2 - 2, height - 56, 30, 20).build());
		
		addRenderableWidget(removeButton =
			Button.builder(Component.literal("移除选中"), b -> {
				itemList.remove(itemList.getItemNames()
					.indexOf(listGui.getSelectedBlockName()));
				minecraft.setScreen(EditItemListScreen.this);
			}).bounds(width / 2 + 52, height - 56, 100, 20).build());
		
		addRenderableWidget(Button.builder(Component.literal("恢复默认"),
			b -> minecraft.setScreen(new ConfirmScreen(b2 -> {
				if(b2)
					itemList.resetToDefaults();
				minecraft.setScreen(EditItemListScreen.this);
			}, Component.literal("恢复默认"),
				Component.literal("确定吗？"))))
			.bounds(width - 108, 8, 100, 20).build());
		
		addRenderableWidget(doneButton = Button
			.builder(Component.literal("完成"), b -> minecraft.setScreen(prevScreen))
			.bounds(width / 2 - 100, height - 28, 200, 20).build());
	}
	
	@Override
	public boolean mouseClicked(MouseButtonEvent context, boolean doubleClick)
	{
		itemNameField.mouseClicked(context, doubleClick);
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
			if(!itemNameField.isFocused())
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
		
		String nameOrId = itemNameField.getValue().toLowerCase();
		itemToAdd = ItemUtils.getItemFromNameOrID(nameOrId);
		addButton.active = itemToAdd != null;
		
		removeButton.active = listGui.getSelected() != null;
	}
	
	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY,
		float partialTicks)
	{
		Matrix3x2fStack matrixStack = context.pose();
		listGui.extractRenderState(context, mouseX, mouseY, partialTicks);
		
		context.centeredText(minecraft.font,
			itemList.getName() + " (" + itemList.getItemNames().size() + ")",
			width / 2, 12, 0xFFFFFFFF);
		
		matrixStack.pushMatrix();
		matrixStack.translate(0, 0);
		
		itemNameField.extractRenderState(context, mouseX, mouseY, partialTicks);
		super.extractRenderState(context, mouseX, mouseY, partialTicks);
		
		matrixStack.pushMatrix();
		matrixStack.translate(-64 + width / 2 - 152, 0);
		
		if(itemNameField.getValue().isEmpty() && !itemNameField.isFocused())
		{
			matrixStack.pushMatrix();
			matrixStack.translate(0, 0);
			context.text(minecraft.font, "物品名称或ID",
				68, height - 50, 0xFF808080);
			matrixStack.popMatrix();
		}
		
		int border = itemNameField.isFocused() ? 0xFFFFFFFF : 0xFFA0A0A0;
		int black = 0xFF000000;
		
		context.fill(48, height - 56, 64, height - 36, border);
		context.fill(49, height - 55, 65, height - 37, black);
		context.fill(214, height - 56, 244, height - 55, border);
		context.fill(214, height - 37, 244, height - 36, border);
		context.fill(244, height - 56, 246, height - 36, border);
		context.fill(213, height - 55, 243, height - 52, black);
		context.fill(213, height - 40, 243, height - 37, black);
		context.fill(213, height - 55, 216, height - 37, black);
		context.fill(242, height - 55, 245, height - 37, black);
		
		matrixStack.popMatrix();
		
		RenderUtils.drawItem(context,
			itemToAdd == null ? ItemStack.EMPTY : new ItemStack(itemToAdd),
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
		extends ObjectSelectionList.Entry<EditItemListScreen.Entry>
	{
		private final String itemName;
		
		public Entry(String itemName)
		{
			this.itemName = Objects.requireNonNull(itemName);
		}
		
		@Override
		public Component getNarration()
		{
			Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemName));
			ItemStack stack = new ItemStack(item);
			
			return Component.translatable("narrator.select",
				"物品 " + getDisplayName(stack) + ", " + itemName + ", "
					+ getIdText(item));
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
			Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemName));
			ItemStack stack = new ItemStack(item);
			Font tr = minecraft.font;
			
			RenderUtils.drawItem(context, stack, x + 1, y + 1, true);
			context.text(tr, getDisplayName(stack), x + 28, y, 0xFFF0F0F0,
				false);
			context.text(tr, itemName, x + 28, y + 9, 0xFFA0A0A0, false);
			context.text(tr, getIdText(item), x + 28, y + 18, 0xFFA0A0A0,
				false);
		}
		
		private String getDisplayName(ItemStack stack)
		{
			return stack.isEmpty() ? "\u00a7o\u672A\u77E5\u7269\u54C1\u00a7r"
				: stack.getHoverName().getString();
		}
		
		private String getIdText(Item item)
		{
			return "\u7269\u54C1ID: " + BuiltInRegistries.ITEM.getId(item);
		}
	}
	
	private final class ListGui
		extends ObjectSelectionList<EditItemListScreen.Entry>
	{
		public ListGui(Minecraft minecraft, EditItemListScreen screen,
			List<String> list)
		{
			super(minecraft, screen.width, screen.height - 96, 32, 30);
			
			list.stream().map(EditItemListScreen.Entry::new)
				.forEach(this::addEntry);
		}
		
		public String getSelectedBlockName()
		{
			EditItemListScreen.Entry selected = getSelected();
			return selected != null ? selected.itemName : null;
		}
	}
}
