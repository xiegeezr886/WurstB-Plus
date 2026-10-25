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
import java.util.Locale;

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
 * HUD overlay showing how long the game has been running in this session.
 *
 * <p>Simplification: Soar displayed an optional icon through its NanoVG icon
 * font. This port draws the text only. The timer starts when the client
 * launches, matching Soar's launch-time based counter.
 */
@SearchTags({"play time", "playtime", "session time", "uptime", "clock"})
public final class PlayTimeDisplayHack extends Hack
	implements GUIRenderListener
{
	private static final long LAUNCH_MILLIS = System.currentTimeMillis();
	private static final int PADDING = 4;

	private final SliderSetting scale = new SliderSetting("Scale",
		"Scale of the playtime text.", 1.5, 0.5, 5, 0.1, ValueDisplay.DECIMAL);

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the playtime text.", 100, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the playtime text.", 100, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting background = new CheckboxSetting(
		"Show background", "Draws a colored box behind the text.", true);

	private final ColorSetting bgColor = new ColorSetting("Background color",
		"Color of the background box.", new Color(0, 0, 0, 150));

	private final ColorSetting textColor = new ColorSetting("Text color",
		"Color of the playtime text.", Color.WHITE);

	public PlayTimeDisplayHack()
	{
		super("PlayTimeDisplay");
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
		if(MC.player == null)
			return;

		String text = "Playtime: " + getPlayTime();
		int width = MC.font.width(text);
		int height = MC.font.lineHeight;

		var pose = context.pose();
		pose.pushMatrix();
		pose.translate((float)posX.getValue(), (float)posY.getValue());
		pose.scale(scale.getValueF(), scale.getValueF());

		if(background.isChecked())
			context.fill(-PADDING, -PADDING, width + PADDING,
				height + PADDING, bgColor.getColor().getRGB());

		context.text(MC.font, text, 0, 0, textColor.getColorI(), true);

		pose.popMatrix();
	}

	private String getPlayTime()
	{
		long seconds = (System.currentTimeMillis() - LAUNCH_MILLIS) / 1000;
		long hours = seconds / 3600;
		long minutes = seconds % 3600 / 60;
		long secs = seconds % 60;
		return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes,
			secs);
	}
}
