/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * Ported from Meteor Client (GPL-3.0).
 */
package net.wurstclient.hacks;

import java.util.Comparator;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.event.EventManager;
import net.wurstclient.events.GUIRenderListener.GUIRenderEvent;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.render.GuiGraphicsExtractor;

@SearchTags({"better tab", "player list", "tablist", "ping", "tab health"})
public final class BetterTabHack extends Hack
{
	private final SliderSetting tabSize = new SliderSetting("Tab size",
		"Maximum number of players to display.", 100, 1, 1000, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting columnHeight = new SliderSetting(
		"Column height", "Maximum number of players per column.", 20, 1, 100,
		1, ValueDisplay.INTEGER);

	private final CheckboxSetting showHealth = new CheckboxSetting(
		"Show health", "Shows each player's health in the list.", true);

	private final CheckboxSetting showPing = new CheckboxSetting("Show ping",
		"Shows each player's ping in the list.", true);

	private final CheckboxSetting showGamemode = new CheckboxSetting(
		"Show gamemode", "Shows each player's game mode in the list.", false);

	public BetterTabHack()
	{
		super("BetterTab");
		setCategory(Category.RENDER);
		addSetting(tabSize);
		addSetting(columnHeight);
		addSetting(showHealth);
		addSetting(showPing);
		addSetting(showGamemode);
	}

	@Override
	protected void onEnable()
	{
		NeoForge.EVENT_BUS.register(this);
	}

	@Override
	protected void onDisable()
	{
		NeoForge.EVENT_BUS.unregister(this);
	}

	@SubscribeEvent
	public void onRenderGuiLayer(RenderGuiLayerEvent.Pre event)
	{
		if(!isEnabled() || MC.player == null || MC.level == null
			|| MC.getConnection() == null || MC.options.hideGui)
			return;

		if(!VanillaGuiLayers.TAB_LIST.equals(event.getName()))
			return;

		// The layer event is posted even while the list is hidden, so mirror
		// vanilla's visibility checks before replacing it.
		if(!MC.options.keyPlayerList.isDown())
			return;

		List<PlayerInfo> players = MC.getConnection()
			.getListedOnlinePlayers().stream()
			.sorted(Comparator.<PlayerInfo>comparingInt(
				PlayerInfo::getTabListOrder).reversed()
				.thenComparingInt(info ->
					info.getGameMode() == GameType.SPECTATOR ? 1 : 0)
				.thenComparing(BetterTabHack::getTeamName,
					String::compareToIgnoreCase)
				.thenComparing(info -> info.getProfile().name(),
					String::compareToIgnoreCase))
			.limit(tabSize.getValueI()).toList();

		if(players.isEmpty())
			return;

		if(MC.isLocalServer() && players.size() <= 1)
			return;

		// Cancelling the layer also skips GuiMixin's GUIRenderEvent at the
		// head of Gui.renderTabList(), which every Wurst HUD element uses.
		// Fire it here so the rest of the HUD keeps rendering.
		if(!MC.debugEntries.isOverlayVisible())
			EventManager.fire(new GUIRenderEvent(
				new GuiGraphicsExtractor(event.getGuiGraphics()),
				event.getPartialTick().getGameTimeDeltaPartialTick(true)));

		event.setCanceled(true);
		renderTabList(event.getGuiGraphics(), players);
	}

	private void renderTabList(GuiGraphics graphics, List<PlayerInfo> players)
	{
		int rows =
			Math.min(columnHeight.getValueI(), Math.max(1, players.size()));
		int columns = (players.size() + rows - 1) / rows;

		int columnWidth = 0;
		for(PlayerInfo info : players)
			columnWidth =
				Math.max(columnWidth, MC.font.width(buildLine(info)));

		int totalWidth = columns * columnWidth + (columns - 1) * 4;
		int startX = graphics.guiWidth() / 2 - totalWidth / 2;

		graphics.nextStratum();

		for(int i = 0; i < players.size(); i++)
		{
			PlayerInfo info = players.get(i);
			Component line = buildLine(info);
			int x = startX + i / rows * (columnWidth + 4);
			int y = 10 + i % rows * 9;

			int color;
			if(info.getGameMode() == GameType.SPECTATOR)
				color = 0xFFAAAAAA;
			else if(info.getProfile().id().equals(MC.player.getUUID()))
				color = 0xFFFFAA00;
			else
				color = 0xFFFFFFFF;

			graphics.fill(x - 1, y - 1, x + columnWidth + 1, y + 8,
				0x80000000);
			graphics.drawString(MC.font, line, x, y, color);
		}
	}

	private Component buildLine(PlayerInfo info)
	{
		MutableComponent line = Component.empty();

		Component displayName = info.getTabListDisplayName();
		if(displayName != null)
			line.append(displayName.copy());
		else
			line.append(PlayerTeam.formatNameForTeam(info.getTeam(),
				Component.literal(info.getProfile().name())));

		if(showHealth.isChecked())
		{
			Player player = MC.level.getPlayerByUUID(info.getProfile().id());
			if(player != null)
			{
				float health =
					player.getHealth() + player.getAbsorptionAmount();
				ChatFormatting color = health <= 5 ? ChatFormatting.DARK_RED
					: health <= 10 ? ChatFormatting.GOLD
						: health <= 15 ? ChatFormatting.YELLOW
							: ChatFormatting.GREEN;
				line.append(Component.literal(" " + Math.round(health))
					.withStyle(color));
			}
		}

		if(showPing.isChecked())
		{
			int latency = info.getLatency();
			ChatFormatting color = latency < 0 ? ChatFormatting.GRAY
				: latency < 100 ? ChatFormatting.GREEN
					: latency < 200 ? ChatFormatting.YELLOW
						: ChatFormatting.RED;
			line.append(Component.literal(" " + latency + "ms")
				.withStyle(color));
		}

		if(showGamemode.isChecked())
		{
			String gamemode = switch(info.getGameMode())
			{
				case SPECTATOR -> "Sp";
				case SURVIVAL -> "S";
				case CREATIVE -> "C";
				case ADVENTURE -> "A";
			};
			line.append(Component.literal(" [" + gamemode + "]")
				.withStyle(ChatFormatting.DARK_GRAY));
		}

		return line;
	}

	private static String getTeamName(PlayerInfo info)
	{
		PlayerTeam team = info.getTeam();
		return team == null ? "" : team.getName();
	}
}
