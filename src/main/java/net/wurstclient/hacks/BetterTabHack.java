/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;

@SearchTags({"better tab", "player list", "tablist", "tab list"})
public final class BetterTabHack extends Hack
{
	private final CheckboxSetting showPing =
		new CheckboxSetting("Show ping", "Show each player's ping.", true);
	private final CheckboxSetting showGamemode =
		new CheckboxSetting("Show gamemode", "Show each player's gamemode.", false);
	private final CheckboxSetting highlightSelf = new CheckboxSetting(
		"Highlight self", "Highlight your own entry.", true);

	public BetterTabHack()
	{
		super("BetterTab");
		setCategory(Category.OTHER);
		addSetting(showPing);
		addSetting(showGamemode);
		addSetting(highlightSelf);
	}

	@Override
	protected void onEnable()
	{
		MinecraftForge.EVENT_BUS.register(this);
	}

	@Override
	protected void onDisable()
	{
		MinecraftForge.EVENT_BUS.unregister(this);
	}

	@SubscribeEvent
	public void onRenderOverlay(RenderGuiOverlayEvent.Pre event)
	{
		if(!event.getOverlay().id().equals(VanillaGuiOverlay.PLAYER_LIST.id()))
			return;
		event.setCanceled(true);
		render(event.getGuiGraphics());
	}

	private void render(GuiGraphics graphics)
	{
		if(MC.getConnection() == null || MC.player == null)
			return;

		List<PlayerInfo> players =
			new ArrayList<>(MC.getConnection().getOnlinePlayers());
		players.sort(Comparator.comparing(
			p -> p.getProfile().getName(), String.CASE_INSENSITIVE_ORDER));

		int width = MC.getWindow().getGuiScaledWidth();
		int height = MC.getWindow().getGuiScaledHeight();
		int rowHeight = 10;
		int rows = Math.min(players.size(), 20);
		int listWidth = 180;
		int listHeight = rows * rowHeight + 8;
		int x = width / 2 - listWidth / 2;
		int y = Math.max(4, height / 2 - listHeight / 2);

		graphics.fill(x, y, x + listWidth, y + listHeight, 0x90000000);
		int textY = y + 4;
		for(int i = 0; i < rows; i++)
		{
			PlayerInfo info = players.get(i);
			String name = info.getProfile().getName();
			int color = 0xFFFFFFFF;
			if(highlightSelf.isChecked()
				&& name.equalsIgnoreCase(MC.player.getGameProfile().getName()))
				color = 0xFF55FF55;

			StringBuilder line = new StringBuilder(name);
			if(showPing.isChecked())
				line.append("  ").append(info.getLatency()).append("ms");
			if(showGamemode.isChecked())
				line.append("  ").append(info.getGameMode().getName());

			graphics.drawString(MC.font, Component.literal(line.toString()),
				x + 4, textY, color, false);
			textY += rowHeight;
		}
	}
}
