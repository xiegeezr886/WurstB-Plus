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

import net.minecraft.client.CameraType;
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
 * Draws a custom crosshair (color, size, gap and thickness) over the vanilla
 * one.
 *
 * <p>Simplification: Soar cancelled the vanilla crosshair and blitted one of
 * six texture variants. Wurst cannot cancel HUD drawing without a mixin, so
 * this port draws simple bars on top of the vanilla crosshair instead.
 */
@SearchTags({"crosshair", "cross hair", "aim", "reticle", "custom crosshair"})
public final class CrosshairHack extends Hack implements GUIRenderListener
{
	private final ColorSetting color = new ColorSetting("Color",
		"Color of the custom crosshair.", new Color(255, 255, 255, 220));

	private final SliderSetting size = new SliderSetting("Size",
		"Length of each crosshair bar.", 4, 1, 15, 1, ValueDisplay.INTEGER);

	private final SliderSetting gap = new SliderSetting("Gap",
		"Distance between the center and each bar.", 2, 0, 10, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting thickness = new SliderSetting("Thickness",
		"Thickness of each crosshair bar.", 1, 1, 5, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting hideThirdPerson = new CheckboxSetting(
		"Hide in third person", "Does not draw the crosshair in third person.",
		false);

	public CrosshairHack()
	{
		super("Crosshair");
		setCategory(Category.RENDER);
		addSetting(color);
		addSetting(size);
		addSetting(gap);
		addSetting(thickness);
		addSetting(hideThirdPerson);
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
		if(MC.player == null || MC.screen != null || MC.options.hideGui)
			return;

		if(hideThirdPerson.isChecked()
			&& MC.options.getCameraType() != CameraType.FIRST_PERSON)
			return;

		int centerX = context.guiWidth() / 2;
		int centerY = context.guiHeight() / 2;
		int length = size.getValueI();
		int spacing = gap.getValueI();
		int width = thickness.getValueI();
		int colorI = toArgb(color.getColor());

		int half = width / 2;
		int x1 = centerX - half;
		int x2 = x1 + width;

		// top and bottom bars
		context.fill(x1, centerY - spacing - length, x2, centerY - spacing,
			colorI);
		context.fill(x1, centerY + spacing, x2, centerY + spacing + length,
			colorI);

		// left and right bars
		int y1 = centerY - half;
		int y2 = y1 + width;
		context.fill(centerX - spacing - length, y1, centerX - spacing, y2,
			colorI);
		context.fill(centerX + spacing, y1, centerX + spacing + length, y2,
			colorI);
	}

	private int toArgb(Color col)
	{
		return col.getAlpha() << 24 | col.getRed() << 16 | col.getGreen() << 8
			| col.getBlue();
	}
}
