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

import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.phys.EntityHitResult;
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
 * HUD overlay showing speed, jump strength and health of the horse the player
 * is riding or looking at.
 *
 * <p>Simplification: Soar only read the looked-at horse. This port also reads
 * the ridden horse, as requested, and uses the vanilla attribute values instead
 * of the old 1.8 horse API.
 */
@SearchTags({"horse stats", "horse", "speed", "jump", "jump strength",
	"horse health"})
public final class HorseStatsHack extends Hack implements GUIRenderListener
{
	private static final double SPEED_FACTOR = 43.1703703704;
	private static final double JUMP_FACTOR = 5.5;
	private static final int PADDING = 4;
	private static final int LINE_GAP = 2;

	private final SliderSetting scale = new SliderSetting("Scale",
		"Scale of the horse stats overlay.", 1.0, 0.5, 5, 0.1,
		ValueDisplay.DECIMAL);

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the overlay.", 100, 0, 2500, 1, ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the overlay.", 300, 0, 2500, 1, ValueDisplay.INTEGER);

	private final CheckboxSetting background = new CheckboxSetting(
		"Show background", "Draws a colored box behind the text.", true);

	private final ColorSetting bgColor = new ColorSetting("Background color",
		"Color of the background box.", new Color(0, 0, 0, 150));

	private final ColorSetting textColor = new ColorSetting("Text color",
		"Color of the horse stats text.", Color.WHITE);

	public HorseStatsHack()
	{
		super("HorseStats");
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

		AbstractHorse horse = getHorse();
		if(horse == null)
			return;

		double speed = horse.getAttributeValue(Attributes.MOVEMENT_SPEED)
			* SPEED_FACTOR;
		double jump = horse.getAttributeValue(Attributes.JUMP_STRENGTH)
			* JUMP_FACTOR;

		String[] lines = {
			String.format(Locale.ROOT, "Speed: %.1f b/s", speed),
			String.format(Locale.ROOT, "Jump: %.1f Blocks", jump),
			String.format(Locale.ROOT, "Health: %.1f / %.1f",
				horse.getHealth(), horse.getMaxHealth())};

		int width = 0;
		for(String line : lines)
			width = Math.max(width, MC.font.width(line));

		int lineHeight = MC.font.lineHeight + LINE_GAP;
		int height = lines.length * lineHeight;

		var pose = context.pose();
		pose.pushMatrix();
		pose.translate((float)posX.getValue(), (float)posY.getValue());
		pose.scale(scale.getValueF(), scale.getValueF());

		if(background.isChecked())
			context.fill(-PADDING, -PADDING, width + PADDING,
				height + PADDING, bgColor.getColor().getRGB());

		int color = textColor.getColorI();
		for(int i = 0; i < lines.length; i++)
			context.text(MC.font, lines[i], 0, i * lineHeight, color, true);

		pose.popMatrix();
	}

	private AbstractHorse getHorse()
	{
		if(MC.player.getVehicle() instanceof AbstractHorse horse)
			return horse;

		if(MC.hitResult instanceof EntityHitResult hitResult
			&& hitResult.getEntity() instanceof AbstractHorse horse)
			return horse;

		return null;
	}
}
