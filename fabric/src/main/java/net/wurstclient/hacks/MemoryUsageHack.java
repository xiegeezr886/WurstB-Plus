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
import java.util.List;
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
 * HUD overlay showing how much of the JVM's maximum heap is in use.
 *
 * <p>Simplification: Soar's "fancy" design drew a circular arc gauge. This
 * port only provides the simple text design, extended with the used and
 * maximum heap sizes in megabytes.
 */
@SearchTags({"memory usage", "memory", "ram", "jvm", "heap", "memory hud"})
public final class MemoryUsageHack extends Hack implements GUIRenderListener
{
	private static final long MEGABYTE = 1024L * 1024L;
	private static final int PADDING = 4;
	private static final int LINE_GAP = 2;

	private final SliderSetting scale = new SliderSetting("Scale",
		"Scale of the memory usage text.", 1.5, 0.5, 5, 0.1,
		ValueDisplay.DECIMAL);

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the memory usage text.", 100, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the memory usage text.", 300, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting showDetails = new CheckboxSetting(
		"Show details", "Shows used and maximum heap size in megabytes.", true);

	private final CheckboxSetting background = new CheckboxSetting(
		"Show background", "Draws a colored box behind the text.", true);

	private final ColorSetting bgColor = new ColorSetting("Background color",
		"Color of the background box.", new Color(0, 0, 0, 150));

	private final ColorSetting textColor = new ColorSetting("Text color",
		"Color of the memory usage text.", Color.WHITE);

	public MemoryUsageHack()
	{
		super("MemoryUsage");
		setCategory(Category.RENDER);
		addSetting(scale);
		addSetting(posX);
		addSetting(posY);
		addSetting(showDetails);
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

		Runtime runtime = Runtime.getRuntime();
		long used = runtime.totalMemory() - runtime.freeMemory();
		long max = runtime.maxMemory();
		long percent = max > 0 ? used * 100L / max : 0;

		List<String> lines = new ArrayList<>();
		lines.add("Memory: " + percent + "%");
		if(showDetails.isChecked())
			lines.add(String.format(Locale.ROOT, "%d / %d MB",
				used / MEGABYTE, max / MEGABYTE));

		int width = 0;
		for(String line : lines)
			width = Math.max(width, MC.font.width(line));

		int lineHeight = MC.font.lineHeight + LINE_GAP;
		int height = lines.size() * lineHeight - LINE_GAP;

		var pose = context.pose();
		pose.pushPose();
		pose.translate((float)posX.getValue(), (float)posY.getValue(), 0);
		pose.scale(scale.getValueF(), scale.getValueF(), 1);

		if(background.isChecked())
			context.fill(-PADDING, -PADDING, width + PADDING,
				height + PADDING, bgColor.getColor().getRGB());

		int color = textColor.getColorI();
		for(int i = 0; i < lines.size(); i++)
			context.drawString(MC.font, lines.get(i), 0, i * lineHeight, color,
				true);

		pose.popPose();
	}
}
