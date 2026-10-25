/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Rotating stylised head preview.
 *
 * <p>Soar rendered a voxel skin from the player's skin texture. Fetching and
 * drawing the skin texture differs between versions, so this port draws a
 * lightweight skin-coloured head with an animated face instead.
 */
@SearchTags({"skin 3d", "skin3d", "skin preview", "player head"})
public final class Skin3DHack extends Hack implements GUIRenderListener
{
	private final SliderSetting scale = new SliderSetting("Scale",
		"Scale of the head preview.", 2, 0.5, 5, 0.1,
		ValueDisplay.DECIMAL);

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the preview.", 30, 0, 2500, 1, ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the preview.", 30, 0, 2500, 1, ValueDisplay.INTEGER);

	private final SliderSetting spin = new SliderSetting("Spin speed",
		"Rotation speed of the preview.", 1, 0, 4, 0.05,
		ValueDisplay.DECIMAL);

	private final CheckboxSetting showName = new CheckboxSetting("Show name",
		"Shows the local player name below the head.", true);

	public Skin3DHack()
	{
		super("Skin3D");
		setCategory(Category.RENDER);
		addSetting(scale);
		addSetting(posX);
		addSetting(posY);
		addSetting(spin);
		addSetting(showName);
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
		if(MC.player == null || uiHidden())
			return;

		int size = (int)(24 * scale.getValue());
		if(size < 4)
			return;

		int x = (int)posX.getValue();
		int y = (int)posY.getValue();
		double angle = System.currentTimeMillis() / 1000.0
			* spin.getValue();
		int off = (int)(Math.sin(angle) * Math.max(1, size / 10));

		context.fill(x + off, y - off, x + size + off, y + size - off,
			0x60000000);
		context.fill(x, y, x + size, y + size, 0xFF1B1B1B);
		context.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0xFFC79A6B);

		int eye = Math.max(2, size / 5);
		context.fill(x + size / 4, y + size / 3, x + size / 4 + eye,
			y + size / 3 + eye, 0xFF202020);
		context.fill(x + size * 3 / 4 - eye, y + size / 3, x + size * 3 / 4,
			y + size / 3 + eye, 0xFF202020);
		context.fill(x + size / 3, y + size * 2 / 3, x + size * 2 / 3,
			y + size * 2 / 3 + Math.max(1, size / 12), 0xFF6B3E2E);

		if(showName.isChecked())
			centeredText(context, MC.player.getName().getString(),
				x + size / 2, y + size + 3, 0xFFFFFFFF);
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
