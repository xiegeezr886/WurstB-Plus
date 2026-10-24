/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
// Ported from Meteor Client (GPL-3.0).
package net.wurstclient.hacks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.ChatOutputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixinterface.IClientPlayerInteractionManager;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.TextFieldSetting;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.InventoryUtils;

@SearchTags({"book bot", "BookWriter", "book writer", "write book",
	"book and quill", "autobook", "auto book"})
public final class BookBotHack extends Hack
	implements UpdateListener, ChatOutputListener
{
	private static final int LINES_PER_PAGE = 14;
	private static final int PAGE_WIDTH = 114;
	private static final int MAX_PAGE_LENGTH = 1024;
	private static final int MAX_TITLE_LENGTH = 32;

	private final TextFieldSetting trigger = new TextFieldSetting("Trigger",
		"Chat message that triggers writing a book.\n"
			+ "Set to an empty string to disable the trigger.",
		"!bookbot");

	private final TextFieldSetting text = new TextFieldSetting("Text",
		"The text to write into the book. Use \\n for a line break.",
		"Hello from WurstB+ Plus!");

	private final SliderSetting pages = new SliderSetting("Pages",
		"The maximum number of pages to write.", 5, 1, 100, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting characters = new SliderSetting(
		"Characters per page",
		"How many characters to put on each page when word wrap is off.",
		256, 1, MAX_PAGE_LENGTH, 1, ValueDisplay.INTEGER);

	private final CheckboxSetting wordWrap = new CheckboxSetting("Word wrap",
		"Wraps the text to fit the book page instead of cutting it at a fixed"
			+ " number of characters.",
		true);

	private final CheckboxSetting overwrite = new CheckboxSetting(
		"Overwrite existing pages",
		"Also writes into books that already contain text.", false);

	private final CheckboxSetting autoWrite = new CheckboxSetting(
		"Write when book screen opens",
		"Writes a book as soon as a book and quill screen is opened.", true);

	private final CheckboxSetting sign = new CheckboxSetting("Sign",
		"Signs the book after writing it.", true);

	private final TextFieldSetting title = new TextFieldSetting("Title",
		"The title to use when signing.", "WurstB+ Plus");

	private int delayTimer;
	private boolean wasBookScreenOpen;
	private boolean pendingWrite;
	private boolean manualWrite;

	public BookBotHack()
	{
		super("BookBot");
		setCategory(Category.ITEMS);
		addSetting(trigger);
		addSetting(text);
		addSetting(pages);
		addSetting(characters);
		addSetting(wordWrap);
		addSetting(overwrite);
		addSetting(autoWrite);
		addSetting(sign);
		addSetting(title);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(ChatOutputListener.class, this);
		delayTimer = 0;
		wasBookScreenOpen = false;
		pendingWrite = false;
		manualWrite = false;
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(ChatOutputListener.class, this);
		pendingWrite = false;
		manualWrite = false;
	}

	@Override
	public void onSentMessage(ChatOutputEvent event)
	{
		String triggerText = trigger.getValue().trim();
		if(triggerText.isEmpty() || !event.getMessage().equals(triggerText))
			return;

		event.cancel();
		pendingWrite = true;
		manualWrite = true;
		delayTimer = 0;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		if(delayTimer > 0)
			delayTimer--;

		boolean bookScreenOpen = MC.screen instanceof BookEditScreen;
		if(autoWrite.isChecked() && bookScreenOpen && !wasBookScreenOpen)
		{
			pendingWrite = true;
			manualWrite = false;
		}
		wasBookScreenOpen = bookScreenOpen;

		if(!pendingWrite || delayTimer > 0)
			return;

		if(writeBook())
			pendingWrite = false;
	}

	private boolean writeBook()
	{
		boolean hasBook = isWritable(MC.player.getMainHandItem());
		int foundSlot = hasBook
			? MC.player.getInventory().selected
			: findWritableBookSlot();

		if(foundSlot < 0)
		{
			if(manualWrite)
				ChatUtils.warning(
					"No writable book found in your inventory.");
			manualWrite = false;
			delayTimer = 20;
			return true;
		}

		if(!hasBook)
		{
			if(foundSlot < 9)
			{
				MC.player.getInventory().selected = foundSlot;
				delayTimer = 2;
				return false;
			}

			if(MC.player.containerMenu != MC.player.inventoryMenu)
			{
				if(manualWrite)
					ChatUtils.warning(
						"Close the container before moving a book into your"
							+ " hand.");
				manualWrite = false;
				delayTimer = 20;
				return true;
			}

			IClientPlayerInteractionManager im =
				(IClientPlayerInteractionManager)IMC
					.getInteractionManager();
			im.windowClick_SWAP(InventoryUtils.toNetworkSlot(foundSlot),
				MC.player.getInventory().selected);
			delayTimer = 3;
			return false;
		}

		List<String> bookPages = buildPages();
		if(bookPages.isEmpty())
		{
			if(manualWrite)
				ChatUtils.warning("The BookBot text is empty.");
			manualWrite = false;
			delayTimer = 20;
			return true;
		}

		String bookTitle = title.getValue().trim();
		if(bookTitle.length() > MAX_TITLE_LENGTH)
			bookTitle = bookTitle.substring(0, MAX_TITLE_LENGTH);
		if(bookTitle.isEmpty())
			bookTitle = "WurstB+ Plus";

		boolean signBook = sign.isChecked();
		int slot = MC.player.getInventory().selected;
		MC.player.connection
			.send(new ServerboundEditBookPacket(slot, bookPages,
				signBook ? Optional.of(bookTitle) : Optional.empty()));

		updateClientBook(bookPages, bookTitle, signBook);

		if(manualWrite)
			ChatUtils.message("Wrote " + bookPages.size() + " page"
				+ (bookPages.size() == 1 ? "" : "s") + " into the book.");

		manualWrite = false;
		delayTimer = 10;
		return true;
	}

	private boolean isWritable(ItemStack stack)
	{
		if(stack.isEmpty() || !stack.is(Items.WRITABLE_BOOK))
			return false;

		if(overwrite.isChecked())
			return true;

		WritableBookContent content =
			stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
		return content == null || content.pages().isEmpty();
	}

	private int findWritableBookSlot()
	{
		Inventory inventory = MC.player.getInventory();
		for(int i = 0; i < 36; i++)
			if(isWritable(inventory.getItem(i)))
				return i;

		return -1;
	}

	private List<String> buildPages()
	{
		String raw = text.getValue().replace("\\n", "\n");
		if(raw.isEmpty())
			return List.of();

		int maxPages = pages.getValueI();
		List<String> result = new ArrayList<>();

		if(wordWrap.isChecked())
		{
			List<FormattedText> lines = MC.font.getSplitter()
				.splitLines(raw, PAGE_WIDTH, Style.EMPTY);
			List<String> currentPage = new ArrayList<>();

			for(FormattedText line : lines)
			{
				currentPage.add(line.getString());
				if(currentPage.size() < LINES_PER_PAGE)
					continue;

				result.add(String.join("\n", currentPage));
				currentPage.clear();

				if(result.size() >= maxPages)
					break;
			}

			if(!currentPage.isEmpty() && result.size() < maxPages)
				result.add(String.join("\n", currentPage));

		}else
		{
			int pageLength = characters.getValueI();
			for(int start = 0; start < raw.length()
				&& result.size() < maxPages; start += pageLength)
				result.add(raw.substring(start,
					Math.min(raw.length(), start + pageLength)));
		}

		return result;
	}

	private void updateClientBook(List<String> bookPages, String bookTitle,
		boolean signBook)
	{
		ItemStack stack = MC.player.getMainHandItem();
		if(stack.isEmpty())
			return;

		if(signBook)
		{
			List<Filterable<Component>> filteredPages =
				new ArrayList<>();
			for(String page : bookPages)
				filteredPages
					.add(Filterable.passThrough(Component.literal(page)));

			stack.set(DataComponents.WRITTEN_BOOK_CONTENT,
				new WrittenBookContent(Filterable.passThrough(bookTitle),
					MC.player.getName().getString(), 0, filteredPages,
					true));

		}else
		{
			List<Filterable<String>> filteredPages = new ArrayList<>();
			for(String page : bookPages)
				filteredPages.add(Filterable.passThrough(page));

			stack.set(DataComponents.WRITABLE_BOOK_CONTENT,
				new WritableBookContent(filteredPages));
		}
	}
}
