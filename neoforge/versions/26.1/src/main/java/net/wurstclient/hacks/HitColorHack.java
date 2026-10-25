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

import net.minecraft.world.entity.Entity;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.events.PlayerAttacksEntityListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Screen flash shown when the player hits an entity.
 *
 * <p>Soar recoloured the red entity hurt overlay. This port draws a configurable
 * hit flash on the HUD instead, which is independent of entity render states.
 */
@SearchTags({"hit color", "hitcolour", "hit flash", "damage flash"})
public final class HitColorHack extends Hack
	implements PlayerAttacksEntityListener, GUIRenderListener
{
	private final ColorSetting color = new ColorSetting("Color",
		"Color of the hit flash.", new Color(255, 40, 40));

	private final SliderSetting alpha = new SliderSetting("Alpha",
		"Maximum opacity of the hit flash.", 0.25, 0, 1, 0.01,
		ValueDisplay.DECIMAL);

	private final SliderSetting duration = new SliderSetting("Duration",
		"How long the hit flash lasts.", 0.25, 0.05, 2, 0.05,
		ValueDisplay.DECIMAL.withSuffix("s"));

	private long lastHit;

	public HitColorHack()
	{
		super("HitColor");
		setCategory(Category.RENDER);
		addSetting(color);
		addSetting(alpha);
		addSetting(duration);
	}

	@Override
	protected void onEnable()
	{
		lastHit = 0;
		EVENTS.add(PlayerAttacksEntityListener.class, this);
		EVENTS.add(GUIRenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(PlayerAttacksEntityListener.class, this);
		EVENTS.remove(GUIRenderListener.class, this);
	}

	@Override
	public void onPlayerAttacksEntity(Entity target)
	{
		lastHit = System.currentTimeMillis();
	}

	@Override
	public void onRenderGUI(GuiGraphicsExtractor context, float partialTicks)
	{
		if(lastHit == 0 || uiHidden())
			return;

		long elapsed = System.currentTimeMillis() - lastHit;
		long total = (long)(duration.getValue() * 1000.0);
		if(elapsed < 0 || elapsed > total)
			return;

		float fade = 1F - elapsed / (float)total;
		int width = MC.getWindow().getGuiScaledWidth();
		int height = MC.getWindow().getGuiScaledHeight();
		int a = (int)(fade * alpha.getValue() * 255F);
		context.fill(0, 0, width, height, argb(color.getColor(), a));
	}

	private int argb(java.awt.Color color, int alpha)
	{
		int a = Math.max(0, Math.min(255, alpha));
		return a << 24 | color.getRGB() & 0xFFFFFF;
	}

	private boolean uiHidden()
	{
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
