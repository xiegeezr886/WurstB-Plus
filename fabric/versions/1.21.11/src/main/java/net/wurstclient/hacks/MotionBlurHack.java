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

import net.minecraft.util.Mth;
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
 * Draws an edge smear whose intensity follows camera movement.
 *
 * <p>Soar used a framebuffer accumulation shader. This port approximates the
 * visual feedback with a movement-reactive HUD overlay, avoiding per-version
 * post-processing code.
 */
@SearchTags({"motion blur", "motionblur", "camera blur", "afterimage"})
public final class MotionBlurHack extends Hack
	implements UpdateListener, GUIRenderListener
{
	private final SliderSetting amount = new SliderSetting("Amount",
		"Strength of the movement smear.", 0.5, 0.1, 0.9, 0.05,
		ValueDisplay.DECIMAL);

	private final ColorSetting color = new ColorSetting("Color",
		"Color of the motion smear.", new Color(255, 255, 255));

	private float lastYaw;
	private float lastPitch;
	private float intensity;
	private boolean initialized;

	public MotionBlurHack()
	{
		super("MotionBlur");
		setCategory(Category.RENDER);
		addSetting(amount);
		addSetting(color);
	}

	@Override
	protected void onEnable()
	{
		intensity = 0;
		initialized = false;
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(GUIRenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(GUIRenderListener.class, this);
		intensity = 0;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null)
			return;

		float yaw = MC.player.getYRot();
		float pitch = MC.player.getXRot();
		if(!initialized)
		{
			lastYaw = yaw;
			lastPitch = pitch;
			initialized = true;
			return;
		}

		float dy = Math.abs(Mth.wrapDegrees(yaw - lastYaw));
		float dp = Math.abs(pitch - lastPitch);
		lastYaw = yaw;
		lastPitch = pitch;

		float movement = Math.min(1F, (dy + dp) / 20F);
		float target = movement * (float)amount.getValue();
		intensity += (target - intensity) * 0.2F;
		if(intensity < 0.005F)
			intensity = 0;
	}

	@Override
	public void onRenderGUI(GuiGraphicsExtractor context, float partialTicks)
	{
		if(intensity <= 0.005F || uiHidden())
			return;

		int width = MC.getWindow().getGuiScaledWidth();
		int height = MC.getWindow().getGuiScaledHeight();
		int maxAlpha = (int)(intensity * 90F);
		int base = color.getColor().getRGB() & 0xFFFFFF;

		for(int i = 0; i < 6; i++)
		{
			int a = maxAlpha * (6 - i) / 12;
			int strip = 4 + i * 6;
			context.fill(0, 0, strip, height, a << 24 | base);
			context.fill(width - strip, 0, width, height, a << 24 | base);
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
