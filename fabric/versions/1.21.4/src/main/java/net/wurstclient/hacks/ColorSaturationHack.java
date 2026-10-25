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
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Applies a screen-space colour grade (hue tint, brightness and contrast).
 *
 * <p>Soar used a post-processing shader. This port uses a lightweight
 * full-screen overlay instead, so it works across every loader and Minecraft
 * version without a version-specific shader pipeline.
 */
@SearchTags({"color saturation", "saturation", "color filter", "colour grade"})
public final class ColorSaturationHack extends Hack implements GUIRenderListener
{
	private final SliderSetting hue = new SliderSetting("Hue",
		"Hue of the colour filter.", 0, 0, 1, 0.01, ValueDisplay.DECIMAL);

	private final SliderSetting saturation = new SliderSetting("Saturation",
		"Strength of the colour filter.", 0.5, 0, 1, 0.01,
		ValueDisplay.DECIMAL);

	private final SliderSetting brightness = new SliderSetting("Brightness",
		"Screen brightness adjustment.", 0.5, 0, 1, 0.01,
		ValueDisplay.DECIMAL);

	private final SliderSetting contrast = new SliderSetting("Contrast",
		"Edge darkening/contrast adjustment.", 0.5, 0, 1, 0.01,
		ValueDisplay.DECIMAL);

	public ColorSaturationHack()
	{
		super("ColorSaturation");
		setCategory(Category.RENDER);
		addSetting(hue);
		addSetting(saturation);
		addSetting(brightness);
		addSetting(contrast);
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
		if(MC.player == null || uiHidden())
			return;

		int width = MC.getWindow().getGuiScaledWidth();
		int height = MC.getWindow().getGuiScaledHeight();

		Color hueColor = Color.getHSBColor((float)hue.getValue(), 1F, 1F);
		int tintAlpha = (int)((1F - saturation.getValue()) * 46F);
		if(tintAlpha > 0)
			context.fill(0, 0, width, height, argb(hueColor, tintAlpha));

		double b = brightness.getValue();
		if(b > 0.5)
			context.fill(0, 0, width, height,
				argb(Color.WHITE, (int)((b - 0.5) * 120)));
		else if(b < 0.5)
			context.fill(0, 0, width, height,
				argb(Color.BLACK, (int)((0.5 - b) * 120)));

		int edge = (int)((1F - contrast.getValue()) * 70F);
		if(edge > 0)
		{
			context.fill(0, 0, width, 6, argb(Color.BLACK, edge));
			context.fill(0, height - 6, width, height, argb(Color.BLACK, edge));
			context.fill(0, 0, 6, height, argb(Color.BLACK, edge));
			context.fill(width - 6, 0, width, height, argb(Color.BLACK, edge));
		}
	}

	private int argb(java.awt.Color color, int alpha)
	{
		int a = Math.max(0, Math.min(255, alpha));
		return a << 24 | color.getRGB() & 0xFFFFFF;
	}

	private boolean uiHidden()
	{
		if(net.wurstclient.hud2.HackHudElement.isPreviewRendering())
			return false;

		try
		{
			Object gui = MC.getClass().getField("gui").get(MC);
			if(gui.getClass().getMethod("screen").invoke(gui) != null)
				return true;
		}catch(Exception ignored)
		{}

		try
		{
			return Boolean.TRUE.equals(MC.options.getClass()
				.getField("hideGui").get(MC.options));
		}catch(Exception ignored)
		{
			return false;
		}
	}

	private void text(GuiGraphics context, String text, int x, int y,
		int color, boolean shadow)
	{
		context.drawString(MC.font, text, x, y, color, shadow);
	}

	private void centeredText(GuiGraphics context, String text, int x,
		int y, int color)
	{
		context.drawCenteredString(MC.font, text, x, y, color);
	}

}
