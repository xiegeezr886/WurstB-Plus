/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.Player;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.RenderUtils;

/**
 * Extra tab list overlay with name, ping and health columns. It appears while
 * the player list key is held.
 *
 * <p>Simplification: Soar removed the vanilla background and heads with
 * mixins into {@code GuiPlayerTabOverlay}. Mixins are out of scope here, so
 * this port draws a separate overlay panel and leaves the vanilla tab list
 * untouched. Player heads are skipped for the same reason.
 */
@SearchTags({"tab editor", "tab list", "player list", "ping", "tab"})
public final class TabEditorHack extends Hack implements GUIRenderListener
{
	private static final int PADDING = 4;
	private static final int ROW_HEIGHT = 11;
	private static final int HEADER_HEIGHT = 13;
	private static final int COLUMN_GAP = 8;

	private final SliderSetting scale = new SliderSetting("Scale",
		"Scale of the tab list overlay.", 1, 0.5, 3, 0.05,
		ValueDisplay.DECIMAL);

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the tab list overlay.", 4, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the tab list overlay.", 4, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting maxPlayers = new SliderSetting("Max players",
		"Maximum number of players shown in the overlay.", 80, 1, 200, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting showPing =
		new CheckboxSetting("Show ping", "Adds a latency column.", true);

	private final CheckboxSetting showHealth = new CheckboxSetting(
		"Show health", "Adds a health column for players in render distance.",
		true);

	private final CheckboxSetting background = new CheckboxSetting(
		"Background", "Draws a background behind the overlay.", true);

	private final ColorSetting bgColor = new ColorSetting("Background color",
		"Color of the overlay background.", new Color(0, 0, 0, 150));

	private final ColorSetting textColor = new ColorSetting("Text color",
		"Color of the player names.", Color.WHITE);

	public TabEditorHack()
	{
		super("TabEditor");
		setCategory(Category.RENDER);
		addSetting(scale);
		addSetting(posX);
		addSetting(posY);
		addSetting(maxPlayers);
		addSetting(showPing);
		addSetting(showHealth);
		addSetting(background);
		addSetting(bgColor);
		addSetting(textColor);
		bgColor.visibleWhen(background::isChecked);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(GUIRenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(GUIRenderListener.class, this);
	}

	@Override
	public void onRenderGUI(GuiGraphics context, float partialTicks)
	{
		if(MC.player == null || MC.level == null || MC.getConnection() == null
			|| MC.options.hideGui || !MC.options.keyPlayerList.isDown())
			return;

		ArrayList<PlayerInfo> infos =
			new ArrayList<>(MC.getConnection().getListedOnlinePlayers());
		infos.sort(Comparator.comparing(info -> info.getProfile().getName(),
			String.CASE_INSENSITIVE_ORDER));

		Map<UUID, Player> playersById = new HashMap<>();
		for(Player player : MC.level.players())
			playersById.put(player.getUUID(), player);

		int limit = Math.min(infos.size(), maxPlayers.getValueI());
		if(limit <= 0)
			return;

		ArrayList<Row> rows = new ArrayList<>(limit);
		int nameWidth = 0;
		int pingWidth = 0;
		int healthWidth = 0;

		for(int i = 0; i < limit; i++)
		{
			PlayerInfo info = infos.get(i);
			String name = String.valueOf(info.getProfile().getName());
			int latency = info.getLatency();
			Player player = playersById.get(info.getProfile().getId());
			String health = player == null ? "-"
				: String.format(Locale.ROOT, "%.1f",
					player.getHealth() + player.getAbsorptionAmount());
			int healthColor = player == null ? 0xFFAAAAAA
				: getHealthColor(player);

			rows.add(new Row(name, latency, health, healthColor));
			nameWidth = Math.max(nameWidth, MC.font.width(name));
			pingWidth = Math.max(pingWidth,
				MC.font.width(latency + "ms"));
			healthWidth = Math.max(healthWidth, MC.font.width(health));
		}

		String header = "Players: " + infos.size();
		int columnsWidth = nameWidth;
		if(showPing.isChecked())
			columnsWidth += COLUMN_GAP + pingWidth;
		if(showHealth.isChecked())
			columnsWidth += COLUMN_GAP + healthWidth;
		int panelWidth = Math.max(columnsWidth, MC.font.width(header))
			+ PADDING * 2;
		int panelHeight =
			HEADER_HEIGHT + rows.size() * ROW_HEIGHT + PADDING;

		var pose = context.pose();
		pose.pushPose();
		pose.translate((float)posX.getValue(), (float)posY.getValue(), 0);
		pose.scale(scale.getValueF(), scale.getValueF(), 1);

		if(background.isChecked())
			context.fill(0, 0, panelWidth, panelHeight,
				bgColor.getColor().getRGB());
		context.renderOutline(0, 0, panelWidth, panelHeight, 0x40FFFFFF);

		context.drawString(MC.font, header, PADDING, PADDING,
			RenderUtils.toIntColor(WURST.getGui().getAcColor(), 0.95F), true);

		int y = PADDING + HEADER_HEIGHT;
		for(Row row : rows)
		{
			int x = PADDING;
			context.drawString(MC.font, row.name(), x, y, textColor.getColorI(),
				true);
			x += nameWidth;

			if(showPing.isChecked())
			{
				x += COLUMN_GAP;
				String ping = row.ping() + "ms";
				int right = x + pingWidth;
				context.drawString(MC.font, ping, right - MC.font.width(ping),
					y, getPingColor(row.ping()), true);
			}

			if(showHealth.isChecked())
			{
				x += (showPing.isChecked() ? pingWidth : 0) + COLUMN_GAP;
				int right = x + healthWidth;
				context.drawString(MC.font, row.health(),
					right - MC.font.width(row.health()), y, row.healthColor(),
					true);
			}

			y += ROW_HEIGHT;
		}

		pose.popPose();
	}

	private int getPingColor(int latency)
	{
		if(latency < 0)
			return 0xFFAAAAAA;
		if(latency < 100)
			return 0xFF55FF55;
		if(latency < 200)
			return 0xFFFFFF55;
		return 0xFFFF5555;
	}

	private int getHealthColor(Player player)
	{
		float max = Math.max(1, player.getMaxHealth());
		float ratio = (player.getHealth() + player.getAbsorptionAmount()) / max;
		if(ratio <= 0.25F)
			return 0xFFFF5555;
		if(ratio <= 0.5F)
			return 0xFFFFAA00;
		if(ratio <= 0.75F)
			return 0xFFFFFF55;
		return 0xFF55FF55;
	}

	private record Row(String name, int ping, String health, int healthColor)
	{
	}
}
