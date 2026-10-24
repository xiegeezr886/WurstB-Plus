/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from InvincibleMachineGun (GPL-3.0).
package net.wurstclient.hacks;

import net.minecraft.client.gui.screens.ChatScreen;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"chat hider", "hide chat", "chat opacity", "clean screen"})
public final class ChatHiderHack extends Hack implements UpdateListener
{
	private final SliderSetting openOpacity = new SliderSetting(
		"Open opacity",
		"How visible the chat is while the chat screen is open.", 1, 0.1, 1,
		0.05, ValueDisplay.DECIMAL);

	private double originalOpacity;

	public ChatHiderHack()
	{
		super("ChatHider");
		setCategory(Category.CHAT);
		addSetting(openOpacity);
	}

	@Override
	protected void onEnable()
	{
		originalOpacity = MC.options.chatOpacity().get();
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		MC.options.chatOpacity()
			.set(originalOpacity > 0 ? originalOpacity : openOpacity.getValue());
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null)
			return;

		if(MC.gui.screen() instanceof ChatScreen)
			MC.options.chatOpacity().set(openOpacity.getValue());
		else
			MC.options.chatOpacity().set(0.0);
	}
}
