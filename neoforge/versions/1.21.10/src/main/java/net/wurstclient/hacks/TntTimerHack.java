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

import net.minecraft.world.entity.item.PrimedTnt;
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
 * HUD overlay showing the fuse time of the nearest primed TNT.
 *
 * <p>Simplification: Soar could also draw a timer tag above every TNT in the
 * world through a render event. No world-space render listener exists for
 * this in WurstB+ Plus, so this port only implements the HUD mode and reports
 * the nearest primed TNT within the configured range.
 */
@SearchTags({"tnt timer", "tnt", "fuse", "primed tnt", "explosion timer"})
public final class TntTimerHack extends Hack implements GUIRenderListener
{
	private static final int PADDING = 4;

	private final SliderSetting range = new SliderSetting("Range",
		"Maximum distance to search for primed TNT.", 32, 1, 128, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting scale = new SliderSetting("Scale",
		"Scale of the TNT timer text.", 1.5, 0.5, 5, 0.1, ValueDisplay.DECIMAL);

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the overlay.", 500, 0, 2500, 1, ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the overlay.", 100, 0, 2500, 1, ValueDisplay.INTEGER);

	private final CheckboxSetting background = new CheckboxSetting(
		"Show background", "Draws a colored box behind the text.", true);

	private final ColorSetting bgColor = new ColorSetting("Background color",
		"Color of the background box.", new Color(0, 0, 0, 150));

	private final ColorSetting textColor = new ColorSetting("Text color",
		"Color of the TNT timer text.", Color.WHITE);

	public TntTimerHack()
	{
		super("TntTimer");
		setCategory(Category.RENDER);
		addSetting(range);
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
		if(MC.player == null || MC.level == null)
			return;

		PrimedTnt nearest = null;
		double nearestDistanceSq = Double.MAX_VALUE;

		for(PrimedTnt tnt : MC.level.getEntitiesOfClass(PrimedTnt.class,
			MC.player.getBoundingBox().inflate(range.getValue())))
		{
			double distanceSq = tnt.distanceToSqr(MC.player);
			if(distanceSq < nearestDistanceSq)
			{
				nearestDistanceSq = distanceSq;
				nearest = tnt;
			}
		}

		if(nearest == null)
			return;

		float seconds = Math.max(0, nearest.getFuse() - partialTicks) / 20.0F;
		String text = String.format(Locale.ROOT, "TNT: %.2fs", seconds);
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
}
