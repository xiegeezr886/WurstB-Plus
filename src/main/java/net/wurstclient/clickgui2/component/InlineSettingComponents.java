package net.wurstclient.clickgui2.component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.clickgui2.GuiIcon;
import net.wurstclient.clickgui2.supersoft.SuperSoftTheme;
import net.wurstclient.clickgui2.supersoft.UiTween;
import net.wurstclient.hacks.autolibrarian.BookOffer;
import net.wurstclient.settings.BlockListSetting;
import net.wurstclient.settings.BlockSetting;
import net.wurstclient.settings.BookOffersSetting;
import net.wurstclient.settings.FileSetting;
import net.wurstclient.settings.ItemListSetting;
import net.wurstclient.util.BlockUtils;

final class FileInlineSettingComponent extends ValueRowComponent
{
	private static final int HEADER_HEIGHT = 25;
	private static final int ROW_HEIGHT = 16;
	private final FileSetting fileSetting;
	private final UiTween contentMotion = new UiTween(0, 200);
	private final UiTween arrowMotion = new UiTween(0, 200);
	private final List<UiTween> rowMotions = new ArrayList<>();
	private List<Path> files = List.of();
	private boolean expanded;

	FileInlineSettingComponent(FileSetting setting)
	{
		super(setting);
		fileSetting = setting;
	}

	@Override
	protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		drawLabel(graphics, mouseX, mouseY);
		drawValue(graphics, fileSetting.getSelectedFileName());
		InlineSettingRender.chevron(graphics, x, y, getWidth(),
			arrowMotion.update(expanded ? 90 : 0));
		int animatedHeight = Math.round(contentMotion.update(
			expanded ? fullContentHeight() : 0));
		if(animatedHeight <= 0)
			return;
		int rowY = (int)y + HEADER_HEIGHT;
		graphics.enableScissor((int)x, rowY, (int)(x + getWidth()),
			rowY + animatedHeight);
		if(files.isEmpty())
			InlineSettingRender.message(graphics, x, rowY, getWidth(), "No files",
				SuperSoftTheme.TEXT_SECONDARY);
		InlineSettingRender.resizeMotions(rowMotions, files.size());
		for(int index = 0; index < files.size(); index++)
		{
			Path path = files.get(index);
			String name = path.getFileName().toString();
			boolean hovered = InlineSettingRender.isHovered(x, rowY,
				getWidth(), ROW_HEIGHT, mouseX, mouseY);
			InlineSettingRender.row(graphics, x, rowY, getWidth(), name,
				name.equals(fileSetting.getSelectedFileName()),
				rowMotions.get(index).update(hovered ? 1 : 0), 0);
			rowY += ROW_HEIGHT;
		}
		graphics.disableScissor();
	}

	@Override
	protected boolean onClick(double mouseX, double mouseY, int button)
	{
		if(button != 0)
			return false;
		if(mouseY < y + HEADER_HEIGHT)
		{
			expanded = !expanded;
			if(expanded)
				files = List.copyOf(fileSetting.listFiles());
			return true;
		}
		if(!expanded)
			return false;
		int index = (int)((mouseY - y - HEADER_HEIGHT) / ROW_HEIGHT);
		if(index >= 0 && index < files.size())
		{
			fileSetting.setSelectedFile(files.get(index).getFileName().toString());
			expanded = false;
		}
		return true;
	}

	@Override
	public double getHeight()
	{
		return HEADER_HEIGHT + contentMotion.get();
	}

	private int fullContentHeight()
	{
		return Math.max(1, files.size()) * ROW_HEIGHT;
	}
}

final class BlockInlineSettingComponent extends ValueRowComponent
	implements GuiTextInput
{
	private static final int HEADER_HEIGHT = 25;
	private final BlockSetting blockSetting;
	private final UiTween contentMotion = new UiTween(0, 200);
	private final UiTween arrowMotion = new UiTween(0, 200);
	private final UiTween inputMotion = new UiTween(0, 150);
	private final UiTween actionMotion = new UiTween(0, 150);
	private boolean expanded;
	private boolean focused;
	private String draft;
	private String error = "";

	BlockInlineSettingComponent(BlockSetting setting)
	{
		super(setting);
		blockSetting = setting;
		draft = setting.getBlockName();
	}

	@Override
	protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		drawLabel(graphics, mouseX, mouseY);
		drawValue(graphics, blockSetting.getShortBlockName());
		InlineSettingRender.chevron(graphics, x, y, getWidth(),
			arrowMotion.update(expanded ? 90 : 0));
		int animatedHeight = Math.round(contentMotion.update(
			expanded ? fullContentHeight() : 0));
		if(animatedHeight <= 0)
			return;
		int top = (int)y + HEADER_HEIGHT + 2;
		graphics.enableScissor((int)x, (int)y + HEADER_HEIGHT,
			(int)(x + getWidth()), (int)y + HEADER_HEIGHT + animatedHeight);
		boolean inputHovered = mouseX >= x + 4
			&& mouseX < x + getWidth() - 31 && mouseY >= top
			&& mouseY < top + 15;
		boolean actionHovered = mouseX >= x + getWidth() - 27
			&& mouseX < x + getWidth() - 4 && mouseY >= top
			&& mouseY < top + 15;
		InlineSettingRender.input(graphics, (int)x + 4, top,
			(int)(x + getWidth()) - 31, draft, focused,
			inputMotion.update(focused || inputHovered ? 1 : 0));
		InlineSettingRender.button(graphics, (int)(x + getWidth()) - 27, top,
			23, "Set", actionMotion.update(actionHovered ? 1 : 0));
		if(!error.isEmpty())
			InlineSettingRender.message(graphics, x, top + 17, getWidth(), error,
				0xFFFF6B6B);
		graphics.disableScissor();
	}

	@Override
	protected boolean onClick(double mouseX, double mouseY, int button)
	{
		if(button != 0)
			return false;
		if(mouseY < y + HEADER_HEIGHT)
		{
			expanded = !expanded;
			if(expanded)
				draft = blockSetting.getBlockName();
			else
				loseFocus();
			return true;
		}
		if(!expanded)
			return false;
		if(mouseX >= x + getWidth() - 31)
		{
			applyDraft();
			return true;
		}
		requestFocus();
		return true;
	}

	private void applyDraft()
	{
		Block block = BlockUtils.getBlockFromNameOrID(draft.trim());
		if(block == null)
		{
			error = "Unknown block";
			return;
		}
		blockSetting.setBlock(block);
		if(blockSetting.getBlock() != block)
		{
			error = "Block is not allowed";
			return;
		}
		draft = blockSetting.getBlockName();
		error = "";
	}

	private void requestFocus()
	{
		focused = true;
		VapeGuiContext context = VapeTextInputComponent.currentContext();
		if(context != null)
			context.beginTextInput(this);
	}

	@Override
	public boolean acceptKey(int keyCode)
	{
		if(!focused)
			return false;
		if(keyCode == GLFW.GLFW_KEY_ESCAPE)
		{
			loseFocus();
			return true;
		}
		if(keyCode == GLFW.GLFW_KEY_ENTER)
		{
			applyDraft();
			loseFocus();
			return true;
		}
		if(keyCode == GLFW.GLFW_KEY_BACKSPACE && !draft.isEmpty())
			draft = draft.substring(0, draft.length() - 1);
		return keyCode == GLFW.GLFW_KEY_BACKSPACE;
	}

	@Override
	public void acceptChar(char codePoint)
	{
		if(focused && !Character.isISOControl(codePoint) && draft.length() < 128)
		{
			draft += codePoint;
			error = "";
		}
	}

	@Override
	public void loseFocus()
	{
		focused = false;
		VapeGuiContext context = VapeTextInputComponent.currentContext();
		if(context != null)
			context.endTextInput(this);
	}

	@Override
	public double getHeight()
	{
		return HEADER_HEIGHT + contentMotion.get();
	}

	private int fullContentHeight()
	{
		return 21 + (error.isEmpty() ? 0 : 14);
	}
}

final class RegistryListInlineSettingComponent extends ValueRowComponent
	implements GuiTextInput
{
	private static final int HEADER_HEIGHT = 25;
	private static final int ROW_HEIGHT = 16;
	private final BlockListSetting blockSetting;
	private final ItemListSetting itemSetting;
	private final UiTween contentMotion = new UiTween(0, 200);
	private final UiTween arrowMotion = new UiTween(0, 200);
	private final UiTween inputMotion = new UiTween(0, 150);
	private final UiTween actionMotion = new UiTween(0, 150);
	private final List<UiTween> rowMotions = new ArrayList<>();
	private final List<UiTween> removeMotions = new ArrayList<>();
	private boolean expanded;
	private boolean focused;
	private String draft = "";
	private String error = "";

	RegistryListInlineSettingComponent(BlockListSetting setting)
	{
		super(setting);
		blockSetting = setting;
		itemSetting = null;
	}

	RegistryListInlineSettingComponent(ItemListSetting setting)
	{
		super(setting);
		blockSetting = null;
		itemSetting = setting;
	}

	private List<String> entries()
	{
		return blockSetting != null ? blockSetting.getBlockNames()
			: itemSetting.getItemNames();
	}

	@Override
	protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		drawLabel(graphics, mouseX, mouseY);
		drawValue(graphics, entries().size() + (blockSetting != null
			? " blocks" : " items"));
		InlineSettingRender.chevron(graphics, x, y, getWidth(),
			arrowMotion.update(expanded ? 90 : 0));
		int animatedHeight = Math.round(contentMotion.update(
			expanded ? fullContentHeight() : 0));
		if(animatedHeight <= 0)
			return;
		int rowY = (int)y + HEADER_HEIGHT;
		graphics.enableScissor((int)x, rowY, (int)(x + getWidth()),
			rowY + animatedHeight);
		List<String> entries = entries();
		InlineSettingRender.resizeMotions(rowMotions, entries.size());
		InlineSettingRender.resizeMotions(removeMotions, entries.size());
		for(int index = 0; index < entries.size(); index++)
		{
			String name = entries.get(index);
			boolean hovered = InlineSettingRender.isHovered(x, rowY,
				getWidth(), ROW_HEIGHT, mouseX, mouseY);
			boolean removeHovered = InlineSettingRender.isHovered(
				x + getWidth() - 18, rowY + 1, 14, 15, mouseX, mouseY);
			InlineSettingRender.row(graphics, x, rowY, getWidth(),
				name.replace("minecraft:", ""), false,
				rowMotions.get(index).update(hovered ? 1 : 0), 20);
			InlineSettingRender.button(graphics, (int)(x + getWidth()) - 18,
				rowY + 1, 14, "x",
				removeMotions.get(index).update(removeHovered ? 1 : 0));
			rowY += ROW_HEIGHT;
		}
		boolean inputHovered = mouseX >= x + 4
			&& mouseX < x + getWidth() - 23 && mouseY >= rowY + 1
			&& mouseY < rowY + 16;
		boolean actionHovered = mouseX >= x + getWidth() - 19
			&& mouseX < x + getWidth() - 4 && mouseY >= rowY + 1
			&& mouseY < rowY + 16;
		InlineSettingRender.input(graphics, (int)x + 4, rowY + 1,
			(int)(x + getWidth()) - 23, draft, focused,
			inputMotion.update(focused || inputHovered ? 1 : 0));
		InlineSettingRender.button(graphics, (int)(x + getWidth()) - 19,
			rowY + 1, 15, "+", actionMotion.update(actionHovered ? 1 : 0));
		if(!error.isEmpty())
			InlineSettingRender.message(graphics, x, rowY + 18, getWidth(), error,
				0xFFFF6B6B);
		graphics.disableScissor();
	}

	@Override
	protected boolean onClick(double mouseX, double mouseY, int button)
	{
		if(button != 0)
			return false;
		if(mouseY < y + HEADER_HEIGHT)
		{
			expanded = !expanded;
			if(!expanded)
				loseFocus();
			return true;
		}
		if(!expanded)
			return false;
		int row = (int)((mouseY - y - HEADER_HEIGHT) / ROW_HEIGHT);
		if(row >= 0 && row < entries().size())
		{
			if(mouseX >= x + getWidth() - 22)
				remove(row);
			return true;
		}
		if(mouseX >= x + getWidth() - 23)
			applyDraft();
		else
			requestFocus();
		return true;
	}

	private void remove(int index)
	{
		if(blockSetting != null)
			blockSetting.remove(index);
		else
			itemSetting.remove(index);
		if(index < rowMotions.size())
			rowMotions.remove(index);
		if(index < removeMotions.size())
			removeMotions.remove(index);
	}

	private void applyDraft()
	{
		String value = draft.trim();
		if(blockSetting != null)
		{
			Block block = BlockUtils.getBlockFromNameOrID(value);
			if(block == null)
			{
				error = "Unknown block";
				return;
			}
			blockSetting.add(block);
		}else
		{
			ResourceLocation id = ResourceLocation.tryParse(value);
			if(id == null || !BuiltInRegistries.ITEM.containsKey(id))
			{
				error = "Unknown item";
				return;
			}
			Item item = BuiltInRegistries.ITEM.get(id);
			itemSetting.add(item);
		}
		draft = "";
		error = "";
	}

	private void requestFocus()
	{
		focused = true;
		VapeGuiContext context = VapeTextInputComponent.currentContext();
		if(context != null)
			context.beginTextInput(this);
	}

	@Override
	public boolean acceptKey(int keyCode)
	{
		if(!focused)
			return false;
		if(keyCode == GLFW.GLFW_KEY_ESCAPE)
		{
			loseFocus();
			return true;
		}
		if(keyCode == GLFW.GLFW_KEY_ENTER)
		{
			applyDraft();
			return true;
		}
		if(keyCode == GLFW.GLFW_KEY_BACKSPACE && !draft.isEmpty())
			draft = draft.substring(0, draft.length() - 1);
		return keyCode == GLFW.GLFW_KEY_BACKSPACE;
	}

	@Override
	public void acceptChar(char codePoint)
	{
		if(focused && !Character.isISOControl(codePoint) && draft.length() < 128)
		{
			draft += codePoint;
			error = "";
		}
	}

	@Override
	public void loseFocus()
	{
		focused = false;
		VapeGuiContext context = VapeTextInputComponent.currentContext();
		if(context != null)
			context.endTextInput(this);
	}

	@Override
	public double getHeight()
	{
		return HEADER_HEIGHT + contentMotion.get();
	}

	private int fullContentHeight()
	{
		return entries().size() * ROW_HEIGHT + 19
			+ (error.isEmpty() ? 0 : 14);
	}
}

final class BookOffersInlineSettingComponent extends ValueRowComponent
	implements GuiTextInput
{
	private static final int HEADER_HEIGHT = 25;
	private static final int ROW_HEIGHT = 16;
	private final BookOffersSetting offersSetting;
	private final UiTween contentMotion = new UiTween(0, 200);
	private final UiTween arrowMotion = new UiTween(0, 200);
	private final UiTween[] inputMotions = {new UiTween(0, 150),
		new UiTween(0, 150), new UiTween(0, 150)};
	private final UiTween actionMotion = new UiTween(0, 150);
	private final List<UiTween> rowMotions = new ArrayList<>();
	private final List<UiTween> removeMotions = new ArrayList<>();
	private boolean expanded;
	private int selectedIndex = -1;
	private int activeField = -1;
	private String enchantment = "";
	private String level = "1";
	private String price = "64";
	private String error = "";

	BookOffersInlineSettingComponent(BookOffersSetting setting)
	{
		super(setting);
		offersSetting = setting;
	}

	@Override
	protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		drawLabel(graphics, mouseX, mouseY);
		drawValue(graphics, offersSetting.getOffers().size() + " offers");
		InlineSettingRender.chevron(graphics, x, y, getWidth(),
			arrowMotion.update(expanded ? 90 : 0));
		int animatedHeight = Math.round(contentMotion.update(
			expanded ? fullContentHeight() : 0));
		if(animatedHeight <= 0)
			return;
		int rowY = (int)y + HEADER_HEIGHT;
		graphics.enableScissor((int)x, rowY, (int)(x + getWidth()),
			rowY + animatedHeight);
		List<BookOffer> offers = offersSetting.getOffers();
		InlineSettingRender.resizeMotions(rowMotions, offers.size());
		InlineSettingRender.resizeMotions(removeMotions, offers.size());
		for(int index = 0; index < offers.size(); index++)
		{
			BookOffer offer = offers.get(index);
			String label = offer.id().replace("minecraft:", "") + " "
				+ offer.level() + " / " + offer.price();
			boolean hovered = InlineSettingRender.isHovered(x, rowY,
				getWidth(), ROW_HEIGHT, mouseX, mouseY);
			boolean removeHovered = InlineSettingRender.isHovered(
				x + getWidth() - 18, rowY + 1, 14, 15, mouseX, mouseY);
			InlineSettingRender.row(graphics, x, rowY, getWidth(), label,
				selectedIndex == index,
				rowMotions.get(index).update(hovered ? 1 : 0), 20);
			InlineSettingRender.button(graphics, (int)(x + getWidth()) - 18,
				rowY + 1, 14, "x",
				removeMotions.get(index).update(removeHovered ? 1 : 0));
			rowY += ROW_HEIGHT;
		}
		boolean enchantmentHovered = mouseX >= x + 4
			&& mouseX < x + getWidth() - 4 && mouseY >= rowY + 1
			&& mouseY < rowY + 16;
		InlineSettingRender.input(graphics, (int)x + 4, rowY + 1,
			(int)(x + getWidth()) - 4, enchantment, activeField == 0,
			inputMotions[0].update(activeField == 0 || enchantmentHovered
				? 1 : 0));
		rowY += 18;
		int left = (int)x + 4;
		boolean levelHovered = mouseX >= left && mouseX < left + 34
			&& mouseY >= rowY && mouseY < rowY + 15;
		boolean priceHovered = mouseX >= left + 38 && mouseX < left + 72
			&& mouseY >= rowY && mouseY < rowY + 15;
		boolean actionHovered = mouseX >= x + getWidth() - 34
			&& mouseX < x + getWidth() - 4 && mouseY >= rowY
			&& mouseY < rowY + 15;
		InlineSettingRender.input(graphics, left, rowY, left + 34, level,
			activeField == 1,
			inputMotions[1].update(activeField == 1 || levelHovered ? 1 : 0));
		InlineSettingRender.input(graphics, left + 38, rowY, left + 72, price,
			activeField == 2,
			inputMotions[2].update(activeField == 2 || priceHovered ? 1 : 0));
		InlineSettingRender.button(graphics, (int)(x + getWidth()) - 34, rowY,
			30, selectedIndex >= 0 ? "Save" : "+",
			actionMotion.update(actionHovered ? 1 : 0));
		if(!error.isEmpty())
			InlineSettingRender.message(graphics, x, rowY + 17, getWidth(), error,
				0xFFFF6B6B);
		graphics.disableScissor();
	}

	@Override
	protected boolean onClick(double mouseX, double mouseY, int button)
	{
		if(button != 0)
			return false;
		if(mouseY < y + HEADER_HEIGHT)
		{
			expanded = !expanded;
			if(!expanded)
				loseFocus();
			return true;
		}
		if(!expanded)
			return false;
		List<BookOffer> offers = offersSetting.getOffers();
		int offset = (int)(mouseY - y - HEADER_HEIGHT);
		int offerArea = offers.size() * ROW_HEIGHT;
		if(offset < offerArea)
		{
			int index = offset / ROW_HEIGHT;
			if(mouseX >= x + getWidth() - 22)
			{
				offersSetting.remove(index);
				if(index < rowMotions.size())
					rowMotions.remove(index);
				if(index < removeMotions.size())
					removeMotions.remove(index);
				clearEditor();
			}else
				select(index, offers.get(index));
			return true;
		}
		int editorOffset = offset - offerArea;
		if(editorOffset < 18)
			requestFocus(0);
		else if(mouseX >= x + getWidth() - 38)
			applyEditor();
		else if(mouseX < x + 40)
			requestFocus(1);
		else if(mouseX < x + 78)
			requestFocus(2);
		return true;
	}

	private void select(int index, BookOffer offer)
	{
		selectedIndex = index;
		enchantment = offer.id();
		level = Integer.toString(offer.level());
		price = Integer.toString(offer.price());
		error = "";
	}

	private void applyEditor()
	{
		ResourceLocation id = ResourceLocation.tryParse(enchantment.trim());
		int parsedLevel = parseInt(level);
		int parsedPrice = parseInt(price);
		BookOffer offer = id != null
			&& BuiltInRegistries.ENCHANTMENT.containsKey(id)
			? new BookOffer(id.toString(), parsedLevel, parsedPrice) : null;
		if(offer == null || !offer.isValid())
		{
			error = "Invalid offer";
			return;
		}
		if(selectedIndex >= 0)
			offersSetting.replace(selectedIndex, offer);
		else
			offersSetting.add(offer);
		clearEditor();
	}

	private static int parseInt(String value)
	{
		try
		{
			return Integer.parseInt(value);
		}catch(NumberFormatException exception)
		{
			return 0;
		}
	}

	private void clearEditor()
	{
		selectedIndex = -1;
		enchantment = "";
		level = "1";
		price = "64";
		error = "";
		loseFocus();
	}

	private void requestFocus(int field)
	{
		activeField = field;
		VapeGuiContext context = VapeTextInputComponent.currentContext();
		if(context != null)
			context.beginTextInput(this);
	}

	@Override
	public boolean acceptKey(int keyCode)
	{
		if(activeField < 0)
			return false;
		if(keyCode == GLFW.GLFW_KEY_ESCAPE)
		{
			loseFocus();
			return true;
		}
		if(keyCode == GLFW.GLFW_KEY_ENTER)
		{
			applyEditor();
			return true;
		}
		if(keyCode == GLFW.GLFW_KEY_BACKSPACE)
		{
			String value = activeValue();
			if(!value.isEmpty())
				setActiveValue(value.substring(0, value.length() - 1));
			return true;
		}
		return false;
	}

	@Override
	public void acceptChar(char codePoint)
	{
		if(activeField < 0 || Character.isISOControl(codePoint))
			return;
		if(activeField > 0 && !Character.isDigit(codePoint))
			return;
		String value = activeValue();
		if(value.length() < 128)
			setActiveValue(value + codePoint);
		error = "";
	}

	private String activeValue()
	{
		return switch(activeField)
		{
			case 1 -> level;
			case 2 -> price;
			default -> enchantment;
		};
	}

	private void setActiveValue(String value)
	{
		switch(activeField)
		{
			case 1 -> level = value;
			case 2 -> price = value;
			default -> enchantment = value;
		}
	}

	@Override
	public void loseFocus()
	{
		activeField = -1;
		VapeGuiContext context = VapeTextInputComponent.currentContext();
		if(context != null)
			context.endTextInput(this);
	}

	@Override
	public double getHeight()
	{
		return HEADER_HEIGHT + contentMotion.get();
	}

	private int fullContentHeight()
	{
		return offersSetting.getOffers().size() * ROW_HEIGHT + 36
			+ (error.isEmpty() ? 0 : 14);
	}
}

final class InlineSettingRender
{
	private InlineSettingRender()
	{}

	static void chevron(GuiGraphics graphics, double x, double y, double width,
		boolean expanded)
	{
		chevron(graphics, x, y, width, expanded ? 90 : 0);
	}

	static void chevron(GuiGraphics graphics, double x, double y, double width,
		float rotation)
	{
		GuiIcon.CHEVRON.drawRotated(graphics, (int)(x + width) - 11,
			(int)y + 15, 6, SuperSoftTheme.TEXT_SECONDARY,
			rotation);
	}

	static void row(GuiGraphics graphics, double x, int y, double width,
		String text, boolean selected, int mouseX, int mouseY, int rightPadding)
	{
		boolean hovered = isHovered(x, y, width, 16, mouseX, mouseY);
		row(graphics, x, y, width, text, selected, hovered ? 1 : 0,
			rightPadding);
	}

	static void row(GuiGraphics graphics, double x, int y, double width,
		String text, boolean selected, float hoverProgress, int rightPadding)
	{
		float highlight = selected ? 1 : hoverProgress;
		if(highlight > 0.001F)
			graphics.fill((int)x + 2, y, (int)(x + width) - 2, y + 16,
				SuperSoftTheme.mix(0x003C3C3C,
					selected ? 0x99000000
						| SuperSoftTheme.ACCENT & 0xFFFFFF
						: SuperSoftTheme.SETTING_HOVER,
					highlight));
		Font font = Minecraft.getInstance().font;
		String visible = font.plainSubstrByWidth(text,
			Math.max(8, (int)width - 12 - rightPadding));
		graphics.drawString(font, visible, (int)x + 5, y + 4,
			SuperSoftTheme.TEXT, false);
	}

	static boolean isHovered(double x, double y, double width, double height,
		int mouseX, int mouseY)
	{
		return mouseX >= x && mouseX < x + width && mouseY >= y
			&& mouseY < y + height;
	}

	static void resizeMotions(List<UiTween> motions, int size)
	{
		while(motions.size() < size)
			motions.add(new UiTween(0, 150));
		while(motions.size() > size)
			motions.remove(motions.size() - 1);
	}

	static void input(GuiGraphics graphics, int left, int top, int right,
		String value, boolean focused)
	{
		input(graphics, left, top, right, value, focused,
			focused ? 1 : 0);
	}

	static void input(GuiGraphics graphics, int left, int top, int right,
		String value, boolean focused, float borderProgress)
	{
		FlatRenderer.fillRoundedRect(graphics, left, top, right, top + 15, 3,
			SuperSoftTheme.SETTING);
		FlatRenderer.drawRoundedOutline(graphics, left, top, right, top + 15, 3,
			SuperSoftTheme.mix(0x66FFFFFF, SuperSoftTheme.TEXT,
				borderProgress));
		Font font = Minecraft.getInstance().font;
		String visible = value.isEmpty() ? "value"
			: font.plainSubstrByWidth(value, Math.max(4, right - left - 8));
		graphics.drawString(font, visible, left + 4, top + 3,
			value.isEmpty() ? 0x66FFFFFF : SuperSoftTheme.TEXT, false);
		if(focused && System.currentTimeMillis() / 500 % 2 == 0)
		{
			int cursor = Math.min(right - 3, left + 4 + font.width(visible));
			graphics.fill(cursor, top + 2, cursor + 1, top + 13,
				SuperSoftTheme.TEXT);
		}
	}

	static void button(GuiGraphics graphics, int left, int top, int width,
		String label, int mouseX, int mouseY)
	{
		boolean hovered = mouseX >= left && mouseX < left + width
			&& mouseY >= top && mouseY < top + 15;
		button(graphics, left, top, width, label, hovered ? 1 : 0);
	}

	static void button(GuiGraphics graphics, int left, int top, int width,
		String label, float hoverProgress)
	{
		FlatRenderer.fillRoundedRect(graphics, left, top, left + width, top + 15,
			2, SuperSoftTheme.mix(SuperSoftTheme.ACCENT,
				SuperSoftTheme.MODULE_HOVER, hoverProgress));
		Font font = Minecraft.getInstance().font;
		graphics.drawString(font, label,
			left + Math.max(2, (width - font.width(label)) / 2), top + 3,
			SuperSoftTheme.TEXT, false);
	}

	static void message(GuiGraphics graphics, double x, int y, double width,
		String message, int color)
	{
		Font font = Minecraft.getInstance().font;
		graphics.drawString(font,
			font.plainSubstrByWidth(message, Math.max(8, (int)width - 8)),
			(int)x + 4, y + 3, color, false);
	}
}
