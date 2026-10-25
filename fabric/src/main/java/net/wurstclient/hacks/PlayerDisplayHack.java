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
import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.FakePlayerEntity;
import net.wurstclient.util.RenderUtils;

/**
 * HUD list of nearby players with name, distance and health.
 *
 * <p>Simplification: Soar's PlayerDisplay rendered a rotating 3D player model
 * through the entity renderer. This port follows the requested player-list
 * layout instead; the model rendering is skipped because it requires
 * render-state access that is not available through the GUI listener.
 */
@SearchTags({"player display", "nearby players", "player list", "hud list"})
public final class PlayerDisplayHack extends Hack implements GUIRenderListener
{
	private static final int PADDING = 4;
	private static final int ROW_HEIGHT = 10;
	private static final int HEADER_HEIGHT = 12;
	private static final int COLUMN_GAP = 6;

	private final SliderSetting scale = new SliderSetting("Scale",
		"Scale of the player list.", 1.25, 0.5, 3, 0.05,
		ValueDisplay.DECIMAL);

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the player list.", 500, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the player list.", 250, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting maxDistance = new SliderSetting("Max distance",
		"Players farther away than this are not listed.", 128, 8, 512, 8,
		ValueDisplay.INTEGER.withSuffix(" blocks"));

	private final SliderSetting maxPlayers = new SliderSetting("Max players",
		"Maximum number of players listed.", 10, 1, 50, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting showDistance = new CheckboxSetting(
		"Show distance", "Adds the distance to each player.", true);

	private final CheckboxSetting showHealth = new CheckboxSetting(
		"Show health", "Adds the current health of each player.", true);

	private final CheckboxSetting background = new CheckboxSetting(
		"Background", "Draws a background behind the list.", true);

	private final ColorSetting bgColor = new ColorSetting("Background color",
		"Color of the list background.", new Color(0, 0, 0, 150));

	private final ColorSetting textColor = new ColorSetting("Text color",
		"Color of the player names.", Color.WHITE);

	public PlayerDisplayHack()
	{
		super("PlayerDisplay");
		setCategory(Category.RENDER);
		addSetting(scale);
		addSetting(posX);
		addSetting(posY);
		addSetting(maxDistance);
		addSetting(maxPlayers);
		addSetting(showDistance);
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
		if(MC.player == null || MC.level == null || MC.options.hideGui)
			return;

		ArrayList<Player> nearby = new ArrayList<>();
		double maxDistanceSq = maxDistance.getValueSq();

		for(Player player : MC.level.players())
		{
			if(player == MC.player || player.isRemoved()
				|| player.getHealth() <= 0
				|| player instanceof FakePlayerEntity
				|| MC.player.distanceToSqr(player) > maxDistanceSq)
				continue;

			nearby.add(player);
		}

		nearby.sort(Comparator.comparingDouble(
			player -> MC.player.distanceTo(player)));

		int shown = Math.min(nearby.size(), maxPlayers.getValueI());
		ArrayList<Row> rows = new ArrayList<>(shown);
		int nameWidth = 0;
		int distanceWidth = 0;
		int healthWidth = 0;

		for(int i = 0; i < shown; i++)
		{
			Player player = nearby.get(i);
			String name = player.getName().getString();
			double distance = MC.player.distanceTo(player);
			String distanceText =
				String.format(Locale.ROOT, "%.1fm", distance);
			String healthText = String.format(Locale.ROOT, "%.1f",
				player.getHealth() + player.getAbsorptionAmount());

			rows.add(new Row(name, distanceText, healthText,
				getHealthColor(player)));
			nameWidth = Math.max(nameWidth, MC.font.width(name));
			distanceWidth = Math.max(distanceWidth,
				MC.font.width(distanceText));
			healthWidth = Math.max(healthWidth, MC.font.width(healthText));
		}

		String header = "Players (" + nearby.size() + ")";
		int columnsWidth = nameWidth;
		if(showDistance.isChecked())
			columnsWidth += COLUMN_GAP + distanceWidth;
		if(showHealth.isChecked())
			columnsWidth += COLUMN_GAP + healthWidth;

		int panelWidth =
			Math.max(columnsWidth, MC.font.width(header)) + PADDING * 2;
		int panelHeight =
			HEADER_HEIGHT + rows.size() * ROW_HEIGHT + PADDING;

		PoseStack pose = context.pose();
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
			context.drawString(MC.font, row.name(), x, y,
				textColor.getColorI(), true);
			x += nameWidth;

			if(showDistance.isChecked())
			{
				x += COLUMN_GAP;
				context.drawString(MC.font, row.distance(), x, y, 0xFFAAAAAA,
					true);
			}

			if(showHealth.isChecked())
			{
				x += (showDistance.isChecked() ? distanceWidth : 0)
					+ COLUMN_GAP;
				int right = x + healthWidth;
				context.drawString(MC.font, row.health(),
					right - MC.font.width(row.health()), y, row.healthColor(),
					true);
			}

			y += ROW_HEIGHT;
		}

		pose.popPose();
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

	private record Row(String name, String distance, String health,
		int healthColor)
	{
	}
}
