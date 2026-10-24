/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from InvincibleMachineGun (GPL-3.0).
package net.wurstclient.hacks;

import java.util.Locale;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.ChatInputListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.TextFieldSetting;

@SearchTags({"feedback blocker", "chat blocker", "spam filter", "block chat"})
public final class FeedbackBlockerHack extends Hack implements ChatInputListener
{
	private final CheckboxSetting ignoreCase = new CheckboxSetting(
		"Ignore case", "Matches keywords regardless of capitalization.", true);

	private final TextFieldSetting keywords = new TextFieldSetting("Keywords",
		"Chat messages containing any of these keywords are blocked.\n"
			+ "Separate keywords with |.",
		"Placed|Undo|Affected|Paste|Schematic");

	public FeedbackBlockerHack()
	{
		super("FeedbackBlocker");
		setCategory(Category.CHAT);
		addSetting(ignoreCase);
		addSetting(keywords);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(ChatInputListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(ChatInputListener.class, this);
	}

	@Override
	public void onReceivedMessage(ChatInputEvent event)
	{
		String content = event.getComponent().getString();
		String contentToCheck = ignoreCase.isChecked()
			? content.toLowerCase(Locale.ROOT) : content;

		for(String part : keywords.getValue().split("\\|"))
		{
			String keyword = part.trim();
			if(keyword.isEmpty())
				continue;

			String keywordToCheck = ignoreCase.isChecked()
				? keyword.toLowerCase(Locale.ROOT) : keyword;

			if(contentToCheck.contains(keywordToCheck))
			{
				event.cancel();
				return;
			}
		}
	}
}
