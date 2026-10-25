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

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.render.GuiGraphicsExtractor;

/**
 * HUD overlay showing how many in-game days the current world has existed.
 *
 * <p>Uses total world time divided by 24000 ticks, matching the Soar module.
 */
@SearchTags({"day counter", "days", "world age", "day count", "time"})
public final class DayCounterHack extends Hack implements GUIRenderListener
{
	private static final int PADDING = 4;

	private final SliderSetting scale = new SliderSetting("Scale",
		"Scale of the day counter text.", 1, 0.5, 5, 0.1,
		ValueDisplay.DECIMAL);

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the day counter.", 10, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the day counter.", 40, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting background = new CheckboxSetting(
		"Show background", "Draws a colored box behind the day counter.", true);

	private final ColorSetting bgColor = new ColorSetting("Background color",
		"Color of the background box.", new Color(0, 0, 0, 150));

	private final ColorSetting textColor = new ColorSetting("Text color",
		"Color of the day counter text.", Color.WHITE);

	public DayCounterHack()
	{
		super("DayCounter");
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
	public void onRenderGUI(GuiGraphicsExtractor context, float partialTicks)
	{
		if(MC.player == null || MC.level == null)
			return;

		long days = MC.level.getGameTime() / 24000L;
		String text = days + " Day" + (days != 1L ? "s" : "");
		int width = MC.font.width(text);

		var pose = context.pose();
		pose.pushMatrix();
		pose.translate((float)posX.getValue(), (float)posY.getValue());
		pose.scale(scale.getValueF(), scale.getValueF());

		if(background.isChecked())
			context.fill(-PADDING, -PADDING, width + PADDING,
				MC.font.lineHeight + PADDING, bgColor.getColor().getRGB());

		context.text(MC.font, text, 0, 0, textColor.getColor().getRGB(), true);
		pose.popMatrix();
	}
}
