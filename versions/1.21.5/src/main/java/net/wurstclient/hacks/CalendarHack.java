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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

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
 * HUD overlay showing the current real-world date and weekday.
 *
 * <p>Simplification: Soar drew a full month grid with the current day
 * highlighted in the accent color. This port shows the month/year and the
 * weekday plus date as flat text, since the WurstB+ Plus event API offers no
 * NanoVG hook for the grid layout.
 */
@SearchTags({"calendar", "date", "weekday", "day of week", "month", "year"})
public final class CalendarHack extends Hack implements GUIRenderListener
{
	private static final int PADDING = 4;
	private static final int LINE_GAP = 1;
	private static final DateTimeFormatter MONTH_YEAR =
		DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ROOT);
	private static final DateTimeFormatter WEEKDAY_DATE =
		DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ROOT);

	private final SliderSetting scale = new SliderSetting("Scale",
		"Scale of the calendar text.", 1, 0.5, 5, 0.1, ValueDisplay.DECIMAL);

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the calendar.", 10, 0, 2500, 1, ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the calendar.", 10, 0, 2500, 1, ValueDisplay.INTEGER);

	private final CheckboxSetting background = new CheckboxSetting(
		"Show background", "Draws a colored box behind the calendar.", true);

	private final ColorSetting bgColor = new ColorSetting("Background color",
		"Color of the background box.", new Color(0, 0, 0, 150));

	private final ColorSetting textColor = new ColorSetting("Text color",
		"Color of the calendar text.", Color.WHITE);

	public CalendarHack()
	{
		super("Calendar");
		setCategory(Category.RENDER);
		addSetting(scale);
		addSetting(posX);
		addSetting(posY);
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
		if(MC.player == null)
			return;

		LocalDate today = LocalDate.now();
		String[] lines = {today.format(MONTH_YEAR),
			today.format(WEEKDAY_DATE)};
		int lineHeight = MC.font.lineHeight + LINE_GAP;
		int width = 0;
		for(String line : lines)
			width = Math.max(width, MC.font.width(line));
		int height = lines.length * lineHeight - LINE_GAP;

		var pose = context.pose();
		pose.pushPose();
		pose.translate((float)posX.getValue(), (float)posY.getValue(), 0);
		pose.scale(scale.getValueF(), scale.getValueF(), 1);

		if(background.isChecked())
			context.fill(-PADDING, -PADDING, width + PADDING, height + PADDING,
				bgColor.getColor().getRGB());

		int color = textColor.getColor().getRGB();
		for(int i = 0; i < lines.length; i++)
			context.drawString(MC.font, lines[i], 0, i * lineHeight, color, true);

		pose.popPose();
	}
}
