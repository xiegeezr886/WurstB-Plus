/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clickgui2.screens;

import org.lwjgl.glfw.GLFW;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.wurstclient.hacks.autolibrarian.BookOffer;
import net.wurstclient.hud2.nanovg.NanoVGUI;
import net.wurstclient.settings.BookOffersSetting;
import net.wurstclient.util.MathUtils;

public final class EditBookOfferScreen extends Screen
{
	private final Screen prevScreen;
	private final BookOffersSetting bookOffers;
	
	private EditBox levelField;
	private Button levelPlusButton;
	private Button levelMinusButton;
	
	private EditBox priceField;
	private Button pricePlusButton;
	private Button priceMinusButton;
	
	private Button saveButton;
	private Button cancelButton;
	
	private BookOffer offerToSave;
	private int index;
	private boolean alreadyAdded;
	
	public EditBookOfferScreen(Screen prevScreen, BookOffersSetting bookOffers,
		int index)
	{
		super(Component.literal(""));
		this.prevScreen = prevScreen;
		this.bookOffers = bookOffers;
		this.index = index;
		offerToSave = bookOffers.getOffers().get(index);
	}
	
	@Override
	public void init()
	{
		levelField = new EditBox(minecraft.font, width / 2 - 32,
			110, 28, 12, Component.literal(""));
		addWidget(levelField);
		levelField.setMaxLength(2);
		levelField.setFilter(t -> {
			if(t.isEmpty())
				return true;
			
			if(!MathUtils.isInteger(t))
				return false;
			
			int level = Integer.parseInt(t);
			if(level < 1 || level > 10)
				return false;
			
			if(offerToSave == null)
				return true;
			
			Enchantment enchantment = offerToSave.getEnchantment();
			return level <= enchantment.getMaxLevel();
		});
		levelField.setResponder(t -> {
			if(!MathUtils.isInteger(t))
				return;
			
			int level = Integer.parseInt(t);
			updateLevel(level, false);
		});
		
		priceField = new EditBox(minecraft.font, width / 2 - 32,
			126, 28, 12, Component.literal(""));
		addWidget(priceField);
		priceField.setMaxLength(2);
		priceField.setFilter(t -> t.isEmpty() || MathUtils.isInteger(t)
			&& Integer.parseInt(t) >= 1 && Integer.parseInt(t) <= 64);
		priceField.setResponder(t -> {
			if(!MathUtils.isInteger(t))
				return;
			
			int price = Integer.parseInt(t);
			updatePrice(price, false);
		});
		
		addRenderableWidget(levelPlusButton =
			Button.builder(Component.literal("+"), b -> updateLevel(1, true))
				.bounds(width / 2 + 2, 110, 20, 12).build());
		levelPlusButton.active = false;
		
		addRenderableWidget(levelMinusButton =
			Button.builder(Component.literal("-"), b -> updateLevel(-1, true))
				.bounds(width / 2 + 26, 110, 20, 12).build());
		levelMinusButton.active = false;
		
		addRenderableWidget(pricePlusButton =
			Button.builder(Component.literal("+"), b -> updatePrice(1, true))
				.bounds(width / 2 + 2, 126, 20, 12).build());
		pricePlusButton.active = false;
		
		addRenderableWidget(priceMinusButton =
			Button.builder(Component.literal("-"), b -> updatePrice(-1, true))
				.bounds(width / 2 + 26, 126, 20, 12).build());
		priceMinusButton.active = false;
		
		addRenderableWidget(
			saveButton = Button.builder(Component.literal("保存"), b -> {
				if(offerToSave == null || !offerToSave.isValid())
					return;
				
				bookOffers.replace(index, offerToSave);
				minecraft.setScreen(prevScreen);
			}).bounds(width / 2 - 102, height / 3 * 2, 100, 20).build());
		saveButton.active = false;
		
		addRenderableWidget(cancelButton = Button
			.builder(Component.literal("取消"), b -> minecraft.setScreen(prevScreen))
			.bounds(width / 2 + 2, height / 3 * 2, 100, 20).build());
		
		updateSelectedOffer(offerToSave);
	}
	
	private void updateLevel(int i, boolean offset)
	{
		if(offerToSave == null)
			return;
		
		String id = offerToSave.id();
		int level = offset ? offerToSave.level() + i : i;
		int price = offerToSave.price();
		
		Enchantment enchantment = offerToSave.getEnchantment();
		if(level < 1 || level > enchantment.getMaxLevel())
			return;
		
		updateSelectedOffer(new BookOffer(id, level, price));
	}
	
	private void updatePrice(int i, boolean offset)
	{
		if(offerToSave == null)
			return;
		
		String id = offerToSave.id();
		int level = offerToSave.level();
		int price = offset ? offerToSave.price() + i : i;
		
		if(price < 1 || price > 64)
			return;
		
		updateSelectedOffer(new BookOffer(id, level, price));
	}
	
	private void updateSelectedOffer(BookOffer offer)
	{
		offerToSave = offer;
		alreadyAdded =
			offer != null && !offer.equals(bookOffers.getOffers().get(index))
				&& bookOffers.contains(offer);
		saveButton.active = offer != null && !alreadyAdded;
		
		if(offer == null)
		{
			if(!levelField.getValue().isEmpty())
				levelField.setValue("");
			
			if(!priceField.getValue().isEmpty())
				priceField.setValue("");
			
		}else
		{
			String level = "" + offer.level();
			if(!levelField.getValue().equals(level))
				levelField.setValue(level);
			
			String price = "" + offer.price();
			if(!priceField.getValue().equals(price))
				priceField.setValue(price);
		}
	}
	
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int mouseButton)
	{
		boolean childClicked = super.mouseClicked(mouseX, mouseY, mouseButton);
		
		levelField.mouseClicked(mouseX, mouseY, mouseButton);
		priceField.mouseClicked(mouseX, mouseY, mouseButton);
		
		if(mouseButton == GLFW.GLFW_MOUSE_BUTTON_4)
			cancelButton.onPress();
		
		return childClicked;
	}
	
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int int_3)
	{
		switch(keyCode)
		{
			case GLFW.GLFW_KEY_ENTER:
			if(saveButton.active)
				saveButton.onPress();
			break;
			
			case GLFW.GLFW_KEY_ESCAPE:
			cancelButton.onPress();
			break;
			
			default:
			break;
		}
		
		return super.keyPressed(keyCode, scanCode, int_3);
	}
	
	@Override
	public void tick()
	{
		levelPlusButton.active = offerToSave != null
			&& offerToSave.level() < offerToSave.getEnchantment().getMaxLevel();
		levelMinusButton.active =
			offerToSave != null && offerToSave.level() > 1;
		
		pricePlusButton.active =
			offerToSave != null && offerToSave.price() < 64;
		priceMinusButton.active =
			offerToSave != null && offerToSave.price() > 1;
		
		levelField.tick();
		priceField.tick();
	}
	
	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY,
		float partialTicks)
	{
		renderDirtBackground(context);
		levelField.render(context, mouseX, mouseY, partialTicks);
		priceField.render(context, mouseX, mouseY, partialTicks);
		super.render(context, mouseX, mouseY, partialTicks);

		NanoVGUI.pass(context, () -> {
			NanoVGUI.centeredText("编辑附魔书报价", width / 2, 12, 0xFFFFFFFF,
				9F);

			int x = width / 2 - 100;
			int y = 64;

			Item item = BuiltInRegistries.ITEM.get(
				new ResourceLocation("enchanted_book"));
			ItemStack stack = new ItemStack(item);

			BookOffer bookOffer = offerToSave;
			String name = bookOffer.getEnchantmentNameWithLevel();

			Enchantment enchantment = bookOffer.getEnchantment();
			int nameColor = enchantment.isCurse() ? 0xff5555 : 0xffffff;
			String price;
			boolean emeraldAfterPrice = false;
			if(bookOffer.price() >= 64)
				price = "任意价格";
			else
			{
				price = "最高 " + bookOffer.price();
				emeraldAfterPrice = true;
			}
			boolean drawEmerald = emeraldAfterPrice;

			float priceWidth = NanoVGUI.width(price, 9F);
			NanoVGUI.item(context, stack, x + 1, y + 1, true);
			NanoVGUI.text(name, x + 28, y, nameColor, 9F);
			NanoVGUI.text(bookOffer.id(), x + 28, y + 9, 0xa0a0a0, 9F);
			NanoVGUI.text(price, x + 28, y + 18, 0xa0a0a0, 9F);
			if(drawEmerald)
				NanoVGUI.item(context, new ItemStack(Items.EMERALD),
					Math.round(x + 28 + priceWidth), y + 16, false);

			int baseX = width / 2 - 100;
			int baseY = 112;
			NanoVGUI.text("等级:", baseX, baseY, 0xf0f0f0, 9F);
			NanoVGUI.text("最高价格:", baseX, baseY + 16, 0xf0f0f0, 9F);

			if(alreadyAdded && offerToSave != null)
			{
				String errorText = offerToSave.getEnchantmentNameWithLevel()
					+ " 已在你的列表中！";
				NanoVGUI.text(errorText, baseX, baseY + 32, 0xff5555, 9F);
			}

			NanoVGUI.item(context, new ItemStack(Items.EMERALD),
				width / 2 - 16, 126, false);
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
