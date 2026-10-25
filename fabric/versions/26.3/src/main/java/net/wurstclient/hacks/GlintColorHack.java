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

import net.minecraft.world.item.ItemStack;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Controls vanilla glint speed/strength and shows the configured glint colour
 * as an overlay on enchanted hotbar slots.
 *
 * <p>Recolouring the world-space glint texture needs a custom shader. This
 * port keeps the vanilla glint pipeline and exposes the colour on the HUD,
 * which avoids per-version render-type changes.
 */
@SearchTags({"glint color", "glintcolour", "enchantment glint", "foil color"})
public final class GlintColorHack extends Hack
	implements UpdateListener, GUIRenderListener
{
	public enum Mode
	{
		SYNC,
		RAINBOW,
		CUSTOM
	}

	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"Colour source for the HUD glint overlay.", Mode.values(), Mode.SYNC);

	private final ColorSetting color = new ColorSetting("Color",
		"Custom glint colour.", new Color(255, 0, 0));

	private final SliderSetting speed = new SliderSetting("Speed",
		"Vanilla glint animation speed.", 0.5, 0, 1, 0.01,
		ValueDisplay.DECIMAL);

	private final SliderSetting strength = new SliderSetting("Strength",
		"Vanilla glint strength.", 0.5, 0, 1, 0.01, ValueDisplay.DECIMAL);

	private final CheckboxSetting hotbar = new CheckboxSetting("Hotbar overlay",
		"Draws the configured colour over enchanted hotbar items.", true);

	private double oldSpeed;
	private double oldStrength;

	public GlintColorHack()
	{
		super("GlintColor");
		setCategory(Category.RENDER);
		addSetting(mode);
		addSetting(color);
		addSetting(speed);
		addSetting(strength);
		addSetting(hotbar);
	}

	@Override
	protected void onEnable()
	{
		oldSpeed = MC.options.glintSpeed().get();
		oldStrength = MC.options.glintStrength().get();
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(GUIRenderListener.class, this);
		apply();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(GUIRenderListener.class, this);
		MC.options.glintSpeed().set(oldSpeed);
		MC.options.glintStrength().set(oldStrength);
	}

	@Override
	public void onUpdate()
	{
		apply();
	}

	private void apply()
	{
		MC.options.glintSpeed().set(speed.getValue());
		MC.options.glintStrength().set(strength.getValue());
	}

	@Override
	public void onRenderGUI(GuiGraphicsExtractor context, float partialTicks)
	{
		if(!hotbar.isChecked() || MC.player == null || uiHidden())
			return;

		int width = MC.getWindow().getGuiScaledWidth();
		int height = MC.getWindow().getGuiScaledHeight();
		int left = width / 2 - 91;
		int top = height - 22;
		int rgb = getGlintColor().getRGB() & 0xFFFFFF;

		for(int i = 0; i < 9; i++)
		{
			ItemStack stack = MC.player.getInventory().getItem(i);
			if(stack.isEmpty() || !stack.isEnchanted())
				continue;

			int x = left + 3 + i * 20;
			int y = top + 3;
			context.fill(x, y, x + 16, y + 16, 0x50000000 | rgb);
		}
	}

	private Color getGlintColor()
	{
		switch(mode.getSelected())
		{
			case RAINBOW:
			float hue = (System.currentTimeMillis() % 3000L) / 3000F;
			return Color.getHSBColor(hue, 1F, 1F);

			case CUSTOM:
			return color.getColor();

			case SYNC:
			default:
			float[] ac = WURST.getGui().getAcColor();
			return new Color(clamp(ac[0]), clamp(ac[1]), clamp(ac[2]));
		}
	}

	private float clamp(float value)
	{
		return Math.max(0, Math.min(1, value));
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
