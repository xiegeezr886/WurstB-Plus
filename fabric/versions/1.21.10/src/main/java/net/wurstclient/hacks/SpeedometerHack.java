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

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.render.GuiGraphicsExtractor;

/**
 * HUD speedometer showing horizontal movement speed in blocks per second.
 *
 * <p>Simplification: Soar drew a 200-sample scrolling graph with NanoVG. This
 * port shows the smoothed numeric speed only.
 */
@SearchTags({"speedometer", "speed", "velocity", "blocks per second",
	"m/s"})
public final class SpeedometerHack extends Hack
	implements UpdateListener, GUIRenderListener
{
	private static final double SMOOTHING = 0.3;
	private static final double MAX_TELEPORT_DISTANCE_SQ = 100;

	private final SliderSetting scale = new SliderSetting("Scale",
		"Scale of the speedometer text.", 1.5, 0.5, 5, 0.1,
		ValueDisplay.DECIMAL);

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the speedometer text.", 500, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the speedometer text.", 330, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting background = new CheckboxSetting(
		"Show background", "Draws a colored box behind the speed text.",
		true);

	private final ColorSetting bgColor = new ColorSetting("Background color",
		"Color of the background box.", new Color(0, 0, 0, 150));

	private final ColorSetting textColor = new ColorSetting("Text color",
		"Color of the speed text.", new Color(255, 255, 255));

	private double speed;
	private double lastX;
	private double lastZ;
	private boolean hasLastPos;

	public SpeedometerHack()
	{
		super("Speedometer");
		setCategory(Category.RENDER);
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
		speed = 0;
		hasLastPos = false;
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(GUIRenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(GUIRenderListener.class, this);
		speed = 0;
		hasLastPos = false;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null)
		{
			speed = 0;
			hasLastPos = false;
			return;
		}

		double x = MC.player.getX();
		double z = MC.player.getZ();

		if(hasLastPos)
		{
			double dx = x - lastX;
			double dz = z - lastZ;
			double distanceSq = dx * dx + dz * dz;

			if(distanceSq <= MAX_TELEPORT_DISTANCE_SQ)
			{
				// Client ticks run at 20 per second.
				double instant = Math.sqrt(distanceSq) * 20.0;
				speed = speed == 0 ? instant
					: speed * (1 - SMOOTHING) + instant * SMOOTHING;
			}else
				speed = 0;
		}

		lastX = x;
		lastZ = z;
		hasLastPos = true;
	}

	@Override
	public void onRenderGUI(GuiGraphicsExtractor context, float partialTicks)
	{
		if(MC.player == null)
			return;

		String text =
			String.format(Locale.ROOT, "Speed: %.2f m/s", speed);
		double textScale = scale.getValue();
		double width = MC.font.width(text) * textScale;
		double height = MC.font.lineHeight * textScale;
		double x = posX.getValue();
		double y = posY.getValue();

		if(background.isChecked())
			context.fill((int)(x - 4), (int)(y - 4), (int)(x + width + 4),
				(int)(y + height + 4), bgColor.getColor().getRGB());

		var pose = context.pose();
		pose.pushMatrix();
		pose.translate((float)x, (float)y);
		pose.scale((float)textScale, (float)textScale);
		context.text(MC.font, text, 0, 0, textColor.getColor().getRGB(), true);
		pose.popMatrix();
	}
}
