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

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
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
 * HUD overlay showing the name, health and distance of the entity the player is
 * currently looking at.
 *
 * <p>Simplification: Soar drew a player head, animated health/armor arcs and
 * eased the panel in and out with NanoVG. This port uses the flat renderer
 * available in WurstB+ Plus and text only, so no skin texture is required.
 */
@SearchTags({"target info", "target hud", "entity info", "entity health",
	"distance", "looked at"})
public final class TargetInfoHack extends Hack implements GUIRenderListener
{
	private static final int PADDING = 4;
	private static final int LINE_GAP = 2;

	private final SliderSetting scale = new SliderSetting("Scale",
		"Scale of the target info overlay.", 1.0, 0.5, 5, 0.1,
		ValueDisplay.DECIMAL);

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the overlay.", 100, 0, 2500, 1, ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the overlay.", 100, 0, 2500, 1, ValueDisplay.INTEGER);

	private final CheckboxSetting background = new CheckboxSetting(
		"Show background", "Draws a colored box behind the text.", true);

	private final ColorSetting bgColor = new ColorSetting("Background color",
		"Color of the background box.", new Color(0, 0, 0, 150));

	private final ColorSetting textColor = new ColorSetting("Text color",
		"Color of the target info text.", Color.WHITE);

	public TargetInfoHack()
	{
		super("TargetInfo");
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
	public void onRenderGUI(GuiGraphics context, float partialTicks)
	{
		if(MC.player == null || MC.level == null)
			return;

		Entity target = getLookedAtEntity();
		if(target == null)
			return;

		String name = target.getName().getString();
		String health;
		if(target instanceof LivingEntity living)
			health = String.format(Locale.ROOT, "Health: %.1f / %.1f",
				living.getHealth(), living.getMaxHealth());
		else
			health = "Health: N/A";

		String distance = String.format(Locale.ROOT, "Distance: %.2f m",
			MC.player.distanceTo(target));

		String[] lines = {name, health, distance};
		int width = 0;
		for(String line : lines)
			width = Math.max(width, MC.font.width(line));

		int lineHeight = MC.font.lineHeight + LINE_GAP;
		int height = lines.length * lineHeight;

		var pose = context.pose();
		pose.pushPose();
		pose.translate((float)posX.getValue(), (float)posY.getValue(), 0.0F);
		pose.scale(scale.getValueF(), scale.getValueF(), 1.0F);

		if(background.isChecked())
			context.fill(-PADDING, -PADDING, width + PADDING,
				height + PADDING, bgColor.getColor().getRGB());

		int color = textColor.getColorI();
		for(int i = 0; i < lines.length; i++)
			context.drawString(MC.font, lines[i], 0, i * lineHeight, color, true);

		pose.popPose();
	}

	private Entity getLookedAtEntity()
	{
		if(MC.hitResult instanceof EntityHitResult hitResult)
			return hitResult.getEntity();

		return null;
	}
}
