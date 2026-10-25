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
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * HUD overlay showing the direction the player is facing and the current yaw.
 *
 * <p>Simplification: Soar offered a "fancy" mode that drew a horizontal
 * compass strip with a moving marker. This port only implements the simple
 * text mode, which is the default in Soar as well.
 */
@SearchTags({"compass", "direction", "facing", "yaw", "north", "south",
	"east", "west"})
public final class CompassHack extends Hack implements GUIRenderListener
{
	private static final int PADDING = 4;

	private final SliderSetting scale = new SliderSetting("Scale",
		"Scale of the compass text.", 1, 0.5, 5, 0.1, ValueDisplay.DECIMAL);

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the compass.", 100, 0, 2500, 1, ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the compass.", 10, 0, 2500, 1, ValueDisplay.INTEGER);

	private final CheckboxSetting background = new CheckboxSetting(
		"Show background", "Draws a colored box behind the compass text.", true);

	private final ColorSetting bgColor = new ColorSetting("Background color",
		"Color of the background box.", new Color(0, 0, 0, 150));

	private final ColorSetting textColor = new ColorSetting("Text color",
		"Color of the compass text.", Color.WHITE);

	public CompassHack()
	{
		super("Compass");
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
		if(MC.player == null)
			return;

		double yaw = ((MC.player.getYRot() % 360) + 360) % 360;
		String text = String.format(Locale.ROOT, "Facing: %s  Yaw: %.1f",
			getDirection(yaw), yaw);
		int width = MC.font.width(text);

		var pose = context.pose();
		pose.pushMatrix();
		pose.translate((float)posX.getValue(), (float)posY.getValue());
		pose.scale(scale.getValueF(), scale.getValueF());

		if(background.isChecked())
			context.fill(-PADDING, -PADDING, width + PADDING,
				MC.font.lineHeight + PADDING, bgColor.getColor().getRGB());

		context.text(MC.font, text, 0, 0, textColor.getColor().getRGB(), true);
		pose.popMatrix();
	}

	/**
	 * Maps a Minecraft yaw (0 = south, 90 = west) to a compass direction,
	 * using the same eight-way thresholds as the Soar module.
	 */
	private String getDirection(double yaw)
	{
		double rotation = (yaw - 90) % 360;
		if(rotation < 0)
			rotation += 360;

		if(rotation < 22.5)
			return "W";
		if(rotation < 67.5)
			return "NW";
		if(rotation < 112.5)
			return "N";
		if(rotation < 157.5)
			return "NE";
		if(rotation < 202.5)
			return "E";
		if(rotation < 247.5)
			return "SE";
		if(rotation < 292.5)
			return "S";
		if(rotation < 337.5)
			return "SW";
		return "W";
	}
}
