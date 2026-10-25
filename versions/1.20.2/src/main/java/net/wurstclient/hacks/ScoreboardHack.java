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

import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Scoreboard;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.minecraft.client.gui.GuiGraphics;

/**
 * HUD overlay rendering the sidebar scoreboard at a configurable position.
 *
 * <p>Simplification: hiding the vanilla sidebar would require a mixin into the
 * Gui renderer, which is out of scope. This port therefore draws an additional,
 * fully customizable copy of the sidebar; the vanilla sidebar stays visible.
 * The rendering mirrors vanilla's ordering and 15-entry limit. The optional
 * shadow setting from Soar is kept.
 */
@SearchTags({"scoreboard", "sidebar", "scores", "objective"})
public final class ScoreboardHack extends Hack implements GUIRenderListener
{
	private static final Comparator<Score> SCORE_ORDER =
		Comparator.comparingInt(Score::getScore).reversed()
			.thenComparing(Score::getOwner, String.CASE_INSENSITIVE_ORDER);
	private static final int MAX_ENTRIES = 15;
	private static final int PADDING = 4;
	private static final int TITLE_GAP = 2;

	private final SliderSetting scale = new SliderSetting("Scale",
		"Scale of the scoreboard.", 1.0, 0.5, 5, 0.1, ValueDisplay.DECIMAL);

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the scoreboard.", 4, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the scoreboard.", 4, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting background = new CheckboxSetting(
		"Show background", "Draws a colored box behind the scoreboard.", true);

	private final CheckboxSetting showNumbers = new CheckboxSetting(
		"Show numbers", "Shows each entry's score on the right.", true);

	private final CheckboxSetting shadow = new CheckboxSetting("Text shadow",
		"Draws the scoreboard text with a shadow.", false);

	private final ColorSetting bgColor = new ColorSetting("Background color",
		"Color of the background box.", new Color(0, 0, 0, 150));

	private final ColorSetting titleColor = new ColorSetting("Title color",
		"Color of the scoreboard title.", Color.WHITE);

	private final ColorSetting textColor = new ColorSetting("Text color",
		"Color of the scoreboard entries.", Color.WHITE);

	private final ColorSetting scoreColor = new ColorSetting("Score color",
		"Color of the entry scores.", new Color(255, 85, 85));

	public ScoreboardHack()
	{
		super("Scoreboard");
		setCategory(Category.RENDER);
		addSetting(scale);
		addSetting(posX);
		addSetting(posY);
		addSetting(background);
		addSetting(showNumbers);
		addSetting(shadow);
		addSetting(bgColor);
		addSetting(titleColor);
		addSetting(textColor);
		addSetting(scoreColor);
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
		if(MC.player == null || MC.level == null)
			return;

		Scoreboard scoreboard = MC.level.getScoreboard();
		Objective objective =
			scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		if(objective == null)
			return;

		List<Score> entries = scoreboard.getPlayerScores(objective).stream()
			.sorted(SCORE_ORDER).limit(MAX_ENTRIES).toList();

		if(entries.isEmpty())
			return;

		Component title = objective.getDisplayName();
		List<Component> names = new ArrayList<>();
		List<Component> scores = new ArrayList<>();

		int width = MC.font.width(title);
		for(Score entry : entries)
		{
			String owner = entry.getOwner();
			PlayerTeam team = scoreboard.getPlayersTeam(owner);
			Component name = PlayerTeam.formatNameForTeam(team,
				Component.literal(owner));
			Component score = Component.literal(Integer.toString(entry.getScore()));

			names.add(name);
			scores.add(score);

			int rowWidth = MC.font.width(name);
			if(showNumbers.isChecked())
				rowWidth += MC.font.width(" ") + MC.font.width(score);
			width = Math.max(width, rowWidth);
		}

		int titleHeight = MC.font.lineHeight + TITLE_GAP;
		int height = titleHeight + entries.size() * MC.font.lineHeight;

		var pose = context.pose();
		pose.pushPose();
		pose.translate((float)posX.getValue(), (float)posY.getValue(), 0);
		pose.scale(scale.getValueF(), scale.getValueF(), 1);

		if(background.isChecked())
			context.fill(-PADDING, -PADDING, width + PADDING,
				height + PADDING, bgColor.getColor().getRGB());

		boolean useShadow = shadow.isChecked();
		int titleX = (width - MC.font.width(title)) / 2;
		context.drawString(MC.font, title, titleX, 0, titleColor.getColorI(),
			useShadow);

		for(int i = 0; i < entries.size(); i++)
		{
			int y = titleHeight + i * MC.font.lineHeight;
			context.drawString(MC.font, names.get(i), 0, y, textColor.getColorI(),
				useShadow);

			if(showNumbers.isChecked())
			{
				Component score = scores.get(i);
				int scoreX = width - MC.font.width(score);
				context.drawString(MC.font, score, scoreX, y, scoreColor.getColorI(),
					useShadow);
			}
		}

		pose.popPose();
	}
}
