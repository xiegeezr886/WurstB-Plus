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
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.render.GuiGraphicsExtractor;

/**
 * Red vignette shown while the player's health is low.
 *
 * <p>Soar drew a smooth texture overlay. This port draws a lightweight tinted
 * overlay, so it works across all supported versions.
 */
@SearchTags({"damage tint", "low health", "red vignette", "health tint"})
public final class DamageTintHack extends Hack
	implements UpdateListener, GUIRenderListener
{
	private final SliderSetting threshold = new SliderSetting("Health",
		"Health threshold at which the tint appears.", 10, 1, 20, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting alpha = new SliderSetting("Alpha",
		"Maximum opacity of the tint.", 0.35, 0, 1, 0.01,
		ValueDisplay.DECIMAL);

	private final ColorSetting color = new ColorSetting("Color",
		"Color of the low-health tint.", new Color(255, 0, 0));

	private float fade;

	public DamageTintHack()
	{
		super("DamageTint");
		setCategory(Category.RENDER);
		addSetting(threshold);
		addSetting(alpha);
		addSetting(color);
	}

	@Override
	protected void onEnable()
	{
		fade = 0;
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(GUIRenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(GUIRenderListener.class, this);
		fade = 0;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null)
			return;

		boolean low = MC.player.getHealth() <= threshold.getValue()
			&& !MC.player.isCreative() && !MC.player.isSpectator();
		float target = low ? 1F : 0F;
		fade += (target - fade) * 0.15F;
		if(fade < 0.01F && !low)
			fade = 0;
	}

	@Override
	public void onRenderGUI(GuiGraphicsExtractor context, float partialTicks)
	{
		if(fade <= 0.01F || MC.player == null || uiHidden() || MC.player.isCreative()
			|| MC.player.isSpectator())
			return;

		int width = MC.getWindow().getGuiScaledWidth();
		int height = MC.getWindow().getGuiScaledHeight();
		int a = (int)(fade * alpha.getValue() * 255F);
		context.fill(0, 0, width, height, argb(color.getColor(), a));

		int edge = Math.min(255, a + 40);
		context.fill(0, 0, width, 8, argb(color.getColor(), edge));
		context.fill(0, height - 8, width, height,
			argb(color.getColor(), edge));
		context.fill(0, 0, 8, height, argb(color.getColor(), edge));
		context.fill(width - 8, 0, width, height,
			argb(color.getColor(), edge));
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

	private void text(GuiGraphicsExtractor context, String text, int x,
		int y, int color, boolean shadow)
	{
		context.text(MC.font, text, x, y, color, shadow);
	}

	private void centeredText(GuiGraphicsExtractor context, String text,
		int x, int y, int color)
	{
		context.centeredText(MC.font, text, x, y, color);
	}

}
