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
import net.minecraft.client.gui.GuiGraphics;

/**
 * HUD overlay that reports the currently playing music.
 *
 * <p>Simplification: Soar bundles its own {@code MusicManager} and audio
 * player. Neither exists in WurstB+ Plus, so this port has no local playback
 * state to read and always shows the same "Nothing is playing" placeholder
 * that Soar shows when idle. The waveform and overlay designs are skipped.
 */
@SearchTags({"music info", "music", "song", "now playing", "music hud"})
public final class MusicInfoHack extends Hack implements GUIRenderListener
{
	private static final String PLACEHOLDER = "Nothing is playing";
	private static final int PADDING = 4;
	private static final int LINE_GAP = 2;

	private final SliderSetting scale = new SliderSetting("Scale",
		"Scale of the music info text.", 1.5, 0.5, 5, 0.1,
		ValueDisplay.DECIMAL);

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the music info.", 100, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the music info.", 350, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting showTitle = new CheckboxSetting("Show title",
		"Shows the 'Music' title above the status line.", true);

	private final CheckboxSetting background = new CheckboxSetting(
		"Show background", "Draws a colored box behind the text.", true);

	private final ColorSetting bgColor = new ColorSetting("Background color",
		"Color of the background box.", new Color(0, 0, 0, 150));

	private final ColorSetting textColor = new ColorSetting("Text color",
		"Color of the music info text.", Color.WHITE);

	public MusicInfoHack()
	{
		super("MusicInfo");
		setCategory(Category.RENDER);
		addSetting(scale);
		addSetting(posX);
		addSetting(posY);
		addSetting(showTitle);
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

		String[] lines = showTitle.isChecked() ? new String[]{"Music",
			PLACEHOLDER} : new String[]{PLACEHOLDER};

		int width = 0;
		for(String line : lines)
			width = Math.max(width, MC.font.width(line));

		int lineHeight = MC.font.lineHeight + LINE_GAP;
		int height = lines.length * lineHeight - LINE_GAP;

		var pose = context.pose();
		pose.pushPose();
		pose.translate((float)posX.getValue(), (float)posY.getValue(), 0);
		pose.scale(scale.getValueF(), scale.getValueF(), 1);

		if(background.isChecked())
			context.fill(-PADDING, -PADDING, width + PADDING,
				height + PADDING, bgColor.getColor().getRGB());

		int color = textColor.getColorI();
		for(int i = 0; i < lines.length; i++)
			context.drawString(MC.font, lines[i], 0, i * lineHeight, color, true);

		pose.popPose();
	}
}
